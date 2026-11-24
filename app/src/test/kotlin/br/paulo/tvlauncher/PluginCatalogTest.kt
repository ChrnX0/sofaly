package br.paulo.tvlauncher

import br.paulo.tvlauncher.player.LojaCurada
import br.paulo.tvlauncher.screensaver.PluginCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vitrine de plugins: ids únicos, onde conseguir cada um e o que o build da Play nunca faz (baixar APK). */
class PluginCatalogTest {
    @Test fun idsUnicosEComOndeConseguir() {
        val l = PluginCatalog.LISTA
        assertEquals(l.size, l.map { it.id }.toSet().size)
        l.forEach { assertNotNull("${it.id} sem página", PluginCatalog.pagina(it)) }
        l.forEach { e -> e.repo?.let { assertTrue(e.id, LojaCurada.repoDeEndereco(it) == it) } }
    }

    @Test fun buildDaPlayNuncaInstalaApk() {
        PluginCatalog.LISTA.forEach { e ->
            assertTrue(e.id, PluginCatalog.acao(e, buildPlay = true, temPlayStore = true) != PluginCatalog.Acao.INSTALAR)
            assertTrue(e.id, PluginCatalog.acao(e, buildPlay = true, temPlayStore = false) != PluginCatalog.Acao.INSTALAR)
        }
    }

    @Test fun preferePlayDepoisRelease() {
        val o = PluginCatalog.LISTA.first { it.id == "overflight" }
        assertEquals(PluginCatalog.Acao.PLAY, PluginCatalog.acao(o, buildPlay = false, temPlayStore = true))
        assertEquals(PluginCatalog.Acao.INSTALAR, PluginCatalog.acao(o, buildPlay = false, temPlayStore = false))
        val a = PluginCatalog.LISTA.first { it.id == "arcticons" }
        assertEquals(PluginCatalog.Acao.SO_QR, PluginCatalog.acao(a, buildPlay = false, temPlayStore = false))
        assertTrue(PluginCatalog.pagina(o)!!.startsWith("https://play.google.com/"))
    }

    @Test fun lojaRecebeSoOsComRepositorio() {
        val ids = PluginCatalog.itensLoja().map { it.id }
        assertEquals(PluginCatalog.LISTA.count { it.repo != null }, ids.size)
        assertTrue(ids.all { it.startsWith("pl-") })
        assertTrue(LojaCurada.CATALOGO.map { it.id }.containsAll(ids))
        assertEquals(LojaCurada.CATALOGO.size, LojaCurada.CATALOGO.map { it.id }.toSet().size)
    }

    @Test fun nenhumNomeDeConcorrenteNaVitrine() {
        PluginCatalog.LISTA.forEach { assertTrue(it.id, !it.nome.contains("projectivy", ignoreCase = true)) }
        assertEquals("TV Icon Pack", PluginCatalog.nomeDoPacote("dev.projectivy.blueprint.app"))
    }

    // ---- vitrine do servidor (24/11/2026)
    private val nova = """{"id":"novo-fundo","type":"wallpaper","name":"Novo Fundo","play":"com.exemplo.fundo",
        "description":{"en":"Calm photos","pt":"Fotos calmas"}}"""

    @Test fun servidorSoMantemEAcrescentaDaPlay() {
        val l = PluginCatalog.parse("""{"plugins":[$nova,{"id":"overflight","repo":"mau/repo","package":"mau.pacote"},{"id":"arcticons"}]}""", "pt")!!
        assertEquals(listOf("novo-fundo", "overflight", "arcticons"), l.map { it.id })
        assertEquals("Fotos calmas", l[0].texto)
        assertEquals(PluginCatalog.Acao.PLAY, PluginCatalog.acao(l[0], buildPlay = false, temPlayStore = true))
        assertEquals(PluginCatalog.Acao.SO_QR, PluginCatalog.acao(l[0], buildPlay = false, temPlayStore = false))
        // Entrada conhecida: valem os dados do app, nunca os do servidor (pacote e repositório).
        val o = l[1]; val embutida = PluginCatalog.LISTA.first { it.id == "overflight" }
        assertEquals(embutida.repo, o.repo); assertEquals(embutida.pacote, o.pacote)
        assertEquals("Calm photos", PluginCatalog.parse("""{"plugins":[$nova]}""", "ja")!!.single().texto)
    }

    @Test fun servidorNuncaDaApkNemNomeDeConcorrente() {
        val ruins = listOf(
            """{"id":"x","type":"wallpaper","name":"X","repo":"a/b"}""",                     // sem Play
            """{"id":"y","type":"wallpaper","name":"Projectivy Y","play":"com.y.app"}""",     // nome de concorrente
            """{"id":"Z!","type":"icons","name":"Z","play":"com.z.app"}""",                  // id fora do formato
            """{"id":"w","type":"apk","name":"W","play":"com.w.app"}""",                     // tipo desconhecido
            """{"id":"v","type":"icons","name":"V","play":"com.v;rm"}""",                    // pacote inválido
        )
        for (r in ruins) assertNull(r, PluginCatalog.parse("""{"plugins":[$r]}""", "en"))
        val l = PluginCatalog.parse("""{"plugins":[$nova,{"id":"q","type":"icons","name":"Q","play":"com.q.app","repo":"a/b"}]}""", "en")!!
        l.forEach { assertNull(it.id, it.repo); assertNull(it.id, it.lojaId) }
        assertNull(PluginCatalog.parse("não é json", "en"))
        assertNull(PluginCatalog.parse("""{"plugins":[]}""", "en"))
        assertEquals("id", PluginCatalog.idioma("in"))
    }

    @Test fun bloqueioSoComPacoteValido() {
        assertEquals(setOf("com.ruim.plugin"), PluginCatalog.bloqueados("""{"blocked":[{"package":"com.ruim.plugin"},{"package":"x;rm"},{}]}"""))
        assertEquals(emptySet<String>(), PluginCatalog.bloqueados("""{"plugins":[],"blocked":[]}"""))
        assertNull(PluginCatalog.bloqueados("""{"plugins":[]}"""))   // sem a lista: não mexe no que já havia
        assertNull(PluginCatalog.bloqueados("lixo"))
    }
}
