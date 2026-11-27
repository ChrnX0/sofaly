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
    BOX,
    /**
     * Anel laranja em volta do item, o mesmo da barra da HOME (coerência das telas, proposta A, 24/11/2026).
     * Porquê: na barra o escolhido tem anel; nas outras telas tinha traço, véu ou quadro, e parecia outro sistema.
     * Fica por último na lista para não mudar o número gravado de quem já escolheu Fio, Véu ou Quadro.
     */
    RING;

    companion object {
        const val KEY = "focus_style"

        /** A escolha gravada; padrão [RING] (o anel da barra). Puro sobre o número (testável): fora da faixa volta ao padrão. */
        fun deNumero(n: Int): FocusStyle = entries.getOrElse(n) { RING }

        /**
         * A última escolha lida, para as cores "no foco" do Estilo (TEXTO_NO_FOCO...), que não recebem Context.
         * Porquê: na Sala (22/11) o painel rápido pintava o título focado de escuro sobre o Fio (fundo escuro) e ele sumia.
         */
        @Volatile var atual: FocusStyle = RING

        fun ler(ctx: Context): FocusStyle = deNumero(Extras.numero(ctx, KEY, -1)).also { atual = it }

        fun gravar(ctx: Context, f: FocusStyle) { atual = f; Extras.definirNumero(ctx, KEY, f.ordinal) }
    }
}
