package br.paulo.tvlauncher.relay

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * WebSocket mínimo (RFC 6455) para a caixa postal: GET /v1/inbox assinado. O servidor (Durable Object hibernável)
 * empurra cada envelope na hora em que chega; parado, não custa nada lá. O app não tem OkHttp e o HttpURLConnection
 * não faz WebSocket, por isso este cliente pequeno: só texto, ping/pong e fechar.
 *
 * TLS: o SSLSocket padrão NÃO confere o nome do servidor sozinho; [conectar] liga a conferência no handshake
 * (endpointIdentificationAlgorithm = "HTTPS", a mesma regra do https comum). Nunca desligar essa conferência.
 * Só JVM (sem Android): roda no teste e foi conferido contra o api.sofaly.tv de verdade.
 */
class RelaySocket private constructor(private val sock: SSLSocket, private val entrada: InputStream, private val saida: OutputStream) {

    companion object {
        private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        const val MAX_MENSAGEM = 200 * 1024   // um envelope (64 KiB) em base64 + JSON, com folga

        fun aceite(chave: String): String =
            RelayCrypto.b64(MessageDigest.getInstance("SHA-1").digest((chave + GUID).toByteArray(Charsets.US_ASCII)))

        /** Abre a conexão assinada com [seed] em wss://[host]/v1/inbox. Erro = IOException. */
        @Throws(IOException::class)
        fun conectar(host: String, seed: ByteArray, userAgent: String, agoraSeg: Long, timeoutMs: Int = 10_000): RelaySocket {
            val s = Socket()   // TCP puro; o TLS vai por cima com o nome do servidor (SNI)
            try {
                s.connect(InetSocketAddress(host, 443), timeoutMs)
                val sni = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(s, host, 443, true) as SSLSocket
                sni.soTimeout = timeoutMs
                // Confere que o certificado é de [host] no próprio handshake (igual ao https comum; Android 7+ e JVM).
                sni.sslParameters = sni.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                sni.startHandshake()
                val chave = RelayCrypto.b64(RelayCrypto.aleatorios(16))
                val cab = RelayCrypto.cabecalhos(seed, "GET", "/v1/inbox", agoraSeg, ByteArray(0))
                val pedido = buildString {
                    append("GET /v1/inbox HTTP/1.1\r\nHost: $host\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n")
                    append("Sec-WebSocket-Key: $chave\r\nSec-WebSocket-Version: 13\r\nUser-Agent: $userAgent\r\n")
                    for ((k, v) in cab) append("$k: $v\r\n")
                    append("\r\n")
                }
                val out = sni.outputStream; val inp = sni.inputStream
                out.write(pedido.toByteArray(Charsets.US_ASCII)); out.flush()
                val linhas = lerCabecalho(inp)
                val status = linhas.firstOrNull() ?: throw IOException("sem resposta")
                if (!status.startsWith("HTTP/1.1 101")) { sni.close(); throw IOException("recusado: $status") }
                val aceito = linhas.drop(1).firstOrNull { it.lowercase().startsWith("sec-websocket-accept:") }?.substringAfter(':')?.trim()
                if (aceito != aceite(chave)) { sni.close(); throw IOException("aceite errado") }
                return RelaySocket(sni, inp, out)
            } catch (e: IOException) { runCatching { s.close() }; throw e }
        }

        private fun lerCabecalho(inp: InputStream): List<String> {
            val b = ByteArrayOutputStream()
            var ultimos = 0
            while (true) {
                val c = inp.read(); if (c < 0) throw IOException("fechou no cabeçalho")
                b.write(c); ultimos = (ultimos shl 8) or c
                if (ultimos == 0x0D0A0D0A) break
                if (b.size() > 16 * 1024) throw IOException("cabeçalho grande demais")
            }
            return b.toString(Charsets.US_ASCII.name()).split("\r\n").filter { it.isNotEmpty() }
        }

        /** Quadro do cliente (sempre mascarado, RFC 6455 5.3). Puro, para o teste. */
        fun quadro(opcode: Int, dados: ByteArray, mascara: ByteArray = RelayCrypto.aleatorios(4)): ByteArray {
            val o = ByteArrayOutputStream()
            o.write(0x80 or opcode)
            when {
                dados.size < 126 -> o.write(0x80 or dados.size)
                dados.size < 65536 -> { o.write(0x80 or 126); o.write(dados.size ushr 8); o.write(dados.size and 0xFF) }
                else -> { o.write(0x80 or 127); for (i in 7 downTo 0) o.write(((dados.size.toLong() ushr (8 * i)) and 0xFF).toInt()) }
            }
            o.write(mascara)
            for (i in dados.indices) o.write(dados[i].toInt() xor mascara[i % 4].toInt())
            return o.toByteArray()
        }

        /** Lê um quadro do servidor (sem máscara). Devolve opcode e dados; -1 = fechou. Puro, para o teste. */
        fun lerQuadro(inp: InputStream): Pair<Int, ByteArray> {
            val b0 = inp.read(); if (b0 < 0) return -1 to ByteArray(0)
            val b1 = inp.read(); if (b1 < 0) return -1 to ByteArray(0)
            var n = (b1 and 0x7F).toLong()
            if (n == 126L) n = ((ler(inp) shl 8) or ler(inp)).toLong()
            else if (n == 127L) { n = 0; repeat(8) { n = (n shl 8) or ler(inp).toLong() } }
            if (n > MAX_MENSAGEM) throw IOException("mensagem grande demais")
            val masc = if (b1 and 0x80 != 0) ByteArray(4) { ler(inp).toByte() } else null
            val d = ByteArray(n.toInt()); var lido = 0
            while (lido < d.size) { val r = inp.read(d, lido, d.size - lido); if (r < 0) throw IOException("fechou no meio"); lido += r }
            if (masc != null) for (i in d.indices) d[i] = (d[i].toInt() xor masc[i % 4].toInt()).toByte()
            if (b0 and 0x80 == 0) throw IOException("mensagem em pedaços não suportada")
            return (b0 and 0x0F) to d
        }

        private fun ler(inp: InputStream): Int { val c = inp.read(); if (c < 0) throw IOException("fechou"); return c }
    }

    /** Hora do último quadro recebido (pong incluído): conexão meio-aberta não recebe nada, nem o pong. */
    @Volatile var ultimoQuadro: Long = System.currentTimeMillis()
        private set

    /** Próxima mensagem de texto (responde ping sozinho). Null = conexão fechada. Timeout = SocketTimeoutException. */
    @Throws(IOException::class)
    fun receber(): String? {
        while (true) {
            val (op, d) = lerQuadro(entrada)
            ultimoQuadro = System.currentTimeMillis()
            when (op) {
                -1, 8 -> return null
                1 -> return String(d, Charsets.UTF_8)
                9 -> enviarQuadro(10, d)   // ping -> pong
                else -> {}   // pong ou binário: ignora
            }
        }
    }

    @Synchronized @Throws(IOException::class) private fun enviarQuadro(op: Int, d: ByteArray) { saida.write(quadro(op, d)); saida.flush() }

    @Throws(IOException::class) fun enviar(texto: String) = enviarQuadro(1, texto.toByteArray(Charsets.UTF_8))
    @Throws(IOException::class) fun ping() = enviarQuadro(9, ByteArray(0))
    fun tempoDeLeitura(ms: Int) { sock.soTimeout = ms }

    fun fechar() {
        runCatching { enviarQuadro(8, byteArrayOf(0x03, 0xE8.toByte())) }
        runCatching { sock.close() }
    }
}
