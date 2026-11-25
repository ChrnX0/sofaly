package br.paulo.tvlauncher.observer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import br.paulo.tvlauncher.Http
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A parte Android de [SofyAiRules]: onde ficam o provedor e a chave do dono, e o pedido à IA.
 * A chave é cifrada por uma chave do Android Keystore (não sai do aparelho) e as prefs "sofy_ai" ficam FORA do backup.
 * Nada daqui vai para o log, o diário ou o barramento além de "perguntei" e do nome das ferramentas escolhidas.
 */
object SofyAi {
    private const val TAG = "TvLauncher.SofyAi"
    const val PREFS = "sofy_ai"
    private const val ALIAS = "sofy_ai_key"
    /** No máximo isto de perguntas por dia: um defeito em laço não vira conta alta na chave do dono. */
    private const val MAX_PER_DAY = 10

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun settings(ctx: Context): SofyAiRules.Settings? {
        val p = prefs(ctx)
        return SofyAiRules.resolve(SofyAiRules.Provider.of(p.getString("provider", null)), p.getString("base", null), p.getString("model", null), key(ctx))
    }

    fun configured(ctx: Context) = settings(ctx) != null

    /** Grava a escolha do dono (painel). [key] null = mantém a que está; "" = apaga. Devolve se ficou utilizável. */
    fun save(ctx: Context, provider: String?, base: String?, model: String?, key: String?): Boolean {
        val e = prefs(ctx).edit().putString("provider", SofyAiRules.Provider.of(provider)?.code).putString("base", base?.trim()).putString("model", model?.trim())
        if (key != null) {
            if (key.isBlank()) e.remove("key").remove("iv")
            else runCatching {
                val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
                e.putString("key", Base64.encodeToString(c.doFinal(key.trim().toByteArray()), Base64.NO_WRAP)).putString("iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
            }.onFailure { Log.w(TAG, "não guardei a chave: ${it.javaClass.simpleName}") }
        }
        e.apply()
        return configured(ctx)
    }

    /** Para o painel: o que está escolhido, SEM a chave (só se existe uma). */
    fun json(ctx: Context): JSONObject {
        val p = prefs(ctx)
        return JSONObject().put("provider", p.getString("provider", "") ?: "").put("base", p.getString("base", "") ?: "")
            .put("model", p.getString("model", "") ?: "").put("has_key", p.contains("key")).put("ready", configured(ctx))
            .put("asked_today", askedToday(ctx))
            .put("test_at", p.getLong("test_at", 0L)).put("test_ok", p.getBoolean("test_ok", false)).put("test_http", p.getInt("test_http", 0))
            .put("ask_at", p.getLong("ask_at", 0L)).put("ask_http", p.getInt("ask_http", 0)).put("ask_result", p.getString("ask_result", "") ?: "")
    }

    private fun key(ctx: Context): String? {
        val p = prefs(ctx)
        val cif = p.getString("key", null) ?: return null
        val iv = p.getString("iv", null) ?: return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(cif, Base64.NO_WRAP)))
        }.getOrNull()
    }

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return g.generateKey()
    }

    private fun askedToday(ctx: Context): Int {
        val p = prefs(ctx)
        return if (p.getLong("asked_day", 0L) == System.currentTimeMillis() / 86_400_000L) p.getInt("asked", 0) else 0
    }

    /** Pergunta à IA do dono o que tentar neste caso. Rede: chamar fora da tela. Vazio = sem resposta útil. */
    fun plan(ctx: Context, c: SofyRepair.Case): List<SofyRepair.Tool> {
        val app = ctx.applicationContext
        // Toda saída deixa rastro (Revisão, Sala 25/11: 4 ASK_AI "FAILED" com ask_at=0, sem saber se a chamada saiu).
        fun note(res: String) {
            Log.i(TAG, "pergunta ${c.type.name} ${c.subject}: $res")
            prefs(app).edit().putLong("ask_at", System.currentTimeMillis()).putInt("ask_http", lastHttp).putString("ask_result", res).apply()
        }
        lastHttp = 0
        val s = settings(app) ?: run { note("sem configuração utilizável (chave ilegível?)"); return emptyList() }
        val n = askedToday(app)
        if (n >= MAX_PER_DAY) { note("limite do dia: $n perguntas"); return emptyList() }
        prefs(app).edit().putLong("asked_day", System.currentTimeMillis() / 86_400_000L).putInt("asked", n + 1).apply()
        var erro: String? = null
        val answer = runCatching { post(s, SofyAiRules.body(s, c).toString()) }.onFailure { erro = it.javaClass.simpleName; Log.w(TAG, "pedido: $erro") }.getOrNull()
        val lido = runCatching { val t = answer?.let { SofyAiRules.text(s.provider.format, it) }; t to SofyAiRules.plan(t) }
            .getOrElse { note("erro ao ler a resposta: ${it.javaClass.simpleName}"); return emptyList() }
        val txt = lido.first
        val (tools, cause) = lido.second
        // O que a última pergunta deu (Revisão, Sala 25/11: ask_ai falhou 3 vezes sem uma linha dizendo por quê). Sem a chave.
        val res = when {
            erro != null -> "erro de rede: $erro"
            answer == null -> "HTTP $lastHttp"
            txt == null -> "resposta sem texto (${SofyAiRules.shape(answer)})"
            tools.isEmpty() -> "sem ferramenta válida: " + txt.replace(Regex("\\s+"), " ").take(160)
            else -> "ok: " + tools.joinToString(",") { it.name.lowercase() }
        }
        note(res)
        if (tools.isNotEmpty()) ObserverTv.logShadow(app, "ai_plan", "${c.type.name} ${c.subject}: ${tools.joinToString(",") { it.name.lowercase() }} ($cause)")
        return tools
    }

    /** Testa a configuração (botão "Testar" do painel): um caso de mentira; devolve se a IA respondeu no formato. */
    fun test(ctx: Context): Boolean {
        val s = settings(ctx.applicationContext) ?: return false
        val c = SofyRepair.Case("ERROR:test", SofyRepair.Type.ERROR, "test", emptySet(), 0, 0, 2)
        val answer = runCatching { post(s, SofyAiRules.body(s, c).toString()) }.getOrNull()
        val ok = answer != null && SofyAiRules.text(s.provider.format, answer) != null
        // Gravado para o relatório (MCP/painel): dá para saber se a IA do dono funciona sem ver a chave.
        prefs(ctx.applicationContext).edit().putLong("test_at", System.currentTimeMillis()).putBoolean("test_ok", ok).putInt("test_http", lastHttp).apply()
        return ok
    }

    @Volatile private var lastHttp = 0

    private fun post(s: SofyAiRules.Settings, body: String): JSONObject? {
        val bytes = body.toByteArray()
        val c = URL(SofyAiRules.url(s)).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 45_000; c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("User-Agent", Http.USER_AGENT)
            SofyAiRules.headers(s).forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            lastHttp = c.responseCode
            if (c.responseCode !in 200..299) { Log.w(TAG, "a IA respondeu ${c.responseCode}"); return null }
            val raw = Http.readLimited(c.inputStream, 256 * 1024, cut = false) ?: return null
            return JSONObject(String(raw))
        } finally { c.disconnect() }
    }
}
