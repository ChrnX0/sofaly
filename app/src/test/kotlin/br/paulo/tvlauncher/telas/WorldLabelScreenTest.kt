package br.paulo.tvlauncher.telas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.MainActivity
import br.paulo.tvlauncher.home.IconeCruz
import br.paulo.tvlauncher.home.LayoutHome
import br.paulo.tvlauncher.home.SplashOverlay
import br.paulo.tvlauncher.home.WorldLabel
import br.paulo.tvlauncher.settings.Extras
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
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Nome do mundo junto da pílula do topo, com o ícone do mundo à esquerda (Paulo, 22/11/2026, vendo a Sala v468606).
 * A HOME de verdade (MainActivity, barra Ilha), mundo por mundo: app/build/telas/pilula-mundo-<n>-<mundo>.png.
 * Confere em cada mundo: o nome na pílula é o do mundo em foco; a coluna não mostra o nome; a pílula fica inteira
 * dentro da borda segura, no mesmo lugar de cima, sem reticências, e não encosta em nenhum texto da cruz.
 * No modo coluna (como era), a pílula não tem mundo e a coluna mostra o nome.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class WorldLabelScreenTest {
    private val saida = File("build/telas")

    @Config(qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
    @Test fun nomeNaPilula() = conferir("pt", WorldLabel.Place.PILL)

    @Config(qualifiers = "en-w960dp-h540dp-land-television-xhdpi")
    @Test fun nomeNaPilulaIngles() = conferir("en", WorldLabel.Place.PILL)

    @Config(qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
    @Test fun nomeNaColuna() = conferir("pt", WorldLabel.Place.COLUMN)

    private fun conferir(idioma: String, lugar: WorldLabel.Place) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        saida.mkdirs()
        val app = RuntimeEnvironment.getApplication()
        SplashOverlay.setEnabled(app, false)
        LayoutHome.definirBarraCruz(app, LayoutHome.BarraCruz.ILHA)
        LayoutHome.definirIconesCruz(app, IconeCruz.Estilo.OBJETO)   // o conjunto da Sala
        Extras.definirNumero(app, WorldLabel.KEY, lugar.ordinal)
        // Sem rede: a tela não depende de o clima chegar antes da foto (como no TelasTest).
        System.setProperty("https.proxyHost", "127.0.0.1"); System.setProperty("https.proxyPort", "9")
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        fun esperar() = repeat(6) { Thread.sleep(100); ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS) }
        try {
            esperar(); esperar()
            assertNotNull("a HOME montou a cruz", a.cruz)
            val cruz = a.cruz!!
            cruz.requestFocus()
            fun tecla(k: Int) { cruz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, k)); cruz.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, k)); esperar() }
            repeat(8) { tecla(KeyEvent.KEYCODE_DPAD_LEFT) }   // o 1º mundo da linha
            val d = a.resources.displayMetrics.density
            val largura = a.window.decorView.width
            var pilula0: Rect? = null
            val vistos = mutableListOf<String>()
            var n = 0
            while (true) {
                val c = cruz.categoriaAtual() ?: break
                if (c.nome in vistos) break
                vistos += c.nome; n++
                foto(a, "pilula-mundo-$idioma-${lugar.name.lowercase()}-$n-${c.cat.id}")
                val onde = "${c.nome} ($idioma, ${lugar.name})"
                val mundo = a.ilhaMundo!!
                if (lugar == WorldLabel.Place.COLUMN) {
                    assertEquals("$onde: no modo coluna a pílula não tem mundo", View.GONE, mundo.visibility)
                    assertEquals("$onde: no modo coluna o nome fica na coluna", View.VISIBLE, cruz.rotuloAtual().visibility)
                } else {
                    assertEquals("$onde: mundo aparece na pílula", View.VISIBLE, mundo.visibility)
                    assertEquals("$onde: o nome da pílula é o do mundo em foco", c.nome, a.ilhaMundoNome!!.text.toString())
                    assertNotNull("$onde: ícone do mundo na pílula", a.ilhaMundoIcone!!.drawable)
                    assertTrue("$onde: a coluna não repete o nome", cruz.rotuloAtual().visibility != View.VISIBLE)
                    val nome = a.ilhaMundoNome!!
                    assertTrue("$onde: nome sem reticências", nome.layout.let { l -> (0 until l.lineCount).none { l.getEllipsisCount(it) > 0 } })
                    // Pílula inteira dentro da borda segura (32 dp) e sempre na mesma altura.
                    val p = Rect(); a.ilhaPilula!!.getGlobalVisibleRect(p)
                    assertTrue("$onde: pílula dentro da borda segura ($p)", p.left >= 32 * d - 1 && p.right <= largura - 32 * d + 1)
                    val p0 = pilula0 ?: Rect(p).also { pilula0 = it }
                    assertEquals("$onde: a pílula mudou de lugar ou de tamanho (a hora andaria)", p0, p)
                    // Nenhum texto da cruz encosta na pílula (folga de 8 dp).
                    val folga = Rect(p).apply { inset(0, (-8 * d).toInt()) }
                    for (t in textos(cruz)) {
                        val r = Rect(); t.getGlobalVisibleRect(r)
                        assertTrue("$onde: \"${t.text}\" encosta na pílula", !Rect.intersects(r, folga))
                    }
                }
                tecla(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
            assertTrue("passou por mais de um mundo (${vistos})", vistos.size >= 2)
        } finally {
            Extras.definirNumero(app, WorldLabel.KEY, WorldLabel.Place.PILL.ordinal)
            System.clearProperty("https.proxyHost"); System.clearProperty("https.proxyPort")
            a.finish()
        }
    }

    private fun textos(v: View): List<TextView> {
        if (v.visibility != View.VISIBLE || v.alpha == 0f) return emptyList()
        if (v is TextView) return if (v.text.isNotBlank() && v.width > 0) listOf(v) else emptyList()
        if (v is ViewGroup) return (0 until v.childCount).flatMap { textos(v.getChildAt(it)) }
        return emptyList()
    }

    private fun foto(a: android.app.Activity, arquivo: String) {
        val raiz: View = a.window.decorView
        val bmp = Bitmap.createBitmap(raiz.width, raiz.height, Bitmap.Config.ARGB_8888)
        raiz.draw(Canvas(bmp))
        File(saida, "$arquivo.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
