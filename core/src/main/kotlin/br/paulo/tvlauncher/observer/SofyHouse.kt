package br.paulo.tvlauncher.observer

/**
 * A Sofy de olho na casa em volta da TV (24/11/2026, ideias 27 a 32 do estudo "extrapolar a Sofy").
 *
 * Por quê: Wi-Fi, LG, Home Assistant, controle e conta de luz já tinham peças no Sofaly, mas cada uma só falava quando
 * alguém abria a tela dela. Para quê: a Sofy junta os sinais no barramento e fala UMA vez quando um padrão aparece:
 *  - 27 Wi-Fi melhor: a internet caiu 3 vezes na semana ou o sinal ficou fraco 3 vezes = ela consulta as dicas do Wi-Fi
 *    (UniFi, quando configurado) e conta o que mudar no roteador. Nunca muda o roteador sozinha.
 *  - 28 jogo na nuvem: com um jogo na nuvem na frente e a internet bem mais lenta que o normal DESTA casa = avisa na hora.
 *  - 29 LG de olho: o despertador pediu para a LG ligar e ela não respondeu em 2 manhãs da semana = avisa e diz o porquê.
 *  - 30 energia: as horas de tela viram kWh e dinheiro na Retrospectiva, quando a casa disse os watts e a tarifa.
 *  - 31 casa que falha: comandos ao Home Assistant falhando 3 vezes em 24 h (metade ou mais) = avisa.
 *  - 32 controle com atraso: o teste do controle (Jogos) deu lento = vira registro e conta no relato da casa.
 * Pura: SofyHouseTest.
 */
object SofyHouse {
    const val DAY_MS = 86_400_000L
    const val WIFI_DROPS = 3
    const val WIFI_WEAK = 3
    const val WIFI_GAP_MS = 14 * DAY_MS
    const val GAME_MIN_MS = 80L
    const val GAME_FACTOR = 2.0
    const val GAME_PLUS_MS = 60L
    const val WAKE_FAILS = 2
    const val HA_FAILS = 3

    /** 27: a semana pede ajuda no Wi-Fi? Quedas da internet (barramento) ou achados de sinal fraco. */
    fun wifiNeedsHelp(events: List<SofyBus.Event>, weakFindings: Int, now: Long): Int? {
        val drops = events.count { it.source == SofyBus.Source.NETWORK && it.kind.startsWith("drop_") && now - it.at in 0..7 * DAY_MS }
        return drops.takeIf { it >= WIFI_DROPS || weakFindings >= WIFI_WEAK }
    }

    /** 28: a latência de agora atrapalha um jogo na nuvem? Bem acima do normal da casa e acima de [GAME_MIN_MS]. */
    fun gameLag(latencyMs: Long?, normalMs: Long?): Boolean {
        if (latencyMs == null || normalMs == null || normalMs <= 0) return false
        return latencyMs >= GAME_MIN_MS && latencyMs >= maxOf((normalMs * GAME_FACTOR).toLong(), normalMs + GAME_PLUS_MS)
    }

    /** 29: manhãs (dias diferentes) da semana em que a LG não respondeu ao despertador ("device.tv_wake" com falha). */
    fun wakeFailures(events: List<SofyBus.Event>, now: Long): Int =
        events.filter { it.source == SofyBus.Source.DEVICE && it.kind == "tv_wake" && !it.ok && now - it.at in 0..7 * DAY_MS }
            .map { it.at / DAY_MS }.distinct().size

    /** 31: falhas de comando ao Home Assistant nas últimas 24 h, só quando são metade ou mais dos pedidos. */
    fun homeHubFailures(events: List<SofyBus.Event>, now: Long): Int? {
        val l = events.filter { it.source == SofyBus.Source.HOUSE && it.kind == "ha_call" && now - it.at in 0..DAY_MS }
        val f = l.count { !it.ok }
        return f.takeIf { it >= HA_FAILS && f * 2 >= l.size }
    }

    /**
     * 30: energia da semana a partir dos minutos de tela. [watts] = a TV e o Chromecast juntos (a casa informa; sem
     * isso, null: não inventamos o consumo); [centsPerKwh] = a tarifa em centavos (0 = só os kWh).
     * Devolve kWh (uma casa decimal) e o valor em centavos (null sem tarifa).
     */
    fun energy(minutes: Long, watts: Int, centsPerKwh: Int): Pair<Double, Long?>? {
        if (watts <= 0 || minutes <= 0) return null
        val kwh = minutes / 60.0 * watts / 1000.0
        return Math.round(kwh * 10) / 10.0 to (if (centsPerKwh > 0) Math.round(kwh * centsPerKwh) else null)
    }
}
