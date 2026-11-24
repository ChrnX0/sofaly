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
import br.paulo.tvlauncher.settings.Tema

/**
 * Fundo da barra única (Paulo, 24/11/2026: "coloca os 3 planos de fundo que você me mostrou, adota o Vale como padrão").
 *
 * Porquê: a barra única existe para deixar a tela livre para o fundo; com o grafite liso, o meio da tela ficava vazio.
 * Para quê: uma foto de paisagem escura (as três do estudo escolhas-home: Vale, Serra, Noite) atrás da Ilha e da barra.
 * Como: [choice] decide (sem escolha gravada: Vale, se o tema for o Escuro de fábrica; com tema escolhido pela pessoa,
 * Galeria, Meu fundo, plugin, fica o do tema). [PhotoBackground] desenha a foto com véus em cima (atrás da Ilha) e
 * embaixo (atrás da linha do mundo e dos cards), para o texto continuar legível.
 */
object StripBackground {
    const val KEY = "strip_bg"   // -1 = nunca escolhido; senão o índice de [Choice]

    enum class Choice(val asset: String?) { VALE("fundos/vale.jpg"), SERRA("fundos/serra.jpg"), NOITE("fundos/noite.jpg"), THEME(null) }

    /** Pura (teste na JVM): o índice gravado e se o tema é o Escuro de fábrica. */
    fun resolve(stored: Int, darkTheme: Boolean): Choice =
        Choice.entries.getOrNull(stored) ?: if (darkTheme) Choice.VALE else Choice.THEME

    fun choice(ctx: Context): Choice = resolve(Extras.numero(ctx, KEY, -1), Tema.atual(ctx) == Tema.ESCURO)

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
