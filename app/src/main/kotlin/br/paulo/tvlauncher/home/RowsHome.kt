package br.paulo.tvlauncher.home

import android.content.Context
import br.paulo.tvlauncher.settings.Extras

/**
 * "Fileiras": a tela inicial em fileiras de categorias, clone do Projectivy (Paulo, 26/11/2026: "quero um clone deles no
 * nosso; depois que estiver exatamente igual em tudo eu entro com minhas modificações").
 *
 * Porquê: o Paulo quer partir do mesmo desenho e das mesmas opções do Projectivy e mudar um a um depois.
 * Para quê: uma fileira por categoria, uma embaixo da outra. O card em foco fica PARADO à esquerda (a fileira corre por
 * baixo dele), maior e com brilho; o nome dele aparece grande embaixo da fileira. A fileira de cima fica apagada e a de
 * baixo mostra o título e o começo dos cards, apagados. No alto à direita, Wi-Fi e engrenagem numa pílula e a hora.
 * Como: a [HomeCruz] guarda o mesmo estado da cruz (categoria = fileira, item = card) e usa as teclas da barra única
 * ([StripHome.passo]: ◀ ▶ nos cards, ▲ ▼ nas fileiras); o desenho é do [RowsLayer]. Tudo que é conta fica aqui, puro.
 *
 * Medidas tiradas das fotos do Projectivy 4.74 na Sala (1920x1080 = 960x540 dp, estudo de 26/11/2026): card 128x72,
 * vão 8, cantos 8, foco 1,3x preso a x=40 com o centro a 58% da altura; título da fileira 72 dp acima do centro dos
 * cards; nome do foco 71 dp abaixo; fileira de baixo a 176 dp, a de cima a 151 dp. NÃO é cópia de código (o do
 * Projectivy é fechado): só o desenho e o comportamento medidos na tela. Letra: nunca menor que 18 sp (a dele é 14-16).
 */
object RowsHome {

    // ---- opções (Extras, números). Os nomes seguem os ajustes do Projectivy; o padrão é o dele.
    const val KEY_SIZE = "rows_card_size"           // tamanho dos cards: Size
    const val KEY_GAP = "rows_card_gap"             // espaço entre os cards: Gap
    const val KEY_CORNERS = "rows_card_corners"     // cantos: Corners
    const val KEY_FOCUS_SIZE = "rows_focus_size"    // tamanho do card em foco: FocusSize
    const val KEY_EFFECT = "rows_focus_effect"      // efeito do card em foco: Effect
    const val KEY_GLOW_ANIM = "rows_glow_anim"      // animação do brilho: GlowAnim
    const val KEY_CARD_TITLE = "rows_card_title"    // nome do card em foco embaixo da fileira (1 = sim)
    const val KEY_ROW_TITLE = "rows_row_title"      // título das categorias (1 = sim)
    const val KEY_ROW_ICON = "rows_row_icon"        // ícone antes do título (1 = sim)
    const val KEY_SPACING = "rows_spacing"          // espaço entre as fileiras: Spacing
    const val KEY_FIXED = "rows_fixed"              // foco da categoria parado na mesma altura (1 = sim)
    const val KEY_HIDE_ABOVE = "rows_hide_above"    // esconder as fileiras acima da escolhida (1 = sim)
    const val KEY_DIM = "rows_dim"                  // opacidade das fileiras fora do foco: Dim
    const val KEY_SHADOW = "rows_shadow"            // sombra sobre o fundo (esquerda e embaixo): Shadow
    const val KEY_CLOCK = "rows_clock"              // hora no alto (1 = sim)
    const val KEY_STATUS = "rows_status"            // pílula com Wi-Fi e engrenagem (1 = sim)

    enum class Size(val k: Float) { XS(0.75f), S(0.875f), M(1f), L(1.15f), XL(1.3f) }
    enum class Gap(val dp: Float) { NONE(0f), TIGHT(4f), NORMAL(8f), WIDE(14f), WIDER(20f) }
    enum class Corners(val dp: Float) { SQUARE(0f), SLIGHT(4f), SOFT(8f), ROUND(14f), ROUNDER(22f) }
    enum class FocusSize(val k: Float) { SAME(1f), SMALL(1.15f), MEDIUM(1.3f), LARGE(1.45f) }
    /** Efeito do card em foco (Projectivy: Brilho / Sombra projetada). */
    enum class Effect { NONE, GLOW, SHADOW }
    /** Animação do brilho (Projectivy: Respirar / Esmaecer / Pulsar / Reluzir). */
    enum class GlowAnim { NONE, BREATH, FADE, PULSE, SHINE }
    /** Espaço entre as fileiras: Auto = a medida dele; os outros em % da altura da tela (o ajuste dele é assim). */
    enum class Spacing(val pct: Float) { AUTO(0f), P24(0.24f), P28(0.28f), P36(0.36f), P42(0.42f) }
    /** Opacidade das fileiras fora do foco. */
    enum class Dim(val alpha: Float) { HIDDEN(0f), FAINT(0.2f), DEFAULT(0.4f), HALF(0.6f), FULL(1f) }
    /** Sombra sobre o fundo (Projectivy: comprimento e opacidade da sombra horizontal e vertical). */
    enum class Shadow(val alpha: Float) { NONE(0f), LIGHT(0.45f), DEFAULT(0.7f), STRONG(0.9f) }

    data class Options(
        val size: Size = Size.M, val gap: Gap = Gap.NORMAL, val corners: Corners = Corners.SOFT,
        val focusSize: FocusSize = FocusSize.MEDIUM, val effect: Effect = Effect.GLOW, val glowAnim: GlowAnim = GlowAnim.NONE,
        val cardTitle: Boolean = true, val rowTitle: Boolean = true, val rowIcon: Boolean = true,
        val spacing: Spacing = Spacing.AUTO, val fixed: Boolean = true, val hideAbove: Boolean = false,
        val dim: Dim = Dim.DEFAULT, val shadow: Shadow = Shadow.DEFAULT, val clock: Boolean = true, val status: Boolean = true,
    ) {
        val cardW get() = Math.round(CARD_W * size.k).toFloat()
        val cardH get() = Math.round(CARD_H * size.k).toFloat()
        /** Muda o desenho da HOME: entra na assinatura que a refaz. */
        fun signature() = listOf(size, gap, corners, focusSize, effect, glowAnim, spacing, dim, shadow).joinToString("") { it.ordinal.toString() } +
            listOf(cardTitle, rowTitle, rowIcon, fixed, hideAbove, clock, status).joinToString("") { if (it) "1" else "0" }
    }

    val DEFAULT = Options()

    fun options(ctx: Context): Options {
        fun <E : Enum<E>> ler(key: String, todos: List<E>, padrao: E): E = todos.getOrNull(Extras.numero(ctx, key, padrao.ordinal)) ?: padrao
        fun sim(key: String, padrao: Boolean) = Extras.numero(ctx, key, if (padrao) 1 else 0) == 1
        val d = DEFAULT
        return Options(
            size = ler(KEY_SIZE, Size.entries, d.size), gap = ler(KEY_GAP, Gap.entries, d.gap),
            corners = ler(KEY_CORNERS, Corners.entries, d.corners), focusSize = ler(KEY_FOCUS_SIZE, FocusSize.entries, d.focusSize),
            effect = ler(KEY_EFFECT, Effect.entries, d.effect), glowAnim = ler(KEY_GLOW_ANIM, GlowAnim.entries, d.glowAnim),
            cardTitle = sim(KEY_CARD_TITLE, d.cardTitle), rowTitle = sim(KEY_ROW_TITLE, d.rowTitle), rowIcon = sim(KEY_ROW_ICON, d.rowIcon),
            spacing = ler(KEY_SPACING, Spacing.entries, d.spacing), fixed = sim(KEY_FIXED, d.fixed), hideAbove = sim(KEY_HIDE_ABOVE, d.hideAbove),
            dim = ler(KEY_DIM, Dim.entries, d.dim), shadow = ler(KEY_SHADOW, Shadow.entries, d.shadow),
            clock = sim(KEY_CLOCK, d.clock), status = sim(KEY_STATUS, d.status),
        )
    }

    /** Gira uma opção ([passo] +1/-1) e devolve o índice novo (sim/não = 2 valores). */
    fun next(ctx: Context, key: String, atual: Int, total: Int, passo: Int): Int =
        Math.floorMod(atual + passo, total).also { Extras.definirNumero(ctx, key, it) }

    // ---- medidas (dp numa tela de 960 x 540)
    const val CARD_W = 128f
    const val CARD_H = 72f
    const val FOCO_X = 40f           // borda esquerda do card em foco
    const val TEXTO_X = 56f          // títulos e nome do foco
    const val FOCO_Y = 0.5815f       // centro dos cards da fileira em foco (fração da altura)
    const val TITULO_ACIMA = 72f     // do centro dos cards ao centro do título da fileira
    const val NOME_ABAIXO = 71f      // do centro dos cards em foco ao centro do nome
    const val PASSO_BAIXO = 176f     // centro dos cards da fileira de baixo
    const val PASSO_CIMA = 151f      // centro dos cards da fileira de cima
    const val TITULO_SP = 18f        // título da fileira (dele: 16)
    const val NOME_SP = 22f          // nome do card em foco (dele: 18)
    const val ICONE_TITULO = 20f     // ícone antes do título
    const val HORA_SP = 32f
    const val TOPO = 47f             // centro da hora e da pílula
    const val DIREITA = 64f          // margem direita da hora
    const val BRILHO = 2.5f          // contorno do brilho do foco
    const val MS = 250L              // troca de fileira e passo (medido nele)
    const val MS_TITULO = 150L       // o título acende

    // ---- conta

    /**
     * Centro vertical (dp) dos cards da fileira [linha] com a [foco] escolhida, numa tela de [altura] dp. Com o foco
     * parado, a fileira em foco fica sempre na mesma altura; sem ele, as fileiras não andam até a escolhida sair da tela.
     */
    fun centroDaFileira(linha: Int, foco: Int, altura: Float, o: Options): Float {
        val base = altura * FOCO_Y
        val baixo = if (o.spacing == Spacing.AUTO) PASSO_BAIXO else altura * o.spacing.pct
        val cima = if (o.spacing == Spacing.AUTO) PASSO_CIMA else baixo * (PASSO_CIMA / PASSO_BAIXO)
        // Solto: a 1ª fileira começa logo abaixo da hora e a escolhida desce até a altura do foco parado; dali, as
        // fileiras sobem (como uma lista comum). Parado: a escolhida está sempre na mesma altura.
        val focoY = if (o.fixed) base else minOf(base, INICIO_SOLTO + foco * cima)
        val d = linha - foco
        return if (d >= 0) focoY + d * baixo else focoY + d * cima
    }

    /** Centro dos cards da 1ª fileira sem o foco parado (logo abaixo da hora, com o título dela visível). */
    const val INICIO_SOLTO = 170f

    /** Opacidade da fileira [linha] (a escolhida é 1; acima some se a opção mandar). */
    fun alfaDaFileira(linha: Int, foco: Int, o: Options): Float = when {
        linha == foco -> 1f
        linha < foco && o.hideAbove -> 0f
        else -> o.dim.alpha
    }

    /**
     * x (dp) de cada card da fileira com o card [sel] em foco preso a [FOCO_X]: os de antes saem pela esquerda, os
     * de depois vêm logo atrás do foco aumentado. [focada] = a fileira é a escolhida (só ela cresce).
     */
    fun xs(n: Int, sel: Int, o: Options, focada: Boolean): FloatArray {
        val w = o.cardW; val vao = o.gap.dp
        val grande = if (focada) w * o.focusSize.k else w
        val r = FloatArray(n)
        val s = sel.coerceIn(0, (n - 1).coerceAtLeast(0))
        if (n == 0) return r
        r[s] = FOCO_X
        var x = FOCO_X + grande + vao
        for (i in s + 1 until n) { r[i] = x; x += w + vao }
        x = FOCO_X
        for (i in s - 1 downTo 0) { x -= w + vao; r[i] = x }
        return r
    }

    /** O card [i] aparece numa tela de [largura] dp (com folga de um card de cada lado, para entrar deslizando). */
    fun visivel(x: Float, o: Options, largura: Float): Boolean = x + o.cardW * o.focusSize.k > -o.cardW && x < largura + o.cardW
}
