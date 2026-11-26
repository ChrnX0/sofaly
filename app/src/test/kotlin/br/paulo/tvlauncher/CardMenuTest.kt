package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.CardMenu
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Abrir ao ligar" do menu do card (bloco 4 do clone do Projectivy, 26/11/2026). */
class CardMenuTest {
    @Test fun abreUmaVezPorBoot() {
        assertTrue("boot novo abre", CardMenu.shouldOpen("com.app", 12, 11))
        assertFalse("mesmo boot não abre de novo", CardMenu.shouldOpen("com.app", 12, 12))
        assertFalse("sem app escolhido", CardMenu.shouldOpen(null, 12, 11))
        assertFalse("contagem ilegível não abre (seria a cada volta à HOME)", CardMenu.shouldOpen("com.app", -1, 11))
    }
}
