package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.GoogleHomeTrim
import br.paulo.tvlauncher.observer.GoogleHomeTrim.PACKAGE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleHomeTrimTest {
    private val min = 60_000L
    private val agora = 100 * min

    @Test fun encerraSoQuandoParadoLongeDaFrente() {
        assertTrue(GoogleHomeTrim.shouldTrim("tv.sofaly.launcher", lastFrontAt = 0L, lastTrimAt = 0L, now = agora))
        // Na frente (voz, Gemini, notificações): nunca.
        assertFalse(GoogleHomeTrim.shouldTrim(PACKAGE, 0L, 0L, agora))
        // Não se sabe quem está na frente (sem acessibilidade): não mexe.
        assertFalse(GoogleHomeTrim.shouldTrim(null, 0L, 0L, agora))
        // Usado há 1 min: espera os 2 min.
        assertFalse(GoogleHomeTrim.shouldTrim("tv.sofaly.launcher", agora - 1 * min, 0L, agora))
        assertTrue(GoogleHomeTrim.shouldTrim("tv.sofaly.launcher", agora - 2 * min, 0L, agora))
        // Encerrado há 5 min: sem vaivém, espera os 10 min.
        assertFalse(GoogleHomeTrim.shouldTrim("com.google.android.youtube.tv", 0L, agora - 5 * min, agora))
        assertTrue(GoogleHomeTrim.shouldTrim("com.google.android.youtube.tv", 0L, agora - 10 * min, agora))
    }

    @Test fun lerSaidaDoComando() {
        assertEquals(23840, GoogleHomeTrim.killedPid("killed 23840\r\n"))
        assertNull(GoogleHomeTrim.killedPid("none\n"))
        assertNull(GoogleHomeTrim.killedPid(null))
        assertNull(GoogleHomeTrim.killedPid("killed abc"))
    }

    @Test fun comandoSoMexeNoProcessoPrincipal() {
        // pidof com o nome exato não acha o ":coreservices" (avisos e notificações), e am kill só encerra o que está em cache.
        assertTrue(GoogleHomeTrim.COMMAND.contains("pidof $PACKAGE)"))
        assertTrue(GoogleHomeTrim.COMMAND.contains("am kill $PACKAGE"))
        assertFalse(GoogleHomeTrim.COMMAND.contains("force-stop"))
        assertFalse(GoogleHomeTrim.COMMAND.contains("kill-all"))
    }
}
