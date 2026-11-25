package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyNight
import br.paulo.tvlauncher.observer.SofyNight.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SofyNightTest {
    @Test fun backupFollowsTheQuietHourUnlessChosen() {
        assertEquals(2 * 60 + 45, SofyNight.backupMinute(2, null))
        assertEquals(3 * 60 + 30, SofyNight.backupMinute(2, 3 * 60 + 30))
        assertEquals(45, SofyNight.backupMinute(0, null))
    }

    @Test fun updateWaitsDuringMaintenanceAndBackup() {
        assertTrue(SofyNight.updateWaits(2 * 60, 2))
        assertTrue(SofyNight.updateWaits(3 * 60 + 59, 2))
        assertFalse(SofyNight.updateWaits(4 * 60, 2))
        assertFalse(SofyNight.updateWaits(60 + 59, 2))   // antes da hora quieta: livre
        assertTrue(SofyNight.updateWaits(0, 23))         // vira a meia-noite
        assertFalse(SofyNight.updateWaits(14 * 60, 2))
    }

    @Test fun planIsInOrder() {
        val p = SofyNight.plan(2, null)
        assertEquals(listOf(Task.MAINTENANCE, Task.BACKUP, Task.UPDATE), p.map { it.task })
        assertEquals(listOf(120, 165, 240), p.map { it.minuteOfDay })
        assertEquals(false, SofyNight.plan(2, 210)[1].learned)
    }
}
