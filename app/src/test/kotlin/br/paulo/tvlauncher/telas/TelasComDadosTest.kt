package br.paulo.tvlauncher.telas

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.ConfirmacoesTv
import br.paulo.tvlauncher.ConfirmarIaActivity
import br.paulo.tvlauncher.ConfirmarRoteadorActivity
import br.paulo.tvlauncher.DocumentPickerActivity
import br.paulo.tvlauncher.ElencoActivity
import br.paulo.tvlauncher.EscolherAppActivity
import br.paulo.tvlauncher.GuardarDadosActivity
import br.paulo.tvlauncher.IconPickerActivity
import br.paulo.tvlauncher.ModoSimples
import br.paulo.tvlauncher.ModoSimplesActivity
import br.paulo.tvlauncher.PedidoShizukuActivity
import br.paulo.tvlauncher.PerguntaTvActivity
import br.paulo.tvlauncher.PhoneCameraActivity
import br.paulo.tvlauncher.PrevisaoActivity
import br.paulo.tvlauncher.SaudeAvisoActivity
import br.paulo.tvlauncher.VodActivity
import br.paulo.tvlauncher.companion.PhoneCameraTv
import br.paulo.tvlauncher.mcp.PedidoPendente
import br.paulo.tvlauncher.network.UnifiCliente
import br.paulo.tvlauncher.player.ElencoTmdb
import br.paulo.tvlauncher.player.InfoExternaCache
import br.paulo.tvlauncher.settings.Extras
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assume.assumeTrue
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

/**
 * Fotos das telas que só aparecem com dados (pedido da Design, 27/11/2026: a auditoria abre cada tela "vazia" e 26 delas
 * saíam em branco, carregando ou fechavam sozinhas). Aqui cada tela recebe dados falsos de teste (fixtures: apps
 * instalados, pedido da IA, pergunta do painel, elenco, vídeo da rede...) e é fotografada em pt e en, sem rede.
 *
 * Grava app/build/telas/com-dados/<idioma>/<nn-tela>.png e um relatório (com-dados-<idioma>.md) com o que a [MedirTela]
 * achou (letra pequena, texto cortado, sem foco) e com as telas que não deu para montar. NÃO reprova: é o material da
 * revisão visual. Roda só com -Ptelas -PcomDados.
 *
 * Telas da lista que não entram (e por quê) estão em [FORA].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class TelasComDadosTest {

    @Config(qualifiers = "pt-w960dp-h540dp-land-television-xhdpi") @Test fun pt() = fotografar("pt")
    @Config(qualifiers = "en-w960dp-h540dp-land-television-xhdpi") @Test fun en() = fotografar("en")

    @After fun limpar() {
        PrevisaoActivity.dadosDeTeste = null
        camera(null, 0L)
    }

    companion object {
        /** Da lista de 26 da Design: as que não são telas nossas ou não desenham nada. */
        val FORA = listOf(
            "Atalho de voz (AtalhoVozActivity): não desenha; faz o comando e fecha (o aviso que aparece é o AvisoTv)",
            "Rotina de acordar (RotinaAcordarActivity): não desenha; acende a TV e abre o app da rotina",
            "Validação Bluetooth (androidx.media3 BluetoothValidationActivity): tela da biblioteca de mídia, não nossa",
            "Google API (GoogleApiActivity): tela de passagem do Google Play Services, não nossa",
            "Cobrança (ProxyBillingActivity): tela de passagem da Play Billing, não nossa; os planos são a ConhecerProActivity",
            "Pedido Dhizuku: só monta com o Device Owner emprestado ligado e plano pago; conferir na TV",
        )
    }

    private val pastaBase = File("build/telas/com-dados")
    private lateinit var lingua: String
    private var n = 0
    private val relatorio = StringBuilder()

    private fun emPt() = lingua == "pt"
    private fun t(pt: String, en: String) = if (emPt()) pt else en

    private fun fotografar(l: String) {
        assumeTrue(System.getProperty("telas.comdados") == "true")
        lingua = l
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        File(pastaBase, lingua).mkdirs()
        // Sem rede: as fotos não podem depender de servidor de fora.
        System.setProperty("https.proxyHost", "127.0.0.1"); System.setProperty("https.proxyPort", "9")
        System.setProperty("http.proxyHost", "127.0.0.1"); System.setProperty("http.proxyPort", "9")
        relatorio.append("# Telas com dados ($lingua)\n\nFotos em app/build/telas/com-dados/$lingua/.\n\n")
        instalarApps()

        caso("confirmar-ia", ::confirmarIa)
        caso("confirmar-roteador", ::confirmarRoteador)
        caso("pergunta-na-tv", ::perguntaNaTv)
        caso("guardar-dados", ::guardarDados)
        caso("aviso-de-saude", ::avisoDeSaude)
        caso("aviso-de-saude-so-shell", ::avisoDeSaudeShell)
        caso("camera-do-celular", ::cameraDoCelular)
        caso("pedido-shizuku", ::pedidoShizuku)
        caso("escolher-app", ::escolherApp)
        caso("escolher-icone", ::escolherIcone)
        caso("escolher-pasta", ::escolherPasta)
        caso("modo-simples", ::modoSimples)
        caso("elenco", ::elenco)
        caso("video-vod", ::vod)
        caso("filmes") { comDados(br.paulo.tvlauncher.FilmesActivity::class.java, "filmes", listOf(
            "-ficha" to listOf(KeyEvent.KEYCODE_DPAD_CENTER))) }
        caso("series") { comDados(br.paulo.tvlauncher.SeriesActivity::class.java, "series", listOf(
            "-ficha" to listOf(KeyEvent.KEYCODE_DPAD_CENTER))) }
        caso("tv-ao-vivo") { comDados(br.paulo.tvlauncher.PlayerActivity::class.java, "tv-ao-vivo", listOf(
            "-lista" to listOf(KeyEvent.KEYCODE_DPAD_CENTER))) }
        val baixo = KeyEvent.KEYCODE_DPAD_DOWN
        caso("radio") { comDados(br.paulo.tvlauncher.RadioActivity::class.java, "radio", listOf(
            "-recentes" to listOf(baixo), "-perto" to listOf(baixo), "-paises" to listOf(baixo, baixo, baixo))) }
        caso("podcasts") { comDados(br.paulo.tvlauncher.PodcastsActivity::class.java, "podcasts", listOf(
            "-novos" to listOf(baixo), "-meus" to listOf(baixo), "-episodios" to listOf(KeyEvent.KEYCODE_DPAD_CENTER))) }
        caso("clima") { comDados(br.paulo.tvlauncher.PrevisaoActivity::class.java, "clima", listOf(
            "-dias" to listOf(baixo), "-mais" to listOf(baixo))) }
        caso("jogos") { comDados(br.paulo.tvlauncher.JogosActivity::class.java, "jogos", listOf(
            "-cima" to listOf(KeyEvent.KEYCODE_DPAD_UP), "-cima-lado" to listOf(KeyEvent.KEYCODE_DPAD_RIGHT))) }
        // A aba 8 (Avançado) fica de fora: aberta direto pelo EXTRA_ABA, o teste não volta (28/11/2026; a Avançado é
        // fotografada pelo SubmenusTelasTest, entrando pela grade).
        for (aba in 0..7) caso("config-aba-$aba") {
            comDados(br.paulo.tvlauncher.ConfigLauncherActivity::class.java, "config-aba-$aba") {
                it.putExtra(br.paulo.tvlauncher.ConfigLauncherActivity.EXTRA_ABA, aba)
            }
        }
        caso("casa") { comDados(br.paulo.tvlauncher.CasaActivity::class.java, "casa", listOf(
            "-ajuste" to listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER))) }

        relatorio.append("\n## Fora desta lista\n")
        FORA.forEach { relatorio.append("- ").append(it).append('\n') }
        relatorio.append("- Já fotografadas com dados por outros testes de tela (rodar junto): Previsão (ClimaTelaTest), Player de podcast " +
            "(PodcastPlayerScreenTest), lista de canais do Player de TV (PlayerTvTelaTest), Sessão junto e Continuar (JuntoTelaTest), " +
            "Cuidar da tela (CuidarTelaTest), abas do Ouvir (OuvirTelaTest), Jogos (JogosTurmaTelaTest)\n")
        File(pastaBase, "com-dados-$lingua.md").writeText(relatorio.toString())
    }

    /** Roda um caso sem derrubar os outros: o que falhar vai para o relatório. */
    private fun caso(nome: String, f: () -> Unit) {
        n++
        try { f() } catch (e: Throwable) {
            relatorio.append("## ${"%02d".format(n)}-$nome\n- NÃO MONTOU: ${e.javaClass.simpleName}: ${e.message?.lineSequence()?.firstOrNull()?.take(160)}\n\n")
        }
    }

    private fun esperar(ms: Long = 600) {
        repeat((ms / 100).toInt()) { Thread.sleep(100); ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS) }
    }

    private fun tecla(a: Activity, k: Int) {
        val tratou = a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, k)); a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, k))
        // As setas, numa TV, andam o foco pelo ViewRootImpl, que o dispatchKeyEvent da Activity não passa: aqui, à mão.
        val dir = when (k) {
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN; KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT; KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT; else -> null
        }
        if (!tratou && dir != null) a.window.decorView.findFocus()?.focusSearch(dir)?.requestFocus(dir)
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS)
    }

    /** Foto da tela (e da janela por cima, se houver) + as medidas da [MedirTela] no relatório. */
    private fun foto(a: Activity, nome: String, sufixo: String = "") {
        val arquivo = "com-dados/$lingua/${"%02d".format(n)}-$nome$sufixo"
        val problemas = if (a.isFinishing) listOf("a tela fechou sozinha (faltou dado?)") else
            MedirTela.medir(a, nome + sufixo, arquivo, 14f, cartao = false)
        // Janela por cima (CaixaTv/Dialog): desenha no meio, sobre a foto da tela.
        val d = ShadowDialog.getLatestDialog()
        if (d != null && d.isShowing) {
            val raiz = a.window.decorView
            val base = Bitmap.createBitmap(raiz.width.coerceAtLeast(1), raiz.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val c = Canvas(base)
            raiz.draw(c)
            val dv = d.window!!.decorView
            c.save(); c.translate(((raiz.width - dv.width) / 2f), ((raiz.height - dv.height) / 2f)); dv.draw(c); c.restore()
            File("build/telas/$arquivo.png").outputStream().use { base.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        relatorio.append("## ${"%02d".format(n)}-$nome$sufixo\n")
        if (problemas.isEmpty()) relatorio.append("- nada fora das regras da MedirTela\n")
        problemas.forEach { relatorio.append("- ").append(it).append('\n') }
        relatorio.append('\n')
    }

    /** [PhoneCameraTv] só aceita foto pelo WebSocket do celular: o teste põe o quadro direto nos campos. */
    private fun camera(b: Bitmap?, em: Long) {
        PhoneCameraTv::class.java.getDeclaredField("latest").apply { isAccessible = true }.set(PhoneCameraTv, b)
        PhoneCameraTv::class.java.getDeclaredField("lastAt").apply { isAccessible = true }.setLong(PhoneCameraTv, em)
    }

    // ================================================================ dados falsos

    private val apps = listOf(
        "com.exemplo.videos" to ("Vídeos" to "Videos"),
        "com.exemplo.noticias" to ("Notícias 24h" to "News 24h"),
        "com.exemplo.musica" to ("Música" to "Music"),
        "com.exemplo.esportes" to ("Esportes ao vivo" to "Live sports"),
        "com.exemplo.fotos" to ("Fotos da família" to "Family photos"),
        "com.exemplo.receitas" to ("Receitas" to "Recipes"),
        "com.exemplo.icones" to ("Ícones Arredondados" to "Rounded Icons"),
    )

    /** Apps de TV falsos (aparecem no menu de apps, na escolha do botão do controle e no modo simples). */
    private fun instalarApps() {
        val pm = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        apps.forEachIndexed { i, (pacote, nomes) ->
            val ai = ApplicationInfo().apply {
                packageName = pacote; nonLocalizedLabel = t(nomes.first, nomes.second); uid = 10_100 + i
                flags = ApplicationInfo.FLAG_INSTALLED
            }
            pm.installPackage(PackageInfo().apply { packageName = pacote; applicationInfo = ai })
            val c = ComponentName(pacote, "$pacote.Principal")
            pm.addActivityIfNotPresent(c)
            pm.addIntentFilterForActivity(c, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER) })
        }
    }

    /**
     * Abre [cls] com os dados da área ([DadosDeTeste]), fotografa e, para cada passo de [passos] (sufixo da foto e teclas),
     * aperta as teclas e fotografa de novo.
     */
    private fun comDados(cls: Class<out Activity>, nome: String, passos: List<Pair<String, List<Int>>> = emptyList(),
                         extras: (Intent) -> Unit = {}) {
        val app = RuntimeEnvironment.getApplication()
        DadosDeTeste.preparar(app, cls.simpleName, lingua)
        try {
            val a = Robolectric.buildActivity(cls, Intent(app, cls).also(extras)).setup().get()
            esperar(3000); foto(a, nome)
            for ((sufixo, teclas) in passos) {
                teclas.forEach { tecla(a, it) }
                esperar(1500); foto(a, nome, sufixo)
            }
            a.finish()
        } finally { DadosDeTeste.limpar() }
    }

    // ================================================================ telas

    private fun confirmarIa() {
        val app = RuntimeEnvironment.getApplication()
        val i = Intent(app, ConfirmarIaActivity::class.java).putExtra(PedidoPendente.EXTRA_TARDIO, true)
            .putExtra(PedidoPendente.EXTRA_FRASE, t("Abre o YouTube e baixa o volume para 20", "Open YouTube and turn the volume down to 20"))
            .putExtra(PedidoPendente.EXTRA_ORIGEM, "claude")
            .putExtra(PedidoPendente.EXTRA_ACOES, "{\"acoes\":[]}")
            .putExtra(PedidoPendente.EXTRA_DESCRICOES, arrayOf(t("Abrir o app Vídeos", "Open the Videos app"), t("Volume em 20", "Volume to 20")))
        val a = Robolectric.buildActivity(ConfirmarIaActivity::class.java, i).setup().get()
        esperar(); foto(a, "confirmar-ia"); a.finish()
    }

    private fun confirmarRoteador() {
        val f = UnifiCliente::class.java.getDeclaredField("estadoReconectar").apply { isAccessible = true }
        f.set(null, UnifiCliente.EstadoReconectar.ESPERANDO_TV)
        try {
            val a = Robolectric.buildActivity(ConfirmarRoteadorActivity::class.java).setup().get()
            esperar(); foto(a, "confirmar-roteador")
            // Fecha sem responder pelo UnifiCliente (que tentaria o roteador): só some.
            f.set(null, UnifiCliente.EstadoReconectar.NADA)
            a.finish()
        } finally { f.set(null, UnifiCliente.EstadoReconectar.NADA) }
    }

    private fun perguntaNaTv() {
        val app = RuntimeEnvironment.getApplication()
        // A pergunta espera a resposta (bloqueia): fica numa thread, a tela lê a pergunta aberta.
        val th = Thread {
            ConfirmacoesTv.perguntarSempre(app, t("Instalar app pelo painel", "Install an app from the panel"),
                t("Instalar \"Receitas\" (12 MB) nesta TV", "Install \"Recipes\" (12 MB) on this TV"), t("Celular da Ana", "Ana's phone"))
        }.apply { isDaemon = true; start() }
        repeat(30) { if (ConfirmacoesTv.atual() == null) Thread.sleep(100) }
        val a = Robolectric.buildActivity(PerguntaTvActivity::class.java).setup().get()
        esperar(); foto(a, "pergunta-na-tv")
        tecla(a, KeyEvent.KEYCODE_BACK)   // nega: solta a thread
        th.join(3_000)
    }

    private fun guardarDados() {
        val a = Robolectric.buildActivity(GuardarDadosActivity::class.java).setup().get()
        esperar(); foto(a, "guardar-dados")
        ShadowDialog.getLatestDialog()?.dismiss()
    }

    private fun avisoDeSaude() {
        val app = RuntimeEnvironment.getApplication()
        val i = Intent(app, SaudeAvisoActivity::class.java).putExtra(SaudeAvisoActivity.EXTRA_SHELL, true)
            .putExtra(SaudeAvisoActivity.EXTRA_TUNEL, true).putExtra(SaudeAvisoActivity.EXTRA_MINUTOS, 25)
        val a = Robolectric.buildActivity(SaudeAvisoActivity::class.java, i).setup().get()
        esperar(); foto(a, "aviso-de-saude"); a.finish()
    }

    private fun avisoDeSaudeShell() {
        val app = RuntimeEnvironment.getApplication()
        val i = Intent(app, SaudeAvisoActivity::class.java).putExtra(SaudeAvisoActivity.EXTRA_SHELL, true).putExtra(SaudeAvisoActivity.EXTRA_MINUTOS, 3)
        val a = Robolectric.buildActivity(SaudeAvisoActivity::class.java, i).setup().get()
        esperar(); foto(a, "aviso-de-saude-so-shell"); a.finish()
    }

    /** Um "quadro" da câmera do celular: céu, chão e uma janela, desenhados (sem foto de ninguém). */
    private fun cameraDoCelular() {
        val b = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = android.graphics.Paint()
        p.color = 0xFF8DB7D6.toInt(); c.drawRect(0f, 0f, 1280f, 430f, p)
        p.color = 0xFF6E5A47.toInt(); c.drawRect(0f, 430f, 1280f, 720f, p)
        p.color = 0xFFE9E1D3.toInt(); c.drawRect(460f, 140f, 820f, 400f, p)
        p.color = 0xFF3A3A3A.toInt(); c.drawRect(632f, 140f, 648f, 400f, p); c.drawRect(460f, 262f, 820f, 278f, p)
        camera(b, System.currentTimeMillis())
        val a = Robolectric.buildActivity(PhoneCameraActivity::class.java).setup().get()
        a.runOnUiThread { }
        // A imagem chega pelo ouvinte; chama o mesmo caminho.
        PhoneCameraActivity::class.java.getDeclaredMethod("mostrar").apply { isAccessible = true }.invoke(a)
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
        foto(a, "camera-do-celular"); a.finish()
    }

    private fun pedidoShizuku() {
        val app = RuntimeEnvironment.getApplication()
        val ai = app.packageManager.getApplicationInfo("com.exemplo.receitas", 0)
        val i = Intent(app, PedidoShizukuActivity::class.java).putExtra("uid", ai.uid).putExtra("pid", 4321).putExtra("requestCode", 1)
            .putExtra("applicationInfo", ai)
        val ctl = Robolectric.buildActivity(PedidoShizukuActivity::class.java, i)
        val a = ctl.create().get()
        // Sem servidor Shizuku no teste: monta a tela direto (o mesmo que acontece quando o binder chega).
        PedidoShizukuActivity::class.java.getDeclaredMethod("montar", ApplicationInfo::class.java).apply { isAccessible = true }.invoke(a, ai)
        ctl.start().resume().visible()
        esperar(); foto(a, "pedido-shizuku"); a.finish()
    }

    private fun escolherApp() {
        val app = RuntimeEnvironment.getApplication()
        val i = Intent(app, EscolherAppActivity::class.java).putExtra(EscolherAppActivity.EXTRA_TECLA, KeyEvent.KEYCODE_BUTTON_3)
        val a = Robolectric.buildActivity(EscolherAppActivity::class.java, i).setup().get()
        esperar(); foto(a, "escolher-app")
        tecla(a, KeyEvent.KEYCODE_DPAD_DOWN); tecla(a, KeyEvent.KEYCODE_DPAD_DOWN)
        foto(a, "escolher-app", "-foco-3o"); a.finish()
    }

    private fun escolherIcone() {
        val app = RuntimeEnvironment.getApplication()
        val i = Intent(app, IconPickerActivity::class.java).putExtra(IconPickerActivity.EXTRA_APP, "com.exemplo.videos")
            .putExtra(IconPickerActivity.EXTRA_PACOTE, "com.exemplo.icones").putExtra(IconPickerActivity.EXTRA_NOME, t("Vídeos", "Videos"))
        val a = Robolectric.buildActivity(IconPickerActivity::class.java, i).setup().get()
        esperar(1500); foto(a, "escolher-icone"); a.finish()
    }

    private fun escolherPasta() {
        val app = RuntimeEnvironment.getApplication()
        val i = Intent(app, DocumentPickerActivity::class.java).setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
        val a = Robolectric.buildActivity(DocumentPickerActivity::class.java, i).setup().get()
        esperar(1500); foto(a, "escolher-pasta")
        val j = Intent(app, DocumentPickerActivity::class.java).setAction(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
        val b = Robolectric.buildActivity(DocumentPickerActivity::class.java, j).setup().get()
        esperar(1500); foto(b, "escolher-pasta", "-arquivo"); a.finish(); b.finish()
    }

    private fun modoSimples() {
        val app = RuntimeEnvironment.getApplication()
        Extras.definir(app, Extras.Chave.MODO_SIMPLES, true)
        ModoSimples.definirEscolhidos(app, listOf("com.exemplo.videos", "com.exemplo.noticias", "com.exemplo.musica", "com.exemplo.fotos"))
        PrevisaoActivity.dadosDeTeste = ClimaTelaTest().previsao()
        try {
            val a = Robolectric.buildActivity(ModoSimplesActivity::class.java).setup().get()
            esperar(2500); foto(a, "modo-simples")
            tecla(a, KeyEvent.KEYCODE_DPAD_RIGHT)
            foto(a, "modo-simples", "-foco-2o"); a.finish()
        } finally {
            Extras.definir(app, Extras.Chave.MODO_SIMPLES, false)
            PrevisaoActivity.dadosDeTeste = null
        }
    }

    /** O elenco vem do TMDB: põe a resposta na memória do cache (a mesma chave que a tela pede), sem rede. */
    private fun elenco() {
        val id = 120
        @Suppress("UNCHECKED_CAST")
        val pedidos = InfoExternaCache::class.java.getDeclaredField("pedidos").apply { isAccessible = true }.get(InfoExternaCache) as MutableMap<String, Any>
        val pessoas = listOf(
            "Elijah Wood" to "Frodo Bolseiro|Frodo Baggins", "Ian McKellen" to "Gandalf", "Viggo Mortensen" to "Aragorn",
            "Sean Astin" to "Samwise Gamgi|Samwise Gamgee", "Cate Blanchett" to "Galadriel", "Orlando Bloom" to "Legolas",
            "Sean Bean" to "Boromir", "Liv Tyler" to "Arwen",
        ).mapIndexed { i, (nome, papel) ->
            val pap = papel.split('|').let { if (emPt()) it.first() else it.last() }
            ElencoTmdb.Pessoa(1000 + i, nome, pap, "")
        }
        synchronized(pedidos) {
            pedidos["elenco:false:$id"] = pessoas
            pessoas.forEach { pedidos["pessoa:${it.id}"] = ElencoTmdb.Creditos(emptySet(), emptySet()) }
        }
        val app = RuntimeEnvironment.getApplication()
        val i = Intent(app, ElencoActivity::class.java).putExtra("tmdb", id).putExtra("serie", false)
            .putExtra("nome", t("O Senhor dos Anéis: A Sociedade do Anel", "The Lord of the Rings: The Fellowship of the Ring"))
        val a = Robolectric.buildActivity(ElencoActivity::class.java, i).setup().get()
        esperar(2000); foto(a, "elenco")
        tecla(a, KeyEvent.KEYCODE_DPAD_RIGHT); esperar(800)
        foto(a, "elenco", "-foco-2o"); a.finish()
    }

    /** Vídeo da rede da casa (sem tocar de verdade): a cortina com o título e a barra do player. */
    private fun vod() {
        val app = RuntimeEnvironment.getApplication()
        val i = Intent(app, VodActivity::class.java).putExtra("url", "http://192.168.0.20:8096/videos/ferias-2025.mkv")
            .putExtra("titulo", t("Férias em família 2025", "Family vacation 2025"))
            .putExtra(VodActivity.EXTRA_CORTINA_LINHA, t("Vídeos da casa · 1 h 12 min", "Home videos · 1 h 12 min"))
        val a = Robolectric.buildActivity(VodActivity::class.java, i).setup().get()
        esperar(1500); foto(a, "video-vod")
        tecla(a, KeyEvent.KEYCODE_DPAD_CENTER); esperar(800)
        foto(a, "video-vod", "-barra"); a.finish()
    }
}
