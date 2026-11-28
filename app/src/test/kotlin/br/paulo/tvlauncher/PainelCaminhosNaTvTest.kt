package br.paulo.tvlauncher

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Caça 27/11/2026: o painel mandava a pessoa às "Configurações do launcher" (palavra que ninguém vê na TV) e a caminhos
 * que mudaram com as portas das Configurações ("Conta", "Sistema › Confirmações", "Sistema › Shell"). Cada texto do
 * painel que ensina um caminho na TV tem de usar os nomes que a TV mostra NA MESMA LÍNGUA (res/values-<l>), em pt e
 * nas 26 traduções. Monta o caminho com os textos da própria TV, como o CaminhoNaTvTest do celular.
 */
class PainelCaminhosNaTvTest {
    private val LINGUAS = listOf("en", "es", "fr", "de", "it", "pl", "ru", "tr", "ar", "hi", "id", "ja", "ko", "zh",
        "nl", "sv", "da", "nb", "fi", "cs", "el", "hu", "ro", "uk", "vi", "th")
    private val raiz = listOf(File("."), File("..")).first { File(it, "app/src/main/assets/painel").isDirectory }
    private val painel = File(raiz, "app/src/main/assets/painel")

    private fun pastaTv(l: String) = when (l) { "pt" -> "values-pt"; "en" -> "values"; "id" -> "values-in"; "zh" -> "values-zh-rCN"; else -> "values-$l" }

    private fun textosTv(l: String): Map<String, String> {
        val f = DocumentBuilderFactory.newInstance()
        return File(raiz, "app/src/main/res/${pastaTv(l)}").listFiles { a -> a.name.endsWith(".xml") }.orEmpty().flatMap { arq ->
            val doc = runCatching { f.newDocumentBuilder().parse(arq) }.getOrNull() ?: return@flatMap emptyList()
            val nos = doc.getElementsByTagName("string")
            (0 until nos.length).map { nos.item(it) }.map { it.attributes.getNamedItem("name").nodeValue to it.textContent.replace("\\'", "'") }
        }.toMap()
    }

    /** Os caminhos que o painel ensina, com os nomes da TV na língua [l]. */
    private fun caminhos(l: String): Map<String, String> {
        val t = textosTv(l)
        fun c(vararg k: String) = k.joinToString(" › ") { t[it] ?: error("a TV não tem $it em ${pastaTv(l)}") }
        val shell = (t["cfg_sec_shizuku"] ?: error("sem cfg_sec_shizuku")).replace(Regex("""\s*[（(][^（()）]*[)）]\s*$"""), "")
        return mapOf(
            "remoto" to c("cfg_titulo", "cfg3_porta_casa_rede", "cl_aba_remoto"),
            "foto" to c("cfg_titulo", "cl_aba_conta", "fp_linha"),
            "confirmacoes" to c("cfg_titulo", "cl_aba_sistema", "cfg2_aba_avancado", "cl_sec_confirmacoes"),
            "shell" to c("cfg_titulo", "cl_aba_sistema", "cfg2_aba_avancado") + " › " + shell,
            "ajudar" to c("cfg_titulo", "cl_aba_sistema", "smem_titulo", "hoh_title_short"),
        )
    }

    /** As frases do painel (chave em português) e o caminho que cada uma ensina. */
    private val frases: Map<String, String> by lazy {
        val pt = caminhos("pt")
        mapOf(
            "Digite o PIN do painel. Ele é definido na TV, em ${pt["remoto"]}." to "remoto",
            "Não consegui falar com a TV. Confira se ela está ligada e se o painel está ligado na TV, em ${pt["remoto"]}." to "remoto",
            "Para usar a foto da conta Google, abra na TV: ${pt["foto"]}." to "foto",
            "painel desligado. Religue na TV, em ${pt["remoto"]}." to "remoto",
            "PIN errado demais: o painel foi desligado. Religue na TV, em ${pt["remoto"]}." to "remoto",
            "bloqueado na TV (${pt["confirmacoes"]})" to "confirmacoes",
            "o shell do launcher não está ligado (${pt["shell"]})" to "shell",
            "Na própria TV, também foi aceito contar como foram os consertos: o modelo da TV, o que quebrou e o que consertou, " +
                "sem dizer de qual casa. Tudo o que vai hoje aparece na TV, em ${pt["ajudar"]}." to "ajudar",
        )
    }

    private val fontes = listOf("app/src/main/assets/painel/index.html", "app/src/main/assets/painel/app.js",
        "app/src/main/kotlin/br/paulo/tvlauncher/panel/PanelLive.kt", "app/src/main/kotlin/br/paulo/tvlauncher/mcp/PedidoIa.kt",
        "app/src/main/kotlin/br/paulo/tvlauncher/PainelApps.kt", "app/src/main/kotlin/br/paulo/tvlauncher/mcp/McpTextos.kt")

    @Test fun asFrasesEstaoNoPainelComOCaminhoDaTv() {
        val tudo = fontes.joinToString("\n") { File(raiz, it).readText() }.replace(Regex("""\s+"""), " ")
        for (f in frases.keys) assertTrue("o painel não mostra: $f", f.replace(Regex("""\s+"""), " ") in tudo)
    }

    @Test fun nenhumTextoMandaParaAsConfiguracoesDoLauncher() {
        for (arq in fontes) {
            val linhas = File(raiz, arq).readLines().filter { "Configurações do launcher" in it && !it.trimStart().startsWith("//") && !it.trimStart().startsWith("*") }
            assertEquals("$arq ainda manda a pessoa às \"Configurações do launcher\"", emptyList<String>(), linhas)
        }
    }

    @Test fun cadaLinguaUsaOsNomesDaTvNaMesmaLingua() {
        for (l in LINGUAS) {
            val linhas = File(painel, "i18n-$l.js").readLines()
            val cam = caminhos(l)
            for ((frase, qual) in frases) {
                val linha = linhas.singleOrNull { it.startsWith("\"$frase\":") } ?: error("i18n-$l.js sem a frase: $frase")
                val traducao = linha.substringAfter("\":")
                assertTrue("i18n-$l.js: \"$frase\" não traz o caminho da TV em $l (${cam[qual]}): $traducao", cam.getValue(qual) in traducao)
            }
        }
    }

    /** Caminhos que mudaram com as portas e não podem voltar em nenhum texto (painel, servidor, ferramentas da IA). */
    @Test fun nenhumCaminhoVelhoNasFontes() {
        val velhos = listOf("Configurações › Sistema › Confirmações", "Configurações › Sistema › Shell)", "Configurações do launcher >")
        val achados = listOf(File(raiz, "app/src/main/kotlin"), painel).flatMap { d -> d.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "js", "html") && !it.name.startsWith("i18n-") }.toList() }
            .flatMap { f -> f.readLines().withIndex().filter { (_, l) -> velhos.any { it in l } && !l.trimStart().startsWith("//") && !l.trimStart().startsWith("*") }.map { "${f.name}:${it.index + 1}" } }
        assertEquals("caminho velho na TV", emptyList<String>(), achados)
    }
}
