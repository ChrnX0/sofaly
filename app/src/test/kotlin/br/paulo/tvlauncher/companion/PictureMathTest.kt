package br.paulo.tvlauncher.companion

import br.paulo.tvlauncher.companion.PictureMath.Advice
import br.paulo.tvlauncher.companion.PictureMath.Box
import br.paulo.tvlauncher.companion.PictureMath.Verdict
import br.paulo.tvlauncher.panel.PanelCompanionRules
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Calibrar a imagem pelo celular (24/11/2026): as contas que viram conselho para o menu da TV. */
class PictureMathTest {

    /** Simula a câmera: a TV corta [k1]/[k2] de cada lado e mostra o resto em [px] pixels a partir de [x0]. */
    private fun camera(k1: Double, k2: Double, x0: Double = 40.0, px: Double = 240.0): Pair<Double, Double> {
        val s = { t: Double -> x0 + (t - k1) / (1 - k1 - k2) * px }
        return s(PictureMath.INSET) to s(1 - PictureMath.INSET)
    }

    @Test fun overscanZeroQuandoATvMostraTudo() {
        val (l, r) = camera(0.0, 0.0)
        val o = PictureMath.overscan(Box(40.0, 30.0, 280.0, 165.0), Box(l, 36.75, r, 158.25))!!
        assertEquals(0.0, o.left, 1e-9); assertEquals(0.0, o.right, 1e-9)
        assertEquals(Verdict.GOOD, PictureMath.judgeOverscan(o).verdict)
    }

    @Test fun overscanRecuperaOCorteDeCadaLado() {
        val (l, r) = camera(0.03, 0.02)
        val o = PictureMath.overscan(Box(40.0, 0.0, 280.0, 100.0), Box(l, 5.0, r, 95.0))!!
        assertEquals(0.03, o.left, 1e-9); assertEquals(0.02, o.right, 1e-9)
        val j = PictureMath.judgeOverscan(o)
        assertEquals(Advice.TURN_ON_JUST_SCAN, j.advice)
        assertTrue(j.detail, j.detail.contains("l=3.0%"))
    }

    @Test fun overscanRecusaCaixasSemSentido() {
        assertNull(PictureMath.overscan(Box(40.0, 0.0, 280.0, 100.0), Box(10.0, 5.0, 300.0, 95.0)))   // maior que a TV
        assertNull(PictureMath.overscan(Box(0.0, 0.0, 2.0, 2.0), Box(0.0, 0.0, 1.0, 1.0)))
        assertEquals(Verdict.UNSURE, PictureMath.judgeOverscan(null).verdict)
    }

    @Test fun pretoEsmagadoPedeMaisBrilho() {
        // Só o degrau 20 (índice 4) se separa do 0.
        val r = PictureMath.judgeBlack(listOf(10.0, 10.2, 10.5, 11.0, 18.0, 25.0, 33.0), null)
        assertEquals(Advice.RAISE_BRIGHTNESS, r.advice)
    }

    @Test fun pretoBomEPretoAcinzentado() {
        val degraus = listOf(8.0, 14.0, 21.0, 29.0, 37.0, 45.0, 53.0)
        assertEquals(Verdict.GOOD, PictureMath.judgeBlack(degraus, 0.05).verdict)
        assertEquals(Advice.LOWER_BRIGHTNESS, PictureMath.judgeBlack(degraus, 0.2).advice)
        assertEquals(Verdict.UNSURE, PictureMath.judgeBlack(degraus.take(5), null).verdict)
    }

    @Test fun brancoColadoPedeMenosContraste() {
        assertEquals(Advice.LOWER_CONTRAST, PictureMath.judgeWhite(listOf(180.0, 190.0, 200.0, 210.0, 220.0, 220.5, 221.0)).advice)
        assertEquals(Verdict.GOOD, PictureMath.judgeWhite(listOf(180.0, 188.0, 196.0, 204.0, 212.0, 220.0, 228.0)).verdict)
        assertEquals(Verdict.UNSURE, PictureMath.judgeWhite(listOf(240.0, 245.0, 250.0, 252.0, 253.0, 254.0, 255.0)).verdict)
    }

    @Test fun corSoValeComOBalancoTravado() {
        assertEquals(Verdict.UNSURE, PictureMath.judgeColor(150.0, 150.0, 180.0, wbLocked = false).verdict)
        assertEquals(Advice.WARMER, PictureMath.judgeColor(150.0, 150.0, 170.0, wbLocked = true).advice)
        assertEquals(Advice.COOLER, PictureMath.judgeColor(175.0, 150.0, 140.0, wbLocked = true).advice)
        assertEquals(Advice.LESS_GREEN, PictureMath.judgeColor(140.0, 150.0, 141.0, wbLocked = true).advice)
        assertEquals(Advice.MORE_GREEN, PictureMath.judgeColor(160.0, 150.0, 159.0, wbLocked = true).advice)
        assertEquals(Verdict.GOOD, PictureMath.judgeColor(151.0, 150.0, 152.0, wbLocked = true).verdict)
        assertEquals(Verdict.UNSURE, PictureMath.judgeColor(0.0, 150.0, 152.0, wbLocked = true).verdict)
    }

    @Test fun ganhoNuncaPassaDeUm() {
        val g = PictureMath.gain(200.0, 180.0, 160.0)!!
        assertEquals(0.8, g.r, 1e-9); assertEquals(160.0 / 180.0, g.g, 1e-9); assertEquals(1.0, g.b, 1e-9)
    }

    @Test fun rotaDoPainelLeSoNumerosComLimite() {
        val bom = JSONObject().put("luma", JSONArray(listOf(8, 14, 21, 29, 37, 45, 53)))
        assertEquals(Verdict.GOOD, PanelCompanionRules.judge("black", bom)!!.verdict)
        assertNull(PanelCompanionRules.judge("black", JSONObject().put("luma", JSONArray(listOf(1, 2, 3)))))
        assertNull(PanelCompanionRules.judge("black", JSONObject().put("luma", JSONArray(listOf(8, 14, 21, 29, 37, 45, 999)))))
        assertNull(PanelCompanionRules.judge("color", JSONObject().put("r", 1).put("g", "x")))
        assertNull(PanelCompanionRules.judge("outra", JSONObject()))
        // blackOverWhite fora de 0..1 é ignorado (não vira "acinzentado").
        assertEquals(Verdict.GOOD, PanelCompanionRules.judge("black", JSONObject(bom.toString()).put("blackOverWhite", 7))!!.verdict)
    }

    @Test fun chaveDaTvEDetalheDoEvento() {
        assertEquals("GSM-1234-2013", CompanionRules.displayKey("GSM", "1234", 2013, "LG TV"))
        assertEquals("LGTV", CompanionRules.displayKey(null, null, 0, "LG TV"))
        assertEquals("hdmi", CompanionRules.displayKey(null, "", -1, null))
        val d = CompanionRules.eventDetail(PictureMath.Result(Verdict.ADJUST, Advice.WARMER, "a\tb\nc" + "x".repeat(200)))
        assertTrue(d.length <= 120 && '\t' !in d && '\n' !in d)
        assertEquals("picture_color_gain", CompanionRules.pictureKind("color"))
    }

    /** O nome do fone Bluetooth fica fora do que vai à Sofy; ações do aparelho vão como DEVICE. */
    @Test fun eventoParaASofySemNomeDeAparelho() {
        assertEquals("bt", CompanionRules.routeType("bt-FonedaAna"))
        assertEquals("hdmi", CompanionRules.routeType("hdmi"))
        assertTrue("remote_find" in CompanionRules.DEVICE_KINDS && "audio_delay_ms" !in CompanionRules.DEVICE_KINDS)
    }
}
