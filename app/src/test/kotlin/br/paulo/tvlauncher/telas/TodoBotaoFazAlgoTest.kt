package br.paulo.tvlauncher.telas

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast

/**
 * TODO botão de TODA tela principal, apertado com o OK do controle, faz algo que a pessoa vê (Paulo, 25/11/2026: "desce o
 * pau"; o Protetor do painel rápido não fazia nada e nenhum teste apertava os botões).
 *
 * Para cada tela e cada item focável e clicável, numa tela NOVA (o clique anterior não contamina): foco, OK, espera até
 * 3 s. "Algo" = abriu uma tela que EXISTE (resolve no aparelho), fechou a tela (Voltar/OK de diálogo), abriu um diálogo
 * ou um aviso, ou a tela mudou (textos, visibilidade, ligado/desligado, foco em outro item). Nada disso = defeito.
 * Sem shell próprio (o Robolectric não tem adb): é o estado em que os caminhos B aparecem.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
class TodoBotaoFazAlgoTest {
    private val telas: List<Pair<String, Class<out Activity>>> = listOf(
        "configuracoes" to br.paulo.tvlauncher.ConfigLauncherActivity::class.java,
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
        "home" to br.paulo.tvlauncher.MainActivity::class.java,
    )

    private fun esperar(ms: Long) = ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)

    /** Retrato do que se vê: textos visíveis, ligado/desligado, marcado e onde está o foco. */
    private fun retrato(raiz: View): String {
        val sb = StringBuilder()
        fun andar(v: View) {
            if (!v.isShown) return
            if (v is TextView) sb.append(v.text).append('|')
            if (v is android.widget.Checkable) sb.append(if (v.isChecked) "[x]" else "[ ]")
            if (v.isActivated) sb.append("A"); if (v.isSelected) sb.append("S")
            if (v is ViewGroup) for (k in 0 until v.childCount) andar(v.getChildAt(k))
        }
        andar(raiz)
        return sb.toString()
    }

    private fun nome(v: View): String {
        val t = ArrayList<String>()
        fun andar(x: View) { if (x is TextView && x.text.isNotBlank()) t += x.text.toString(); if (x is ViewGroup) for (k in 0 until x.childCount) andar(x.getChildAt(k)) }
        andar(v)
        return (v.contentDescription?.toString()?.takeIf { it.isNotBlank() } ?: t.joinToString(" / ")).take(60)
            .ifBlank { v.javaClass.simpleName + "#" + v.id }
    }

    /** Tem quem ouça a tecla (setOnKeyListener): o OK pode ser tratado ali, sem clique. */
    private fun ouveTecla(v: View): Boolean = try {
        val li = View::class.java.getDeclaredField("mListenerInfo").apply { isAccessible = true }.get(v) ?: return false
        li.javaClass.getDeclaredField("mOnKeyListener").apply { isAccessible = true }.get(li) != null
    } catch (_: Exception) { false }

    private fun clicaveis(a: Activity): List<View> {
        val foc = ArrayList<View>(); a.window.decorView.addFocusables(foc, View.FOCUS_DOWN)
        // TODO focável, não só os com clique: as telas desenhadas à mão (CruzOuvir de Jogos e Rádio, o alarme) tratam o OK
        // na própria View. Ficam de fora só os que não são botão por natureza: rolagem e campo de texto.
        return foc.filter { it.isShown && it.isEnabled && it !is android.widget.ScrollView && it !is android.widget.EditText &&
            it !is androidx.core.widget.NestedScrollView }
    }

    /** O OK do controle de verdade: tecla DPAD_CENTER descendo e subindo na tela, com o item focado. */
    private fun apertarOk(a: Activity) {
        a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_CENTER))
        a.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @org.junit.Before fun telasDoGoogleTv() {
        GoogleTvTelas.registrar()
        // Na TV o Device Owner concede BLUETOOTH_CONNECT sozinho (PrivilegioDO.conceder); aqui o teste faz o papel dele.
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
    }

    @org.junit.Before fun dados() {
        val p = ClimaTelaTest().previsao()
        br.paulo.tvlauncher.PrevisaoActivity.dadosDeTeste = p
        br.paulo.tvlauncher.weather.Forecast.dadosDeTeste = p
    }

    @org.junit.After fun limpar() {
        br.paulo.tvlauncher.PrevisaoActivity.dadosDeTeste = null
        br.paulo.tvlauncher.weather.Forecast.dadosDeTeste = null
    }

    private fun existe(i: android.content.Intent): Boolean {
        // O pedido de permissão é montado pelo SISTEMA com o pacote do permissioncontroller do aparelho (no Robolectric um,
        // no Google TV com.google.android.permissioncontroller); vale a ação, conferida na Sala.
        val j = if (i.action == "android.content.pm.action.REQUEST_PERMISSIONS") android.content.Intent(i).setPackage(null) else i
        return RuntimeEnvironment.getApplication().packageManager.resolveActivity(j, 0) != null
    }

    /**
     * Com -PtelasTodas: TODAS as telas do app (o manifesto inteiro), não só as 16 principais. As que precisam de algo para
     * abrir (um vídeo, um pedido) e não abrem sem isso entram na lista "não abriu sozinha", que não quebra o teste.
     */
    private fun todasAsTelas(): List<Pair<String, Class<out Activity>>> {
        val app = RuntimeEnvironment.getApplication()
        @Suppress("DEPRECATION")
        val info = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_ACTIVITIES)
        val ja = telas.map { it.second.name }.toSet()
        return telas + info.activities.orEmpty().map { it.name }.filter { it !in ja && it !in FORA }.sorted().mapNotNull { n ->
            @Suppress("UNCHECKED_CAST")
            runCatching { Class.forName(n) as Class<out Activity> }.getOrNull()?.let { n.substringAfterLast('.') to it }
        }
    }

    @Test fun todoBotaoFazAlgo() = varrer(telas)

    @Test fun todoBotaoDeTodaTela() {
        org.junit.Assume.assumeTrue(System.getProperty("telas.todas") == "true")
        varrer(todasAsTelas())
    }

    private fun varrer(telas: List<Pair<String, Class<out Activity>>>) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val app = RuntimeEnvironment.getApplication()
        br.paulo.tvlauncher.home.SplashOverlay.setEnabled(app, false)
        val mudos = ArrayList<String>()
        val externos = ArrayList<String>()
        val naoAbriu = ArrayList<String>()
        var apertados = 0
        for ((tela, cls) in telas) {
            val alvos = try {
                val ctl = Robolectric.buildActivity(cls).setup(); esperar(1500)
                val l = clicaveis(ctl.get()).map(::nome)
                runCatching { ctl.pause().stop().destroy() }; l
            } catch (e: Throwable) { naoAbriu += "$tela (${e.javaClass.simpleName}: ${e.message?.take(80)})"; continue }
            val vistos = HashMap<String, Int>()
            for (alvo in alvos.take(MAX_POR_TELA)) {
                val n = vistos.merge(alvo, 1, Int::plus)!!   // o n-ésimo com o mesmo nome
                while (shadowOf(app).nextStartedActivity != null) Unit
                ShadowToast.reset(); ShadowDialog.reset()
                val ctl = try { Robolectric.buildActivity(cls).setup() } catch (e: Throwable) { mudos += "$tela: não abriu"; break }
                val a = ctl.get()
                esperar(1500)
                // A HOME põe o foco na cruz no 1º desenho, que chega depois dos apps (em outra thread): esperar por ele,
                // senão no CI lento ele tira o foco do item que o teste escolheu ("home › Buscar (não pega o foco)").
                if (a is br.paulo.tvlauncher.MainActivity) {
                    val ate = System.currentTimeMillis() + 5000
                    while (a.focoInicialNaTv && System.currentTimeMillis() < ate) { Thread.sleep(50); esperar(50) }
                }
                var v = clicaveis(a).filter { nome(it) == alvo }.getOrNull(n - 1)
                if (v == null) { runCatching { ctl.pause().stop().destroy() }; continue }   // item que só aparece às vezes
                v.requestFocus(); esperar(100)
                // #602: a tela pode se refazer no meio (o Rádio refaz os itens quando a resposta da internet chega, com
                // atraso diferente a cada CI). Item que saiu da janela não é defeito: pega o novo, pelo nome, e tenta de novo.
                // Um item que de fato não pega o foco continua na janela e segue reprovando.
                var refeitas = 0
                while (!v.isFocused && !v.isAttachedToWindow && refeitas++ < 3) {
                    v = clicaveis(a).filter { nome(it) == alvo }.getOrNull(n - 1) ?: break
                    v.requestFocus(); esperar(100)
                }
                if (!v.isFocused && !v.isAttachedToWindow) { runCatching { ctl.pause().stop().destroy() }; continue }   // sumiu de vez
                if (!v.isFocused) { mudos += "$tela › $alvo (não pega o foco)"; runCatching { ctl.pause().stop().destroy() }; continue }
                val antes = retrato(a.window.decorView)
                val dialogoAntes = ShadowDialog.getLatestDialog()
                apertarOk(a); apertados++
                var feito = ""
                val fim = System.currentTimeMillis() + 3000
                while (feito.isEmpty() && System.currentTimeMillis() < fim) {
                    esperar(200)
                    val abertas = listOfNotNull(shadowOf(a).nextStartedActivity, shadowOf(app).nextStartedActivity)
                    feito = when {
                        abertas.any { existe(it) } -> "abriu"
                        abertas.isNotEmpty() -> { externos += "$tela › $alvo → ${abertas.first().toUri(0)}"; "externo" }
                        a.isFinishing -> "fechou"
                        ShadowDialog.getLatestDialog() !== dialogoAntes -> "diálogo"
                        ShadowToast.getLatestToast() != null -> "aviso"
                        retrato(a.window.decorView) != antes -> "mudou"
                        else -> ""
                    }
                    if (feito.isEmpty()) Thread.sleep(50)
                }
                if (feito.isEmpty() && SO_LEITURA.none { (t, prefixo) -> t == tela && alvo.startsWith(prefixo) }) mudos += "$tela › $alvo"
                runCatching { ctl.pause().stop().destroy() }
            }
            println("BOTOES $tela: ${alvos.size} clicáveis")
        }
        println("BOTOES telas=${telas.size} apertados=$apertados externos=${externos.size}\n" + externos.joinToString("\n"))
        println("BOTOES não abriu sozinha (${naoAbriu.size}):\n" + naoAbriu.joinToString("\n"))
        assertTrue("botão que não faz nada ao apertar OK (${mudos.size}):\n" + mudos.joinToString("\n") +
            "\n\nbotão que abre tela de fora NÃO conferida no Google TV (GoogleTvTelas):\n" + externos.joinToString("\n"),
            mudos.isEmpty() && externos.isEmpty())
    }

    private companion object {
        const val MAX_POR_TELA = 200
        /** Telas que tocam som ou mídia de verdade ao abrir (alarme tocando, players, câmera): conferidas nos testes delas. */
        val FORA = setOf("br.paulo.tvlauncher.AlarmeTocandoActivity", "br.paulo.tvlauncher.PlayerActivity",
            "br.paulo.tvlauncher.VodActivity", "br.paulo.tvlauncher.PhoneCameraActivity", "br.paulo.tvlauncher.PodcastPlayerActivity")
        /**
         * Focáveis SÓ PARA LER, declarados um a um com o motivo (como o Dormir do painel rápido). Qualquer outro mudo quebra.
         *  - placar: as 7 linhas dos pilares são a nota de cada um; o foco existe para a lista rolar. As linhas de FALHA
         *    (acima delas, quando há) têm "▶ consertar" e são testadas como botão.
         *  - planos: o QR (APK do site) segura o foco para o VOLTAR funcionar; quem compra lê o QR com o celular.
         *  - recado em vídeo e DNS: ver ao lado de cada um.
         */
        val SO_LEITURA = (1..7).map { "placar" to "$it. " } + listOf("planos" to "Leia com o celular",
            // Lista vazia: a única linha é o aviso "nenhum vídeo", que segura o foco para o VOLTAR.
            "RecadoVideoActivity" to "Nenhum vídeo ainda",
            // A linha MARCADA já está em uso: o OK refaz o teste e mostra "Testando…", que aqui já está na tela porque o teste
            // da abertura não termina sem rede. Na TV ele termina em ~1 s e o OK mostra "Testando…" de novo.
            "DnsActivity" to "●")
    }
}
