package br.paulo.tvlauncher.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.paulo.tvlauncher.DesignTokens
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.settings.Fontes

/**
 * O desenho da barra única ([StripHome]): a linha do mundo (ícone de traço, nome e descrição numa linha só, Manrope) e a
 * linha de cards dos apps embaixo. A [HomeCruz] cuida das teclas e do estado e chama [mostrar] a cada passo.
 *
 * Desempenho: os cards de um mundo são criados UMA vez quando o mundo entra; cada ◀ ▶ só anima translationX/Y e alpha.
 * Os banners (a arte de TV de cada app) chegam pelo [bannerDe] só para os cards perto da janela visível.
 * Referências: estudo escolhas-home v2 (aprovado pelo Paulo em 24/11/2026); Projectivy (cards personalizáveis, linha
 * fixa do foco); Apple TV no Mobbin (linha de cards embaixo, título acima).
 */
class StripLayer(ctx: Context) : FrameLayout(ctx) {

    var opcoes: StripHome.Options = StripHome.DEFAULT
        set(v) { if (field != v) { field = v; mundoDesenhado = -1 } }
    /** Banner de TV de um item (só apps); responde depois, na thread principal. null = sem banner, fica o ícone. */
    var bannerDe: ((HomeCruz.Item, (Drawable?) -> Unit) -> Unit)? = null
    /** Avisa (dp) quanto a linha do mundo ocupa do pé da tela até o topo dela; muda com o jeito dos cards (camada dos widgets). */
    var aoMudarBase: ((Float) -> Unit)? = null
    private var baseAvisada = -1f

    private val dm = resources.displayMetrics
    private fun dp(v: Float) = v * dm.density
    private fun dpi(v: Float) = (v * dm.density + 0.5f).toInt()
    private val curva = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    /** Curva do voo do mundo: entra e sai devagar (ease-in-out suave), sem o arranque da [curva]. */
    private val curvaVoo = PathInterpolator(0.45f, 0f, 0.2f, 1f)
    private val forte = Fontes.manrope(ctx, true)
    private val media = Fontes.manrope(ctx, false)
    private val desc2 = 0xBDFFF3E6.toInt()   // creme 74%

    private val trilho = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
    private var trilhoDe: List<CruzLogica.Categoria> = emptyList()
    private companion object {
        const val TRILHO_ICONE = 16f; const val TRILHO_VAO = 6f; const val TRILHO_OUTROS = 0.75f
        const val ROTULO_SP = 11f          // nome dentro do card
        const val ICONE_LADO = 0.42f       // card nosso: ícone ao lado do nome (fração da altura)
        const val LADO_VAO = 6f            // dp entre o ícone e o nome
        /** Conjuntos de traço de uma cor só (Linha e Símbolo): esses ganham a tinta da placa; o Duotom tem halo e fica. */
        val TRACO_DE_UMA_COR = setOf(IconeCruz.Estilo.LINHA, IconeCruz.Estilo.SIMBOLO)
        const val BRILHO_ESCURO = 0.30f    // "Brilho da cor": quanto da cor do item entra no vidro escuro aceso
        const val BRILHO_BRANCO = 0.16f    // e na placa branca
        const val SO_ICONE = 1.7f          // "só o escolhido": ícone dos outros, sem placa
        const val SO_OUTROS = 0.72f        // "só o escolhido": tamanho dos outros
        const val NOME_FORA_SOBRA = 28f    // o nome embaixo do redondo pode passar um pouco da largura do círculo
    }
    private val linha = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }
    private val icone = ImageView(ctx)
    private val nome = texto(StripHome.FONTE_NOME, forte, DesignTokens.CREAM)
    private val barrinha = View(ctx).apply { background = GradientDrawable().apply { cornerRadius = dp(1f); setColor(0x59FFF3E6) } }
    private val desc = texto(StripHome.FONTE_DESC, media, desc2).apply { ellipsize = TextUtils.TruncateAt.END }
    private val placa = View(ctx)
    private val anel = View(ctx)

    private class Card(val item: HomeCruz.Item, val raiz: FrameLayout, val banner: ImageView, val proprio: LinearLayout,
                       val icone: ImageView, val nomeFoco: TextView, val rotulo: TextView?, val fora: TextView?,
                       val fundo: GradientDrawable, val conteudo: Boolean, val own: StripHome.Own?) {
        var pediu = false
        var temBanner = false
    }
    private val cards = ArrayList<Card>()
    private var mundoDesenhado = -1
    /** As opções com que os cards do mundo atual foram feitos (o jeito do mundo pode ser outro que o geral). */
    private var opcoesDesenhadas: StripHome.Options? = null
    private var formatoDesenhado: StripHome.Format = StripHome.DEFAULT.format
    private var idsDesenhados: List<String> = emptyList()
    private var avisoAte = 0L
    private val linhaAnimando = false   // (25/11/2026) a linha não anda mais inteira: o ícone voa e os textos entram
    /** Quanto movimento a HOME pode ter agora (a [HomeCruz] passa antes de cada desenho; PARADO sem animações). */
    var nivel: MovimentoAdaptativo.Nivel = MovimentoAdaptativo.Nivel.COMPLETO
    /** O último desenho, para refazer no lugar quando a tela muda de tamanho (antes de medir vale 960 x 540). */
    private var ultimo: (() -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w > 0 && h > 0 && (w != ow || h != oh)) post { ultimo?.invoke() }
    }

    /** O ícone vivo só anda com a HOME na tela (um app na frente ou a cruz no lugar da barra: para). */
    @Suppress("SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL")
    private fun moverIcone() {
        if (icone == null) return
        val aVista = isShown && windowVisibility == View.VISIBLE
        (icone.drawable as? WorldMotion)?.let { if (aVista) it.start() else it.stop() }
        // os ícones dos cards também param fora da tela; ao voltar, o último desenho liga de novo os que se mexem
        if (!aVista) pararIcones() else if (cards.isNotEmpty()) post { if (isShown) ultimo?.invoke() }
    }
    private fun pararIcones() { for (k in cards) (k.icone.drawable as? IconeCruz)?.animar(false) }
    override fun onWindowVisibilityChanged(v: Int) { super.onWindowVisibilityChanged(v); moverIcone() }
    override fun onVisibilityAggregated(v: Boolean) { super.onVisibilityAggregated(v); moverIcone() }
    override fun onDetachedFromWindow() { (icone.drawable as? WorldMotion)?.stop(); pararIcones(); super.onDetachedFromWindow() }

    init {
        clipChildren = false; clipToPadding = false
        trilho.clipChildren = false; trilho.clipToPadding = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(trilho, LayoutParams(dpi(TRILHO_ICONE), LayoutParams.WRAP_CONTENT))
        linha.addView(icone, LinearLayout.LayoutParams(dpi(StripHome.ICONE), dpi(StripHome.ICONE)))
        linha.addView(nome, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(12f) })
        linha.addView(barrinha, LinearLayout.LayoutParams(dpi(1.5f).coerceAtLeast(2), dpi(20f)).apply { marginStart = dpi(14f); marginEnd = dpi(14f) })
        linha.addView(desc, LinearLayout.LayoutParams(-2, -2))
        icone.elevation = 0f
        addView(linha, LayoutParams(-2, dpi(StripHome.LINHA_A)))
        placa.background = GradientDrawable().apply { setColor(0x8C121212.toInt()); setStroke(dpi(1f), 0x1FFFF3E6) }
        placa.visibility = View.GONE
        addView(placa, LayoutParams(0, 0))
        anel.outlineProvider = null
        anel.elevation = dp(14f)
        anel.alpha = 0f
        addView(anel, LayoutParams(0, 0))
    }

    private fun texto(sp: Float, tf: Typeface, cor: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = tf
        setTextColor(cor)
        maxLines = 1
        includeFontPadding = false
        setShadowLayer(dp(10f), 0f, dp(1f), 0x8C000000.toInt())
    }

    private fun largura() = if (width > 0) width / dm.density else 960f
    private fun altura() = if (height > 0) height / dm.density else 540f

    // ------------------------------------------------------------------ desenho

    /**
     * Põe tudo no lugar do estado: mundo [cat] de [cats], app [sel] dele. [direcao] = de onde o mundo veio (-1 de cima,
     * +1 de baixo, 0 = mesmo mundo); [animar] = false põe direto no lugar.
     */
    fun mostrar(cats: List<HomeCruz.Categoria>, cat: Int, sel: Int, focada: Boolean, animar: Boolean, direcao: Int) {
        ultimo = { mostrar(cats, cat, sel, focada, false, 0) }
        selDesc = sel; focadaDesc = focada
        val c = cats.getOrNull(cat) ?: return
        val anima = animar && !semAnimacao()
        val o = opcoesDe(c.cat)
        // trilho: o ícone de cada mundo, pequeno, o atual aceso (24/11/2026, navegação do Projectivy: a pessoa vê de relance
        // onde está e o que vem em cima/embaixo; os pontinhos não diziam qual mundo era qual)
        if (trilho.childCount != cats.size || trilhoDe != cats.map { it.cat }) {
            trilho.removeAllViews()
            trilhoDe = cats.map { it.cat }
            for ((i, k) in cats.withIndex()) trilho.addView(ImageView(context).apply { setImageDrawable(WorldGlyph(k.cat, DesignTokens.CREAM, 2.4f)) },
                LinearLayout.LayoutParams(dpi(TRILHO_ICONE), dpi(TRILHO_ICONE)).apply { topMargin = if (i > 0) dpi(TRILHO_VAO) else 0 })
        }
        // O mundo escolhido SAI do trilho (Paulo, 25/11/2026: o ícone ficava duplicado, no trilho e grande na linha):
        // no trilho fica o vão dele, e o ícone grande da linha "voa" desse vão para o lugar dele (zoom para a direita).
        // O que deixou de ser o escolhido volta voando para o vão dele.
        val voo = dp((StripHome.TRILHO_X + 2f) - (StripHome.MARGEM + StripHome.ICONE / 2f))   // do lugar grande até o vão (negativo)
        val zoom = StripHome.ICONE / TRILHO_ICONE
        val antigo = mundoDesenhado
        for (i in 0 until trilho.childCount) trilho.getChildAt(i).let { v ->
            val atual = i == cat
            v.animate().cancel()
            if (atual) { v.alpha = 0f; v.translationX = 0f; v.scaleX = TRILHO_OUTROS; v.scaleY = TRILHO_OUTROS; return@let }
            if (anima && i == antigo && antigo != cat) {
                // volta do lugar grande para o vão, encolhendo
                v.translationX = -voo; v.scaleX = zoom; v.scaleY = zoom; v.alpha = 1f
                v.animate().translationX(0f).scaleX(TRILHO_OUTROS).scaleY(TRILHO_OUTROS).alpha(0.38f)
                    .setDuration(StripHome.MS_VOO).setInterpolator(curvaVoo).start()
            } else { v.translationX = 0f; v.scaleX = TRILHO_OUTROS; v.scaleY = TRILHO_OUTROS; v.alpha = 0.38f }
        }
        // Cards grandes (ou com placa, ou a grade de 2 linhas) não encostam no pé da tela nem na linha do mundo: sobem o que
        // precisar. [topo] = alto do bloco de cards (o card em foco sobe mais 4 dp).
        val folgaPlaca = if (o.glass) StripHome.PLACA else 0f
        val topo = minOf(altura() - StripHome.CARDS_DO_FUNDO - o.cardH / 2f, altura() - 16f - folgaPlaca - o.blocoH)
        val topoLinha = minOf(altura() - StripHome.LINHA_DO_FUNDO, topo - folgaPlaca - StripHome.SUBIR_FOCO - 14f - StripHome.LINHA_A)
        if (height > 0 && altura() - topoLinha != baseAvisada) { baseAvisada = altura() - topoLinha; aoMudarBase?.invoke(baseAvisada) }
        // o trilho anda para que o vão do mundo escolhido fique sempre na altura da linha do mundo (sobe e desce nas setas)
        val trilhoY = dp(topoLinha + StripHome.LINHA_A / 2f - (cat * (TRILHO_ICONE + TRILHO_VAO) + TRILHO_ICONE / 2f))
        trilho.translationX = dp(StripHome.TRILHO_X + 2f - TRILHO_ICONE / 2f)
        trilho.animate().cancel()
        if (anima && antigo >= 0 && antigo != cat) trilho.animate().translationY(trilhoY).setDuration(StripHome.MS_VOO).setInterpolator(curvaVoo).start()
        else trilho.translationY = trilhoY
        trilho.visibility = if (cats.size > 1) View.VISIBLE else View.INVISIBLE
        linha.translationX = dp(StripHome.MARGEM)

        // linha do mundo
        val ids = c.itens.map { it.id }
        val mundoNovo = cat != mundoDesenhado || ids != idsDesenhados
        val trocouMundo = cat != mundoDesenhado
        val trocouAnim = (icone.drawable as? WorldMotion)?.let { it.anim != o.worldAnim } ?: false
        if (trocouMundo || trocouAnim) {
            // Ícone vivo (24/11/2026; estilos de 25/11 no Ajuste fino): anima sem parar; sem movimento permitido, o parado.
            (icone.drawable as? WorldMotion)?.stop()
            icone.setImageDrawable(if (WorldMotion.permitido(context)) WorldMotion(c.cat, DesignTokens.CREAM, anim = o.worldAnim).also { if (isShown) it.start() }
                else WorldGlyph(c.cat, DesignTokens.CREAM))
            nome.text = c.nome
        }
        escreverDesc(c)
        if (trocouMundo && anima && antigo >= 0) {
            // o ícone sai do vão do trilho (mesma altura) e cresce até o lugar dele; os textos entram logo atrás
            linha.animate().cancel(); linha.alpha = 1f; linha.translationY = dp(topoLinha)
            icone.animate().cancel()
            icone.translationX = voo; icone.scaleX = 1f / zoom; icone.scaleY = 1f / zoom
            icone.animate().translationX(0f).scaleX(1f).scaleY(1f).setDuration(StripHome.MS_VOO).setInterpolator(curvaVoo).start()
            for (t in listOf(nome, barrinha, desc)) {
                t.animate().cancel(); t.alpha = 0f; t.translationY = dp(6f * direcao)
                t.animate().alpha(1f).translationY(0f).setStartDelay(StripHome.MS_VOO / 2).setDuration(StripHome.MS_VOO / 2 + 80).setInterpolator(curvaVoo)
                    .withEndAction { t.animate().setStartDelay(0) }.start()
            }
        } else if (!linhaAnimando) {
            linha.alpha = 1f; linha.translationY = dp(topoLinha)
            if (trocouMundo) { icone.animate().cancel(); icone.translationX = 0f; icone.scaleX = 1f; icone.scaleY = 1f
                for (t in listOf(nome, barrinha, desc)) { t.animate().cancel(); t.alpha = 1f; t.translationY = 0f } }
        }

        // cards
        val fmt = o.format
        val grade = fmt.grade
        if (mundoNovo || o != opcoesDesenhadas) { refazerCards(c, o); opcoesDesenhadas = o }
        mundoDesenhado = cat; idsDesenhados = ids; formatoDesenhado = fmt
        val s = sel.coerceIn(0, (cards.size - 1).coerceAtLeast(0))
        val sCol = StripHome.coluna(s, grade)
        val nCols = StripHome.colunas(cards.size, grade)
        // largura de cada COLUNA (na fileira comum, coluna = card)
        val larguras = (0 until nCols).map { col -> if (grade) o.cardW else larguraCard(cards[col], col == s && o.name && fmt.nomeNoFoco, o) }
        val vao = o.gap.dp
        val (ini, vis) = StripHome.janela(sCol, larguras, vao, largura() - 2 * StripHome.MARGEM)
        // x de cada coluna: a 1ª visível começa na margem; as de antes ficam à esquerda, fora (a linha desliza)
        val xs = FloatArray(nCols)
        var x = StripHome.MARGEM
        for (i in ini until nCols) { xs[i] = x; x += larguras[i] + vao }
        x = StripHome.MARGEM
        for (i in ini - 1 downTo 0) { x -= larguras[i] + vao; xs[i] = x }
        fun yDe(i: Int) = topo + StripHome.linhaDaGrade(i, grade) * (o.cardH + StripHome.VAO_GRADE)
        val semMenu = hasWindowFocus() || br.paulo.tvlauncher.FineTuneActivity.aberto
        for ((i, k) in cards.withIndex()) {
            val col = StripHome.coluna(i, grade)
            val escolhido = i == s
            val visivel = col - ini in 0 until vis
            val w = larguras[col]
            val lp = k.raiz.layoutParams
            if (lp.width != dpi(w)) { lp.width = dpi(w); k.raiz.layoutParams = lp }
            k.nomeFoco.visibility = if (escolhido && o.name && fmt.nomeNoFoco) View.VISIBLE else View.GONE
            val escala = if (fmt == StripHome.Format.ONLY) soEscolhido(k, escolhido) else 1f
            tingir(k, escolhido, focada)
            moverIconeDoCard(k, escolhido, focada && semMenu && visivel, kotlin.math.abs(col - sCol), o)
            val sobe = if (escolhido && focada) StripHome.SUBIR_FOCO else 0f
            val ty = dp(yDe(i) - sobe)
            val alfa = if (visivel) 1f else 0f
            k.raiz.elevation = dp(if (escolhido && focada) 10f else 4f)
            // nome embaixo do redondo: acompanha o card; o do escolhido fica creme cheio
            val fx = dp(xs[col] - NOME_FORA_SOBRA / 2f); val fy = dp(yDe(i) + o.cardH + 6f)
            k.fora?.setTextColor(if (escolhido && focada) DesignTokens.CREAM else desc2)
            if (anima && !mundoNovo) {
                k.raiz.animate().cancel()
                k.raiz.animate().translationX(dp(xs[col])).translationY(ty).alpha(alfa).scaleX(escala).scaleY(escala).setDuration(StripHome.MS).setInterpolator(curva).start()
                k.fora?.animate()?.translationX(fx)?.translationY(fy)?.alpha(alfa)?.setDuration(StripHome.MS)?.setInterpolator(curva)?.start()
            } else if (!(anima && mundoNovo)) {
                k.raiz.animate().cancel()
                k.raiz.translationX = dp(xs[col]); k.raiz.translationY = ty; k.raiz.alpha = alfa; k.raiz.scaleX = escala; k.raiz.scaleY = escala
                k.fora?.apply { animate().cancel(); translationX = fx; translationY = fy; alpha = alfa }
            } else {
                // mundo novo: os cards entram de baixo, coluna depois de coluna (40 ms)
                k.raiz.animate().cancel()
                k.raiz.translationX = dp(xs[col]); k.raiz.translationY = ty + dp(10f); k.raiz.alpha = 0f; k.raiz.scaleX = escala; k.raiz.scaleY = escala
                k.fora?.apply { animate().cancel(); translationX = fx; translationY = fy + dp(10f); alpha = 0f }
                val atraso = StripHome.ESCALONA_MS * (col - ini)
                if (visivel) {
                    k.raiz.animate().translationY(ty).alpha(1f).setStartDelay(atraso)
                        .setDuration(StripHome.MS).setInterpolator(curva).withEndAction { k.raiz.animate().setStartDelay(0) }.start()
                    k.fora?.let { f -> f.animate().translationY(fy).alpha(1f).setStartDelay(atraso).setDuration(StripHome.MS).setInterpolator(curva)
                        .withEndAction { f.animate().setStartDelay(0) }.start() }
                } else k.raiz.translationY = ty
            }
            if (col - ini in -1..vis) pedirBanner(k, o)
        }
        // anel laranja no escolhido (só com o foco na linha e nenhuma janela por cima)
        cards.getOrNull(s)?.let { _ ->
            val a = StripHome.ANEL
            val r = o.raio + a
            (anel.background as? GradientDrawable ?: GradientDrawable().also { anel.background = it }).apply {
                cornerRadius = dp(r); setColor(0); setStroke(dpi(a), Estilo.ACENTO)
            }
            val lp = anel.layoutParams
            val aw = dpi(larguras[sCol] + 2 * a); val ah = dpi(o.cardH + 2 * a)
            if (lp.width != aw || lp.height != ah) { lp.width = aw; lp.height = ah; anel.layoutParams = lp }
            val ax = dp(xs[sCol] - a); val ay = dp(yDe(s) - StripHome.SUBIR_FOCO - a)
            val ligado = if (focada && semMenu) 1f else 0f
            anel.animate().cancel()
            if (anima && !mundoNovo) anel.animate().translationX(ax).translationY(ay).alpha(ligado).setDuration(StripHome.MS).setInterpolator(curva).start()
            else { anel.translationX = ax; anel.translationY = ay; anel.alpha = if (anima && mundoNovo) 0f else ligado
                if (anima && mundoNovo && ligado > 0f) anel.animate().alpha(1f).setStartDelay(StripHome.ESCALONA_MS * (sCol - ini)).setDuration(StripHome.MS)
                    .withEndAction { anel.animate().setStartDelay(0) }.start() }
        }
        // placa de vidro atrás dos visíveis
        if (o.glass && vis > 0) {
            val p = StripHome.PLACA
            var total = 0f
            for (i in ini until ini + vis) total += larguras[i] + (if (i > ini) vao else 0f)
            val lp = placa.layoutParams
            val pw = dpi(total + 2 * p); val ph = dpi(o.blocoH + 2 * p)
            if (lp.width != pw || lp.height != ph) { lp.width = pw; lp.height = ph; placa.layoutParams = lp }
            (placa.background as GradientDrawable).cornerRadius = dp(o.corners.dp + p)
            placa.translationX = dp(StripHome.MARGEM - p); placa.translationY = dp(topo - p)
            placa.visibility = View.VISIBLE
        } else placa.visibility = View.GONE
    }

    /** O jeito de cards do mundo [cat] (o dele, se tiver; senão o do Ajuste fino). A [HomeCruz] usa para as teclas da grade. */
    fun formato(cat: CruzLogica.Categoria): StripHome.Format = opcoesDe(cat).format

    private fun opcoesDe(cat: CruzLogica.Categoria) = StripHome.doMundo(opcoes, WorldOptions.of(context, cat).cards)

    /**
     * "Só o escolhido" (Projectivy, layout minimalista): o card em foco tem a placa e a arte; os outros ficam só o ícone,
     * menor e mais apagado, sem placa. Troca na hora o que aparece e devolve a escala (quem chama anima).
     */
    private fun soEscolhido(k: Card, escolhido: Boolean): Float {
        val arte = escolhido && (k.temBanner || k.conteudo)
        k.fundo.alpha = if (escolhido) 255 else 0
        k.banner.visibility = if (arte) View.VISIBLE else View.GONE
        k.proprio.visibility = if (arte) View.INVISIBLE else View.VISIBLE
        k.rotulo?.visibility = if (escolhido) View.VISIBLE else View.GONE
        k.proprio.alpha = if (escolhido) 1f else 0.7f
        // sem placa, o ícone sozinho fica do tamanho de um logo (na placa ele divide o card com o nome)
        val ic = if (escolhido) 1f else SO_ICONE
        k.icone.scaleX = ic; k.icone.scaleY = ic
        return if (escolhido) 1f else SO_OUTROS
    }

    /** Cor da placa de um card nosso ([own]); [aceso] = escolhido com o foco na linha (o vidro claro clareia um pouco). */
    private fun placaPropria(own: StripHome.Own?, aceso: Boolean): Int = when (own) {
        StripHome.Own.GLASS -> android.graphics.Color.argb(if (aceso) 66 else 40, 255, 255, 255)
        StripHome.Own.DARK -> (DesignTokens.NIGHT and 0x00FFFFFF) or (150 shl 24)
        else -> DesignTokens.PAPER
    }

    /**
     * Card nosso. A placa: o vidro claro clareia no escolhido; com a personalidade "Brilho da cor" o card em foco acende
     * na cor do item (fundo e borda). O ícone: cada um na cor dele (a cruz era assim: "a cor é de cada ícone, sempre");
     * só na placa BRANCA o traço claro da Linha/Símbolo leva tinta escura, senão some (o escolhido na cor da marca).
     * No "só o escolhido" os outros ficam sem placa, sobre a foto: aí o traço volta ao dele.
     * Por que não tingir mais no vidro (25/11/2026): a tinta única deixou todos os ícones laranja e iguais (Paulo:
     * "senti falta de personalidade").
     */
    private fun tingir(k: Card, escolhido: Boolean, focada: Boolean) {
        val own = k.own ?: return
        val aceso = escolhido && focada
        val cor = corDoItem(k)
        val brilha = aceso && cor != null && opcoesDesenhadas?.mood == StripHome.Mood.GLOW
        k.fundo.setColor(if (brilha) placaAcesa(own, cor!!) else placaPropria(own, aceso))
        bordaPropria(k.fundo, own, if (brilha) cor else null)
        val tinta = own == StripHome.Own.WHITE && (k.icone.drawable as? IconeCruz)?.estilo in TRACO_DE_UMA_COR &&
            !(formatoDesenhado == StripHome.Format.ONLY && !escolhido)
        if (!tinta) { if (k.icone.layerType != View.LAYER_TYPE_NONE) k.icone.setLayerType(View.LAYER_TYPE_NONE, null); return }
        val c = if (aceso) DesignTokens.ORANGE_ON_LIGHT else DesignTokens.NIGHT
        // A tinta vai na camada da view, não no Drawable: o Símbolo desenha por um IconeApp interno, com pincéis
        // próprios, e um colorFilter no Drawable não chegava nele (Sala, 25/11/2026).
        k.icone.setLayerType(View.LAYER_TYPE_HARDWARE, android.graphics.Paint().apply {
            colorFilter = android.graphics.PorterDuffColorFilter(c, android.graphics.PorterDuff.Mode.SRC_IN)
        })
    }

    /** A cor do item (a do desenho dele na cruz), ou null quando o ícone não é nosso. */
    private fun corDoItem(k: Card): Int? = (k.item.icone as? IconeCruz)?.let { IconeCruz.corLinha(it.tipo) }

    /** Placa do card em foco com "Brilho da cor": a do jeito, puxada para a cor do item. */
    private fun placaAcesa(own: StripHome.Own, cor: Int): Int = when (own) {
        StripHome.Own.DARK -> (mistura(DesignTokens.NIGHT, cor, BRILHO_ESCURO) and 0x00FFFFFF) or (200 shl 24)
        StripHome.Own.GLASS -> (cor and 0x00FFFFFF) or (84 shl 24)
        StripHome.Own.WHITE -> mistura(DesignTokens.PAPER, cor, BRILHO_BRANCO)
    }

    /** Borda fina do vidro; no card aceso com brilho, na cor do item. */
    private fun bordaPropria(f: GradientDrawable, own: StripHome.Own, cor: Int?) {
        val c = when {
            cor != null -> (cor and 0x00FFFFFF) or (150 shl 24)
            own == StripHome.Own.GLASS -> android.graphics.Color.argb(72, 255, 255, 255)
            own == StripHome.Own.DARK -> android.graphics.Color.argb(26, 255, 255, 255)
            else -> 0
        }
        f.setStroke(if (c == 0) 0 else dpi(1f), c)
    }

    /**
     * O ícone do card nosso: no Azulejo, a peça das Configurações; o Símbolo (sem animação própria) vira a Linha, que
     * tem o mesmo traço de uma cor e se mexe. Um objeto por card, para cada um ter o seu tempo de animação.
     */
    private fun iconeProprio(d: android.graphics.drawable.Drawable?, mood: StripHome.Mood): android.graphics.drawable.Drawable? {
        val c = d as? IconeCruz ?: return d
        val e = when {
            mood == StripHome.Mood.TILE -> IconeCruz.Estilo.AZULEJO
            c.estilo == IconeCruz.Estilo.SIMBOLO -> IconeCruz.Estilo.LINHA
            else -> c.estilo
        }
        return IconeCruz(c.tipo, e)
    }

    /**
     * Liga a animação do ícone (a mesma que a cruz tocava): o escolhido, com a linha em foco; no "Brilho da cor" os
     * outros visíveis respiram devagar, um depois do outro. O nível de movimento da HOME manda (PARADO = nenhum).
     */
    private fun moverIconeDoCard(k: Card, escolhido: Boolean, ativa: Boolean, distancia: Int, o: StripHome.Options) {
        val ic = k.icone.drawable as? IconeCruz ?: return
        val descEsc = MovimentoAdaptativo.descansoEscolhido(nivel)
        val descCat = MovimentoAdaptativo.descansoCategoria(nivel)
        when {
            !ativa || !isShown -> ic.animar(false)
            escolhido -> ic.animar(descEsc != null, descEsc ?: IconeCruz.DESCANSO_ESCOLHIDO_MS)
            k.own != null && o.mood == StripHome.Mood.GLOW && descCat != null ->
                ic.animar(true, descCat, MovimentoAdaptativo.atrasoCategoria(nivel, distancia))
            else -> ic.animar(false)
        }
    }

    /** O texto do mundo mudou (o que tem agora); a linha fica onde está. */
    fun atualizarTexto(cats: List<HomeCruz.Categoria>, cat: Int) { cats.getOrNull(cat)?.let { escreverDesc(it) } }

    // O card em foco: a descrição fala dele (Paulo, 24/11/2026: "o que aparece deveria ser relacionado a cada card").
    private var selDesc = 0
    private var focadaDesc = false

    private fun escreverDesc(c: HomeCruz.Categoria) {
        if (System.currentTimeMillis() < avisoAte) return
        val fixa = fixa(c.cat)
        val itens = c.itens.map { StripHome.Itens(it.id, it.nome, it.sub, it.situacao) }
        val viva = if (opcoes.desc != StripHome.Desc.LIVE) null
            else if (focadaDesc) itens.getOrNull(selDesc)?.let { StripHome.doCard(it) }
            else StripHome.viva(c.cat, itens) { n -> context.getString(R.string.strip_apps_count, n) }
        val t = viva ?: fixa
        val sb = SpannableStringBuilder()
        for ((p, negrito) in StripHome.partes(t)) {
            val ini = sb.length
            sb.append(p)
            if (negrito) {
                sb.setSpan(TypefaceSpan(forte), ini, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(DesignTokens.CREAM), ini, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        if (desc.text.toString() != sb.toString()) desc.text = sb
        val cabe = (largura() - StripHome.MARGEM * 2 - StripHome.ICONE - 40f - nome.paint.measureText(c.nome) / dm.density).coerceAtLeast(120f)
        desc.maxWidth = dpi(cabe)
        val vazio = sb.isEmpty()
        barrinha.visibility = if (vazio) View.GONE else View.VISIBLE
    }

    private fun fixa(k: CruzLogica.Categoria): String = context.getString(when (k) {
        CruzLogica.Categoria.ASSISTIR -> R.string.strip_desc_watch
        CruzLogica.Categoria.OUVIR -> R.string.strip_desc_listen
        CruzLogica.Categoria.JOGAR -> R.string.strip_desc_play
        CruzLogica.Categoria.APLICATIVOS -> R.string.strip_desc_apps
        CruzLogica.Categoria.FERRAMENTAS -> R.string.strip_desc_tools
        CruzLogica.Categoria.CONTINUAR -> R.string.cruz_continuar
    })

    /** "Não deu para abrir": vira a descrição por uns segundos (o leitor de tela fala pela HomeCruz). */
    fun aviso(texto: String, ms: Long) {
        avisoAte = System.currentTimeMillis() + ms
        desc.text = texto
        desc.setTextColor(Estilo.ACENTO)
        barrinha.visibility = View.VISIBLE
        postDelayed({ avisoAte = 0L; desc.setTextColor(desc2); requestLayout() }, ms)
    }

    /** O card escolhido "aperta" e volta: o OK foi entendido. */
    fun apertar(sel: Int) {
        val k = cards.getOrNull(sel)?.raiz ?: return
        if (semAnimacao()) return
        k.animate().cancel()
        k.pivotX = k.width / 2f; k.pivotY = k.height / 2f
        k.animate().scaleX(0.94f).scaleY(0.94f).setDuration(br.paulo.tvlauncher.DesignTokens.MS_MICRO).withEndAction {
            k.animate().scaleX(1f).scaleY(1f).setDuration(br.paulo.tvlauncher.DesignTokens.MS_FAST).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
        }.start()
    }

    // ------------------------------------------------------------------ cards

    private fun larguraCard(k: Card, comNome: Boolean, o: StripHome.Options): Float {
        if (!comNome) return o.cardW
        val folga = if (o.format == StripHome.Format.WIDE) 16f else 12f
        return o.cardW + k.nomeFoco.paint.measureText(k.item.nome) / dm.density + folga
    }

    private fun refazerCards(c: HomeCruz.Categoria, o: StripHome.Options) {
        for (k in cards) { (k.icone.drawable as? IconeCruz)?.animar(false); k.raiz.animate().cancel(); removeView(k.raiz); k.fora?.let { it.animate().cancel(); removeView(it) } }
        cards.clear()
        val fmt = o.format
        for (item in c.itens) {
            val raiz = FrameLayout(context)
            val app = ehApp(item.id)
            val conteudo = item.id.startsWith("card:")
            // Card nosso (TV, Séries, Filmes, Rede...): placa clara como a dos apps, com o ícone na linha do nome
            // (Paulo, 25/11/2026: o marrom "tinha um destaque feio"; ícone alinhado à descrição, igual ao card do YouTube).
            val own = if (!app && !conteudo && fmt != StripHome.Format.LOGO) o.own else null
            val claro = own == StripHome.Own.WHITE
            val fundo = GradientDrawable().apply {
                cornerRadius = dp(o.raio)
                setColor(when {
                    conteudo -> DesignTokens.NIGHT
                    fmt == StripHome.Format.LOGO -> 0   // logo solto: sem placa
                    app -> fundoDoIcone(item.icone)
                    else -> placaPropria(own, false)
                })
            }
            if (own == StripHome.Own.GLASS) fundo.setStroke(dpi(1f), android.graphics.Color.argb(72, 255, 255, 255))
            if (own == StripHome.Own.DARK) fundo.setStroke(dpi(1f), android.graphics.Color.argb(26, 255, 255, 255))
            raiz.background = fundo
            raiz.clipToOutline = true
            val banner = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
            raiz.addView(banner, LayoutParams(dpi(o.cardW), LayoutParams.MATCH_PARENT))
            val lado = own != null && fmt.rotuloDentro   // ícone ao lado do nome
            val proprio = LinearLayout(context).apply {
                orientation = if (lado) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL; gravity = Gravity.CENTER
            }
            val rotulo = if (!fmt.rotuloDentro) null else texto(ROTULO_SP, forte, if (claro) DesignTokens.NIGHT else DesignTokens.CREAM).apply {
                setShadowLayer(0f, 0f, 0f, 0)   // dentro do card não leva a sombra da linha do mundo
                includeFontPadding = false; gravity = Gravity.CENTER; text = item.nome
                breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
            }
            // Nome que não cabe numa linha ("Dormir em 30 min") vai inteiro em duas, com o ícone menor: nunca "Dormir em 30 m…".
            // Uma palavra só que não cabe ("Configurações" na grade) não se parte ao meio: a letra diminui até caber.
            val cabe = dp(o.cardW - 12f - (if (lado) o.cardH * ICONE_LADO + LADO_VAO else 0f))
            val largo = rotulo != null && rotulo.paint.measureText(item.nome) > cabe
            val umaPalavra = !item.nome.trim().contains(' ')
            if (largo && umaPalavra) rotulo?.let { it.textSize = ROTULO_SP * maxOf(0.75f, cabe / it.paint.measureText(item.nome)) }   // em sp
            val duasLinhas = largo && !umaPalavra
            rotulo?.apply { maxLines = if (duasLinhas) 2 else 1; ellipsize = TextUtils.TruncateAt.END }
            val tamIcone = o.cardH * when {
                fmt == StripHome.Format.LOGO -> 0.8f
                fmt == StripHome.Format.ROUND -> 0.6f
                lado -> ICONE_LADO
                rotulo == null -> 0.56f
                duasLinhas -> 0.36f
                else -> 0.48f
            }
            // A capa de um card de conteúdo é UM Drawable: em duas ImageViews, os limites da menor ganhavam e a capa saía
            // num cantinho (Sala, 24/11/2026). Ela fica só no banner.
            val ic = ImageView(context).apply { setImageDrawable(if (conteudo) null else if (own != null) iconeProprio(item.icone, o.mood) else item.icone); scaleType = ImageView.ScaleType.FIT_CENTER }
            proprio.addView(ic, LinearLayout.LayoutParams(dpi(tamIcone), dpi(tamIcone)))
            if (rotulo != null && lado) {
                rotulo.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                rotulo.maxWidth = cabe.toInt()
                proprio.addView(rotulo, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(LADO_VAO) })
            } else if (rotulo != null) proprio.addView(rotulo, LinearLayout.LayoutParams(dpi(o.cardW - 12f), -2).apply { topMargin = dpi(3f) })
            raiz.addView(proprio, LayoutParams(dpi(o.cardW), LayoutParams.MATCH_PARENT))
            // Card de conteúdo (capa de filme/episódio de dentro do app, AppChannels): a capa ocupa o card inteiro.
            if (conteudo) {
                banner.scaleType = if (fmt == StripHome.Format.ROUND || o.cardW < o.cardH * 1.5f) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_XY
                banner.setImageDrawable(item.icone); banner.visibility = View.VISIBLE
                proprio.visibility = View.INVISIBLE
            }
            val nomeFoco = texto(15f, forte, if (claro) DesignTokens.NIGHT else DesignTokens.CREAM).apply {
                setShadowLayer(0f, 0f, 0f, 0)
                maxLines = 1; includeFontPadding = false; gravity = Gravity.CENTER_VERTICAL; text = item.nome
                visibility = View.GONE
            }
            raiz.addView(nomeFoco, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply { marginStart = dpi(o.cardW) })
            addView(raiz, indexOfChild(anel), LayoutParams(dpi(o.cardW), dpi(o.cardH)))
            // Redondo: o nome fica embaixo do círculo, sempre à vista (como os apps de um celular).
            val fora = if (!fmt.nomeEmbaixo) null else texto(12f, media, desc2).apply {
                gravity = Gravity.CENTER_HORIZONTAL; text = item.nome; ellipsize = TextUtils.TruncateAt.END
                alpha = 0f
            }
            fora?.let { addView(it, indexOfChild(anel), LayoutParams(dpi(o.cardW + NOME_FORA_SOBRA), LayoutParams.WRAP_CONTENT)) }
            cards += Card(item, raiz, banner, proprio, ic, nomeFoco, rotulo, fora, fundo, conteudo, own)
            tingir(cards.last(), escolhido = false, focada = false)
        }
    }

    private fun pedirBanner(k: Card, o: StripHome.Options) {
        if (k.pediu || !o.format.banner || !ehApp(k.item.id)) return
        k.pediu = true
        val f = bannerDe ?: return
        f(k.item) { d ->
            if (d == null || !cards.contains(k)) return@f
            k.banner.setImageDrawable(d)
            k.temBanner = true
            // "Só o escolhido": o banner só aparece no card em foco (o próximo passo arruma; aqui não mexe nos outros)
            if (formatoDesenhado == StripHome.Format.ONLY && k.fundo.alpha == 0) return@f
            // Logo solto: o banner é o logo, inteiro e sem corte, sem placa atrás
            if (formatoDesenhado == StripHome.Format.LOGO) k.banner.scaleType = ImageView.ScaleType.FIT_CENTER
            k.banner.visibility = View.VISIBLE
            k.proprio.visibility = View.INVISIBLE
        }
    }

    private fun ehApp(id0: String) = id0.removePrefix(PREFIXO_CONTINUAR).let { id ->
        id.startsWith("app:") || id.startsWith("c:jogo:") || id.startsWith("c:midia:") }

    /** O fundo do card de um app sem banner: a cor média do ícone, escurecida (o logo aparece como numa placa da marca). */
    private fun fundoDoIcone(d: Drawable): Int = try {
        val b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val antes = d.bounds
        d.setBounds(0, 0, 8, 8); d.draw(cv); d.bounds = antes
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            val p = b.getPixel(x, y)
            val a = p ushr 24
            if (a < 128) continue
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF; n++
        }
        b.recycle()
        if (n == 0L) mistura(DesignTokens.CREAM, DesignTokens.NIGHT, 0.85f)
        else mistura((0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt(), DesignTokens.NIGHT, 0.55f)
    } catch (e: Exception) { mistura(DesignTokens.CREAM, DesignTokens.NIGHT, 0.85f) }

    /** [a] misturada em [b]: [quantoB] = 0 só a, 1 só b. */
    private fun mistura(a: Int, b: Int, quantoB: Float): Int {
        fun c(s: Int) = (((a shr s) and 0xFF) * (1 - quantoB) + ((b shr s) and 0xFF) * quantoB).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (c(16) shl 16) or (c(8) shl 8) or c(0)
    }

    private fun semAnimacao() = runCatching {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    /** Para o teste de tela. */
    internal fun linhaDoMundo(): View = linha
    internal fun descricao(): TextView = desc
    internal fun cardsVisiveis(): List<View> = cards.map { it.raiz }.filter { it.alpha > 0.5f }
    internal fun anel(): View = anel
}
