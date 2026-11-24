package br.paulo.tvlauncher

import br.paulo.tvlauncher.backup.BackupCompleto
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Revisão de lógica (24/11/2026): uma chave que mudou de tipo entre versões (Int virou Long) não pode voltar de um
 * backup antigo com o tipo velho: derrubaria quem a lê. Ela fica de fora; as outras passam.
 */
class BackupCompletoTiposTest {
    private fun item(t: String, v: Any) = JSONObject().put("t", t).put("v", v)

    @Test fun chaveQueMudouDeTipoFicaDeFora() {
        val p = JSONObject().put("volume", item("i", 5)).put("nome", item("s", "Sala"))
        val r = BackupCompleto.conferirTipos("extras", p, mapOf("volume" to 5L, "nome" to "Quarto")) {}
        assertFalse(r.has("volume"))
        assertTrue(r.has("nome"))
    }

    @Test fun chaveNovaOuDoMesmoTipoPassa() {
        val p = JSONObject().put("volume", item("l", 5L)).put("nova", item("b", true))
        val r = BackupCompleto.conferirTipos("extras", p, mapOf("volume" to 7L)) {}
        assertTrue(r.has("volume") && r.has("nova"))
    }
}
