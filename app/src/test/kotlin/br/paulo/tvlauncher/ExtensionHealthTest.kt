package br.paulo.tvlauncher

import br.paulo.tvlauncher.screensaver.ExtensionHealth as H
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionHealthTest {
    @Test fun respostaBoaZera() {
        assertEquals(H.Placar(), H.contar(H.Placar(3, 100), ok = true, ms = 200, agora = 500))
    }

    @Test fun cincoFalhasSeguidasDesligam() {
        var p = H.Placar()
        repeat(H.LIMITE_FALHAS - 1) { p = H.contar(p, false, 0, 1_000L + it) }
        assertFalse(H.desligar(p))
        p = H.contar(p, false, 0, 2_000L)
        assertTrue(H.desligar(p))
    }

    @Test fun respostaLentaContaComoFalha() {
        assertEquals(1, H.contar(H.Placar(), ok = true, ms = H.LENTO_MS + 1, agora = 10).falhas)
    }

    @Test fun falhaAntigaNaoSoma() {
        val p = H.contar(H.Placar(4, 0L), false, 0, H.JANELA_MS + 1)
        assertEquals(H.Placar(1, H.JANELA_MS + 1), p)
    }
}
