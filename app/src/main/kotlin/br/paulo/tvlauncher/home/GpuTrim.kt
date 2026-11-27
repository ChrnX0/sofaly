package br.paulo.tvlauncher.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import br.paulo.tvlauncher.Memoria
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Devolve a memória de GPU da HOME como o Android faria com um app comum (refatoração G, etapa 4b; o porquê está em
 * [Memoria.GPU_HOME_ESCONDIDA]): nível 20 quando a HOME sai da tela, nível 40 depois de [Memoria.GPU_ESPERA_APAGADA_MS]
 * com a tela apagada. Chama o mesmo método que o ActivityThread chama no aviso do sistema
 * (WindowManagerGlobal.trimMemory, API escondida, pela HiddenApiBypass). Sempre na thread da tela, como lá.
 * Falhou uma vez (outro Android): desiste quieto, a HOME segue como antes.
 */
object GpuTrim {
    private const val TAG = "TvLauncher.Memoria"
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var falhou = false
    @Volatile var ultimo: String = "nenhum"   // EXTRAS leveza / gpu: o último pedido e quanto levou
        private set

    /** Pede ao HWUI que solte a GPU no [nivel] (20 ou 40). Pode ser chamado de qualquer thread. */
    fun soltar(nivel: Int, motivo: String) {
        if (falhou) return
        if (Looper.myLooper() != Looper.getMainLooper()) { ui.post { soltar(nivel, motivo) }; return }
        val t0 = SystemClock.elapsedRealtime()
        try {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val instancia = HiddenApiBypass.invoke(wmg, null, "getInstance")
            HiddenApiBypass.invoke(wmg, instancia, "trimMemory", nivel)
            val ms = SystemClock.elapsedRealtime() - t0
            ultimo = "$nivel ($motivo) em ${ms} ms"
            Log.i(TAG, "GPU solta: nível $nivel, $motivo, ${ms} ms")
        } catch (e: Throwable) {
            falhou = true
            ultimo = "falhou: ${e.javaClass.simpleName}"
            Log.w(TAG, "GPU: trimMemory indisponível, desisto", e)
        }
    }

    private val tarefaApagada = Runnable { telaApagada?.let { if (!it.isInteractive) soltar(Memoria.GPU_TELA_APAGADA, "tela apagada") } }
    private var telaApagada: PowerManager? = null

    /** Uma vez, no processo principal (LauncherApp): tela apagada há um tempo = solta tudo. */
    fun vigiar(app: Context) {
        if (telaApagada != null) return
        telaApagada = app.getSystemService(PowerManager::class.java) ?: return
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                ui.removeCallbacks(tarefaApagada)
                if (i.action == Intent.ACTION_SCREEN_OFF) ui.postDelayed(tarefaApagada, Memoria.GPU_ESPERA_APAGADA_MS)
            }
        }, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) })
    }
}
