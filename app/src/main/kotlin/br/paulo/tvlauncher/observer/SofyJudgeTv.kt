package br.paulo.tvlauncher.observer

import android.content.Context
import android.media.AudioManager
import android.util.Log
import br.paulo.tvlauncher.alarm.Despertador
import br.paulo.tvlauncher.family.Diario

/** A parte do Android do juiz de conflitos ([SofyJudge]): monta a situação de agora e anota cada espera. */
object SofyJudgeTv {
    private const val TAG = "TvLauncher.SofyJudge"
    private val lastWhy = HashMap<SofyJudge.Task, String>()

    fun situation(ctx: Context): SofyJudge.Situation {
        val app = ctx.applicationContext
        val now = System.currentTimeMillis()
        val front = runCatching { AppProfileTv.frontPackage(app) }.getOrNull()
        return SofyJudge.Situation(
            now = now,
            nextAlarmAt = runCatching { Despertador.proximo(app)?.second }.getOrNull() ?: 0L,
            alarmRinging = runCatching { SofyJudge.alarmRinging(SofyBusTv.events(app), now) }.getOrDefault(false),
            mediaActive = runCatching { app.getSystemService(AudioManager::class.java)?.isMusicActive == true }.getOrDefault(false),
            cloudGameFront = front != null && front in SofyJudge.CLOUD_GAMES,
        )
    }

    /** Pode rodar [t] agora? Quando não, anota o porquê (uma vez por motivo, para não encher o registro). */
    fun mayRun(ctx: Context, t: SofyJudge.Task): SofyJudge.Verdict {
        val v = runCatching { SofyJudge.decide(t, situation(ctx)) }.getOrElse { SofyJudge.Verdict(true) }
        if (v.go) return v
        val app = ctx.applicationContext
        val novo = synchronized(lastWhy) { (lastWhy[t] != v.why).also { lastWhy[t] = v.why } }
        SofyBusTv.emit(app, SofyBus.Source.SOFY, "conflict_${t.name.lowercase()}", ok = false, detail = v.why, minGapMs = 10 * 60_000L)
        if (novo) {
            ObserverTv.logShadow(app, "judge_wait", "${t.name.lowercase()} esperou ${v.waitMs / 60_000} min: ${v.why}")
            runCatching { Diario.anotar(app, "tv", "Sofy segurou: ${t.name.lowercase()}", v.why) }
        }
        Log.i(TAG, "$t espera ${v.waitMs / 60_000} min: ${v.why}")
        return v
    }
}
