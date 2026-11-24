package br.paulo.tvlauncher

import br.paulo.tvlauncher.screensaver.WidgetsTempo
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetsTempoTest {
    private val fuso = TimeZone.getTimeZone("GMT-3")
    private val hora = 60 * 60_000L

    /** 18 horas a partir das 21h de 14/11, com a chance de chuva dada. */
    private fun horas(vararg chuva: Int): String = chuva.mapIndexed { i, c ->
        val h = 21 + i
        val dia = if (h < 24) "14" else "15"
        "2026-11-${dia}T%02d:00|${20 - i % 3}|61|$c".format(h % 24)
    }.joinToString(";")

    private val base = WidgetsTempo.lerHoras(horas(0), fuso).first().ms   // 21:00

    @Test fun leAsHorasEPulaLinhaQuebrada() {
        val l = WidgetsTempo.lerHoras("2026-11-14T21:00|22|3|40;lixo;2026-11-14T22:00|x|3|10;2026-11-14T23:00|21|61|-1", fuso)
        assertEquals(2, l.size)
        assertEquals(WidgetsTempo.Marca(base, 22, 3, 40), l[0])
        assertEquals(-1, l[1].chuva)
        assertTrue(WidgetsTempo.lerHoras("", fuso).isEmpty())
    }
}
