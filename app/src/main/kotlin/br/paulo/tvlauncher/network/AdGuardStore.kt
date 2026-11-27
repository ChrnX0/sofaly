package br.paulo.tvlauncher.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * O que a ligação com a conta AdGuard DNS do dono guarda na TV (27/11/2026; lógica em [AdGuardDns], no :core).
 * Mesma receita da chave do UniFi ([UnifiArmazem]):
 * - Preferências PRÓPRIAS, "adguard_seguro": o backup comum do painel não leva; o backup completo cifrado leva a chave
 *   (BackupCompleto, junto com a do UniFi).
 * - A chave fica CIFRADA (AES-GCM) com uma chave do Android Keystore, que nunca sai do aparelho.
 * - O painel nunca recebe a chave de volta: só os 4 últimos caracteres.
 */
object AdGuardStore {
    private const val TAG = "TvLauncher.AdGuard"
    private const val ARQUIVO = "adguard_seguro"
    private const val APELIDO = "adguard_chave_api"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- a ligação com a conta

    fun servidorId(ctx: Context): String? = prefs(ctx).getString("servidor", null)
    fun aparelhoId(ctx: Context): String? = prefs(ctx).getString("aparelho", null)
    fun hostDot(ctx: Context): String? = prefs(ctx).getString("host", null)
    fun ligada(ctx: Context): Boolean = temChave(ctx) && servidorId(ctx) != null && hostDot(ctx) != null

    fun guardarLigacao(ctx: Context, l: AdGuardDns.Ligacao) {
        prefs(ctx).edit().putString("servidor", l.servidorId).putString("aparelho", l.aparelhoId).putString("host", l.hostDot).commit()
    }

    /** DNS que a TV usava antes de ligar (para voltar ao desligar). "modo|host". */
    fun dnsAnterior(ctx: Context): Dns.Estado? {
        val t = prefs(ctx).getString("dns_antes", null) ?: return null
        val modo = runCatching { Dns.Modo.valueOf(t.substringBefore('|')) }.getOrNull() ?: return null
        return Dns.Estado(modo, t.substringAfter('|', "").ifEmpty { null })
    }
    fun guardarDnsAnterior(ctx: Context, e: Dns.Estado) {
        if (!prefs(ctx).contains("dns_antes")) prefs(ctx).edit().putString("dns_antes", "${e.modo}|${e.host.orEmpty()}").apply()
    }

    // ---------------------------------------------------------------- o que a Família manda para o DNS

    fun familiaLigada(ctx: Context) = prefs(ctx).getBoolean("familia", false)
    fun dormirNoDns(ctx: Context) = prefs(ctx).getBoolean("dormir", true)
    fun youtubeSeguro(ctx: Context) = prefs(ctx).getBoolean("youtube_seguro", true)
    fun buscaSegura(ctx: Context) = prefs(ctx).getBoolean("busca_segura", true)
    fun bloquearAdulto(ctx: Context) = prefs(ctx).getBoolean("adulto", true)
    fun servicos(ctx: Context): Set<String> = prefs(ctx).getStringSet("servicos", emptySet())!!.toSet()

    fun guardarFamilia(ctx: Context, ligada: Boolean, dormir: Boolean, youtube: Boolean, busca: Boolean, adulto: Boolean, servicos: Set<String>) {
        prefs(ctx).edit().putBoolean("familia", ligada).putBoolean("dormir", dormir).putBoolean("youtube_seguro", youtube)
            .putBoolean("busca_segura", busca).putBoolean("adulto", adulto).putStringSet("servicos", servicos).commit()
    }

    /** Última vez que a Família foi gravada no AdGuard (ms) e o que deu ("ok" ou o nome do erro). */
    fun ultimaSincronia(ctx: Context): Pair<Long, String>? =
        prefs(ctx).getString("sincronia", null)?.let { (it.substringBefore('|').toLongOrNull() ?: 0L) to it.substringAfter('|') }
    fun guardarSincronia(ctx: Context, resultado: String) {
        prefs(ctx).edit().putString("sincronia", "${System.currentTimeMillis()}|$resultado").apply()
    }
    /** Assinatura das regras gravadas por último: só grava de novo quando algo mudou. */
    fun assinatura(ctx: Context): String? = prefs(ctx).getString("assinatura", null)
    fun guardarAssinatura(ctx: Context, a: String?) { prefs(ctx).edit().putString("assinatura", a).apply() }

    // ---------------------------------------------------------------- a chave

    fun temChave(ctx: Context): Boolean = prefs(ctx).contains("chave_cifrada")
    fun finalDaChave(ctx: Context): String? = prefs(ctx).getString("chave_final", null)?.let { "…$it" }

    /** Grava a chave (já validada por [AdGuardDns.chaveValida]), cifrada. Trocar a chave esquece a ligação antiga. */
    fun definirChave(ctx: Context, chave: String): Boolean = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, chaveDoKeystore())
        val cifrada = c.doFinal(chave.toByteArray(Charsets.US_ASCII))
        val e = prefs(ctx).edit()
        if (chave.takeLast(4) != prefs(ctx).getString("chave_final", null)) e.remove("servidor").remove("aparelho").remove("host").remove("assinatura")
        e.putString("chave_cifrada", Base64.encodeToString(cifrada, Base64.NO_WRAP))
            .putString("chave_iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
            .putString("chave_final", chave.takeLast(4))
            .commit()
    } catch (e: Exception) {
        Log.w(TAG, "não consegui cifrar a chave: ${e.javaClass.simpleName}")
        false
    }

    /** A chave em claro, só para montar o pedido. Null sem chave ou se o Keystore perdeu a dele. */
    fun chave(ctx: Context): String? {
        val p = prefs(ctx)
        val cifrada = p.getString("chave_cifrada", null) ?: return null
        val iv = p.getString("chave_iv", null) ?: return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, chaveDoKeystore(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(cifrada, Base64.NO_WRAP)), Charsets.US_ASCII)
        } catch (e: Exception) {
            Log.w(TAG, "não consegui decifrar a chave: ${e.javaClass.simpleName}")   // nunca a chave no log
            null
        }
    }

    /** Apaga tudo da TV (a conta AdGuard não é tocada: o servidor "Sofaly · …" fica lá para o dono apagar no site). */
    fun esquecer(ctx: Context) { prefs(ctx).edit().clear().commit() }

    private fun chaveDoKeystore(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(APELIDO, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gerador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gerador.init(KeyGenParameterSpec.Builder(APELIDO, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return gerador.generateKey()
    }
}
