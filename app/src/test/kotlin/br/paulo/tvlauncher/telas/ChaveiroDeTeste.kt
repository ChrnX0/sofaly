package br.paulo.tvlauncher.telas

import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.Enumeration
import javax.crypto.KeyGenerator
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey

/**
 * "AndroidKeyStore" de mentira para os testes de tela com dados (28/11/2026). O Robolectric não traz o chaveiro do
 * Android, e as contas do player, dos serviços de filmes e outras coisas são cifradas com uma chave dele
 * (PlayerArmazem.cifrar): sem chaveiro, "não há lista de TV" e Filmes, Séries e TV abrem vazias.
 *
 * Aqui: o KeyStore guarda as chaves na memória e o KeyGenerator aceita o KeyGenParameterSpec e gera uma AES comum (o
 * Cipher "AES/GCM/NoPadding" do Java faz o resto). Só para teste: nada disso existe no app.
 */
object ChaveiroDeTeste {
    private val chaves = Collections.synchronizedMap(HashMap<String, SecretKey>())

    fun instalar() {
        if (Security.getProvider("AndroidKeyStore") != null) return
        Security.addProvider(Provedor())
    }

    private class Provedor : Provider("AndroidKeyStore", 1.0, "chaveiro de teste") {
        init {
            put("KeyStore.AndroidKeyStore", Loja::class.java.name)
            put("KeyGenerator.AES", Gerador::class.java.name)
        }
    }

    class Loja : KeyStoreSpi() {
        override fun engineGetKey(alias: String, password: CharArray?): Key? = chaves[alias]
        // O padrão do Java só devolve entrada sem senha quando é certificado; o chaveiro do Android devolve a chave secreta.
        override fun engineGetEntry(alias: String, protParam: KeyStore.ProtectionParameter?): KeyStore.Entry? =
            chaves[alias]?.let { KeyStore.SecretKeyEntry(it) }
        override fun engineGetCertificateChain(alias: String?): Array<Certificate>? = null
        override fun engineGetCertificate(alias: String?): Certificate? = null
        override fun engineGetCreationDate(alias: String?): Date? = if (chaves.containsKey(alias)) Date() else null
        override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<out Certificate>?) {
            chaves[alias] = key as SecretKey
        }
        override fun engineSetKeyEntry(alias: String?, key: ByteArray?, chain: Array<out Certificate>?) = throw UnsupportedOperationException()
        override fun engineSetCertificateEntry(alias: String?, cert: Certificate?) = throw UnsupportedOperationException()
        override fun engineDeleteEntry(alias: String) { chaves.remove(alias) }
        override fun engineAliases(): Enumeration<String> = Collections.enumeration(chaves.keys.toList())
        override fun engineContainsAlias(alias: String): Boolean = chaves.containsKey(alias)
        override fun engineSize(): Int = chaves.size
        override fun engineIsKeyEntry(alias: String): Boolean = chaves.containsKey(alias)
        override fun engineIsCertificateEntry(alias: String?): Boolean = false
        override fun engineGetCertificateAlias(cert: Certificate?): String? = null
        override fun engineStore(stream: OutputStream?, password: CharArray?) {}
        override fun engineLoad(stream: InputStream?, password: CharArray?) {}
        override fun engineLoad(param: KeyStore.LoadStoreParameter?) {}
    }

    class Gerador : KeyGeneratorSpi() {
        private var apelido: String? = null
        private var bits = 256
        override fun engineInit(random: SecureRandom?) {}
        override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) {
            // android.security.keystore.KeyGenParameterSpec: lido por reflexão (getKeystoreAlias/getKeySize).
            apelido = params?.javaClass?.getMethod("getKeystoreAlias")?.invoke(params) as? String
            (params?.javaClass?.getMethod("getKeySize")?.invoke(params) as? Int)?.takeIf { it > 0 }?.let { bits = it }
        }
        override fun engineInit(keysize: Int, random: SecureRandom?) { bits = keysize }
        override fun engineGenerateKey(): SecretKey {
            val k = KeyGenerator.getInstance("AES", "SunJCE").apply { init(bits) }.generateKey()
            apelido?.let { chaves[it] = k }
            return k
        }
    }
}
