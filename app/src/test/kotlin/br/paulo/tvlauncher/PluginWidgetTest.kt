package br.paulo.tvlauncher

import br.paulo.tvlauncher.screensaver.PluginWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginWidgetTest {
    @Test fun gravaELeAListaSoComComponenteValido() {
        val l = listOf(PluginWidget.Escolhido(7, "com.exemplo.clima/.Widget"), PluginWidget.Escolhido(9, "com.b.app/com.b.app.W\$Grande"))
        assertEquals(l, PluginWidget.ler(PluginWidget.gravar(l)))
        assertEquals("com.exemplo.clima", l[0].pacote)
        // Lixo, id negativo, componente sem classe ou com espaço: saem; nunca passa de MAX.
        val t = """[{"id":-1,"provider":"a.b/.C"},{"id":1,"provider":"semclasse"},{"id":2,"provider":"a.b/ .C"},
            {"id":3,"provider":"a.b/.C"},{"id":4,"provider":"c.d/.E"},{"id":5,"provider":"e.f/.G"}]"""
        assertEquals(listOf(3, 4), PluginWidget.ler(t).map { it.id })
        assertEquals(emptyList<PluginWidget.Escolhido>(), PluginWidget.ler("{nada"))
        assertEquals(emptyList<PluginWidget.Escolhido>(), PluginWidget.ler(null))
    }

    @Test fun cabeNoMaximoDoisEUmDeCadaWidget() {
        val um = listOf(PluginWidget.Escolhido(1, "a.b/.C"))
        assertTrue(PluginWidget.cabe(emptyList(), "a.b/.C"))
        assertFalse(PluginWidget.cabe(um, "a.b/.C"))
        assertTrue(PluginWidget.cabe(um, "a.b/.D"))
        assertFalse(PluginWidget.cabe(um + PluginWidget.Escolhido(2, "a.b/.D"), "x.y/.Z"))
    }

    @Test fun enderecoDaFotoMudaComAvisoOuComOTempo() {
        val a = PluginWidget.imagem(7, 0, 0L)
        assertEquals(a, PluginWidget.imagem(7, 0, PluginWidget.FOTO_MS - 1))
        assertFalse(a == PluginWidget.imagem(7, 1, 0L))
        assertFalse(a == PluginWidget.imagem(7, 0, PluginWidget.FOTO_MS))
        assertEquals(7, PluginWidget.idDaImagem(a))
        assertNull(PluginWidget.idDaImagem("content://x/y"))
        assertTrue(PluginWidget.ehWidget(a))
    }

    @Test fun tamanhoDaFotoFicaEntreOsLimites() {
        assertEquals(160 to 100, PluginWidget.tamanho(10, 10, 1f))
        assertEquals(360 to 300, PluginWidget.tamanho(5000, 5000, 1f))
        assertEquals(500 to 400, PluginWidget.tamanho(500, 400, 2f))
    }
}
