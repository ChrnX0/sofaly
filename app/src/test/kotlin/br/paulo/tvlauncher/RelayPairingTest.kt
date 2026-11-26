package br.paulo.tvlauncher

import br.paulo.tvlauncher.relay.RelayCrypto
import br.paulo.tvlauncher.relay.RelayPairing
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pareamento TV <-> celular pelo relay (passo A2): QR + envelope de apresentação. */
class RelayPairingTest {
    private val tvEd = ByteArray(32) { 11 }; private val tvX = ByteArray(32) { 12 }
    private val celEd = ByteArray(32) { 21 }; private val celX = ByteArray(32) { 22 }
    private val tvId = RelayCrypto.idDe(RelayCrypto.edPublica(tvEd))
    private val celId = RelayCrypto.idDe(RelayCrypto.edPublica(celEd))
    private val agora = 1_795_000_000_000L
    private val qr = RelayPairing.Qr("ABCD2345", tvId, RelayCrypto.xPublica(tvX))

    @Test fun qrIdaEVolta() {
        val t = RelayPairing.qrTexto(qr)
        val l = RelayPairing.lerQr(t)!!
        assertEquals(qr.codigo, l.codigo); assertEquals(tvId, l.tvId); assertArrayEquals(qr.tvX, l.tvX)
    }

    @Test fun qrEstragadoNaoLe() {
        assertNull(RelayPairing.lerQr("https://exemplo.com"))
        assertNull(RelayPairing.lerQr(RelayPairing.qrTexto(qr).replace("ABCD2345", "ABCD1345")))   // 1 não existe no código
        assertNull(RelayPairing.lerQr("sofaly://pair?c=ABCD2345&t=$tvId&x=AAAA"))
        assertNull(RelayPairing.lerQr("sofaly://pair?c=ABCD2345"))
    }

    @Test fun apresentacaoAbreNaTv() {
        val corpo = RelayPairing.apresentacao(celEd, celX, qr, "Celular da Ana", agora)
        assertTrue(RelayPairing.ehApresentacao(corpo))
        val p = RelayPairing.abrirApresentacao(tvId, tvX, "ABCD2345", celId, corpo, agora + 30_000)!!
        assertEquals(celId, p.id); assertEquals("Celular da Ana", p.nome); assertArrayEquals(RelayCrypto.xPublica(celX), p.xPub)
    }

    @Test fun outroCodigoOuOutraTvNaoAbre() {
        val corpo = RelayPairing.apresentacao(celEd, celX, qr, "Ana", agora)
        assertNull(RelayPairing.abrirApresentacao(tvId, tvX, "ABCD2346", celId, corpo, agora))
        val outraX = ByteArray(32) { 13 }
        assertNull(RelayPairing.abrirApresentacao(tvId, outraX, "ABCD2345", celId, corpo, agora))
    }

    @Test fun quemNaoViuOQrNaoPareia() {
        // Sabe o código e o id da TV, mas chuta a chave X25519 da TV.
        val chute = RelayPairing.Qr("ABCD2345", tvId, RelayCrypto.xPublica(ByteArray(32) { 99.toByte() }))
        val corpo = RelayPairing.apresentacao(celEd, celX, chute, "Intruso", agora)
        assertNull(RelayPairing.abrirApresentacao(tvId, tvX, "ABCD2345", celId, corpo, agora))
    }

    @Test fun corpoDeOutroRemetenteNaoAbre() {
        // O servidor viu outro aparelho assinar o pedido: a chave dentro do corpo não é dele.
        val corpo = RelayPairing.apresentacao(celEd, celX, qr, "Ana", agora)
        assertNull(RelayPairing.abrirApresentacao(tvId, tvX, "ABCD2345", tvId, corpo, agora))
    }

    @Test fun mexidaOuVelhaNaoAbre() {
        val corpo = RelayPairing.apresentacao(celEd, celX, qr, "Ana", agora)
        for (i in listOf(40, 70, corpo.size - 1)) {
            val m = corpo.copyOf(); m[i] = (m[i].toInt() xor 1).toByte()
            assertNull("byte $i", RelayPairing.abrirApresentacao(tvId, tvX, "ABCD2345", celId, m, agora))
        }
        assertNull(RelayPairing.abrirApresentacao(tvId, tvX, "ABCD2345", celId, corpo, agora + 16 * 60_000L))
    }

    @Test fun nomeComControleELimpo() {
        val corpo = RelayPairing.apresentacao(celEd, celX, qr, "\u0007\n  ", agora)
        assertEquals("Celular", RelayPairing.abrirApresentacao(tvId, tvX, "ABCD2345", celId, corpo, agora)!!.nome)
        assertFalse(RelayPairing.ehApresentacao(ByteArray(10)))
    }
}
