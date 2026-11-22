package br.paulo.tvlauncher

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ícone repetido na cruz (22/11/2026, Sala: Painel rápido e Configurações com a mesma engrenagem). Os itens fixos de
 * cada mundo (Ferramentas "t:..." e os de Assistir/Ouvir/Jogar sem prefixo) não podem dividir o mesmo desenho.
 * Lê o código da HomeMotion: um item novo que copie o ícone de outro falha aqui.
 */
class IconesSemRepetirTest {
    @Test fun itensFixosTemIconesDiferentes() {
        val src = CodeSource.file("HomeMotion.kt").readText()
        val achados = Regex("""icCruz\("([a-z:]*[a-z])", IconeCruz\.Tipo\.(\w+)\)""").findAll(src)
            .map { it.groupValues[1] to it.groupValues[2] }
            .filter { (id, _) -> !id.startsWith("c:") && !id.startsWith("cat:") && !id.startsWith("plugin") }
            .toList()
        assertTrue("achei poucos itens (${achados.size}): o formato da HomeMotion mudou?", achados.size >= 10)
        for (grupo in listOf(achados.filter { it.first.startsWith("t:") }, achados.filter { ':' !in it.first })) {
            val repetidos = grupo.groupBy { it.second }.filterValues { it.size > 1 }
            assertTrue("ícone repetido: $repetidos", repetidos.isEmpty())
        }
    }
}
