package br.paulo.onetv.celular

import br.paulo.onetv.celular.Compartilhar.Tipo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Item 16 (Brainstorm 26/11/2026): foto, vídeo e arquivo pelo "Compartilhar" do celular. */
class CompartilharTest {
    @Test fun tipoPeloMimeOuPelaExtensao() {
        assertEquals(Tipo.IMAGEM, Compartilhar.tipo("image/heic", "x.heic"))
        assertEquals(Tipo.VIDEO, Compartilhar.tipo("video/mp4", "viagem"))
        assertEquals(Tipo.AUDIO, Compartilhar.tipo("AUDIO/OGG", "nota"))
        assertEquals(Tipo.VIDEO, Compartilhar.tipo(null, "Viagem.MKV"))
        assertEquals(Tipo.AUDIO, Compartilhar.tipo("application/octet-stream", "audio.opus"))
        assertEquals(Tipo.OUTRO, Compartilhar.tipo("application/pdf", "conta.pdf"))
    }

    @Test fun soTocaOQueATvAbre() {
        assertTrue(Compartilhar.tocavel(Tipo.VIDEO, "a.mp4"))
        assertFalse(Compartilhar.tocavel(Tipo.VIDEO, "a.3gp"))   // a TV responderia 415: só guarda
        assertTrue(Compartilhar.tocavel(Tipo.AUDIO, "a.opus"))
        assertFalse(Compartilhar.tocavel(Tipo.OUTRO, "a.mp4"))
    }

    @Test fun pastaPorTipo() {
        assertEquals("Pictures", Compartilhar.pasta(Tipo.IMAGEM))
        assertEquals("Movies", Compartilhar.pasta(Tipo.VIDEO))
        assertEquals("Movies", Compartilhar.pasta(Tipo.AUDIO))
        assertEquals("Download", Compartilhar.pasta(Tipo.OUTRO))
    }

    @Test fun nomeQueATvAceita() {
        assertEquals("a_b_c.jpg", Compartilhar.nomeSeguro("a/b\\c.jpg", "image/jpeg"))
        assertEquals("IMG_2031.jpg", Compartilhar.nomeSeguro("IMG_2031", "image/jpeg"))
        assertEquals("arquivo.mp4", Compartilhar.nomeSeguro(null, "video/mp4"))
        assertEquals("arquivo", Compartilhar.nomeSeguro("   ", null))
        assertEquals("oculto.txt", Compartilhar.nomeSeguro("..oculto.txt", null))   // sem ".." nem arquivo escondido
        assertEquals("x_y", Compartilhar.nomeSeguro("x\u0000y", null))
        val longo = Compartilhar.nomeSeguro("a".repeat(300) + ".mp4", null)
        assertEquals(200, longo.length); assertTrue(longo.endsWith(".mp4"))
    }

    @Test fun nomeLivreNaoApagaOQueJaEsta() {
        assertEquals("foto.jpg", Compartilhar.nomeLivre("foto.jpg", setOf("outra.jpg")))
        assertEquals("foto (2).jpg", Compartilhar.nomeLivre("foto.jpg", setOf("foto.jpg")))
        assertEquals("foto (3).jpg", Compartilhar.nomeLivre("foto.jpg", setOf("foto.jpg", "foto (2).jpg")))
        assertEquals("LEIA (2)", Compartilhar.nomeLivre("LEIA", setOf("LEIA")))
    }

    @Test fun fotoReduzidaA960() {
        assertEquals(960 to 540, Compartilhar.reduzido(3840, 2160))
        assertEquals(720 to 960, Compartilhar.reduzido(3000, 4000))
        assertEquals(800 to 600, Compartilhar.reduzido(800, 600))   // pequena fica como está
        assertEquals(1, Compartilhar.amostra(1200, 900))
        assertEquals(8, Compartilhar.amostra(8000, 6000))   // lê 1000 px, depois reduz para 960
        assertTrue(8000 / Compartilhar.amostra(8000, 6000) >= 960)
    }
}
