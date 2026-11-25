package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyRules
import br.paulo.tvlauncher.observer.SofyRules.Kind
import br.paulo.tvlauncher.observer.SofyRules.Rule
import br.paulo.tvlauncher.observer.SofyRules.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Revisor noturno (plano D, passo 3): regras que a IA propõe, conferidas pela TV, com sombra antes de valer. */
class SofyRulesTest {
    private val previsao = listOf("Agora 30°", "Chance de chuva 40%", "Hoje 31° 22°", "Atualizado há 2 h")

    @Test fun recusaPadraoPerigosoOuGrande() {
        assertNull(SofyRules.safe("(a+)+b"))
        assertNull(SofyRules.safe("(\\d*)*x"))
        assertNull(SofyRules.safe("x".repeat(81)))
        assertNull(SofyRules.safe("[unclosed"))
        assertNotNull(SofyRules.safe("(\\d+)\\s*%"))
    }

    @Test fun buscaSemFimParaSozinha() {
        // Mesmo que um padrão ruim passe, a busca tem limite de passos e vira "não casou".
        val r = Rule("x", Kind.FORBIDDEN, "*", "(x|x)*y")
        assertNull(SofyRules.violation(r, listOf("x".repeat(200))))
    }

    @Test fun faixaDePorcentagem() {
        val r = Rule("p", Kind.RANGE, "*", "(\\d+)\\s*%", 0.0, 100.0)
        assertNull(SofyRules.violation(r, previsao))
        assertEquals("Chance de chuva 140%", SofyRules.violation(r, listOf("Chance de chuva 140%")))
    }

    @Test fun regraNasceNaSombraEValeDepoisDeTresNoites() {
        var rs = listOf(Rule("n", Kind.FORBIDDEN, "*", "\\bnull\\b"))
        repeat(2) { rs = SofyRules.evaluate(rs, "previsao", previsao).first }
        assertEquals(State.SHADOW, rs.single().state)
        rs = SofyRules.evaluate(rs, "agenda", listOf("Natal")).first
        assertEquals(State.ACTIVE, rs.single().state)
        val (_, broken) = SofyRules.evaluate(rs, "agenda", listOf("Evento: null"))
        assertEquals("Evento: null", broken.single().second)
    }

    @Test fun disparoNaSombraEAlarmeFalsoERegraCai() {
        val rs = listOf(Rule("h", Kind.FORBIDDEN, "*", "\\d+ h"))
        val (after, broken) = SofyRules.evaluate(rs, "previsao", previsao)
        assertTrue(broken.isEmpty())
        assertEquals(State.DROPPED, after.single().state)
        assertEquals("Atualizado há 2 h", after.single().example)
    }

    @Test fun regraDeOutraTelaNaoAnda() {
        val rs = listOf(Rule("a", Kind.FORBIDDEN, "agenda", "null"))
        assertEquals(0, SofyRules.evaluate(rs, "previsao", previsao).first.single().nights)
    }

    @Test fun leARespostaDaIA() {
        val answer = "ok {\"defects\":[\"Hoje 27° abaixo de agora 30°\"],\"rules\":[" +
            "{\"kind\":\"RANGE\",\"screen\":\"*\",\"pattern\":\"(\\\\d+)\\\\s*%\",\"min\":0,\"max\":100,\"why\":\"porcentagem\"}," +
            "{\"kind\":\"RANGE\",\"screen\":\"*\",\"pattern\":\"\\\\d+%\",\"min\":0,\"max\":100}," +     // sem grupo: fora
            "{\"kind\":\"FORBIDDEN\",\"screen\":\"*\",\"pattern\":\"(a+)+\"}," +                         // perigoso: fora
            "{\"kind\":\"EXEC\",\"pattern\":\"x\"}]}"                                                  // tipo desconhecido: fora
        val rev = SofyRules.parse(answer, "previsao", previsao, emptyList(), 5)
        assertEquals(listOf("Hoje 27° abaixo de agora 30°"), rev.defects)
        assertEquals(listOf("(\\d+)\\s*%"), rev.rules.map { it.pattern })
        assertEquals(State.SHADOW, rev.rules.single().state)
        assertTrue(SofyRules.parse("lixo", "x", previsao, emptyList(), 0).rules.isEmpty())
    }

    @Test fun regraQueJaCasaComATelaSoEntraSeForODefeitoApontado() {
        val rule = "{\"kind\":\"FORBIDDEN\",\"screen\":\"*\",\"pattern\":\"\\\\d{4,} h\"}"
        val tela = listOf("Duração 498783 h")
        assertTrue(SofyRules.parse("{\"defects\":[],\"rules\":[$rule]}", "x", tela, emptyList(), 0).rules.isEmpty())
        val r = SofyRules.parse("{\"defects\":[\"duração absurda\"],\"rules\":[$rule]}", "x", tela, emptyList(), 0).rules.single()
        assertEquals("Duração 498783 h", r.example)
        // Na sombra, o defeito conhecido continuando lá não derruba a regra.
        assertEquals(State.SHADOW, SofyRules.evaluate(listOf(r), "x", tela).first.single().state)
    }

    @Test fun naoRepeteENaoPassaDoLimite() {
        val old = (1..SofyRules.MAX_RULES).map { Rule("r$it", Kind.FORBIDDEN, "*", "p$it", state = if (it % 2 == 0) State.DROPPED else State.ACTIVE, at = it.toLong()) }
        val merged = SofyRules.merge(old, listOf(Rule("new", Kind.FORBIDDEN, "*", "q", at = 99)))
        assertEquals(SofyRules.MAX_RULES, merged.size)
        assertTrue(merged.none { it.id == "r2" })   // a que caiu mais velha sai primeiro
        val again = SofyRules.parse("{\"rules\":[{\"kind\":\"FORBIDDEN\",\"pattern\":\"p1\"}]}", "x", emptyList(), old, 0)
        assertTrue(again.rules.isEmpty())
    }

    @Test fun livroVoltaIgualDoJson() {
        val rs = listOf(Rule("a", Kind.RANGE, "previsao", "(\\d+)%", 0.0, 100.0, "w", State.ACTIVE, 3, "ex", 7))
        assertEquals(rs, SofyRules.fromJson(SofyRules.toJson(rs)))
        assertTrue(SofyRules.fromJson("{").isEmpty())
    }

    @Test fun pedidoLevaATelaERegrasQueCairam() {
        val p = SofyRules.prompt("previsao", previsao, listOf(Rule("d", Kind.FORBIDDEN, "*", "\\d+ h", state = State.DROPPED, example = "há 2 h")))
        assertTrue(p.contains("- Chance de chuva 40%"))
        assertTrue(p.contains("false alarms"))
    }
}
