package br.paulo.tvlauncher

/**
 * Matriz da tela (Paulo, 24/11/2026: "padronizar a matriz da tela"; escolheu 12 x 8 vendo a grade desenhada em
 * home-refino/grade/grade-12x8-*.png).
 *
 * Porquê: cada tela posicionava as peças com números próprios, então nada se alinhava entre telas.
 * Para quê: toda peça começa e termina numa coluna e numa linha daqui.
 * Como: tela de 960 x 540 dp; margem segura 48 dp nos lados e 30 dp em cima e embaixo; área útil 864 x 480;
 * 12 colunas de 50 dp com vão de 24 dp; 8 linhas de 60 dp (A a H) sem vão; passo fino de 4 dp. Tudo em dp, puro.
 */
object ScreenGrid {
    const val W = 960f
    const val H = 540f
    const val MARGIN_X = 48f
    const val MARGIN_Y = 30f
    const val COLS = 12
    const val COL_W = 50f
    const val GUTTER = 24f
    const val ROWS = 8
    const val ROW_H = 60f

    /** Borda esquerda da coluna [c] (1 a 12). */
    fun colX(c: Int): Float = MARGIN_X + (c - 1) * (COL_W + GUTTER)

    /** Largura de [n] colunas seguidas, com os vãos entre elas (2 colunas = 124 dp). */
    fun span(n: Int): Float = n * COL_W + (n - 1) * GUTTER

    /** Topo da linha [r] (1 = A ... 8 = H). */
    fun rowY(r: Int): Float = MARGIN_Y + (r - 1) * ROW_H

    /** Altura de [n] linhas seguidas. */
    fun rows(n: Int): Float = n * ROW_H
}
