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

    /** As telas de entrada que viram fileira na Barra (Rádio, Podcasts e Jogos usam a CruzOuvir). */
    private val entradas: List<Pair<String, Class<out Activity>>> = listOf(
        "radio" to br.paulo.tvlauncher.RadioActivity::class.java,
        "podcasts" to br.paulo.tvlauncher.PodcastsActivity::class.java,
        "jogos" to br.paulo.tvlauncher.JogosActivity::class.java,
    )

    @Test fun gaveta() = conferir(ScreenStyle.DRAWER)

    @Test fun vidro() = conferir(ScreenStyle.GLASS)

    /** Painel ao lado (padrão desde 25/11/2026): as Configurações num vidro à direita, sem menu de um item só. */
    @Test fun painel() = conferir(ScreenStyle.PANEL)

    /** Barra: as Configurações viram a fileira de cards; as outras telas caem no Vidro. */
    @Test fun barra() = conferir(ScreenStyle.STRIP, telas + entradas)

    private fun conferir(jeito: ScreenStyle, lista: List<Pair<String, Class<out Activity>>> = telas) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        saida.mkdirs()
        val app = RuntimeEnvironment.getApplication()
        SplashOverlay.setEnabled(app, false)
        BottomBar.setMode(app, BottomBar.Mode.STRIP)
        ScreenStyle.set(app, jeito)
        System.setProperty("https.proxyHost", "127.0.0.1"); System.setProperty("https.proxyPort", "9")
        val problemas = mutableListOf<String>()
        for ((nome, cls) in lista) {
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
            problemas += cabecalho(raiz, nome)
            ctl.pause().stop().destroy()
        }
        ScreenStyle.set(app, ScreenStyle.DEFAULT)
        assertTrue(problemas.joinToString("\n"), problemas.isEmpty())
    }

    /**
     * A linha do foco ao lado do título (Moldura.cabecalho): move o foco para ela aparecer e confere que não fica em cima
     * do título nem da pílula da hora, e que o nome do item não sai cortado. Na Sala, 25/11/2026, ela saiu em cima do
     * título ("Configurações" + "TV e filmes") e, antes, cortada antes da pílula ("Tela inicial ..").
     */
    private fun cabecalho(raiz: View, nome: String): List<String> {
        val titulo = raiz.findViewWithTag<android.widget.TextView>(br.paulo.tvlauncher.Estilo.TAG_TITULO) ?: return emptyList()
        val faixa = titulo.parent as? android.view.ViewGroup ?: return emptyList()
        val linha = (0 until faixa.childCount).map { faixa.getChildAt(it) }.firstOrNull { it !== titulo } as? android.view.ViewGroup ?: return emptyList()
        val foco = raiz.findFocus() ?: return emptyList()
        (foco.focusSearch(View.FOCUS_RIGHT) ?: foco.focusSearch(View.FOCUS_DOWN))?.requestFocus()
        repeat(4) { Thread.sleep(30); ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS) }
        if (linha.visibility != View.VISIBLE) return emptyList()
        val p = mutableListOf<String>()
        val rt = Rect(); titulo.getGlobalVisibleRect(rt)
        val textos = (0 until linha.childCount).map { linha.getChildAt(it) }.filterIsInstance<android.widget.TextView>()
        val nomeTv = textos.firstOrNull() ?: return emptyList()
        val rn = Rect(); nomeTv.getGlobalVisibleRect(rn)
        if (Rect.intersects(rt, rn)) p += "$nome: a linha do foco ficou em cima do título"
        raiz.findViewWithTag<View>("moldura-relogio")?.let { pil -> val rp = Rect(); pil.getGlobalVisibleRect(rp); if (Rect.intersects(rp, rn)) p += "$nome: a linha do foco ficou em cima da pílula da hora" }
        val focado = raiz.findFocus()
        if (br.paulo.tvlauncher.home.Moldura.jaVisivel(raiz, nomeTv.text.toString(), focado, linha)) p += "$nome: a linha do foco repete \"${nomeTv.text}\", que a tela já mostra"
        if ((nomeTv.layout?.getEllipsisCount(0) ?: 0) > 0) p += "$nome: o nome do item em foco saiu cortado (\"${nomeTv.text}\")"
        return p
    }

    private fun foto(raiz: View, arquivo: String) {
        if (raiz.width <= 0 || raiz.height <= 0) return
        val bmp = Bitmap.createBitmap(raiz.width, raiz.height, Bitmap.Config.ARGB_8888)
        raiz.draw(Canvas(bmp))
        File(saida, "$arquivo.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
