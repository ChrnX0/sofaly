package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.EscritaAlta
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscritaAltaTest {
    @Test fun latimCirilicoGregoNao() { for (t in listOf("1 de janeiro", "1 января", "1 Ιανουαρίου", "Øst 12:00", "")) assertFalse(t, EscritaAlta.tem(t)) }
    @Test fun cjkArabeHindiSim() { for (t in listOf("1月1日", "1월 1일", "١ يناير", "1 जनवरी", "1 มกราคม")) assertTrue(t, EscritaAlta.tem(t)) }
}
