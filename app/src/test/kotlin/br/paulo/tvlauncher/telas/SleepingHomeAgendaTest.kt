package br.paulo.tvlauncher.telas

import android.view.ViewGroup
import br.paulo.tvlauncher.screensaver.SleepingHome
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * A linha da agenda do descanso (itens sem ícone) monta sem travar. Antes do conserto (23/11/2026) o item fazia
 * getChildAt(0).alpha mesmo sem ícone: NPE no fio principal que derrubava o launcher inteiro ao abrir o descanso.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class SleepingHomeAgendaTest {
    @Test fun linhaDaAgendaSemIcone() {
        val s = SleepingHome(RuntimeEnvironment.getApplication())
        val linha = s.linhaItens(listOf(Triple("", "Dentista", "amanhã 9:00"), Triple("", "Aniversário da Ana", "sábado"))) as ViewGroup
        assertEquals(3, linha.childCount)   // 2 itens + 1 espaço para completar as 3 colunas
    }

    /** Teste 100% (23/11/2026): às 17:23 um compromisso de amanhã às 14:00 saía "Hoje, 14:00". */
    @Test fun amanhaNaoViraHoje() {
        val s = SleepingHome(RuntimeEnvironment.getApplication())
        val sp = java.util.TimeZone.getTimeZone("America/Sao_Paulo")
        fun ms(d: Int, h: Int, m: Int) = java.util.Calendar.getInstance(sp).apply { clear(); set(2026, 10, d, h, m) }.timeInMillis
        assertEquals(1, s.diasAte(ms(23, 17, 23), ms(24, 14, 0), sp))
        assertEquals(0, s.diasAte(ms(23, 17, 23), ms(23, 23, 59), sp))
        assertEquals(1, s.diasAte(ms(23, 23, 59), ms(24, 0, 1), sp))
        assertEquals(2, s.diasAte(ms(23, 8, 0), ms(25, 7, 0), sp))
    }
}
