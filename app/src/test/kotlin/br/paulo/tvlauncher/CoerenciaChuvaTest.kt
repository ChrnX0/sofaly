package br.paulo.tvlauncher

import br.paulo.tvlauncher.DicasClima.Resposta
import br.paulo.tvlauncher.DicasClima.TipoFrase
import br.paulo.tvlauncher.weather.ClimaFontes
import br.paulo.tvlauncher.weather.Forecast
import br.paulo.tvlauncher.weather.Localizacao
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A MESMA informação dita igual em todas as telas (Paulo, 25/11/2026: "a porcentagem da HOME não concorda com a do
 * descanso de tela").
 *
 * A pílula da HOME (ClimaFontes) e o Clima/protetor (Forecast) leem a previsão por caminhos diferentes. Aqui os dois recebem
 * o MESMO JSON, em 2.000 dias sorteados (hora de agora, dia/noite e chance de cada hora ao acaso), e têm de anunciar a
 * mesma chuva: mesma hora e mesmo número. E as três falas do Clima (frase, guarda-chuva) concordam com ela.
 */
class CoerenciaChuvaTest {
    private val ponto = Localizacao.Ponto(-23.55, -46.63, "teste", "Bairro de exemplo")

    private fun dia(r: Random): Pair<JSONObject, Boolean> {
        val h0 = r.nextInt(24)
        val horas = 72
        // Chuva em "frentes": a maioria das horas seca, algumas perto dos limiares (29/30, 39/40, 49/50) para pegar bordas.
        val chance = IntArray(horas) { when (r.nextInt(10)) { 0 -> 49 + r.nextInt(2); 1 -> 39 + r.nextInt(2); 2 -> 29 + r.nextInt(2)
            3 -> 50 + r.nextInt(51); else -> r.nextInt(30) } }
        fun arr(n: Int, f: (Int) -> Any) = JSONArray().apply { (0 until n).forEach { put(f(it)) } }
        val nasce = 5 + r.nextInt(2); val poe = 18 + r.nextInt(2)
        val eDia = h0 in nasce until poe
        val j = JSONObject()
            .put("current", JSONObject().put("time", "2026-11-25T%02d:15".format(h0)).put("temperature_2m", 24.0).put("apparent_temperature", 25.0)
                .put("relative_humidity_2m", 70).put("weather_code", 2).put("pressure_msl", 1013.0).put("wind_speed_10m", 8.0)
                .put("wind_direction_10m", 90).put("is_day", if (eDia) 1 else 0))
            .put("hourly", JSONObject()
                .put("time", arr(horas) { "2026-11-%02dT%02d:00".format(25 + it / 24, it % 24) })
                .put("temperature_2m", arr(horas) { 24.0 }).put("precipitation_probability", arr(horas) { chance[it] })
                .put("weather_code", arr(horas) { 2 }).put("wind_speed_10m", arr(horas) { 8.0 }).put("visibility", arr(horas) { 20000.0 })
                .put("dew_point_2m", arr(horas) { 18.0 }).put("uv_index", arr(horas) { 3.0 }))
            .put("daily", JSONObject()
                .put("time", arr(3) { "2026-11-%02d".format(25 + it) })
                .put("weather_code", arr(3) { 2 }).put("temperature_2m_max", arr(3) { 30.0 }).put("temperature_2m_min", arr(3) { 20.0 })
                .put("precipitation_probability_max", arr(3) { d -> (0 until 24).maxOf { chance[d * 24 + it] } })
                .put("sunrise", arr(3) { "2026-11-%02dT%02d:00".format(25 + it, nasce) })
                .put("sunset", arr(3) { "2026-11-%02dT%02d:00".format(25 + it, poe) })
                .put("precipitation_sum", arr(3) { 3.0 }))
        return j to eDia
    }

    @Test fun homeEClimaAnunciamAMesmaChuva() {
        val r = Random(20261125)
        var comChuva = 0
        repeat(2000) { n ->
            val (j, eDia) = dia(r)
            val agora = j.getJSONObject("current").getString("time")
            val h = j.getJSONObject("hourly")
            val tempos = (0 until h.getJSONArray("time").length()).map { h.getJSONArray("time").getString(it) }
            val chances = (0 until tempos.size).map { h.getJSONArray("precipitation_probability").getInt(it) }

            val clima = Forecast.parse(j, ponto, null).entrada(24, chovendo = false)
            // O "é dia agora" das duas é o mesmo cálculo (Astronomia.eDia com o relógio de verdade), como na TV.
            val eDiaAgora = br.paulo.tvlauncher.weather.Astronomia.eDia(System.currentTimeMillis(), ponto.lat, ponto.lon, eDia)
            val home = ClimaFontes.entradaDaChuva(j, agora, tempos, chances, eDiaAgora)
            val ctx = "dia $n, agora $agora"

            // As duas leituras veem as mesmas horas: mesma hora do relógio, chance, dia/noite e "é hoje".
            val k = minOf(clima.horas.size, home.horas.size)
            assertTrue("$ctx: poucas horas", k >= 12)
            for (i in 0 until k) {
                val a = clima.horas[i]; val b = home.horas[i]
                assertEquals("$ctx, hora $i", listOf(a.hora, a.chance, a.dia, a.hoje), listOf(b.hora, b.chance, b.dia, b.hoje))
            }
            val anunciada = DicasClima.chuvaAnunciada(clima)
            assertEquals("$ctx: HOME e Clima anunciam outra chuva", anunciada, DicasClima.chuvaAnunciada(home))

            // As falas do Clima concordam com a chuva anunciada.
            val frase = DicasClima.frase(clima)
            val guarda = DicasClima.guardaChuva(clima)
            when {
                anunciada == null -> {
                    assertTrue("$ctx: frase $frase sem chuva anunciada", frase.tipo != TipoFrase.CHUVA_AGORA && frase.tipo != TipoFrase.CHUVA_AS)
                    assertTrue("$ctx: guarda-chuva $guarda sem chuva anunciada", guarda.resposta != Resposta.AGORA && guarda.resposta != Resposta.SIM_AS)
                }
                anunciada.first == 0 -> {
                    comChuva++
                    assertEquals(ctx, DicasClima.Frase(TipoFrase.CHUVA_AGORA, clima.horas[0].hora, anunciada.second), frase)
                    assertEquals(ctx, Resposta.AGORA, guarda.resposta)
                }
                else -> {
                    comChuva++
                    val hora = clima.horas[anunciada.first].hora
                    assertEquals(ctx, DicasClima.Frase(TipoFrase.CHUVA_AS, hora, anunciada.second), frase)
                    assertEquals(ctx, DicasClima.Dica(Resposta.SIM_AS, hora), guarda)
                }
            }
        }
        assertTrue("o sorteio precisa ter dias com e sem chuva: $comChuva", comChuva in 200..1800)
    }
}
