package br.paulo.tvlauncher.telas

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.ConfigLauncherActivity
import org.junit.Assert.assertEquals
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
 * Configurações abertas direto numa aba (atalho de outra tela, EXTRA_ABA) mostram ESSA aba, também quando ela não é a
 * primeira da porta. Antes, a aba 5 (Acesso remoto) abria na Casa e a 7 (Ajuda) no Sistema: o foco inicial do Android
 * caía no 1º item do menu e trocava a aba (fotos com dados, 28/11/2026). Só roda com -Ptelas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
class ConfigAbaPedidaTest {
    @Test fun abreNaAbaPedida() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val app = RuntimeEnvironment.getApplication()
        br.paulo.tvlauncher.home.SplashOverlay.setEnabled(app, false)
        // Sem escolha salva o uso é o Completo, que mostra todas as abas no menu.
        for (aba in listOf(1, 5, 7)) {
            val intent = Intent(app, ConfigLauncherActivity::class.java).putExtra(ConfigLauncherActivity.EXTRA_ABA, aba)
            val a = Robolectric.buildActivity(ConfigLauncherActivity::class.java, intent).setup().get()
            ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS)
            assertEquals("aba atual ao abrir na $aba", aba, a.abaAtual)
            assertEquals("aba desenhada ao abrir na $aba", aba, a.abaMontada)
            a.finish()
        }
    }

    /**
     * Porta Sistema (Sistema, Ajuda, Avançado) na coluna estreita: nenhuma aba parte palavra no meio. Na Sala (28/11/2026)
     * saía "Siste/ma", "Aju/da", "Avança/do": nem o mínimo cabia com o ícone. Agora, sem caber, a aba perde o ícone.
     */
    @Test fun abasDoSistemaNaoPartemPalavra() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val app = RuntimeEnvironment.getApplication()
        br.paulo.tvlauncher.home.SplashOverlay.setEnabled(app, false)
        val intent = Intent(app, ConfigLauncherActivity::class.java).putExtra(ConfigLauncherActivity.EXTRA_ABA, 6)
        val a = Robolectric.buildActivity(ConfigLauncherActivity::class.java, intent).setup().get()
        ShadowLooper.idleMainLooper(1500, TimeUnit.MILLISECONDS)
        val visiveis = a.itensMenu.filter { it.visibility == android.view.View.VISIBLE && it.isShown }
        org.junit.Assert.assertTrue("a porta Sistema tem mais de uma aba", visiveis.size > 1)
        for (t in visiveis) {
            val l = t.layout ?: continue
            val texto = t.text.toString()
            for (linha in 0 until l.lineCount - 1) {
                val fim = l.getLineEnd(linha)
                val partiu = fim in 1 until texto.length && !texto[fim - 1].isWhitespace() && !texto[fim].isWhitespace()
                org.junit.Assert.assertFalse("a aba \"$texto\" partiu a palavra (linha ${linha + 1} até \"${texto.substring(0, fim)}\")", partiu)
            }
        }
        a.finish()
    }
}
