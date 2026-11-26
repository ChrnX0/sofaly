package br.paulo.tvlauncher.relay

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Criptografia do relay do api.sofaly.tv (decisão A do Paulo, 26/11/2026: acesso de fora de casa sem Tailscale).
 *
 * Porquê: quem compra na Play não vai criar conta no Tailscale, e o servidor (server/api) já tem pareamento por código,
 * caixa postal e envelopes, parados porque o app não falava com eles.
 * Para quê: o celular pareado manda comandos à TV de qualquer lugar; o servidor só carrega envelopes que não consegue ler.
 * Como: cada aparelho tem um par Ed25519 (assina os pedidos ao servidor; o id do aparelho vem dele, como em
 * server/api/src/rules.js) e um par X25519 (cifra os envelopes de ponta a ponta com AES-256-GCM).
 *
 * Tudo aqui é puro (sem Android), com BouncyCastle (já vem no app pelo libadb) e javax.crypto: testado no computador
 * contra vetores gerados pelo próprio código do servidor (RelayCryptoTest).
 */
object RelayCrypto {
    private val aleatorio = SecureRandom()
    fun aleatorios(n: Int): ByteArray = ByteArray(n).also { aleatorio.nextBytes(it) }

    // ---------------------------------------------------------------- Ed25519 (assinatura e id)

    /** Chave pública Ed25519 (32 bytes) da semente privada [seed] (32 bytes). */
    fun edPublica(seed: ByteArray): ByteArray = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded

    fun edAssinar(seed: ByteArray, texto: ByteArray): ByteArray = Ed25519Signer().run {
        init(true, Ed25519PrivateKeyParameters(seed, 0)); update(texto, 0, texto.size); generateSignature()
    }

    fun edConferir(publica: ByteArray, texto: ByteArray, assinatura: ByteArray): Boolean = runCatching {
        Ed25519Signer().run { init(false, Ed25519PublicKeyParameters(publica, 0)); update(texto, 0, texto.size); verifySignature(assinatura) }
    }.getOrDefault(false)

    private const val B32 = "abcdefghijklmnopqrstuvwxyz234567"

    fun base32(bytes: ByteArray): String {
        val sb = StringBuilder()
        var bits = 0; var valor = 0
        for (b in bytes) {
            valor = (valor shl 8) or (b.toInt() and 0xFF); bits += 8
            while (bits >= 5) { sb.append(B32[(valor ushr (bits - 5)) and 31]); bits -= 5 }
            valor = valor and ((1 shl bits) - 1)   // só os bits que faltam (o Int não estoura)
        }
        if (bits > 0) sb.append(B32[(valor shl (5 - bits)) and 31])
        return sb.toString()
    }

    /** Id do aparelho = 20 letras base32 do SHA-256 da chave pública Ed25519 (igual ao idFromKey do servidor). */
    fun idDe(edPublica: ByteArray): String = base32(sha256(edPublica)).take(20)

    val ID_RE = Regex("^[a-z2-7]{20}$")

    fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
    fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    fun b64url(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    fun deB64url(t: String): ByteArray = Base64.getUrlDecoder().decode(t)
    fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    fun deB64(t: String): ByteArray = Base64.getDecoder().decode(t)

    /** O texto que o servidor confere: "MÉTODO\ncaminho\nhora\nsha256(corpo) em hex" (rules.js signedText). */
    fun textoAssinado(metodo: String, caminho: String, horaSeg: Long, corpo: ByteArray): ByteArray =
        "$metodo\n$caminho\n$horaSeg\n${hex(sha256(corpo))}".toByteArray(Charsets.UTF_8)

    /** Cabeçalhos X-Sofaly-* de um pedido assinado. */
    fun cabecalhos(seed: ByteArray, metodo: String, caminho: String, horaSeg: Long, corpo: ByteArray): Map<String, String> = mapOf(
        "X-Sofaly-Key" to b64url(edPublica(seed)),
        "X-Sofaly-Time" to horaSeg.toString(),
        "X-Sofaly-Signature" to b64url(edAssinar(seed, textoAssinado(metodo, caminho, horaSeg, corpo))),
    )

    // ---------------------------------------------------------------- X25519 + HKDF + AES-GCM (envelopes)

    fun xPublica(privada: ByteArray): ByteArray = X25519PrivateKeyParameters(privada, 0).generatePublicKey().encoded

    fun xSegredo(minhaPrivada: ByteArray, publicaDele: ByteArray): ByteArray = ByteArray(32).also {
        X25519Agreement().run { init(X25519PrivateKeyParameters(minhaPrivada, 0)); calculateAgreement(X25519PublicKeyParameters(publicaDele, 0), it, 0) }
    }

    fun hkdf(ikm: ByteArray, sal: ByteArray, info: ByteArray, n: Int = 32): ByteArray = ByteArray(n).also {
        HKDFBytesGenerator(SHA256Digest()).run { init(HKDFParameters(ikm, sal, info)); generateBytes(it, 0, n) }
    }

    const val NONCE = 12

    fun aesCifrar(chave: ByteArray, nonce: ByteArray, texto: ByteArray, aad: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(chave, "AES"), GCMParameterSpec(128, nonce)); updateAAD(aad); doFinal(texto)
        }

    /** Null = chave errada, envelope mexido ou de outro remetente (o AAD não confere). */
    fun aesDecifrar(chave: ByteArray, nonce: ByteArray, cifrado: ByteArray, aad: ByteArray): ByteArray? = runCatching {
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(chave, "AES"), GCMParameterSpec(128, nonce)); updateAAD(aad); doFinal(cifrado)
        }
    }.getOrNull()
}
