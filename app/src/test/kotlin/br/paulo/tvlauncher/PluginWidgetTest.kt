package br.paulo.tvlauncher

import br.paulo.tvlauncher.screensaver.PluginWidget
import br.paulo.tvlauncher.screensaver.PluginWidget.Escolhido
import br.paulo.tvlauncher.screensaver.PluginWidget.Limites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Widgets vivos na grade da HOME (PluginWidget): guardar, caber, limites, achar lugar, mover e mudar o tamanho. */
class PluginWidgetTest {
    private val livre = Limites(1, 1, PluginWidget.COLS, PluginWidget.LINHAS)

    @Test fun gravaELeComLugarESoComComponenteValido() {
        val l = listOf(Escolhido(7, "com.exemplo.clima/.Widget", 8, 0, 4, 2), Escolhido(9, "com.b.app/com.b.app.W\$Grande"))
        assertEquals(l, PluginWidget.ler(PluginWidget.gravar(l)))
        assertEquals("com.exemplo.clima", l[0].pacote)
        assertTrue(l[0].colocado); assertFalse(l[1].colocado)
        // Lixo, id negativo, componente sem classe ou com espaço: saem; nunca passa de MAX.
        val t = """[{"id":-1,"provider":"a.b/.C"},{"id":1,"provider":"semclasse"},{"id":2,"provider":"a.b/ .C"},
            {"id":3,"provider":"a.b/.C"},{"id":4,"provider":"c.d/.E"},{"id":5,"provider":"e.f/.G"},{"id":6,"provider":"g.h/.I"}]"""
        assertEquals(listOf(3, 4, 5), PluginWidget.ler(t).map { it.id })
        assertEquals(emptyList<Escolhido>(), PluginWidget.ler("{nada"))
        assertEquals(emptyList<Escolhido>(), PluginWidget.ler(null))
    }

    @Test fun lugarForaDaGradeViraSemLugar() {
        val t = """[{"id":1,"provider":"a.b/.C","x":10,"y":0,"w":4,"h":2},{"id":2,"provider":"c.d/.E","x":-1,"y":0,"w":2,"h":2}]"""
        assertEquals(listOf(false, false), PluginWidget.ler(t).map { it.colocado })
    }

    @Test fun cabeNoMaximoTresEUmDeCadaWidget() {
        val um = listOf(Escolhido(1, "a.b/.C"))
        assertTrue(PluginWidget.cabe(emptyList(), "a.b/.C"))
        assertFalse(PluginWidget.cabe(um, "a.b/.C"))
        assertTrue(PluginWidget.cabe(um, "a.b/.D"))
        assertFalse(PluginWidget.cabe(um + Escolhido(2, "a.b/.D") + Escolhido(3, "a.b/.E"), "x.y/.Z"))
    }

    @Test fun limitesEmCelulasArredondamParaCimaEFicamNaGrade() {
        // Célula de 100 x 80 px. Mínimo 180 x 110 -> 2 x 2; máximo 0 (não declarado) -> grade toda.
        assertEquals(Limites(2, 2, PluginWidget.COLS, PluginWidget.LINHAS), PluginWidget.limites(180, 110, 0, 0, 100f, 80f))
        // Máximo menor que o mínimo não passa do mínimo; mínimo maior que a grade fica na grade.
        assertEquals(Limites(2, 2, 2, 2), PluginWidget.limites(180, 110, 50, 50, 100f, 80f))
        assertEquals(Limites(PluginWidget.COLS, PluginWidget.LINHAS, PluginWidget.COLS, PluginWidget.LINHAS),
            PluginWidget.limites(99999, 99999, 0, 0, 100f, 80f))
        // Grade ainda sem tamanho (0 px): 1 célula, sem dividir por zero.
        assertEquals(1, PluginWidget.limites(180, 110, 0, 0, 0f, 0f).minW)
    }

    @Test fun lugarNovoComecaNoCantoDeCimaADireitaEDesviaDosOutros() {
        val (x, y, t) = PluginWidget.lugar(emptyList(), 4, 2, livre)!!
        assertEquals(Triple(PluginWidget.COLS - 4, 0, 4 to 2), Triple(x, y, t))
        val outro = listOf(Escolhido(1, "a.b/.C", PluginWidget.COLS - 4, 0, 4, 2))
        val (x2, y2, _) = PluginWidget.lugar(outro, 4, 2, livre)!!
        assertEquals(PluginWidget.COLS - 8 to 0, x2 to y2)
    }

    @Test fun gradeCheiaEncolheAteOMinimoDepoisDesiste() {
        // Um widget ocupa tudo menos a última coluna: um 3 x 2 só cabe encolhido para 1 coluna.
        val quase = listOf(Escolhido(1, "a.b/.C", 0, 0, PluginWidget.COLS - 1, PluginWidget.LINHAS))
        assertEquals(1 to 2, PluginWidget.lugar(quase, 3, 2, livre)!!.third)
        assertNull(PluginWidget.lugar(quase, 3, 2, Limites(2, 2, 6, 6)))
    }

    @Test fun moverAndaUmaCelulaEParaNaBordaOuNoOutro() {
        val l = listOf(Escolhido(1, "a.b/.C", 0, 0, 2, 2), Escolhido(2, "c.d/.E", 3, 0, 2, 2))
        assertEquals(l, PluginWidget.mover(l, 1, -1, 0))                       // borda da esquerda
        assertEquals(l, PluginWidget.mover(l, 1, 0, -1))                       // borda de cima
        val m = PluginWidget.mover(l, 1, 1, 0)
        assertEquals(1, m[0].x)
        assertEquals(m, PluginWidget.mover(m, 1, 1, 0))                        // encostou no outro: fica
        assertEquals(1, PluginWidget.mover(l, 1, 0, 1)[0].y)
        assertEquals(l[1], PluginWidget.mover(l, 1, 1, 0)[1])                  // o outro não muda
    }

    @Test fun redimensionarRespeitaLimitesECresceParaOOutroLadoNaBorda() {
        val lim = Limites(2, 2, 4, 3)
        val l = listOf(Escolhido(1, "a.b/.C", 0, 0, 2, 2))
        assertEquals(3, PluginWidget.redimensionar(l, 1, 1, 0, lim)[0].w)
        assertEquals(l, PluginWidget.redimensionar(l, 1, -1, 0, lim))          // já está no mínimo
        val grande = listOf(Escolhido(1, "a.b/.C", 0, 0, 4, 3))
        assertEquals(grande, PluginWidget.redimensionar(grande, 1, 1, 1, lim)) // já está no máximo
        // Encostado na direita: cresce para a esquerda (anda uma coluna).
        val canto = listOf(Escolhido(1, "a.b/.C", PluginWidget.COLS - 2, 0, 2, 2))
        val r = PluginWidget.redimensionar(canto, 1, 1, 0, lim)[0]
        assertEquals(PluginWidget.COLS - 3 to 3, r.x to r.w)
        // Outro widget no caminho dos dois lados: não muda.
        val preso = listOf(Escolhido(1, "a.b/.C", 2, 0, 2, 2), Escolhido(2, "c.d/.E", 4, 0, 2, 2), Escolhido(3, "e.f/.G", 0, 0, 2, 2))
        assertEquals(preso, PluginWidget.redimensionar(preso, 1, 1, 0, lim))
    }

    @Test fun livreNaoContaOProprioNemQuemNaoTemLugar() {
        val l = listOf(Escolhido(1, "a.b/.C", 0, 0, 2, 2), Escolhido(2, "c.d/.E"))
        assertTrue(PluginWidget.livre(l, 1, 0, 0, 3, 3))
        assertFalse(PluginWidget.livre(l, 9, 1, 1, 2, 2))
        assertTrue(PluginWidget.livre(l, 9, 2, 0, 2, 2))
        assertFalse(PluginWidget.livre(l, 9, PluginWidget.COLS - 1, 0, 2, 1))
    }
}
