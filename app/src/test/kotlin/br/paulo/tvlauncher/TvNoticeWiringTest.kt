package br.paulo.tvlauncher

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O aviso dos módulos de área (TvNotice, no :core) só aparece se o app ligar o AvisoTv nele na partida do processo.
 * Sem essa linha nada quebra na compilação: os avisos dos módulos sumiriam calados. Por isso o teste lê o código.
 */
class TvNoticeWiringTest {
    @Test fun appLigaOAvisoNoNucleo() {
        val app = CodeSource.file("LauncherApp.kt").readText()
        assertTrue("LauncherApp.onCreate liga o TvNotice", app.contains("TvNotice.display = AvisoTv.comoTvNotice"))
    }

    @Test fun nucleoNaoDependeDoApp() {
        val core = CodeSource.file("TvNotice.kt").readText()
        assertTrue("TvNotice mora no :core", CodeSource.file("TvNotice.kt").path.replace('\\', '/').contains("/core/src/main/"))
        val codigo = core.lines().map { it.trim() }.filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
        assertTrue("sem AvisoTv no código do :core (só nos comentários)", codigo.none { it.contains("AvisoTv") })
    }
}
