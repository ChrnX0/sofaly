package br.paulo.onetv.celular

import br.paulo.tvlauncher.relay.RelayPairing
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Controle de fora de casa pelo relay (decisão A, 26/11/2026): o que vai pela caixa e quando trocar as chaves. */
class RelayCelularTest {
    @Test fun soTeclaETextoVaoPeloRelay() {
        val t = RelayCelular.mensagem("/api/tela/tecla", JSONObject().put("tecla", "inicio"), 1L)!!
        assertEquals(RelayPairing.Comando.Tecla("inicio"), RelayPairing.comando(t))
        val x = RelayCelular.mensagem("/api/tela/texto", JSONObject().put("texto", "novela"), 1L)!!
        assertEquals(RelayPairing.Comando.Texto("novela"), RelayPairing.comando(x))
        assertNull(RelayCelular.mensagem("/api/arquivos/tocar", JSONObject().put("caminho", "x"), 1L))
        assertNull(RelayCelular.mensagem("/api/tela/tecla", JSONObject(), 1L))
    }

    @Test fun trocaAsChavesSoDepoisDeDarCertoEmCasa() {
        assertTrue(ApiTv.deveParearRelay(200, temRelay = false, caminho = "/api/estado", jaRecusou = false))
        assertFalse(ApiTv.deveParearRelay(0, false, "/api/estado", false))          // fora de casa: não dá
        assertFalse(ApiTv.deveParearRelay(401, false, "/api/estado", false))        // sem sessão do dono
        assertFalse(ApiTv.deveParearRelay(200, true, "/api/estado", false))         // já tem
        assertFalse(ApiTv.deveParearRelay(200, false, "/api/estado", true))         // TV antiga recusou neste processo
        assertFalse(ApiTv.deveParearRelay(200, false, "/api/relay/parear", false))  // não entra em laço
        assertFalse(ApiTv.deveParearRelay(200, false, "/api/aparelho/emitir", false))
    }
}
