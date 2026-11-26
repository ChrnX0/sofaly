package br.paulo.tvlauncher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import br.paulo.tvlauncher.companion.PhoneCameraTv

/**
 * A tela que mostra a câmera do celular ([PhoneCameraTv]). A imagem ocupa a tela inteira sem cortar; por cima, o título e
 * a barra de dicas do sistema (Estilo, regra das telas), que somem depois de 4 s para não tapar a imagem. Fecha com
 * VOLTAR, quando o celular para, ou 4 s sem foto nova.
 */
class PhoneCameraActivity : Activity() {
    private lateinit var imagem: ImageView
    private val ui = Handler(Looper.getMainLooper())
    private val vigia = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() - PhoneCameraTv.lastAt > 4_000) { finish(); return }
            ui.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (VigiaAtualizacao.fechada(this)) return
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        imagem = ImageView(this).apply { setBackgroundColor(Color.BLACK); scaleType = ImageView.ScaleType.FIT_CENTER }
        val m = Estilo.dp(this, Estilo.MARGEM_LATERAL); val v = Estilo.dp(this, Estilo.MARGEM_VERTICAL)
        val titulo = Estilo.tituloTela(this, getString(R.string.phone_camera_title))
        val dicas = Estilo.barraDicas(this, getString(R.string.cuidar_tecla_voltar) to getString(R.string.fp_fechar))
        setContentView(FrameLayout(this).apply {
            addView(imagem, FrameLayout.LayoutParams(-1, -1))
            addView(titulo, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(m, v, m, 0) })
            addView(dicas, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(m, 0, m, v) })
        })
        ui.postDelayed({ titulo.animate().alpha(0f).setDuration(br.paulo.tvlauncher.DesignTokens.MS_SLOW); dicas.animate().alpha(0f).setDuration(br.paulo.tvlauncher.DesignTokens.MS_SLOW) }, 4_000)
        PhoneCameraTv.listen(ouvinte)
        ui.postDelayed(vigia, 4_000)
    }

    private val ouvinte: () -> Unit = { ui.post { mostrar() } }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        PhoneCameraTv.dismissed = true
        @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun mostrar() {
        if (PhoneCameraTv.lastAt == 0L) { finish(); return }
        PhoneCameraTv.latest?.let { imagem.setImageBitmap(it) }
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        PhoneCameraTv.unlisten(ouvinte)
        super.onDestroy()
    }
}
