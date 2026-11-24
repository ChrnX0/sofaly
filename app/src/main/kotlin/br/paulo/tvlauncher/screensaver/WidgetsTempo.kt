package br.paulo.tvlauncher.screensaver

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * A previsão hora a hora que a HOME grava nas prefs "clima" (ClimaFontes). Era dos widgets do protetor antigo; hoje
 * quem lê é o céu da noite ([br.paulo.tvlauncher.weather.NightSky], céu limpo na hora do evento). Puro (WidgetsTempoTest).
 */
object WidgetsTempo {
    /** Uma hora da previsão: quando, temperatura, código WMO do céu e chance de chuva (-1 = sem dado). */
    data class Marca(val ms: Long, val temp: Int, val codigo: Int, val chuva: Int)

    /**
     * Lê "hora|temp|código|chuva;…" (ClimaFontes.proximasHoras, hora a hora, no fuso do lugar, que é o da TV).
     * Linha quebrada é pulada, não derruba o resto.
     */
    fun lerHoras(texto: String, fuso: TimeZone = TimeZone.getDefault()): List<Marca> {
        if (texto.isBlank()) return emptyList()
        val leitor = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = fuso }
        return texto.split(';').mapNotNull { linha ->
            val p = linha.split('|')
            if (p.size < 4) return@mapNotNull null
            val ms = try { leitor.parse(p[0].trim())?.time } catch (e: Exception) { null } ?: return@mapNotNull null
            val temp = p[1].trim().toIntOrNull() ?: return@mapNotNull null
            Marca(ms, temp, p[2].trim().toIntOrNull() ?: 0, p[3].trim().toIntOrNull() ?: -1)
        }.sortedBy { it.ms }
    }
}
