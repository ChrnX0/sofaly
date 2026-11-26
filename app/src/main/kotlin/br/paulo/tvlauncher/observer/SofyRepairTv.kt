package br.paulo.tvlauncher.observer

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import br.paulo.tvlauncher.AutorizaShizukuService
import br.paulo.tvlauncher.Config
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.ProtetorTela
import br.paulo.tvlauncher.alarm.NaoPerturbe
import br.paulo.tvlauncher.mcp.TunelTailscale
import br.paulo.tvlauncher.panel.PainelControle
import br.paulo.tvlauncher.panel.PainelSaude
import br.paulo.tvlauncher.privilege.AutoPairing
import br.paulo.tvlauncher.privilege.GuardiaoShizuku
import br.paulo.tvlauncher.privilege.JanelaNaFrente
import br.paulo.tvlauncher.privilege.Privilegio
import br.paulo.tvlauncher.privilege.SaudeAcesso
import br.paulo.tvlauncher.observer.SofyRepair.Need
import br.paulo.tvlauncher.observer.SofyRepair.Signal
import br.paulo.tvlauncher.observer.SofyRepair.Tool
import br.paulo.tvlauncher.observer.SofyRepair.Type
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * A parte Android de [SofyRepair]: de onde vêm os sinais, como cada ferramenta é aplicada e onde o estado fica.
 *
 * Sinais (três portas, todas genéricas):
 *  1. SONDAS baratas a cada batida ([tick], ~2 min): acesso interno, acessibilidade, depuração sem fio, permissões, túnel.
 *  2. O BARRAMENTO: todo evento que qualquer parte do Sofaly emitir com ok = false vira sinal ([fromBus]). Uma parte
 *     nova que conte "deu errado" já entra no ciclo, sem ninguém escrever receita.
 *  3. [signal]/[expect]: um botão ou tela diz "isto devia ter mudado e não mudou" (ação sem efeito).
 * Não mostra nada na tela (Paulo, 25/11/2026): diário da Sofy, barramento e relatório (painel/MCP).
 */
object SofyRepairTv {
    private const val TAG = "TvLauncher.SofyRepair"
    private const val PREFS = "sofy_repair"
    /** Caso sem sonda (veio do barramento ou de um botão): "consertado" = meia hora sem o sinal voltar. */
    private const val QUIET_MS = 30 * 60_000L
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var working = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- registros (B: cada parte declara)

    /** Como reler um dado ("data:<nome>") do zero. Quem tem cache registra aqui. */
    val refreshers = ConcurrentHashMap<String, (Context) -> Unit>()
    /** Como desligar uma função que quebrou ("feature:<nome>" / "tile:<id>"). Devolve true se desligou. */
    val switches = ConcurrentHashMap<String, (Context) -> Boolean>()

    // ---------------------------------------------------------------- porta 3: sinal direto e ação sem efeito

    fun signal(ctx: Context, type: Type, subject: String, detail: String = "", needs: Set<Need> = emptySet()) {
        val app = ctx.applicationContext
        Fundo.rodar("sofy-sinal") { runCatching { add(app, Signal(type, subject, System.currentTimeMillis(), detail.take(120), needs)) } }
    }

    /**
     * Ação sem efeito: quem dispara uma ação diz o que devia ser verdade [afterMs] depois. Se [worked] der false, vira
     * sinal NO_EFFECT com as dependências declaradas, e o conserto vai direto na causa.
     */
    fun expect(ctx: Context, subject: String, needs: Set<Need> = emptySet(), afterMs: Long = 4_000, worked: () -> Boolean) {
        val app = ctx.applicationContext
        main.postDelayed({
            Fundo.rodar("sofy-efeito") {
                val ok = runCatching { worked() }.getOrDefault(true)
                if (!ok) add(app, Signal(Type.NO_EFFECT, subject, System.currentTimeMillis(), needs = needs))
            }
        }, afterMs)
    }

    /**
     * Abri o app [pkg]: em [afterMs] ele tem que ter aparecido. Só confere com a acessibilidade ligada (é ela que vê a
     * tela; sem ela não há como saber e é melhor não acusar). Assunto "app:<pacote>" (as ferramentas de app entram).
     */
    fun expectApp(ctx: Context, pkg: String, afterMs: Long = 8_000) {
        if (pkg.isBlank() || AutorizaShizukuService.instancia == null) return
        val at = System.currentTimeMillis()
        expect(ctx, "app:$pkg", emptySet(), afterMs) { AutorizaShizukuService.instancia == null || (JanelaNaFrente.vistoEm[pkg] ?: 0L) >= at - 500 }
    }

    /** Abri uma tela (nossa ou do sistema) pelo [intent]: em [afterMs] o pacote dela tem que ter aparecido. */
    fun expectScreen(ctx: Context, subject: String, intent: android.content.Intent, needs: Set<Need> = emptySet(), afterMs: Long = 6_000) {
        if (AutorizaShizukuService.instancia == null) return
        val pkg = intent.component?.packageName ?: runCatching { ctx.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName }.getOrNull() ?: return
        val at = System.currentTimeMillis()
        expect(ctx, subject, needs, afterMs) { AutorizaShizukuService.instancia == null || (JanelaNaFrente.vistoEm[pkg] ?: 0L) >= at - 500 }
    }

    // ---------------------------------------------------------------- porta 1: sondas

    private data class Probe(val type: Type, val subject: String, val bad: (Context) -> Boolean)

    private fun granted(c: Context, p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun wifi(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private val probes = listOf(
        Probe(Type.ACCESS_LOST, "shell") { SaudeAcesso.shellEsperado(it) && (SaudeAcesso.shellDownMs(it) ?: 0L) >= SHELL_DOWN_MS },
        Probe(Type.ACCESS_LOST, "accessibility") { Config.SERVICO_TECLAS_SEMPRE_LIGADO && SaudeAcesso.shellEsperado(it) && AutorizaShizukuService.instancia == null },
        Probe(Type.ACCESS_LOST, "wireless_debug") {
            Config.DEPURACAO_SEM_FIO_SEMPRE_LIGADA && SaudeAcesso.shellEsperado(it) && wifi(it) &&
                Settings.Global.getInt(it.contentResolver, "adb_wifi_enabled", 0) != 1
        },
        Probe(Type.ACCESS_LOST, "tunnel") { SaudeAcesso.tunnelDown() },
        Probe(Type.PERMISSION_LOST, "perm:overlay") { SaudeAcesso.shellEsperado(it) && !Settings.canDrawOverlays(it) },
        Probe(Type.PERMISSION_LOST, "perm:secure_settings") { SaudeAcesso.shellEsperado(it) && !granted(it, "android.permission.WRITE_SECURE_SETTINGS") },
        Probe(Type.PERMISSION_LOST, "perm:logs") { SaudeAcesso.shellEsperado(it) && Diagnostico.PERMISSOES_SHELL.any { p -> !granted(it, p) } },
        // Testar como gente (C): bater na porta do painel e do MCP como um celular/o Claude bateria. Só depois de 3 min de
        // processo (subindo, a porta ainda não abriu) e só se a função está ligada.
        Probe(Type.ERROR, "feature:panel") { upMs() > 3 * 60_000L && br.paulo.tvlauncher.panel.PainelArmazem.podeLigar(it) && !answers(Config.PAINEL_PORTA) },
        Probe(Type.ERROR, "feature:mcp") {
            upMs() > 3 * 60_000L && br.paulo.tvlauncher.mcp.McpServidor.precisaServidor(it) && br.paulo.tvlauncher.panel.PainelArmazem.temPin(it) && !answers(Config.MCP_PORTA)
        },
    )

    private fun upMs() = android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()

    /** A porta local aceita conexão em até 1,5 s? (Só abre e fecha: não fala HTTP, não conta como tentativa de PIN.) */
    private fun answers(port: Int): Boolean = runCatching {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", port), 1_500) }
        true
    }.getOrDefault(false)

    init {
        // Como "reler do zero" cada função testada acima (REFRESH_DATA): pedir para ela se ajustar de novo.
        refreshers["feature:panel"] = { br.paulo.tvlauncher.panel.PainelWeb.ajustar(it) }
        refreshers["feature:mcp"] = { br.paulo.tvlauncher.mcp.McpServidor.ajustar(it) }
    }
    private const val SHELL_DOWN_MS = 20 * 60_000L

    // ---------------------------------------------------------------- porta 2: o barramento

    // A regra (o que é defeito e que sinal vira) é pura: [SofyRepair.fromBus].

    // ---------------------------------------------------------------- estado

    @Synchronized private fun add(app: Context, s: Signal) {
        val ev = SofyBusTv.events(app)
        val antes = SofyBus.around(ev, s.at - 10 * 60_000L, s.at).filter { it.ok }.takeLast(6).map { "${it.key}${if (it.detail.isEmpty()) "" else "(${it.detail})"}" }
        val before = loadCases(app)
        val novo = before.none { it.key == SofyRepair.key(s.type, s.subject) && it.state == SofyRepair.State.OPEN }
        saveCases(app, SofyRepair.onSignal(before, s, antes))
        if (novo) {
            Log.i(TAG, "caso: ${s.type} ${s.subject} ${s.detail}")
            ObserverTv.logShadow(app, "repair_case", "${s.type.name} ${s.subject} ${s.detail}".trim())
        }
    }

    private fun loadCases(ctx: Context): List<SofyRepair.Case> = runCatching {
        val a = JSONArray(prefs(ctx).getString("cases", "[]"))
        (0 until a.length()).mapNotNull { i -> caseOf(a.getJSONObject(i)) }
    }.getOrDefault(emptyList())

    private fun saveCases(ctx: Context, cs: List<SofyRepair.Case>) =
        prefs(ctx).edit().putString("cases", JSONArray().apply { cs.takeLast(60).forEach { put(json(it)) } }.toString()).apply()

    fun json(c: SofyRepair.Case): JSONObject = JSONObject()
        .put("type", c.type.name).put("subject", c.subject).put("first", c.firstAt).put("last", c.lastAt).put("count", c.count)
        .put("state", c.state.name).put("detail", c.detail)
        .put("needs", JSONArray(c.needs.map { it.name })).put("context", JSONArray(c.context)).put("ai", JSONArray(c.aiPlan.map { it.name }))
        .put("banned", JSONObject().apply { c.banned.forEach { (t, at) -> put(t.name, at) } })
        .put("tried", JSONArray().apply { c.tried.forEach { put(JSONObject().put("tool", it.tool.name).put("at", it.at).put("result", it.result.name)) } })

    fun caseOf(o: JSONObject): SofyRepair.Case? {
        val type = Type.entries.firstOrNull { it.name == o.optString("type") } ?: return null
        val subject = o.optString("subject").ifEmpty { return null }
        fun strings(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        val tried = o.optJSONArray("tried")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val t = a.getJSONObject(i)
                val tool = Tool.entries.firstOrNull { it.name == t.optString("tool") } ?: return@mapNotNull null
                val r = SofyRepair.Result.entries.firstOrNull { it.name == t.optString("result") } ?: SofyRepair.Result.FAILED
                SofyRepair.Attempt(tool, t.optLong("at"), r)
            }
        }.orEmpty()
        return SofyRepair.Case(
            SofyRepair.key(type, subject), type, subject,
            strings("needs").mapNotNull { n -> Need.entries.firstOrNull { it.name == n } }.toSet(),
            o.optLong("first"), o.optLong("last"), o.optInt("count", 1), tried,
            SofyRepair.State.entries.firstOrNull { it.name == o.optString("state") } ?: SofyRepair.State.OPEN,
            strings("context"), strings("ai").mapNotNull { n -> Tool.entries.firstOrNull { it.name == n } }, o.optString("detail"),
            o.optJSONObject("banned")?.let { b -> b.keys().asSequence().mapNotNull { k -> Tool.entries.firstOrNull { it.name == k }?.let { it to b.optLong(k) } }.toMap() }.orEmpty(),
        )
    }

    private fun loadLearned(ctx: Context): SofyLearned = runCatching {
        val o = JSONObject(prefs(ctx).getString("learned", "{}"))
        o.keys().asSequence().associateWith { f ->
            val m = o.getJSONObject(f)
            m.keys().asSequence().mapNotNull { t ->
                val tool = Tool.entries.firstOrNull { it.name == t } ?: return@mapNotNull null
                val s = m.getJSONObject(t)
                tool to SofyRepair.Score(s.optInt("ok"), s.optInt("fail"))
            }.toMap()
        }
    }.getOrDefault(emptyMap())

    private fun saveLearned(ctx: Context, l: SofyLearned) {
        val o = JSONObject()
        l.forEach { (f, m) -> o.put(f, JSONObject().apply { m.forEach { (t, s) -> put(t.name, JSONObject().put("ok", s.ok).put("fail", s.fail)) } }) }
        prefs(ctx).edit().putString("learned", o.toString()).apply()
    }

    /** Para o relatório (painel e MCP): os casos e o que ela aprendeu. */
    fun reportJson(ctx: Context): JSONObject = JSONObject()
        .put("cases", JSONArray().apply { loadCases(ctx).forEach { put(json(it)) } })
        .put("learned", JSONObject(prefs(ctx).getString("learned", "{}") ?: "{}"))
        .put("ai", runCatching { SofyAi.json(ctx) }.getOrNull() ?: JSONObject())
        .put("waiting", JSONObject().apply { prefs(ctx).all.forEach { (k, v) -> if (k.startsWith("wait:")) put(k.removePrefix("wait:"), v.toString()) } })   // sem a chave (SofyAi.json não a põe)

    // ---------------------------------------------------------------- a batida

    /** A cada ~2 min (pela saúde do acesso). Barato: lê estado; trabalho de verdade vai para fora da tela. */
    fun tick(ctx: Context) {
        val app = ctx.applicationContext
        SettingsJournalTv.ensure(app)
        if (working) return
        working = true
        Fundo.rodar("sofy-conserto") { try { runCatching { beat(app) }.onFailure { Log.w(TAG, "batida: $it") } } finally { working = false } }
    }

    private fun env(app: Context, now: Long): SofyRepair.Env {
        val pm = app.getSystemService(PowerManager::class.java)
        val idle = pm?.isInteractive == false || ProtetorTela.aberto()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val p = prefs(app)
        val runs = p.getString("runs", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < SofyRepair.HOUR }
        val indisponiveis = HashSet<Tool>()
        if (!SofyAi.configured(app)) indisponiveis += Tool.ASK_AI
        if (SettingsJournalTv.load(app).none { !it.undone && now - it.at < SofyRepair.DAY }) indisponiveis += Tool.UNDO_LAST_CHANGE
        return SofyRepair.Env(
            now, idle, NaoPerturbe.madrugada(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)),
            aiAvailable = SofyAi.configured(app), toolsLastHour = runs.size,
            failsLastHour = loadCases(app).sumOf { c -> c.tried.count { now - it.at < SofyRepair.HOUR && it.tool != Tool.REPORT && it.tool != Tool.ASK_AI &&
                (it.result == SofyRepair.Result.NOT_FIXED || it.result == SofyRepair.Result.FAILED) } },
            lastRunAt = runs.maxOrNull() ?: 0L,
            lastLauncherRestart = p.getLong("launcher_restart", 0L), lastReboot = p.getLong("reboot", 0L), unavailable = indisponiveis,
        )
    }

    private fun beat(app: Context) {
        val now = System.currentTimeMillis()
        // 1. sondas
        val ruins = probes.filter { runCatching { it.bad(app) }.getOrDefault(false) }
        for (pr in ruins) add(app, Signal(pr.type, pr.subject, now))
        // 2. barramento (só o que ainda não foi lido)
        val p = prefs(app)
        val lidoAte = p.getLong("bus_read", now - SofyRepair.HOUR)
        SofyBusTv.events(app).filter { it.at > lidoAte && it.at <= now }.mapNotNull { SofyRepair.fromBus(it) }.forEach { add(app, it) }
        p.edit().putLong("bus_read", now).apply()

        var cases = SofyRepair.prune(loadCases(app), now)
        if (p.getInt("ban_version", 0) != br.paulo.tvlauncher.BuildConfig.VERSION_CODE) {
            cases = SofyRepair.unban(cases)
            p.edit().putInt("ban_version", br.paulo.tvlauncher.BuildConfig.VERSION_CODE).apply()
        }
        var learned = loadLearned(app)
        val ruinsAgora = ruins.map { SofyRepair.key(it.type, it.subject) }.toSet()
        val comSonda = probes.map { SofyRepair.key(it.type, it.subject) }.toSet()

        // 3. conferir as tentativas pendentes
        cases = cases.map { c ->
            val last = c.tried.lastOrNull()?.takeIf { it.result == SofyRepair.Result.PENDING } ?: return@map c
            val temSonda = c.key in comSonda
            val voltou = if (temSonda) c.key in ruinsAgora else c.lastAt > last.at
            val pronto = if (temSonda) SofyRepair.dueToVerify(c, now) else voltou || now - last.at >= QUIET_MS
            if (!pronto) return@map c
            val (novo, aprender) = SofyRepair.onVerified(c, stillBad = voltou, now = now)
            aprender?.let { (tool, ok) ->
                learned = SofyRepair.learn(learned, c, tool, ok)
                // Desfazer um ajuste que não resolveu nada: o ajuste volta para o que a pessoa escolheu.
                if (tool == Tool.UNDO_LAST_CHANGE) { if (ok) SettingsJournalTv.keep(app, c.key) else SettingsJournalTv.redo(app, c.key) }
                val o = if (ok) "repaired" else "repair_failed"
                SofyBusTv.emit(app, SofyBus.Source.SOFY, o, ok = ok, detail = "${c.subject} ${tool.name.lowercase()}")
                ObserverTv.logShadow(app, o, "${c.type.name} ${c.subject}: ${tool.name.lowercase()}")
                Log.i(TAG, "$o: ${c.subject} com ${tool.name}")
            }
            novo
        }
        // Caso com sonda que ficou bom sozinho (sem tentativa pendente): fecha.
        cases = cases.map { c ->
            if (c.state == SofyRepair.State.OPEN && c.key in comSonda && c.key !in ruinsAgora && c.tried.lastOrNull()?.result != SofyRepair.Result.PENDING)
                c.copy(state = SofyRepair.State.FIXED) else c
        }
        // Laço é defeito como qualquer outro: percebe, troca de estratégia e registra (vira issue pela tarefa diária).
        cases = cases.map { c ->
            val t = c.takeIf { it.state == SofyRepair.State.OPEN }?.let { SofyRepair.looping(it, now) } ?: return@map c
            Log.w(TAG, "laço: ${c.subject} com ${t.name}; troca de estratégia")
            ObserverTv.logShadow(app, "repair_loop", "${c.type.name} ${c.subject}: ${t.name.lowercase()}")
            ObserverTv.logShadow(app, "self_defect", "REPAIR_LOOP: ${SofyRepair.brief(c)}")
            SofyRepair.onLoop(c, t, now)
        }
        saveLearned(app, learned)
        saveCases(app, cases)

        // 3b. de madrugada, com a TV parada: confere uma tela nossa (parte C). Não gasta a batida: é noite, não há fila.
        runCatching { SofyScreenCheckTv.talvez(app) }.onFailure { Log.w(TAG, "telas: $it") }

        // 4. um passo por batida, no caso aberto mais antigo que tenha o que fazer
        val e = env(app, now)
        // A rede de segurança disparou: o detector de laço deixou passar. Defeito da própria Sofy, registrado uma vez por hora.
        if (SofyRepair.guardTripped(e) && now - p.getLong("guard_tripped_at", 0L) > SofyRepair.HOUR) {
            p.edit().putLong("guard_tripped_at", now).apply()
            Log.w(TAG, "trava contra laço disparou: ${e.toolsLastHour} ferramentas na última hora")
            ObserverTv.logShadow(app, "self_defect", "REPAIR_LOOP_GUARD: ${e.toolsLastHour} ferramentas na última hora")
        }
        // Acesso e permissão primeiro: são a causa de muita coisa (sem shell, metade das ferramentas não roda).
        val ordem = cases.filter { it.state == SofyRepair.State.OPEN }
            .sortedWith(compareBy({ it.type != Type.ACCESS_LOST && it.type != Type.PERMISSION_LOST }, { it.firstAt }))
        for (c in ordem) {
            // Ferramenta sem alvo neste assunto (nenhum leitor de dado/chave registrado) não entra: falharia sempre e
            // viraria laço, que reabre a IA, que sugere a mesma ferramenta (Sala 25/11: norm:ai_drill2 x14 perguntas).
            val semAlvo = buildSet {
                if (!refreshers.containsKey(c.subject)) add(Tool.REFRESH_DATA)
                if (!switches.containsKey(c.subject)) add(Tool.DISABLE_FEATURE)
            }
            val passo = SofyRepair.next(c, learned, e.copy(unavailable = e.unavailable + semAlvo))
            if (passo is SofyRepair.Step.Wait) { noteWait(app, c, passo); continue }
            // Não está mais esperando: o motivo antigo sai do relatório (a Revisão leu um "alguém está usando" velho).
            p.edit().remove("wait:${c.key}").apply()
            val step = passo as? SofyRepair.Step.Run ?: continue
            val applied = runCatching { runTool(app, c, step.tool) }.onFailure { Log.w(TAG, "${step.tool}: $it") }.getOrNull()
            if (applied == null && step.tool == Tool.REPAIR_SHELL) {   // ainda não é a hora (regras do AutoPairing): tenta depois
                noteWait(app, c, SofyRepair.Step.Wait(Tool.REPAIR_SHELL, "pareamento: ${AutoPairing.lastWhy}"))
                continue
            }
            Log.i(TAG, "${c.subject}: ${step.tool} aplicada=$applied")
            ObserverTv.logShadow(app, "repair_try", "${c.type.name} ${c.subject}: ${step.tool.name.lowercase()}${if (applied == true) "" else " (não aplicou)"}")
            // Reinício do launcher/da TV: a tentativa já foi gravada antes de o processo morrer (saveCasesNow).
            if (applied == true && (step.tool == Tool.RESTART_LAUNCHER || step.tool == Tool.REBOOT)) break
            var novo = SofyRepair.onRun(c, step.tool, now, applied == true)
            if (step.tool == Tool.ASK_AI) novo = SofyRepair.withAiPlan(novo, aiPlan)
            saveCases(app, loadCases(app).map { if (it.key == c.key) novo else it })
            // Só ferramenta de verdade conta no ritmo (registrar e perguntar à IA não mexem na TV).
            if (step.tool != Tool.REPORT && step.tool != Tool.ASK_AI) {
                val runs = (p.getString("runs", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < SofyRepair.HOUR } + now)
                p.edit().putString("runs", runs.joinToString(",")).apply()
            }
            break
        }
    }

    @Volatile private var aiPlan: List<Tool> = emptyList()

    /**
     * Por que um caso está parado (o freio que barrou). Vai para o log e para o relatório, uma vez por mudança: a
     * Revisão (Sala, 25/11/2026) viu um caso aberto 7 batidas sem tentativa nenhuma e sem uma linha dizendo por quê.
     */
    private fun noteWait(app: Context, c: SofyRepair.Case, w: SofyRepair.Step.Wait) {
        val ate = if (w.until > 0) " (até " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(java.util.Date(w.until)) + ")" else ""
        val txt = "${w.tool.name.lowercase()}: ${w.why}$ate"
        val p = prefs(app)
        if (p.getString("wait:${c.key}", null) == txt) return
        p.edit().putString("wait:${c.key}", txt).apply()
        Log.i(TAG, "${c.subject} esperando ($txt)")
        ObserverTv.logShadow(app, "repair_wait", "${c.type.name} ${c.subject}: $txt")
    }

    // ---------------------------------------------------------------- as ferramentas

    /** Aplica. true = aplicou (vai conferir depois); false = não deu; null = ainda não é hora (só REPAIR_SHELL). */
    private fun runTool(app: Context, c: SofyRepair.Case, tool: Tool): Boolean? {
        val pkg = c.subject.removePrefix("app:").takeIf { c.subject.startsWith("app:") && Regex("[A-Za-z0-9_.]+").matches(it) }
        return when (tool) {
            Tool.GRANT_PERMISSIONS -> GuardiaoShizuku.verificarECurar(app) != null
            Tool.RELIGHT_ACCESSIBILITY -> { Privilegio.religarAcessibilidade(app); true }
            Tool.RELIGHT_WIRELESS_DEBUG -> { Privilegio.religarDepuracaoSemFio(app); true }
            Tool.RECONNECT_SHELL -> { GuardiaoShizuku.garantir(app, "Sofy", mesmoSemFalta = true); GuardiaoShizuku.comando(app, "echo ok", 30_000)?.contains("ok") == true }
            Tool.REPAIR_SHELL -> if (AutoPairing.maybe(app, SaudeAcesso.shellDownMs(app))) true else null
            Tool.RESTART_TUNNEL -> { TunelTailscale.reiniciar(); true }
            Tool.REFRESH_DATA -> refreshers[c.subject]?.let { it(app); true } ?: false
            Tool.FREE_MEMORY -> PainelSaude.liberar(app) != null
            Tool.RESTART_APP -> pkg != null && pkg != app.packageName && (JanelaNaFrente.pacote != pkg || env(app, System.currentTimeMillis()).idle) &&
                GuardiaoShizuku.comando(app, "am force-stop $pkg", 15_000) != null
            Tool.CLEAR_APP_CACHE -> pkg != null && pkg != app.packageName &&
                GuardiaoShizuku.comando(app, "pm clear --cache-only $pkg", 20_000)?.contains("Success") == true
            Tool.DISABLE_FEATURE -> switches[c.subject]?.invoke(app) ?: false
            Tool.RESTART_LAUNCHER -> {
                if (app.getSystemService(AudioManager::class.java)?.isMusicActive == true) return false
                prefs(app).edit().putLong("launcher_restart", System.currentTimeMillis()).commit()   // commit: o processo morre abaixo
                // Marca a tentativa antes de morrer (o processo novo confere se o sinal sumiu).
                saveCasesNow(app, c, tool)
                main.postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 500)
                true
            }
            Tool.REBOOT -> {
                prefs(app).edit().putLong("reboot", System.currentTimeMillis()).commit()
                saveCasesNow(app, c, tool)
                PainelControle.reiniciarTv(app) == null
            }
            Tool.ASK_AI -> {
                aiPlan = emptyList()   // se a pergunta falhar, o plano de OUTRO caso não pode ficar neste
                aiPlan = SofyAi.plan(app, c)
                SofyBusTv.emit(app, SofyBus.Source.SOFY, "asked_ai", ok = aiPlan.isNotEmpty(), detail = c.subject)
                aiPlan.isNotEmpty()
            }
            Tool.REPORT -> {
                // Vira issue pela tarefa diária, que lê o registro "observer" (mesmo caminho do autoexame).
                ObserverTv.logShadow(app, "self_defect", "REPAIR_${c.type.name}: ${SofyRepair.brief(c)}")
                SofyBusTv.emit(app, SofyBus.Source.SOFY, "repair_gave_up", ok = false, detail = "${c.type.name.lowercase()} ${c.subject}")
                true
            }
            Tool.UNDO_LAST_CHANGE -> SettingsJournalTv.undo(app, c.key, c.firstAt)
        }
    }

    /** Antes de matar o processo: grava a tentativa já, senão ela se perde com ele. */
    private fun saveCasesNow(app: Context, c: SofyRepair.Case, tool: Tool) {
        val novo = SofyRepair.onRun(c, tool, System.currentTimeMillis(), true)
        prefs(app).edit().putString("cases", JSONArray().apply { loadCases(app).map { if (it.key == c.key) novo else it }.forEach { put(json(it)) } }.toString()).commit()
    }
}
