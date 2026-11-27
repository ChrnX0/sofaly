package br.paulo.tvlauncher.panel

import android.content.Context
import br.paulo.tvlauncher.companion.PhoneMicTv
import br.paulo.tvlauncher.companion.PhoneCameraTv
import br.paulo.tvlauncher.companion.AudioSyncTv
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

    /** MIC = voz, CAM = fotos, CLOCK = pings de relógio para acertar o som (sem abrir nada na TV). */
    enum class Kind { MIC, CAM, CLOCK }
    data class Pedido(val accept: String, val kind: Kind)

    /** Confere o pedido de WebSocket: upgrade, chave, mesma origem e o tipo (k=mic|cam). null = recusa. */
    fun handshake(upgrade: String?, key: String?, origin: String?, host: String?, k: String?): Pedido? {
        if (!upgrade.equals("websocket", ignoreCase = true)) return null
        if (key == null || !Regex("^[A-Za-z0-9+/]{22}==$").matches(key)) return null
        // Mesma origem: uma página de outro site não abre o microfone da TV com o cookie da pessoa (CSWSH).
        if (origin == null || host == null || origin != "https://$host") return null
        val kind = when (k) { "mic" -> Kind.MIC; "cam" -> Kind.CAM; "clock" -> Kind.CLOCK; else -> return null }
        return Pedido(WsFrames.accept(key), kind)
    }

    fun serve(app: Context, p: Pedido, entrada: InputStream, saida: OutputStream) {
        saida.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: ${p.accept}\r\n\r\n").toByteArray(Charsets.ISO_8859_1))
        saida.flush()
        val dentro = DataInputStream(entrada)
        if (p.kind == Kind.CLOCK) { relogio(dentro, saida); return }
        val destino: (ByteArray) -> Unit = when (p.kind) {
            Kind.MIC -> { b -> PhoneMicTv.chunk(app, b) }
            Kind.CAM -> { b -> PhoneCameraTv.frame(app, b) }
            Kind.CLOCK -> { _ -> }   // não chega aqui (saiu acima)
        }
        val limite = if (p.kind == Kind.CAM) 512 * 1024 else 64 * 1024
        // A voz não espera o resto do painel (as threads do painel rodam em BACKGROUND): fila de áudio vazia = buraco na voz.
        runCatching { android.os.Process.setThreadPriority(if (p.kind == Kind.MIC) android.os.Process.THREAD_PRIORITY_URGENT_AUDIO else android.os.Process.THREAD_PRIORITY_DEFAULT) }
        // TV dormindo (tela apagada): nada apareceria nem sairia som. O celular diz isso em vez de "ao vivo" no vazio.
        val acesa = runCatching { app.getSystemService(android.os.PowerManager::class.java)?.isInteractive }.getOrNull() != false
        if (!acesa) { fechar(saida, WsFrames.CLOSE_TV_OFF); return }
        // Um celular por vez: se outro já está com o microfone (ou a câmera), esta conexão fecha sem desligar o dele.
        val comecou = if (p.kind == Kind.MIC) PhoneMicTv.start(app) else PhoneCameraTv.start(app)
        if (!comecou) { fechar(saida, WsFrames.CLOSE_IN_USE); return }
        try {
            val leitor = WsFrames.Reader(dentro, limite)
            while (true) {
                val f = leitor.next() ?: break
                when (f.opcode) {
                    WsFrames.BINARY -> {
                        destino(f.payload)
                        // VOLTAR na TV fechou a câmera: avisa o celular, que para de mandar e diz isso na tela.
                        if (p.kind == Kind.CAM && PhoneCameraTv.dismissed) { fechar(saida, WsFrames.CLOSE_DISMISSED); break }
                    }
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

    private fun fechar(saida: OutputStream, codigo: Int) {
        runCatching { WsFrames.write(saida, WsFrames.CLOSE, byteArrayOf((codigo shr 8).toByte(), (codigo and 0xff).toByte())); saida.flush() }
    }

    /**
     * Pings de relógio: cada quadro do celular volta como texto com a hora da TV ([AudioSyncTv.nowMs]). Pela mesma conexão
     * a ida e a volta passam pelo mesmo caminho, que é o que [br.paulo.tvlauncher.companion.AudioSyncMath.clockOffset]
     * supõe. No máximo 40 pings por conexão.
     */
    private fun relogio(dentro: DataInputStream, saida: OutputStream) {
        try {
            val leitor = WsFrames.Reader(dentro, 64)
            repeat(40) {
                val f = leitor.next() ?: return
                when (f.opcode) {
                    WsFrames.TEXT, WsFrames.BINARY -> {
                        WsFrames.write(saida, WsFrames.TEXT, AudioSyncTv.nowMs().toString().toByteArray(Charsets.US_ASCII)); saida.flush()
                    }
                    WsFrames.PING -> { WsFrames.write(saida, WsFrames.PONG, f.payload); saida.flush() }
                    WsFrames.CLOSE -> { WsFrames.write(saida, WsFrames.CLOSE, ByteArray(0)); saida.flush(); return }
                }
            }
        } catch (_: IOException) {}
    }
}

/** Quadros WebSocket (RFC 6455), só o que o celular manda: sem extensões. Puro. */
object WsFrames {
    const val CONTINUATION = 0
    const val TEXT = 1
    const val BINARY = 2
    const val CLOSE = 8
    const val PING = 9
    const val PONG = 10
    /** Códigos de fechamento que o painel entende (companion.js): 1008 = outro celular já usa; 4xxx = nossos. */
    const val CLOSE_IN_USE = 1008
    const val CLOSE_DISMISSED = 4001
    const val CLOSE_TV_OFF = 4002
    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    fun accept(key: String): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray(Charsets.ISO_8859_1)))

    class Frame(val opcode: Int, val payload: ByteArray, val fin: Boolean = true)

    /**
     * Junta as mensagens que o navegador parte em vários quadros. O Chrome (e o Brave) partem toda mensagem acima de
     * 64 KiB: antes a TV recusava o quadro partido e a câmera caía na primeira foto grande (revisão, 25/11/2026).
     * [next] devolve uma mensagem inteira (TEXT/BINARY) ou um quadro de controle (que pode vir no meio das partes).
     */
    class Reader(private val d: DataInputStream, private val limite: Int) {
        private var partes: java.io.ByteArrayOutputStream? = null
        private var tipo = 0
        fun next(): Frame? {
            while (true) {
                val f = read(d, limite) ?: return null
                if (f.opcode >= 8) {
                    if (!f.fin || f.payload.size > 125) throw IOException("controle inválido")
                    return f
                }
                if (f.opcode == CONTINUATION) {
                    val p = partes ?: throw IOException("continuação sem começo")
                    if (p.size() + f.payload.size > limite) throw IOException("mensagem grande demais")
                    p.write(f.payload)
                    if (f.fin) { partes = null; return Frame(tipo, p.toByteArray()) }
                } else {
                    if (partes != null) throw IOException("mensagem nova no meio de outra")
                    if (f.fin) return f
                    tipo = f.opcode
                    partes = java.io.ByteArrayOutputStream(f.payload.size * 2).also { it.write(f.payload) }
                }
            }
        }
    }

    /**
     * Lê um quadro (pode ser parte de uma mensagem: [Frame.fin] false ou opcode [CONTINUATION]; use [Reader]). Do
     * navegador todo quadro vem mascarado (RFC 6455 5.1); sem máscara ou maior que [limite] = IOException (a conexão
     * fecha). null = fim do fluxo antes de um quadro novo.
     */
    fun read(d: DataInputStream, limite: Int): Frame? {
        val b0 = d.read()
        if (b0 < 0) return null
        val b1 = d.readUnsignedByte()
        if (b0 and 0x70 != 0) throw IOException("extensão")
        if (b1 and 0x80 == 0) throw IOException("sem máscara")
        var len = (b1 and 0x7f).toLong()
        if (len == 126L) len = d.readUnsignedShort().toLong() else if (len == 127L) len = d.readLong()
        if (len < 0 || len > limite) throw IOException("quadro grande demais")
        val mask = ByteArray(4).also { d.readFully(it) }
        val payload = ByteArray(len.toInt()).also { d.readFully(it) }
        for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i and 3].toInt()).toByte()
        return Frame(b0 and 0x0f, payload, b0 and 0x80 != 0)
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
