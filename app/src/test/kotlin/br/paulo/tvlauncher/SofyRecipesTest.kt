package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyRecipes
import br.paulo.tvlauncher.observer.SofyRepair
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Receitas da rede de Sofys: só dado da lista fechada, e começam em sombra (Paulo, 27/11/2026). */
class SofyRecipesTest {
    private fun receita(type: String = "CRASH", area: String = "app", target: String = "", cause: String = "update",
                        tools: List<String> = listOf("CLEAR_APP_CACHE")) = JSONObject()
        .put("id", "$type|$area|$target|$cause")
        .put("match", JSONObject().put("type", type).put("area", area).put("target", target).put("cause", cause))
        .put("tools", JSONArray(tools)).put("evidence", JSONObject().put("homes", 3).put("cases", 5)).put("day", 1)

    private fun resposta(vararg r: JSONObject) = JSONObject().put("day", 1).put("recipes", JSONArray(r.toList()))

    private fun caso(fixedBy: SofyRepair.Tool?, state: SofyRepair.State = SofyRepair.State.FIXED, detail: String = "") = SofyRepair.Case(
        key = "CRASH:app:com.app.x", type = SofyRepair.Type.CRASH, subject = "app:com.app.x", needs = emptySet(),
        firstAt = 1_000L, lastAt = 2_000L, count = 2,
        tried = listOfNotNull(
            SofyRepair.Attempt(SofyRepair.Tool.RECONNECT_SHELL, 1_200L, SofyRepair.Result.NOT_FIXED),
            fixedBy?.let { SofyRepair.Attempt(it, 1_500L, SofyRepair.Result.FIXED) }),
        state = state, context = listOf("update.app_updated(qualquer coisa)"), detail = detail,
    )

    @Test fun leAReceitaCerta() {
        val r = SofyRecipes.parse(resposta(receita())).single()
        assertEquals(SofyRepair.Type.CRASH, r.type)
        assertEquals(listOf(SofyRepair.Tool.CLEAR_APP_CACHE), r.tools)
        assertEquals(3, r.homes)
    }

    @Test fun qualquerCampoForaDaListaDescartaAReceita() {
        val ruins = listOf(
            receita(type = "APAGAR_TUDO"), receita(area = "banco"), receita(cause = "o vizinho"),
            receita(target = "Sala da Ana"), receita(area = "feature", target = "Sala da Ana"),
            receita(tools = listOf("rm -rf /")), receita(tools = emptyList()),
            receita(tools = listOf("CLEAR_APP_CACHE", "RECONNECT_SHELL", "RESTART_TUNNEL", "GRANT_PERMISSIONS")),
            receita(tools = listOf("ASK_AI")), receita(tools = listOf("REPORT")),
            receita(tools = listOf("CLEAR_APP_CACHE", "CLEAR_APP_CACHE")),
        )
        for (r in ruins) assertEquals("passou: $r", 0, SofyRecipes.parse(resposta(r)).size)
        // uma ruim não derruba as boas
        assertEquals(1, SofyRecipes.parse(resposta(receita(type = "X"), receita())).size)
    }

    @Test fun lixoNaoLeNada() {
        assertEquals(0, SofyRecipes.parse(null).size)
        assertEquals(0, SofyRecipes.parse(JSONObject().put("day", 1)).size)
    }

    // Vetor feito no Node com uma chave de TESTE (não é a real), no mesmo formato do Worker: R||S em base64.
    private val chaveTeste = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaSelq8iEFbtDP1lU5HjtjOgNU3vHDoUMboDHJLIJ8zBH/2Cpo58gG4qFPphWL1xv2zKu44LzH7OWNFkLXmgWng=="
    private val dado = "{\"day\":20000,\"recipes\":[]}"
    private val assinatura = "0a+58ei4T6sGCINllebugq3YF/xR/0JMjz6ye4a/iRV0wyLxeDuGtHnGH3SrnLbnM6DrNSqYJVXBNxYw6MGi5A=="
    private val hoje = 20_001L * 86_400_000L
    private fun envelope(data: String = dado, sig: Any? = assinatura, v: Int = 1) =
        JSONObject().put("v", v).put("data", data).put("sig", sig ?: JSONObject.NULL)

    @Test fun abreSoComAssinaturaCerta() {
        assertEquals(20000L, SofyRecipes.open(envelope(), hoje, chaveTeste)?.optLong("day"))
        assertNull(SofyRecipes.open(envelope(data = dado.replace("20000", "20001")), hoje, chaveTeste))   // texto mexido
        assertNull(SofyRecipes.open(envelope(sig = null), hoje, chaveTeste))                              // sem chave no servidor
        assertNull(SofyRecipes.open(envelope(), hoje))                                                    // outra chave (a real)
        assertNull(SofyRecipes.open(envelope(v = 2), hoje, chaveTeste))
        assertNull(SofyRecipes.open(envelope(), hoje + 10 * 86_400_000L, chaveTeste))                     // velha demais
        assertNull(SofyRecipes.open(null, hoje, chaveTeste))
    }

    @Test fun derDaAssinaturaCrua() {
        val raw = ByteArray(64).also { it[0] = 0x80.toByte(); it[32] = 0; it[33] = 0x01 }
        val der = SofyRecipes.rawToDer(raw)
        assertEquals(0x30, der[0].toInt())
        assertEquals(33, der[3].toInt())   // R com bit alto ganha um zero na frente
        assertEquals(31, der[3 + 33 + 2].toInt())   // S perde o zero do começo
    }

    @Test fun sombraAcertoEErro() {
        val lista = SofyRecipes.parse(resposta(receita()))
        assertEquals(SofyRecipes.Verdict.HIT, SofyRecipes.judge(caso(SofyRepair.Tool.CLEAR_APP_CACHE), lista)?.second)
        assertEquals(SofyRecipes.Verdict.MISS, SofyRecipes.judge(caso(SofyRepair.Tool.RESTART_TUNNEL), lista)?.second)
    }

    @Test fun semConsertoSemReceitaOuTesteNaoConta() {
        val lista = SofyRecipes.parse(resposta(receita()))
        assertNull(SofyRecipes.judge(caso(null, SofyRepair.State.OPEN), lista))
        assertNull(SofyRecipes.judge(caso(null, SofyRepair.State.GIVEN_UP), lista))
        assertNull(SofyRecipes.judge(caso(SofyRepair.Tool.CLEAR_APP_CACHE), emptyList()))
        // outra causa (nada mudou antes): a receita de "update" não vale
        assertNull(SofyRecipes.judge(caso(SofyRepair.Tool.CLEAR_APP_CACHE).copy(context = emptyList()), lista))
    }
}
