package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.BottomBar
import br.paulo.tvlauncher.home.CruzLogica.Tecla
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Teclas da barra embaixo (23/11/2026): a tradução para as teclas da cruz e a rolagem da linha de apps. */
class BottomBarTest {
    @Test fun naLinhaDasCategorias() {
        assertEquals(BottomBar.Passo(Tecla.ESQUERDA, false), BottomBar.passo(Tecla.ESQUERDA, false, 0))
        assertEquals(BottomBar.Passo(Tecla.DIREITA, false), BottomBar.passo(Tecla.DIREITA, false, 3))
        assertEquals("▲ abre a linha de apps", BottomBar.Passo(null, true), BottomBar.passo(Tecla.CIMA, false, 0))
        assertEquals("OK abre a linha de apps", BottomBar.Passo(null, true), BottomBar.passo(Tecla.OK, false, 0))
        assertEquals("▼ não faz nada", BottomBar.Passo(null, false), BottomBar.passo(Tecla.BAIXO, false, 0))
    }

    @Test fun naLinhaDeApps() {
        assertEquals("◀ volta um app", BottomBar.Passo(Tecla.CIMA, true), BottomBar.passo(Tecla.ESQUERDA, true, 2))
        assertNull("◀ no 1º não sai", BottomBar.passo(Tecla.ESQUERDA, true, 0).tecla)
        assertFalse("◀ no 1º não sobe ao topo", BottomBar.passo(Tecla.ESQUERDA, true, 0).subirTopo)
        assertEquals("▶ anda um app", BottomBar.Passo(Tecla.BAIXO, true), BottomBar.passo(Tecla.DIREITA, true, 0))
        assertEquals("OK abre o app", BottomBar.Passo(Tecla.OK, true), BottomBar.passo(Tecla.OK, true, 1))
        assertEquals("▼ fecha a linha", BottomBar.Passo(null, false), BottomBar.passo(Tecla.BAIXO, true, 1))
        assertTrue("▲ sobe para a barra de cima", BottomBar.passo(Tecla.CIMA, true, 1).subirTopo)
    }

    @Test fun homeFechaALinha() {
        assertEquals(BottomBar.Passo(Tecla.HOME, false), BottomBar.passo(Tecla.HOME, true, 4))
        assertEquals(BottomBar.Passo(Tecla.HOME, false), BottomBar.passo(Tecla.HOME, false, 0))
    }

    @Test fun rolagemDaLinha() {
        assertEquals(0, BottomBar.primeiro(0, 10, 5))
        assertEquals(0, BottomBar.primeiro(1, 10, 5))
        assertEquals(2, BottomBar.primeiro(3, 10, 5))
        assertEquals("no fim não sobra buraco", 5, BottomBar.primeiro(9, 10, 5))
        assertEquals("poucos apps não rolam", 0, BottomBar.primeiro(2, 3, 5))
    }
}
