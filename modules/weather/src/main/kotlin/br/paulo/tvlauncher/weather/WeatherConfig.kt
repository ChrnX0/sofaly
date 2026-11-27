package br.paulo.tvlauncher.weather

import br.paulo.tvlauncher.BuildConfig

/**
 * Os ajustes do clima que moravam no Config.kt do app (o Config continua com os mesmos nomes, apontando para cá).
 * Os dados pessoais (coordenadas, unidade, estação, eclipses da cidade) vêm do pessoal.properties pelo BuildConfig.
 */
object WeatherConfig {
    // Coordenadas de reserva: a localização de verdade vem da própria TV (Wi-Fi do Android, depois IP; Localizacao.kt).
    val LATITUDE: Double = BuildConfig.LATITUDE
    val LONGITUDE: Double = BuildConfig.LONGITUDE
    val USAR_FAHRENHEIT: Boolean = BuildConfig.USAR_FAHRENHEIT
    // Estação de medição (aeroporto, código ICAO) fixa; vazio = a mais perto, escolhida sozinha.
    val ESTACAO_METAR: String = BuildConfig.ESTACAO_METAR
    // Minutos depois do PÔR do sol real (Astronomia.nascerEPor) em que o ícone ainda considera "dia". A Open-Meteo marca
    // noite no instante exato do pôr, sem o crepúsculo civil. Flagrado em 03/11/2026 às 18:38: pôr às 18:24, ícone já de
    // lua, mas a foto da varanda mostrava céu claramente ainda claro.
    const val MARGEM_CREPUSCULO_MIN = 20

    // Eclipses solares visíveis da cidade (chave eclipses do pessoal.properties; formato em pessoal.properties.exemplo).
    // Cada um: 1º contato, máximo, último contato (UTC, ISO-8601) e a magnitude. Sem a chave, nunca aparece eclipse.
    val ECLIPSES_SOLARES: List<EclipseSolar> = eclipsesDe(BuildConfig.ECLIPSES)

    /** "ini,max,fim,mag; ini,max,fim,mag" -> lista. Um item malformado é ignorado (não derruba o launcher). */
    fun eclipsesDe(texto: String): List<EclipseSolar> = texto.split(';').mapNotNull { item ->
        val c = item.split(',').map { it.trim() }
        if (c.size != 4) return@mapNotNull null
        try { EclipseSolar(c[0], c[1], c[2], c[3].toDouble()) } catch (e: Exception) { null }
    }
}
