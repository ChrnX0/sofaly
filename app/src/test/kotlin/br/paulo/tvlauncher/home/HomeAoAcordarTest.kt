package br.paulo.tvlauncher.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAoAcordarTest {
    private val seletor = HomeAoAcordar.SELETORES.first()

    @Test fun seletorLogoAoAcordarSemTeclaViraHome() =
        assertTrue(HomeAoAcordar.desviar(seletor, agora = 105_000, acordouEm = 100_000, teclaEm = 0))

    @Test fun teclaQueAcordouATvNaoConta() =
        assertTrue(HomeAoAcordar.desviar(seletor, agora = 105_000, acordouEm = 100_000, teclaEm = 100_800))

    @Test fun pessoaJaMexeuNoControleFicaOSeletor() =
        assertFalse(HomeAoAcordar.desviar(seletor, agora = 105_000, acordouEm = 100_000, teclaEm = 103_000))

    @Test fun depoisDaJanelaFicaOSeletor() =
        assertFalse(HomeAoAcordar.desviar(seletor, agora = 100_000 + HomeAoAcordar.JANELA_MS + 1, acordouEm = 100_000, teclaEm = 0))

    @Test fun telaApagadaNaoDesvia() =
        assertFalse(HomeAoAcordar.desviar(seletor, agora = 105_000, acordouEm = Long.MAX_VALUE, teclaEm = 0))

    @Test fun outraTelaDoGoogleNaoDesvia() =
        assertFalse(HomeAoAcordar.desviar("com.google.android.apps.tv.launcherx.gemini.chat.ChatActivity", 105_000, 100_000, 0))
}
