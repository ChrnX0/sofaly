package br.paulo.tvlauncher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Nenhum texto da TV manda a pessoa rodar comando de adb nem mostra o nome de pacote antigo (auditoria de telas,
 * 27/11/2026: a Agenda mostrava "pm grant br.paulo.tvlauncher android.permission.READ_CALENDAR", com o pacote errado).
 * Passa por todas as línguas.
 */
class SemComandoNaTelaTest {
    private val linha = Regex("""<string name="([a-z0-9_]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val comando = Regex("""pm grant|am broadcast|am start|br\.paulo\.tvlauncher|android\.permission\.""")

    @Test
    fun nenhumTextoTemComandoDeAdb() {
        val pastas = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
        assertTrue("achei poucas línguas", pastas.size >= 20)
        val achados = pastas.sortedBy { it.name }.flatMap { p ->
            p.listFiles { f -> f.extension == "xml" }!!.sortedBy { it.name }.flatMap { x ->
                linha.findAll(x.readText()).filter { comando.containsMatchIn(it.groupValues[2]) }
                    .map { "${p.name}/${x.name} ${it.groupValues[1]}: ${it.groupValues[2]}" }.toList()
            }
        }
        assertTrue("Texto com comando de adb ou pacote antigo (a pessoa não usa adb):\n" + achados.take(40).joinToString("\n"), achados.isEmpty())
    }
}
