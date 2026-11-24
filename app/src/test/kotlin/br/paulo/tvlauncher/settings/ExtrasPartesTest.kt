package br.paulo.tvlauncher.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** Rótulo das chaves de Extras dividido em título + explicação (teste 100%, 23/11/2026). */
class ExtrasPartesTest {
    @Test fun doisPontos() = assertEquals("Pular abertura (IntroDB)" to "Nos episódios, a TV pergunta",
        ExtrasTv.partes("Pular abertura (IntroDB): nos episódios, a TV pergunta"))

    @Test fun parentesesNoFim() = assertEquals("Atualização do sistema só de madrugada" to "3h às 5h",
        ExtrasTv.partes("Atualização do sistema só de madrugada (3h às 5h)"))

    @Test fun curtoFicaInteiro() = assertEquals("Modo leve" to "", ExtrasTv.partes("Modo leve"))

    @Test fun semCorte() = assertEquals("Pedir o PIN para mudar a HOME" to "", ExtrasTv.partes("Pedir o PIN para mudar a HOME"))

    @Test fun doisPontosDentroDosParenteses() = assertEquals(
        "Lembretes da casa no alto da tela inicial no dia" to "Ex.: dia do lixo reciclável, com aviso na hora marcada",
        ExtrasTv.partes("Lembretes da casa no alto da tela inicial no dia (ex.: dia do lixo reciclável), com aviso na hora marcada"))
}
