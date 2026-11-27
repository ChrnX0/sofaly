package br.paulo.tvlauncher.relay

import org.json.JSONObject

/**
 * TV com TV de casas diferentes pelo api.sofaly.tv (Assistir junto sem Tailscale, Paulo 26/11/2026: "os dois" =
 * código de 8 letras e, depois, o atalho pelo celular). Puro (RelayHousesTest).
 *
 * Como duas TVs se conhecem pelo código:
 *  1. A TV A pede um código ao servidor (POST /v1/pairing) e mostra na tela. Vale 10 min e uma vez só.
 *  2. A TV B digita o código e manda a [apresentacao] dela (POST /v1/pairing/<código>); o servidor entrega na caixa
 *     de A e devolve a B o id de A.
 *  3. A mostra os 4 números de [conferencia] e pergunta. Quem está nas duas casas confere pelo telefone que os números
 *     são iguais; no OK, A guarda B e manda a apresentação dela para B, que guarda A.
 * Apresentação vai aberta (só chaves públicas): ela é o que permite os envelopes cifrados depois. Os números de
 * conferência saem das duas chaves: se o servidor trocasse uma delas no caminho, as duas TVs mostrariam números
 * diferentes.
 *
 * Depois disso só passa o que é da Sessão junto ([junto]): convite, eventos (play/pausa/posição/reação) e sair.
 */
object RelayHouses {
    /** Primeiro byte de uma apresentação na caixa postal (envelope cifrado começa com [RelayEnvelope.VERSAO] = 1). */
    const val APRESENTACAO: Byte = 2
    private const val MAX_APRESENTACAO = 2048

    /** Alfabeto do servidor (server/api/src/rules.js): A-Z e 2-9. */
    val CODIGO = Regex("^[A-Z2-9]{8}$")

    /**
     * O que a pessoa digitou -> código, ou null. Aceita minúsculas, espaços e traços; 0 e 1 viram O e I (o alfabeto
     * não tem 0 nem 1, e no controle é fácil trocar).
     */
    fun codigo(digitado: String?): String? {
        val c = (digitado ?: return null).uppercase().filter { !it.isWhitespace() && it != '-' }
            .map { when (it) { '0' -> 'O'; '1' -> 'I'; else -> it } }.joinToString("")
        return c.takeIf { CODIGO.matches(it) }
    }

    /** "ABCD EFGH": mais fácil de ler em voz alta. */
    fun mostrar(codigo: String): String = if (codigo.length == 8) codigo.substring(0, 4) + " " + codigo.substring(4) else codigo

    fun apresentacao(id: String, edPub: ByteArray, xPub: ByteArray, nome: String): ByteArray =
        byteArrayOf(APRESENTACAO) + JSONObject().put("id", id).put("ed", RelayCrypto.b64url(edPub))
            .put("x", RelayCrypto.b64url(xPub)).put("name", nome).toString().toByteArray(Charsets.UTF_8)

    /**
     * Lê uma apresentação que o servidor diz ter vindo de [de]. Null se não for apresentação, se estiver estragada ou se
     * o id dela não for [de] (o servidor confere a assinatura de quem manda: o id que ele dá é o certo).
     */
    fun lerApresentacao(dados: ByteArray, de: String): RelayPairing.Pedido? {
        if (dados.size < 2 || dados.size > MAX_APRESENTACAO || dados[0] != APRESENTACAO) return null
        val o = runCatching { JSONObject(String(dados, 1, dados.size - 1, Charsets.UTF_8)) }.getOrNull() ?: return null
        val p = RelayPairing.lerPedido(o) ?: return null
        if (p.id != de) return null
        return p.copy(nome = o.optString("name").replace(Regex("\\p{Cntrl}"), "").trim().take(40).ifEmpty { "TV" })
    }

    /** 4 números que as duas TVs mostram iguais (independe de quem é A ou B). */
    fun conferencia(xA: ByteArray, xB: ByteArray): String {
        val (p, q) = if (RelayCrypto.hex(xA) <= RelayCrypto.hex(xB)) xA to xB else xB to xA
        val h = RelayCrypto.sha256("sofaly-houses-v1".toByteArray() + p + q)
        val n = ((h[0].toLong() and 0xFF) shl 24) or ((h[1].toLong() and 0xFF) shl 16) or ((h[2].toLong() and 0xFF) shl 8) or (h[3].toLong() and 0xFF)
        return (n % 10_000).toString().padStart(4, '0')
    }

    // ---------------------------------------------------------------- Sessão junto pelo relay

    const val JUNTO = "junto"
    /** Só as rotas da Sessão junto (SessaoJunto): convite, evento e sair. Nada de "estado" (o envelope já prova quem manda). */
    private val CAMINHO = Regex("^/junto/(convite|[0-9a-f]{32}/(evento|sair))$")
    private const val MAX_CORPO = 4096

    fun junto(caminho: String, corpo: JSONObject?, agora: Long): RelayEnvelope.Mensagem? {
        if (!CAMINHO.matches(caminho)) return null
        val c = corpo ?: JSONObject()
        if (c.toString().length > MAX_CORPO) return null
        return RelayEnvelope.Mensagem(RelayEnvelope.novoId(), agora, JUNTO, JSONObject().put("path", caminho).put("body", c))
    }

    /** (caminho, corpo) de uma mensagem da Sessão junto, ou null se não for uma. */
    fun lerJunto(m: RelayEnvelope.Mensagem): Pair<String, JSONObject>? {
        if (m.tipo != JUNTO) return null
        val caminho = m.dados.optString("path").takeIf { CAMINHO.matches(it) } ?: return null
        val corpo = m.dados.optJSONObject("body") ?: return null
        if (corpo.toString().length > MAX_CORPO) return null
        return caminho to corpo
    }

    // ---------------------------------------------------------------- atalho pelo celular

    /**
     * Atalho pelo celular (Paulo 26/11/2026, "os dois"): o celular do dono, pareado pelo relay com as duas TVs (cada uma
     * em sua casa, com o PIN), manda a cada uma o id e a chave X25519 da outra, dentro do envelope cifrado. Não precisa
     * de código nem dos 4 números: o celular já tem as chaves certas das duas, recebidas na rede de casa de cada uma.
     */
    const val CASA = "house"
    data class Casa(val id: String, val xPub: ByteArray, val nome: String)

    fun casa(id: String, xPub: ByteArray, nome: String, agora: Long): RelayEnvelope.Mensagem? {
        if (!RelayCrypto.ID_RE.matches(id) || xPub.size != 32) return null
        return RelayEnvelope.Mensagem(RelayEnvelope.novoId(), agora, CASA,
            JSONObject().put("id", id).put("x", RelayCrypto.b64url(xPub)).put("name", limparNome(nome)))
    }

    fun lerCasa(m: RelayEnvelope.Mensagem): Casa? {
        if (m.tipo != CASA) return null
        val id = m.dados.optString("id").takeIf { RelayCrypto.ID_RE.matches(it) } ?: return null
        val x = runCatching { RelayCrypto.deB64url(m.dados.optString("x")) }.getOrNull()?.takeIf { it.size == 32 } ?: return null
        return Casa(id, x, limparNome(m.dados.optString("name")))
    }

    private fun limparNome(n: String?) = (n ?: "").replace(Regex("\\p{Cntrl}"), "").trim().take(40).ifEmpty { "TV" }
}
