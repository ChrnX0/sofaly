package br.paulo.tvlauncher

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Textos do app que faltam em alguma língua (issue da caçada de 27/11/2026). Sem tradução, o Android mostra o inglês
 * no meio da tela em japonês, alemão... O português tem de estar sempre completo. As outras 26 línguas já têm um
 * atraso conhecido (o teto em test/resources/traducoes-faltando.txt): este teste impede que ele cresça. Quem traduz
 * baixa o teto do arquivo junto (o teste avisa quando dá para baixar).
 */
class TraducoesFaltandoTest {
    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
    private val tetoArq = listOf(File("src/test/resources/traducoes-faltando.txt"), File("app/src/test/resources/traducoes-faltando.txt")).first { it.isFile }

    /** Nomes traduzíveis (string, string-array, plurals) de uma pasta values*. */
    private fun nomes(pasta: File): Set<String> {
        val f = DocumentBuilderFactory.newInstance()
        return pasta.listFiles { x -> x.name.endsWith(".xml") }.orEmpty().flatMap { arq ->
            val raiz = f.newDocumentBuilder().parse(arq).documentElement
            val filhos = raiz.childNodes
            (0 until filhos.length).mapNotNull { filhos.item(it) as? Element }
                .filter { it.tagName in setOf("string", "string-array", "plurals") && it.getAttribute("translatable") != "false" }
                .map { it.getAttribute("name") }
        }.toSet()
    }

    @Test fun nenhumaLinguaPerdeMaisTextos() {
        val en = nomes(File(res, "values"))
        val teto = tetoArq.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim().toInt() }
        val linguas = res.listFiles()!!.filter { it.name.startsWith("values-") && File(it, "strings.xml").exists() }
        assertTrue(linguas.size >= 26)
        val podeBaixar = mutableListOf<String>()
        for (d in linguas) {
            val faltam = (en - nomes(d)).sorted()
            val max = teto[d.name] ?: 0
            assertTrue("${d.name}: faltam ${faltam.size} textos (teto $max). Novos sem tradução: " +
                faltam.take(15).joinToString(), faltam.size <= max)
            if (faltam.size < max) podeBaixar += "${d.name}=${faltam.size}"
        }
        if (podeBaixar.isNotEmpty()) println("TraducoesFaltandoTest: dá para baixar o teto: ${podeBaixar.joinToString(" ")}")
        assertEquals("o português nunca fica para trás", 0, teto["values-pt"] ?: 0)
    }
}
