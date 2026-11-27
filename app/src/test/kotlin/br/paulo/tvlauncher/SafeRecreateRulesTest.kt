package br.paulo.tvlauncher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #619 (27/11/2026): dois recreate() seguidos na mesma tela derrubavam o app em findViewLocked. Toda tela passa por
 * recreateOnce (SafeRecreate.kt); este teste não deixa um recreate() solto voltar ao código.
 */
class SafeRecreateRulesTest {
    @Test fun nenhumRecreateSolto() {
        val soltos = CodeSource.all().filter { it.name != "SafeRecreate.kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l ->
                val codigo = l.trimStart()
                if (codigo.startsWith("//") || codigo.startsWith("*") || codigo.startsWith("/*")) return@mapIndexedNotNull null
                val semComentario = l.substringBefore("//")
                if (Regex("""(?<![\w])recreate\(\)""").containsMatchIn(semComentario)) "${f.name}:${i + 1}" else null
            }
        }.toList()
        assertTrue("recreate() direto em $soltos: use recreateOnce() (SafeRecreate.kt, #619)", soltos.isEmpty())
    }

    @Test fun soOPrimeiroPedidoPassa() {
        assertTrue(SafeRecreate.permitido(jaPediu = false, fechando = false, destruida = false))
        assertFalse("segundo pedido da mesma tela", SafeRecreate.permitido(jaPediu = true, fechando = false, destruida = false))
        assertFalse("tela fechando", SafeRecreate.permitido(jaPediu = false, fechando = true, destruida = false))
        assertFalse("tela destruída", SafeRecreate.permitido(jaPediu = false, fechando = false, destruida = true))
    }
}
