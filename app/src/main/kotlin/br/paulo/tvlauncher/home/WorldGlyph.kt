package br.paulo.tvlauncher.home

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Ícone de traço fino de cada mundo na barra única ([StripLayer]) (Paulo, 24/11/2026: "elegante e minimalista").
 *
 * Porquê: os ícones de categoria antigos eram cheios e coloridos; na linha do mundo eles brigavam com o fundo.
 * Para quê: o mesmo desenho aprovado no estudo (escolhas-home, v2): grade de 32, traço 2,1, pontas redondas, creme.
 * Como: cada ícone é uma lista de formas no padrão SVG (o mesmo texto do estudo) que [SvgPath] converte em [Path] uma
 * vez; o desenho só escala a grade de 32 para o tamanho da view.
 */
class WorldGlyph(cat: CruzLogica.Categoria, cor: Int, traco: Float = 2.1f) : StrokeGlyph(formasDe(cat), cor, traco) {
    companion object {
        fun formasDe(cat: CruzLogica.Categoria): List<StrokeGlyph.Forma> = when (cat) {
            CruzLogica.Categoria.ASSISTIR -> listOf(
                StrokeGlyph.Forma.Ret(4f, 6f, 24f, 16.5f, 4f), StrokeGlyph.Forma.Linha("M12 26.5h8"), StrokeGlyph.Forma.Linha("M14 11.2l5.6 3.05L14 17.3z"))
            CruzLogica.Categoria.OUVIR -> listOf(
                StrokeGlyph.Forma.Linha("M6 19.5v-3a10 10 0 0 1 20 0v3"),
                StrokeGlyph.Forma.Ret(4.5f, 18.5f, 5.5f, 8.5f, 2.75f), StrokeGlyph.Forma.Ret(22f, 18.5f, 5.5f, 8.5f, 2.75f))
            CruzLogica.Categoria.JOGAR -> listOf(
                StrokeGlyph.Forma.Linha("M10.5 10h11a6.5 6.5 0 0 1 6.5 6.5v2.6a4.4 4.4 0 0 1-8 2.5l-1.3-1.6h-5.4L12 21.6a4.4 4.4 0 0 1-8-2.5v-2.6A6.5 6.5 0 0 1 10.5 10z"),
                StrokeGlyph.Forma.Linha("M10.5 13.6v5M8 16.1h5"), StrokeGlyph.Forma.Circ(20.6f, 14.6f, 0.9f, true), StrokeGlyph.Forma.Circ(23.4f, 17.4f, 0.9f, true))
            CruzLogica.Categoria.APLICATIVOS -> listOf(
                StrokeGlyph.Forma.Ret(5f, 5f, 9f, 9f, 2.6f), StrokeGlyph.Forma.Ret(18f, 5f, 9f, 9f, 2.6f), StrokeGlyph.Forma.Ret(5f, 18f, 9f, 9f, 2.6f), StrokeGlyph.Forma.Circ(22.5f, 22.5f, 4.6f))
            CruzLogica.Categoria.FERRAMENTAS -> listOf(
                StrokeGlyph.Forma.Linha("M5 10.5h4.5M15.5 10.5H27M5 21.5h13M24 21.5h3"), StrokeGlyph.Forma.Circ(12.5f, 10.5f, 3f), StrokeGlyph.Forma.Circ(21f, 21.5f, 3f))
            // Continuar deixou de ser mundo (21/11/2026); se voltar, um relógio de volta.
            CruzLogica.Categoria.CONTINUAR -> listOf(StrokeGlyph.Forma.Circ(16f, 16f, 11f), StrokeGlyph.Forma.Linha("M16 10v6l4 2.5"))
        }
    }
}

/**
 * Ícones do céu da noite (Paulo, 24/11/2026: "pode manter, iremos adicionar mais ícones de estilos diferentes depois"):
 * o mesmo traço dos mundos, desenhados no estudo https://claude.ai/artifact/NSxNSFygoiPJAuB4yLfnv9 . Usados na Ilha
 * e na linha do céu no descanso de tela.
 */
class NightSkyGlyph(kind: br.paulo.tvlauncher.weather.NightSky.Kind, cor: Int, traco: Float = 2.1f) : StrokeGlyph(formasDe(kind), cor, traco) {
    companion object {
        fun formasDe(k: br.paulo.tvlauncher.weather.NightSky.Kind): List<StrokeGlyph.Forma> = when (k) {
            br.paulo.tvlauncher.weather.NightSky.Kind.ISS -> listOf(
                StrokeGlyph.Forma.Ret(13f, 13f, 6f, 6f, 1.6f), StrokeGlyph.Forma.Linha("M11 16h2M19 16h2"),
                StrokeGlyph.Forma.Ret(3.5f, 11f, 7.5f, 10f, 1.4f), StrokeGlyph.Forma.Ret(21f, 11f, 7.5f, 10f, 1.4f), StrokeGlyph.Forma.Linha("M7.25 11v10M24.75 11v10M16 13v-4M14 9h4"))
            br.paulo.tvlauncher.weather.NightSky.Kind.METEORS -> listOf(
                StrokeGlyph.Forma.Linha("M5 5l11 11M14 4l8 8M4 15l6 6"),
                StrokeGlyph.Forma.Circ(18.2f, 18.2f, 2.6f, true), StrokeGlyph.Forma.Circ(23.5f, 13.5f, 1.7f, true), StrokeGlyph.Forma.Circ(11.6f, 22.6f, 1.5f, true))
            br.paulo.tvlauncher.weather.NightSky.Kind.SUPERMOON -> listOf(
                StrokeGlyph.Forma.Circ(16f, 16f, 10f), StrokeGlyph.Forma.Circ(12.4f, 13f, 2f), StrokeGlyph.Forma.Circ(19.2f, 18.6f, 2.8f), StrokeGlyph.Forma.Circ(17.5f, 10.6f, 1.1f))
            // O sol (círculo da esquerda) aparece só onde a lua (direita) não cobre.
            br.paulo.tvlauncher.weather.NightSky.Kind.ECLIPSE_SUN -> listOf(StrokeGlyph.Forma.Menos(StrokeGlyph.Forma.Circ(14f, 17f, 9.5f), StrokeGlyph.Forma.Circ(19f, 14f, 8f)), StrokeGlyph.Forma.Circ(19f, 14f, 8f))
        }
    }
}

/**
 * Ícone de traço numa grade de 32 (base de [WorldGlyph] e [NightSkyGlyph]).
 * Como: cada ícone é uma lista de formas no padrão SVG que [SvgPath] converte em [Path] uma vez; o desenho só escala a
 * grade de 32 para o tamanho da view.
 */
open class StrokeGlyph(formas: List<Forma>, private val cor: Int, private val traco: Float = 2.1f) : Drawable() {

    sealed class Forma {
        class Linha(val d: String) : Forma()
        class Ret(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) : Forma()
        class Circ(val cx: Float, val cy: Float, val r: Float, val cheio: Boolean = false) : Forma()
        /** [a] sem a parte que [b] cobre (só contorno). */
        class Menos(val a: Circ, val b: Circ) : Forma()
    }

    // Os caminhos na grade de 32 (convertidos uma vez); os círculos cheios vão à parte.
    private val contorno = Path()
    private val cheio = Path()

    init {
        for (f in formas) when (f) {
            is Forma.Linha -> contorno.addPath(SvgPath.parse(f.d))
            is Forma.Ret -> contorno.addRoundRect(RectF(f.x, f.y, f.x + f.w, f.y + f.h), f.r, f.r, Path.Direction.CW)
            is Forma.Circ -> (if (f.cheio) cheio else contorno).addCircle(f.cx, f.cy, f.r, Path.Direction.CW)
            is Forma.Menos -> contorno.addPath(Path().apply {
                addCircle(f.a.cx, f.a.cy, f.a.r, Path.Direction.CW)
                op(Path().apply { addCircle(f.b.cx, f.b.cy, f.b.r, Path.Direction.CW) }, Path.Op.DIFFERENCE)
            })
        }
    }

    private val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = cor; strokeWidth = traco
    }
    private val tintaCheia = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = cor }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val s = minOf(b.width(), b.height()) / 32f
        val n = c.save()
        c.translate(b.left + (b.width() - 32f * s) / 2f, b.top + (b.height() - 32f * s) / 2f)
        c.scale(s, s)
        c.drawPath(contorno, tinta)
        c.drawPath(cheio, tintaCheia)
        c.restoreToCount(n)
    }

    override fun setAlpha(alpha: Int) { tinta.alpha = alpha; tintaCheia.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(cf: ColorFilter?) { tinta.colorFilter = cf; tintaCheia.colorFilter = cf; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = -1
    override fun getIntrinsicHeight() = -1
}

/**
 * Um leitor mínimo de "d" de SVG (M L H V A Z, absolutos e relativos): o bastante para os ícones desenhados no estudo.
 * O arco (A) vira centro + ângulos pela conta da especificação do SVG (F.6.5) e entra com [Path.arcTo].
 */
object SvgPath {
    fun parse(d: String): Path {
        val p = Path()
        val tokens = Regex("[MmLlHhVvAaZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?").findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = 'M'
        var x = 0f; var y = 0f; var x0 = 0f; var y0 = 0f
        fun num(): Float = tokens[i++].toFloat()
        // As flags do arco podem vir grudadas ("0 0 1-8"): o regex lê "1" e "-8" separados, então cada flag é um token.
        while (i < tokens.size) {
            val t = tokens[i]
            if (t[0].isLetter()) { cmd = t[0]; i++; if (cmd == 'Z' || cmd == 'z') { p.close(); x = x0; y = y0; continue } }
            val rel = cmd.isLowerCase()
            when (cmd.uppercaseChar()) {
                'M' -> { val nx = num(); val ny = num(); x = if (rel) x + nx else nx; y = if (rel) y + ny else ny
                    p.moveTo(x, y); x0 = x; y0 = y; cmd = if (rel) 'l' else 'L' }
                'L' -> { val nx = num(); val ny = num(); x = if (rel) x + nx else nx; y = if (rel) y + ny else ny; p.lineTo(x, y) }
                'H' -> { val nx = num(); x = if (rel) x + nx else nx; p.lineTo(x, y) }
                'V' -> { val ny = num(); y = if (rel) y + ny else ny; p.lineTo(x, y) }
                'A' -> {
                    val rx = num(); val ry = num(); val rot = num(); val grande = num() != 0f; val horario = num() != 0f
                    val nx = num(); val ny = num()
                    val x2 = if (rel) x + nx else nx; val y2 = if (rel) y + ny else ny
                    arco(p, x, y, x2, y2, rx, ry, rot, grande, horario)
                    x = x2; y = y2
                }
                else -> i++
            }
        }
        return p
    }

    private fun arco(p: Path, x1: Float, y1: Float, x2: Float, y2: Float, rx0: Float, ry0: Float, rotGraus: Float, grande: Boolean, horario: Boolean) {
        if (rx0 == 0f || ry0 == 0f) { p.lineTo(x2, y2); return }
        val phi = Math.toRadians(rotGraus.toDouble())
        val cos = Math.cos(phi); val sin = Math.sin(phi)
        val dx = (x1 - x2) / 2.0; val dy = (y1 - y2) / 2.0
        val x1p = cos * dx + sin * dy; val y1p = -sin * dx + cos * dy
        var rx = Math.abs(rx0.toDouble()); var ry = Math.abs(ry0.toDouble())
        val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1) { val s = Math.sqrt(lambda); rx *= s; ry *= s }
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        var coef = Math.sqrt(maxOf(0.0, num / den))
        if (grande == horario) coef = -coef
        val cxp = coef * rx * y1p / ry; val cyp = -coef * ry * x1p / rx
        val cx = cos * cxp - sin * cyp + (x1 + x2) / 2.0
        val cy = sin * cxp + cos * cyp + (y1 + y2) / 2.0
        fun ang(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val a = Math.toDegrees(Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy))
            return a
        }
        val ux = (x1p - cxp) / rx; val uy = (y1p - cyp) / ry
        val vx = (-x1p - cxp) / rx; val vy = (-y1p - cyp) / ry
        val inicio = ang(1.0, 0.0, ux, uy)
        var varre = ang(ux, uy, vx, vy)
        if (!horario && varre > 0) varre -= 360.0 else if (horario && varre < 0) varre += 360.0
        // Os ícones só usam arcos de círculo sem rotação: o oval é o retângulo do círculo.
        p.arcTo(RectF((cx - rx).toFloat(), (cy - ry).toFloat(), (cx + rx).toFloat(), (cy + ry).toFloat()), inicio.toFloat(), varre.toFloat(), false)
    }
}
