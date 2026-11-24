package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.CruzLogica
import br.paulo.tvlauncher.home.CruzLogica.Categoria
import br.paulo.tvlauncher.home.CruzLogica.Tecla
import br.paulo.tvlauncher.home.StripHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Barra única (24/11/2026): teclas, janela de cards, descrição viva e o padrão que o Paulo escolheu. */
class StripHomeTest {
    @Test fun teclas() {
        assertEquals("◀ volta um app", StripHome.Passo(Tecla.CIMA), StripHome.passo(Tecla.ESQUERDA, 1, 2))
        assertNull("◀ no 1º app para (borda)", StripHome.passo(Tecla.ESQUERDA, 1, 0).tecla)
        assertEquals("▶ anda um app", StripHome.Passo(Tecla.BAIXO), StripHome.passo(Tecla.DIREITA, 1, 0))
        assertEquals("▲ mundo de cima", StripHome.Passo(Tecla.ESQUERDA), StripHome.passo(Tecla.CIMA, 2, 0))
        assertTrue("▲ no 1º mundo sobe para a Ilha", StripHome.passo(Tecla.CIMA, 0, 3).subirTopo)
        assertEquals("▼ mundo de baixo", StripHome.Passo(Tecla.DIREITA), StripHome.passo(Tecla.BAIXO, 0, 0))
        assertEquals(StripHome.Passo(Tecla.OK), StripHome.passo(Tecla.OK, 1, 1))
        assertEquals(StripHome.Passo(Tecla.HOME), StripHome.passo(Tecla.HOME, 3, 1))
    }

    @Test fun padraoDoPaulo() {
        val o = StripHome.DEFAULT
        assertEquals(StripHome.Desc.LIVE, o.desc)
        assertEquals(StripHome.Format.WIDE, o.format)
        assertEquals(StripHome.Size.SMALL, o.size)
        assertEquals(StripHome.Gap.WIDE, o.gap)
        assertEquals(StripHome.Corners.ROUND, o.corners)
        assertEquals(false, o.glass)
        assertEquals(false, o.name)
        assertEquals("16:9 pequeno = 104 x 59 dp", 104f to 59f, o.cardW to o.cardH)
    }

    @Test fun janela() {
        val l = List(20) { 104f }
        // 840 dp com vão 30: cabem 6 (6*104 + 5*30 = 774)
        assertEquals(0 to 6, StripHome.janela(0, l, 30f, 840f))
        assertEquals("o escolhido no fim da janela ainda mostra um depois", 0 to 6, StripHome.janela(4, l, 30f, 840f))
        assertEquals(1 to 6, StripHome.janela(5, l, 30f, 840f))
        assertEquals("no último, a janela encosta no fim", 14 to 6, StripHome.janela(19, l, 30f, 840f))
        assertEquals("poucos apps", 0 to 3, StripHome.janela(2, List(3) { 104f }, 30f, 840f))
        assertEquals(0 to 0, StripHome.janela(0, emptyList(), 30f, 840f))
    }

    @Test fun descricaoViva() {
        fun i(id: String, nome: String = id, sub: String = "", s: CruzLogica.Situacao? = null) = StripHome.Itens(id, nome, sub, s)
        assertEquals("**No ar** Jornal · faltam 20 min", StripHome.viva(Categoria.ASSISTIR,
            listOf(i("series"), i("tv", s = CruzLogica.Situacao("No ar", "Jornal · faltam 20 min", "tv")))) { "$it apps" })
        assertNull("sem nada agora: frase fixa", StripHome.viva(Categoria.ASSISTIR, listOf(i("series"))) { "$it apps" })
        assertEquals("Próximo às 6:30 · 24°", StripHome.viva(Categoria.FERRAMENTAS,
            listOf(i("t:despertador", sub = "Próximo às 6:30"), i("t:clima", sub = "24°\nPrevisão"))) { "$it apps" })
        assertEquals("3 apps", StripHome.viva(Categoria.APLICATIVOS, listOf(i("app:a"), i("app:b"), i("app:c"), i("c:x"))) { "$it apps" })
        assertNull("Jogar sem jogo em andamento: frase fixa", StripHome.viva(Categoria.JOGAR, listOf(i("app:retro", "RetroArch", "Emulador"))) { "$it apps" })
        assertEquals("**Parou em** 1981 - O Mundo Está Sendo Reprogramado? Dr. Alex", StripHome.viva(Categoria.OUVIR,
            listOf(i("podcasts", s = CruzLogica.Situacao("Parou em", "1981 - O MUNDO ESTÁ SENDO REPROGRAMADO? DR. ALEX", "podcasts")))) { "$it apps" })
        assertEquals("normal passa igual", "Jornal Nacional · TV Globo", StripHome.semGritar("Jornal Nacional · TV Globo"))
        assertEquals(listOf("No ar" to true, " Jornal" to false), StripHome.partes("**No ar** Jornal"))
    }
}
