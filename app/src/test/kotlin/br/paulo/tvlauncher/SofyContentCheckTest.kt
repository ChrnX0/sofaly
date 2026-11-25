package br.paulo.tvlauncher

import br.paulo.tvlauncher.alarm.Agenda
import br.paulo.tvlauncher.observer.SofyContentCheck
import br.paulo.tvlauncher.observer.SofyContentCheck.Defeito
import br.paulo.tvlauncher.observer.SofyScreenCheck.Caixa
import br.paulo.tvlauncher.observer.SofyScreenCheck.Tela
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cada caso é um defeito real da Sala (25/11/2026) que a Revisão achou e a Sofy passa a achar sozinha. */
class SofyContentCheckTest {
    private val ptBr = "pt.brazilian#holiday@group.v.calendar.google.com"
    private val enBr = "en.brazilian#holiday@group.v.calendar.google.com"
    private val us = "en.usa#holiday@group.v.calendar.google.com"
    private val uk = "en.uk#holiday@group.v.calendar.google.com"
    private fun ev(t: String, ini: Long, dono: String) = Agenda.Evento(t, ini, ini + 1000, true, 0, dono)
    private fun tela(nome: String, vararg textos: String) =
        Tela(nome, 1920, 1080, textos.mapIndexed { i, s -> Caixa(s, 96, 100 + i * 60, 600, 150 + i * 60) }, true)
    private fun defeitos(t: List<Tela>, r: SofyContentCheck.Retrato) = SofyContentCheck.conferir(t, r).map { it.defeito }

    @Test fun feriadoDosEuaNoProtetorDeUmaTvNoBrasil() {
        // Foto de 17:22Z: só feriados dos EUA nos 2 dias; a agenda do Brasil assinada, sem evento na janela (#500).
        val r = SofyContentCheck.Retrato(pais = "BR", eventos = listOf(ev("Thanksgiving Day", 1, us), ev("Black Friday", 2, us)),
            agendas = listOf(ptBr, enBr, us, uk, "<conta>"))
        val achados = SofyContentCheck.conferir(listOf(tela("protetor", "Thanksgiving Day", "Amanhã", "Black Friday", "sexta-feira")), r)
        assertEquals(listOf(Defeito.FERIADO_DE_FORA, Defeito.FERIADO_DE_FORA), achados.map { it.defeito })
        assertEquals(setOf("Thanksgiving Day", "Black Friday"), achados.map { it.onde }.toSet())
        // Depois do conserto a tela não mostra mais: nada a acusar.
        assertTrue(defeitos(listOf(tela("protetor", "Fase", "Lua cheia")), r).isEmpty())
    }

    @Test fun feriadoDeForaSoContaQuandoODoPaisEstaAssinado() {
        val r = SofyContentCheck.Retrato(pais = "BR", eventos = listOf(ev("Thanksgiving Day", 1, us)), agendas = listOf(us))
        assertTrue(defeitos(listOf(tela("protetor", "Thanksgiving Day")), r).isEmpty())
        assertTrue(defeitos(listOf(tela("protetor", "Thanksgiving Day")), r.copy(pais = null)).isEmpty())
    }

    @Test fun mesmoFeriadoComDoisNomesNaMesmaTela() {
        // Agenda real (#497): "Natal" da agenda de feriados e "Dia de Natal" da pessoal, no mesmo dia.
        val r = SofyContentCheck.Retrato(pais = "BR", eventos = listOf(ev("Natal", 7, ptBr), ev("Dia de Natal", 7, "<conta>"),
            ev("Véspera de Natal", 6, ptBr), ev("Véspera de Natal (após às 14 horas)", 6, "<conta>")))
        val a = SofyContentCheck.conferir(listOf(tela("agenda", "Véspera de Natal", "Véspera de Natal (após às 14 horas)", "Natal", "Dia de Natal")), r)
        assertEquals(listOf(Defeito.REPETIDO, Defeito.REPETIDO), a.map { it.defeito })
        // Um nome só na tela: ok. E o mesmo texto que não é da agenda ("Hoje" em dois lugares) não conta.
        assertTrue(defeitos(listOf(tela("agenda", "Natal", "Hoje", "Hoje")), r).isEmpty())
    }

    @Test fun iconeSemChuvaComAFraseDeChuvaAgora() {
        // #498: "parcialmente nublado" ao lado de "Chuva provável agora (72%)".
        val r = SofyContentCheck.Retrato(chuvaAgora = true, icones = mapOf("home" to "clima:3d/sun_behind_cloud", "protetor" to "clima:3d/sun_behind_rain_cloud"))
        val a = SofyContentCheck.conferir(emptyList(), r)
        assertEquals(listOf(SofyContentCheck.Achado(Defeito.CHUVA_DESENCONTRADA, "home", "clima:3d/sun_behind_cloud")), a)
        assertTrue(SofyContentCheck.conferir(emptyList(), r.copy(chuvaAgora = false)).isEmpty())
        assertTrue(Defeito.CHUVA_DESENCONTRADA in SofyContentCheck.deDado)
    }

    @Test fun hojeNaFaixaDosDiasNaoContaATemperaturaDeAgora() {
        // Protetor 18:05Z: 30° agora e "Hoje 27° 22°" (o topo e o Clima já corrigiam; a faixa não).
        val faixa = Tela("protetor.dias", 1920, 1080, listOf(
            Caixa("Hoje", 96, 870, 180, 910), Caixa("27° 22°", 96, 920, 260, 970), Caixa("100%", 96, 980, 190, 1020),
            Caixa("Qui", 342, 870, 400, 910), Caixa("29° 21°", 342, 920, 500, 970)), true)
        val r = SofyContentCheck.Retrato(tempAgora = 30)
        assertEquals(27 to 22, SofyContentCheck.hojeNaFaixa(faixa, r.rotulosHoje))
        assertEquals(listOf(Defeito.HOJE_NAO_CONTA_AGORA), defeitos(listOf(faixa), r))
        assertTrue(defeitos(listOf(faixa), r.copy(tempAgora = 25)).isEmpty())
        // Mínima acima de agora (madrugada fria) também é defeito.
        assertEquals(listOf(Defeito.HOJE_NAO_CONTA_AGORA), defeitos(listOf(faixa), r.copy(tempAgora = 20)))
        // Topo do protetor ("29°/22°") sem "Hoje" ao lado: não é faixa de dias, não confere.
        assertEquals(null, SofyContentCheck.hojeNaFaixa(tela("topo", "29°", "29°/22°"), r.rotulosHoje))
    }

    @Test fun duracaoAbsurdaEPorcentoForaDeFaixa() {
        // #497: ficha com "498783 h" (data zerada).
        assertEquals(listOf(Defeito.DURACAO_ABSURDA), defeitos(listOf(tela("fichas", "YouTube", "498783 h")), SofyContentCheck.Retrato()))
        assertEquals(listOf(Defeito.PORCENTO_FORA_DE_FAIXA), defeitos(listOf(tela("clima", "120%")), SofyContentCheck.Retrato()))
        // O que é normal não acusa: horas pequenas, ano, versão, 100%, "13h15".
        assertTrue(defeitos(listOf(tela("x", "faltam 2 h", "2026", "v473391", "100%", "13h15", "1012 hPa", "87%")), SofyContentCheck.Retrato()).isEmpty())
    }

    @Test fun botaoMudo() {
        assertTrue(SofyContentCheck.botaoMudo(listOf("Buscar"), listOf("Buscar"), "Buscar", "Buscar", false))
        assertFalse(SofyContentCheck.botaoMudo(listOf("Buscar"), listOf("Buscar"), "Buscar", "Buscar", true))
        assertFalse(SofyContentCheck.botaoMudo(listOf("Buscar"), listOf("Buscar", "Teclado"), "Buscar", "Buscar", false))
        assertFalse(SofyContentCheck.botaoMudo(listOf("Buscar"), listOf("Buscar"), "Buscar", "Apps", false))
    }

    @Test fun resumoCurto() {
        assertEquals("conteúdo ok", SofyContentCheck.resumo(emptyList()))
        val s = SofyContentCheck.resumo(listOf(SofyContentCheck.Achado(Defeito.FERIADO_DE_FORA, "protetor", "Black Friday")))
        assertEquals("FERIADO_DE_FORA x1 (protetor: Black Friday)", s)
    }
}
