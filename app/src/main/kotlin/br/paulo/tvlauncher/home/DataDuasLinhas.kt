package br.paulo.tvlauncher.home

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextClock

/**
 * A data do topo em duas linhas ("Quinta-feira" / "1 de janeiro") que vira UMA linha curta ([umaLinha], ex.: "1月1日(木)")
 * quando as duas não cabem na altura da pílula ou a escrita é alta ([EscritaAlta]). Por quê: em japonês, coreano e híndi a fonte de reserva é mais alta e a
 * 2ª linha saía cortada pela metade (teste de telas nas 26 línguas, 24/11/2026). Encolher a letra não serve (nada abaixo
 * de 15 sp); trocar para uma linha mantém o tamanho e mostra tudo.
 */
class DataDuasLinhas(ctx: Context, private val umaLinha: String) : LinearLayout(ctx) {
    private var virou = false

    override fun onMeasure(larg: Int, alt: Int) {
        super.onMeasure(larg, alt)
        if (virou || childCount < 2 || MeasureSpec.getMode(alt) == MeasureSpec.UNSPECIFIED) return
        // Escrita alta (japonês, coreano, chinês, árabe, híndi…): a fonte de reserva desenha fora da linha que a fonte da
        // interface mede, então a conta de altura não enxerga o corte. Nessas escritas vai direto para uma linha.
        val alta = (0 until childCount).any { EscritaAlta.tem((getChildAt(it) as? android.widget.TextView)?.text) }
        // A altura medida já vem cortada no limite (AT_MOST): soma o que as linhas pedem de verdade.
        var precisa = paddingTop + paddingBottom
        for (i in 0 until childCount) getChildAt(i).takeIf { it.visibility != View.GONE }?.let { precisa += it.measuredHeight }
        if (!alta && precisa <= MeasureSpec.getSize(alt)) return
        virou = true
        getChildAt(0).visibility = View.GONE
        (getChildAt(1) as? TextClock)?.let { it.format12Hour = umaLinha; it.format24Hour = umaLinha }
        super.onMeasure(larg, alt)
    }
}

/** Escritas cujas letras são mais altas que as da fonte da interface (latim, cirílico e grego cabem na linha normal). */
object EscritaAlta {
    private val NORMAIS = setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.CYRILLIC, Character.UnicodeScript.GREEK,
        Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED)

    fun tem(texto: CharSequence?): Boolean {
        if (texto == null) return false
        var i = 0
        while (i < texto.length) {
            val c = Character.codePointAt(texto, i)
            if (Character.isLetterOrDigit(c) && Character.UnicodeScript.of(c) !in NORMAIS) return true
            i += Character.charCount(c)
        }
        return false
    }
}
