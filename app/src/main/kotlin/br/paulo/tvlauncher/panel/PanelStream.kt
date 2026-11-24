package br.paulo.tvlauncher.panel

import android.content.Context
import br.paulo.tvlauncher.companion.PhoneMicTv
import br.paulo.tvlauncher.companion.PhoneCameraTv
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64

/**
 * WebSocket do celular para a TV (24/11/2026, proposta B: microfone de karaokê e câmera do celular na TV).
 *
 * Por quê: o painel fecha a conexão a cada pedido ("Connection: close"); mandar 25 pedaços de som e 8 fotos por segundo
 * assim custaria um aperto de mão TLS por pedaço na CPU de 32 bits do Chromecast, e o som chegaria atrasado. Para quê:
 * voz do celular saindo na TV com pouco atraso, e a câmera do celular na tela. Como: UMA conexão WebSocket na mesma porta
 * https do painel, aberta só depois da sessão (o roteador do painel já conferiu o login) e só da mesma origem; o celular
 * manda quadros binários ([PhoneMicTv] = PCM 16 bits, [PhoneCameraTv] = JPEG). Nada é gravado.
 *
 * A parte pura (aperto de mão, leitura de quadros) está em [WsFrames] (PanelStreamTest).
 */
object PanelStream {
    const val ROTA = "/api/companion/stream"
    /** Sem dado do celular por este tempo = o celular sumiu (tela apagou, saiu do Wi-Fi): a TV encerra. */
    const val IDLE_MS = 6_000

    enum class Kind { MIC, CAM }
    data class Pedido(val accept: String, val kind: Kind)

    /** Confere o pedido de WebSocket: upgrade, chave, mesma origem e o tipo (k=mic|cam). null = recusa. */
    fun handshake(upgrade: String?, key: String?, origin: String?, host: String?, k: String?): Pedido? {
        if (!upgrade.equals("websocket", ignoreCase = true)) return null
        if (key == null || !Regex("^[A-Za-z0-9+/]{22}==$").matches(key)) return null
        // Mesma origem: uma página de outro site não abre o microfone da TV com o cookie da pessoa (CSWSH).
        if (origin == null || host == null || origin != "https://$host") return null
        val kind = when (k) { "mic" -> Kind.MIC; "cam" -> Kind.CAM; else -> return null }
        return Pedido(WsFrames.accept(key), kind)
    }

    fun serve(app: Context, p: Pedido, entrada: InputStream, saida: OutputStream) {
        saida.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: ${p.accept}\r\n\r\n").toByteArray(Charsets.ISO_8859_1))
        saida.flush()
        val dentro = DataInputStream(entrada)
        val destino: (ByteArray) -> Unit = when (p.kind) {
            Kind.MIC -> { b -> PhoneMicTv.chunk(app, b) }
            Kind.CAM -> { b -> PhoneCameraTv.frame(app, b) }
        }
        val limite = if (p.kind == Kind.CAM) 512 * 1024 else 64 * 1024
        // Um celular por vez: se outro já está com o microfone (ou a câmera), esta conexão fecha sem desligar o dele.
        val comecou = if (p.kind == Kind.MIC) PhoneMicTv.start(app) else PhoneCameraTv.start(app)
        if (!comecou) { WsFrames.write(saida, WsFrames.CLOSE, byteArrayOf(0x03, 0xF0.toByte())); saida.flush(); return }   // 1008: em uso
        try {
            while (true) {
                val f = WsFrames.read(dentro, limite) ?: break
                when (f.opcode) {
                    WsFrames.BINARY -> destino(f.payload)
                    WsFrames.PING -> { WsFrames.write(saida, WsFrames.PONG, f.payload); saida.flush() }
                    WsFrames.CLOSE -> { WsFrames.write(saida, WsFrames.CLOSE, ByteArray(0)); saida.flush(); break }
                    else -> {}   // texto e pong: nada a fazer
                }
            }
        } catch (_: IOException) {
            // celular sumiu, prazo de leitura acabou ou quadro inválido: fim
        } finally {
            if (p.kind == Kind.MIC) PhoneMicTv.stop(app) else PhoneCameraTv.stop(app)
        }
    }
}

/** Quadros WebSocket (RFC 6455), só o que o celular manda: sem fragmentos longos, sem extensões. Puro. */
object WsFrames {
    const val TEXT = 1
    const val BINARY = 2
    const val CLOSE = 8
    const val PING = 9
    const val PONG = 10
    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    fun accept(key: String): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray(Charsets.ISO_8859_1)))

    class Frame(val opcode: Int, val payload: ByteArray)

    /**
     * Lê um quadro inteiro. Do navegador todo quadro vem mascarado (RFC 6455 5.1); sem máscara, ou maior que [limite],
     * ou fragmentado = IOException (a conexão fecha). null = fim do fluxo antes de um quadro novo.
     */
    fun read(d: DataInputStream, limite: Int): Frame? {
        val b0 = d.read()
        if (b0 < 0) return null
        val b1 = d.readUnsignedByte()
        if (b0 and 0x80 == 0) throw IOException("quadro fragmentado")
        if (b0 and 0x70 != 0) throw IOException("extensão")
        if (b1 and 0x80 == 0) throw IOException("sem máscara")
        var len = (b1 and 0x7f).toLong()
        if (len == 126L) len = d.readUnsignedShort().toLong() else if (len == 127L) len = d.readLong()
        if (len < 0 || len > limite) throw IOException("quadro grande demais")
        val mask = ByteArray(4).also { d.readFully(it) }
        val payload = ByteArray(len.toInt()).also { d.readFully(it) }
        for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()
        return Frame(b0 and 0x0f, payload)
    }

    /** Escreve um quadro do servidor (sem máscara). */
    fun write(o: OutputStream, opcode: Int, payload: ByteArray) {
        o.write(0x80 or opcode)
        when {
            payload.size < 126 -> o.write(payload.size)
            payload.size < 65536 -> { o.write(126); o.write(payload.size shr 8); o.write(payload.size and 0xff) }
            else -> { o.write(127); for (s in 56 downTo 0 step 8) o.write(((payload.size.toLong() shr s) and 0xff).toInt()) }
        }
        o.write(payload)
    }
}
