package br.paulo.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenGridTest {
    @Test fun `12 colunas fecham a area util`() {
        assertEquals(ScreenGrid.W - ScreenGrid.MARGIN_X, ScreenGrid.colX(12) + ScreenGrid.COL_W, 0.01f)
        assertEquals(864f, ScreenGrid.span(12), 0.01f)
        assertEquals(124f, ScreenGrid.span(2), 0.01f)
    }

    @Test fun `8 linhas fecham a altura`() {
        assertEquals(ScreenGrid.H - ScreenGrid.MARGIN_Y, ScreenGrid.rowY(8) + ScreenGrid.ROW_H, 0.01f)
        assertEquals(450f, ScreenGrid.rowY(8), 0.01f)
    }
}
