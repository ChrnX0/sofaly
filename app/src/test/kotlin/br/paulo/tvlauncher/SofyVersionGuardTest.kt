package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.AppProfile
import br.paulo.tvlauncher.observer.SofyBus
import br.paulo.tvlauncher.observer.SofyVersionGuard
import br.paulo.tvlauncher.observer.SofyVersionGuard.Stats
import br.paulo.tvlauncher.observer.SofyVersionGuard.What
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guarda de cada versão (ideia 5) e pré-HOME (ideia 7) da Sofy. */
class SofyVersionGuardTest {
    private val T = 1_800_000_000_000L
    private val MIN = 60_000L

    @Test fun slowerHomeNeedsFactorMinimumAndOpens() {
        val prev = Stats(1, 48.0, 0, 0, 1000, 10)
        assertEquals(listOf(What.HOME_SLOWER), SofyVersionGuard.compare(prev, Stats(2, 2.0, 0, 0, 1400, 6)).map { it.what })
        assertTrue(SofyVersionGuard.compare(prev, Stats(2, 2.0, 0, 0, 1250, 6)).isEmpty())   // 25%: não
        assertTrue(SofyVersionGuard.compare(Stats(1, 48.0, 0, 0, 500, 10), Stats(2, 2.0, 0, 0, 700, 6)).isEmpty())   // 40% mas só 200 ms
        assertTrue(SofyVersionGuard.compare(prev, Stats(2, 2.0, 0, 0, 3000, 4)).isEmpty())   // 4 aberturas
    }

    @Test fun crashesAndDropsPerDayNeedADayOfEachSide() {
        val prev = Stats(1, 48.0, 0, 2, null, 0)
        val w = SofyVersionGuard.compare(prev, Stats(2, 24.0, 2, 5, null, 0)).map { it.what }
        assertEquals(listOf(What.MORE_CRASHES, What.MORE_DROPS), w)
        assertTrue(SofyVersionGuard.compare(prev, Stats(2, 12.0, 5, 9, null, 0)).isEmpty())       // meio dia: cedo
        assertTrue(SofyVersionGuard.compare(prev.copy(drops = 6), Stats(2, 24.0, 0, 5, null, 0)).isEmpty())   // 3/dia antes: 5 não é pior o bastante
        assertTrue(SofyVersionGuard.compare(prev, prev).isEmpty())   // mesma versão
    }

    @Test fun countsComeFromTheBus() {
        val ev = listOf(SofyBus.Event(T, SofyBus.Source.CRASH, "launcher_crash", false), SofyBus.Event(T + 1, SofyBus.Source.NETWORK, "drop_tv_lost_wifi"),
            SofyBus.Event(T + 2, SofyBus.Source.NETWORK, "check"), SofyBus.Event(T - 10, SofyBus.Source.CRASH, "launcher_crash"))
        assertEquals(1 to 1, SofyVersionGuard.count(ev, T, T + 10))
    }

    @Test fun preHomeFiresNearTheUsualReturnOnly() {
        val c = AppProfile.Card("yt", T).copy(opens = List(24) { if (it == 20) 4.0 else 0.0 }, frontMs = 4 * 40 * MIN.toDouble())   // 40 min por sessão
        val s = AppProfile.State(mapOf("yt" to c), front = "yt", frontSince = T)
        assertEquals(40 * MIN, AppProfile.avgSessionMs(c))
        assertFalse(AppProfile.returnSoon(s, T + 20 * MIN))
        assertTrue(AppProfile.returnSoon(s, T + 33 * MIN))
        assertFalse(AppProfile.returnSoon(s, T + 90 * MIN))   // passou muito: já não é a sessão de costume
        val curta = s.copy(cards = mapOf("yt" to c.copy(frontMs = 4 * 5 * MIN.toDouble())))
        assertFalse(AppProfile.returnSoon(curta, T + 4 * MIN))   // sessão de 5 min: não vale
        assertFalse(AppProfile.returnSoon(s.copy(front = null), T + 33 * MIN))
    }
}
