package br.paulo.tvlauncher

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.WeakHashMap

/**
 * Molde das telas de AJUSTE em três colunas (Paulo, 26/11/2026, escolheu "A+C nos ajustes, B na leitura"; página
 * https://claude.ai/artifact/FJqg47QZ3skB1ytyZjf22c). Antes era [Estilo.telaLista]: uma coluna esticada nos 960 dp.
 *
 * - Esquerda: o trilho das seções. Some quando a tela só tem uma seção.
 * - Meio: os ajustes da seção em foco, com o valor alinhado à direita ([valueRow]).
 * - Direita: o efeito do item em foco ([describe]): o que acontece, quando, para quem. Não recebe foco, só acompanha.
 *   Some quando nada na tela tem o que dizer, e a tela vira duas colunas.
 *
 * Controle: ▲ ▼ andam dentro da coluna; ▶ no trilho entra nos ajustes; ◀ volta ao trilho quando a linha não usa ◀ ▶
 * para mudar valor; VOLTAR nos ajustes também volta ao trilho ([back]), e no trilho fecha a tela.
 * Referências do Mobbin: Revolut (trilho), Snowflake (valor à direita), incident.io (coluna de prévia), Productboard.
 *
 * Uso: `val s = ColumnsScreen(this, titulo)`; `val sec = s.section(nome)`; linhas em `sec`; `s.describe(linha) { ... }`;
 * no fim `setContentView(s.root)` e `s.start()`.
 */
class ColumnsScreen(private val a: Activity, titulo: String, subtitulo: String? = null) {

    /** O que a coluna da direita mostra para um item: rótulo pequeno, frase em destaque e explicação. */
    data class Info(val label: CharSequence? = null, val headline: CharSequence? = null, val body: CharSequence? = null)

    private class Section(val name: String, val rail: TextView, val rows: LinearLayout)

    private val sections = mutableListOf<Section>()
    private val infos = WeakHashMap<View, () -> Info?>()
    private val sectionInfos = HashMap<LinearLayout, () -> Info?>()
    private var current: Section? = null

    private fun dp(v: Int) = Estilo.dp(a, v)

    private val railColumn = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false; clipToPadding = false
        setPaddingRelative(0, dp(Estilo.E1), 0, dp(Estilo.E2))
    }
    private val railScroll = ScrollView(a).apply {
        isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; isFocusable = false
        setPaddingRelative(dp(Estilo.E1), 0, dp(Estilo.E1), 0)
        addView(railColumn)
    }
    private val middle = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false; clipToPadding = false
        setPaddingRelative(0, dp(Estilo.E1), 0, dp(Estilo.MARGEM_VERTICAL + 16))
    }
    private val middleScroll = ScrollView(a).apply {
        isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; isFocusable = false
        isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(dp(48))
        setPaddingRelative(dp(Estilo.E1), 0, dp(Estilo.E1), 0)
        addView(middle)
    }

    private val infoLabel = Estilo.texto(a, Estilo.Papel.SECAO)
    private val infoHeadline = Estilo.texto(a, Estilo.Papel.CORPO).apply {
        typeface = Estilo.fonte(700); setPaddingRelative(0, dp(Estilo.E1), 0, 0)
    }
    private val infoBody = Estilo.texto(a, Estilo.Papel.LEGENDA).apply {
        setLineSpacing(0f, 1.25f); setPaddingRelative(0, dp(Estilo.E1), 0, 0)
    }
    private val infoCard = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(dp(Estilo.E3), dp(Estilo.E3 - 4), dp(Estilo.E3), dp(Estilo.E3))
        background = Estilo.fundo(a, Estilo.SUPERFICIE, Estilo.RAIO, Estilo.BORDA)
        addView(infoLabel); addView(infoHeadline); addView(infoBody)
        visibility = View.GONE
    }

    /** Raiz para o setContentView. */
    val root: LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        // Grafite liso (Paulo, 26/11/2026: o escuro nunca azulado). A foto da barra borrada puxava para o azul.
        background = if (Estilo.altoContraste) ColorDrawable(android.graphics.Color.BLACK) else ColorDrawable(Estilo.FUNDO)
        clipChildren = true; clipToPadding = false
        setPaddingRelative(dp(Estilo.MARGEM_LATERAL), dp(Estilo.MARGEM_VERTICAL + 9), dp(Estilo.MARGEM_LATERAL), 0)
        addView(Estilo.tituloTela(a, titulo))
        if (subtitulo != null) addView(Estilo.texto(a, Estilo.Papel.LEGENDA, subtitulo),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
            // Trilho: 220 dp; meio: o resto; contexto: 260 dp (a mesma conta da página aprovada, em 960 dp de largura).
            addView(railScroll, LinearLayout.LayoutParams(dp(RAIL_DP + 2 * Estilo.E1), -1).apply { marginStart = -dp(Estilo.E1) })
            addView(middleScroll, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(Estilo.E2 - 2 * Estilo.E1) })
            addView(infoCard, LinearLayout.LayoutParams(dp(INFO_DP), -2).apply {
                marginStart = dp(Estilo.E3); topMargin = dp(Estilo.E1)
            })
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(Estilo.E3) })
    }

    /** Nova seção: um item no trilho e a coluna (devolvida) onde a tela põe as linhas dela. */
    fun section(name: String, info: (() -> Info?)? = null): LinearLayout {
        val rows = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            visibility = View.GONE
        }
        middle.addView(rows, LinearLayout.LayoutParams(-1, -2))
        val item = railItem(name)
        railColumn.addView(item, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        val s = Section(name, item, rows)
        sections += s
        if (info != null) sectionInfos[rows] = info
        item.setOnClickListener { firstRow(s)?.requestFocus() }
        return rows
    }

    /** O que a coluna da direita diz quando [row] está em foco (null = o da seção, ou nada). */
    fun describe(row: View, info: () -> Info?) { infos[row] = info }

    /**
     * Linha com rótulo à esquerda e valor à direita (a linha das Configurações). O valor pode ficar vazio: só o rótulo.
     * Devolve a linha e os dois textos, para a tela trocar o que mostram.
     */
    fun valueRow(): Triple<LinearLayout, TextView, TextView> {
        val value = Estilo.valor(a, "", seta = false)
        val row = Estilo.linhaOpcao(a, "", null, value)
        val label = (row.getChildAt(0) as ViewGroup).getChildAt(0) as TextView
        return Triple(row, label, value)
    }

    /** A 1ª linha da seção chamada [name] (sem diferença de maiúsculas), para abrir a tela já nela. */
    fun firstRowOf(name: String): View? = sections.firstOrNull { it.name.equals(name, true) }?.let { firstRow(it) }

    /** LayoutParams de uma linha dos ajustes. */
    fun lpRow() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }

    /**
     * Monta a navegação e mostra a 1ª seção (ou a de [focus], uma linha de dentro dela, que já recebe o foco).
     * Chame depois do setContentView.
     */
    fun start(focus: View? = null) {
        val single = sections.size < 2
        railScroll.visibility = if (single) View.GONE else View.VISIBLE
        if (single) (middleScroll.layoutParams as LinearLayout.LayoutParams).marginStart = -dp(Estilo.E1)
        for (s in sections) {
            if (s.rail.id == View.NO_ID) s.rail.id = View.generateViewId()
            for (i in 0 until s.rows.childCount) {
                val r = s.rows.getChildAt(i)
                if (r.isFocusable && !single) r.nextFocusLeftId = s.rail.id
            }
            firstRow(s)?.let { f ->
                if (f.id == View.NO_ID) f.id = View.generateViewId()
                s.rail.nextFocusRightId = f.id
            }
        }
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, now -> onFocus(now) }
        val alvo = focus?.let { f -> sections.firstOrNull { isInside(f, it.rows) } }
        show(alvo ?: sections.firstOrNull())
        val first = focus ?: if (single) sections.firstOrNull()?.let { firstRow(it) } else sections.firstOrNull()?.rail
        first?.requestFocus()
        first?.post { if (!first.isFocused) first.requestFocus() }
    }

    /** VOLTAR: dos ajustes volta ao trilho (true = tratado); no trilho, ou sem trilho, a tela fecha (false). */
    fun back(): Boolean {
        val f = a.currentFocus ?: return false
        if (sections.size < 2) return false
        val s = sections.firstOrNull { isInside(f, it.rows) } ?: return false
        s.rail.requestFocus()
        return true
    }

    /** Refaz o texto da coluna da direita (depois de mudar um valor). */
    fun refresh() = onFocus(a.currentFocus)

    private fun onFocus(now: View?) {
        if (now == null) return
        sections.firstOrNull { it.rail === now }?.let { show(it); paintRail(); showInfo(sectionInfos[it.rows]?.invoke()); return }
        val s = sections.firstOrNull { isInside(now, it.rows) } ?: return
        if (s !== current) show(s)
        paintRail()
        val own = generateSequence(now) { it.parent as? View }.takeWhile { it !== s.rows }.firstNotNullOfOrNull { infos[it] }
        showInfo(own?.invoke() ?: sectionInfos[s.rows]?.invoke())
    }

    private fun show(s: Section?) {
        if (s == null) return
        current = s
        for (x in sections) x.rows.visibility = if (x === s) View.VISIBLE else View.GONE
        // A coluna da direita existe só na seção que tem o que dizer; nas outras o meio ocupa o lugar dela.
        val temInfo = sectionInfos.containsKey(s.rows) || infos.keys.any { it != null && isInside(it, s.rows) }
        infoCard.visibility = if (temInfo) View.INVISIBLE else View.GONE
        middleScroll.scrollTo(0, 0)
        paintRail()
    }

    private fun showInfo(i: Info?) {
        if (infoCard.visibility == View.GONE) return
        if (i == null || (i.headline.isNullOrEmpty() && i.body.isNullOrEmpty())) { infoCard.visibility = View.INVISIBLE; return }
        infoLabel.text = i.label ?: ""; infoLabel.visibility = if (i.label.isNullOrEmpty()) View.GONE else View.VISIBLE
        infoHeadline.text = i.headline ?: ""; infoHeadline.visibility = if (i.headline.isNullOrEmpty()) View.GONE else View.VISIBLE
        infoBody.text = i.body ?: ""; infoBody.visibility = if (i.body.isNullOrEmpty()) View.GONE else View.VISIBLE
        infoCard.visibility = View.VISIBLE
    }

    /** Item do trilho: sem caixa; o da seção aberta fica com o fundo um tom acima, o focado com o foco do sistema. */
    private fun railItem(name: String): TextView = Estilo.texto(a, Estilo.Papel.CORPO, name).apply {
        isFocusable = true; isClickable = true
        maxLines = 2
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        minimumHeight = dp(48)
        setPaddingRelative(dp(Estilo.E2), dp(10), dp(Estilo.E2), dp(10))
        setOnFocusChangeListener { v, foco ->
            paintRail()
            v.animate().scaleX(if (foco) Estilo.ESCALA_LINHA else 1f).scaleY(if (foco) Estilo.ESCALA_LINHA else 1f)
                .setDuration(Estilo.FOCO_MS).start()
        }
    }

    private fun paintRail() {
        for (s in sections) {
            val t = s.rail
            val open = s === current
            t.background = when {
                t.isFocused -> Estilo.fundoFocado(a, Estilo.RAIO)
                open -> Estilo.fundo(a, Estilo.SUPERFICIE_ALTA, Estilo.RAIO)
                else -> null
            }
            t.typeface = Estilo.fonte(if (open) 700 else 400)
            t.setTextColor(if (t.isFocused && Estilo.focoEscurece(a)) Estilo.TEXTO_NO_FOCO else if (open || t.isFocused) Estilo.TEXTO else Estilo.TEXTO_2)
        }
    }

    private fun firstRow(s: Section): View? = (0 until s.rows.childCount).map { s.rows.getChildAt(it) }.firstOrNull { it.isFocusable }

    private fun isInside(v: View, g: ViewGroup): Boolean = generateSequence(v) { it.parent as? View }.any { it === g }

    companion object {
        const val RAIL_DP = 220
        const val INFO_DP = 260
    }
}
