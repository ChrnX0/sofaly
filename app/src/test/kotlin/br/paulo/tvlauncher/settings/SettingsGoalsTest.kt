package br.paulo.tvlauncher.settings

import br.paulo.tvlauncher.home.StripBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Onda D (25/11/2026): cada objetivo guarda a foto do que muda e o desfazer devolve igual (chave nova volta a não existir). */
class SettingsGoalsTest {
    @Test fun desfazerDevolveAFoto() {
        val antes = mapOf(TamanhoTexto.CHAVE to 110)
        val foto = SettingsGoals.Rules.foto(SettingsGoals.Goal.READ) { antes[it] }
        val volta = SettingsGoals.Rules.desfazer(foto)
        assertEquals(SettingsGoals.Goal.READ.valores.keys, volta.keys)
        assertEquals(110, volta[TamanhoTexto.CHAVE])
        assertEquals(null, volta[StripBackground.KEY])
    }

    @Test fun lerMelhorAumentaALetra() = assertEquals(120, SettingsGoals.Goal.READ.valores[TamanhoTexto.CHAVE])

    @Test fun fotoIlegivelNaoMexeEmNada() {
        assertTrue(SettingsGoals.Rules.desfazer(null).isEmpty())
        assertTrue(SettingsGoals.Rules.desfazer("{x").isEmpty())
    }

    @Test fun criancasNaoMexeAquiAbreAReceita() = assertTrue(SettingsGoals.Goal.KIDS.valores.isEmpty())
}
