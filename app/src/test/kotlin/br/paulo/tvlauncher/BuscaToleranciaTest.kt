package br.paulo.tvlauncher

import br.paulo.tvlauncher.search.BuscaUnica
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Letra errada só quando nada bate certo (teste 100%, 24/11/2026: "duna" trazia "Pato Donald" antes do filme Duna). */
class BuscaToleranciaTest {
    private val canais = listOf("[24h] pato donald", "[24h] show da luna", "dinamo moscow x krasnodar")

    @Test fun comFilmeCertoNaoAceitaLetraErrada() {
        assertTrue(BuscaUnica.algumBateCerto("Duna", (canais + "duna parte dois").asSequence()))
    }

    @Test fun semNadaCertoAceitaLetraErrada() {
        assertFalse(BuscaUnica.algumBateCerto("duna", canais.asSequence()))
        // e com a tolerância os parecidos ainda aparecem ("netflx" acha Netflix)
        assertTrue(BuscaUnica.pontosNorm("netflix", BuscaUnica.palavras("netflx"), tolerante = true) != null)
    }

    @Test fun termoVazioNaoBate() {
        assertFalse(BuscaUnica.algumBateCerto("  ", canais.asSequence()))
    }
}
