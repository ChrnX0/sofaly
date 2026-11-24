package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyBus
import br.paulo.tvlauncher.observer.SofyHouse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SofyHouseTest {
    private val d = SofyHouse.DAY_MS
    private val now = 1_800_000_000_000L
    private fun ev(ago: Long, s: SofyBus.Source, k: String, ok: Boolean = true) = SofyBus.Event(now - ago, s, k, ok)

    @Test fun wifiPedeAjuda() {
        val tres = listOf(ev(d, SofyBus.Source.NETWORK, "drop_internet"), ev(2 * d, SofyBus.Source.NETWORK, "drop_wifi"), ev(3 * d, SofyBus.Source.NETWORK, "drop_internet"))
        assertEquals(3, SofyHouse.wifiNeedsHelp(tres, 0, now))
        assertNull(SofyHouse.wifiNeedsHelp(tres.take(2), 0, now))
        assertEquals(2, SofyHouse.wifiNeedsHelp(tres.take(2), 3, now))   // sinal fraco 3 vezes basta
        assertNull(SofyHouse.wifiNeedsHelp(tres.map { it.copy(at = it.at - 8 * d) }, 0, now))   // semana passada não conta
    }

    @Test fun jogoNaNuvem() {
        assertTrue(SofyHouse.gameLag(150, 30))
        assertFalse(SofyHouse.gameLag(70, 20))    // abaixo de 80 ms não atrapalha
        assertFalse(SofyHouse.gameLag(100, 60))   // casa que já é assim: 100 < 120
        assertFalse(SofyHouse.gameLag(null, 30)); assertFalse(SofyHouse.gameLag(200, null))
    }

    @Test fun lgEHomeAssistant() {
        val e = listOf(ev(1000, SofyBus.Source.DEVICE, "tv_wake", false), ev(2000, SofyBus.Source.DEVICE, "tv_wake", false),
            ev(2 * d, SofyBus.Source.DEVICE, "tv_wake", false), ev(3 * d, SofyBus.Source.DEVICE, "tv_wake", true))
        assertTrue(SofyHouse.wakeFailures(e, now) >= 2)
        assertEquals(1, SofyHouse.wakeFailures(e.take(2), now))   // duas no mesmo dia = uma manhã
        val ha = List(3) { ev(it * 1000L, SofyBus.Source.HOUSE, "ha_call", false) } + List(2) { ev(it * 1000L, SofyBus.Source.HOUSE, "ha_call") }
        assertEquals(3, SofyHouse.homeHubFailures(ha, now))
        assertNull(SofyHouse.homeHubFailures(ha + List(5) { ev(it * 10L, SofyBus.Source.HOUSE, "ha_call") }, now))   // 3 de 10: casa ok
    }

    @Test fun energia() {
        assertEquals(10.5 to 945L, SofyHouse.energy(60 * 70, 150, 90))   // 70 h × 150 W = 10,5 kWh × R$ 0,90
        assertEquals(10.5 to null, SofyHouse.energy(60 * 70, 150, 0))
        assertNull(SofyHouse.energy(60 * 70, 0, 90))
    }
}
