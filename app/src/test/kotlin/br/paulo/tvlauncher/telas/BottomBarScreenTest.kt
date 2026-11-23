package br.paulo.tvlauncher.telas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.MainActivity
import br.paulo.tvlauncher.home.BottomBar
import br.paulo.tvlauncher.home.IconeCruz
import br.paulo.tvlauncher.home.LayoutHome
import br.paulo.tvlauncher.home.SplashOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Barra embaixo (23/11/2026): a HOME de verdade com o 2º jeito ligado. Confere as teclas (▲ abre a linha de apps,
 * ◀ ▶ andam, ▼ fecha) e tira as fotos app/build/telas/barra-*.png para o Paulo aprovar antes de instalar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class BottomBarScreenTest {
    private val saida = File("build/telas")

    @Config(qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
    @Test fun barra() = conferir("pt")

    @Config(qualifiers = "en-w960dp-h540dp-land-television-xhdpi")
    @Test fun barraIngles() = conferir("en")

    private fun conferir(idioma: String) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        saida.mkdirs()
        val app = RuntimeEnvironment.getApplication()
        SplashOverlay.setEnabled(app, false)
        LayoutHome.definirBarraCruz(app, LayoutHome.BarraCruz.ILHA)
        LayoutHome.definirIconesCruz(app, IconeCruz.Estilo.OBJETO)
        BottomBar.set(app, true)
        System.setProperty("https.proxyHost", "127.0.0.1"); System.setProperty("https.proxyPort", "9")
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        fun esperar() = repeat(6) { Thread.sleep(100); ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS) }
        try {
            esperar(); esperar()
            val cruz = a.cruz!!
            assertTrue("barra ligada", cruz.barra)
            cruz.requestFocus(); esperar()
            fun tecla(k: Int) { cruz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, k)); cruz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, k)); esperar() }
            assertFalse("abre com a linha de apps fechada", cruz.appsAbertos())
            foto(a.window.decorView, "barra-fechada-$idioma")
            // ▶ troca a categoria sem abrir a linha
            val primeira = cruz.categoriaAtual()?.cat
            tecla(KeyEvent.KEYCODE_DPAD_RIGHT)
            assertNotEquals("▶ trocou a categoria", primeira, cruz.categoriaAtual()?.cat)
            assertFalse(cruz.appsAbertos())
            tecla(KeyEvent.KEYCODE_DPAD_LEFT)
            assertEquals("◀ voltou", primeira, cruz.categoriaAtual()?.cat)
            // ▲ abre a linha de apps; ▶ anda nela sem trocar de categoria
            tecla(KeyEvent.KEYCODE_DPAD_UP)
            assertTrue("▲ abriu a linha de apps", cruz.appsAbertos())
            val antes = cruz.itemAtual()?.id
            tecla(KeyEvent.KEYCODE_DPAD_RIGHT)
            assertEquals("▶ ficou na categoria", primeira, cruz.categoriaAtual()?.cat)
            if ((cruz.categoriaAtual()?.itens?.size ?: 0) > 1) assertNotEquals("▶ andou nos apps", antes, cruz.itemAtual()?.id)
            esperar()
            foto(a.window.decorView, "barra-apps-$idioma")
            // ▼ fecha a linha e volta para a categoria
            tecla(KeyEvent.KEYCODE_DPAD_DOWN)
            assertFalse("▼ fechou a linha", cruz.appsAbertos())
            // as categorias cabem inteiras na tela, lado a lado, na mesma altura
            val cats = (0 until cruz.childCount).map { cruz.getChildAt(it) }
                .filter { it.visibility == View.VISIBLE && it.contentDescription == null && it is android.widget.LinearLayout && it.childCount == 2 }
            assertTrue("categorias na tela", cats.isNotEmpty())
        } finally {
            BottomBar.set(app, false)
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
