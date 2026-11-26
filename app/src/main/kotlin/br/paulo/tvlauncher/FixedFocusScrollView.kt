package br.paulo.tvlauncher

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.ScrollView

/**
 * Lista com o FOCO PARADO (Paulo, 26/11/2026: "adota tudo do Projectivy"; estudo https://claude.ai/artifact/BH1rjTagzoRMLPg5Axffuo).
 *
 * Porquê: no Projectivy a linha com foco fica sempre na mesma altura da tela (~39%, medida nas fotos da Sala: y 420 de
 * 1080), do começo ao fim da lista; quem anda é a lista, por baixo. O olho não precisa caçar onde o foco foi parar.
 * Antes, na Sofaly, o foco descia até a borda e só então a tela rolava.
 * Para quê: as listas de ajuste (Configurações, telas em colunas) leem como as dele.
 * Como: o ScrollView pede ao Android "quanto rolar para mostrar o filho em foco"
 * ([computeScrollDeltaToGetChildRectOnScreen]); aqui a resposta é "o bastante para o topo dele cair na linha fixa".
 * Para a 1ª e a última linha também chegarem lá, o conteúdo ganha folga em cima e embaixo do tamanho certo (mantendo o
 * recuo que ele já tinha). Nada muda para quem usa o ScrollView: é só trocar a classe.
 *
 * [fraction] = a altura da linha fixa, como fração da altura da TELA (não da lista), igual ao Projectivy.
 */
open class FixedFocusScrollView(ctx: Context, private val fraction: Float = FRACTION) : ScrollView(ctx) {

    private var baseTop = -1
    private var baseBottom = -1
    private val loc = IntArray(2)

    init {
        isVerticalScrollBarEnabled = false
        isSmoothScrollingEnabled = true
    }

    /** Onde a linha fixa cai dentro desta lista (px a partir do topo dela). */
    fun focusLine(): Int {
        getLocationOnScreen(loc)
        val alvo = (resources.displayMetrics.heightPixels * fraction).toInt() - loc[1]
        return alvo.coerceIn(0, (height - MIN_ROOM_PX).coerceAtLeast(0))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val filho = getChildAt(0)
        if (filho != null && height > 0) {
            if (baseTop < 0) { baseTop = filho.paddingTop; baseBottom = filho.paddingBottom }
            val linha = focusLine()
            val cima = baseTop + linha
            val baixo = baseBottom + (height - linha)
            if (filho.paddingTop != cima || filho.paddingBottom != baixo) {
                filho.setPaddingRelative(filho.paddingStart, cima, filho.paddingEnd, baixo)
                post { requestLayout(); findFocus()?.let { f -> if (f !== this) centrar(f, animar = false) } }
            }
        }
        super.onLayout(changed, l, t, r, b)
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
        if (childCount == 0 || height == 0) return 0
        val max = (getChildAt(0).height - height).coerceAtLeast(0)
        val quer = (rect.top - focusLine()).coerceIn(0, max)
        return quer - scrollY
    }

    /** Põe [v] (um filho, em qualquer nível) na linha fixa. */
    fun centrar(v: View, animar: Boolean = true) {
        val r = Rect(); v.getDrawingRect(r)
        runCatching { offsetDescendantRectToMyCoords(v, r) }.onFailure { return }
        val d = computeScrollDeltaToGetChildRectOnScreen(r)
        if (d == 0) return
        if (animar) smoothScrollBy(0, d) else scrollBy(0, d)
    }

    companion object {
        /** A altura da linha com foco no Projectivy: y 420 de 1080 nas fotos da Sala. */
        const val FRACTION = 0.39f
        private const val MIN_ROOM_PX = 120
    }
}
