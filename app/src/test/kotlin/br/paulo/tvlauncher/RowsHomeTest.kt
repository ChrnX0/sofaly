package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.RowsHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contas das Fileiras (clone do Projectivy, 26/11/2026): medidas das fotos dele numa tela de 960 x 540 dp. */
class RowsHomeTest {
    private val o = RowsHome.DEFAULT

    @Test fun fileiraEmFocoNaMesmaAltura() {
        // foco parado: a escolhida fica a 58% da altura, qualquer que seja
        for (f in 0..4) assertEquals(314f, RowsHome.centroDaFileira(f, f, 540f, o), 1f)
        // a de baixo a 176 dp, a de cima a 151 dp (medido)
        assertEquals(490f, RowsHome.centroDaFileira(2, 1, 540f, o), 1f)
        assertEquals(163f, RowsHome.centroDaFileira(0, 1, 540f, o), 1f)
    }

    @Test fun semFocoParadoAPrimeiraFicaNoAlto() {
        val solto = o.copy(fixed = false)
        assertEquals(RowsHome.INICIO_SOLTO, RowsHome.centroDaFileira(0, 0, 540f, solto), 0.1f)
        // descendo, a escolhida não passa da altura do foco parado
        assertTrue(RowsHome.centroDaFileira(5, 5, 540f, solto) <= 314.1f)
    }

    @Test fun cardEmFocoPresoAEsquerda() {
        val xs = RowsHome.xs(6, 2, o, focada = true)
        assertEquals(RowsHome.FOCO_X, xs[2], 0.01f)
        // o seguinte vem logo depois do foco aumentado (128 x 1,3 + 8)
        assertEquals(RowsHome.FOCO_X + 128f * 1.3f + 8f, xs[3], 0.01f)
        // os de antes saem pela esquerda
        assertEquals(RowsHome.FOCO_X - 136f, xs[1], 0.01f)
        assertTrue(xs[0] < 0f)
    }

    @Test fun opacidadeDasOutras() {
        assertEquals(1f, RowsHome.alfaDaFileira(1, 1, o), 0f)
        assertEquals(0.4f, RowsHome.alfaDaFileira(2, 1, o), 0f)
        assertEquals(0f, RowsHome.alfaDaFileira(0, 1, o.copy(hideAbove = true)), 0f)
        assertEquals(0.4f, RowsHome.alfaDaFileira(2, 1, o.copy(hideAbove = true)), 0f)
    }

    @Test fun assinaturaMudaComCadaOpcao() {
        val base = o.signature()
        listOf(o.copy(size = RowsHome.Size.L), o.copy(cardTitle = false), o.copy(status = false), o.copy(glowAnim = RowsHome.GlowAnim.PULSE))
            .forEach { assertTrue(it.signature() != base) }
    }
}
