package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyExperiment
import br.paulo.tvlauncher.observer.SofyLimits
import br.paulo.tvlauncher.observer.SofyScore
import br.paulo.tvlauncher.observer.SofyScore.Action
import br.paulo.tvlauncher.observer.SofyScore.Outcome
import br.paulo.tvlauncher.observer.SofyScore.Record
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Placar (ideia 34), limites que se ajustam (35), experimento noturno (36) e o pico fora da sombra (37). */
class SofyLearnTest {
    private val T = 1_800_000_000_000L
    private val H = 3_600_000L
    private val DAY = 86_400_000L
    private val MIN = 60_000L

    // ---------------------------------------------------------------- placar

    @Test fun recordsRoundTripAndOldOnesLeave() {
        var txt: String? = null
        txt = SofyScore.add(txt, Record(T, Action.TELL, Outcome.HIT, true, "a\tb"), T)
        txt = SofyScore.add(txt, Record(T + DAY, Action.RECOMPILE, Outcome.UNDONE), T + DAY)
        val r = SofyScore.records(txt)
        assertEquals(2, r.size)
        assertEquals(true, r[0].picoAgreed); assertEquals("a b", r[0].detail)
        assertNull(r[1].picoAgreed)
        val later = SofyScore.add(txt, Record(T + 62 * DAY, Action.PREWARM, Outcome.MISS), T + 62 * DAY)
        assertEquals(listOf(Action.PREWARM), SofyScore.records(later).map { it.action })
    }

    @Test fun tallyCountsUndoneAsAMiss() {
        val r = listOf(Record(T, Action.RECOMPILE, Outcome.HIT), Record(T, Action.RECOMPILE, Outcome.UNDONE), Record(T, Action.RECOMPILE, Outcome.HIT),
            Record(T - DAY, Action.RECOMPILE, Outcome.HIT))
        val t = SofyScore.tally(r, T).getValue(Action.RECOMPILE)
        assertEquals(3, t.total); assertEquals(2.0 / 3, t.rate!!, 1e-9)
    }

    @Test fun tellLabelOnlyWhenClear() {
        assertEquals(true, SofyScore.tellLabel(30 * MIN, true))
        assertEquals(false, SofyScore.tellLabel(5 * MIN, false))
        assertNull(SofyScore.tellLabel(5 * MIN, true))     // ainda aberto: não sabe
        assertNull(SofyScore.tellLabel(20 * MIN, false))   // no meio: não sabe
    }

    @Test fun picoLeavesTheShadowOnlyWithTheWrittenRule() {
        fun tells(n: Int, right: Int, at: Long = T) = (0 until n).map { Record(at + it, Action.TELL, Outcome.HIT, it < right) }
        assertFalse(SofyScore.picoReady(tells(19, 19), T + DAY))       // poucas notas
        assertTrue(SofyScore.picoReady(tells(20, 16), T + DAY))        // 80%
        assertFalse(SofyScore.picoReady(tells(20, 15), T + DAY))       // 75%
        assertFalse(SofyScore.picoReady(tells(20, 20), T + 15 * DAY))  // velhas demais: volta para a sombra
    }

    // ---------------------------------------------------------------- limites

    private fun outcomes(a: Action, hits: Int, misses: Int, at: Long) =
        (0 until hits).map { Record(at + it, a, Outcome.HIT) } + (0 until misses).map { Record(at + 100 + it, a, Outcome.MISS) }

    @Test fun limitLoosensOneStepWhenSheKeepsHitting() {
        val l = SofyLimits.RECOMPILE_SLOW_MS
        val c = SofyLimits.adjust(l, 2500.0, outcomes(Action.RECOMPILE, 4, 1, T), 0L, T + DAY)!!
        assertEquals(2250.0, c.to, 1e-9)   // mais baixo = recompila mais apps
        assertTrue(c.why.contains("4 de 5"))
    }

    @Test fun limitTightensWhenSheMissesAndStaysInTheSafeRange() {
        val l = SofyLimits.PREWARM_SHARE
        assertEquals(0.4, SofyLimits.adjust(l, 0.35, outcomes(Action.PREWARM, 1, 4, T), 0L, T + DAY)!!.to, 1e-9)
        assertNull(SofyLimits.adjust(l, 0.6, outcomes(Action.PREWARM, 0, 6, T), 0L, T + DAY))   // já no teto
    }

    @Test fun limitWaitsForEvidenceAndForTheGap() {
        val l = SofyLimits.RECOMPILE_SLOW_MS
        assertNull(SofyLimits.adjust(l, 2500.0, outcomes(Action.RECOMPILE, 4, 0, T), 0L, T + DAY))               // 4 < 5
        assertNull(SofyLimits.adjust(l, 2500.0, outcomes(Action.RECOMPILE, 3, 3, T), 0L, T + DAY))               // 50%: fica
        assertNull(SofyLimits.adjust(l, 2500.0, outcomes(Action.RECOMPILE, 9, 0, T), T - DAY, T + DAY))          // mudou há 2 dias
        assertNull(SofyLimits.adjust(l, 2500.0, outcomes(Action.RECOMPILE, 9, 0, T - 20 * DAY), T - 15 * DAY, T)) // desfechos de antes da mudança não contam
    }

    // ---------------------------------------------------------------- experimento

    private fun night(s: SofyExperiment.State, at: Long, vararg ms: Int): SofyExperiment.State {
        val on = SofyExperiment.armFor(s, at)
        var x = SofyExperiment.startNight(s, at, on)
        for ((i, m) in ms.withIndex()) x = SofyExperiment.addSample(x, at + (i + 1) * H, m)
        return SofyExperiment.decide(x, at + 19 * H)
    }

    @Test fun nightsAlternateAndTheActionStaysWhenItHelps() {
        var s = SofyExperiment.State("night_kill_idle")
        assertFalse(SofyExperiment.armFor(s, T))   // começa sem
        for (n in 0 until 8) s = if (n % 2 == 0) night(s, T + n * DAY, 1000, 1100, 1050) else night(s, T + n * DAY, 800, 850, 820)
        assertEquals(true, s.keepOn)
        assertTrue(s.why.contains("820 ms") && s.why.contains("1050 ms"))
        assertTrue(SofyExperiment.armFor(s, T + 9 * DAY))
        assertEquals(s, SofyExperiment.addSample(s, T + 9 * DAY, 500))   // decidido: não mede mais
    }

    @Test fun actionLeavesWhenTheGainIsSmall() {
        var s = SofyExperiment.State("night_kill_idle")
        for (n in 0 until 8) s = night(s, T + n * DAY, *(if (n % 2 == 0) intArrayOf(1000, 1000, 1000) else intArrayOf(950, 950, 950)))
        assertEquals(false, s.keepOn)
        assertFalse(SofyExperiment.armFor(s, T + 9 * DAY))
    }

    @Test fun samplesOutsideTheWindowAndThinNightsDoNotCount() {
        var s = SofyExperiment.startNight(SofyExperiment.State("x"), T, false)
        s = SofyExperiment.addSample(s, T + 21 * H, 900)
        assertTrue(s.nights.last().samples.isEmpty())
        for (n in 1 until 8) s = night(s, T + n * DAY, 900, 900)   // só 2 medidas por noite
        assertNull(s.keepOn)
    }

    @Test fun decisionExpiresAndJsonRoundTrips() {
        val s = SofyExperiment.State("night_kill_idle", listOf(SofyExperiment.Night(T, true, listOf(1, 2))), true, T, "porque")
        val back = SofyExperiment.fromJson(JSONObject(SofyExperiment.toJson(s).toString()), "night_kill_idle")
        assertEquals(s, back)
        assertEquals(SofyExperiment.State("other"), SofyExperiment.fromJson(SofyExperiment.toJson(s), "other"))
        val again = SofyExperiment.startNight(s, T + 31 * DAY, false)
        assertNull(again.keepOn); assertEquals(1, again.nights.size)
    }
}
