package br.paulo.tvlauncher.relay

import org.json.JSONObject

/**
 * Pareamento pelo relay (passo A2, 26/11/2026): o celular aprende a chave da TV pelo QR e a TV aprende a do celular
 * pelo envelope de apresentação que o servidor entrega junto com o código.
 *
 * 1. A TV pede um código (POST /v1/pairing) e mostra um QR com: código + id da TV + X25519 pública da TV.
 * 2. O celular lê o QR e manda POST /v1/pairing/<código> com a [apresentacao] no corpo. O servidor entrega o corpo na
 *    caixa da TV, com o "from" = id do celular (tirado da assinatura do pedido).
 * 3. A TV [abrirApresentacao]: confere que a Ed25519 do corpo dá o id que o servidor viu, que o código é o que ela está
 *    mostrando, e decifra com a X25519 do celular. Só então pergunta na tela ("Permitir?") e guarda o pareado.
 * Quem não viu o QR não tem a X25519 da TV: não consegue fechar uma apresentação que abra.
 */
object RelayPairing {
    const val VERSAO: Byte = 1
    private const val TIPO_APRESENTACAO = 'I'.code.toByte()
    private const val CAB = 2 + 32 + 32
    private val INFO = "sofaly-pair-v1".toByteArray()
    val CODIGO_RE = Regex("^[A-HJKMNP-Z2-9]{8}$")   // igual ao CODE_RE do servidor (rules.js)
    const val ESQUEMA = "sofaly://pair"

    data class Qr(val codigo: String, val tvId: String, val tvX: ByteArray)

    fun qrTexto(q: Qr): String = "$ESQUEMA?c=${q.codigo}&t=${q.tvId}&x=${RelayCrypto.b64url(q.tvX)}"

    /** Lê o texto do QR. Null = não é um QR de pareamento da Sofaly (ou está estragado). */
    fun lerQr(texto: String): Qr? {
        if (!texto.startsWith("$ESQUEMA?")) return null
        val campos = texto.substringAfter('?').split('&').mapNotNull { p ->
            val i = p.indexOf('='); if (i <= 0) null else p.substring(0, i) to p.substring(i + 1)
        }.toMap()
        val c = campos["c"] ?: return null; val t = campos["t"] ?: return null
        val x = runCatching { RelayCrypto.deB64url(campos["x"] ?: return null) }.getOrNull() ?: return null
        if (!CODIGO_RE.matches(c) || !RelayCrypto.ID_RE.matches(t) || x.size != 32) return null
        return Qr(c, t, x)
    }

    private fun chave(meuX: ByteArray, xDele: ByteArray, codigo: String) =
        RelayCrypto.hkdf(RelayCrypto.xSegredo(meuX, xDele), codigo.toByteArray(Charsets.US_ASCII), INFO)

    private fun aad(cab: ByteArray, codigo: String, tvId: String) = cab + "$codigo|$tvId".toByteArray(Charsets.US_ASCII)

    /** Lado do celular: o corpo do POST /v1/pairing/<código>. [nome] é como o celular aparece na TV. */
    fun apresentacao(
        edSeed: ByteArray, x: ByteArray, q: Qr, nome: String, agora: Long,
        nonce: ByteArray = RelayCrypto.aleatorios(RelayCrypto.NONCE),
    ): ByteArray {
        val cab = byteArrayOf(VERSAO, TIPO_APRESENTACAO) + RelayCrypto.edPublica(edSeed) + RelayCrypto.xPublica(x)
        val texto = JSONObject().put("name", nome.take(40)).put("t", agora).toString().toByteArray()
        return cab + nonce + RelayCrypto.aesCifrar(chave(x, q.tvX, q.codigo), nonce, texto, aad(cab, q.codigo, q.tvId))
    }

    /** O que a TV fica sabendo do celular que quer parear (antes de perguntar na tela). */
    data class Pedido(val id: String, val xPub: ByteArray, val nome: String)

    /** É uma apresentação (e não um envelope comum)? A caixa da TV recebe os dois. */
    fun ehApresentacao(dados: ByteArray) = dados.size > CAB + RelayCrypto.NONCE + 16 && dados[0] == VERSAO && dados[1] == TIPO_APRESENTACAO

    /**
     * Lado da TV. [de] = o "from" que o servidor viu; [codigo] = o código que ESTA TV está mostrando agora.
     * Null = não é para este código, id não bate com a chave, mexida, ou velha (mais de 15 min).
     */
    fun abrirApresentacao(meuId: String, meuX: ByteArray, codigo: String, de: String, dados: ByteArray, agora: Long): Pedido? {
        if (!ehApresentacao(dados) || dados.size > 4096) return null
        val edPub = dados.copyOfRange(2, 34); val xPub = dados.copyOfRange(34, 66)
        if (RelayCrypto.idDe(edPub) != de) return null   // o corpo tem que ser de quem assinou o pedido
        val cab = dados.copyOfRange(0, CAB)
        val nonce = dados.copyOfRange(CAB, CAB + RelayCrypto.NONCE)
        val k = runCatching { chave(meuX, xPub, codigo) }.getOrNull() ?: return null
        val texto = RelayCrypto.aesDecifrar(k, nonce, dados.copyOfRange(CAB + RelayCrypto.NONCE, dados.size), aad(cab, codigo, meuId)) ?: return null
        val o = runCatching { JSONObject(String(texto, Charsets.UTF_8)) }.getOrNull() ?: return null
        val t = o.optLong("t", -1)
        if (t < agora - 15 * 60_000L || t > agora + RelayEnvelope.FOLGA_MS) return null
        val nome = o.optString("name").replace(Regex("[\\p{Cntrl}]"), "").trim().take(40).ifEmpty { "Celular" }
        return Pedido(de, xPub, nome)
    }
}
