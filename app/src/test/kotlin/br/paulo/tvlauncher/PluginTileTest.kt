package br.paulo.tvlauncher

import br.paulo.tvlauncher.screensaver.PluginTile as T
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PluginTileTest {
    @Test fun linhaCompleta() {
        val b = T.limpar("app.vpn", mapOf("title" to "VPN de casa", "detail" to "Conectada", "state" to "ON", "icon" to "chave"))
        assertEquals(T.Botao("app.vpn", "VPN de casa", "Conectada", T.Estado.LIGADO, "chave"), b)
    }

    @Test fun semTituloNaoServe() = assertNull(T.limpar("a", mapOf("title" to "  ", "state" to "on")))

    @Test fun textoLongoECortadoESemControle() {
        val b = T.limpar("a", mapOf("title" to "Luz\nda sala de estar grande", "detail" to "x".repeat(40)))!!
        assertEquals("Luz da sala de", b.titulo)
        assertEquals(16, b.detalhe.length)
        assertEquals(T.Estado.NENHUM, b.estado)
    }

    @Test fun iconeDeForaViraPadrao() {
        assertEquals(T.ICONE_PADRAO, T.limpar("a", mapOf("title" to "X", "icon" to "https://evil/x.png"))!!.icone)
    }
}
