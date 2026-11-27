package br.paulo.tvlauncher

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android apaga o espaço do começo e do fim de uma string de recurso que não está entre aspas. Foi assim que o diagnóstico
 * da internet mostrou "DNS respondem.Sem quedas" (27/11/2026: a revisão de telas da Sofy pegou na Sala, os 4 modelos).
 * Texto que precisa do espaço nas pontas vai entre aspas: <string name="x">" Sem quedas"</string>.
 */
class StringWhitespaceTest {
    private val res = listOf("src/main/res", "app/src/main/res").map { File(it) }.first { it.exists() }
    private val item = Regex("""<(string|item)(\s[^>]*)?>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)

    @Test fun spacesAtTheEdgesAreQuoted() {
        val ruins = res.listFiles()!!.filter { it.name.startsWith("values") }.flatMap { pasta ->
            pasta.listFiles()!!.filter { it.extension == "xml" }.flatMap { f ->
                item.findAll(f.readText()).map { it.groupValues[3] }
                    .filter { it.isNotEmpty() && !it.startsWith("\"") && (it.first().isWhitespace() || it.last().isWhitespace()) }
                    .map { "${pasta.name}/${f.name}: [$it]" }
                    .toList()
            }
        }
        assertTrue("espaço na ponta sem aspas (o Android apaga):\n" + ruins.take(20).joinToString("\n"), ruins.isEmpty())
    }
}
