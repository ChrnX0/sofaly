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
    /** Prefixo das prefs da IA RESERVA (a segunda chave: outra empresa ou outra conta). A principal não tem prefixo. */
    const val BACKUP = "b_"
    /**
     * Teto do mês de fábrica, em tokens (entrada + saída), para a chave do dono não ser "devorada" (Paulo, 25/11/2026: não
     * sabemos ainda quanto o sistema gasta). O dono muda no painel; 0 = sem teto. ~300 mil tokens custam centavos num
     * modelo pequeno. Contado pelo que a própria resposta da IA informa.
     */
    const val DEFAULT_BUDGET = 300_000L

    fun budget(ctx: Context) = prefs(ctx).getLong("budget_tokens", DEFAULT_BUDGET)
    fun monthTokens(ctx: Context): Long {
        val p = prefs(ctx)
        return if (p.getInt("month", 0) == SofyAiRules.month(System.currentTimeMillis())) p.getLong("month_tokens", 0) else 0
    }
    fun setBudget(ctx: Context, tokens: Long) { prefs(ctx).edit().putLong("budget_tokens", tokens.coerceAtLeast(0)).apply() }

    /** Soma o gasto desta resposta ao mês (zera na virada). */
    private fun spend(ctx: Context, answer: JSONObject?) {
        val n = answer?.let { SofyAiRules.usage(it) } ?: 0L
        val mes = SofyAiRules.month(System.currentTimeMillis())
        val p = prefs(ctx)
        val antes = if (p.getInt("month", 0) == mes) p.getLong("month_tokens", 0) else 0
        val calls = if (p.getInt("month", 0) == mes) p.getInt("month_calls", 0) else 0
        p.edit().putInt("month", mes).putLong("month_tokens", antes + n).putInt("month_calls", calls + 1).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A IA principal ([slot] "") ou a reserva ([BACKUP]). */
    fun settings(ctx: Context, slot: String = ""): SofyAiRules.Settings? {
        val p = prefs(ctx)
        return SofyAiRules.resolve(SofyAiRules.Provider.of(p.getString(slot + "provider", null)), p.getString(slot + "base", null),
            p.getString(slot + "model", null), key(ctx, slot))
    }

    fun configured(ctx: Context) = settings(ctx) != null || settings(ctx, BACKUP) != null

    /** Quem perguntar, em ordem, para esta tarefa (principal, depois a reserva; segunda opinião = outra IA). */
    private fun chain(ctx: Context, task: SofyAiRules.Task): List<SofyAiRules.Settings> {
        val p = prefs(ctx)
        return SofyAiRules.chain(task, settings(ctx), settings(ctx, BACKUP), p.getString("model_review", null), p.getString(BACKUP + "model_review", null),
            available(ctx, ""), available(ctx, BACKUP))
    }

    /** Os modelos que a chave alcança, como a própria API disse da última vez (vazio = ainda não leu). */
    fun available(ctx: Context, slot: String): Set<String> =
        prefs(ctx).getString(slot + "models", "").orEmpty().split(',').filter { it.isNotBlank() }.toSet()

    /**
     * Pergunta à API que modelos a chave alcança (Paulo, 25/11/2026: "verifica se pode usar qq modelo"). Uma vez por dia
     * por IA, e sempre no Testar. Rede: chamar fora da tela. Não gasta token (é só a lista).
     */
    fun refreshModels(ctx: Context, force: Boolean = false) {
        val app = ctx.applicationContext
        for (slot in listOf("", BACKUP)) {
            val s = settings(app, slot) ?: continue
            val p = prefs(app)
            if (!force && System.currentTimeMillis() - p.getLong(slot + "models_at", 0L) < 86_400_000L) continue
            val ids = runCatching { get(s, SofyAiRules.modelsUrl(s))?.let { SofyAiRules.modelIds(it) } }.getOrNull()
            val e = p.edit().putLong(slot + "models_at", System.currentTimeMillis())
            if (!ids.isNullOrEmpty()) e.putString(slot + "models", ids.sorted().joinToString(","))
            e.apply()
            Log.i(TAG, "modelos ${s.provider.code}: ${ids?.size ?: "não li"}; conserto=${SofyAiRules.pick(s.provider, SofyAiRules.Task.REPAIR, ids.orEmpty())} " +
                "revisão=${SofyAiRules.pick(s.provider, SofyAiRules.Task.REVIEW, ids.orEmpty())} segunda=${SofyAiRules.pick(s.provider, SofyAiRules.Task.SECOND, ids.orEmpty())}")
        }
    }

    private fun get(s: SofyAiRules.Settings, url: String): JSONObject? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", Http.USER_AGENT)
            SofyAiRules.headers(s).forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (c.responseCode !in 200..299) { Log.w(TAG, "lista de modelos: HTTP ${c.responseCode}"); return null }
            val raw = Http.readLimited(c.inputStream, 512 * 1024, cut = false) ?: return null
            return JSONObject(String(raw))
        } finally { c.disconnect() }
    }

    /**
     * Grava a escolha do dono (painel). [key] null = mantém a que está; "" = apaga. [modelReview] null = mantém.
     * Devolve se ficou utilizável.
     */
    fun save(ctx: Context, provider: String?, base: String?, model: String?, key: String?, slot: String = "", modelReview: String? = null): Boolean {
        val e = prefs(ctx).edit().putString(slot + "provider", SofyAiRules.Provider.of(provider)?.code).putString(slot + "base", base?.trim())
            .putString(slot + "model", model?.trim())
        if (modelReview != null) e.putString(slot + "model_review", modelReview.trim())
        if (key != null) {
            if (key.isBlank()) e.remove(slot + "key").remove(slot + "iv")
            else runCatching {
                val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
                e.putString(slot + "key", Base64.encodeToString(c.doFinal(key.trim().toByteArray()), Base64.NO_WRAP))
                    .putString(slot + "iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
            }.onFailure { Log.w(TAG, "não guardei a chave: ${it.javaClass.simpleName}") }
        }
        e.apply()
        return settings(ctx, slot) != null
    }

    /** Para o painel: o que está escolhido, SEM a chave (só se existe uma). */
    fun json(ctx: Context): JSONObject {
        val p = prefs(ctx)
        return JSONObject().put("provider", p.getString("provider", "") ?: "").put("base", p.getString("base", "") ?: "")
            .put("model", p.getString("model", "") ?: "").put("has_key", p.contains("key")).put("ready", settings(ctx) != null)
            .put("model_review", p.getString("model_review", "") ?: "")
            .put("review_model_used", chain(ctx, SofyAiRules.Task.REVIEW).firstOrNull()?.model ?: "")
            .put("backup", JSONObject().put("provider", p.getString(BACKUP + "provider", "") ?: "").put("base", p.getString(BACKUP + "base", "") ?: "")
                .put("model", p.getString(BACKUP + "model", "") ?: "").put("model_review", p.getString(BACKUP + "model_review", "") ?: "")
                .put("has_key", p.contains(BACKUP + "key")).put("ready", settings(ctx, BACKUP) != null))
            .put("second_model", chain(ctx, SofyAiRules.Task.SECOND).firstOrNull()?.let { "${it.provider.code}/${it.model}" } ?: "")
            .put("second_at", p.getLong("second_at", 0L)).put("second_result", p.getString("second_result", "") ?: "")
            .put("fallbacks", p.getInt("fallbacks", 0))
            .put("repair_model_used", chain(ctx, SofyAiRules.Task.REPAIR).firstOrNull()?.model ?: "")
            .put("models_known", available(ctx, "").size).put("models_at", p.getLong("models_at", 0L))
            .put("asked_today", askedToday(ctx))
            .put("budget_tokens", budget(ctx)).put("month_tokens", monthTokens(ctx))
            .put("month_calls", if (p.getInt("month", 0) == SofyAiRules.month(System.currentTimeMillis())) p.getInt("month_calls", 0) else 0)
            .put("test_at", p.getLong("test_at", 0L)).put("test_ok", p.getBoolean("test_ok", false)).put("test_http", p.getInt("test_http", 0))
            .put("ask_at", p.getLong("ask_at", 0L)).put("ask_http", p.getInt("ask_http", 0)).put("ask_result", p.getString("ask_result", "") ?: "")
            .put("review_at", p.getLong("review_at", 0L)).put("review_result", p.getString("review_result", "") ?: "")
    }

    private fun key(ctx: Context, slot: String = ""): String? {
        val p = prefs(ctx)
        val cif = p.getString(slot + "key", null) ?: return null
        val iv = p.getString(slot + "iv", null) ?: return null
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
        runCatching { refreshModels(app) }
        val base = chain(app, SofyAiRules.Task.REPAIR)
        // O caso já pediu a IA e não resolveu: sobe para o modelo mais capaz (effort baixo); a fila normal fica de reserva.
        val subir = if (SofyAiRules.escalate(c)) settings(app)?.let { SofyAiRules.forEscalation(it, available(app, "")) } else null
        val ordem = listOfNotNull(subir) + base
        if (ordem.isEmpty()) { note("sem configuração utilizável (chave ilegível?)"); return emptyList() }
        val n = askedToday(app)
        val gasto = monthTokens(app); val teto = budget(app)
        if (!SofyAiRules.withinBudget(gasto, teto)) { note("teto do mês: $gasto de $teto tokens"); return emptyList() }
        prefs(app).edit().putLong("asked_day", System.currentTimeMillis() / 86_400_000L).putInt("asked", n + 1).apply()
        val r = call(app, ordem) { SofyAiRules.withEffort(it, SofyAiRules.body(it, c), SofyAiRules.repairEffort(it)) }
        val (tools, cause) = runCatching { SofyAiRules.plan(r.text) }.getOrElse { note("erro ao ler a resposta: ${it.javaClass.simpleName}"); return emptyList() }
        // O que a última pergunta deu (Revisão, Sala 25/11: ask_ai falhou 3 vezes sem uma linha dizendo por quê). Sem a chave.
        val res = when {
            r.text == null -> r.why
            tools.isEmpty() -> "sem ferramenta válida: " + r.text.replace(Regex("\\s+"), " ").take(160)
            else -> "ok: " + tools.joinToString(",") { it.name.lowercase() }
        } + r.by
        note(res)
        if (tools.isNotEmpty()) ObserverTv.logShadow(app, "ai_plan", "${c.type.name} ${c.subject}: ${tools.joinToString(",") { it.name.lowercase() }} ($cause)")
        return tools
    }

    /**
     * Pergunta livre à IA do dono (revisor noturno). Respeita o teto do mês e soma o gasto. Rede: chamar fora da tela.
     * Devolve o texto da resposta ou null; o porquê fica em "review_result" (sem a chave).
     */
    fun ask(ctx: Context, system: String, user: String): String? {
        val app = ctx.applicationContext
        fun note(res: String) { Log.i(TAG, "revisor: $res")
            prefs(app).edit().putLong("review_at", System.currentTimeMillis()).putString("review_result", res).apply() }
        lastHttp = 0
        runCatching { refreshModels(app) }
        val ordem = chain(app, SofyAiRules.Task.REVIEW)
        if (ordem.isEmpty()) { note("sem configuração utilizável"); return null }
        val gasto = monthTokens(app); val teto = budget(app)
        if (!SofyAiRules.withinBudget(gasto, teto)) { note("teto do mês: $gasto de $teto tokens"); return null }
        val r = call(app, ordem) { SofyAiRules.bodyFor(it, system, user) }
        note((if (r.text == null) r.why else "ok (${r.text.length} caracteres)") + r.by)
        return r.text
    }

    /**
     * Segunda opinião (Paulo, 25/11/2026): OUTRA IA julga as regras novas da revisão. Null = ninguém diferente para
     * perguntar, teto do mês ou falha; quem chama trata como "não concordou" (a regra volta a ser proposta outra noite).
     */
    fun second(ctx: Context, system: String, user: String): String? {
        val app = ctx.applicationContext
        fun note(res: String) { Log.i(TAG, "segunda opinião: $res")
            prefs(app).edit().putLong("second_at", System.currentTimeMillis()).putString("second_result", res).apply() }
        val ordem = chain(app, SofyAiRules.Task.SECOND)
        if (ordem.isEmpty()) { note("sem outra IA ou outro modelo para perguntar"); return null }
        if (!SofyAiRules.withinBudget(monthTokens(app), budget(app))) { note("teto do mês"); return null }
        val r = call(app, ordem) { SofyAiRules.bodyFor(it, system, user) }
        note((if (r.text == null) r.why else "ok") + r.by)
        return r.text
    }

    /** Uma chamada do banco de prova: o texto e as medidas (nunca a chave nem o pedido). */
    class BenchCall(val text: String?, val http: Int, val ms: Long, val inTok: Long, val outTok: Long, val cacheRead: Long, val stop: String)

    /**
     * Banco de prova ([SofyBench]): a mesma pergunta com a chave principal e um modelo escolhido, sem fila nem reserva.
     * Soma no gasto do mês como qualquer outra. Null = sem configuração ou teto do mês. Rede: chamar fora da tela.
     */
    fun bench(ctx: Context, model: String, system: String, user: String, effort: String?): BenchCall? {
        val app = ctx.applicationContext
        val s = settings(app)?.copy(model = model) ?: return null
        if (!SofyAiRules.withinBudget(monthTokens(app), budget(app))) return null
        val body = SofyAiRules.withEffort(s, SofyAiRules.bodyFor(s, system, user), effort)
        lastHttp = 0
        val t0 = android.os.SystemClock.elapsedRealtime()
        val answer = runCatching { post(s, body.toString()) }.onFailure { Log.w(TAG, "banco: ${it.javaClass.simpleName}") }.getOrNull()
        val ms = android.os.SystemClock.elapsedRealtime() - t0
        spend(app, answer)
        val u = answer?.let { SofyAiRules.usageParts(it) } ?: LongArray(3)
        val txt = answer?.let { runCatching { SofyAiRules.text(s.provider.format, it) }.getOrNull() }
        val stop = answer?.optString("stop_reason").orEmpty()
        return BenchCall(txt, lastHttp, ms, u[0], u[1], u[2], stop)
    }

    /** O resultado de perguntar a uma fila de IAs: o texto, por que falhou (sem a chave) e quem respondeu. */
    private class Answer(val text: String?, val why: String, val by: String)

    /**
     * Pergunta à primeira da fila; se ela falhar (rede, HTTP de erro, resposta sem texto), à próxima. Cada resposta soma
     * no gasto do mês. Toda troca para a reserva é contada ("fallbacks"), para o relatório.
     */
    private fun call(app: Context, ordem: List<SofyAiRules.Settings>, body: (SofyAiRules.Settings) -> JSONObject): Answer {
        val falhas = ArrayList<String>()
        for ((i, s) in ordem.withIndex()) {
            lastHttp = 0
            var erro: String? = null
            val answer = runCatching { post(s, body(s).toString()) }.onFailure { erro = it.javaClass.simpleName; Log.w(TAG, "pedido: $erro") }.getOrNull()
            spend(app, answer)
            val txt = answer?.let { runCatching { SofyAiRules.text(s.provider.format, it) }.getOrNull() }
            val quem = "${s.provider.code}/${s.model}"
            if (txt != null) {
                if (i > 0) prefs(app).edit().putInt("fallbacks", prefs(app).getInt("fallbacks", 0) + 1).apply()
                return Answer(txt, "", " [$quem" + (if (falhas.isEmpty()) "" else "; antes: ${falhas.joinToString("; ")}") + "]")
            }
            falhas += "$quem " + when { erro != null -> "erro de rede: $erro"; answer == null -> "HTTP $lastHttp"
                else -> "resposta sem texto (${SofyAiRules.shape(answer)})" }
        }
        return Answer(null, falhas.joinToString("; "), "")
    }

    /** Testa a configuração (botão "Testar" do painel): um caso de mentira; devolve se a IA respondeu no formato. */
    fun test(ctx: Context, slot: String = ""): Boolean {
        runCatching { refreshModels(ctx, force = true) }
        val s = settings(ctx.applicationContext, slot)?.let { SofyAiRules.forRepair(it, available(ctx, slot)) } ?: return false
        val c = SofyRepair.Case("ERROR:test", SofyRepair.Type.ERROR, "test", emptySet(), 0, 0, 2)
        val answer = runCatching { post(s, SofyAiRules.body(s, c).toString()) }.getOrNull()
        spend(ctx.applicationContext, answer)
        val ok = answer != null && SofyAiRules.text(s.provider.format, answer) != null
        // Gravado para o relatório (MCP/painel): dá para saber se a IA do dono funciona sem ver a chave.
        prefs(ctx.applicationContext).edit().putLong(slot + "test_at", System.currentTimeMillis()).putBoolean(slot + "test_ok", ok).putInt(slot + "test_http", lastHttp).apply()
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
