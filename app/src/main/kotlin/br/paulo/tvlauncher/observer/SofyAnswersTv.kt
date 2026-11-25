package br.paulo.tvlauncher.observer

import android.app.ActivityManager
import android.content.Context
import android.media.AudioManager
import br.paulo.tvlauncher.AvisoTv
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.panel.PainelSaude
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** A parte do Android da Sofy respondendo ([SofyAnswers], ideias 44, 49 e 50). */
object SofyAnswersTv {
    enum class Problem { INTERNET, SOUND, SLOW, REMOTE }

    /** 44: o título da linha "Fazer" da busca para cada problema. */
    fun title(ctx: Context, p: Problem): String = ctx.getString(when (p) {
        Problem.INTERNET -> R.string.sofy_help_internet
        Problem.SOUND -> R.string.sofy_help_sound
        Problem.SLOW -> R.string.sofy_help_slow
        Problem.REMOTE -> R.string.sofy_help_remote
    })

    /** 44: confere o problema fora da tela e responde num aviso. Corrige só o que é seguro e foi pedido (mudo, memória). */
    fun check(ctx: Context, p: Problem) {
        val app = ctx.applicationContext
        Fundo.rodar("sofy-checar") {
            val texto = runCatching { answer(app, p) }.getOrNull() ?: return@rodar
            SofyBusTv.emit(app, SofyBus.Source.SOFY, "answered", detail = p.name.lowercase())
            ObserverTv.logShadow(app, "answered", "${p.name}: $texto")
            AvisoTv.mostrar(app, title(app, p), texto, 20, som = false, guardarSeApagada = false, historico = false)
        }
    }

    private fun answer(app: Context, p: Problem): String = when (p) {
        Problem.INTERNET -> {
            val w = Diagnostico.wifi(app); val i = Diagnostico.internet(app); val d = Diagnostico.dns(app); val v = Diagnostico.vpn(app)
            listOf(Diagnostico.veredito(w, i, d, v).detalhe, w.detalhe, i.detalhe).joinToString("\n")
        }
        Problem.SOUND -> {
            val am = app.getSystemService(AudioManager::class.java)
            val s = AudioManager.STREAM_MUSIC
            val max = am.getStreamMaxVolume(s)
            when (SofyAnswers.sound(am.isStreamMute(s), am.getStreamVolume(s), max, am.isVolumeFixed)) {
                SofyAnswers.Sound.FIXED_TV -> app.getString(R.string.sofy_help_sound_fixed)
                SofyAnswers.Sound.MUTED, SofyAnswers.Sound.ZERO -> {
                    am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0)
                    am.setStreamVolume(s, (max * SofyAnswers.FIX_SOUND_PCT / 100).coerceAtLeast(1), 0)
                    app.getString(R.string.sofy_help_sound_fixed_now, SofyAnswers.FIX_SOUND_PCT)
                }
                SofyAnswers.Sound.LOW -> app.getString(R.string.sofy_help_sound_low, pct(am.getStreamVolume(s), max))
                SofyAnswers.Sound.OK -> app.getString(R.string.sofy_help_sound_ok, pct(am.getStreamVolume(s), max))
            }
        }
        Problem.SLOW -> {
            val mi = ActivityManager.MemoryInfo().also { app.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            val livre = mi.availMem shr 20
            val normal = ObserverTv.normalOf(app, ObserverCore.Signal.MEMORY_AVAILABLE_MB)?.toLong()
            when (SofyAnswers.slow(livre, mi.totalMem shr 20, normal)) {
                SofyAnswers.Slow.OK -> app.getString(R.string.sofy_help_slow_ok, livre)
                else -> PainelSaude.liberar(app)?.let { app.getString(R.string.sofy_help_slow_freed, (it shr 20).coerceAtLeast(0)) }
                    ?: app.getString(R.string.sofy_help_slow_tight, livre)
            }
        }
        Problem.REMOTE -> app.getString(when (SofyAnswers.remote(SofyBusTv.events(app), System.currentTimeMillis())) {
            SofyAnswers.Remote.KEY_STUCK -> R.string.sofy_help_remote_stuck
            SofyAnswers.Remote.BATTERY_LOW -> R.string.sofy_help_remote_battery
            SofyAnswers.Remote.LAG -> R.string.sofy_help_remote_lag
            SofyAnswers.Remote.NONE -> R.string.sofy_help_remote_none
        })
    }

    private fun pct(v: Int, max: Int) = if (max > 0) v * 100 / max else 0

    // ---------------------------------------------------------------- 49: o que você fez hoje
    private fun dayStart(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun today(ctx: Context, now: Long = System.currentTimeMillis()): List<Pair<SofyAnswers.Today, Int>> =
        SofyAnswers.today(SofyBusTv.events(ctx), dayStart(now), now)

    /** As linhas na língua de [c] (a da TV, ou a do navegador no painel). Vazio = a frase de "dia tranquilo". */
    fun todayLines(c: Context, items: List<Pair<SofyAnswers.Today, Int>>): List<String> {
        val linhas = items.mapNotNull { (t, n) -> text(c, "sofy_today_${t.code}", n) }
        return linhas.ifEmpty { listOf(c.getString(R.string.sofy_today_none)) }
    }

    /** Na TV (busca "o que você fez hoje"): um aviso com o resumo. */
    fun showToday(ctx: Context) {
        val app = ctx.applicationContext
        Fundo.rodar("sofy-hoje") {
            val linhas = todayLines(app, today(app))
            AvisoTv.mostrar(app, app.getString(R.string.sofy_today_title), linhas.joinToString("\n"), 20, som = false, guardarSeApagada = false, historico = false)
        }
    }

    /** Para o relatório (painel e MCP): os grupos de hoje com a contagem. */
    fun todayJson(ctx: Context): JSONArray = JSONArray().apply {
        today(ctx).forEach { (t, n) -> put(JSONObject().put("code", t.code).put("count", n)) }
    }

    // ---------------------------------------------------------------- 50: como eu sei
    fun evidence(c: Context, kindName: String): String? {
        val k = ObserverCore.Kind.entries.firstOrNull { it.name == kindName } ?: return null
        return text(c, "sofy_evidence_${SofyAnswers.evidence(k).code}")
    }

    private fun text(c: Context, key: String, vararg args: Any): String? {
        @Suppress("DiscouragedApi")
        val id = c.resources.getIdentifier(key, "string", c.packageName)
        return if (id == 0) null else runCatching { c.getString(id, *args) }.getOrNull()
    }
}
