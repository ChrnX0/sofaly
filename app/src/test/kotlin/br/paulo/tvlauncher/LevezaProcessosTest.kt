package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.LevezaTv
import org.junit.Assert.assertEquals
import org.junit.Test

/** Régua da refatoração G: as partes da memória de cada processo saem do resumo do Android em MB. */
class LevezaProcessosTest {
    @Test fun converteKbParaMbComUmaCasa() {
        val m = LevezaTv.partesMb(mapOf("summary.java-heap" to "8076", "summary.native-heap" to "18896", "summary.graphics" to "77824"))
        assertEquals(7.9, m.getValue("javaMb"), 0.0)
        assertEquals(18.5, m.getValue("nativoMb"), 0.0)
        assertEquals(76.0, m.getValue("graficosMb"), 0.0)
    }

    @Test fun parteQueFaltaOuNaoNumericaViraZero() {
        val m = LevezaTv.partesMb(mapOf("summary.code" to "?"))
        assertEquals(0.0, m.getValue("codigoMb"), 0.0)
        assertEquals(0.0, m.getValue("pilhaMb"), 0.0)
        assertEquals(LevezaTv.PARTES.keys, m.keys)
    }
}
