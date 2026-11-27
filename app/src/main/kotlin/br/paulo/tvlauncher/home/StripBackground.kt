package br.paulo.tvlauncher.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import br.paulo.tvlauncher.DesignTokens
import br.paulo.tvlauncher.settings.Extras

/**
 * Fundo da barra única (Paulo, 24/11/2026: "coloca os 3 planos de fundo que você me mostrou, adota o Vale como padrão").
 *
 * Porquê: a barra única existe para deixar a tela livre para o fundo; com o grafite liso, o meio da tela ficava vazio.
 * Para quê: uma foto de paisagem escura (as três do estudo escolhas-home: Vale, Serra, Noite) atrás da Ilha e da barra.
 * Como: [choice] decide (sem escolha gravada: Vale, qualquer que seja o tema; "Do tema" no Ajuste fino volta ao fundo
 * do tema: Escuro, Galeria, Meu fundo, plugin). [PhotoBackground] desenha a foto com véus em cima (atrás da Ilha) e
 * embaixo (atrás da linha do mundo e dos cards), para o texto continuar legível.
 */
object StripBackground {
    const val KEY = "strip_bg"   // -1 = nunca escolhido; senão o índice de [Choice]

    enum class Choice(val asset: String?) { VALE("fundos/vale.jpg"), SERRA("fundos/serra.jpg"), NOITE("fundos/noite.jpg"), THEME(null) }

    /** Pura (teste na JVM): o índice gravado; nunca escolhido = Vale. */
    fun resolve(stored: Int): Choice = Choice.entries.getOrNull(stored) ?: Choice.VALE

    fun choice(ctx: Context): Choice = resolve(Extras.numero(ctx, KEY, -1))

    /** Gira a escolha ([passo] +1/-1), grava e devolve a nova. */
    fun next(ctx: Context, passo: Int): Choice {
        val n = Math.floorMod(choice(ctx).ordinal + passo, Choice.entries.size)
        Extras.definirNumero(ctx, KEY, n)
        return Choice.entries[n]
    }

    /** "Novo fundo agora": a próxima das três fotos (não passa pelo "do tema"). */
    fun nextPhoto(atual: Choice): Choice = Choice.entries[(atual.ordinal + 1) % 3]
}

/** A foto de fundo da barra única, com véus de leitura. Lê o arquivo fora da tela ([emSegundoPlano]). */
class PhotoBackground(ctx: Context, private var escolha: StripBackground.Choice,
                      private val emSegundoPlano: (() -> Unit) -> Unit) : View(ctx), FundoVivo {
    private var foto: Bitmap? = null
    /** Versão pequena e borrada da foto (modos "Caber com fundo borrado" e "Borrar") e a cor média dela (cores dinâmicas). */
    private var borrada: Bitmap? = null
    private var corMedia = DesignTokens.NIGHT
    /** Modo e cores dinâmicas (Fileiras), lidos uma vez: mudar a opção refaz a HOME (assinatura) e esta view. */
    private val modo = if (BottomBar.rows(ctx)) RowsHome.options(ctx).bgMode else RowsHome.BgMode.CROP
    private val dinamico = BottomBar.rows(ctx) && RowsHome.options(ctx).bgDynamic
    /** A foto de antes, que some aos poucos quando a nova chega (troca com esmaecer, bloco 5). */
    private var anterior: Bitmap? = null
    private var mistura = 1f
    private val pinta = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pintaNova = Paint(Paint.FILTER_BITMAP_FLAG)
    /** Troca sozinha (Fileiras: "Trocar o fundo a cada"): a próxima das três fotos a cada N minutos. */
    private val trocar = object : Runnable {
        override fun run() {
            val min = trocaMin()
            if (min <= 0) return
            proximo()
            postDelayed(this, min * 60_000L)
        }
    }
    private fun trocaMin(): Int = if (BottomBar.rows(context)) RowsHome.options(context).bgEvery.min else 0
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        trocaMin().takeIf { it > 0 }?.let { postDelayed(trocar, it * 60_000L) }
    }
    override fun onDetachedFromWindow() { removeCallbacks(trocar); super.onDetachedFromWindow() }
    private val veu = Paint()
    private val origem = Rect()
    private val destino = RectF()

    init { carregar() }

    private fun carregar() {
        val asset = escolha.asset ?: return
        emSegundoPlano {
            val b = runCatching { context.assets.open(asset).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }) } }.getOrNull()
            val pequena = b?.let { runCatching { Bitmap.createScaledBitmap(it, maxOf(1, it.width / 8), maxOf(1, it.height / 8), true) }.getOrNull() }
            val media = pequena?.let { corMediaDe(it) } ?: DesignTokens.NIGHT
            val borr = pequena?.let { p ->
                val w = p.width; val h = p.height
                val px = IntArray(w * h); p.getPixels(px, 0, w, 0, 0, w, h)
                repeat(3) { GlassBackground.caixa(px, w, h, 4) }
                Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
            }
            post {
                borrada = borr; corMedia = media
                // a primeira entra direto; as outras esmaecem por cima da de antes (600 ms)
                if (foto == null || b == null) { foto = b; invalidate(); return@post }
                anterior = foto; foto = b; mistura = 0f
                android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = TROCA_MS
                    addUpdateListener { mistura = it.animatedValue as Float; invalidate() }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(a: android.animation.Animator) { anterior = null; mistura = 1f; invalidate() }
                    })
                }.start()
            }
        }
    }

    override fun atualizar(e: EstadoCeu) {}

    override fun proximo(): Boolean {
        escolha = StripBackground.nextPhoto(escolha)
        Extras.definirNumero(context, StripBackground.KEY, escolha.ordinal)
        carregar()
        return true
    }

    /** Cor média da foto, escurecida para virar véu (cores dinâmicas). */
    private fun corMediaDe(b: Bitmap): Int {
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) {
            val c = b.getPixel(x, y); r += c shr 16 and 0xFF; g += c shr 8 and 0xFF; bl += c and 0xFF; n++
        }
        if (n == 0L) return DesignTokens.NIGHT
        return RowsHome.dynamicVeil((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    /** Desenha a foto [img] no modo escolhido (Fileiras: "Modo de exibição"). */
    private fun desenhar(c: Canvas, img: Bitmap, w: Float, h: Float, p: Paint) {
        when (modo) {
            RowsHome.BgMode.CROP -> recortar(c, img, w, h, p)
            RowsHome.BgMode.STRETCH -> { origem.set(0, 0, img.width, img.height); destino.set(0f, 0f, w, h); c.drawBitmap(img, origem, destino, p) }
            RowsHome.BgMode.BLUR -> recortar(c, borrada ?: img, w, h, p)
            RowsHome.BgMode.FIT, RowsHome.BgMode.FIT_BLUR -> {
                if (modo == RowsHome.BgMode.FIT_BLUR) borrada?.let { recortar(c, it, w, h, p) }
                val escala = minOf(w / img.width, h / img.height)
                val dw = img.width * escala; val dh = img.height * escala
                origem.set(0, 0, img.width, img.height); destino.set((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2)
                c.drawBitmap(img, origem, destino, p)
            }
        }
    }

    /** centerCrop da foto [img] na tela inteira. */
    private fun recortar(c: Canvas, img: Bitmap, w: Float, h: Float, p: Paint) {
        val escala = maxOf(w / img.width, h / img.height)
        val vw = (w / escala).toInt(); val vh = (h / escala).toInt()
        val x0 = (img.width - vw) / 2; val y0 = (img.height - vh) / 2
        origem.set(x0, y0, x0 + vw, y0 + vh); destino.set(0f, 0f, w, h)
        c.drawBitmap(img, origem, destino, p)
    }

    private companion object { const val TROCA_MS = 600L }

    private fun noite(alfa: Int) = (alfa shl 24) or (DesignTokens.NIGHT and 0xFFFFFF)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(DesignTokens.NIGHT)
        val img = foto ?: return
        anterior?.let { desenhar(c, it, w, h, pinta) }
        pintaNova.alpha = (mistura * 255).toInt()
        desenhar(c, img, w, h, if (anterior != null) pintaNova else pinta)
        // Cores dinâmicas (Fileiras): um véu na cor da foto por cima, que pinta a tela com o tom dela
        if (dinamico) { veu.color = (0x66 shl 24) or (corMedia and 0xFFFFFF); c.drawRect(0f, 0f, w, h, veu) }
        // Véu de cima (atrás da Ilha) e de baixo (atrás da linha do mundo, que começa a 76% da altura, e dos cards).
        veu.shader = LinearGradient(0f, 0f, 0f, h * 0.28f, noite(0x8C), noite(0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h * 0.28f, veu)
        veu.shader = LinearGradient(0f, h * 0.45f, 0f, h * 0.8f, noite(0), noite(0xD9), Shader.TileMode.CLAMP)
        c.drawRect(0f, h * 0.45f, w, h, veu)
        veu.shader = null
    }
}

/**
 * O fundo das outras telas quando a HOME é a barra única: a MESMA foto da barra, borrada e escurecida (coerência das
 * telas, proposta A "Vidro da paisagem", 24/11/2026).
 * Porquê: saindo da barra para Configurações, Rádio ou Despertador, o fundo virava grafite liso e parecia outro app.
 * Para quê: a pessoa sente que continua no mesmo lugar; o borrão e o véu deixam o texto tão legível quanto no grafite.
 * Como: lê a foto em 1/8 do tamanho (200 x 112), borra com 3 passadas de caixa (fica perto de um desfoque gaussiano) e
 * guarda numa cache por foto; cada tela só desenha o bitmap esticado com filtro e o véu Noite por cima.
 */
object GlassBackground {
    /** Véu Noite por cima da foto borrada: 70% segura o contraste do texto creme (medido nas telas). */
    const val VEU = 0xB3
    private const val REDUCAO = 8
    private const val RAIO = 3
    private const val PASSADAS = 3
    private val cache = HashMap<StripBackground.Choice, Bitmap>()
    private val nitidas = HashMap<StripBackground.Choice, Bitmap>()

    /** Onde a gaveta começa, em fração da altura da tela: a foto fica livre acima disso (Paulo escolheu, 24/11/2026). */
    const val GAVETA_TOPO = 0.36f

    /**
     * O fundo da Gaveta (proposta B): a foto NÍTIDA (metade do tamanho, 800 x 450, em RGB 565: ~700 KB) com o véu de cima
     * (atrás da pílula) e o véu Noite que vira painel a partir de [GAVETA_TOPO]. Null quando não há foto.
     */
    fun drawer(ctx: Context): android.graphics.drawable.Drawable? = nitida(ctx)?.let { GlassDrawable(it, gaveta = true) }

    /**
     * O fundo do jeito Barra (proposta C) nas telas de entrada: a mesma foto nítida da Gaveta, livre na metade de cima e
     * escurecendo para o Noite embaixo, atrás da fileira de cards (como a HOME). Null quando não há foto.
     */
    fun strip(ctx: Context): android.graphics.drawable.Drawable? = nitida(ctx)?.let { GlassDrawable(it, barra = true) }

    /**
     * O fundo de toda tela desde 26/11/2026 (Paulo aprovou a prévia "Arte com véu"): a foto nítida inteira sob um véu
     * Noite em degradê, mais leve no alto e firme embaixo, o mesmo das Fileiras. Null quando não há foto.
     */
    fun veiled(ctx: Context): android.graphics.drawable.Drawable? = nitida(ctx)?.let { GlassDrawable(it, tudo = true) }

    /** A foto da barra em metade do tamanho (800 x 450, RGB 565: ~700 KB), guardada só a da escolha atual. */
    private fun nitida(ctx: Context): Bitmap? {
        val escolha = StripBackground.choice(ctx)
        val asset = escolha.asset ?: return null
        return synchronized(nitidas) {
            nitidas[escolha] ?: runCatching {
                ctx.assets.open(asset).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2; inPreferredConfig = Bitmap.Config.RGB_565 })
                }
            }.getOrNull()?.also { nitidas.clear(); nitidas[escolha] = it }
        }
    }

    /** O fundo de vidro, ou null quando a HOME não é a barra ou o fundo da barra não é foto (aí fica o grafite). */
    fun of(ctx: Context): android.graphics.drawable.Drawable? {
        if (!BottomBar.strip(ctx)) return null
        val escolha = StripBackground.choice(ctx)
        val asset = escolha.asset ?: return null
        val bmp = synchronized(cache) {
            cache[escolha] ?: runCatching {
                ctx.assets.open(asset).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = REDUCAO })
                }
            }.getOrNull()?.let { borrar(it) }?.also { cache[escolha] = it }
        } ?: return null
        return GlassDrawable(bmp)
    }

    /** Desfoque de caixa separável, [PASSADAS] vezes, num bitmap pequeno. Devolve um bitmap novo (o original é solto). */
    private fun borrar(b: Bitmap): Bitmap {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        repeat(PASSADAS) { caixa(px, w, h, RAIO) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).also { if (it !== b) b.recycle() }
    }

    /** Uma passada de caixa (horizontal e depois vertical) com borda presa. Puro sobre o vetor: testável. */
    internal fun caixa(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        fun passo(origem: IntArray, destino: IntArray, n: Int, linhas: Int, idx: (Int, Int) -> Int) {
            for (l in 0 until linhas) for (i in 0 until n) {
                var a = 0; var rr = 0; var g = 0; var bb = 0
                for (k in -r..r) {
                    val c = origem[idx(l, (i + k).coerceIn(0, n - 1))]
                    a += c ushr 24; rr += c shr 16 and 0xFF; g += c shr 8 and 0xFF; bb += c and 0xFF
                }
                val q = 2 * r + 1
                destino[idx(l, i)] = (a / q shl 24) or (rr / q shl 16) or (g / q shl 8) or (bb / q)
            }
        }
        passo(px, tmp, w, h) { l, i -> l * w + i }
        passo(tmp, px, h, w) { l, i -> i * w + l }
    }
}

/** Desenha a foto borrada em "centerCrop" com filtro e o véu Noite por cima. Opaco. */
class GlassDrawable(private val foto: Bitmap, private val gaveta: Boolean = false, val barra: Boolean = false,
                    private val tudo: Boolean = false) : android.graphics.drawable.Drawable() {
    private val pinta = Paint(Paint.FILTER_BITMAP_FLAG)
    private val veu = Paint().apply { color = (GlassBackground.VEU shl 24) or (DesignTokens.NIGHT and 0xFFFFFF) }
    private val origem = Rect()
    private val destino = RectF()
    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        val escala = maxOf(w / foto.width, h / foto.height)
        val vw = (w / escala).toInt().coerceAtMost(foto.width); val vh = (h / escala).toInt().coerceAtMost(foto.height)
        val x0 = (foto.width - vw) / 2; val y0 = (foto.height - vh) / 2
        origem.set(x0, y0, x0 + vw, y0 + vh); destino.set(bounds)
        c.drawColor(DesignTokens.NIGHT)
        c.drawBitmap(foto, origem, destino, pinta)
        if (barra) { barra(c, h); return }
        if (tudo) { veuInteiro(c, h); return }
        if (!gaveta) { c.drawRect(destino, veu); return }
        // Gaveta: véu de cima atrás da pílula (como na barra) e o painel Noite que sobe um pouco antes do conteúdo.
        val t = bounds.top.toFloat()
        val topo = t + h * GlassBackground.GAVETA_TOPO
        val noite = DesignTokens.NIGHT and 0xFFFFFF
        degrade.shader = LinearGradient(0f, t, 0f, t + h * 0.22f, (0x8C shl 24) or noite, noite, Shader.TileMode.CLAMP)
        c.drawRect(bounds.left.toFloat(), t, bounds.right.toFloat(), t + h * 0.22f, degrade)
        degrade.shader = LinearGradient(0f, topo - h * 0.16f, 0f, topo + h * 0.04f, noite, (0xF2 shl 24) or noite, Shader.TileMode.CLAMP)
        c.drawRect(bounds.left.toFloat(), topo - h * 0.16f, bounds.right.toFloat(), bounds.bottom.toFloat(), degrade)
        degrade.shader = null
    }
    private val degrade = Paint()

    /** Barra: véu de cima atrás do título e da pílula; embaixo a foto escurece até o Noite atrás dos cards. */
    private fun barra(c: Canvas, h: Float) {
        val t = bounds.top.toFloat(); val l = bounds.left.toFloat(); val r = bounds.right.toFloat()
        val noite = DesignTokens.NIGHT and 0xFFFFFF
        degrade.shader = LinearGradient(0f, t, 0f, t + h * 0.26f, (0x99 shl 24) or noite, noite, Shader.TileMode.CLAMP)
        c.drawRect(l, t, r, t + h * 0.26f, degrade)
        degrade.shader = LinearGradient(0f, t + h * BARRA_DEGRADE, 0f, t + h * BARRA_ESCURO, noite, (0xE6 shl 24) or noite, Shader.TileMode.CLAMP)
        c.drawRect(l, t + h * BARRA_DEGRADE, r, bounds.bottom.toFloat(), degrade)
        degrade.shader = null
    }
    /**
     * Véu da tela inteira (prévia aprovada 26/11/2026): Noite a 55% no alto, 62% a um terço, 85% a 62% e pleno embaixo.
     * O texto nunca fica sobre a foto crua; a montanha aparece no meio, como nas Fileiras.
     */
    private fun veuInteiro(c: Canvas, h: Float) {
        val t = bounds.top.toFloat()
        val noite = DesignTokens.NIGHT and 0xFFFFFF
        degrade.shader = LinearGradient(0f, t, 0f, t + h,
            intArrayOf((0x8C shl 24) or noite, (0x9E shl 24) or noite, (0xD9 shl 24) or noite, (0xFF shl 24) or noite),
            floatArrayOf(0f, 0.35f, 0.62f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(bounds.left.toFloat(), t, bounds.right.toFloat(), bounds.bottom.toFloat(), degrade)
        degrade.shader = null
    }

    private companion object {
        /** Onde a foto começa a escurecer e onde chega ao Noite quase pleno (fração da altura), no jeito Barra. */
        const val BARRA_DEGRADE = 0.38f
        const val BARRA_ESCURO = 0.70f
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
}
