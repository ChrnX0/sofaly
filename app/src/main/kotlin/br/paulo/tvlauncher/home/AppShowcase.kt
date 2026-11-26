package br.paulo.tvlauncher.home

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Vitrine do app escolhido (21/11/2026, proposta B dos cartões dos apps; AppChannels.Mode.SHOWCASE).
 *
 * Porquê: a área no alto à direita da cruz fica vazia (só a capa esmaecida); a prateleira da Apple TV e as fileiras do
 * Projectivy mostram ali o que tem dentro do app. Para quê: parou no Prime Video, já se vê o que ele está oferecendo, sem
 * tecla nova (a vitrine é só de olhar; segurar OK no app tem "Ver o que tem no app").
 * Como: um título (app · fileira) e até 3 capas 16:9 com o nome embaixo. Aparece e some só por alpha e um passo de 8 dp
 * (tokens do transitions.dev: abrir 400 ms, fechar 350 ms, curva 0.22,1,0.36,1); as capas vêm do cache do AppChannels.
 */
class AppShowcase(ctx: Context, fonte: Typeface, fonteForte: Typeface) : LinearLayout(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d + 0.5f).toInt()
    private val titulo = TextView(ctx).apply {
        typeface = fonteForte; textSize = 18f; setTextColor(0xFFFFF3E6.toInt()); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        setShadowLayer(6f, 0f, 1f, 0xCC000000.toInt())
    }
    private val fila = LinearLayout(ctx).apply { orientation = HORIZONTAL }
    private val capas = Array(AppChannels.MAX_VITRINE) { ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_XY } }
    private val nomes = Array(AppChannels.MAX_VITRINE) {
        TextView(ctx).apply {
            typeface = fonte; textSize = 18f; setTextColor(0xE6FFF3E6.toInt()); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(6f, 0f, 1f, 0xCC000000.toInt())
        }
    }
    private val celulas = Array(AppChannels.MAX_VITRINE) { i ->
        LinearLayout(ctx).apply {
            orientation = VERTICAL
            addView(capas[i], LayoutParams(dp(LARGURA), dp(LARGURA * 9 / 16)))
            addView(nomes[i], LayoutParams(dp(LARGURA), LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
    }
    private var pacoteAgora: String? = null
    private val curva = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    // 120 dp (visto na Sala 21/11, v467855: com 150 dp os nomes das capas encostavam no nome do item escolhido e no
    // ícone do mundo ao lado). 3 x 120 + 2 x 14 = 388 dp, à direita da margem: começa depois do texto do item.
    companion object { const val LARGURA = 120 }

    init {
        orientation = VERTICAL
        alpha = 0f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS   // só olhar; o leitor fala o item
        addView(titulo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
        celulas.forEachIndexed { i, c -> fila.addView(c, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { if (i > 0) marginStart = dp(14) }) }
        addView(fila)
    }

    /** Mostra os cartões de [pacote] ([nomeApp] · nome da fileira); lista vazia = esconde. */
    fun mostrar(pacote: String, nomeApp: String, cards: List<AppChannels.Card>, reserva: Drawable?) {
        if (cards.isEmpty()) { esconder(); return }
        if (pacote != pacoteAgora) {
            pacoteAgora = pacote
            val canal = cards.first().canal.takeIf { it.isNotBlank() && !it.equals(nomeApp, true) }
            titulo.text = listOfNotNull(nomeApp, canal).joinToString("  ·  ")
            for (i in capas.indices) {
                val c = cards.getOrNull(i)
                celulas[i].visibility = if (c == null) View.INVISIBLE else View.VISIBLE
                capas[i].setImageDrawable(c?.let { PosterDrawable(context, it.poster, reserva?.constantState?.newDrawable()?.mutate()) })
                nomes[i].text = c?.titulo.orEmpty()
            }
            translationY = dp(8).toFloat()
        }
        animate().cancel()
        animate().alpha(1f).translationY(0f).setDuration(br.paulo.tvlauncher.DesignTokens.MS_SLOW).setInterpolator(curva).start()
    }

    fun esconder() {
        if (pacoteAgora == null && alpha == 0f) return
        pacoteAgora = null
        animate().cancel()
        animate().alpha(0f).setDuration(br.paulo.tvlauncher.DesignTokens.MS_MEDIUM).setInterpolator(curva).start()
    }
}
