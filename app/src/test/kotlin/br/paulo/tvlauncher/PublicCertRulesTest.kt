package br.paulo.tvlauncher

import br.paulo.tvlauncher.panel.CertificadoX509
import br.paulo.tvlauncher.panel.PublicCertRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.thread

/** Certificado público da TV (item 26, opção A): escolha pelo SNI num aperto de mão TLS de verdade, renovação, nomes. */
class PublicCertRulesTest {
    private val id = "abcdefghijklmnopqrst"
    private val sufixo = "$id.lan.sofaly.tv"
    private val publico = "192-168-1-153.$sufixo"

    @Test fun usaPublicoSoNoNomeDestaTv() {
        assertTrue(PublicCertRules.usaPublico(publico, sufixo, true))
        assertTrue(PublicCertRules.usaPublico(publico.uppercase() + ".", sufixo, true))
        assertFalse(PublicCertRules.usaPublico(publico, sufixo, false))            // sem certificado público
        assertFalse(PublicCertRules.usaPublico("tv", sufixo, true))
        assertFalse(PublicCertRules.usaPublico(null, sufixo, true))                 // IP: sem SNI
        assertFalse(PublicCertRules.usaPublico(sufixo, sufixo, true))               // sem o rótulo do IP
        assertFalse(PublicCertRules.usaPublico("a.b.$sufixo", sufixo, true))        // dois rótulos: o curinga não cobre
        assertFalse(PublicCertRules.usaPublico("192-168-1-153.outraTVxxxxxxxxxxxxx.lan.sofaly.tv", sufixo, true))
        assertFalse(PublicCertRules.usaPublico("x$sufixo", sufixo, true))           // sufixo grudado
    }

    @Test fun renovaQuandoFaltaUmTerco() {
        val dia = 86_400_000L
        val ini = 0L; val fim = 90 * dia
        assertTrue(PublicCertRules.deveTentar(null, null, 5 * dia, 0, 0))           // sem certificado
        assertFalse(PublicCertRules.deveTentar(ini, fim, 59 * dia, 0, 0))
        assertTrue(PublicCertRules.deveTentar(ini, fim, 60 * dia, 0, 0))
        assertTrue(PublicCertRules.deveTentar(ini, fim, 91 * dia, 0, 0))            // vencido
        // falhou 3 vezes: espera 4 h desde a última tentativa
        val ultima = 60 * dia
        assertFalse(PublicCertRules.deveTentar(ini, fim, ultima + 3 * 3_600_000L, 3, ultima))
        assertTrue(PublicCertRules.deveTentar(ini, fim, ultima + 4 * 3_600_000L, 3, ultima))
        assertEquals(24 * 3_600_000L, PublicCertRules.espera(50))                   // teto de 1 dia
        assertEquals(0L, PublicCertRules.espera(0))
    }

    @Test fun serveSoComONomeDaTvEDentroDaValidade() {
        val agora = System.currentTimeMillis()
        val e = CertificadoX509.emitir(listOf("*.$sufixo"), null, 24, "t", agora)
        val cadeia = arrayOf(e.servidor, e.ca)
        assertTrue(PublicCertRules.serve(cadeia, id, agora))
        assertFalse(PublicCertRules.serve(cadeia, "bbbbbbbbbbbbbbbbbbbb", agora))
        assertFalse(PublicCertRules.serve(cadeia, id, e.servidor.notAfter.time))
        assertFalse(PublicCertRules.serve(null, id, agora))
        assertFalse(PublicCertRules.serve(cadeia, null, agora))
        assertEquals(setOf(publico, "10-0-0-2.$sufixo"), PublicCertRules.nomes(id, listOf("192.168.1.153", "10.0.0.2", "lixo")))
        assertEquals(emptySet<String>(), PublicCertRules.nomes(null, listOf("192.168.1.153")))
    }

    /** O mesmo servidor entrega o público para o nome público e o da TV para "tv" e para o IP (sem SNI). */
    @Test fun handshakeEscolhePeloSni() {
        val agora = System.currentTimeMillis()
        val propria = CertificadoX509.emitir(listOf("tv", "tv.lan", "tv.local"), "127.0.0.1", 8, "Propria", agora)
        val pub = CertificadoX509.emitir(listOf("*.$sufixo"), null, 24, "Publica", agora)
        var temPublico = true
        val km = PublicCertRules.PorNome(
            { PublicCertRules.Par(propria.chaveServidor, arrayOf(propria.servidor, propria.ca)) },
            { if (temPublico) PublicCertRules.Par(pub.chaveServidor, arrayOf(pub.servidor, pub.ca)) else null },
            { sufixo })
        val srv = SSLContext.getInstance("TLS").apply { init(arrayOf(km), null, null) }
        val ss = srv.serverSocketFactory.createServerSocket(0, 8, InetAddress.getLoopbackAddress()) as SSLServerSocket
        val atende = thread(isDaemon = true) {
            while (!ss.isClosed) try { ss.accept().use { s -> s.getOutputStream().apply { write("ok".toByteArray()); flush() } } } catch (_: Exception) {}
        }
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("a", propria.ca); setCertificateEntry("b", pub.ca) }
        // Contexto novo a cada conexão: sem isso o cliente retoma a sessão anterior (com o certificado de antes).
        fun cli() = SSLContext.getInstance("TLS").apply { init(null, TrustManagerFactory.getInstance("PKIX").apply { init(ks) }.trustManagers, null) }

        /** Conecta pedindo [sni] (null = como um IP, sem SNI) e devolve o certificado que o servidor mandou. */
        fun folha(sni: String?, conferirNome: Boolean): X509Certificate {
            val cru = Socket(InetAddress.getLoopbackAddress(), ss.localPort)
            val s = cli().socketFactory.createSocket(cru, sni ?: "127.0.0.1", ss.localPort, true) as SSLSocket
            s.sslParameters = s.sslParameters.apply {
                serverNames = if (sni == null) emptyList() else listOf(SNIHostName(sni))
                if (conferirNome) endpointIdentificationAlgorithm = "HTTPS"
            }
            return s.use { it.startHandshake(); assertEquals("ok", String(it.inputStream.readNBytes(2))); it.session.peerCertificates[0] as X509Certificate }
        }
        assertEquals(pub.servidor, folha(publico, conferirNome = true))   // o navegador aceita o nome público
        assertEquals(propria.servidor, folha("tv", conferirNome = true))
        assertEquals(propria.servidor, folha(null, conferirNome = false))  // app do celular pelo IP: o fixado de sempre
        temPublico = false                                                 // venceu/sumiu: volta ao da TV sem reabrir a porta
        assertEquals(propria.servidor, folha(publico, conferirNome = false))
        ss.close(); atende.join(2000)
    }
}
