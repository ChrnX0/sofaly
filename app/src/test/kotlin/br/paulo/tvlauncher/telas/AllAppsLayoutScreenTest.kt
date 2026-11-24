package br.paulo.tvlauncher.telas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.MainActivity
import br.paulo.tvlauncher.home.AllAppsLayout
import br.paulo.tvlauncher.home.AllAppsScreen
import br.paulo.tvlauncher.home.AppFolders
import br.paulo.tvlauncher.home.IconeCruz
import br.paulo.tvlauncher.home.LayoutHome
import br.paulo.tvlauncher.home.SplashOverlay
import br.paulo.tvlauncher.home.abrirPasta
import br.paulo.tvlauncher.home.abrirTodos
import br.paulo.tvlauncher.settings.Extras
import org.junit.Assert.assertEquals
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
 * As 3 formas de "Todos os apps" (22/11/2026) com 14 apps de mentira e uma pasta: app/build/telas/todos-<forma>-<idioma>.png.
 * Confere: Prateleiras sem painel; Pastas e canais com a fileira Pastas; Biblioteca com o trilho; pasta aberta só com os apps dela.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class AllAppsLayoutScreenTest {
    private val saida = File("build/telas")
    private val nomes = listOf("YouTube", "Netflix", "Prime Video", "Max", "Globoplay", "Disney+", "Spotify", "Deezer",
        "YouTube Music", "Pluto TV", "Twitch", "Kodi", "VLC", "Navegador")

    @Config(qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
    @Test fun tresFormas() = conferir("pt")

    @Config(qualifiers = "en-w960dp-h540dp-land-television-xhdpi")
    @Test fun tresFormasIngles() = conferir("en")

    private fun conferir(idioma: String) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        saida.mkdirs()
        val app = RuntimeEnvironment.getApplication()
        SplashOverlay.setEnabled(app, false)
        LayoutHome.definirBarraCruz(app, LayoutHome.BarraCruz.ILHA)
        LayoutHome.definirIconesCruz(app, IconeCruz.Estilo.OBJETO)
        System.setProperty("https.proxyHost", "127.0.0.1"); System.setProperty("https.proxyPort", "9")
        AppFolders.save(app, emptyList())
        AppFolders.change(app) { AppFolders.Rules.create(it, if (idioma == "pt") "Crianças" else "Kids", listOf("p0", "p9")) }
        fun esperar() = repeat(6) { Thread.sleep(100); ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS) }
        try {
            for (m in AllAppsLayout.Mode.entries) {
                Extras.definirNumero(app, AllAppsLayout.KEY, m.ordinal)
                val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
                try {
                    esperar(); esperar()
                    val cores = intArrayOf(0xFFC43B2A.toInt(), 0xFF2A6BC4.toInt(), 0xFF2AC46B.toInt(), 0xFF8B2AC4.toInt())
                    a.instalados = nomes.mapIndexed { i, n -> MainActivity.Item("p$i", n, ColorDrawable(cores[i % cores.size])) }
                    a.abrirTodos(); esperar(); esperar()
                    val tela = a.telaTodos!!
                    assertEquals(m, tela.mode)
                    val txt = textos(tela).map { it.text.toString() }
                    val pastas = if (idioma == "pt") "Pastas" else "Folders"
                    when (m) {
                        AllAppsLayout.Mode.SHELVES -> assertTrue("A sem fileira Pastas ($txt)", pastas !in txt)
                        AllAppsLayout.Mode.FOLDERS -> assertTrue("B com fileira Pastas ($txt)", pastas in txt)
                        AllAppsLayout.Mode.LIBRARY -> assertTrue("C com o trilho ($txt)", (if (idioma == "pt") "Tudo" else "All") in txt)
                    }
                    foto(a.window.decorView, "todos-${m.name.lowercase()}-$idioma")
                    if (m != AllAppsLayout.Mode.SHELVES) {
                        a.abrirPasta(AppFolders.all(app).first().id); esperar()
                        val cartoes = cartoes(tela)
                        assertEquals("pasta aberta: só os 2 apps dela", listOf("p0", "p9"), cartoes.map { it.pkg })
                        foto(a.window.decorView, "todos-${m.name.lowercase()}-pasta-$idioma")
                    }
                } finally { a.finish() }
            }
        } finally {
            Extras.definirNumero(app, AllAppsLayout.KEY, AllAppsLayout.Mode.SHELVES.ordinal)
            AppFolders.save(app, emptyList())
            System.clearProperty("https.proxyHost"); System.clearProperty("https.proxyPort")
        }
    }

    private fun cartoes(v: View): List<AllAppsScreen.Card> {
        if (v.visibility != View.VISIBLE) return emptyList()
        val aqui = listOfNotNull(v.tag as? AllAppsScreen.Card)
        if (v is ViewGroup) return aqui + (0 until v.childCount).flatMap { cartoes(v.getChildAt(it)) }
        return aqui
    }

    private fun textos(v: View): List<TextView> {
        if (v.visibility != View.VISIBLE) return emptyList()
        if (v is TextView) return if (v.text.isNotBlank()) listOf(v) else emptyList()
        if (v is ViewGroup) return (0 until v.childCount).flatMap { textos(v.getChildAt(it)) }
        return emptyList()
    }

    private fun foto(raiz: View, arquivo: String) {
        val bmp = Bitmap.createBitmap(raiz.width, raiz.height, Bitmap.Config.ARGB_8888)
        raiz.draw(Canvas(bmp))
        File(saida, "$arquivo.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
