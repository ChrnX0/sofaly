package br.paulo.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Item 22 (Brainstorm 26/11/2026): saídas que o tratador não vê contam na vigia; o servidor pode segurar versões. */
class VersoesSeguradasTest {
    @Test fun lerALista() {
        assertEquals(setOf(475010L, 475020L), VersoesSeguradas.ler("""{"hold":[475010,475020]}"""))
        assertEquals(emptySet<Long>(), VersoesSeguradas.ler("""{"hold":[]}"""))
        assertEquals(emptySet<Long>(), VersoesSeguradas.ler(null))              // sem rede: não segura
        assertEquals(emptySet<Long>(), VersoesSeguradas.ler("<html>erro</html>"))
        assertEquals(setOf(5L), VersoesSeguradas.ler("""{"hold":[0,-1,"x",5]}"""))
    }

    private val v = 475000L
    private val t0 = VigiaRegras.RELOGIO_MINIMO_MS + 1_000_000L
    private fun vigiando() = VigiaRegras.Estado(versaoVista = v, vigiando = v, vigiaDesde = t0)

    @Test fun duasTravadasEm10MinMarcamRuim() {
        val q = VigiaRegras.aoSair(vigiando(), v, listOf(t0 + 60_000 to VigiaRegras.Saida.TRAVADA, t0 + 120_000 to VigiaRegras.Saida.NATIVA))
        assertTrue(q.marcouRuim)
        assertTrue(v in q.estado.ruins)
        assertEquals(t0 + 120_000, q.estado.saidasAte)
    }

    @Test fun naoContaDuasVezesNemOQueVeioAntesDaVigia() {
        val primeira = VigiaRegras.aoSair(vigiando(), v, listOf(t0 + 60_000 to VigiaRegras.Saida.TRAVADA, t0 - 5_000 to VigiaRegras.Saida.MEMORIA))
        assertFalse(primeira.marcouRuim)
        assertEquals(1, primeira.estado.quedas.size)   // a de antes da vigia (versão velha) não conta
        // Próxima abertura: o Android devolve a mesma lista de novo; nada novo, nada muda.
        val deNovo = VigiaRegras.aoSair(primeira.estado, v, listOf(t0 + 60_000 to VigiaRegras.Saida.TRAVADA))
        assertFalse(deNovo.marcouRuim)
        assertEquals(1, deNovo.estado.quedas.size)
    }

    @Test fun semVigiaNaoMarca() {
        val e = VigiaRegras.Estado(versaoVista = v)
        val q = VigiaRegras.aoSair(e, v, listOf(t0 to VigiaRegras.Saida.TRAVADA, t0 + 1 to VigiaRegras.Saida.TRAVADA))
        assertFalse(q.marcouRuim)
        assertEquals(t0 + 1, q.estado.saidasAte)
    }

    @Test fun estadoGuardaSaidasAte() {
        val e = vigiando().copy(saidasAte = t0 + 9)
        assertEquals(e, VigiaRegras.deTexto(VigiaRegras.paraTexto(e)))
    }
}
