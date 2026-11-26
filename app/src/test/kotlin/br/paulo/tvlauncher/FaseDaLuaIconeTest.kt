package br.paulo.tvlauncher

import br.paulo.tvlauncher.weather.IconesClima
import br.paulo.tvlauncher.weather.Lua
import org.junit.Assert.assertEquals
import org.junit.Test

/** Brainstorm item 20 (26/11/2026): noite limpa mostra a lua de hoje em toda tela; os outros ícones passam iguais. */
class FaseDaLuaIconeTest {
    @Test fun noiteLimpaViraAFaseDeHoje() {
        val agora = 1_795_000_000_000L
        val noite = IconesClima.chave(0, dia = false)
        assertEquals(Lua.chaveIcone(agora, -23.5), IconesClima.comFaseDaLua(noite, -23.5, agora))
    }

    @Test fun outrosIconesNaoMudam() {
        val dia = IconesClima.chave(0, dia = true)
        val chuva = IconesClima.chave(61, dia = false)
        assertEquals(dia, IconesClima.comFaseDaLua(dia, -23.5))
        assertEquals(chuva, IconesClima.comFaseDaLua(chuva, -23.5))
    }
}
