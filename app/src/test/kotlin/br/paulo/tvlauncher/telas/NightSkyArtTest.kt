package br.paulo.tvlauncher.telas

import android.graphics.Bitmap
import android.graphics.Canvas
import br.paulo.tvlauncher.home.NightSkyGlyph
import br.paulo.tvlauncher.screensaver.NightSkyMotion
import br.paulo.tvlauncher.weather.NightSky
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration

/**
 * Arte do céu da noite (24/11/2026): desenha os 4 ícones e 3 momentos do movimento de cada evento numa folha só
 * (build/telas/ceu-da-noite.png, para conferir sem esperar um evento de verdade) e confere que cada ícone desenha algo
 * e que o movimento muda de um momento para o outro.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class NightSkyArtTest {
    private val creme = 0xFFF3E9DA.toInt()
    private val noite = 0xFF0E1116.toInt()

    @Test fun iconesEMovimento() {
        val kinds = NightSky.Kind.entries
        val folha = Bitmap.createBitmap(100 + 3 * 220, kinds.size * 110, Bitmap.Config.ARGB_8888)
        val c = Canvas(folha).apply { drawColor(noite) }
        kinds.forEachIndexed { i, k ->
            val y = i * 110 + 5
            val g = NightSkyGlyph(k, creme).apply { setBounds(10, y + 10, 90, y + 90) }
            val so = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
            g.setBounds(0, 0, 80, 80); g.draw(Canvas(so))
            assertTrue("ícone $k desenha algo", contar(so) > 40)
            g.setBounds(10, y + 10, 90, y + 90); g.draw(c)
            val m = NightSkyMotion(k, creme, noite, 70)
            val quadros = (0..2).map { q ->
                val b = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(noite) }
                m.setBounds(0, 0, 200, 100); m.draw(Canvas(b))
                m.setBounds(100 + q * 220, y, 300 + q * 220, y + 100); m.draw(c)
                ShadowSystemClock.advanceBy(Duration.ofMillis(2_300))
                b
            }
            assertTrue("movimento de $k muda com o tempo", !quadros[0].sameAs(quadros[1]) || !quadros[1].sameAs(quadros[2]))
        }
        File("build/telas").apply { mkdirs() }.let { File(it, "ceu-da-noite.png") }.outputStream().use { folha.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun contar(b: Bitmap): Int {
        var n = 0
        for (x in 0 until b.width) for (y in 0 until b.height) if ((b.getPixel(x, y) ushr 24) > 80) n++
        return n
    }
}
