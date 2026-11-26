package br.paulo.tvlauncher.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** Onda C (25/11/2026): no modo Simples cada aba mostra só as 5 linhas mais usadas; empate fica com a ordem da aba. */
class PortasSimplesTest {
    @Test fun tvNovaMostraAsPrimeirasCinco() = assertEquals(setOf(0, 1, 2, 3, 4), PortasConfig.noSimples(List(9) { 0 }))

    @Test fun maisUsadasSobem() =
        assertEquals(setOf(0, 1, 2, 6, 8), PortasConfig.noSimples(listOf(0, 0, 0, 0, 0, 0, 3, 0, 9)))

    @Test fun empateFicaComAOrdem() =
        assertEquals(setOf(5, 0, 1, 2, 3), PortasConfig.noSimples(listOf(0, 0, 0, 0, 0, 1, 0)))

    @Test fun abaCurtaFicaInteira() = assertEquals(setOf(0, 1), PortasConfig.noSimples(listOf(4, 0)))
}
