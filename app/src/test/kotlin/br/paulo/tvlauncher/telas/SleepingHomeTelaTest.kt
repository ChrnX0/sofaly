package br.paulo.tvlauncher.telas

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import br.paulo.tvlauncher.screensaver.SleepingHome
import br.paulo.tvlauncher.weather.Forecast
import org.junit.After
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
 * A home que adormece (21/11/2026): com a previsão fixa do [ClimaTelaTest], desenha o topo (widget de hora e tempo) e
 * cada linha da faixa de baixo, passando com ↓ como o controle (app/build/telas/descanso-*.png). Confere letra de pelo
 * menos 18 sp, nada cortado e nada por cima de outra coisa. O protetor não tem foco (é um sonho do Android): esse item
 * da medida não vale aqui.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class SleepingHomeTelaTest {
    @Config(qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
    @Test fun portugues() = andar("pt")

    @Config(qualifiers = "en-w960dp-h540dp-land-television-xhdpi")
    @Test fun ingles() = andar("en")

    @Config(qualifiers = "ar-ldrtl-w960dp-h540dp-land-television-xhdpi")
    @Test fun arabe() = andar("ar")

    /** Tema do sistema (28/11/2026): a 1ª tela do descanso em cada tema, só a foto (descanso-tema-<tema>.png). */
    @Config(qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
    @Test fun temas() {
        val antes = br.paulo.tvlauncher.settings.SystemLook.atual
        try {
            for (l in br.paulo.tvlauncher.settings.SystemLook.entries) {
                br.paulo.tvlauncher.settings.SystemLook.atual = l
                Forecast.dadosDeTeste = ClimaTelaTest().previsao()
                val a = Robolectric.buildActivity(Activity::class.java).setup().get()
                android.provider.Settings.Global.putFloat(a.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
                val s = SleepingHome(a)
                a.setContentView(s.montar(), FrameLayout.LayoutParams(-1, -1))
                fun linhas(): Int = (a.window.decorView as ViewGroup).findViewById<ViewGroup>(android.R.id.content)?.let { contar(it) } ?: 0
                repeat(150) { if (linhas() < 5) { Thread.sleep(100); ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS) } }
                ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
                MedirTela.medir(a, "descanso-tema", "descanso-tema-${l.name.lowercase()}", 18f, cartao = true)
                s.soltar(); a.finish()
            }
        } finally { br.paulo.tvlauncher.settings.SystemLook.atual = antes }
    }

    @After fun limpar() { Forecast.dadosDeTeste = null }

    private fun andar(lingua: String) {
        Forecast.dadosDeTeste = ClimaTelaTest().previsao()
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        // Sem animação (o caminho "sem movimento" do app): o fio do giro, a Sofy e a troca de linha animam a cada quadro,
        // e cada quadro com gráficos nativos custa caro; com elas ligadas o teste levou mais de 20 minutos sem acabar.
        android.provider.Settings.Global.putFloat(a.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val s = SleepingHome(a)
        a.setContentView(s.montar(), FrameLayout.LayoutParams(-1, -1))
        // A previsão "chega" numa thread (Fundo.rodar), como na TV: espera a faixa ganhar as linhas.
        fun linhas(): Int = ((((a.window.decorView as ViewGroup).findViewById<ViewGroup>(android.R.id.content))))?.let { contar(it) } ?: 0
        repeat(150) { if (linhas() < 5) { Thread.sleep(100); ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS) } }
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        val total = linhas()
        assertTrue("a faixa tem as linhas da previsão (achou $total)", total >= 5)
        val problemas = mutableListOf<String>()
        for (i in 0 until total) {
            problemas += MedirTela.medir(a, "descanso-$i", "descanso-$lingua-$i", 18f, cartao = true).filterNot { it.endsWith("nada tem o foco") }
            s.tecla(); s.mover(1)
            ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        }
        s.soltar()
        assertTrue("sem problema de tela: $problemas", problemas.isEmpty())
        a.finish()
    }

    /** Quantas linhas a janela da faixa tem (o FrameLayout cujos filhos ocupam a mesma área, um por vez). */
    private fun contar(v: ViewGroup): Int {
        if (v is FrameLayout && v.clipChildren && v.childCount >= 2 && v.tag == SleepingHome.TAG_JANELA) return v.childCount
        for (i in 0 until v.childCount) (v.getChildAt(i) as? ViewGroup)?.let { val n = contar(it); if (n > 0) return n }
        return 0
    }
}
