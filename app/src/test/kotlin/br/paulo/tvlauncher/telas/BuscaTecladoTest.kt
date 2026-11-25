package br.paulo.tvlauncher.telas

import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.BuscaActivity
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
import java.util.concurrent.TimeUnit

/**
 * Buscar no teclado (teste da noite na Sala, 25/11/2026): o teclado ficava aberto por cima dos resultados e o foco
 * continuava no campo. Agora o Buscar leva o foco ao 1º resultado (e fecha o teclado). Só roda com -Ptelas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
class BuscaTecladoTest {
    private fun esperar(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    @Test fun buscarNoTecladoLevaOFocoAoResultado() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        br.paulo.tvlauncher.home.SplashOverlay.setEnabled(RuntimeEnvironment.getApplication(), false)
        val a = Robolectric.buildActivity(BuscaActivity::class.java).setup().get()
        val campo = a.window.decorView.findFocus() as? EditText
        assertNotNull("a busca abre com o foco no campo", campo)
        campo!!.setText("internet")
        campo.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
        // O índice e a busca rodam em segundo plano (Fundo.rodar): espera o desenho chegar à thread principal.
        var foco = a.window.decorView.findFocus()
        for (i in 0 until 60) {
            foco = a.window.decorView.findFocus()
            if (foco != null && foco !== campo) break
            Thread.sleep(50); esperar(100)
        }
        assertTrue("depois de Buscar, o foco tem de sair do campo para o resultado (está em $foco)", foco != null && foco !== campo)
    }
}
