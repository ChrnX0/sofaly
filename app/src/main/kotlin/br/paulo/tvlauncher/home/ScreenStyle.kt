package br.paulo.tvlauncher.home

import android.content.Context
import br.paulo.tvlauncher.settings.Extras

/**
 * Jeito das telas com a barra única (Paulo, 24/11/2026: "A+B+C deixando o usuário escolher", a Gaveta como padrão).
 * Porquê: o Sofaly tem de ser tão ou mais personalizável que o Projectivy; cada pessoa prefere um jeito.
 * Para quê: a tela aberta a partir da barra continua "dentro" da paisagem da HOME.
 * Como: decidido num ponto só. [Moldura] transforma a tela de lista em gaveta; [Estilo.fundoTela] dá o vidro; a Barra
 * vale nas telas de entrada (Configurações, Rádio, Podcasts, Jogos: [Estilo.fundoEntrada] e CruzOuvir); nas outras, o Vidro. Fica numa pref do Extras ([KEY]).
 */
enum class ScreenStyle {
    /** B: a foto nítida em cima e a tela num painel que sobe da barra, na metade de baixo. */
    DRAWER,
    /** A: a foto borrada e escura atrás da tela inteira. */
    GLASS,
    /** C: as telas de entrada viram uma fileira de cards igual à da HOME (as outras ficam no Vidro). */
    STRIP,
    /**
     * A+B no estilo da E (Paulo, 25/11/2026, padrão): as Configurações abrem num painel de vidro à direita, por cima da
     * HOME de verdade, que continua à vista e muda na hora enquanto a pessoa mexe na aparência. Por enquanto só as
     * Configurações usam o painel; as outras telas de lista ficam na Gaveta até os menus entrarem no mesmo vidro.
     * Fica no fim da lista: o número gravado das outras escolhas não muda.
     */
    PANEL;

    companion object {
        const val KEY = "screen_style"
        val DEFAULT = PANEL
        fun deNumero(n: Int): ScreenStyle = entries.getOrNull(n) ?: DEFAULT
        fun of(ctx: Context): ScreenStyle = deNumero(Extras.numero(ctx, KEY, -1))
        fun set(ctx: Context, s: ScreenStyle) = Extras.definirNumero(ctx, KEY, s.ordinal)
        /** O jeito Barra vale agora? Só com a HOME na barra única e fora do alto contraste (que pede fundo chapado). */
        fun barra(ctx: Context): Boolean =
            BottomBar.strip(ctx) && of(ctx) == STRIP && !br.paulo.tvlauncher.Estilo.altoContraste
        /** As Configurações abrem no painel ao lado? Fora do alto contraste (que pede a tela chapada, sem nada por trás). */
        fun panel(ctx: Context): Boolean = of(ctx) == PANEL && !br.paulo.tvlauncher.Estilo.altoContraste
        /** As telas de lista descem na gaveta? A Gaveta, e o Painel enquanto só as Configurações usam o painel. */
        fun drawer(ctx: Context): Boolean = of(ctx).let { it == DRAWER || it == PANEL }
        fun next(ctx: Context, passo: Int): ScreenStyle =
            entries[Math.floorMod(of(ctx).ordinal + passo, entries.size)].also { set(ctx, it) }
    }
}
