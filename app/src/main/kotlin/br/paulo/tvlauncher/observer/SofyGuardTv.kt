package br.paulo.tvlauncher.observer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import br.paulo.tvlauncher.AvisoTv
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.resources.R
import br.paulo.tvlauncher.family.Diario
import org.json.JSONObject

/** A parte do Android da Sofy guardando a casa ([SofyGuard], ideias 42, 43, 46, 47 e 48). */
object SofyGuardTv {
    private const val TAG = "TvLauncher.SofyGuard"
    private const val LOST_GAP_MS = 30 * 60_000L
    private const val STUCK_GAP_MS = 3_600_000L
    private const val PIN_GAP_MS = 6 * 3_600_000L

    private val lost = SofyGuard.LostMeter()
    @Volatile private var lostAt = 0L
    private val stuckAt = HashMap<Int, Long>()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(ObserverTv.PREFS, Context.MODE_PRIVATE)

    /** No funil das teclas (thread principal): barato, só conta. Nunca engole a tecla. */
    fun key(ctx: Context, e: KeyEvent) {
        if (e.action != KeyEvent.ACTION_DOWN) return
        val now = SystemClock.elapsedRealtime()
        if (e.repeatCount == 0 && lost.key(e.keyCode, now) && now - lostAt > LOST_GAP_MS) {
            lostAt = now
            lostHelp(ctx.applicationContext)
        }
        if (SofyGuard.stuck(e.keyCode, e.repeatCount)) {
            val last = synchronized(stuckAt) { stuckAt[e.keyCode] ?: 0L }
            if (now - last > STUCK_GAP_MS) {
                synchronized(stuckAt) { stuckAt[e.keyCode] = now }
                stuckKey(ctx.applicationContext, e.keyCode)
            }
        }
    }

    /** 42: a dica de como voltar, uma vez a cada meia hora, e o barramento fica sabendo. */
    private fun lostHelp(app: Context) {
        runCatching { AvisoTv.rapido(app, app.getString(R.string.sofy_lost_title), app.getString(R.string.sofy_lost_text), 10) }
        // Disco e travas do ObserverTv fora da thread principal: aqui estamos no funil das teclas (revisor, 25/11/2026).
        Fundo.rodar("sofy-perdido") {
            SofyBusTv.emit(app, SofyBus.Source.SOFY, "lost_help")
            ObserverTv.logShadow(app, "lost_help", "VOLTAR/INÍCIO repetidos sem navegar: mostrei como voltar")
        }
    }

    /** 43: tecla presa (segurada ~8 s, fora o volume). Vai à Ilha: o controle pode estar apertado no sofá. */
    private fun stuckKey(app: Context, keyCode: Int) {
        val nome = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
        Fundo.rodar("sofy-tecla-presa") {
            SofyBusTv.emit(app, SofyBus.Source.DEVICE, "key_stuck", ok = false, detail = nome)
            ObserverTv.logShadow(app, "key_stuck", "tecla $nome segurada por mais de 8 s")
            ObserverTv.enqueueNote(app, SofyGuard.keyStuckNote(nome, System.currentTimeMillis()))
        }
    }

    /** 47: o painel recusou o PIN. Cinco em uma hora = a Ilha avisa (e o Diário guarda quando). Chamado pelo painel. */
    fun pinWrong(ctx: Context) {
        val app = ctx.applicationContext
        SofyBusTv.emit(app, SofyBus.Source.HOUSE, "panel_pin_wrong", ok = false)
        val now = System.currentTimeMillis()
        val n = SofyGuard.pinAlarm(SofyBusTv.events(app), now) ?: return
        val p = prefs(app)
        if (now - p.getLong("guard_pin_told_at", 0L) < PIN_GAP_MS) return
        p.edit().putLong("guard_pin_told_at", now).apply()
        ObserverTv.logShadow(app, "panel_pin_wrong", "$n PINs errados no painel em 1 h")
        runCatching { Diario.anotar(app, "tv", "Sofy: PIN errado no painel", "$n tentativas erradas na última hora") }
        ObserverTv.enqueueNote(app, SofyGuard.pinWrongNote(n, now))
    }

    /** 46 e 48: uma vez por dia (pelo autoexame). Apps novos com poder demais; atualização do Google que quebrou algo. */
    fun daily(ctx: Context, now: Long) {
        val app = ctx.applicationContext
        val p = prefs(app)
        if (now - p.getLong("guard_daily_at", 0L) < 20 * 3_600_000L) return
        p.edit().putLong("guard_daily_at", now).apply()
        runCatching { newApps(app, now) }.onFailure { Log.w(TAG, "apps novos: $it") }
        runCatching { systemChecks(app, now) }.onFailure { Log.w(TAG, "checagens: $it") }
    }

    private fun newApps(app: Context, now: Long) {
        val pm = app.packageManager
        val user = pm.getInstalledApplications(0).filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != app.packageName }
        val nowSet = user.map { it.packageName }.toSet()
        val p = prefs(app)
        val before = p.getString("guard_known_pkgs", null)?.split(',')?.filter { it.isNotEmpty() }?.toSet()
        p.edit().putString("guard_known_pkgs", nowSet.joinToString(",")).apply()
        for (pkg in SofyGuard.newPackages(before, nowSet)) {
            val info = runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
            }.getOrNull() ?: continue
            val powers = SofyGuard.powers(info.requestedPermissions?.toList().orEmpty(), info.services?.mapNotNull { it.permission }.orEmpty())
            if (!SofyGuard.tooPowerful(powers)) continue
            val nome = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            ObserverTv.logShadow(app, "app_powerful", "$pkg pede: ${powers.joinToString(", ")}")
            runCatching { Diario.anotar(app, "tv", "Sofy: app novo com muito poder", "$nome ($pkg): ${powers.joinToString(", ")}") }
            SofyBusTv.emit(app, SofyBus.Source.SOFY, "app_powerful", ok = false, detail = powers.joinToString(","))
            ObserverTv.enqueueNote(app, ObserverCore.sofyNote(ObserverCore.Kind.APP_POWERFUL, now, ObserverCore.Step.SUGGESTS, listOf(nome, powers.size.toString())))
        }
    }

    /** As checagens do que a casa usa do Google: busca por voz, loja, ajustes, Chromecast (cast) e a HOME do Google. */
    private fun checks(app: Context): Map<String, Boolean> {
        val pm = app.packageManager
        fun resolves(i: Intent) = runCatching { pm.queryIntentActivities(i, 0).isNotEmpty() }.getOrDefault(false)
        fun enabled(pkg: String) = runCatching { pm.getApplicationInfo(pkg, 0).enabled }.getOrDefault(false)
        return mapOf(
            "voice" to resolves(Intent("android.speech.action.WEB_SEARCH")),
            "store" to (pm.getLeanbackLaunchIntentForPackage("com.android.vending") != null || pm.getLaunchIntentForPackage("com.android.vending") != null),
            "settings" to resolves(Intent(android.provider.Settings.ACTION_SETTINGS)),
            "cast" to enabled("com.google.android.apps.mediashell"),
            "google_home" to enabled("com.google.android.apps.tv.launcherx"),
        )
    }

    private fun versions(app: Context): Map<String, Long> = SofyGuard.WATCHED.mapNotNull { pkg ->
        runCatching { pkg to app.packageManager.getPackageInfo(pkg, 0).longVersionCode }.getOrNull()
    }.toMap()

    private fun systemChecks(app: Context, now: Long) {
        val p = prefs(app)
        val beforeChecks = p.getString("guard_checks", null)?.let { s -> runCatching { JSONObject(s) }.getOrNull() }
            ?.let { o -> o.keys().asSequence().associateWith { o.optBoolean(it) } }.orEmpty()
        val beforeVers = p.getString("guard_versions", null)?.let { s -> runCatching { JSONObject(s) }.getOrNull() }
            ?.let { o -> o.keys().asSequence().associateWith { o.optLong(it) } }.orEmpty()
        val c = checks(app)
        val v = versions(app)
        p.edit().putString("guard_checks", JSONObject(c as Map<*, *>).toString()).putString("guard_versions", JSONObject(v as Map<*, *>).toString()).apply()
        val broke = SofyGuard.regressions(beforeChecks, c)
        if (broke.isEmpty()) return
        val updated = SofyGuard.watchedUpdated(beforeVers, v)
        val why = "parou: ${broke.joinToString(", ")}" + if (updated.isEmpty()) " (sem atualização do Google)" else " depois de atualizar ${updated.joinToString(", ")}"
        ObserverTv.logShadow(app, "system_update_broke", why)
        runCatching { Diario.anotar(app, "tv", "Sofy: algo do Google parou", why) }
        SofyBusTv.emit(app, SofyBus.Source.UPDATE, "google_broke", ok = false, detail = broke.joinToString(","))
        if (updated.isNotEmpty()) ObserverTv.enqueueNote(app, ObserverCore.sofyNote(ObserverCore.Kind.SYSTEM_UPDATE_BROKE, now,
            ObserverCore.Step.OBSERVED, listOf(broke.size.toString())))
    }
}
