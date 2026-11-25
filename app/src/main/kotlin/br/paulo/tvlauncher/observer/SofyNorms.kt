package br.paulo.tvlauncher.observer

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Padrões da casa (passo 2 do plano D, Paulo 25/11/2026: "ela tem q ser excepcional", sem se limitar às regras que a
 * Revisão ensinou).
 *
 * Por quê: toda regra escrita à mão só pega o defeito que alguém já imaginou. Para quê: a Sofy aprende sozinha o que é
 * NORMAL nesta casa e acusa quando o normal quebra, sem custo de IA e sem nada sair da TV. Como: uma vez por dia ela
 * guarda os números do dia que passou ([Day]: quantas vezes cada coisa aconteceu no barramento, e qualquer medida que
 * alguém mais quiser mandar) e compara com os dias anteriores. Quatro jeitos de o normal quebrar:
 *  - [Kind.SPIKE]: uma falha (chave terminando em ".fail") aconteceu muito mais que o normal da casa.
 *  - [Kind.SILENT]: algo que acontecia TODO dia (várias vezes) não aconteceu nenhuma vez: peça parada em silêncio.
 *  - [Kind.OUT_OF_RANGE]: uma medida (chave sem "count.") saiu da faixa de sempre.
 *  - [Kind.PAIR_BROKEN]: duas coisas que sempre vinham juntas (A no dia => B no mesmo dia) se separaram.
 * Freios: nada acusa antes de [MIN_DAYS] dias de história (a regra aprendida fica em sombra até lá); a mesma quebra
 * [ACCEPT_AFTER] dias seguidos vira o novo normal (a casa mudou, não é defeito) e fica calada por [ACCEPT_MS].
 * Pura: SofyNormsTest.
 */
object SofyNorms {
    const val MIN_DAYS = 7
    const val KEEP_DAYS = 30
    const val ACCEPT_AFTER = 3
    const val ACCEPT_DAYS = 30
    const val ACCEPT_MS = ACCEPT_DAYS * 86_400_000L
    /** Desvio robusto: quantos MADs (escalados para desvio padrão) separam "diferente" de "normal". */
    const val K = 4.0
    const val MAX_PAIR_KEYS = 40
    /** Chaves de contagem (zero quando não aparecem no dia). As outras são medidas (ausente = não medido). */
    const val COUNT = "count."

    enum class Kind { SPIKE, SILENT, OUT_OF_RANGE, PAIR_BROKEN }
    data class Day(val day: Int, val values: Map<String, Double>)
    data class Finding(val kind: Kind, val key: String, val detail: String) { val id get() = "${kind.name}:$key" }
    /** [streak]: id da quebra -> (último dia visto, dias seguidos). [accepted]: id -> dia em que virou o novo normal. */
    data class Book(val days: List<Day> = emptyList(), val streak: Map<String, Pair<Int, Int>> = emptyMap(),
                    val accepted: Map<String, Int> = emptyMap())

    private fun isCount(k: String) = k.startsWith(COUNT)
    private fun value(d: Day, k: String): Double? = d.values[k] ?: if (isCount(k)) 0.0 else null

    fun median(xs: List<Double>): Double { val s = xs.sorted(); val n = s.size
        return if (n == 0) 0.0 else if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2 }
    /** Desvio absoluto mediano, escalado (1,4826) para valer como desvio padrão numa curva normal. */
    fun mad(xs: List<Double>): Double { val m = median(xs); return median(xs.map { abs(it - m) }) * 1.4826 }

    /** Guarda o dia (substitui se já havia) e mantém só os [KEEP_DAYS] mais novos. */
    fun record(b: Book, d: Day): Book = b.copy(days = (b.days.filter { it.day != d.day } + d).sortedBy { it.day }.takeLast(KEEP_DAYS))

    /** Compara [today] com a história anterior a ele. Não muda o livro: use [settle] depois. */
    fun check(b: Book, today: Day): List<Finding> {
        val past = b.days.filter { it.day < today.day }.takeLast(KEEP_DAYS)
        if (past.size < MIN_DAYS) return emptyList()
        val out = ArrayList<Finding>()
        val keys = (past.flatMap { it.values.keys } + today.values.keys).toSortedSet()
        for (k in keys) {
            val hist = past.mapNotNull { value(it, k) }
            val x = value(today, k) ?: continue
            if (hist.size < MIN_DAYS) continue
            val med = median(hist); val spread = mad(hist)
            if (isCount(k)) {
                if (k.endsWith(".fail")) {
                    val limit = med + maxOf(K * spread, 3.0, med * 0.5)
                    if (x > limit) out += Finding(Kind.SPIKE, k, "${fmt(x)} hoje; o normal da casa é ${fmt(med)} (limite ${fmt(limit)})")
                }
                // Os dias antes do silêncio atual (zeros no fim já são o mesmo silêncio, contado dia a dia).
                val zeros = hist.takeLastWhile { it == 0.0 }.size
                val before = hist.dropLast(zeros).takeLast(MIN_DAYS)
                if (x == 0.0 && before.size == MIN_DAYS && before.all { it >= 1.0 } && median(before) >= 2.0)
                    out += Finding(Kind.SILENT, k, "nenhuma vez hoje" + (if (zeros > 0) " (${zeros + 1}º dia seguido)" else "") +
                        "; acontecia todo dia (normal ${fmt(median(before))})")
            } else {
                val tol = maxOf(K * spread, abs(med) * 0.1, 1.0)
                if (abs(x - med) > tol) out += Finding(Kind.OUT_OF_RANGE, k, "${fmt(x)}; o normal é ${fmt(med)} ± ${fmt(tol)}")
            }
        }
        out += pairs(past, today)
        return out.filter { f -> b.accepted[f.id]?.let { today.day - it >= ACCEPT_DAYS } != false }
    }

    /** A => B: em todo dia (pelo menos [MIN_DAYS]) em que A aconteceu, B também. Só contagens, as mais frequentes. */
    private fun pairs(past: List<Day>, today: Day): List<Finding> {
        fun present(d: Day) = d.values.filter { isCount(it.key) && it.value > 0 }.keys
        val freq = past.flatMap { present(it) }.groupingBy { it }.eachCount().filterValues { it >= MIN_DAYS }
        val top = freq.entries.sortedByDescending { it.value }.take(MAX_PAIR_KEYS).map { it.key }.toSet()
        val now = present(today)
        val out = ArrayList<Finding>()
        // B que vinha TODO dia já é assunto do SILENT; aqui só o que depende de A. Um achado por B (o primeiro A).
        val always = top.filter { k -> past.all { k in present(it) } }.toSet()
        for (bk in top) {
            if (bk in now || bk in always) continue
            val a = top.firstOrNull { a -> a != bk && a in now && past.filter { a in present(it) }.all { bk in present(it) } } ?: continue
            val n = past.count { a in present(it) }
            out += Finding(Kind.PAIR_BROKEN, "$a=>$bk", "$a aconteceu sem $bk; nos $n dias anteriores com $a, $bk veio junto")
        }
        return out
    }

    /**
     * Depois de acusar: conta os dias seguidos de cada quebra. [ACCEPT_AFTER] seguidos = a casa mudou; a quebra entra
     * em [accepted] e é devolvida no segundo valor (para a Sofy contar uma vez "aprendi um normal novo").
     */
    fun settle(b: Book, day: Int, found: List<Finding>): Pair<Book, List<Finding>> {
        val streak = HashMap<String, Pair<Int, Int>>()
        val accepted = b.accepted.filterValues { day - it < ACCEPT_DAYS * 2 }.toMutableMap()
        val newNormal = ArrayList<Finding>()
        for (f in found) {
            val prev = b.streak[f.id]
            val n = if (prev != null && prev.first == day - 1) prev.second + 1 else 1
            if (n >= ACCEPT_AFTER) { accepted[f.id] = day; newNormal += f } else streak[f.id] = day to n
        }
        return b.copy(streak = streak, accepted = accepted) to newNormal
    }

    /** As contagens de um dia a partir do barramento: "count.<fonte>.<tipo>.ok|fail". */
    fun keyOf(e: SofyBus.Event) = "$COUNT${e.key}.${if (e.ok) "ok" else "fail"}"
    fun countsOf(events: List<SofyBus.Event>, from: Long, to: Long): Map<String, Double> =
        events.filter { it.at in from until to }.groupingBy { keyOf(it) }.eachCount().mapValues { it.value.toDouble() }

    private fun fmt(x: Double) = if (x == Math.floor(x)) x.toLong().toString() else String.format(java.util.Locale.US, "%.1f", x)

    fun toJson(b: Book): String = JSONObject()
        .put("days", JSONArray().apply { b.days.forEach { d -> put(JSONObject().put("d", d.day)
            .put("v", JSONObject().apply { d.values.forEach { (k, v) -> put(k, v) } })) } })
        .put("streak", JSONObject().apply { b.streak.forEach { (k, v) -> put(k, JSONArray().put(v.first).put(v.second)) } })
        .put("accepted", JSONObject().apply { b.accepted.forEach { (k, v) -> put(k, v) } })
        .toString()

    fun fromJson(s: String?): Book = runCatching {
        val o = JSONObject(s ?: return Book())
        val days = o.optJSONArray("days") ?: JSONArray()
        Book(
            days = (0 until days.length()).mapNotNull { i -> days.optJSONObject(i)?.let { d ->
                val v = d.optJSONObject("v") ?: JSONObject()
                Day(d.getInt("d"), v.keys().asSequence().associateWith { v.getDouble(it) }) } },
            streak = o.optJSONObject("streak")?.let { st -> st.keys().asSequence().associateWith { k ->
                st.getJSONArray(k).let { it.getInt(0) to it.getInt(1) } } }.orEmpty(),
            accepted = o.optJSONObject("accepted")?.let { a -> a.keys().asSequence().associateWith { a.getInt(it) } }.orEmpty(),
        )
    }.getOrDefault(Book())
}
