package br.paulo.tvlauncher.weather

import org.junit.Assert.assertEquals
import org.junit.Test

/** A tela usa o orvalho da MESMA medida que a umidade do topo, não o do modelo (24/11/2026, revisor). */
class UsarTopoTest {
    private fun dados() = ForecastData(
        Localizacao.Ponto(0.0, 0.0, "teste"), "2026-11-24T15:10", 34.0, 36.0, 60, 1, 1012.0, 9.0, 90, true,
        listOf("2026-11-24T15:00"), listOf(34.0), listOf(10), listOf(1), listOf(9.0), listOf(10000.0), listOf(22.0), listOf(5.0),
        listOf("2026-11-24"), listOf(1), listOf(35.0), listOf(20.0), listOf(10), listOf("2026-11-24T05:20"), listOf("2026-11-24T18:40"), 0.0, null)

    @Test fun semTopoUsaOModelo() = assertEquals(22.0, dados().orvalho(), 0.01)

    @Test fun comTopoOrvalhoConcordaComAUmidade() {
        val d = dados().apply { usarTopo(34, 41, 3, fahrenheit = false) }
        assertEquals(41, d.umidade); assertEquals(3, d.codigo)
        assertEquals(18.9, d.orvalho(), 0.3)
    }

    @Test fun topoSemUmidadeNaoMexeNoOrvalho() {
        val d = dados().apply { usarTopo(34, null, null, fahrenheit = false) }
        assertEquals(60, d.umidade); assertEquals(22.0, d.orvalho(), 0.01)
    }
}
