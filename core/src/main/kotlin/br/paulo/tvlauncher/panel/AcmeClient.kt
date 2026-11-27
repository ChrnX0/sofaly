package br.paulo.tvlauncher.panel

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Cliente ACME (RFC 8555) para o certificado público da TV (item 26 do Brainstorm 26/11/2026, opção A, em preparo:
 * nada chama isto ainda). A TV pede ao Let's Encrypt `*.<id>.lan.sofaly.tv` com o desafio DNS-01: o valor do TXT vai
 * ao api.sofaly.tv (PUT /v1/dns/txt, assinado pela identidade do relay) e as duas máquinas de DNS (server/dns) o
 * mostram em `_acme-challenge.<id>.lan.sofaly.tv`. A chave do certificado nasce na TV e nunca sai dela: ao servidor
 * vão só o pedido assinado (CSR) e o TXT do desafio.
 *
 * Lógica pura (java.security + org.json), sem Android: o HTTP entra por [Transporte], para o teste rodar contra o
 * Pebble (o servidor ACME de teste do próprio Let's Encrypt) e contra respostas falsas. Teste: AcmeClientTest.
 *
 * Por que só o curinga: o painel responde em `192-168-1-153.<id>.lan.sofaly.tv` (o IP no nome, calculado pelo DNS);
 * o IP muda, o curinga cobre todos. Um pedido = uma autorização = um TXT.
 */
class AcmeClient(
    private val diretorio: String,
    private val http: Transporte,
    private val conta: KeyPair,
    private val dormir: (Long) -> Unit = { Thread.sleep(it) },
) {
    /** Um pedido HTTP. [corpo] null = GET/HEAD sem corpo. Erro de rede = IOException. */
    fun interface Transporte {
        @Throws(IOException::class)
        fun pedir(metodo: String, url: String, corpo: ByteArray?, tipo: String?): Resposta
    }

    /** [cabecalhos] com o nome em minúsculas. */
    class Resposta(val codigo: Int, val cabecalhos: Map<String, String>, val corpo: ByteArray) {
        fun texto() = String(corpo, Charsets.UTF_8)
        fun json() = JSONObject(texto())
    }

    /** Falha do servidor ACME ([tipo] = "urn:ietf:params:acme:error:..."), ou do fluxo. */
    class Falha(val tipo: String, mensagem: String) : IOException(mensagem)

    /** O desafio DNS-01 a publicar: o TXT de `_acme-challenge.<dominio>` tem de valer [valor]. */
    class Desafio(val dominio: String, val valor: String)

    private var urls: JSONObject? = null
    private var nonce: String? = null
    private var kid: String? = null

    /**
     * Pede o certificado para [dominios] com a chave [chaveCertificado] e devolve a cadeia (folha primeiro).
     * [publicar] grava os TXT (todos de uma vez, antes de qualquer resposta ao servidor); [limpar] apaga depois, com
     * sucesso ou não. [tentativas] x [esperaMs] limita a espera pela validação e pela emissão.
     */
    @Throws(IOException::class)
    fun obter(dominios: List<String>, chaveCertificado: KeyPair, publicar: (List<Desafio>) -> Unit, limpar: () -> Unit,
              tentativas: Int = 30, esperaMs: Long = 2_000): List<X509Certificate> {
        require(dominios.isNotEmpty()) { "sem domínios" }
        registrar()
        val novo = assinado(url("newOrder"), JSONObject().put("identifiers", JSONArray().apply {
            for (d in dominios) put(JSONObject().put("type", "dns").put("value", d))
        }))
        val urlPedido = novo.cabecalhos["location"] ?: throw Falha("", "pedido sem Location")
        var pedido = novo.json()
        try {
            val autorizacoes = pedido.getJSONArray("authorizations").let { a -> List(a.length()) { a.getString(it) } }
            val pendentes = mutableListOf<Pair<String, String>>()   // (url da autorização, url do desafio)
            val desafios = mutableListOf<Desafio>()
            for (urlAut in autorizacoes) {
                val aut = assinado(urlAut, null).json()
                if (aut.optString("status") == "valid") continue  // a conta já provou este nome há pouco
                val dns01 = aut.getJSONArray("challenges").let { c -> (0 until c.length()).map { c.getJSONObject(it) } }
                    .firstOrNull { it.optString("type") == "dns-01" } ?: throw Falha("", "servidor sem dns-01")
                val dominio = aut.getJSONObject("identifier").getString("value")   // curinga vem sem o "*."
                desafios += Desafio(dominio, valorTxt(dns01.getString("token"), conta))
                pendentes += urlAut to dns01.getString("url")
            }
            if (desafios.isNotEmpty()) publicar(desafios)
            for ((_, urlDesafio) in pendentes) assinado(urlDesafio, JSONObject())   // "pode conferir"
            for ((urlAut, _) in pendentes) esperar(urlAut, tentativas, esperaMs, setOf("valid"), "autorização")

            pedido = assinado(pedido.getString("finalize"),
                JSONObject().put("csr", b64(csr(dominios, chaveCertificado)))).json()
            if (pedido.optString("status") != "valid") pedido = esperar(urlPedido, tentativas, esperaMs, setOf("valid"), "pedido")
            val pem = assinado(pedido.getString("certificate"), null).texto()
            return cadeia(pem).also { require(it.isNotEmpty()) { "cadeia vazia" } }
        } finally {
            runCatching { limpar() }
        }
    }

    // ---------------------------------------------------------------- conta, nonce, JWS

    private fun url(nome: String): String {
        val d = urls ?: http.pedir("GET", diretorio, null, null).also { exigir(it, 200) }.json().also { urls = it }
        return d.optString(nome).ifEmpty { throw Falha("", "diretório sem $nome") }
    }

    private fun registrar() {
        if (kid != null) return
        val r = assinado(url("newAccount"), JSONObject().put("termsOfServiceAgreed", true), comJwk = true)
        kid = r.cabecalhos["location"] ?: throw Falha("", "conta sem Location")
    }

    private fun novoNonce(): String =
        nonce?.also { nonce = null } ?: http.pedir("HEAD", url("newNonce"), null, null).cabecalhos["replay-nonce"]
            ?: throw Falha("", "sem Replay-Nonce")

    /**
     * POST assinado (JWS). [carga] null = POST-as-GET. badNonce é repetido até [TENTATIVAS_NONCE] vezes, cada vez com o
     * nonce novo que veio na recusa (o Pebble recusa 5% de propósito; duas recusas seguidas aconteceram no teste).
     */
    private fun assinado(destino: String, carga: JSONObject?, comJwk: Boolean = false): Resposta {
        repeat(TENTATIVAS_NONCE) { tentativa ->
            val prot = JSONObject().put("alg", "ES256").put("nonce", novoNonce()).put("url", destino)
            if (comJwk) prot.put("jwk", jwk(conta)) else prot.put("kid", kid)
            val p = b64(prot.toString().toByteArray())
            val c = if (carga == null) "" else b64(carga.toString().toByteArray())
            val corpo = JSONObject().put("protected", p).put("payload", c).put("signature", b64(es256("$p.$c".toByteArray(), conta)))
            val r = http.pedir("POST", destino, corpo.toString().toByteArray(), "application/jose+json")
            r.cabecalhos["replay-nonce"]?.let { nonce = it }
            if (r.codigo in 200..299) return r
            val erro = runCatching { r.json() }.getOrNull()
            val tipo = erro?.optString("type").orEmpty()
            if (tipo.endsWith(":badNonce") && tentativa < TENTATIVAS_NONCE - 1) return@repeat
            throw Falha(tipo, "HTTP ${r.codigo}: ${erro?.optString("detail") ?: r.texto().take(200)}")
        }
        throw Falha("", "inalcançável")
    }

    private fun esperar(destino: String, tentativas: Int, esperaMs: Long, ok: Set<String>, oque: String): JSONObject {
        repeat(tentativas) {
            val o = assinado(destino, null).json()
            when (val s = o.optString("status")) {
                in ok -> return o
                "invalid", "revoked", "deactivated", "expired" -> throw Falha(motivo(o), "$oque $s: ${detalhe(o)}")
            }
            dormir(esperaMs)
        }
        throw Falha("", "$oque não ficou pronto a tempo")
    }

    private fun motivo(o: JSONObject): String = erroDe(o)?.optString("type").orEmpty()
    private fun detalhe(o: JSONObject): String = erroDe(o)?.optString("detail").orEmpty()
    private fun erroDe(o: JSONObject): JSONObject? = o.optJSONObject("error") ?: o.optJSONArray("challenges")?.let { c ->
        (0 until c.length()).firstNotNullOfOrNull { c.getJSONObject(it).optJSONObject("error") }
    }

    private fun exigir(r: Resposta, codigo: Int) {
        if (r.codigo != codigo) throw Falha("", "HTTP ${r.codigo} em vez de $codigo")
    }

    companion object {
        /** Chave EC P-256 (conta ACME e certificado). */
        fun novaChave(): KeyPair =
            KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

        /** O TXT do DNS-01: base64url(SHA-256(token + "." + impressão da chave da conta)) (RFC 8555 §8.1 e §8.4). */
        fun valorTxt(token: String, conta: KeyPair): String =
            b64(sha256("$token.${impressao(conta)}".toByteArray()))

        /** Impressão JWK (RFC 7638): SHA-256 do JSON com os campos em ordem alfabética e sem espaços. */
        fun impressao(conta: KeyPair): String {
            val j = jwk(conta)
            return b64(sha256("""{"crv":"P-256","kty":"EC","x":"${j.getString("x")}","y":"${j.getString("y")}"}""".toByteArray()))
        }

        fun jwk(conta: KeyPair): JSONObject {
            val p = (conta.public as ECPublicKey).w
            return JSONObject().put("kty", "EC").put("crv", "P-256").put("x", b64(fixo32(p.affineX))).put("y", b64(fixo32(p.affineY)))
        }

        /** Assinatura ES256 do JWS: R||S com 32 bytes cada (o Java devolve DER). */
        fun es256(dados: ByteArray, chave: KeyPair): ByteArray {
            val der = Signature.getInstance("SHA256withECDSA").apply { initSign(chave.private); update(dados) }.sign()
            var i = 2 + (if (der[1].toInt() and 0x80 != 0) der[1].toInt() and 0x7f else 0)   // pula SEQUENCE + tamanho
            fun inteiro(): BigInteger {
                require(der[i].toInt() == 0x02) { "DER inesperado" }
                val n = der[i + 1].toInt() and 0xff
                return BigInteger(1, der.copyOfRange(i + 2, i + 2 + n)).also { i += 2 + n }
            }
            return fixo32(inteiro()) + fixo32(inteiro())
        }

        /** Pedido de certificado (PKCS#10) em DER: CN = 1º nome, SAN = todos, assinado com a chave do certificado. */
        fun csr(dominios: List<String>, chave: KeyPair): ByteArray {
            val san = seq(*dominios.map { tlv(0x82, it.toByteArray(Charsets.US_ASCII)) }.toTypedArray())
            val ext = seq(seq(oid(OID_SAN), tlv(0x04, san)))
            val pedidoExt = seq(oid(OID_EXT_REQ), tlv(0x31, ext))
            val info = seq(
                tlv(0x02, byteArrayOf(0)),                                                       // versão 1
                seq(tlv(0x31, seq(oid(OID_CN), tlv(0x0c, dominios.first().toByteArray(Charsets.UTF_8))))),
                chave.public.encoded,                                                             // SubjectPublicKeyInfo
                tlv(0xa0, pedidoExt),                                                             // atributos
            )
            val ass = Signature.getInstance("SHA256withECDSA").apply { initSign(chave.private); update(info) }.sign()
            return seq(info, seq(oid(OID_ECDSA_SHA256)), tlv(0x03, byteArrayOf(0) + ass))
        }

        /** Os certificados de um PEM com vários blocos, na ordem (folha primeiro). */
        fun cadeia(pem: String): List<X509Certificate> {
            val cf = CertificateFactory.getInstance("X.509")
            return Regex("-----BEGIN CERTIFICATE-----([^-]+)-----END CERTIFICATE-----").findAll(pem).map { m ->
                cf.generateCertificate(Base64.getMimeDecoder().decode(m.groupValues[1]).inputStream()) as X509Certificate
            }.toList()
        }

        /** O nome do painel para a TV [id]: `*.<id>.lan.sofaly.tv` no certificado, `192-168-1-153.<id>...` no uso. */
        fun curinga(id: String): String {
            require(Regex("^[a-z2-7]{20}$").matches(id)) { "id inválido" }
            return "*.$id.$ZONA"
        }

        fun nomeComIp(id: String, ip: String): String {
            require(Regex("^(\\d{1,3}\\.){3}\\d{1,3}$").matches(ip)) { "IP inválido" }
            return "${ip.replace('.', '-')}.${curinga(id).removePrefix("*.")}"
        }

        const val ZONA = "lan.sofaly.tv"
        private const val TENTATIVAS_NONCE = 5
        const val LETS_ENCRYPT = "https://acme-v02.api.letsencrypt.org/directory"
        const val LETS_ENCRYPT_TESTE = "https://acme-staging-v02.api.letsencrypt.org/directory"

        private const val OID_CN = "2.5.4.3"
        private const val OID_SAN = "2.5.29.17"
        private const val OID_EXT_REQ = "1.2.840.113549.1.9.14"
        private const val OID_ECDSA_SHA256 = "1.2.840.10045.4.3.2"

        private fun b64(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
        private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

        private fun fixo32(n: BigInteger): ByteArray {
            val b = n.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it }
            return ByteArray(32 - b.size) + b
        }

        private fun tlv(tag: Int, c: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + tamanho(c.size) + c
        private fun tamanho(n: Int): ByteArray = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        }
        private fun seq(vararg p: ByteArray) = tlv(0x30, p.fold(ByteArray(0)) { a, b -> a + b })
        private fun oid(s: String): ByteArray {
            val n = s.split('.').map { it.toLong() }
            var out = byteArrayOf((n[0] * 40 + n[1]).toByte())
            for (v in n.drop(2)) {
                var x = v
                val partes = mutableListOf((x and 0x7f).toByte())
                x = x shr 7
                while (x > 0) { partes.add(0, ((x and 0x7f) or 0x80).toByte()); x = x shr 7 }
                out += partes.toByteArray()
            }
            return tlv(0x06, out)
        }
    }
}
