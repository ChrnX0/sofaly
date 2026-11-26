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
    // Cartões (onda 2): o resto de Aparência > Cartões do Projectivy
    const val KEY_ZOOM = "rows_zoom"                // o card em foco cresce (1 = sim)
    const val KEY_GLOW_SIZE = "rows_glow_size"      // espessura do brilho: GlowSize
    const val KEY_GLOW_COLOR = "rows_glow_color"    // cor do brilho: GlowColor
    const val KEY_RIPPLE = "rows_ripple"            // onda de luz ao apertar o card (1 = sim)
    const val KEY_TV_RATIO = "rows_tv_ratio"        // proporção dos cards de apps de TV (com banner): Ratio
    const val KEY_MOBILE_RATIO = "rows_mobile_ratio" // proporção dos cards de apps de celular (sem banner): Ratio
    const val KEY_CARD_BG = "rows_card_bg"          // cor de fundo dos cards sem arte: CardBg
    const val KEY_ICON_TINT = "rows_icon_tint"      // cor dos ícones de um tom só (os nossos): IconTint
    const val KEY_PROGRESS = "rows_progress"        // barra de progresso no card (1 = sim)
    const val KEY_START = "rows_start_margin"       // margem da esquerda (onde o foco fica preso): Margin
    const val KEY_END = "rows_end_margin"           // margem da direita (os cards somem antes dela): EndMargin
    const val KEY_TOP = "rows_top_margin"           // sobe ou desce a fileira em foco: TopMargin
    const val KEY_BADGE = "rows_badge"              // selo no card com novidade (1 = sim)
    // Fundo (bloco 5): sombra por cima do fundo e o fundo em si
    const val KEY_SHADOW_TINT = "rows_shadow_tint"  // cor da sombra: ShadowTint
    const val KEY_SHADOW_W = "rows_shadow_w"        // até onde a sombra da esquerda vai: ShadowLength
    const val KEY_SHADOW_H = "rows_shadow_h"        // até onde a sombra de baixo sobe: ShadowLength
    const val KEY_FOCUS_BG = "rows_focus_bg"        // a arte do card em foco vira o fundo (1 = sim; Projectivy: "Programa em foco")
    const val KEY_BG_EVERY = "rows_bg_every"        // troca a foto de fundo sozinha: BgEvery
    // Barra de status, hora e letra (bloco 6): Aparência > Barra de status e Fonte do Projectivy
    const val KEY_AUTOHIDE = "rows_autohide_top"    // a hora e a pílula somem fora da 1ª fileira (1 = sim; dele: "Ocultar ao rolar")
    const val KEY_STATUS_COLOR = "rows_status_color" // cor dos ícones da pílula: IconColor
    const val KEY_STATUS_BOX = "rows_status_box"    // fundo da pílula: StatusBox
    const val KEY_STATUS_SIZE = "rows_status_size"  // tamanho dos ícones da pílula: IconSize
    const val KEY_CLOCK_SIZE = "rows_clock_size"    // tamanho da hora: ClockSize
    const val KEY_CLOCK_LINE2 = "rows_clock_line2"  // 2ª linha embaixo da hora: Line2
    const val KEY_TEXT_SIZE = "rows_text_size"      // tamanho da letra dos títulos e do nome: TextSize
    const val KEY_BOLD = "rows_bold"                // letra em negrito (1 = sim)
    const val KEY_ITALIC = "rows_italic"            // letra em itálico (1 = sim)

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
    enum class GlowSize(val dp: Float) { THIN(1.5f), NORMAL(2.5f), THICK(4f) }
    /** Cor do brilho: DYNAMIC = a do card, clara (o padrão dele: "vazio = cor dinâmica do card"). */
    enum class GlowColor { DYNAMIC, WHITE, ACCENT }
    /** Proporções dele: 16/9, 4/3, 3/2, 1/1, 2/3 e pôster de filme. */
    enum class Ratio(val wh: Float) { R16_9(16f / 9f), R4_3(4f / 3f), R3_2(1.5f), R1_1(1f), R2_3(2f / 3f), POSTER(27f / 40f) }
    /** Fundo dos cards sem arte: AUTO = a cor do ícone (o dele); os outros, uma cor fixa. */
    enum class CardBg { AUTO, GRAPHITE, BLACK, WHITE, ACCENT }
    /** Tinta dos ícones de um tom só (dele: "Cor de ícone personalizado, para ícones monocromáticos"). */
    enum class IconTint { CREAM, BLACK, ACCENT }
    /** Sombra sobre o fundo (Projectivy: comprimento e opacidade da sombra horizontal e vertical). */
    /** Margens (Projectivy: Margem inicial / final / superior). Esquerda: onde o card em foco fica preso. */
    enum class Margin(val dp: Float) { TIGHT(16f), SMALL(28f), DEFAULT(40f), WIDE(56f), WIDER(80f) }
    /** Direita: os cards somem antes dela (0 = vão até a borda, como o dele). */
    enum class EndMargin(val dp: Float) { NONE(0f), SMALL(24f), MEDIUM(48f), LARGE(96f) }
    /** Em cima: desloca a fileira em foco (negativo = sobe). */
    enum class TopMargin(val dp: Float) { UP2(-48f), UP(-24f), DEFAULT(0f), DOWN(24f), DOWN2(48f) }
    enum class Shadow(val alpha: Float) { NONE(0f), LIGHT(0.45f), DEFAULT(0.7f), STRONG(0.9f) }
    /** Cor da sombra (dele: "Tonalidade da sombra"). Nunca azulada nem marrom: preto ou grafite. */
    enum class ShadowTint { BLACK, GRAPHITE }
    /** Comprimento da sombra (dele: horizontal e vertical), em fração da tela. */
    enum class ShadowLength(val pct: Float) { SHORT(0.35f), MEDIUM(0.55f), LONG(0.75f), FULL(1f) }
    /** Troca da foto de fundo (dele: "Frequência de atualização"), em minutos; 0 = nunca. */
    enum class BgEvery(val min: Int) { NEVER(0), M15(15), M30(30), M60(60), M180(180) }
    /** Cor dos ícones da pílula (dele: "Cor dos ícones"). */
    enum class IconColor { CREAM, WHITE, ACCENT }
    /** Fundo da pílula (dele: "Cor do contêiner dos ícones"): vidro leve, escuro ou nenhum. */
    enum class StatusBox { GLASS, DARK, NONE }
    enum class IconSize(val dp: Float) { S(18f), M(22f), L(28f) }
    /** Tamanho da hora (dele: "Tamanho da fonte do relógio"). */
    enum class ClockSize(val sp: Float) { S(26f), M(32f), L(40f), XL(48f) }
    /** 2ª linha do relógio (dele: "Formato do relógio, linha 2"): nada, data, dia da semana ou os dois. */
    enum class Line2 { NONE, DATE, WEEKDAY, BOTH }
    /** Tamanho da letra (dele: "Tamanho" da fonte). Sem degrau menor: nada na TV abaixo de 18 sp. */
    enum class TextSize(val k: Float) { M(1f), L(1.15f), XL(1.3f) }

    data class Options(
        val size: Size = Size.M, val gap: Gap = Gap.NORMAL, val corners: Corners = Corners.SOFT,
        val focusSize: FocusSize = FocusSize.MEDIUM, val effect: Effect = Effect.GLOW, val glowAnim: GlowAnim = GlowAnim.NONE,
        val cardTitle: Boolean = true, val rowTitle: Boolean = true, val rowIcon: Boolean = true,
        val spacing: Spacing = Spacing.AUTO, val fixed: Boolean = true, val hideAbove: Boolean = false,
        val dim: Dim = Dim.DEFAULT, val shadow: Shadow = Shadow.DEFAULT, val clock: Boolean = true, val status: Boolean = true,
        val zoom: Boolean = true, val glowSize: GlowSize = GlowSize.NORMAL, val glowColor: GlowColor = GlowColor.DYNAMIC,
        val ripple: Boolean = true, val tvRatio: Ratio = Ratio.R16_9, val mobileRatio: Ratio = Ratio.R1_1,
        val cardBg: CardBg = CardBg.AUTO, val iconTint: IconTint = IconTint.CREAM, val progress: Boolean = true, val badge: Boolean = true,
        val start: Margin = Margin.DEFAULT, val end: EndMargin = EndMargin.NONE, val top: TopMargin = TopMargin.DEFAULT,
        val shadowTint: ShadowTint = ShadowTint.BLACK, val shadowW: ShadowLength = ShadowLength.FULL, val shadowH: ShadowLength = ShadowLength.FULL,
        val focusBg: Boolean = false, val bgEvery: BgEvery = BgEvery.NEVER,
        val autohide: Boolean = false, val statusColor: IconColor = IconColor.CREAM, val statusBox: StatusBox = StatusBox.GLASS,
        val statusSize: IconSize = IconSize.M, val clockSize: ClockSize = ClockSize.M, val clockLine2: Line2 = Line2.NONE,
        val textSize: TextSize = TextSize.M, val bold: Boolean = false, val italic: Boolean = false,
    ) {
        /** Borda esquerda do card em foco e dos textos (o título e o nome ficam 16 dp para dentro, como nele). */
        val focoX get() = start.dp
        val textoX get() = start.dp + (TEXTO_X - FOCO_X)
        /** Quanto o card em foco cresce (sem o zoom, nada). */
        val focoK get() = if (zoom) focusSize.k else 1f
        /** Largura de um card com a proporção [r] (a altura é a do tamanho escolhido). */
        fun largura(r: Ratio) = Math.round(cardH * r.wh).toFloat()
        val cardW get() = Math.round(CARD_W * size.k).toFloat()
        val cardH get() = Math.round(CARD_H * size.k).toFloat()
        /** Muda o desenho da HOME: entra na assinatura que a refaz. */
        fun signature() = listOf(size, gap, corners, focusSize, effect, glowAnim, spacing, dim, shadow, glowSize, glowColor, tvRatio,
            mobileRatio, cardBg, iconTint, start, end, top, shadowTint, shadowW, shadowH, statusColor, statusBox, statusSize, clockSize,
            clockLine2, textSize).joinToString("") { it.ordinal.toString() } +
            listOf(cardTitle, rowTitle, rowIcon, fixed, hideAbove, clock, status, zoom, ripple, progress, badge, focusBg, autohide, bold,
                italic).joinToString("") { if (it) "1" else "0" }
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
            zoom = sim(KEY_ZOOM, d.zoom), glowSize = ler(KEY_GLOW_SIZE, GlowSize.entries, d.glowSize),
            glowColor = ler(KEY_GLOW_COLOR, GlowColor.entries, d.glowColor), ripple = sim(KEY_RIPPLE, d.ripple),
            tvRatio = ler(KEY_TV_RATIO, Ratio.entries, d.tvRatio), mobileRatio = ler(KEY_MOBILE_RATIO, Ratio.entries, d.mobileRatio),
            cardBg = ler(KEY_CARD_BG, CardBg.entries, d.cardBg), iconTint = ler(KEY_ICON_TINT, IconTint.entries, d.iconTint),
            progress = sim(KEY_PROGRESS, d.progress), badge = sim(KEY_BADGE, d.badge),
            start = ler(KEY_START, Margin.entries, d.start), end = ler(KEY_END, EndMargin.entries, d.end), top = ler(KEY_TOP, TopMargin.entries, d.top),
            shadowTint = ler(KEY_SHADOW_TINT, ShadowTint.entries, d.shadowTint), shadowW = ler(KEY_SHADOW_W, ShadowLength.entries, d.shadowW),
            shadowH = ler(KEY_SHADOW_H, ShadowLength.entries, d.shadowH), focusBg = sim(KEY_FOCUS_BG, d.focusBg),
            bgEvery = ler(KEY_BG_EVERY, BgEvery.entries, d.bgEvery),
            autohide = sim(KEY_AUTOHIDE, d.autohide), statusColor = ler(KEY_STATUS_COLOR, IconColor.entries, d.statusColor),
            statusBox = ler(KEY_STATUS_BOX, StatusBox.entries, d.statusBox), statusSize = ler(KEY_STATUS_SIZE, IconSize.entries, d.statusSize),
            clockSize = ler(KEY_CLOCK_SIZE, ClockSize.entries, d.clockSize), clockLine2 = ler(KEY_CLOCK_LINE2, Line2.entries, d.clockLine2),
            textSize = ler(KEY_TEXT_SIZE, TextSize.entries, d.textSize), bold = sim(KEY_BOLD, d.bold), italic = sim(KEY_ITALIC, d.italic),
        )
    }

    /** Padrão de data da 2ª linha do relógio (o Android localiza a ordem e os nomes); null = sem 2ª linha. */
    fun line2Skeleton(l: Line2): String? = when (l) {
        Line2.NONE -> null
        Line2.DATE -> "dMMMM"
        Line2.WEEKDAY -> "EEEE"
        Line2.BOTH -> "EEEEdMMMM"
    }

    /** A hora e a pílula aparecem? Com "ocultar ao rolar", só na 1ª fileira (como o dele). */
    fun topoVisivel(o: Options, cat: Int) = !o.autohide || cat == 0

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
    fun centroDaFileira(linha: Int, foco: Int, altura: Float, o: Options, extras: FloatArray? = null): Float {
        val base = altura * FOCO_Y + o.top.dp
        val baixo = if (o.spacing == Spacing.AUTO) PASSO_BAIXO else altura * o.spacing.pct
        val cima = if (o.spacing == Spacing.AUTO) PASSO_CIMA else baixo * (PASSO_CIMA / PASSO_BAIXO)
        fun ex(j: Int) = extras?.getOrNull(j) ?: 0f
        // Solto: a 1ª fileira começa logo abaixo da hora e a escolhida desce até a altura do foco parado; dali, as
        // fileiras sobem (como uma lista comum). Parado: a escolhida está sempre na mesma altura.
        var focoY = if (o.fixed) base else minOf(base, INICIO_SOLTO + (0 until foco).sumOf { (cima + ex(it)).toDouble() }.toFloat())
        // Grade em foco: sobe o bastante para as linhas de baixo e o nome caberem na tela
        if (ex(foco) > 0f) focoY = minOf(focoY, altura - ex(foco) - NOME_ABAIXO - MARGEM_BAIXO).coerceAtLeast(INICIO_SOLTO)
        return when {
            linha == foco -> focoY
            linha > foco -> focoY + (foco until linha).sumOf { (baixo + ex(it)).toDouble() }.toFloat()
            else -> focoY - (linha until foco).sumOf { (cima + ex(it)).toDouble() }.toFloat()
        }
    }

    /** Folga embaixo do nome do card quando a fileira em foco é uma grade. */
    const val MARGEM_BAIXO = 24f

    /** Distância (dp) entre as linhas de uma grade: cabe o card em foco crescido sem encostar no vizinho. */
    fun passoDaLinha(o: Options): Float = o.cardH * (1f + (o.focoK - 1f) / 2f) + o.gap.dp.coerceAtLeast(8f)

    /** Altura a mais (dp) de uma fileira de [n] cards em [linhas] linhas (0 = fileira comum). */
    fun extraDaGrade(n: Int, linhas: Int, o: Options): Float = (minOf(linhas, n).coerceAtLeast(1) - 1) * passoDaLinha(o)

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
     * de depois vêm logo atrás do foco aumentado. [larguras] de cada card (a proporção muda com o tipo de app);
     * [focada] = a fileira é a escolhida (só ela cresce).
     */
    fun xs(larguras: List<Float>, sel: Int, o: Options, focada: Boolean, linhas: Int = 1): FloatArray {
        val n = larguras.size
        val vao = o.gap.dp
        val r = FloatArray(n)
        if (n == 0) return r
        // Grade: as colunas andam juntas (card i na coluna i / L); a coluna do foco fica presa em focoX.
        val l = linhas.coerceAtLeast(1)
        val s = sel.coerceIn(0, n - 1)
        val cols = (n + l - 1) / l
        val sc = s / l
        val largura = FloatArray(cols) { c -> (c * l until minOf(n, c * l + l)).maxOf { larguras[it] } }
        val colX = FloatArray(cols)
        colX[sc] = o.focoX
        var x = o.focoX + maxOf(largura[sc], larguras[s] * (if (focada) o.focoK else 1f)) + vao
        for (c in sc + 1 until cols) { colX[c] = x; x += largura[c] + vao }
        x = o.focoX
        for (c in sc - 1 downTo 0) { x -= largura[c] + vao; colX[c] = x }
        for (i in 0 until n) r[i] = colX[i / l]
        return r
    }

    /** Fileira de [n] cards iguais (teste e quem não tem as larguras). */
    fun xs(n: Int, sel: Int, o: Options, focada: Boolean) = xs(List(n) { o.cardW }, sel, o, focada)

    /** O card em [x] com largura [w] aparece numa tela de [largura] dp (folga de um card de cada lado, para entrar deslizando). */
    fun visivel(x: Float, w: Float, o: Options, largura: Float): Boolean =
        x + w * o.focoK > -w && (if (o.end == EndMargin.NONE) x < largura + w else x + w <= largura - o.end.dp)
}
