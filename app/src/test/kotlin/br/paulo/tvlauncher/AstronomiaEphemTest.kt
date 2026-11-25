package br.paulo.tvlauncher

import br.paulo.tvlauncher.weather.Astronomia
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dado do mundo real contra fonte externa (pedido do Paulo, 25/11/2026, depois da lua errada): nascer e pôr do sol que
 * o protetor e a HOME mostram, comparados com o PyEphem 4.x (`Observer.previous_rising/next_setting`, horizonte -0°50',
 * sem refração extra) em São Paulo, nas estações do ano. Tolerância de 2 min (a TV mostra hora:minuto).
 */
class AstronomiaEphemTest {
    private val sp = TimeZone.getTimeZone("America/Sao_Paulo")

    @Test fun nascerEPorBatemComOEphem() {
        val casos = listOf(   // dia ao meio-dia UTC, nascer e pôr em ms UTC (PyEphem)
            Triple(1768478400000L, 1768465914364L, 1768514387858L),   // 15/01/2026
            Triple(1776254400000L, 1776244740777L, 1776286420223L),   // 15/04/2026
            Triple(1782043200000L, 1782035196020L, 1782073807967L),   // 21/06/2026
            Triple(1790078400000L, 1790067296083L, 1790111028642L),   // 22/09/2026
            Triple(1795608000000L, 1795594210041L, 1795642626829L),   // 25/11/2026
            Triple(1797854400000L, 1797840935801L, 1797890022194L),   // 21/12/2026
        )
        for ((dia, nascer, por) in casos) {
            val (n, p) = Astronomia.nascerEPor(dia, -23.55, -46.63, sp)!!
            assertEquals("nascer $dia", nascer.toDouble(), n.toDouble(), 120_000.0)
            assertEquals("pôr $dia", por.toDouble(), p.toDouble(), 120_000.0)
        }
    }
}
