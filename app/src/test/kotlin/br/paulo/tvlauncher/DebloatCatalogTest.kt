package br.paulo.tvlauncher

import br.paulo.tvlauncher.privilege.Debloat
import br.paulo.tvlauncher.privilege.DebloatCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DebloatCatalogTest {
    private val json = """{"schema":1,"devices":492,"packages":{
      "com.tcl.bi":{"lists":42,"brands":["tcl"],"kind":"telemetry","note":"TCL analytics"},
      "com.sony.only":{"lists":1,"brands":["sony"],"kind":"preloaded"},
      "com.rare.app":{"lists":2,"brands":["acme"],"kind":"other"},
      "com.google.android.feedback":{"lists":368,"brands":["google","tcl"],"kind":"telemetry"},
      "com.google.android.katniss":{"lists":207,"brands":["google"],"kind":"other"},
      "com.vendor.tvlauncher.x":{"lists":50,"brands":["tcl"],"kind":"other"},
      "sem-ponto":{"lists":9,"brands":["x"],"kind":"other"},
      "com.bad.lists":{"lists":0,"brands":["x"]},
      "com.bad.note":{"lists":5,"brands":["x"],"note":"a\nb"}
    }}"""
    private val cat = DebloatCatalog.parse(json)

    @Test fun parseDescartaMalformado() {
        assertEquals(492, cat.devices)
        assertFalse("sem-ponto" in cat.entries)
        assertFalse("com.bad.lists" in cat.entries)
        assertEquals(null, cat.entries["com.bad.note"]!!.note)
        assertEquals(DebloatCatalog.Kind.TELEMETRY, cat.entries["com.tcl.bi"]!!.kind)
    }

    @Test fun jsonInvalidoOuSchemaNovoDaVazio() {
        assertTrue(DebloatCatalog.parse("{").entries.isEmpty())
        assertTrue(DebloatCatalog.parse("""{"schema":2,"packages":{}}""").entries.isEmpty())
    }

    @Test fun soInstaladosComListasOuMesmaMarca() {
        val inst = listOf("com.tcl.bi", "com.sony.only", "com.rare.app", "com.google.android.feedback", "com.nao.no.catalogo")
        val s = DebloatCatalog.sugerir(cat, inst, setOf("sony"), Debloat.NUNCA, emptySet()).map { it.pkg }
        // mesma marca primeiro (sony, 1 lista), depois por número de listas; com.rare.app (2 listas, outra marca) fica fora
        assertEquals(listOf("com.sony.only", "com.google.android.feedback", "com.tcl.bi"), s)
    }

    @Test fun nuncaPalavrasEDecididosFicamFora() {
        val inst = listOf("com.google.android.katniss", "com.vendor.tvlauncher.x", "com.tcl.bi", "com.google.android.feedback")
        val s = DebloatCatalog.sugerir(cat, inst, setOf("tcl"), Debloat.NUNCA, setOf("com.google.android.feedback")).map { it.pkg }
        assertEquals(listOf("com.tcl.bi"), s)
    }

    @Test fun protecaoEmTempoRealVence() =
        assertTrue(DebloatCatalog.sugerir(cat, listOf("com.tcl.bi"), setOf("tcl"), setOf("com.tcl.bi"), emptySet()).isEmpty())

    @Test fun marcasDoFabricante() = assertEquals(setOf("sony", "corporation"), DebloatCatalog.marcas("Sony Corporation", null))

    /** O catálogo do APK: nada do NUNCA, nada com palavra protegida, nenhum pacote do launcher do Google. */
    @Test fun catalogoDoApkRespeitaAProtecao() {
        val f = listOf("src/main/assets/debloat/catalog.json", "app/src/main/assets/debloat/catalog.json").map(::File).first { it.exists() }
        val c = DebloatCatalog.parse(f.readText())
        assertTrue(c.entries.size > 500)
        assertTrue(c.entries.keys.none { it in Debloat.NUNCA })
        assertTrue(c.entries.keys.none { DebloatCatalog.protegidoPorNome(it) })
    }
}
