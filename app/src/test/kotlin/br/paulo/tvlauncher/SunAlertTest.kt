package br.paulo.tvlauncher

import br.paulo.tvlauncher.weather.SunAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SunAlertTest {
    private val horas = listOf("2026-11-24T11:00", "2026-11-24T12:00", "2026-11-24T13:00", "2026-11-24T14:00")
    private val uvs = listOf(6.5, 7.6, 9.4, 8.1)

    @Test fun avisaComUvMuitoAltoAgoraOuNaProximaHora() {
        assertEquals(9, SunAlert.uvParaAviso(horas, uvs, "2026-11-24T12", codigo = 1, chovendo = false, eDia = true))   // 13h vem 9,4
        assertEquals(9, SunAlert.uvParaAviso(horas, uvs, "2026-11-24T13", 0, false, true))
        assertNull(SunAlert.uvParaAviso(horas, uvs, "2026-11-24T11", 0, false, true))   // 6,5 e 7,6: ainda não
    }

    @Test fun ceuFechadoChuvaOuNoiteNaoAvisam() {
        assertNull(SunAlert.uvParaAviso(horas, uvs, "2026-11-24T13", codigo = 3, chovendo = false, eDia = true))
        assertNull(SunAlert.uvParaAviso(horas, uvs, "2026-11-24T13", 1, chovendo = true, eDia = true))
        assertNull(SunAlert.uvParaAviso(horas, uvs, "2026-11-24T13", 1, false, eDia = false))
    }

    @Test fun semAHoraDeAgoraNaoInventa() {
        assertNull(SunAlert.uvParaAviso(horas, uvs, "2026-11-25T13", 0, false, true))
        assertNull(SunAlert.uvParaAviso(horas, emptyList(), "2026-11-24T13", 0, false, true))
    }
}
