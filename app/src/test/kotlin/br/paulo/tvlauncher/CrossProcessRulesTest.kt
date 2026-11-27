package br.paulo.tvlauncher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regras do dono das preferências (CrossProcessPrefs.kt, 27/11/2026), lidas do código e do manifesto:
 *  - nenhum MODE_MULTI_PROCESS no app (obsoleto; dois processos gravando o mesmo arquivo perdiam gravações);
 *  - todo componente do ":player" pede as preferências pelo dono (senão volta a gravar direto no disco).
 */
class CrossProcessRulesTest {
    private val manifesto = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.isFile }.readText()

    @Test fun semModeMultiProcess() {
        val achados = CodeSource.all().filter { f ->
            f.readLines().any { l -> "MODE_MULTI_PROCESS" in l && !l.trimStart().startsWith("*") && !l.trimStart().startsWith("//") }
        }.map { it.name }.toList()
        assertTrue("MODE_MULTI_PROCESS voltou em $achados: use o dono (CrossProcessPrefs.kt)", achados.isEmpty())
    }

    @Test fun componentesDoPlayerPassamPeloDono() {
        val nomes = Regex("""<(activity|service|receiver)\b[^>]*?>""", RegexOption.DOT_MATCHES_ALL).findAll(manifesto)
            .map { it.value }.filter { "android:process=\":player\"" in it }
            .mapNotNull { Regex("""android:name="\.?([A-Za-z0-9_.]+)"""").find(it)?.groupValues?.get(1) }.toList()
        assertTrue("achou os componentes do :player (${nomes.size})", nomes.size >= 10)
        val sem = nomes.filterNot { nome ->
            val classe = nome.substringAfterLast('.')
            val f = CodeSource.all().firstOrNull { f -> Regex("""^(abstract |open )?class $classe\b""", RegexOption.MULTILINE).containsMatchIn(f.readText()) }
                ?: return@filterNot false
            val texto = f.readText()
            val corpo = texto.substring(Regex("""^(abstract |open )?class $classe\b""", RegexOption.MULTILINE).find(texto)!!.range.first)
                .let { c -> Regex("""^(abstract |open )?class \w""", RegexOption.MULTILINE).findAll(c).drop(1).firstOrNull()?.let { c.substring(0, it.range.first) } ?: c }
            "CrossProcessPrefs.wrap" in corpo || Regex(""":\s*CatalogActivity<""").containsMatchIn(corpo)
        }
        assertTrue("componentes do :player sem CrossProcessPrefs.wrap: $sem", sem.isEmpty())
        assertTrue("CatalogActivity passa pelo dono", "CrossProcessPrefs.wrap" in CodeSource.file("CatalogActivity.kt").readText())
        assertTrue("o provider mora no processo principal",
            Regex("""<provider android:name="\.PrefsProvider"[^>]*exported="false"""").containsMatchIn(manifesto) &&
                !Regex("""<provider android:name="\.PrefsProvider"[^>]*process""").containsMatchIn(manifesto))
    }
}
