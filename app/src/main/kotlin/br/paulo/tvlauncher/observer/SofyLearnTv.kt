package br.paulo.tvlauncher.observer

import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.family.Diario
import org.json.JSONArray
import org.json.JSONObject

/**
 * A parte do Android do que a Sofy aprende sobre ela mesma (24/11/2026, ideias 34 a 37): o placar ([SofyScore]), os
 * limites que se ajustam ([SofyLimits]) e o experimento noturno ([SofyExperiment]).
 *
 * Onde mora: placar e limites em "sofy_learning" (viajam no backup: são da casa); o experimento em "observer" (mede ESTA
 * TV: a HOME de outro aparelho abre em outro tempo). Tudo aparece no MCP `observer` (chave "learning").
 */
object SofyLearnTv {
    private const val TAG = "TvLauncher.SofyLearn"
    const val EXPERIMENT = "night_kill_idle"

    // ---------------------------------------------------------------- placar (ideia 34)

    @Synchronized fun record(ctx: Context, action: SofyScore.Action, outcome: SofyScore.Outcome, picoAgreed: Boolean? = null, detail: String = "") {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        val l = ObserverTv.learning(app)
        l.edit().putString("score", SofyScore.add(l.getString("score", null), SofyScore.Record(now, action, outcome, picoAgreed, detail), now)).apply()
        SofyBusTv.emit(app, SofyBus.Source.SOFY, "score_${action.name.lowercase()}", ok = outcome == SofyScore.Outcome.HIT)
        runCatching { adjustLimits(app, now) }.onFailure { Log.w(TAG, "limites: $it") }
    }

    fun records(ctx: Context): List<SofyScore.Record> = SofyScore.records(ObserverTv.learning(ctx).getString("score", null))

    /** Ideia 37: o pico decide os avisos pequenos? (critério escrito em [SofyScore.picoReady]) */
    fun picoReady(ctx: Context): Boolean = runCatching { SofyScore.picoReady(records(ctx), System.currentTimeMillis()) }.getOrDefault(false)

    // ---------------------------------------------------------------- limites (ideia 35)

    private fun limitsJson(ctx: Context): JSONObject = ObserverTv.learning(ctx).getString("limits", null)
        ?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    /** O valor de agora do limite (o de fábrica enquanto ela não mudou). */
    fun limit(ctx: Context, l: SofyLimits.Limit): Double =
        limitsJson(ctx).optJSONObject(l.name)?.optDouble("value", l.default)?.takeIf { !it.isNaN() }?.coerceIn(l.min, l.max) ?: l.default

    @Synchronized private fun adjustLimits(ctx: Context, now: Long) {
        val o = limitsJson(ctx)
        val r = records(ctx)
        var mudou = false
        for (l in SofyLimits.ALL) {
            val cur = o.optJSONObject(l.name)
            val value = cur?.optDouble("value", l.default)?.takeIf { !it.isNaN() } ?: l.default
            val c = SofyLimits.adjust(l, value, r, cur?.optLong("changed_at") ?: 0L, now) ?: continue
            o.put(l.name, JSONObject().put("value", c.to).put("changed_at", now).put("before", c.from).put("why", c.why))
            val hist = (o.optJSONArray("history") ?: JSONArray()).apply {
                put(JSONObject().put("at", now).put("name", l.name).put("from", c.from).put("to", c.to).put("why", c.why))
            }
            o.put("history", JSONArray().apply { for (i in maxOf(0, hist.length() - 20) until hist.length()) put(hist.get(i)) })
            mudou = true
            ObserverTv.logShadow(ctx, "limit_changed", "${l.name}: ${SofyLimits.fmt(c.from)} → ${SofyLimits.fmt(c.to)} (${c.why})")
            runCatching { Diario.anotar(ctx, "tv", "Sofy mudou um limite", "${l.name}: ${SofyLimits.fmt(c.from)} → ${SofyLimits.fmt(c.to)}. ${c.why}") }
            SofyBusTv.emit(ctx, SofyBus.Source.SOFY, "limit_changed", detail = l.name)
        }
        if (mudou) ObserverTv.learning(ctx).edit().putString("limits", o.toString()).apply()
    }

    /** Desfazer: todos os limites voltam ao de fábrica (adb `EXTRAS --es o sofy_limits_reset`). O histórico fica. */
    @Synchronized fun resetLimits(ctx: Context): String {
        val o = limitsJson(ctx)
        val hist = o.optJSONArray("history") ?: JSONArray()
        ObserverTv.learning(ctx).edit().putString("limits", JSONObject().put("history", hist).toString()).apply()
        ObserverTv.logShadow(ctx, "limit_changed", "todos os limites voltaram ao de fábrica (a pedido)")
        return "limites de fábrica: " + SofyLimits.ALL.joinToString { "${it.name}=${SofyLimits.fmt(it.default)}" }
    }

    // ---------------------------------------------------------------- experimento noturno (ideia 36)

    private fun experiment(ctx: Context): SofyExperiment.State = SofyExperiment.fromJson(
        ctx.getSharedPreferences(ObserverTv.PREFS, Context.MODE_PRIVATE).getString("experiment", null)?.let { runCatching { JSONObject(it) }.getOrNull() }, EXPERIMENT)

    private fun save(ctx: Context, s: SofyExperiment.State) =
        ctx.getSharedPreferences(ObserverTv.PREFS, Context.MODE_PRIVATE).edit().putString("experiment", SofyExperiment.toJson(s).toString()).apply()

    /** De madrugada (ManutencaoNoturna): roda ou não a ação desta noite. [act] faz a ação e devolve o que fez. */
    @Synchronized fun night(ctx: Context, act: () -> String): String {
        val now = System.currentTimeMillis()
        val s = experiment(ctx)
        val on = SofyExperiment.armFor(s, now)
        val n = SofyExperiment.startNight(s, now, on)
        save(ctx, n)
        val feito = if (on) runCatching { act() }.getOrElse { it.javaClass.simpleName } else "noite sem a ação"
        SofyBusTv.emit(ctx, SofyBus.Source.SOFY, "experiment_night", detail = if (on) "on" else "off")
        return "experimento $EXPERIMENT: $feito${if (n.keepOn != null) " (decidido)" else ""}"
    }

    /** A HOME abriu em [ms] (DesempenhoTv). Entra na noite que passou; com noites suficientes, decide. */
    @Synchronized fun homeOpened(ctx: Context, ms: Int) {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        val s0 = experiment(app)
        if (s0.keepOn != null) return
        val s = SofyExperiment.decide(SofyExperiment.addSample(s0, now, ms), now)
        if (s == s0) return
        save(app, s)
        val keep = s.keepOn ?: return
        record(app, SofyScore.Action.EXPERIMENT, if (keep) SofyScore.Outcome.HIT else SofyScore.Outcome.MISS, detail = EXPERIMENT)
        ObserverTv.logShadow(app, "experiment_decided", s.why)
        runCatching { Diario.anotar(app, "tv", "Sofy terminou um experimento", s.why) }
    }

    // ---------------------------------------------------------------- MCP

    fun json(ctx: Context): JSONObject {
        val now = System.currentTimeMillis()
        val r = records(ctx)
        val tally = JSONObject()
        for ((a, t) in SofyScore.tally(r, now - 30 * 86_400_000L)) tally.put(a.name.lowercase(), JSONObject().put("hits", t.hits)
            .put("misses", t.misses).put("undone", t.undone).put("rate", t.rate?.let { Math.round(it * 100) / 100.0 } ?: JSONObject.NULL))
        val (n, rate) = SofyScore.picoAgreement(r, now)
        val limits = JSONObject()
        val stored = limitsJson(ctx)
        for (l in SofyLimits.ALL) limits.put(l.name, JSONObject().put("value", limit(ctx, l)).put("default", l.default)
            .put("range", "${SofyLimits.fmt(l.min)}..${SofyLimits.fmt(l.max)}").apply { stored.optJSONObject(l.name)?.optString("why")?.let { put("why", it) } })
        return JSONObject().put("score_30d", tally)
            .put("pico", JSONObject().put("notes_14d", n).put("agreement", rate?.let { Math.round(it * 100) / 100.0 } ?: JSONObject.NULL)
                .put("deciding", SofyScore.picoReady(r, now)).put("rule", "${SofyScore.PICO_MIN}+ notas e ${Math.round(SofyScore.PICO_RATE * 100)}% em 14 dias"))
            .put("limits", limits).put("limits_history", stored.optJSONArray("history") ?: JSONArray())
            .put("experiment", SofyExperiment.toJson(experiment(ctx)))
    }
}
