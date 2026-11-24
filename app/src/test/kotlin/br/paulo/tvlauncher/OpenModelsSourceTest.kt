package br.paulo.tvlauncher

import br.paulo.tvlauncher.weather.OpenModelsSource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OpenModelsSource (Básico, 24/11/2026): a resposta do api.sofaly.tv/v1/weather/open já vem no formato da Open-Meteo;
 * aqui só se corta a partir de agora, nos dias pedidos, e se converte para °F. Amostra montada (3 dias x 24 h).
 */
class OpenModelsSourceTest {
    private val dias = listOf("2026-11-24", "2026-11-25", "2026-11-26")

    private fun amostra(): JSONObject {
        val horas = JSONArray(); val temp = JSONArray(); val chance = JSONArray(); val uv = JSONArray()
        for (d in dias) for (h in 0..23) {
            horas.put("%sT%02d:00".format(d, h)); temp.put(20.0); chance.put(if (h >= 15) 75 else 0); uv.put(0)
        }
        return JSONObject()
            .put("current", JSONObject().put("time", "2026-11-24T10:15").put("temperature_2m", 30.0).put("weather_code", 3))
            .put("hourly", JSONObject().put("time", horas).put("temperature_2m", temp).put("precipitation_probability", chance).put("uv_index", uv))
            .put("daily", JSONObject().put("time", JSONArray(dias)).put("temperature_2m_max", JSONArray(listOf(30.0, 31.0, 32.0)))
                .put("temperature_2m_min", JSONArray(listOf(20.0, 21.0, 22.0))).put("precipitation_probability_max", JSONArray(listOf(75, 25, JSONObject.NULL))))
            .put("models", JSONArray(listOf("ecmwf_ifs")))
    }

    @Test fun desdeAgoraCortaNaHoraCheia() {
        val j = OpenModelsSource.ajustar(amostra(), dias = 2, desdeAgora = true, fahrenheit = false)
        val h = j.getJSONObject("hourly")
        assertEquals("2026-11-24T10:00", h.getJSONArray("time").getString(0))
        assertEquals(14 + 24, h.getJSONArray("time").length())   // resto de hoje + amanhã
        assertEquals(h.getJSONArray("time").length(), h.getJSONArray("precipitation_probability").length())
        assertEquals(2, j.getJSONObject("daily").getJSONArray("time").length())
        assertEquals(OpenModelsSource.FONTE, j.getString("fonte"))
        assertTrue("o que a TV não usa não passa", !j.has("models"))
    }

    @Test fun deMeiaNoiteETodosOsDias() {
        val j = OpenModelsSource.ajustar(amostra(), dias = 9, desdeAgora = false, fahrenheit = false)
        assertEquals("2026-11-24T00:00", j.getJSONObject("hourly").getJSONArray("time").getString(0))
        assertEquals(72, j.getJSONObject("hourly").getJSONArray("time").length())
        assertEquals(3, j.getJSONObject("daily").getJSONArray("time").length())   // pediu 9, o servidor tinha 3
        assertTrue(j.getJSONObject("daily").getJSONArray("precipitation_probability_max").isNull(2))
    }

    @Test fun fahrenheitSoNasTemperaturas() {
        val j = OpenModelsSource.ajustar(amostra(), dias = 1, desdeAgora = false, fahrenheit = true)
        assertEquals(86.0, j.getJSONObject("current").getDouble("temperature_2m"), 0.01)
        assertEquals(86.0, j.getJSONObject("daily").getJSONArray("temperature_2m_max").getDouble(0), 0.01)
        assertEquals(68.0, j.getJSONObject("hourly").getJSONArray("temperature_2m").getDouble(0), 0.01)
        assertEquals(3, j.getJSONObject("current").getInt("weather_code"))
        assertEquals(75, j.getJSONObject("daily").getJSONArray("precipitation_probability_max").getInt(0))
    }

    @Test fun pausa() {
        assertEquals(6 * 3_600_000L, OpenModelsSource.pausaPara(503))
        assertEquals(15 * 60_000L, OpenModelsSource.pausaPara(502))
    }
}
