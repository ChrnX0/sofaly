package br.paulo.tvlauncher

import br.paulo.tvlauncher.backup.BackupCompleto
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Revisor 26/11: restaurar não reabre acesso revogado nem apaga o PIN de hoje; numa TV zerada, o do backup entra. */
class BackupAcessoTest {
    private fun s(v: String) = JSONObject().put("t", "s").put("v", v)
    private val chaves = BackupCompleto.ACESSO_DA_TV.getValue("painel_seguro")

    @Test fun pinDeHojeVenceODoBackup() {
        val backup = JSONObject().put("pin_resumo", s("velho")).put("chaves_acesso", s("revogada")).put("ligado", JSONObject().put("t", "b").put("v", true))
        val hoje = JSONObject().put("pin_resumo", s("novo"))
        BackupCompleto.mesclarAcesso(backup, hoje, chaves)
        assertEquals("novo", backup.getJSONObject("pin_resumo").getString("v"))
        assertFalse(backup.has("chaves_acesso"))   // revogada hoje: não volta
        assertTrue(backup.has("ligado"))           // o resto do backup segue
    }

    @Test fun backupSemPinNaoApagaOPinDeHoje() {
        val backup = JSONObject()
        BackupCompleto.mesclarAcesso(backup, JSONObject().put("pin_resumo", s("atual")), chaves)
        assertEquals("atual", backup.getJSONObject("pin_resumo").getString("v"))
    }

    @Test fun tvZeradaAdotaODoBackup() {
        val backup = JSONObject().put("pin_resumo", s("do backup"))
        BackupCompleto.mesclarAcesso(backup, JSONObject(), chaves)
        assertEquals("do backup", backup.getJSONObject("pin_resumo").getString("v"))
    }
}
