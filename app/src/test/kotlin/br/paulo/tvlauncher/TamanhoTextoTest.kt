package br.paulo.tvlauncher

import br.paulo.tvlauncher.settings.TamanhoTexto
import org.junit.Assert.assertEquals
import org.junit.Test

class TamanhoTextoTest {
    @Test fun ciclaEDaAVolta() {
        assertEquals(110, TamanhoTexto.proximo(100))
        assertEquals(120, TamanhoTexto.proximo(110))
        assertEquals(80, TamanhoTexto.proximo(120))   // dá a volta pela letra menor (26/11/2026)
        assertEquals(90, TamanhoTexto.proximo(80))
        assertEquals(100, TamanhoTexto.proximo(90))
    }

    @Test fun valorEstranhoVoltaAoNormal() {
        assertEquals(100, TamanhoTexto.valido(250))
        assertEquals(100, TamanhoTexto.proximo(37))   // fora da lista: o próximo é o normal
        assertEquals(120, TamanhoTexto.valido(120))
    }

    /** Teto de 120%: acima disso o texto não cabe nos blocos de altura fixa (painel rápido, pôsteres). */
    @Test fun maximoEh120() = assertEquals(120, TamanhoTexto.OPCOES.max())
}
