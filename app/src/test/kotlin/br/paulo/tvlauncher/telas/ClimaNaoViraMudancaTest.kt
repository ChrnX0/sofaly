package br.paulo.tvlauncher.telas

import android.content.Context
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.AppsEscolhaActivity
import br.paulo.tvlauncher.Desfazer
import br.paulo.tvlauncher.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * 27/11/2026: o CicloDeVidaTelaTest falhou uma vez na CI ("appsescolha: VOLTAR não fechou a tela"). Causa provada:
 * a HOME grava o clima (temperatura, chuva, horas...) nas prefs "clima" a cada 5 min, e o VigiaDesfazer da tela aberta
 * tratava isso como mudança da pessoa: anotava um passo no Diário e mostrava o aviso "Mudou: ... [Desfazer]", cujo botão
 * pega o foco; o VOLTAR então só fechava o aviso. Aqui: a tela aberta, o clima chega, nada de passo, VOLTAR fecha.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
class ClimaNaoViraMudancaTest {
    private fun esperar(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    @Test fun climaQueChegaComATelaAbertaNaoViraPassoNemRoubaOVoltar() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val c = Robolectric.buildActivity(AppsEscolhaActivity::class.java).setup()
        esperar(500)
        val a = c.get()
        val antes = Desfazer.lista(a).size
        // O mesmo que MainActivity.buscarClima grava (cache da previsão, não escolha da pessoa).
        a.getSharedPreferences(Prefs.CLIMA, Context.MODE_PRIVATE).edit()
            .putInt("temp", 27).putBoolean("chovendo", false).putBoolean("chuva_agora", false).putInt("codigo", 2)
            .putBoolean("eDia", true).putInt("umidade", 60).putInt("chuva", 30).putInt("max", 31).putInt("min", 19)
            .putInt("amanha_max", 30).putInt("amanha_min", 18).putInt("amanha_codigo", 61).putInt("amanha_chuva", 70)
            .putFloat("lat", -10f).putFloat("lon", -40f).putString("linha", "Parcialmente nublado")
            .putString("horas", "[]").putString("idioma", "pt").putLong("em", System.currentTimeMillis()).apply()
        esperar(1000)   // o vigia junta mudanças por 400 ms
        assertEquals("o clima virou passo no Diário", antes, Desfazer.lista(a).size)
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        esperar(500)
        assertTrue("VOLTAR não fechou a tela", a.isFinishing)
    }
}
