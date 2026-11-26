package br.paulo.tvlauncher.settings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.home.IconeCruz

/**
 * A coluna da explicação do jeito GUIADO (Paulo, 26/11/2026: "adota tudo do Projectivy"; estudo
 * https://claude.ai/artifact/BH1rjTagzoRMLPg5Axffuo, molde 2 "tela guiada").
 *
 * Porquê: no Projectivy toda tela de ajuste tem, à esquerda, um azulejo com o ícone, o caminho ("Configurações"), o título
 * na cor de destaque e uma frase do que se faz ali; a lista fica à direita. Quem chega numa tela sabe onde está sem ler a
 * lista. Para quê: as Configurações da Sofaly leem igual (foi pedido copiar tudo agora e mudar um a um depois).
 * Como: uma coluna simples que a Activity atualiza a cada porta/aba ([mostrar]); a troca de texto entra com o mesmo
 * movimento dele (some e volta deslizando um pouco, curva da casa). O topo fica a 1/4 da altura da tela, medida dele.
 *
 * Medidas tiradas das fotos da Sala (1920x1080 = 960x540 dp): azulejo 108 px = 54 dp, título fino grande, explicação
 * em 2 a 4 linhas. Letra nunca menor que a da casa (18 sp no corpo), diferente dele (14 sp).
 */
internal class GuidedPane(ctx: Context) : LinearLayout(ctx) {

    private val azulejo = FrameLayout(ctx)
    private val icone = View(ctx)
    private val tTrilha = Estilo.texto(ctx, Estilo.Papel.LEGENDA).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val tTitulo = Estilo.texto(ctx, Estilo.Papel.TITULO).apply {
        textSize = TITULO_SP; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; typeface = Estilo.fonte(300)
    }
    private val tTexto = Estilo.texto(ctx, Estilo.Papel.CORPO).apply { maxLines = 5; ellipsize = TextUtils.TruncateAt.END }
    /** Onde a Activity põe a barra de dicas e a marca (embaixo, na coluna larga, para a lista estreita não apertar). */
    val rodape = LinearLayout(ctx).apply { orientation = VERTICAL }
    private var atual: String? = null

    init {
        orientation = VERTICAL
        val d = ctx.resources.displayMetrics
        setPaddingRelative(Estilo.dp(ctx, Estilo.MARGEM_LATERAL), (d.heightPixels / 4) - Estilo.dp(ctx, AZULEJO_DP + 24), Estilo.dp(ctx, 48), Estilo.dp(ctx, Estilo.MARGEM_VERTICAL - 7))
        azulejo.background = GradientDrawable().apply { cornerRadius = Estilo.dp(ctx, 14).toFloat(); setColor(Estilo.ACENTO) }
        val lado = Estilo.dp(ctx, AZULEJO_DP)
        azulejo.addView(icone, FrameLayout.LayoutParams(Estilo.dp(ctx, 30), Estilo.dp(ctx, 30), Gravity.CENTER))
        addView(azulejo, LayoutParams(lado, lado).apply { bottomMargin = Estilo.dp(ctx, 24) })
        tTrilha.setTextColor(Estilo.TEXTO_2)
        addView(tTrilha, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        tTitulo.setTextColor(Estilo.ACENTO)
        addView(tTitulo, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Estilo.dp(ctx, 2) })
        tTexto.setTextColor(Estilo.TEXTO_2)
        addView(tTexto, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Estilo.dp(ctx, 12) })
        addView(View(ctx), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(rodape, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Troca o que a coluna diz. Mesmo texto = não anima (redesenho da aba no lugar). */
    fun mostrar(trilha: String, titulo: String, texto: String, tipo: IconeCruz.Tipo) {
        val chave = "$trilha|$titulo|$texto|$tipo"
        if (chave == atual) return
        val primeira = atual == null
        atual = chave
        val trocar = {
            tTrilha.text = trilha
            tTitulo.text = titulo
            tTexto.text = texto
            tTexto.visibility = if (texto.isEmpty()) View.GONE else View.VISIBLE
            // Ícone escuro sobre o laranja (o mesmo contraste do foco no Quadro); com alto contraste, preto.
            icone.background = IconeCruz(tipo, IconeCruz.Estilo.LINHA).apply { corFixa = br.paulo.tvlauncher.DesignTokens.NIGHT }
            contentDescription = "$trilha, $titulo"
        }
        if (primeira || (context as? android.app.Activity)?.let { SidePanel.semMovimento(it) } != false) { trocar(); return }
        val anda = Estilo.dp(context, 12).toFloat() * (if (layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1)
        val partes = listOf(azulejo, tTrilha, tTitulo, tTexto)
        partes.forEach { it.animate().cancel() }
        partes.forEach { v ->
            v.animate().alpha(0f).translationX(-anda).setDuration(SAI_MS).setInterpolator(Estilo.curva()).withEndAction {
                if (v === azulejo) trocar()
                v.translationX = anda
                v.animate().alpha(1f).translationX(0f).setDuration(ENTRA_MS).setInterpolator(Estilo.curva()).start()
            }.start()
        }
    }

    companion object {
        const val AZULEJO_DP = 54
        const val TITULO_SP = 34f
        /** Tempos do Projectivy na troca de página (500 ms entrando, 300 ms saindo), encurtados à metade: aqui só o texto muda. */
        const val SAI_MS = 150L
        const val ENTRA_MS = 250L
        /** Largura da coluna da lista (de 960 dp): a dele tem 344 dp; a nossa letra é maior (18 sp), então 400. */
        const val LISTA_DP = 400
    }
}
