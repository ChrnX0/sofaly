package br.paulo.tvlauncher

import br.paulo.tvlauncher.relay.RelaySocket
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** O WebSocket mínimo do relay (RFC 6455). A conexão de verdade foi conferida contra o api.sofaly.tv. */
class RelaySocketTest {
    @Test fun aceiteDoExemploDoRfc() {
        // RFC 6455, seção 1.3.
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", RelaySocket.aceite("dGhlIHNhbXBsZSBub25jZQ=="))
    }

    @Test fun quadroMascaradoVoltaIgual() {
        for (n in listOf(0, 5, 125, 126, 300, 70_000)) {
            val dados = ByteArray(n) { (it * 7).toByte() }
            val q = RelaySocket.quadro(1, dados, byteArrayOf(1, 2, 3, 4))
            val (op, lido) = RelaySocket.lerQuadro(ByteArrayInputStream(q))
            assertEquals(1, op); assertArrayEquals(dados, lido)
        }
    }

    @Test fun quadroDoServidorSemMascara() {
        // "Hello" do RFC 6455, seção 5.7.
        val (op, d) = RelaySocket.lerQuadro(ByteArrayInputStream(byteArrayOf(0x81.toByte(), 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f)))
        assertEquals(1, op); assertEquals("Hello", String(d))
        assertEquals(-1, RelaySocket.lerQuadro(ByteArrayInputStream(ByteArray(0))).first)
    }

    @Test(expected = IOException::class) fun grandeDemaisRecusa() {
        val q = byteArrayOf(0x81.toByte(), 127, 0, 0, 0, 0, 0x7F, 0, 0, 0)
        RelaySocket.lerQuadro(ByteArrayInputStream(q))
    }
}
