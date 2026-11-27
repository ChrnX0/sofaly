package br.paulo.onetv.celular

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Usar fora de casa?" (27/11/2026): quando o controle oferece e o que a resposta do PIN quer dizer. */
class ForaDeCasaTest {
    @Test fun ofereceSoEmCasaComQrESemRelay() {
        assertTrue(ApiTv.deveOferecerFora(200, podeUsar = true, temRelay = false))
        assertFalse("já tem as chaves do relay", ApiTv.deveOferecerFora(200, true, true))
        assertFalse("sem QR (sem certificado fixado) não há controle nativo", ApiTv.deveOferecerFora(200, false, false))
        assertFalse("202 = chegou pelo relay, então já está fora de casa", ApiTv.deveOferecerFora(202, true, false))
        assertFalse("TV não respondeu", ApiTv.deveOferecerFora(0, true, false))
        assertFalse("sem sessão", ApiTv.deveOferecerFora(401, true, false))
    }

    @Test fun respostaDoPin() {
        assertEquals(ApiTv.Fora.ATIVADO, ApiTv.foraPeloPin(200, JSONObject().put("ok", true)))
        assertEquals("401 com restantes = PIN errado", ApiTv.Fora.PIN_ERRADO,
            ApiTv.foraPeloPin(401, JSONObject().put("erro", "PIN errado").put("restantes", 4)))
        assertEquals("401 sem restantes = sessão vencida, não PIN errado", ApiTv.Fora.SEM_SESSAO,
            ApiTv.foraPeloPin(401, JSONObject().put("erro", "entre com o PIN")))
        assertEquals(ApiTv.Fora.SEM_SESSAO, ApiTv.foraPeloPin(401, null))
        assertEquals(ApiTv.Fora.ESPERAR, ApiTv.foraPeloPin(429, JSONObject().put("esperarSeg", 30)))
        assertEquals(ApiTv.Fora.SEM_TV, ApiTv.foraPeloPin(0, null))
        assertEquals(ApiTv.Fora.SEM_TV, ApiTv.foraPeloPin(ApiTv.DEMOROU, null))
        assertEquals(ApiTv.Fora.SEM_TV, ApiTv.foraPeloPin(ApiTv.CERTIFICADO_TROCOU, null))
        assertEquals("painel desligado", ApiTv.Fora.RECUSOU, ApiTv.foraPeloPin(403, JSONObject().put("erro", "painel desligado")))
    }

    @Test fun pinNoFormatoDoPainel() {
        assertTrue(ApiTv.pinValido("000000"))
        assertTrue(ApiTv.pinValido("123456789012"))
        assertFalse(ApiTv.pinValido("12345"))
        assertFalse(ApiTv.pinValido("1234567890123"))
        assertFalse(ApiTv.pinValido("12a456"))
        assertFalse(ApiTv.pinValido(""))
    }
}
