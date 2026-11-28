package br.paulo.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A marca é Sofaly (27/11/2026, Paulo: "por que AINDA está com o nome de one-tv?"). Este teste falha se o nome antigo
 * voltar em algo que a pessoa vê, ou se um pacote público ou um arquivo do Release levar o nome do Paulo / "one-tv".
 *
 * Fica de fora de propósito, até o Paulo decidir: as pastas "OneTV-backup" e "OneTV-gravacoes" (trocar o nome deixaria
 * as cópias já feitas de fora da restauração) e o nome "onetv-<TV>" das TVs que já estavam no Tailscale (TV nova nasce "sofaly-"; trocar muda o endereço
 * público e o conector MCP, então é feito por adb no horário combinado: EXTRAS --es o tailscale_prefixo). Nomes internos (pacote do código br.paulo.tvlauncher, ids de UPnP/MTP,
 * travas de energia) não aparecem para ninguém e não entram aqui.
 */
class BrandNamesTest {
    private val raiz = listOf(File(".."), File(".")).first { File(it, ".github/workflows/release.yml").isFile }

    /** "One TV", "one-tv" e "OneTV" (menos as duas pastas de cópia em espera da decisão). */
    private val proibido = Regex("""One ?TV(?!-backup|-gravacoes)|one-tv""")

    private fun arquivos(dir: String, vararg ext: String): Sequence<File> =
        File(raiz, dir).walkTopDown().filter { it.isFile && ext.any { e -> it.name.endsWith(e) } && "node_modules" !in it.path }

    private fun visiveis(): Sequence<File> =
        arquivos("app/src/main/res", ".xml").filter { it.parentFile.name.startsWith("values") } +
            arquivos("celular/src/main/res", ".xml").filter { it.parentFile.name.startsWith("values") } +
            arquivos("app/src/main/assets/painel", ".html", ".js", ".css") +
            arquivos("site", ".html", ".json", ".js") +
            arquivos("website", ".html", ".json") +
            sequenceOf(File(raiz, ".github/release-notes.md"), File(raiz, "README.md"), File(raiz, "docs/launch/README.md")).filter { it.isFile }

    @Test fun nomeAntigoNaoAparece() {
        val achados = visiveis().flatMap { f ->
            f.readLines().asSequence().mapIndexedNotNull { i, l ->
                proibido.find(l)?.let { "${f.relativeTo(raiz)}:${i + 1}: ${it.value}" }
            }
        }.toList()
        assertTrue("Nome antigo no que a pessoa vê (a marca é Sofaly):\n" + achados.take(30).joinToString("\n"), achados.isEmpty())
        assertTrue("o teste precisa achar os arquivos", visiveis().count() > 50)
    }

    @Test fun pacotesPublicosSemONomeDoPaulo() {
        val app = File(raiz, "app/build.gradle.kts").readText()
        val linha = app.lines().first { "applicationId =" in it && "defaultConfig." !in it }
        // Só a ponte (-Plegacy, para TVs ainda no pacote antigo) pode ser br.paulo.tvlauncher.
        assertTrue(linha, Regex("""applicationId = if \(project\.hasProperty\("legacy"\)\) "br\.paulo\.tvlauncher" else "tv\.sofaly\.launcher"""").containsMatchIn(linha))
        val todos = Regex("""applicationId = "([^"]+)"""").findAll(app + File(raiz, "celular/build.gradle.kts").readText()).map { it.groupValues[1] }.toList()
        assertTrue(todos.toString(), todos.isNotEmpty() && todos.all { it.startsWith("tv.sofaly.") })
    }

    @Test fun arquivosDoReleaseComONomeSofaly() {
        val yml = File(raiz, ".github/workflows/release.yml").readText()
        val nomes = Regex("""publicar/([A-Za-z0-9._-]+?)\.apk\b""").findAll(yml).map { it.groupValues[1] }.toSet()
        // Exceções com prazo: a ponte para TVs no pacote antigo (o Atualizador delas só conhece esse nome) e a cópia do
        // app do celular com o nome que os painéis até a v475825 ainda pedem.
        val transicao = setOf("one-tv-launcher", "one-tv-launcher-arm64", "one-tv-celular")
        val ruins = nomes.filter { !it.startsWith("sofaly-") && it !in transicao && !it.startsWith("app-") && it != "celular-release" && !it.startsWith("\$") }
        assertEquals("arquivo do Release fora do padrão sofaly-*.apk", emptyList<String>(), ruins)
        assertTrue("o app do celular sai como sofaly-phone.apk", "sofaly-phone" in nomes)
        val titulo = Regex("""--title "([^"]+)"""").find(yml)?.groupValues?.get(1) ?: ""
        assertTrue(titulo, titulo.startsWith("Sofaly ") && proibido.find(titulo) == null)
        assertTrue("o painel baixa o app do celular pelo nome novo",
            "latest/download/sofaly-phone.apk" in File(raiz, "app/src/main/assets/painel/index.html").readText())
    }
}
