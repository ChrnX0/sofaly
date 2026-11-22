package br.paulo.tvlauncher.home

import android.content.Context
import android.net.Uri
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

/**
 * Motor leve do fundo animado (degrau 1 da proposta C, 22/11/2026). Um só para o "Meu fundo", os plugins e o descanso.
 *
 * Porquê: o vídeo de fundo era um TextureView dentro de uma camada de hardware (criarFundo). Cada quadro do vídeo
 * refazia a camada inteira de 1920x1080 e depois a GPU colava a camada de novo: duas pinturas de tela cheia por quadro,
 * disputando a GPU com a cruz numa TV de 32 bits.
 * Para quê: o vídeo andar sem roubar quadro da navegação.
 * Como (o mesmo caminho dos players de filme e do Projectivy, que também usa ExoPlayer):
 *  - [SurfaceView]: o vídeo vai direto para a camada de vídeo da TV (o compositor de hardware sobrepõe), sem passar
 *    pela GPU do app. A cruz e o vídeo deixam de dividir o mesmo quadro.
 *  - ExoPlayer com [Player.REPEAT_MODE_ONE]: ele já carrega o começo enquanto o fim toca, então o laço não pisca
 *    (o MediaPlayer reabria o arquivo e às vezes mostrava um quadro preto).
 *  - Sem faixa de áudio (nem decodifica o som) e com buffer curto (arquivo local: 2 a 5 s bastam, pouca memória).
 *  - Um tocador por camada, reaproveitado entre um vídeo e o próximo: nunca dois decodificadores vivos por camada.
 *  - Só anda com a janela visível (outra tela por cima ou TV dormindo = pausa e solta nada até voltar).
 *  - Recorte "preencher a tela" feito no tamanho do SurfaceView (o modo de recorte do decodificador não vale em todo aparelho).
 *  - Entrada suave: um véu preto por cima some quando o 1º quadro chega (SurfaceView não respeita alpha em todo Android).
 *
 * Limite: um SurfaceView "fura" a janela; nada acima dele pode ter camada de hardware nem desfoque (o furo some atrás
 * da camada). Por isso [liberar] tira a camada e o desfoque do fundo que contém o vídeo, e [restaurar] devolve.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class VideoLayer(ctx: Context) : FrameLayout(ctx) {
    private val superficie = SurfaceView(ctx)
    private val veu = View(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK) }
    private val escurecer = View(ctx)
    private var tocador: ExoPlayer? = null
    private var proporcao = 16f / 9f

    /** Chamado na thread da tela quando o 1º quadro de um vídeo novo aparece. */
    var aoPrimeiroQuadro: (() -> Unit)? = null

    init {
        addView(superficie, LayoutParams(-1, -1, Gravity.CENTER))
        addView(escurecer, LayoutParams(-1, -1))
        addView(veu, LayoutParams(-1, -1))
        escurecer.visibility = GONE
    }

    /** Brilho do "Meu fundo" (-100..100) como véu preto ou branco por cima: a pintura de camada não vale para vídeo. */
    fun brilho(v: Int) {
        val a = VideoLayerRules.alfaVeu(v)
        if (a == 0) { escurecer.visibility = GONE; return }
        escurecer.setBackgroundColor((a shl 24) or (if (v < 0) 0x000000 else 0xFFFFFF))
        escurecer.visibility = VISIBLE
    }

    /** Toca [uri] em laço, sem som. Reaproveita o tocador (um decodificador só). */
    fun tocar(uri: Uri) {
        veu.animate().cancel(); veu.alpha = 1f; veu.visibility = VISIBLE
        val p = tocador ?: criarTocador().also { tocador = it }
        runCatching {
            p.setMediaItem(MediaItem.fromUri(uri))
            p.prepare()
            p.playWhenReady = windowVisibility == VISIBLE
        }
    }

    fun tocar(f: java.io.File) = tocar(Uri.fromFile(f))

    private fun criarTocador(): ExoPlayer {
        val carga = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_000, 5_000, 500, 500)
            .build()
        return ExoPlayer.Builder(context).setLoadControl(carga).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            setVideoSurfaceView(superficie)
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    veu.animate().alpha(0f).setDuration(VideoLayerRules.ENTRADA_MS)
                        .withEndAction { veu.visibility = GONE }.start()
                    aoPrimeiroQuadro?.invoke()
                }
                override fun onVideoSizeChanged(s: VideoSize) {
                    if (s.width > 0 && s.height > 0) {
                        proporcao = s.width * s.pixelWidthHeightRatio / s.height
                        requestLayout()
                    }
                }
                // Vídeo que o aparelho não toca: fica o véu preto, sem caixa de erro (nada aqui derruba a HOME).
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {}
            })
        }
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        super.onMeasure(wSpec, hSpec)
        // Cobrir a tela inteira mantendo a proporção do vídeo (o que sobra sai pelas bordas da tela).
        val (w, h) = VideoLayerRules.cobrir(measuredWidth, measuredHeight, proporcao)
        superficie.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // O fundo que contém este vídeo (criarFundo pôs nele camada de hardware, e talvez desfoque): tirar, senão o furo some.
        var v: android.view.ViewParent? = parent
        while (v != null && v !is FundoVivo) v = v.parent
        (v as? View)?.let { if (visibility == VISIBLE) liberar(it) }
    }

    override fun onWindowVisibilityChanged(v: Int) {
        super.onWindowVisibilityChanged(v)
        tocador?.playWhenReady = v == VISIBLE
    }

    /** Solta o decodificador (a camada pode voltar a tocar depois com [tocar]). */
    fun soltar() {
        tocador?.let { runCatching { it.release() } }
        tocador = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        soltar()
    }

    companion object {
        private const val TAG_CAMADA = 0x5EF0_0001
        private const val TAG_EFEITO = 0x5EF0_0002

        /** Tira do [fundo] a camada de hardware e o desfoque (eles escondem o furo do SurfaceView). Guarda para [restaurar]. */
        fun liberar(fundo: View) {
            if (fundo.getTag(TAG_CAMADA) != null) return
            fundo.setTag(TAG_CAMADA, fundo.layerType)
            fundo.setTag(TAG_EFEITO, android.os.Build.VERSION.SDK_INT >= 31)
            fundo.setLayerType(View.LAYER_TYPE_NONE, null)
            if (android.os.Build.VERSION.SDK_INT >= 31) fundo.setRenderEffect(null)
            // O preto de segurança do criarFundo ficaria atrás do furo: inofensivo (o furo limpa por cima).
        }

        /** Devolve a camada de hardware (e o desfoque escolhido) quando o fundo volta a ser imagem. */
        fun restaurar(fundo: View) {
            val camada = fundo.getTag(TAG_CAMADA) as? Int ?: return
            fundo.setTag(TAG_CAMADA, null)
            fundo.setLayerType(camada, (fundo as? FundoMeu)?.pintura)
            if (fundo.getTag(TAG_EFEITO) == true) br.paulo.tvlauncher.settings.BackgroundStyle.desfocar(fundo.context, fundo)
            fundo.setTag(TAG_EFEITO, null)
        }
    }
}

/** Contas puras do [VideoLayer] (testadas na JVM). */
object VideoLayerRules {
    const val ENTRADA_MS = 400L

    /** Tamanho que cobre [w]x[h] com a proporção [p] (largura/altura), sem faixa preta. */
    fun cobrir(w: Int, h: Int, p: Float): Pair<Int, Int> {
        if (w <= 0 || h <= 0 || p <= 0f) return w to h
        return if (w.toFloat() / h > p) w to Math.round(w / p) else Math.round(h * p) to h
    }

    /** Opacidade (0..255) do véu que imita o brilho -100..100 da pintura de camada (só a parte do brilho). */
    fun alfaVeu(brilho: Int): Int = Math.round(kotlin.math.abs(brilho.coerceIn(-100, 100)) / 100f * 0.85f * 255)
}
