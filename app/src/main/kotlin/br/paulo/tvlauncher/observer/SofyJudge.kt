package br.paulo.tvlauncher.observer

/**
 * Juiz de conflitos (24/11/2026, ideia 4). Por quê: cada parte do Sofaly decidia sozinha a própria hora. A atualização
 * instala às 6h55 e reinicia o launcher com o despertador marcado para as 7h; o backup cifrado roda enquanto o alarme
 * toca; a atualização baixa o APK no meio de um jogo na nuvem. Para quê: quando duas coisas querem a mesma hora ou o mesmo
 * recurso, a Sofy decide quem espera e anota por quê (registro, Diário e barramento).
 * Como: [decide] recebe a tarefa e a situação de agora e diz "pode" ou "espere N min". Pura: SofyJudgeTest.
 */
object SofyJudge {
    enum class Task { UPDATE, BACKUP }

    data class Situation(
        val now: Long,
        /** O próximo despertador (0 = nenhum). */
        val nextAlarmAt: Long = 0L,
        /** O despertador está tocando agora (barramento: tocou e ainda não parou). */
        val alarmRinging: Boolean = false,
        /** Som tocando (vídeo ou música). */
        val mediaActive: Boolean = false,
        /** Um app de jogo na nuvem está na frente. */
        val cloudGameFront: Boolean = false,
    )

    data class Verdict(val go: Boolean, val waitMs: Long = 0L, val why: String = "")

    const val MIN = 60_000L
    /** Antes do despertador: a atualização reinicia o launcher (quem toca o alarme) e o backup ocupa disco e CPU. */
    const val ALARM_GUARD_UPDATE_MS = 45 * MIN
    const val ALARM_GUARD_BACKUP_MS = 20 * MIN
    /** Depois que o alarme tocou, deixa a manhã começar. */
    const val AFTER_ALARM_MS = 15 * MIN
    const val RETRY_MS = 30 * MIN

    /** Jogo na nuvem: o atraso de rede vira atraso no controle. Pacotes públicos das lojas. */
    val CLOUD_GAMES = setOf("com.nvidia.geforcenow", "com.gamepass", "com.microsoft.xcloud", "com.amazon.luna", "com.boosteroid.streaming")

    fun decide(t: Task, s: Situation): Verdict {
        if (s.alarmRinging) return Verdict(false, AFTER_ALARM_MS, "o despertador está tocando")
        val guard = when (t) { Task.UPDATE -> ALARM_GUARD_UPDATE_MS; Task.BACKUP -> ALARM_GUARD_BACKUP_MS }
        if (s.nextAlarmAt > 0) {
            val falta = s.nextAlarmAt - s.now
            if (falta in 0 until guard) return Verdict(false, falta + AFTER_ALARM_MS, "o despertador toca em ${falta / MIN} min")
        }
        if (s.cloudGameFront && t == Task.UPDATE) return Verdict(false, RETRY_MS, "jogo na nuvem na frente: a rede é dele")
        if (s.mediaActive) return Verdict(false, RETRY_MS, "vídeo ou música tocando")
        return Verdict(true)
    }

    /** O alarme está tocando? O último evento do despertador (até 30 min) foi "tocou" e nada de "parou/soneca" depois. */
    fun alarmRinging(events: List<SofyBus.Event>, now: Long): Boolean {
        val last = events.filter { it.source == SofyBus.Source.ALARM && now - it.at in 0..RETRY_MS }.maxByOrNull { it.at } ?: return false
        return last.kind == "rang" || last.kind == "timer_rang"
    }
}
