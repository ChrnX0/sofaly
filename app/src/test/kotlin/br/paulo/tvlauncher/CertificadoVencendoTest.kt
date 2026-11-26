package br.paulo.tvlauncher

import br.paulo.tvlauncher.panel.PainelTls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Brainstorm 26/11, item 18: o celular fica sabendo antes de o certificado do painel vencer. */
class CertificadoVencendoTest {
    private val dia = 86_400_000L
    private val agora = 1_800_000_000_000L

    @Test fun `conta os dias que faltam`() = assertEquals(59L, PainelTls.diasAteVencer(agora + 59 * dia + 1000, agora))

    @Test fun `longe do vencimento nao avisa`() = assertFalse(PainelTls.deveAvisar(61, 0L, agora))

    @Test fun `com 60 dias ou menos avisa uma vez por semana`() {
        assertTrue(PainelTls.deveAvisar(60, 0L, agora))
        assertFalse(PainelTls.deveAvisar(30, agora - 3 * dia, agora))
        assertTrue(PainelTls.deveAvisar(30, agora - 7 * dia, agora))
    }

    @Test fun `vencido nao avisa por aqui (a TV emite outro sozinha)`() = assertFalse(PainelTls.deveAvisar(-1, 0L, agora))
}
