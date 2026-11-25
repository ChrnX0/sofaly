package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyBus
import br.paulo.tvlauncher.observer.SofyBus.Event
import br.paulo.tvlauncher.observer.SofyBus.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SofyBusTest {
    private val T = 1_800_000_000_000L
    private val DAY = 86_400_000L

    @Test fun lineRoundTripsAndCleansTabsAndBreaks() {
        val e = Event(T, Source.BACKUP, "night_backup", ok = false, detail = "pasta\tNAS\nfalhou")
        val back = SofyBus.parse(SofyBus.line(e))!!
        assertEquals(T, back.at); assertEquals(Source.BACKUP, back.source); assertEquals("night_backup", back.kind)
        assertEquals(false, back.ok); assertEquals("pasta NAS falhou", back.detail)
        assertEquals("backup.night_backup", back.key)
    }

    @Test fun detailIsCut() {
        val back = SofyBus.parse(SofyBus.line(Event(T, Source.UPDATE, "check", detail = "x".repeat(500))))!!
        assertEquals(120, back.detail.length)
    }

    @Test fun badLinesAreIgnored() {
        assertNull(SofyBus.parse(""))
        assertNull(SofyBus.parse("abc\tHOUSE\ttv_ligou\t1"))
        assertNull(SofyBus.parse("$T\tNOPE\ttv_ligou\t1"))
        assertNull(SofyBus.parse("$T\tHOUSE\t\t1"))
        assertEquals("tv_ligou", SofyBus.parse("$T\tHOUSE\ttv_ligou\t1")!!.kind)   // sem detalhe vale
    }

    @Test fun trimKeepsOnlyTheWeekAndTheNewest() {
        val old = SofyBus.line(Event(T - 8 * DAY, Source.HOUSE, "a"))
        val future = SofyBus.line(Event(T + DAY, Source.HOUSE, "b"))   // relógio voltou: fora
        val keep = (0 until SofyBus.MAX + 10).map { SofyBus.line(Event(T - 1000 + it, Source.ALARM, "rang$it")) }
        val out = SofyBus.trim(listOf(old, future, "lixo") + keep, T + 10_000)
        assertEquals(SofyBus.MAX, out.size)
        assertEquals("rang${SofyBus.MAX + 9}", SofyBus.parse(out.last())!!.kind)
        assertTrue(out.none { it == old || it == future })
    }

    @Test fun countsFailuresAndAround() {
        val ev = listOf(Event(T - 2 * DAY, Source.HOUSE, "tv_ligou"), Event(T - 100, Source.HOUSE, "tv_ligou"),
            Event(T - 50, Source.PLUGIN, "disabled", ok = false), Event(T - 10, Source.HOUSE, "tv_ligou"))
        assertEquals(mapOf("house.tv_ligou" to 2, "plugin.disabled" to 1), SofyBus.counts(ev, T - DAY))
        assertEquals(listOf("disabled"), SofyBus.failures(ev, T - DAY).map { it.kind })
        assertEquals(listOf(T - 100, T - 50), SofyBus.around(ev, T - 200, T - 20).map { it.at })
    }
}
