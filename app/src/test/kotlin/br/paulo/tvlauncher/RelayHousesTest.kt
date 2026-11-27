package br.paulo.tvlauncher

import br.paulo.tvlauncher.relay.RelayCrypto
import br.paulo.tvlauncher.relay.RelayEnvelope
import br.paulo.tvlauncher.relay.RelayHouses
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TV com TV de outra casa pelo código de 8 letras (Assistir junto sem Tailscale). */
class RelayHousesTest {
    private val H = RelayHouses
    private class Tv(n: Int) {
        val ed = ByteArray(32) { (n * 7 + it).toByte() }
        val x = ByteArray(32) { (n * 11 + it).toByte() }
        val edPub = RelayCrypto.edPublica(ed)
        val xPub = RelayCrypto.xPublica(x)
        val id = RelayCrypto.idDe(edPub)
    }
    private val a = Tv(1); private val b = Tv(2)

    @Test fun codigoDigitadoComoAPessoaDigita() {
        assertEquals("ABCDEFGH", H.codigo("abcd efgh"))
        assertEquals("ABCDEFGH", H.codigo(" ABCD-EFGH "))
        assertEquals("OIAB2345", H.codigo("01ab2345"))   // 0 e 1 não existem no alfabeto: viram O e I
        assertNull(H.codigo("ABCDEFG"))
        assertNull(H.codigo("ABCDEFGHI"))
        assertNull(H.codigo("ABCD#FGH"))
        assertNull(H.codigo(null))
        assertEquals("ABCD EFGH", H.mostrar("ABCDEFGH"))
    }

    @Test fun apresentacaoIdaEVolta() {
        val p = H.lerApresentacao(H.apresentacao(b.id, b.edPub, b.xPub, "Casa da Vó"), b.id)!!
        assertEquals(b.id, p.id); assertEquals("Casa da Vó", p.nome)
        assertTrue(p.xPub.contentEquals(b.xPub))
    }

    @Test fun apresentacaoDeOutroRemetenteNaoVale() {
        // O servidor diz que veio de A (assinatura), mas a apresentação é de B: alguém repassando chaves alheias.
        assertNull(H.lerApresentacao(H.apresentacao(b.id, b.edPub, b.xPub, "B"), a.id))
    }

    @Test fun lixoNaoEApresentacao() {
        assertNull(H.lerApresentacao(ByteArray(0), a.id))
        assertNull(H.lerApresentacao(byteArrayOf(1, 2, 3), a.id))           // envelope cifrado começa com 1
        assertNull(H.lerApresentacao(byteArrayOf(2) + "{}".toByteArray(), a.id))
        assertNull(H.lerApresentacao(ByteArray(5000) { 2 }, a.id))
        assertEquals("TV", H.lerApresentacao(H.apresentacao(a.id, a.edPub, a.xPub, "\u0007 "), a.id)!!.nome)
    }

    @Test fun numerosIguaisNasDuasTvs() {
        val n = H.conferencia(a.xPub, b.xPub)
        assertEquals(n, H.conferencia(b.xPub, a.xPub))
        assertTrue(Regex("^[0-9]{4}$").matches(n))
        // Servidor trocando a chave de B por outra: o número muda (quem confere pelo telefone percebe).
        assertNotEquals(n, H.conferencia(a.xPub, Tv(3).xPub))
    }

    @Test fun soAsRotasDaSessaoJunto() {
        val t = "0123456789abcdef0123456789abcdef"
        for (c in listOf("/junto/convite", "/junto/$t/evento", "/junto/$t/sair")) {
            val m = H.junto(c, JSONObject().put("seq", 1), 5)!!
            assertEquals(c, H.lerJunto(m)!!.first)
        }
        assertNull(H.junto("/junto/$t/estado", null, 5))          // a conferência de volta não passa pelo relay
        assertEquals("/continuar", H.lerJunto(H.junto("/continuar", JSONObject().put("titulo", "x"), 5)!!)!!.first)   // Continuar em outra TV
        assertNull(H.junto("/continuar/0123456789abcdef0123456789abcdef/estado", null, 5))   // o conferir de volta não passa
        assertNull(H.junto("/api/tela/tecla", null, 5))
        assertNull(H.junto("/junto/convite", JSONObject().put("x", "y".repeat(5000)), 5))
        // Mensagem de outro tipo (tecla de celular) não é Sessão junto.
        assertNull(H.lerJunto(RelayEnvelope.Mensagem("abcdefgh", 5, "key", JSONObject().put("key", "HOME"))))
        assertNull(H.lerJunto(RelayEnvelope.Mensagem("abcdefgh", 5, "junto", JSONObject().put("path", "/x").put("body", JSONObject()))))
        assertNotNull(H.junto("/junto/convite", null, 5))
    }

    @Test fun celularApresentaUmaTvAOutra() {
        val m = H.casa(b.id, b.xPub, "Casa\u0007 da Vó", 7)!!
        val c = H.lerCasa(m)!!
        assertEquals(b.id, c.id); assertEquals("Casa da Vó", c.nome); assertTrue(c.xPub.contentEquals(b.xPub))
        assertNull(H.casa("curto", b.xPub, "x", 7))
        assertNull(H.casa(b.id, ByteArray(31), "x", 7))
        // Outro tipo, id ruim ou chave ruim: não é apresentação de casa.
        assertNull(H.lerCasa(RelayEnvelope.Mensagem("abcdefgh", 5, "key", m.dados)))
        assertNull(H.lerCasa(RelayEnvelope.Mensagem("abcdefgh", 5, H.CASA, JSONObject(m.dados.toString()).put("id", "x"))))
        assertNull(H.lerCasa(RelayEnvelope.Mensagem("abcdefgh", 5, H.CASA, JSONObject(m.dados.toString()).put("x", "AAAA"))))
        // A junto não aceita esse tipo, e a casa não aceita junto.
        assertNull(H.lerJunto(m))
        assertNull(H.lerCasa(H.junto("/junto/convite", null, 5)!!))
    }
}
