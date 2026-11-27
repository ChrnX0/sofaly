package br.paulo.onetv.celular

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL

/**
 * Todo POST do app passa por [ApiTv.prepararEConectar]. Até 27/11/2026 o app conectava antes de pôr os cabeçalhos e o
 * corpo: o HttpURLConnection recusava ("Already connected") e o controle nativo, o PIN do "Usar fora de casa" e o envio
 * de arquivos diziam "A TV não respondeu" com a TV ali. Aqui um servidor HTTP de verdade (o do próprio JDK) confere
 * que o pedido chega com cabeçalho e corpo.
 */
class PedidoNaOrdemTest {
    private lateinit var servidor: HttpServer
    private var recebido = ""
    private var cabecalho: String? = null

    @Before fun subir() {
        servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        servidor.createContext("/api/tela/tecla") { t ->
            recebido = t.requestBody.readBytes().decodeToString()
            cabecalho = t.requestHeaders.getFirst("X-Painel")
            val r = """{"ok":true}""".toByteArray()
            t.sendResponseHeaders(200, r.size.toLong()); t.responseBody.use { it.write(r) }
        }
        servidor.start()
    }

    @After fun descer() = servidor.stop(0)

    @Test fun postChegaComCabecalhoECorpo() {
        val c = URL("http://127.0.0.1:${servidor.address.port}/api/tela/tecla").openConnection() as HttpURLConnection
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
        val c = URL("http://127.0.0.1:${servidor.address.port}/api/tela/tecla").openConnection() as HttpURLConnection
        ApiTv.prepararEConectar(c) { }
        assertEquals(200, c.responseCode)
    }
}
