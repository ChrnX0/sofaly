package br.paulo.tvlauncher

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Molde das telas de LEITURA em cartões (Paulo, 26/11/2026, "B na leitura": Como esta TV está indo, Conferir a TV,
 * Armazenamento). Cada item vira um cartão numa grade de [columns] colunas, com o número grande; o que cabe aparece
 * de uma vez, sem rolar. Par do [ColumnsScreen] (as telas de ajuste). Referências do Mobbin: Stripe (ajustes em grade),
 * Braintrust (painel de cartões no escuro), Dropbox Dash (grupos com título).
 *
 * Uso: `val s = CardsScreen(this, titulo, sub)`; `s.group(nome)` (opcional); `s.card()` devolve um cartão focável
 * vazio (LinearLayout vertical) para a tela encher; `s.number(...)`, `s.caption(...)` ajudam; `setContentView(s.root)`.
 */
class CardsScreen(private val a: Activity, titulo: String, subtitulo: String? = null, private val columns: Int = 4) {

    private fun dp(v: Int) = Estilo.dp(a, v)

    private val grid = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false; clipToPadding = false
        setPaddingRelative(0, dp(Estilo.E1), 0, dp(Estilo.MARGEM_VERTICAL))
    }
    private var line: LinearLayout? = null
    private var inLine = 0
    private val cards = mutableListOf<View>()

    val root: LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = Estilo.fundoTela(a)   // arte com véu como as outras telas (auditoria 27/11/2026: era o grafite chapado)
        clipChildren = true; clipToPadding = false
        setPaddingRelative(dp(Estilo.MARGEM_LATERAL), dp(Estilo.MARGEM_VERTICAL + 9), dp(Estilo.MARGEM_LATERAL), 0)
        addView(Estilo.tituloTela(a, titulo))
        if (subtitulo != null) addView(Estilo.texto(a, Estilo.Papel.LEGENDA, subtitulo),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        addView(ScrollView(a).apply {
            isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; isFocusable = false
            isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(dp(48))
            setPaddingRelative(dp(Estilo.E1), 0, dp(Estilo.E1), 0)
            addView(grid)
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(Estilo.E2); marginStart = -dp(Estilo.E1); marginEnd = -dp(Estilo.E1) })
    }

    /** Nome de grupo por cima de uma nova fileira de cartões. */
    fun group(name: String) {
        closeLine()
        grid.addView(Estilo.secao(a, name).apply { setPaddingRelative(dp(4), if (grid.childCount == 0) 0 else dp(Estilo.E2), 0, dp(Estilo.E1)) })
    }

    /** Novo cartão focável (vazio, vertical). [wide] = ocupa a fileira inteira. */
    fun card(wide: Boolean = false): LinearLayout {
        if (wide) closeLine()
        val l = line ?: LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            clipChildren = false; clipToPadding = false
            grid.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = if (grid.childCount == 0) 0 else dp(Estilo.E2 - 4) })
            line = this; inLine = 0
        }
        val c = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true; isClickable = true
            minimumHeight = dp(MIN_DP)
            setPaddingRelative(dp(Estilo.E2 + 2), dp(Estilo.E2), dp(Estilo.E2 + 2), dp(Estilo.E2))
            background = Estilo.caixa(a)
            Estilo.focoCard(this)
        }
        l.addView(c, LinearLayout.LayoutParams(0, -1, 1f).apply { if (inLine > 0) marginStart = dp(Estilo.E2 - 4) })
        inLine++
        cards += c
        if (wide) { line = null; inLine = 0 } else if (inLine >= columns) closeLine()
        return c
    }

    /** Fecha a fileira aberta: as vagas que sobraram ficam vazias, para os cartões não esticarem. */
    private fun closeLine() {
        val l = line ?: return
        repeat(if (inLine in 1 until columns) columns - inLine else 0) {
            // Altura MATCH_PARENT como os cartões: uma vaga com altura fixa faz o LinearLayout medir a fileira por ela
            // (allFillParent=false) e espreme os cartões a 0 px (TelasTest, Placar com 7 cartões).
            l.addView(View(a), LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(Estilo.E2 - 4) })
        }
        line = null; inLine = 0
    }

    /** Título do cartão (o nome do item). */
    fun name(c: LinearLayout, text: CharSequence): TextView = Estilo.texto(a, Estilo.Papel.CORPO, text).apply {
        typeface = Estilo.fonte(700); maxLines = 2
        c.addView(this)
    }

    /** O número grande do cartão ("0,6 s", "12 de 12"). */
    fun number(c: LinearLayout, text: CharSequence): TextView = Estilo.texto(a, Estilo.Papel.TITULO, text).apply {
        typeface = Estilo.fonte(800); maxLines = 1
        setPaddingRelative(0, dp(Estilo.E1 + 2), 0, 0)
        c.addView(this)
    }

    /** Linha pequena embaixo (alvo, quando foi medido, explicação). */
    fun caption(c: LinearLayout, text: CharSequence, color: Int? = null): TextView = Estilo.texto(a, Estilo.Papel.LEGENDA, text).apply {
        if (color != null) setTextColor(color)
        setPaddingRelative(0, dp(4), 0, 0)
        c.addView(this)
    }

    /** Empurra o que vier depois para o pé do cartão (o estado fica sempre na mesma altura). */
    fun spacer(c: LinearLayout) = c.addView(View(a), LinearLayout.LayoutParams(1, 0, 1f))

    /** Fecha a grade e põe o foco no primeiro cartão (ou em [focus]). Chame depois do setContentView. */
    fun start(focus: View? = null) {
        closeLine()
        val f = focus ?: cards.firstOrNull() ?: return
        f.requestFocus(); f.post { if (!f.isFocused) f.requestFocus() }
    }

    /** Apaga os cartões (para redesenhar). */
    fun clear() { grid.removeAllViews(); cards.clear(); line = null; inLine = 0 }

    companion object { const val MIN_DP = 150 }
}
