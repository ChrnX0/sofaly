package br.paulo.tvlauncher.observer

import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.privilege.GuardiaoShizuku
import br.paulo.tvlauncher.privilege.JanelaNaFrente
import java.util.concurrent.atomic.AtomicBoolean

/**
 * O lado Android do [GoogleHomeTrim]: anota quando o launcherx esteve na frente ([onWindow], serviço de acessibilidade)
 * e, na batida do Observador ([tick]), encerra o processo principal dele se ficou parado.
 * Prefs "google_home_trim" (fora do backup: é estado desta TV). adb: EXTRAS --es o observer mostra a linha.
 */
object GoogleHomeTrimTv {
    private const val TAG = "TvLauncher.GoogleTrim"
    private const val PREFS = "google_home_trim"
    @Volatile private var lastFrontAt = 0L
    private val running = AtomicBoolean(false)

    fun onWindow(foreground: CharSequence?) {
        if (foreground?.toString() == GoogleHomeTrim.PACKAGE) lastFrontAt = System.currentTimeMillis()
    }

    fun tick(ctx: Context) {
        val app = ctx.applicationContext
        val front = JanelaNaFrente.pacote
        val agora = System.currentTimeMillis()
        if (front == GoogleHomeTrim.PACKAGE) { lastFrontAt = agora; return }
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!GoogleHomeTrim.shouldTrim(front, lastFrontAt, p.getLong("last_at", 0L), agora)) return
        if (!running.compareAndSet(false, true)) return
        Fundo.rodar("google-home-trim") {
            try {
                val out = GuardiaoShizuku.comando(app, GoogleHomeTrim.COMMAND) ?: return@rodar   // sem shell: tenta na próxima batida
                val pid = GoogleHomeTrim.killedPid(out)
                p.edit().putLong("last_at", agora)
                    .apply { if (pid != null) putInt("count", p.getInt("count", 0) + 1).putLong("killed_at", agora) }
                    .apply()
                if (pid != null) Log.i(TAG, "launcherx parado em segundo plano encerrado (pid $pid)")
            } catch (e: Exception) {
                Log.w(TAG, "enxugar: $e")
            } finally { running.set(false) }
        }
    }

    fun json(ctx: Context): org.json.JSONObject {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return org.json.JSONObject().put("package", GoogleHomeTrim.PACKAGE)
            .put("count", p.getInt("count", 0)).put("last_check_at", p.getLong("last_at", 0L))
            .put("killed_at", p.getLong("killed_at", 0L)).put("front_at", lastFrontAt)
    }

    fun report(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val k = p.getLong("killed_at", 0L)
        return "launcher do Google parado: encerrado ${p.getInt("count", 0)} vezes" +
            if (k > 0) ", a última há ${(System.currentTimeMillis() - k) / 60_000} min" else ""
    }
}
