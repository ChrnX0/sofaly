package br.paulo.tvlauncher.panel

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException

/** O WebSocket do microfone e da câmera do celular (PanelStream.kt): aperto de mão e quadros, sem rede. */
class PanelStreamTest {
    private val key = "dGhlIHNhbXBsZSBub25jZQ=="

    /** Exemplo da própria RFC 6455 (seção 1.3). */
    @Test fun acceptDaRfc() = assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WsFrames.accept(key))

    @Test fun handshakeSoMesmaOrigem() {
        assertNotNull(PanelStream.handshake("websocket", key, "https://tv.lan:8444", "tv.lan:8444", "mic"))
        assertEquals(PanelStream.Kind.CAM, PanelStream.handshake("WebSocket", key, "https://tv.lan:8444", "tv.lan:8444", "cam")!!.kind)
        assertNull(PanelStream.handshake("websocket", key, "https://mal.com", "tv.lan:8444", "mic"))
        assertNull(PanelStream.handshake("websocket", key, "http://tv.lan:8444", "tv.lan:8444", "mic"))
        assertNull(PanelStream.handshake("websocket", key, null, "tv.lan:8444", "mic"))
        assertNull(PanelStream.handshake(null, key, "https://tv.lan:8444", "tv.lan:8444", "mic"))
        assertNull(PanelStream.handshake("websocket", "curta", "https://tv.lan:8444", "tv.lan:8444", "mic"))
        assertNull(PanelStream.handshake("websocket", key, "https://tv.lan:8444", "tv.lan:8444", "tela"))
        assertEquals(PanelStream.Kind.CLOCK, PanelStream.handshake("websocket", key, "https://tv.lan:8444", "tv.lan:8444", "clock")!!.kind)
    }

    private fun quadro(op: Int, dados: ByteArray, mascara: Boolean = true, fin: Boolean = true): ByteArray {
        val o = ByteArrayOutputStream()
        o.write((if (fin) 0x80 else 0) or op)
        val m = if (mascara) 0x80 else 0
        when {
            dados.size < 126 -> o.write(m or dados.size)
            dados.size < 65536 -> { o.write(m or 126); o.write(dados.size shr 8); o.write(dados.size and 0xff) }
            else -> { o.write(m or 127); for (s in 56 downTo 0 step 8) o.write(((dados.size.toLong() shr s) and 0xff).toInt()) }
        }
        val k = byteArrayOf(1, 2, 3, 4)
        if (mascara) o.write(k)
        o.write(ByteArray(dados.size) { i -> if (mascara) (dados[i].toInt() xor k[i and 3].toInt()).toByte() else dados[i] })
        return o.toByteArray()
    }

    private fun ler(b: ByteArray, limite: Int = 1 shl 20) = WsFrames.read(DataInputStream(ByteArrayInputStream(b)), limite)

    @Test fun lerQuadrosMascarados() {
        val curto = "oi".toByteArray()
        ler(quadro(WsFrames.BINARY, curto))!!.let { assertEquals(WsFrames.BINARY, it.opcode); assertArrayEquals(curto, it.payload) }
        val medio = ByteArray(1280) { it.toByte() }
        assertArrayEquals(medio, ler(quadro(WsFrames.BINARY, medio))!!.payload)
        val grande = ByteArray(70_000) { (it * 7).toByte() }
        assertArrayEquals(grande, ler(quadro(WsFrames.BINARY, grande))!!.payload)
        assertNull(ler(ByteArray(0)))
    }

    @Test(expected = IOException::class) fun semMascaraRecusa() { ler(quadro(WsFrames.BINARY, "x".toByteArray(), mascara = false)) }
    @Test(expected = IOException::class) fun grandeDemaisRecusa() { ler(quadro(WsFrames.BINARY, ByteArray(200)), limite = 100) }
    @Test(expected = IOException::class) fun fragmentoRecusa() { ler(quadro(WsFrames.BINARY, "x".toByteArray(), fin = false)) }

    @Test fun escreverSemMascara() {
        for (n in listOf(0, 125, 126, 65535, 65536)) {
            val o = ByteArrayOutputStream(); WsFrames.write(o, WsFrames.PONG, ByteArray(n) { 5 })
            val b = o.toByteArray()
            assertEquals(0x80 or WsFrames.PONG, b[0].toInt() and 0xff)
            assertEquals(0, b[1].toInt() and 0x80)
            assertEquals(n + when { n < 126 -> 2; n < 65536 -> 4; else -> 10 }, b.size)
        }
    }
}
