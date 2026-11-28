package br.paulo.tvlauncher.telas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.MainActivity
import br.paulo.tvlauncher.home.BottomBar
import br.paulo.tvlauncher.home.SplashOverlay
import br.paulo.tvlauncher.home.RowsHome
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
 * Fileiras (26/11/2026, clone do Projectivy): a HOME de verdade no jeito de fábrica. Confere o foco preso à esquerda e
 * aumentado, o nome embaixo, as teclas (◀ ▶ nos cards, ▲ ▼ nas fileiras) e tira as fotos app/build/telas/fileiras-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class RowsScreenTest {
    private val saida = File("build/telas")

    @Config(qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
    @Test fun fileiras() = conferir("pt")

    @Config(qualifiers = "en-w960dp-h540dp-land-television-xhdpi")
    @Test fun fileirasIngles() = conferir("en")

    private fun conferir(idioma: String) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        saida.mkdirs()
        val app = RuntimeEnvironment.getApplication()
        SplashOverlay.setEnabled(app, false)
        assertEquals("fileiras são o jeito de fábrica", BottomBar.Mode.ROWS, BottomBar.mode(app))
        System.setProperty("https.proxyHost", "127.0.0.1"); System.setProperty("https.proxyPort", "9")
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        fun esperar() = repeat(6) { Thread.sleep(100); ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS) }
        try {
            esperar(); esperar()
            val cruz = a.cruz!!
            assertTrue("fileiras ligadas", cruz.faixa && cruz.fileiras)
            cruz.requestFocus(); esperar()
            fun tecla(k: Int) { cruz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, k)); cruz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, k)); esperar() }
            foto(a.window.decorView, "fileiras-$idioma")
            val d = app.resources.displayMetrics.density
            val nome = cruz.nomeDoFoco()
            assertTrue("nome do foco visível", nome.alpha > 0.9f && nome.text.isNotBlank())
            assertEquals("nome do foco numa linha", 1, nome.lineCount.coerceAtMost(1))
            val cards = cruz.cardsDaFaixa()
            assertTrue("cards na tela", cards.isNotEmpty())
            // o card em foco fica preso à esquerda (x = 40 dp), maior que os outros
            val foco = cards.minByOrNull { kotlin.math.abs(it.translationX / d - RowsHome.FOCO_X) }!!
            assertEquals("foco preso a 40 dp", RowsHome.FOCO_X, foco.translationX / d, 1f)
            assertTrue("foco aumentado", foco.scaleX > 1.2f)
            assertTrue("nome abaixo do foco", nome.translationY > foco.translationY + foco.height * foco.scaleY / 2f)
            val mundo = cruz.categoriaAtual()?.cat
            val antes = cruz.itemAtual()?.id
            tecla(KeyEvent.KEYCODE_DPAD_RIGHT)
            assertEquals("▶ fica na fileira", mundo, cruz.categoriaAtual()?.cat)
            if ((cruz.categoriaAtual()?.itens?.size ?: 0) > 1) assertNotEquals("▶ andou", antes, cruz.itemAtual()?.id)
            esperar()
            foto(a.window.decorView, "fileiras-direita-$idioma")
            tecla(KeyEvent.KEYCODE_DPAD_DOWN)
            if (cruz.categorias().size > 1) assertNotEquals("▼ trocou a fileira", mundo, cruz.categoriaAtual()?.cat)
            esperar()
            foto(a.window.decorView, "fileiras-baixo-$idioma")
            tecla(KeyEvent.KEYCODE_DPAD_UP)
            assertEquals("▲ voltou", mundo, cruz.categoriaAtual()?.cat)
            // opções (foto para comparar): cards grandes e redondos, sombra projetada, sem foco parado
            cruz.opcoesFileiras = RowsHome.DEFAULT.copy(size = RowsHome.Size.L, corners = RowsHome.Corners.ROUND,
                effect = RowsHome.Effect.SHADOW, fixed = false, dim = RowsHome.Dim.HALF)
            esperar()
            foto(a.window.decorView, "fileiras-opcoes-$idioma")
            // tema do sistema (28/11/2026): a mesma HOME em cada tema, para comparar (não falha por cor)
            cruz.opcoesFileiras = RowsHome.DEFAULT; esperar()
            val antesTema = br.paulo.tvlauncher.settings.SystemLook.atual
            try {
                for (l in br.paulo.tvlauncher.settings.SystemLook.entries) {
                    br.paulo.tvlauncher.settings.SystemLook.atual = l
                    cruz.opcoesFileiras = RowsHome.DEFAULT.copy(dim = RowsHome.Dim.HALF); esperar()
                    cruz.opcoesFileiras = RowsHome.DEFAULT; esperar()
                    foto(a.window.decorView, "fileiras-tema-${l.name.lowercase()}-$idioma")
                }
            } finally { br.paulo.tvlauncher.settings.SystemLook.atual = antesTema }
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
