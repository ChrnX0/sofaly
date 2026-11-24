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
}
