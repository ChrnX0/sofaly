package br.paulo.tvlauncher.settings

import android.graphics.BitmapFactory
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import br.paulo.tvlauncher.ConfigLauncherActivity
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.resources.R
import br.paulo.tvlauncher.home.HomeLooks
import br.paulo.tvlauncher.home.StripHome
import br.paulo.tvlauncher.image.Images

/**
 * A fileira "Visuais prontos" no topo da Aparência (onda B da simplificação, 25/11/2026). Ver [HomeLooks].
 *
 * Cada cartão é uma miniatura do visual: a foto de fundo dele e três cards no jeito dele embaixo. OK aplica na hora; com
 * as Configurações no painel ao lado, a HOME atrás muda junto (a prévia da MainActivity). Depois do primeiro OK aparece
 * "Como estava" no começo da fileira, que devolve tudo como era antes de entrar aqui.
 * Só aparece na barra única: os visuais mexem nas opções dela, na cruz não mudariam quase nada.
 */
internal fun ConfigLauncherActivity.linhaVisuaisProntos(c: LinearLayout) {
    if (!br.paulo.tvlauncher.home.BottomBar.strip(this)) return
    c.addView(Estilo.texto(this, Estilo.Papel.SECAO, getString(R.string.hl_titulo)), lp())
    val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false
        setPaddingRelative(dp(4), dp(8), dp(4), dp(8)) }
    val rolagem = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; isFocusable = false
        addView(fila)
    }
    c.addView(rolagem, lp())

    fun montar(focar: HomeLooks.Look?) {
        fila.removeAllViews()
        val atual = HomeLooks.atual(this)
        fotoVisuais?.let { foto ->
            fila.addView(cartaoVisual(null, false) {
                HomeLooks.voltar(this, foto); fotoVisuais = null; montar(null)
                fila.getChildAt(0)?.requestFocus()
            })
        }
        for (l in HomeLooks.Look.entries) {
            val v = cartaoVisual(l, l == atual) {
                if (fotoVisuais == null) fotoVisuais = HomeLooks.foto(this)
                HomeLooks.aplicar(this, l)
                montar(l)
            }
            fila.addView(v)
            if (l == focar) v.post { v.requestFocus() }
        }
    }
    montar(null)
}

private fun ConfigLauncherActivity.cartaoVisual(l: HomeLooks.Look?, escolhido: Boolean, aoClicar: () -> Unit): View {
    val w = dp(144); val h = dp(81)
    val raio = dp(14).toFloat()
    val miniatura = FrameLayout(this).apply {
        background = Estilo.fundo(this@cartaoVisual, Estilo.FUNDO, 14, if (escolhido) Estilo.ACENTO else Estilo.VIDRO_BORDA, if (escolhido) 2 else 1)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, raio) }
        }
    }
    if (l != null) {
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        miniatura.addView(img, FrameLayout.LayoutParams(w, h))
        l.foto.asset?.let { asset ->
            trabalho.execute {
                val b = runCatching { assets.open(asset).use { Images.decodeStream("settings/ConfigLooksRow", it, null, BitmapFactory.Options().apply { inSampleSize = 8 }) } }.getOrNull()
                ui.post { if (!isDestroyed) img.setImageBitmap(b) }
            }
        }
        // Três cards no jeito do visual, embaixo, como na HOME.
        val cards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val cor = when (l.cards) {
            StripHome.Own.WHITE -> Estilo.TEXTO
            StripHome.Own.GLASS -> Estilo.VIDRO_TINTA
            StripHome.Own.DARK -> Estilo.CARTAO
        }
        repeat(3) { i ->
            cards.addView(View(this).apply { background = Estilo.fundo(this@cartaoVisual, cor, 4, Estilo.VIDRO_BORDA) },
                LinearLayout.LayoutParams(dp(30), dp(18)).apply { if (i > 0) marginStart = dp(6) })
        }
        miniatura.addView(cards, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM).apply { bottomMargin = dp(8) })
    } else {
        // "Como estava": a seta de voltar no lugar da miniatura.
        miniatura.addView(Estilo.texto(this, Estilo.Papel.TITULO, "↺").apply { gravity = Gravity.CENTER },
            FrameLayout.LayoutParams(w, h))
    }
    val nome = getString(when (l) {
        null -> R.string.hl_voltar
        HomeLooks.Look.SOFALY -> R.string.hl_sofaly
        HomeLooks.Look.NIGHT -> R.string.hl_night
        HomeLooks.Look.MOUNTAIN -> R.string.hl_mountain
        HomeLooks.Look.EASY -> R.string.hl_easy
        HomeLooks.Look.MINIMAL -> R.string.hl_minimal
    })
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        isFocusable = true; isClickable = true
        contentDescription = if (escolhido) getString(R.string.hl_em_uso, nome) else nome
        addView(miniatura, LinearLayout.LayoutParams(w, h))
        addView(Estilo.texto(this@cartaoVisual, Estilo.Papel.LEGENDA, nome).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            if (escolhido) setTextColor(Estilo.ACENTO)
        }, LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { marginEnd = dp(14) }
        Estilo.focoCard(this)
        setOnClickListener { aoClicar() }
    }
}
