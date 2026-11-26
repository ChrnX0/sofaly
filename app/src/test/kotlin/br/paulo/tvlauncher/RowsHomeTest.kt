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

    @Test fun gradeAndaPorColuna() {
        // 7 cards em 3 linhas = colunas [0,1,2] [3,4,5] [6]; foco no 4 prende a coluna do meio
        val xs = RowsHome.xs(List(7) { 128f }, 4, o, focada = true, linhas = 3)
        for (i in 3..5) assertEquals(RowsHome.FOCO_X, xs[i], 0.01f)
        assertEquals(RowsHome.FOCO_X + 128f * 1.3f + 8f, xs[6], 0.01f)
        for (i in 0..2) assertEquals(RowsHome.FOCO_X - 136f, xs[i], 0.01f)
        // 1 linha = a conta de sempre
        assertEquals(RowsHome.xs(6, 2, o, true).toList(), RowsHome.xs(List(6) { 128f }, 2, o, true, linhas = 1).toList())
    }

    @Test fun gradeEmpurraAsDeBaixoESobeParaCaber() {
        val e = RowsHome.extraDaGrade(7, 3, o)
        assertEquals(2 * RowsHome.passoDaLinha(o), e, 0.01f)
        assertEquals(0f, RowsHome.extraDaGrade(1, 3, o), 0f)   // 1 card não vira grade
        val extras = floatArrayOf(0f, e, 0f)
        // a grade em foco sobe até as 3 linhas e o nome caberem
        val y = RowsHome.centroDaFileira(1, 1, 540f, o, extras)
        assertTrue(y + e + RowsHome.NOME_ABAIXO <= 540f - RowsHome.MARGEM_BAIXO + 0.1f)
        // a fileira de baixo desce o passo comum mais a altura da grade
        assertEquals(y + RowsHome.PASSO_BAIXO + e, RowsHome.centroDaFileira(2, 1, 540f, o, extras), 0.1f)
        // com o foco na de baixo, a grade de cima sobe também a altura dela
        assertEquals(314f - RowsHome.PASSO_CIMA - e, RowsHome.centroDaFileira(1, 2, 540f, o, extras), 0.5f)
    }

    @Test fun opacidadeDasOutras() {
        assertEquals(1f, RowsHome.alfaDaFileira(1, 1, o), 0f)
        assertEquals(0.4f, RowsHome.alfaDaFileira(2, 1, o), 0f)
        assertEquals(0f, RowsHome.alfaDaFileira(0, 1, o.copy(hideAbove = true)), 0f)
        assertEquals(0.4f, RowsHome.alfaDaFileira(2, 1, o.copy(hideAbove = true)), 0f)
    }

    @Test fun assinaturaMudaComCadaOpcao() {
        val base = o.signature()
        listOf(o.copy(size = RowsHome.Size.L), o.copy(cardTitle = false), o.copy(status = false), o.copy(glowAnim = RowsHome.GlowAnim.PULSE),
            o.copy(shadowTint = RowsHome.ShadowTint.GRAPHITE), o.copy(shadowW = RowsHome.ShadowLength.SHORT), o.copy(shadowH = RowsHome.ShadowLength.LONG),
            o.copy(focusBg = true), o.copy(autohide = true), o.copy(statusColor = RowsHome.IconColor.ACCENT),
            o.copy(statusBox = RowsHome.StatusBox.NONE), o.copy(statusSize = RowsHome.IconSize.L), o.copy(clockSize = RowsHome.ClockSize.XL),
            o.copy(clockLine2 = RowsHome.Line2.DATE), o.copy(textSize = RowsHome.TextSize.L), o.copy(bold = true), o.copy(italic = true))
            .forEach { assertTrue(it.signature() != base) }
    }

    @Test fun topoSomeForaDaPrimeiraFileiraSoComOcultar() {
        assertTrue(RowsHome.topoVisivel(o, 3))
        val oculta = o.copy(autohide = true)
        assertTrue(RowsHome.topoVisivel(oculta, 0))
        assertTrue(!RowsHome.topoVisivel(oculta, 1))
    }

    @Test fun segundaLinhaDaHora() {
        assertEquals(null, RowsHome.line2Skeleton(RowsHome.Line2.NONE))
        assertEquals("EEEE", RowsHome.line2Skeleton(RowsHome.Line2.WEEKDAY))
        assertTrue(RowsHome.line2Skeleton(RowsHome.Line2.BOTH)!!.contains("EEEE"))
        // a letra nunca encolhe abaixo do padrão (18 sp mínimo na TV)
        assertTrue(RowsHome.TextSize.entries.all { it.k >= 1f })
    }
}
