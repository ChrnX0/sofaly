package br.paulo.tvlauncher

import br.paulo.tvlauncher.weather.Orvalho
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ponto de orvalho pela umidade medida: tem de concordar com a umidade que a tela mostra (24/11/2026). */
class OrvalhoTest {
    // Valores de referência da tabela psicrométrica (Magnus-Sonntag), tolerância de 0,5°.
    @Test fun caso_da_sala() = assertEquals(19.0, Orvalho.de(34.0, 41), 0.5)
    @Test fun saturado_e_a_propria_temperatura() = assertEquals(20.0, Orvalho.de(20.0, 100), 0.05)
    @Test fun seco_e_frio() = assertEquals(-10.2, Orvalho.de(10.0, 23), 0.5)
    @Test fun fahrenheit() = assertEquals(66.2, Orvalho.de(93.2, 41, fahrenheit = true), 1.0)
    @Test fun umidade_zero_nao_explode() { val d = Orvalho.de(30.0, 0); assert(d.isFinite() && d < 0) }
}
