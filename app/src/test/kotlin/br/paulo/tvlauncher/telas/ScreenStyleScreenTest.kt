package br.paulo.tvlauncher.telas

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.home.BottomBar
import br.paulo.tvlauncher.home.ScreenStyle
import br.paulo.tvlauncher.home.SplashOverlay
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
 * Jeito das telas com a barra única (24/11/2026): Gaveta (padrão), Vidro e Barra. Abre algumas telas de lista nos dois jeitos,
 * tira as fotos app/build/telas/jeito-<jeito>-<tela>.png e confere que o item com foco fica inteiro dentro da tela.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
class ScreenStyleScreenTest {
    private val saida = File("build/telas")
    private val telas: List<Pair<String, Class<out Activity>>> = listOf(
        "configuracoes" to br.paulo.tvlauncher.ConfigLauncherActivity::class.java,
        "despertador" to br.paulo.tvlauncher.AlarmesActivity::class.java,
        "ajuda" to br.paulo.tvlauncher.AjudaActivity::class.java,
        "extras" to br.paulo.tvlauncher.ExtrasActivity::class.java,
        "busca" to br.paulo.tvlauncher.BuscaActivity::class.java,
    )

    @Test fun gaveta() = conferir(ScreenStyle.DRAWER)

    @Test fun vidro() = conferir(ScreenStyle.GLASS)

    /** Barra: as Configurações viram a fileira de cards; as outras telas caem no Vidro. */
    @Test fun barra() = conferir(ScreenStyle.STRIP)

    private fun conferir(jeito: ScreenStyle) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        saida.mkdirs()
        val app = RuntimeEnvironment.getApplication()
        SplashOverlay.setEnabled(app, false)
        BottomBar.setMode(app, BottomBar.Mode.STRIP)
        ScreenStyle.set(app, jeito)
        System.setProperty("https.proxyHost", "127.0.0.1"); System.setProperty("https.proxyPort", "9")
        val problemas = mutableListOf<String>()
        for ((nome, cls) in telas) {
            val ctl = Robolectric.buildActivity(cls).setup()
            val a = ctl.get()
            repeat(4) { Thread.sleep(50); ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS) }
            val raiz = a.window.decorView
            foto(raiz, "jeito-${jeito.name.lowercase()}-$nome")
            val foco = raiz.findFocus()
            if (foco != null) {
                val r = Rect(); foco.getGlobalVisibleRect(r)
                val alto = foco.height
                if (r.height() < alto - 2 || r.bottom > raiz.height) problemas += "$nome: o item com foco ficou cortado (${r.height()} de $alto px)"
            }
            ctl.pause().stop().destroy()
        }
        ScreenStyle.set(app, ScreenStyle.DEFAULT)
        assertTrue(problemas.joinToString("\n"), problemas.isEmpty())
    }

    private fun foto(raiz: View, arquivo: String) {
        if (raiz.width <= 0 || raiz.height <= 0) return
        val bmp = Bitmap.createBitmap(raiz.width, raiz.height, Bitmap.Config.ARGB_8888)
        raiz.draw(Canvas(bmp))
        File(saida, "$arquivo.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
