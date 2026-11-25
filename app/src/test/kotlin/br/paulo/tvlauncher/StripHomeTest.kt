package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.CruzLogica
import br.paulo.tvlauncher.home.CruzLogica.Categoria
import br.paulo.tvlauncher.home.CruzLogica.Tecla
import br.paulo.tvlauncher.home.StripHome
import br.paulo.tvlauncher.home.StripBackground.Choice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("palavras pequenas no meio ficam minúsculas", "Dr. Alex Alves e Thiago Lima de Novo", StripHome.semGritar("DR. ALEX ALVES E THIAGO LIMA DE NOVO"))
        assertEquals("depois de separador abre frase", "Rádio - O Fim da Noite", StripHome.semGritar("RÁDIO - O FIM DA NOITE"))
        assertEquals("normal passa igual", "Jornal Nacional · TV Globo", StripHome.semGritar("Jornal Nacional · TV Globo"))
        assertEquals("só o trecho que grita", "Oração do Dia Nada Faltando | Pastora Ana", StripHome.semGritar("ORAÇÃO DO DIA NADA FALTANDO | Pastora Ana"))
        assertEquals("sigla curta fica", "Jogo da NBA ao vivo", StripHome.semGritar("Jogo da NBA ao vivo"))
        assertEquals(listOf("No ar" to true, " Jornal" to false), StripHome.partes("**No ar** Jornal"))
    }

    @Test fun fundoDaBarra() {
        val B = br.paulo.tvlauncher.home.StripBackground
        assertEquals("nunca escolhido: Vale (Paulo, 24/11/2026)", Choice.VALE, B.resolve(-1))
        assertEquals(Choice.NOITE, B.resolve(2))
        assertEquals(Choice.THEME, B.resolve(3))
        assertEquals("novo fundo agora gira só entre as fotos", Choice.VALE, B.nextPhoto(Choice.NOITE))
    }

    @Test fun iconeVivoDoMundo() {
        val M = br.paulo.tvlauncher.home.WorldMotion.Motion
        assertEquals(0f, M.drawOn(0f), 1e-4f)
        assertEquals("traço inteiro ao fim do desenho", 1f, M.drawOn(M.DRAW_MS), 1e-4f)
        assertEquals("o gesto não começa durante o desenho", 0f, M.calm(M.DRAW_MS / 2), 1e-4f)
        assertEquals(1f, M.calm(M.DRAW_MS + M.CALM_MS + 1f), 1e-4f)
        assertEquals("ciclo contínuo: volta ao mesmo ponto", M.breath(1234f), M.breath(1234f + M.CYCLE_MS), 1e-3f)
        assertEquals("respiração suave, nunca passa de 1", true, (0..12000 step 37).all { kotlin.math.abs(M.breath(it.toFloat())) <= 1f })
        assertEquals(0f, M.wave(0f), 1e-4f)
    }

    @Test fun animacoesDoMundo() {
        val K = br.paulo.tvlauncher.home.WorldScenes.Curvas
        assertEquals("padrão = Pequena cena (Paulo, 25/11/2026)", StripHome.WorldAnim.SCENE, StripHome.Options().worldAnim)
        assertEquals(0f, K.io(0f), 1e-4f); assertEquals(1f, K.io(1f), 1e-4f); assertEquals(0.5f, K.io(0.5f), 1e-4f)
        assertEquals("a mola chega no alvo", 1f, K.mola(1f), 0.01f)
        assertEquals("sem pulinho antes do fim da volta", 1f to 1f, K.pulinho(1f, 5.5f))
        assertEquals("loop sem fim: a mesma volta se repete", K.pulinho(5.0f, 5.5f).second, K.pulinho(5.0f + 5.5f * 3, 5.5f).second, 1e-3f)
        assertEquals("acendem em sequência", listOf(false, false, false), (0..2).map { K.acende(0.1f, 5.5f, it, 3) })
        assertEquals(listOf(true, true, true), (0..2).map { K.acende(5.5f * 0.77f, 5.5f, it, 3) })
    }

    @Test fun descricaoDoCardEmFoco() {
        fun i(id: String, nome: String = id, sub: String = "", s: CruzLogica.Situacao? = null) = StripHome.Itens(id, nome, sub, s)
        assertEquals("**No ar** Jornal Hoje · faltam 25 min", StripHome.doCard(i("tv", "TV", "Canais", CruzLogica.Situacao("No ar", "Jornal Hoje · faltam 25 min", "tv"))))
        assertEquals("**Netflix** Filmes e séries", StripHome.doCard(i("app:n", "Netflix", "Filmes e séries")))
        assertEquals("linhas viram ·", "**Clima** 34° · Previsão", StripHome.doCard(i("t:clima", "Clima", "34°\nPrevisão")))
        assertEquals("sem linha de baixo: só o nome", "**Rádio**", StripHome.doCard(i("radio", "Rádio")))
        assertEquals("não repete o nome", "**Casa**", StripHome.doCard(i("casa", "Casa", "casa")))
    }

    /** Grade de 2 linhas: 5 itens = colunas [0,1] [2,3] [4]. */
    @Test fun gradeDeDuasLinhas() {
        fun g(t: Tecla, sel: Int, cat: Int = 1) = StripHome.passo(t, cat, sel, 5, grade = true)
        assertEquals("▶ pula a coluna", 2, g(Tecla.DIREITA, 0).paraSel)
        assertEquals("▶ da linha de baixo vai à última coluna (só a de cima)", 4, g(Tecla.DIREITA, 3).paraSel)
        assertNull("▶ na última coluna para", g(Tecla.DIREITA, 4).paraSel)
        assertNull("... sem trocar de mundo", g(Tecla.DIREITA, 4).tecla)
        assertEquals("◀ volta a coluna", 1, g(Tecla.ESQUERDA, 3).paraSel)
        assertNull("◀ na 1ª coluna para", g(Tecla.ESQUERDA, 1).paraSel)
        assertEquals("▼ na linha de cima desce", 3, g(Tecla.BAIXO, 2).paraSel)
        assertEquals("▼ na linha de baixo troca o mundo", StripHome.Passo(Tecla.DIREITA), g(Tecla.BAIXO, 3))
        assertEquals("▼ sem ninguém embaixo troca o mundo", StripHome.Passo(Tecla.DIREITA), g(Tecla.BAIXO, 4))
        assertEquals("▲ na linha de baixo sobe", 0, g(Tecla.CIMA, 1).paraSel)
        assertEquals("▲ na linha de cima troca o mundo", StripHome.Passo(Tecla.ESQUERDA), g(Tecla.CIMA, 2))
        assertTrue("▲ na linha de cima do 1º mundo sobe para a Ilha", g(Tecla.CIMA, 0, cat = 0).subirTopo)
        assertEquals(3, StripHome.colunas(5, true)); assertEquals(5, StripHome.colunas(5, false))
        assertEquals(1, StripHome.linhaDaGrade(3, true)); assertEquals(1, StripHome.coluna(3, true))
    }

    @Test fun jeitoDosCardsPorMundo() {
        val geral = StripHome.DEFAULT
        assertEquals("sem o dele, segue o geral", geral, StripHome.doMundo(geral, null))
        assertEquals(StripHome.Format.ROUND, StripHome.doMundo(geral, StripHome.Format.ROUND.ordinal).format)
        assertEquals("número estranho = o geral", geral, StripHome.doMundo(geral, 99))
        assertEquals("ordem guardada não muda", listOf(0, 1, 2), listOf(StripHome.Format.WIDE, StripHome.Format.CLASSIC, StripHome.Format.SQUARE).map { it.ordinal })
        val r = geral.copy(format = StripHome.Format.ROUND)
        assertEquals("redondo é círculo", r.cardH / 2f, r.raio)
        assertEquals(r.cardH + StripHome.NOME_EMBAIXO, r.blocoH)
        val gr = geral.copy(format = StripHome.Format.GRID)
        assertEquals(2 * gr.cardH + StripHome.VAO_GRADE, gr.blocoH)
        assertTrue(StripHome.Format.ONLY.banner && StripHome.Format.GRID.banner && StripHome.Format.LOGO.banner && !StripHome.Format.ROUND.banner)
        assertFalse(StripHome.Format.GRID.nomeNoFoco || StripHome.Format.ROUND.nomeNoFoco)
        val o = br.paulo.tvlauncher.home.WorldOptions.Options(cards = 5)
        assertEquals(o, br.paulo.tvlauncher.home.WorldOptions.Rules.read(br.paulo.tvlauncher.home.WorldOptions.Rules.write(o)))
        assertNull(br.paulo.tvlauncher.home.WorldOptions.Rules.read("{\"sort\":\"az\"}").cards)
    }
}
