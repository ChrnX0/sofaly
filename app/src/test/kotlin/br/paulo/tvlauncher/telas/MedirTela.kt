package br.paulo.tvlauncher.telas

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import br.paulo.tvlauncher.Config
import java.io.File
import org.robolectric.RuntimeEnvironment

/**
 * Regras fixas de tela do degrau 4 (17/11/2026), as mesmas do CuidarTelaTest: salva a foto em app/build/telas, e
 * reclama de letra menor que [minimoSp], texto cortado (reticências), texto por cima de outro (só em [cartao], onde
 * nada rola) e tela sem foco.
 */
object MedirTela {
    private val saida = File("build/telas")

    fun medir(a: Activity, chave: String, arquivo: String, minimoSp: Float, cartao: Boolean): List<String> {
        saida.mkdirs()
        val raiz: View = a.window.decorView
        val foto = Bitmap.createBitmap(raiz.width, raiz.height, Bitmap.Config.ARGB_8888)
        raiz.draw(Canvas(foto))
        File(saida, "$arquivo.png").outputStream().use { foto.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val escala = RuntimeEnvironment.getApplication().resources.displayMetrics.scaledDensity
        val textos = mutableListOf<Pair<TextView, Rect>>()
        fun andar(v: View) {
            if (v.visibility != View.VISIBLE || v.alpha == 0f) return
            if (v is TextView && v.text.isNotBlank() && v.width > 0 && v.height > 0) {
                val r = Rect()
                if (v.getGlobalVisibleRect(r)) textos += v to r
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) andar(v.getChildAt(i))
        }
        andar(raiz)
        val p = mutableListOf<String>()
        fun rotulo(t: TextView) = t.text.toString().replace('\n', ' ').trim().take(40)
        for ((t, _) in textos) {
            val sp = t.textSize / escala
            if (sp < minimoSp - 0.05f) p += "$chave: letra pequena (${sp}sp) em \"${rotulo(t)}\""
            if (t.layout?.let { l -> (0 until l.lineCount).any { l.getEllipsisCount(it) > 0 } } == true) p += "$chave: texto cortado \"${rotulo(t)}\""
            // 24/11/2026 (Paulo viu na Sala o "DEPOIS" cortado ao meio): texto que a caixa de cima corta na vertical.
            // Linhas de lista que rola (a última aparece pela metade de propósito, como aviso de que tem mais) não contam.
            cortadoNaVertical(t)?.let { p += "$chave: texto cortado na vertical ($it) \"${rotulo(t)}\"" }
        }
        if (cartao) for (i in textos.indices) for (j in i + 1 until textos.size) {
            val (t1, r1) = textos[i]; val (t2, r2) = textos[j]
            if (t1.parent === t2 || t2.parent === t1) continue
            val x = Rect()
            if (x.setIntersect(r1, r2) && x.width() > 3 && x.height() > 3) p += "$chave: \"${rotulo(t1)}\" por cima de \"${rotulo(t2)}\""
        }
        if (raiz.findFocus() == null) p += "$chave: nada tem o foco"
        return p
    }

    /**
     * As linhas de texto (não a caixa da TextView, que pode ser mais alta por padding ou altura mínima) passam da área
     * visível: o pai corta letras. Fora de lista que rola (a última linha aparece pela metade de propósito).
     */
    fun cortadoNaVertical(t: TextView): String? {
        var pai = t.parent
        while (pai is View) {
            if (pai is android.widget.AbsListView || pai is android.widget.ScrollView || pai is android.widget.HorizontalScrollView ||
                pai.javaClass.name.contains("RecyclerView")) return null
            pai = pai.parent
        }
        val l = t.layout ?: return null
        if (l.lineCount == 0 || !t.getGlobalVisibleRect(Rect())) return null
        // As linhas, nas coordenadas da própria TextView; sobe pai a pai aplicando posição, rolagem e transformações
        // (escala do foco, translação), e confere contra cada pai que corta os filhos (clipChildren).
        // A tinta das letras (não a caixa da linha, que em fonte grande sem "font padding" passa um pouco da área sem
        // cortar nada): da primeira à última linha, pelos limites reais dos caracteres.
        // Só letras e algarismos: símbolos de fonte reserva (↑ ↓) dão limites maiores que o desenho no Robolectric
        // (conferido na Sala em 24/11/2026: "↑ 05:20" inteiro na TV, o teste dizia cortado).
        fun tinta(linha: Int): Rect {
            val so = l.text.subSequence(l.getLineStart(linha), l.getLineEnd(linha)).filter { it.isLetterOrDigit() }.toString()
            return Rect().also { if (so.isNotEmpty()) t.paint.getTextBounds(so, 0, so.length, it) }
        }
        val ult = l.lineCount - 1
        val b0 = tinta(0); val b1 = tinta(ult)
        if (b0.isEmpty && b1.isEmpty) return null
        val base = t.totalPaddingTop - t.scrollY
        val r = android.graphics.RectF(0f, (base + l.getLineBaseline(0) + b0.top).toFloat(), t.width.toFloat(),
            (base + l.getLineBaseline(ult) + b1.bottom).toFloat())
        var v: View = t
        while (true) {
            val p = v.parent as? ViewGroup ?: return null
            v.matrix.mapRect(r)
            r.offset((v.left - p.scrollX).toFloat(), (v.top - p.scrollY).toFloat())
            if (p.clipChildren && (r.top < -4f || r.bottom > p.height + 4f))
                return "${p.javaClass.simpleName} ${p.height}px, letras ${r.top.toInt()}..${r.bottom.toInt()}"
            v = p
        }
    }
}
