package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.ObserverCore
import br.paulo.tvlauncher.observer.SofyAnswers
import br.paulo.tvlauncher.observer.SofyBus
import org.junit.Assert.assertEquals
import org.junit.Test

class SofyAnswersTest {
    private val now = 1_800_000_000_000L

    @Test fun som() {
        assertEquals(SofyAnswers.Sound.FIXED_TV, SofyAnswers.sound(muted = true, volume = 0, max = 15, fixed = true))
        assertEquals(SofyAnswers.Sound.MUTED, SofyAnswers.sound(true, 8, 15, false))
        assertEquals(SofyAnswers.Sound.ZERO, SofyAnswers.sound(false, 0, 15, false))
        assertEquals(SofyAnswers.Sound.LOW, SofyAnswers.sound(false, 2, 15, false))     // 13%
        assertEquals(SofyAnswers.Sound.OK, SofyAnswers.sound(false, 3, 15, false))      // 20%
    }

    @Test fun lenta() {
        assertEquals(SofyAnswers.Slow.LOW_MEMORY, SofyAnswers.slow(200, 2000, 600))
        assertEquals(SofyAnswers.Slow.BUSY, SofyAnswers.slow(300, 2000, 600))           // metade do normal da casa
        assertEquals(SofyAnswers.Slow.OK, SofyAnswers.slow(500, 2000, 600))
        assertEquals(SofyAnswers.Slow.OK, SofyAnswers.slow(300, 2000, null))
    }

    @Test fun controle() {
        fun e(ago: Long, k: String) = SofyBus.Event(now - ago, SofyBus.Source.DEVICE, k, false)
        assertEquals(SofyAnswers.Remote.KEY_STUCK, SofyAnswers.remote(listOf(e(1000, "controller_lag"), e(2000, "key_stuck")), now))
        assertEquals(SofyAnswers.Remote.LAG, SofyAnswers.remote(listOf(e(1000, "controller_lag")), now))
        assertEquals(SofyAnswers.Remote.NONE, SofyAnswers.remote(listOf(e(2 * SofyAnswers.DAY_MS, "key_stuck")), now))
    }

    @Test fun hoje() {
        val inicio = now - 10 * 3_600_000L
        val ev = listOf(
            SofyBus.Event(now - 1000, SofyBus.Source.SOFY, "prewarm"),
            SofyBus.Event(now - 2000, SofyBus.Source.HOUSE, "arrived"),
            SofyBus.Event(now - 3000, SofyBus.Source.NETWORK, "drop_wifi", false),
            SofyBus.Event(now - 4000, SofyBus.Source.BACKUP, "night_backup"),
            SofyBus.Event(now - 5000, SofyBus.Source.BACKUP, "night_backup", false),   // backup que falhou não é "feito"
            SofyBus.Event(inicio - 1, SofyBus.Source.SOFY, "prewarm"),                  // ontem
            SofyBus.Event(now - 6000, SofyBus.Source.MEASURE, "picture_test"),          // não é algo que ela fez
        )
        assertEquals(listOf(SofyAnswers.Today.PREPARED to 2, SofyAnswers.Today.NIGHT to 1, SofyAnswers.Today.INTERNET to 1),
            SofyAnswers.today(ev, inicio, now))
        assertEquals(emptyList<Pair<SofyAnswers.Today, Int>>(), SofyAnswers.today(emptyList(), inicio, now))
    }

    @Test fun comoEuSei() {
        // Todo tipo de recado tem a sua origem (o when é exaustivo; aqui confere alguns e que nenhum quebra).
        ObserverCore.Kind.entries.forEach { SofyAnswers.evidence(it) }
        assertEquals(SofyAnswers.Evidence.MEASURED, SofyAnswers.evidence(ObserverCore.Kind.NO_INTERNET))
        assertEquals(SofyAnswers.Evidence.BASELINE, SofyAnswers.evidence(ObserverCore.Kind.UNUSUAL_LATENCY))
        assertEquals(SofyAnswers.Evidence.TEST, SofyAnswers.evidence(ObserverCore.Kind.FREEZE_UNDONE))
        assertEquals(SofyAnswers.Evidence.BUS, SofyAnswers.evidence(ObserverCore.Kind.KEY_STUCK))
    }
}
