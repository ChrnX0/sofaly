package br.paulo.tvlauncher.home

import br.paulo.tvlauncher.AvisoTv
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.util.Log
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.MainActivity
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.TocandoAgoraTv
import br.paulo.tvlauncher.family.PinNaTela

/**
 * Bloco 4 do clone do Projectivy (26/11/2026): o que faltava no menu do card (segurar OK num app). O menu dele tem Abrir,
 * Atalhos, Reordenar, Abrir ao ligar e Detalhes; o resto (ocultar, trocar ícone, mover de categoria, desinstalar) o
 * nosso já tinha. Montado em [menuAppCruz].
 */
object CardMenu {
    private const val PREFS = "launch_on_boot"
    private const val KEY_APP = "app"
    private const val KEY_DONE = "done_boot"

    /** O app que abre sozinho quando a TV liga (só um, como no dele), ou null. */
    fun bootApp(ctx: Context): String? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_APP, null)

    /** Liga/desliga o "Abrir ao ligar" de [pkg]. Ligar agora não abre já: o boot de agora conta como feito. */
    fun setBootApp(ctx: Context, pkg: String?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_APP, pkg).putInt(KEY_DONE, bootCount(ctx)).apply()
    }

    // Controle parental do card (bloco 7, o "Parental control" dele): apps que só abrem com o PIN da TV.
    private const val LOCKS = "card_lock"
    fun locked(ctx: Context): Set<String> = ctx.getSharedPreferences(LOCKS, Context.MODE_PRIVATE).getStringSet("apps", null).orEmpty()
    fun isLocked(ctx: Context, pkg: String) = pkg in locked(ctx)
    fun setLocked(ctx: Context, pkg: String, on: Boolean) {
        val novo = locked(ctx).toMutableSet().apply { if (on) add(pkg) else remove(pkg) }
        ctx.getSharedPreferences(LOCKS, Context.MODE_PRIVATE).edit().putStringSet("apps", novo).apply()
    }

    // Ajustes do "Abrir ao ligar" (bloco 7): espera antes de abrir e abrir também ao acordar a TV. Em Extras.numero.
    const val KEY_DELAY = "boot_app_delay"
    const val KEY_WAKE = "boot_app_wake"
    val DELAYS_S = listOf(1, 3, 5, 10, 20)
    fun delayMs(ctx: Context) = delayMs(br.paulo.tvlauncher.settings.Extras.numero(ctx, KEY_DELAY, 0))
    fun delayMs(i: Int) = DELAYS_S.getOrElse(i) { 1 } * 1000L
    fun onWake(ctx: Context) = br.paulo.tvlauncher.settings.Extras.numero(ctx, KEY_WAKE, 0) == 1

    /** Número do boot (o Android conta cada vez que liga do zero); -1 se não der para ler. */
    private fun bootCount(ctx: Context): Int =
        runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

    /** Regra pura: abre quando há app escolhido e este boot ainda não abriu (contagem conhecida e diferente). */
    fun shouldOpen(app: String?, count: Int, done: Int): Boolean = app != null && count >= 0 && count != done

    /** Na 1ª vez que a HOME aparece depois de ligar: devolve o app a abrir (e marca o boot como feito). */
    fun takeBootApp(ctx: Context): String? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val app = p.getString(KEY_APP, null)
        val count = bootCount(ctx)
        if (!shouldOpen(app, count, p.getInt(KEY_DONE, -1))) return null
        p.edit().putInt(KEY_DONE, count).apply()
        return app
    }

    /** Atalhos que o app publica (os do launcher: estáticos, dinâmicos e fixados). Só a HOME padrão pode ler. */
    fun shortcuts(ctx: Context, pkg: String): List<ShortcutInfo> = runCatching {
        val la = ctx.getSystemService(LauncherApps::class.java)
        if (!la.hasShortcutHostPermission()) return@runCatching emptyList()
        val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        la.getShortcuts(q, Process.myUserHandle()).orEmpty().filter { it.isEnabled }.sortedBy { it.rank }
    }.onFailure { Log.w("TvLauncher.Apps", "atalhos de $pkg: ${it.message}") }.getOrDefault(emptyList())

    fun openShortcut(ctx: Context, s: ShortcutInfo): Boolean = runCatching {
        ctx.getSystemService(LauncherApps::class.java).startShortcut(s, null, null); true
    }.getOrDefault(false)
}

/** As linhas novas do menu do card (bloco 4). [primeiras] vão no topo; [ultimas] antes de desinstalar. */
internal fun MainActivity.opcoesDoCard(pacote: String): Pair<List<MainActivity.Opcao>, List<MainActivity.Opcao>> {
    val primeiras = mutableListOf(MainActivity.Opcao(getString(R.string.rw_abrir)) { abrir(pacote) })
    val atalhos = CardMenu.shortcuts(this, pacote)
    if (atalhos.isNotEmpty()) primeiras += MainActivity.Opcao(getString(R.string.rw_atalhos)) {
        menuAberto = Estilo.lista(this, getString(R.string.rw_atalhos), atalhos.map { (it.shortLabel ?: it.id).toString() }) { i ->
            val s = atalhos[i]
            // App travado: o PIN antes, aqui, para o atalho abrir no destino escolhido (e não na tela principal).
            if (CardMenu.isLocked(this, pacote)) TravaCartaoTv.pedirEAbrir(this, pacote) { if (!CardMenu.openShortcut(this, s)) cruz?.naoAbriu() }
            else if (!CardMenu.openShortcut(this, s)) cruz?.naoAbriu()
        }
    }
    // Mídia (bloco 7): com o app tocando algo, o menu dele controla a reprodução sem abrir o app
    TocandoAgoraTv.controlador(this, pacote)?.let { mc ->
        val tocando = mc.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        primeiras += MainActivity.Opcao(getString(if (tocando) R.string.rw_pausar else R.string.rw_tocar)) {
            runCatching { if (tocando) mc.transportControls.pause() else mc.transportControls.play() }
        }
        primeiras += MainActivity.Opcao(getString(R.string.rw_proxima)) { runCatching { mc.transportControls.skipToNext() } }
        primeiras += MainActivity.Opcao(getString(R.string.rw_anterior)) { runCatching { mc.transportControls.skipToPrevious() } }
    }
    val ultimas = mutableListOf<MainActivity.Opcao>()
    // Controle parental (bloco 7): travar pede o PIN (senão uma criança destrava); destravar também
    val travado = CardMenu.isLocked(this, pacote)
    ultimas += MainActivity.Opcao(getString(if (travado) R.string.rw_destravar else R.string.rw_travar)) {
        PinNaTela.pedir(this, getString(if (travado) R.string.rw_destravar else R.string.rw_travar)) {
            CardMenu.setLocked(this, pacote, !travado)
            // A trava de fora da home (busca, Google, outro launcher) depende do serviço de acessibilidade. Sem ele, a
            // trava vale só daqui: diz isso em vez de deixar a pessoa achar que o app está trancado (revisor 26/11).
            if (!travado && br.paulo.tvlauncher.AutorizaShizukuService.instancia == null)
                AvisoTv.mostrar(this, getString(R.string.rw_travar), getString(R.string.rw_travar_sem_acesso), 10, som = false,
                    guardarSeApagada = false, historico = false)
        }
    }
    val noBoot = CardMenu.bootApp(this) == pacote
    ultimas += MainActivity.Opcao(getString(if (noBoot) R.string.rw_ao_ligar_tirar else R.string.rw_ao_ligar)) {
        CardMenu.setBootApp(this, if (noBoot) null else pacote)
    }
    ultimas += MainActivity.Opcao(getString(R.string.rw_detalhes)) {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pacote")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { cruz?.naoAbriu() }
    }
    return primeiras to ultimas
}

/** Reordenar (como no Projectivy): ◀ ▶ levam o card pela fileira, OK termina. A ordem do mundo vira "Do meu jeito". */
internal fun MainActivity.reordenarNoMundo(pacote: String) {
    val (cat, _) = mundoEmFoco() ?: return
    cruz?.comecarAMover("app:$pacote") { passo ->
        val o = WorldOptions.of(this, cat)
        val atual = cruz?.categoriaAtual()?.itens.orEmpty().map { it.id }.filter { it.startsWith("app:") }.map { it.removePrefix("app:") }
        if (pacote !in atual) return@comecarAMover
        WorldOptions.save(this, cat, o.copy(sort = WorldOptions.Sort.MANUAL, manual = WorldOptions.Rules.move(atual, pacote, passo)))
        atualizarAposMudanca("app:$pacote")
    }
}
