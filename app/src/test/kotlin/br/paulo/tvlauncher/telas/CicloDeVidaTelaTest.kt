package br.paulo.tvlauncher.telas

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.ConfigLauncherActivity
import br.paulo.tvlauncher.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Ciclo de vida (matriz de testes, 24/11/2026), sem rede (o Robolectric não tem): em cada tela principal,
 * 1) a TV dorme e acorda (pause/stop/start/resume) três vezes: não quebra e o foco volta;
 * 2) o sistema mata e recria a tela com o estado salvo (troca de configuração / processo morto): não quebra e há foco;
 * 3) VOLTAR fecha a tela (a HOME não fecha). Só roda com -Ptelas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
@Suppress("UNCHECKED_CAST")
class CicloDeVidaTelaTest {
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
        // Demais telas que abrem sem dados extras (uma IA, o painel ou um atalho podem abri-las direto).
        "addons" to Class.forName("br.paulo.tvlauncher.AddonsActivity") as Class<out Activity>,
        "appsescolha" to Class.forName("br.paulo.tvlauncher.AppsEscolhaActivity") as Class<out Activity>,
        "backupnoturno" to Class.forName("br.paulo.tvlauncher.BackupNoturnoActivity") as Class<out Activity>,
        "bloqueiocanais" to Class.forName("br.paulo.tvlauncher.BloqueioCanaisActivity") as Class<out Activity>,
        "calendario" to Class.forName("br.paulo.tvlauncher.CalendarioActivity") as Class<out Activity>,
        "calibracao" to Class.forName("br.paulo.tvlauncher.CalibracaoActivity") as Class<out Activity>,
        "casa" to Class.forName("br.paulo.tvlauncher.CasaActivity") as Class<out Activity>,
        "cenas" to Class.forName("br.paulo.tvlauncher.CenasActivity") as Class<out Activity>,
        "conectarclaude" to Class.forName("br.paulo.tvlauncher.ConectarClaudeActivity") as Class<out Activity>,
        "cuidardelonge" to Class.forName("br.paulo.tvlauncher.CuidarDeLongeActivity") as Class<out Activity>,
        "dns" to Class.forName("br.paulo.tvlauncher.DnsActivity") as Class<out Activity>,
        "escolherapp" to Class.forName("br.paulo.tvlauncher.EscolherAppActivity") as Class<out Activity>,
        "filaapps" to Class.forName("br.paulo.tvlauncher.FilaAppsActivity") as Class<out Activity>,
        "finetune" to Class.forName("br.paulo.tvlauncher.FineTuneActivity") as Class<out Activity>,
        "gravacoes" to Class.forName("br.paulo.tvlauncher.GravacoesActivity") as Class<out Activity>,
        "iconpicker" to Class.forName("br.paulo.tvlauncher.IconPickerActivity") as Class<out Activity>,
        "licencas" to Class.forName("br.paulo.tvlauncher.LicencasActivity") as Class<out Activity>,
        "midiarede" to Class.forName("br.paulo.tvlauncher.MidiaRedeActivity") as Class<out Activity>,
        "painelblocos" to Class.forName("br.paulo.tvlauncher.PainelBlocosActivity") as Class<out Activity>,
        "painelrapido" to Class.forName("br.paulo.tvlauncher.PainelRapidoActivity") as Class<out Activity>,
        "podcasts" to Class.forName("br.paulo.tvlauncher.PodcastsActivity") as Class<out Activity>,
        "rotinas" to Class.forName("br.paulo.tvlauncher.RotinasActivity") as Class<out Activity>,
        "servicosfilmes" to Class.forName("br.paulo.tvlauncher.ServicosFilmesActivity") as Class<out Activity>,
        "sincronizar" to Class.forName("br.paulo.tvlauncher.SincronizarActivity") as Class<out Activity>,
        "storage" to Class.forName("br.paulo.tvlauncher.StorageActivity") as Class<out Activity>,
        "teclas" to Class.forName("br.paulo.tvlauncher.TeclasActivity") as Class<out Activity>,
        "testeteclas" to Class.forName("br.paulo.tvlauncher.TesteTeclasActivity") as Class<out Activity>,
        "vod" to Class.forName("br.paulo.tvlauncher.VodActivity") as Class<out Activity>,
    )

    private fun esperar(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)
    /** Foco presente, ou a tela não tem NADA focável (lista vazia no teste: anotado em [semFocaveis], não é falha). */
    private val semFocaveis = sortedSetOf<String>()
    private var atual = ""
    private fun focoVolta(a: Activity): Boolean {
        for (i in 0 until 20) { if (a.window.decorView.findFocus() != null) return true; Thread.sleep(50); esperar(100) }
        if (a.window.decorView.getFocusables(android.view.View.FOCUS_DOWN).none { it.isShown }) { semFocaveis += atual; return true }
        return false
    }

    @Test fun dormirAcordarRecriarVoltar() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        br.paulo.tvlauncher.home.SplashOverlay.setEnabled(RuntimeEnvironment.getApplication(), false)
        val problemas = mutableListOf<String>()
        for ((nome, cls) in telas) {
            atual = nome
            var etapa = "abrir"
            var c: ActivityController<out Activity>? = null
            try {
                c = Robolectric.buildActivity(cls).setup()
                esperar(500)
                repeat(3) { n ->
                    etapa = "dormir ${n + 1}"; c.pause().stop(); esperar(1000)
                    etapa = "acordar ${n + 1}"; c.restart().start().resume(); esperar(500)
                    if (!focoVolta(c.get())) problemas += "$nome: sem foco depois de acordar (${n + 1}ª vez)"
                }
                etapa = "recriar"
                val estado = Bundle()
                c.pause().saveInstanceState(estado).stop().destroy()
                c = Robolectric.buildActivity(cls).setup(estado)
                esperar(500)
                if (!focoVolta(c.get())) problemas += "$nome: sem foco depois de recriar com o estado salvo"
                etapa = "voltar"
                val a = c.get()
                a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
                esperar(500)
                if (nome == "home" && a.isFinishing) problemas += "home: VOLTAR fechou a HOME"
                if (nome != "home" && !a.isFinishing) problemas += "$nome: VOLTAR não fechou a tela"
            } catch (e: Throwable) {
                problemas += "$nome: quebrou em '$etapa': ${e.javaClass.simpleName}: ${e.message?.take(160)}"
            } finally {
                try { c?.get()?.finish() } catch (_: Throwable) {}
            }
        }
        println("CICLORESUMO ${telas.size} telas; sem nada focável no teste: $semFocaveis")
        assertTrue("Ciclo de vida:\n" + problemas.joinToString("\n"), problemas.isEmpty())
    }
}
