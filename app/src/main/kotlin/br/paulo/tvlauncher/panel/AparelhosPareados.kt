package br.paulo.tvlauncher.panel

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Celulares com acesso ao painel sem o PIN (Brainstorm 26/11/2026, item 9). Puro (AparelhosPareadosTest); quem guarda
 * é o [PainelWeb] (prefs "painel_aparelhos", fora do backup).
 *
 * Por quê: a sessão do painel cai com 30 min sem uso, 12 h no total e a cada reinício do launcher; o controle "a um
 * toque" do app do celular virava "digite o PIN" várias vezes por dia. Como: o app do celular, já dentro do painel e
 * falando com a TV pelo certificado fixado do QR, pede uma chave de aparelho longa (256 bits) e guarda cifrada no
 * Keystore dele. Quando a sessão cai, troca a chave por uma sessão nova, sem PIN e SEM elevação: o que não tem volta
 * (desinstalar, restaurar, apagar) continua pedindo o PIN. A TV guarda só o SHA-256 da chave; o painel lista cada
 * aparelho com "Revogar".
 */
object AparelhosPareados {
    const val MAX = 10
    /** Chave de aparelho: 32 bytes em hex. */
    const val TAMANHO_HEX = 64

    data class Aparelho(val id: String, val hash: String, val nome: String, val criado: Long, val ultimoUso: Long)

    /** Nome que o celular diz ter (modelo do aparelho): só texto curto e visível, sem controle. */
    fun nomeLimpo(nome: String?): String =
        (nome ?: "").filter { it.code >= 0x20 && it.code != 0x7f }.trim().take(40).ifEmpty { "Celular" }

    /** Emite uma chave nova. Devolve a lista nova (a mais antiga sai depois de [MAX]) e a chave, que só o celular guarda. */
    fun emitir(lista: List<Aparelho>, nome: String?, agora: Long, aleatorio: SecureRandom = SecureRandom()): Pair<List<Aparelho>, String> {
        val chave = ByteArray(32).also { aleatorio.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        var id: String
        do { id = ByteArray(4).also { aleatorio.nextBytes(it) }.joinToString("") { "%02x".format(it) } } while (lista.any { it.id == id })
        val novo = Aparelho(id, hash(chave), nomeLimpo(nome), agora, agora)
        return (lista.sortedBy { it.criado } + novo).takeLast(MAX) to chave
    }

    /** O aparelho dono da [chave], ou null. Comparação em tempo constante (o tempo não entrega o começo do hash). */
    fun conferir(lista: List<Aparelho>, chave: String?): Aparelho? {
        if (chave == null || chave.length != TAMANHO_HEX || chave.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        val h = hash(chave).toByteArray()
        var achado: Aparelho? = null
        for (a in lista) if (MessageDigest.isEqual(a.hash.toByteArray(), h)) achado = a   // percorre todos de propósito
        return achado
    }

    fun usado(lista: List<Aparelho>, id: String, agora: Long): List<Aparelho> =
        lista.map { if (it.id == id) it.copy(ultimoUso = agora) else it }

    fun revogar(lista: List<Aparelho>, id: String): List<Aparelho> = lista.filterNot { it.id == id }

    /** O que o painel mostra (sem o hash). */
    fun publico(a: Aparelho): JSONObject = JSONObject().put("id", a.id).put("nome", a.nome).put("criado", a.criado).put("uso", a.ultimoUso)

    fun texto(lista: List<Aparelho>): String = JSONArray().apply {
        lista.forEach { put(JSONObject().put("id", it.id).put("h", it.hash).put("n", it.nome).put("c", it.criado).put("u", it.ultimoUso)) }
    }.toString()

    fun ler(t: String?): List<Aparelho> = runCatching {
        val a = JSONArray(t ?: return emptyList())
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Aparelho(it.getString("id"), it.getString("h"), it.optString("n"), it.optLong("c"), it.optLong("u")) } }
    }.getOrDefault(emptyList())

    private fun hash(chave: String): String =
        MessageDigest.getInstance("SHA-256").digest(chave.toByteArray()).joinToString("") { "%02x".format(it) }
}
