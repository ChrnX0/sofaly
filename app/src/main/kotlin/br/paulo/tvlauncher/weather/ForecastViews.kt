package br.paulo.tvlauncher.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import br.paulo.tvlauncher.Rtl
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Os desenhos da tela Previsão. Ficam no :app (são tela); os dados e os textos da previsão moram no módulo :weather.

/** Escala de 0 a 300+ do índice de qualidade do ar (verde a vinho) com um marcador onde o valor está. */
internal class BarraAqi(ctx: Context, val aqi: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) = Rtl.espelhar(this, c) { desenhar(c) }   // 16.8: o tempo anda para a esquerda no árabe
    private fun desenhar(c: Canvas) {
        val h = height.toFloat()
        p.shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
            intArrayOf(0xFF7BE0A0.toInt(), 0xFFF2E26B.toInt(), 0xFFFFB566.toInt(), 0xFFFF7A6B.toInt(), 0xFFD08CFF.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, width.toFloat(), h), h / 2, h / 2, p); p.shader = null
        p.color = Color.WHITE
        c.drawCircle(((aqi.coerceIn(0, 300) / 300f) * (width - h) + h / 2), h / 2, h * 0.9f, p)
    }
}

/** O arco do sol: tracejado do nascer ao pôr, com o sol (bolinha amarela) na posição de agora ([fracao] 0 = nascer, 1 = pôr). */
internal class ArcoSol(ctx: Context, val fracao: Float) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) = Rtl.espelhar(this, c) { desenhar(c) }   // 16.8: o tempo anda para a esquerda no árabe
    private fun desenhar(c: Canvas) {
        val d = resources.displayMetrics.density
        val raioX = width / 2f - 12 * d; val cy = height - 4 * d; val raioY = cy - 12 * d; val cx = width / 2f   // 12: o halo (11) do sol no alto cabe inteiro
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = 0x55FFFFFF
        p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(5 * d, 5 * d), 0f)
        c.drawArc(RectF(cx - raioX, cy - raioY, cx + raioX, cy + raioY), 180f, 180f, false, p); p.pathEffect = null
        p.color = 0x55FFFFFF; c.drawLine(8 * d, cy, width - 8 * d, cy, p)
        val ang = PI * (1 - fracao.coerceIn(0f, 1f))
        val x = cx + raioX * cos(ang).toFloat(); val y = cy - raioY * sin(ang).toFloat()
        p.style = Paint.Style.FILL
        p.color = 0x55FFD36B; c.drawCircle(x, y, 11 * d, p)
        p.color = 0xFFFFD36B.toInt(); c.drawCircle(x, y, 6.5f * d, p)
    }
}
