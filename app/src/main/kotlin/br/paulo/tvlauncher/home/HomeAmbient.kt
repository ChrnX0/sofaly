package br.paulo.tvlauncher.home

import android.content.Intent
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import br.paulo.tvlauncher.Desligamento
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.MainActivity
import br.paulo.tvlauncher.MainActivity.Item
import br.paulo.tvlauncher.resources.R
import br.paulo.tvlauncher.family.ModoVisita
import br.paulo.tvlauncher.family.Perfil
import br.paulo.tvlauncher.family.PerfilCriancaTv
import br.paulo.tvlauncher.games.JogosTv
import br.paulo.tvlauncher.screensaver.WallpaperPluginView

private const val WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
private const val MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT

/** Onda 3 da cruz (CruzOnda3): ambiente, paralaxe, "Quem está vendo?" e "Dormir em 30 min". Saiu da MainActivity na refatoração de 21/11/2026. */

internal fun MainActivity.reagendarAmbiente() {
    ui.removeCallbacks(tarefaAmbiente)
    if (cruz != null && homeNaFrente && CruzOnda3.ligado(this, CruzOnda3.CHAVE_AMBIENTE)) ui.postDelayed(tarefaAmbiente, CruzOnda3.ambienteMs(this))
}

internal fun MainActivity.tocandoAgora() = capsulaPronta && capsulaTocando.visibility == View.VISIBLE && barrasTocando.tocando

internal fun MainActivity.entrarAmbiente() {
    val c = cruz ?: return
    if (ambiente || !homeNaFrente) return
    if (!CruzOnda3.entraAmbiente(CruzOnda3.ligado(this, CruzOnda3.CHAVE_AMBIENTE), c.hasFocus() || c.isFocused, todosAbertos,
            menuAberto?.isShowing == true, tocandoAgora())) { reagendarAmbiente(); return }
    ambiente = true
    (fundo as? WallpaperPluginView)?.ocioso(true)
    val d = CruzOnda3.AMBIENTE_FADE_MS
    val alfa = CruzOnda3.ambienteAlfa(this)
    c.animate().alpha(alfa).setDuration(d).start()
    topoRaiz?.animate()?.alpha(alfa)?.setDuration(d)?.start()
    relogioCruz?.animate()?.alpha(0f)?.setDuration(d)?.start()
    if (!CruzOnda3.ambienteHora(this)) { c.announceForAccessibility(getString(R.string.cruz_ambiente)); return }
    // A hora grande no canto livre de baixo (a mesma da barra "Relógio grande"), com a data e o tempo.
    val r = relogioAmbiente ?: montarRelogioCruz().also { v ->
        v.alpha = 0f
        raizCruz?.addView(v, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = dp(Grade.MARGEM); bottomMargin = dp(40)
        })
        relogioAmbiente = v
    }
    r.visibility = View.VISIBLE
    r.animate().alpha(1f).setDuration(d).start()
    c.announceForAccessibility(getString(R.string.cruz_ambiente))
}

/** Volta exatamente onde estava (a cruz não mudou de lugar; só a transparência). */
internal fun MainActivity.sairAmbiente(animar: Boolean = true) {
    if (!ambiente) return
    ambiente = false
    (fundo as? WallpaperPluginView)?.ocioso(false)
    val d = if (animar) CruzOnda3.VOLTA_MS else 0L
    cruz?.animate()?.alpha(1f)?.setDuration(d)?.start()
    topoRaiz?.animate()?.alpha(1f)?.setDuration(d)?.start()
    relogioCruz?.animate()?.alpha(if (barraRecolhida) 1f else 0.35f)?.setDuration(d)?.start()
    relogioAmbiente?.animate()?.alpha(0f)?.setDuration(d)?.withEndAction { relogioAmbiente?.visibility = View.GONE }?.start()
    reagendarAmbiente()
}

/** Antes de tudo no dispatchKeyEvent: no modo ambiente, a 1ª tecla só acorda a cruz (volume passa direto). */
internal fun MainActivity.teclaDoAmbiente(e: KeyEvent): Boolean {
    val volume = e.keyCode == KeyEvent.KEYCODE_VOLUME_UP || e.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || e.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
    if (ambiente && !volume) {
        if (e.action == KeyEvent.ACTION_DOWN) { engolirTecla = e.keyCode; sairAmbiente() }
        return true
    }
    if (e.keyCode == engolirTecla && e.action == KeyEvent.ACTION_UP) { engolirTecla = -1; return true }
    if (e.keyCode == engolirTecla) return true   // repetição da mesma tecla segurada
    if (e.action == KeyEvent.ACTION_DOWN) reagendarAmbiente()
    return false
}

/** Paralaxe: a arte anda [CruzOnda3.PARALAXE_DP] para o lado oposto da tecla e volta devagar. */
internal fun MainActivity.deslizarArte(t: CruzLogica.Tecla) {
    if (!capaPronta || capa.desligada || !CruzOnda3.ligado(this, CruzOnda3.CHAVE_PARALAXE)) return
    if (android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) return
    val (x, y) = CruzOnda3.paralaxe(t)
    if (x == 0 && y == 0) return
    val passo = CruzOnda3.PARALAXE_DP * resources.displayMetrics.density
    capa.animate().cancel()
    capa.alpha = capa.alfaAlvo   // o cancel também parava o esmaecer da capa no meio
    capa.translationX = x * passo; capa.translationY = y * passo
    capa.animate().translationX(0f).translationY(0f).setDuration(CruzOnda3.PARALAXE_MS)
        .setInterpolator(Estilo.curva()).start()
}

internal fun MainActivity.vigiarTela() {
    if (vigiaTela != null) return
    val r = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: android.content.Context, i: Intent) {
            val agora = android.os.SystemClock.elapsedRealtime()
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { apagouEm = agora; sairAmbiente(animar = false); TravaCartaoTv.telaApagou() }
                Intent.ACTION_SCREEN_ON -> {
                    acendeuEm = agora
                    dormiuMs = if (apagouEm > 0) agora - apagouEm else 0L
                    perguntouNestaVez = false
                    // Logo Sofaly ao acordar a TV (SplashRules): HOME na frente = já; pausada = no onResume dos próximos 3 s.
                    if (SplashRules.onWake(SplashOverlay.enabled(ctx), dormiuMs, homeInFront = true)) {
                        if (homeNaFrente) SplashOverlay.show(this@vigiarTela) else splashPendenteAte = agora + 3000L
                    }
                    abrirIlhaAoAcordar()
                    // "Abrir ao ligar" também ao acordar do descanso (bloco 7: o "...e ao sair do standby" dele)
                    // Só com a HOME na frente (revisor 26/11): acordar por Cast, despertador ou com um app aberto não cobre o que está lá.
                    if (CardMenu.onWake(ctx)) CardMenu.bootApp(ctx)?.let { p -> ui.postDelayed({ if (!isDestroyed && homeNaFrente) abrir(p) }, CardMenu.delayMs(ctx)) }
                    if (homeNaFrente) { ui.removeCallbacks(tarefaQuemVe); ui.postDelayed(tarefaQuemVe, 1500) }
                }
            }
        }
    }
    val f = android.content.IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }
    runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, android.content.Context.RECEIVER_NOT_EXPORTED) else registerReceiver(r, f)
        vigiaTela = r
    }
}

internal fun MainActivity.talvezPerguntarQuemVe() {
    if (perguntouNestaVez || !homeNaFrente || isDestroyed) return
    val desde = android.os.SystemClock.elapsedRealtime() - acendeuEm
    val contas = Perfil.contas(this)
    if (!CruzOnda3.perguntaQuemVe(CruzOnda3.ligado(this, CruzOnda3.CHAVE_QUEM), contas.size, dormiuMs, desde, ModoVisita.ativo(this))) {
        if (desde > CruzOnda3.QUEM_JANELA_MS) perguntouNestaVez = true
        return
    }
    if (menuAberto?.isShowing == true) return
    perguntouNestaVez = true
    val ativa = Perfil.ativa(this, contas)
    val nomes = contas.map { Perfil.nome(this, it) + if (it == ativa) getString(R.string.pp_ativo) else "" }
    menuAberto = Estilo.lista(this, getString(R.string.cruz_quem_titulo), nomes) { i ->
        val conta = contas[i]
        if (conta != ativa) PerfilCriancaTv.trocar(this, ativa, conta) { trocou ->
            if (trocou && !isDestroyed) { atualizarFotoPerfil(); reorganizar(); preencherContinuar() }
        }
    }
}

/** [sempre] = true nas Ferramentas (21/11/2026): lá o item fica o dia todo, não só à noite. */
internal fun MainActivity.itemDormir(sempre: Boolean = false): HomeCruz.Item? {
    val marcado = Desligamento.marcado(this)
    val hora = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    if (!sempre && !CruzOnda3.mostraDormir(CruzOnda3.ligado(this, CruzOnda3.CHAVE_DORMIR), hora, marcado != null)) return null
    val ic = icCruz("c:dormir", IconeCruz.Tipo.DORMIR)
    return if (marcado != null) HomeCruz.Item("c:dormir",
        getString(R.string.cruz_desliga_as, android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(marcado))),
        "", ic, { Desligamento.cancelar(this); atualizarCruz() })
    else HomeCruz.Item("c:dormir", getString(R.string.cruz_dormir), getString(R.string.cruz_dormir_sub), ic, {
        Desligamento.agendar(this, CruzOnda3.DORMIR_MIN); atualizarCruz()
    })
}

/**
 * O que a grade de "Todos os apps" mostra e quantos ficaram escondidos (Paulo, 19/11/2026: "3 apps na grade · 5 ocultos").
 * Mesma conta da própria grade ([contagemGrade]): antes a HOME tirava os jogos e contava os apps da visita como
 * escondidos, e a Sala mostrava "8 apps" na HOME e "13 apps" ao abrir a grade (teste de 21/11/2026).
 */
internal fun MainActivity.contaApps(): String {
    val (naGrade, escondidos) = contagemGrade()
    return if (escondidos == 0) getString(R.string.cruz_apps_n, naGrade) else getString(R.string.cruz_apps_ocultos, naGrade, escondidos)
}

/** Apps que a grade de "Todos os apps" mostra e quantos estão escondidos atrás do PIN. Fonte única das duas telas. */
internal fun MainActivity.contagemGrade(): Pair<Int, Int> {
    val visita = ModoVisita.ativo(this)
    val daVisita = if (visita) ModoVisita.apps(this) else emptySet()
    val ocultos = Organizacao.ocultos(this)
    val apps = instalados.filter { it.pacote !in daVisita }
    val escondidos = if (visita) 0 else apps.count { it.pacote in ocultos }
    return apps.count { it.pacote !in ocultos } to escondidos
}

/**
 * Voltar duas vezes na HOME (bloco 7): abre o protetor de tela do sistema (o nosso ProtetorTela) pelo shell próprio;
 * sem shell, entra no modo ambiente da HOME. O 1º Voltar segue o caminho normal (não é engolido).
 */
internal fun MainActivity.voltarDuplo(e: KeyEvent): Boolean {
    if (e.keyCode != KeyEvent.KEYCODE_BACK || e.action != KeyEvent.ACTION_DOWN || e.repeatCount > 0) return false
    if (!CruzOnda3.voltar2(this) || todosAbertos || menuAberto?.isShowing == true) return false
    val agora = android.os.SystemClock.uptimeMillis()
    val duplo = CruzOnda3.voltarDuplo(ultimoVoltar, agora)
    ultimoVoltar = if (duplo) 0L else agora
    if (!duplo) return false
    engolirTecla = KeyEvent.KEYCODE_BACK
    br.paulo.tvlauncher.Fundo.rodar("voltar-duplo") {
        val r = br.paulo.tvlauncher.privilege.GuardiaoShizuku.comando(this, "cmd dreams start-dreaming", 5_000)
        if (r == null) ui.post { if (!isDestroyed) entrarAmbiente() }
    }
    return true
}
