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

    private val dm = resources.displayMetrics
    private fun dp(v: Float) = v * dm.density
    private fun dpi(v: Float) = (v * dm.density + 0.5f).toInt()
    private val curva = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    private val forte = Fontes.manrope(ctx, true)
    private val media = Fontes.manrope(ctx, false)
    private val desc2 = 0xBDFFF3E6.toInt()   // creme 74%

    private val trilho = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
    private var trilhoDe: List<CruzLogica.Categoria> = emptyList()
    private companion object {
        const val TRILHO_ICONE = 16f; const val TRILHO_VAO = 6f; const val TRILHO_OUTROS = 0.75f
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
                       val fundo: GradientDrawable, val conteudo: Boolean) {
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
    private var linhaAnimando = false
    /** O último desenho, para refazer no lugar quando a tela muda de tamanho (antes de medir vale 960 x 540). */
    private var ultimo: (() -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w > 0 && h > 0 && (w != ow || h != oh)) post { ultimo?.invoke() }
    }

    /** O ícone vivo só anda com a HOME na tela (um app na frente ou a cruz no lugar da barra: para). */
    @Suppress("SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL")
    private fun moverIcone() { if (icone == null) return; (icone.drawable as? WorldMotion)?.let { if (isShown && windowVisibility == View.VISIBLE) it.start() else it.stop() } }
    override fun onWindowVisibilityChanged(v: Int) { super.onWindowVisibilityChanged(v); moverIcone() }
    override fun onVisibilityAggregated(v: Boolean) { super.onVisibilityAggregated(v); moverIcone() }
    override fun onDetachedFromWindow() { (icone.drawable as? WorldMotion)?.stop(); super.onDetachedFromWindow() }

    init {
        clipChildren = false; clipToPadding = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(trilho, LayoutParams(dpi(TRILHO_ICONE), LayoutParams.WRAP_CONTENT))
        linha.addView(icone, LinearLayout.LayoutParams(dpi(StripHome.ICONE), dpi(StripHome.ICONE)))
        linha.addView(nome, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(12f) })
        linha.addView(barrinha, LinearLayout.LayoutParams(dpi(1.5f).coerceAtLeast(2), dpi(20f)).apply { marginStart = dpi(14f); marginEnd = dpi(14f) })
        linha.addView(desc, LinearLayout.LayoutParams(-2, -2))
        icone.elevation = 0f
        addView(linha, LayoutParams(-2, dpi(StripHome.LINHA_A)))
        placa.background = GradientDrawable().apply { setColor(0x8C141628.toInt()); setStroke(dpi(1f), 0x1FFFF3E6) }
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
        for (i in 0 until trilho.childCount) trilho.getChildAt(i).let { v ->
            val atual = i == cat
            val e = if (atual) 1f else TRILHO_OUTROS
            if (anima) v.animate().scaleX(e).scaleY(e).alpha(if (atual) 1f else 0.38f).setDuration(StripHome.MS).setInterpolator(curva).start()
            else { v.scaleX = e; v.scaleY = e; v.alpha = if (atual) 1f else 0.38f }
        }
        // Cards grandes (ou com placa, ou a grade de 2 linhas) não encostam no pé da tela nem na linha do mundo: sobem o que
        // precisar. [topo] = alto do bloco de cards (o card em foco sobe mais 4 dp).
        val folgaPlaca = if (o.glass) StripHome.PLACA else 0f
        val topo = minOf(altura() - StripHome.CARDS_DO_FUNDO - o.cardH / 2f, altura() - 16f - folgaPlaca - o.blocoH)
        val topoLinha = minOf(altura() - StripHome.LINHA_DO_FUNDO, topo - folgaPlaca - StripHome.SUBIR_FOCO - 14f - StripHome.LINHA_A)
        // o trilho fica centrado na altura da linha do mundo
        val trilhoA = cats.size * TRILHO_ICONE + (cats.size - 1) * TRILHO_VAO
        trilho.translationX = dp(StripHome.TRILHO_X + 2f - TRILHO_ICONE / 2f); trilho.translationY = dp(topoLinha + StripHome.LINHA_A / 2f - trilhoA / 2f)
        trilho.visibility = if (cats.size > 1) View.VISIBLE else View.INVISIBLE
        linha.translationX = dp(StripHome.MARGEM)

        // linha do mundo
        val ids = c.itens.map { it.id }
        val mundoNovo = cat != mundoDesenhado || ids != idsDesenhados
        val trocouMundo = cat != mundoDesenhado
        if (trocouMundo) {
            // Ícone vivo (24/11/2026): desenha o traço e respira; sem movimento permitido, o parado.
            (icone.drawable as? WorldMotion)?.stop()
            icone.setImageDrawable(if (WorldMotion.permitido(context)) WorldMotion(c.cat, DesignTokens.CREAM).also { if (isShown) it.start() }
                else WorldGlyph(c.cat, DesignTokens.CREAM))
            nome.text = c.nome
        }
        escreverDesc(c)
        if (trocouMundo && anima && direcao != 0) {
            linha.animate().cancel()
            linha.alpha = 0f; linha.translationY = dp(topoLinha + 8f * direcao)
            linhaAnimando = true
            linha.animate().alpha(1f).translationY(dp(topoLinha)).setDuration(StripHome.MS).setInterpolator(curva)
                .withEndAction { linhaAnimando = false }.start()
        } else if (!linhaAnimando) { linha.alpha = 1f; linha.translationY = dp(topoLinha) }

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
        return if (escolhido) 1f else SO_OUTROS
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
        k.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70L).withEndAction {
            k.animate().scaleX(1f).scaleY(1f).setDuration(210L).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
        }.start()
    }

    // ------------------------------------------------------------------ cards

    private fun larguraCard(k: Card, comNome: Boolean, o: StripHome.Options): Float {
        if (!comNome) return o.cardW
        val folga = if (o.format == StripHome.Format.WIDE) 16f else 12f
        return o.cardW + k.nomeFoco.paint.measureText(k.item.nome) / dm.density + folga
    }

    private fun refazerCards(c: HomeCruz.Categoria, o: StripHome.Options) {
        for (k in cards) { k.raiz.animate().cancel(); removeView(k.raiz); k.fora?.let { it.animate().cancel(); removeView(it) } }
        cards.clear()
        val fmt = o.format
        val corMundo = BottomBar.cor(c.cat)
        for (item in c.itens) {
            val raiz = FrameLayout(context)
            val app = ehApp(item.id)
            val conteudo = item.id.startsWith("card:")
            val fundo = GradientDrawable().apply {
                cornerRadius = dp(o.raio)
                setColor(when {
                    conteudo -> DesignTokens.NIGHT
                    fmt == StripHome.Format.LOGO -> 0   // logo solto: sem placa
                    app -> fundoDoIcone(item.icone)
                    else -> mistura(corMundo, DesignTokens.NIGHT, 0.78f)
                })
            }
            raiz.background = fundo
            raiz.clipToOutline = true
            val banner = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
            raiz.addView(banner, LayoutParams(dpi(o.cardW), LayoutParams.MATCH_PARENT))
            val proprio = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            val rotulo = if (!fmt.rotuloDentro) null else texto(11f, forte, DesignTokens.CREAM).apply {
                setShadowLayer(0f, 0f, 0f, 0)   // dentro do card não leva a sombra da linha do mundo
                includeFontPadding = false; gravity = Gravity.CENTER; text = item.nome
                breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
            }
            // Nome que não cabe numa linha ("Dormir em 30 min") vai inteiro em duas, com o ícone menor: nunca "Dormir em 30 m…".
            val duasLinhas = rotulo != null && rotulo.paint.measureText(item.nome) > dp(o.cardW - 12f)
            rotulo?.apply { maxLines = if (duasLinhas) 2 else 1; ellipsize = TextUtils.TruncateAt.END }
            val tamIcone = o.cardH * when {
                fmt == StripHome.Format.LOGO -> 0.8f
                fmt == StripHome.Format.ROUND -> 0.6f
                rotulo == null -> 0.56f
                duasLinhas -> 0.36f
                else -> 0.48f
            }
            // A capa de um card de conteúdo é UM Drawable: em duas ImageViews, os limites da menor ganhavam e a capa saía
            // num cantinho (Sala, 24/11/2026). Ela fica só no banner.
            val ic = ImageView(context).apply { setImageDrawable(if (conteudo) null else item.icone); scaleType = ImageView.ScaleType.FIT_CENTER }
            proprio.addView(ic, LinearLayout.LayoutParams(dpi(tamIcone), dpi(tamIcone)))
            if (rotulo != null) proprio.addView(rotulo, LinearLayout.LayoutParams(dpi(o.cardW - 12f), -2).apply { topMargin = dpi(3f) })
            raiz.addView(proprio, LayoutParams(dpi(o.cardW), LayoutParams.MATCH_PARENT))
            // Card de conteúdo (capa de filme/episódio de dentro do app, AppChannels): a capa ocupa o card inteiro.
            if (conteudo) {
                banner.scaleType = if (fmt == StripHome.Format.ROUND || o.cardW < o.cardH * 1.5f) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_XY
                banner.setImageDrawable(item.icone); banner.visibility = View.VISIBLE
                proprio.visibility = View.INVISIBLE
            }
            val nomeFoco = texto(15f, forte, DesignTokens.CREAM).apply {
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
            cards += Card(item, raiz, banner, proprio, ic, nomeFoco, rotulo, fora, fundo, conteudo)
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
