package br.paulo.tvlauncher.observer

/**
 * Pilha do controle (24/11/2026, ideia 25). Por quê: o controle morre no meio do filme, sem aviso, e quem tem pilha em
 * casa só descobre na hora. Para quê: com uma semana de folga, a Sofy avisa uma vez na Ilha que a pilha está no fim.
 * Como: o Android 12+ informa a bateria de controles e joysticks Bluetooth (InputDevice.getBatteryState); [toWarn]
 * escolhe quem avisar: no máximo [LOW] de carga, e o mesmo aparelho só de novo depois de [AGAIN_MS] ou se a carga
 * subiu (pilha trocada) e caiu de novo. Pura: ControllerBatteryTest.
 */
object ControllerBattery {
    const val LOW = 0.15f
    const val AGAIN_MS = 7 * 86_400_000L
    /** Subiu pelo menos isto desde o aviso: trocaram a pilha (ou carregaram), então um aviso novo vale. */
    const val REFILL = 0.30f

    /** [id] = descritor do aparelho (estável entre religadas, sem nome de pessoa). [level] 0..1. */
    data class Reading(val id: String, val level: Float)
    data class Warned(val at: Long, val level: Float)

    fun toWarn(now: Long, readings: List<Reading>, warned: Map<String, Warned>): List<Reading> =
        readings.filter { r ->
            if (r.level.isNaN() || r.level > LOW || r.level < 0f) return@filter false
            val w = warned[r.id] ?: return@filter true
            now - w.at >= AGAIN_MS
        }

    /** Depois de uma leitura: quem foi recarregado sai da lista de avisados (pode ser avisado de novo quando cair). */
    fun refilled(readings: List<Reading>, warned: Map<String, Warned>): Map<String, Warned> =
        warned.filterNot { (id, w) -> readings.any { it.id == id && !it.level.isNaN() && it.level - w.level >= REFILL } }

    fun percent(level: Float): Int = Math.round(level * 100).coerceIn(0, 100)
}
