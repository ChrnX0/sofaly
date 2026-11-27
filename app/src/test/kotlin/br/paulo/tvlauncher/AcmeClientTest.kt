package br.paulo.tvlauncher

import br.paulo.tvlauncher.panel.AcmeClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.Signature
import java.security.cert.CertificateFactory
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Cliente ACME do certificado público da TV (item 26, opção A). Partes puras sempre; o fluxo inteiro contra o Pebble
 * (servidor ACME de teste do Let's Encrypt) quando ONETV_PEBBLE aponta para a pasta com pebble + pebble-challtestsrv
 * já rodando (ver scripts/acme-pebble.sh). O Pebble valida de verdade: JWS, nonce, CSR, desafio DNS-01 pelo TXT.
 */
class AcmeClientTest {
    private val b64 = Base64.getUrlDecoder()

    @Test fun es256DaUmaAssinaturaQueOJavaConfere() {
        val k = AcmeClient.novaChave()
        repeat(50) {
            val dados = "teste $it".toByteArray()
            val rs = AcmeClient.es256(dados, k)
            assertEquals(64, rs.size)
            // R||S de volta para DER, para o Java conferir
            fun int(b: ByteArray): ByteArray { var x = b.dropWhile { it == 0.toByte() }.toByteArray(); if (x.isEmpty() || x[0] < 0) x = byteArrayOf(0) + x; return byteArrayOf(2, x.size.toByte()) + x }
            val corpo = int(rs.copyOfRange(0, 32)) + int(rs.copyOfRange(32, 64))
            val der = byteArrayOf(0x30, corpo.size.toByte()) + corpo
            assertTrue(Signature.getInstance("SHA256withECDSA").apply { initVerify(k.public); update(dados) }.verify(der))
        }
    }

    @Test fun jwkTemCoordenadasDe32Bytes() {
        repeat(30) {
            val j = AcmeClient.jwk(AcmeClient.novaChave())
            assertEquals(32, b64.decode(j.getString("x")).size)
            assertEquals(32, b64.decode(j.getString("y")).size)
        }
    }

    @Test fun valorDoTxtEhSha256DaChaveDeAutorizacao() {
        val k = AcmeClient.novaChave()
        val v = AcmeClient.valorTxt("tok", k)
        assertEquals(43, v.length)   // 32 bytes em base64url sem "="
        assertEquals(v, AcmeClient.valorTxt("tok", k))
        assertTrue(v != AcmeClient.valorTxt("tok2", k))
    }

    @Test fun nomesDaTv() {
        val id = "abcdefghijklmnopqrst"
        assertEquals("*.$id.lan.sofaly.tv", AcmeClient.curinga(id))
        assertEquals("192-168-1-153.$id.lan.sofaly.tv", AcmeClient.nomeComIp(id, "192.168.1.153"))
        assertTrue(runCatching { AcmeClient.curinga("ABC") }.isFailure)
        assertTrue(runCatching { AcmeClient.nomeComIp(id, "1.2.3") }.isFailure)
    }

    /** Servidor falso: confere a ordem dos passos, o badNonce repetido e que o TXT sai antes de "pode conferir". */
    @Test fun fluxoContraServidorFalso() {
        val log = mutableListOf<String>()
        var nonce = 0
        var rejeitouUmNonce = false
        val e = br.paulo.tvlauncher.panel.CertificadoX509.emitir(listOf("x.lan.sofaly.tv"), null, 24, "teste")
        val pem = br.paulo.tvlauncher.panel.CertificadoX509.pem(e.servidor) + br.paulo.tvlauncher.panel.CertificadoX509.pem(e.ca)
        val h = AcmeClient.Transporte { m, url, corpo, _ ->
            val n = mapOf("replay-nonce" to "n${nonce++}")
            fun ok(cod: Int, json: String, extra: Map<String, String> = emptyMap()) = AcmeClient.Resposta(cod, n + extra, json.toByteArray())
            if (m == "GET") return@Transporte ok(200, """{"newNonce":"h/nonce","newAccount":"h/acct","newOrder":"h/order"}""")
            if (m == "HEAD") return@Transporte ok(200, "")
            val env = JSONObject(String(corpo!!))
            val prot = JSONObject(String(b64.decode(env.getString("protected"))))
            assertEquals(url, prot.getString("url"))
            log += url
            if (url == "h/order" && !rejeitouUmNonce) { rejeitouUmNonce = true; return@Transporte ok(400, """{"type":"urn:ietf:params:acme:error:badNonce"}""") }
            when (url) {
                "h/acct" -> { assertTrue(prot.has("jwk")); ok(201, "{}", mapOf("location" to "h/acct/1")) }
                "h/order" -> { assertEquals("h/acct/1", prot.getString("kid")); ok(201, """{"status":"pending","authorizations":["h/authz"],"finalize":"h/fin"}""", mapOf("location" to "h/order/1")) }
                "h/authz" -> if (log.count { it == "h/authz" } == 1)
                    ok(200, """{"status":"pending","identifier":{"type":"dns","value":"x.lan.sofaly.tv"},"challenges":[{"type":"http-01","url":"h/h","token":"a"},{"type":"dns-01","url":"h/chal","token":"tok"}]}""")
                    else ok(200, """{"status":"valid"}""")
                "h/chal" -> { assertTrue("TXT antes de pedir a conferência", log.contains("publicou")); ok(200, "{}") }
                "h/fin" -> ok(200, """{"status":"valid","certificate":"h/cert"}""")
                "h/cert" -> AcmeClient.Resposta(200, n, pem.toByteArray())
                else -> error(url)
            }
        }
        val conta = AcmeClient.novaChave()
        var limpou = false
        val cadeia = AcmeClient(diretorio = "h/dir", http = h, conta = conta, dormir = {}).obter(
            listOf("*.x.lan.sofaly.tv"), AcmeClient.novaChave(),
            publicar = { d -> assertEquals(1, d.size); assertEquals("x.lan.sofaly.tv", d[0].dominio); assertEquals(AcmeClient.valorTxt("tok", conta), d[0].valor); log += "publicou" },
            limpar = { limpou = true })
        assertEquals(2, cadeia.size)
        assertTrue(limpou)
        assertTrue(rejeitouUmNonce)
    }

    @Test fun falhaDoDesafioDizOMotivoELimpa() {
        var limpou = false
        var nonce = 0
        val h = AcmeClient.Transporte { m, url, _, _ ->
            val n = mapOf("replay-nonce" to "n${nonce++}")
            when {
                m == "GET" -> AcmeClient.Resposta(200, n, """{"newNonce":"nn","newAccount":"a","newOrder":"o"}""".toByteArray())
                m == "HEAD" -> AcmeClient.Resposta(200, n, ByteArray(0))
                url == "a" -> AcmeClient.Resposta(201, n + ("location" to "k"), "{}".toByteArray())
                url == "o" -> AcmeClient.Resposta(201, n + ("location" to "o/1"), """{"authorizations":["z"],"finalize":"f"}""".toByteArray())
                url == "z" && nonce < 6 -> AcmeClient.Resposta(200, n, """{"status":"pending","identifier":{"value":"d"},"challenges":[{"type":"dns-01","url":"c","token":"t"}]}""".toByteArray())
                url == "z" -> AcmeClient.Resposta(200, n, """{"status":"invalid","challenges":[{"type":"dns-01","error":{"type":"urn:ietf:params:acme:error:dns","detail":"NXDOMAIN"}}]}""".toByteArray())
                url == "c" -> AcmeClient.Resposta(200, n, "{}".toByteArray())
                else -> error(url)
            }
        }
        val e = runCatching {
            AcmeClient("dir", h, AcmeClient.novaChave(), dormir = {}).obter(listOf("d"), AcmeClient.novaChave(), {}, { limpou = true })
        }.exceptionOrNull() as AcmeClient.Falha
        assertEquals("urn:ietf:params:acme:error:dns", e.tipo)
        assertTrue(e.message!!.contains("NXDOMAIN"))
        assertTrue(limpou)
    }

    /** De ponta a ponta contra o Pebble de verdade (pula sem ONETV_PEBBLE). */
    @Test fun pebbleEmiteOCuringa() {
        val pasta = System.getenv("ONETV_PEBBLE")
        assumeTrue("ONETV_PEBBLE não definido", !pasta.isNullOrBlank())
        val ca = CertificateFactory.getInstance("X.509").generateCertificate(File(pasta, "pebble.minica.pem").inputStream())
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("pebble", ca) }
        val tls = SSLContext.getInstance("TLS").apply { init(null, TrustManagerFactory.getInstance("PKIX").apply { init(ks) }.trustManagers, null) }
        val http = AcmeClient.Transporte { m, url, corpo, tipo ->
            val c = URL(url).openConnection() as HttpsURLConnection
            c.sslSocketFactory = tls.socketFactory
            c.requestMethod = m
            if (corpo != null) { c.doOutput = true; c.setRequestProperty("Content-Type", tipo); c.outputStream.use { it.write(corpo) } }
            val cod = c.responseCode
            val txt = (if (cod >= 400) c.errorStream else c.inputStream)?.use { it.readBytes() } ?: ByteArray(0)
            AcmeClient.Resposta(cod, c.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.first() }, txt)
        }
        fun gestao(rota: String, json: String) {
            val c = URL("http://127.0.0.1:8055/$rota").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.outputStream.use { it.write(json.toByteArray()) }
            assertEquals(200, c.responseCode)
        }
        val id = "abcdefghijklmnopqrst"
        val chave = AcmeClient.novaChave()
        var limpou = false
        val cadeia = AcmeClient("https://127.0.0.1:14000/dir", http, AcmeClient.novaChave(), dormir = { Thread.sleep(500) }).obter(
            listOf(AcmeClient.curinga(id)), chave,
            publicar = { ds -> for (d in ds) gestao("set-txt", """{"host":"_acme-challenge.${d.dominio}.","value":"${d.valor}"}""") },
            limpar = { gestao("clear-txt", """{"host":"_acme-challenge.$id.lan.sofaly.tv."}"""); limpou = true })
        val folha = cadeia.first()
        assertEquals(chave.public, folha.publicKey)
        assertTrue(folha.subjectAlternativeNames.any { it[1] == "*.$id.lan.sofaly.tv" })
        assertTrue(cadeia.size >= 2)   // folha + intermediária
        assertTrue(limpou)
    }
}
