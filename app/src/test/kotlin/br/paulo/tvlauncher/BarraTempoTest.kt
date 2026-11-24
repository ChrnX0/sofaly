package br.paulo.tvlauncher

import br.paulo.tvlauncher.player.BarraTempo
import org.junit.Assert.assertEquals
import org.junit.Test

class BarraTempoTest {
    @Test fun duracaoArredondaParaCima() {
        assertEquals("1 min", BarraTempo.duracao(0))
        assertEquals("1 min", BarraTempo.duracao(20_000))
        assertEquals("22 min", BarraTempo.duracao(21 * 60_000L + 1))
        assertEquals("59 min", BarraTempo.duracao(59 * 60_000L))
        assertEquals("1 h", BarraTempo.duracao(60 * 60_000L))
        assertEquals("1 h 5 min", BarraTempo.duracao(65 * 60_000L))
        assertEquals("2 h 30 min", BarraTempo.duracao(150 * 60_000L))
    }

    @Test fun duracaoNaLinguaDaTv() {
        val pt = java.util.Locale("pt", "BR")
        assertEquals("1 h 4 min", BarraTempo.duracao(64 * 60_000L, pt))
        assertEquals("1 h 4 min", BarraTempo.duracao(64 * 60_000L, java.util.Locale.US))
        assertEquals("1小时4分钟", BarraTempo.duracao(64 * 60_000L, java.util.Locale.SIMPLIFIED_CHINESE))
        assertEquals("1\u00A0год 4\u00A0хв", BarraTempo.duracao(64 * 60_000L, java.util.Locale("uk")))
        // Árabe: algarismos e unidades da língua, nada de "h"/"min" latinos no meio da frase.
        val ar = BarraTempo.duracao(64 * 60_000L, java.util.Locale("ar"))
        assertEquals(false, ar.contains("h") || ar.contains("min"))
    }

    @Test fun fracaoFicaEntreZeroEUm() {
        assertEquals(0f, BarraTempo.fracao(10, 0), 0f)
        assertEquals(0.5f, BarraTempo.fracao(50, 100), 0f)
        assertEquals(1f, BarraTempo.fracao(150, 100), 0f)
        assertEquals(0f, BarraTempo.fracao(-5, 100), 0f)
    }
}
