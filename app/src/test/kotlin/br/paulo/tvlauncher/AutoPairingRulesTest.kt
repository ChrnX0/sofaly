package br.paulo.tvlauncher

import br.paulo.tvlauncher.privilege.AutoPairingRules
import br.paulo.tvlauncher.privilege.AutoPairingRules.Decision
import br.paulo.tvlauncher.privilege.AutoPairingRules.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O autorreparo do pareamento (25/11/2026): quando tentar, e como achar o caminho na tela. */
class AutoPairingRulesTest {
    private val now = 1_800_000_000_000L
    private val h = 3_600_000L
    private fun st(
        enabled: Boolean = true, expected: Boolean = true, down: Long? = 25 * 60_000L, saver: Boolean = true,
        a11y: Boolean = true, wifi: Boolean = true, attempts: List<Long> = emptyList(),
    ) = AutoPairingRules.State(enabled, expected, down, saver, a11y, wifi, attempts, now)

    @Test fun triesOnlyWhenShellLostOnAPairedTvWithNobodyWatching() {
        assertEquals(Decision.TRY, AutoPairingRules.decide(st()))
        assertEquals(Decision.NO, AutoPairingRules.decide(st(enabled = false)))      // Play
        assertEquals(Decision.NO, AutoPairingRules.decide(st(expected = false)))     // nunca teve shell: não é conserto
        assertEquals(Decision.NO, AutoPairingRules.decide(st(a11y = false)))
        assertEquals(Decision.NO, AutoPairingRules.decide(st(down = null)))
        assertEquals(Decision.NO, AutoPairingRules.decide(st(down = 5 * 60_000L)))   // soluço
        assertEquals(Decision.NO, AutoPairingRules.decide(st(wifi = false)))
        assertEquals(Decision.WAIT, AutoPairingRules.decide(st(saver = false)))      // alguém pode estar vendo
    }

    @Test fun spacesAndLimitsAttempts() {
        assertEquals(Decision.WAIT, AutoPairingRules.decide(st(attempts = listOf(now - h))))
        assertEquals(Decision.TRY, AutoPairingRules.decide(st(attempts = listOf(now - 4 * h))))
        val four = listOf(now - 20 * h, now - 15 * h, now - 10 * h, now - 4 * h)
        assertEquals(Decision.WAIT, AutoPairingRules.decide(st(attempts = four)))
        assertFalse(AutoPairingRules.stillTrying(st(attempts = four)))              // aí o aviso pode aparecer
        assertTrue(AutoPairingRules.stillTrying(st(attempts = listOf(now - 25 * h) + four.drop(1))))
    }

    @Test fun findsTheStepFromTheScreenTexts() {
        val pair = AutoPairingRules.PAIR_FALLBACK
        assertEquals(Step.OPEN_WIRELESS, AutoPairingRules.step(listOf("Opções do desenvolvedor", "Depuração USB"), pair))
        assertEquals(Step.OPEN_PAIRING, AutoPairingRules.step(listOf("Depuração por Wi-Fi", "Parear o dispositivo com um código de pareamento"), pair))
        assertEquals(Step.READ_CODE, AutoPairingRules.step(listOf("Código de pareamento Wi-Fi", "482 913", "192.168.1.153:37121"), pair))
    }

    @Test fun labelsMatchWithoutAccentsOrCase() {
        assertTrue(AutoPairingRules.matches("Depuracao por wi-fi", "Depuração por Wi-Fi"))
        assertTrue(AutoPairingRules.matches("Pair device with pairing code…", "Pair device with pairing code"))
        assertFalse(AutoPairingRules.matches("Depuração USB", "Depuração por Wi-Fi"))
        assertFalse(AutoPairingRules.matches("qualquer", ""))
    }
}
