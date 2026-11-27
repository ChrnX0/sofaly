package br.paulo.tvlauncher.player

import br.paulo.tvlauncher.CodeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * O ML Kit só pode aparecer no motor (MlKitSubtitles.kt): assim ele sai do APK para uma parte separada sem mexer no
 * resto (refatoração G). E o nome que SubtitleEngines procura tem de ser o do motor de verdade.
 */
class SubtitleEngineGateTest {
    @Test fun mlKitSoNoMotor() {
        val usam = CodeSource.all().filter { f -> f.readLines().any { it.startsWith("import com.google.mlkit") } }.map { it.name }.toList()
        assertEquals(listOf("MlKitSubtitles.kt"), usam)
    }

    @Test fun nomeProcuradoEOMotor() {
        assertEquals(MlKitSubtitles::class.java.name, SubtitleEngines.CLASS)
        assertSame(MlKitSubtitles, SubtitleEngines.provider)
    }
}
