package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyBench
import br.paulo.tvlauncher.observer.SofyRepair.Tool
import br.paulo.tvlauncher.observer.SofyRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O gabarito do banco de prova dos modelos tem de ser coerente; senão a nota mede o gabarito, não o modelo. */
class SofyBenchTest {

    @Test fun casosDeConsertoTemCertoEErradoSeparados() {
        assertEquals(24, SofyBench.REPAIR.size)
        assertEquals(SofyBench.REPAIR.size, SofyBench.REPAIR.map { it.id }.toSet().size)
        for (it in SofyBench.REPAIR) {
            assertTrue(it.id, it.good.isNotEmpty())
            assertTrue("${it.id}: certo e errado ao mesmo tempo", (it.good intersect it.bad).isEmpty())
            assertFalse(it.id, Tool.ASK_AI in it.good)
        }
    }

    @Test fun telasPlantadasTemOTextoErradoESoEle() {
        val defeito = SofyBench.REVIEW.filter { it.bad != null }
        val limpas = SofyBench.REVIEW.filter { it.bad == null }
        assertEquals(12, defeito.size); assertEquals(6, limpas.size)
        for (it in defeito) assertTrue(it.id, it.bad in it.texts)
    }

    @Test fun regraBoaPegaODefeitoENaoDisparaEmTextoCerto() {
        for (j in SofyBench.SECOND) {
            assertEquals(4, j.rules.size); assertEquals(2, j.keep.size)
            for (r in j.rules) {
                assertNotNull("${r.id} não compila", SofyRules.safe(r.pattern))
                val noCorpus = SofyRules.violation(r, SofyBench.CORPUS)
                if (r.id in j.keep) {
                    assertNull("${r.id} (boa) disparou em \"$noCorpus\"", noCorpus)
                    assertNotNull("${r.id} (boa) não pega nada da tela", SofyRules.violation(r, j.texts))
                } else assertNotNull("${r.id} (ruim) não dispara em texto certo nenhum", noCorpus)
            }
        }
    }

    @Test fun textosCertosDasTelasNaoQuebramAsRegrasBoas() {
        val boas = SofyBench.SECOND.flatMap { j -> j.rules.filter { it.id in j.keep } }
        for (v in SofyBench.REVIEW) for (r in boas) {
            val certos = v.texts.filter { it != v.bad }
            assertNull("${r.id} disparou em texto certo de ${v.id}", SofyRules.violation(r, certos))
        }
    }

    @Test fun notaDoConserto() {
        val r07 = SofyBench.REPAIR.first { it.id == "r07" }
        val bom = SofyBench.scoreRepair(r07, """{"tools":["UNDO_LAST_CHANGE","REPORT"],"cause":"setting changed"}""")
        assertTrue(bom.valid && bom.top1 && bom.any); assertEquals(0, bom.harm)
        val mau = SofyBench.scoreRepair(r07, """{"tools":["REBOOT","UNDO_LAST_CHANGE"]}""")
        assertFalse(mau.top1); assertTrue(mau.any); assertEquals(1, mau.harm)
        assertFalse(SofyBench.scoreRepair(r07, "não sei").valid)
    }

    @Test fun notaDaRevisao() {
        val v02 = SofyBench.REVIEW.first { it.id == "v02" }
        val resp = """{"defects":["chance de chuva acima de 100%"],"rules":[
            {"kind":"RANGE","screen":"*","pattern":"chuva (\\d+)%","min":0,"max":100,"why":"x"},
            {"kind":"FORBIDDEN","screen":"*","pattern":"°","why":"y"}]}"""
        val s = SofyBench.scoreReview(v02.screen, v02.texts, v02.bad, resp)
        assertTrue(s.valid); assertEquals(1, s.flagged); assertEquals(2, s.raw)
        assertEquals(1, s.catch)
        assertTrue("a regra do ° dispara em texto certo", s.falseFire >= 1)
        assertFalse(SofyBench.scoreReview("x", listOf("a"), null, "sem json").valid)
    }

    @Test fun notaDaSegundaOpiniao() {
        val j1 = SofyBench.SECOND.first { it.id == "j1" }
        val s = SofyBench.scoreJudge(j1, """{"keep":["j1a","j1c"]}""")
        assertEquals(listOf(1, 1, 1, 1), listOf(s.tp, s.fp, s.fn, s.tn))
    }

    @Test fun linhaNaoLevaTexto() {
        val l = SofyBench.line(SofyBench.Task.REPAIR, "claude-haiku-5-5", "", "r01", 1, 200, 1500, 600, 200, 0, "end_turn", "valid=1")
        assertEquals("B task=REPAIR model=claude-haiku-5-5 eff=- item=r01 rep=1 http=200 ms=1500 in=600 out=200 cache=0 stop=end_turn valid=1", l)
    }
}
