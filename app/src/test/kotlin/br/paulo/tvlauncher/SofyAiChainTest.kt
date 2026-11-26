package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyAiRules
import br.paulo.tvlauncher.observer.SofyAiRules.Provider
import br.paulo.tvlauncher.observer.SofyAiRules.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Modelo por tarefa, IA reserva e segunda opinião (Paulo, 25/11/2026: "pode fazer os 3"). */
class SofyAiChainTest {
    private val claude = SofyAiRules.resolve(Provider.CLAUDE, null, null, "k1")!!
    private val openai = SofyAiRules.resolve(Provider.OPENAI, null, null, "k2")!!

    @Test fun consertoBaratoRevisaoMaisForte() {
        assertEquals("claude-haiku-5-5", claude.model)
        assertEquals("claude-sonnet-5-5", SofyAiRules.forReview(claude, null).model)
        assertEquals("claude-sonnet-5-5", SofyAiRules.forReview(claude, "  ").model)
        assertEquals("claude-opus-5-5", SofyAiRules.forReview(claude, "claude-opus-5-5").model)
    }

    @Test fun modeloEscolhidoPeloDonoValeTambemNaRevisao() {
        val local = SofyAiRules.resolve(Provider.CUSTOM, "http://192.168.1.50:11434/v1", "llama3", null)!!
        assertEquals("llama3", SofyAiRules.forReview(local, null).model)
        val dono = SofyAiRules.resolve(Provider.CLAUDE, null, "claude-haiku-5-5", "k")!!
        assertEquals("claude-haiku-5-5", SofyAiRules.forReview(dono, null).model)
    }

    @Test fun reservaVemDepoisDaPrincipal() {
        val c = SofyAiRules.chain(Task.REPAIR, claude, openai, null, null)
        assertEquals(listOf(Provider.CLAUDE, Provider.OPENAI), c.map { it.provider })
        assertEquals(listOf(Provider.OPENAI), SofyAiRules.chain(Task.REPAIR, null, openai, null, null).map { it.provider })
        val r = SofyAiRules.chain(Task.REVIEW, claude, openai, null, null)
        assertEquals(listOf("claude-sonnet-5-5", "gpt-4o"), r.map { it.model })
    }

    @Test fun segundaOpiniaoEOutraIa() {
        // Com reserva: a outra empresa julga.
        assertEquals(listOf(Provider.OPENAI), SofyAiRules.chain(Task.SECOND, claude, openai, null, null).map { it.provider })
        // Sem reserva: a mesma chave com o modelo mais capaz (outro que o da revisão).
        assertEquals(listOf("claude-opus-5-5"), SofyAiRules.chain(Task.SECOND, claude, null, null, null).map { it.model })
        // A chave não alcança Opus: o mais capaz que ela alcança, se diferente do da revisão.
        val so = setOf("claude-haiku-5-5", "claude-sonnet-5-5")
        assertEquals(listOf("claude-haiku-5-5"), SofyAiRules.chain(Task.SECOND, claude, null, null, null, so).map { it.model })
        // Sem nada diferente (o dono fixou um modelo só): ninguém.
        val dono = SofyAiRules.resolve(Provider.CLAUDE, null, "claude-haiku-5-5", "k")!!
        assertTrue(SofyAiRules.chain(Task.SECOND, dono, null, null, null).isEmpty())
    }

    @Test fun escolheOMaisIndicadoQueAChaveAlcanca() {
        val tudo = setOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5-20261001")
        assertEquals("claude-haiku-5-5", SofyAiRules.pick(Provider.CLAUDE, Task.REPAIR, tudo))
        assertEquals("claude-sonnet-5-5", SofyAiRules.pick(Provider.CLAUDE, Task.REVIEW, tudo))
        assertEquals("claude-opus-5-5", SofyAiRules.pick(Provider.CLAUDE, Task.SECOND, tudo))
        // Chave limitada a modelos antigos: cai no que existe.
        val velha = setOf("claude-haiku-4-5", "claude-sonnet-4-6")
        assertEquals("claude-haiku-4-5", SofyAiRules.pick(Provider.CLAUDE, Task.REPAIR, velha))
        assertEquals("claude-sonnet-4-6", SofyAiRules.forReview(claude, null, velha).model)
        assertEquals("claude-haiku-4-5", SofyAiRules.forRepair(claude, velha).model)
        // Lista vazia (não deu para ler): a preferência.
        assertEquals("claude-sonnet-5-5", SofyAiRules.pick(Provider.CLAUDE, Task.REVIEW, emptySet()))
    }

    @Test fun leAListaDeModelos() {
        val r = org.json.JSONObject("{\"data\":[{\"id\":\"claude-haiku-5-5\"},{\"id\":\"claude-opus-5-5\"},{}],\"has_more\":false}")
        assertEquals(setOf("claude-haiku-5-5", "claude-opus-5-5"), SofyAiRules.modelIds(r))
    }
}
