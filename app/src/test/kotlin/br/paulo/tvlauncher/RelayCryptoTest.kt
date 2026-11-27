package br.paulo.tvlauncher

import br.paulo.tvlauncher.relay.RelayCrypto
import br.paulo.tvlauncher.relay.RelayEnvelope
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Relay do api.sofaly.tv (decisão A, 26/11/2026). Os vetores "do servidor" foram gerados rodando o próprio
 * server/api/src/rules.js no Node (idFromKey, signedText e crypto.sign Ed25519): se o app divergir, o servidor recusa.
 */
class RelayCryptoTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private val seed = ByteArray(32) { (it + 1).toByte() }   // bytes 1..32

    @Test fun chaveEIdIguaisAoServidor() {
        val pub = RelayCrypto.edPublica(seed)
        assertEquals("79b5562e8fe654f94078b112e8a98ba7901f853ae695bed7e0e3910bad049664", RelayCrypto.hex(pub))
        assertEquals("mw3am46w5weex4a4fqrc", RelayCrypto.idDe(pub))
        assertTrue(RelayCrypto.ID_RE.matches(RelayCrypto.idDe(pub)))
    }

    @Test fun assinaturaIgualAoServidor() {
        val corpo = """{"ids":["a"]}""".toByteArray()
        val h = RelayCrypto.cabecalhos(seed, "POST", "/v1/inbox/ack", 1795000000L, corpo)
        assertEquals("YOaxJqesqGX8vxBTQ_i7taZ5yZJNdnGVoIv3ufDL9MpVM59frSSCQujbwRqPcC3XaP43jmdrv8dSMwqKAL8CBQ", h["X-Sofaly-Signature"])
        assertEquals("1795000000", h["X-Sofaly-Time"])
        assertEquals(RelayCrypto.b64url(RelayCrypto.edPublica(seed)), h["X-Sofaly-Key"])
        // E confere do lado de cá; mudar o corpo invalida.
        val texto = RelayCrypto.textoAssinado("POST", "/v1/inbox/ack", 1795000000L, corpo)
        assertTrue(RelayCrypto.edConferir(RelayCrypto.edPublica(seed), texto, RelayCrypto.deB64url(h["X-Sofaly-Signature"]!!)))
        val outro = RelayCrypto.textoAssinado("POST", "/v1/inbox/ack", 1795000000L, "{}".toByteArray())
        assertFalse(RelayCrypto.edConferir(RelayCrypto.edPublica(seed), outro, RelayCrypto.deB64url(h["X-Sofaly-Signature"]!!)))
    }

    @Test fun x25519IgualAoNode() {
        val a = ByteArray(32) { 7 }; val b = ByteArray(32) { 9 }
        assertEquals("57db4b359f23ae5e146e4e2512056704722506348c150c14753d0c933d04d421", RelayCrypto.hex(RelayCrypto.xPublica(b)))
        val s1 = RelayCrypto.xSegredo(a, RelayCrypto.xPublica(b))
        assertEquals("2ffe325b2a26611bf1911325d4a62b33749b8f350d0e88ba91e316acaae66554", RelayCrypto.hex(s1))
        assertArrayEquals(s1, RelayCrypto.xSegredo(b, RelayCrypto.xPublica(a)))
    }

    @Test fun base32ComoORfc4648() {
        // Vetores do RFC 4648 (em minúsculas e sem "=").
        assertEquals("", RelayCrypto.base32(ByteArray(0)))
        assertEquals("my", RelayCrypto.base32("f".toByteArray()))
        assertEquals("mzxw6ytboi", RelayCrypto.base32("foobar".toByteArray()))
        assertEquals("mzxw6ytb", RelayCrypto.base32("fooba".toByteArray()))
    }

    // ---------------------------------------------------------------- envelopes

    private class Aparelho(n: Int) {
        val ed = ByteArray(32) { (n * 3 + it).toByte() }
        val x = ByteArray(32) { (n * 5 + it).toByte() }
        val id = RelayCrypto.idDe(RelayCrypto.edPublica(ed))
        val xPub = RelayCrypto.xPublica(x)
    }
    private val tv = Aparelho(1)
    private val celular = Aparelho(2)
    private val intruso = Aparelho(3)
    private val agora = 1_795_000_000_000L
    private fun msg(t: Long = agora) = RelayEnvelope.Mensagem("abcdefghijklmnop", t, "remote_key", JSONObject().put("key", "HOME"))
    private val pareados = mapOf(celular.id to celular.xPub)

    @Test fun envelopeIdaEVolta() {
        val env = RelayEnvelope.fechar(celular.id, celular.x, tv.id, tv.xPub, msg())
        assertEquals(celular.id, RelayEnvelope.remetente(env))
        val a = RelayEnvelope.abrir(tv.id, tv.x, env, agora) { pareados[it] }!!
        assertEquals(celular.id, a.remetente)
        assertEquals("remote_key", a.mensagem.tipo)
        assertEquals("HOME", a.mensagem.dados.getString("key"))
        assertEquals("abcdefghijklmnop", a.mensagem.id)
    }

    @Test fun cadaEnvelopeSaiDiferente() {
        val e1 = RelayEnvelope.fechar(celular.id, celular.x, tv.id, tv.xPub, msg())
        val e2 = RelayEnvelope.fechar(celular.id, celular.x, tv.id, tv.xPub, msg())
        assertFalse(e1.contentEquals(e2))
    }

    @Test fun remetenteNaoPareadoNaoAbre() {
        val env = RelayEnvelope.fechar(intruso.id, intruso.x, tv.id, tv.xPub, msg())
        assertNull(RelayEnvelope.abrir(tv.id, tv.x, env, agora) { pareados[it] })
    }

    @Test fun intrusoFingindoSerOCelularNaoAbre() {
        // O intruso põe o id do celular no cabeçalho, mas não tem a X25519 dele: a chave não bate.
        val env = RelayEnvelope.fechar(celular.id, intruso.x, tv.id, tv.xPub, msg())
        assertNull(RelayEnvelope.abrir(tv.id, tv.x, env, agora) { pareados[it] })
    }

    @Test fun mexidoNoCaminhoNaoAbre() {
        val env = RelayEnvelope.fechar(celular.id, celular.x, tv.id, tv.xPub, msg())
        for (i in listOf(0, 5, 40, 60, env.size - 1)) {
            val m = env.copyOf(); m[i] = (m[i].toInt() xor 1).toByte()
            assertNull("byte $i", RelayEnvelope.abrir(tv.id, tv.x, m, agora) { pareados[it] })
        }
    }

    @Test fun paraOutraTvNaoAbre() {
        val outraTv = Aparelho(4)
        val env = RelayEnvelope.fechar(celular.id, celular.x, outraTv.id, outraTv.xPub, msg())
        assertNull(RelayEnvelope.abrir(tv.id, tv.x, env, agora) { pareados[it] })
        // Nem a outra TV abre se o servidor entregou na caixa errada com o id trocado: o destino está no AAD.
        val env2 = RelayEnvelope.fechar(celular.id, celular.x, tv.id, tv.xPub, msg())
        assertNull(RelayEnvelope.abrir(outraTv.id, outraTv.x, env2, agora) { pareados[it] })
    }

    @Test fun veloOuDoFuturoNaoAbre() {
        val velho = RelayEnvelope.fechar(celular.id, celular.x, tv.id, tv.xPub, msg(agora - RelayEnvelope.VALIDADE_MS - 1))
        assertNull(RelayEnvelope.abrir(tv.id, tv.x, velho, agora) { pareados[it] })
        val futuro = RelayEnvelope.fechar(celular.id, celular.x, tv.id, tv.xPub, msg(agora + RelayEnvelope.FOLGA_MS + 1))
        assertNull(RelayEnvelope.abrir(tv.id, tv.x, futuro, agora) { pareados[it] })
        val adiantado = RelayEnvelope.fechar(celular.id, celular.x, tv.id, tv.xPub, msg(agora + 60_000))
        assertNotNull(RelayEnvelope.abrir(tv.id, tv.x, adiantado, agora) { pareados[it] })
    }

    @Test fun lixoNaoQuebra() {
        for (b in listOf(ByteArray(0), ByteArray(10), ByteArray(100) { 1 }, ByteArray(RelayEnvelope.MAX_BYTES + 1)))
            assertNull(RelayEnvelope.abrir(tv.id, tv.x, b, agora) { pareados[it] })
    }

    @Test fun repetidoSoUmaVez() {
        val v = RelayEnvelope.Vistos(max = 3)
        assertTrue(v.primeiraVez("a", agora, agora))
        assertFalse(v.primeiraVez("a", agora, agora))
        // Sobrevive a reiniciar o app.
        val v2 = RelayEnvelope.Vistos(); v2.carregar(v.paraJson())
        assertFalse(v2.primeiraVez("a", agora, agora))
        // Passou da validade: esquece (o abrir já recusa envelope tão velho).
        assertTrue(v2.primeiraVez("a", agora, agora + RelayEnvelope.VALIDADE_MS + RelayEnvelope.FOLGA_MS + 1))
        // Limite de tamanho: o mais antigo sai.
        for (id in listOf("b", "c", "d")) v.primeiraVez(id, agora, agora)
        assertTrue(v.primeiraVez("a", agora, agora))
    }

    @Test fun caixaPostalComoOServidorEntrega() {
        val env = RelayEnvelope.fechar(celular.id, celular.x, tv.id, tv.xPub, msg())
        // Formato de server/api/src/mailbox.js pending(): base64 padrão em "data".
        val json = JSONObject().put("envelopes", org.json.JSONArray()
            .put(JSONObject().put("id", "srv1").put("from", celular.id).put("created", 1).put("data", RelayCrypto.b64(env)))
            .put(JSONObject().put("id", "srv2").put("from", intruso.id).put("created", 2).put("data", RelayCrypto.b64(env)))
            .put(JSONObject().put("id", "srv3").put("from", celular.id).put("data", "%%%"))
            .put(JSONObject().put("from", celular.id))).toString()
        val l = RelayEnvelope.lerPendentes(json)
        assertEquals(listOf("srv1", "srv2", "srv3"), l.map { it.caixaId })   // sem id não dá nem para confirmar: sai
        assertTrue(RelayEnvelope.remetenteConfere(l[0]))
        assertFalse(RelayEnvelope.remetenteConfere(l[1]))   // servidor viu outro remetente
        assertFalse(RelayEnvelope.remetenteConfere(l[2]))   // dados estragados, mas continua na lista para o ack
        assertTrue(RelayEnvelope.lerPendentes("lixo").isEmpty())
        assertTrue(RelayEnvelope.lerPendentes(null).isEmpty())
    }

    @Test fun novoIdTemTamanhoValido() {
        val id = RelayEnvelope.novoId()
        assertTrue(id.length in 8..64)
        assertFalse(id == RelayEnvelope.novoId())
    }
}
