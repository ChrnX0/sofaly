package br.paulo.tvlauncher

import br.paulo.tvlauncher.family.Mural
import br.paulo.tvlauncher.home.BarraIlha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Brainstorm item 10 (26/11/2026): recado da família fica no descanso e na Ilha até ser lido. */
class MuralRecadoTest {
    @Test fun partesSeparaQuemDoTexto() {
        assertEquals("Ana" to "Dentista quinta", Mural.partes("Ana: Dentista quinta"))
        assertEquals("Comprar pão" to "", Mural.partes("Comprar pão"))
        // ":" no começo ou longe demais não é nome
        assertEquals(": x" to "", Mural.partes(": x"))
    }

    @Test fun linhaDaIlhaSoComNaoLidos() {
        val um = { t: String -> "Recado · $t" }
        val varios = { n: Int, t: String -> "$n recados · $t" }
        assertNull(Mural.linhaIlha(Mural.Conteudo(), um, varios))
        val c = Mural.Conteudo(recados = listOf("Ana: oi", "Bia: jantar 20h"), lidos = listOf("Ana: oi"))
        assertEquals("Recado · Bia: jantar 20h", Mural.linhaIlha(c, um, varios))
        assertEquals("2 recados · Bia: jantar 20h", Mural.linhaIlha(c.copy(lidos = emptyList()), um, varios))
        assertNull(Mural.linhaIlha(c.copy(lidos = c.recados), um, varios))
    }

    @Test fun recadoEntraNaFilaDaIlhaECresce() {
        val f = BarraIlha.fila(BarraIlha.Estado(recado = "Recado · Ana: oi"))
        assertEquals(BarraIlha.Tipo.RECADO, f.single().tipo)
        assertEquals(BarraIlha.Tipo.RECADO, BarraIlha.chegou(emptyList(), f)?.tipo)
    }
}
