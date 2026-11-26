package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyRepair
import br.paulo.tvlauncher.observer.SofyRepair.Need
import br.paulo.tvlauncher.observer.SofyRepair.Result
import br.paulo.tvlauncher.observer.SofyRepair.Signal
import br.paulo.tvlauncher.observer.SofyRepair.State
import br.paulo.tvlauncher.observer.SofyRepair.Step
import br.paulo.tvlauncher.observer.SofyRepair.Tool
import br.paulo.tvlauncher.observer.SofyRepair.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O ciclo geral da Sofy: sinal -> caso -> ferramenta -> confere -> aprende, com os freios. */
class SofyRepairTest {
    private val t0 = 1_800_000_000_000L
    private val min = 60_000L
    private fun env(now: Long = t0, idle: Boolean = false, night: Boolean = false, ai: Boolean = false, hour: Int = 0) =
        SofyRepair.Env(now, idle, night, ai, toolsLastHour = hour)
    private fun open(type: Type, subject: String, n: Int = 1, needs: Set<Need> = emptySet()): SofyRepair.Case {
        var cs = emptyList<SofyRepair.Case>()
        repeat(n) { cs = SofyRepair.onSignal(cs, Signal(type, subject, t0 + it, needs = needs)) }
        return cs.single()
    }
    /** Roda o caso até parar, com o sinal sempre presente; devolve as ferramentas na ordem. */
    private fun drain(c0: SofyRepair.Case, e: SofyRepair.Env, l: br.paulo.tvlauncher.observer.SofyLearned = emptyMap()): List<Tool> {
        var c = c0; val out = ArrayList<Tool>()
        while (true) {
            val s = SofyRepair.next(c, l, e) as? Step.Run ?: break
            out += s.tool
            c = SofyRepair.onRun(c, s.tool, e.now, applied = true)
            c = SofyRepair.onVerified(c, stillBad = true, now = e.now).first
        }
        return out
    }

    @Test fun signalsBecomeOneCasePerSubject() {
        var cs = SofyRepair.onSignal(emptyList(), Signal(Type.ACCESS_LOST, "shell", t0))
        cs = SofyRepair.onSignal(cs, Signal(Type.ACCESS_LOST, "shell", t0 + min))
        cs = SofyRepair.onSignal(cs, Signal(Type.CRASH, "app:com.x", t0 + min))
        assertEquals(2, cs.size)
        assertEquals(2, cs.first().count)
    }

    @Test fun theDayOf25NovTheButtonWithoutEffectLeadsToTheShell() {
        // O botão do Protetor não fez efeito e declara que depende do shell: o conserto vai direto na causa.
        val c = open(Type.NO_EFFECT, "tile:screensaver", needs = setOf(Need.SHELL))
        assertEquals(listOf(Tool.RELIGHT_WIRELESS_DEBUG, Tool.RECONNECT_SHELL), SofyRepair.ordered(c, emptyMap()).take(2))
        // Com alguém vendo, parear de novo espera; com a TV parada, roda.
        val depois = SofyRepair.onRun(SofyRepair.onRun(c, Tool.RELIGHT_WIRELESS_DEBUG, t0, false), Tool.RECONNECT_SHELL, t0, false)
        assertEquals(Step.Wait(Tool.REPAIR_SHELL, "alguém está usando a TV"), SofyRepair.next(depois, emptyMap(), env()))
        assertEquals(Step.Run(Tool.REPAIR_SHELL), SofyRepair.next(depois, emptyMap(), env(idle = true)))
    }

    @Test fun verifiesAndLearns() {
        var c = open(Type.ACCESS_LOST, "accessibility")
        c = SofyRepair.onRun(c, Tool.RELIGHT_ACCESSIBILITY, t0, applied = true)
        assertEquals(Step.Verify, SofyRepair.next(c, emptyMap(), env()))
        assertFalse(SofyRepair.dueToVerify(c, t0 + min))
        assertTrue(SofyRepair.dueToVerify(c, t0 + 3 * min))
        val (ok, aprender) = SofyRepair.onVerified(c, stillBad = false, now = t0 + 3 * min)
        assertEquals(State.FIXED, ok.state)
        assertEquals(Tool.RELIGHT_ACCESSIBILITY to true, aprender)
        val l = SofyRepair.learn(emptyMap(), ok, Tool.RELIGHT_ACCESSIBILITY, true)
        assertEquals(1, l.getValue("ACCESS_LOST:accessibility").getValue(Tool.RELIGHT_ACCESSIBILITY).ok)
    }

    @Test fun whatWorkedBeforeIsTriedFirst() {
        val c = open(Type.SLOW, "app:com.x", n = 2)
        assertEquals(Tool.FREE_MEMORY, SofyRepair.ordered(c, emptyMap()).first())
        var l: br.paulo.tvlauncher.observer.SofyLearned = emptyMap()
        repeat(3) { l = SofyRepair.learn(l, c, Tool.RESTART_APP, true) }
        l = SofyRepair.learn(l, c, Tool.FREE_MEMORY, false)
        // Aprendido com um app vale para outro app (a família é o tipo de assunto).
        val outro = open(Type.SLOW, "app:com.y", n = 2)
        assertEquals(Tool.RESTART_APP, SofyRepair.ordered(outro, l).first())
        assertEquals(listOf(Tool.ASK_AI, Tool.REPORT), SofyRepair.ordered(outro, l).takeLast(2))
    }

    @Test fun relapseCountsAgainstTheFix() {
        var cs = listOf(open(Type.ACCESS_LOST, "accessibility"))
        cs = listOf(SofyRepair.onVerified(SofyRepair.onRun(cs[0], Tool.RELIGHT_ACCESSIBILITY, t0, true), false, t0 + 3 * min).first)
        cs = SofyRepair.onSignal(cs, Signal(Type.ACCESS_LOST, "accessibility", t0 + 60 * min))
        assertEquals(State.OPEN, cs[0].state)
        assertEquals(Result.NOT_FIXED, cs[0].tried.last().result)
    }

    @Test fun brakes() {
        // Um soluço só: nada pesado. A queda isolada de um app não limpa cache.
        val uma = open(Type.CRASH, "app:com.x")
        assertEquals(listOf(Tool.RESTART_APP, Tool.REPORT), drain(uma, env(idle = true)))
        // Repetido e com a TV parada: tenta tudo, menos reiniciar a TV fora da madrugada.
        val lenta = open(Type.SLOW, "home", n = 3)
        assertEquals(listOf(Tool.FREE_MEMORY, Tool.RESTART_LAUNCHER), drain(lenta, env(idle = true)))
        assertEquals(listOf(Tool.FREE_MEMORY, Tool.RESTART_LAUNCHER, Tool.REBOOT, Tool.REPORT), drain(lenta, env(idle = true, night = true)))
        // Alguém vendo: só o que não interrompe.
        assertEquals(listOf(Tool.FREE_MEMORY), drain(lenta, env()))
        // Só a trava contra laço é fixa.
        assertTrue(SofyRepair.next(lenta, emptyMap(), env(idle = true, hour = SofyRepair.LOOP_GUARD_PER_HOUR)) is Step.Wait)
        assertTrue(SofyRepair.next(lenta, emptyMap(), env(idle = true, hour = 6)) is Step.Run)
        // Nada destrutivo existe na caixa.
        assertTrue(Tool.entries.none { it.name.contains("WIPE") || it.name.contains("UNINSTALL") || it.name.contains("CLEAR_DATA") })
    }

    @Test fun tunnelIsNotRestartedByOneCaseAfterAnother() {
        // Teste de carga na Sala (26/11/2026): 23 reinícios do túnel em 1 h, um por caso de rede. O túnel é um só.
        val c = open(Type.ACCESS_LOST, "tunnel")
        assertEquals(Step.Run(Tool.RESTART_TUNNEL), SofyRepair.next(c, emptyMap(), env()))
        val logo = SofyRepair.Env(t0, false, false, false, lastTunnelRestart = t0 - 5 * min)
        assertTrue(SofyRepair.next(c, emptyMap(), logo) != Step.Run(Tool.RESTART_TUNNEL))
        val depois = SofyRepair.Env(t0, false, false, false, lastTunnelRestart = t0 - SofyRepair.RESTART_TUNNEL_GAP_MS - min)
        assertEquals(Step.Run(Tool.RESTART_TUNNEL), SofyRepair.next(c, emptyMap(), depois))
    }

    @Test fun screenDefectIsCodeSoItIsOnlyReported() {
        val c = open(Type.SCREEN_BROKEN, "screen:previsao", n = 2)
        assertEquals(listOf(Tool.REPORT), drain(c, env(idle = true)))
        // Nem com IA configurada: nenhuma ferramenta conserta código, então não gasta a pergunta.
        assertEquals(listOf(Tool.REPORT), drain(c, env(idle = true, ai = true)))
    }

    @Test fun aiOnlyPicksFromTheBox() {
        assertEquals(listOf(Tool.RESTART_APP, Tool.FREE_MEMORY), SofyRepair.parseAiPlan(listOf("restart_app", "rm -rf /", "ASK_AI", "FREE_MEMORY", "restart_app")))
        val c = SofyRepair.withAiPlan(open(Type.INCOHERENT, "data:clima", n = 2), listOf(Tool.FREE_MEMORY))
        assertEquals(Tool.FREE_MEMORY, SofyRepair.ordered(c, emptyMap()).first())
        assertTrue(SofyRepair.brief(c).startsWith("INCOHERENT em data:clima; 2 vez(es)"))
    }

    @Test fun reportedOnceThenGivenUpUntilItGoesStale() {
        var c = open(Type.SCREEN_BROKEN, "screen:x")
        c = SofyRepair.onRun(c, Tool.REPORT, t0, true)
        assertEquals(State.GIVEN_UP, c.state)
        assertEquals(Step.Done, SofyRepair.next(c, emptyMap(), env()))
        assertEquals(0, SofyRepair.prune(listOf(c), t0 + 4 * SofyRepair.DAY).size)
    }
}

/** O barramento como fonte de sinais e a IA do dono (qualquer provedor, inclusive local). */
class SofyRepairBusAndAiTest {
    private fun ev(src: br.paulo.tvlauncher.observer.SofyBus.Source, kind: String, ok: Boolean = false, detail: String = "") =
        br.paulo.tvlauncher.observer.SofyBus.Event(1L, src, kind, ok, detail)

    @Test fun anyFailureOnTheBusIsASignalEvenIfNobodyForesawIt() {
        assertEquals("bus:player.novo_tipo", SofyRepair.fromBus(ev(br.paulo.tvlauncher.observer.SofyBus.Source.PLAYER, "novo_tipo"))!!.subject)
        assertEquals(Type.CRASH, SofyRepair.fromBus(ev(br.paulo.tvlauncher.observer.SofyBus.Source.CRASH, "launcher_crash"))!!.type)
        assertEquals(null, SofyRepair.fromBus(ev(br.paulo.tvlauncher.observer.SofyBus.Source.PLAYER, "novo_tipo", ok = true)))
        // Aviso à pessoa não é defeito; o próprio ciclo não se realimenta.
        assertEquals(null, SofyRepair.fromBus(ev(br.paulo.tvlauncher.observer.SofyBus.Source.DEVICE, "battery_low")))
        assertEquals(null, SofyRepair.fromBus(ev(br.paulo.tvlauncher.observer.SofyBus.Source.SOFY, "repair_failed")))
        // Medição ("não deu para medir, fico no padrão") não é defeito (Sala, 25/11/2026)
        assertEquals(null, SofyRepair.fromBus(ev(br.paulo.tvlauncher.observer.SofyBus.Source.DEVICE, "audio_route")))
        assertEquals(null, SofyRepair.fromBus(ev(br.paulo.tvlauncher.observer.SofyBus.Source.MEASURE, "picture_white")))
        // e o caso que uma versão antiga abriu com ele sai da fila
        val velho = SofyRepair.Case("ERROR:bus:device.audio_route", Type.ERROR, "bus:device.audio_route", emptySet(), 0, 0, 1)
        assertEquals(emptyList<SofyRepair.Case>(), SofyRepair.prune(listOf(velho), 1))
        // A Sofy contando o que ela mesma fez não vira caso (era um laço: asked_ai -> caso -> asked_ai)
        assertEquals(null, SofyRepair.fromBus(ev(br.paulo.tvlauncher.observer.SofyBus.Source.SOFY, "asked_ai")))
        assertTrue(SofyRepair.fromBus(ev(br.paulo.tvlauncher.observer.SofyBus.Source.SOFY, "self_defect")) != null)
        val laco = SofyRepair.Case("ERROR:bus:sofy.asked_ai", Type.ERROR, "bus:sofy.asked_ai", emptySet(), 0, 0, 1)
        assertEquals(emptyList<SofyRepair.Case>(), SofyRepair.prune(listOf(laco), 1))
        assertEquals(null, SofyRepair.fromBus(ev(br.paulo.tvlauncher.observer.SofyBus.Source.NETWORK, "drop_router")))
    }

    @Test fun ownerPicksAnyProvider() {
        val claude = br.paulo.tvlauncher.observer.SofyAiRules.resolve(br.paulo.tvlauncher.observer.SofyAiRules.Provider.CLAUDE, null, null, "k")!!
        assertEquals("https://api.anthropic.com/v1/messages", br.paulo.tvlauncher.observer.SofyAiRules.url(claude))
        assertEquals("k", br.paulo.tvlauncher.observer.SofyAiRules.headers(claude)["x-api-key"])
        val pplx = br.paulo.tvlauncher.observer.SofyAiRules.resolve(br.paulo.tvlauncher.observer.SofyAiRules.Provider.of("perplexity"), "http://ignorado", "", "k")!!
        assertEquals("https://api.perplexity.ai/orchestrator/v1/chat/completions", br.paulo.tvlauncher.observer.SofyAiRules.url(pplx))
        assertEquals("Bearer k", br.paulo.tvlauncher.observer.SofyAiRules.headers(pplx)["authorization"])
        // IA local na rede da casa: http e sem chave valem; http para fora, não.
        val local = br.paulo.tvlauncher.observer.SofyAiRules.resolve(br.paulo.tvlauncher.observer.SofyAiRules.Provider.CUSTOM, "http://192.168.1.50:11434/v1/", "llama3", "")!!
        assertEquals("http://192.168.1.50:11434/v1/chat/completions", br.paulo.tvlauncher.observer.SofyAiRules.url(local))
        assertFalse(br.paulo.tvlauncher.observer.SofyAiRules.headers(local).containsKey("authorization"))
        assertEquals(null, br.paulo.tvlauncher.observer.SofyAiRules.resolve(br.paulo.tvlauncher.observer.SofyAiRules.Provider.CUSTOM, "http://example.com/v1", "m", ""))
        assertEquals(null, br.paulo.tvlauncher.observer.SofyAiRules.resolve(br.paulo.tvlauncher.observer.SofyAiRules.Provider.CUSTOM, "", "m", ""))
        assertEquals(null, br.paulo.tvlauncher.observer.SofyAiRules.resolve(br.paulo.tvlauncher.observer.SofyAiRules.Provider.OPENAI, null, null, ""))   // provedor de fora sem chave
        assertTrue(br.paulo.tvlauncher.observer.SofyAiRules.homeHost("ollama.local")); assertTrue(br.paulo.tvlauncher.observer.SofyAiRules.homeHost("10.0.0.2")); assertFalse(br.paulo.tvlauncher.observer.SofyAiRules.homeHost("8.8.8.8"))
    }

    @Test fun answerOnlyCountsAsToolsFromTheBox() {
        val (tools, cause) = br.paulo.tvlauncher.observer.SofyAiRules.plan("Sure! {\"tools\":[\"RESTART_APP\",\"format_disk\",\"free_memory\"],\"cause\":\"cache\"} bye")
        assertEquals(listOf(Tool.RESTART_APP, Tool.FREE_MEMORY), tools)
        assertEquals("cache", cause)
        assertTrue(br.paulo.tvlauncher.observer.SofyAiRules.plan("não sei").first.isEmpty())
        assertTrue(br.paulo.tvlauncher.observer.SofyAiRules.plan(null).first.isEmpty())
        val c = SofyRepair.Case("ERROR:x", Type.ERROR, "x", emptySet(), 0, 0, 2)
        assertTrue(br.paulo.tvlauncher.observer.SofyAiRules.prompt(c).contains("RESTART_LAUNCHER") && !br.paulo.tvlauncher.observer.SofyAiRules.prompt(c).contains("ASK_AI"))
        val claude = org.json.JSONObject("{\"content\":[{\"type\":\"text\",\"text\":\"oi\"}]}")
        val openai = org.json.JSONObject("{\"choices\":[{\"message\":{\"content\":\"oi\"}}]}")
        assertEquals("oi", br.paulo.tvlauncher.observer.SofyAiRules.text(br.paulo.tvlauncher.observer.SofyAiRules.Format.CLAUDE, claude)); assertEquals("oi", br.paulo.tvlauncher.observer.SofyAiRules.text(br.paulo.tvlauncher.observer.SofyAiRules.Format.OPENAI, openai))
    }
}

/** Diário de ajustes: o que conta como mudança de alguém e qual delas a Sofy desfaz. */
/** Gasto da IA na chave do dono (Paulo, 25/11/2026: o sistema não pode "devorar" o crédito de ninguém). */
class SofyAiSpendTest {
    private val R = br.paulo.tvlauncher.observer.SofyAiRules

    @Test fun contaOsTokensQueARespostaInforma() {
        // Claude informa input/output; OpenAI e parecidos, prompt/completion. Sem "usage", não inventa número.
        assertEquals(1234L, R.usage(org.json.JSONObject("""{"usage":{"input_tokens":1000,"output_tokens":234}}""")))
        assertEquals(80L, R.usage(org.json.JSONObject("""{"usage":{"prompt_tokens":50,"completion_tokens":30}}""")))
        assertEquals(0L, R.usage(org.json.JSONObject("{}")))
    }

    @Test fun tetoParaEZeroESemTeto() {
        assertTrue(R.withinBudget(299_999, 300_000))
        assertFalse(R.withinBudget(300_000, 300_000))
        assertTrue(R.withinBudget(9_000_000, 0))   // o dono tirou o teto
    }

    @Test fun respostaSemTextoDizPorque() {
        // Sala 25/11: o pensamento gastou o limite e não veio bloco de texto; o relatório tem que dizer isso.
        val r = org.json.JSONObject("""{"stop_reason":"max_tokens","content":[{"type":"thinking","thinking":""}]}""")
        assertEquals(null, R.text(br.paulo.tvlauncher.observer.SofyAiRules.Format.CLAUDE, r))
        assertEquals("parada=max_tokens blocos=thinking", R.shape(r))
    }
}

class SettingsJournalTest {
    private val J = br.paulo.tvlauncher.observer.SettingsJournal
    private fun ch(key: String, at: Long, old: String? = "b:false", new: String? = "b:true") =
        br.paulo.tvlauncher.observer.SettingsJournal.Change("launcher", key, old, new, at)

    @org.junit.Test fun relogioEContadorNaoSaoAjuste() {
        org.junit.Assert.assertTrue(J.record(emptyList(), ch("ultima_vez", 1)).isEmpty())
        org.junit.Assert.assertTrue(J.record(emptyList(), ch("tema", 1, "s:a", "s:a")).isEmpty())
        org.junit.Assert.assertEquals(1, J.record(emptyList(), ch("tema", 1, "s:a", "s:b")).size)
        org.junit.Assert.assertNull(J.encode(5L))
        org.junit.Assert.assertEquals("i:5", J.encode(5))
    }

    @org.junit.Test fun desfazSoOQueMudouLogoAntesDoDefeito() {
        val min = 60_000L
        val l = listOf(ch("velho", 0), ch("recente", 50 * min), ch("depois", 70 * min))
        // defeito aos 60 min: "velho" está fora da janela de 30 min, "depois" veio depois do defeito
        org.junit.Assert.assertEquals("recente", J.candidate(l, 60 * min)?.key)
        org.junit.Assert.assertNull(J.candidate(l, 40 * min))
        org.junit.Assert.assertNull(J.candidate(l.map { it.copy(undone = true) }, 60 * min))
    }

    @org.junit.Test fun chaveQueMudaTodaHoraNaoECausa() {
        val min = 60_000L
        val l = listOf(ch("ajuste", 40 * min), ch("pisca", 50 * min), ch("pisca", 51 * min), ch("pisca", 52 * min))
        org.junit.Assert.assertEquals("ajuste", J.candidate(l, 60 * min)?.key)
    }

    @org.junit.Test fun idaEVoltaDoJson() {
        val l = listOf(ch("a", 1, null, "s:x"), ch("b", 2).copy(undone = true))
        org.junit.Assert.assertEquals(l, J.fromJson(J.toJson(l)))
    }
}

/** Régua das telas conferidas na própria TV (parte C). */
class SofyScreenCheckTest {
    private val S = br.paulo.tvlauncher.observer.SofyScreenCheck
    private fun cx(t: String, x1: Int, y1: Int, x2: Int, y2: Int) = br.paulo.tvlauncher.observer.SofyScreenCheck.Caixa(t, x1, y1, x2, y2)
    private fun tela(vararg c: br.paulo.tvlauncher.observer.SofyScreenCheck.Caixa, foco: Boolean = true) =
        br.paulo.tvlauncher.observer.SofyScreenCheck.Tela("previsao", 1920, 1080, c.toList(), foco)

    @org.junit.Test fun telaBoaNaoAcusaNada() {
        org.junit.Assert.assertEquals(emptyList<Any>(), S.conferir(tela(cx("Hoje", 40, 40, 300, 100), cx("22°", 40, 120, 300, 180))))
    }

    @org.junit.Test fun textoForaDaTelaECortado() {
        val a = S.conferir(tela(cx("Sexta-feira", 1800, 40, 2100, 100)))
        org.junit.Assert.assertEquals(listOf(br.paulo.tvlauncher.observer.SofyScreenCheck.Defeito.CORTADO), a.map { it.defeito })
        // 2 px na borda é folga, não defeito
        org.junit.Assert.assertTrue(S.conferir(tela(cx("Sexta", 1700, 40, 1921, 100))).isEmpty())
    }

    @org.junit.Test fun textoComCaixaZeroEInvisivel() {
        org.junit.Assert.assertEquals(listOf(br.paulo.tvlauncher.observer.SofyScreenCheck.Defeito.INVISIVEL),
            S.conferir(tela(cx("22°", 40, 40, 40, 40))).map { it.defeito })
    }

    @org.junit.Test fun textoPorCimaDeOutro() {
        val juntos = S.conferir(tela(cx("Hoje", 40, 40, 300, 100), cx("22°", 50, 50, 310, 110)))
        org.junit.Assert.assertEquals(listOf(br.paulo.tvlauncher.observer.SofyScreenCheck.Defeito.SOBREPOSTO), juntos.map { it.defeito })
        // encostados (1 px) não contam
        org.junit.Assert.assertTrue(S.conferir(tela(cx("Hoje", 40, 40, 300, 100), cx("22°", 40, 99, 300, 160))).isEmpty())
    }

    @org.junit.Test fun telaSemFocoSoAcusaSeTemTexto() {
        org.junit.Assert.assertEquals(listOf(br.paulo.tvlauncher.observer.SofyScreenCheck.Defeito.SEM_FOCO),
            S.conferir(tela(cx("Hoje", 40, 40, 300, 100), foco = false)).map { it.defeito })
        org.junit.Assert.assertTrue(S.conferir(tela(foco = false)).isEmpty())
    }

    @org.junit.Test fun resumoNaoLevaOTextoInteiro() {
        val r = S.resumo("previsao", S.conferir(tela(cx("Sexta-feira em São Paulo, 22 graus", 1800, 40, 2100, 100))))
        org.junit.Assert.assertTrue(r.startsWith("previsao: CORTADO "))
        org.junit.Assert.assertFalse(r.contains("graus"))
    }
}


/**
 * Sala, 25/11/2026: shell fora 30 min sem tentativa porque outros casos gastaram um teto fixo da hora. Paulo: "ela tem
 * que saber se calibrar sozinha". O ritmo agora vem da gravidade, do custo, do placar e das falhas seguidas.
 */
class SofyPaceTest {
    private val t0 = 1_800_000_000_000L
    private val min = 60_000L
    private fun case(type: Type, subject: String, count: Int = 2) =
        SofyRepair.Case(SofyRepair.key(type, subject), type, subject, emptySet<SofyRepair.Need>(), t0, t0, count)
    private fun env(now: Long, fails: Int = 0, last: Long = 0, hour: Int = 0) =
        SofyRepair.Env(now = now, idle = true, night = false, aiAvailable = false, toolsLastHour = hour, failsLastHour = fails, lastRunAt = last)

    @org.junit.Test fun acessoPerdidoNaoEsperaPelosOutros() {
        // Muita coisa falhando agora: o caso menos grave respira; o acesso perdido não.
        val e = env(t0, fails = 5, last = t0 - min, hour = 8)
        assertTrue(SofyRepair.next(case(Type.ACCESS_LOST, "shell"), emptyMap(), e) is Step.Run)
        val w = SofyRepair.next(case(Type.ERROR, "bus:x.y"), emptyMap(), e)
        assertTrue(w is Step.Wait && w.why.startsWith("calma") && w.until > t0)
        // Sem pressão, nenhum teto fixo segura o caso menos grave.
        assertTrue(SofyRepair.next(case(Type.ERROR, "bus:x.y"), emptyMap(), env(t0, hour = 8)) is Step.Run)
        // Só a trava contra laço vale para todos.
        assertTrue(SofyRepair.next(case(Type.ACCESS_LOST, "shell"), emptyMap(), env(t0, hour = SofyRepair.LOOP_GUARD_PER_HOUR)) is Step.Wait)
    }

    @org.junit.Test fun recuoDobraACadaFalhaEEncolheComAGravidade() {
        val shell = case(Type.ACCESS_LOST, "shell"); val lento = case(Type.SLOW, "home")
        val g1 = SofyRepair.retryGapMs(shell, Tool.RECONNECT_SHELL, 1, 0.5, 0)
        assertEquals(2 * g1, SofyRepair.retryGapMs(shell, Tool.RECONNECT_SHELL, 2, 0.5, 0))
        assertTrue(SofyRepair.retryGapMs(lento, Tool.FREE_MEMORY, 1, 0.5, 0) > g1)               // menos grave espera mais
        assertTrue(SofyRepair.retryGapMs(shell, Tool.REPAIR_SHELL, 1, 0.5, 0) > g1)              // mais cara espera mais
        assertTrue(SofyRepair.retryGapMs(shell, Tool.RECONNECT_SHELL, 1, 0.9, 0) < g1)           // o que costuma funcionar volta antes
        assertTrue(SofyRepair.retryGapMs(lento, Tool.FREE_MEMORY, 1, 0.5, 8) > SofyRepair.retryGapMs(lento, Tool.FREE_MEMORY, 1, 0.5, 0))
        // Nunca para de vez: o teto da espera é um dia dividido pela gravidade.
        assertEquals(SofyRepair.DAY / 4, SofyRepair.retryGapMs(shell, Tool.REPAIR_SHELL, 30, 0.0, 0))
    }

    @org.junit.Test fun acessoPerdidoContinuaTentandoDepoisDeRegistrar() {
        var c = case(Type.ACCESS_LOST, "shell", count = 7)
        var now = t0
        val rodou = ArrayList<Tool>()
        repeat(40) {
            when (val s = SofyRepair.next(c, emptyMap(), env(now))) {
                is Step.Run -> { rodou += s.tool; c = SofyRepair.onRun(c, s.tool, now, true); if (s.tool != Tool.REPORT) c = SofyRepair.onVerified(c, true, now + 2 * min).first }
                is Step.Wait -> now = maxOf(now + min, s.until)
                else -> return@repeat
            }
        }
        assertEquals(State.OPEN, c.state)                                    // registrar não é desistir
        assertEquals(1, rodou.count { it == Tool.REPORT })
        assertTrue(rodou.count { it == Tool.REPAIR_SHELL } >= 3)            // continua tentando, cada vez mais espaçado
        val horas = c.tried.filter { it.tool == Tool.REPAIR_SHELL }.map { it.at }.zipWithNext { a, b -> b - a }
        assertTrue(horas.zipWithNext().all { (a, b) -> b >= a })             // o espaço só cresce
    }

    @org.junit.Test fun lacoViraDefeitoEElaTrocaDeEstrategia() {
        var c = case(Type.ACCESS_LOST, "shell")
        repeat(3) { i -> c = SofyRepair.onVerified(SofyRepair.onRun(c, Tool.RECONNECT_SHELL, t0 + i * 10 * min, true), true, t0 + i * 10 * min + 2 * min).first }
        val now = t0 + 40 * min
        assertEquals(Tool.RECONNECT_SHELL, SofyRepair.looping(c, now))
        // Pergunta à IA já feita antes do laço: depois do laço ela pode perguntar de novo.
        c = c.copy(tried = listOf(SofyRepair.Attempt(Tool.ASK_AI, t0 - min, Result.NOT_FIXED)) + c.tried)
        c = SofyRepair.onLoop(c, Tool.RECONNECT_SHELL, now)
        assertEquals(null, SofyRepair.looping(c, now))
        assertTrue(c.context.last().startsWith("laço: reconnect_shell"))
        val e = SofyRepair.Env(now = now, idle = true, night = false, aiAvailable = true)
        // A ferramenta em laço sai; segue com as outras e, quando acabarem, a IA é consultada de novo.
        val passos = ArrayList<Tool>(); var x = c
        while (true) { val s = SofyRepair.next(x, emptyMap(), e) as? Step.Run ?: break; passos += s.tool
            x = SofyRepair.onRun(x, s.tool, now, true); if (s.tool != Tool.REPORT && s.tool != Tool.ASK_AI) x = SofyRepair.onVerified(x, true, now).first }
        assertFalse(Tool.RECONNECT_SHELL in passos)
        assertTrue(Tool.ASK_AI in passos)
        // Consertou: as proibições caem; e a proibição vence sozinha depois de um dia.
        val ok = SofyRepair.onVerified(SofyRepair.onRun(c, Tool.REPAIR_SHELL, now, true), false, now + 2 * min).first
        assertTrue(ok.banned.isEmpty())
        assertTrue(SofyRepair.banned(c, now + SofyRepair.LOOP_BAN_MS / 4).isEmpty())   // acesso perdido: 6 h, não um dia
        assertFalse(SofyRepair.banned(c, now + SofyRepair.LOOP_BAN_MS / 4 - 1).isEmpty())
        assertTrue(SofyRepair.unban(listOf(c)).single().banned.isEmpty())               // versão nova: estratégia nova
        // Tudo fora por laço: espera com motivo e hora de volta, nunca some em silêncio.
        val tudo = c.copy(banned = mapOf(Tool.RELIGHT_WIRELESS_DEBUG to now, Tool.RECONNECT_SHELL to now, Tool.REPAIR_SHELL to now),
            tried = c.tried + SofyRepair.Attempt(Tool.ASK_AI, now, Result.FAILED) + SofyRepair.Attempt(Tool.REPORT, now, Result.NOT_FIXED))
        val w = SofyRepair.next(tudo, emptyMap(), e)
        assertTrue(w is Step.Wait && w.why == "fora por laço" && w.until == now + SofyRepair.LOOP_BAN_MS / 4)
        // A rede de segurança fixa continua, e é reconhecida como defeito da própria Sofy.
        assertTrue(SofyRepair.guardTripped(SofyRepair.Env(now, true, false, false, toolsLastHour = SofyRepair.LOOP_GUARD_PER_HOUR)))
    }

    @org.junit.Test fun casoAntigoDesistidoReabreSeTemFerramenta() {
        val velho = case(Type.ACCESS_LOST, "shell").copy(state = State.GIVEN_UP)
        val cs = SofyRepair.onSignal(listOf(velho), Signal(Type.ACCESS_LOST, "shell", t0 + min))
        assertEquals(State.OPEN, cs.single().state)
        val tela = case(Type.SCREEN_BROKEN, "screen:x").copy(state = State.GIVEN_UP)
        assertEquals(State.GIVEN_UP, SofyRepair.onSignal(listOf(tela), Signal(Type.SCREEN_BROKEN, "screen:x", t0 + min)).single().state)
    }
}
