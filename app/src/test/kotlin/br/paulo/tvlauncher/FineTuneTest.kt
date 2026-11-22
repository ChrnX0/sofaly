package br.paulo.tvlauncher

import br.paulo.tvlauncher.settings.ClockStyle
import br.paulo.tvlauncher.settings.FontChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ajuste fino (21/11/2026): fonte baixada só se for fonte de verdade; formato da hora. */
class FineTuneTest {
    @Test fun cabecalhoDeFonte() {
        assertTrue(FontChoice.looksLikeFont(byteArrayOf(0, 1, 0, 0, 9)))
        assertTrue(FontChoice.looksLikeFont("OTTO....".toByteArray()))
        assertTrue(FontChoice.looksLikeFont("true....".toByteArray()))
        assertFalse(FontChoice.looksLikeFont("<!DOCTYPE html>".toByteArray()))   // página de erro nunca vira fonte
        assertFalse(FontChoice.looksLikeFont(byteArrayOf(0, 1)))
    }

    @Test fun listaSemRepetidosEComRobotoPrimeiro() {
        assertEquals(FontChoice.DEFAULT, FontChoice.FONTS.first().id)
        assertEquals("roboto", FontChoice.DEFAULT)   // régua de estilo 22/11/2026: a do sistema, sem baixar
        assertEquals(FontChoice.NUNITO, FontChoice.FONTS[1].id)
        assertEquals(FontChoice.FONTS.size, FontChoice.FONTS.map { it.id }.toSet().size)
        assertTrue(FontChoice.FONTS.drop(2).all { it.path!!.startsWith("ofl/") && it.path!!.endsWith(".ttf") })
        assertEquals(FontChoice.FONTS[0], FontChoice.byId("nao-existe"))
    }

    @Test fun pesos() {
        // Régua 22/11/2026: sem negrito; o antigo negrito vira médio, o resto fica leve.
        assertEquals(200, FontChoice.weight(200))
        assertEquals(300, FontChoice.weight(300))
        assertEquals(400, FontChoice.weight(400))
        assertEquals(400, FontChoice.weight(500))
        assertEquals(500, FontChoice.weight(700))
        assertEquals(500, FontChoice.weight(800))
    }

    @Test fun focoPadraoEFaixa() {
        assertEquals(br.paulo.tvlauncher.settings.FocusStyle.LINE, br.paulo.tvlauncher.settings.FocusStyle.deNumero(0))
        assertEquals(br.paulo.tvlauncher.settings.FocusStyle.BOX, br.paulo.tvlauncher.settings.FocusStyle.deNumero(2))
        assertEquals(br.paulo.tvlauncher.settings.FocusStyle.LINE, br.paulo.tvlauncher.settings.FocusStyle.deNumero(9))
    }

    @Test fun reguaSemLetraPequenaNemMaiuscula() {
        for (p in Estilo.Papel.entries) {
            assertTrue("${p.name} ${p.sp} sp", p.sp >= 18f)
            assertFalse(p.name, p.caixaAlta)
            assertTrue(p.name, p.peso <= 400)
        }
    }

    @Test fun formatoDaHora() {
        assertEquals("HH:mm", ClockStyle.pattern(ClockStyle.Format.AUTO, true))
        assertEquals("h:mm", ClockStyle.pattern(ClockStyle.Format.AUTO, false))
        assertEquals("HH:mm", ClockStyle.pattern(ClockStyle.Format.H24, false))
        assertEquals("h:mm a", ClockStyle.pattern(ClockStyle.Format.H12_AMPM, true))
        assertEquals("HH:mm:ss", ClockStyle.pattern(ClockStyle.Format.SECONDS, true))
    }
}
