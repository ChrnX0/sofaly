package br.paulo.tvlauncher.telas

import android.graphics.Bitmap
import br.paulo.tvlauncher.MainActivity
import br.paulo.tvlauncher.image.ImageLedger
import br.paulo.tvlauncher.image.Images
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** O livro das imagens com bitmaps de verdade (gráficos nativos do Robolectric) e a HOME aberta. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
class ImageLedgerTelaTest {
    private fun doTeste() = ImageLedger.live().filter { it.origin.startsWith("teste/") }

    @Test fun contaOsVivosUmaVezSoEEsqueceOsReciclados() {
        val a = Images.create("teste/a", 100, 50, Bitmap.Config.ARGB_8888)
        val b = Images.create("teste/b", 10, 10, Bitmap.Config.ALPHA_8)
        ImageLedger.track("teste/a", a)   // anotado duas vezes: conta uma
        val vivos = doTeste()
        assertEquals(2, vivos.size)
        assertEquals(100L * 50 * 4, vivos.first { it.origin == "teste/a" }.bytes)
        assertEquals(100L, vivos.first { it.origin == "teste/b" }.bytes)
        b.recycle()
        assertEquals(listOf("teste/a"), doTeste().map { it.origin })
        a.recycle()
    }

    @Test fun limparALista() {
        // Mais que o limite inicial (256) de bitmaps já reciclados: a lista se limpa e só os vivos ficam.
        repeat(600) { Images.create("teste/lixo", 2, 2, Bitmap.Config.ARGB_8888).recycle() }
        val vivo = Images.create("teste/vivo", 2, 2, Bitmap.Config.ARGB_8888)
        assertEquals(listOf("teste/vivo"), doTeste().map { it.origin })
        vivo.recycle()
    }

    /** A HOME aberta: o relatório sai com origens da HOME e soma igual às linhas. Imprime para comparar com a TV. */
    @Test fun homeAbertaApareceNoRelatorio() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        ShadowLooper.idleMainLooper(3000, TimeUnit.MILLISECONDS)
        val j = ImageLedger.json()
        println("ImageLedger HOME (Robolectric): $j")
        val linhas = j.getJSONArray("porOrigem")
        assertTrue("a HOME não criou nenhuma imagem pela porta", linhas.length() > 0)
        var soma = 0.0
        for (i in 0 until linhas.length()) soma += linhas.getJSONObject(i).getDouble("mb")
        assertEquals(j.getDouble("totalMb"), soma, 0.1 * linhas.length())
        c.pause().stop().destroy()
    }
}
