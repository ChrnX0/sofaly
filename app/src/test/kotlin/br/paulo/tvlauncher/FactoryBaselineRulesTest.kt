package br.paulo.tvlauncher

import br.paulo.tvlauncher.privilege.FactoryBaselineRules
import br.paulo.tvlauncher.privilege.FactoryBaselineRules.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FactoryBaselineRulesTest {
    private val foto = Snapshot(1L, setOf("com.maker.off"), null, null)

    @Test fun soVoltaOQueFoiOcultadoDepoisDaFoto() {
        val p = FactoryBaselineRules.plano(foto, setOf("com.maker.off", "com.b", "com.a"), "opportunistic", null, emptyMap(), emptySet())
        assertEquals(listOf("com.a", "com.b"), p.mostrar)   // o do fabricante fica desligado
        assertFalse(p.dnsMudou)
    }

    @Test fun gerenciadosENuncaFicam() {
        val p = FactoryBaselineRules.plano(foto, setOf("com.a", "com.ondemand", "tv.sofaly.launcher"), null, null,
            mapOf("com.ondemand" to "on_demand"), setOf("tv.sofaly.launcher"))
        assertEquals(listOf("com.a"), p.mostrar)
        assertEquals(mapOf("com.ondemand" to "on_demand"), p.mantidos)
    }

    @Test fun dnsAutomaticoTemVariosNomes() {
        assertTrue(FactoryBaselineRules.dnsIgual(null, null, "opportunistic", "dns.google"))
        assertTrue(FactoryBaselineRules.dnsIgual("", null, null, null))
        assertFalse(FactoryBaselineRules.dnsIgual(null, null, "hostname", "dns.adguard-dns.com"))
        assertTrue(FactoryBaselineRules.dnsIgual("hostname", "DNS.google", "hostname", "dns.google"))
        assertFalse(FactoryBaselineRules.dnsIgual("hostname", "a.b", "hostname", "c.d"))
        assertFalse(FactoryBaselineRules.dnsIgual("off", null, null, null))
    }

    @Test fun planoVazioQuandoNadaMudou() =
        assertTrue(FactoryBaselineRules.plano(foto, setOf("com.maker.off"), null, null, emptyMap(), emptySet()).vazio)

    @Test fun fotoNumaTvJaMexidaTiraOQueALimpezaOcultou() =
        assertEquals(setOf("com.maker.off"), FactoryBaselineRules.foto(5L, setOf("com.maker.off", "com.limpo"), setOf("com.limpo"), null, null).ocultos)

    @Test fun jsonIdaEVolta() {
        val s = Snapshot(42L, setOf("a.b", "c.d"), "hostname", "dns.google")
        assertEquals(s, FactoryBaselineRules.deJson(FactoryBaselineRules.paraJson(s)))
        val n = Snapshot(42L, emptySet(), null, null)
        assertEquals(n, FactoryBaselineRules.deJson(FactoryBaselineRules.paraJson(n)))
        assertNull(FactoryBaselineRules.deJson("{"))
        assertNull(FactoryBaselineRules.deJson(null))
    }
}
