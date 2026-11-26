package br.paulo.tvlauncher

import br.paulo.tvlauncher.alarm.NaoPerturbe
import br.paulo.tvlauncher.alarm.NoiteCasa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Noite da casa (Brainstorm 26/11/2026, item 12): um horário de noite para o Não perturbe e o filtro noturno. */
class NoiteCasaTest {
    @Test fun padraoEhOAntigoDoNaoPerturbe() {
        // Quem nunca mexeu continua com o mesmo 23h-7h de antes.
        assertEquals(23 * 60, NoiteCasa.PADRAO_INICIO)
        assertEquals(7 * 60, NoiteCasa.PADRAO_FIM)
        assertEquals(NaoPerturbe.Motivo.MADRUGADA, NaoPerturbe.motivo(true, false, 23 * 60 + 30, false))
        assertNull(NaoPerturbe.motivo(true, false, 22 * 60, false))
    }

    @Test fun naoPerturbeSegueANoiteEscolhida() {
        val ini = 21 * 60 + 30; val fim = 6 * 60
        assertEquals(NaoPerturbe.Motivo.MADRUGADA, NaoPerturbe.motivo(true, false, 22 * 60, false, ini, fim))
        assertNull(NaoPerturbe.motivo(true, false, 6 * 60 + 30, false, ini, fim))
        // Noite que não vira a meia-noite (quem dorme de dia): 8h-15h.
        assertEquals(NaoPerturbe.Motivo.MADRUGADA, NaoPerturbe.motivo(true, false, 10 * 60, false, 8 * 60, 15 * 60))
        // Início igual ao fim = sem noite (nunca "a noite toda").
        assertNull(NaoPerturbe.motivo(true, false, 3 * 60, false, 0, 0))
        // Filme e visita continuam vindo antes.
        assertEquals(NaoPerturbe.Motivo.FILME, NaoPerturbe.motivo(true, true, 22 * 60, false, ini, fim))
        assertNull(NaoPerturbe.motivo(false, false, 22 * 60, false, ini, fim))
    }

    @Test fun ajusteProprioDoFiltro() {
        assertNull(NoiteCasa.proprio(23 * 60, 23 * 60))           // igual à noite: volta a seguir
        assertEquals(22 * 60, NoiteCasa.proprio(22 * 60, 23 * 60))
    }

    @Test fun minutoValido() {
        assertEquals(0, NoiteCasa.minutoValido(0))
        assertEquals(1439, NoiteCasa.minutoValido(1439))
        assertNull(NoiteCasa.minutoValido(1440))
        assertNull(NoiteCasa.minutoValido(-1))
    }
}
