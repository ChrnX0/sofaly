package br.paulo.tvlauncher

import android.view.KeyEvent
import br.paulo.tvlauncher.screensaver.SleepingHome
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regras puras da home que adormece: teclas, carrossel da faixa e a escolha do tempo de rodar. */
class SleepingHomeTest {
    @Test fun teclas() {
        assertEquals(SleepingHome.Acao.ANTERIOR, SleepingHome.acao(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(SleepingHome.Acao.PROXIMA, SleepingHome.acao(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(SleepingHome.Acao.PASSAR, SleepingHome.acao(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(SleepingHome.Acao.FECHAR, SleepingHome.acao(KeyEvent.KEYCODE_BACK))
        assertEquals(SleepingHome.Acao.FECHAR, SleepingHome.acao(KeyEvent.KEYCODE_HOME))
        assertEquals(SleepingHome.Acao.FECHAR, SleepingHome.acao(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test fun carrossel() {
        assertEquals(1, SleepingHome.girar(0, 1, 9))
        assertEquals(0, SleepingHome.girar(8, 1, 9))   // da última volta à primeira
        assertEquals(8, SleepingHome.girar(0, -1, 9))  // e para cima, da primeira à última
        assertEquals(0, SleepingHome.girar(3, 1, 0))
    }

    @Test fun tempoDeRodar() {
        assertEquals(30, SleepingHome.nextRotate(15))
        assertEquals(60, SleepingHome.nextRotate(30))
        assertEquals(0, SleepingHome.nextRotate(60))
        assertEquals(15, SleepingHome.nextRotate(0))
        assertEquals(30, SleepingHome.nextRotate(7))   // valor estranho: começa da primeira e vai à próxima
    }
}
