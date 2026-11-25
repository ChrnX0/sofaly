package br.paulo.tvlauncher.screensaver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import br.paulo.tvlauncher.DesignTokens
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.weather.ForecastData
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

/**
 * Os 7 jeitos da hora do descanso ([SleepingLook.Clock]). Monta a hora ([relogio]) e o que mora embaixo dela no
 * jeito escolhido ([extra]: o despertador do Anel, a régua do dia, a frase que troca). O descanso põe a data e a dica do
 * guarda-chuva entre os dois (menos na Frase, que é só a hora e a frase, como no Oura).
 *
 * Porquê: a hora é o que se lê de longe; o Paulo achou que "tem como ficar melhor" e escolheu deixar todos os jeitos
 * como opção (25/11/2026). Como: TextClock (o Android acerta a hora e o formato 12/24 h); o que anda (pontos que
 * respiram, fio do minuto, ponto do anel) redesenha devagar e para junto com o descanso ([parar]) ou sem movimento.
 */
internal class SleepingClock(
    private val ctx: Context,
    val estilo: SleepingLook.Clock,
    private val fina: Typeface,
    private val leve: Typeface,
    private val movimento: Boolean,
) {
    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d + 0.5f).toInt()
    private val ui = Handler(Looper.getMainLooper())
    private val animados = mutableListOf<View>()

    /** A Frase esconde a data; os outros mostram. */
    val mostraData: Boolean get() = estilo != SleepingLook.Clock.PHRASE

    val relogio: View = montarRelogio()
    val extra: View? = montarExtra()

    // ------------------------------------------------------------------ hora

    private fun relogioTexto(f12: String, f24: String, sp: Float, face: Typeface, alfa: Float = 1f) = TextClock(ctx).apply {
        format12Hour = f12; format24Hour = f24
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(Estilo.TEXTO); typeface = face; includeFontPadding = false
        setShadowLayer(10 * d, 0f, d, 0x59000000); isSingleLine = true; alpha = alfa
        fontFeatureSettings = "tnum"   // algarismos da mesma largura: a hora não dança quando o minuto vira
    }

    private fun montarRelogio(): View = when (estilo) {
        SleepingLook.Clock.THIN -> relogioTexto("h:mm", "HH:mm", 76f, fina)
        // Hora firme, minuto mais claro, e os dois pontos respirando devagar (4 s).
        SleepingLook.Clock.SPLIT -> LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(relogioTexto("h", "HH", 76f, leve))
            addView(Respira(), LinearLayout.LayoutParams(dp(26), dp(52)))
            addView(relogioTexto("mm", "mm", 76f, fina, 0.62f))
        }
        // Hora em cima do minuto (StandBy do iPhone): ocupa menos largura.
        SleepingLook.Clock.STACKED -> LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(relogioTexto("h", "HH", 64f, fina))
            addView(relogioTexto("mm", "mm", 64f, fina, 0.62f), LinearLayout.LayoutParams(-2, -2).apply { topMargin = -dp(4) })
        }
        // Traço finíssimo e um fio embaixo que enche a cada minuto (laranja só como progresso, régua de estilo).
        SleepingLook.Clock.MINUTE -> LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val h = relogioTexto("h:mm", "HH:mm", 84f, fina)
            addView(h)
            addView(FioMinuto(), LinearLayout.LayoutParams(-1, dp(3)).apply { topMargin = dp(4) })
        }
        // TIDE (mobbin.com/screens/6da072f3-2c5d-493e-96b4-05869d8f5e9d): hora sobre minuto dentro de um anel fino; um
        // ponto de luz dá a volta a cada minuto.
        SleepingLook.Clock.RING -> FrameLayout(ctx).apply {
            addView(Anel(), FrameLayout.LayoutParams(dp(150), dp(150)))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(relogioTexto("h", "HH", 50f, fina))
                addView(relogioTexto("mm", "mm", 50f, fina, 0.5f))
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        SleepingLook.Clock.RULER -> relogioTexto("h:mm", "HH:mm", 76f, fina)
        // Oura (d4f18703) e Pillow (6e7faed3): um rótulo pequeno e espaçado e a hora bem grande.
        SleepingLook.Clock.PHRASE -> LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(ctx).apply {
                text = ctx.getString(R.string.pv_agora).lowercase(); letterSpacing = 0.32f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(Estilo.TEXTO_2); typeface = leve
            })
            addView(relogioTexto("h:mm", "HH:mm", 96f, fina))
        }
    }

    // ------------------------------------------------------------------ o que mora embaixo

    private var pilula: TextView? = null
    private var regua: Regua? = null
    private var frase: TextView? = null

    private fun montarExtra(): View? = when (estilo) {
        SleepingLook.Clock.RING -> TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setTextColor(Estilo.TEXTO); typeface = leve
            background = Estilo.vidro(ctx, 99); setPaddingRelative(dp(14), dp(4), dp(14), dp(5)); visibility = View.GONE
            pilula = this
        }
        SleepingLook.Clock.RULER -> Regua().also { regua = it }
        SleepingLook.Clock.PHRASE -> TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f); setTextColor(0xD1FFF3E6.toInt()); typeface = leve; maxLines = 2
            setShadowLayer(10 * d, 0f, d, 0x59000000)
            frase = this
        }
        else -> null
    }

    /** Lado do cartão/tamanho da régua: a largura que a régua pode ocupar. */
    fun larguraExtra(): Int = when (estilo) { SleepingLook.Clock.RULER -> dp(400); else -> -2 }

    private var frases: List<String> = emptyList()
    private var iFrase = 0

    /**
     * Dados novos (a cada 15 min e quando a previsão chega). [despertador] = "7:00 amanhã" (até 2 dias) ou null;
     * [fraseDia] = a frase do dia do Clima; [porDoSol] = instante do próximo pôr do sol (ou null).
     */
    fun atualizar(f: ForecastData?, tempAgora: Int?, despertador: String?, fraseDia: String?, porDoSol: Long?) {
        pilula?.let { p -> p.text = despertador?.let { "⏰  $it" } ?: ""; p.visibility = if (despertador == null) View.GONE else View.VISIBLE }
        regua?.dados(f, tempAgora, porDoSol)
        frase?.let { t ->
            val agora = System.currentTimeMillis()
            frases = listOfNotNull(
                fraseDia,
                porDoSol?.takeIf { it > agora }?.let { val m = ((it - agora) / 60_000L).toInt()
                    ctx.getString(R.string.slk_por_em, if (m >= 60) ctx.getString(R.string.sh_horas_min, m / 60, m % 60) else "$m min") },
                despertador?.let { "${ctx.getString(R.string.lbl_despertador)}: $it" },
            )
            iFrase = iFrase.coerceAtMost((frases.size - 1).coerceAtLeast(0))
            t.text = frases.getOrNull(iFrase) ?: ""
            ui.removeCallbacks(trocaFrase)
            if (frases.size > 1) ui.postDelayed(trocaFrase, TROCA_FRASE_MS)
        }
    }

    private val trocaFrase = object : Runnable {
        override fun run() {
            val t = frase ?: return
            if (frases.size < 2) return
            iFrase = (iFrase + 1) % frases.size
            if (!movimento) t.text = frases[iFrase]
            else t.animate().alpha(0f).setDuration(700).withEndAction { t.text = frases[iFrase]; t.animate().alpha(1f).setDuration(700).start() }.start()
            ui.postDelayed(this, TROCA_FRASE_MS)
        }
    }

    fun parar() { ui.removeCallbacksAndMessages(null); animados.clear() }

    // ------------------------------------------------------------------ peças que se desenham

    /** Repinta [v] a cada [ms] enquanto estiver na tela (e só com movimento). */
    private fun andar(v: View, ms: Long) {
        if (!movimento) return
        animados += v
        val r = object : Runnable { override fun run() { if (v !in animados) return; v.invalidate(); ui.postDelayed(this, ms) } }
        ui.postDelayed(r, ms)
    }

    private fun segundosDoMinuto(): Float = (System.currentTimeMillis() % 60_000L) / 60_000f

    /** Os dois pontos da hora que respiram (opacidade 0.9 -> 0.35 -> 0.9 em 4 s). */
    private inner class Respira : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Estilo.TEXTO }
        init { andar(this, 100) }
        override fun onDraw(c: Canvas) {
            val t = (System.currentTimeMillis() % 4000L) / 4000f
            p.alpha = if (!movimento) 230 else (255 * (0.625f + 0.275f * cos(t * 2 * Math.PI).toFloat())).toInt()
            val r = dp(4).toFloat(); val x = width / 2f
            c.drawCircle(x, height * 0.36f, r, p); c.drawCircle(x, height * 0.68f, r, p)
        }
    }

    /** O fio do minuto: trilho claro e a parte cheia em laranja (progresso). */
    private inner class FioMinuto : View(ctx) {
        private val trilho = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2EFFF3E6 }
        private val cheio = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DesignTokens.ORANGE }
        init { andar(this, 250) }
        // Pede largura zero: a coluna (WRAP) fica da largura da hora e depois estica o fio até ela. Com MATCH_PARENT puro
        // o fio pedia a tela inteira e empurrava o tempo para fora (Sala, 25/11/2026).
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(
            if (MeasureSpec.getMode(w) == MeasureSpec.EXACTLY) MeasureSpec.getSize(w) else 0, MeasureSpec.getSize(h))
        override fun onDraw(c: Canvas) {
            val r = height / 2f
            c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, trilho)
            c.drawRoundRect(0f, 0f, width * segundosDoMinuto(), height.toFloat(), r, r, cheio)
        }
    }

    /** O anel fino e o ponto de luz que dá a volta em 1 minuto. */
    private inner class Anel : View(ctx) {
        private val aro = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d; color = 0x38FFF3E6 }
        private val ponto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Estilo.TEXTO; setShadowLayer(8 * d, 0f, 0f, 0xB3FFF3E6.toInt()) }
        init { setLayerType(LAYER_TYPE_SOFTWARE, null); andar(this, 66) }   // sombra do ponto pede software (view pequena)
        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = minOf(cx, cy) - 6 * d
            c.drawCircle(cx, cy, r, aro)
            val a = segundosDoMinuto() * 2 * Math.PI - Math.PI / 2
            c.drawCircle(cx + (r * cos(a)).toFloat(), cy + (r * sin(a)).toFloat(), 4.5f * d, ponto)
        }
    }

    /**
     * A régua das próximas 12 horas ((Not Boring) Weather e863133e + Tempo da Apple 9ff6bb7f): trilho claro, azul onde a
     * chance de chuva passa de [CHUVA_NA_REGUA]%, um traço dourado no pôr do sol, o ponto de "agora" na ponta. Embaixo:
     * "agora 30°", o pôr do sol (dourado), +6 h e +12 h com a temperatura.
     */
    inner class Regua : View(ctx) {
        private val trilho = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x29FFF3E6 }
        private val chuva = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD98EC5FF.toInt() }
        private val sol = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OURO }
        private val agora = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Estilo.TEXTO }
        private val aro = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8C0E0D14.toInt() }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18f, ctx.resources.displayMetrics); color = Estilo.TEXTO_2; typeface = leve
            setShadowLayer(8 * d, 0f, d, 0x59000000)
        }
        private var faixas: List<Pair<Float, Float>> = emptyList()
        private var solEm: Float? = null
        private var rotulos: List<Triple<Float, String, Int>> = emptyList()   // posição 0..1, texto, cor

        init {
            contentDescription = ctx.getString(R.string.slk_regua)
            minimumHeight = dp(52)
        }

        fun dados(f: ForecastData?, tempAgora: Int?, porDoSol: Long?) {
            val agoraMs = System.currentTimeMillis()
            faixas = if (f == null) emptyList() else ReguaConta.faixasDeChuva(f.hChuva.drop(f.i0).take(HORAS + 1), CHUVA_NA_REGUA)
            solEm = porDoSol?.let { ((it - agoraMs) / 3_600_000f / HORAS).takeIf { x -> x in 0f..1f } }
            val hf = android.text.format.DateFormat.getTimeFormat(ctx)
            val r = mutableListOf<Triple<Float, String, Int>>()
            r += Triple(0f, listOfNotNull(ctx.getString(R.string.pv_agora).lowercase(), tempAgora?.let { "$it°" }).joinToString(" "), Estilo.TEXTO_2)
            solEm?.let { s -> if (s in 0.18f..0.72f) r += Triple(s, "☀ " + hf.format(Date(porDoSol!!)), OURO) }
            if (f != null) {
                val i12 = f.i0 + HORAS
                if (i12 < f.hTempo.size) r += Triple(1f, "${br.paulo.tvlauncher.weather.ForecastText.hora(ctx, f.hTempo[i12].substring(11, 13).toInt())} ${Math.round(f.hTemp[i12]).toInt()}°", Estilo.TEXTO_2)
            }
            rotulos = r
            invalidate()
        }

        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w).let { if (MeasureSpec.getMode(w) == MeasureSpec.UNSPECIFIED) dp(400) else it }, dp(56))

        override fun onDraw(c: Canvas) {
            val pad = dp(11).toFloat()
            val w = width - 2 * pad; val y = dp(14).toFloat(); val alt = dp(10).toFloat(); val r = alt / 2
            c.drawRoundRect(RectF(pad, y, pad + w, y + alt), r, r, trilho)
            for ((a, b) in faixas) c.drawRoundRect(RectF(pad + a * w, y, pad + b * w, y + alt), r, r, chuva)
            solEm?.let { s -> c.drawRoundRect(RectF(pad + s * w - d, y - dp(5), pad + s * w + d, y + alt + dp(5)), d, d, sol) }
            c.drawCircle(pad, y + r, dp(11).toFloat(), aro); c.drawCircle(pad, y + r, dp(8).toFloat(), agora)
            val base = y + alt + dp(26)
            for ((x, s, cor) in rotulos) {
                txt.color = cor
                val tw = txt.measureText(s)
                val px = (pad + x * w - when (x) { 0f -> pad; 1f -> tw - pad; else -> tw / 2 }).coerceIn(0f, width - tw)
                c.drawText(s, px, base, txt)
            }
        }
    }

    companion object {
        const val TROCA_FRASE_MS = 8_000L
        const val HORAS = 12
        const val CHUVA_NA_REGUA = 40
        const val OURO = 0xFFFFC27A.toInt()
    }
}

/** A conta pura da régua (SleepingLookTest). */
internal object ReguaConta {
    /** Trechos (início, fim) em 0..1 das horas com chance >= [limite]; [chances] = da hora atual em diante (n+1 pontos). */
    fun faixasDeChuva(chances: List<Int>, limite: Int): List<Pair<Float, Float>> {
        val n = (chances.size - 1).coerceAtLeast(1)
        val saida = mutableListOf<Pair<Float, Float>>()
        var ini = -1
        for (i in chances.indices) {
            val molhada = chances[i] >= limite
            if (molhada && ini < 0) ini = i
            if ((!molhada || i == chances.lastIndex) && ini >= 0) {
                val fim = if (molhada) i else i - 1
                saida += (ini.toFloat() / n) to ((fim + 1).coerceAtMost(n).toFloat() / n).coerceAtLeast((ini + 0.5f) / n)
                ini = -1
            }
        }
        return saida
    }
}
