package br.paulo.tvlauncher.telas

import br.paulo.tvlauncher.companion.CompanionTv
import br.paulo.tvlauncher.observer.AppProfileTv
import br.paulo.tvlauncher.observer.SofyBus
import br.paulo.tvlauncher.observer.SofyBusTv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Travas da Sofy (revisor independente, 25/11/2026). Roda com -Ptelas (precisa do Android do Robolectric).
 * - As leituras das fichas dos apps não esperam a trava do AppProfileTv: o tick a segura por até ~35 s (shell) e o juiz,
 *   com a trava do ObserverTv, lia o app da frente = as duas threads presas para sempre.
 * - O barramento aguenta várias threads do mesmo processo (antes: OverlappingFileLockException e evento perdido).
 * - O Celular e TV chega ao barramento (antes por reflexão, que o R8 quebrava no APK final).
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class SofyTravasTest {
    private val app get() = RuntimeEnvironment.getApplication()

    /** Roda [f] noutra thread; true se terminou em [ms]. */
    private fun terminaEm(ms: Long, f: () -> Unit): Boolean {
        val t = Thread { f() }.apply { isDaemon = true; start() }
        t.join(ms)
        return !t.isAlive
    }

    @Test fun leiturasDasFichasNaoEsperamOTick() {
        val segurando = CountDownLatch(1)
        val soltar = CountDownLatch(1)
        // Como o tick: segura a trava do AppProfileTv (lendo o shell) por muito tempo.
        Thread { synchronized(AppProfileTv) { segurando.countDown(); soltar.await(30, TimeUnit.SECONDS) } }.apply { isDaemon = true; start() }
        assertTrue(segurando.await(5, TimeUnit.SECONDS))
        try {
            assertTrue("frontPackage esperou a trava", terminaEm(3_000) { AppProfileTv.frontPackage(app) })
            assertTrue("allCards esperou a trava", terminaEm(3_000) { AppProfileTv.allCards(app) })
            assertTrue("opensByProfile esperou a trava", terminaEm(3_000) { AppProfileTv.opensByProfile(app) })
            assertTrue("report esperou a trava", terminaEm(3_000) { AppProfileTv.report(app) })
        } finally { soltar.countDown() }
    }

    @Test fun barramentoAguentaVariasThreads() {
        val antes = SofyBusTv.events(app).size
        val threads = (0 until 8).map { n ->
            Thread { repeat(40) { SofyBusTv.emit(app, SofyBus.Source.SOFY, "trava_teste", detail = "t$n") } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(30_000) }
        assertEquals("evento perdido com threads ao mesmo tempo", antes + 320, SofyBusTv.events(app).size)
    }

    @Test fun celularETvChegaAoBarramento() {
        CompanionTv.sofy(app, "picture_black", true, "ok teste")
        CompanionTv.sofy(app, "remote_find", true, "")
        val ev = SofyBusTv.events(app)
        assertTrue(ev.any { it.kind == "picture_black" && it.source == SofyBus.Source.MEASURE })
        assertTrue(ev.any { it.kind == "remote_find" && it.source == SofyBus.Source.DEVICE })
    }
}
