package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.ObserverCore
import br.paulo.tvlauncher.observer.ObserverCore.Kind
import br.paulo.tvlauncher.observer.ObserverCore.Severity
import br.paulo.tvlauncher.observer.ObserverCore.State
import br.paulo.tvlauncher.observer.ObserverCore.Step
import br.paulo.tvlauncher.observer.SofyPolicy
import br.paulo.tvlauncher.observer.SofyPolicy.Expression
import br.paulo.tvlauncher.observer.SofyPolicy.Place
import br.paulo.tvlauncher.observer.SofyPolicy.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SofyPolicyTest {
    private val T = 1_800_000_000_000L
    private val NOON = 12 * 60
    private val H = 3_600_000L

    private fun note(step: Step, state: State = State.DONE, sev: Severity = Severity.WARNING, type: String = "no_internet") =
        ObserverCore.Note("x", T, Kind.NO_INTERNET, sev, step, state, "t", "x", emptyList(), null, type, T + H)

    private fun now(minute: Int = NOON, media: Boolean = false, reader: Boolean = false, voice: Boolean = false,
                    shown: List<Long> = emptyList(), muted: Map<String, Long> = emptyMap()) =
        SofyPolicy.Now(T, minute, media, reader, voice, shown, muted)

    @Test fun observedStaysInTheDiary() =
        assertEquals(Place.DIARY, SofyPolicy.decide(note(Step.OBSERVED), now()).place)

    @Test fun feltProblemShowsOverVideo() =
        assertEquals(Place.OVERLAY, SofyPolicy.decide(note(Step.ACTED_FELT), now(media = true)).place)

    @Test fun suggestionDuringVideoWaitsForMorning() =
        assertEquals(Place.MORNING, SofyPolicy.decide(note(Step.SUGGESTS), now(media = true)).place)

    @Test fun suggestionShowsOnHome() =
        assertEquals(Place.HOME_CARD, SofyPolicy.decide(note(Step.SUGGESTS), now()).place)

    @Test fun reversibleActionNeverInterrupts() =
        assertEquals(Place.MORNING, SofyPolicy.decide(note(Step.ACTED_REVERSIBLE), now()).place)

    @Test fun nightGoesToMorningEvenIfFelt() {
        assertEquals(Place.MORNING, SofyPolicy.decide(note(Step.ACTED_FELT), now(minute = 2 * 60)).place)
        assertEquals(Place.MORNING, SofyPolicy.decide(note(Step.QUESTION), now(minute = 23 * 60 + 30)).place)
    }

    @Test fun budgetOnePerHourThreePerDay() {
        assertTrue(SofyPolicy.withinBudget(listOf(T - 2 * H), T))
        assertFalse(SofyPolicy.withinBudget(listOf(T - H / 2), T))
        assertFalse(SofyPolicy.withinBudget(listOf(T - 3 * H, T - 5 * H, T - 7 * H), T))
        assertTrue(SofyPolicy.withinBudget(listOf(T - 25 * H, T - 26 * H, T - 27 * H), T))
        assertEquals(Place.MORNING, SofyPolicy.decide(note(Step.ACTED_FELT), now(shown = listOf(T - 10 * 60_000L))).place)
    }

    @Test fun questionIgnoresBudgetAndMute() {
        val d = SofyPolicy.decide(note(Step.QUESTION), now(shown = listOf(T - 1000), muted = mapOf("no_internet" to T + H)))
        assertEquals(Place.HOME_CARD, d.place)
        assertFalse(SofyPolicy.countsForBudget(note(Step.QUESTION), d))
    }

    @Test fun mutedTypeGoesToDiary() =
        assertEquals(Place.DIARY, SofyPolicy.decide(note(Step.SUGGESTS), now(muted = mapOf("no_internet" to T + H))).place)

    @Test fun twoDismissalsInARowMuteForSevenDays() {
        var d = SofyPolicy.Dismissals(emptyMap(), emptyMap())
        d = SofyPolicy.onAnswer(d, "slow_home", true, T)
        assertTrue(d.mutedUntil.isEmpty())
        d = SofyPolicy.onAnswer(d, "slow_home", true, T)
        assertEquals(T + SofyPolicy.MUTE_MS, d.mutedUntil["slow_home"])
        assertTrue(d.streak.isEmpty())
    }

    @Test fun usingTheNoteResetsTheStreak() {
        var d = SofyPolicy.Dismissals(emptyMap(), emptyMap())
        d = SofyPolicy.onAnswer(d, "slow_home", true, T)
        d = SofyPolicy.onAnswer(d, "slow_home", false, T)
        d = SofyPolicy.onAnswer(d, "slow_home", true, T)
        assertTrue(d.mutedUntil.isEmpty())
    }

    @Test fun voiceOffByDefaultScreenReaderAnnounces() {
        assertEquals(Voice.NONE, SofyPolicy.decide(note(Step.SUGGESTS), now()).voice)
        assertEquals(Voice.ANNOUNCE, SofyPolicy.decide(note(Step.SUGGESTS), now(reader = true, voice = true)).voice)
        assertEquals(Voice.SPEAK, SofyPolicy.decide(note(Step.SUGGESTS), now(voice = true)).voice)
    }

    @Test fun neverSpeaksOverMediaOrAtNight() {
        assertEquals(Voice.NONE, SofyPolicy.decide(note(Step.ACTED_FELT), now(media = true, voice = true)).voice)
        assertEquals(Voice.NONE, SofyPolicy.voiceFor(Place.HOME_CARD, now(minute = 3 * 60, reader = true)))
        assertEquals(Voice.NONE, SofyPolicy.voiceFor(Place.MORNING, now(voice = true)))
    }

    @Test fun faceMatchesWhatSheDid() {
        assertEquals(Expression.FIXING, SofyPolicy.expressionFor(note(Step.ACTED_FELT, State.IN_PROGRESS)))
        assertEquals(Expression.SAD, SofyPolicy.expressionFor(note(Step.ACTED_FELT, State.FAILED)))
        assertEquals(Expression.QUESTION, SofyPolicy.expressionFor(note(Step.QUESTION)))
        assertEquals(Expression.HAPPY, SofyPolicy.expressionFor(note(Step.ACTED_FELT)))
        assertEquals(Expression.SURPRISED, SofyPolicy.expressionFor(note(Step.SUGGESTS, sev = Severity.CRITICAL)))
    }

    @Test fun facesAreAsciiSoAllLanguagesDrawThem() =
        Expression.entries.forEach { e -> assertTrue(e.name, (e.eyes + e.mouth).all { it.code in 0x20..0x7E }) }

    @Test fun timeOnScreenGrowsWithText() {
        assertEquals(6, SofyPolicy.secondsOnScreen("Wi-Fi de volta"))
        assertEquals(7, SofyPolicy.secondsOnScreen("um dois três quatro cinco seis sete oito nove dez onze"))
    }

    // ---------------------------------------------------------------- Ilha (24/11/2026)

    private fun n(id: String, step: Step, at: Long = T, type: String = id, seen: Boolean = false) =
        ObserverCore.Note(id, at, Kind.NO_INTERNET, Severity.WARNING, step, State.DONE, "t", "x", emptyList(), null, type, T + H, seen)

    @Test fun islandShowsTheHomeNoteOldestFirst() {
        val l = listOf(n("b", Step.SUGGESTS, T - 1), n("a", Step.SUGGESTS, T - 2), n("q", Step.QUESTION, T - 3))
        assertEquals("a", SofyPolicy.islandNote(l, now(), emptySet())?.id)
        // Pergunta não vai para a Ilha (ainda não dá para responder lá); visto e vencido também não.
        assertEquals(null, SofyPolicy.islandNote(listOf(n("q", Step.QUESTION), n("s", Step.SUGGESTS, seen = true)), now(), emptySet()))
    }

    @Test fun islandKeepsWhatIsAlreadyThereWhenTheBudgetFills() {
        val cheio = now(shown = listOf(T - 60_000L))
        assertEquals(null, SofyPolicy.islandNote(listOf(n("a", Step.SUGGESTS)), cheio, emptySet()))
        assertEquals("a", SofyPolicy.islandNote(listOf(n("a", Step.SUGGESTS)), cheio, setOf("a"))?.id)
    }

    @Test fun morningSummaryOnlyInTheMorningWithWhatHappenedAtNight() {
        val madrugada = T - 5 * H
        val noite = mapOf(madrugada to 3 * 60, T - H to 11 * 60)
        val minuto: (Long) -> Int = { noite[it] ?: NOON }
        val l = listOf(n("noite", Step.ACTED_FELT, madrugada), n("dia", Step.SUGGESTS, T - H), n("desfez", Step.ACTED_REVERSIBLE, T - H),
            n("so_viu", Step.OBSERVED, madrugada))
        assertEquals(listOf("noite", "desfez"), SofyPolicy.morningNotes(l, now(minute = 8 * 60), minuto).map { it.id })
        assertEquals(emptyList<String>(), SofyPolicy.morningNotes(l, now(minute = 14 * 60), minuto).map { it.id })
        assertEquals(emptyList<String>(), SofyPolicy.morningNotes(l, now(minute = 8 * 60, muted = mapOf("noite" to T + H, "desfez" to T + H)), minuto).map { it.id })
    }

    // Brainstorm 26/11, item 4: o grave e o "agi e você vai sentir" também vão ao celular, com teto e fora da madrugada.
    @Test fun criticalGoesToPhone() =
        assertTrue(SofyPolicy.toPhone(note(Step.SUGGESTS, sev = Severity.CRITICAL), now(), emptyList()))

    @Test fun feltActionGoesToPhone() =
        assertTrue(SofyPolicy.toPhone(note(Step.ACTED_FELT), now(), emptyList()))

    @Test fun warningSuggestionStaysOnTheTv() =
        assertFalse(SofyPolicy.toPhone(note(Step.SUGGESTS), now(), emptyList()))

    @Test fun observedOrQuestionNeverGoToPhone() {
        assertFalse(SofyPolicy.toPhone(note(Step.OBSERVED, sev = Severity.CRITICAL), now(), emptyList()))
        assertFalse(SofyPolicy.toPhone(note(Step.QUESTION, sev = Severity.CRITICAL), now(), emptyList()))
    }

    @Test fun phoneWaitsAtNightAndRespectsMuteAndBudget() {
        val grave = note(Step.SUGGESTS, sev = Severity.CRITICAL)
        assertFalse(SofyPolicy.toPhone(grave, now(minute = 2 * 60), emptyList()))
        assertFalse(SofyPolicy.toPhone(grave, now(muted = mapOf("no_internet" to T + H)), emptyList()))
        assertFalse(SofyPolicy.toPhone(grave, now(), listOf(T - 10 * 60_000L)))           // 1 na última hora
        assertFalse(SofyPolicy.toPhone(grave, now(), listOf(T - 2 * H, T - 3 * H, T - 4 * H)))   // 3 no dia
        assertTrue(SofyPolicy.toPhone(grave, now(), listOf(T - 2 * H)))
    }

    @Test fun expiredNoteDoesNotGoToPhone() =
        assertFalse(SofyPolicy.toPhone(note(Step.ACTED_FELT).copy(expiresAt = T - 1), now(), emptyList()))
}
