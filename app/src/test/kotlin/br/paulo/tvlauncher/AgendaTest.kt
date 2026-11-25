package br.paulo.tvlauncher

import br.paulo.tvlauncher.alarm.Agenda
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ideia 32: agenda de todas as contas juntas, sem repetir o mesmo compromisso. */
class AgendaTest {
    private fun ev(t: String, i: Long, f: Long = i + 3_600_000) = Agenda.Evento(t, i, f, false, 0)

    @Test fun mesmoCompromissoEmDuasContasApareceUmaVez() {
        val l = Agenda.juntar(listOf(ev("Dentista", 2000), ev("Aniversário", 1000), ev("dentista ", 2000)))
        assertEquals(listOf("Aniversário", "Dentista"), l.map { it.titulo })
    }

    @Test fun mesmoTituloEmHorarioDiferenteFica() {
        assertEquals(2, Agenda.juntar(listOf(ev("Futebol", 1000), ev("Futebol", 5000))).size)
        assertEquals(2, Agenda.juntar(listOf(ev("Futebol", 1000, 2000), ev("Futebol", 1000, 3000))).size)
    }

    @Test fun feriadosEmDoisIdiomasFicaOdaTv() {
        val pt = "pt-br.brazilian#holiday@group.v.calendar.google.com"; val en = "en.brazilian#holiday@group.v.calendar.google.com"
        val l = listOf(Agenda.Evento("Proclamação da República", 1000, 2000, true, 0, pt),
            Agenda.Evento("Republic Proclamation Day", 1000, 2000, true, 0, en), ev("Dentista", 3000))
        assertEquals(listOf("Proclamação da República", "Dentista"), Agenda.juntar(l, "pt").map { it.titulo })
        assertEquals(listOf("Republic Proclamation Day", "Dentista"), Agenda.juntar(l, "en").map { it.titulo })
        assertEquals(2, Agenda.juntar(l, "es").size)   // idioma sem agenda própria: fica só a 1ª (pt)
    }

    @Test fun idiomaDaAgendaDeFeriados() {
        assertEquals("pt", Agenda.idiomaFeriados("pt-br.brazilian#holiday@group.v.calendar.google.com"))
        assertEquals("en", Agenda.idiomaFeriados("en.brazilian#holiday@group.v.calendar.google.com"))
        assertEquals(null, Agenda.idiomaFeriados("alguem@gmail.com"))
    }

    @Test fun feriadosDeOutroPaisSaemQuandoHaODoPaisDaTv() {
        // Caso real da Sala (25/11/2026): Brasil em pt e en, EUA e Reino Unido em en, TV em português no Brasil.
        val ptBr = "pt.brazilian#holiday@group.v.calendar.google.com"; val enBr = "en.brazilian#holiday@group.v.calendar.google.com"
        val us = "en.usa#holiday@group.v.calendar.google.com"; val uk = "en.uk#holiday@group.v.calendar.google.com"
        val l = listOf(Agenda.Evento("Thanksgiving Day", 1000, 2000, true, 0, us), Agenda.Evento("Black Friday", 3000, 4000, true, 0, us),
            Agenda.Evento("St Andrew's Day", 5000, 6000, true, 0, uk), Agenda.Evento("Natal", 7000, 8000, true, 0, ptBr),
            Agenda.Evento("Christmas Day", 7000, 8000, true, 0, enBr), ev("Dentista", 9000))
        assertEquals(listOf("Natal", "Dentista"), Agenda.juntar(l, "pt", "BR").map { it.titulo })
        assertEquals(listOf("Christmas Day", "Dentista"), Agenda.juntar(l, "en", "BR").map { it.titulo })
        assertEquals(listOf("Thanksgiving Day", "Black Friday", "Dentista"), Agenda.juntar(l, "en", "US").map { it.titulo })
        // País da TV sem agenda própria (ou desconhecido): nada sai por país, só a regra do idioma.
        assertEquals(5, Agenda.juntar(l, "en", "DE").size)
        assertEquals(5, Agenda.juntar(l, "en", null).size)
    }

    @Test fun paisDaAgendaDeFeriados() {
        assertEquals("BR", Agenda.paisFeriados("pt-br.brazilian#holiday@group.v.calendar.google.com"))
        assertEquals("US", Agenda.paisFeriados("en.usa#holiday@group.v.calendar.google.com"))
        assertEquals(null, Agenda.paisFeriados("en.atlantis#holiday@group.v.calendar.google.com"))
        assertEquals(null, Agenda.paisFeriados("alguem@gmail.com"))
    }

    @Test fun mesmoFeriadoNaAgendaPessoalApareceUmaVez() {
        // Agenda real da Sala (25/11/2026): a agenda pessoal também traz os feriados, com outro nome.
        val ptBr = "pt.brazilian#holiday@group.v.calendar.google.com"; val eu = "alguem@exemplo.com"
        val l = listOf(Agenda.Evento("Véspera de Natal", 1000, 2000, true, 0, eu),
            Agenda.Evento("Véspera de Natal (após às 14 horas)", 1000, 2000, true, 0, ptBr),
            Agenda.Evento("Dia de Natal", 2000, 3000, true, 0, eu), Agenda.Evento("Natal", 2000, 3000, true, 0, ptBr),
            Agenda.Evento("Natal na casa da vó", 2000, 3000, true, 0, eu), Agenda.Evento("Natal", 2000, 2500, false, 0, eu))
        assertEquals(listOf("Véspera de Natal (após às 14 horas)", "Natal", "Natal na casa da vó", "Natal"),
            Agenda.juntar(l, "pt", "BR").map { it.titulo })
    }
}
