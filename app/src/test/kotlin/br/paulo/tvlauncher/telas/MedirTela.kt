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
        val sumidos = mutableListOf<TextView>()
        fun andar(v: View) {
            if (v.visibility != View.VISIBLE || v.alpha == 0f) return
            if (v is TextView && espremido(v)) sumidos += v
            if (v is TextView && v.text.isNotBlank() && v.width > 0 && v.height > 0) {
                val r = Rect()
                if (v.getGlobalVisibleRect(r)) textos += v to r
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) andar(v.getChildAt(i))
        }
        andar(raiz)
        val p = mutableListOf<String>()
        fun rotulo(t: TextView) = t.text.toString().replace('\n', ' ').trim().take(40)
        for (t in sumidos) p += "$chave: texto espremido a zero (${t.width}x${t.height}) \"${rotulo(t)}\""
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
        val l = t.layout ?: return null
        if (l.lineCount == 0 || !t.getGlobalVisibleRect(Rect())) return null
        // A tinta das letras (não a caixa da linha, que em fonte grande sem "font padding" passa um pouco da área sem
        // cortar nada): da primeira à última linha, pelos limites reais dos caracteres.
        // Só letras, algarismos e sinais de vogal acima/abaixo (devanágari, tailandês: são os que passam da linha): símbolos de fonte reserva (↑ ↓) dão limites maiores que o desenho no Robolectric
        // (conferido na Sala em 24/11/2026: "↑ 05:20" inteiro na TV, o teste dizia cortado).
        fun tinta(linha: Int): Rect {
            val so = l.text.subSequence(l.getLineStart(linha), l.getLineEnd(linha)).filter { c -> c.isLetterOrDigit() ||
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() || Character.getType(c) == Character.COMBINING_SPACING_MARK.toInt() }.toString()
            return Rect().also { if (so.isNotEmpty()) t.paint.getTextBounds(so, 0, so.length, it) }
        }
        val ult = l.lineCount - 1
        // Onde a TextView desenha: padding + o deslocamento da gravidade vertical quando o texto é mais baixo que a caixa
        // (CENTER/BOTTOM; é o getVerticalOffset do onDraw, privado). Revisor, 24/11/2026: sem ele o texto centrado
        // parecia estar no topo da caixa.
        val livre = t.height - t.extendedPaddingTop - t.extendedPaddingBottom - l.height
        val desloc = if (livre <= 0) 0 else when (t.gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK) {
            android.view.Gravity.BOTTOM -> livre
            android.view.Gravity.CENTER_VERTICAL -> livre / 2
            else -> 0
        }
        val base = t.extendedPaddingTop + desloc - t.scrollY
        val caixa = android.graphics.RectF(0f, (base + l.getLineTop(0)).toFloat(), t.width.toFloat(), (base + l.getLineBottom(ult)).toFloat())
        // A própria TextView corta o desenho na área dela menos o padding (revisor, 24/11/2026: altura fixa com 2 linhas
        // sumia com a 2ª sem aviso). Vale a caixa das LINHAS (não a tinta): é ela que a TextView mede para caber; com
        // altura fixa menor, a última linha fica fora. Antes da tinta, para valer também em texto só de símbolos (★★★).
        val folga = 4f
        if (caixa.top < t.extendedPaddingTop - folga || caixa.bottom > t.height - t.extendedPaddingBottom + folga)
            return "a própria caixa ${t.height}px, linhas ${caixa.top.toInt()}..${caixa.bottom.toInt()}"
        val b0 = tinta(0); val b1 = tinta(ult)
        if (b0.isEmpty && b1.isEmpty) return null
        var r = android.graphics.RectF(0f, (base + l.getLineBaseline(0) + b0.top).toFloat(), t.width.toFloat(),
            (base + l.getLineBaseline(ult) + b1.bottom).toFloat())
        // Limite de tinta impossível (mais de 1,5× a altura das linhas): o Robolectric mede mal algumas fontes de reserva
        // (algarismos árabes do relógio: 55..328 numa caixa de 207 px, inteiro na foto). Aí vale a caixa das linhas.
        if (r.height() > caixa.height() * 1.5f) r = android.graphics.RectF(caixa)
        // Sobe pai a pai aplicando posição, rolagem e transformações (escala do foco, translação) e confere contra cada pai
        // que corta os filhos (clipChildren), na borda do padding quando clipToPadding (o padrão). Para ao chegar numa
        // lista que rola: a última linha dela aparece pela metade de propósito; o que está DENTRO da linha da lista
        // continua conferido (revisor, 24/11/2026: antes qualquer lista acima isentava o texto inteiro).
        var v: View = t
        while (true) {
            val p = v.parent as? ViewGroup ?: return null
            if (p is android.widget.AbsListView || p is android.widget.ScrollView || p is android.widget.HorizontalScrollView ||
                p.javaClass.name.contains("RecyclerView")) return null
            v.matrix.mapRect(r)
            r.offset((v.left - p.scrollX).toFloat(), (v.top - p.scrollY).toFloat())
            if (p.clipChildren) {
                val cima = if (p.clipToPadding) p.paddingTop.toFloat() else 0f
                val baixo = if (p.clipToPadding) (p.height - p.paddingBottom).toFloat() else p.height.toFloat()
                if (r.top < cima - folga || r.bottom > baixo + folga)
                    return "${p.javaClass.simpleName} ${p.height}px, letras ${r.top.toInt()}..${r.bottom.toInt()}"
            }
            v = p
        }
    }

    /**
     * Texto que existe e devia aparecer, mas a caixa foi espremida a zero (altura ou largura) por um pai de tamanho fixo
     * (revisor, 24/11/2026: some sem reticências e sem corte, e nenhuma outra checagem via).
     */
    fun espremido(t: TextView): Boolean {
        if (t.text.isBlank() || (t.width > 0 && t.height > 0)) return false
        var v: View = t
        while (true) { if (v.visibility != View.VISIBLE || v.alpha == 0f) return false; v = v.parent as? View ?: break }
        // Sobe até o primeiro pai com tamanho (revisor, 24/11/2026: um cartão inteiro espremido a zero escondia os textos
        // dele, e o pai direto também tinha zero). Para em lista que rola: linha reciclada fora da tela pode ter zero.
        var p = t.parent as? View ?: return false
        while (p.width <= 0 || p.height <= 0) {
            if (p is android.widget.AbsListView || p is android.widget.ScrollView || p is android.widget.HorizontalScrollView ||
                p.javaClass.name.contains("RecyclerView")) return false
            p = p.parent as? View ?: return false
        }
        return p !is android.widget.AbsListView && p !is android.widget.ScrollView && p !is android.widget.HorizontalScrollView &&
            !p.javaClass.name.contains("RecyclerView")
    }
}
