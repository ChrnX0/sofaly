package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.GlassBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** O desfoque do fundo de vidro (coerência das telas, 24/11/2026): cor lisa não muda; um ponto claro se espalha. */
class GlassBackgroundTest {
    @Test fun corLisaContinuaLisa() {
        val px = IntArray(12 * 8) { 0xFF336699.toInt() }
        GlassBackground.caixa(px, 12, 8, 3)
        assertTrue(px.all { it == 0xFF336699.toInt() })
    }

    @Test fun pontoClaroSeEspalha() {
        val w = 9; val h = 9
        val px = IntArray(w * h) { 0xFF000000.toInt() }
        px[4 * w + 4] = 0xFFFFFFFF.toInt()
        GlassBackground.caixa(px, w, h, 1)
        val centro = px[4 * w + 4] and 0xFF
        val vizinho = px[3 * w + 3] and 0xFF
        assertEquals(255 / 9, centro)
        assertEquals(centro, vizinho)
        assertEquals(0, px[0] and 0xFF)
    }
}
