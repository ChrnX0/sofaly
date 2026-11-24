package br.paulo.tvlauncher.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** O pé da ficha do canal (24/11/2026): nada cortado, a dica cede antes dos programas, rótulo sozinho some. */
class RodapeContaTest {
    private val dica = listOf(50, 50, 50, 50)          // 4 linhas de tecla
    private val topo = listOf(70, 100, 100, 100)        // "DEPOIS" + 3 programas

    @Test fun sobraEspacoMostraTudo() = assertEquals(4 to 4, RodapeConta.escolher(topo, dica, 1000, 10))

    @Test fun dicaCedeAteCaberemDoisProgramas() =
        // 400 px: com a dica inteira (200 + 10) sobram 190, só o rótulo cabe. Tirando linhas da dica uma a uma: 3 linhas ainda
        // deixa 340; com 2 linhas (100+10) sobram 290 = rótulo + 2 programas.
        assertEquals(3 to 2, RodapeConta.escolher(topo, dica, 400, 10))

    @Test fun rotuloSozinhoSome() = assertEquals(0 to 1, RodapeConta.escolher(topo, dica, 150, 10))

    @Test fun semProgramasADicaFicaInteira() = assertEquals(0 to 4, RodapeConta.escolher(emptyList(), dica, 250, 10))

    @Test fun nadaCabe() = assertEquals(0 to 0, RodapeConta.escolher(topo, dica, 30, 10))

    @Test fun umProgramaSoNaoTiraDicaAMais() =
        // Só 1 programa a seguir: a dica perde linhas até ele caber, não além.
        assertEquals(2 to 3, RodapeConta.escolher(listOf(70, 100), dica, 340, 10))
}
