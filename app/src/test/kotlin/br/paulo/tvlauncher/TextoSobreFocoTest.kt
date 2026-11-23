package br.paulo.tvlauncher

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste 100% (Sala, 23/11/2026): o "Parar" do timer e as ações da ficha de filme apareciam como pílula branca vazia.
 * O fundo do foco era o branco fixo (Estilo.FOCO / Estilo.TEXTO) e o texto usava Estilo.TEXTO_NO_FOCO, que desde a régua
 * de estilo é creme no Fio e no Véu. Sobre fundo sempre claro o texto tem de ser Estilo.TEXTO_SOBRE_FOCO.
 *
 * Regra conferida aqui: num trecho de até 4 linhas que pinta um fundo de foco com Estilo.FOCO, não pode haver
 * TEXTO_NO_FOCO pintando texto ou ícone.
 */
class TextoSobreFocoTest {
    private val fundoBranco = Regex("""if \((foco|f|focada)\) Estilo\.FOCO else""")

    @Test
    fun textoEmCimaDoBrancoFixoNaoUsaTextoNoFoco() {
        val achados = mutableListOf<String>()
        for (arq in CodeSource.all()) {
            val linhas = arq.readLines()
            for ((i, l) in linhas.withIndex()) {
                if (!fundoBranco.containsMatchIn(l) && !(l.contains("Estilo.fundo(") && l.contains("Estilo.FOCO,"))) continue
                for (j in maxOf(0, i - 2)..minOf(linhas.lastIndex, i + 2)) {
                    if (Regex("""TEXTO_NO_FOCO\b""").containsMatchIn(linhas[j]) && !linhas[j].contains("setColor(")) achados += "${arq.name}:${j + 1}: ${linhas[j].trim()}"
                }
            }
        }
        assertTrue("texto creme sobre fundo branco fixo:\n" + achados.joinToString("\n"), achados.isEmpty())
    }

    @Test
    fun alarmeEFichaUsamTextoEscuro() {
        assertTrue(CodeSource.file("AlarmeTocandoActivity.kt").readText().contains("Estilo.TEXTO_SOBRE_FOCO"))
        assertTrue(CodeSource.file("FichaAcoes.kt").readText().contains("Estilo.TEXTO_SOBRE_FOCO"))
    }
}
