package br.paulo.tvlauncher.settings

import android.content.Context

/**
 * Como o foco aparece no app inteiro (régua de estilo, 22/11/2026, https://claude.ai/artifact/QLTSmmQE2ipkr9KMY4ZuhX).
 * Porquê: o quadro branco cheio era a coisa mais "bruta" da tela, e cada pessoa vê de um jeito (de longe, sobre fundo claro).
 * Para quê: o Paulo escolheu o Fio como padrão, mas quem precisa enxergar mais pode trocar em Ajuste fino > Foco.
 * Como: o Estilo lê daqui na hora de cada mudança de foco (listas, botões, pílulas, cartões) e a cruz pinta o traço do
 * escolhido com a mesma regra; trocar não precisa reabrir nada.
 */
enum class FocusStyle {
    /** Traço laranja curto, vertical, de pontas redondas, à esquerda do item; sem caixa. Vertical para não parecer progresso. */
    LINE,
    /** Véu de vidro claro (branco a 10%) com fio branco fino. */
    VEIL,
    /** O quadro branco cheio de antes (texto escurece), para quem enxerga menos. */
    BOX;

    companion object {
        const val KEY = "focus_style"

        /** A escolha gravada; padrão [LINE]. Puro sobre o número (testável): fora da faixa volta ao padrão. */
        fun deNumero(n: Int): FocusStyle = entries.getOrElse(n) { LINE }

        fun ler(ctx: Context): FocusStyle = deNumero(Extras.numero(ctx, KEY, LINE.ordinal))

        fun gravar(ctx: Context, f: FocusStyle) = Extras.definirNumero(ctx, KEY, f.ordinal)
    }
}
