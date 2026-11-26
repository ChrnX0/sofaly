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

    @Test fun soPadraoTecnicoSaiDaCasa() {
        assertTrue(SofyRules.shareable("\\bnull\\b"))
        assertTrue(SofyRules.shareable("(\\d+)\\s*%"))
        assertTrue(SofyRules.shareable("[A-Za-z]+ NaN"))
        assertTrue(!SofyRules.shareable("Aniversário da Maria"))
        assertTrue(!SofyRules.shareable("Curitiba \\d+"))
        assertTrue(!SofyRules.shareable("(a+)+"))
    }

    @Test fun mandaSoAsQueValemEPodemSair() {
        val rs = listOf(
            Rule("a", Kind.RANGE, "previsao", "(\\d+)\\s*%", 0.0, 100.0, "w", State.ACTIVE, example = "x", at = 9),
            Rule("b", Kind.FORBIDDEN, "*", "null", state = State.SHADOW),
            Rule("c", Kind.FORBIDDEN, "*", "Natal da Maria", state = State.ACTIVE))
        val a = SofyRules.communityRules(rs)
        assertEquals(1, a.length())
        val o = a.getJSONObject(0)
        assertEquals("(\\d+)\\s*%", o.getString("pattern")); assertEquals(100.0, o.getDouble("max"), 0.0)
        assertTrue(!o.has("example") && !o.has("why") && !o.has("at"))
    }

    @Test fun recebeDeOutrasCasasNaSombra() {
        val o = org.json.JSONObject("{\"rules\":[" +
            "{\"kind\":\"FORBIDDEN\",\"screen\":\"*\",\"pattern\":\"NaN\",\"homes\":7}," +
            "{\"kind\":\"FORBIDDEN\",\"screen\":\"*\",\"pattern\":\"undefined\",\"homes\":2}," +
            "{\"kind\":\"FORBIDDEN\",\"screen\":\"*\",\"pattern\":\"Maria\",\"homes\":9}," +
            "{\"kind\":\"RANGE\",\"screen\":\"*\",\"pattern\":\"\\\\d+%\",\"min\":0,\"max\":100,\"homes\":9}]}")
        val r = SofyRules.fromCommunity(o, 5, 3).single()
        assertEquals("NaN", r.pattern); assertEquals(State.SHADOW, r.state)
        assertEquals(SofyRules.idOf("NaN", Kind.FORBIDDEN, "*"), r.id)   // mesma regra derrubada aqui não volta
        assertTrue(SofyRules.fromCommunity(null, 5, 0).isEmpty())
    }
}

/** Segunda opinião: só a regra com que a outra IA concorda entra na sombra. */
class SofyRulesSecondTest {
    private val a = Rule("ra", Kind.FORBIDDEN, "*", "\\bnull\\b", why = "placeholder")
    private val b = Rule("rb", Kind.RANGE, "previsao", "(\\d+)\\s*%", min = 0.0, max = 100.0, why = "percent")

    @Test fun ficaSoOQueAOutraConcordou() {
        assertEquals(listOf(b), SofyRules.agreed("ok {\"keep\":[\"rb\",\"zz\"]}", listOf(a, b)))
        assertEquals(listOf(a, b), SofyRules.agreed("{\"keep\":[\"ra\",\"rb\"]}", listOf(a, b)))
    }

    @Test fun semRespostaNenhumaEntra() {
        assertTrue(SofyRules.agreed(null, listOf(a, b)).isEmpty())
        assertTrue(SofyRules.agreed("não sei", listOf(a, b)).isEmpty())
        assertTrue(SofyRules.agreed("{\"keep\":[]}", listOf(a, b)).isEmpty())
    }

    @Test fun perguntaLevaIdsEPadroes() {
        val p = SofyRules.secondPrompt("previsao", listOf("Chance de chuva 40%"), listOf(a, b))
        assertTrue(p.contains("id=ra") && p.contains("id=rb") && p.contains("max=100.0") && p.contains("Chance de chuva 40%"))
    }
}
