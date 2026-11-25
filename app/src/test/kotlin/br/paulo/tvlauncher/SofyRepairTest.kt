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
        // Teto da hora.
        assertTrue(SofyRepair.next(lenta, emptyMap(), env(idle = true, hour = 6)) is Step.Wait)
        // Nada destrutivo existe na caixa.
        assertTrue(Tool.entries.none { it.name.contains("WIPE") || it.name.contains("UNINSTALL") || it.name.contains("CLEAR_DATA") })
    }

    @Test fun screenDefectIsCodeSoItIsOnlyReported() {
        val c = open(Type.SCREEN_BROKEN, "screen:previsao", n = 2)
        assertEquals(listOf(Tool.REPORT), drain(c, env(idle = true)))
        assertEquals(listOf(Tool.ASK_AI, Tool.REPORT), drain(c, env(idle = true, ai = true)))
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
        assertEquals(null, SofyRepair.fromBus(ev(br.paulo.tvlauncher.observer.SofyBus.Source.NETWORK, "drop_router")))
    }

    @Test fun ownerPicksAnyProvider() {
        val claude = br.paulo.tvlauncher.observer.SofyAiRules.resolve(br.paulo.tvlauncher.observer.SofyAiRules.Provider.CLAUDE, null, null, "k")!!
        assertEquals("https://api.anthropic.com/v1/messages", br.paulo.tvlauncher.observer.SofyAiRules.url(claude))
        assertEquals("k", br.paulo.tvlauncher.observer.SofyAiRules.headers(claude)["x-api-key"])
        val pplx = br.paulo.tvlauncher.observer.SofyAiRules.resolve(br.paulo.tvlauncher.observer.SofyAiRules.Provider.of("perplexity"), "http://ignorado", "", "k")!!
        assertEquals("https://api.perplexity.ai/chat/completions", br.paulo.tvlauncher.observer.SofyAiRules.url(pplx))
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
