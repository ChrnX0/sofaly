package br.paulo.tvlauncher.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import br.paulo.tvlauncher.DesignTokens
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.settings.Fontes

/**
 * O desenho das "Fileiras" ([RowsHome], clone do Projectivy): uma fileira por categoria, o card em foco preso à
 * esquerda, o nome dele grande embaixo, a sombra sobre o fundo e, no alto à direita, a pílula (Wi-Fi e engrenagem) e a
 * hora. A [HomeCruz] cuida das teclas e do estado e chama [mostrar] a cada passo.
 *
 * Desempenho: os cards são criados UMA vez quando a lista muda; cada passo só anima translationX/Y, escala e alpha.
 * Os banners chegam pelo [bannerDe] só para os cards perto da tela.
 * Referências: fotos do Projectivy 4.74 na Sala (estudo de 26/11/2026: home, linha2, foco no 2º card, cartões e
 * categorias); Apple TV e Google TV no Mobbin (fileiras com título acima, a de baixo aparecendo).
 */
class RowsLayer(ctx: Context) : FrameLayout(ctx) {

    var opcoes: RowsHome.Options = RowsHome.DEFAULT
        set(v) { if (field != v) { field = v; idsDesenhados = emptyList(); aplicarTopo() } }
    /** Banner de TV de um item (só apps); responde depois, na thread principal. null = sem banner, fica o ícone. */
    var bannerDe: ((HomeCruz.Item, (Drawable?) -> Unit) -> Unit)? = null
    /** A engrenagem da pílula do alto foi apertada (abre as Configurações). */
    var aoEngrenagem: (() -> Unit)? = null
    var nivel: MovimentoAdaptativo.Nivel = MovimentoAdaptativo.Nivel.COMPLETO

    private val dm = resources.displayMetrics
    private fun dp(v: Float) = v * dm.density
    private fun dpi(v: Float) = (v * dm.density + 0.5f).toInt()
    /** Curva medida no Projectivy (0.12, 1, 0.36, 1): sai rápido e assenta devagar. */
    private val curva = PathInterpolator(0.12f, 1f, 0.36f, 1f)

    private val sombraEsq = View(ctx)
    private val sombraBaixo = View(ctx)
    private val pilula = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val wifi = ImageView(ctx)
    /** A engrenagem da pílula: focável (▲ na 1ª fileira chega nela; ▼ volta para as fileiras). */
    val engrenagem = ImageView(ctx)
    private val hora = TextClock(ctx)
    /** 2ª linha do relógio (bloco 6): data, dia da semana ou os dois. */
    private val data = TextClock(ctx)
    private val nome = TextView(ctx)

    private class Card(val item: HomeCruz.Item, val raiz: FrameLayout, val banner: ImageView, val icone: ImageView,
                       val fundo: GradientDrawable, val cor: Int, val onda: View, val o: RowsHome.Options) {
        var pediu = false
        /** Largura (dp): a proporção de app de TV; vira a de celular quando o app não tem banner. */
        var w = 0f
    }
    /** [o] = as opções da fileira: as gerais com o tamanho de cards do mundo, quando ele tem um (menu do mundo). */
    private class Fileira(val cat: HomeCruz.Categoria, val titulo: LinearLayout, val cards: List<Card>, val o: RowsHome.Options, val linhas: Int = 1) {
        /** Linha (0 = de cima) do card [i] numa grade. */
        fun linhaDe(i: Int) = i % linhas
    }

    /** As opções da fileira do mundo [c]: o tamanho próprio do mundo vence o geral (Projectivy: tamanho por categoria). */
    private fun opcoesDe(c: HomeCruz.Categoria, o: RowsHome.Options): RowsHome.Options =
        WorldOptions.of(context, c.cat).size?.let { RowsHome.Size.entries.getOrNull(it) }?.let { o.copy(size = it) } ?: o
    private val fileiras = ArrayList<Fileira>()
    private var idsDesenhados: List<String> = emptyList()
    private val anel = View(ctx)
    private var brilhoAnim: ValueAnimator? = null
    private var ultimo: (() -> Unit)? = null
    private var avisoAte = 0L
    private var nomeMostrado: String? = null
    private lateinit var topoView: LinearLayout

    init {
        clipChildren = false; clipToPadding = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(sombraEsq, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(sombraBaixo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        anel.alpha = 0f
        addView(anel, LayoutParams(0, 0))
        nome.apply {
            Estilo.letraMostrador(this, RowsHome.NOME_SP)
            typeface = Fontes.nunito(ctx, 500)
            setTextColor(DesignTokens.CREAM)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; includeFontPadding = false
            setShadowLayer(dp(8f), 0f, dp(1f), 0x99000000.toInt())
        }
        addView(nome, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        // pílula do alto: Wi-Fi e engrenagem num vidro escuro; a hora ao lado, grande e fina
        pilula.background = GradientDrawable().apply { cornerRadius = dp(20f); setColor(0x33000000) }
        pilula.setPaddingRelative(dpi(10f), dpi(6f), dpi(10f), dpi(6f))
        wifi.setImageDrawable(WifiGlyph(DesignTokens.CREAM))
        pilula.addView(wifi, LinearLayout.LayoutParams(dpi(22f), dpi(22f)))
        engrenagem.setImageDrawable(IconeCruz(IconeCruz.Tipo.CONFIG, IconeCruz.Estilo.LINHA).apply { corFixa = DesignTokens.CREAM })
        engrenagem.isFocusable = true
        engrenagem.contentDescription = ctx.getString(br.paulo.tvlauncher.R.string.cfg_titulo)
        engrenagem.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0) }
        engrenagem.setPaddingRelative(dpi(2f), dpi(2f), dpi(2f), dpi(2f))
        engrenagem.setOnFocusChangeListener { v, tem ->
            ((v.background as GradientDrawable)).setColor(if (tem) DesignTokens.CREAM else 0)
            (engrenagem.drawable as? IconeCruz)?.corFixa = if (tem) DesignTokens.NIGHT else corDoIcone
            engrenagem.invalidate()
            v.animate().scaleX(if (tem) 1.15f else 1f).scaleY(if (tem) 1.15f else 1f).setDuration(RowsHome.MS_TITULO).start()
        }
        engrenagem.setOnClickListener { aoEngrenagem?.invoke() }
        pilula.addView(engrenagem, LinearLayout.LayoutParams(dpi(26f), dpi(26f)).apply { marginStart = dpi(10f) })
        val topo = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topo.addView(pilula, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        hora.apply {
            format12Hour = "hh:mm"; format24Hour = "HH:mm"
            Estilo.letraMostrador(this, RowsHome.HORA_SP)
            typeface = Fontes.nunito(ctx, 300)
            setTextColor(0xE6FFFFFF.toInt())
            includeFontPadding = false
            setShadowLayer(dp(8f), 0f, dp(1f), 0x80000000.toInt())
        }
        data.apply {
            Estilo.letraMostrador(this, RowsHome.TITULO_SP)
            typeface = Fontes.nunito(ctx, 400)
            setTextColor(DesignTokens.CREAM)
            includeFontPadding = false; gravity = Gravity.END
            setShadowLayer(dp(6f), 0f, dp(1f), 0x80000000.toInt())
        }
        val relogio = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        relogio.addView(hora, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        relogio.addView(data, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(2f) })
        topo.addView(relogio, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(12f) })
        addView(topo, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = dpi(RowsHome.TOPO - 24f); marginEnd = dpi(RowsHome.DIREITA)
        })
        topoView = topo
        aplicarTopo()
    }

    /** Pílula, hora e sombra seguem as opções. */
    private fun aplicarTopo() {
        if (!::topoView.isInitialized) return
        pilula.visibility = if (opcoes.status) View.VISIBLE else View.GONE
        hora.visibility = if (opcoes.clock) View.VISIBLE else View.GONE
        aplicarStatusEHora()
        val a = (opcoes.shadow.alpha * 255).toInt()
        // cor e comprimento da sombra (bloco 5): a cor sem o alfa, o alfa da opacidade escolhida
        val rgb = (if (opcoes.shadowTint == RowsHome.ShadowTint.GRAPHITE) DesignTokens.NIGHT else DesignTokens.NIGHT_DEEP) and 0xFFFFFF
        fun cor(alfa: Int) = (alfa shl 24) or rgb
        sombraEsq.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cor(a), cor((a * 0.45f).toInt()), cor(0)))
        sombraBaixo.background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(cor(a), cor((a * 0.35f).toInt()), cor(0)))
        (sombraEsq.layoutParams as? LayoutParams)?.let { it.width = if (opcoes.shadowW == RowsHome.ShadowLength.FULL) LayoutParams.MATCH_PARENT else (width * opcoes.shadowW.pct).toInt().coerceAtLeast(1); sombraEsq.layoutParams = it }
        (sombraBaixo.layoutParams as? LayoutParams)?.let { it.height = if (opcoes.shadowH == RowsHome.ShadowLength.FULL) LayoutParams.MATCH_PARENT else (height * opcoes.shadowH.pct).toInt().coerceAtLeast(1); it.gravity = Gravity.BOTTOM; sombraBaixo.layoutParams = it }
        sombraEsq.visibility = if (a > 0) View.VISIBLE else View.GONE
        sombraBaixo.visibility = sombraEsq.visibility
    }

    /** Bloco 6: cor, fundo e tamanho dos ícones da pílula; tamanho, formato, cor e 2ª linha da hora; letra do nome. */
    private fun aplicarStatusEHora() {
        val o = opcoes
        val corIcone = when (o.statusColor) { RowsHome.IconColor.CREAM -> DesignTokens.CREAM; RowsHome.IconColor.WHITE -> DesignTokens.FOCUS; RowsHome.IconColor.ACCENT -> Estilo.ACENTO }
        wifi.setImageDrawable(WifiGlyph(corIcone))
        if (!engrenagem.isFocused) (engrenagem.drawable as? IconeCruz)?.corFixa = corIcone
        corDoIcone = corIcone
        (pilula.background as? GradientDrawable)?.setColor(when (o.statusBox) {
            RowsHome.StatusBox.GLASS -> 0x33000000
            RowsHome.StatusBox.DARK -> (0xCC shl 24) or (DesignTokens.NIGHT_DEEP and 0xFFFFFF)
            RowsHome.StatusBox.NONE -> 0
        })
        val ic = dpi(o.statusSize.dp)
        (wifi.layoutParams as? LinearLayout.LayoutParams)?.let { it.width = ic; it.height = ic; wifi.layoutParams = it }
        (engrenagem.layoutParams as? LinearLayout.LayoutParams)?.let { it.width = ic + dpi(4f); it.height = ic + dpi(4f); engrenagem.layoutParams = it }
        val padrao = br.paulo.tvlauncher.settings.ClockStyle.pattern(context)
        hora.format12Hour = padrao; hora.format24Hour = padrao
        hora.setTextColor(br.paulo.tvlauncher.settings.ClockStyle.color(context).argb ?: 0xE6FFFFFF.toInt())
        Estilo.letraMostrador(hora, o.clockSize.sp)
        val esqueleto = RowsHome.line2Skeleton(o.clockLine2)
        if (esqueleto != null && o.clock) {
            val p = android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), esqueleto)
            data.format12Hour = p; data.format24Hour = p; data.visibility = View.VISIBLE
        } else data.visibility = View.GONE
        Estilo.letraMostrador(nome, RowsHome.NOME_SP * o.textSize.k)
        nome.typeface = letra(500)
    }

    /** A letra das Fileiras com negrito e itálico do bloco 6 (a família é a da opção "Letra" do sistema). */
    private fun letra(peso: Int): android.graphics.Typeface {
        val base = Fontes.nunito(context, if (opcoes.bold) 700 else peso)
        return if (opcoes.italic) android.graphics.Typeface.create(base, android.graphics.Typeface.ITALIC) else base
    }

    /** A cor dos ícones da pílula fora do foco (a engrenagem volta para ela). */
    private var corDoIcone = DesignTokens.CREAM

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w != ow || h != oh) post { aplicarTopo() }   // a sombra mede em fração da tela
        if (w > 0 && h > 0 && (w != ow || h != oh)) post { ultimo?.invoke() }
    }

    override fun onDetachedFromWindow() { brilhoAnim?.cancel(); super.onDetachedFromWindow() }

    private fun largura() = if (width > 0) width / dm.density else 960f
    private fun altura() = if (height > 0) height / dm.density else 540f

    // ------------------------------------------------------------------ desenho

    /** Põe tudo no lugar: fileira [cat] de [cats], card [sel] dela. [animar] = false põe direto no lugar. */
    fun mostrar(cats: List<HomeCruz.Categoria>, cat: Int, sel: Int, focada: Boolean, animar: Boolean) {
        ultimo = { mostrar(cats, cat, sel, focada, false) }
        val o = opcoes
        val ids = cats.flatMap { c -> listOf("#" + c.cat.id + c.nome + "/" + WorldOptions.of(context, c.cat).let { "${it.size}x${it.lines}" }) + c.itens.map { it.id } }
        if (ids != idsDesenhados) { refazer(cats, o); idsDesenhados = ids; nomeMostrado = null }
        val anima = animar && !semAnimacao()
        wifi.alpha = if (conectado()) 1f else 0.35f
        // bloco 6: "ocultar ao rolar" (a hora e a pílula só na 1ª fileira)
        if (::topoView.isInitialized) {
            val alvo = if (RowsHome.topoVisivel(o, cat)) 1f else 0f
            if (topoView.alpha != alvo) { if (anima) topoView.animate().alpha(alvo).setDuration(RowsHome.MS).start() else topoView.alpha = alvo }
        }
        val alt = altura(); val larg = largura()
        val semMenu = hasWindowFocus() || br.paulo.tvlauncher.FineTuneActivity.aberto
        // grades (bloco 3): a altura a mais de cada fileira empurra as de baixo
        val extras = FloatArray(fileiras.size) { RowsHome.extraDaGrade(fileiras[it].cards.size, fileiras[it].linhas, fileiras[it].o) }
        for ((j, f) in fileiras.withIndex()) {
            val cy = RowsHome.centroDaFileira(j, cat, alt, o, extras)
            val fo = f.o
            val passoLinha = RowsHome.passoDaLinha(fo)
            val alfa = RowsHome.alfaDaFileira(j, cat, o)
            val naTela = cy + extras[j] > -fo.cardH * 2 && cy < alt + fo.cardH * 2
            val emFoco = j == cat
            val s = if (emFoco) sel else (f.cards.size.let { n -> lembrado(j, n) })
            val xs = RowsHome.xs(f.cards.map { it.w }, s, fo, emFoco, f.linhas)
            // título: centro 72 dp acima do centro dos cards
            val th = if (f.titulo.height == 0) dp(12f) else f.titulo.height / 2f
            val ty = dp(cy - RowsHome.TITULO_ACIMA) - th
            val tAlfa = if (!o.rowTitle || !naTela) 0f else if (emFoco) 1f else alfa
            mover(f.titulo, dp(o.textoX), ty, tAlfa, 1f, anima)
            for ((i, k) in f.cards.withIndex()) {
                val escolhido = emFoco && i == s
                val k1 = if (escolhido) o.focoK else 1f
                val vis = naTela && RowsHome.visivel(xs[i], k.w, fo, larg) && xs[i] + k.w * k1 > 0f
                val a = if (!vis) 0f else if (emFoco) 1f else alfa
                k.raiz.pivotX = 0f; k.raiz.pivotY = dp(fo.cardH) / 2f
                k.raiz.elevation = dp(if (escolhido) (if (o.effect == RowsHome.Effect.SHADOW) 16f else 8f) else 2f)
                mover(k.raiz, dp(xs[i]), dp(cy + f.linhaDe(i) * passoLinha - fo.cardH / 2f), a, k1, anima)
                // ícone dos itens nossos: laranja no card em foco (Paulo, 26/11/2026), a tinta escolhida fora dele
                (k.icone.drawable as? IconeCruz)?.let { d ->
                    val c = if (escolhido && semMenu) Estilo.corIconeNoFoco(context) else k.icone.tag as? Int
                    if (c != null && d.corFixa != c) d.corFixa = c
                }
                if (vis || (naTela && kotlin.math.abs(i - s) < 8)) pedirBanner(k)
            }
        }
        // nome do card em foco, grande, embaixo da fileira
        val f = fileiras.getOrNull(cat)
        val card = f?.cards?.getOrNull(sel)
        val cyF = RowsHome.centroDaFileira(cat, cat, alt, o, extras)
        // y do card em foco (numa grade, a linha dele) e o fim da fileira (o nome fica embaixo da grade inteira)
        val cyCard = cyF + (if (f != null && card != null) f.linhaDe(sel) * RowsHome.passoDaLinha(f.o) else 0f)
        val fimF = cyF + extras.getOrElse(cat) { 0f }
        if (System.currentTimeMillis() >= avisoAte) {
            val novo = card?.item?.nome.orEmpty()
            if (novo != nomeMostrado) {
                val primeira = nomeMostrado == null
                nomeMostrado = novo
                if (anima && !primeira) {
                    nome.animate().cancel()
                    nome.animate().alpha(0f).setDuration(RowsHome.MS_TITULO / 2).withEndAction {
                        nome.text = novo
                        nome.animate().alpha(if (o.cardTitle) 1f else 0f).setDuration(RowsHome.MS_TITULO).setInterpolator(curva).start()
                    }.start()
                } else { nome.text = novo; nome.alpha = if (o.cardTitle) 1f else 0f }
            }
        }
        nome.visibility = if (o.cardTitle) View.VISIBLE else View.INVISIBLE
        nome.maxWidth = dpi(larg - o.textoX * 2)
        val nh = if (nome.height > 0) nome.height / 2f else dp(RowsHome.NOME_SP * 0.6f)
        // posição direto (sem animate(): ele cancelaria o esmaecer do texto que acabou de começar)
        nome.translationX = dp(o.textoX); nome.translationY = dp(fimF + RowsHome.NOME_ABAIXO) - nh
        // brilho em volta do card em foco
        if (card != null) {
            val k1 = o.focoK
            val w = card.w * k1; val h = card.o.cardH * k1
            val b = o.glowSize.dp
            val lp = anel.layoutParams
            if (lp.width != dpi(w + 2 * b) || lp.height != dpi(h + 2 * b)) { lp.width = dpi(w + 2 * b); lp.height = dpi(h + 2 * b); anel.layoutParams = lp }
            val g = anel.background as? GradientDrawable ?: GradientDrawable().also { anel.background = it }
            g.cornerRadius = dp(o.corners.dp * k1 + b)
            g.setColor(0)
            g.setStroke(dpi(b), when (o.glowColor) {
                RowsHome.GlowColor.DYNAMIC -> corDoBrilho(card.cor)
                RowsHome.GlowColor.WHITE -> 0xF2FFFFFF.toInt()
                RowsHome.GlowColor.ACCENT -> Estilo.ACENTO
            })
            anel.elevation = dp(20f)
            val ligado = focada && semMenu && o.effect == RowsHome.Effect.GLOW
            mover(anel, dp(o.focoX - b), dp(cyCard - h / 2f - b), if (ligado) 1f else 0f, 1f, anima)
            animarBrilho(if (ligado) o.glowAnim else RowsHome.GlowAnim.NONE)
        } else anel.alpha = 0f
    }

    /** O card que a fileira [j] mostra à esquerda quando não está em foco: o último escolhido nela (o estado guarda). */
    private var lembradosRef: List<Int> = emptyList()
    fun lembrar(sel: List<Int>) { lembradosRef = sel }
    private fun lembrado(j: Int, n: Int) = lembradosRef.getOrElse(j) { 0 }.coerceIn(0, (n - 1).coerceAtLeast(0))

    private fun mover(v: View, x: Float, y: Float, a: Float, k: Float, anima: Boolean) {
        v.animate().cancel()
        if (anima) v.animate().translationX(x).translationY(y).scaleX(k).scaleY(k).alpha(a).setDuration(RowsHome.MS).setInterpolator(curva).start()
        else { v.translationX = x; v.translationY = y; v.scaleX = k; v.scaleY = k; v.alpha = a }
    }

    /** Cor do brilho: a do card, clara (Projectivy: "vazio = cor dinâmica do card"); card escuro = creme. */
    private fun corDoBrilho(c: Int): Int {
        val l = (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)) / 255.0
        return if (l < 0.25) 0xE6FFF3E6.toInt() else (mistura(c, DesignTokens.FOCUS, 0.55f) and 0x00FFFFFF) or (0xE6 shl 24)
    }

    private var animAtual = RowsHome.GlowAnim.NONE
    private fun animarBrilho(a: RowsHome.GlowAnim) {
        if (a == animAtual && (a == RowsHome.GlowAnim.NONE || brilhoAnim?.isRunning == true)) return
        animAtual = a
        brilhoAnim?.cancel(); brilhoAnim = null
        anel.scaleX = 1f; anel.scaleY = 1f
        if (a == RowsHome.GlowAnim.NONE || semAnimacao() || nivel == MovimentoAdaptativo.Nivel.PARADO) return
        val (de, ate, ms) = when (a) {
            RowsHome.GlowAnim.BREATH -> Triple(0.45f, 1f, 2200L)
            RowsHome.GlowAnim.FADE -> Triple(0f, 1f, 1600L)
            RowsHome.GlowAnim.PULSE -> Triple(1f, 1.035f, 900L)
            RowsHome.GlowAnim.SHINE -> Triple(0.6f, 1f, 500L)
            RowsHome.GlowAnim.NONE -> return
        }
        brilhoAnim = ValueAnimator.ofFloat(de, ate).apply {
            duration = ms; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            if (a == RowsHome.GlowAnim.SHINE) startDelay = 1800L
            addUpdateListener {
                val v = it.animatedValue as Float
                if (a == RowsHome.GlowAnim.PULSE) {
                    anel.pivotX = anel.width / 2f; anel.pivotY = anel.height / 2f; anel.scaleX = v; anel.scaleY = v
                } else if (anel.alpha > 0f) anel.alpha = v
            }
            start()
        }
    }

    /** "Não deu para abrir": vira o nome do foco por uns segundos, na cor de destaque. */
    fun aviso(texto: String, ms: Long) {
        avisoAte = System.currentTimeMillis() + ms
        nome.animate().cancel(); nome.alpha = 1f
        nome.text = texto
        nome.setTextColor(Estilo.ACENTO)
        postDelayed({ avisoAte = 0L; nome.setTextColor(DesignTokens.CREAM); nomeMostrado = null; ultimo?.invoke() }, ms)
    }

    /** Tira o aviso já (o reordenar terminou): o nome do card volta. */
    fun fimDoAviso() { avisoAte = 0L; nome.setTextColor(DesignTokens.CREAM); nomeMostrado = null; ultimo?.invoke() }

    /** O card escolhido "aperta" e volta: o OK foi entendido. */
    fun apertar(cat: Int, sel: Int) {
        val c = fileiras.getOrNull(cat)?.cards?.getOrNull(sel) ?: return
        val k = c.raiz
        if (semAnimacao()) return
        if (opcoes.ripple) c.onda.apply {
            // onda de luz que nasce no centro do card e se espalha (o "efeito ondulatório" dele)
            animate().cancel(); alpha = 0.45f; scaleX = 0.2f; scaleY = 0.2f
            animate().alpha(0f).scaleX(1.6f).scaleY(1.6f).setDuration(420L).setInterpolator(curva).start()
        }
        val s = k.scaleX
        k.animate().cancel()
        k.animate().scaleX(s * 0.94f).scaleY(s * 0.94f).setDuration(DesignTokens.MS_MICRO).withEndAction {
            k.animate().scaleX(s).scaleY(s).setDuration(DesignTokens.MS_FAST).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
        }.start()
    }

    // ------------------------------------------------------------------ cards

    private fun refazer(cats: List<HomeCruz.Categoria>, o: RowsHome.Options) {
        for (f in fileiras) { removeView(f.titulo); for (k in f.cards) { k.raiz.animate().cancel(); removeView(k.raiz) } }
        fileiras.clear()
        val fonte = letra(500)
        for (c in cats) {
            val titulo = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; alpha = 0f }
            if (o.rowIcon) titulo.addView(ImageView(context).apply { setImageDrawable(WorldGlyph(c.cat, DesignTokens.CREAM, 2.2f)) },
                LinearLayout.LayoutParams(dpi(RowsHome.ICONE_TITULO), dpi(RowsHome.ICONE_TITULO)).apply { marginEnd = dpi(10f) })
            titulo.addView(TextView(context).apply {
                Estilo.letraMostrador(this, RowsHome.TITULO_SP * o.textSize.k); typeface = fonte
                setTextColor(DesignTokens.CREAM); includeFontPadding = false; maxLines = 1; text = c.nome
                setShadowLayer(dp(6f), 0f, dp(1f), 0x99000000.toInt())
            }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(titulo, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            val fo = opcoesDe(c, o)
            val cards = c.itens.map { item -> criarCard(item, fo) }
            fileiras += Fileira(c, titulo, cards, fo, WorldOptions.of(context, c.cat).lines)
        }
        // o brilho e o nome ficam por cima dos cards
        bringChildToFront(anel); bringChildToFront(nome)
    }

    private fun criarCard(item: HomeCruz.Item, o: RowsHome.Options): Card {
        val raiz = FrameLayout(context)
        val app = ehApp(item.id)
        val conteudo = item.id.startsWith("card:")
        val cor = when {
            conteudo -> DesignTokens.NIGHT
            o.cardBg == RowsHome.CardBg.GRAPHITE -> PLACA_PROPRIA
            o.cardBg == RowsHome.CardBg.BLACK -> DesignTokens.NIGHT_DEEP
            o.cardBg == RowsHome.CardBg.WHITE -> DesignTokens.PAPER
            o.cardBg == RowsHome.CardBg.ACCENT -> Estilo.ACENTO
            app -> fundoDoIcone(item.icone)
            else -> PLACA_PROPRIA
        }
        val tinta = when (o.iconTint) {
            RowsHome.IconTint.CREAM -> if (o.cardBg == RowsHome.CardBg.WHITE) DesignTokens.NIGHT else DesignTokens.CREAM
            RowsHome.IconTint.BLACK -> DesignTokens.NIGHT
            RowsHome.IconTint.ACCENT -> if (o.cardBg == RowsHome.CardBg.ACCENT) DesignTokens.NIGHT else Estilo.ACENTO
        }
        val fundo = GradientDrawable().apply { cornerRadius = dp(o.corners.dp); setColor(cor) }
        raiz.background = fundo
        raiz.clipToOutline = true
        raiz.alpha = 0f
        val banner = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }
        raiz.addView(banner, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val lado = o.cardH * (if (app) 0.62f else 0.5f)
        val ic = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            // os itens nossos (TV, Séries, Configurações...) em traço creme sobre o grafite, como os atalhos dele
            setImageDrawable(if (conteudo) null else (item.icone as? IconeCruz)?.let { c -> IconeCruz(c.tipo, IconeCruz.Estilo.LINHA).apply { corFixa = tinta } } ?: item.icone)
        }
        ic.tag = tinta   // a cor de volta quando o card perde o foco
        raiz.addView(ic, LayoutParams(dpi(lado), dpi(lado), Gravity.CENTER))
        if (conteudo) { banner.setImageDrawable(item.icone); banner.visibility = View.VISIBLE }
        val w = o.largura(o.tvRatio)
        if (o.progress) item.progresso?.let { p ->
            // barra de progresso embaixo (filme pela metade, episódio): trilho escuro e a parte vista na cor de destaque
            raiz.addView(View(context).apply { background = GradientDrawable().apply { setColor(0x66000000) } },
                LayoutParams(LayoutParams.MATCH_PARENT, dpi(4f), Gravity.BOTTOM))
            raiz.addView(View(context).apply { pivotX = 0f; scaleX = p.coerceIn(0f, 1f); background = GradientDrawable().apply { setColor(Estilo.ACENTO) } },
                LayoutParams(LayoutParams.MATCH_PARENT, dpi(4f), Gravity.BOTTOM))
        }
        // selo: o item tem novidade agora (no ar, episódio novo, tocando): um ponto na cor de destaque no canto
        if (o.badge && item.situacao?.texto?.isNotBlank() == true) raiz.addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Estilo.ACENTO); setStroke(dpi(1.5f), DesignTokens.NIGHT) }
        }, LayoutParams(dpi(11f), dpi(11f), Gravity.TOP or Gravity.END).apply { topMargin = dpi(6f); marginEnd = dpi(6f) })
        val onda = View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(DesignTokens.FOCUS) }
            alpha = 0f
        }
        raiz.addView(onda, LayoutParams(dpi(w), dpi(w), Gravity.CENTER))
        addView(raiz, indexOfChild(anel), LayoutParams(dpi(w), dpi(o.cardH)))
        return Card(item, raiz, banner, ic, fundo, cor, onda, o).also { it.w = w }
    }

    private fun pedirBanner(k: Card) {
        if (k.pediu || !ehApp(k.item.id)) return
        k.pediu = true
        val f = bannerDe ?: return
        f(k.item) { d ->
            if (d == null) {
                // sem banner = app de celular: o card fica na proporção deles (1:1 de fábrica) e a fileira se refaz
                val w = k.o.largura(k.o.mobileRatio)
                if (w != k.w) { k.w = w; k.raiz.layoutParams = k.raiz.layoutParams.apply { width = dpi(w) }; post { ultimo?.invoke() } }
                return@f
            }
            k.banner.setImageDrawable(d); k.banner.visibility = View.VISIBLE; k.icone.visibility = View.INVISIBLE
        }
    }

    private fun ehApp(id0: String) = id0.removePrefix(PREFIXO_CONTINUAR).let { id ->
        id.startsWith("app:") || id.startsWith("c:jogo:") || id.startsWith("c:midia:") }

    /** O fundo do card de um app sem banner: a cor média do ícone, escurecida (o logo numa placa da marca). */
    private fun fundoDoIcone(d: Drawable): Int = try {
        val b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val antes = d.bounds
        d.setBounds(0, 0, 8, 8); d.draw(cv); d.bounds = antes
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            val p = b.getPixel(x, y)
            if ((p ushr 24) < 128) continue
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF; n++
        }
        b.recycle()
        if (n == 0L) PLACA_PROPRIA
        else mistura((0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt(), DesignTokens.NIGHT, 0.35f)
    } catch (e: Exception) { PLACA_PROPRIA }

    private fun mistura(a: Int, b: Int, quantoB: Float): Int {
        fun c(s: Int) = (((a shr s) and 0xFF) * (1 - quantoB) + ((b shr s) and 0xFF) * quantoB).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (c(16) shl 16) or (c(8) shl 8) or c(0)
    }

    private fun conectado(): Boolean = runCatching {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    private fun semAnimacao() = runCatching {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    /** Para o teste de tela: o título da fileira em foco, o nome do foco e os cards visíveis. */
    internal fun nomeDoFoco(): TextView = nome
    internal fun cardsVisiveis(): List<View> = fileiras.flatMap { f -> f.cards.map { it.raiz } }.filter { it.alpha > 0.5f }

    private companion object {
        /** Placa dos itens nossos (atalhos): grafite neutro, nunca azulado (regra do Paulo). */
        const val PLACA_PROPRIA = 0xFF3A3A39.toInt()
    }
}

/** Wi-Fi em traço (três arcos e um ponto), na cor dada. Desenho próprio (o do Projectivy é um ícone do Material). */
internal class WifiGlyph(private val cor: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = cor }
    private val ponto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor }
    override fun draw(c: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        val cx = b.exactCenterX(); val base = b.top + s * 0.86f
        p.strokeWidth = s * 0.1f
        for (k in 1..3) {
            val r = s * (0.2f + 0.24f * (k - 1))
            c.drawArc(RectF(cx - r, base - r, cx + r, base + r), 225f, 90f, false, p)
        }
        c.drawCircle(cx, base - s * 0.02f, s * 0.07f, ponto)
    }
    override fun setAlpha(a: Int) { p.alpha = a; ponto.alpha = a }
    override fun setColorFilter(f: ColorFilter?) { p.colorFilter = f; ponto.colorFilter = f }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
