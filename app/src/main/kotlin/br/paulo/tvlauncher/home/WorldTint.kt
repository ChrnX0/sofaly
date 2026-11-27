package br.paulo.tvlauncher.home

import android.content.Context

/**
 * O "mundo" de cada tela (18/11/2026, identidade visual). Nasceu com um brilho de cor por mundo (Ouvir turquesa, Jogar
 * violeta); desde 27/11/2026 todos são grafite neutro (regra: o escuro nunca é azulado) e a cor de cada mundo fica só
 * nos ícones e destaques. O enum continua para o mundo de cada tela ter um lugar só.
 *
 * É só o brilho de cima, à esquerda, do grafite padrão ([desenharFundoEscuro]); o resto da tela (texto, foco branco,
 * acento laranja) não muda. As cores são as mesmas da HOME, mais escuras para não brigar com o texto.
 */
enum class World(val glow: Int) {
    /**
     * Configurações, Casa e o resto: um brilho de grafite neutro. Era um azul-aço (0x4A7388) que deixava o fundo escuro
     * azulado; regra do Paulo (26/11/2026): escuro é grafite neutro, NUNCA azulado.
     */
    NEUTRAL(0x5A5753),
    // Assistir segue o neutro (crítica de design 19/11/2026, decisão 4: o laranja fica só para marca, foco e progresso;
    // depois, 26/11, o azul também saiu pela regra do escuro sem azul). O mundo continua existindo para o futuro.
    WATCH(0x5A5753),
    // Ouvir (era turquesa 0x2A9A8E) e Jogar (era violeta 0x5257C8) também viraram grafite na auditoria de 27/11/2026: nas
    // fotos eram as únicas telas com o escuro azulado. A cor de cada mundo segue nos ícones da cruz.
    LISTEN(0x5A5753),
    PLAY(0x5A5753);

    companion object {
        private val WATCH_SCREENS = setOf(
            "FilmesActivity", "SeriesActivity", "VodActivity", "PlayerActivity", "MidiaRedeActivity", "ServicosFilmesActivity",
            "BloqueioCanaisActivity", "AddonsActivity", "GravacoesActivity", "ElencoActivity",
        )
        private val LISTEN_SCREENS = setOf("RadioActivity", "PodcastsActivity", "PodcastPlayerActivity")
        private val PLAY_SCREENS = setOf("JogosActivity")

        /** O mundo da tela [ctx] pelo nome da classe dela (o que não é tela conhecida fica no neutro). */
        fun of(ctx: Context?): World = ofScreen(ctx?.javaClass?.simpleName.orEmpty())

        fun ofScreen(name: String): World = when (name) {
            in WATCH_SCREENS -> WATCH
            in LISTEN_SCREENS -> LISTEN
            in PLAY_SCREENS -> PLAY
            else -> NEUTRAL
        }
    }
}
