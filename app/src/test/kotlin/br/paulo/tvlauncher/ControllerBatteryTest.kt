package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.ControllerBattery
import br.paulo.tvlauncher.observer.ControllerBattery.Reading
import br.paulo.tvlauncher.observer.ControllerBattery.Warned
import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerBatteryTest {
    private val T = 1_800_000_000_000L
    private val DAY = 86_400_000L

    @Test fun warnsOnlyWhenLowAndKnown() {
        val r = listOf(Reading("a", 0.12f), Reading("b", 0.5f), Reading("c", Float.NaN), Reading("d", 0.15f))
        assertEquals(listOf("a", "d"), ControllerBattery.toWarn(T, r, emptyMap()).map { it.id })
    }

    @Test fun onceAWeekPerDevice() {
        val r = listOf(Reading("a", 0.10f))
        assertEquals(emptyList<Reading>(), ControllerBattery.toWarn(T, r, mapOf("a" to Warned(T - 2 * DAY, 0.14f))))
        assertEquals(1, ControllerBattery.toWarn(T, r, mapOf("a" to Warned(T - 8 * DAY, 0.14f))).size)
    }

    @Test fun newBatteryResetsTheWarning() {
        val warned = mapOf("a" to Warned(T - DAY, 0.12f), "b" to Warned(T - DAY, 0.10f))
        val left = ControllerBattery.refilled(listOf(Reading("a", 0.95f), Reading("b", 0.11f)), warned)
        assertEquals(setOf("b"), left.keys)
    }

    @Test fun percentIsRounded() {
        assertEquals(12, ControllerBattery.percent(0.124f)); assertEquals(100, ControllerBattery.percent(1.2f))
    }
}
