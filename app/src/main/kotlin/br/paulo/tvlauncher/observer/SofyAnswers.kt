package br.paulo.tvlauncher.observer

/**
 * A Sofy respondendo (24/11/2026, ideias 44, 49 e 50 do estudo "extrapolar a Sofy").
 *
 * Por quê: a pessoa diz o problema do jeito dela ("TV sem som", "internet lenta") e quer a resposta, não um menu; quer
 * saber o que a Sofy fez sozinha hoje; e, diante de um recado, quer saber de onde ela tirou aquilo. Para quê: confiança.
 * Ela só diz o que mediu, viu no registro ou aprendeu, e diz qual dos três foi. Como: regras pequenas e puras; as frases
 * moram nos recursos (strings_sofy_hands.xml). Pura: SofyAnswersTest.
 */
object SofyAnswers {
    // ---------------------------------------------------------------- 44: o problema em palavras
    enum class Sound { FIXED_TV, MUTED, ZERO, LOW, OK }

    /** O som do aparelho. [fixed] = o volume é o da TV (HDMI-CEC): aí o Chromecast não manda. */
    fun sound(muted: Boolean, volume: Int, max: Int, fixed: Boolean): Sound = when {
        fixed -> Sound.FIXED_TV
        muted -> Sound.MUTED
        volume <= 0 -> Sound.ZERO
        max > 0 && volume * 100 / max < LOW_SOUND_PCT -> Sound.LOW
        else -> Sound.OK
    }
    const val LOW_SOUND_PCT = 15
    /** Ao corrigir mudo/zero, o volume volta para 20% (baixo de propósito: pode ser madrugada). */
    const val FIX_SOUND_PCT = 20

    enum class Slow { LOW_MEMORY, BUSY, OK }

    /** [availMb]/[totalMb] = a memória agora; [normalMb] = a memória livre normal desta casa nesta hora (linha de base). */
    fun slow(availMb: Long, totalMb: Long, normalMb: Long?): Slow = when {
        totalMb > 0 && availMb * 100 / totalMb < 12 -> Slow.LOW_MEMORY
        normalMb != null && normalMb > 0 && availMb * 10 < normalMb * 6 -> Slow.BUSY
        else -> Slow.OK
    }

    enum class Remote { KEY_STUCK, BATTERY_LOW, LAG, NONE }

    /** O controle, pelo que o barramento viu nas últimas 24 h (a pior coisa primeiro). */
    fun remote(events: List<SofyBus.Event>, now: Long): Remote {
        val day = events.filter { it.source == SofyBus.Source.DEVICE && now - it.at in 0..DAY_MS }
        return when {
            day.any { it.kind == "key_stuck" } -> Remote.KEY_STUCK
            day.any { it.kind == "battery_low" } -> Remote.BATTERY_LOW
            day.any { it.kind == "controller_lag" } -> Remote.LAG
            else -> Remote.NONE
        }
    }

    // ---------------------------------------------------------------- 49: o que você fez hoje
    const val DAY_MS = 24 * 3_600_000L

    /** Os grupos do resumo do dia, na ordem em que aparecem. Cada um vira "sofy_today_<code>" (com a contagem). */
    enum class Today(val code: String, val match: (SofyBus.Event) -> Boolean) {
        PREPARED("prepared", { (it.source == SofyBus.Source.SOFY && it.kind in setOf("prewarm", "prehome", "acted_on_demand", "acted_recompile", "recipe_memory"))
            || (it.source == SofyBus.Source.HOUSE && it.kind == "arrived") }),
        NIGHT("night", { (it.source == SofyBus.Source.SOFY && it.kind in setOf("night_maintenance", "experiment_night")) || (it.source == SofyBus.Source.BACKUP && it.ok) }),
        GUARDED("guarded", { (it.source == SofyBus.Source.SOFY && it.kind in setOf("app_powerful", "lost_help", "app_crashy", "version_worse", "disk_filling", "self_defect"))
            || (it.source == SofyBus.Source.UPDATE && it.kind == "google_broke") || (it.source == SofyBus.Source.HOUSE && it.kind == "panel_pin_wrong")
            || (it.source == SofyBus.Source.DEVICE && it.kind in setOf("key_stuck", "battery_low")) }),
        INTERNET("internet", { it.source == SofyBus.Source.NETWORK && it.kind.startsWith("drop_") }),
        ASKED("asked", { it.source == SofyBus.Source.SOFY && it.kind == "asked" }),
        APPLIED("applied", { it.source == SofyBus.Source.SOFY && it.kind == "suggestion_applied" }),
        ALARMS("alarms", { (it.source == SofyBus.Source.ALARM && it.kind in setOf("rang", "timer_rang")) || (it.source == SofyBus.Source.PLAYER && it.kind == "reminder") }),
        AUTO_OFF("auto_off", { it.source == SofyBus.Source.HOUSE && it.kind == "auto_off" }),
        HOME("home", { it.source == SofyBus.Source.HOUSE && it.kind == "ha_call" }),
        UPDATES("updates", { it.source == SofyBus.Source.SOFY && it.kind == "app_updated" }),
    }

    /** O dia de hoje (desde [dayStart], a meia-noite local): grupo → quantas vezes. Grupos sem nada ficam de fora. */
    fun today(events: List<SofyBus.Event>, dayStart: Long, now: Long): List<Pair<Today, Int>> {
        val day = events.filter { it.at in dayStart..now }
        return Today.entries.mapNotNull { t -> day.count(t.match).takeIf { it > 0 }?.let { t to it } }
    }

    // ---------------------------------------------------------------- 50: como eu sei
    /** De onde vem o que o recado diz. Cada um vira "sofy_evidence_<code>". */
    enum class Evidence(val code: String) { MEASURED("measured"), BASELINE("baseline"), BUS("bus"), SYSTEM("system"), ROUTER("router"), TEST("test"), OTHER_TV("other_tv"), COMMUNITY("community") }

    fun evidence(kind: ObserverCore.Kind): Evidence = when (kind) {
        ObserverCore.Kind.NO_INTERNET, ObserverCore.Kind.SLOW_INTERNET, ObserverCore.Kind.WEAK_WIFI, ObserverCore.Kind.LOW_MEMORY,
        ObserverCore.Kind.MEMORY_PRESSURE, ObserverCore.Kind.SLOW_HOME, ObserverCore.Kind.LOW_DISK, ObserverCore.Kind.GAME_LAG -> Evidence.MEASURED
        ObserverCore.Kind.UNUSUAL_LATENCY, ObserverCore.Kind.UNUSUAL_MEMORY, ObserverCore.Kind.UNUSUAL_WIFI, ObserverCore.Kind.UNUSUAL_FRAMES,
        ObserverCore.Kind.VERSION_SLOWER, ObserverCore.Kind.DISK_FILLING -> Evidence.BASELINE
        ObserverCore.Kind.FREEZE_UNDONE -> Evidence.TEST
        ObserverCore.Kind.OTHER_TV_CRASHY, ObserverCore.Kind.OTHER_TV_SLOWER -> Evidence.OTHER_TV
        ObserverCore.Kind.HOMES_CRASHY, ObserverCore.Kind.HOMES_SLOWER -> Evidence.COMMUNITY
        ObserverCore.Kind.WIFI_ADVICE -> Evidence.ROUTER
        ObserverCore.Kind.APP_POWERFUL, ObserverCore.Kind.SYSTEM_UPDATE_BROKE, ObserverCore.Kind.APP_ON_DEMAND -> Evidence.SYSTEM
        ObserverCore.Kind.WIFI_RESTARTED, ObserverCore.Kind.TV_RESTARTED, ObserverCore.Kind.REMOTE_BATTERY_LOW, ObserverCore.Kind.APP_CRASHY,
        ObserverCore.Kind.APP_CRASHY_UPDATE, ObserverCore.Kind.TV_WAKE_FAILING, ObserverCore.Kind.HOME_HUB_FAILING, ObserverCore.Kind.KEY_STUCK,
        ObserverCore.Kind.PANEL_PIN_WRONG -> Evidence.BUS
    }
}
