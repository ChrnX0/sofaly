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
    private val linha = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false; clipToPadding = false }
    private val icone = ImageView(ctx)
    private val nome = texto(StripHome.FONTE_NOME, forte, DesignTokens.CREAM)
    private val barrinha = View(ctx).apply { background = GradientDrawable().apply { cornerRadius = dp(1f); setColor(0x59FFF3E6) } }
    private val desc = texto(StripHome.FONTE_DESC, media, desc2).apply { ellipsize = TextUtils.TruncateAt.END }
    private val placa = View(ctx)
    private val anel = View(ctx)

    private class Card(val item: HomeCruz.Item, val raiz: FrameLayout, val banner: ImageView, val proprio: LinearLayout,
                       val icone: ImageView, val nomeFoco: TextView) {
        var pediu = false
        var temBanner = false
    }
    private val cards = ArrayList<Card>()
    private var mundoDesenhado = -1
    private var idsDesenhados: List<String> = emptyList()
    private var avisoAte = 0L
    private var linhaAnimando = false
    /** O último desenho, para refazer no lugar quando a tela muda de tamanho (antes de medir vale 960 x 540). */
    private var ultimo: (() -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w > 0 && h > 0 && (w != ow || h != oh)) post { ultimo?.invoke() }
    }

    init {
        clipChildren = false; clipToPadding = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(trilho, LayoutParams(dpi(4f), LayoutParams.WRAP_CONTENT))
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
        val c = cats.getOrNull(cat) ?: return
        val anima = animar && !semAnimacao()
        val o = opcoes
        // trilho: um ponto por mundo, o atual comprido
        if (trilho.childCount != cats.size) {
            trilho.removeAllViews()
            repeat(cats.size) {
                trilho.addView(View(context).apply { background = GradientDrawable().apply { cornerRadius = dp(2f); setColor(DesignTokens.CREAM) } },
                    LinearLayout.LayoutParams(dpi(4f), dpi(4f)).apply { topMargin = if (it > 0) dpi(4f) else 0 })
            }
        }
        for (i in 0 until trilho.childCount) trilho.getChildAt(i).let { v ->
            val lp = v.layoutParams as LinearLayout.LayoutParams
            val h = dpi(if (i == cat) 10f else 4f)
            if (lp.height != h) { lp.height = h; v.layoutParams = lp }
            v.alpha = if (i == cat) 1f else 0.3f
        }
        val topoLinha = altura() - StripHome.LINHA_DO_FUNDO
        // o trilho fica centrado na altura da linha do mundo (10 dp o atual, 4 dp os outros, 4 dp entre eles)
        val trilhoA = 10f + (cats.size - 1) * 8f
        trilho.translationX = dp(StripHome.TRILHO_X); trilho.translationY = dp(topoLinha + StripHome.LINHA_A / 2f - trilhoA / 2f)
        trilho.visibility = if (cats.size > 1) View.VISIBLE else View.INVISIBLE
        linha.translationX = dp(StripHome.MARGEM)

        // linha do mundo
        val ids = c.itens.map { it.id }
        val mundoNovo = cat != mundoDesenhado || ids != idsDesenhados
        val trocouMundo = cat != mundoDesenhado
        if (trocouMundo) {
            icone.setImageDrawable(WorldGlyph(c.cat, DesignTokens.CREAM))
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
        if (mundoNovo) refazerCards(c)
        mundoDesenhado = cat; idsDesenhados = ids
        val s = sel.coerceIn(0, (cards.size - 1).coerceAtLeast(0))
        val larguras = cards.mapIndexed { i, k -> larguraCard(k, i == s && o.name) }
        val vao = o.gap.dp
        val (ini, vis) = StripHome.janela(s, larguras, vao, largura() - 2 * StripHome.MARGEM)
        val centro = altura() - StripHome.CARDS_DO_FUNDO
        // x de cada card: o 1º visível começa na margem; os de antes ficam à esquerda, fora (a linha desliza)
        val xs = FloatArray(cards.size)
        var x = StripHome.MARGEM
        for (i in ini until cards.size) { xs[i] = x; x += larguras[i] + vao }
        x = StripHome.MARGEM
        for (i in ini - 1 downTo 0) { x -= larguras[i] + vao; xs[i] = x }
        val semMenu = hasWindowFocus() || br.paulo.tvlauncher.FineTuneActivity.aberto
        for ((i, k) in cards.withIndex()) {
            val escolhido = i == s
            val visivel = i - ini in 0 until vis
            val w = larguras[i]
            val lp = k.raiz.layoutParams
            if (lp.width != dpi(w)) { lp.width = dpi(w); k.raiz.layoutParams = lp }
            k.nomeFoco.visibility = if (escolhido && o.name) View.VISIBLE else View.GONE
            val sobe = if (escolhido && focada) StripHome.SUBIR_FOCO else 0f
            val ty = dp(centro - o.cardH / 2f - sobe)
            val alfa = if (visivel) 1f else 0f
            k.raiz.elevation = dp(if (escolhido && focada) 10f else 4f)
            if (anima && !mundoNovo) {
                k.raiz.animate().cancel()
                k.raiz.animate().translationX(dp(xs[i])).translationY(ty).alpha(alfa).setDuration(StripHome.MS).setInterpolator(curva).start()
            } else if (!(anima && mundoNovo)) {
                k.raiz.animate().cancel()
                k.raiz.translationX = dp(xs[i]); k.raiz.translationY = ty; k.raiz.alpha = alfa
            } else {
                // mundo novo: os cards entram de baixo, um depois do outro (40 ms)
                k.raiz.animate().cancel()
                k.raiz.translationX = dp(xs[i]); k.raiz.translationY = ty + dp(10f); k.raiz.alpha = 0f
                if (visivel) k.raiz.animate().translationY(ty).alpha(1f).setStartDelay(StripHome.ESCALONA_MS * (i - ini))
                    .setDuration(StripHome.MS).setInterpolator(curva).withEndAction { k.raiz.animate().setStartDelay(0) }.start()
                else k.raiz.translationY = ty
            }
            if (i - ini in -1..vis) pedirBanner(k)
        }
        // anel laranja no escolhido (só com o foco na linha e nenhuma janela por cima)
        cards.getOrNull(s)?.let { k ->
            val a = StripHome.ANEL
            val r = o.corners.dp + a
            (anel.background as? GradientDrawable ?: GradientDrawable().also { anel.background = it }).apply {
                cornerRadius = dp(r); setColor(0); setStroke(dpi(a), Estilo.ACENTO)
            }
            val lp = anel.layoutParams
            val aw = dpi(larguras[s] + 2 * a); val ah = dpi(o.cardH + 2 * a)
            if (lp.width != aw || lp.height != ah) { lp.width = aw; lp.height = ah; anel.layoutParams = lp }
            val ax = dp(xs[s] - a); val ay = dp(centro - o.cardH / 2f - StripHome.SUBIR_FOCO - a)
            val ligado = if (focada && semMenu) 1f else 0f
            anel.animate().cancel()
            if (anima && !mundoNovo) anel.animate().translationX(ax).translationY(ay).alpha(ligado).setDuration(StripHome.MS).setInterpolator(curva).start()
            else { anel.translationX = ax; anel.translationY = ay; anel.alpha = if (anima && mundoNovo) 0f else ligado
                if (anima && mundoNovo && ligado > 0f) anel.animate().alpha(1f).setStartDelay(StripHome.ESCALONA_MS * (s - ini)).setDuration(StripHome.MS)
                    .withEndAction { anel.animate().setStartDelay(0) }.start() }
        }
        // placa de vidro atrás dos visíveis
        if (o.glass && vis > 0) {
            val p = StripHome.PLACA
            var total = 0f
            for (i in ini until ini + vis) total += larguras[i] + (if (i > ini) vao else 0f)
            val lp = placa.layoutParams
            val pw = dpi(total + 2 * p); val ph = dpi(o.cardH + 2 * p)
            if (lp.width != pw || lp.height != ph) { lp.width = pw; lp.height = ph; placa.layoutParams = lp }
            (placa.background as GradientDrawable).cornerRadius = dp(o.corners.dp + p)
            placa.translationX = dp(StripHome.MARGEM - p); placa.translationY = dp(centro - o.cardH / 2f - p)
            placa.visibility = View.VISIBLE
        } else placa.visibility = View.GONE
    }

    /** O texto do mundo mudou (o que tem agora); a linha fica onde está. */
    fun atualizarTexto(cats: List<HomeCruz.Categoria>, cat: Int) { cats.getOrNull(cat)?.let { escreverDesc(it) } }

    private fun escreverDesc(c: HomeCruz.Categoria) {
        if (System.currentTimeMillis() < avisoAte) return
        val fixa = fixa(c.cat)
        val viva = if (opcoes.desc == StripHome.Desc.LIVE) StripHome.viva(c.cat,
            c.itens.map { StripHome.Itens(it.id, it.nome, it.sub, it.situacao) }) { n -> context.getString(R.string.strip_apps_count, n) } else null
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

    private fun larguraCard(k: Card, comNome: Boolean): Float {
        val o = opcoes
        if (!comNome) return o.cardW
        val folga = if (o.format == StripHome.Format.WIDE) 16f else 12f
        return o.cardW + k.nomeFoco.paint.measureText(k.item.nome) / dm.density + folga
    }

    private fun refazerCards(c: HomeCruz.Categoria) {
        for (k in cards) { k.raiz.animate().cancel(); removeView(k.raiz) }
        cards.clear()
        val o = opcoes
        val corMundo = BottomBar.cor(c.cat)
        for (item in c.itens) {
            val raiz = FrameLayout(context)
            val app = ehApp(item.id)
            raiz.background = GradientDrawable().apply {
                cornerRadius = dp(o.corners.dp)
                setColor(if (app) fundoDoIcone(item.icone) else mistura(corMundo, DesignTokens.NIGHT, 0.78f))
            }
            raiz.clipToOutline = true
            val banner = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
            raiz.addView(banner, LayoutParams(dpi(o.cardW), LayoutParams.MATCH_PARENT))
            val quad = o.format != StripHome.Format.WIDE
            val proprio = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            val tamIcone = if (quad) o.cardH * 0.56f else o.cardH * 0.48f
            val ic = ImageView(context).apply { setImageDrawable(item.icone); scaleType = ImageView.ScaleType.FIT_CENTER }
            proprio.addView(ic, LinearLayout.LayoutParams(dpi(tamIcone), dpi(tamIcone)))
            if (!quad) proprio.addView(TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); typeface = forte; setTextColor(DesignTokens.CREAM)
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END; includeFontPadding = false; gravity = Gravity.CENTER
                text = item.nome
            }, LinearLayout.LayoutParams(dpi(o.cardW - 12f), -2).apply { topMargin = dpi(3f) })
            raiz.addView(proprio, LayoutParams(dpi(o.cardW), LayoutParams.MATCH_PARENT))
            val nomeFoco = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = forte; setTextColor(DesignTokens.CREAM)
                maxLines = 1; includeFontPadding = false; gravity = Gravity.CENTER_VERTICAL; text = item.nome
                visibility = View.GONE
            }
            raiz.addView(nomeFoco, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply { leftMargin = dpi(o.cardW) })
            addView(raiz, indexOfChild(anel), LayoutParams(dpi(o.cardW), dpi(o.cardH)))
            cards += Card(item, raiz, banner, proprio, ic, nomeFoco)
        }
    }

    private fun pedirBanner(k: Card) {
        if (k.pediu || opcoes.format != StripHome.Format.WIDE || !ehApp(k.item.id)) return
        k.pediu = true
        val f = bannerDe ?: return
        f(k.item) { d ->
            if (d == null || !cards.contains(k)) return@f
            k.banner.setImageDrawable(d)
            k.banner.visibility = View.VISIBLE
            k.proprio.visibility = View.INVISIBLE
            k.temBanner = true
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
