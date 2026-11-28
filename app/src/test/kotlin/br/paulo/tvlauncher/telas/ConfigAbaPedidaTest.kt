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
}
