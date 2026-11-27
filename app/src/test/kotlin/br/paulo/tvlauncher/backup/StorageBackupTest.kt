package br.paulo.tvlauncher.backup

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** #556: a medida do pen drive antes de formatar não pode tratar pasta que não abre como pasta vazia. */
class StorageBackupTest {
    @Test fun medeArquivosLegiveis() {
        val raiz = Files.createTempDirectory("pen").toFile()
        File(raiz, "fotos").mkdirs(); File(raiz, "fotos/a.jpg").writeBytes(ByteArray(10)); File(raiz, "b.txt").writeBytes(ByteArray(5))
        assertEquals(15L to 2, StorageBackup.medir(raiz))
        raiz.deleteRecursively()
    }

    @Test fun pastaQueNaoAbreLancaEmVezDeSumir() {
        val raiz = Files.createTempDirectory("pen").toFile()
        val fechada = File(raiz, "Android/data").apply { mkdirs() }
        File(fechada, "x.bin").writeBytes(ByteArray(3))
        fechada.setReadable(false, false); fechada.setExecutable(false, false)
        try {
            assumeTrue("rodando como root: a permissão não fecha a pasta", fechada.listFiles() == null)
            val e = runCatching { StorageBackup.medir(raiz) }.exceptionOrNull()
            assertTrue("esperava PastaIlegivel, veio $e", e is StorageBackup.PastaIlegivel)
            assertEquals(listOf("Android/data"), (e as StorageBackup.PastaIlegivel).pastas)
        } finally {
            fechada.setReadable(true, false); fechada.setExecutable(true, false); raiz.deleteRecursively()
        }
    }
}
