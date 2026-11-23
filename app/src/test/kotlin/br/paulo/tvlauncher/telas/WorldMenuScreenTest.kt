package br.paulo.tvlauncher.telas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.MainActivity
import br.paulo.tvlauncher.home.CruzLogica
import br.paulo.tvlauncher.home.IconeCruz
import br.paulo.tvlauncher.home.LayoutHome
import br.paulo.tvlauncher.home.SplashOverlay
import br.paulo.tvlauncher.home.WorldOptions
import br.paulo.tvlauncher.home.menuDoMundo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Menu de cada mundo (22/11/2026): a HOME de verdade, o menu aberto em Assistir e em Ferramentas, e a cruz depois de
 * renomear e esconder. Fotos em app/build/telas/menu-mundo-*.png (para o Paulo aprovar antes de instalar).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class WorldMenuScreenTest {
    private val saida = File("build/telas")

    @Config(qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
    @Test fun menuDoMundo() = conferir("pt")

    @Config(qualifiers = "en-w960dp-h540dp-land-television-xhdpi")
    @Test fun menuDoMundoIngles() = conferir("en")

    private fun conferir(idioma: String) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        saida.mkdirs()
        val app = RuntimeEnvironment.getApplication()
        SplashOverlay.setEnabled(app, false)
        LayoutHome.definirBarraCruz(app, LayoutHome.BarraCruz.ILHA)
        LayoutHome.definirIconesCruz(app, IconeCruz.Estilo.OBJETO)
        System.setProperty("https.proxyHost", "127.0.0.1"); System.setProperty("https.proxyPort", "9")
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        fun esperar() = repeat(6) { Thread.sleep(100); ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS) }
        try {
            esperar(); esperar()
            val cruz = a.cruz!!
            cruz.requestFocus()
            fun tecla(k: Int) { cruz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, k)); cruz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, k)); esperar() }
            repeat(8) { tecla(KeyEvent.KEYCODE_DPAD_LEFT) }
            var achou = 0
            while (true) {
                val c = cruz.categoriaAtual() ?: break
                if (c.cat == CruzLogica.Categoria.ASSISTIR || c.cat == CruzLogica.Categoria.FERRAMENTAS) {
                    a.menuDoMundo(c.cat, c.nome); esperar()
                    val d = ShadowDialog.getLatestDialog()
                    assertNotNull("${c.nome}: menu abriu", d)
                    val textos = textos(d.window!!.decorView).map { it.text.toString() }
                    val esperado = if (idioma == "pt") "Renomear a categoria" else "Rename this category"
                    assertTrue("${c.nome}: tem $esperado ($textos)", esperado in textos)
                    val ordenar = if (idioma == "pt") "Ordenar" else "Sort"
                    val temApps = c.itens.any { it.id.startsWith("app:") }
                    assertEquals("${c.nome}: Ordenar só onde há apps", temApps, ordenar in textos)
                    val esconde = if (idioma == "pt") "Esconder a categoria" else "Hide this category"
                    assertEquals("${c.nome}: Esconder só em Assistir/Ouvir/Jogar", c.cat == CruzLogica.Categoria.ASSISTIR, esconde in textos)
                    foto(d.window!!.decorView, "menu-mundo-$idioma-${c.cat.id}")
                    d.dismiss(); esperar(); achou++
                }
                val antes = c.nome
                tecla(KeyEvent.KEYCODE_DPAD_RIGHT)
                if (cruz.categoriaAtual()?.nome == antes) break
            }
            assertEquals("passou por Ferramentas e Assistir", 2, achou)
            // Só o ícone (23/11/2026): todas as categorias sem nome na linha; a foto vai para o Paulo conferir.
            for (k in CruzLogica.Categoria.entries) WorldOptions.save(app, k, WorldOptions.Options(iconOnly = true))
            a.atualizarAposMudanca(null); esperar(); esperar()
            assertTrue("só ícone chegou à cruz", cruz.categorias().all { it.soIcone })
            foto(a.window.decorView, "cruz-so-icone-$idioma")
            for (k in CruzLogica.Categoria.entries) WorldOptions.save(app, k, WorldOptions.Options())
            // Renomear Assistir: a cruz mostra o nome novo; esconder: o mundo some e Ferramentas continua.
            WorldOptions.save(app, CruzLogica.Categoria.ASSISTIR, WorldOptions.Options(name = "Filmes"))
            a.atualizarAposMudanca(null); esperar(); esperar()
            assertTrue("nome trocado aparece", cruz.categorias().any { it.nome == "Filmes" })
            foto(a.window.decorView, "menu-mundo-$idioma-renomeado")
            WorldOptions.save(app, CruzLogica.Categoria.ASSISTIR, WorldOptions.Options(hidden = true))
            a.atualizarAposMudanca(null); esperar(); esperar()
            assertTrue("Assistir sumiu", cruz.categorias().none { it.cat == CruzLogica.Categoria.ASSISTIR })
            assertTrue("Ferramentas fica", cruz.categorias().any { it.cat == CruzLogica.Categoria.FERRAMENTAS })
        } finally {
            WorldOptions.save(app, CruzLogica.Categoria.ASSISTIR, WorldOptions.Options())
            System.clearProperty("https.proxyHost"); System.clearProperty("https.proxyPort")
            a.finish()
        }
    }

    private fun textos(v: View): List<TextView> {
        if (v.visibility != View.VISIBLE) return emptyList()
        if (v is TextView) return if (v.text.isNotBlank()) listOf(v) else emptyList()
        if (v is ViewGroup) return (0 until v.childCount).flatMap { textos(v.getChildAt(it)) }
        return emptyList()
    }

    private fun foto(raiz: View, arquivo: String) {
        if (raiz.width <= 0 || raiz.height <= 0) return
        val bmp = Bitmap.createBitmap(raiz.width, raiz.height, Bitmap.Config.ARGB_8888)
        raiz.draw(Canvas(bmp))
        File(saida, "$arquivo.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
