package br.paulo.tvlauncher.relay

import org.json.JSONObject

/**
 * Pareamento pelo relay (passo A2, 26/11/2026). As chaves se trocam pelo canal que JÁ é de confiança: o celular
 * pareado com a TV em casa (QR + certificado fixado + sessão do painel aberta com o PIN do dono) manda as chaves públicas
 * dele em POST /api/relay/parear e recebe as da TV. Sem tela nova na TV e sem "Permitir?": quem tem a sessão do dono
 * já pode controlar a TV, e só o dono chega nessa rota (PapeisPainel nega o que não está na lista do papel).
 *
 * Depois disso, fora de casa, o celular fecha envelopes ([RelayEnvelope]) para a TV pelo api.sofaly.tv.
 * Lógica pura (RelayPairingTest).
 */
object RelayPairing {
    data class Pedido(val id: String, val edPub: ByteArray, val xPub: ByteArray, val nome: String)

    /**
     * Lê e confere o corpo {id, ed, x, name} (chaves em base64url). Null = faltando, tamanho errado ou id que não vem
     * da chave Ed25519 (o id é o endereço da caixa postal: não pode ser um id qualquer escolhido pelo pedido).
     */
    fun lerPedido(o: JSONObject?): Pedido? {
        o ?: return null
        val id = o.optString("id")
        val ed = runCatching { RelayCrypto.deB64url(o.optString("ed")) }.getOrNull() ?: return null
        val x = runCatching { RelayCrypto.deB64url(o.optString("x")) }.getOrNull() ?: return null
        if (ed.size != 32 || x.size != 32 || !RelayCrypto.ID_RE.matches(id) || RelayCrypto.idDe(ed) != id) return null
        val nome = o.optString("name").replace(Regex("\\p{Cntrl}"), "").trim().take(40).ifEmpty { "Celular" }
        return Pedido(id, ed, x, nome)
    }

    /** O que a TV devolve: o id dela e a X25519 pública (o celular fecha os envelopes com ela). */
    /**
     * Tecla e texto só valem na hora: a caixa postal guarda até 7 dias, e uma tecla mandada com a TV desligada não pode
     * ser executada quando ela ligar (horas depois, com alguém vendo outra coisa). Passou disto, descarta.
     */
    const val ATRASO_MAX_MS = 60_000L
    /**
     * [chegouSeg] = a hora (s) em que o SERVIDOR pôs o envelope na caixa (campo "created"). Com ela a conta não depende
     * do relógio do celular (revisor 26/11: celular 2 min atrasado tinha toda tecla descartada). Sem ela, a do celular.
     */
    fun naHora(m: RelayEnvelope.Mensagem, agora: Long, chegouSeg: Long = 0L): Boolean =
        agora - (if (chegouSeg > 0) chegouSeg * 1000 else m.t) <= ATRASO_MAX_MS

    fun resposta(tvId: String, tvX: ByteArray): JSONObject = JSONObject().put("id", tvId).put("x", RelayCrypto.b64url(tvX))

    // ---------------------------------------------------------------- o que pode vir de fora (lista fechada)

    /** Tipos aceitos pela TV. Qualquer outro é ignorado (e confirmado na caixa, para não voltar). */
    const val TECLA = "key"
    const val TEXTO = "text"
    val TIPOS = setOf(TECLA, TEXTO)

    sealed class Comando {
        data class Tecla(val nome: String) : Comando()
        data class Texto(val texto: String) : Comando()
    }

    /** Traduz uma mensagem aberta num comando da lista fechada; null = fora da lista ou dados ruins. */
    fun comando(m: RelayEnvelope.Mensagem): Comando? = when (m.tipo) {
        TECLA -> m.dados.optString("key").takeIf { it.isNotEmpty() && it.length <= 40 }?.let { Comando.Tecla(it) }
        TEXTO -> m.dados.optString("text").takeIf { it.isNotEmpty() && it.length <= 500 }?.let { Comando.Texto(it) }
        else -> null
    }

    /** Mensagem pronta para o celular mandar ([RelayEnvelope.fechar]). */
    fun tecla(nome: String, agora: Long) = RelayEnvelope.Mensagem(RelayEnvelope.novoId(), agora, TECLA, JSONObject().put("key", nome))
    fun texto(t: String, agora: Long) = RelayEnvelope.Mensagem(RelayEnvelope.novoId(), agora, TEXTO, JSONObject().put("text", t))
}
