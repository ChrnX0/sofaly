package br.paulo.tvlauncher.settings

import android.content.Context

/**
 * O tema do sistema inteiro (Paulo, 28/11/2026: escolheu 3 das 5 propostas, https://claude.ai/artifact/XkMACepiukLeNbxjoruycF).
 * Porquê: cada tela tinha a sua regra de caixa, foco e ícone, e o sistema não "falava a mesma língua".
 * Para quê: um só lugar decide caixa, foco, cantos e ícones; trocar o tema em Configurações muda TODAS as telas, o painel
 * web e o celular. O padrão de fábrica é [COZY] (Aconchego), escolha do Paulo (28/11/2026 02:58Z).
 * Como: o Estilo lê [atual] a cada caixa e foco que pinta; [FocusStyle] usa [focoPadrao] enquanto ninguém escolheu outro foco.
 * Não confundir com o enum `Tema` do app, que é o tipo de FUNDO (escuro, galeria, céu...).
 */
enum class SystemLook(
    /** Cor da caixa (cartão, item de lista, botão secundário), sobre o fundo com véu. */
    val caixa: Int,
    /** Cantos dos cartões e caixas (dp), antes da escala do Ajuste fino. */
    val raio: Int,
    /** O foco que vem com o tema (quem escolheu outro em Ajuste fino > Foco continua com o seu). */
    val focoPadrao: FocusStyle,
    /** Cada ícone pinta o próprio desenho com a sua cor exclusiva ([IconPalette]). */
    val iconeColorido: Boolean,
    /** O ícone fica dentro de um azulejo cheio na cor dele, com o desenho escuro por cima. */
    val iconeEmAzulejo: Boolean,
) {
    /** Vidro: caixa de vidro fosco (grafite a 34%) com cantos grandes; foco = placa creme com texto escuro. */
    GLASS(0x57282826, 22, FocusStyle.BOX, iconeColorido = false, iconeEmAzulejo = false),
    /** Azulejos: caixa quase opaca, ícone em azulejo de cor cheia; foco = anel claro e o cartão cresce. */
    TILES(0xE6282826.toInt(), 16, FocusStyle.RING, iconeColorido = false, iconeEmAzulejo = true),
    /** Aconchego: caixa grafite quente, ícone com cor própria no desenho; foco = fio laranja vertical. */
    COZY(0xDB2C2925.toInt(), 18, FocusStyle.LINE, iconeColorido = true, iconeEmAzulejo = false);

    companion object {
        const val KEY = "system_look"
        val PADRAO = COZY

        /** Puro sobre o número gravado: fora da faixa (ou nunca escolhido) volta ao [PADRAO]. */
        fun deNumero(n: Int): SystemLook = entries.getOrElse(n) { PADRAO }

        /** Pelo nome em inglês ou em português (comando adb `EXTRAS --es o visual --es nome cozy`). */
        fun deNome(nome: String): SystemLook? = when (nome.trim().lowercase()) {
            "glass", "vidro" -> GLASS
            "tiles", "azulejos" -> TILES
            "cozy", "aconchego" -> COZY
            else -> null
        }

        /** A última escolha lida, para quem pinta sem Context (as cores do Estilo). */
        @Volatile var atual: SystemLook = PADRAO

        fun ler(ctx: Context): SystemLook = deNumero(Extras.numero(ctx, KEY, -1)).also { atual = it }

        /** Grava o tema e devolve o foco ao do tema (o foco escolhido antes era do tema velho). */
        fun gravar(ctx: Context, l: SystemLook) {
            atual = l
            Extras.definirNumero(ctx, KEY, l.ordinal)
            FocusStyle.voltarAoTema(ctx)
        }
    }
}
