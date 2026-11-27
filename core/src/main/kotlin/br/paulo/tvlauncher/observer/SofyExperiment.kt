package br.paulo.tvlauncher.observer

import org.json.JSONArray
import org.json.JSONObject

/**
 * Experimento noturno (24/11/2026, ideia 36). Por quê: a Sofy faz coisas de madrugada "porque deve ajudar", sem prova.
 * Para quê: ela testa uma mudança numa noite, mede no dia seguinte e fica só com o que provou.
 * Como: noites alternadas com e sem a ação ([armFor]); cada tempo de abrir a HOME do dia seguinte entra na noite que o
 * precedeu ([addSample]). Com [MIN_NIGHTS] noites de cada lado (cada uma com [MIN_SAMPLES] medidas), [decide] compara a
 * mediana das medianas: a ação fica se deixou a HOME pelo menos [MIN_GAIN] mais rápida; senão, sai (menos coisa
 * rodando = menos desgaste). Decidido, vale por [RECHECK_MS] e o experimento recomeça (apps e Android mudam).
 * Primeiro experimento: "night_kill_idle" (fechar de madrugada os apps parados em segundo plano). Pura: SofyExperimentTest.
 */
object SofyExperiment {
    const val MIN_NIGHTS = 4
    const val MIN_SAMPLES = 3
    const val MIN_GAIN = 0.10
    const val SAMPLE_WINDOW_MS = 20 * 3_600_000L
    const val RECHECK_MS = 30 * 86_400_000L
    private const val MAX_NIGHTS = 20

    data class Night(val at: Long, val on: Boolean, val samples: List<Int> = emptyList())
    data class State(val name: String, val nights: List<Night> = emptyList(), val keepOn: Boolean? = null, val decidedAt: Long = 0L, val why: String = "")

    /** Esta noite roda a ação? Decidido e dentro do prazo: a decisão. Senão alterna com a noite anterior (começa sem). */
    fun armFor(s: State, now: Long): Boolean {
        if (s.keepOn != null && now - s.decidedAt < RECHECK_MS) return s.keepOn
        return s.nights.lastOrNull()?.on?.not() ?: false
    }

    /** A noite rodou (com ou sem a ação). Decisão vencida: recomeça o experimento. */
    fun startNight(s: State, now: Long, on: Boolean): State {
        val base = if (s.keepOn != null && now - s.decidedAt >= RECHECK_MS) State(s.name) else s
        if (base.keepOn != null) return base   // decidido: não mede mais
        return base.copy(nights = (base.nights + Night(now, on)).takeLast(MAX_NIGHTS))
    }

    fun addSample(s: State, now: Long, ms: Int): State {
        if (s.keepOn != null) return s
        val last = s.nights.lastOrNull() ?: return s
        if (now - last.at !in 0..SAMPLE_WINDOW_MS || ms <= 0) return s
        return s.copy(nights = s.nights.dropLast(1) + last.copy(samples = (last.samples + ms).takeLast(30)))
    }

    private fun median(l: List<Int>) = l.sorted()[l.size / 2]

    fun decide(s: State, now: Long): State {
        if (s.keepOn != null) return s
        val good = s.nights.filter { it.samples.size >= MIN_SAMPLES }
        val on = good.filter { it.on }.map { median(it.samples) }
        val off = good.filter { !it.on }.map { median(it.samples) }
        if (on.size < MIN_NIGHTS || off.size < MIN_NIGHTS) return s
        val mOn = median(on); val mOff = median(off)
        val keep = mOn <= mOff * (1 - MIN_GAIN)
        val why = "HOME abriu em $mOn ms depois das noites com a ação e $mOff ms nas noites sem (${on.size}+${off.size} noites): " +
            if (keep) "a ação fica" else "não ajudou, a ação sai"
        return s.copy(keepOn = keep, decidedAt = now, why = why)
    }

    fun toJson(s: State): JSONObject = JSONObject().put("name", s.name).put("why", s.why).put("decided_at", s.decidedAt)
        .put("keep_on", s.keepOn ?: JSONObject.NULL).put("nights", JSONArray().apply {
            s.nights.forEach { n -> put(JSONObject().put("at", n.at).put("on", n.on).put("ms", JSONArray(n.samples))) }
        })

    fun fromJson(o: JSONObject?, name: String): State {
        if (o == null || o.optString("name") != name) return State(name)
        val arr = o.optJSONArray("nights") ?: JSONArray()
        val nights = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { n ->
            val ms = n.optJSONArray("ms") ?: JSONArray()
            Night(n.optLong("at"), n.optBoolean("on"), (0 until ms.length()).map { ms.optInt(it) })
        } }
        return State(name, nights, if (o.isNull("keep_on") || !o.has("keep_on")) null else o.optBoolean("keep_on"), o.optLong("decided_at"), o.optString("why"))
    }
}
