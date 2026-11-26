package br.paulo.tvlauncher.observer

import br.paulo.tvlauncher.alarm.NaoPerturbe

/**
 * Sofy (20/11/2026): quando e onde a personagem fala, com que cara e se fala em voz alta. Regra pura (SofyPolicyTest);
 * quem desenha e fala é a parte Android (próximos PRs). Estudo: https://claude.ai/artifact/BbWfArkQXdSPdFb2WSDz5L
 *
 * Por quê: a Sofy (o núcleo do ObserverCore) age sozinha na TV; ação sem explicação parece defeito, e explicação demais
 * vira o clipe do Office. Para quê: a pessoa sabe o que ela fez e por quê, sem ser interrompida à toa. Como: cada recado
 * ([ObserverCore.Note]) passa por [decide], que escolhe o lugar ([Place]), a expressão ([Expression]) e a voz ([Voice]).
 *
 * Regras escolhidas pelo Paulo (20/11/2026, proposta B + acessibilidade):
 *  - por cima de vídeo/música só o problema que a pessoa já sentiu ([ObserverCore.Step.ACTED_FELT]);
 *  - teto: 1 fala por hora e 3 por dia sem a pessoa pedir; o que passar vai para o resumo da manhã;
 *  - madrugada (a mesma do [NaoPerturbe]): nada aparece, tudo vai para o resumo da manhã;
 *  - recusada 2 vezes seguidas = aquele tipo de fala fica calado 7 dias (só no Diário);
 *  - voz desligada de fábrica; liga sozinha quando o leitor de tela (TalkBack) está ligado, e aí quem fala é ele.
 * Recado de pessoa, alarme, campainha e alerta oficial não passam por aqui (não são falas da Sofy).
 */
object SofyPolicy {

    /** Onde o recado aparece. [DIARY] = só no Diário (painel e Configurações), quando a pessoa abre. */
    enum class Place { OVERLAY, HOME_CARD, MORNING, DIARY }

    /** Como a fala chega ao ouvido. [ANNOUNCE] = o leitor de tela da pessoa lê (voz e velocidade dela). */
    enum class Voice { NONE, ANNOUNCE, SPEAK }

    /**
     * O rosto: olhos (e boca opcional) escritos na tela da TV-sofá com símbolos do teclado comum (ASCII), iguais nas 14
     * línguas. Cada um tem o seu movimento (próximo PR); aqui fica só o que a regra precisa escolher.
     */
    enum class Expression(val eyes: String, val mouth: String = "") {
        HAPPY("^ ^"), ATTENTIVE("o o"), SURPRISED("O O", "o"), QUESTION("? ?"), FIXING("> <"), CROSS("\\ /"),
        DELIGHTED("* *"), SAD("T T"), WORRIED("; ;"), CONFUSED("@ @"), SUSPICIOUS("= ="), WINK("^ -"), SLEEPING("- -"),
    }

    const val PER_HOUR = 1
    const val PER_DAY = 3
    const val DISMISSALS_TO_MUTE = 2
    const val MUTE_MS = 7 * 86_400_000L
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L

    /**
     * O momento. [shownAt] = quando a Sofy apareceu sem a pessoa pedir (para o teto); [mutedUntil] = tipo da fala →
     * até quando fica calado; [voiceOn] = a chave "Sofy fala"; [screenReader] = TalkBack ligado.
     */
    data class Now(
        val at: Long, val minuteOfDay: Int, val mediaPlaying: Boolean,
        val screenReader: Boolean, val voiceOn: Boolean,
        val shownAt: List<Long> = emptyList(), val mutedUntil: Map<String, Long> = emptyMap(),
    )

    data class Decision(val place: Place, val expression: Expression, val voice: Voice, val why: String)

    /** A cara que combina com o que a Sofy fez. */
    fun expressionFor(n: ObserverCore.Note): Expression = when {
        n.state == ObserverCore.State.IN_PROGRESS -> Expression.FIXING
        n.state == ObserverCore.State.FAILED || n.step == ObserverCore.Step.FAILED -> Expression.SAD
        n.step == ObserverCore.Step.QUESTION -> Expression.QUESTION
        n.step == ObserverCore.Step.ACTED_FELT || n.step == ObserverCore.Step.ACTED_REVERSIBLE -> Expression.HAPPY
        n.severity == ObserverCore.Severity.CRITICAL -> Expression.SURPRISED
        n.severity == ObserverCore.Severity.WARNING -> Expression.WORRIED
        else -> Expression.ATTENTIVE
    }

    fun night(minuteOfDay: Int): Boolean = NaoPerturbe.madrugada(minuteOfDay)

    /** Ainda cabe uma fala sem a pessoa pedir? (1 na última hora, 3 nas últimas 24 h) */
    fun withinBudget(shownAt: List<Long>, at: Long): Boolean =
        shownAt.count { at - it in 0 until HOUR_MS } < PER_HOUR && shownAt.count { at - it in 0 until DAY_MS } < PER_DAY

    fun muted(typeKey: String, mutedUntil: Map<String, Long>, at: Long): Boolean = (mutedUntil[typeKey] ?: 0L) > at

    /**
     * Vai também ao celular de quem cuida? (Brainstorm 26/11, item 4: o grave nunca saía da TV.) Só o grave (CRITICAL)
     * e o "agi e você vai sentir" (ACTED_FELT), ainda válido, de tipo não calado e fora da madrugada (de madrugada
     * espera: o recado vale 6 h e sai de manhã se ainda for verdade). Pergunta e "só observou" ficam na TV.
     * [sentAt] = quando os últimos foram ao celular: o mesmo teto das falas (1 por hora, 3 por dia).
     */
    fun toPhone(n: ObserverCore.Note, now: Now, sentAt: List<Long>): Boolean =
        (n.severity == ObserverCore.Severity.CRITICAL || n.step == ObserverCore.Step.ACTED_FELT) &&
            n.step != ObserverCore.Step.QUESTION && n.step != ObserverCore.Step.OBSERVED && n.expiresAt > now.at &&
            !muted(n.typeKey, now.mutedUntil, now.at) && !night(now.minuteOfDay) && withinBudget(sentAt, now.at)

    fun decide(n: ObserverCore.Note, now: Now): Decision {
        val face = expressionFor(n)
        val budget = withinBudget(now.shownAt, now.at)
        val (place, why) = when {
            // Pergunta nunca cala (sem resposta, a Sofy não faz): espera a home, de dia.
            n.step == ObserverCore.Step.QUESTION ->
                if (night(now.minuteOfDay)) Place.MORNING to "pergunta de madrugada espera a manhã"
                else Place.HOME_CARD to "pergunta espera a resposta na home"
            !n.canAppear(now.at) -> Place.DIARY to "só observou, já visto ou vencido: fica no Diário"
            muted(n.typeKey, now.mutedUntil, now.at) -> Place.DIARY to "tipo calado depois de 2 recusas"
            night(now.minuteOfDay) -> Place.MORNING to "madrugada: vai para o resumo da manhã"
            n.step == ObserverCore.Step.ACTED_REVERSIBLE -> Place.MORNING to "agiu e dá para desfazer: conta no resumo"
            !budget -> Place.MORNING to "teto de falas atingido"
            n.step == ObserverCore.Step.ACTED_FELT -> Place.OVERLAY to "a pessoa sentiu o problema: conta na hora"
            now.mediaPlaying -> Place.MORNING to "vídeo ou música tocando"
            else -> Place.HOME_CARD to "sugestão ou falha: mostra na home"
        }
        return Decision(place, face, voiceFor(place, now), why)
    }

    /**
     * Ilha da home (24/11/2026, Sofy em todo o sistema): o recado que fica na pílula, o mais velho primeiro. Antes o
     * [Place.HOME_CARD] não tinha lugar na tela e a maior parte das falas sumia. [onIsland] = os que já entraram na Ilha:
     * esses continuam lá (o teto foi gasto quando entraram) até serem vistos, vencerem ou o estado acabar. Pergunta fica
     * de fora até a Ilha saber responder.
     */
    fun islandNote(notes: List<ObserverCore.Note>, now: Now, onIsland: Set<String>): ObserverCore.Note? =
        notes.filter { it.canAppear(now.at) && it.step != ObserverCore.Step.QUESTION }.sortedBy { it.at }
            .firstOrNull { it.id in onIsland || decide(it, now).place == Place.HOME_CARD }

    /** Janela do resumo da manhã na Ilha: das 6h ao meio-dia. */
    const val MORNING_FROM_MIN = 6 * 60
    const val MORNING_UNTIL_MIN = 12 * 60

    /**
     * O resumo da manhã na Ilha (o [Place.MORNING] também não tinha tela): ao acordar a TV de manhã, o que a Sofy viu ou
     * fez de madrugada e o que fez e dá para desfazer, ainda não visto. [minuteOf] = minuto do dia de um instante (fuso
     * da TV). Fora da janela, nada. Só observar não entra (fica no Diário), nem pergunta nem tipo calado.
     */
    fun morningNotes(notes: List<ObserverCore.Note>, now: Now, minuteOf: (Long) -> Int): List<ObserverCore.Note> =
        if (now.minuteOfDay !in MORNING_FROM_MIN until MORNING_UNTIL_MIN) emptyList()
        else notes.filter {
            it.canAppear(now.at) && it.step != ObserverCore.Step.QUESTION &&
                !muted(it.typeKey, now.mutedUntil, now.at) && (night(minuteOf(it.at)) || it.step == ObserverCore.Step.ACTED_REVERSIBLE)
        }.sortedBy { it.at }

    /** Voz só onde a fala aparece na hora, nunca de madrugada nem por cima de vídeo/música. */
    fun voiceFor(place: Place, now: Now): Voice = when {
        place != Place.OVERLAY && place != Place.HOME_CARD -> Voice.NONE
        night(now.minuteOfDay) || now.mediaPlaying -> Voice.NONE
        now.screenReader -> Voice.ANNOUNCE
        now.voiceOn -> Voice.SPEAK
        else -> Voice.NONE
    }

    /** Conta para o teto: só o que apareceu sem a pessoa pedir (a pergunta é necessária, não conta). */
    fun countsForBudget(n: ObserverCore.Note, d: Decision): Boolean =
        n.step != ObserverCore.Step.QUESTION && (d.place == Place.OVERLAY || d.place == Place.HOME_CARD)

    /**
     * A pessoa fechou ([dismissed] = true) ou usou a fala. Devolve as recusas seguidas por tipo e, quando chega a
     * [DISMISSALS_TO_MUTE], até quando o tipo fica calado (o Diário anota que calou).
     */
    data class Dismissals(val streak: Map<String, Int>, val mutedUntil: Map<String, Long>)

    fun onAnswer(d: Dismissals, typeKey: String, dismissed: Boolean, at: Long): Dismissals {
        if (!dismissed) return d.copy(streak = d.streak - typeKey)
        val count = (d.streak[typeKey] ?: 0) + 1
        return if (count >= DISMISSALS_TO_MUTE) Dismissals(d.streak - typeKey, d.mutedUntil + (typeKey to at + MUTE_MS))
        else d.copy(streak = d.streak + (typeKey to count))
    }

    /** Tempo na tela: 6 s mais 1 s a cada 10 palavras (pessoa idosa lê devagar a 3 m). */
    fun secondsOnScreen(text: String): Int = 6 + text.trim().split(Regex("\\s+")).count { it.isNotEmpty() } / 10
}
