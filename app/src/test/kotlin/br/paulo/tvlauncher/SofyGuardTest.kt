package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyBus
import br.paulo.tvlauncher.observer.SofyGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SofyGuardTest {
    @Test fun perdido() {
        val m = SofyGuard.LostMeter()
        // 5 VOLTAR em 10 s: ainda não; o 6º: perdido; e zera.
        for (i in 0 until 5) assertFalse(m.key(SofyGuard.KEY_BACK, i * 2_000L))
        assertTrue(m.key(SofyGuard.KEY_BACK, 10_000L))
        assertFalse(m.key(SofyGuard.KEY_BACK, 11_000L))
        // VOLTAR espaçados (fora da janela) não contam.
        val n = SofyGuard.LostMeter()
        for (i in 0 until 10) assertFalse(n.key(SofyGuard.KEY_BACK, i * 4_000L))
        // Navegar no meio (seta = 19) zera a conta.
        val o = SofyGuard.LostMeter()
        for (i in 0 until 4) o.key(SofyGuard.KEY_BACK, i * 1_000L)
        o.key(19, 4_500L)
        assertFalse(o.key(SofyGuard.KEY_BACK, 5_000L))
        // INÍCIO 3 vezes em 20 s.
        val h = SofyGuard.LostMeter()
        assertFalse(h.key(SofyGuard.KEY_HOME, 0)); assertFalse(h.key(SofyGuard.KEY_HOME, 5_000)); assertTrue(h.key(SofyGuard.KEY_HOME, 9_000))
    }

    @Test fun teclaPresa() {
        assertTrue(SofyGuard.stuck(23, 150))      // OK segurado 7 s
        assertFalse(SofyGuard.stuck(23, 40))
        assertFalse(SofyGuard.stuck(24, 400))     // volume segurado é normal
    }

    @Test fun appComPoderDemais() {
        val acess = SofyGuard.powers(listOf("android.permission.INTERNET"), listOf("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertEquals(listOf("accessibility"), acess)
        assertTrue(SofyGuard.tooPowerful(acess))
        val dois = SofyGuard.powers(listOf("android.permission.RECORD_AUDIO", "android.permission.CAMERA"), emptyList())
        assertFalse(SofyGuard.tooPowerful(dois))
        assertTrue(SofyGuard.tooPowerful(SofyGuard.powers(listOf("android.permission.RECORD_AUDIO", "android.permission.CAMERA",
            "android.permission.SYSTEM_ALERT_WINDOW"), emptyList())))
        assertEquals(emptySet<String>(), SofyGuard.newPackages(null, setOf("a", "b")))   // primeira olhada
        assertEquals(setOf("c"), SofyGuard.newPackages(setOf("a", "b"), setOf("a", "b", "c")))
    }

    @Test fun pinErrado() {
        val now = 1_800_000_000_000L
        val e = List(5) { SofyBus.Event(now - it * 60_000L, SofyBus.Source.HOUSE, "panel_pin_wrong", false) }
        assertEquals(5, SofyGuard.pinAlarm(e, now))
        assertNull(SofyGuard.pinAlarm(e.take(4), now))
        assertNull(SofyGuard.pinAlarm(e.map { it.copy(at = it.at - 2 * 3_600_000L) }, now))
    }

    @Test fun atualizacaoQuebrou() {
        assertEquals(listOf("voice"), SofyGuard.regressions(mapOf("voice" to true, "store" to true), mapOf("voice" to false, "store" to true, "cast" to false)))
        assertEquals(listOf("com.android.vending"), SofyGuard.watchedUpdated(mapOf("com.android.vending" to 1L, "x" to 1L), mapOf("com.android.vending" to 2L, "x" to 3L)))
    }
}
