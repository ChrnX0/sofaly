package br.paulo.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NomeNoTest {
    @Test fun nomesComuns() {
        assertEquals("sofaly-sala", NomeNo.montar("Sala"))
        assertEquals("sofaly-quarto", NomeNo.montar("Quarto"))
        assertEquals("sofaly-sala-de-estar", NomeNo.montar("  Sala de Estar "))
        assertEquals("sofaly-cozinha-n-2", NomeNo.montar("Cozinha Nº 2"))
        assertEquals("sofaly-escritorio-do-joao", NomeNo.montar("Escritório do João"))
    }

    /** A Sala (e toda TV que já entrou no Tailscale) segue "onetv-" até a troca combinada: o endereço não pode mudar sozinho. */
    @Test fun prefixoAntigoContinuaIgual() {
        assertEquals("onetv-sala-de-estar", NomeNo.montar("Sala de Estar", NomeNo.PREFIXO_ANTIGO))
        assertEquals("onetv-tv", NomeNo.montar(null, NomeNo.PREFIXO_ANTIGO))
        assertEquals("sofaly-sala", NomeNo.montar("Sala", "qualquer"))   // prefixo estranho não vira nome
    }

    @Test fun semNomeOuSoSimbolos() {
        assertEquals("sofaly-tv", NomeNo.montar(null))
        assertEquals("sofaly-tv", NomeNo.montar("★★"))
    }

    @Test fun cabeNumRotuloDns() {
        for (p in NomeNo.PREFIXOS) {
            val n = NomeNo.montar("a".repeat(57) + " b" + "c".repeat(30), p)
            assertTrue(n, n.length <= 63)
            assertTrue(n, Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$").matches(n))
        }
    }

    @Test fun qualPrefixoATvUsa() {
        // TV nova: nada salvo, nunca entrou.
        assertEquals("sofaly", NomeNo.prefixoPara(null, null, jaEntrou = false))
        // TV que já entrou com o código antigo (crachá salvo ou endereço onetv-): fica onetv.
        assertEquals("onetv", NomeNo.prefixoPara(null, null, jaEntrou = true))
        assertEquals("onetv", NomeNo.prefixoPara(null, "onetv-sala-de-estar.tail65f02d.ts.net", jaEntrou = true))
        assertEquals("onetv", NomeNo.prefixoPara(null, "tv-sala.tail1.ts.net", jaEntrou = true))
        assertEquals("sofaly", NomeNo.prefixoPara(null, "sofaly-sala.tail1.ts.net", jaEntrou = true))
        // O salvo manda (inclusive depois da troca pedida).
        assertEquals("sofaly", NomeNo.prefixoPara("sofaly", "onetv-sala.tail1.ts.net", jaEntrou = true))
        assertEquals("onetv", NomeNo.prefixoPara("onetv", null, jaEntrou = false))
        assertEquals("sofaly", NomeNo.prefixoPara("lixo", null, jaEntrou = false))
    }

    @Test fun reconheceOsDoisPrefixos() {
        assertTrue(NomeNo.ehTv("sofaly-quarto")); assertTrue(NomeNo.ehTv("onetv-quarto"))
        assertFalse(NomeNo.ehTv("pc-do-paulo")); assertFalse(NomeNo.ehTv("sofaly-")); assertFalse(NomeNo.ehTv("sofalyx-sala"))
        assertEquals("quarto-dos-pais", NomeNo.semPrefixo("sofaly-quarto-dos-pais"))
        assertEquals("quarto-dos-pais", NomeNo.semPrefixo("onetv-quarto-dos-pais"))
        assertEquals("pc-do-paulo", NomeNo.semPrefixo("pc-do-paulo"))
        assertNull(NomeNo.prefixoDe("pc"))
    }
}
