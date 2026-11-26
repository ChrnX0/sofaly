package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.ServicoSofaly
import br.paulo.tvlauncher.observer.ServicoSofaly.Estado
import org.junit.Assert.assertEquals
import org.junit.Test

/** Linha "Sofaly" do Diagnóstico (Brainstorm 26/11/2026, item 25). */
class ServicoSofalyTest {
    @Test fun dizQualParteEstaFora() {
        val r = ServicoSofaly.ler(200, """{"ok":false,"services":{"podcasts":true,"weather_open":false,"weather_pro":false,"ai":true,"community":false,"mailbox":true}}""")
        assertEquals(Estado.FALTA, r.estado)
        assertEquals(listOf("weather_open", "community"), r.faltando)   // o clima Pro não aparece
        assertEquals(1, r.nivel)
    }

    @Test fun tudoCertoMesmoSemOPro() {
        val r = ServicoSofaly.ler(200, """{"ok":false,"services":{"podcasts":true,"weather_open":true,"weather_pro":false,"ai":true,"community":true,"mailbox":true}}""")
        assertEquals(Estado.OK, r.estado); assertEquals(0, r.nivel)
    }

    @Test fun desligadoSemRespostaEServidorAntigo() {
        assertEquals(Estado.DESLIGADO, ServicoSofaly.ler(503, null).estado)
        assertEquals(Estado.SEM_RESPOSTA, ServicoSofaly.ler(-1, null).estado)
        assertEquals(2, ServicoSofaly.ler(500, "x").nivel)
        assertEquals(Estado.OK, ServicoSofaly.ler(200, """{"ok":true}""").estado)
    }
}
