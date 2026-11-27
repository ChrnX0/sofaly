package br.paulo.onetv.celular

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A chave de aparelho de cada TV (Brainstorm 26/11/2026, item 9; na TV: AparelhosPareados). Com ela o app entra no
 * painel sem o PIN quando a sessão cai (30 min parado, 12 h, launcher reiniciado). Guardada cifrada com uma chave do
 * Android Keystore que nunca sai do aparelho: um backup ou uma cópia das prefs não leva o acesso junto
 * (o app também tem allowBackup=false). Uma chave por TV, pelo [Pareamento.chave].
 */
object ChaveAparelho {
    private const val ALIAS = "sofaly-aparelho"
    private const val PREFS = "aparelho"

    // ------------------------------------------------------------ puro (ChaveAparelhoTest)

    /** iv (12 bytes) + texto cifrado, numa linha só. */
    fun juntar(iv: ByteArray, cifrado: ByteArray): ByteArray = iv + cifrado

    /** O inverso de [juntar]; null se o guardado não tem nem o iv e a etiqueta do GCM. */
    fun separar(tudo: ByteArray): Pair<ByteArray, ByteArray>? =
        if (tudo.size < 12 + 16) null else tudo.copyOfRange(0, 12) to tudo.copyOfRange(12, tudo.size)

    /** Só uma chave no formato da TV (64 hex) vale guardar ou mandar. */
    fun valida(c: String?): Boolean = c != null && c.length == 64 && c.all { it in '0'..'9' || it in 'a'..'f' }

    // ------------------------------------------------------------ Keystore

    private fun segredo(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    /**
     * A TV ganhou id (ou nome Tailscale) depois de a chave ser guardada pelo "ip:casa|ip" antigo: a chave estava lá e o
     * app pedia o PIN de novo (revisor 26/11). Acha pela chave antiga e passa para a nova.
     */
    private fun guardado(ctx: Context, tv: Pareamento.Tv): String? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val k = Pareamento.chave(tv)
        p.getString(k, null)?.let { return it }
        val antiga = "ip:${tv.casa}|${tv.ip}"
        if (antiga == k) return null
        val v = p.getString(antiga, null) ?: return null
        p.edit().putString(k, v).remove(antiga).apply()
        return v
    }

    fun ler(ctx: Context, tv: Pareamento.Tv): String? = runCatching {
        val b64 = guardado(ctx, tv) ?: return null
        val (iv, ct) = separar(Base64.decode(b64, Base64.NO_WRAP)) ?: return null
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, segredo(), GCMParameterSpec(128, iv)) }
        String(c.doFinal(ct)).takeIf { valida(it) }
    }.getOrNull()

    /** Guarda e lê de volta. false = o Keystore deste celular falhou (o app para de pedir chave nesta sessão). */
    fun guardar(ctx: Context, tv: Pareamento.Tv, chave: String): Boolean {
        if (!valida(chave)) return false
        runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, segredo()) }
            val ct = c.doFinal(chave.toByteArray())
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(Pareamento.chave(tv), Base64.encodeToString(juntar(c.iv, ct), Base64.NO_WRAP)).commit()
        }
        return ler(ctx, tv) == chave
    }

    fun esquecer(ctx: Context, tv: Pareamento.Tv) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(Pareamento.chave(tv)).remove("ip:${tv.casa}|${tv.ip}").apply()
}
