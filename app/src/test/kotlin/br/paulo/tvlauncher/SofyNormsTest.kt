package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyBus
import br.paulo.tvlauncher.observer.SofyNorms
import br.paulo.tvlauncher.observer.SofyNorms.Day
import br.paulo.tvlauncher.observer.SofyNorms.Kind
import br.paulo.tvlauncher.observer.SofyRepair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Padrões da casa (plano D, passo 2): a Sofy aprende o normal sozinha e acusa quando ele quebra. */
class SofyNormsTest {
    private fun book(vararg days: Day) = days.fold(SofyNorms.Book()) { b, d -> SofyNorms.record(b, d) }
    private fun days(n: Int, f: (Int) -> Map<String, Double>) = (1..n).map { Day(it, f(it)) }.toTypedArray()

    @Test fun antesDeSeteDiasNaoAcusaNada() {
        val b = book(*days(6) { mapOf("count.network.drop.fail" to 0.0) })
        assertTrue(SofyNorms.check(b, Day(7, mapOf("count.network.drop.fail" to 50.0))).isEmpty())
    }

    @Test fun falhaMuitoAcimaDoNormalViraPico() {
        val b = book(*days(10) { mapOf("count.player.open.fail" to (it % 2).toDouble()) })
        val f = SofyNorms.check(b, Day(11, mapOf("count.player.open.fail" to 9.0)))
        assertEquals(listOf(Kind.SPIKE), f.map { it.kind })
        assertTrue(SofyNorms.check(b, Day(11, mapOf("count.player.open.fail" to 2.0))).isEmpty())
    }

    @Test fun algoQueAconteciaTodoDiaEParouViraSilencio() {
        val b = book(*days(10) { mapOf("count.update.check.ok" to 4.0, "count.sofy.tick.ok" to 20.0) })
        val f = SofyNorms.check(b, Day(11, mapOf("count.sofy.tick.ok" to 21.0)))   // update.check sumiu (= 0)
        assertEquals(listOf("SILENT:count.update.check.ok"), f.map { it.id })
    }

    @Test fun medidaForaDaFaixa() {
        val b = book(*days(10) { mapOf("home.ms" to 900.0 + it * 10) })
        assertEquals(listOf(Kind.OUT_OF_RANGE), SofyNorms.check(b, Day(11, mapOf("home.ms" to 4000.0))).map { it.kind })
        assertTrue(SofyNorms.check(b, Day(11, mapOf("home.ms" to 1000.0))).isEmpty())
    }

    @Test fun duasCoisasQueAndavamJuntasSeSeparam() {
        // 8 dias com o despertador tocando e a TV ligando; 2 dias sem nenhum dos dois. Hoje tocou e a TV não ligou.
        val b = book(*days(10) { if (it <= 8) mapOf("count.alarm.fired.ok" to 1.0, "count.house.tv_wake.ok" to 1.0) else emptyMap() })
        val f = SofyNorms.check(b, Day(11, mapOf("count.alarm.fired.ok" to 1.0)))
        assertEquals(listOf("PAIR_BROKEN:count.alarm.fired.ok=>count.house.tv_wake.ok"), f.map { it.id })
    }

    @Test fun tresDiasSeguidosViramONovoNormalEElaCala() {
        var b = book(*days(10) { mapOf("count.update.check.ok" to 4.0) })
        var accepted = emptyList<SofyNorms.Finding>()
        for (d in 11..13) {
            val day = Day(d, emptyMap())
            val f = SofyNorms.check(b, day)
            assertEquals("dia $d", 1, f.size)
            val (s, nn) = SofyNorms.settle(b, d, f)
            b = SofyNorms.record(s, day); accepted = nn
        }
        assertEquals(listOf("SILENT:count.update.check.ok"), accepted.map { it.id })
        assertTrue(SofyNorms.check(b, Day(14, emptyMap())).isEmpty())
    }

    @Test fun livroVoltaIgualDoJson() {
        val b = SofyNorms.settle(book(*days(3) { mapOf("count.a.b.ok" to it.toDouble(), "x" to 1.5) }), 3,
            listOf(SofyNorms.Finding(Kind.SPIKE, "k", "d"))).first.copy(accepted = mapOf("SILENT:z" to 2))
        assertEquals(b, SofyNorms.fromJson(SofyNorms.toJson(b)))
        assertEquals(SofyNorms.Book(), SofyNorms.fromJson("lixo"))
    }

    @Test fun contaPorFonteTipoEResultado() {
        val ev = listOf(SofyBus.Event(10, SofyBus.Source.NETWORK, "drop", ok = false), SofyBus.Event(20, SofyBus.Source.NETWORK, "drop", ok = false),
            SofyBus.Event(30, SofyBus.Source.ALARM, "fired"), SofyBus.Event(99, SofyBus.Source.ALARM, "fired"))
        assertEquals(mapOf("count.network.drop.fail" to 2.0, "count.alarm.fired.ok" to 1.0), SofyNorms.countsOf(ev, 0, 50))
    }

    @Test fun quebraDoNormalVaiParaAIAENaoParaFerramentaCega() {
        val c = SofyRepair.onSignal(emptyList(), SofyRepair.Signal(SofyRepair.Type.INCOHERENT, "norm:count.player.open.fail", 1)).single()
        assertEquals(listOf(SofyRepair.Tool.ASK_AI, SofyRepair.Tool.REPORT), SofyRepair.candidates(c))
    }
}
