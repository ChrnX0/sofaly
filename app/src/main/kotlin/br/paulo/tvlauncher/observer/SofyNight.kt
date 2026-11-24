package br.paulo.tvlauncher.observer

/**
 * Uma noite só (24/11/2026, ideia 2). Por quê: a madrugada tinha 3 relógios que não conversavam: a manutenção na hora
 * aprendida pela Sofy, o backup cifrado às 3h30 fixas e o atualizador a cada 6 h com a tela apagada. Numa casa cuja
 * hora quieta é 2h, o backup rodava fora da janela e uma atualização podia reiniciar o launcher no meio do backup.
 * Para quê: tudo na mesma janela, numa ordem que não briga: manutenção (hora quieta), backup ([BACKUP_AFTER_MIN] depois)
 * e só então atualização. Quem escolheu o horário do backup à mão continua mandando.
 * Como: regras puras (SofyNightTest); BackupNoturno.minutoDoDia e Atualizador.talvezVerificar perguntam aqui.
 */
object SofyNight {
    /** O backup começa este tanto depois da hora quieta (a manutenção roda primeiro, na virada da hora). */
    const val BACKUP_AFTER_MIN = 45
    /** Da hora quieta até este tanto depois, a atualização espera (manutenção + backup ainda podem estar rodando). */
    const val UPDATE_WAIT_MIN = 120

    enum class Task { MAINTENANCE, BACKUP, UPDATE }
    data class Slot(val task: Task, val minuteOfDay: Int, val learned: Boolean)

    /** Minuto do dia do backup: o escolhido pela pessoa, ou o da noite da Sofy. */
    fun backupMinute(quietHour: Int, chosenMinute: Int?): Int = chosenMinute ?: Math.floorMod(quietHour * 60 + BACKUP_AFTER_MIN, 1440)

    /** A atualização automática deve esperar agora? (dentro da janela de manutenção + backup desta noite) */
    fun updateWaits(minuteOfDay: Int, quietHour: Int): Boolean =
        Math.floorMod(minuteOfDay - quietHour * 60, 1440) < UPDATE_WAIT_MIN

    /** O plano da noite, na ordem, para o MCP e o painel. */
    fun plan(quietHour: Int, chosenBackupMinute: Int?): List<Slot> = listOf(
        Slot(Task.MAINTENANCE, Math.floorMod(quietHour * 60, 1440), true),
        Slot(Task.BACKUP, backupMinute(quietHour, chosenBackupMinute), chosenBackupMinute == null),
        Slot(Task.UPDATE, Math.floorMod(quietHour * 60 + UPDATE_WAIT_MIN, 1440), true),
    )
}
