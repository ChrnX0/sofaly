package br.paulo.tvlauncher.home

import android.content.Context
import br.paulo.tvlauncher.settings.Extras

/**
 * Jeito das telas com a barra única (Paulo, 24/11/2026: "A+B+C deixando o usuário escolher", a Gaveta como padrão).
 * Porquê: o Sofaly tem de ser tão ou mais personalizável que o Projectivy; cada pessoa prefere um jeito.
 * Para quê: a tela aberta a partir da barra continua "dentro" da paisagem da HOME.
 * Como: decidido num ponto só. [Moldura] transforma a tela de lista em gaveta; [Estilo.fundoTela] dá o vidro; a Barra
 * vale nas telas de entrada (onde ela não existe ainda, cai no Vidro). Fica numa pref do Extras ([KEY]).
 */
enum class ScreenStyle {
    /** B: a foto nítida em cima e a tela num painel que sobe da barra, na metade de baixo. Padrão. */
    DRAWER,
    /** A: a foto borrada e escura atrás da tela inteira. */
    GLASS,
    /** C: as telas de entrada viram uma barra de cards igual à da HOME (as outras ficam no Vidro). */
    STRIP;

    companion object {
        const val KEY = "screen_style"
        val DEFAULT = DRAWER
        fun deNumero(n: Int): ScreenStyle = entries.getOrNull(n) ?: DEFAULT
        fun of(ctx: Context): ScreenStyle = deNumero(Extras.numero(ctx, KEY, -1))
        fun set(ctx: Context, s: ScreenStyle) = Extras.definirNumero(ctx, KEY, s.ordinal)
        fun next(ctx: Context, passo: Int): ScreenStyle =
            entries[Math.floorMod(of(ctx).ordinal + passo, entries.size)].also { set(ctx, it) }
    }
}
