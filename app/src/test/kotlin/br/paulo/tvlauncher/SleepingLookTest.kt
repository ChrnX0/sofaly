package br.paulo.tvlauncher

import br.paulo.tvlauncher.screensaver.ReguaConta
import br.paulo.tvlauncher.screensaver.SleepingLook
import br.paulo.tvlauncher.screensaver.SleepingLookNomes
import org.junit.Assert.assertEquals
import org.junit.Test

/** Jeito dos widgets do descanso (25/11/2026): valores gravados, lado do cartão e a régua de 12 horas. */
class SleepingLookTest {

    @Test fun nomeGravadoViraOpcao() {
        assertEquals(SleepingLook.Clock.RING, SleepingLook.deNome("ring", SleepingLook.Clock.THIN))
        assertEquals(SleepingLook.Clock.THIN, SleepingLook.deNome(null, SleepingLook.Clock.THIN))
        // Nome de uma versão futura (ou lixo) cai no padrão em vez de quebrar o descanso.
        assertEquals(SleepingLook.Place.TOP_START, SleepingLook.deNome("galaxy", SleepingLook.Place.TOP_START))
    }

    @Test fun cadaOpcaoTemNome() {
        assertEquals(SleepingLook.Clock.entries.size, SleepingLookNomes.CLOCK.size)
        assertEquals(SleepingLook.Card.entries.size, SleepingLookNomes.CARD.size)
        assertEquals(SleepingLook.Place.entries.size, SleepingLookNomes.PLACE.size)
    }

    @Test fun ladoDoCartao() {
        // Faixa e cartão vivo ficam do lado da hora; a pilha vai para o outro (no centro, para o fim).
        assertEquals(-1, SleepingLook.cardSide(SleepingLook.Card.CARD, SleepingLook.Place.TOP_START))
        assertEquals(1, SleepingLook.cardSide(SleepingLook.Card.STACK, SleepingLook.Place.TOP_START))
        assertEquals(-1, SleepingLook.cardSide(SleepingLook.Card.STACK, SleepingLook.Place.BOTTOM_END))
        assertEquals(1, SleepingLook.cardSide(SleepingLook.Card.STACK, SleepingLook.Place.TOP_CENTER))
    }

    @Test fun reguaDeChuva() {
        // 13 pontos = 12 horas; chuva nas horas 0-2 e 10-12 (>= 40%).
        val c = listOf(70, 60, 45, 30, 10, 0, 0, 0, 0, 5, 40, 80, 90)
        val f = ReguaConta.faixasDeChuva(c, 40)
        assertEquals(2, f.size)
        assertEquals(0f, f[0].first, 1e-4f); assertEquals(3f / 12, f[0].second, 1e-4f)
        assertEquals(10f / 12, f[1].first, 1e-4f); assertEquals(1f, f[1].second, 1e-4f)
        assertEquals(0, ReguaConta.faixasDeChuva(List(13) { 0 }, 40).size)
        // Uma hora só de chuva ainda aparece (meia hora de largura no mínimo).
        val uma = ReguaConta.faixasDeChuva(listOf(0, 0, 50, 0, 0), 40).single()
        assertEquals(0.5f, uma.first, 1e-4f); assertEquals(0.75f, uma.second, 1e-4f)
    }
}
