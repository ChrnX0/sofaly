package br.paulo.tvlauncher.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O tema do sistema (28/11/2026) e a cor exclusiva de cada ícone. */
class SystemLookTest {
    @Test fun aconchegoVemDeFabrica() {
        assertEquals(SystemLook.COZY, SystemLook.PADRAO)
        assertEquals(SystemLook.COZY, SystemLook.deNumero(-1))
        assertEquals(SystemLook.COZY, SystemLook.deNumero(7))
        assertEquals(SystemLook.GLASS, SystemLook.deNumero(0))
    }

    @Test fun nomesDoComandoAdb() {
        assertEquals(SystemLook.GLASS, SystemLook.deNome("vidro"))
        assertEquals(SystemLook.TILES, SystemLook.deNome(" Tiles "))
        assertEquals(SystemLook.COZY, SystemLook.deNome("aconchego"))
        assertNull(SystemLook.deNome("rosa"))
    }

    @Test fun cadaTemaTemSeuFoco() {
        assertEquals(FocusStyle.BOX, SystemLook.GLASS.focoPadrao)
        assertEquals(FocusStyle.RING, SystemLook.TILES.focoPadrao)
        assertEquals(FocusStyle.LINE, SystemLook.COZY.focoPadrao)
        assertTrue(SystemLook.COZY.iconeColorido)
    }

    private fun hsl(c: Int): Pair<Float, Float> {
        val r = (c shr 16 and 255) / 255f; val g = (c shr 8 and 255) / 255f; val b = (c and 255) / 255f
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
        val l = (mx + mn) / 2
        val s = if (d == 0f) 0f else d / (1 - Math.abs(2 * l - 1))
        val h = when {
            d == 0f -> 0f
            mx == r -> 60 * (((g - b) / d) % 6)
            mx == g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
        return ((h + 360) % 360) to s
    }

    private fun lum(c: Int): Double {
        fun f(x: Int): Double { val v = x / 255.0; return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
        return 0.2126 * f(c shr 16 and 255) + 0.7152 * f(c shr 8 and 255) + 0.0722 * f(c and 255)
    }

    @Test fun paletaSemRosaSemLaranjaDaMarcaEComLeitura() {
        val caixa = lum(0xFF2C2925.toInt())
        for (c in IconPalette.TODAS) {
            val (h, s) = hsl(c)
            val nome = Integer.toHexString(c)
            assertTrue("$nome é rosa/magenta (matiz $h)", s < 0.25f || h !in 285f..350f)
            assertTrue("$nome parece o laranja da marca (matiz $h)", s < 0.5f || h !in 10f..32f)
            assertTrue("$nome some na caixa grafite", (lum(c) + 0.05) / (caixa + 0.05) >= 4.5)
        }
    }

    @Test fun iconeNovoGanhaSempreAMesmaCor() {
        assertEquals(IconPalette.CEU, IconPalette.cor("wifi"))
        assertEquals(IconPalette.cor("um-icone-novo"), IconPalette.cor("UM-ICONE-NOVO"))
        assertTrue(IconPalette.cor("um-icone-novo") in IconPalette.TODAS)
    }

    /** Painel web: na coluna da navegação (17 primeiros) duas vizinhas nunca repetem; os do mesmo papel da TV, a mesma cor. */
    @Test fun painelComCorPorIcone() {
        val nav = IconPalette.ICONES_PAINEL.take(17).map { IconPalette.corDoPainel(it) }
        nav.zipWithNext().forEachIndexed { i, (a, b) -> assertTrue("vizinhas $i iguais", a != b) }
        for (id in listOf("tv", "config", "rede", "familia", "energia", "casa")) assertEquals(id, IconPalette.cor(id), IconPalette.corDoPainel(id))
    }
}
