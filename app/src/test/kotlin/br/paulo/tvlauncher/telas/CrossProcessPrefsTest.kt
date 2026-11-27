package br.paulo.tvlauncher.telas

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ProviderInfo
import android.os.Looper
import br.paulo.tvlauncher.CrossProcessPrefs
import br.paulo.tvlauncher.PrefsProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.File

/**
 * Preferências entre o processo principal e o `:player` pelo dono (CrossProcessPrefs.kt, 27/11/2026). Sem dois
 * processos no teste, o "principal" é o SharedPreferences normal (o dono, que grava no disco) e o "`:player`" é o
 * RemotePrefs, com uma cópia local separada que imita a memória do outro processo. Só roda com -Ptelas.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class CrossProcessPrefsTest {
    private lateinit var ctx: Context
    private lateinit var dono: SharedPreferences

    @Before fun montar() {
        ctx = RuntimeEnvironment.getApplication()
        CrossProcessPrefs.resetForTest()
        Robolectric.buildContentProvider(PrefsProvider::class.java)
            .create(ProviderInfo().apply { authority = CrossProcessPrefs.authority(ctx) })
        dono = ctx.getSharedPreferences(NOME, Context.MODE_PRIVATE)
        dono.edit().clear().commit()
    }
    @After fun limpar() = CrossProcessPrefs.resetForTest()

    /** O `:player`: lê e grava pelo dono; a cópia "local" (outro arquivo) nunca deve ser usada para este tamanho. */
    private fun player(): SharedPreferences =
        CrossProcessPrefs.prefs(ctx, NOME, { ctx.getSharedPreferences("$NOME-local", Context.MODE_PRIVATE) }, here = true)

    @Test fun naoPerdeGravacaoDeNenhumDosDois() {
        dono.edit().putInt("telas", 1).commit()                       // Configurações, processo principal
        val p = player()
        assertEquals(1, p.getInt("telas", -1))
        assertTrue(p.edit().putLong("atraso:x", 250L).commit())       // o :player salva o atraso da legenda
        dono.edit().putInt("telas", 2).commit()                       // as Configurações mudam de novo
        // O defeito antigo: a cópia velha de um processo, gravada inteira, apagava a gravação do outro.
        assertEquals("a gravação do :player ficou no dono", 250L, dono.getLong("atraso:x", -1))
        assertEquals(2, dono.getInt("telas", -1))
        assertEquals("o :player vê o que o principal mudou, sem reabrir", 2, p.getInt("telas", -1))
        assertEquals(250L, p.getLong("atraso:x", -1))
        assertTrue("nada foi para a cópia local", ctx.getSharedPreferences("$NOME-local", Context.MODE_PRIVATE).all.isEmpty())
        // E no disco: um leitor novo do arquivo vê as duas gravações.
        val xml = File(ctx.applicationInfo.dataDir, "shared_prefs/$NOME.xml").readText()
        assertTrue(xml, "atraso:x" in xml && "\"telas\" value=\"2\"" in xml)
    }

    @Test fun applyChegaNaOrdemEQuemGravouLeNaHora() {
        val p = player()
        p.edit().putInt("n", 1).apply()
        assertEquals("quem gravou lê o valor novo na hora", 1, p.getInt("n", -1))
        p.edit().putInt("n", 2).apply()
        p.edit().putString("s", "a").apply()
        assertTrue(p.edit().putBoolean("fim", true).commit())        // commit espera os apply de antes
        assertEquals(2, dono.getInt("n", -1))
        assertEquals("a", dono.getString("s", null))
        assertTrue(dono.getBoolean("fim", false))
    }

    @Test fun removerLimparEConjuntos() {
        dono.edit().putString("a", "1").putStringSet("set", setOf("x", "y")).putFloat("f", 1.5f).commit()
        val p = player()
        assertEquals(setOf("x", "y"), p.getStringSet("set", null))
        assertEquals(1.5f, p.getFloat("f", 0f))
        p.edit().remove("a").putStringSet("set", setOf("z")).commit()
        assertNull(dono.getString("a", null))
        assertEquals(setOf("z"), dono.getStringSet("set", null))
        assertFalse(p.contains("a"))
        p.edit().clear().putInt("so", 7).commit()
        assertEquals(mapOf("so" to 7), dono.all)
        assertEquals(mapOf("so" to 7), p.all)
    }

    @Test fun ouvinteDoPlayerSabeQuandoOPrincipalMuda() {
        val p = player()
        p.getInt("telas", 0)
        val vistos = ArrayList<String?>()
        val ouvinte = SharedPreferences.OnSharedPreferenceChangeListener { _, k -> vistos += k }
        p.registerOnSharedPreferenceChangeListener(ouvinte)
        dono.edit().putInt("telas", 3).commit()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("telas"), vistos)
        assertEquals(3, p.getInt("telas", -1))
        p.unregisterOnSharedPreferenceChangeListener(ouvinte)
    }

    @Test fun wrapSoMudaNoPlayer() {
        assertTrue("fora do :player é o próprio contexto", CrossProcessPrefs.wrap(ctx, here = false) === ctx)
        val w = CrossProcessPrefs.wrap(ctx, here = true)
        dono.edit().putInt("k", 9).commit()
        assertEquals(9, w.getSharedPreferences(NOME, Context.MODE_PRIVATE).getInt("k", -1))
        assertFalse("fora do :player não faz nada", CrossProcessPrefs.inPlayer)
    }

    @Test fun arquivoGrandeFicaLocal() {
        val grande = "x".repeat((CrossProcessPrefs.MAX_BYTES + 10).toInt())
        dono.edit().putString("cache", grande).commit()
        val local = ctx.getSharedPreferences("$NOME-local", Context.MODE_PRIVATE)
        local.edit().putString("cache", "local").commit()
        assertEquals("cache grande não viaja entre processos", "local", player().getString("cache", null))
    }

    @Test fun soArquivosPequenosDeAjuste() {
        val dir = File(RuntimeEnvironment.getApplication().cacheDir, "prefs-lista").apply { mkdirs() }
        File(dir, "a.xml").writeText("<map/>")
        File(dir, "grande.xml").writeBytes(ByteArray((CrossProcessPrefs.MAX_BYTES + 1).toInt()))
        File(dir, "b.bak").writeText("x")
        assertEquals(listOf("a"), CrossProcessPrefs.settingsFiles(dir))
        assertTrue(CrossProcessPrefs.settingsFiles(File(dir, "nao-existe")).isEmpty())
    }

    private companion object { const val NOME = "prefs_teste" }
}
