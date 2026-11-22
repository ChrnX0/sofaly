package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.VideoLayerRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLayerRulesTest {
    @Test fun cobreSemFaixaPreta() {
        // Vídeo 16:9 numa tela 16:9: do tamanho da tela.
        assertEquals(1920 to 1080, VideoLayerRules.cobrir(1920, 1080, 16f / 9f))
        // Vídeo 4:3: mais alto que a tela (sobra em cima e embaixo), nunca faixa dos lados.
        val (w, h) = VideoLayerRules.cobrir(1920, 1080, 4f / 3f)
        assertEquals(1920, w); assertEquals(1440, h)
        // Vídeo de celular em pé (9:16): ocupa a largura toda.
        val (w2, h2) = VideoLayerRules.cobrir(1920, 1080, 9f / 16f)
        assertTrue(w2 >= 1920 && h2 >= 1080)
        // Cinema 2.39:1: mais largo que a tela, cobre a altura.
        val (w3, h3) = VideoLayerRules.cobrir(1920, 1080, 2.39f)
        assertEquals(1080, h3); assertTrue(w3 > 1920)
    }

    @Test fun medidaInvalidaNaoQuebra() {
        assertEquals(0 to 0, VideoLayerRules.cobrir(0, 0, 1.7f))
        assertEquals(1920 to 1080, VideoLayerRules.cobrir(1920, 1080, 0f))
    }

    @Test fun brilhoViraVeu() {
        assertEquals(0, VideoLayerRules.alfaVeu(0))
        assertEquals(VideoLayerRules.alfaVeu(50), VideoLayerRules.alfaVeu(-50))
        assertTrue(VideoLayerRules.alfaVeu(-100) < 255)   // nunca preto total: o vídeo ainda aparece
        assertEquals(VideoLayerRules.alfaVeu(100), VideoLayerRules.alfaVeu(300))
    }
}

class LarguraTextoCruzTest {
    @Test fun textoNuncaEncostaNaCategoriaDaDireita() {
        for (r in br.paulo.tvlauncher.home.CrossRhythm.entries) {
            val l = br.paulo.tvlauncher.home.CruzLogica.larguraTexto(r.coluna, r.iconeL, r.vaoTexto, 20, r.catDireita, r.catL, 32, 300)
            val fimTexto = r.coluna + r.iconeL / 2f + r.vaoTexto + 20 + l
            assertTrue("${r.name}: texto termina em $fimTexto", fimTexto <= r.catDireita - r.catL / 2f - 32)
        }
    }
}
