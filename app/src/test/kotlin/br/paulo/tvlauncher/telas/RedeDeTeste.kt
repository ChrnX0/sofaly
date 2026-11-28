package br.paulo.tvlauncher.telas

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Proxy
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.cert.Certificate
import java.util.function.Function
import javax.net.ssl.HttpsURLConnection

/**
 * Respostas de rede de mentira para os testes de tela com dados (28/11/2026): as telas que buscam listas na internet
 * (rádios, podcasts, capas) recebem o JSON do teste em vez da rede. Vale para tudo que abre `URL(...).openConnection()`
 * (o Http do core e a maior parte do app); OkHttp não passa por aqui.
 *
 * O Java só deixa instalar a fábrica de URLs uma vez por processo, e o Robolectric recarrega as classes do teste a cada
 * classe. Então a fábrica só usa tipos do Java e lê as respostas de uma Function guardada nas propriedades do sistema
 * ([CHAVE]); o que não tem resposta (ou com as respostas desligadas) vai para a rede de verdade, como antes.
 */
object RedeDeTeste {
    private const val CHAVE = "telas.rede.respostas"
    private const val INSTALADA = "telas.rede.instalada"

    /** [respostas]: endereço -> corpo (String) ou null para seguir para a rede de verdade. */
    fun ligar(respostas: (String) -> String?) {
        instalar()
        System.getProperties()[CHAVE] = Function<String, Any?> { respostas(it) }
    }

    fun desligar() { System.getProperties().remove(CHAVE) }

    private fun instalar() {
        if (System.getProperty(INSTALADA) == "sim") return
        // Endereços criados ANTES da fábrica guardam o tratador padrão do Java: URL(base, endereço) reaproveita o dele.
        val baseHttp = URL("http://localhost/")
        val baseHttps = URL("https://localhost/")
        URL.setURLStreamHandlerFactory { proto ->
            when (proto) {
                "http" -> Tratador(baseHttp)
                "https" -> Tratador(baseHttps)
                else -> null
            }
        }
        System.setProperty(INSTALADA, "sim")
    }

    private class Tratador(private val base: URL) : URLStreamHandler() {
        private fun resposta(u: URL): Any? {
            @Suppress("UNCHECKED_CAST")
            val f = System.getProperties()[CHAVE] as? Function<String, Any?> ?: return null
            return f.apply(u.toString())
        }
        override fun openConnection(u: URL): URLConnection =
            resposta(u)?.let { Falsa(u, it.toString()) } ?: URL(base, u.toString()).openConnection()
        override fun openConnection(u: URL, p: Proxy): URLConnection =
            resposta(u)?.let { Falsa(u, it.toString()) } ?: URL(base, u.toString()).openConnection(p)
    }

    /** Conexão que responde 200 com [corpo] (serve como http e https). */
    private class Falsa(u: URL, corpo: String) : HttpsURLConnection(u) {
        private val bytes = corpo.toByteArray(Charsets.UTF_8)
        private val saida = ByteArrayOutputStream()
        private val tipo = when {
            corpo.trimStart().startsWith("{") || corpo.trimStart().startsWith("[") -> "application/json"
            corpo.trimStart().startsWith("<") -> "application/xml"
            else -> "text/plain"
        }
        override fun connect() { connected = true }
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getResponseMessage() = "OK"
        override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)
        override fun getErrorStream(): InputStream? = null
        override fun getOutputStream(): OutputStream = saida
        override fun getContentType() = "$tipo; charset=utf-8"
        override fun getContentLength() = bytes.size
        override fun getContentLengthLong() = bytes.size.toLong()
        override fun getHeaderField(name: String?): String? = when (name?.lowercase()) {
            "content-type" -> contentType
            "content-length" -> bytes.size.toString()
            else -> null
        }
        override fun getCipherSuite() = "TLS_AES_128_GCM_SHA256"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = throw IOException("sem certificado no teste")
    }
}
