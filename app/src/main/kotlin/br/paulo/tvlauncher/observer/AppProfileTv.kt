package br.paulo.tvlauncher.observer

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.family.Diario
import br.paulo.tvlauncher.family.Perfil
import br.paulo.tvlauncher.player.OnDemandTv
import br.paulo.tvlauncher.privilege.GuardiaoShizuku
import br.paulo.tvlauncher.privilege.SaudeAcesso
import org.json.JSONArray
import org.json.JSONObject

/**
 * A parte do Android da ficha viva de cada app ([AppProfile]). A cada [GAP_MS] (na batida da rede do Observador):
 *  1. lê os eventos de uso do Android desde a última leitura (app foi para a frente / saiu; a permissão de uso o Device
 *     Owner já concede) e soma na ficha do PERFIL ativo (Perfil.chaveDados; "" = compartilhado ou visita);
 *  2. a cada 2 h, pelo shell próprio (numa TV que já o pareou), lê no registro de eventos do Android quanto cada app
 *     demorou para abrir;
 *  3. uma vez por dia, escreve as dicas no registro do Observador ("app_tip") e no Diário;
 *  4. AGE (24/11/2026): abre quente na hora firme ([prewarm]), receita antes de abrir app pesado ([beforeOpen]) e, de
 *     madrugada, recompila o que abre devagar e põe sob demanda o esquecido ([night]). Cada ação vai ao registro e ao barramento.
 *
 * As fichas moram em "sofy_learning" (chave "apps"), que vai no backup: TV restaurada já conhece os hábitos da casa.
 */
object AppProfileTv {
    private const val TAG = "TvLauncher.AppProfile"
    private const val GAP_MS = 15 * 60_000L
    private const val TIPS_GAP_MS = 86_400_000L
    private const val STARTS_GAP_MS = 2 * 3_600_000L
    private const val FIRST_LOOK_MS = 3 * 86_400_000L   // 1ª leitura: os últimos 3 dias que o Android ainda guarda

    @Volatile private var lastRun = 0L
    @Volatile private var states: MutableMap<String, AppProfile.State>? = null

    private fun ignore(ctx: Context) = setOf(ctx.packageName, "com.android.systemui", "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher", "com.android.tv.settings")

    private fun load(ctx: Context): MutableMap<String, AppProfile.State> = states ?: HashMap(AppProfile.fromJson(
        ObserverTv.learning(ctx).getString("apps", null)?.let { runCatching { JSONObject(it) }.getOrNull() })).also { states = it }

    /** Na batida da rede (a cada 5 min); só trabalha de [GAP_MS] em [GAP_MS]. Fora da tela. */
    @Synchronized fun tick(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRun < GAP_MS) return
        lastRun = now
        val app = ctx.applicationContext
        val dev = app.getSharedPreferences("observer", Context.MODE_PRIVATE)
        val from = dev.getLong("apps_events_at", 0L).takeIf { it > 0 } ?: (now - FIRST_LOOK_MS)
        val events = readEvents(app, from, now) ?: return
        val profile = runCatching { Perfil.chaveDados(app) }.getOrDefault("")
        val all = load(app)
        // Só app com ícone (o que a pessoa abre): a busca por voz, a tela de permissões etc. não são "apps da casa"
        // (vistos na Sala 24/11/2026). Eles entram como "ignorar": tiram o app anterior da frente, sem ganhar ficha.
        val launchable = launchable(app)
        val skip = ignore(app) + events.map { it.pkg }.filter { launchable != null && it !in launchable }
        val before = all[profile] ?: AppProfile.State(emptyMap())
        val clean = if (launchable == null) before else before.copy(cards = before.cards.filterKeys { it in launchable })
        var s = AppProfile.learn(clean, events, { ObserverTv.hours(it).second }, now, skip)
        s = withStarts(app, dev, s)
        s = withVersionsAndMemory(app, s)
        runCatching { prewarm(app, s, now) }.onFailure { Log.w(TAG, "abrir quente: $it") }?.getOrNull()?.let { s = it }
        all[profile] = s
        ObserverTv.learning(app).edit().putString("apps", AppProfile.toJson(all).toString()).apply()
        dev.edit().putLong("apps_events_at", now).apply()
        if (now - dev.getLong("apps_tips_at", 0L) >= TIPS_GAP_MS) {
            dev.edit().putLong("apps_tips_at", now).apply()
            shadowTips(app, profile, s, now)
        }
    }

    // ---------------------------------------------------------------- a ficha age (ideias 8, 9, 10, 11 e 14)

    /** Versão de cada app (mudou = recomeça a medir o tempo de abrir) e a memória livre com o app da frente (ideias 14 e 11). */
    private fun withVersionsAndMemory(ctx: Context, s: AppProfile.State): AppProfile.State {
        val pm = ctx.packageManager
        val cards = HashMap(s.cards)
        for ((pkg, c) in s.cards) {
            val ver = runCatching { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(pkg, 0)) }.getOrDefault(0L)
            val n = AppProfile.noteVersion(c, ver)
            if (c.ver != 0L && n.ver != c.ver) SofyBusTv.emit(ctx, SofyBus.Source.SOFY, "app_updated")
            cards[pkg] = n
        }
        val front = s.front
        if (front != null) cards[front]?.let { c ->
            runCatching {
                val mi = android.app.ActivityManager.MemoryInfo()
                ctx.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(mi)
                cards[front] = AppProfile.noteMemory(c, mi.availMem / 1_048_576)
            }
        }
        return s.copy(cards = cards)
    }

    /**
     * Solta memória: pede ao Android para fechar os apps das fichas que estão parados em segundo plano (`am kill` só
     * mata processo em segundo plano; nunca o da frente, nunca o [keep], nada com som tocando). Pelo shell, então só
     * numa TV que já o pareou. Devolve quantos pediu para fechar.
     */
    private fun freeOthers(ctx: Context, keep: String, front: String?, cards: Collection<AppProfile.Card>): Int {
        if (ctx.getSystemService(android.media.AudioManager::class.java)?.isMusicActive == true) return 0
        if (!runCatching { SaudeAcesso.shellEsperado(ctx) }.getOrDefault(false)) return 0
        val alvos = cards.map { it.pkg }.filter { it != keep && it != front && it != ctx.packageName }.take(12)
        if (alvos.isEmpty()) return 0
        runCatching { GuardiaoShizuku.comando(ctx, alvos.joinToString("; ") { "am kill $it" }, 15_000) }
        return alvos.size
    }

    /** Abrir quente (ideia 8): até 20 min antes da hora firme de um app, com a tela acesa, solta a memória dos outros. */
    private fun prewarm(ctx: Context, s: AppProfile.State, now: Long): AppProfile.State? {
        if (ctx.getSystemService(android.os.PowerManager::class.java)?.isInteractive != true) return null
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val minuto = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val dia = now / 86_400_000L
        val tip = tips(ctx, s, now).firstOrNull { t -> t.advice == AppProfile.Advice.PREWARM && t.hour != null && t.pkg != s.front &&
            s.cards[t.pkg]?.let { AppProfile.prewarmNow(it, t.hour, minuto, dia) } == true } ?: return null
        val n = freeOthers(ctx, tip.pkg, s.front, s.cards.values)
        SofyBusTv.emit(ctx, SofyBus.Source.SOFY, "prewarm", detail = "$n apps")
        ObserverTv.logShadow(ctx, "acted_prewarm", "hora firme às ${tip.hour}h: soltei a memória de $n apps parados")
        return s.copy(cards = s.cards + (tip.pkg to s.cards.getValue(tip.pkg).copy(prewarmDay = dia)))
    }

    @Volatile private var lastRecipe = 0L

    /**
     * Receita por app (ideia 11): a HOME vai abrir [pkg]. Se a ficha diz que ele aperta a memória da TV, a Sofy solta a
     * memória dos outros antes (no máximo 1 vez a cada 10 min). Não bloqueia a abertura.
     */
    fun beforeOpen(ctx: Context, pkg: String) {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        if (now - lastRecipe < 10 * 60_000L) return
        br.paulo.tvlauncher.Fundo.rodar("app-recipe") {
            val profile = runCatching { Perfil.chaveDados(app) }.getOrDefault("")
            val s = synchronized(AppProfileTv) { load(app)[profile] } ?: return@rodar
            val c = s.cards[pkg] ?: return@rodar
            if (!AppProfile.heavy(c, ObserverTv.normalOf(app, ObserverCore.Signal.MEMORY_AVAILABLE_MB))) return@rodar
            lastRecipe = now
            val n = freeOthers(app, pkg, null, s.cards.values)
            SofyBusTv.emit(app, SofyBus.Source.SOFY, "recipe_memory", detail = "$n apps")
            ObserverTv.logShadow(app, "acted_recipe", "app pesado vai abrir: soltei a memória de $n apps parados")
        }
    }

    /**
     * De madrugada (ManutencaoNoturna, depois dos passos): recompila até 2 apps que abrem devagar (ideia 9) e põe sob
     * demanda até 1 app esquecido e seguro (ideia 10, com o recado da manhã e o desfazer de sempre). Devolve o resumo.
     */
    @Synchronized fun night(ctx: Context): String {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        val profile = runCatching { Perfil.chaveDados(app) }.getOrDefault("")
        val all = load(app)
        val s = all[profile] ?: return "sem ficha"
        val t = tips(app, s, now)
        val feito = ArrayList<String>()
        val cards = HashMap(s.cards)
        if (runCatching { SaudeAcesso.shellEsperado(app) }.getOrDefault(false)) {
            for (tip in t.filter { it.advice == AppProfile.Advice.RECOMPILE }.take(2)) {
                val out = runCatching { GuardiaoShizuku.comando(app, "cmd package compile -m speed-profile -f ${tip.pkg}", 180_000) }.getOrNull()
                val ok = out?.contains("Success") == true
                if (ok) cards[tip.pkg]?.let { cards[tip.pkg] = AppProfile.noteCompiled(it, now) }
                SofyBusTv.emit(app, SofyBus.Source.SOFY, "acted_recompile", ok = ok)
                ObserverTv.logShadow(app, "acted_recompile", "${tip.why}: recompilei (${if (ok) "ok" else "falhou"})")
                feito += "recompilou 1 (${if (ok) "ok" else "falhou"})"
            }
        }
        t.firstOrNull { it.advice == AppProfile.Advice.ON_DEMAND }?.let { tip ->
            val motivo = runCatching { OnDemandTv.mark(app, tip.pkg) }.getOrElse { it.javaClass.simpleName }
            val ok = motivo == null
            SofyBusTv.emit(app, SofyBus.Source.SOFY, "acted_on_demand", ok = ok)
            if (ok) {
                val nome = runCatching { app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(tip.pkg, 0)).toString() }.getOrDefault(tip.pkg)
                ObserverTv.enqueueNote(app, ObserverCore.sofyNote(ObserverCore.Kind.APP_ON_DEMAND, now, ObserverCore.Step.ACTED_REVERSIBLE, listOf(nome)))
                runCatching { Diario.anotar(app, "tv", "Sofy: app sob demanda", "$nome: ${tip.why}") }
            }
            ObserverTv.logShadow(app, "acted_on_demand", "${tip.why}: ${if (ok) "sob demanda" else "não deu ($motivo)"}")
            feito += "sob demanda 1 (${if (ok) "ok" else "não deu"})"
        }
        all[profile] = s.copy(cards = cards)
        ObserverTv.learning(app).edit().putString("apps", AppProfile.toJson(all).toString()).apply()
        return feito.joinToString("; ").ifEmpty { "nada a fazer" }
    }

    /** Pacotes com ícone na TV (Leanback ou launcher comum); null se o Android não respondeu (aí não filtra). */
    private fun launchable(ctx: Context): Set<String>? = runCatching {
        val pm = ctx.packageManager
        fun of(cat: String) = pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(cat), 0)
            .map { it.activityInfo.packageName }
        (of(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER) + of(android.content.Intent.CATEGORY_LAUNCHER)).toSet()
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun readEvents(ctx: Context, from: Long, to: Long): List<AppProfile.Event>? = try {
        val ev = ctx.getSystemService(UsageStatsManager::class.java).queryEvents(from, to)
        val out = ArrayList<AppProfile.Event>()
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> out += AppProfile.Event(e.packageName, e.timeStamp, true)
                UsageEvents.Event.ACTIVITY_PAUSED -> out += AppProfile.Event(e.packageName, e.timeStamp, false)
            }
        }
        out
    } catch (t: Exception) { Log.w(TAG, "eventos de uso: ${t.javaClass.simpleName}"); null }

    /**
     * Tempos de abrir desde a última leitura (o registro de eventos é do sistema: só o shell lê). De [STARTS_GAP_MS] em
     * [STARTS_GAP_MS] e só numa TV que já pareou o shell: o shell da Sala conecta sob demanda (o estado "desligado"
     * entre um uso e outro é normal, verificado 24/11/2026: ESTADO = "shell ok" em 29 ms).
     */
    private fun withStarts(ctx: Context, dev: android.content.SharedPreferences, s: AppProfile.State): AppProfile.State {
        val now = System.currentTimeMillis()
        if (now - dev.getLong("apps_starts_tried", 0L) < STARTS_GAP_MS) return s
        if (!runCatching { SaudeAcesso.shellEsperado(ctx) }.getOrDefault(false)) return s
        // Tentativas e o resultado da última: a Sofy se acusa se o tempo de abrir nunca chega (SofySelfCheck, ideia 38).
        dev.edit().putLong("apps_starts_tried", now).putInt("apps_starts_tries", dev.getInt("apps_starts_tries", 0) + 1).apply()
        val out = runCatching {
            GuardiaoShizuku.comando(ctx, "logcat -d -b events -v epoch -s wm_activity_launch_time am_activity_launch_time", 15_000)
        }.getOrNull()
        if (out == null) { dev.edit().putString("apps_starts_last", "o shell não respondeu").apply(); return s }
        val since = dev.getLong("apps_starts_at", 0L)
        var newest = since
        val cards = HashMap(s.cards)
        var lidas = 0; var tempos = 0; var comFicha = 0
        for (line in out.lineSequence()) {
            if (line.isBlank()) continue
            lidas++
            val at = (line.trim().substringBefore(' ').toDoubleOrNull() ?: continue).times(1000).toLong()
            val (pkg, ms) = AppProfile.parseLaunchTime(line) ?: continue
            tempos++
            if (at <= since) continue
            cards[pkg]?.let { cards[pkg] = AppProfile.noteStart(it, ms); comFicha++ }
            if (at > newest) newest = at
        }
        dev.edit().putString("apps_starts_last", "$lidas linhas, $tempos tempos, $comFicha de apps com ficha").apply {
            if (newest > since) putLong("apps_starts_at", newest)
        }.apply()
        return s.copy(cards = cards)
    }

    private fun tips(ctx: Context, s: AppProfile.State, now: Long): List<AppProfile.Tip> {
        val onDemand = runCatching { OnDemandTv.entries(ctx).map { it.pkg }.toSet() }.getOrDefault(emptySet())
        return AppProfile.advise(s.cards.values, now, onDemand) { pkg -> runCatching { OnDemandTv.eligible(ctx, pkg) }.getOrDefault(false) }
    }

    private fun shadowTips(ctx: Context, profile: String, s: AppProfile.State, now: Long) {
        val t = tips(ctx, s, now)
        if (t.isEmpty()) return
        val why = t.joinToString("; ") { "${it.advice.name.lowercase()} ${it.pkg}: ${it.why}" }
        ObserverTv.logShadow(ctx, "app_tip", why)
        runCatching { Diario.anotar(ctx, "tv", "Sofy: ficha dos apps", why) }
        Log.i(TAG, "dicas (sombra, perfil ${profile.ifEmpty { "-" }}): $why")
    }

    /** Para o MCP `observer`: a ficha de cada app do perfil ativo e as dicas de agora. Só leitura. */
    fun report(ctx: Context): JSONObject {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        val profile = runCatching { Perfil.chaveDados(app) }.getOrDefault("")
        val s = synchronized(this) { load(app)[profile] } ?: AppProfile.State(emptyMap())
        val cards = JSONArray()
        for (c in s.cards.values.map { AppProfile.decay(it, now) }.sortedByDescending { it.totalOpens }) {
            val h = c.opens.indices.maxByOrNull { c.opens[it] }
            cards.put(JSONObject().put("pkg", c.pkg).put("opens", Math.round(c.totalOpens * 10) / 10.0)
                .put("top_hour", h ?: JSONObject.NULL).put("front_min", Math.round(c.frontMs / 60_000))
                .put("last_open", c.lastOpen).put("start_ms_median", if (c.startsMs.isEmpty()) JSONObject.NULL else AppProfile.median(c.startsMs))
                .put("start_ms_before", if (c.startsBefore > 0) c.startsBefore else JSONObject.NULL)
                .put("mem_front_mb", if (c.memSamples > 0) Math.round(c.memFrontMb) else JSONObject.NULL)
                .put("compiled_at", if (c.compiledAt > 0) c.compiledAt else JSONObject.NULL))
        }
        val tips = JSONArray()
        for (t in tips(app, s, now)) tips.put(JSONObject().put("pkg", t.pkg).put("advice", t.advice.name).put("hour", t.hour ?: JSONObject.NULL).put("why", t.why))
        val dev = app.getSharedPreferences("observer", Context.MODE_PRIVATE)
        return JSONObject().put("profile_shared", profile.isEmpty()).put("cards", cards).put("tips", tips)
            .put("mode", "acting").put("starts_tries", dev.getInt("apps_starts_tries", 0)).put("starts_last", dev.getString("apps_starts_last", "").orEmpty())
    }
}
