package br.paulo.tvlauncher.privilege

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import br.paulo.tvlauncher.AutorizaShizukuService
import br.paulo.tvlauncher.BuildConfig
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.PlayPolicy
import br.paulo.tvlauncher.ProtetorTela
import br.paulo.tvlauncher.observer.ObserverTv
import br.paulo.tvlauncher.observer.SofyBus
import br.paulo.tvlauncher.observer.SofyBusTv
import java.net.NetworkInterface

/**
 * A Sofy religa o shell próprio sozinha quando a TV esquece o pareamento (25/11/2026; regras em [AutoPairingRules]).
 *
 * Como: com o NOSSO protetor na frente (TV ligada, ninguém mexendo), abre as Opções do desenvolvedor, entra em
 * "Depuração sem fio" › "Parear com código" pela acessibilidade, lê o código de 6 dígitos na tela, acha a porta por
 * mDNS (só a DESTE aparelho) e pareia pela fila do adb. Depois volta à HOME; o protetor volta no tempo normal.
 *
 * A exceção à regra de 18/11/2026 (PareamentoNaTv: parear só com OK de tecla FÍSICA) é estreita de propósito:
 *  - só a Sofy dispara, por dentro (nenhum painel, MCP, adb `input` ou clique externo chega aqui);
 *  - só numa TV em que o shell JÁ funcionou (é conserto, não ganho de poder novo);
 *  - só com o nosso protetor na frente, no máximo 4 vezes por dia. Fora da Play (PlayPolicy.READ_PAIRING_CODE).
 * Não explica nada na tela: anota no diário e no barramento (Paulo, 25/11/2026: "só identificar e corrigir").
 */
object AutoPairing {
    private const val TAG = "TvLauncher.AutoPairing"
    private const val PREFS = "auto_pairing"
    private const val SERVICE = "_adb-tls-pairing._tcp"
    private const val TICK_MS = 1_500L

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun attempts(ctx: Context): List<Long> =
        prefs(ctx).getString("attempts", "")!!.split(',').mapNotNull { it.toLongOrNull() }

    private fun wifiOn(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun state(ctx: Context, shellDownForMs: Long?) = AutoPairingRules.State(
        enabled = PlayPolicy.allowed(PlayPolicy.Power.READ_PAIRING_CODE, BuildConfig.PLAY),
        shellExpected = SaudeAcesso.shellEsperado(ctx),
        shellDownForMs = shellDownForMs,
        screensaverOpen = ProtetorTela.aberto(),
        accessibilityOn = AutorizaShizukuService.instancia != null,
        wifiOn = wifiOn(ctx),
        attempts = attempts(ctx),
        now = System.currentTimeMillis(),
    )

    /** O aviso de "acesso caiu" espera enquanto a Sofy ainda pode consertar sozinha. */
    fun stillTrying(ctx: Context) = AutoPairingRules.stillTrying(state(ctx.applicationContext, null))

    /**
     * Chamado pelo ciclo de conserto da Sofy (SofyRepairTv, ferramenta REPAIR_SHELL). Barato: só lê estado; age só
     * quando a regra manda. Devolve true se começou uma tentativa.
     */
    /** O último motivo de não tentar (para o relatório da Sofy). */
    @Volatile var lastWhy = ""
        private set

    fun maybe(ctx: Context, shellDownForMs: Long?): Boolean {
        val app = ctx.applicationContext
        if (running) return false
        val s = state(app, shellDownForMs)
        if (AutoPairingRules.decide(s) != AutoPairingRules.Decision.TRY) {
            val w = AutoPairingRules.why(s)
            if (w != lastWhy) { lastWhy = w; Log.i(TAG, "não tentou: $w") }
            return false
        }
        val list = (AutoPairingRules.recent(s.attempts, s.now) + s.now).joinToString(",")
        prefs(app).edit().putString("attempts", list).apply()
        main.post { start(app) }
        return true
    }

    // ---------------------------------------------------------------- uma tentativa

    private class Attempt(val app: Context, val startedAt: Long) {
        var port = -1
        var code: String? = null
        var listener: NsdManager.DiscoveryListener? = null
        var clicks = 0
        var scrolls = 0
        var pairing = false
        var openedWireless = false
        val pairLabels = labels(app, AutoPairingRules.PAIR_RES) + AutoPairingRules.PAIR_FALLBACK
        val wirelessLabels = labels(app, AutoPairingRules.WIRELESS_RES) + AutoPairingRules.WIRELESS_FALLBACK
    }

    private fun start(app: Context) {
        if (running) return
        running = true
        val a = Attempt(app, System.currentTimeMillis())
        log(app, "tentativa: shell fora, protetor na frente; abrindo a depuração sem fio")
        SofyBusTv.emit(app, SofyBus.Source.SOFY, "access_repair_started")
        ProtetorTela.fecharSeAberto()   // acorda e segura a TV 20 s (sem isso a tela abre atrás do protetor)
        discover(a)
        main.postDelayed({
            try {
                app.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { finish(a, false, "sem Opções do desenvolvedor: ${e.javaClass.simpleName}"); return@postDelayed }
            main.postDelayed({ tick(a) }, TICK_MS)
        }, 800)
    }

    private fun tick(a: Attempt) {
        if (!running || a.pairing) return
        if (System.currentTimeMillis() - a.startedAt > AutoPairingRules.ATTEMPT_MS) { finish(a, false, "prazo"); return }
        val svc = AutorizaShizukuService.instancia ?: run { finish(a, false, "sem acessibilidade"); return }
        val roots = roots(svc)
        val texts = roots.flatMap { texts(it) }
        when (AutoPairingRules.step(texts, a.pairLabels)) {
            AutoPairingRules.Step.READ_CODE -> {
                a.code = CodigoPareamento.achar(texts)
                if (a.port > 0 && a.code != null) { pair(a); return }
            }
            AutoPairingRules.Step.OPEN_PAIRING -> { if (!clickLabel(roots, a.pairLabels)) scroll(roots, a) else a.clicks++ }
            // Já entrou na "Depuração sem fio" e o item de parear não apareceu: está abaixo da dobra. Rola em vez de
            // clicar de novo no rótulo (na página de dentro ele é o interruptor: clicar desligaria a depuração).
            AutoPairingRules.Step.OPEN_WIRELESS ->
                if (a.openedWireless || !clickLabel(roots, a.wirelessLabels)) scroll(roots, a) else { a.clicks++; a.openedWireless = true }
        }
        if (a.clicks > 8 || a.scrolls > 20) { finish(a, false, "não achei o caminho na tela"); return }
        main.postDelayed({ tick(a) }, TICK_MS)
    }

    private fun pair(a: Attempt) {
        a.pairing = true
        val port = a.port; val code = a.code ?: return
        Fundo.rodar("auto-pairing") {
            var erro: String? = null
            val ok = GuardiaoShizuku.parear(a.app, port, code) { erro = it }
            if (ok) GuardiaoShizuku.parearEGarantir(a.app, null, -1)
            main.post { finish(a, ok, if (ok) "pareado" else "pareamento recusado: $erro") }
        }
    }

    private fun finish(a: Attempt, ok: Boolean, why: String) {
        if (!running) return
        running = false
        a.listener?.let { l -> runCatching { (a.app.getSystemService(Context.NSD_SERVICE) as NsdManager).stopServiceDiscovery(l) } }
        // Volta para a HOME (a caixinha do código fecha sozinha ao parear; sem parear, sai das Configurações do mesmo jeito).
        AutorizaShizukuService.instancia?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        log(a.app, (if (ok) "consertado: " else "não consertou: ") + why)
        // Deu certo: o recuo zera (a próxima queda começa do intervalo curto de novo).
        if (ok) prefs(a.app).edit().remove("attempts").apply()
        SofyBusTv.emit(a.app, SofyBus.Source.SOFY, if (ok) "access_repaired" else "access_repair_failed", ok = ok, detail = why)
        ObserverTv.logShadow(a.app, if (ok) "access_repaired" else "access_repair_failed", why)
    }

    // ---------------------------------------------------------------- tela (acessibilidade)

    private fun roots(svc: AutorizaShizukuService): List<AccessibilityNodeInfo> =
        (runCatching { svc.windows.mapNotNull { it.root } }.getOrNull() ?: emptyList())
            .ifEmpty { listOfNotNull(runCatching { svc.rootInActiveWindow }.getOrNull()) }
            .filter { it.packageName?.toString() != svc.packageName }

    private fun texts(root: AccessibilityNodeInfo): List<String> {
        val out = ArrayList<String>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 30 || out.size > 400) return
            n.text?.toString()?.let { out += it }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        runCatching { walk(root, 0) }
        return out
    }

    /** Acha o nó com o rótulo e clica nele ou no primeiro pai clicável (o item da lista é o LinearLayout, não o texto). */
    private fun clickLabel(roots: List<AccessibilityNodeInfo>, labels: List<String>): Boolean {
        fun find(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
            if (n == null || depth > 30) return null
            val t = n.text?.toString()
            if (t != null && labels.any { AutoPairingRules.matches(t, it) }) return n
            for (i in 0 until n.childCount) find(n.getChild(i), depth + 1)?.let { return it }
            return null
        }
        for (r in roots) {
            var n = runCatching { find(r, 0) }.getOrNull() ?: continue
            for (up in 0..5) {
                if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                n = n.parent ?: break
            }
        }
        return false
    }

    /** O item pode estar abaixo da dobra: rola a primeira lista rolável. */
    private fun scroll(roots: List<AccessibilityNodeInfo>, a: Attempt) {
        fun find(n: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
            if (n == null || depth > 30) return null
            if (n.isScrollable) return n
            for (i in 0 until n.childCount) find(n.getChild(i), depth + 1)?.let { return it }
            return null
        }
        a.scrolls++
        roots.firstNotNullOfOrNull { runCatching { find(it, 0) }.getOrNull() }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    /** Os rótulos na língua da TV, tirados das próprias Configurações (AOSP e TvSettings). */
    private fun labels(app: Context, names: List<String>): List<String> =
        listOf("com.android.settings", "com.android.tv.settings").flatMap { pkg ->
            runCatching {
                val res = app.packageManager.getResourcesForApplication(pkg)
                names.mapNotNull { n ->
                    @Suppress("DiscouragedApi") val id = res.getIdentifier(n, "string", pkg)
                    if (id == 0) null else res.getString(id)
                }
            }.getOrDefault(emptyList())
        }

    // ---------------------------------------------------------------- porta (mDNS)

    @Suppress("DEPRECATION")
    private fun discover(a: Attempt) {
        val nsd = a.app.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val locals = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }.mapNotNull { it.hostAddress }.toSet()
        }.getOrDefault(emptySet())
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(i: NsdServiceInfo, e: Int) {}
                    override fun onServiceResolved(i: NsdServiceInfo) {
                        val host = i.host?.hostAddress ?: return
                        if (host !in locals && host != "127.0.0.1") return   // só a porta DESTE aparelho
                        main.post { a.port = i.port; if (a.code != null && !a.pairing && running) pair(a) }
                    }
                })
            }
            override fun onServiceLost(info: NsdServiceInfo) { main.post { a.port = -1 } }
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }
        try { nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, l); a.listener = l } catch (e: Exception) { Log.w(TAG, "mDNS: $e") }
    }

    private fun log(app: Context, t: String) {
        Log.i(TAG, t)
        ServidorShizuku.anotar(app, "[autorreparo] $t")
    }
}
