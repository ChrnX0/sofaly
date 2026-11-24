package br.paulo.tvlauncher.observer

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ficha viva de cada app (24/11/2026, Paulo: "para cada app aberto ela estuda o comportamento e otimiza o sistema da
 * melhor maneira para extrair o melhor uso de cada coisa").
 *
 * Por quê: a Sofy já cuidava da TV como um todo (memória, rede, disco), mas não sabia nada de cada app: quando a casa
 * usa, quanto tempo fica na frente, quanto demora para abrir. Sem isso, "otimizar" vira chute igual para todos.
 * Para quê: cada app ganha o ajuste que serve para ELE (deixar pronto antes da hora de uso, compilar de novo o que abre
 * devagar, congelar sob demanda o que ninguém usa mais), por pessoa (perfil), e o que ela aprende viaja no backup.
 * Como: [learn] lê os eventos de uso do Android (app foi para a frente / saiu) e soma aberturas por hora do dia e tempo
 * na frente, com meia-vida de [HALF_LIFE_MS] (o hábito de hoje pesa mais que o de um mês atrás); [noteStart] guarda o
 * tempo de abrir que o próprio Android mede; [advise] transforma a ficha em dicas. Por enquanto as dicas ficam em
 * SOMBRA (vão ao registro e ao MCP, ninguém age), como o pico: primeiro ver se acertam na Sala.
 *
 * Pura (sem Android): AppProfileTest. A parte do Android é AppProfileTv.
 */
object AppProfile {
    const val HALF_LIFE_MS = 14 * 86_400_000L
    /** Fichas por perfil: passou disso, sai a do app menos usado (o backup fica pequeno). */
    const val MAX_APPS = 60
    const val MAX_STARTS = 8
    /** Volta ao mesmo app em menos que isso (ex.: abriu as Configurações e voltou) não é uma abertura nova. */
    const val REOPEN_GAP_MS = 60_000L

    // Dicas: limites escolhidos para errar para o lado de não mexer.
    /** Hora de uso firme: pelo menos isto de aberturas (já com meia-vida) e [PREWARM_SHARE] das aberturas do app. */
    const val PREWARM_MIN_OPENS = 3.0
    const val PREWARM_SHARE = 0.35
    /** Abre devagar: mediana de pelo menos [RECOMPILE_MIN_SAMPLES] aberturas acima disto. */
    const val RECOMPILE_SLOW_MS = 2_500
    const val RECOMPILE_MIN_SAMPLES = 3
    /** Esquecido: conhecido há e sem abrir há pelo menos isto. */
    const val UNUSED_MS = 21 * 86_400_000L

    data class Card(
        val pkg: String,
        val firstSeen: Long,
        val lastOpen: Long = 0L,
        /** Aberturas por hora do dia (0-23), com meia-vida, conferidas em [decayedAt]. */
        val opens: List<Double> = List(24) { 0.0 },
        val frontMs: Double = 0.0,
        val decayedAt: Long = firstSeen,
        /** Tempos de abrir medidos pelo Android (ms), os [MAX_STARTS] mais novos. */
        val startsMs: List<Int> = emptyList(),
    ) {
        val totalOpens: Double get() = opens.sum()
    }

    /** Um evento de uso: [front] = o app foi para a frente; false = saiu da frente. */
    data class Event(val pkg: String, val at: Long, val front: Boolean)

    fun decay(c: Card, now: Long): Card {
        if (now <= c.decayedAt) return c
        val f = Math.pow(0.5, (now - c.decayedAt).toDouble() / HALF_LIFE_MS)
        return c.copy(opens = c.opens.map { it * f }, frontMs = c.frontMs * f, decayedAt = now)
    }

    /**
     * Soma os [events] (de qualquer ordem) às fichas de um perfil. [hourOf] = hora do dia de um instante (fuso da TV).
     * [ignore] = pacotes que não são "apps que a pessoa usa" (o próprio launcher, a interface do sistema).
     * [front]/[frontSince] = o app que estava na frente quando a leitura anterior parou (continua contando o tempo).
     */
    data class State(val cards: Map<String, Card>, val front: String? = null, val frontSince: Long = 0L, val lastLeft: Map<String, Long> = emptyMap())

    fun learn(s: State, events: List<Event>, hourOf: (Long) -> Int, now: Long, ignore: Set<String>): State {
        val cards = HashMap<String, Card>()
        for ((k, v) in s.cards) cards[k] = decay(v, now)
        val left = HashMap(s.lastLeft)
        var front = s.front
        var since = s.frontSince
        fun close(at: Long) {
            val f = front ?: return
            val dur = (at - since).coerceIn(0L, 6 * 3_600_000L)   // TV esquecida ligada não vira 20 h de uso
            cards[f]?.let { cards[f] = it.copy(frontMs = it.frontMs + dur) }
            left[f] = at
            front = null
        }
        for (e in events.sortedBy { it.at }) {
            if (e.pkg in ignore) { if (e.front) close(e.at); continue }
            if (e.front) {
                if (front == e.pkg) continue
                close(e.at)
                val c = cards[e.pkg] ?: Card(e.pkg, firstSeen = e.at, decayedAt = now)
                val reopen = (e.at - (left[e.pkg] ?: Long.MIN_VALUE / 2)) < REOPEN_GAP_MS
                cards[e.pkg] = if (reopen) c.copy(lastOpen = e.at) else {
                    val h = hourOf(e.at).coerceIn(0, 23)
                    c.copy(lastOpen = e.at, opens = c.opens.mapIndexed { i, v -> if (i == h) v + 1.0 else v })
                }
                front = e.pkg; since = e.at
            } else if (front == e.pkg) close(e.at)
        }
        val kept = if (cards.size <= MAX_APPS) cards
            else cards.values.sortedByDescending { it.totalOpens + it.frontMs / 3_600_000.0 }.take(MAX_APPS).associateBy { it.pkg }
        return State(kept, front, since, left.filterKeys { it in kept })
    }

    fun noteStart(c: Card, ms: Int): Card = c.copy(startsMs = (c.startsMs + ms).takeLast(MAX_STARTS))

    enum class Advice { PREWARM, RECOMPILE, ON_DEMAND }
    data class Tip(val pkg: String, val advice: Advice, val hour: Int?, val why: String)

    fun median(v: List<Int>): Int = v.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }

    /**
     * As dicas de cada ficha. [onDemand] = já congelados sob demanda; [freezable] = a ficha do pacote diz que é seguro
     * congelar (OnDemand só aceita esses).
     */
    fun advise(cards: Collection<Card>, now: Long, onDemand: Set<String>, freezable: (String) -> Boolean): List<Tip> {
        val out = ArrayList<Tip>()
        for (c0 in cards) {
            val c = decay(c0, now)
            val total = c.totalOpens
            val h = c.opens.indices.maxByOrNull { c.opens[it] }
            if (h != null && c.opens[h] >= PREWARM_MIN_OPENS && c.opens[h] >= PREWARM_SHARE * total)
                out += Tip(c.pkg, Advice.PREWARM, h, "abre mais às ${h}h (${"%.1f".format(c.opens[h])} de ${"%.1f".format(total)} aberturas)")
            if (c.startsMs.size >= RECOMPILE_MIN_SAMPLES && median(c.startsMs) > RECOMPILE_SLOW_MS)
                out += Tip(c.pkg, Advice.RECOMPILE, null, "demora ${median(c.startsMs)} ms para abrir (mediana de ${c.startsMs.size})")
            if (c.pkg !in onDemand && now - c.firstSeen >= UNUSED_MS && now - c.lastOpen >= UNUSED_MS && freezable(c.pkg))
                out += Tip(c.pkg, Advice.ON_DEMAND, null, "sem abrir há ${(now - c.lastOpen) / 86_400_000L} dias")
        }
        return out.sortedWith(compareBy({ it.advice.ordinal }, { it.pkg }))
    }

    /**
     * Tempo de abrir que o Android escreve no registro de eventos (`logcat -b events`), linhas como
     * `... wm_activity_launch_time: [0,123456,com.app/.Main,850]` (Android 10+; antes, am_activity_launch_time).
     * Devolve pacote -> ms, ou null se a linha não é dessas.
     */
    fun parseLaunchTime(line: String): Pair<String, Int>? {
        if (!line.contains("activity_launch_time")) return null
        val inner = line.substringAfter('[', "").substringBefore(']', "")
        if (inner.isEmpty()) return null
        val parts = inner.split(',').map { it.trim() }
        val comp = parts.firstOrNull { '/' in it } ?: return null
        val ms = parts.last().toIntOrNull() ?: return null
        if (ms !in 1..120_000) return null
        return comp.substringBefore('/') to ms
    }

    // ---------------------------------------------------------------- guardar ("sofy_learning", chave "apps")

    fun toJson(byProfile: Map<String, State>): JSONObject {
        val o = JSONObject()
        for ((profile, s) in byProfile) {
            val cards = JSONArray()
            for (c in s.cards.values) cards.put(JSONObject().put("p", c.pkg).put("f", c.firstSeen).put("l", c.lastOpen)
                .put("o", JSONArray(c.opens.map { Math.round(it * 100) / 100.0 })).put("t", Math.round(c.frontMs)).put("d", c.decayedAt)
                .put("s", JSONArray(c.startsMs)))
            o.put(profile, JSONObject().put("cards", cards))
        }
        return o
    }

    fun fromJson(o: JSONObject?): Map<String, State> {
        if (o == null) return emptyMap()
        val out = HashMap<String, State>()
        for (profile in o.keys()) runCatching {
            val arr = o.getJSONObject(profile).getJSONArray("cards")
            val cards = HashMap<String, Card>()
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val op = j.optJSONArray("o")
                val opens = List(24) { k -> op?.optDouble(k, 0.0)?.takeIf { !it.isNaN() } ?: 0.0 }
                val st = j.optJSONArray("s")
                val starts = if (st == null) emptyList() else (0 until st.length()).map { st.getInt(it) }
                val c = Card(j.getString("p"), j.getLong("f"), j.optLong("l"), opens, j.optDouble("t", 0.0), j.optLong("d", j.getLong("f")), starts)
                cards[c.pkg] = c
            }
            out[profile] = State(cards)
        }
        return out
    }
}
