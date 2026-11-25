package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.DropCause
import br.paulo.tvlauncher.observer.DropCause.Cause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DropCauseTest {
    private val T = 1_800_000_000_000L
    private val MIN = 60_000L

    /** Uma queda de [n] amostras de 30 s, com o Wi-Fi conectado nas que [onWifi] diz; devolve o episódio fechado. */
    private fun drop(n: Int, onWifi: (Int) -> Boolean): Pair<DropCause.Episode, Long> {
        var open: DropCause.Episode? = null
        for (i in 0 until n) open = DropCause.step(open, false, if (onWifi(i)) -60 else 0, T + i * 30_000L).first
        val end = T + n * 30_000L
        val (still, closed) = DropCause.step(open, true, -60, end)
        assertNull(still)
        return closed!! to end
    }

    @Test fun noDropNoEpisode() = assertEquals(null to null, DropCause.step(null, true, -60, T))

    @Test fun shortDropIsIgnored() {
        val (e, end) = drop(3) { false }
        assertNull(DropCause.classify(e, end, emptyList()))
    }

    @Test fun tvOffWifiAllTheTime() {
        val (e, end) = drop(20) { false }
        val r = DropCause.classify(e, end, listOf(90 * 86_400_000L))!!
        assertEquals(Cause.TV_LOST_WIFI, r.cause); assertEquals(10L, r.minutes)
        assertEquals(true, r.why.contains("o roteador não reiniciou"))
    }

    @Test fun onWifiButNoInternetIsOutside() {
        val (e, end) = drop(20) { true }
        assertEquals(Cause.INTERNET_OUTSIDE, DropCause.classify(e, end, emptyList())!!.cause)
    }

    @Test fun routerUptimeShorterThanTheDropMeansItRestarted() {
        val (e, end) = drop(20) { false }
        // Perguntado 1 min depois do fim; o ponto de acesso ligou há 8 min (no meio da queda de 10 min).
        val r = DropCause.classify(e, end, listOf(8 * MIN, 40 * 86_400_000L), askedAt = end + MIN)!!
        assertEquals(Cause.ROUTER_RESTARTED, r.cause)
        // Ligado há 3 h: não foi ele.
        assertEquals(Cause.TV_LOST_WIFI, DropCause.classify(e, end, listOf(180 * MIN), askedAt = end + MIN)!!.cause)
    }

    @Test fun mixed() {
        val (e, end) = drop(20) { it % 2 == 0 }
        assertEquals(Cause.MIXED, DropCause.classify(e, end, emptyList())!!.cause)
    }
}
