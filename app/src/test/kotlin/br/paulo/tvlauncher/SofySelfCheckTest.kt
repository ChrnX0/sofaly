package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofySelfCheck
import br.paulo.tvlauncher.observer.SofySelfCheck.Defect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SofySelfCheckTest {
    private val T = 1_800_000_000_000L
    private val H = 3_600_000L
    private val MIN = 60_000L

    /** Tudo em dia, de pé há 3 dias. */
    private fun healthy() = SofySelfCheck.Pulse(now = T, upSince = T - 72 * H, interactive = true, lastReadingAt = T - 4 * MIN,
        appsEventsAt = T - 10 * MIN, startsTries = 10, startsAt = T - 3 * H, startsLastResult = "", lastNightAt = T - 18 * H,
        lastEventAt = T - 2 * H)

    private fun defects(p: SofySelfCheck.Pulse) = SofySelfCheck.check(p).map { it.defect }

    @Test fun healthyHasNoDefect() = assertEquals(emptyList<Defect>(), defects(healthy()))

    @Test fun launchTimesThatNeverArriveAreADefect() {
        val f = SofySelfCheck.check(healthy().copy(startsAt = 0L, startsLastResult = "40 linhas, 12 tempos, 0 de apps com ficha"))
        assertEquals(listOf(Defect.LAUNCH_TIMES_MISSING), f.map { it.defect })
        assertTrue(f[0].why.contains("10 vezes") && f[0].why.contains("0 de apps com ficha"))
        // Poucas tentativas ainda: não acusa.
        assertEquals(emptyList<Defect>(), defects(healthy().copy(startsAt = 0L, startsTries = 3)))
    }

    @Test fun readingsOnlyMatterWithTheScreenOnAndAfterWarmUp() {
        val late = healthy().copy(lastReadingAt = T - 2 * H)
        assertEquals(listOf(Defect.NO_READINGS), defects(late))
        assertEquals(emptyList<Defect>(), defects(late.copy(interactive = false)))
        assertEquals(emptyList<Defect>(), defects(late.copy(upSince = T - 10 * MIN, lastReadingAt = 0L)))
    }

    @Test fun nightAppProfileAndEventsLate() {
        val p = healthy().copy(lastNightAt = T - 40 * H, appsEventsAt = T - 5 * H, lastEventAt = 0L)
        assertEquals(setOf(Defect.NIGHT_MISSING, Defect.APP_PROFILE_STALE, Defect.EVENTS_SILENT), defects(p).toSet())
        // Acabou de abrir: nada disso é cobrado ainda.
        assertEquals(emptyList<Defect>(), defects(p.copy(upSince = T - H)))
    }
}
