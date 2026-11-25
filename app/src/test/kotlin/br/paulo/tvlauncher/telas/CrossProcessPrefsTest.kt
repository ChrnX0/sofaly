package br.paulo.tvlauncher.telas

import android.content.Context
import br.paulo.tvlauncher.CrossProcessPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.File

/**
 * O `:player` vê o que as Configurações mudaram (CrossProcessPrefs, 25/11/2026). Sem dois processos no teste, o "outro
 * processo" é imitado como ele age de verdade: regrava o XML no disco. Só roda com -Ptelas (usa o Android do Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class CrossProcessPrefsTest {
    @Test fun releOQueOutroProcessoGravou() {
        val ctx = RuntimeEnvironment.getApplication()
        val p = ctx.getSharedPreferences("extras_teste", Context.MODE_PRIVATE)
        p.edit().putInt("telas", 0).commit()
        assertEquals(0, p.getInt("telas", -1))
        // O outro processo grava o arquivo (como o SharedPreferencesImpl dele faz) e ESTE processo não fica sabendo.
        val xml = File(ctx.applicationInfo.dataDir, "shared_prefs/extras_teste.xml")
        Thread.sleep(1100)   // a data do arquivo tem de mudar (alguns sistemas de arquivo guardam só segundos)
        xml.writeText("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <int name=\"telas\" value=\"2\" />\n</map>\n")
        assertEquals("sem a releitura o valor fica velho (é o defeito)", 0, ctx.getSharedPreferences("extras_teste", Context.MODE_PRIVATE).getInt("telas", -1))
        CrossProcessPrefs.refresh(ctx, here = true)
        assertEquals("depois da releitura, a mesma instância vê o valor novo", 2, p.getInt("telas", -1))
    }

    @Test fun soArquivosPequenosDeAjuste() {
        val dir = File(RuntimeEnvironment.getApplication().cacheDir, "prefs-lista").apply { mkdirs() }
        File(dir, "a.xml").writeText("<map/>")
        File(dir, "grande.xml").writeBytes(ByteArray((CrossProcessPrefs.MAX_BYTES + 1).toInt()))
        File(dir, "b.bak").writeText("x")
        assertEquals(listOf("a"), CrossProcessPrefs.settingsFiles(dir))
        assertTrue(CrossProcessPrefs.settingsFiles(File(dir, "nao-existe")).isEmpty())
        assertFalse("fora do :player não faz nada", CrossProcessPrefs.inPlayer)
    }
}
