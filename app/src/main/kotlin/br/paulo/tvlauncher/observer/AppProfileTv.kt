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
 *  3. uma vez por dia, escreve as dicas no registro do Observador ("app_tip") e no Diário. SOMBRA: ninguém age ainda.
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
        all[profile] = s
        ObserverTv.learning(app).edit().putString("apps", AppProfile.toJson(all).toString()).apply()
        dev.edit().putLong("apps_events_at", now).apply()
        if (now - dev.getLong("apps_tips_at", 0L) >= TIPS_GAP_MS) {
            dev.edit().putLong("apps_tips_at", now).apply()
            shadowTips(app, profile, s, now)
        }
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
        dev.edit().putLong("apps_starts_tried", now).apply()
        val out = runCatching {
            GuardiaoShizuku.comando(ctx, "logcat -d -b events -v epoch -s wm_activity_launch_time am_activity_launch_time", 15_000)
        }.getOrNull() ?: return s
        val since = dev.getLong("apps_starts_at", 0L)
        var newest = since
        val cards = HashMap(s.cards)
        for (line in out.lineSequence()) {
            val at = (line.trim().substringBefore(' ').toDoubleOrNull() ?: continue).times(1000).toLong()
            if (at <= since) continue
            val (pkg, ms) = AppProfile.parseLaunchTime(line) ?: continue
            cards[pkg]?.let { cards[pkg] = AppProfile.noteStart(it, ms) }
            if (at > newest) newest = at
        }
        if (newest > since) dev.edit().putLong("apps_starts_at", newest).apply()
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
                .put("last_open", c.lastOpen).put("start_ms_median", if (c.startsMs.isEmpty()) JSONObject.NULL else AppProfile.median(c.startsMs)))
        }
        val tips = JSONArray()
        for (t in tips(app, s, now)) tips.put(JSONObject().put("pkg", t.pkg).put("advice", t.advice.name).put("hour", t.hour ?: JSONObject.NULL).put("why", t.why))
        return JSONObject().put("profile_shared", profile.isEmpty()).put("cards", cards).put("tips", tips).put("mode", "shadow")
    }
}
