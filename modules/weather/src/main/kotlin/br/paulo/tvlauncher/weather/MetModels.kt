package br.paulo.tvlauncher.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A resposta do MET Norway (locationforecast/2.0/compact) em modelos tipados: o primeiro passo do JSON tipado da
 * refatoração G (27/11/2026). Só os campos que o [ClimaMet] lê; o resto é ignorado. Campo ausente ou null = null, e
 * quem lê decide o padrão (o mesmo que o optDouble/optString de antes dava, conferido pelo ClimaMetTest contra a saída
 * gravada do conversor antigo).
 */
@Serializable class MetResponse(val properties: MetProperties)

@Serializable class MetProperties(val timeseries: List<MetStep> = emptyList())

@Serializable class MetStep(val time: String, val data: MetData)

@Serializable class MetData(
    val instant: MetInstant,
    @SerialName("next_1_hours") val next1Hours: MetPeriod? = null,
    @SerialName("next_6_hours") val next6Hours: MetPeriod? = null,
)

@Serializable class MetInstant(val details: MetDetails)

@Serializable class MetPeriod(val summary: MetSummary? = null, val details: MetDetails? = null)

@Serializable class MetSummary(@SerialName("symbol_code") val symbolCode: String? = null)

/** Os números do MET (instante ou período). Todos opcionais: cada um aparece só em parte das horas. */
@Serializable class MetDetails(
    @SerialName("air_temperature") val airTemperature: Double? = null,
    @SerialName("apparent_air_temperature") val apparentAirTemperature: Double? = null,
    @SerialName("relative_humidity") val relativeHumidity: Double? = null,
    @SerialName("air_pressure_at_sea_level") val airPressureAtSeaLevel: Double? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("wind_from_direction") val windFromDirection: Double? = null,
    @SerialName("fog_area_fraction") val fogAreaFraction: Double? = null,
    @SerialName("dew_point_temperature") val dewPointTemperature: Double? = null,
    @SerialName("ultraviolet_index_clear_sky") val ultravioletIndexClearSky: Double? = null,
    @SerialName("precipitation_amount") val precipitationAmount: Double? = null,
    @SerialName("air_temperature_max") val airTemperatureMax: Double? = null,
    @SerialName("air_temperature_min") val airTemperatureMin: Double? = null,
)

object MetJson {
    /** Campos novos do MET não quebram a leitura; um número entre aspas ainda vira número (como o optDouble aceitava). */
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Texto da resposta -> modelo. Formato estranho lança exceção (quem chama já trata, como com o JSONObject). */
    fun parse(text: String): MetResponse = json.decodeFromString(MetResponse.serializer(), text)
}
