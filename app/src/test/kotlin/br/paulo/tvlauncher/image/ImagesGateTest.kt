package br.paulo.tvlauncher.image

import br.paulo.tvlauncher.CodeSource
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Toda imagem nasce em [Images] (refatoração G, 27/11/2026): só assim a régua (`EXTRAS --es o leveza`, "imagens") conta
 * tudo e o corte de tamanho vale para todas. Este teste recusa a criação direta fora da pasta image/ e confere que a
 * origem anotada em cada chamada é o próprio arquivo (senão o relatório culparia o arquivo errado).
 */
class ImagesGateTest {
    private val proibido = Regex("""BitmapFactory\.decode\w*\(|Bitmap\.createBitmap\(|createScaledBitmap\(|ImageDecoder\.decode\w*\(|\w\.copy\(\s*Bitmap\.Config""")
    private val chamada = Regex("""Images\.\w+\(\s*"([^"]*)"""")

    private fun arquivos() = CodeSource.all().filter { "/image/" !in it.invariantSeparatorsPath }

    @Test fun nenhumaImagemNasceForaDaPorta() {
        val achados = arquivos().flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l ->
                if (l.trimStart().startsWith("//") || l.trimStart().startsWith("*")) null
                else if (proibido.containsMatchIn(l.substringBefore(" // "))) "${f.name}:${i + 1}: ${l.trim()}" else null
            }
        }.toList()
        assertTrue("Crie bitmaps por br.paulo.tvlauncher.image.Images (a régua de memória conta só o que passa por lá):\n" +
            achados.joinToString("\n"), achados.isEmpty())
    }

    @Test fun origemAnotadaEOProprioArquivo() {
        val errados = arquivos().flatMap { f ->
            val esperado = ImageTally.originOf(f.invariantSeparatorsPath)
            chamada.findAll(f.readText()).map { it.groupValues[1] }.filter { it != esperado }.map { "${f.name}: \"$it\" (esperado \"$esperado\")" }
        }.toList()
        assertTrue(errados.joinToString("\n"), errados.isEmpty())
    }

    @Test fun aPortaEstaEmUso() {
        val usos = arquivos().sumOf { f -> chamada.findAll(f.readText()).count() }
        assertTrue("só $usos chamadas: o teste está lendo a pasta certa?", usos >= 90)
    }
}
