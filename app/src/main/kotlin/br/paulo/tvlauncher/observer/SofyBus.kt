package br.paulo.tvlauncher.observer

/**
 * O barramento de eventos da Sofy (24/11/2026, ideia 1 do estudo "extrapolar a Sofy"; Paulo: "a Sofy é o cérebro
 * central de tudo: tudo passa por ela, tudo ela vê e sabe").
 *
 * Por quê: até aqui só 11 arquivos fora de observer/ falavam com ela. Alarme, player, backup, atualizador, plugins,
 * automações e quedas do launcher aconteciam sem ela saber, então ela não tinha como coordenar nada.
 * Para quê: cada parte do Sofaly conta, numa linha curta, o que aconteceu. Ela vira a única memória de "o que aconteceu
 * nesta TV", e as próximas peças (uma noite só, uma voz só, rotina que vira automação, placar de acerto) leem daqui.
 * Como: um evento = quando, de onde ([Source]), o quê (kind), se deu certo e um detalhe curto, SEM dado pessoal (nada de
 * nome de app de terceiro, título, nome de pessoa). Guardado em linhas de texto separadas por TAB ([line]/[parse]);
 * [trim] mantém só [KEEP_MS] e no máximo [MAX] linhas. A parte do Android (arquivo com trava, qualquer processo) é
 * [SofyBusTv]. Pura: SofyBusTest.
 */
object SofyBus {
    const val MAX = 500
    const val KEEP_MS = 7 * 86_400_000L
    private const val DETAIL_MAX = 120

    /** De onde veio. O nome vai no arquivo e no MCP: nunca renomear (só acrescentar). */
    enum class Source { HOUSE, ALARM, PLAYER, BACKUP, UPDATE, PLUGIN, CRASH, NETWORK, SOFY,
        /** Medidas feitas pela casa (calibração de imagem e som pelo celular, etc.): kind = o que foi medido. */
        MEASURE }

    data class Event(val at: Long, val source: Source, val kind: String, val ok: Boolean = true, val detail: String = "") {
        val key: String get() = "${source.name.lowercase()}.$kind"
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    fun line(e: Event): String =
        "${e.at}\t${e.source.name}\t${clean(e.kind)}\t${if (e.ok) 1 else 0}\t${clean(e.detail).take(DETAIL_MAX)}"

    fun parse(line: String): Event? {
        val p = line.split('\t')
        if (p.size < 4) return null
        val at = p[0].toLongOrNull() ?: return null
        val src = Source.entries.firstOrNull { it.name == p[1] } ?: return null
        if (p[2].isEmpty()) return null
        return Event(at, src, p[2], p[3] != "0", p.getOrElse(4) { "" })
    }

    /** O que fica: só os últimos [KEEP_MS], no máximo [MAX] linhas, as mais novas. Linhas estragadas saem. */
    fun trim(lines: List<String>, now: Long): List<String> =
        lines.filter { l -> parse(l)?.let { now - it.at in 0..KEEP_MS } == true }.takeLast(MAX)

    /** Quantos de cada "fonte.tipo" desde [since]. */
    fun counts(events: List<Event>, since: Long): Map<String, Int> =
        events.filter { it.at >= since }.groupingBy { it.key }.eachCount().toSortedMap()

    /** Os que deram errado desde [since], o mais novo primeiro. */
    fun failures(events: List<Event>, since: Long): List<Event> =
        events.filter { it.at >= since && !it.ok }.sortedByDescending { it.at }

    /** O que aconteceu na janela [from, to]: o contexto de um achado (ex.: a HOME ficou lenta logo depois de quê?). */
    fun around(events: List<Event>, from: Long, to: Long): List<Event> = events.filter { it.at in from..to }.sortedBy { it.at }
}
