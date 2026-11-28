package br.paulo.tvlauncher

import br.paulo.tvlauncher.privilege.CompilarAot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompilarAotTest {
    @Test fun versaoNovaCompila() = assertTrue(CompilarAot.deveCompilar(200, 100, 0))
    @Test fun versaoJaFeitaNao() = assertFalse(CompilarAot.deveCompilar(200, 200, 0))
    @Test fun desisteDepoisDoLimite() = assertFalse(CompilarAot.deveCompilar(200, 100, CompilarAot.MAX_TENTATIVAS))
    @Test fun sucessoSoComLinhaSuccess() {
        assertTrue(CompilarAot.deuCerto("Success\n"))
        assertFalse(CompilarAot.deuCerto("Failure: package not found"))
        assertFalse(CompilarAot.deuCerto(null))
    }

    // Compilada no protetor (13/11/2026): reinicia no próximo apagar, uma vez por versão.
    @Test fun compiladaSemReiniciarReinicia() = assertTrue(CompilarAot.deveReiniciar(200, 200, 100))
    @Test fun jaReiniciadaNao() = assertFalse(CompilarAot.deveReiniciar(200, 200, 200))
    @Test fun naoCompiladaNaoReinicia() = assertFalse(CompilarAot.deveReiniciar(200, 100, 0))

    // Saiu do protetor (28/11/2026, SLOW_HOME na Sala): compilada e pendente reinicia já, com a tela acesa.
    @Test fun saiuDoProtetorReinicia() =
        assertTrue(CompilarAot.deveReiniciarAoAcordar(200, 200, 100, false, true, false, true, false))
    @Test fun saiuDoProtetorSemPendenciaNao() {
        assertFalse(CompilarAot.deveReiniciarAoAcordar(200, 200, 200, false, true, false, true, false))   // já reiniciou
        assertFalse(CompilarAot.deveReiniciarAoAcordar(200, 100, 0, false, true, false, true, false))     // não compilou
    }
    @Test fun saiuDoProtetorComFreios() {
        assertFalse(CompilarAot.deveReiniciarAoAcordar(200, 200, 100, true, true, false, true, false))    // compilando
        assertFalse(CompilarAot.deveReiniciarAoAcordar(200, 200, 100, false, false, false, true, false))  // tela apagou: o apagar cuida
        assertFalse(CompilarAot.deveReiniciarAoAcordar(200, 200, 100, false, true, true, true, false))    // som tocando
        assertFalse(CompilarAot.deveReiniciarAoAcordar(200, 200, 100, false, true, false, false, false))  // despertador ou outro app
        assertFalse(CompilarAot.deveReiniciarAoAcordar(200, 200, 100, false, true, false, true, true))    // conferência de telas
    }
}
