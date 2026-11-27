package br.paulo.tvlauncher.observer

import br.paulo.tvlauncher.CodeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * O texto que dá o consentimento tem de prometer o que sai de fato (caça 27/11, mesmo tipo do #541). O interruptor
 * "Ajudar outras casas" do painel liga as fichas ([SofyCommunityTv.todayBody]): pacote + versão + tipo
 * ([SofyShare.KINDS]: travou ou ficou lento) e as regras de conferir telas que já valem ([SofyRules.communityRules]).
 * O texto dizia só "os apps que travaram". Mudou o que vai? Mude este teste e os textos juntos.
 */
class HelpHomesConsentTextTest {
    private val main = listOf(File("src/main"), File("app/src/main")).first { File(it, "assets/painel/index.html").isFile }
    private val painel = File(main, "assets/painel")
    private val res = File(main, "res")

    /** O parágrafo logo depois do interruptor no index.html. */
    private fun textoDoPainel(): String {
        val html = File(painel, "index.html").readText()
        val depois = html.substringAfter("id=\"sofyAjudarCasas\"")
        return Regex("<p class=\"aviso-curto\">([^<]*)</p>").find(depois)!!.groupValues[1]
    }

    @Test fun `os tipos que vao sao os que o texto cita`() {
        val fonte = CodeSource.file("SofyShare.kt").readText()
        assertTrue("SofyShare.KINDS mudou: reveja os textos", "val KINDS = setOf(\"crashy\", \"slower\")" in fonte)
    }

    @Test fun `painel promete travaram, ficaram lentos e as regras de conferir telas`() {
        val t = textoDoPainel()
        assertTrue(t, "apps que travaram ou ficaram lentos" in t)
        assertTrue(t, "regras que o Sofaly usa para conferir as telas" in t)
    }

    @Test fun `o texto novo do painel esta traduzido nos 26 idiomas`() {
        val chave = "\"" + textoDoPainel() + "\":"
        val arquivos = painel.listFiles { f -> f.name.matches(Regex("i18n-[a-z]{2}\\.js")) }!!
        assertEquals(26, arquivos.size)
        val faltam = arquivos.filter { f -> f.readLines().none { it.startsWith(chave) } }.map { it.name }
        assertEquals(emptyList<String>(), faltam)
    }

    @Test fun `tela da TV tambem lista os lentos`() {
        for ((pasta, trecho) in listOf("values" to "got slow", "values-pt" to "ficaram lentos")) {
            val xml = File(res, "$pasta/strings_help_homes.xml").readText()
            assertTrue("$pasta: hoh_goes_list sem \"$trecho\"", trecho in xml.substringAfter("name=\"hoh_goes_list\"").substringBefore("</string-array>"))
        }
    }
}
