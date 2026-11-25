package br.paulo.tvlauncher.telas

import br.paulo.tvlauncher.ConfirmacoesTv
import br.paulo.tvlauncher.panel.PainelControle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * Segurança (revisor, 24/11/2026): com a pergunta "Permitir?" aberta na TV, tecla e texto vindos do painel (ou do MCP)
 * são recusados, para ninguém de fora aprovar o próprio pedido. Liga/desliga e volume continuam.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
class PerguntaTravaTesteTest {
    @Test fun painelNaoRespondeAPerguntaDaTv() {
        val app = RuntimeEnvironment.getApplication()
        val t = Thread { ConfirmacoesTv.perguntarSempre(app, "Permitir?", "PC da visita quer os arquivos", "PC") }
        t.start()
        for (i in 0 until 100) { if (ConfirmacoesTv.atualId() != null) break; Thread.sleep(20) }
        val id = assertNotNull(ConfirmacoesTv.atualId()).let { ConfirmacoesTv.atualId()!! }
        val trava = PainelControle.perguntaNaTela()
        assertNotNull(trava)
        for (k in listOf("ok", "direita", "esquerda", "cima", "baixo", "voltar", "inicio", "entrar"))
            assertEquals(k, trava, PainelControle.tecla(app, k))
        assertEquals(trava, PainelControle.digitar(app, "sim"))
        assertNotEquals(trava, PainelControle.tecla(app, "volume+"))
        ConfirmacoesTv.responder(id, false)
        t.join(5_000)
        assertEquals(null, PainelControle.perguntaNaTela())
    }
}
