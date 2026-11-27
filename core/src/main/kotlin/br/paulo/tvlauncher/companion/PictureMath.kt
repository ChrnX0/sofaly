package br.paulo.tvlauncher.companion

/**
 * Calibrar a imagem pela câmera do celular (24/11/2026, proposta B, "funções do Apple TV"). Conta pura, sem Android
 * (PictureMathTest).
 *
 * Por quê: a imagem da TV costuma sair da loja com brilho, contraste e cor errados, e o tvOS acerta isso com o iPhone.
 * Para quê: preto com detalhe, branco sem estourar, nada cortado nas bordas e branco neutro, sem a pessoa entender de
 * calibração. Como: a TV mostra padrões fixos ([Pattern]); o celular, pelo painel (companion.js), fotografa cada um e
 * manda só números (caixas e médias de cor); aqui eles viram veredito e conselho. Quem guarda e avisa a Sofy é o
 * [CompanionTv].
 *
 * Limite verificado na Sala (24/11/2026): o Android não deixa o Sofaly mudar a cor do sistema sem root
 * (SurfaceFlinger 1015 = "Operation not permitted"). Então o resultado é conselho para o menu da TV, medido de novo ao
 * vivo enquanto a pessoa mexe, e não uma correção aplicada por baixo.
 */
object PictureMath {

    /** Os padrões que a TV desenha a pedido do celular. Os valores ficam aqui para o teste e a tela usarem os mesmos. */
    enum class Pattern { BLACK, WHITE, INSET, NEAR_BLACK, NEAR_WHITE, GRAY }

    /** Recuo do retângulo branco do padrão [Pattern.INSET], em fração da tela, de cada lado. */
    const val INSET = 0.05

    /** Degraus dos padrões de quase preto e quase branco (7 barras verticais, da esquerda para a direita). */
    val NEAR_BLACK_STEPS = listOf(0, 5, 10, 15, 20, 25, 30)
    val NEAR_WHITE_STEPS = listOf(225, 230, 235, 240, 245, 250, 255)

    /** Cinza do padrão de cor: 80% fica longe de estourar na câmera e ainda mostra o tom do branco da TV. */
    const val GRAY_LEVEL = 204

    /** Caixa em pixels da câmera. */
    data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        val width get() = right - left
        val height get() = bottom - top
        fun valid() = width > 4 && height > 4
    }

    /** Quanto da imagem a TV corta em cada lado, em fração da tela (0.03 = 3%). */
    data class Overscan(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        val worst get() = maxOf(left, top, right, bottom)
    }

    /**
     * O corte das bordas a partir de duas caixas vistas pela câmera: [visible] = o branco da tela inteira (o que a TV
     * deixa ver) e [inset] = o retângulo branco recuado [INSET] da borda. Modelo: a TV mostra só a faixa [k1, 1-k2] da
     * imagem e a estica no painel; resolvendo as duas bordas do retângulo dá k1 e k2 (contas no comentário de [cut]).
     * null quando as caixas não fazem sentido (celular tremeu, TV fora do quadro).
     */
    fun overscan(visible: Box, inset: Box): Overscan? {
        if (!visible.valid() || !inset.valid()) return null
        if (inset.left < visible.left - 2 || inset.right > visible.right + 2 || inset.top < visible.top - 2 || inset.bottom > visible.bottom + 2) return null
        val h = cut((inset.left - visible.left) / visible.width, (visible.right - inset.right) / visible.width) ?: return null
        val v = cut((inset.top - visible.top) / visible.height, (visible.bottom - inset.bottom) / visible.height) ?: return null
        return Overscan(h.first, v.first, h.second, v.second)
    }

    /**
     * u e w = distância (fração da largura vista) de cada borda do retângulo até a borda da tela vista. Com D = 1-k1-k2:
     * u·D = INSET-k1 e w·D = INSET-k2. Somando: D·(u+w) = 2·INSET-(1-D), logo D = (1-2·INSET)/(1-u-w). Corte negativo
     * (imagem menor que a tela, "underscan") vira 0: para a pessoa só importa se perde imagem.
     */
    private fun cut(u: Double, w: Double): Pair<Double, Double>? {
        if (u.isNaN() || w.isNaN() || u + w >= 0.9) return null
        val d = (1 - 2 * INSET) / (1 - u - w)
        if (d <= 0.5 || d > 1.2) return null
        val k1 = (INSET - u * d).coerceAtLeast(0.0)
        val k2 = (INSET - w * d).coerceAtLeast(0.0)
        return k1 to k2
    }

    /**
     * Corte até 2% fica dentro do erro da foto (1 ponto da foto de 320 px = ~0,4% da tela, mais a perspectiva do celular
     * torto). Overscan de verdade corta 2,5 a 5% de cada lado. Antes era 1% e um celular inclinado já virava "cortando"
     * (Sala, 25/11/2026: l=1,4 t=0 r=1,1 b=3,7, que é perspectiva e brilho estourado, não corte).
     */
    const val OVERSCAN_OK = 0.02

    /** Corte de verdade é parecido nos dois lados de cada eixo; diferença maior que isso = foto torta, não corte. */
    const val OVERSCAN_ASYMMETRY = 0.025

    enum class Verdict { GOOD, ADJUST, UNSURE }

    /** O que a pessoa faz no menu da TV. [Advice.KEEP] = está bom. */
    enum class Advice {
        KEEP,
        /** Overscan: ligar "Tamanho original" / "Just Scan" / "Ajustar à tela" no formato da imagem. */
        TURN_ON_JUST_SCAN,
        /** Preto: os primeiros degraus somem no preto. */
        RAISE_BRIGHTNESS,
        /** Preto acinzentado: o degrau 0 já aparece claro. */
        LOWER_BRIGHTNESS,
        /** Branco: os últimos degraus colam no branco. */
        LOWER_CONTRAST,
        /** Cor: branco puxando para o azul (frio). */
        WARMER,
        /** Cor: branco puxando para o amarelo/vermelho (quente). */
        COOLER,
        /** Cor: branco puxando para o verde. */
        LESS_GREEN,
        /** Cor: branco puxando para o magenta (falta verde). */
        MORE_GREEN,
    }

    data class Result(val verdict: Verdict, val advice: Advice, val detail: String)

    fun judgeOverscan(o: Overscan?): Result = when {
        o == null -> Result(Verdict.UNSURE, Advice.KEEP, "no_frame")
        o.worst > OVERSCAN_OK && (kotlin.math.abs(o.left - o.right) > OVERSCAN_ASYMMETRY ||
            kotlin.math.abs(o.top - o.bottom) > OVERSCAN_ASYMMETRY) -> Result(Verdict.UNSURE, Advice.KEEP, "tilted " + fmtOverscan(o))
        o.worst <= OVERSCAN_OK -> Result(Verdict.GOOD, Advice.KEEP, fmtOverscan(o))
        else -> Result(Verdict.ADJUST, Advice.TURN_ON_JUST_SCAN, fmtOverscan(o))
    }

    private fun fmtOverscan(o: Overscan) =
        "l=${pct(o.left)} t=${pct(o.top)} r=${pct(o.right)} b=${pct(o.bottom)}"

    private fun pct(x: Double) = "%.1f%%".format(java.util.Locale.ROOT, x * 100)

    /**
     * Quantos degraus se separam do primeiro. [luma] = brilho médio de cada barra na foto (0..255 da câmera), na ordem
     * da tela. Uma barra "aparece" quando passa do vizinho de baixo por mais que o ruído: [minStep] em unidades da câmera
     * (padrão 2,5: abaixo disso a compressão do vídeo da câmera já inventa diferença).
     */
    fun visibleSteps(luma: List<Double>, minStep: Double = 2.5): Int {
        var n = 0
        for (i in 1 until luma.size) if (luma[i] - luma[0] > minStep * i.coerceAtMost(2)) n++
        return n
    }

    /** Câmera estourada: a foto chegou ao teto e não serve para ver os brancos. */
    fun saturated(luma: List<Double>) = luma.isNotEmpty() && luma.max() >= 250.0

    /**
     * Quase preto (degraus 0..30). Bom: o degrau 5 ou 10 já se separa do 0 e o 0 não está claro. [blackOverWhite] = brilho
     * do degrau 0 dividido pelo do branco da tela inteira, quando a exposição da câmera estava travada (senão null):
     * acima de 0,12 o preto está acinzentado (painel LCD bom fica perto de 0,05 na câmera do celular; número de partida,
     * não medido em muitas TVs).
     */
    fun judgeBlack(luma: List<Double>, blackOverWhite: Double?): Result {
        if (luma.size != NEAR_BLACK_STEPS.size) return Result(Verdict.UNSURE, Advice.KEEP, "bars=${luma.size}")
        val seen = visibleSteps(luma)
        val firstSeen = (1 until luma.size).firstOrNull { luma[it] - luma[0] > 2.5 * it.coerceAtMost(2) }
        val detail = "first=${firstSeen?.let { NEAR_BLACK_STEPS[it] } ?: "none"} seen=$seen" +
            (blackOverWhite?.let { " bw=%.3f".format(java.util.Locale.ROOT, it) } ?: "")
        return when {
            firstSeen == null || firstSeen >= 3 -> Result(Verdict.ADJUST, Advice.RAISE_BRIGHTNESS, detail)
            blackOverWhite != null && blackOverWhite > 0.12 -> Result(Verdict.ADJUST, Advice.LOWER_BRIGHTNESS, detail)
            else -> Result(Verdict.GOOD, Advice.KEEP, detail)
        }
    }

    /** Quase branco (225..255): bom quando o 250 ainda se separa do 255. Câmera estourada = não dá para dizer. */
    fun judgeWhite(luma: List<Double>): Result {
        if (luma.size != NEAR_WHITE_STEPS.size) return Result(Verdict.UNSURE, Advice.KEEP, "bars=${luma.size}")
        if (saturated(luma)) return Result(Verdict.UNSURE, Advice.KEEP, "camera_saturated")
        val last = luma.size - 1
        // Quantos degraus do topo colam no 255 (diferença menor que o ruído).
        var glued = 0
        for (i in last - 1 downTo 0) if (luma[last] - luma[i] < 1.5) glued++ else break
        val detail = "glued=$glued top=${NEAR_WHITE_STEPS[last - glued]}"
        return if (glued >= 2) Result(Verdict.ADJUST, Advice.LOWER_CONTRAST, detail) else Result(Verdict.GOOD, Advice.KEEP, detail)
    }

    /** Ganho de cada canal para o cinza ficar neutro: o canal mais fraco fica em 1, os outros abaixo. */
    data class Gain(val r: Double, val g: Double, val b: Double)

    /**
     * Cor do cinza 80% na foto (médias R, G, B da câmera). Só conta quando o balanço de branco da câmera estava travado
     * ([wbLocked]); com ele automático, a câmera "corrige" a TV e a medida não diz nada (por isso a Apple exige um sensor
     * calibrado). Tolerância: 3% entre canais, perto do que se enxerga num branco grande.
     */
    fun gain(r: Double, g: Double, b: Double): Gain? {
        if (r <= 0 || g <= 0 || b <= 0) return null
        // Baixar os canais fortes até o mais fraco: nenhum ganho passa de 1 (subir um canal estouraria o branco).
        val m = minOf(r, g, b)
        return Gain(m / r, m / g, m / b)
    }

    const val COLOR_TOLERANCE = 0.03

    fun judgeColor(r: Double, g: Double, b: Double, wbLocked: Boolean): Result {
        val gain = gain(r, g, b) ?: return Result(Verdict.UNSURE, Advice.KEEP, "no_light")
        val detail = "r=%.2f g=%.2f b=%.2f".format(java.util.Locale.ROOT, gain.r, gain.g, gain.b)
        if (!wbLocked) return Result(Verdict.UNSURE, Advice.KEEP, "$detail wb=auto")
        if (r >= 250 || g >= 250 || b >= 250) return Result(Verdict.UNSURE, Advice.KEEP, "camera_saturated")
        val rr = r / g; val bb = b / g
        val advice = when {
            // Eixo quente/frio primeiro (é o "Temperatura da cor" do menu, o mais fácil de achar).
            bb - rr > 2 * COLOR_TOLERANCE -> Advice.WARMER
            rr - bb > 2 * COLOR_TOLERANCE -> Advice.COOLER
            // Depois o verde contra os outros dois.
            1 - maxOf(rr, bb) > COLOR_TOLERANCE && 1 - minOf(rr, bb) > COLOR_TOLERANCE -> Advice.LESS_GREEN
            minOf(rr, bb) - 1 > COLOR_TOLERANCE -> Advice.MORE_GREEN
            else -> Advice.KEEP
        }
        return Result(if (advice == Advice.KEEP) Verdict.GOOD else Verdict.ADJUST, advice, detail)
    }
}
