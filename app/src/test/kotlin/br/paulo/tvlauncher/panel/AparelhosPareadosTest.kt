package br.paulo.tvlauncher.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AparelhosPareadosTest {
    private val A = AparelhosPareados

    @Test fun emiteChaveLongaEGuardaSoOHash() {
        val (lista, chave) = A.emitir(emptyList(), "Pixel 8", 1000)
        assertEquals(A.TAMANHO_HEX, chave.length)
        assertTrue(chave.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(1, lista.size)
        assertFalse("a chave em si não fica na TV", A.texto(lista).contains(chave))
        assertEquals("Pixel 8", lista[0].nome)
    }

    @Test fun conferirAchaSoAChaveCerta() {
        val (l1, c1) = A.emitir(emptyList(), "A", 1)
        val (l2, c2) = A.emitir(l1, "B", 2)
        assertEquals("A", A.conferir(l2, c1)?.nome)
        assertEquals("B", A.conferir(l2, c2)?.nome)
        assertNull(A.conferir(l2, c1.reversed()))
        assertNull(A.conferir(l2, null))
        assertNull(A.conferir(l2, ""))
        assertNull("maiúscula não é a mesma chave", A.conferir(l2, c1.uppercase()))
        assertNull("tamanho errado", A.conferir(l2, c1 + "0"))
    }

    @Test fun revogarTiraOAcesso() {
        val (l, c) = A.emitir(emptyList(), "A", 1)
        val sem = A.revogar(l, l[0].id)
        assertNull(A.conferir(sem, c))
        assertTrue(sem.isEmpty())
    }

    @Test fun guardaNoMaximoDezEOMaisAntigoSai() {
        var l = emptyList<AparelhosPareados.Aparelho>()
        val chaves = mutableListOf<String>()
        for (i in 1..12) { val (n, c) = A.emitir(l, "c$i", i.toLong()); l = n; chaves += c }
        assertEquals(A.MAX, l.size)
        assertNull(A.conferir(l, chaves[0]))
        assertNull(A.conferir(l, chaves[1]))
        assertNotNull(A.conferir(l, chaves[11]))
        assertEquals(l.size, l.map { it.id }.toSet().size)
    }

    @Test fun usoETextoIdaEVolta() {
        val (l, c) = A.emitir(emptyList(), "A", 1)
        val u = A.usado(l, l[0].id, 99)
        assertEquals(99, u[0].ultimoUso)
        val lido = A.ler(A.texto(u))
        assertEquals(u, lido)
        assertNotNull(A.conferir(lido, c))
        assertTrue(A.ler("lixo").isEmpty())
        assertTrue(A.ler(null).isEmpty())
    }

    @Test fun nomeLimpoEPublicoSemHash() {
        assertEquals("Celular", A.nomeLimpo(null))
        assertEquals("Celular", A.nomeLimpo("  \n "))
        assertEquals("ab", A.nomeLimpo("a\u0000b"))
        assertEquals(40, A.nomeLimpo("x".repeat(100)).length)
        val (l, _) = A.emitir(emptyList(), "A", 5)
        val p = A.publico(l[0])
        assertFalse(p.has("h")); assertFalse(p.has("hash"))
        assertEquals(5, p.getLong("criado"))
    }
}
