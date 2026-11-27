package br.paulo.tvlauncher.player

import android.content.Context

/**
 * O motor que traduz cada fala da legenda (refatoração G, 27/11/2026). Hoje é o ML Kit, na TV ([SubtitleEngines]);
 * a lógica de ordem, texto e disco fica em LegendaTraduzida/[TraducaoLegenda], que não sabem qual motor é.
 * Por quê: o ML Kit pesa 5,7 MB no APK e poucos usam; separado assim, ele pode virar uma parte baixada só por quem liga
 * a tradução, sem mexer no resto.
 */
interface SubtitleEngine : AutoCloseable {
    /** Deixa o motor pronto (o ML Kit baixa o modelo do idioma na primeira vez). Bloqueia; erro = exceção. */
    fun prepare(timeoutSeconds: Long)
    /** Uma fala traduzida. Bloqueia; erro = exceção. */
    fun translate(text: String, timeoutSeconds: Long): String
    override fun close()
}

/** Quem fabrica o motor: diz se traduz para o idioma e abre um motor de [from] para [to] (etiquetas BCP 47). */
interface SubtitleEngineProvider {
    fun supports(languageTag: String): Boolean
    fun open(context: Context, from: String, to: String): SubtitleEngine
}

/**
 * Onde LegendaTraduzida acha o motor. [CLASS] é procurada por nome: mora no APK ou numa parte baixada depois; sem ela,
 * [provider] é null e a tradução some da tela (como num idioma que não tem tradutor).
 */
object SubtitleEngines {
    const val CLASS = "br.paulo.tvlauncher.player.MlKitSubtitles"

    @Volatile private var cached: SubtitleEngineProvider? = null

    val provider: SubtitleEngineProvider?
        get() = cached ?: load()?.also { cached = it }

    private fun load(): SubtitleEngineProvider? = try {
        Class.forName(CLASS).getField("INSTANCE").get(null) as? SubtitleEngineProvider
    } catch (_: ReflectiveOperationException) { null } catch (_: LinkageError) { null }
}
