package br.paulo.tvlauncher.observer

/**
 * Guarda de cada versão (24/11/2026, ideia 5). Por quê: a guarda de desempenho (Desempenho) só olhava o p90 dos quadros
 * e avisava pelo painel; a HOME abrir mais devagar, cair mais ou perder a rede mais depois de uma versão nova passava
 * sem ninguém ligar à versão. Para quê: depois de cada atualização a Sofy compara com a versão anterior e, se piorou,
 * diz o quê, com os números (registro, barramento, MCP; a Ilha só quando a HOME ficou mais lenta, que é o que a casa
 * sente). Voltar de versão pela TV não dá (o Android recusa versão menor): o caminho é a próxima versão, e a tarefa
 * diária da ideia 39 leva o achado ao desenvolvedor.
 * Como: [Stats] por versão (horas medidas, quedas do launcher e da rede no barramento, mediana de abrir a HOME);
 * [compare] só julga com [MIN_HOURS] de cada lado (a HOME, com [MIN_OPENS] aberturas). Pura: SofyVersionGuardTest.
 */
object SofyVersionGuard {
    const val MIN_HOURS = 24.0
    const val MIN_OPENS = 5
    const val HOME_FACTOR = 1.3
    const val HOME_MIN_MS = 300

    enum class What { HOME_SLOWER, MORE_CRASHES, MORE_DROPS }

    data class Stats(val version: Long, val hours: Double, val crashes: Int, val drops: Int, val homeOpenMs: Int?, val opens: Int)
    data class Worse(val what: What, val why: String)

    private fun perDay(n: Int, h: Double) = n / (h / 24.0)
    private fun f1(d: Double) = Math.round(d * 10) / 10.0

    fun compare(prev: Stats, cur: Stats): List<Worse> {
        if (prev.version == cur.version || prev.version == 0L) return emptyList()
        val out = ArrayList<Worse>()
        val a = prev.homeOpenMs; val b = cur.homeOpenMs
        if (a != null && b != null && prev.opens >= MIN_OPENS && cur.opens >= MIN_OPENS && b > a * HOME_FACTOR && b - a >= HOME_MIN_MS)
            out += Worse(What.HOME_SLOWER, "a HOME abre em $b ms na versão ${cur.version} (antes $a ms na ${prev.version})")
        if (prev.hours >= MIN_HOURS && cur.hours >= MIN_HOURS) {
            val c0 = perDay(prev.crashes, prev.hours); val c1 = perDay(cur.crashes, cur.hours)
            if (c1 >= 1.0 && c1 >= 2 * c0) out += Worse(What.MORE_CRASHES, "o launcher caiu ${f1(c1)} vezes por dia (antes ${f1(c0)})")
            val d0 = perDay(prev.drops, prev.hours); val d1 = perDay(cur.drops, cur.hours)
            if (d1 >= 2.0 && d1 >= 2 * d0 + 1) out += Worse(What.MORE_DROPS, "a internet caiu ${f1(d1)} vezes por dia (antes ${f1(d0)})")
        }
        return out
    }

    /** Quedas do launcher e da rede no barramento entre [from] e [to]. */
    fun count(events: List<SofyBus.Event>, from: Long, to: Long): Pair<Int, Int> {
        val w = events.filter { it.at in from..to }
        return w.count { it.source == SofyBus.Source.CRASH } to w.count { it.source == SofyBus.Source.NETWORK && it.kind.startsWith("drop_") }
    }
}
