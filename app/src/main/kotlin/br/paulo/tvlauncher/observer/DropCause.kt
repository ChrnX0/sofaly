package br.paulo.tvlauncher.observer

/**
 * A causa de cada queda de internet (24/11/2026, ideia 26). Por quê: a queda de 21/11 às 4h04 ficou sem causa; toda
 * queda deixa a mesma pergunta ("foi a TV, o roteador ou a operadora?") e a resposta mudava o que fazer.
 * Para quê: ao fim de cada queda de pelo menos [MIN_DROP_MS], a Sofy anota a causa provável, com o porquê em números.
 * Como: [Episode] acompanha a queda amostra a amostra (a TV estava conectada ao Wi-Fi ou não?) e [classify] decide,
 * com o tempo ligado dos aparelhos UniFi quando a casa tem o roteador configurado. Pura: DropCauseTest.
 */
object DropCause {
    const val MIN_DROP_MS = 2 * 60_000L
    /** Folga para dizer "o roteador reiniciou durante a queda": tempo ligado menor que a queda + isto. */
    const val ROUTER_SLACK_MS = 5 * 60_000L

    enum class Cause {
        /** Algum aparelho UniFi (gateway, ponto de acesso) ligou de novo durante a queda. */
        ROUTER_RESTARTED,
        /** A TV perdeu o Wi-Fi (não estava conectada) e o roteador não reiniciou. */
        TV_LOST_WIFI,
        /** A TV ficou conectada ao Wi-Fi o tempo todo e mesmo assim sem internet: roteador sem saída ou operadora. */
        INTERNET_OUTSIDE,
        /** Parte do tempo conectada, parte não: não dá para separar. */
        MIXED,
    }

    data class Episode(val start: Long, val samples: Int = 0, val onWifi: Int = 0, val offWifi: Int = 0) {
        fun add(dbm: Int): Episode = if (dbm != 0) copy(samples = samples + 1, onWifi = onWifi + 1) else copy(samples = samples + 1, offWifi = offWifi + 1)
    }

    data class Result(val cause: Cause, val minutes: Long, val why: String)

    /**
     * Uma amostra de rede chegou. Devolve o episódio aberto (ou null) e, quando a queda acabou agora, o episódio fechado.
     * [dbm] 0 = a TV não estava conectada a uma rede Wi-Fi (no cabo o histórico também dá 0, mas aí não há "perdeu o Wi-Fi").
     */
    fun step(open: Episode?, internet: Boolean, dbm: Int, now: Long): Pair<Episode?, Episode?> = when {
        !internet -> (open ?: Episode(now)).add(dbm) to null
        open != null -> null to open
        else -> null to null
    }

    /**
     * [routerUptimesMs] = tempo ligado de cada aparelho UniFi lido em [askedAt] (vazio = casa sem UniFi configurado).
     */
    fun classify(e: Episode, end: Long, routerUptimesMs: List<Long>, askedAt: Long = end): Result? {
        val dur = end - e.start
        if (dur < MIN_DROP_MS || e.samples == 0) return null
        val min = dur / 60_000
        val newest = routerUptimesMs.minOrNull()
        if (newest != null && newest <= (askedAt - e.start) + ROUTER_SLACK_MS)
            return Result(Cause.ROUTER_RESTARTED, min, "um aparelho do roteador está ligado há só ${newest / 60_000} min: reiniciou durante a queda de $min min")
        val roteador = if (routerUptimesMs.isEmpty()) "" else "; o roteador não reiniciou"
        return when {
            e.onWifi == 0 -> Result(Cause.TV_LOST_WIFI, min, "a TV ficou fora do Wi-Fi nas ${e.samples} amostras da queda de $min min$roteador")
            e.offWifi == 0 -> Result(Cause.INTERNET_OUTSIDE, min, "a TV ficou no Wi-Fi nas ${e.samples} amostras e mesmo assim sem internet por $min min$roteador")
            else -> Result(Cause.MIXED, min, "${e.onWifi} de ${e.samples} amostras com Wi-Fi na queda de $min min$roteador")
        }
    }
}
