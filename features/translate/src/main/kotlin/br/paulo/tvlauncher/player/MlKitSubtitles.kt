package br.paulo.tvlauncher.player

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKit
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.TimeUnit

/**
 * O motor de tradução de legenda com o ML Kit (ideia 90): traduz NA TV, sem mandar o texto para fora; o modelo de
 * ~30 MB de cada idioma vem no primeiro uso. Achado por nome em [SubtitleEngines.CLASS] (o R8 mantém pelo proguard-rules).
 * Na build com partes o provider que iniciava o ML Kit ao abrir o processo saiu (ver o manifesto desta parte): [open]
 * inicia na mão. MlKit.initialize não faz nada se já estiver iniciado (a build de sempre ainda tem o provider).
 */
object MlKitSubtitles : SubtitleEngineProvider {
    override fun supports(languageTag: String): Boolean = TranslateLanguage.fromLanguageTag(languageTag) != null

    override fun open(context: Context, from: String, to: String): SubtitleEngine {
        MlKit.initialize(context.applicationContext)
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
