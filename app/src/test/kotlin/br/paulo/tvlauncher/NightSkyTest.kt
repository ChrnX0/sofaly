package br.paulo.tvlauncher

import br.paulo.tvlauncher.screensaver.WidgetsTempo
import br.paulo.tvlauncher.weather.CeuNoturno
import br.paulo.tvlauncher.weather.EclipseSolar
import br.paulo.tvlauncher.weather.NightSky
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightSkyTest {
    private val hora = 3_600_000L
    private val sp = TimeZone.getTimeZone("America/Sao_Paulo")
    private val agora = 1795543200000L   // 24/11/2026 18:00Z = 15:00 em São Paulo

    /** 30 horas de previsão a partir de [de], com o mesmo céu e a mesma chance de chuva. */
    private fun horas(de: Long, codigo: Int, chuva: Int = 0) = (0 until 30).map { WidgetsTempo.Marca(de + it * hora, 20, codigo, chuva) }
    private fun passagem(em: Long, altura: Int) = CeuNoturno.Passagem(em, em + 5 * 60_000L, 5, 1, altura)

    @Test fun estacaoAltaComCeuLimpoAvisa() {
        val iss = passagem(agora + 5 * hora, 55)
        val e = NightSky.hoje(agora, -23.55, -46.63, horas(agora, 1), iss, emptyList(), sp)
        assertEquals(NightSky.Kind.ISS, e?.kind)
        assertEquals(iss, e?.pass)
    }

    @Test fun ceuFechadoChuvaOuSemPrevisaoCala() {
        val iss = passagem(agora + 5 * hora, 55)
        assertNull(NightSky.hoje(agora, -23.55, -46.63, horas(agora, 3), iss, emptyList(), sp))          // encoberto
        assertNull(NightSky.hoje(agora, -23.55, -46.63, horas(agora, 1, chuva = 60), iss, emptyList(), sp))   // vai chover
        assertNull(NightSky.hoje(agora, -23.55, -46.63, emptyList(), iss, emptyList(), sp))               // não sei: não avisa
    }

    @Test fun estacaoRasanteNaoValeASaida() {
        assertNull(NightSky.hoje(agora, -23.55, -46.63, horas(agora, 0), passagem(agora + 5 * hora, 30), emptyList(), sp))
    }

    @Test fun eclipseVemAntesDaEstacao() {
        val ecl = EclipseSolar("2026-11-25T13:00:00Z", "2026-11-25T14:00:00Z", "2026-11-25T15:00:00Z", 0.5)
        val e = NightSky.hoje(agora, -23.55, -46.63, horas(agora, 0), passagem(agora + 5 * hora, 70), listOf(ecl), sp)
        assertEquals(NightSky.Kind.ECLIPSE_SUN, e?.kind)
    }

    @Test fun meteorosFracosNaoContam() {
        // Leônidas (15/h) no pico, céu limpo: da cidade quase não se vê. Nada.
        val leonidas = 1794949200000L
        assertNull(NightSky.meteoros(leonidas, -23.55, sp))
    }

    @Test fun perseidasNoPicoComLuaNova() {
        // 12/08/2026, Londres: pico das Perseidas (100/h) e lua nova (eclipse do Sol nesse dia).
        val londres = TimeZone.getTimeZone("Europe/London")
        val t = 1786557600000L   // 18:00Z = 19:00 locais
        assertTrue(NightSky.luaIluminada(t) < 0.1)
        val e = NightSky.hoje(t, 51.5, -0.12, horas(t, 0), null, emptyList(), londres)
        assertEquals(NightSky.Kind.METEORS, e?.kind)
        assertEquals(CeuNoturno.ChuvaMeteoros.PERSEIDAS, e?.shower)
        assertTrue(e!!.start > t)   // começa quando escurece
        // Dois dias depois do pico já não é a noite delas.
        assertNull(NightSky.meteoros(t + 2 * 86_400_000L, 51.5, londres))
    }

    @Test fun janelasDaIlhaEDoDescanso() {
        val e = NightSky.Event(NightSky.Kind.ISS, agora + 5 * hora, agora + 5 * hora + 300_000L)
        assertFalse(NightSky.naIlha(e, agora + 1 * hora))                // 4 h antes: cedo demais
        assertTrue(NightSky.naIlha(e, agora + 2 * hora + 60_000L))       // menos de 3 h antes
        assertFalse(NightSky.noDescanso(e, agora + 4 * hora))            // 1 h antes: só a Ilha
        assertTrue(NightSky.noDescanso(e, agora + 5 * hora - 10 * 60_000L))
        assertFalse(NightSky.naIlha(e, e.end))                           // acabou: some
    }

    @Test fun limpoPelaHoraCheia() {
        val h = listOf(WidgetsTempo.Marca(0, 20, 1, 10), WidgetsTempo.Marca(hora, 20, 45, 10))
        assertEquals(true, NightSky.limpo(h, 30 * 60_000L))
        assertEquals(false, NightSky.limpo(h, hora + 60_000L))          // neblina
        assertNull(NightSky.limpo(h, 5 * hora))                          // sem marca perto
    }
}
