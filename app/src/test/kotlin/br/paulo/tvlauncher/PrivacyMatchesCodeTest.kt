package br.paulo.tvlauncher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A política de privacidade (site/privacy.html) e a tela de consentimento da TV ("Ajudar outras casas") têm de dizer o
 * que o código faz. Em 27/11/2026 a tela dizia que "seus apps" nunca saem, mas as lições compartilhadas mandam o pacote e a
 * versão dos apps que travaram; e a política dizia "não há telemetria" sem ressalva. Este teste amarra os números e as
 * promessas aos valores do código (servidor e TV), para a próxima mudança de um lado quebrar aqui.
 */
class PrivacyMatchesCodeTest {
    private val repo = generateSequence(CodeSource.root.absoluteFile) { it.parentFile }.first { File(it, "server/api/src").isDirectory }
    private val privacy = File(repo, "site/privacy.html").readText()
    private fun js(name: String) = File(repo, "server/api/src/$name").readText()
    private fun const(src: String, name: String) =
        Regex("""export const $name = (\d+);""").find(src)?.groupValues?.get(1)?.toInt() ?: error("$name não achado")

    @Test fun numerosDaPoliticaIguaisAoServidor() {
        val community = js("community.js"); val fleet = js("fleet.js")
        val minHomes = const(community, "MIN_HOMES"); val window = const(community, "WINDOW_DAYS")
        val keep = const(fleet, "KEEP_DAYS"); val maxMin = const(fleet, "MAX_MIN_HOMES")
        for (t in listOf("after $minHomes different homes report it within $window days", "erased after those $window days",
                "Rows are erased after $keep days", "rising to $maxMin",
                "depois que $minHomes casas diferentes a mandam em $window dias", "apagadas depois de $keep dias", "subindo até $maxMin")) {
            assertTrue("site/privacy.html deveria dizer \"$t\"", t in privacy)
        }
    }

    @Test fun telemetriaSoComOSimDaPessoa() {
        assertTrue(privacy.contains("no analytics or telemetry unless you turn on “Help other homes”"))
        assertTrue(privacy.contains("nem telemetria, a não ser que você ligue “Ajudar outras casas”"))
        assertTrue("a política não pode dizer \"não há telemetria\" sem ressalva", !privacy.contains("There are no analytics or telemetry, and"))
    }

    /** As lições mandam "pkg": a tela do consentimento tem de pôr o nome dos apps em "O que vai", em cada língua. */
    @Test fun telaDoConsentimentoContaQueOsAppsVao() {
        assertTrue(CodeSource.file("SofyShare.kt").readText().contains(".put(\"pkg\", it.pkg)"))
        val res = File(CodeSource.root, "../../../../res").normalize()
        val pastas = res.listFiles { d -> d.name.startsWith("values") && File(d, "strings_help_homes.xml").isFile }.orEmpty()
        assertTrue(pastas.size >= 20)
        for (p in pastas) {
            val xml = File(p, "strings_help_homes.xml").readText()
            val vai = Regex("""name="hoh_goes_list">(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL).find(xml)!!.groupValues[1]
            assertTrue("${p.name}: \"O que vai\" precisa de 5 itens (com os apps que travaram)", Regex("<item>").findAll(vai).count() == 5)
        }
        val en = File(res, "values/strings_help_homes.xml").readText()
        assertTrue(en.contains("<item>Name and version of apps that crashed or got slow</item>"))
        assertTrue(!en.contains("<item>Your apps and what you type</item>"))
    }
}
