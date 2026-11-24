package br.paulo.tvlauncher

import br.paulo.tvlauncher.network.InternetEmUmaFrase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O diagnóstico "Em uma frase" na TV usa as strings ief_* do idioma do aparelho (DiagnosticoInternet.textos). Cada chave
 * de InternetEmUmaFrase.PT_TEXTOS tem de existir em todos os idiomas com os mesmos marcadores (%1$s, %2$d...): sem a
 * string, a tela quebra; com marcador trocado, o texto sai com a hora no lugar da duração.
 */
class InternetEmUmaFraseIdiomasTest {
    private val res = listOf("src/main/res", "app/src/main/res").map { File(it) }.first { it.exists() }
    private val marcador = Regex("""%(\d)\$(\.\d)?[sdf]""")

    private fun ief(pasta: File): Map<String, String> =
        Regex("""<string name="ief_(\w+)"[^>]*>(.*?)</string>""").findAll(File(pasta, "strings.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    @Test fun todasAsChavesEmTodosOsIdiomasComOsMesmosMarcadores() {
        val pastas = res.listFiles()!!.filter { it.name == "values" || (it.name.startsWith("values-") && File(it, "strings.xml").exists() && File(it, "strings.xml").readText().contains("<string name=\"ief_")) }
        assertTrue("esperava ao menos 16 idiomas", pastas.size >= 16)
        val chaves = InternetEmUmaFrase.PT_TEXTOS.keys
        for (p in pastas) {
            val m = ief(p)
            assertEquals("chaves em ${p.name}", chaves, m.keys)
            for (k in chaves) assertEquals("marcadores de $k em ${p.name}",
                marcador.findAll(InternetEmUmaFrase.PT_TEXTOS.getValue(k)).map { it.value }.sorted().toList(),
                marcador.findAll(m.getValue(k)).map { it.value }.sorted().toList())
        }
    }

    @Test fun textosPadraoFormatamSemErro() {
        val tx = InternetEmUmaFrase.PT
        assertEquals("a página de teste levou 2,5 s para abrir", tx.t("lenta_http", 2.5))
        assertEquals("1 h 20 min", InternetEmUmaFrase.duracao(80 * 60_000L))
    }
}
