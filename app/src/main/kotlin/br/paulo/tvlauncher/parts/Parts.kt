package br.paulo.tvlauncher.parts

import android.content.Context
import android.content.pm.ApplicationInfo
import br.paulo.tvlauncher.player.SubtitleEngines
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Partes baixadas sob demanda (refatoração G, 27/11/2026): o tradutor de legenda ("translate") e o túnel para a internet
 * ("tunnel") podem vir em APKs separados da base (split APKs, ver features/ e scripts/check-parts.py). No APK cheio de
 * sempre, tudo está dentro e [has] responde sim para todas.
 *
 * Sempre lê o PackageManager na hora ([fresh]): depois de instalar uma parte sem reiniciar o processo (setDontKillApp),
 * o `applicationInfo` que o processo recebeu ao abrir continua o de antes (outra pasta de libs, sem a parte).
 */
object Parts {
    const val TRANSLATE = "translate"
    const val TUNNEL = "tunnel"
    val ALL = listOf(TRANSLATE, TUNNEL)

    fun fresh(ctx: Context): ApplicationInfo = ctx.packageManager.getApplicationInfo(ctx.packageName, 0)

    /** Nomes dos splits instalados agora (partes e as de arquitetura, ex.: "config.armeabi_v7a"). Vazio = APK cheio. */
    fun splits(ctx: Context): List<String> = fresh(ctx).splitNames?.toList().orEmpty()

    /** APK cheio (o de sempre): sem split nenhum, todas as partes estão dentro. */
    fun fullApk(ctx: Context): Boolean = splits(ctx).isEmpty()

    fun has(ctx: Context, part: String): Boolean = splits(ctx).let { it.isEmpty() || part in it }

    /** A pasta de libs nativas de agora (muda de lugar a cada instalação, inclusive a de uma parte). */
    fun nativeLibDir(ctx: Context): String = fresh(ctx).nativeLibraryDir

    /**
     * Como o Atualizador lê um APK baixado (getPackageArchiveInfo + chave): pacote, versão e se a chave é a do app
     * instalado. Para provar que a base de um Release em partes (part-base.apk) passa na mesma conferência do APK cheio.
     */
    fun readApk(ctx: Context, path: String): JSONObject {
        val pm = ctx.packageManager
        val info = pm.getPackageArchiveInfo(path, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return JSONObject().put("legivel", false)
        val meu = pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
        fun chaves(p: android.content.pm.PackageInfo) = p.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        return JSONObject().put("legivel", true).put("pacote", info.packageName).put("versao", info.longVersionCode)
            .put("mesmaChave", chaves(info) != null && chaves(info) == chaves(meu))
    }

    /** Diagnóstico (EXTRAS --es o partes): o que o PackageManager diz e o que o processo enxerga. */
    fun probe(ctx: Context): JSONObject {
        val agora = fresh(ctx)
        val processo = ctx.applicationInfo
        val carregador = Parts::class.java.classLoader?.toString().orEmpty()
        return JSONObject()
            .put("apkCheio", agora.splitNames.isNullOrEmpty())
            .put("splits", JSONArray(agora.splitNames?.toList().orEmpty()))
            .put("splitsDoProcesso", JSONArray(processo.splitNames?.toList().orEmpty()))
            .put("libs", agora.nativeLibraryDir)
            .put("libsDoProcesso", processo.nativeLibraryDir)
            .put("tsnet", File(agora.nativeLibraryDir, "libtsnet.so").let { if (it.isFile) it.length() else -1L })
            .put("tsnetDoProcesso", File(processo.nativeLibraryDir, "libtsnet.so").isFile)
            .put("translateJni", File(agora.nativeLibraryDir, "libtranslate_jni.so").isFile)
            .put("carregadorTemTranslate", carregador.contains("split_translate"))
            .put("motorTraducao", runCatching { Class.forName(SubtitleEngines.CLASS); "carrega" }
                .getOrElse { it.javaClass.simpleName })   // sem SubtitleEngines.provider: ele pediria a parte
    }
}
