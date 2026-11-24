package br.paulo.tvlauncher

import br.paulo.tvlauncher.network.RedeMensal
import br.paulo.tvlauncher.player.GravacaoRegras
import br.paulo.tvlauncher.player.LegendaExterna
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Revisão de lógica (24/11/2026): "%02d".format usa o idioma da TV, e em árabe sai "٠٧". Texto que outra máquina lê
 * (legenda SRT, nome de arquivo, JSON do painel e do MCP) tem de sair sempre com algarismos 0-9.
 */
class NumerosDeMaquinaTest {
    private val antes = Locale.getDefault()
    @Before fun arabe() { Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab")) }
    @After fun volta() { Locale.setDefault(antes) }

    private fun soLatinos(s: String) = s.none { it in '٠'..'٩' || it in '۰'..'۹' }

    @Test fun legendaDeslocadaContinuaSrt() {
        val r = LegendaExterna.deslocar("1\n00:00:01,000 --> 00:00:02,500\nOi", 1000)
        assertTrue(r, r.contains("00:00:02,000 --> 00:00:03,500"))
    }

    @Test fun nomeDaGravacaoComAlgarismosComuns() {
        val n = GravacaoRegras.nomeArquivo(0L, "Canal", null, 1, TimeZone.getTimeZone("UTC"))
        assertTrue(n, n.startsWith("1970-01-01 0000"))
    }

    @Test fun mesDoHistoricoDeRedeComAlgarismosComuns() {
        val m = RedeMensal.meses(listOf(RedeMensal.Dia(LocalDate.of(2026, 11, 24), 0, 0, 0, 0)))
        assertEquals("2026-11", m.first().mes)
        assertTrue(soLatinos(m.first().mes))
    }
}
