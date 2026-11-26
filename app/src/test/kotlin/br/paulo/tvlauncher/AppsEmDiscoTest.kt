package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.AppsEmDisco
import br.paulo.tvlauncher.home.AppsEmDisco.Registro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lista de apps da HOME em disco para a abertura a frio (Brainstorm 26/11/2026, item 27). */
class AppsEmDiscoTest {
    private val regs = listOf(
        Registro("com.netflix.ninja", "Netflix", "/data/app/x/base.apk|Main|0", 2, false, 1_700_000_000_000L),
        Registro("entrada:hdmi1", "HDMI 1", "", -1, true, 0L),
    )

    @Test fun idaEVolta() {
        val t = AppsEmDisco.indice(regs, "BANNER", 474999, 2f)
        val i = AppsEmDisco.lerIndice(t, 474999, 2f)!!
        assertEquals("BANNER", i.estilo)
        assertEquals(regs, i.apps)
    }

    @Test fun ignoraOutraVersaoDensidadeOuLixo() {
        val t = AppsEmDisco.indice(regs, "BANNER", 474999, 2f)
        assertNull(AppsEmDisco.lerIndice(t, 475000, 2f))    // app atualizado: o desenho pode ter mudado
        assertNull(AppsEmDisco.lerIndice(t, 474999, 1.5f))  // outra tela: ícone do tamanho errado
        assertNull(AppsEmDisco.lerIndice("{", 474999, 2f))
        assertNull(AppsEmDisco.lerIndice(null, 474999, 2f))
        assertNull(AppsEmDisco.lerIndice(t.replace("\"formato\":1", "\"formato\":99"), 474999, 2f))
    }

    @Test fun soGravaQuandoMuda() {
        val a = AppsEmDisco.assinatura(regs, "BANNER", setOf("com.netflix.ninja"))
        // Uso alguns minutos depois (mesma hora): não regrava a cada volta à HOME.
        val poucoDepois = regs.map { if (it.uso > 0) it.copy(uso = it.uso + 60_000) else it }
        assertEquals(a, AppsEmDisco.assinatura(poucoDepois, "BANNER", setOf("com.netflix.ninja")))
        assertNotEquals(a, AppsEmDisco.assinatura(regs, "REDONDO", setOf("com.netflix.ninja")))
        assertNotEquals(a, AppsEmDisco.assinatura(regs.take(1), "BANNER", setOf("com.netflix.ninja")))
        assertNotEquals(a, AppsEmDisco.assinatura(regs, "BANNER", emptySet()))
        assertNotEquals(a, AppsEmDisco.assinatura(listOf(regs[0].copy(chave = "novo.apk")) + regs[1], "BANNER", setOf("com.netflix.ninja")))
    }
}
