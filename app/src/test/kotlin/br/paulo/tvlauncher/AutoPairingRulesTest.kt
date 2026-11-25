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

    @Test fun backsOffByItselfAndNeverStopsTrying() {
        // Recuo: 15 min depois da 1ª, 30 depois da 2ª, 1 h depois da 3ª... até 6 h. Sem teto por dia.
        val m = 60_000L
        assertEquals(Decision.WAIT, AutoPairingRules.decide(st(attempts = listOf(now - 10 * m))))
        assertEquals(Decision.TRY, AutoPairingRules.decide(st(attempts = listOf(now - 16 * m))))
        assertEquals(Decision.WAIT, AutoPairingRules.decide(st(attempts = listOf(now - 40 * m, now - 20 * m))))
        assertEquals(Decision.TRY, AutoPairingRules.decide(st(attempts = listOf(now - 60 * m, now - 31 * m))))
        assertEquals(AutoPairingRules.MAX_GAP_MS, AutoPairingRules.gapMs(30))
        val sete = (1..7).map { now - 23 * h + it * h }          // 7 no dia, a última há 16 h (> 6 h): tenta de novo
        assertEquals(Decision.TRY, AutoPairingRules.decide(st(attempts = sete)))
        assertTrue(AutoPairingRules.why(st(attempts = listOf(now - 10 * m))).startsWith("recuo: 1 tentativa(s) hoje, próxima em 5 min"))
        // O aviso só pode aparecer depois de várias tentativas sem sucesso; ela segue tentando.
        val quatro = listOf(now - 20 * h, now - 15 * h, now - 10 * h, now - 7 * h)
        assertFalse(AutoPairingRules.stillTrying(st(attempts = quatro)))
        assertEquals(Decision.TRY, AutoPairingRules.decide(st(attempts = quatro)))
        assertTrue(AutoPairingRules.stillTrying(st(attempts = listOf(now - 25 * h) + quatro.drop(1))))
    }

    @Test fun logsWhereItGotLostWithoutNumbers() {
        assertEquals(listOf("open_wireless@x x3", "rolou", "open_pairing@x"), AutoPairingRules.squash(listOf("open_wireless@x", "open_wireless@x", "open_wireless@x", "rolou", "open_pairing@x")))
        val t = AutoPairingRules.screenSummary(listOf("Depuração sem fio", "192.168.1.153:37123", "Código 123456", "Depuração sem fio"))
        assertEquals("Depuração sem fio | ###.###.#.###:##### | Código ######", t)
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
