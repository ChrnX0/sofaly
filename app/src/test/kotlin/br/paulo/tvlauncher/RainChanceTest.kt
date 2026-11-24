package br.paulo.tvlauncher

import br.paulo.tvlauncher.weather.RainChance
import org.junit.Assert.assertEquals
import org.junit.Test

/** RainChance (24/11/2026): as palavras da chance de chuva em terços e a roda do ajuste. */
class RainChanceTest {
    @Test fun faixas() {
        assertEquals(0, RainChance.faixa(0))
        assertEquals(0, RainChance.faixa(33))
        assertEquals(1, RainChance.faixa(34))
        assertEquals(1, RainChance.faixa(66))
        assertEquals(2, RainChance.faixa(67))
        assertEquals(2, RainChance.faixa(100))
    }

    @Test fun rodaVoltaAoNumero() {
        var m = RainChance.Modo.NUMERO
        repeat(RainChance.Modo.entries.size) { m = RainChance.proximo(m) }
        assertEquals(RainChance.Modo.NUMERO, m)
        assertEquals(RainChance.Modo.PALAVRA, RainChance.proximo(RainChance.Modo.NUMERO))
    }
}
