package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyBus
import br.paulo.tvlauncher.observer.SofyJudge
import br.paulo.tvlauncher.observer.SofyJudge.Situation
import br.paulo.tvlauncher.observer.SofyJudge.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Juiz de conflitos (ideia 4 da Sofy). */
class SofyJudgeTest {
    private val T = 1_800_000_000_000L
    private val MIN = 60_000L

    @Test fun quietHouseRunsEverything() {
        assertTrue(SofyJudge.decide(Task.UPDATE, Situation(T)).go)
        assertTrue(SofyJudge.decide(Task.BACKUP, Situation(T, nextAlarmAt = T + 3 * 60 * MIN)).go)
    }

    @Test fun updateWaitsForTheAlarmAndTheMorning() {
        val v = SofyJudge.decide(Task.UPDATE, Situation(T, nextAlarmAt = T + 40 * MIN))
        assertFalse(v.go)
        assertEquals(55 * MIN, v.waitMs)   // 40 até tocar + 15 depois
        assertTrue(v.why.contains("40 min"))
        assertTrue(SofyJudge.decide(Task.BACKUP, Situation(T, nextAlarmAt = T + 40 * MIN)).go)   // o backup só guarda 20 min
        assertFalse(SofyJudge.decide(Task.BACKUP, Situation(T, nextAlarmAt = T + 10 * MIN)).go)
        assertTrue(SofyJudge.decide(Task.UPDATE, Situation(T, nextAlarmAt = T - MIN)).go)       // alarme no passado não segura
    }

    @Test fun ringingAlarmHoldsAll() {
        for (t in Task.entries) assertFalse(SofyJudge.decide(t, Situation(T, alarmRinging = true)).go)
    }

    @Test fun cloudGameHoldsOnlyTheUpdateAndMediaHoldsBoth() {
        assertFalse(SofyJudge.decide(Task.UPDATE, Situation(T, cloudGameFront = true)).go)
        assertTrue(SofyJudge.decide(Task.BACKUP, Situation(T, cloudGameFront = true)).go)
        assertFalse(SofyJudge.decide(Task.BACKUP, Situation(T, mediaActive = true)).go)
    }

    @Test fun ringingComesFromTheBus() {
        fun ev(at: Long, kind: String) = SofyBus.Event(at, SofyBus.Source.ALARM, kind)
        assertTrue(SofyJudge.alarmRinging(listOf(ev(T - 2 * MIN, "rang")), T))
        assertFalse(SofyJudge.alarmRinging(listOf(ev(T - 2 * MIN, "rang"), ev(T - MIN, "stopped")), T))
        assertFalse(SofyJudge.alarmRinging(listOf(ev(T - 40 * MIN, "rang")), T))   // tocou há muito: não conta
        assertFalse(SofyJudge.alarmRinging(emptyList(), T))
    }
}
