package br.paulo.tvlauncher.weather

import android.util.Log
import br.paulo.tvlauncher.Config
import br.paulo.tvlauncher.alarm.FaixaDias
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Previsão do plano Básico (24/11/2026, aprovado pelo Paulo): CONSENSO de 4 modelos abertos (ECMWF IFS, ECMWF AIFS,
 * GFS, ICON), pedida ao NOSSO servidor (GET api.sofaly.tv/v1/weather/open, ver server/api/src/weatherOpen.js).
 *
 * Por quê: no teste de 24/11 (30 aeroportos x 60 dias, conferido com o METAR) a fração dos modelos que preveem chuva
 * acertou "chove amanhã?" mais do que qualquer modelo sozinho (HSS 0,55; o MET Norway usa o ECMWF aqui, 0,44).
 * Detalhes: clima-alternativas.md, seção 11, nos arquivos do projeto.
 *
 * O servidor já devolve o formato da Open-Meteo (current / hourly / daily em horas locais): temperatura e céu do
 * ECMWF IFS, "precipitation_probability" = % dos modelos com chuva. Aqui só corta (a partir de agora, [dias] dias),
 * converte para °F se for o caso e marca a [FONTE] (a tela de Previsão mostra o crédito que a licença CC BY pede).
 * Servidor sem os modelos (503) ou fora do ar: null, e a TV usa o MET Norway (pausa como o [WeatherApiSource]).
 */
object OpenModelsSource {
    private const val TAG = "TvLauncher.OpenModels"
    const val URL_BASE = "https://api.sofaly.tv/v1/weather/open"
    const val FONTE = "open-models"
    /** 503 = servidor sem a fonte configurada (só muda com um deploy): não pergunta de novo por 6 h. */
    private const val PAUSA_MS = 6 * 3_600_000L
    /** Falha passageira (sem rede, 502, 429): tenta de novo em 15 min. */
    private const val PAUSA_CURTA_MS = 15 * 60_000L
    /** O servidor guarda 30 min; aqui 15 min para a HOME, a Previsão e os avisos dividirem uma chamada. */
    private const val GUARDA_MS = 15 * 60_000L

    @Volatile private var pausaAte = 0L
    private class Guardado(val chave: String, val corpo: String, val em: Long)
    @Volatile private var guardado: Guardado? = null

    fun emPausa(agora: Long = System.currentTimeMillis()) = agora < pausaAte

    /** Só para o teste de telas (ver [ClimaMet.esquecerCache]). */
    internal fun esquecerCache() { guardado = null }

    /** Previsão do ponto no formato da Open-Meteo, ou null. Fora da thread da tela. */
    fun previsao(lat: Double, lon: Double, desdeAgora: Boolean, dias: Int = FaixaDias.DIAS): JSONObject? {
        if (emPausa()) return null
        val corpo = buscar(lat, lon) ?: return null
        return try {
            ajustar(JSONObject(corpo), dias, desdeAgora, Config.USAR_FAHRENHEIT)
        } catch (e: Exception) {
            Log.w(TAG, "formato estranho: ${e.javaClass.simpleName}")
            null
        }
    }

    @Synchronized
    private fun buscar(lat: Double, lon: Double): String? {
        val chave = ClimaMet.coord(lat) + "," + ClimaMet.coord(lon)
        val agora = System.currentTimeMillis()
        guardado?.takeIf { it.chave == chave && agora - it.em < GUARDA_MS }?.let { return it.corpo }
        val c = URL("$URL_BASE?lat=${ClimaMet.coord(lat)}&lon=${ClimaMet.coord(lon)}").openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", ClimaMet.AGENTE)
            br.paulo.tvlauncher.relay.RelayTv.assinar(c)   // cota do dia por TV (item 23)
            if (c.responseCode == 200) {
                c.inputStream.bufferedReader().use { it.readText() }.also { guardado = Guardado(chave, it, agora) }
            } else {
                val pausa = pausaPara(c.responseCode)
                Log.i(TAG, "servidor respondeu ${c.responseCode}: MET Norway por ${pausa / 60_000} min")
                pausaAte = agora + pausa
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "falhou: ${e.javaClass.simpleName}")
            pausaAte = agora + PAUSA_CURTA_MS
            null
        } finally { c.disconnect() }
    }

    // ---------------------------------------------------------------- lógica pura (OpenModelsSourceTest)

    fun pausaPara(status: Int): Long = if (status == 503) PAUSA_MS else PAUSA_CURTA_MS

    private val TEMPERATURAS = setOf("temperature_2m", "apparent_temperature", "dew_point_2m", "temperature_2m_max", "temperature_2m_min")

    /**
     * Corta e converte a resposta do servidor. [desdeAgora] = o "hourly" começa na hora cheia de "current.time" (como o
     * MET e a WeatherAPI fazem); senão, à meia-noite de hoje. "daily" fica com [dias] dias; "hourly", até o fim deles.
     */
    fun ajustar(j: JSONObject, dias: Int, desdeAgora: Boolean, fahrenheit: Boolean): JSONObject {
        val current = j.getJSONObject("current")
        val hourly = j.getJSONObject("hourly")
        val daily = j.getJSONObject("daily")
        val dTempo = daily.getJSONArray("time")
        val nDias = minOf(dias, dTempo.length())
        val fimDia = dTempo.getString(nDias - 1)                       // último dia que fica ("2026-11-24")
        val horaAgora = current.getString("time").take(13)              // "2026-11-24T10"
        val hTempo = hourly.getJSONArray("time")
        val manter = (0 until hTempo.length()).filter { i ->
            val t = hTempo.getString(i)
            t.take(10) <= fimDia && (!desdeAgora || t.take(13) >= horaAgora)
        }
        fun f(v: Any?): Any? = if (fahrenheit && v is Number) Math.round((v.toDouble() * 9 / 5 + 32) * 10) / 10.0 else v
        fun cortar(bloco: JSONObject, indices: List<Int>) = JSONObject().also { novo ->
            for (k in bloco.keys()) {
                val a = bloco.optJSONArray(k) ?: continue
                novo.put(k, JSONArray().also { b ->
                    for (i in indices) b.put((if (k in TEMPERATURAS) f(a.opt(i)) else a.opt(i)) ?: JSONObject.NULL)
                })
            }
        }
        val novoAgora = JSONObject()
        for (k in current.keys()) novoAgora.put(k, if (k in TEMPERATURAS) f(current.opt(k)) else current.opt(k))
        return JSONObject()
            .put("current", novoAgora)
            .put("hourly", cortar(hourly, manter))
            .put("daily", cortar(daily, (0 until nDias).toList()))
            .put("fonte", FONTE)
    }
}
