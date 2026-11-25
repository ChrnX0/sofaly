package br.paulo.tvlauncher.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.sin

/**
 * Ícone do mundo em movimento na barra única (Paulo, 24/11/2026: "animações suaves, elegantes e contínuas").
 *
 * Porquê: o ícone parado era só um rótulo; o movimento lento dá vida à tela sem chamar atenção (o fundo é o protagonista).
 * Para quê: ao trocar de mundo o ícone se desenha traço a traço (0,7 s); depois respira sem parar num ciclo de 4 s (25/11: gestos maiores, Paulo achou o de 6 s parado demais), cada mundo com
 * um gesto próprio: a tela de Assistir pulsa o "play"; o fone de Ouvir balança e solta ondas; o controle de Jogar
 * inclina e acende os botões; os quadrados de Aplicativos respiram em sequência; os botões de Ferramentas deslizam.
 * Como: as mesmas formas do [WorldGlyph] (grade de 32), separadas em peças; ~30 quadros/s só enquanto está na tela
 * ([start]/[stop]). Movimento desligado (sistema, alto contraste, "Parado"): quem chama usa o [WorldGlyph] parado.
 * Refs: transitions.dev (draw-on de traço, ease-out), Mobbin Calm/Headspace (respiração lenta), Apple TV (ícones vivos).
 */
class WorldMotion(private val cat: CruzLogica.Categoria, cor: Int, private val traco: Float = 2.1f) : Drawable(), Animatable {

    private var rodando = false
    private var inicio = SystemClock.uptimeMillis()
    private val formas = WorldGlyph.formasDe(cat)
    private val pecas: List<Path> = formas.map { f -> Path().apply {
        when (f) {
            is StrokeGlyph.Forma.Linha -> addPath(SvgPath.parse(f.d))
            is StrokeGlyph.Forma.Ret -> addRoundRect(RectF(f.x, f.y, f.x + f.w, f.y + f.h), f.r, f.r, Path.Direction.CW)
            is StrokeGlyph.Forma.Circ -> addCircle(f.cx, f.cy, f.r, Path.Direction.CW)
            is StrokeGlyph.Forma.Menos -> addCircle(f.a.cx, f.a.cy, f.a.r, Path.Direction.CW)
        }
    } }
    private val cheias = formas.map { it is StrokeGlyph.Forma.Circ && it.cheio }
    private val maior = pecas.maxOf { p -> var l = 0f; val m = PathMeasure(p, false); do { l = maxOf(l, m.length) } while (m.nextContour()); l }

    private val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = cor; strokeWidth = traco
    }
    private val tintaCheia = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = cor }
    private val onda = Paint(tinta).apply { strokeWidth = traco * 0.75f }
    private val linhaViva = Path()
    private val oval = RectF()
    private var alfaBase = 255

    private val proximo = Runnable { invalidateSelf() }

    /** Recomeça do desenho do traço (troca de mundo). */
    fun reiniciar() { inicio = SystemClock.uptimeMillis(); invalidateSelf() }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val t = (SystemClock.uptimeMillis() - inicio).toFloat()
        val desenho = Motion.drawOn(t)
        val s = Motion.breath(t)            // -1..1, ciclo de 6 s, entra devagar depois do desenho
        val k = Motion.calm(t)              // 0 durante o desenho, 1 depois (o gesto entra sem tranco)
        val esc = minOf(b.width(), b.height()) / 32f
        val n = c.save()
        c.translate(b.left + (b.width() - 32f * esc) / 2f, b.top + (b.height() - 32f * esc) / 2f)
        c.scale(esc, esc)
        tinta.pathEffect = if (desenho < 1f) DashPathEffect(floatArrayOf(maior, maior), maior * (1f - desenho)) else null
        tintaCheia.alpha = (alfaBase * desenho).toInt()
        when (cat) {
            CruzLogica.Categoria.ASSISTIR -> {
                peca(c, 0); peca(c, 1)
                val e = 1f + 0.16f * s * k
                c.save(); c.scale(e, e, 16.3f, 14.25f); peca(c, 2); c.restore()
            }
            CruzLogica.Categoria.OUVIR -> {
                c.save(); c.translate(0f, -1.6f * s * k); pecas.indices.forEach { peca(c, it) }; c.restore()
                // ondas que saem dos dois lados, uma a cada 3 s
                val f = Motion.wave(t)
                onda.alpha = (alfaBase * 0.7f * (1f - f) * k).toInt()
                if (onda.alpha > 0) {
                    val r = 3.5f + 3f * f
                    oval.set(7.25f - r, 22.75f - r, 7.25f + r, 22.75f + r); c.drawArc(oval, 150f, 60f, false, onda)
                    oval.set(24.75f - r, 22.75f - r, 24.75f + r, 22.75f + r); c.drawArc(oval, -30f, 60f, false, onda)
                }
            }
            CruzLogica.Categoria.JOGAR -> {
                c.save(); c.rotate(8f * s * k, 16f, 16f)
                peca(c, 0); peca(c, 1)
                val a = tintaCheia.alpha
                tintaCheia.alpha = (a * (0.55f + 0.45f * maxOf(0f, s * k + (1 - k)))).toInt(); peca(c, 2)
                tintaCheia.alpha = (a * (0.55f + 0.45f * maxOf(0f, -s * k + (1 - k)))).toInt(); peca(c, 3)
                tintaCheia.alpha = a
                c.restore()
            }
            CruzLogica.Categoria.APLICATIVOS -> {
                val centros = listOf(9.5f to 9.5f, 22.5f to 9.5f, 9.5f to 22.5f, 22.5f to 22.5f)
                for (i in 0 until 4) {
                    val e = 1f + 0.16f * Motion.breath(t, i / 4f) * k
                    c.save(); c.scale(e, e, centros[i].first, centros[i].second); peca(c, i); c.restore()
                }
            }
            CruzLogica.Categoria.FERRAMENTAS -> {
                // os dois botões deslizam em sentidos opostos; os trilhos abrem o vão onde o botão está
                val x1 = 12.5f + 4.5f * s * k; val x2 = 21f - 4.5f * s * k
                linhaViva.rewind()
                linhaViva.moveTo(5f, 10.5f); linhaViva.lineTo(x1 - 3f, 10.5f); linhaViva.moveTo(x1 + 3f, 10.5f); linhaViva.lineTo(27f, 10.5f)
                linhaViva.moveTo(5f, 21.5f); linhaViva.lineTo(x2 - 3f, 21.5f); linhaViva.moveTo(x2 + 3f, 21.5f); linhaViva.lineTo(27f, 21.5f)
                c.drawPath(linhaViva, tinta)
                c.drawCircle(x1, 10.5f, 3f, tinta); c.drawCircle(x2, 21.5f, 3f, tinta)
            }
            CruzLogica.Categoria.CONTINUAR -> {
                peca(c, 0)
                c.save(); c.rotate((t / 60f) % 360f * k, 16f, 16f); peca(c, 1); c.restore()
            }
        }
        c.restoreToCount(n)
        if (rodando) scheduleSelf(proximo, SystemClock.uptimeMillis() + QUADRO_MS)
    }

    private fun peca(c: Canvas, i: Int) { pecas.getOrNull(i)?.let { c.drawPath(it, if (cheias[i]) tintaCheia else tinta) } }

    override fun start() { if (!rodando) { rodando = true; invalidateSelf() } }
    override fun stop() { rodando = false; unscheduleSelf(proximo) }
    override fun isRunning() = rodando

    override fun setAlpha(alpha: Int) { alfaBase = alpha; tinta.alpha = alpha; tintaCheia.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(cf: ColorFilter?) { tinta.colorFilter = cf; tintaCheia.colorFilter = cf; onda.colorFilter = cf; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = -1
    override fun getIntrinsicHeight() = -1

    /** As curvas do movimento, puras (teste na JVM). [t] em ms desde a troca de mundo. */
    object Motion {
        const val DRAW_MS = 700f
        const val CYCLE_MS = 4000f
        const val CALM_MS = 1200f
        /** 0..1 do desenho do traço, ease-out cúbico. */
        fun drawOn(t: Float): Float { val x = (t / DRAW_MS).coerceIn(0f, 1f); val u = 1f - x; return 1f - u * u * u }
        /** -1..1, seno lento; [fase] 0..1 adianta o ciclo (os quadrados de Aplicativos em sequência). */
        fun breath(t: Float, fase: Float = 0f): Float = sin(2.0 * PI * (t / CYCLE_MS - fase)).toFloat()
        /** 0 até o fim do desenho, sobe a 1 em [CALM_MS] (smoothstep): o gesto entra sem tranco. */
        fun calm(t: Float): Float { val x = ((t - DRAW_MS) / CALM_MS).coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
        /** 0..1 da onda de Ouvir, uma a cada metade do ciclo. */
        fun wave(t: Float): Float = (t % (CYCLE_MS / 2f)) / (CYCLE_MS / 2f)
    }

    companion object {
        const val QUADRO_MS = 33L

        /** Movimento permitido? Desligado no sistema, alto contraste ou "Parado" no movimento adaptativo: não. */
        fun permitido(ctx: Context): Boolean {
            val sistema = runCatching {
                android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }.getOrDefault(false)
            if (sistema || br.paulo.tvlauncher.Estilo.altoContraste) return false
            return runCatching { MovimentoAdaptativo.atual(ctx, sistema) != MovimentoAdaptativo.Nivel.PARADO }.getOrDefault(true)
        }
    }
}
