package br.paulo.tvlauncher

import br.paulo.tvlauncher.listen.PodcastBusca
import br.paulo.tvlauncher.player.LegendaBaixar
import org.junit.Assert.assertEquals
import org.junit.Test

/** Revisor 26/11: a tela diz o motivo real (cota, servidor fora), não "nada encontrado" nem o texto cru da API. */
class MotivoDaFalhaTest {
    @Test fun buscaDePodcast() {
        assertEquals(PodcastBusca.Falha.NENHUMA, PodcastBusca.falhaDe(200))
        assertEquals(PodcastBusca.Falha.COTA, PodcastBusca.falhaDe(429))
        assertEquals(PodcastBusca.Falha.FORA, PodcastBusca.falhaDe(503))
    }

    @Test fun legendas() {
        assertEquals(1, LegendaBaixar.textoDoCodigo(429))
        assertEquals(2, LegendaBaixar.textoDoCodigo(401))
        assertEquals(3, LegendaBaixar.textoDoCodigo(503))
        assertEquals(0, LegendaBaixar.textoDoCodigo(400))
    }
}
