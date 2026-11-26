package br.paulo.tvlauncher.relay

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import br.paulo.tvlauncher.Http
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * O lado Android do relay (decisão A, 26/11/2026): a identidade desta TV e a conversa com a caixa postal do
 * api.sofaly.tv. A criptografia pura mora em [RelayCrypto]/[RelayEnvelope].
 *
 * - Identidade: semente Ed25519 + privada X25519 (32 bytes cada), nascem aqui e ficam cifradas por uma chave AES do
 *   Android Keystore. Prefs "relay" ficam FORA do backup: restaurar em outra TV não pode clonar a identidade desta
 *   (dois aparelhos com o mesmo id disputariam a mesma caixa postal).
 * - Aparelhos pareados: id -> X25519 pública. Só envelope de quem está aqui abre (o pareamento em si vem no passo A2).
 * - [receber]: busca os pendentes, abre, descarta repetidos e confirma TODOS (inclusive lixo), para a caixa não encher.
 */
object RelayTv {
    private const val TAG = "TvLauncher.Relay"
    const val SERVIDOR = "https://api.sofaly.tv"
    private const val APELIDO = "relay_identity"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("relay", Context.MODE_PRIVATE)

    class Identidade(val ed: ByteArray, val x: ByteArray) {
        val edPub: ByteArray = RelayCrypto.edPublica(ed)
        val xPub: ByteArray = RelayCrypto.xPublica(x)
        val id: String = RelayCrypto.idDe(edPub)
    }

    @Volatile private var cache: Identidade? = null

    /** A identidade desta TV (cria na primeira vez). Null só se o Keystore falhar. */
    @Synchronized fun identidade(ctx: Context): Identidade? {
        cache?.let { return it }
        val p = prefs(ctx)
        val cif = p.getString("chaves", null); val iv = p.getString("chaves_iv", null)
        val i = if (cif != null && iv != null) {
            runCatching {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, RelayCrypto.deB64(iv)))
                val b = c.doFinal(RelayCrypto.deB64(cif))
                Identidade(b.copyOfRange(0, 32), b.copyOfRange(32, 64))
            }.onFailure { Log.w(TAG, "identidade ilegível: ${it.javaClass.simpleName}") }.getOrNull()
                ?: return null   // não recria por cima: um Keystore com soluço não pode trocar o id da TV
        } else {
            val nova = Identidade(RelayCrypto.aleatorios(32), RelayCrypto.aleatorios(32))
            val ok = runCatching {
                val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, chave())
                p.edit().putString("chaves", RelayCrypto.b64(c.doFinal(nova.ed + nova.x)))
                    .putString("chaves_iv", RelayCrypto.b64(c.iv)).commit()
            }.onFailure { Log.w(TAG, "Keystore: ${it.javaClass.simpleName}") }.getOrDefault(false)
            if (!ok) return null
            Log.i(TAG, "identidade nova: ${nova.id}")
            nova
        }
        cache = i
        return i
    }

    private fun chave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(APELIDO, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(APELIDO, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return g.generateKey()
    }

    // ---------------------------------------------------------------- aparelhos pareados

    data class Pareado(val id: String, val xPub: ByteArray, val nome: String)

    fun pareados(ctx: Context): List<Pareado> {
        val a = runCatching { JSONArray(prefs(ctx).getString("pareados", "[]")) }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id"); val x = runCatching { RelayCrypto.deB64(o.optString("x")) }.getOrNull()
            if (!RelayCrypto.ID_RE.matches(id) || x?.size != 32) null else Pareado(id, x, o.optString("nome"))
        }
    }

    @Synchronized fun parear(ctx: Context, p: Pareado) {
        val lista = pareados(ctx).filterNot { it.id == p.id } + p
        prefs(ctx).edit().putString("pareados", JSONArray().apply {
            for (q in lista) put(JSONObject().put("id", q.id).put("x", RelayCrypto.b64(q.xPub)).put("nome", q.nome))
        }.toString()).apply()
    }

    @Synchronized fun esquecer(ctx: Context, id: String) {
        val lista = pareados(ctx).filterNot { it.id == id }
        prefs(ctx).edit().putString("pareados", JSONArray().apply {
            for (q in lista) put(JSONObject().put("id", q.id).put("x", RelayCrypto.b64(q.xPub)).put("nome", q.nome))
        }.toString()).apply()
    }

    // ---------------------------------------------------------------- servidor (fora da thread da tela)

    class Resposta(val codigo: Int, val corpo: String)

    /** Pedido assinado ao api.sofaly.tv. Erro de rede vira IOException. */
    @Throws(IOException::class)
    fun pedir(ctx: Context, metodo: String, caminho: String, corpo: ByteArray = ByteArray(0)): Resposta {
        val eu = identidade(ctx) ?: throw IOException("sem identidade (Keystore)")
        val c = URL(SERVIDOR + caminho).openConnection() as HttpURLConnection
        try {
            c.requestMethod = metodo; c.connectTimeout = 8_000; c.readTimeout = 15_000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", Http.USER_AGENT)
            for ((k, v) in RelayCrypto.cabecalhos(eu.ed, metodo, caminho, System.currentTimeMillis() / 1000, corpo)) c.setRequestProperty(k, v)
            if (corpo.isNotEmpty()) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/octet-stream")
                c.setFixedLengthStreamingMode(corpo.size)
                c.outputStream.use { it.write(corpo) }
            }
            val code = c.responseCode
            val s = (if (code in 200..299) c.inputStream else c.errorStream)?.use { Http.readLimited(it, 2_000_000, cut = false) }
            return Resposta(code, s?.toString(Charsets.UTF_8) ?: "")
        } finally { c.disconnect() }
    }

    /** Manda [m] fechado para um aparelho pareado. true = o servidor guardou. */
    fun enviar(ctx: Context, para: Pareado, m: RelayEnvelope.Mensagem): Boolean {
        val eu = identidade(ctx) ?: return false
        val env = RelayEnvelope.fechar(eu.id, eu.x, para.id, para.xPub, m)
        return runCatching { pedir(ctx, "POST", "/v1/envelope/${para.id}", env).codigo == 201 }.getOrDefault(false)
    }

    private val vistos = RelayEnvelope.Vistos()
    @Volatile private var vistosCarregados = false

    /**
     * Busca e processa a caixa postal. [tratar] recebe cada mensagem nova (aberta, de um pareado, não repetida).
     * Devolve quantas foram tratadas, ou -1 se não falou com o servidor.
     */
    @Synchronized fun receber(ctx: Context, tratar: (RelayEnvelope.Aberto) -> Unit): Int {
        val eu = identidade(ctx) ?: return -1
        if (!vistosCarregados) { vistos.carregar(prefs(ctx).getString("vistos", null)); vistosCarregados = true }
        val r = runCatching { pedir(ctx, "GET", "/v1/inbox/pending") }.getOrNull() ?: return -1
        if (r.codigo != 200) { Log.w(TAG, "caixa: HTTP ${r.codigo}"); return -1 }
        val lista = RelayEnvelope.lerPendentes(r.corpo)
        if (lista.isEmpty()) return 0
        val chaves = pareados(ctx).associate { it.id to it.xPub }
        val agora = System.currentTimeMillis()
        var n = 0
        for (e in lista) {
            if (!RelayEnvelope.remetenteConfere(e)) { Log.w(TAG, "envelope ${e.caixaId}: remetente não confere"); continue }
            val a = RelayEnvelope.abrir(eu.id, eu.x, e.dados, agora) { chaves[it] }
            if (a == null) { Log.w(TAG, "envelope ${e.caixaId} de ${e.de}: não abriu"); continue }
            if (!vistos.primeiraVez(a.mensagem.id, a.mensagem.t, agora)) continue
            runCatching { tratar(a) }.onFailure { Log.w(TAG, "tratar ${a.mensagem.tipo}: ${it.javaClass.simpleName}") }
            n++
        }
        prefs(ctx).edit().putString("vistos", vistos.paraJson()).apply()
        // Confirma tudo, até o que não abriu: lixo de quem não é pareado não pode ficar 7 dias ocupando a caixa.
        val ids = JSONArray(lista.map { it.caixaId })
        runCatching { pedir(ctx, "POST", "/v1/inbox/ack", JSONObject().put("ids", ids).toString().toByteArray()) }
        return n
    }

    /** adb: EXTRAS --es o relay. Mostra o id e testa a caixa postal (sem tratar nada). */
    fun texto(ctx: Context): String {
        val eu = identidade(ctx) ?: return "sem identidade: Keystore falhou"
        val teste = runCatching { pedir(ctx, "GET", "/v1/inbox/pending") }
            .fold({ r -> "HTTP ${r.codigo}, ${RelayEnvelope.lerPendentes(r.corpo).size} pendente(s)" }, { "sem rede: ${it.javaClass.simpleName}" })
        return "id ${eu.id}; pareados ${pareados(ctx).size}; caixa: $teste"
    }
}
