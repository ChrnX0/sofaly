package br.paulo.tvlauncher.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import br.paulo.tvlauncher.Config
import br.paulo.tvlauncher.DicasClima
import br.paulo.tvlauncher.Http
import br.paulo.tvlauncher.Prefs
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.Rtl
import br.paulo.tvlauncher.alarm.FaixaDias
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A previsão completa (agora, hora a hora, dias, ar), dividida entre a tela do Clima e a home que adormece
 * (21/11/2026, Paulo: "a tela de descanso nada mais é do que a tela de clima, mas mais simplificada").
 *
 * Porquê: as duas telas mostram os mesmos números; buscar e converter em dois lugares deixaria uma dizendo 27° e a
 * outra 25° para o mesmo instante.
 * Como: [Forecast.load] busca (Open-Meteo, MET ou WeatherAPI, conforme o plano) e guarda o resultado na memória por
 * [CACHE_MS]; o descanso de tela, que abre muitas vezes por dia, reaproveita a busca do Clima em vez de ir à rede.
 */
internal class ForecastData(
    val ponto: Localizacao.Ponto,
    val agoraTexto: String,            // "2026-11-02T16:15" (hora local do lugar)
    val temp: Double, val sensacao: Double, var umidade: Int, var codigo: Int, val pressao: Double,   // var: o topo da HOME manda
    val vento: Double, val ventoDir: Int, val eDia: Boolean,
    val hTempo: List<String>, val hTemp: List<Double>, val hChuva: List<Int>, val hCodigo: List<Int>,
    val hVento: List<Double>, val hVisib: List<Double>, val hOrvalho: List<Double>, val hUv: List<Double>,
    val dData: List<String>, val dCodigo: List<Int>, val dMax: List<Double>, val dMin: List<Double>, val dChuva: List<Int>,
    val dNascer: List<String>, val dPoente: List<String>, val dChuvaMm: Double,
    val aqi: Int?,
    /** "weatherapi.com", "met.no" ou "" (Open-Meteo): a WeatherAPI pede o crédito na tela. */
    val fonte: String = "",
    /** Nuvens de agora em % (só a Open-Meteo manda; -1 = sem dado). */
    val nuvens: Int = -1,
) {
    /** Índice da hora de agora nas listas hora a hora. */
    val i0: Int get() = max(0, hTempo.indexOfFirst { it >= agoraTexto.substring(0, 13) + ":00" })

    fun eDiaNaHora(i: Int): Boolean { val di = min(i / 24, dNascer.size - 1); return hTempo[i] >= dNascer[di] && hTempo[i] < dPoente[di] }

    /** A entrada das regras da frase e das dicas ([DicasClima]), com a temperatura de agora corrigida pelo topo. */
    fun entrada(tempAgora: Int, chovendo: Boolean): DicasClima.Entrada {
        val i0 = i0; val fim = min(hTempo.size, i0 + 24); val hoje = agoraTexto.substring(0, 10)
        val maxHoje = max(Math.round(dMax[0]).toInt(), tempAgora)
        return DicasClima.Entrada(chovendo,
            (i0 until fim).map { i -> DicasClima.Hora(hTempo[i].substring(11, 13).toInt(), if (i == i0) tempAgora.toDouble() else hTemp[i],
                hChuva[i], hUv.getOrElse(i) { 0.0 }, if (i == i0) eDia else eDiaNaHora(i), hTempo[i].startsWith(hoje)) },
            maxHoje, dChuva.getOrNull(1))
    }
}

internal object Forecast {
    /** Teste de telas: uma previsão fixa no formato da Open-Meteo, para desenhar sem rede. */
    @Volatile var dadosDeTeste: JSONObject? = null

    /** A previsão da memória vale por 30 min (a do topo da HOME é renovada a cada 15). */
    const val CACHE_MS = 30 * 60_000L
    @Volatile private var guardada: Pair<Long, ForecastData>? = null

    /** A última previsão buscada, se tiver menos de [CACHE_MS]. */
    fun recente(): ForecastData? = guardada?.takeIf { System.currentTimeMillis() - it.first < CACHE_MS }?.second

    /** Busca na rede (fora da thread da tela). null = sem lugar ou sem rede. */
    fun load(ctx: Context): ForecastData? {
        dadosDeTeste?.let { return parse(it, Localizacao.Ponto(-23.55, -46.63, "teste", "Bairro de exemplo"), 42) }
        val ponto = Localizacao.atual(ctx) { Http.getText(it) } ?: return null
        val unidade = if (Config.USAR_FAHRENHEIT) "&temperature_unit=fahrenheit" else ""
        val openMeteo = DataSources.weather(ctx) == DataSources.Weather.OPEN_METEO
        val j = if (!openMeteo) DataSources.forecast(ctx, ponto.lat, ponto.lon, desdeAgora = false) ?: return null else JSONObject(Http.getText(
            "https://api.open-meteo.com/v1/forecast?latitude=${ponto.lat}&longitude=${ponto.lon}" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,pressure_msl,wind_speed_10m,wind_direction_10m,is_day,cloud_cover" +
                "&hourly=temperature_2m,precipitation_probability,weather_code,wind_speed_10m,visibility,dew_point_2m,uv_index" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,precipitation_sum" +
                "&timezone=auto&forecast_days=${FaixaDias.DIAS}$unidade"
        ) ?: return null)
        // Qualidade do ar: Open-Meteo (Ultra) ou WeatherAPI (Pro, já no JSON convertido); no MET (Básico) fica sem.
        val aqi = if (!openMeteo) j.optJSONObject("air_quality")?.optInt("us_aqi", -1)?.takeIf { it >= 0 } else try {
            JSONObject(Http.getText("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${ponto.lat}&longitude=${ponto.lon}&current=us_aqi")
                ?: "{}").optJSONObject("current")?.optInt("us_aqi", -1)?.takeIf { it >= 0 }
        } catch (e: Exception) { null }
        return parse(j, ponto, aqi).also { guardada = System.currentTimeMillis() to it }
    }

    /** A guardada se for recente; senão busca. */
    fun recenteOuBusca(ctx: Context): ForecastData? = recente() ?: try { load(ctx) } catch (e: Exception) { null }

    /** O JSON no formato da Open-Meteo (o MET e a WeatherAPI já chegam convertidos nele) vira [ForecastData]. */
    fun parse(j: JSONObject, ponto: Localizacao.Ponto, aqi: Int?): ForecastData {
        val c = j.getJSONObject("current"); val h = j.getJSONObject("hourly"); val d = j.getJSONObject("daily")
        fun <T> lista(o: JSONObject, k: String, f: (org.json.JSONArray, Int) -> T) = o.getJSONArray(k).let { a -> (0 until a.length()).map { f(a, it) } }
        return ForecastData(
            ponto, c.getString("time"),
            c.getDouble("temperature_2m"), c.getDouble("apparent_temperature"), c.getInt("relative_humidity_2m"), c.getInt("weather_code"),
            c.getDouble("pressure_msl"), c.getDouble("wind_speed_10m"), c.getInt("wind_direction_10m"),
            // Mesma regra da HOME (Astronomia.eDia, com o crepúsculo): antes era o is_day cru da API e as telas discordavam.
            Astronomia.eDia(System.currentTimeMillis(), ponto.lat, ponto.lon, c.getInt("is_day") == 1),
            lista(h, "time") { a, i -> a.getString(i) }, lista(h, "temperature_2m") { a, i -> a.optDouble(i) },
            lista(h, "precipitation_probability") { a, i -> a.optInt(i, 0) }, lista(h, "weather_code") { a, i -> a.optInt(i, 3) },
            lista(h, "wind_speed_10m") { a, i -> a.optDouble(i) }, lista(h, "visibility") { a, i -> a.optDouble(i) },
            lista(h, "dew_point_2m") { a, i -> a.optDouble(i) }, lista(h, "uv_index") { a, i -> a.optDouble(i, 0.0) },
            lista(d, "time") { a, i -> a.getString(i) }, lista(d, "weather_code") { a, i -> a.optInt(i, 3) },
            lista(d, "temperature_2m_max") { a, i -> a.optDouble(i) }, lista(d, "temperature_2m_min") { a, i -> a.optDouble(i) },
            lista(d, "precipitation_probability_max") { a, i -> a.optInt(i, 0) },
            lista(d, "sunrise") { a, i -> a.getString(i) }, lista(d, "sunset") { a, i -> a.getString(i) },
            d.getJSONArray("precipitation_sum").optDouble(0, 0.0),
            aqi,
            j.optString("fonte"),
            c.optInt("cloud_cover", -1),
        )
    }

    /**
     * A medida "do topo" (aeroporto/estação que a HOME mostra), se tiver até 90 min: temperatura, chovendo, céu e
     * umidade. As telas usam estes no lugar do modelo para não discordar da HOME (teste na TV, 15/11/2026).
     */
    class Topo(val temp: Int?, val chovendo: Boolean, val codigo: Int?, val umidade: Int?)

    fun topo(ctx: Context): Topo {
        val g = ctx.getSharedPreferences(Prefs.CLIMA, Context.MODE_PRIVATE)
        val recente = System.currentTimeMillis() - g.getLong("em", 0) < 90 * 60_000L
        if (!recente) return Topo(null, false, null, null)
        return Topo(if (g.contains("temp")) g.getInt("temp", 0) else null, g.getBoolean("chovendo", false),
            g.getInt("codigo", -1).takeIf { it >= 0 }, g.getInt("umidade", -1).takeIf { it >= 0 })
    }

    /** "2026-11-02T16:15" -> minutos desde a meia-noite (só a hora do lugar; sem fuso, de propósito). */
    fun minutos(t: String) = t.substring(11, 13).toInt() * 60 + t.substring(14, 16).toInt()
}

/** Os textos da previsão no idioma da TV (a frase do dia, as dicas, UV e ar), iguais no Clima e no descanso. */
internal object ForecastText {
    fun hora(ctx: Context, h: Int) = ctx.getString(R.string.cl_hora, h)

    fun frase(ctx: Context, f: DicasClima.Frase): String = when (f.tipo) {
        DicasClima.TipoFrase.PARA_AS -> ctx.getString(R.string.cl_f_parar, hora(ctx, f.a))
        DicasClima.TipoFrase.CHUVA_SEGUE -> ctx.getString(R.string.cl_f_segue)
        DicasClima.TipoFrase.CHUVA_AS -> ctx.getString(R.string.cl_f_chuva, hora(ctx, f.a), f.b)
        DicasClima.TipoFrase.CALOR -> ctx.getString(R.string.cl_f_calor, f.a, hora(ctx, f.b))
        DicasClima.TipoFrase.FRIO -> ctx.getString(R.string.cl_f_frio, f.a)
        DicasClima.TipoFrase.SOL_FORTE -> ctx.getString(R.string.cl_f_sol, hora(ctx, f.a), hora(ctx, f.b))
        DicasClima.TipoFrase.AMANHA_CHUVA -> ctx.getString(R.string.cl_f_amanha, f.a)
        DicasClima.TipoFrase.CALMO -> ctx.getString(R.string.cl_f_calmo, f.a)
    }

    fun dica(ctx: Context, d: DicasClima.Dica): String = when (d.resposta) {
        DicasClima.Resposta.AGORA -> ctx.getString(R.string.cl_v_agora)
        DicasClima.Resposta.SIM_AS -> ctx.getString(R.string.cl_v_sim_as, hora(ctx, d.hora))
        DicasClima.Resposta.TALVEZ -> ctx.getString(R.string.cl_v_talvez)
        DicasClima.Resposta.NAO -> ctx.getString(R.string.cl_v_nao)
        DicasClima.Resposta.ATE -> ctx.getString(R.string.cl_v_ate, hora(ctx, d.hora))
        DicasClima.Resposta.SECA_HOJE -> ctx.getString(R.string.cl_v_seca)
        DicasClima.Resposta.MELHOR_NAO -> ctx.getString(R.string.cl_v_melhor_nao)
        DicasClima.Resposta.AMANHA_SIM -> ctx.getString(R.string.cl_v_amanha_sim)
        DicasClima.Resposta.AMANHA_NAO -> ctx.getString(R.string.cl_v_amanha_nao)
        DicasClima.Resposta.AS -> ctx.getString(R.string.cl_v_as, hora(ctx, d.hora))
        DicasClima.Resposta.AMANHA_AS -> ctx.getString(R.string.cl_v_amanha_as, hora(ctx, d.hora))
        DicasClima.Resposta.HOJE_NAO -> ctx.getString(R.string.cl_v_hoje_nao)
    }

    fun uv(ctx: Context, uv: Double) = ctx.getString(when {
        uv < 3 -> R.string.uv_baixo; uv < 6 -> R.string.uv_moderado; uv < 8 -> R.string.uv_alto; uv < 11 -> R.string.uv_muito_alto; else -> R.string.uv_extremo
    })

    /** UV que pede atenção (o fio fica laranja no descanso). */
    fun uvAlto(uv: Double) = uv >= 8

    fun aqi(ctx: Context, a: Int): Pair<String, Int> = when {
        a <= 50 -> ctx.getString(R.string.aq_boa) to 0xFF7BE0A0.toInt()
        a <= 100 -> ctx.getString(R.string.aq_moderada) to 0xFFF2E26B.toInt()
        a <= 150 -> ctx.getString(R.string.aq_sensiveis) to 0xFFFFB566.toInt()
        a <= 200 -> ctx.getString(R.string.aq_ruim) to 0xFFFF7A6B.toInt()
        a <= 300 -> ctx.getString(R.string.aq_muito_ruim) to 0xFFD08CFF.toInt()
        else -> ctx.getString(R.string.aq_perigosa) to 0xFFFF5C7A.toInt()
    }

    /** "do nordeste" a partir dos graus de onde o vento vem (a rosa dos ventos das strings). */
    fun rosa(ctx: Context, graus: Int): String = ctx.resources.getStringArray(R.array.rosa_ventos)[((graus + 22.5) / 45).toInt() % 8]
}

/** Escala de 0 a 300+ do índice de qualidade do ar (verde a vinho) com um marcador onde o valor está. */
internal class BarraAqi(ctx: Context, val aqi: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) = Rtl.espelhar(this, c) { desenhar(c) }   // 16.8: o tempo anda para a esquerda no árabe
    private fun desenhar(c: Canvas) {
        val h = height.toFloat()
        p.shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
            intArrayOf(0xFF7BE0A0.toInt(), 0xFFF2E26B.toInt(), 0xFFFFB566.toInt(), 0xFFFF7A6B.toInt(), 0xFFD08CFF.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, width.toFloat(), h), h / 2, h / 2, p); p.shader = null
        p.color = Color.WHITE
        c.drawCircle(((aqi.coerceIn(0, 300) / 300f) * (width - h) + h / 2), h / 2, h * 0.9f, p)
    }
}

/** O arco do sol: tracejado do nascer ao pôr, com o sol (bolinha amarela) na posição de agora ([fracao] 0 = nascer, 1 = pôr). */
internal class ArcoSol(ctx: Context, val fracao: Float) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) = Rtl.espelhar(this, c) { desenhar(c) }   // 16.8: o tempo anda para a esquerda no árabe
    private fun desenhar(c: Canvas) {
        val d = resources.displayMetrics.density
        val raioX = width / 2f - 12 * d; val raioY = height - 10 * d; val cx = width / 2f; val cy = height - 4 * d
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = 0x55FFFFFF
        p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(5 * d, 5 * d), 0f)
        c.drawArc(RectF(cx - raioX, cy - raioY, cx + raioX, cy + raioY), 180f, 180f, false, p); p.pathEffect = null
        p.color = 0x55FFFFFF; c.drawLine(8 * d, cy, width - 8 * d, cy, p)
        val ang = PI * (1 - fracao.coerceIn(0f, 1f))
        val x = cx + raioX * cos(ang).toFloat(); val y = cy - raioY * sin(ang).toFloat()
        p.style = Paint.Style.FILL
        p.color = 0x55FFD36B; c.drawCircle(x, y, 11 * d, p)
        p.color = 0xFFFFD36B.toInt(); c.drawCircle(x, y, 6.5f * d, p)
    }
}
