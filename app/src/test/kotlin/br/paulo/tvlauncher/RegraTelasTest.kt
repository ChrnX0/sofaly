package br.paulo.tvlauncher

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Regra das telas (23/11/2026, Paulo: "o sistema TODINHO com a mesma coerência"): toda tela usa as peças do Estilo
 * (título, barra de dicas, cores e tamanhos de letra do sistema). Hoje muitas telas ainda fogem disso; a lista do que
 * falta está em `regra-telas/divida.txt`. Este teste é uma catraca: um arquivo pode ter MENOS do que a lista diz,
 * nunca mais, e arquivo novo entra já limpo. Assim a dívida só diminui enquanto as telas vão sendo refeitas.
 */
class RegraTelasTest {
    private val padroes = mapOf(
        // cor escrita à mão em vez de Estilo.TEXTO, SUPERFICIE etc.
        "cor" to Regex("""Color\.parseColor\(|0x[Ff][Ff][0-9A-Fa-f]{6}"""),
        // tamanho de letra escrito à mão em vez de Estilo.Papel
        "letra" to Regex("""textSize\s*=\s*[0-9]|setTextSize\("""),
    )
    private val tela = Regex("""class \w+ *(\([^)]*\))? *: *(Activity|ComponentActivity|FragmentActivity|AppCompatActivity)\(""")

    /** O que cada arquivo tem fora da regra agora (mesma conta que gerou a lista). */
    private fun contar(): Map<String, Int> {
        val r = HashMap<String, Int>()
        CodeSource.all().filter { it.name != "Estilo.kt" }.forEach { f ->
            val s = f.readText()
            padroes.forEach { (tipo, p) -> p.findAll(s).count().takeIf { it > 0 }?.let { r["${f.name} $tipo"] = it } }
            if (tela.containsMatchIn(s)) {
                if ("barraDicas" !in s) r["${f.name} dicas"] = 1
                if ("tituloTela" !in s) r["${f.name} titulo"] = 1
            }
        }
        return r
    }

    private fun divida(): Map<String, Int> {
        val f = listOf(File("src/test/resources/regra-telas/divida.txt"), File("app/src/test/resources/regra-telas/divida.txt"))
            .first { it.isFile }
        return f.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.associate { l ->
            val p = l.trim().split(Regex("\\s+"))
            "${p[0]} ${p[1]}" to p[2].toInt()
        }
    }

    @Test
    fun telasNaoFogemMaisDaRegra() {
        val base = divida()
        val piorou = contar().filter { (k, n) -> n > (base[k] ?: 0) }
        if (piorou.isNotEmpty()) fail(
            "Tela fora da regra (use Estilo.tituloTela, Estilo.barraDicas, as cores e os Papéis do Estilo):\n" +
                piorou.entries.sortedBy { it.key }.joinToString("\n") { (k, n) -> "  $k: $n (a lista permite ${base[k] ?: 0})" }
        )
    }
}
