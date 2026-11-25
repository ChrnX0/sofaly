package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.AppProfile
import br.paulo.tvlauncher.observer.AppProfile.Advice
import br.paulo.tvlauncher.observer.AppProfile.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProfileTest {
    private val T = 1_800_000_000_000L
    private val MIN = 60_000L
    private val H = 3_600_000L
    private val DAY = 86_400_000L
    private val hour: (Long) -> Int = { ((it / H) % 24).toInt() }
    private val home = setOf("tv.sofaly.launcher")

    private fun learn(ev: List<Event>, s: AppProfile.State = AppProfile.State(emptyMap()), now: Long = T + 2 * H) =
        AppProfile.learn(s, ev, hour, now, home)

    @Test fun countsOpensByHourAndTimeInFront() {
        val s = learn(listOf(Event("yt", T, true), Event("yt", T + 30 * MIN, false), Event("tv.sofaly.launcher", T + 30 * MIN, true),
            Event("net", T + H, true), Event("tv.sofaly.launcher", T + H + 10 * MIN, true)), now = T + H + 10 * MIN)
        assertEquals(1.0, s.cards.getValue("yt").opens[hour(T)], 1e-9)
        assertEquals(30 * MIN.toDouble(), s.cards.getValue("yt").frontMs, 1.0)
        assertEquals(10 * MIN.toDouble(), s.cards.getValue("net").frontMs, 1.0)   // saiu quando a HOME veio para a frente
        assertTrue("tv.sofaly.launcher" !in s.cards)
        assertNull(s.front)
    }

    @Test fun quickReturnIsNotANewOpen() {
        val s = learn(listOf(Event("yt", T, true), Event("settings", T + MIN, true), Event("yt", T + MIN + 20_000, true)))
        assertEquals(1.0, s.cards.getValue("yt").totalOpens, 1e-9)
    }

    @Test fun keepsCountingTheAppStillInFrontAcrossReads() {
        val a = learn(listOf(Event("yt", T, true)), now = T + 10 * MIN)
        assertEquals("yt", a.front)
        val b = AppProfile.learn(a, listOf(Event("yt", T + 40 * MIN, false)), hour, T + 40 * MIN, home)
        assertEquals(40 * MIN.toDouble(), b.cards.getValue("yt").frontMs, 1.0)
    }

    @Test fun oldHabitFadesWithHalfLife() {
        val s = learn(listOf(Event("yt", T, true)), now = T)
        val later = AppProfile.decay(s.cards.getValue("yt"), T + AppProfile.HALF_LIFE_MS)
        assertEquals(0.5, later.totalOpens, 1e-9)
    }

    @Test fun advisesPrewarmAtTheFirmHourOnly() {
        val ev = (0 until 5).flatMap { d -> listOf(Event("yt", T + d * DAY + 20 * H, true), Event("yt", T + d * DAY + 21 * H, false)) } +
            listOf(Event("net", T + 9 * H, true), Event("net", T + 10 * H, false))
        val s = learn(ev, now = T + 5 * DAY)
        val tips = AppProfile.advise(s.cards.values, T + 5 * DAY, emptySet()) { true }
        assertEquals(listOf("yt" to hour(T + 20 * H)), tips.filter { it.advice == Advice.PREWARM }.map { it.pkg to it.hour })
    }

    @Test fun advisesRecompileWhenOpeningIsSlow() {
        var c = AppProfile.Card("slow", T)
        for (ms in listOf(3000, 2800, 3100)) c = AppProfile.noteStart(c, ms)
        val fast = AppProfile.noteStart(AppProfile.noteStart(AppProfile.noteStart(AppProfile.Card("fast", T), 900), 800), 4000)
        val tips = AppProfile.advise(listOf(c, fast), T, emptySet()) { true }
        assertEquals(listOf("slow"), tips.filter { it.advice == Advice.RECOMPILE }.map { it.pkg })
    }

    @Test fun advisesOnDemandOnlyForForgottenSafeApps() {
        val old = AppProfile.Card("old", T, lastOpen = T + DAY)
        val unsafe = AppProfile.Card("unsafe", T, lastOpen = T + DAY)
        val already = AppProfile.Card("already", T, lastOpen = T + DAY)
        val now = T + 30 * DAY
        val tips = AppProfile.advise(listOf(old, unsafe, already), now, setOf("already")) { it != "unsafe" }
        assertEquals(listOf("old"), tips.filter { it.advice == Advice.ON_DEMAND }.map { it.pkg })
    }

    @Test fun readsLaunchTimeFromTheEventLog() {
        assertEquals("com.netflix.ninja" to 1850,
            AppProfile.parseLaunchTime("1764000000.123  1000  1234  1250 I wm_activity_launch_time: [0,12345678,com.netflix.ninja/.MainActivity,1850]"))
        assertEquals("com.app" to 700, AppProfile.parseLaunchTime("I am_activity_launch_time: [0,99,com.app/com.app.Main,700,700]"))
        assertNull(AppProfile.parseLaunchTime("I wm_task_moved: [1,2,3]"))
    }

    @Test fun roundTripsJsonPerProfile() {
        val s = learn(listOf(Event("yt", T, true), Event("yt", T + H, false)), now = T + H)
        val c = AppProfile.noteStart(s.cards.getValue("yt"), 1200)
        val back = AppProfile.fromJson(AppProfile.toJson(mapOf("ana" to s.copy(cards = mapOf("yt" to c)), "" to AppProfile.State(emptyMap()))))
        assertEquals(setOf("ana", ""), back.keys)
        val r = back.getValue("ana").cards.getValue("yt")
        assertEquals(c.totalOpens, r.totalOpens, 0.01)
        assertEquals(listOf(1200), r.startsMs)
        assertEquals(c.lastOpen, r.lastOpen)
    }

    @Test fun keepsOnlyTheMostUsedApps() {
        val ev = (0 until AppProfile.MAX_APPS + 5).map { Event("app$it", T + it * 2 * MIN, true) } + listOf(Event("app0", T + 300 * MIN, true))
        val s = learn(ev, now = T + 301 * MIN)
        assertEquals(AppProfile.MAX_APPS, s.cards.size)
        assertTrue("app0" in s.cards)
    }

    // ---- a ficha age (ideias 8-11, 14)
    private fun card(vararg starts: Int) = AppProfile.Card("yt", T).let { c -> starts.fold(c) { a, ms -> AppProfile.noteStart(a, ms) } }

    @Test fun newVersionKeepsTheOldMedianAndRestartsMeasuring() {
        val c = AppProfile.noteVersion(card(900, 1000, 1100), 10)
        assertEquals(10L, c.ver)
        assertEquals(0, c.startsBefore)               // primeira versão vista: só anota
        val d = AppProfile.noteVersion(c, 11)
        assertEquals(1000, d.startsBefore)
        assertTrue(d.startsMs.isEmpty())
        assertEquals(d, AppProfile.noteVersion(d, 11)) // mesma versão: nada muda
    }

    @Test fun slowerAfterUpdateNeedsBothFactorAndMinimum() {
        val base = AppProfile.noteVersion(AppProfile.noteVersion(card(1000, 1000, 1000), 1), 2)
        val pior = listOf(1600, 1700, 1800).fold(base) { a, ms -> AppProfile.noteStart(a, ms) }
        assertTrue(AppProfile.advise(listOf(pior), T, emptySet()) { false }.any { it.advice == Advice.SLOWER_AFTER_UPDATE })
        val pouco = listOf(1400, 1450, 1450).fold(base) { a, ms -> AppProfile.noteStart(a, ms) }   // 1,45x: não
        assertTrue(AppProfile.advise(listOf(pouco), T, emptySet()) { false }.none { it.advice == Advice.SLOWER_AFTER_UPDATE })
    }

    @Test fun heavyNeedsSamplesAndLowMemory() {
        var c = AppProfile.Card("jogo", T)
        c = AppProfile.noteMemory(c, 500); c = AppProfile.noteMemory(c, 500)
        assertTrue(!AppProfile.heavy(c, 1000.0))       // só 2 amostras
        c = AppProfile.noteMemory(c, 500)
        assertTrue(AppProfile.heavy(c, 1000.0))
        assertTrue(!AppProfile.heavy(c, null))
        assertTrue(!AppProfile.heavy(c, 600.0))        // 500 > 0,75 x 600
    }

    @Test fun recompiledAppIsNotRecompiledForAMonthAndShowsTheGain() {
        val lento = card(3000, 3200, 3100)
        assertTrue(AppProfile.advise(listOf(lento), T, emptySet()) { false }.any { it.advice == Advice.RECOMPILE })
        val feito = AppProfile.noteCompiled(lento, T)
        assertEquals(3100, feito.startsBefore)
        val depois = listOf(2600, 2700, 2650).fold(feito) { a, ms -> AppProfile.noteStart(a, ms) }
        assertEquals(-450, AppProfile.startChange(depois))
        assertTrue(AppProfile.advise(listOf(depois), T + DAY, emptySet()) { false }.none { it.advice == Advice.RECOMPILE })
        assertTrue(AppProfile.advise(listOf(depois), T + 31 * DAY, emptySet()) { false }.any { it.advice == Advice.RECOMPILE })
        assertTrue(AppProfile.advise(listOf(depois), T + DAY, emptySet()) { false }.none { it.advice == Advice.SLOWER_AFTER_UPDATE })
    }

    @Test fun prewarmOnlyInTheLeadWindowOncePerDay() {
        val c = AppProfile.Card("yt", T)
        assertTrue(AppProfile.prewarmNow(c, 20, 19 * 60 + 45, 100))
        assertTrue(!AppProfile.prewarmNow(c, 20, 19 * 60 + 30, 100))   // 30 min antes: cedo
        assertTrue(!AppProfile.prewarmNow(c, 20, 20 * 60, 100))        // já é a hora
        assertTrue(AppProfile.prewarmNow(c, 0, 23 * 60 + 50, 100))     // vira a meia-noite
        assertTrue(!AppProfile.prewarmNow(c.copy(prewarmDay = 100), 20, 19 * 60 + 45, 100))
    }

    @Test fun newFieldsSurviveJson() {
        val c = AppProfile.noteCompiled(AppProfile.noteMemory(AppProfile.noteVersion(card(900, 1000, 1100), 7), 640), T)
            .copy(prewarmDay = 42)
        val r = AppProfile.fromJson(AppProfile.toJson(mapOf("" to AppProfile.State(mapOf("yt" to c))))).getValue("").cards.getValue("yt")
        assertEquals(7L, r.ver); assertEquals(1000, r.startsBefore); assertEquals(640.0, r.memFrontMb, 0.5)
        assertEquals(1, r.memSamples); assertEquals(T, r.compiledAt); assertEquals(42L, r.prewarmDay)
    }

    // ---- travadas e internet parado (ideias 12 e 13)
    @Test fun dropboxLinesBecomeCrashesPerApp() {
        val out = """
            2026-11-24 10:00:00 data_app_crash (text, 1234 bytes)
            Process: com.amazon.avod
            2026-11-24 11:00:00 data_app_anr (text, 99 bytes)
            Process: com.netflix.ninja:remote
            2026-11-24 12:00:00 data_app_crash (text, 10 bytes)
            2026-11-24 12:00:01 SYSTEM_TOMBSTONE (compressed text, 1 bytes)
            Process: ignorado
        """.trimIndent()
        val r = AppProfile.parseDropbox(out.lineSequence()) { d -> if (d.startsWith("2026-11-24 1")) d.substring(11, 13).toLong() else null }
        assertEquals(listOf(Triple("com.amazon.avod", 10L, false), Triple("com.netflix.ninja", 11L, true)), r)
    }

    @Test fun threeCrashesAWeekBecomeATipAndKnowTheUpdate() {
        var c = AppProfile.noteVersion(AppProfile.noteVersion(AppProfile.Card("prime", T), 1, T), 2, T + DAY)
        assertEquals(T + DAY, c.verAt)
        for (k in 1..3) c = AppProfile.noteCrash(c, T + DAY + k * H)
        c = AppProfile.noteCrash(c, T + DAY + H)   // a mesma travada lida de novo não conta duas vezes
        assertEquals(3, AppProfile.crashesWeek(c, T + 2 * DAY))
        assertTrue(AppProfile.crashesSinceUpdate(c, T + 2 * DAY))
        val tip = AppProfile.advise(listOf(c), T + 2 * DAY, emptySet()) { false }.first { it.advice == Advice.CRASHY }
        assertTrue(tip.why.contains("desde a versão nova"))
        assertEquals(0, AppProfile.crashesWeek(c, T + 10 * DAY))
        assertTrue(!AppProfile.crashesSinceUpdate(AppProfile.noteCrash(c, T), T + 2 * DAY))   // uma antes da versão nova
    }

    @Test fun backgroundDataNeedsDaysAndVolume() {
        var c = AppProfile.Card("tv", T)
        c = AppProfile.noteBackground(c, 500.0); c = AppProfile.noteBackground(c, 500.0)
        assertTrue(AppProfile.advise(listOf(c), T, emptySet()) { false }.none { it.advice == Advice.BACKGROUND_DATA })
        c = AppProfile.noteBackground(c, 500.0)
        assertTrue(AppProfile.advise(listOf(c), T, emptySet()) { false }.any { it.advice == Advice.BACKGROUND_DATA })
        val back = AppProfile.fromJson(AppProfile.toJson(mapOf("" to AppProfile.State(mapOf("tv" to AppProfile.noteCrash(c, T)))))).getValue("").cards.getValue("tv")
        assertEquals(3, back.bgDays); assertEquals(500.0, back.bgMbDay, 0.1); assertEquals(listOf(T), back.crashes)
    }
}
