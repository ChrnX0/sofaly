package br.paulo.tvlauncher.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageTallyTest {
    @Test fun agrupaPorOrigemMaiorSomaPrimeiro() {
        val linhas = ImageTally.group(listOf(
            ImageTally.Entry("home/DesenhoHome", 96, 96, 36_864),
            ImageTally.Entry("home/Fundos", 1920, 1080, 8_294_400),
            ImageTally.Entry("home/DesenhoHome", 128, 128, 65_536),
        ))
        assertEquals(listOf("home/Fundos", "home/DesenhoHome"), linhas.map { it.origin })
        val icones = linhas[1]
        assertEquals(2, icones.count)
        assertEquals(102_400L, icones.bytes)
        assertEquals(128, icones.largestWidth)   // o maior em bytes, não o primeiro
    }

    @Test fun empateSaiEmOrdemDeNome() {
        val l = ImageTally.group(listOf(ImageTally.Entry("b", 1, 1, 4), ImageTally.Entry("a", 1, 1, 4)))
        assertEquals(listOf("a", "b"), l.map { it.origin })
    }

    @Test fun vazioDaListaVazia() = assertEquals(emptyList<ImageTally.Line>(), ImageTally.group(emptyList()))

    @Test fun mbComUmaCasa() {
        assertEquals(7.9, ImageTally.mb(8_294_400), 0.0)   // papel de parede 1920x1080 ARGB
        assertEquals(0.0, ImageTally.mb(0), 0.0)
    }

    @Test fun origemDoCaminhoDoArquivo() {
        assertEquals("home/Fundos", ImageTally.originOf("app/src/main/kotlin/br/paulo/tvlauncher/home/Fundos.kt"))
        assertEquals("MainActivity", ImageTally.originOf("C:\\x\\br\\paulo\\tvlauncher\\MainActivity.kt"))
    }
}
