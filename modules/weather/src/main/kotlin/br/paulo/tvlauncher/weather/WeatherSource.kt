package br.paulo.tvlauncher.weather

import android.content.Context
import br.paulo.tvlauncher.alarm.FaixaDias

/**
 * Qual fonte de previsão o aparelho usa e o pedido a ela (saiu do DataSources do app na refatoração G, etapa 5). A
 * ESCOLHA depende do plano (Planos, no app) e chega por [WeatherHost.kind]; a regra continua em DataSources.weather.
 * Aqui ficam as travas de licença do clima, a ordem de reserva (WeatherAPI -> modelos abertos -> MET Norway) e a
 * mediana dos modelos, que a HOME e o aviso de chuva usam iguais.
 */
object WeatherSource {
    /** Open-Meteo pago (mediana de ECMWF/ICON/GFS + qualidade do ar). O grátis deles é só não comercial. Sem contrato. */
    const val OPEN_METEO_LICENSED = false
    /**
     * WeatherAPI: a chave fica no Worker (secret WEATHERAPI_KEY). Pode ficar ligado sem a chave: o servidor responde 503
     * e a TV volta ao MET Norway e só pergunta de novo horas depois ([WeatherApiSource]).
     */
    const val WEATHERAPI_ENABLED = true
    /**
     * Consenso de 4 modelos abertos (Básico). Pode ficar ligado antes do servidor ter a fonte: ele responde 503 e a TV
     * fica no MET Norway, perguntando de novo horas depois ([OpenModelsSource]).
     */
    const val OPEN_MODELS_ENABLED = true

    enum class Kind { MET_NORWAY, OPEN_MODELS, WEATHERAPI, OPEN_METEO }

    /** A fonte deste aparelho agora (plano + licença + fontes em pausa). */
    fun kind(ctx: Context): Kind = WeatherHost.kind(ctx)

    /** Sem o app para dizer o plano (teste, outro processo): o que o Básico usaria. */
    fun semPlano(): Kind = if (OPEN_MODELS_ENABLED && !OpenModelsSource.emPausa()) Kind.OPEN_MODELS else Kind.MET_NORWAY

    /**
     * Previsão no formato da Open-Meteo para a fonte do aparelho, ou null quando a fonte é a Open-Meteo paga (quem chama
     * monta o pedido dela, como sempre fez) ou quando nada respondeu. A WeatherAPI que falhar cai nos modelos abertos e
     * depois no MET, na mesma hora; os modelos abertos que falharem caem no MET.
     */
    fun forecast(ctx: Context, lat: Double, lon: Double, desdeAgora: Boolean, dias: Int = FaixaDias.DIAS): org.json.JSONObject? =
        forecast(kind(ctx), lat, lon, desdeAgora, dias)

    fun forecast(kind: Kind, lat: Double, lon: Double, desdeAgora: Boolean, dias: Int = FaixaDias.DIAS): org.json.JSONObject? =
        when (kind) {
            Kind.OPEN_METEO -> null
            Kind.WEATHERAPI -> WeatherApiSource.previsao(lat, lon, desdeAgora, dias)
                ?.let { wa -> if (WeatherApiSource.dias(wa) >= dias) wa else WeatherApiSource.completar(wa, ClimaMet.previsao(lat, lon, desdeAgora, dias)) }
                ?: abertosOuMet(lat, lon, desdeAgora, dias)
            Kind.OPEN_MODELS -> abertosOuMet(lat, lon, desdeAgora, dias)
            Kind.MET_NORWAY -> ClimaMet.previsao(lat, lon, desdeAgora, dias)
        }

    private fun abertosOuMet(lat: Double, lon: Double, desdeAgora: Boolean, dias: Int): org.json.JSONObject? =
        (if (OPEN_MODELS_ENABLED) OpenModelsSource.previsao(lat, lon, desdeAgora, dias) else null)
            ?: ClimaMet.previsao(lat, lon, desdeAgora, dias)

    /** Mediana hora a hora de vários modelos; hora sem nenhum valor = 0. Média dos dois do meio quando o número é par. */
    fun mediana(porModelo: List<List<Int?>>): List<Int> {
        val n = porModelo.maxOfOrNull { it.size } ?: 0
        return (0 until n).map { i ->
            val v = porModelo.mapNotNull { it.getOrNull(i) }.sorted()
            when {
                v.isEmpty() -> 0
                v.size % 2 == 1 -> v[v.size / 2]
                else -> (v[v.size / 2 - 1] + v[v.size / 2]) / 2
            }
        }
    }
}
