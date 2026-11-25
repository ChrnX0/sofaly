package br.paulo.tvlauncher.telas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.PainelRapidoActivity
import br.paulo.tvlauncher.settings.FocusStyle
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Painel rápido na régua de estilo (Sala, 22/11/2026: título focado escuro sobre o Fio, "NOTIFICAÇÕES" em maiúsculas,
 * tempo cortado, Fio colado no ícone). Tira foto com o foco em cada tile da 1ª linha, nas 3 formas de foco,
 * em app/build/telas/painel-rapido-<foco>-<n>.png, para conferir antes de ir para a TV.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class PainelRapidoTelaTest {
    private val saida = File("build/telas")

    @Config(qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
    @Test fun focoNosTiles() {
        saida.mkdirs()
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        for (f in FocusStyle.entries) {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            FocusStyle.gravar(ctx, f)
            val ctl = Robolectric.buildActivity(PainelRapidoActivity::class.java).setup()
            val a = ctl.get()
            ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
            val focaveis = ArrayList<View>()
            a.window.decorView.addFocusables(focaveis, View.FOCUS_DOWN)
            val tiles = focaveis.filter { (it as? ViewGroup)?.childCount == 2 && it.width > 0 }.take(4)
            for ((n, t) in tiles.withIndex()) {
                t.requestFocus()
                ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
                val raiz = a.window.decorView
                val bmp = Bitmap.createBitmap(raiz.width, raiz.height, Bitmap.Config.ARGB_8888)
                raiz.draw(Canvas(bmp))
                File(saida, "painel-rapido-${f.name.lowercase()}-${n + 1}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            ctl.pause().stop().destroy()
        }
        FocusStyle.gravar(InstrumentationRegistry.getInstrumentation().targetContext, FocusStyle.RING)
    }
}
