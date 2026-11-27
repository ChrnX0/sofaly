package br.paulo.onetv.celular

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.Size

/**
 * Leitor do QR da TV (27/11/2026, Paulo: "aparece um leitor de barras e não de QR"). A CaptureActivity padrão do ZXing
 * abre deitada (sensorLandscape), com uma janela larga e uma linha vermelha: a cara de leitor de código de barras de
 * supermercado. Aqui ela fica em pé (manifest: portrait), com uma janela QUADRADA no meio, sem a linha vermelha e com
 * os cantos na cor da marca, que é como os leitores de QR do Android e do iPhone se apresentam.
 * O resto (câmera, decodificação, devolver o texto lido para a TvsActivity) continua com a CaptureActivity.
 */
class QrScannerActivity : CaptureActivity() {

    override fun initializeContent(): DecoratedBarcodeView {
        // A classe-mãe monta o layout padrão do ZXing (câmera + máscara + texto) e devolve a vista; só ajustamos.
        val leitor = super.initializeContent()
        val lado = JanelaQr.lado(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        // Janela de leitura quadrada: o ZXing só procura o código dentro dela e escurece o resto da imagem.
        leitor.barcodeView.framingRectSize = Size(lado, lado)
        leitor.viewFinder.setLaserVisibility(false)   // a linha vermelha piscando é coisa de leitor de barras
        leitor.statusView.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)   // letra grande: quem usa pode enxergar pouco
            gravity = Gravity.CENTER
            val m = Visual.dp(context, 24)
            setPadding(m, m, m, m * 2)
        }
        // Cantos laranja por cima da máscara. A janela do ZXing fica centralizada na área da câmera (a tela inteira),
        // então um quadrado do mesmo lado centralizado no mesmo FrameLayout cai exatamente em cima dela.
        leitor.addView(CantosQr(this, lado), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return leitor
    }
}

/** Conta pura, fora da Activity para o teste na JVM não precisar carregar a câmera do Android. */
object JanelaQr {
    /** Lado do quadrado de leitura em pixels: 70% do lado menor da tela, cabe em pé em qualquer celular e deixa margem. */
    fun lado(largura: Int, altura: Int): Int = (minOf(largura, altura) * 0.7f).toInt()
}

/** Quatro cantos em "L" na cor da marca, desenhados em volta do quadrado de leitura. Só desenha: não recebe toques. */
private class CantosQr(ctx: Context, private val lado: Int) : View(ctx) {
    private val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DesignTokens.ORANGE
        style = Paint.Style.STROKE
        strokeWidth = Visual.dp(ctx, 5).toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private val braco = Visual.dp(ctx, 36).toFloat()

    init { isClickable = false; isFocusable = false }

    override fun onDraw(c: Canvas) {
        val l = (width - lado) / 2f
        val t = (height - lado) / 2f
        val r = l + lado
        val b = t + lado
        // Cada canto: uma linha na horizontal e outra na vertical, saindo do vértice.
        c.drawLine(l, t, l + braco, t, tinta); c.drawLine(l, t, l, t + braco, tinta)
        c.drawLine(r, t, r - braco, t, tinta); c.drawLine(r, t, r, t + braco, tinta)
        c.drawLine(l, b, l + braco, b, tinta); c.drawLine(l, b, l, b - braco, tinta)
        c.drawLine(r, b, r - braco, b, tinta); c.drawLine(r, b, r, b - braco, tinta)
    }
}
