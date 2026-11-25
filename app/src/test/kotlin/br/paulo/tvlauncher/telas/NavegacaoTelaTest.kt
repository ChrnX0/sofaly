package br.paulo.tvlauncher.telas

import android.app.Activity
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.ConfigLauncherActivity
import br.paulo.tvlauncher.MainActivity
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
 * Navegação com o controle (matriz de testes, 24/11/2026): em cada tela principal, ▼ ▶ ▲ ◀ várias vezes, como alguém
 * explorando. Depois de cada tecla o foco tem de existir, estar visível e dentro da tela; e quem tem mais de um item
 * focável tem de conseguir sair do primeiro. Só roda com -Ptelas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
class NavegacaoTelaTest {
    private val telas: List<Pair<String, Class<out Activity>>> = listOf(
        "home" to MainActivity::class.java,
        "configuracoes" to ConfigLauncherActivity::class.java,
        "filmes" to br.paulo.tvlauncher.FilmesActivity::class.java,
        "series" to br.paulo.tvlauncher.SeriesActivity::class.java,
        "ajuda" to br.paulo.tvlauncher.AjudaActivity::class.java,
        "previsao" to br.paulo.tvlauncher.PrevisaoActivity::class.java,
        "despertador" to br.paulo.tvlauncher.AlarmesActivity::class.java,
        "jogos" to br.paulo.tvlauncher.JogosActivity::class.java,
        "planos" to br.paulo.tvlauncher.ConhecerProActivity::class.java,
        "placar" to br.paulo.tvlauncher.PlacarActivity::class.java,
        "extras" to br.paulo.tvlauncher.ExtrasActivity::class.java,
        "automacoes" to br.paulo.tvlauncher.AutomacoesActivity::class.java,
        "busca" to br.paulo.tvlauncher.BuscaActivity::class.java,
        "diagnostico" to br.paulo.tvlauncher.DiagnosticoActivity::class.java,
        "radio" to br.paulo.tvlauncher.RadioActivity::class.java,
        "sobre" to br.paulo.tvlauncher.AboutActivity::class.java,
    )

    private fun esperar(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    @Test fun focoNuncaSome() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val app = RuntimeEnvironment.getApplication()
        br.paulo.tvlauncher.home.SplashOverlay.setEnabled(app, false)
        val problemas = mutableListOf<String>()
        for ((nome, cls) in telas) {
            val a = try { Robolectric.buildActivity(cls).setup().get() } catch (e: Throwable) {
                problemas += "$nome: não abriu (${e.javaClass.simpleName})"; continue
            }
            for (i in 0 until 20) { if (a.window.decorView.findFocus() != null) break; Thread.sleep(50); esperar(100) }
            val raiz = a.window.decorView
            val tela = Rect(0, 0, raiz.width, raiz.height)
            val vistos = HashSet<View>()
            var focaveis = 0
            fun contar(v: View) { if (v.visibility != View.VISIBLE) return; if (v.isFocusable && v.isShown) focaveis++
                if (v is android.view.ViewGroup) for (k in 0 until v.childCount) contar(v.getChildAt(k)) }
            contar(raiz)
            raiz.findFocus()?.let { vistos += it }
            val teclas = List(6) { KeyEvent.KEYCODE_DPAD_DOWN } + List(6) { KeyEvent.KEYCODE_DPAD_RIGHT } +
                List(6) { KeyEvent.KEYCODE_DPAD_UP } + List(6) { KeyEvent.KEYCODE_DPAD_LEFT }
            for ((n, k) in teclas.withIndex()) {
                if (a.isFinishing) { problemas += "$nome: a tela fechou com ${KeyEvent.keyCodeToString(k)} (tecla ${n + 1})"; break }
                // Como o ViewRootImpl na TV: a tecla vai para a tela; se ninguém a usou, o sistema procura o vizinho na
                // direção (focusSearch). O Activity.dispatchKeyEvent sozinho não faz essa 2ª parte.
                val usou = a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, k))
                if (!usou) raiz.findFocus()?.let { atual ->
                    val dir = when (k) { KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN; KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
                        KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT; else -> View.FOCUS_RIGHT }
                    atual.focusSearch(dir)?.requestFocus(dir)
                }
                a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, k))
                esperar(400)
                val f = raiz.findFocus()
                if (f == null) { problemas += "$nome: foco sumiu depois de ${KeyEvent.keyCodeToString(k)} (tecla ${n + 1})"; break }
                val r = Rect()
                if (!f.isShown || !f.getGlobalVisibleRect(r) || !Rect.intersects(r, tela)) {
                    problemas += "$nome: foco fora da tela/invisível depois de ${KeyEvent.keyCodeToString(k)} (${f.javaClass.simpleName} ${f.contentDescription ?: ""})"; break
                }
                vistos += f
            }
            println("NAVRESUMO $nome focáveis=$focaveis visitados=${vistos.size}")
            if (focaveis > 1 && vistos.size < 2) problemas += "$nome: o foco não sai do primeiro item ($focaveis focáveis)"
            a.finish()
        }
        assertTrue("Navegação:\n" + problemas.joinToString("\n"), problemas.isEmpty())
    }
}
