package br.paulo.tvlauncher.companion

import br.paulo.tvlauncher.companion.AudioSyncMath.Ping
import br.paulo.tvlauncher.companion.AudioSyncMath.Verdict
import br.paulo.tvlauncher.panel.PanelCompanionRules
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Acertar o som pelo microfone do celular (24/11/2026). */
class AudioSyncMathTest {

    @Test fun relogioUsaAsIdasEVoltasMaisRapidas() {
        // Relógio da TV = celular + 5000. Uma ida e volta lenta (Wi-Fi segurou o pacote só na volta) fica de fora.
        val pings = listOf(Ping(100.0, 5106.0, 112.0), Ping(200.0, 5205.0, 210.0), Ping(300.0, 5304.0, 308.0), Ping(400.0, 5402.0, 700.0))
        val (off, err) = AudioSyncMath.clockOffset(pings)!!
        assertEquals(5000.0, off, 1.0)
        assertEquals(4.0, err, 1e-9)
        assertNull(AudioSyncMath.clockOffset(listOf(Ping(10.0, 0.0, 5.0))))   // volta antes da ida: lixo
    }

    @Test fun atrasoCasaCadaBipeComOEscritoAntes() {
        val escritos = (0 until 6).map { 10_000.0 + it * 700 }
        // Celular = TV - 5000; som sai 180 ms depois (±4), mais um eco 120 ms depois do 2º bipe.
        val ouvidos = escritos.mapIndexed { i, t -> t + 180 + (if (i % 2 == 0) 4 else -4) - 5000 } + (escritos[1] + 300 - 5000)
        val r = AudioSyncMath.delay(escritos, ouvidos, 5000.0, 5.0)
        assertEquals(Verdict.GOOD, r.verdict)
        assertEquals(180, r.delayMs)
        assertEquals(6, r.heard)
    }

    @Test fun poucosBipesOuRelogioRuimNaoValem() {
        val escritos = listOf(0.0, 700.0, 1400.0)
        assertEquals(Verdict.UNSURE, AudioSyncMath.delay(escritos, listOf(200.0, 900.0), 0.0, 5.0).verdict)
        assertEquals(Verdict.UNSURE, AudioSyncMath.delay(escritos, listOf(200.0, 900.0, 1600.0), 0.0, 80.0).verdict)
        // Espalhado demais: 150, 250, 350.
        assertEquals(Verdict.UNSURE, AudioSyncMath.delay(escritos, listOf(150.0, 950.0, 1750.0), 0.0, 5.0).verdict)
    }

    @Test fun nomeDaSaida() {
        assertEquals("hdmi", AudioSyncMath.routeKey("hdmi", null))
        assertEquals("bt-JBLFlip5", AudioSyncMath.routeKey("bt", "JBL Flip 5"))
    }

    @Test fun rotaLeRelogioEHorasComLimite() {
        val p = JSONArray().put(JSONObject().put("s", 1).put("t", 2).put("g", 3))
        assertEquals(1, PanelCompanionRules.pings(p)!!.size)
        assertNull(PanelCompanionRules.pings(JSONArray().put(JSONObject().put("s", 1).put("t", "x").put("g", 3))))
        assertNull(PanelCompanionRules.pings(JSONArray()))
        assertNull(PanelCompanionRules.times(JSONArray((0..50).toList())))
        assertNull(PanelCompanionRules.times(JSONArray(listOf(1.0, -2.0))))
        assertEquals(2, PanelCompanionRules.times(JSONArray(listOf(1.0, 2.0)))!!.size)
    }
}
