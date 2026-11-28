package br.paulo.tvlauncher.observer

/**
 * A Sofy se acusa (24/11/2026, ideia 38). Por quê: o tempo de abrir os apps ficou vazio nas fichas por dias e ninguém
 * viu; uma peça dela parada parece, por fora, uma casa sem problemas. Para quê: quando uma parte dela não anda, ela
 * mesma registra o defeito (registro "observer", Diário, barramento e MCP), com o porquê em números.
 * Como: [check] recebe o "pulso" de cada peça (quando rodou pela última vez) e devolve os defeitos. Pura: SofySelfCheckTest.
 */
object SofySelfCheck {
    const val HOUR = 3_600_000L

    enum class Defect { NO_READINGS, APP_PROFILE_STALE, LAUNCH_TIMES_MISSING, NIGHT_MISSING, EVENTS_SILENT }

    data class Finding(val defect: Defect, val why: String)

    data class Pulse(
        val now: Long,
        /** Desde quando o processo principal (a Sofy) está de pé. */
        val upSince: Long,
        /** Tela acesa agora. */
        val interactive: Boolean,
        /** A leitura de sinal mais nova da caixa-preta (0 = nenhuma). */
        val lastReadingAt: Long,
        /** A última leitura dos eventos de uso da ficha dos apps (0 = nunca). */
        val appsEventsAt: Long,
        /** Quantas vezes ela tentou ler o tempo de abrir, e quando o último chegou (0 = nunca). */
        val startsTries: Int,
        val startsAt: Long,
        /** O que a última tentativa achou (linhas lidas / aproveitadas), para o porquê. */
        val startsLastResult: String,
        /** A última noite com sonda ou com "noite pulada" registrada (0 = nunca). */
        val lastNightAt: Long,
        /** O evento mais novo do barramento (0 = nenhum). */
        val lastEventAt: Long,
    )

    /** Pelo menos isto de pé antes de cobrar cada peça (um processo que acabou de abrir ainda não teve tempo). */
    const val READINGS_LATE_MS = 30 * 60_000L
    const val APP_PROFILE_LATE_MS = 3 * HOUR
    const val STARTS_MIN_TRIES = 6
    const val NIGHT_LATE_MS = 30 * HOUR
    const val EVENTS_LATE_MS = 48 * HOUR

    fun check(p: Pulse): List<Finding> {
        val up = p.now - p.upSince
        val out = ArrayList<Finding>()
        if (p.interactive && up >= READINGS_LATE_MS && p.now - p.lastReadingAt >= READINGS_LATE_MS)
            out += Finding(Defect.NO_READINGS, if (p.lastReadingAt == 0L) "nenhuma leitura de sinal em ${up / 60_000} min de pé"
                else "última leitura de sinal há ${(p.now - p.lastReadingAt) / 60_000} min (o normal é a cada 5 min)")
        if (up >= APP_PROFILE_LATE_MS && p.now - p.appsEventsAt >= APP_PROFILE_LATE_MS)
            out += Finding(Defect.APP_PROFILE_STALE, if (p.appsEventsAt == 0L) "a ficha dos apps nunca leu os eventos de uso"
                else "a ficha dos apps não lê os eventos de uso há ${(p.now - p.appsEventsAt) / HOUR} h (o normal é a cada 15 min)")
        if (p.startsTries >= STARTS_MIN_TRIES && p.startsAt == 0L)
            out += Finding(Defect.LAUNCH_TIMES_MISSING, "tentou ler o tempo de abrir ${p.startsTries} vezes e nenhum chegou" +
                if (p.startsLastResult.isNotEmpty()) " (última: ${p.startsLastResult})" else "")
        if (up >= NIGHT_LATE_MS && p.now - p.lastNightAt >= NIGHT_LATE_MS)
            out += Finding(Defect.NIGHT_MISSING, if (p.lastNightAt == 0L) "nenhuma noite registrada (nem sonda, nem noite pulada)"
                else "a última noite registrada foi há ${(p.now - p.lastNightAt) / HOUR} h")
        if (up >= EVENTS_LATE_MS && p.now - p.lastEventAt >= EVENTS_LATE_MS)
            out += Finding(Defect.EVENTS_SILENT, if (p.lastEventAt == 0L) "nenhum evento no barramento desde que ela abriu"
                else "nenhum evento no barramento há ${(p.now - p.lastEventAt) / HOUR} h")
        return out
    }
}
