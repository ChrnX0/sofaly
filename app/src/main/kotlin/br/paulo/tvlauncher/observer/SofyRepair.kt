package br.paulo.tvlauncher.observer

/**
 * A Sofy percebe e corrige sozinha (25/11/2026, pedido do Paulo: "uma IA que de fato percebe o que for de problema e
 * age para corrigir o que quer que seja, da maneira mais autônoma e automática possível", sem explicar na tela).
 *
 * Por quê: em 25/11 a Sala perdeu o shell, um botão parou de fazer efeito, e quem achou foi uma pessoa. Uma lista de
 * erros conhecidos nunca alcança o próximo defeito. Para quê: qualquer defeito vira um SINAL genérico ([Type]) com um
 * assunto ([Signal.subject]); cada assunto com defeito vira um CASO ([Case]); para cada caso ela escolhe a próxima
 * FERRAMENTA de uma caixa fechada ([Tool]), aplica, CONFERE se o sinal sumiu e, se não sumiu, tenta a seguinte. O que
 * deu certo vira receita aprendida ([SofyLearned]) e passa a ser tentado primeiro. Sem receita que sirva: pergunta à IA
 * ([Tool.ASK_AI], que só pode responder com ferramentas desta caixa) e, por fim, registra para virar issue ([Tool.REPORT]).
 *
 * Freios de segurança (não mudam com o aprendizado): nada destrutivo existe na caixa; ferramenta que interrompe só roda
 * com a TV parada; reinício só de madrugada; um soluço só (1 ocorrência) não dispara ferramenta pesada; uma trava contra
 * laço ([LOOP_GUARD_PER_HOUR]). O RITMO não tem teto fixo: ele se calibra sozinho ([pace]) pela gravidade do caso, pelo
 * custo da ferramenta, pelo placar dela e pelas falhas seguidas (recuo exponencial). Pura: SofyRepairTest. A parte
 * Android é [SofyRepairTv].
 */
object SofyRepair {
    /** Os sinais genéricos: o que caracteriza QUALQUER defeito, previsto ou não. */
    enum class Type { NO_EFFECT, ERROR, CRASH, SLOW, ACCESS_LOST, PERMISSION_LOST, INCOHERENT, SCREEN_BROKEN }

    /**
     * [subject] diz ONDE: "shell", "accessibility", "wireless_debug", "tunnel", "home", "perm:<nome>", "app:<pacote>",
     * "tile:<id>", "screen:<nome>", "data:<nome>", "feature:<nome>". [needs] = do que aquilo depende (para o conserto
     * de um botão sem efeito ir direto na causa). Sem dados pessoais em [detail].
     */
    data class Signal(val type: Type, val subject: String, val at: Long, val detail: String = "", val needs: Set<Need> = emptySet())

    /** Do que um botão/tela/função depende (proposta B: cada um declara). */
    enum class Need { SHELL, OWNER, ACCESSIBILITY, OVERLAY, SECURE_SETTINGS, USAGE_STATS, NETWORK }

    /** A caixa de ferramentas. [interrupts] = atrapalha quem está vendo; [heavy] = só com defeito repetido. */
    enum class Tool(val interrupts: Boolean = false, val heavy: Boolean = false, val nightOnly: Boolean = false) {
        GRANT_PERMISSIONS,            // devolve o que o dono/shell pode conceder (autocura)
        RELIGHT_ACCESSIBILITY,
        RELIGHT_WIRELESS_DEBUG,
        RECONNECT_SHELL,              // o guardião conecta de novo
        REPAIR_SHELL(interrupts = true),   // pareia de novo sozinho (AutoPairing), só com o protetor na frente
        RESTART_TUNNEL,
        REFRESH_DATA,                 // joga fora o cache daquele dado e lê de novo
        FREE_MEMORY,                  // fecha apps em segundo plano
        RESTART_APP(interrupts = true),    // interrompe só se o app estiver na frente (quem aplica confere)
        CLEAR_APP_CACHE(heavy = true),
        RESTART_LAUNCHER(interrupts = true, heavy = true),
        UNDO_LAST_CHANGE(heavy = true),    // volta o último ajuste mudado antes do defeito
        DISABLE_FEATURE(heavy = true),     // desliga a função que quebrou (ela volta na próxima versão)
        REBOOT(interrupts = true, heavy = true, nightOnly = true),
        ASK_AI,                       // diagnóstico da IA da nuvem: devolve ferramentas DESTA caixa
        REPORT,                       // defeito de código: vira issue pela tarefa diária
    }

    enum class Result { PENDING, FIXED, NOT_FIXED, FAILED }
    data class Attempt(val tool: Tool, val at: Long, val result: Result)
    enum class State { OPEN, FIXED, GIVEN_UP }

    data class Case(
        val key: String, val type: Type, val subject: String, val needs: Set<Need>,
        val firstAt: Long, val lastAt: Long, val count: Int,
        val tried: List<Attempt> = emptyList(), val state: State = State.OPEN,
        /** O que o barramento contou logo antes (o que mudou): contexto para a IA e para a issue. */
        val context: List<String> = emptyList(),
        /** Ferramentas que a IA indicou para este caso (entram na frente da fila). */
        val aiPlan: List<Tool> = emptyList(),
        val detail: String = "",
        /** Ferramentas que entraram em laço neste caso -> quando (ficam de fora por [LOOP_BAN_MS] ou até consertar). */
        val banned: Map<Tool, Long> = emptyMap(),
    )

    fun key(type: Type, subject: String) = "${type.name}:$subject"

    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR
    /** Um caso consertado que volta em menos disto: o conserto não segurou (conta contra a receita). */
    const val RELAPSE_MS = 6 * HOUR
    /** Caso sem sinal novo há tanto tempo = sumiu sozinho; sai da lista. */
    const val STALE_MS = 3 * DAY
    /**
     * A única trava fixa do ritmo: mais que isto de ferramentas numa hora (todos os casos) é laço, não conserto.
     * Paulo (25/11/2026): "ela tem que saber se calibrar sozinha"; o antigo teto de 6 por hora barrava o shell à toa.
     */
    const val LOOP_GUARD_PER_HOUR = 20

    // ---------------------------------------------------------------- laço também é defeito (Paulo, 25/11/2026)

    /**
     * "Repetir a mesma ação sem parar não é um erro que ela deveria entender e corrigir também?" Sim: a mesma ferramenta
     * [LOOP_REPEAT] vezes numa hora no mesmo caso, sem consertar, é um laço. Ela percebe ([looping]), troca de
     * estratégia ([onLoop]: tira a ferramenta deste caso, anota no contexto e libera uma pergunta nova à IA) e a falha
     * entra no placar. A trava fixa [LOOP_GUARD_PER_HOUR] fica só como rede, caso este detector falhe; disparar a trava
     * é registrado como defeito da própria Sofy.
     */
    const val LOOP_REPEAT = 3
    const val LOOP_BAN_MS = 24 * 3_600_000L

    /**
     * Quanto a ferramenta fica de fora: um dia dividido pela gravidade. Sala (25/11): o laço tirou o REPAIR_SHELL, a única
     * ferramenta que conserta o shell, por um dia inteiro; com acesso perdido são 6 h. Versão nova também limpa ([unban]).
     */
    fun banMs(c: Case) = LOOP_BAN_MS / urgency(c.type)

    fun banned(c: Case, now: Long): Set<Tool> = c.banned.filterValues { now - it < banMs(c) }.keys

    /** Versão nova do app = estratégia nova: as proibições por laço caem (o motivo do laço pode ter sido consertado). */
    fun unban(cases: List<Case>) = cases.map { if (it.banned.isEmpty()) it else it.copy(banned = emptyMap()) }

    /** A ferramenta em laço neste caso, se houver. */
    fun looping(c: Case, now: Long): Tool? = c.tried
        .filter { now - it.at < HOUR && it.tool != Tool.REPORT && it.tool != Tool.ASK_AI && (it.result == Result.NOT_FIXED || it.result == Result.FAILED) }
        .groupingBy { it.tool }.eachCount().filter { (t, n) -> n >= LOOP_REPEAT && t !in banned(c, now) }.keys.firstOrNull()

    fun onLoop(c: Case, t: Tool, now: Long): Case = c.copy(
        banned = c.banned + (t to now), aiPlan = c.aiPlan - t,
        context = (c.context + "laço: ${t.name.lowercase()} repetiu $LOOP_REPEAT vezes sem consertar").takeLast(MAX_CONTEXT),
    )

    /** A rede de segurança disparou: o detector de laço deixou passar (defeito da própria Sofy). */
    fun guardTripped(e: Env) = e.toolsLastHour >= LOOP_GUARD_PER_HOUR
    const val VERIFY_AFTER_MS = 2 * 60_000L
    const val REBOOT_GAP_MS = 7 * DAY
    const val RESTART_LAUNCHER_GAP_MS = 6 * HOUR

    // ---------------------------------------------------------------- 1. sinal -> caso

    /** Um sinal chega: abre o caso, soma a ocorrência, ou reabre um que parecia consertado. */
    fun onSignal(cases: List<Case>, s: Signal, context: List<String> = emptyList()): List<Case> {
        val k = key(s.type, s.subject)
        val old = cases.firstOrNull { it.key == k }
            ?: return cases + Case(k, s.type, s.subject, s.needs, s.at, s.at, 1, context = context.takeLast(MAX_CONTEXT), detail = s.detail)
        val relapse = old.state == State.FIXED && s.at - old.lastAt < RELAPSE_MS
        val novo = old.copy(
            lastAt = s.at, count = old.count + 1, needs = old.needs + s.needs, detail = s.detail.ifEmpty { old.detail },
            // Desistido só fica desistido se não há ferramenta (casos de versões antigas, que desistiam depois do REPORT, reabrem).
            state = if (old.state == State.GIVEN_UP && !hasTools(old)) State.GIVEN_UP else State.OPEN,
            // Voltou logo depois do "consertado": aquela ferramenta não resolveu de verdade.
            tried = if (relapse) old.tried.mapLast { if (it.result == Result.FIXED) it.copy(result = Result.NOT_FIXED) else it } else old.tried,
        )
        return cases.map { if (it.key == k) novo else it }
    }
    const val MAX_CONTEXT = 12

    /** Há alguma ferramenta de verdade (além de perguntar e registrar) para este caso? */
    fun hasTools(c: Case) = candidates(c).any { it != Tool.ASK_AI && it != Tool.REPORT }

    private fun <T> List<T>.mapLast(f: (T) -> T): List<T> = if (isEmpty()) this else dropLast(1) + f(last())

    /** Limpeza: casos parados há [STALE_MS] saem (inclusive os desistidos: se voltar, começa de novo). */
    fun prune(cases: List<Case>, now: Long) = cases.filter { now - it.lastAt < STALE_MS && !nowNotDefect(it) }

    /** Caso aberto por uma versão antiga a partir de um evento que hoje não é mais defeito: sai da fila. */
    private fun nowNotDefect(c: Case): Boolean {
        val k = c.subject.removePrefix("bus:").takeIf { c.subject.startsWith("bus:") } ?: return false
        return k in NOT_DEFECTS || k.substringBefore('.') in NOT_DEFECT_SOURCES || (k.startsWith("sofy.") && k !in SOFY_DEFECTS)
    }

    // ---------------------------------------------------------------- 2. receitas (o ponto de partida, não o limite)

    /** As ferramentas que fazem sentido para um caso, na ordem de tentar. O aprendizado e a IA reordenam/ampliam. */
    fun candidates(c: Case): List<Tool> {
        val porNecessidade = c.needs.flatMap { n ->
            when (n) {
                Need.SHELL -> listOf(Tool.RELIGHT_WIRELESS_DEBUG, Tool.RECONNECT_SHELL, Tool.REPAIR_SHELL)
                Need.ACCESSIBILITY -> listOf(Tool.RELIGHT_ACCESSIBILITY)
                Need.OVERLAY, Need.SECURE_SETTINGS, Need.USAGE_STATS, Need.OWNER -> listOf(Tool.GRANT_PERMISSIONS)
                Need.NETWORK -> listOf(Tool.REFRESH_DATA)
            }
        }
        val app = c.subject.startsWith("app:")
        val base = when (c.type) {
            Type.ACCESS_LOST -> when (c.subject) {
                "shell" -> listOf(Tool.RELIGHT_WIRELESS_DEBUG, Tool.RECONNECT_SHELL, Tool.REPAIR_SHELL)
                "accessibility" -> listOf(Tool.RELIGHT_ACCESSIBILITY, Tool.RESTART_LAUNCHER)
                "wireless_debug" -> listOf(Tool.RELIGHT_WIRELESS_DEBUG)
                "tunnel" -> listOf(Tool.RESTART_TUNNEL)
                else -> listOf(Tool.GRANT_PERMISSIONS)
            }
            Type.PERMISSION_LOST -> listOf(Tool.GRANT_PERMISSIONS)
            // App que não abriu: fechar e abrir de novo / limpar o cache dele; botão sem efeito: permissões, reinício.
            Type.NO_EFFECT -> if (app) listOf(Tool.RESTART_APP, Tool.CLEAR_APP_CACHE, Tool.FREE_MEMORY) else listOf(Tool.GRANT_PERMISSIONS, Tool.RESTART_LAUNCHER)
            Type.ERROR -> if (app) listOf(Tool.RESTART_APP, Tool.CLEAR_APP_CACHE)
                else listOf(Tool.REFRESH_DATA, Tool.UNDO_LAST_CHANGE, Tool.RESTART_LAUNCHER, Tool.DISABLE_FEATURE)
            Type.CRASH -> if (app) listOf(Tool.RESTART_APP, Tool.CLEAR_APP_CACHE)
                else listOf(Tool.UNDO_LAST_CHANGE, Tool.DISABLE_FEATURE, Tool.RESTART_LAUNCHER)
            Type.SLOW -> if (app) listOf(Tool.FREE_MEMORY, Tool.RESTART_APP, Tool.CLEAR_APP_CACHE, Tool.REBOOT)
                else listOf(Tool.FREE_MEMORY, Tool.RESTART_LAUNCHER, Tool.REBOOT)
            // Quebra do normal da casa (SofyNorms): conferida uma vez por dia, então nenhuma ferramenta daria para
            // verificar em minutos (o placar aprenderia "consertou" à toa). Vai para a IA do dono e para o relato.
            Type.INCOHERENT -> if (c.subject.startsWith("norm:")) emptyList() else listOf(Tool.REFRESH_DATA, Tool.RESTART_LAUNCHER)
            Type.SCREEN_BROKEN -> emptyList()   // texto cortado, foco perdido: é defeito de código
        }
        // DISABLE_FEATURE só para o que é uma função desligável; CLEAR_APP_CACHE/RESTART_APP só para app.
        return (c.aiPlan + porNecessidade + base + Tool.ASK_AI + Tool.REPORT).distinct().filter { fits(it, c) }
    }

    private fun fits(t: Tool, c: Case) = when (t) {
        // Texto cortado/foco perdido é defeito de código: nenhuma ferramenta conserta e a IA não tem o que escolher.
        // Vai direto para o relatório (e para a issue da tarefa diária).
        Tool.ASK_AI -> c.type != Type.SCREEN_BROKEN
        Tool.RESTART_APP, Tool.CLEAR_APP_CACHE -> c.subject.startsWith("app:")
        Tool.DISABLE_FEATURE -> c.subject.startsWith("feature:") || c.subject.startsWith("tile:")
        else -> true
    }

    // ---------------------------------------------------------------- 3. aprendizado

    /** Placar de cada ferramenta por tipo de caso ("TIPO:assunto sem o detalhe depois de ':'"). */
    data class Score(val ok: Int = 0, val fail: Int = 0) {
        /** Taxa com um empurrão para o meio (1 acerto em 1 não vale mais que 8 em 10). */
        val rate get() = (ok + 1.0) / (ok + fail + 2.0)
    }

    /** "app:com.x" e "app:com.y" aprendem juntos (a receita é do TIPO de assunto), "shell" aprende sozinho. */
    fun family(c: Case) = "${c.type.name}:${c.subject.substringBefore(':')}"

    fun learn(l: SofyLearned, c: Case, tool: Tool, fixed: Boolean): SofyLearned {
        if (tool == Tool.ASK_AI || tool == Tool.REPORT) return l
        val f = family(c)
        val m = l[f].orEmpty()
        val s = m[tool] ?: Score()
        return l + (f to (m + (tool to if (fixed) s.copy(ok = s.ok + 1) else s.copy(fail = s.fail + 1))))
    }

    /** A ordem final: o que já consertou este tipo de caso vem primeiro; ASK_AI e REPORT ficam sempre no fim. */
    fun ordered(c: Case, l: SofyLearned): List<Tool> {
        val cand = candidates(c)
        val placar = l[family(c)].orEmpty()
        val (fim, meio) = cand.partition { it == Tool.ASK_AI || it == Tool.REPORT }
        // Estável: sem placar, vale a ordem da receita. Ferramenta leve com a mesma taxa vem antes da pesada.
        val ord = meio.withIndex().sortedWith(
            compareByDescending<IndexedValue<Tool>> { placar[it.value]?.rate ?: 0.5 }
                .thenBy { it.value.heavy }.thenBy { it.index },
        ).map { it.value }
        return ord + fim
    }

    // ---------------------------------------------------------------- 4. o próximo passo (com os freios)

    data class Env(
        val now: Long,
        /** Ninguém mexendo: tela apagada ou o nosso protetor na frente. */
        val idle: Boolean,
        /** Madrugada (a janela do Não perturbe / da manutenção noturna). */
        val night: Boolean,
        /** Há chave/caminho para a IA da nuvem. */
        val aiAvailable: Boolean,
        /** Ferramentas rodadas na última hora (todos os casos) e quando foram o último reinício do launcher e da TV. */
        val toolsLastHour: Int = 0, val lastLauncherRestart: Long = 0, val lastReboot: Long = 0,
        /** Tentativas que NÃO consertaram na última hora (todos os casos) e a última ferramenta rodada: a pressão. */
        val failsLastHour: Int = 0, val lastRunAt: Long = 0,
        /** Ferramentas que esta TV não tem como rodar agora (ex.: sem dono, sem acessibilidade). */
        val unavailable: Set<Tool> = emptySet(),
    )

    sealed class Step {
        data class Run(val tool: Tool) : Step()
        /** Há o que tentar, mas não agora (alguém vendo, não é madrugada, recuo). [until] = quando volta (0 = depende da TV). */
        data class Wait(val tool: Tool, val why: String, val until: Long = 0) : Step()
        /** Esperando conferir a última tentativa. */
        object Verify : Step()
        /** Acabou a caixa: fica registrado (já passou por REPORT). */
        object Done : Step()
    }

    // ---------------------------------------------------------------- o ritmo que se calibra sozinho

    /** Gravidade: acesso/permissão perdidos derrubam muita coisa; lentidão incomoda, mas espera. */
    fun urgency(t: Type): Int = when (t) {
        Type.ACCESS_LOST, Type.PERMISSION_LOST -> 4
        Type.NO_EFFECT, Type.CRASH -> 3
        Type.ERROR, Type.INCOHERENT -> 2
        Type.SLOW, Type.SCREEN_BROKEN -> 1
    }

    /** Custo/risco da ferramenta: 1 (leve) a 5 (pesada e interrompe). Registrar e perguntar à IA não custam nada. */
    fun cost(t: Tool): Int = if (t == Tool.REPORT || t == Tool.ASK_AI) 0 else 1 + (if (t.heavy) 2 else 0) + (if (t.interrupts) 2 else 0)

    const val MIN = 60_000L
    /** Base do recuo de uma ferramenta que não consertou (antes de gravidade, custo e placar). */
    const val RETRY_BASE_MS = 15 * MIN
    const val RETRY_MIN_MS = 2 * MIN
    /** A espera máxima: um dia dividido pela gravidade (acesso perdido volta a tentar pelo menos a cada 6 h). */
    fun retryMaxMs(c: Case) = DAY / urgency(c.type)
    /** Sob pressão (outras tentativas falhando), um caso menos grave espera isto por falha recente antes de começar. */
    const val PRESSURE_STEP_MS = 3 * MIN

    /**
     * Quanto esperar para repetir [t] neste caso depois de [fails] tentativas que não consertaram: dobra a cada falha
     * (recuo exponencial), encolhe com a gravidade e com o placar da ferramenta (o que costuma funcionar volta logo),
     * cresce com o custo e com a pressão geral (muita coisa falhando = calma no que é menos grave).
     */
    fun retryGapMs(c: Case, t: Tool, fails: Int, rate: Double, failsLastHour: Int): Long {
        if (fails <= 0) return 0
        val u = urgency(c.type)
        val pressao = if (u >= 4) 1.0 else 1.0 + failsLastHour / 4.0
        val gap = RETRY_BASE_MS * maxOf(1, cost(t)) * Math.pow(2.0, (fails - 1).coerceAtMost(12).toDouble()) * (1.5 - rate) * pressao / u
        return gap.toLong().coerceIn(RETRY_MIN_MS, retryMaxMs(c))
    }

    /** Ferramentas que não consertaram este caso (o FIXED zera: o recuo é das falhas seguidas). */
    private fun failsSinceFix(c: Case, t: Tool): Int =
        c.tried.takeLastWhile { it.result != Result.FIXED }.count { it.tool == t && (it.result == Result.NOT_FIXED || it.result == Result.FAILED) }

    fun next(c: Case, l: SofyLearned, e: Env): Step {
        if (c.state != State.OPEN) return Step.Done
        if (c.tried.lastOrNull()?.result == Result.PENDING) return Step.Verify
        val placar = l[family(c)].orEmpty()
        val reported = c.tried.any { it.tool == Tool.REPORT }
        // Depois de um laço, a IA pode ser consultada de novo (o caso mudou: uma estratégia caiu).
        val ultimoLaco = c.banned.values.maxOrNull() ?: 0L
        val askedRecently = c.tried.any { it.tool == Tool.ASK_AI && e.now - it.at < DAY && it.at >= ultimoLaco }
        val fora = banned(c, e.now)
        var cedo: Step.Wait? = null   // a ferramenta que volta primeiro, se todas estiverem no recuo
        for (t in ordered(c, l)) {
            if (t in e.unavailable) continue
            if (t in fora) {
                // Fora por laço: não some em silêncio (Sala 473144, 25/11: tudo banido = Done mudo, sem log nenhum).
                val volta = (c.banned[t] ?: e.now) + banMs(c)
                if (cedo == null || volta < cedo.until) cedo = Step.Wait(t, "fora por laço", volta)
                continue
            }
            if (t == Tool.ASK_AI) { if (!e.aiAvailable || askedRecently) continue; return Step.Run(t) }
            if (t == Tool.REPORT) {
                if (!reported) return Step.Run(t)   // registrar não gasta nada nem atrapalha ninguém
                continue
            }
            // Um soluço só não dispara ferramenta pesada (uma queda de app isolada não limpa cache nem reinicia nada).
            if (t.heavy && c.count < 2) continue
            if (t == Tool.RESTART_LAUNCHER && e.now - e.lastLauncherRestart < RESTART_LAUNCHER_GAP_MS) continue
            if (t == Tool.REBOOT && e.now - e.lastReboot < REBOOT_GAP_MS) continue
            // Recuo próprio desta ferramenta neste caso.
            val fails = failsSinceFix(c, t)
            if (fails > 0) {
                val ultima = c.tried.last { it.tool == t }.at
                val volta = ultima + retryGapMs(c, t, fails, placar[t]?.rate ?: 0.5, e.failsLastHour)
                if (e.now < volta) {
                    if (cedo == null || volta < cedo.until) cedo = Step.Wait(t, "recuo: não consertou $fails vez(es)", volta)
                    continue
                }
            }
            if (e.toolsLastHour >= LOOP_GUARD_PER_HOUR) return Step.Wait(t, "trava contra laço", e.lastRunAt + HOUR)
            // Pressão: com outras tentativas falhando, o caso menos grave dá um respiro antes de mexer de novo.
            val u = urgency(c.type)
            if (u < 4 && e.failsLastHour > 0) {
                val respiro = e.lastRunAt + PRESSURE_STEP_MS * e.failsLastHour / u
                if (e.now < respiro) return Step.Wait(t, "calma: ${e.failsLastHour} tentativa(s) falharam na última hora", respiro)
            }
            if (t.nightOnly && !(e.night && e.idle)) return Step.Wait(t, "só de madrugada, com a TV parada")
            if (t.interrupts && !e.idle) return Step.Wait(t, "alguém está usando a TV")
            return Step.Run(t)
        }
        return cedo ?: Step.Done
    }

    /**
     * Marca a ferramenta como aplicada (esperando conferir). REPORT registra; o caso só fecha como "desisti" quando não
     * há ferramenta nenhuma para ele (defeito de código). Com ferramenta, ela continua tentando no ritmo do recuo.
     */
    fun onRun(c: Case, tool: Tool, now: Long, applied: Boolean): Case = when {
        tool == Tool.REPORT -> c.copy(tried = c.tried + Attempt(tool, now, Result.NOT_FIXED), state = if (hasTools(c)) State.OPEN else State.GIVEN_UP)
        tool == Tool.ASK_AI -> c.copy(tried = c.tried + Attempt(tool, now, if (applied) Result.NOT_FIXED else Result.FAILED))
        !applied -> c.copy(tried = c.tried + Attempt(tool, now, Result.FAILED))
        else -> c.copy(tried = c.tried + Attempt(tool, now, Result.PENDING))
    }

    /** A conferência: o sinal sumiu ([stillBad] = false) ou continua. Devolve o caso e o que aprender. */
    fun onVerified(c: Case, stillBad: Boolean, now: Long): Pair<Case, Pair<Tool, Boolean>?> {
        val last = c.tried.lastOrNull()?.takeIf { it.result == Result.PENDING } ?: return c to null
        val r = if (stillBad) Result.NOT_FIXED else Result.FIXED
        val novo = c.copy(tried = c.tried.dropLast(1) + last.copy(result = r), state = if (stillBad) State.OPEN else State.FIXED, lastAt = if (stillBad) c.lastAt else now,
            banned = if (stillBad) c.banned else emptyMap())   // consertou: a próxima vez começa sem proibições
        return novo to (last.tool to !stillBad)
    }

    /** Já dá para conferir a última tentativa? */
    fun dueToVerify(c: Case, now: Long) = c.tried.lastOrNull()?.let { it.result == Result.PENDING && now - it.at >= VERIFY_AFTER_MS } == true

    // ---------------------------------------------------------------- o barramento como fonte de sinais

    /** Eventos com ok = false que NÃO são defeito consertável (avisos à pessoa, o próprio ciclo de conserto). */
    private val NOT_DEFECTS = setOf(
        "device.battery_low", "device.key_stuck", "house.panel_pin_wrong", "network.wifi_advice", "network.game_lag",
        "sofy.app_powerful", "sofy.disk_filling", "sofy.night_skipped", "sofy.access_repair_failed", "sofy.repair_failed",
        "sofy.repair_gave_up", "plugin.disabled", "sofy.app_crashy",
        // Medições (Sala, 25/11/2026: o primeiro caso da Sala foi "audio_route hdmi not_measured"). "Não deu para medir,
        // fico no padrão" é resposta de calibração, não defeito: virava caso e ia gastar a pergunta de IA do dono.
        "device.audio_route",
    )
    /** Fontes cujo ok = false é resultado de medição/calibração ("unsure keep"), nunca defeito. */
    private val NOT_DEFECT_SOURCES = setOf("measure")
    /**
     * Da própria Sofy, só estes viram caso. O resto é ela contando o que fez (perguntou à IA, tentou um conserto...):
     * virar caso era um laço (Sala, 25/11/2026: "sofy.asked_ai" abriu um caso novo, que ia perguntar à IA de novo).
     */
    private val SOFY_DEFECTS = setOf("sofy.self_defect", "sofy.version_worse")

    /** Um evento do barramento que deu errado vira sinal. Null = não é defeito para o ciclo.  */
    fun fromBus(e: SofyBus.Event): Signal? {
        if (e.ok || e.key in NOT_DEFECTS || e.key.substringBefore('.') in NOT_DEFECT_SOURCES || e.kind.startsWith("drop_")) return null
        if (e.key.startsWith("sofy.") && e.key !in SOFY_DEFECTS) return null
        return when (e.key) {
            "crash.launcher_crash" -> Signal(Type.CRASH, "home", e.at, e.detail)
            "sofy.version_worse" -> Signal(Type.SLOW, "home", e.at, e.detail)
            "sofy.self_defect" -> Signal(Type.ERROR, "feature:sofy_${e.detail}", e.at)
            "update.google_broke" -> Signal(Type.ERROR, "system:google_update", e.at, e.detail)
            "house.ha_call" -> Signal(Type.ERROR, "data:home_assistant", e.at, needs = setOf(Need.NETWORK))
            else -> Signal(Type.ERROR, "bus:${e.key}", e.at, e.detail)   // o que ninguém previu
        }
    }

    // ---------------------------------------------------------------- 5. a IA só escolhe dentro da caixa

    /** A resposta da IA vira ferramentas DESTA caixa; o que não existe aqui é ignorado. Nunca ASK_AI de novo. */
    fun parseAiPlan(names: List<String>): List<Tool> =
        names.mapNotNull { n -> Tool.entries.firstOrNull { it.name.equals(n.trim(), ignoreCase = true) } }
            .filter { it != Tool.ASK_AI }.distinct().take(4)

    fun withAiPlan(c: Case, plan: List<Tool>) = c.copy(aiPlan = plan)

    /** A ficha do caso em texto curto, para a IA e para a issue. Sem dados pessoais: só tipo, assunto, contagem e contexto do barramento. */
    fun brief(c: Case): String = buildString {
        append("${c.type.name} em ${c.subject}; ${c.count} vez(es)")
        if (c.detail.isNotEmpty()) append("; ${c.detail}")
        if (c.needs.isNotEmpty()) append("; depende de ${c.needs.joinToString(",") { it.name.lowercase() }}")
        if (c.tried.isNotEmpty()) append("; tentei ${c.tried.joinToString(",") { "${it.tool.name.lowercase()}=${it.result.name.lowercase()}" }}")
        if (c.context.isNotEmpty()) append("; antes: ${c.context.joinToString(" | ")}")
    }
}

/** Placar aprendido: família do caso -> ferramenta -> acertos/erros. */
typealias SofyLearned = Map<String, Map<SofyRepair.Tool, SofyRepair.Score>>
