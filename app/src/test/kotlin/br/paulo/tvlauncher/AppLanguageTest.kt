package br.paulo.tvlauncher

import br.paulo.tvlauncher.settings.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Língua do app (AppLanguage.kt): cada opção da lista tem pasta de textos, cada pasta de textos tem opção, e o klingon
 * escondido só troca textos que existem em inglês.
 */
class AppLanguageTest {
    private val res = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    /** Pasta de recursos que o Android usa para a tag (id = "in" antigo; região vira -r). */
    private fun pasta(tag: String): String = when (tag) {
        "en" -> "values"
        "id" -> "values-in"
        "pt-BR" -> "values-pt"
        else -> "values-" + tag.replace("-", "-r")
    }

    @Test fun cadaOpcaoTemPastaECadaPastaTemOpcao() {
        val opcoes = AppLanguage.OPTIONS.map { pasta(it.tag) }.toSet()
        for (p in opcoes) assertTrue("$p não existe", File(res, "$p/strings.xml").isFile)
        val pastas = res.listFiles()!!.filter { it.name == "values" || it.name.startsWith("values-") && File(it, "strings.xml").isFile }
            .map { it.name }.toSet()
        assertEquals("línguas sem opção na lista", emptySet<String>(), pastas - opcoes)
        assertEquals(AppLanguage.OPTIONS.size, AppLanguage.OPTIONS.map { it.tag }.toSet().size)
    }

    @Test fun klingonSoTrocaTextosQueExistem() {
        val nomes = Regex("""<string name="([^"]+)"""")
        val en = File(res, "values").listFiles()!!.filter { it.name.endsWith(".xml") }
            .flatMap { f -> nomes.findAll(f.readText()).map { it.groupValues[1] }.toList() }.toSet()
        val tlh = File(res, "values-b+tlh/strings_tlh.xml").readText()
        val ids = nomes.findAll(tlh).map { it.groupValues[1] }.toList()
        assertTrue("o modo nerd tem textos", ids.size >= 40)
        assertEquals("textos do klingon sem par em inglês", emptySet<String>(), ids.toSet() - en)
        // Fora da conta de línguas (BuildConfig.IDIOMAS lê só strings.xml): o klingon é escondido.
        assertTrue(!File(res, "values-b+tlh/strings.xml").exists())
    }

    @Test fun aContaDeRepeticoesParaNoLimite() {
        assertEquals(1, AppLanguage.nextRepeat(0))
        assertEquals(AppLanguage.REPEATS_TO_UNLOCK, AppLanguage.nextRepeat(AppLanguage.REPEATS_TO_UNLOCK))
        assertTrue(AppLanguage.sameTag("pt-BR", "pt-br"))
    }
}
