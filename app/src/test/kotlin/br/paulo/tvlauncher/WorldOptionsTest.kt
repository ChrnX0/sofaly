package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.AppFolders
import br.paulo.tvlauncher.home.AppNames
import br.paulo.tvlauncher.home.CruzLogica.Categoria
import br.paulo.tvlauncher.home.WorldOptions.Options
import br.paulo.tvlauncher.home.WorldOptions.Rules
import br.paulo.tvlauncher.home.WorldOptions.Sort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Base das opções dos mundos, das pastas e dos nomes trocados (22/11/2026). */
class WorldOptionsTest {
    private val uso = listOf("yt", "nf", "prime", "globo")   // já na ordem de uso
    private val nomes = mapOf("yt" to "YouTube", "nf" to "Netflix", "prime" to "Prime Video", "globo" to "Globoplay")

    @Test fun usoNaoMexe() = assertEquals(uso, Rules.arrange(uso, Sort.USAGE, nomes))

    @Test fun alfabeticoComFixadoNoTopo() =
        assertEquals(listOf("prime", "globo", "nf", "yt"), Rules.arrange(uso, Sort.AZ, nomes, pinned = setOf("prime")))

    @Test fun alfabeticoIgnoraAcentoECaixa() =
        assertEquals(listOf("b", "a"), Rules.arrange(listOf("a", "b"), Sort.AZ, mapOf("a" to "Órbita", "b" to "abacate")))

    @Test fun aMaoComAppNovoNoFimEAppSumido() =
        assertEquals(listOf("globo", "yt", "nf", "prime"), Rules.arrange(uso, Sort.MANUAL, manual = listOf("globo", "apagado", "yt")))

    @Test fun moverNasPontas() {
        assertEquals(listOf("b", "a", "c"), Rules.move(listOf("a", "b", "c"), "b", -1))
        assertEquals(listOf("a", "b", "c"), Rules.move(listOf("a", "b", "c"), "a", -1))
        assertEquals(listOf("a", "c", "b"), Rules.move(listOf("a", "b", "c"), "b", +1))
    }

    @Test fun guardarELer() {
        val o = Options(Sort.MANUAL, listOf("a", "b"), "Filmes", hidden = true)
        assertEquals(o, Rules.read(Rules.write(o)))
        assertEquals(Options(), Rules.read(null))
        assertEquals(Options(), Rules.read("{estragado"))
    }

    @Test fun soAssistirOuvirJogarSomem() {
        val esc = Options(hidden = true)
        assertFalse(Rules.visible(Categoria.ASSISTIR, esc))
        assertTrue("Ferramentas leva às Configurações", Rules.visible(Categoria.FERRAMENTAS, esc))
        assertTrue("Aplicativos leva a Todos os apps", Rules.visible(Categoria.APLICATIVOS, esc))
    }

    @Test fun pastas() {
        var l = AppFolders.Rules.create(emptyList(), "  Crianças  ", listOf("yt", "yt"))
        l = AppFolders.Rules.create(l, "Esportes")
        l = AppFolders.Rules.create(l, "   ")   // nome vazio não cria
        assertEquals(listOf("f1", "f2"), l.map { it.id })
        assertEquals(listOf("yt"), l[0].apps)
        l = AppFolders.Rules.toggle(l, "f2", "globo")
        l = AppFolders.Rules.pin(l, "f2", "assistir")
        l = AppFolders.Rules.rename(l, "f1", "Crianças da casa")
        assertEquals(l, AppFolders.Rules.read(AppFolders.Rules.write(l)))
        assertEquals(listOf("f2"), AppFolders.Rules.pinned(l, "assistir").map { it.id })
        l = AppFolders.Rules.delete(l, "f1")
        assertEquals("id novo não reaproveita outro em uso", "f1", AppFolders.Rules.create(l, "Nova").last().id)
        assertEquals(emptyList<String>(), AppFolders.Rules.prune(l, setOf("yt")).first().apps)
        assertEquals(emptyList<AppFolders.Folder>(), AppFolders.Rules.read("[{\"id\":\"\",\"name\":\"x\"}]"))
    }

    @Test fun nomeTrocado() {
        assertEquals("Amazon", AppNames.clean("  Amazon "))
        assertNull(AppNames.clean("   "))
        assertEquals(AppNames.MAX, AppNames.clean("x".repeat(60))!!.length)
    }
}
