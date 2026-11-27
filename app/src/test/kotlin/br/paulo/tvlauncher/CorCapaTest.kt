package br.paulo.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Capa sem foto (27/11/2026): só grafite e laranja, nunca roxo, verde ou rosa; mesmo nome, mesmo tom. */
class CorCapaTest {
    private fun r(c: Int) = c shr 16 and 0xFF
    private fun g(c: Int) = c shr 8 and 0xFF
    private fun b(c: Int) = c and 0xFF

    @Test fun soAPaleta() {
        val nomes = listOf("Velocidade Máxima", "Rádio Mar Azul FM", "Sertanejo", "Mpb", "Jazz", "BR", "Paulo", "Desenhos", "a", "", "99")
        for (n in nomes) {
            val c = Estilo.corCapa(n)
            // Quente como o laranja: vermelho >= verde >= azul (roxo tem azul alto; verde, verde acima do vermelho).
            assertTrue("$n: ${Integer.toHexString(c)}", r(c) >= g(c) && g(c) >= b(c))
            // Rosa = vermelho alto com azul acima do verde; aqui o azul nunca passa do grafite.
            assertTrue("$n: escuro o bastante para o nome creme por cima", r(c) < 0x80)
            assertEquals("opaca", 0xFF, c ushr 24)
        }
    }

    @Test fun mesmoNomeMesmoTom() {
        assertEquals(Estilo.corCapa("Velocidade Máxima"), Estilo.corCapa("velocidade  máxima"))
    }
}
