package br.paulo.tvlauncher.observer

import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.AvisoTv
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.family.Diario
import br.paulo.tvlauncher.network.UnifiArmazem
import br.paulo.tvlauncher.network.UnifiCliente
import br.paulo.tvlauncher.settings.Extras

/** A parte do Android da Sofy de olho na casa ([SofyHouse], ideias 27 a 31). Fora da tela. */
object SofyHouseTv {
    private const val TAG = "TvLauncher.SofyHouse"
    private const val GAME_GAP_MS = 2 * 3_600_000L
    private const val DAILY_GAP_MS = 20 * 3_600_000L
    private const val TELL_GAP_MS = 7 * SofyHouse.DAY_MS
    /** Watts da TV + Chromecast e tarifa (centavos por kWh), para a energia da Retrospectiva (ideia 30). 0 = não informado.
     *  Nas preferências do Extras (vão no backup); adb: EXTRAS --es o energy --ei watts 120 --ei centavos 95. */
    const val KEY_WATTS = "energy_watts"
    const val KEY_CENTS_KWH = "energy_cents_kwh"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(ObserverTv.PREFS, Context.MODE_PRIVATE)

    /** Já contou isto há menos de [gap]? Senão, marca agora e deixa contar. */
    private fun once(ctx: Context, key: String, gap: Long, now: Long): Boolean {
        val p = prefs(ctx)
        if (now - p.getLong(key, 0L) < gap) return false
        p.edit().putLong(key, now).apply()
        return true
    }

    /** 28: na batida do juiz. Jogo na nuvem na frente + internet bem mais lenta que o normal da casa = avisa. */
    fun onJudge(ctx: Context, snap: Map<ObserverCore.Signal, ObserverCore.Reading>, now: Long) {
        val front = runCatching { AppProfileTv.frontPackage(ctx) }.getOrNull() ?: return
        if (front !in SofyJudge.CLOUD_GAMES) return
        val lat = snap[ObserverCore.Signal.LATENCY_MS]?.value
        val normal = ObserverTv.normalOf(ctx, ObserverCore.Signal.LATENCY_MS)?.toLong()
        if (!SofyHouse.gameLag(lat, normal) || !once(ctx, "house_game_lag_at", GAME_GAP_MS, now)) return
        val app = ctx.applicationContext
        SofyBusTv.emit(app, SofyBus.Source.NETWORK, "game_lag", ok = false, detail = "$lat ms (normal $normal ms)")
        ObserverTv.logShadow(app, "game_lag", "jogo na nuvem com a internet a $lat ms (normal da casa: $normal ms)")
        // Modo jogo ligado = avisos esperam: fica só a nota na Ilha para depois.
        if (!Extras.ligado(app, Extras.Chave.MODO_JOGO)) runCatching {
            AvisoTv.rapido(app, app.getString(R.string.observer_game_lag_title), app.getString(R.string.observer_game_lag_text, lat.toString()), 8)
        }
        ObserverTv.enqueueNote(app, ObserverCore.sofyNote(ObserverCore.Kind.GAME_LAG, now, ObserverCore.Step.SUGGESTS, listOf(lat.toString())))
    }

    /** 27, 29 e 31: uma vez por dia (pelo autoexame da Sofy). Cada um fala no máximo uma vez por semana (o Wi-Fi, a cada 2). */
    fun daily(ctx: Context, now: Long) {
        if (!once(ctx, "house_daily_at", DAILY_GAP_MS, now)) return
        val app = ctx.applicationContext
        val events = runCatching { SofyBusTv.events(app) }.getOrDefault(emptyList())
        runCatching { wifi(app, events, now) }.onFailure { Log.w(TAG, "wifi: $it") }
        val wake = SofyHouse.wakeFailures(events, now)
        if (wake >= SofyHouse.WAKE_FAILS && once(app, "house_wake_told_at", TELL_GAP_MS, now)) {
            ObserverTv.logShadow(app, "tv_wake_failing", "a LG não respondeu ao despertador em $wake manhãs da semana")
            runCatching { Diario.anotar(app, "tv", "Sofy: a TV não ligou com o despertador", "$wake manhãs nesta semana. Conferir o HDMI-CEC (SimpLink) na LG.") }
            ObserverTv.enqueueNote(app, ObserverCore.sofyNote(ObserverCore.Kind.TV_WAKE_FAILING, now, ObserverCore.Step.SUGGESTS, listOf(wake.toString())))
        }
        SofyHouse.homeHubFailures(events, now)?.let { n ->
            if (!once(app, "house_ha_told_at", TELL_GAP_MS, now)) return@let
            ObserverTv.logShadow(app, "home_hub_failing", "$n comandos ao Home Assistant falharam em 24 h")
            ObserverTv.enqueueNote(app, ObserverCore.sofyNote(ObserverCore.Kind.HOME_HUB_FAILING, now, ObserverCore.Step.SUGGESTS, listOf(n.toString())))
        }
    }

    /**
     * 27: semana com quedas (ou sinal fraco agora) = as dicas do Wi-Fi (as mesmas da tela do UniFi; sem UniFi, só o que
     * o Android sabe) vão ao Diário e a Ilha avisa. Só leitura: ela nunca muda o roteador.
     */
    private fun wifi(app: Context, events: List<SofyBus.Event>, now: Long) {
        val dbm = ObserverTv.blackBox.snapshot()[ObserverCore.Signal.WIFI_DBM]?.value
        val weakNow = dbm != null && dbm in -126L..-76L
        val drops = SofyHouse.wifiNeedsHelp(events, if (weakNow) SofyHouse.WIFI_WEAK else 0, now) ?: return
        if (now - prefs(app).getLong("house_wifi_told_at", 0L) < SofyHouse.WIFI_GAP_MS) return
        val dicas = if (UnifiArmazem.endereco(app) != null) runCatching { UnifiCliente.wifiDaTv(app).dicas }.getOrDefault(emptyList())
            else emptyList()
        val android = wifiAndroid(app)
        val todas = (dicas.ifEmpty { android }).take(4)
        if (todas.isEmpty()) return   // nada concreto a sugerir: não fala por falar
        prefs(app).edit().putLong("house_wifi_told_at", now).apply()
        ObserverTv.logShadow(app, "wifi_advice", "$drops quedas na semana; ${todas.size} dicas")
        runCatching { Diario.anotar(app, "tv", "Sofy: o Wi-Fi pode melhorar", todas.joinToString("\n")) }
        SofyBusTv.emit(app, SofyBus.Source.NETWORK, "wifi_advice", ok = false, detail = "$drops quedas")
        ObserverTv.enqueueNote(app, ObserverCore.sofyNote(ObserverCore.Kind.WIFI_ADVICE, now, ObserverCore.Step.SUGGESTS, listOf(drops.toString())))
    }

    /** Sem UniFi: as dicas que o próprio Android permite (sinal, banda, velocidade do link). */
    @Suppress("DEPRECATION")
    private fun wifiAndroid(app: Context): List<String> = runCatching {
        val info = (app.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).connectionInfo ?: return@runCatching emptyList()
        if (info.rssi !in -126..-1) emptyList() else br.paulo.tvlauncher.network.Unifi.dicas(info.rssi, info.frequency, info.linkSpeed, null)
    }.getOrDefault(emptyList())

    /**
     * 33 (Extras: a presença pelo roteador/HA é do Extras): alguém chegou em casa com a TV acesa. A Sofy anota no barramento e
     * deixa pronto o app de costume desta hora (solta a memória dos parados). Não abre nada nem fala: só prepara.
     */
    fun arrived(ctx: Context) {
        val app = ctx.applicationContext
        val usual = runCatching { SofyHabitsTv.usualNow(app, System.currentTimeMillis()) }.getOrNull()
        val n = usual?.let { runCatching { AppProfileTv.prepareFor(app, it) }.getOrDefault(0) } ?: 0
        SofyBusTv.emit(app, SofyBus.Source.HOUSE, "arrived", detail = if (usual != null) "preparou o de costume ($n apps soltos)" else "")
        if (usual != null) ObserverTv.logShadow(app, "arrived", "alguém chegou: deixei $usual pronto ($n apps soltos)")
    }
}
