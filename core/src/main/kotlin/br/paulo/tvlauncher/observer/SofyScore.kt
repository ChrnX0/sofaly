package br.paulo.tvlauncher.observer

/**
 * Placar de acerto da Sofy (24/11/2026, ideias 34 e 37 do estudo "extrapolar a Sofy").
 *
 * Por quê: "evoluir a si mesma" precisa de uma nota. Até aqui a única medida de acerto era "2 recusas calam 7 dias", e
 * nem ela rodava (a Ilha não tem botão de recusar: SofyTv.answered nunca era chamado). Para quê: cada coisa que ela faz
 * ganha um desfecho medido PELA PRÓPRIA TV, sem depender de a pessoa responder, e o placar muda os limites dela
 * ([SofyLimits]) e diz quando o pico pode sair da sombra ([picoReady]).
 * Como: um [Record] por desfecho ([Action] e [Outcome]), em linhas de texto como o barramento; [tally] soma por ação.
 * Desfechos de cada ação (quem mede: SofyLearnTv / AppProfileTv / ObserverTv):
 *  - TELL: um achado apareceu. Durou [TELL_REAL_MS] ou mais = era de verdade (HIT); sumiu em menos de [TELL_NOISE_MS]
 *    = era barulho (MISS). O pico tinha dado a nota antes: acertou ou não ([Record.picoAgreed]).
 *  - RECOMPILE: a mediana do tempo de abrir caiu 10% ou mais = HIT; subiu 10% ou mais = UNDONE (piorou); senão MISS.
 *  - PREWARM: o app abriu até 1 h depois da hora firme = HIT; não abriu = MISS.
 *  - ON_DEMAND: a pessoa tirou do sob demanda em 14 dias = UNDONE; continuou = HIT.
 *  - FREEZE_TRIAL: a prova desfez o congelamento = UNDONE; manteve = HIT.
 *  - EXPERIMENT: o experimento da noite decidiu manter = HIT; descartar = MISS.
 *  - PREHOME: depois do pré-HOME, a volta seguinte foi quente (o processo vivo) = HIT; fria = MISS.
 *  - SUGGESTION: a pessoa ligou o que a sugestão propôs = HIT; "agora não" = MISS; "não sugerir mais" = UNDONE.
 * Pura: SofyScoreTest. Fica em "sofy_learning" (viaja no backup): é o que ela aprendeu da casa.
 */
object SofyScore {
    const val KEEP_MS = 60 * 86_400_000L
    const val MAX = 400
    const val TELL_REAL_MS = 30 * 60_000L
    const val TELL_NOISE_MS = 10 * 60_000L
    /** O pico decide os avisos pequenos só com [PICO_MIN] notas nos últimos [PICO_WINDOW_MS] e [PICO_RATE] de acerto. */
    const val PICO_MIN = 20
    const val PICO_RATE = 0.8
    const val PICO_WINDOW_MS = 14 * 86_400_000L

    /** Nome vai para o arquivo: nunca renomear (só acrescentar). */
    enum class Action { TELL, RECOMPILE, PREWARM, ON_DEMAND, FREEZE_TRIAL, EXPERIMENT, SUGGESTION, PREHOME }
    enum class Outcome { HIT, MISS, UNDONE }

    data class Record(val at: Long, val action: Action, val outcome: Outcome, val picoAgreed: Boolean? = null, val detail: String = "")

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').trim().take(80)

    fun line(r: Record): String = "${r.at}\t${r.action.name}\t${r.outcome.name}\t${when (r.picoAgreed) { true -> "1"; false -> "0"; null -> "" }}\t${clean(r.detail)}"

    fun parse(l: String): Record? {
        val p = l.split('\t')
        if (p.size < 3) return null
        val at = p[0].toLongOrNull() ?: return null
        val a = Action.entries.firstOrNull { it.name == p[1] } ?: return null
        val o = Outcome.entries.firstOrNull { it.name == p[2] } ?: return null
        return Record(at, a, o, when (p.getOrNull(3)) { "1" -> true; "0" -> false; else -> null }, p.getOrElse(4) { "" })
    }

    fun add(text: String?, r: Record, now: Long): String =
        (text.orEmpty().lines().filter { l -> parse(l)?.let { now - it.at in 0..KEEP_MS } == true } + line(r)).takeLast(MAX).joinToString("\n")

    fun records(text: String?): List<Record> = text.orEmpty().lines().mapNotNull { parse(it) }

    data class Tally(val hits: Int, val misses: Int, val undone: Int) {
        val total get() = hits + misses + undone
        /** Fração de acerto (desfeito conta como erro). */
        val rate: Double? get() = if (total == 0) null else hits.toDouble() / total
    }

    fun tally(records: List<Record>, since: Long): Map<Action, Tally> = records.filter { it.at >= since }.groupBy { it.action }
        .mapValues { (_, l) -> Tally(l.count { it.outcome == Outcome.HIT }, l.count { it.outcome == Outcome.MISS }, l.count { it.outcome == Outcome.UNDONE }) }

    /** Rótulo de "valia avisar?" de um achado: durou = sim; sumiu logo = não; no meio = não sei (null, não aprende). */
    fun tellLabel(openMs: Long, stillOpen: Boolean): Boolean? = when {
        openMs >= TELL_REAL_MS -> true
        !stillOpen && openMs < TELL_NOISE_MS -> false
        else -> null
    }

    /** Quantas notas do pico nos últimos 14 dias e quanto ele acertou. */
    fun picoAgreement(records: List<Record>, now: Long): Pair<Int, Double?> {
        val l = records.filter { it.action == Action.TELL && it.picoAgreed != null && now - it.at in 0..PICO_WINDOW_MS }
        return l.size to (if (l.isEmpty()) null else l.count { it.picoAgreed == true }.toDouble() / l.size)
    }

    /** Ideia 37: o pico sai da sombra quando o critério escrito passa (e volta para a sombra sozinho se cair). */
    fun picoReady(records: List<Record>, now: Long): Boolean =
        picoAgreement(records, now).let { (n, r) -> n >= PICO_MIN && r != null && r >= PICO_RATE }
}
