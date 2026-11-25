package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyBus
import br.paulo.tvlauncher.observer.SofyHabits
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SofyHabitsTest {
    private val tz = TimeZone.getTimeZone("America/Sao_Paulo")
    /** 2026-11-02 (segunda) + [dia] dias, na hora e minuto dados, no fuso da casa. */
    private fun t(dia: Int, h: Int, m: Int = 0): Long = Calendar.getInstance(tz).apply {
        clear(); set(2026, Calendar.NOVEMBER, 2, h, m); add(Calendar.DAY_OF_YEAR, dia)
    }.timeInMillis

    private fun horas(vararg p: Pair<Int, Double>) = List(24) { h -> p.firstOrNull { it.first == h }?.second ?: 0.0 }

    @Test fun deCostumeSoComFolga() {
        val f = mapOf("noticias" to horas(7 to 6.0), "youtube" to horas(7 to 1.0, 20 to 9.0))
        assertEquals("noticias", SofyHabits.usual(f, 7))
        assertEquals("youtube", SofyHabits.usual(f, 20))
        assertNull(SofyHabits.usual(f, 13))   // nada nessa hora
        // Empate largo: nenhum é "o de costume".
        assertNull(SofyHabits.usual(mapOf("a" to horas(9 to 3.0), "b" to horas(9 to 3.0), "c" to horas(9 to 3.0)), 9))
    }

    @Test fun quemEstaVendo() {
        val ana = mapOf("novela" to horas(21 to 20.0), "noticias" to horas(7 to 10.0))
        val bia = mapOf("jogo" to horas(21 to 15.0), "desenho" to horas(16 to 15.0))
        val todos = mapOf("ana" to ana, "bia" to bia)
        // Perfil da Ana ativo, mas abriram o jogo e o desenho: cara da Bia.
        assertEquals("bia", SofyHabits.whoIsWatching(todos, "ana", listOf(21 to "jogo", 21 to "desenho")))
        // Abriram a novela: é a Ana mesmo.
        assertNull(SofyHabits.whoIsWatching(todos, "ana", listOf(21 to "novela", 21 to "noticias")))
        // Um app só não basta; ficha pequena não vale.
        assertNull(SofyHabits.whoIsWatching(todos, "ana", listOf(21 to "jogo")))
        assertNull(SofyHabits.whoIsWatching(mapOf("ana" to ana, "bia" to mapOf("jogo" to horas(21 to 3.0))), "ana", listOf(21 to "jogo", 21 to "jogo")))
    }

    @Test fun rotinaProposta() {
        // Notícias às 7h05-7h25 em 5 dias úteis; YouTube em horas soltas.
        val abre = (0 until 5).map { t(it, 7, 5 + it * 5) to "noticias" } + (0 until 5).map { t(it, 7, 40) to "noticias" } +
            listOf(t(0, 10) to "youtube", t(2, 15) to "youtube", t(4, 20) to "youtube", t(5, 22) to "youtube")
        val r = SofyHabits.routine(abre, tz)!!
        assertEquals("noticias", r.pkg)
        assertEquals(7 * 60 + 5, r.minuteOfDay)
        assertEquals(5, r.days)
        assertEquals(0b0111110, r.weekdays)   // segunda a sexta
        assertNull(SofyHabits.routine(abre, tz, skip = setOf("noticias")))
        assertNull(SofyHabits.routine(abre.take(3), tz))   // 3 dias não é rotina
    }

    @Test fun dormiuVendo() {
        val offs = listOf(t(0, 23, 50), t(1, 1, 30), t(1, 23, 10), t(3, 14, 0))   // 14h não conta; 1h30 de terça = noite de segunda
        assertEquals(2, SofyHabits.dozedNights(offs, tz))
        assertEquals(3, SofyHabits.dozedNights(offs + t(4, 2, 0), tz))
        assertEquals(120, SofyHabits.shorterOff(180, listOf(0, 30, 60, 90, 120, 180, 240)))
        assertEquals(60, SofyHabits.shorterOff(90, listOf(0, 30, 60, 90, 120, 180, 240)))
        assertNull(SofyHabits.shorterOff(60, listOf(0, 30, 60, 90)))
    }

    @Test fun volumeDaNoite() {
        val p = SofyHabits.Person()
        for (i in 0 until 5) { for (h in 9..20) SofyHabits.noteVolume(p, h, 30); for (h in listOf(23, 0, 1)) SofyHabits.noteVolume(p, h, 70) }
        assertEquals(30, SofyHabits.nightCeiling(p))
        val calmo = SofyHabits.Person()
        for (i in 0 until 5) { for (h in 9..20) SofyHabits.noteVolume(calmo, h, 30); for (h in listOf(23, 0, 1)) SofyHabits.noteVolume(calmo, h, 35) }
        assertNull(SofyHabits.nightCeiling(calmo))
        assertNull(SofyHabits.nightCeiling(SofyHabits.Person()))   // sem amostras
        val volta = SofyHabits.fromJson(SofyHabits.toJson(mapOf("_" to p)))["_"]!!
        assertEquals(30, SofyHabits.nightCeiling(volta))
        assertTrue(SofyHabits.profileId("ana@x").startsWith("p")); assertEquals("_", SofyHabits.profileId(""))
    }

    @Test fun alarmeQueSeAjusta() {
        fun ev(dia: Int, h: Int, m: Int, k: String, id: Int = 1) = SofyBus.Event(t(dia, h, m), SofyBus.Source.ALARM, k, detail = id.toString())
        val e = ArrayList<SofyBus.Event>()
        for (d in 0 until 5) {
            e += ev(d, 6, 30, "rang")
            if (d != 2) { e += ev(d, 6, 31, "snoozed"); if (d % 2 == 0) e += ev(d, 6, 41, "snoozed") }
        }
        e += ev(0, 8, 0, "rang", 2)
        // 4 dias com soneca, média 1,5 soneca de 10 min = 15 min.
        assertEquals(1 to 15, SofyHabits.snoozeShift(e, tz))
        // Só 2 dias com soneca: nada.
        assertNull(SofyHabits.snoozeShift(e.filter { !(it.kind == "snoozed" && it.at > t(1, 12)) }, tz))
    }
}
