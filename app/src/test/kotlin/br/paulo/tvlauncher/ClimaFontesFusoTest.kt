package br.paulo.tvlauncher

import br.paulo.tvlauncher.weather.ClimaFontes
import java.util.TimeZone
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** As horas da Open-Meteo (timezone=auto) vêm no fuso do lugar, não no da TV. */
class ClimaFontesFusoTest {
    @Test fun usaOFusoDaResposta() {
        assertEquals("Asia/Tokyo", ClimaFontes.fusoDe(JSONObject("""{"timezone":"Asia/Tokyo"}""")).id)
        assertEquals(TimeZone.getDefault().id, ClimaFontes.fusoDe(JSONObject("{}")).id)
        assertEquals(TimeZone.getDefault().id, ClimaFontes.fusoDe(JSONObject("""{"timezone":"Lugar/Inventado"}""")).id)
    }

    @Test fun horasDoLugarViramHorasDaTv() {
        val sp = TimeZone.getTimeZone("America/Sao_Paulo"); val lisboa = TimeZone.getTimeZone("Europe/Lisbon")
        assertEquals("2026-11-24T21:00", ClimaFontes.paraFusoDaTv("2026-11-24T18:00", sp, lisboa))
        assertEquals("2026-11-24T18:00", ClimaFontes.paraFusoDaTv("2026-11-24T18:00", sp, sp))
    }
}
