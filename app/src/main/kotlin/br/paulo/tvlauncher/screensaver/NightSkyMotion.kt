package br.paulo.tvlauncher.screensaver

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import br.paulo.tvlauncher.weather.NightSky

/**
 * O movimento do céu da noite na linha do descanso de tela (estudo de 24/11/2026,
 * https://claude.ai/artifact/NSxNSFygoiPJAuB4yLfnv9 ; refs Mobbin: Lumy, Moonlitt, Moonly).
 *
 * Porquê: o ícone parado diz "tem algo no céu"; o movimento mostra O QUE vai acontecer, sem texto a mais.
 * Para quê: estação = um ponto que atravessa a meia cúpula pontilhada (altura = a altura máxima real da passagem);
 * meteoros = três riscos que caem um de cada vez; superlua = a lua nasce e acende um halo; eclipse = o disco escuro
 * passa na frente do sol e deixa o anel.
 * Como: desenho numa grade de 200 x 100 (horizonte em y = 88), ciclos lentos (7-8 s) a ~25 quadros/s, só enquanto
 * está na tela ([start]/[stop]). Sem animações no sistema, quem chama usa o ícone parado.
 */
class NightSkyMotion(private val kind: NightSky.Kind, private val cor: Int, private val fundo: Int, alturaMax: Int = 60) :
    Drawable(), Animatable {

    private var rodando = false
    private val inicio = SystemClock.uptimeMillis()
    private val ciclo = if (kind == NightSky.Kind.ISS) 7_000L else if (kind == NightSky.Kind.METEORS) 6_000L else 8_000L

    private fun tinta(a: Float, w: Float, cheio: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cor; alpha = (a * 255).toInt(); strokeWidth = w; strokeCap = Paint.Cap.ROUND
        style = if (cheio) Paint.Style.FILL else Paint.Style.STROKE
    }
    private val horizonte = tinta(0.35f, 1.5f)
    private val pontos = tinta(0.45f, 1.6f).apply { pathEffect = DashPathEffect(floatArrayOf(1f, 5f), 0f) }
    private val traco = tinta(1f, 2f)
    private val cheio = tinta(1f, 0f, true)
    private val brilho = tinta(0.18f, 0f, true)
    private val cratera = tinta(1f, 1.5f)
    private val anelP = tinta(0.7f, 1.2f)
    private val solP = tinta(0.9f, 0f, true)
    private val riscoP = tinta(1f, 2f)
    private val haloP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(100f, 60f, 44f, (cor and 0xFFFFFF) or 0x80000000.toInt(), cor and 0xFFFFFF, Shader.TileMode.CLAMP)
    }
    private val apaga = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fundo; style = Paint.Style.FILL }

    // Estação: arco de 30 a 170 no horizonte; o topo sobe com a altura máxima (10° rasante ... 90° no alto).
    private val arco = Path().apply {
        val topo = 88f - 90f * (alturaMax.coerceIn(10, 90) / 90f)
        moveTo(30f, 88f); quadTo(100f, 2f * topo - 88f, 170f, 88f)
    }
    private val medida = PathMeasure(arco, false)
    private val pos = FloatArray(2)

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val t = ((SystemClock.uptimeMillis() - inicio) % ciclo) / ciclo.toFloat()
        val s = minOf(b.width() / 200f, b.height() / 100f)
        val n = c.save()
        c.translate(b.left + (b.width() - 200f * s) / 2f, b.top + (b.height() - 100f * s) / 2f)
        c.scale(s, s)
        when (kind) {
            NightSky.Kind.ISS -> {
                c.drawLine(14f, 88f, 186f, 88f, horizonte)
                c.drawPath(arco, pontos)
                medida.getPosTan(medida.length * t, pos, null)
                c.drawCircle(pos[0], pos[1], 9f, brilho)
                c.drawCircle(pos[0], pos[1], 3.4f, cheio)
            }
            NightSky.Kind.METEORS -> {
                for (i in 0..2) {
                    // cada risco vive no seu terço do ciclo: cresce (12%) e some pela cauda (até 30%)
                    val u = ((t - i / 3f) + 1f) % 1f
                    if (u > 0.3f) continue
                    val x0 = 40f + i * 45f; val y0 = 8f + i * 6f
                    val cab = (u / 0.12f).coerceAtMost(1f); val cau = ((u - 0.12f) / 0.18f).coerceIn(0f, 1f)
                    riscoP.alpha = ((1f - cau) * 255).toInt()
                    c.drawLine(x0 + 40f * cau, y0 + 40f * cau, x0 + 40f * cab, y0 + 40f * cab, riscoP)
                }
                c.drawLine(14f, 88f, 186f, 88f, horizonte)
            }
            NightSky.Kind.SUPERMOON -> {
                // sobe até 45%, fica, desce no fim; o halo acende depois que ela chega
                val sobe = when { t < 0.45f -> 1f - t / 0.45f; t < 0.9f -> 0f; else -> (t - 0.9f) / 0.1f }
                val halo = when { t < 0.35f -> 0f; t < 0.6f -> (t - 0.35f) / 0.25f; t < 0.9f -> 1f; else -> 1f - (t - 0.9f) / 0.1f }
                val n2 = c.save()
                c.clipRect(0f, 0f, 200f, 88f)
                val dy = 40f * sobe
                if (halo > 0f) { haloP.alpha = (halo * 255).toInt(); c.drawCircle(100f, 60f, 44f, haloP) }
                c.drawCircle(100f, 56f + dy, 22f, traco)
                c.drawCircle(92f, 50f + dy, 4f, cratera)
                c.drawCircle(108f, 62f + dy, 5.5f, cratera)
                c.restoreToCount(n2)
                c.drawLine(14f, 88f, 186f, 88f, horizonte)
            }
            NightSky.Kind.ECLIPSE_SUN -> {
                c.drawCircle(100f, 48f, 24f, solP)
                val anel = when { t < 0.4f -> 0f; t < 0.5f -> (t - 0.4f) / 0.1f; t < 0.62f -> 1f; t < 0.75f -> 1f - (t - 0.62f) / 0.13f; else -> 0f }
                if (anel > 0f) { anelP.alpha = (0.7f * anel * 255).toInt(); c.drawCircle(100f, 48f, 31f, anelP) }
                val x = when { t < 0.45f -> 44f + 56f * (t / 0.45f); t < 0.65f -> 100f; else -> 100f + 56f * ((t - 0.65f) / 0.35f) }
                c.drawCircle(x, 48f, 24f, apaga)
                c.drawLine(14f, 88f, 186f, 88f, horizonte)
            }
        }
        c.restoreToCount(n)
        if (rodando) scheduleSelf(proximo, SystemClock.uptimeMillis() + QUADRO_MS)
    }

    private val proximo = Runnable { invalidateSelf() }

    override fun start() { if (!rodando) { rodando = true; invalidateSelf() } }
    override fun stop() { rodando = false; unscheduleSelf(proximo) }
    override fun isRunning() = rodando

    override fun setAlpha(alpha: Int) { invalidateSelf() }
    override fun setColorFilter(cf: ColorFilter?) { invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = -1
    override fun getIntrinsicHeight() = -1

    companion object { const val QUADRO_MS = 40L }
}
