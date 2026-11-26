package br.paulo.tvlauncher.observer

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import br.paulo.tvlauncher.AutorizaShizukuService
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.ProtetorTela
import br.paulo.tvlauncher.alarm.NaoPerturbe
import java.util.Calendar

/**
 * A TV confere as próprias telas de madrugada (parte C, 25/11/2026). Abre [POR_NOITE] telas nossas por noite, lê a árvore de
 * acessibilidade, passa pela régua pura ([SofyScreenCheck]) e volta para a home. Um defeito vira caso SCREEN_BROKEN:
 * a caixa de ferramentas não conserta código, então ele segue para o relatório (e para a issue da tarefa diária).
 *
 * Freios (a TV é da casa, não bancada): só com a tela APAGADA ou o nosso protetor na frente, só na madrugada da casa,
 * só com a acessibilidade de pé (é ela que lê), uma rodada por noite, e volta para a home no fim. Com a tela apagada a
 * árvore vem vazia, então ela acende a tela (e a TV pelo HDMI) por uns 15 s, com um véu preto por cima, e apaga de
 * novo (Paulo, 26/11/2026).
 * Durante a rodada segura a tela acesa (sem protetor por cima e sem o "desligar sem uso"). Se alguém apertar uma
 * tecla no meio, para na hora e deixa a TV acesa.
 */
object SofyScreenCheckTv {
    private const val TAG = "TvLauncher.SofyTelas"
    private const val PREFS = "sofy_repair"
    /**
     * As telas, na ordem: uma por noite, em uma semana e meia passa por todas e começa de novo. São telas que só leem
     * (abrir não muda nada na casa): nada que grave, toque som ou mexa em outro app.
     */
    private val TELAS: List<Pair<String, Class<*>>> = listOf(
        "previsao" to br.paulo.tvlauncher.PrevisaoActivity::class.java,
        "agenda" to br.paulo.tvlauncher.CalendarioActivity::class.java,
        "despertador" to br.paulo.tvlauncher.AlarmesActivity::class.java,
        "config" to br.paulo.tvlauncher.ConfigLauncherActivity::class.java,
        "jogos" to br.paulo.tvlauncher.JogosActivity::class.java,
        "podcasts" to br.paulo.tvlauncher.PodcastsActivity::class.java,
        "filmes" to br.paulo.tvlauncher.FilmesActivity::class.java,
        "series" to br.paulo.tvlauncher.SeriesActivity::class.java,
        "diagnostico" to br.paulo.tvlauncher.DiagnosticoActivity::class.java,
        "armazenamento" to br.paulo.tvlauncher.StorageActivity::class.java,
        "ajuda" to br.paulo.tvlauncher.AjudaActivity::class.java,
        "sobre" to br.paulo.tvlauncher.AboutActivity::class.java,
    )
    private const val ESPERA_MS = 2_500L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Quantas telas por madrugada. Acender a TV (e a LG pelo HDMI) custa: várias de uma vez, rodando a lista em 4 noites.
     * A IA olha cada tela lida (revisão + segunda opinião), então o número também segura o gasto da chave do dono.
     */
    private const val POR_NOITE = 3
    /** Quanto esperar a tela acender depois do pedido. */
    private const val ACENDER_MS = 10_000L

    /** Chamado pela batida da Sofy. Devolve true se começou uma rodada agora. */
    fun talvez(ctx: Context): Boolean {
        val app = ctx.applicationContext
        val s = AutorizaShizukuService.instancia ?: return false
        if (!paradaDeVerdade(app)) return false
        val cal = Calendar.getInstance()
        if (!NaoPerturbe.madrugada(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))) return false
        val p = prefs(app)
        val hoje = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        if (p.getString("telas_dia", "") == hoje) return false
        p.edit().putString("telas_dia", hoje).apply()
        val i = p.getInt("telas_i", 0)
        val lista = (0 until POR_NOITE).map { TELAS[(i + it) % TELAS.size] }
        p.edit().putInt("telas_i", i + POR_NOITE).apply()
        Fundo.rodar("sofy-telas") { rodada(app, s, lista) }
        return true
    }

    /**
     * Pelo adb, para teste (Paulo, 26/11/2026: "testar todos os modelos com força"): confere AGORA as telas pedidas
     * (nomes de [TELAS]; vazio = todas), com a revisão da IA e a segunda opinião em cada uma. Mesmos freios da noite,
     * menos a hora: só com a TV parada; dormindo, acende a tela como de madrugada.
     */
    fun agora(ctx: Context, nomes: List<String>, capture: Boolean = false): String {
        val app = ctx.applicationContext
        val s = AutorizaShizukuService.instancia ?: return "sem acessibilidade"
        if (!paradaDeVerdade(app)) return "a TV está em uso: só com a tela apagada ou o protetor"
        val lista = TELAS.filter { nomes.isEmpty() || it.first in nomes }
        if (lista.isEmpty()) return "telas: " + TELAS.joinToString(",") { it.first }
        Fundo.rodar("sofy-telas-agora") { rodada(app, s, lista, capture) }
        return "conferindo ${lista.size} telas: " + lista.joinToString(",") { it.first }
    }

    /**
     * Uma rodada: com a TV dormindo, acende a tela (Paulo, 26/11/2026: dormindo a árvore da acessibilidade vem vazia e a
     * conferência não lia nada, em silêncio), lê as telas, volta para a home e apaga de novo. A IA só olha DEPOIS, com a
     * TV já apagada: a tela fica acesa o mínimo. Se alguém apertar uma tecla no meio, para e deixa a TV acesa para a pessoa.
     */
    private fun rodada(app: Context, s: AutorizaShizukuService, lista: List<Pair<String, Class<*>>>, capture: Boolean = false) {
        val pm = app.getSystemService(android.os.PowerManager::class.java)
        val acendi = pm?.isInteractive == false
        val inicio = android.os.SystemClock.elapsedRealtime()
        // Véu preto por cima de tudo (Paulo, 26/11/2026): de madrugada a sala não vê as telas passando. É uma janela da
        // acessibilidade sem foco: a tela de baixo continua ativa e a árvore dela é lida normalmente.
        val veu = cobrir(s)
        if (acendi && !acender(app, pm)) { descobrir(veu); Log.w(TAG, "não consegui acender a tela: telas não conferidas"); return }
        // Só tecla do controle conta como "alguém chegou": abrir a nossa tela por cima do protetor já o fecha.
        fun mexeram() = br.paulo.tvlauncher.Rotinas.teclaEm() > inicio
        // Segura a TV acesa até o fim (Paulo, 26/11/2026): nem o protetor por cima, nem o "desligar sem uso" no meio.
        @Suppress("DEPRECATION")
        val acesa = runCatching { pm?.newWakeLock(android.os.PowerManager.SCREEN_DIM_WAKE_LOCK, "tvlauncher:sofy-telas") }.getOrNull()
        val prazo = lista.size * (ESPERA_MS + 3_000L) + 60_000L
        runCatching { acesa?.acquire(prazo) }
        br.paulo.tvlauncher.alarm.AutoDesligar.segurar(prazo)
        val lidas = ArrayList<SofyScreenCheck.Tela>()
        var parei = false
        try {
            for ((nome, classe) in lista) {
                if (mexeram()) { parei = true; break }
                runCatching { ler(app, s, nome, classe) }.onFailure { Log.w(TAG, "$nome: $it") }.getOrNull()?.let { lidas += it }
                voltar(s)
            }
        } finally {
            voltar(s)
            parei = parei || mexeram()
            br.paulo.tvlauncher.alarm.AutoDesligar.segurar(0)
            if (acendi && !parei) Thread.sleep(1_500)   // a home atrás do véu antes de apagar (não pisca a cruz)
            descobrir(veu)
            runCatching { if (acesa?.isHeld == true) acesa.release() }
            if (acendi && !parei) br.paulo.tvlauncher.Rotinas.desligar(app)
        }
        Log.i(TAG, "li ${lidas.size} de ${lista.size} telas" + (if (acendi) " (acendi a tela)" else "") + (if (parei) "; alguém mexeu: parei" else ""))
        if (lidas.size < lista.size && !parei) ObserverTv.logShadow(app, "self_defect", "conferência de telas: li ${lidas.size} de ${lista.size}")
        // Banco de prova (SofyBench): só guarda os textos para todos os modelos lerem a mesma tela; não analisa nem grava regra.
        if (capture) { for (t in lidas) SofyBenchTv.capture(t.nome, t.caixas.map { it.texto }); return }
        for (t in lidas) runCatching { analisar(app, t) }.onFailure { Log.w(TAG, "${t.nome}: $it") }
    }

    private val principal = android.os.Handler(android.os.Looper.getMainLooper())

    /** Põe o véu preto e espera ele estar na tela (até 2 s). null = não deu (segue sem véu). */
    private fun cobrir(s: AutorizaShizukuService): android.view.View? {
        val pronto = java.util.concurrent.CountDownLatch(1)
        var v: android.view.View? = null
        principal.post {
            runCatching {
                val p = android.view.WindowManager.LayoutParams(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.OPAQUE)
                val preto = android.view.View(s).apply { setBackgroundColor(android.graphics.Color.BLACK); importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                s.getSystemService(android.view.WindowManager::class.java).addView(preto, p)
                v = preto
            }.onFailure { Log.w(TAG, "véu: $it") }
            pronto.countDown()
        }
        pronto.await(2, java.util.concurrent.TimeUnit.SECONDS)
        return v
    }

    private fun descobrir(v: android.view.View?) {
        v ?: return
        principal.post { runCatching { v.context.getSystemService(android.view.WindowManager::class.java).removeView(v) } }
    }

    /** Acende a tela pela tela invisível das rotinas (o mesmo caminho do despertador) e espera ela acender. */
    private fun acender(app: Context, pm: android.os.PowerManager?): Boolean {
        runCatching { app.startActivity(Intent(app, br.paulo.tvlauncher.RotinaAcordarActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)) }.onFailure { return false }
        val limite = android.os.SystemClock.elapsedRealtime() + ACENDER_MS
        while (android.os.SystemClock.elapsedRealtime() < limite) {
            if (pm?.isInteractive == true) { Thread.sleep(2_000); return true }   // a home da rotina abre 1,5 s depois
            Thread.sleep(250)
        }
        return pm?.isInteractive == true
    }

    /** Tela apagada ou o nosso protetor na frente: ninguém está vendo. */
    private fun paradaDeVerdade(app: Context): Boolean {
        val pm = app.getSystemService(android.os.PowerManager::class.java)
        return pm?.isInteractive == false || ProtetorTela.aberto()
    }

    /** Abre a tela e lê o que ela mostra. null = não deu para ler (outra janela na frente). */
    private fun ler(app: Context, s: AutorizaShizukuService, nome: String, classe: Class<*>): SofyScreenCheck.Tela? {
        app.startActivity(Intent(app, classe).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(ESPERA_MS)
        val raiz = runCatching { s.rootInActiveWindow }.getOrNull()
        if (raiz == null || raiz.packageName?.toString() != app.packageName) {
            Log.w(TAG, "$nome: não deu para ler (janela ${raiz?.packageName ?: "nenhuma"})")
            return null
        }
        return ler(raiz, nome)
    }

    private fun analisar(app: Context, tela: SofyScreenCheck.Tela) {
        val nome = tela.nome
        // Revisor noturno (plano D, passo 3): regras aprendidas + IA do dono olhando o que a tela DIZ.
        runCatching { SofyRulesTv.onScreen(app, nome, tela.caixas.map { it.texto }) }.onFailure { Log.w(TAG, "revisor: $it") }
        // O que a Revisão ensinou (#502): o SENTIDO do que a tela diz (feriado de fora, evento repetido, "498783 h", 140%).
        runCatching { conteudo(app, tela) }.onFailure { Log.w(TAG, "conteúdo: $it") }
        val achados = SofyScreenCheck.conferir(tela)
        if (achados.isEmpty()) { Log.i(TAG, "$nome: ok (${tela.caixas.size} textos)"); return }
        val resumo = SofyScreenCheck.resumo(nome, achados)
        Log.w(TAG, resumo)
        ObserverTv.logShadow(app, "self_defect", resumo)   // a tarefa diária abre a issue a partir daqui
        SofyRepairTv.signal(app, SofyRepair.Type.SCREEN_BROKEN, "screen:$nome", achados.first().defeito.name)
    }

    /**
     * Conferência do conteúdo ([SofyContentCheck]) com o que a TV sabe agora: país, agenda crua e agendas assinadas.
     * Defeito de dado (velho) vira INCOHERENT (o conserto atualiza os dados); o resto é de código e vai para o relato.
     * Ainda sem a temperatura e a chuva de agora no retrato (as regras que usam isso ficam caladas aqui).
     */
    private fun conteudo(app: Context, tela: SofyScreenCheck.Tela) {
        val agora = System.currentTimeMillis()
        val contas = br.paulo.tvlauncher.alarm.Agenda.contas(app)
        val retrato = SofyContentCheck.Retrato(
            pais = br.paulo.tvlauncher.alarm.FeriadosTv.pais(),
            eventos = br.paulo.tvlauncher.alarm.Agenda.eventos(app, contas, agora - 86_400_000L, agora + 14 * 86_400_000L, cru = true),
            agendas = br.paulo.tvlauncher.alarm.Agenda.agendasAssinadas(app, contas),
        )
        val achados = SofyContentCheck.conferir(listOf(tela), retrato)
        if (achados.isEmpty()) return
        val resumo = SofyContentCheck.resumo(achados)
        Log.w(TAG, "${tela.nome}: $resumo")
        ObserverTv.logShadow(app, "self_defect", "conteúdo de ${tela.nome}: $resumo")
        for (a in achados.distinctBy { it.defeito }) {
            val tipo = if (a.defeito in SofyContentCheck.deDado) SofyRepair.Type.INCOHERENT else SofyRepair.Type.SCREEN_BROKEN
            SofyRepairTv.signal(app, tipo, "content:${tela.nome}:${a.defeito.name.lowercase()}", a.onde.take(80))
        }
    }

    private fun voltar(s: AutorizaShizukuService) {
        runCatching { s.performGlobalAction(AccessibilityNodeInfo.ACTION_DISMISS) }
        runCatching { s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) }
    }

    /** Lê a árvore: cada nó com texto vira uma caixa; guarda se existe algo focável. */
    private fun ler(raiz: AccessibilityNodeInfo, nome: String): SofyScreenCheck.Tela {
        val tudo = Rect().also { raiz.getBoundsInScreen(it) }
        val caixas = ArrayList<SofyScreenCheck.Caixa>()
        var foco = false
        fun andar(n: AccessibilityNodeInfo?, nivel: Int) {
            if (n == null || nivel > 25) return
            if (n.isFocusable) foco = true
            val t = (n.text ?: n.contentDescription)?.toString()
            // Só o nó folha: um ViewGroup repete o texto dos filhos e sobreporia tudo.
            if (!t.isNullOrBlank() && n.childCount == 0) {
                val r = Rect().also { n.getBoundsInScreen(it) }
                caixas += SofyScreenCheck.Caixa(t, r.left, r.top, r.right, r.bottom)
            }
            for (k in 0 until n.childCount) andar(runCatching { n.getChild(k) }.getOrNull(), nivel + 1)
        }
        andar(raiz, 0)
        return SofyScreenCheck.Tela(nome, tudo.width(), tudo.height(), caixas, foco)
    }
}
