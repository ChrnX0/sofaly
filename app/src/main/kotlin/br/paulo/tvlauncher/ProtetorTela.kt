package br.paulo.tvlauncher

import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import br.paulo.tvlauncher.games.JogosTv
import br.paulo.tvlauncher.panel.FotosRede
import br.paulo.tvlauncher.screensaver.FotosProtetor
import br.paulo.tvlauncher.screensaver.SleepingHome

/**
 * O protetor de tela do launcher (um DreamService do Android). Existe para APOSENTAR o protetor de fábrica
 * (com.google.android.apps.tv.dreamx, ~59 MB residentes): apontamos o protetor do sistema para este.
 *
 * Desde 21/11/2026 (Paulo: "a tela de descanso nada mais é do que a tela de clima, mas mais simplificada") ele é a
 * HOME QUE ADORMECE ([SleepingHome]): o MESMO fundo da tela inicial, sem a cruz, com a hora, o tempo de agora e a
 * frase do dia em cima e a faixa de baixo que roda sozinha ou com ↑/↓. HOME, VOLTAR e as outras teclas acordam a TV
 * (a cruz volta); volume não acorda. Saíram a pintura própria do protetor, as fotos da NASA e os 14 liga/desliga.
 * Fotos enviadas pelo painel ou da pasta da rede (ideia 27) continuam passando por trás, como antes.
 */
class ProtetorTela : DreamService() {
    private val ui = Handler(Looper.getMainLooper())
    private var descanso: SleepingHome? = null

    override fun onAttachedToWindow() {
        atual = java.lang.ref.WeakReference(this)
        super.onAttachedToWindow()
        isInteractive = true         // as teclas chegam ao dispatchKeyEvent; quem fecha é ele
        isFullscreen = true
        // Modo jogo (card Jogos): com um jogo na frente o protetor não fica; fecha e devolve o jogo.
        if (JogosTv.modoJogoAgora(this)) { ui.post { fechar() }; return }
        isScreenBright = true        // o fundo é o da HOME; quem escurece aos poucos é o SleepingHome (contra marcar a tela)
        FotosRede.talvezSincronizar(this)   // pasta da rede (ideia 27): renova a cópia se tiver mais de 6 h
        val s = SleepingHome(this)
        val raiz = s.montar() as FrameLayout
        fotos = (FotosProtetor.listar(this) + FotosRede.listar(this)).shuffled()
        if (fotos.isNotEmpty()) {
            // Por cima do fundo do tema e por baixo do véu e do texto (posição 1).
            var i = 1
            for (v in camadas) raiz.addView(v.apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }, i++, FrameLayout.LayoutParams(-1, -1))
            raiz.addView(View(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x66000000, 0x22000000, 0x66000000))
            }, i, FrameLayout.LayoutParams(-1, -1))
            ui.post(trocarFoto)
        }
        setContentView(raiz)
        descanso = s
    }

    // ---------------------------------------------------------------- fotos (FotosProtetor.kt)
    private var fotos: List<java.io.File> = emptyList()
    private val camadas by lazy { arrayOf(ImageView(this), ImageView(this)) }
    private var indiceFoto = -1
    private var camadaAtual = 0
    private val carregador = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** Decodifica a próxima foto fora da thread da tela e esmaece da atual para ela em 2 s. */
    private val trocarFoto = object : Runnable {
        override fun run() {
            if (fotos.isEmpty()) return
            indiceFoto = (indiceFoto + 1) % fotos.size
            val f = fotos[indiceFoto]
            val m = resources.displayMetrics
            carregador.execute {
                val bmp = FotosProtetor.carregar(f, m.widthPixels, m.heightPixels)
                ui.post {
                    if (carregador.isShutdown) return@post   // o protetor já fechou enquanto a foto carregava
                    if (bmp != null) {
                        val entra = camadas[1 - camadaAtual]
                        val sai = camadas[camadaAtual]
                        entra.setImageBitmap(bmp)
                        entra.animate().alpha(1f).setDuration(2000).start()
                        sai.animate().alpha(0f).setDuration(2000).withEndAction { sai.setImageDrawable(null) }.start()   // solta a memória
                        camadaAtual = 1 - camadaAtual
                    }
                    ui.postDelayed(this, FotosProtetor.TROCA_MS)
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val acao = SleepingHome.acao(event.keyCode)
        if (acao == SleepingHome.Acao.PASSAR) return super.dispatchKeyEvent(event)
        // Age na descida; a subida da mesma tecla é engolida (senão cairia no app que volta para a frente).
        if (event.action != android.view.KeyEvent.ACTION_DOWN) return true
        val s = descanso
        when (acao) {
            SleepingHome.Acao.ANTERIOR -> { s?.tecla(); s?.mover(-1) }
            SleepingHome.Acao.PROXIMA -> { s?.tecla(); s?.mover(1) }
            else -> if (event.repeatCount == 0) fechar()
        }
        return true
    }

    /** Fecha o sonho como a tecla fazia antes (wakeUp devolve a tela ao que estava por baixo). */
    private fun fechar() { if (android.os.Build.VERSION.SDK_INT >= 29) wakeUp() else finish() }

    override fun onDetachedFromWindow() {
        if (atual?.get() === this) atual = null
        ui.removeCallbacks(trocarFoto)
        descanso?.soltar(); descanso = null
        carregador.shutdownNow()
        camadas.forEach { it.setImageDrawable(null) }
        super.onDetachedFromWindow()
    }

    companion object {
        /** O protetor que está na tela agora (só um por vez). Fraco: não segura o serviço depois de fechado. */
        @Volatile private var atual: java.lang.ref.WeakReference<ProtetorTela>? = null

        /**
         * Fecha o protetor se estiver aberto. Para quem abre uma tela de fora (cena, MCP, pedido): sem isso a tela abria
         * POR BAIXO do protetor e ninguém via (teste na TV, 15/11/2026: cena "abrir previsão" ficou atrás dele).
         */
        /** O protetor está na tela agora? */
        fun aberto(): Boolean = atual?.get() != null

        fun fecharSeAberto() {
            val p = atual?.get() ?: return
            manterAcordada(p.applicationContext)
            android.os.Handler(android.os.Looper.getMainLooper()).post { try { p.fechar() } catch (_: Exception) {} }
        }

        /**
         * Acordar só com wakeUp não conta como uso da TV: se ela já estava parada há mais que o tempo do protetor, o
         * Android volta a sonhar na mesma hora (teste na Sala, 15/11/2026: a confirmação abriu atrás de um protetor
         * novo). Uma trava de tela por 20 s segura a TV acordada e, ao soltar, conta como uso (ON_AFTER_RELEASE): o
         * protetor só volta depois do tempo normal.
         */
        @Suppress("DEPRECATION")
        private fun manterAcordada(ctx: android.content.Context) {
            try {
                ctx.getSystemService(android.os.PowerManager::class.java)
                    ?.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ON_AFTER_RELEASE,
                        "onetv:sai-do-protetor")?.acquire(20_000L)
            } catch (_: Exception) {}
        }

    }
}
