package br.paulo.tvlauncher

import android.media.session.PlaybackState

// Saiu do TrailerTv.kt (refatoração G): é só regra de texto, e o módulo :media também usa.
/**
 * Trailer no YouTube (18/11/2026, Paulo: "trailers que abrem errado no YouTube ou não abrem, e se abrem, ao sair fica
 * aberto o aplicativo do YouTube"). Parte pura (sem Android), testada em TrailerTvTest.
 *
 * Visto na Sala (v462950): VOLTAR no trailer leva para a tela inicial do YouTube (recomendados), não para a ficha; o
 * YouTube fica aberto por trás. `am force-stop` do YouTube devolve a ficha na hora (a ficha está na tarefa de baixo).
 */
object TrailerLink {
    const val YOUTUBE_TV = "com.google.android.youtube.tv"

    private val reId = Regex("[A-Za-z0-9_-]{11}")
    private val reLink = Regex(
        """^(?:https?://)?(?:(?:www|m|music)\.)?(?:youtube(?:-nocookie)?\.com/(?:watch\?(?:[^#]*&)?v=|embed/|v/|shorts/|live/|e/)|youtu\.be/)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])""",
        RegexOption.IGNORE_CASE)

    /**
     * O código do vídeo (11 caracteres) em qualquer forma que os servidores mandam: só o código, watch?v=, youtu.be/,
     * embed/, shorts/, com ou sem https e www. Antes só watch?v= e youtu.be passavam: "embed/..." (comum no Xtream)
     * sumia com o botão Trailer. Qualquer outra coisa = null (não abrimos endereço desconhecido).
     */
    fun codigo(valor: String?): String? {
        val v = valor?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (v.matches(reId)) return v
        return reLink.find(v)?.groupValues?.get(1)
    }

    fun link(codigo: String) = "https://www.youtube.com/watch?v=$codigo"

    /**
     * Uma janela nova quer dizer que a pessoa saiu do YouTube? Só uma TELA de verdade (Activity). Janelas por cima que
     * não tiram o YouTube da frente também geram o evento: os nossos avisos (serviço de acessibilidade: classe
     * "android.widget.FrameLayout"), diálogos e toasts ("android.app.Dialog", "android.widget.Toast"...). Visto na Sala
     * (18/11/2026): o aviso "o Claude olhou a tela" fechava o trailer 2 s depois de abrir.
     */
    fun saiuDoYoutube(nosso: String, pacote: String, classe: String): Boolean {
        if (classe.startsWith("android.widget.") || classe.startsWith("android.view.") || classe.startsWith("android.app.Dialog") ||
            classe.startsWith("android.app.AlertDialog") || classe.startsWith("android.inputmethodservice.")) return false
        // nosso pacote: só uma Activity nossa (a ficha voltando, a HOME); o resto são janelas nossas por cima
        if (pacote == nosso) return classe.startsWith("$nosso.") && classe.endsWith("Activity")
        return true
    }

    /** O link normalizado, ou "" quando não é um vídeo do YouTube. */
    fun normalizar(valor: String?): String = codigo(valor)?.let { link(it) } ?: ""

    /**
     * Qual trailer abrir: o do TMDB primeiro (escolhido pelo id do filme, no idioma da TV, oficial), depois o do servidor
     * de IPTV (muitas vezes de outro filme com nome parecido, ou um vídeo apagado: "abre errado"/"não abre").
     */
    fun escolher(tmdb: String?, servidor: String?): String? =
        codigo(tmdb)?.let { link(it) } ?: codigo(servidor)?.let { link(it) }

    /**
     * O trailer acabou? (o YouTube emenda o próximo vídeo sozinho). Só depois de ter tocado: parou de vez, deu erro, ou o
     * título mudou (outro vídeo começou).
     */
    fun acabou(tocou: Boolean, tituloInicial: String?, estado: Int, tituloAgora: String?): Boolean {
        if (!tocou) return false
        if (estado == PlaybackState.STATE_STOPPED || estado == PlaybackState.STATE_ERROR || estado == PlaybackState.STATE_NONE) return true
        return !tituloInicial.isNullOrBlank() && !tituloAgora.isNullOrBlank() && tituloAgora != tituloInicial
    }
}
