package br.paulo.tvlauncher

import br.paulo.tvlauncher.family.CuidarDeLonge
import br.paulo.tvlauncher.family.CuidarDeLonge.Config
import br.paulo.tvlauncher.family.CuidarDeLonge.Estado
import br.paulo.tvlauncher.family.CuidarDeLonge.Lembrete
import br.paulo.tvlauncher.home.HomeCards
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A lista única de lembretes da casa (26/11/2026): o antigo "lembrete da casa" da pílula (HomeCards) e os lembretes
 * com "Feito" do Cuidar de longe passaram a ser uma lista só.
 */
class HouseRemindersTest {
    private val fuso = TimeZone.getTimeZone("America/Sao_Paulo")
    private fun ms(t: String) = LocalDateTime.parse(t).atZone(ZoneId.of("America/Sao_Paulo")).toInstant().toEpochMilli()

    @Test fun diasDeSegundaViramDiasDeDomingoEVoltam() {
        val seg = HomeCards.bit(DayOfWeek.MONDAY)
        val dom = HomeCards.bit(DayOfWeek.SUNDAY)
        assertEquals(1 shl 1, CuidarDeLonge.diasDeSegunda(seg))   // segunda = bit 1 no despertador (domingo = 0)
        assertEquals(1, CuidarDeLonge.diasDeSegunda(dom))
        assertEquals(CuidarDeLonge.TODO_DIA, CuidarDeLonge.diasDeSegunda(HomeCards.TODOS_OS_DIAS))
        for (d in 1..127) assertEquals(d, CuidarDeLonge.diasParaSegunda(CuidarDeLonge.diasDeSegunda(d)))
    }

    @Test fun lembreteDaCasaSemHoraViraDiaTodoSemFeito() {
        val casa = HomeCards.criarLembrete("Lixo reciclável", HomeCards.bit(DayOfWeek.TUESDAY), null)!!
        val l = CuidarDeLonge.salvarLembrete(emptyList(), CuidarDeLonge.daCasa(casa)).single()
        assertTrue(l.diaTodo)
        assertFalse(l.feito)
        assertEquals("", l.hhmm)
        // E volta igual para a pílula.
        assertEquals(casa, CuidarDeLonge.paraCasa(l))
    }

    @Test fun lembreteDaCasaComHoraMantemAHora() {
        val casa = HomeCards.criarLembrete("Regar as plantas", HomeCards.TODOS_OS_DIAS, 18 * 60 + 30)!!
        val l = CuidarDeLonge.daCasa(casa)
        assertEquals(18, l.hora); assertEquals(30, l.minuto); assertFalse(l.feito)
        assertEquals(casa, CuidarDeLonge.paraCasa(l))
    }

    @Test fun diaTodoComFeitoNaoEntra() {
        // Sem hora não há tela grande nem prazo para confirmar: o Feito exige hora.
        val l = Lembrete(0, -1, -1, CuidarDeLonge.TODO_DIA, "Remédio", feito = true)
        assertTrue(CuidarDeLonge.salvarLembrete(emptyList(), l).isEmpty())
    }

    @Test fun juntarNaoDuplicaEMantemOIdDoCuidar() {
        val remedio = Lembrete(7, 20, 0, CuidarDeLonge.TODO_DIA, "Remédio da pressão")
        val lixo = CuidarDeLonge.daCasa(HomeCards.criarLembrete("Lixo", HomeCards.TODOS_OS_DIAS, null)!!)
        val uma = CuidarDeLonge.juntar(emptyList(), listOf(remedio, lixo))
        assertEquals(2, uma.size)
        assertEquals(7, uma.first { it.feito }.id)   // as marcas do dia usam o id
        // Rodar a migração de novo (backup antigo restaurado) não duplica.
        assertEquals(uma, CuidarDeLonge.juntar(uma, listOf(remedio, lixo)))
    }

    @Test fun juntarComIdRepetidoGanhaIdNovo() {
        val a = Lembrete(1, 8, 0, CuidarDeLonge.TODO_DIA, "Água")
        val b = Lembrete(1, 9, 0, CuidarDeLonge.TODO_DIA, "Caminhar", feito = false)
        val r = CuidarDeLonge.juntar(listOf(a), listOf(b))
        assertEquals(2, r.size)
        assertEquals(2, r.map { it.id }.toSet().size)
    }

    @Test fun cartaoDaCasaNaoMexeNosComFeito() {
        val remedio = Lembrete(3, 20, 0, CuidarDeLonge.TODO_DIA, "Remédio")
        val lixo = Lembrete(4, -1, -1, CuidarDeLonge.TODO_DIA, "Lixo", feito = false)
        val lista = listOf(remedio, lixo)
        // O painel (sem PIN) tirou o "Lixo" e pôs "Feira": o remédio continua, com o mesmo id.
        val casa = HomeCards.adicionarLembrete(emptyList(), HomeCards.criarLembrete("Feira", HomeCards.TODOS_OS_DIAS, 7 * 60)!!)
        val r = CuidarDeLonge.trocarDaCasa(lista, casa)
        assertEquals(setOf("Remédio", "Feira"), r.map { it.texto }.toSet())
        assertEquals(3, r.first { it.texto == "Remédio" }.id)
        assertFalse(r.first { it.texto == "Feira" }.feito)
        // Quem continua com o mesmo texto mantém o id.
        val r2 = CuidarDeLonge.trocarDaCasa(lista, listOf(CuidarDeLonge.paraCasa(lixo)))
        assertEquals(4, r2.first { it.texto == "Lixo" }.id)
    }

    @Test fun semFeitoNaoAbreTelaGrandeNemAvisaAFamilia() {
        val regar = Lembrete(1, 18, 0, CuidarDeLonge.TODO_DIA, "Regar", feito = false)
        val c = Config(nome = "Ana", aparelhos = listOf("abc"), avisos = CuidarDeLonge.Aviso.values().toSet(), lembretes = listOf(regar))
        val pronta = CuidarDeLonge.consentir(c, 0L)
        val agora = ms("2026-11-26T19:00")   // 1 h depois: com Feito, já teria mostrado e avisado
        assertTrue(CuidarDeLonge.devidos(pronta, Estado(), agora, fuso).none { it is CuidarDeLonge.Acao.Mostrar || it is CuidarDeLonge.Acao.AvisarLembrete })
        val dia = Lembrete(2, -1, -1, CuidarDeLonge.TODO_DIA, "Lixo", feito = false)
        assertTrue(CuidarDeLonge.ocorrencias(dia, 0L, agora, fuso).isEmpty())
    }

    @Test fun jsonGuardaOFeito() {
        // Na ordem da lista: "o dia todo" (hora -1) vem antes dos com hora.
        val l = listOf(Lembrete(2, -1, -1, 0b0000010, "Lixo", feito = false), Lembrete(1, 20, 0, CuidarDeLonge.TODO_DIA, "Remédio"))
        val volta = CuidarDeLonge.lembretesDeJson(JSONArray(CuidarDeLonge.lembretesParaJson(l).toString()))
        assertEquals(l, volta)
        // Lista antiga do Cuidar (sem o campo): continua com Feito.
        val antiga = JSONArray("""[{"id":5,"hora":8,"minuto":0,"dias":127,"texto":"Água"}]""")
        assertTrue(CuidarDeLonge.lembretesDeJson(antiga).single().feito)
    }
}
