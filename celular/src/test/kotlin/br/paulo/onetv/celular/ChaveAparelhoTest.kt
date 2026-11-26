package br.paulo.onetv.celular

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaveAparelhoTest {
    @Test fun juntarESepararIdaEVolta() {
        val iv = ByteArray(12) { it.toByte() }
        val ct = ByteArray(80) { (it * 3).toByte() }
        val (a, b) = ChaveAparelho.separar(ChaveAparelho.juntar(iv, ct))!!
        assertArrayEquals(iv, a); assertArrayEquals(ct, b)
        assertNull(ChaveAparelho.separar(ByteArray(27)))
    }

    @Test fun soChaveNoFormatoDaTv() {
        assertTrue(ChaveAparelho.valida("a".repeat(64)))
        assertFalse(ChaveAparelho.valida("A".repeat(64)))
        assertFalse(ChaveAparelho.valida("a".repeat(63)))
        assertFalse(ChaveAparelho.valida(null))
        assertFalse(ChaveAparelho.valida("g".repeat(64)))
    }

    @Test fun reentraSoNo401ComChaveEForaDasRotasDaChave() {
        assertTrue(ApiTv.deveReentrar(401, true, "/api/tela/tecla"))
        assertFalse(ApiTv.deveReentrar(401, false, "/api/tela/tecla"))
        assertFalse(ApiTv.deveReentrar(403, true, "/api/tela/tecla"))
        assertFalse("sem laço: a própria entrada não reentra", ApiTv.deveReentrar(401, true, "/api/aparelho/entrar"))
    }

    @Test fun pedeChaveSoDepoisDeDarCerto() {
        assertTrue(ApiTv.devePedirChave(200, false, "/api/estado", false))
        assertFalse(ApiTv.devePedirChave(200, true, "/api/estado", false))
        assertFalse(ApiTv.devePedirChave(401, false, "/api/estado", false))
        assertFalse("TV que recusou não é perguntada de novo", ApiTv.devePedirChave(200, false, "/api/estado", true))
        assertFalse(ApiTv.devePedirChave(200, false, "/api/aparelho/emitir", false))
    }

    @Test fun semPinAindaTentaDepois() {
        assertTrue("TV pediu o PIN: tenta de novo depois", ApiTv.esperaPin(403, true))
        assertFalse("403 sem precisaPin: desiste nesta sessão", ApiTv.esperaPin(403, false))
        assertFalse(ApiTv.esperaPin(404, false))
    }
}
