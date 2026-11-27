package br.paulo.onetv.celular

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * TV fora do ar (Caça 27/11/2026): o painel no celular mostrava a página de erro do próprio WebView ("Webpage not
 * available", o endereço do Tailscale e "net::ERR_NAME_NOT_RESOLVED"). Agora, quando a casa e o Tailscale falham (ou
 * não há Tailscale), a PainelActivity cobre o WebView com um aviso do app e "Tentar de novo".
 */
class PainelSemTvTest {
    private val raiz = listOf(File(".."), File(".")).first { File(it, "celular/src/main/kotlin").isDirectory }
    private val fonte = File(raiz, "celular/src/main/kotlin/br/paulo/onetv/celular/PainelActivity.kt").readText()

    private fun corpo(nome: String): String {
        val i = fonte.indexOf("fun $nome(")
        assertTrue("PainelActivity sem $nome", i >= 0)
        return fonte.substring(i, fonte.indexOf("\n        }\n", i))
    }

    @Test fun erroDaPaginaPrincipalMostraOAvisoDoApp() {
        val erro = corpo("onReceivedError")
        assertTrue("subquadros não contam", erro.contains("isForMainFrame"))
        assertTrue("primeiro tenta o Tailscale", erro.contains("enderecoFora"))
        assertTrue("depois mostra o aviso do app", erro.contains("semTv.visibility = View.VISIBLE"))
        // Um "?: return" deixava a página de erro do WebView na tela quando a TV não mandou o nome do Tailscale.
        assertFalse("sem saída que deixe a página de erro do WebView", erro.contains("?: return"))
    }

    @Test fun avisoTemTextoEBotaoDoApp() {
        assertTrue(fonte.contains("R.string.cel_ctrl_sem_tv"))
        assertTrue(fonte.contains("R.string.cel_enviar_de_novo"))
        val tentar = fonte.substring(fonte.indexOf("fun tentarDeNovo("), fonte.indexOf("fun daTv("))
        assertTrue("tentar de novo volta a tentar o Tailscale", tentar.contains("tentouFora = false"))
        assertTrue("e esconde o aviso", tentar.contains("semTv.visibility = View.GONE"))
    }
}
