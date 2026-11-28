package br.paulo.tvlauncher.observer

/**
 * Limites que se ajustam (24/11/2026, ideia 35). Por quê: os limites da ficha dos apps (2,5 s para "abre devagar", 35%
 * para "hora firme") foram escolhidos por chute, para errar do lado de não mexer; cada casa é uma. Para quê: com o
 * placar ([SofyScore]) ela move o próprio limite, um degrau por vez e só dentro de uma faixa segura, e anota cada mudança
 * com o porquê e o valor de antes (desfazer = `EXTRAS --es o sofy_limits_reset`).
 * Como: [adjust] olha só os desfechos medidos DESDE a última mudança daquele limite (o efeito do valor atual). Pelo
 * menos [MIN_OUTCOMES]; acerto de [LOOSEN_AT] ou mais = afrouxa (ela age em mais casos); [TIGHTEN_AT] ou menos = aperta.
 * Entre uma mudança e outra, pelo menos [GAP_MS]. Pura: SofyLimitsTest.
 */
object SofyLimits {
    const val MIN_OUTCOMES = 5
    const val LOOSEN_AT = 0.8
    const val TIGHTEN_AT = 0.4
    const val GAP_MS = 14 * 86_400_000L

    /** [looserIsDown] = baixar o número faz ela agir em mais casos. */
    data class Limit(val name: String, val default: Double, val min: Double, val max: Double, val step: Double,
                     val action: SofyScore.Action, val looserIsDown: Boolean)

    val RECOMPILE_SLOW_MS = Limit("recompile_slow_ms", AppProfile.RECOMPILE_SLOW_MS.toDouble(), 1500.0, 4000.0, 250.0, SofyScore.Action.RECOMPILE, true)
    val PREWARM_SHARE = Limit("prewarm_share", AppProfile.PREWARM_SHARE, 0.25, 0.6, 0.05, SofyScore.Action.PREWARM, true)
    val ALL = listOf(RECOMPILE_SLOW_MS, PREWARM_SHARE)

    data class Change(val at: Long, val name: String, val from: Double, val to: Double, val why: String)

    fun adjust(l: Limit, current: Double, records: List<SofyScore.Record>, lastChangeAt: Long, now: Long): Change? {
        if (now - lastChangeAt < GAP_MS) return null
        val t = SofyScore.tally(records.filter { it.action == l.action }, lastChangeAt)[l.action] ?: return null
        if (t.total < MIN_OUTCOMES) return null
        val rate = t.rate ?: return null
        val looser = when {
            rate >= LOOSEN_AT -> true
            rate <= TIGHTEN_AT -> false
            else -> return null
        }
        val dir = if (looser == l.looserIsDown) -1 else 1
        val to = (current + dir * l.step).coerceIn(l.min, l.max).let { Math.round(it * 1000) / 1000.0 }
        if (to == current) return null
        val pct = Math.round(rate * 100)
        return Change(now, l.name, current, to, "${t.hits} de ${t.total} acertos ($pct%) com ${fmt(current)}: " +
            if (looser) "ela age em mais casos" else "ela fica mais cuidadosa")
    }

    fun fmt(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
}
