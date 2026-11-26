package br.paulo.tvlauncher.relay

import org.json.JSONObject

/**
 * Envelope cifrado de ponta a ponta que passa pela caixa postal do api.sofaly.tv (decisão A, 26/11/2026).
 *
 * Porquê: o servidor guarda o envelope até 7 dias e entrega a quem tem o id; ele não pode conseguir ler nem forjar.
 * Para quê: comandos do celular, "Juntos" e a outra TV da casa falam com esta TV de fora da rede de casa.
 * Como (parecido com o "box" do NaCl, com chave efêmera):
 *   chave = HKDF( X25519(efêmera, fixaDele) || X25519(minhaFixa, fixaDele), sal = efêmera.pub || fixaDele.pub )
 *   - a parte efêmera dá uma chave nova a cada envelope;
 *   - a parte fixa prova quem mandou: só o dono da chave X25519 pareada consegue a mesma chave
 *     (o campo "from" do servidor não basta, o servidor não é de confiança).
 *   Formato: [1 versão][32 efêmera.pub][20 id do remetente][12 nonce][AES-256-GCM(texto)].
 *   AAD = os 53 primeiros bytes + id do destinatário: trocar remetente ou destinatário estraga a etiqueta do GCM.
 *   Texto = JSON {"id": aleatório, "t": hora em ms, "kind": tipo, "data": {...}}: o "id" serve para descartar repetidos
 *   (a caixa entrega "pelo menos uma vez") e o "t" descarta envelopes velhos demais.
 */
object RelayEnvelope {
    const val VERSAO: Byte = 1
    private const val CAB = 1 + 32 + 20
    private val INFO = "sofaly-relay-v1".toByteArray()
    /** Igual ao servidor (envelope até 64 KiB, guardado 7 dias). */
    const val MAX_BYTES = 64 * 1024
    const val VALIDADE_MS = 7L * 24 * 60 * 60 * 1000
    /** Relógio de um aparelho adiantado em relação ao outro: tolera até isto "no futuro". */
    const val FOLGA_MS = 10L * 60 * 1000

    data class Mensagem(val id: String, val t: Long, val tipo: String, val dados: JSONObject)

    data class Aberto(val remetente: String, val mensagem: Mensagem)

    private fun chave(segredoEfemero: ByteArray, segredoFixo: ByteArray, efemeraPub: ByteArray, destinoPub: ByteArray) =
        RelayCrypto.hkdf(segredoEfemero + segredoFixo, efemeraPub + destinoPub, INFO)

    /**
     * Fecha [m] para o aparelho [destinoId] cuja X25519 fixa é [destinoX]. [efemera] e [nonce] só são passados nos
     * testes (vetores fixos); no app ficam aleatórios.
     */
    fun fechar(
        meuId: String, minhaX: ByteArray, destinoId: String, destinoX: ByteArray, m: Mensagem,
        efemera: ByteArray = RelayCrypto.aleatorios(32), nonce: ByteArray = RelayCrypto.aleatorios(RelayCrypto.NONCE),
    ): ByteArray {
        require(RelayCrypto.ID_RE.matches(meuId) && RelayCrypto.ID_RE.matches(destinoId)) { "id inválido" }
        val efPub = RelayCrypto.xPublica(efemera)
        val k = chave(RelayCrypto.xSegredo(efemera, destinoX), RelayCrypto.xSegredo(minhaX, destinoX), efPub, destinoX)
        val cab = byteArrayOf(VERSAO) + efPub + meuId.toByteArray(Charsets.US_ASCII)
        val texto = JSONObject().put("id", m.id).put("t", m.t).put("kind", m.tipo).put("data", m.dados).toString().toByteArray()
        val saida = cab + nonce + RelayCrypto.aesCifrar(k, nonce, texto, cab + destinoId.toByteArray(Charsets.US_ASCII))
        require(saida.size <= MAX_BYTES) { "envelope grande demais" }
        return saida
    }

    /** O id de quem diz ter mandado (antes de abrir): para achar a chave X25519 dele na lista de pareados. */
    fun remetente(env: ByteArray): String? {
        if (env.size < CAB + RelayCrypto.NONCE + 16 || env[0] != VERSAO) return null
        return String(env, 33, 20, Charsets.US_ASCII).takeIf { RelayCrypto.ID_RE.matches(it) }
    }

    /**
     * Abre um envelope para mim. [xDoRemetente] devolve a X25519 fixa de um aparelho pareado (null = desconhecido).
     * Null = não é para mim, remetente não pareado, mexido no caminho, JSON estragado ou velho/futuro demais.
     */
    fun abrir(meuId: String, minhaX: ByteArray, env: ByteArray, agora: Long, xDoRemetente: (String) -> ByteArray?): Aberto? {
        if (env.size > MAX_BYTES) return null
        val de = remetente(env) ?: return null
        val xDele = xDoRemetente(de) ?: return null
        val efPub = env.copyOfRange(1, 33)
        val minhaPub = RelayCrypto.xPublica(minhaX)
        val k = runCatching { chave(RelayCrypto.xSegredo(minhaX, efPub), RelayCrypto.xSegredo(minhaX, xDele), efPub, minhaPub) }.getOrNull() ?: return null
        val cab = env.copyOfRange(0, CAB)
        val nonce = env.copyOfRange(CAB, CAB + RelayCrypto.NONCE)
        val texto = RelayCrypto.aesDecifrar(k, nonce, env.copyOfRange(CAB + RelayCrypto.NONCE, env.size), cab + meuId.toByteArray(Charsets.US_ASCII)) ?: return null
        val o = runCatching { JSONObject(String(texto, Charsets.UTF_8)) }.getOrNull() ?: return null
        val id = o.optString("id"); val t = o.optLong("t", -1); val tipo = o.optString("kind")
        if (id.length !in 8..64 || tipo.isEmpty() || t < 0) return null
        if (t > agora + FOLGA_MS || t < agora - VALIDADE_MS) return null
        return Aberto(de, Mensagem(id, t, tipo, o.optJSONObject("data") ?: JSONObject()))
    }

    /** Um envelope como a caixa postal entrega: [caixaId] é o id DO SERVIDOR (para o ack), [de] o remetente que ele viu. */
    data class Recebido(val caixaId: String, val de: String, val criado: Long, val dados: ByteArray)

    /**
     * Lê a resposta de GET /v1/inbox/pending ({"envelopes":[{id, from, created, data(base64)}]}). Item estragado sai;
     * os que vieram só com id continuam (com dados vazios) para receberem o ack e não voltarem para sempre.
     */
    fun lerPendentes(texto: String?): List<Recebido> {
        val a = runCatching { JSONObject(texto ?: return emptyList()).optJSONArray("envelopes") }.getOrNull() ?: return emptyList()
        val saida = ArrayList<Recebido>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotEmpty() && it.length <= 100 } ?: continue
            val dados = runCatching { RelayCrypto.deB64(o.optString("data")) }.getOrDefault(ByteArray(0))
            saida += Recebido(id, o.optString("from"), o.optLong("created"), dados)
        }
        return saida
    }

    /** O "from" que o servidor viu tem que bater com o remetente escrito no envelope (senão nem tenta abrir). */
    fun remetenteConfere(r: Recebido): Boolean = remetente(r.dados)?.let { it == r.de } ?: false

    /** Id novo para uma mensagem (16 bytes aleatórios em base32). */
    fun novoId(): String = RelayCrypto.base32(RelayCrypto.aleatorios(16))

    /**
     * Ids já processados (a caixa pode entregar o mesmo envelope de novo, p.ex. se o ack se perdeu).
     * Guarda os ids com a hora da mensagem e esquece os que passaram da validade (aí o próprio [abrir] já recusa).
     */
    class Vistos(private val max: Int = 2000) {
        private val mapa = LinkedHashMap<String, Long>()

        /** true = primeira vez (processar); false = repetido (só confirmar o recebimento). */
        @Synchronized fun primeiraVez(id: String, t: Long, agora: Long): Boolean {
            val it = mapa.entries.iterator()
            while (it.hasNext()) if (it.next().value < agora - VALIDADE_MS - FOLGA_MS) it.remove()
            if (mapa.containsKey(id)) return false
            mapa[id] = t
            while (mapa.size > max) mapa.remove(mapa.keys.first())
            return true
        }

        @Synchronized fun paraJson(): String = JSONObject().apply { for ((k, v) in mapa) put(k, v) }.toString()

        @Synchronized fun carregar(json: String?) {
            mapa.clear()
            val o = runCatching { JSONObject(json ?: return) }.getOrNull() ?: return
            for (k in o.keys()) mapa[k] = o.optLong(k)
        }
    }
}
