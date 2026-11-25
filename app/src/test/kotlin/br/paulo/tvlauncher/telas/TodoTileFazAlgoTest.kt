package br.paulo.tvlauncher.telas

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.robolectric.RuntimeEnvironment
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.PainelRapidoActivity
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Todo bloco do painel rápido, apertado com o OK do controle, FAZ ALGO que a pessoa vê (Sala, 25/11/2026: o Protetor
 * com o shell próprio fora fechava o painel e não fazia nada, sem aviso; a matriz da noite não apertou os blocos).
 *
 * "Algo" = abriu uma tela, ou o próprio bloco mudou (texto, ligado/desligado). Fechar o painel sozinho NÃO conta.
 * Roda nos dois estados do shell: respondendo e fora (null), porque é com ele fora que os caminhos B aparecem.
 * Dormir é a exceção declarada: o efeito é apagar a tela (acessibilidade/Device Owner), conferido à parte.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
class TodoTileFazAlgoTest {
    private val padrao = PainelRapidoActivity.shell

    /**
     * As telas do sistema que EXISTEM no Google TV (Chromecast da Sala, Android 14), conferidas com
     * `pm resolve-activity --brief` em 25/11/2026. android.settings.DREAM_SETTINGS deu "No activity found" e fica de fora.
     */
    @org.junit.Before fun telasDoGoogleTv() {
        val pm = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        for ((acao, comp) in listOf(
            "android.settings.WIFI_SETTINGS" to "com.android.tv.settings/.connectivity.NetworkActivity",
            "com.android.systemui.TV_MEDIA_OUTPUT_DIALOG" to "com.android.systemui/.media.dialog.tv.TvMediaOutputDialogActivity",
            "android.settings.SOUND_SETTINGS" to "com.android.tv.settings/.device.displaysound.DisplaySoundActivity",
            "android.settings.SETTINGS" to "com.android.tv.settings/.MainSettings",
            "parear" to "com.android.tv.settings/.accessories.AddAccessoryActivity",
        )) {
            val c = android.content.ComponentName.unflattenFromString(comp)!!
            pm.addActivityIfNotPresent(c)
            if (acao == "parear") continue
            pm.addIntentFilterForActivity(c, android.content.IntentFilter(acao).apply { addCategory(android.content.Intent.CATEGORY_DEFAULT) })
        }
    }

    // Na TV o Device Owner concede BLUETOOTH_CONNECT sozinho (PrivilegioDO.conceder); aqui o teste faz o papel dele.
    @org.junit.Before fun permissoes() = shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)

    @After fun volta() { PainelRapidoActivity.shell = padrao }

    private fun textos(v: View): String = when (v) {
        is TextView -> v.text.toString() + "|" + v.visibility
        is ViewGroup -> (0 until v.childCount).joinToString(",") { textos(v.getChildAt(it)) } + "|" + v.isActivated + v.isSelected
        else -> ""
    }

    /** A tela existe: é nossa (componente explícito) ou o aparelho tem quem abra o Intent. */
    private fun existe(i: android.content.Intent): Boolean =
        RuntimeEnvironment.getApplication().packageManager.resolveActivity(i, 0) != null

    private fun blocos(): List<Pair<String, Int>> {
        val ctl = Robolectric.buildActivity(PainelRapidoActivity::class.java).setup()
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        val foc = ArrayList<View>(); ctl.get().window.decorView.addFocusables(foc, View.FOCUS_DOWN)
        val r = foc.withIndex().filter { (it.value as? ViewGroup)?.childCount == 2 && it.value.isClickable }
            .map { (it.value as ViewGroup).let { g -> textos(g).substringBefore("|") } to it.index }
        ctl.pause().stop().destroy()
        return r
    }

    private fun confere(shellResponde: Boolean) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val pedidos = ArrayList<String>()
        PainelRapidoActivity.shell = { _, cmd, _ -> synchronized(pedidos) { pedidos += cmd }; if (shellResponde) "Result: Parcel(00000000    '....')" else null }
        val app = RuntimeEnvironment.getApplication()
        val lista = blocos()
        assertTrue("o painel tem blocos: $lista", lista.size >= 6)
        val mudos = ArrayList<String>()
        for ((nome, _) in lista) {
            if (nome.trim(',') == "Dormir") continue
            synchronized(pedidos) { pedidos.clear() }
            while (shadowOf(app).nextStartedActivity != null) Unit
            val ctl = Robolectric.buildActivity(PainelRapidoActivity::class.java).setup()
            val a = ctl.get()
            ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
            val foc = ArrayList<View>(); a.window.decorView.addFocusables(foc, View.FOCUS_DOWN)
            val bloco = foc.filterIsInstance<ViewGroup>().firstOrNull { it.childCount == 2 && it.isClickable && textos(it).substringBefore("|") == nome }
                ?: run { mudos += "$nome (sumiu)"; ctl.pause().stop().destroy(); continue }
            val antes = textos(bloco)
            bloco.requestFocus()
            bloco.performClick()
            // O que roda em segundo plano (Fundo.rodar) volta à tela com ui.post: espera até 3 s.
            var abriu = false
            val fim = System.currentTimeMillis() + 3000
            while (!abriu && System.currentTimeMillis() < fim) {
                ShadowLooper.idleMainLooper(200, TimeUnit.MILLISECONDS)
                // Só conta tela que EXISTE: um Intent sem ninguém para abrir (ex.: ACTION_DREAM_SETTINGS no Google TV) é nada.
                abriu = listOfNotNull(shadowOf(a).nextStartedActivity, shadowOf(app).nextStartedActivity).any { existe(it) } ||
                    (shellResponde && synchronized(pedidos) { pedidos.isNotEmpty() })   // com o shell ok, o efeito é o comando
                if (!abriu) Thread.sleep(50)
            }
            val mudou = textos(bloco) != antes
            if (!abriu && !mudou) mudos += "$nome (shell ${if (shellResponde) "ok" else "fora"})"
            runCatching { ctl.pause().stop().destroy() }
        }
        assertTrue("bloco que não faz nada ao apertar OK: $mudos", mudos.isEmpty())
    }

    @Test fun comShellFora() = confere(shellResponde = false)
    @Test fun comShellOk() = confere(shellResponde = true)
}
