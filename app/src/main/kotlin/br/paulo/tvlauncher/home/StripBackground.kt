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
    private val pinta = Paint(Paint.FILTER_BITMAP_FLAG)
    private val veu = Paint()
    private val origem = Rect()
    private val destino = RectF()

    init { carregar() }

    private fun carregar() {
        val asset = escolha.asset ?: return
        emSegundoPlano {
            val b = runCatching { context.assets.open(asset).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }) } }.getOrNull()
            post { foto = b; invalidate() }
        }
    }

    override fun atualizar(e: EstadoCeu) {}

    override fun proximo(): Boolean {
        escolha = StripBackground.nextPhoto(escolha)
        Extras.definirNumero(context, StripBackground.KEY, escolha.ordinal)
        carregar()
        return true
    }

    private fun noite(alfa: Int) = (alfa shl 24) or (DesignTokens.NIGHT and 0xFFFFFF)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(DesignTokens.NIGHT)
        val img = foto ?: return
        // centerCrop
        val escala = maxOf(w / img.width, h / img.height)
        val vw = (w / escala).toInt(); val vh = (h / escala).toInt()
        val x0 = (img.width - vw) / 2; val y0 = (img.height - vh) / 2
        origem.set(x0, y0, x0 + vw, y0 + vh); destino.set(0f, 0f, w, h)
        c.drawBitmap(img, origem, destino, pinta)
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
class GlassDrawable(private val foto: Bitmap) : android.graphics.drawable.Drawable() {
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
        c.drawRect(destino, veu)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
}
