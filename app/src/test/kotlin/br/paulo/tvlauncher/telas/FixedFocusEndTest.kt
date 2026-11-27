package br.paulo.tvlauncher.telas

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import br.paulo.tvlauncher.FixedFocusScrollView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.shadows.ShadowLooper

/**
 * Sala, 26/11/2026: nas Configurações, descer além do último item ("Sistema") rolava a lista para a folga de baixo e a
 * tela ficava vazia, com o foco escondido. Aqui: no último item, BAIXO não rola nem tira o foco dele; no meio, anda.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
class FixedFocusEndTest {
    @Test fun baixoNoUltimoNaoEsvaziaALista() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val lista = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val itens = (1..4).map { n -> TextView(a).apply { text = "Item $n"; isFocusable = true; minHeight = 140 } }
        itens.forEach { lista.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        val rolagem = FixedFocusScrollView(a).apply { addView(lista) }
        a.setContentView(rolagem, android.view.ViewGroup.LayoutParams(-1, 600))
        ShadowLooper.idleMainLooper()

        val baixo = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
        itens.first().requestFocus()
        assertTrue("no meio a lista anda", rolagem.executeKeyEvent(baixo))
        ShadowLooper.idleMainLooper()
        assertTrue(itens[1].isFocused)

        itens.last().requestFocus()
        ShadowLooper.idleMainLooper()
        val antes = rolagem.scrollY
        assertFalse("no fim a tecla segue para fora da lista", rolagem.executeKeyEvent(baixo))
        ShadowLooper.idleMainLooper()
        assertEquals("a lista não rola para a folga", antes, rolagem.scrollY)
        assertTrue(itens.last().isFocused)
        assertEquals(View.VISIBLE, itens.last().visibility)
    }

    /**
     * Sala, 27/11/2026 (Casa e rede): depois do último item havia uma peça focável sem tamanho; o ScrollView comum rolava
     * "uma página" para alcançá-la e o último item subia para baixo das pílulas. Agora BAIXO não rola para a folga.
     */
    @Test fun focavelSemTamanhoNoFimNaoRolaAPagina() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val lista = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val itens = (1..4).map { n -> TextView(a).apply { text = "Item $n"; isFocusable = true; minHeight = 140 } }
        itens.forEach { lista.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        lista.addView(View(a).apply { isFocusable = true }, LinearLayout.LayoutParams(-1, 0))
        val rolagem = FixedFocusScrollView(a).apply { addView(lista) }
        a.setContentView(rolagem, android.view.ViewGroup.LayoutParams(-1, 600))
        ShadowLooper.idleMainLooper()

        itens.last().requestFocus()
        ShadowLooper.idleMainLooper()
        val antes = rolagem.scrollY
        rolagem.executeKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        ShadowLooper.idleMainLooper()
        assertEquals("a lista não rola para a folga", antes, rolagem.scrollY)
        assertTrue(itens.last().isFocused)
    }
}
