package br.paulo.tvlauncher.home

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * As três animações do ícone grande do mundo (Paulo, 25/11/2026: "algo incrível, mas não exagerado, e não tão curto";
 * escolheu os 3 como opção, com a Pequena cena de padrão). Prova aprovada: artifact "Ícones vivos dos mundos".
 *
 * Porquê: o respiro antigo (escala de 8 %) parecia parado e "simplista".
 * Para quê: [StripHome.WorldAnim.SCENE] faz a ação do mundo (a tela toca e enche a barra; o fone tem equalizador e solta
 * notas; o controle aperta o direcional e os botões; os apps trocam de lugar; os ajustes deslizam um de cada vez).
 * [StripHome.WorldAnim.LIGHT] deixa o desenho calmo e corre uma luz laranja pelo contorno, como neon.
 * [StripHome.WorldAnim.BOUNCE] acende as partes em sequência e dá um pulinho elástico no fim da volta.
 * Como: grade de 32 do [WorldGlyph], tempo [t] em segundos desde a troca de mundo; voltas de 5 a 7 s, sem fim.
 * Refs: SF Symbols (bounce, variable color, wiggle), transitions.dev (traço que se desenha), Mobbin Facebook "Life
 * updates" e Lifesum (ícones que contam uma cena).
 */
internal class WorldScenes(private val creme: Int, private val laranja: Int, private val w: Float) {

    var alfa = 255
    var filtro: android.graphics.ColorFilter? = null
        set(v) { field = v; st.colorFilter = v; fill.colorFilter = v }

    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val oval = RectF()
    private val r = RectF()

    private fun ret(l: Float, t: Float, rr: Float, b: Float, raio: Float) = Path().apply { addRoundRect(RectF(l, t, rr, b), raio, raio, Path.Direction.CW) }
    private val tela = ret(4f, 6f, 28f, 22.5f, 4f)
    private val pe = SvgPath.parse("M12 26.5h8")
    private val play = SvgPath.parse("M14 11.2l5.6 3.05L14 17.3z")
    private val arco = SvgPath.parse("M6 19.5v-3a10 10 0 0 1 20 0v3")
    private val conchaE = ret(4.5f, 18.5f, 10f, 27f, 2.75f)
    private val conchaD = ret(22f, 18.5f, 27.5f, 27f, 2.75f)
    private val ctrl = SvgPath.parse("M10.5 10h11a6.5 6.5 0 0 1 6.5 6.5v2.6a4.4 4.4 0 0 1-8 2.5l-1.3-1.6h-5.4L12 21.6a4.4 4.4 0 0 1-8-2.5v-2.6A6.5 6.5 0 0 1 10.5 10z")
    private val cruz = SvgPath.parse("M10.5 13.6v5M8 16.1h5")
    private val nota = SvgPath.parse("M2 8V0l5 1.5")

    /** Contornos (cada um com o seu comprimento) para o traço de luz. */
    private class Contorno(val p: Path, val l: Float)
    private fun contornos(vararg ps: Path): List<Contorno> = ps.flatMap { p ->
        val m = PathMeasure(p, false); val out = mutableListOf<Contorno>()
        do { val l = m.length; if (l > 0f) { val d = Path(); m.getSegment(0f, l, d, true); out += Contorno(d, l) } } while (m.nextContour())
        out
    }
    private val luzes: Map<CruzLogica.Categoria, List<Contorno>> by lazy {
        mapOf(
            CruzLogica.Categoria.ASSISTIR to contornos(tela, pe, play),
            CruzLogica.Categoria.OUVIR to contornos(arco, conchaE, conchaD),
            CruzLogica.Categoria.JOGAR to contornos(ctrl, cruz),
            CruzLogica.Categoria.APLICATIVOS to contornos(ret(5f, 5f, 14f, 14f, 2.6f), ret(18f, 5f, 27f, 14f, 2.6f), ret(5f, 18f, 14f, 27f, 2.6f),
                Path().apply { addCircle(22.5f, 22.5f, 4.6f, Path.Direction.CW) }),
            CruzLogica.Categoria.FERRAMENTAS to contornos(SvgPath.parse("M5 10.5h4.5M15.5 10.5H27M5 21.5h13M24 21.5h3"),
                Path().apply { addCircle(12.5f, 10.5f, 3f, Path.Direction.CW) }, Path().apply { addCircle(21f, 21.5f, 3f, Path.Direction.CW) }),
        )
    }

    // ---------- pincéis ----------
    private fun a(op: Float) = (alfa * op).toInt().coerceIn(0, 255)
    private fun traco(cor: Int, largura: Float, op: Float): Paint = st.apply { color = cor; strokeWidth = largura; alpha = a(op); pathEffect = null }
    private fun s(c: Canvas, p: Path, cor: Int = creme, largura: Float = w, op: Float = 1f) = c.drawPath(p, traco(cor, largura, op))
    private fun f(c: Canvas, p: Path, cor: Int, op: Float = 1f) = c.drawPath(p, fill.apply { color = cor; alpha = a(op) })
    private fun linha(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, cor: Int = creme, largura: Float = w, op: Float = 1f) =
        c.drawLine(x1, y1, x2, y2, traco(cor, largura, op))
    private fun circ(c: Canvas, x: Float, y: Float, raio: Float, cor: Int = creme, largura: Float = w, op: Float = 1f) =
        c.drawCircle(x, y, raio, traco(cor, largura, op))
    private fun bola(c: Canvas, x: Float, y: Float, raio: Float, cor: Int, op: Float = 1f) =
        c.drawCircle(x, y, raio, fill.apply { color = cor; alpha = a(op) })
    private fun quadrado(c: Canvas, x: Float, y: Float, meio: Float, cor: Int, cheio: Boolean) {
        r.set(x - meio, y - meio, x + meio, y + meio)
        if (cheio) c.drawRoundRect(r, 2.6f, 2.6f, fill.apply { color = laranja; alpha = a(0.25f) })
        c.drawRoundRect(r, 2.6f, 2.6f, traco(cor, w, 1f))
    }
    /** Arco pela corda vertical que começa em ([x], [y]) e desce [corda]; bojo para a direita ou para a esquerda. */
    private fun arcoCorda(c: Canvas, x: Float, y: Float, corda: Float, raio: Float, direita: Boolean, cor: Int, largura: Float, op: Float) {
        val meio = corda / 2f
        val d = sqrt(max(0f, raio * raio - meio * meio))
        val th = Math.toDegrees(asin((meio / raio).coerceIn(-1f, 1f).toDouble())).toFloat()
        val cx = if (direita) x - d else x + d
        oval.set(cx - raio, y + meio - raio, cx + raio, y + meio + raio)
        c.drawArc(oval, if (direita) -th else 180f - th, 2f * th, false, traco(cor, largura, op))
    }

    fun desenhar(c: Canvas, cat: CruzLogica.Categoria, anim: StripHome.WorldAnim, t: Float) = when (anim) {
        StripHome.WorldAnim.SCENE -> cena(c, cat, t)
        StripHome.WorldAnim.LIGHT -> luz(c, cat, t)
        StripHome.WorldAnim.BOUNCE -> pulo(c, cat, t)
    }

    // ---------- A: pequena cena ----------
    private fun cena(c: Canvas, cat: CruzLogica.Categoria, t: Float) {
        when (cat) {
            CruzLogica.Categoria.ASSISTIR -> {
                val q = Curvas.fr(t / 7f)
                val batida = max(0f, sin(q * 2f * PI.toFloat() * 2f)) * 0.1f      // o play "aperta" duas vezes por volta
                val anel = Curvas.fr(t / 3.5f)
                s(c, tela); s(c, pe)
                c.save(); c.clipPath(tela)
                val gx = -10f + q * 50f
                linha(c, gx, 4f, gx - 6f, 26f, creme, 3f, 0.18f)                    // brilho passando pela tela
                c.restore()
                circ(c, 16.3f, 14.25f, 2f + anel * 7f, laranja, 1.2f, (1f - anel) * 0.7f)
                c.save(); c.scale(1f + batida, 1f + batida, 16.3f, 14.25f)
                if (batida > 0.06f) f(c, play, laranja, 0.9f)
                s(c, play); c.restore()
                linha(c, 7f, 19.6f, 7f + 18f * Curvas.io(q), 19.6f, laranja, 1.6f)      // a barra de progresso enche
            }
            CruzLogica.Categoria.OUVIR -> {
                val sobe = sin(t * 2f * PI.toFloat() / 4.5f)
                c.save(); c.translate(0f, -sobe); s(c, arco); s(c, conchaE); s(c, conchaD); c.restore()
                floatArrayOf(12.6f, 15f, 17.4f, 19.8f).forEachIndexed { i, x ->       // equalizador entre as conchas
                    val h = 1.5f + 4.5f * abs(sin(t * 2.2f + i * 1.3f) * sin(t * 1.1f + i))
                    linha(c, x, 25f, x, 25f - h, if (i % 2 == 1) creme else laranja, 1.5f)
                }
                for (k in 0..1) {                                                    // duas notas sobem e somem
                    val q = Curvas.fr(t / 5f + k / 2f)
                    val x = if (k == 1) 24f + q * 3f else 8f - q * 3f
                    val op = sin(q * PI.toFloat())
                    c.save(); c.translate(x, 12f - q * 9f); c.scale(0.55f, 0.55f)
                    s(c, nota, laranja, 2.2f, op); bola(c, 0f, 8f, 2f, laranja, op); c.restore()
                }
            }
            CruzLogica.Categoria.JOGAR -> {
                val q = Curvas.fr(t / 7f)
                val lado = floor(q * 4f).toInt().coerceIn(0, 3)
                c.save(); c.rotate(sin(t * 2f * PI.toFloat() / 7f) * 6f, 16f, 16f)
                s(c, ctrl); s(c, cruz, creme, w, 0.55f)
                val bracos = arrayOf(floatArrayOf(10.5f, 13.6f), floatArrayOf(13f, 16.1f), floatArrayOf(10.5f, 18.6f), floatArrayOf(8f, 16.1f))
                val aperto = sin(Curvas.fr(q * 4f) * PI.toFloat())
                linha(c, 10.5f, 16.1f, bracos[lado][0], bracos[lado][1], laranja, w + 0.6f * aperto)   // o direcional aperta em volta
                val b1 = max(0f, sin(t * 2.4f)); val b2 = max(0f, sin(t * 2.4f + 2.2f))
                bola(c, 20.6f, 14.6f, 0.9f + 0.8f * b1, if (b1 > 0.5f) laranja else creme)
                bola(c, 23.4f, 17.4f, 0.9f + 0.8f * b2, if (b2 > 0.5f) laranja else creme)
                c.restore()
                if (b1 > 0.05f) circ(c, 20.6f, 14.6f, 2f + 4f * b1, laranja, 0.8f, (1f - b1) * 0.5f)
            }
            CruzLogica.Categoria.APLICATIVOS -> {
                val vagas = arrayOf(floatArrayOf(9.5f, 9.5f), floatArrayOf(22.5f, 9.5f), floatArrayOf(22.5f, 22.5f), floatArrayOf(9.5f, 22.5f))
                val passo = 2.4f
                val k = floor(t / passo).toInt()
                val u = ((t / passo - k) / 0.45f).coerceIn(0f, 1f)
                val e = Curvas.io(u)
                for (i in 0 until 4) {                                               // as peças trocam de lugar em roda
                    val de = vagas[(i + k) % 4]; val para = vagas[(i + k + 1) % 4]
                    val x = de[0] + (para[0] - de[0]) * e; val y = de[1] + (para[1] - de[1]) * e
                    val esc = 1f + sin(e * PI.toFloat()) * 0.15f
                    val chegou = (i + k + 1) % 4 == 0 && u >= 1f
                    val cor = if (chegou) laranja else creme
                    if (i == 3) { if (chegou) bola(c, x, y, 4.6f * esc, laranja, 0.25f); circ(c, x, y, 4.6f * esc, cor) }
                    else quadrado(c, x, y, 4.5f * esc, cor, chegou)
                }
            }
            CruzLogica.Categoria.FERRAMENTAS -> {
                val alvos = arrayOf(floatArrayOf(9f, 22f), floatArrayOf(21f, 11f), floatArrayOf(14f, 24f))
                floatArrayOf(8f, 16f, 24f).forEachIndexed { i, y ->                   // três ajustes deslizam, um de cada vez
                    val q = Curvas.fr((t + i * 2.1f) / 6.5f)
                    val m = if (q < 0.5f) Curvas.mola(q * 2.2f) else 1f - Curvas.mola((q - 0.5f) * 2.2f)
                    val x = alvos[i][0] + (alvos[i][1] - alvos[i][0]) * m.coerceIn(-0.2f, 1.2f)
                    val mexendo = q < 0.35f || (q > 0.5f && q < 0.85f)
                    linha(c, 5f, y, x - 3f, y, creme, w, 0.9f); linha(c, x + 3f, y, 27f, y, creme, w, 0.9f)
                    if (mexendo) { linha(c, 5f, y, x - 3f, y, laranja, w, 0.55f); bola(c, x, y, 3f, laranja, 0.25f) }
                    circ(c, x, y, 3f, if (mexendo) laranja else creme)
                }
            }
            else -> Unit
        }
    }

    // ---------- B: traço de luz ----------
    private fun luz(c: Canvas, cat: CruzLogica.Categoria, t: Float) {
        val (vel, fase) = when (cat) {
            CruzLogica.Categoria.OUVIR -> 0.16f to 0.3f
            CruzLogica.Categoria.JOGAR -> 0.14f to 0f
            CruzLogica.Categoria.APLICATIVOS -> 0.2f to 0f
            CruzLogica.Categoria.FERRAMENTAS -> 0.17f to 0f
            else -> 0.16f to 0f
        }
        val anda = Curvas.fr(t * vel + fase)
        for (k in luzes[cat].orEmpty()) {
            c.drawPath(k.p, traco(creme, w, 0.45f))
            val l = k.l
            c.drawPath(k.p, traco(laranja, w + 0.4f, 1f).apply { pathEffect = DashPathEffect(floatArrayOf(0.16f * l, 0.84f * l), l - anda * l) })
            c.drawPath(k.p, traco(creme, w * 0.6f, 0.9f).apply {
                pathEffect = DashPathEffect(floatArrayOf(0.03f * l, 0.97f * l), Curvas.fr((l - anda * l - 0.13f * l) / l) * l)
            })
        }
        st.pathEffect = null
        if (cat == CruzLogica.Categoria.JOGAR) { bola(c, 20.6f, 14.6f, 0.95f, creme); bola(c, 23.4f, 17.4f, 0.95f, creme) }
    }

    // ---------- C: pulo e acende ----------
    private fun pulo(c: Canvas, cat: CruzLogica.Categoria, t: Float) {
        val T = 5.5f
        val (sx, sy) = Curvas.pulinho(t, T)
        fun on(i: Int, n: Int) = Curvas.acende(t, T, i, n)
        when (cat) {
            CruzLogica.Categoria.ASSISTIR -> {
                c.save(); c.scale(sx, sy, 16f, 26.5f)
                s(c, tela); s(c, pe); if (on(2, 3)) f(c, play, laranja); s(c, play); c.restore()
                for (i in 0..2) arcoCorda(c, 20f + i * 2f, 11.5f - i * 0.6f, 5.5f + i * 1.2f, 3f + i * 2f, true,
                    if (on(i, 3)) laranja else creme, 1.3f, if (on(i, 3)) 1f else 0.18f)
            }
            CruzLogica.Categoria.OUVIR -> {
                c.save(); c.scale(sx, sy, 16f, 27f); s(c, arco); s(c, conchaE); s(c, conchaD); c.restore()
                for (i in 0..2) {
                    val rr = 3f + i * 2.2f; val cor = if (on(i, 3)) laranja else creme; val op = if (on(i, 3)) 1f else 0.18f
                    arcoCorda(c, 1f - i * 1.3f, 19.5f - rr * 0.6f, rr * 1.2f, rr, false, cor, 1.3f, op)
                    arcoCorda(c, 31f + i * 1.3f, 19.5f - rr * 0.6f, rr * 1.2f, rr, true, cor, 1.3f, op)
                }
            }
            CruzLogica.Categoria.JOGAR -> {
                val q = Curvas.fr(t / T)
                val balanco = if (q < 0.35f) sin(q / 0.35f * PI.toFloat() * 4f) * 10f * (1f - q / 0.35f) else 0f
                c.save(); c.rotate(balanco, 16f, 22f); c.scale(sx, sy, 16f, 22f)
                s(c, ctrl); s(c, cruz, if (on(2, 3)) laranja else creme)
                bola(c, 20.6f, 14.6f, 1.05f, if (on(0, 3)) laranja else creme)
                bola(c, 23.4f, 17.4f, 1.05f, if (on(1, 3)) laranja else creme)
                c.restore()
            }
            CruzLogica.Categoria.APLICATIVOS -> {
                val ordem = intArrayOf(0, 1, 3, 2)
                val formas = arrayOf(floatArrayOf(9.5f, 9.5f), floatArrayOf(22.5f, 9.5f), floatArrayOf(9.5f, 22.5f), floatArrayOf(22.5f, 22.5f))
                c.save(); c.scale(sx, sy, 16f, 27f)
                formas.forEachIndexed { i, p ->
                    val aceso = on(ordem.indexOf(i), 4); val k = if (aceso) 1.08f else 1f; val cor = if (aceso) laranja else creme
                    if (i == 3) { if (aceso) bola(c, p[0], p[1], 4.6f * k, laranja, 0.3f); circ(c, p[0], p[1], 4.6f * k, cor) }
                    else quadrado(c, p[0], p[1], 4.5f * k, cor, aceso)
                }
                c.restore()
            }
            CruzLogica.Categoria.FERRAMENTAS -> {
                val q = Curvas.fr(t / T)
                val x1 = 8f + 9f * Curvas.io((q / 0.39f).coerceIn(0f, 1f))
                val x2 = 24f - 9f * Curvas.io(((q - 0.39f) / 0.39f).coerceIn(0f, 1f))
                val on1 = q < 0.39f; val on2 = q >= 0.39f && q < 0.78f
                c.save(); c.scale(sx, sy, 16f, 27f)
                linha(c, 5f, 10.5f, x1 - 3f, 10.5f); linha(c, x1 + 3f, 10.5f, 27f, 10.5f); linha(c, 5f, 10.5f, x1 - 3f, 10.5f, laranja, w, if (on1) 1f else 0.5f)
                linha(c, 5f, 21.5f, x2 - 3f, 21.5f); linha(c, x2 + 3f, 21.5f, 27f, 21.5f); linha(c, x2 + 3f, 21.5f, 27f, 21.5f, laranja, w, if (on2) 1f else 0.5f)
                circ(c, x1, 10.5f, 3f, if (on1) laranja else creme); circ(c, x2, 21.5f, 3f, if (on2) laranja else creme)
                c.restore()
            }
            else -> Unit
        }
    }

    /** As curvas, puras (teste na JVM). */
    object Curvas {
        fun fr(x: Float) = x - floor(x)
        /** ease in-out cúbico. */
        fun io(x: Float): Float = if (x < 0.5f) 4f * x * x * x else { val u = -2f * x + 2f; 1f - u * u * u / 2f }
        /** chega com um quique (mola). */
        fun mola(x: Float): Float { val u = x.coerceIn(0f, 1f); return 1f - exp(-6f * u) * kotlin.math.cos(9f * u) }
        /** pulinho elástico no fim de cada volta de [periodo] s: (escala x, escala y). */
        fun pulinho(t: Float, periodo: Float): Pair<Float, Float> {
            val q = fr(t / periodo); if (q < 0.78f) return 1f to 1f
            val u = (q - 0.78f) / 0.22f
            val s = sin(u * 2f * PI.toFloat()) * exp(-3f * u) * 0.16f
            return (1f - s * 0.6f) to (1f + s)
        }
        /** a parte [i] de [n] está acesa? acendem em sequência até 78 % da volta e apagam juntas no pulinho. */
        fun acende(t: Float, periodo: Float, i: Int, n: Int): Boolean { val q = fr(t / periodo) / 0.78f; return q >= (i + 1f) / (n + 1f) && q <= 1f }
    }
}
