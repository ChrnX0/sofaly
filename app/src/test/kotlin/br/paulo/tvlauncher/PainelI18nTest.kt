package br.paulo.tvlauncher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O painel em 26 línguas (14 em 17/11/2026, +12 em 24/11/2026): o i18n.js escolhe a língua do navegador e carrega i18n-<língua>.js.
 * Confere que as 14 têm as mesmas frases (texto novo esquecido numa língua aparece em português só nela),
 * que as frases montadas guardam as partes ($1, $2...) e que o servidor entrega cada arquivo (fora do TIPOS = 404 calado).
 */
class PainelI18nTest {
    private val LINGUAS = listOf("en", "es", "fr", "de", "it", "pl", "ru", "tr", "ar", "hi", "id", "ja", "ko", "zh",
        "nl", "sv", "da", "nb", "fi", "cs", "el", "hu", "ro", "uk", "vi", "th")

    private fun pasta(): File = listOf("src/main/assets/painel", "app/src/main/assets/painel").map(::File).first { it.isDirectory }

    /** Chaves (texto em português, como literal JSON) e trocas das frases montadas de um arquivo de língua. */
    private fun ler(l: String): Pair<List<String>, List<String>> {
        val f = File(pasta(), "i18n-$l.js")
        assertTrue("falta $f", f.isFile)
        val linhas = f.readLines()
        val meio = linhas.indexOf("},P:[")
        assertTrue("i18n-$l.js sem a parte P", meio > 0)
        val chave = Regex("""^("(?:[^"\\]|\\.)*"):""")
        val t = linhas.subList(0, meio).mapNotNull { chave.find(it)?.groupValues?.get(1) }
        // Com ou sem espaço depois da vírgula: 7 linhas do en/de/... têm o espaço e ficavam fora da conta (24/11/2026).
        val troca = Regex("""^\["(?:[^"\\]|\\.)*",\s*"((?:[^"\\]|\\.)*)"],$""")
        val p = linhas.subList(meio + 1, linhas.size).mapNotNull { troca.find(it)?.groupValues?.get(1) }
        return t to p
    }

    private fun partes(s: String) = Regex("""\$\d""").findAll(s).map { it.value }.sorted().toList()

    @Test fun todasAsLinguasTemAsMesmasFrases() {
        val (tEn, pEn) = ler("en")
        assertTrue("o inglês tem frases", tEn.size > 1000 && pEn.size > 100)
        for (l in LINGUAS.drop(1)) {
            val (t, p) = ler(l)
            assertEquals("i18n-$l.js: frases diferentes do inglês", tEn.toSet() - t.toSet(), emptySet<String>())
            assertEquals("i18n-$l.js: frases a mais que o inglês", t.toSet() - tEn.toSet(), emptySet<String>())
            assertEquals("i18n-$l.js: número de frases montadas", pEn.size, p.size)
            for (i in p.indices) assertEquals("i18n-$l.js frase montada $i perdeu ou ganhou partes", partes(pEn[i]), partes(p[i]))
        }
    }

    /** A ficha de cada app (assets/fichas-apps.json, em português) aparece no painel: cada texto precisa de tradução. */
    @Test fun aFichaDosAppsTemTraducao() {
        val fichas = org.json.JSONObject(File(pasta().parentFile, "fichas-apps.json").readText())
        val (t, _) = ler("en")
        val chaves = t.map { org.json.JSONArray("[$it]").getString(0) }.toSet()
        val faltam = fichas.keys().asSequence().flatMap { p -> fichas.getJSONObject(p).let { sequenceOf(it.getString("d"), it.getString("q")) } }
            .filter { it !in chaves }.toSet()
        assertEquals("textos da ficha dos apps sem tradução no painel", emptySet<String>(), faltam)
    }

    @Test fun oMotorConheceAsMesmasLinguas() {
        val motor = File(pasta(), "i18n.js").readText()
        val lista = Regex("""LINGUAS = \[([^\]]+)]""").find(motor)?.groupValues?.get(1)
        assertEquals(LINGUAS, Regex("""'([a-z]+)'""").findAll(lista ?: "").map { it.groupValues[1] }.toList())
    }

    @Test fun oServidorEntregaCadaLingua() {
        val web = CodeSource.file("PainelWeb.kt").readText()
        for (l in LINGUAS) assertTrue("i18n-$l.js fora da lista TIPOS do PainelWeb.kt", "\"/i18n-$l.js\" to" in web)
    }
}
