package br.paulo.onetv.celular

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import kotlin.concurrent.thread

/**
 * Todo POST do app passa por [ApiTv.prepararEConectar]. Até 27/11/2026 o app conectava antes de pôr os cabeçalhos e o
 * corpo: o HttpURLConnection recusava ("Already connected") e o controle nativo, o PIN do "Usar fora de casa" e o envio
 * de arquivos diziam "A TV não respondeu" com a TV ali. Aqui um servidor HTTP mínimo num ServerSocket de verdade confere
 * que o pedido chega com cabeçalho e corpo. (ServerSocket, e não o HttpServer do JDK: o teste do módulo Android compila
 * contra o android.jar, que não tem com.sun.net.httpserver.)
 */
class PedidoNaOrdemTest {
    private lateinit var servidor: ServerSocket
    @Volatile private var recebido = ""
    @Volatile private var cabecalho: String? = null

    /** Atende uma conexão: lê as linhas do cabeçalho, o corpo pelo Content-Length e responde 200 {"ok":true}. */
    @Before fun subir() {
        servidor = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            runCatching {
                servidor.accept().use { s ->
                    val ent = BufferedInputStream(s.getInputStream())
                    fun linha(): String {
                        val b = StringBuilder()
                        while (true) { val c = ent.read(); if (c < 0 || c == '\n'.code) break; if (c != '\r'.code) b.append(c.toChar()) }
                        return b.toString()
                    }
                    linha() // "POST /api/tela/tecla HTTP/1.1"
                    var tamanho = 0
                    while (true) {
                        val l = linha(); if (l.isEmpty()) break
                        val (nome, valor) = l.split(":", limit = 2).map { it.trim() }
                        if (nome.equals("Content-Length", true)) tamanho = valor.toInt()
                        if (nome.equals("X-Painel", true)) cabecalho = valor
                    }
                    recebido = String(ByteArray(tamanho).also { var lido = 0; while (lido < tamanho) lido += ent.read(it, lido, tamanho - lido) })
                    val r = """{"ok":true}"""
                    s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${r.length}\r\nConnection: close\r\n\r\n$r".toByteArray())
                    s.getOutputStream().flush()
                }
            }
        }
    }

    @After fun descer() = servidor.close()

    private fun conexao() = URL("http://127.0.0.1:${servidor.localPort}/api/tela/tecla").openConnection() as HttpURLConnection

    @Test fun postChegaComCabecalhoECorpo() {
        val c = conexao()
        c.requestMethod = "POST"
        c.setRequestProperty("X-Painel", "1")
        ApiTv.prepararEConectar(c) { x ->
            x.setRequestProperty("Content-Type", "application/json")
            x.doOutput = true
            x.outputStream.use { it.write("""{"tecla":"inicio"}""".toByteArray()) }
        }
        assertEquals(200, c.responseCode)
        assertEquals("""{"tecla":"inicio"}""", recebido)
        assertEquals("1", cabecalho)
    }

    @Test fun getTambemConecta() {
        val c = conexao()
        ApiTv.prepararEConectar(c) { }
        assertEquals(200, c.responseCode)
    }
}
