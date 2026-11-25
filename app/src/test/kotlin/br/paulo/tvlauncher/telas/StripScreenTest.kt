package br.paulo.tvlauncher.telas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.MainActivity
import br.paulo.tvlauncher.home.BottomBar
import br.paulo.tvlauncher.home.LayoutHome
import br.paulo.tvlauncher.home.SplashOverlay
import br.paulo.tvlauncher.home.StripHome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Barra única (24/11/2026): a HOME de verdade no jeito de fábrica. Confere as teclas (◀ ▶ nos apps, ▲ ▼ no mundo), que a
 * linha do mundo cabe numa linha só, que os cards ficam dentro da tela, e tira as fotos app/build/telas/faixa-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class StripScreenTest {
    private val saida = File("build/telas")

    @Config(qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
    @Test fun faixa() = conferir("pt")

    @Config(qualifiers = "en-w960dp-h540dp-land-television-xhdpi")
    @Test fun faixaIngles() = conferir("en")

    private fun conferir(idioma: String) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        saida.mkdirs()
        val app = RuntimeEnvironment.getApplication()
        SplashOverlay.setEnabled(app, false)
        LayoutHome.definirBarraCruz(app, LayoutHome.BarraCruz.ILHA)
        assertEquals("barra única é o jeito de fábrica", BottomBar.Mode.STRIP, BottomBar.mode(app))
        System.setProperty("https.proxyHost", "127.0.0.1"); System.setProperty("https.proxyPort", "9")
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        fun esperar() = repeat(6) { Thread.sleep(100); ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS) }
        try {
            esperar(); esperar()
            val cruz = a.cruz!!
            assertTrue("barra única ligada", cruz.faixa)
            cruz.requestFocus(); esperar()
            fun tecla(k: Int) { cruz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, k)); cruz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, k)); esperar() }
            foto(a.window.decorView, "faixa-$idioma")
            val d = app.resources.displayMetrics.density
            // a linha do mundo é UMA linha, acima dos cards, dentro da tela
            val linha = cruz.linhaDoMundo()
            assertTrue("linha do mundo visível", linha.isShown && linha.alpha > 0.9f)
            assertEquals("descrição numa linha só", 1, cruz.descricaoDoMundo().lineCount.coerceAtMost(1))
            val cards = cruz.cardsDaFaixa()
            assertTrue("cards na tela", cards.isNotEmpty())
            for (c in cards) {
                assertTrue("card dentro da tela (x=${c.translationX / d})", c.translationX >= 0 && c.translationX + c.width <= a.window.decorView.width + 1)
                assertTrue("card abaixo da linha do mundo", c.translationY > linha.translationY + linha.height)
            }
            // ▶ anda no mundo; ▼ troca o mundo e ▲ volta
            val mundo = cruz.categoriaAtual()?.cat
            val antes = cruz.itemAtual()?.id
            tecla(KeyEvent.KEYCODE_DPAD_RIGHT)
            assertEquals("▶ fica no mundo", mundo, cruz.categoriaAtual()?.cat)
            if ((cruz.categoriaAtual()?.itens?.size ?: 0) > 1) assertNotEquals("▶ andou", antes, cruz.itemAtual()?.id)
            tecla(KeyEvent.KEYCODE_DPAD_DOWN)
            if (cruz.categorias().size > 1) assertNotEquals("▼ trocou o mundo", mundo, cruz.categoriaAtual()?.cat)
            esperar()
            foto(a.window.decorView, "faixa-baixo-$idioma")
            tecla(KeyEvent.KEYCODE_DPAD_UP)
            assertEquals("▲ voltou", mundo, cruz.categoriaAtual()?.cat)
            // outras opções dos cards (foto para comparar): 4:3 grande com nome e placa
            cruz.opcoesFaixa = StripHome.DEFAULT.copy(format = StripHome.Format.CLASSIC, size = StripHome.Size.LARGE, name = true, glass = true)
            esperar()
            foto(a.window.decorView, "faixa-opcoes-$idioma")
            // placa dos cards nossos (25/11/2026): vidro escuro (de fábrica, nas fotos acima), branco e vidro claro
            for (own in listOf(StripHome.Own.WHITE, StripHome.Own.GLASS)) {
                cruz.opcoesFaixa = StripHome.DEFAULT.copy(own = own)
                esperar()
                foto(a.window.decorView, "faixa-${own.name.lowercase()}-$idioma")
            }
            // personalidade dos ícones (25/11/2026): brilho da cor (de fábrica, acima), cor própria e azulejo
            for (mood in listOf(StripHome.Mood.COLOR, StripHome.Mood.TILE)) {
                cruz.opcoesFaixa = StripHome.DEFAULT.copy(mood = mood)
                esperar()
                foto(a.window.decorView, "faixa-${mood.name.lowercase()}-$idioma")
            }
        } finally {
            System.clearProperty("https.proxyHost"); System.clearProperty("https.proxyPort")
            a.finish()
        }
    }

    private fun foto(raiz: View, arquivo: String) {
        if (raiz.width <= 0 || raiz.height <= 0) return
        val bmp = Bitmap.createBitmap(raiz.width, raiz.height, Bitmap.Config.ARGB_8888)
        raiz.draw(Canvas(bmp))
        File(saida, "$arquivo.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
