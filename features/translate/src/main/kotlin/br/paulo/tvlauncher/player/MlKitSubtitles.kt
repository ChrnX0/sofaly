package br.paulo.tvlauncher.player

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.TimeUnit

/**
 * O motor de tradução de legenda com o ML Kit (ideia 90): traduz NA TV, sem mandar o texto para fora; o modelo de
 * ~30 MB de cada idioma vem no primeiro uso. Achado por nome em [SubtitleEngines.CLASS] (o R8 mantém pelo proguard-rules).
 */
object MlKitSubtitles : SubtitleEngineProvider {
    override fun supports(languageTag: String): Boolean = TranslateLanguage.fromLanguageTag(languageTag) != null

    override fun open(context: Context, from: String, to: String): SubtitleEngine {
        val origem = TranslateLanguage.fromLanguageTag(from) ?: error("idioma sem tradutor: $from")
        val alvo = TranslateLanguage.fromLanguageTag(to) ?: error("idioma sem tradutor: $to")
        val tradutor = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(origem).setTargetLanguage(alvo).build())
        return object : SubtitleEngine {
            override fun prepare(timeoutSeconds: Long) {
                Tasks.await(tradutor.downloadModelIfNeeded(DownloadConditions.Builder().build()), timeoutSeconds, TimeUnit.SECONDS)
            }
            override fun translate(text: String, timeoutSeconds: Long): String =
                Tasks.await(tradutor.translate(text), timeoutSeconds, TimeUnit.SECONDS)
            override fun close() = tradutor.close()
        }
    }
}
