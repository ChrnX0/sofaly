package br.paulo.tvlauncher

import br.paulo.tvlauncher.relay.RelayCrypto
import br.paulo.tvlauncher.relay.RelayEnvelope
import br.paulo.tvlauncher.relay.RelayPairing
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pareamento pelo painel (passo A2) e a lista fechada do que pode vir de fora. */
class RelayPairingTest {
    private val ed = ByteArray(32) { 21 }; private val x = ByteArray(32) { 22 }
    private val edPub = RelayCrypto.edPublica(ed)
    private val id = RelayCrypto.idDe(edPub)
    private fun corpo(i: String = id, e: ByteArray = edPub, xx: ByteArray = RelayCrypto.xPublica(x), nome: String = "Celular da Ana") =
        JSONObject().put("id", i).put("ed", RelayCrypto.b64url(e)).put("x", RelayCrypto.b64url(xx)).put("name", nome)

    @Test fun pedidoBom() {
        val p = RelayPairing.lerPedido(corpo())!!
        assertEquals(id, p.id); assertEquals("Celular da Ana", p.nome)
    }

    @Test fun idQueNaoVemDaChaveNaoPareia() {
        // Se aceitasse, um pedido poderia apontar a TV para a caixa postal de outro aparelho.
        val outro = RelayCrypto.idDe(RelayCrypto.edPublica(ByteArray(32) { 5 }))
        assertNull(RelayPairing.lerPedido(corpo(i = outro)))
    }

    @Test fun chavesRuinsNaoPareiam() {
        assertNull(RelayPairing.lerPedido(corpo(xx = ByteArray(31))))
        assertNull(RelayPairing.lerPedido(JSONObject().put("id", id)))
        assertNull(RelayPairing.lerPedido(null))
        assertNull(RelayPairing.lerPedido(corpo().put("ed", "%%%")))
    }

    @Test fun nomeLimpo() {
        assertEquals("Celular", RelayPairing.lerPedido(corpo(nome = "\u0007\n "))!!.nome)
        assertEquals(40, RelayPairing.lerPedido(corpo(nome = "a".repeat(100)))!!.nome.length)
    }

    @Test fun listaFechada() {
        val t = 1L
        assertEquals(RelayPairing.Comando.Tecla("HOME"), RelayPairing.comando(RelayPairing.tecla("HOME", t)))
        assertEquals(RelayPairing.Comando.Texto("oi"), RelayPairing.comando(RelayPairing.texto("oi", t)))
        assertNull(RelayPairing.comando(RelayEnvelope.Mensagem("abcdefgh", t, "shell", JSONObject().put("cmd", "reboot"))))
        assertNull(RelayPairing.comando(RelayEnvelope.Mensagem("abcdefgh", t, "key", JSONObject())))
        assertNull(RelayPairing.comando(RelayEnvelope.Mensagem("abcdefgh", t, "text", JSONObject().put("text", "x".repeat(501)))))
    }

    @Test fun respostaLevaAChaveDaTv() {
        val r = RelayPairing.resposta("abcdefghijklmnopqrst", ByteArray(32) { 1 })
        assertTrue(RelayCrypto.deB64url(r.getString("x")).all { it == 1.toByte() })
    }

    @Test fun teclaAtrasadaNaoVale() {
        val agora = 10_000_000L
        assertTrue(RelayPairing.naHora(RelayPairing.tecla("HOME", agora - 5_000), agora))
        assertTrue(RelayPairing.naHora(RelayPairing.tecla("HOME", agora + 30_000), agora))   // relógio do celular adiantado
        assertFalse(RelayPairing.naHora(RelayPairing.tecla("HOME", agora - RelayPairing.ATRASO_MAX_MS - 1), agora))
    }

    @Test fun horaDoServidorValeMaisQueORelogioDoCelular() {
        val agora = 10_000_000L
        val celularAtrasado = RelayPairing.tecla("HOME", agora - 120_000)   // celular 2 min atrasado
        assertTrue(RelayPairing.naHora(celularAtrasado, agora, chegouSeg = (agora - 2_000) / 1000))
        assertFalse(RelayPairing.naHora(RelayPairing.tecla("HOME", agora), agora, chegouSeg = (agora - 120_000) / 1000))   // ficou na caixa
    }
}
