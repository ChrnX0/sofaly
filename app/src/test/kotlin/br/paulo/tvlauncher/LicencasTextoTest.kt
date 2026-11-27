package br.paulo.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Licenças em duas línguas (auditoria de telas, 27/11/2026): licencas.txt (português) e licencas-en.txt (as outras
 * línguas) precisam citar os mesmos endereços, para ninguém esquecer de mexer nos dois. Confere também que as marcas
 * que a build da Play usa para trocar a seção de dados existem em cada arquivo.
 */
class LicencasTextoTest {
    private val url = Regex("""https?://[^\s,;)]+""")
    private fun urls(f: String) = url.findAll(File(f).readText()).map { it.value }.toSortedSet()

    @Test
    fun mesmosEnderecosNasDuasLinguas() {
        assertEquals(urls("src/main/assets/licencas.txt"), urls("src/main/assets/licencas-en.txt"))
        assertEquals(urls("src/play/assets/licencas-dados-play.txt"), urls("src/play/assets/licencas-dados-play-en.txt"))
    }

    @Test
    fun marcasDaTrocaDaPlayExistem() {
        val pt = File("src/main/assets/licencas.txt").readText()
        val en = File("src/main/assets/licencas-en.txt").readText()
        assertTrue(pt.contains("— Dados —") && pt.indexOf("Os sons do despertador") > pt.indexOf("— Dados —"))
        assertTrue(en.contains("— Data —") && en.indexOf("The alarm sounds") > en.indexOf("— Data —"))
        assertTrue(File("src/play/assets/licencas-dados-play-en.txt").readText().startsWith("— Data —"))
    }
}
