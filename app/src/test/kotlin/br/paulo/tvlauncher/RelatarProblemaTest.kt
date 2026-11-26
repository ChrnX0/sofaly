package br.paulo.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

/** Relatar um problema (degrau 3): só dados técnicos; a mensagem do erro nunca vai. */
class RelatarProblemaTest {
    private val d = RelatarProblema.Dados("1.0", 460100, "12", "Chromecast", "completo", 1, 0,
        "java.lang.IllegalStateException: /storage/emulated/0/Ana/foto.jpg")

    @Test fun soOTipoDoErro() {
        assertEquals("IllegalStateException", RelatarProblema.tipoDoErro(d.ultimoErro!!))
        assertEquals("?", RelatarProblema.tipoDoErro("algo sem tipo"))
        val c = RelatarProblema.corpo(d)
        assertFalse(c.contains("Ana")); assertFalse(c.contains("/storage"))
        assertTrue(c.contains("460100")); assertTrue(c.contains("1/0"))
    }

    @Test fun emailQuandoHouver() {
        val m = RelatarProblema.destino("suporte@exemplo.com", d)!!
        assertTrue(m.startsWith("mailto:suporte@exemplo.com?subject="))
        assertFalse(m.contains(" ")); assertFalse(m.contains("\n")); assertFalse(m.contains("Ana"))
        assertTrue(m.contains("460100")); assertFalse(m.contains("**"))
        assertTrue(m.length < 1200)   // QR ainda legível de longe
    }

    /** Item 6 (Brainstorm 26/11): sem e-mail no build, nada de QR. O GitHub público nunca mais. */
    @Test fun semEmailNaoHaQrENuncaGithub() {
        for (e in listOf("", "  ", "sem-arroba", "a@b", "x@y.com?bcc=z@w.com", "a b@c.com"))
            assertNull(e, RelatarProblema.destino(e, d))
        assertFalse(CodeSource.file("RelatarProblema.kt").readText().contains("github.com"))
    }

    @Test fun envioSoComCamposTecnicos() {
        val o = RelatarProblema.envio(d, "  A HOME travou  ")
        assertEquals("tv", o.getString("kind"))
        assertEquals("A HOME travou", o.getString("text"))
        assertEquals(460100, o.getLong("code"))
        val e = o.getJSONArray("errors").getJSONObject(0)
        assertEquals("IllegalStateException", e.getString("type"))
        assertFalse(o.toString().contains("Ana")); assertFalse(o.toString().contains("/storage"))
        assertEquals(2000, RelatarProblema.envio(d, "x".repeat(3000)).getString("text").length)
        assertEquals(0, RelatarProblema.envio(d.copy(ultimoErro = null), "").getJSONArray("errors").length())
    }

    @Test fun envioDosErrosDoPainel() {
        val erros = JSONObject().put("erros", JSONArray()
            .put(JSONObject().put("tipo", "NullPointerException").put("dia", "2026-11-26").put("versao", "1.0 (460100)")
                .put("pilha", JSONArray().put("br.paulo.tvlauncher.MainActivity.onCreate(MainActivity.kt:10)")))
            .put(JSONObject().put("tipo", "rm -rf").put("dia", "ontem").put("versao", JSONObject.NULL)))
        val o = RelatarProblema.envioErros(d, erros)
        assertEquals("errors", o.getString("kind"))
        val a = o.getJSONArray("errors")
        assertEquals(2, a.length())
        assertEquals(460100, a.getJSONObject(0).getLong("version"))
        assertEquals("2026-11-26", a.getJSONObject(0).getString("day"))
        assertEquals(1, a.getJSONObject(0).getJSONArray("stack").length())
        assertEquals("error", a.getJSONObject(1).getString("type"))
        assertEquals("", a.getJSONObject(1).getString("day"))
        assertEquals(0, a.getJSONObject(1).getLong("version"))
    }

    @Test fun codigoDaVersao() {
        assertEquals(475033, RelatarProblema.codigoDe("3.4 (475033)"))
        assertEquals(475033, RelatarProblema.codigoDe("475033"))
        assertEquals(0, RelatarProblema.codigoDe(null))
        assertEquals(0, RelatarProblema.codigoDe("abc"))
    }
}
