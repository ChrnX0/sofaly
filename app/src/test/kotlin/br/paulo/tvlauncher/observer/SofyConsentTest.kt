package br.paulo.tvlauncher.observer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #541 (28/11/2026): um "Sim" vale para o texto lido. Nada novo sai sem um "Sim" novo. */
class SofyConsentTest {
    private val s = SofyConsent.SHARE_VERSION
    private val f = SofyConsent.FLEET_VERSION

    @Test fun `o 1 guardado antes de 28-11 vale desligado e pede a pergunta de novo`() {
        // Antes as chaves guardavam 1 sob textos que mudaram três vezes em 27/11: não dá para saber qual foi lido.
        assertFalse(SofyConsent.shareOn(1))
        assertFalse(SofyConsent.fleetOn(1, 1))
        assertTrue(SofyConsent.reask(1, 0))   // ligado pelo painel
        assertTrue(SofyConsent.reask(1, 1))   // o "Sim" da tela do sofáTV
    }

    @Test fun `so a versao atual liga`() {
        assertTrue(SofyConsent.shareOn(s)); assertTrue(SofyConsent.fleetOn(s, f))
        assertFalse(SofyConsent.fleetOn(s, 0)); assertFalse(SofyConsent.fleetOn(0, f))
        assertFalse(SofyConsent.reask(s, f)); assertFalse(SofyConsent.reask(s, 0)); assertFalse(SofyConsent.reask(0, 0))
    }

    @Test fun `fichas novas e relato velho perguntam de novo pelo relato`() {
        assertTrue(SofyConsent.reask(s, 1))
        assertFalse(SofyConsent.fleetOn(s, 1))
    }

    @Test fun `os valores gravados`() {
        assertEquals(0 to 0, SofyConsent.values(on = false, fleet = true, oldFleet = f))      // desligar zera tudo
        assertEquals(s to f, SofyConsent.values(on = true, fleet = true, oldFleet = 0))       // o "Sim" da TV
        assertEquals(s to 0, SofyConsent.values(on = true, fleet = false, oldFleet = 0))      // o painel: só as fichas
        assertEquals(s to f, SofyConsent.values(on = true, fleet = false, oldFleet = f))      // o painel não tira o da TV
        assertEquals(s to 0, SofyConsent.values(on = true, fleet = false, oldFleet = 1))      // nem revive um velho
        val (a, b) = SofyConsent.values(on = true, fleet = false, oldFleet = 1)
        assertFalse(SofyConsent.fleetOn(a, b))
    }

    /**
     * A trava: o que cada versão promete. Mudou o pacote ou os tipos de lição? Este teste para o build até alguém subir a
     * versão daquela metade (e os textos da TV e do painel), para que todo "Sim" antigo pergunte de novo.
     */
    @Test fun `a versao acompanha o que vai`() {
        val tv = SofyFleet.Device("Google", "Chromecast HD", googleTv = true, androidTv = true, sdk = 31, is64 = false, ramMb = 1536, diskGb = 6)
        val caso = SofyRepair.Case(key = "CRASH:app:x.y", type = SofyRepair.Type.CRASH, subject = "app:x.y", needs = emptySet(),
            firstAt = 1L, lastAt = 2L, count = 1, tried = listOf(SofyRepair.Attempt(SofyRepair.Tool.CLEAR_APP_CACHE, 1L, SofyRepair.Result.FIXED)),
            state = SofyRepair.State.FIXED, context = emptyList(), aiPlan = emptyList(), detail = "")
        val p = SofyFleet.packet("0123456789abcdef", 1, tv, listOf(caso), emptyMap(), SofyFleet.State(0, false, "wifi", 0))
        fun JSONObject.chaves() = keys().asSequence().toSortedSet().joinToString(",")
        val c = p.getJSONArray("cases").getJSONObject(0)
        val formaFrota = listOf(SofyFleet.VERSION.toString(), p.chaves(), p.getJSONObject("device").chaves(), c.chaves(),
            c.getJSONObject("state").chaves(), c.getJSONArray("tries").getJSONObject(0).chaves()).joinToString(" | ")
        assertEquals("o pacote da rede de Sofys mudou: suba SofyConsent.FLEET_VERSION e reveja os textos", 2 to
            "1 | app,cases,code,device,v | abi,maker,model,platform | area,cause,outcome,state,target,tries,type | memory,net | by,result,tool",
            f to formaFrota)
        val formaFichas = SofyShare.KINDS.sorted().joinToString(",") + " | " +
            SofyShare.communityBody(listOf(SofyShare.Lesson("x.y", 1, "crashy"))).getJSONArray("lessons").getJSONObject(0).chaves()
        assertEquals("as fichas mudaram: suba SofyConsent.SHARE_VERSION e reveja os textos", 2 to "crashy,slower | kind,pkg,ver", s to formaFichas)
    }
}
