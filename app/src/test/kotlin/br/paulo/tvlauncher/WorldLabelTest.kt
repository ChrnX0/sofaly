package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.WorldLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/** A conta que prende a hora no meio da tela ([WorldLabel.deslocamento]). */
class WorldLabelTest {
    @Test fun horaVaiParaOMeio() {
        // Tela 1920, pílula 600 centrada (esquerda 660); a hora em 400..500 dentro dela: meio da hora em 1110 -> volta 150.
        assertEquals(-150f, WorldLabel.deslocamento(1920, 660, 600, 400, 100, 64), 0.01f)
    }

    @Test fun naoPassaDaBorda() {
        // Pílula larga: o deslocamento pedido a levaria para fora; para na borda de 64.
        assertEquals(-36f, WorldLabel.deslocamento(1920, 100, 1720, 1700, 100, 64), 0.01f)
    }

    @Test fun pilulaMaiorQueATelaFicaCentrada() {
        assertEquals(0f, WorldLabel.deslocamento(1920, -40, 2000, 900, 100, 64), 0.01f)
    }
}
