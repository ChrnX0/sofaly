package br.paulo.tvlauncher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.ImageView
import br.paulo.tvlauncher.companion.PhoneCameraTv

/**
 * A tela que mostra a câmera do celular ([PhoneCameraTv]). Tela cheia, preta, sem texto: a imagem ocupa o que couber sem
 * cortar. Fecha com VOLTAR, quando o celular para, ou 4 s sem foto nova.
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
        setContentView(imagem)
        PhoneCameraTv.listen { ui.post { mostrar() } }
        ui.postDelayed(vigia, 4_000)
    }

    private fun mostrar() {
        if (PhoneCameraTv.lastAt == 0L) { finish(); return }
        PhoneCameraTv.latest?.let { imagem.setImageBitmap(it) }
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        PhoneCameraTv.listen(null)
        super.onDestroy()
    }
}
