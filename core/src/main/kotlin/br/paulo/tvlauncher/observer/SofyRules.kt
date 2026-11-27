package br.paulo.tvlauncher.observer

import org.json.JSONArray
import org.json.JSONObject

/**
 * Revisor noturno com IA (passo 3 do plano D, Paulo 25/11/2026: a Sofy descobre regras novas sozinha, "ela tem q ser
 * excepcional").
 *
 * Por quê: a Revisão achou defeitos que nenhuma regra previa (feriado de outro país, "498783 h", chuva dita sem ícone),
 * todos lendo o que a tela DIZIA. Para quê: de madrugada, depois de ler uma tela, a Sofy mostra os textos à IA do dono;
 * a IA aponta o que já está errado e propõe regras CHECÁVEIS para o futuro. A TV confere as regras sozinha, sem IA.
 * Como (o que protege a casa):
 *  - Só dois tipos de regra, fechados: [Kind.FORBIDDEN] (um padrão que nunca deve aparecer, ex.: "null", "NaN") e
 *    [Kind.RANGE] (um número capturado pelo padrão tem que ficar entre min e max, ex.: porcentagem 0..100).
 *  - O padrão é conferido antes de valer ([safe]): curto, sem quantificador aninhado, e toda busca tem limite de passos
 *    ([Limited]); padrão perigoso é recusado.
 *  - Toda regra nasce em SOMBRA: precisa passar [SHADOW_NIGHTS] leituras da tela dela sem disparar para valer. Se
 *    disparar na sombra, é alarme falso (a tela estava boa quando a IA propôs) e a regra cai ([State.DROPPED]); a lista
 *    das que caíram vai na próxima pergunta, para a IA não repetir.
 *  - No máximo [PER_NIGHT] regras novas por noite e [MAX_RULES] guardadas.
 * Pura: SofyRulesTest.
 */
object SofyRules {
    const val SHADOW_NIGHTS = 3
    const val PER_NIGHT = 3
    const val MAX_RULES = 40
    const val PATTERN_MAX = 80
    const val TEXT_MAX = 200
    const val STEPS_MAX = 200_000

    enum class Kind { FORBIDDEN, RANGE }
    enum class State { SHADOW, ACTIVE, DROPPED }
    data class Rule(
        val id: String, val kind: Kind, val screen: String, val pattern: String,
        val min: Double = 0.0, val max: Double = 0.0, val why: String = "",
        val state: State = State.SHADOW, val nights: Int = 0, val example: String = "", val at: Long = 0,
    )
    data class Review(val rules: List<Rule>, val defects: List<String>)

    /** Lição da Revisão (25/11) que vai de exemplo para a IA: os defeitos de sentido que escaparam dos testes. */
    const val SYSTEM = "You review the visible texts of one screen of a TV home-screen app, read from the real TV at night. " +
        "Find texts that are WRONG (impossible or contradictory values, placeholders like null/NaN/{x}, durations like " +
        "\"498783 h\", percentages above 100, today's max below the current temperature, the same event twice with different " +
        "names, holidays of another country) and propose CHECKABLE rules that would catch the same class of defect later. " +
        "Rule kinds: FORBIDDEN {\"kind\":\"FORBIDDEN\",\"screen\":\"<name or *>\",\"pattern\":\"<java regex>\",\"why\":\"...\"} = " +
        "the pattern must never match any text; RANGE {\"kind\":\"RANGE\",\"screen\":\"...\",\"pattern\":\"<regex with ONE capture " +
        "group holding a number>\",\"min\":n,\"max\":n,\"why\":\"...\"} = every captured number stays within min..max. Patterns: " +
        "short, no nested quantifiers, must NOT match any text shown now unless that text is a defect you list. Prefer screen \"*\" " +
        "for rules that hold everywhere. Answer ONLY with JSON {\"defects\":[\"short sentence\"],\"rules\":[...]} (at most 3 rules, " +
        "empty arrays are fine). A text ending in \"[cut]\" was shortened by us, not by the app: never report it as cut. No other text."

    fun prompt(screen: String, texts: List<String>, rules: List<Rule>): String = buildString {
        append("Screen: ").append(screen).append('\n')
        append("Texts (one per line):\n")
        // Texto longo cortado AQUI ganha "[cut]" (27/11/2026): sem a marca, 2 dos 4 modelos acusaram "linha cortada no fim"
        // no Diagnóstico da Sala, e o corte era nosso.
        texts.map { it.replace('\n', ' ').let { t -> if (t.length > TEXT_MAX) t.take(TEXT_MAX) + " [cut]" else t } }.distinct().take(80).forEach { append("- ").append(it).append('\n') }
        val known = rules.filter { it.state != State.DROPPED }
        if (known.isNotEmpty()) { append("Rules already kept (do not repeat):\n"); known.forEach { append("- ").append(it.kind).append(' ').append(it.pattern).append('\n') } }
        val dropped = rules.filter { it.state == State.DROPPED }
        if (dropped.isNotEmpty()) { append("Rules that gave false alarms (avoid):\n"); dropped.takeLast(10).forEach { append("- ").append(it.pattern).append(" matched \"").append(it.example).append("\"\n") } }
    }

    /** Padrão aceitável? Curto, compila, sem grupo quantificado que tenha quantificador dentro ("(a+)+"). */
    private val NESTED = Regex("""\((?:[^()\\]|\\.)*[+*}](?:[^()\\]|\\.)*\)[+*{]""")
    fun safe(pattern: String): Regex? {
        if (pattern.isBlank() || pattern.length > PATTERN_MAX || NESTED.containsMatchIn(pattern)) return null
        return runCatching { Regex(pattern) }.getOrNull()
    }

    /** Texto que conta os passos do regex: busca que passa de [STEPS_MAX] para (vira "não casou") em vez de travar. */
    private class Limited(private val s: CharSequence, private val counter: IntArray) : CharSequence {
        override val length get() = s.length
        override fun get(index: Int): Char { if (++counter[0] > STEPS_MAX) throw IllegalStateException("regex"); return s[index] }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = Limited(s.subSequence(startIndex, endIndex), counter)
        override fun toString() = s.toString()
    }

    /** O primeiro texto que quebra a regra (ou null). */
    fun violation(r: Rule, texts: List<String>): String? {
        val re = safe(r.pattern) ?: return null
        for (t0 in texts) {
            val t = t0.take(TEXT_MAX)
            val steps = IntArray(1)
            val hit = runCatching {
                when (r.kind) {
                    Kind.FORBIDDEN -> re.containsMatchIn(Limited(t, steps))
                    Kind.RANGE -> re.findAll(Limited(t, steps)).any { m ->
                        val n = m.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
                        n != null && (n < r.min || n > r.max)
                    }
                }
            }.getOrDefault(false)
            if (hit) return t
        }
        return null
    }

    /**
     * Uma leitura de [screen]: cada regra que vale para ela anda. Devolve as regras atualizadas e as quebras das regras
     * que já VALEM (as da sombra que disparam caem caladas).
     */
    fun evaluate(rules: List<Rule>, screen: String, texts: List<String>): Pair<List<Rule>, List<Pair<Rule, String>>> {
        val broken = ArrayList<Pair<Rule, String>>()
        val out = rules.map { r ->
            if (r.state == State.DROPPED || (r.screen != "*" && r.screen != screen)) return@map r
            val v = violation(r, texts)
            when {
                r.state == State.ACTIVE -> { if (v != null) broken += r to v; r }
                // Na sombra, só o defeito que a IA já apontou (example) pode casar; qualquer outro texto = alarme falso.
                v != null && v != r.example -> r.copy(state = State.DROPPED, example = v.take(60))
                r.nights + 1 >= SHADOW_NIGHTS -> r.copy(state = State.ACTIVE, nights = r.nights + 1)
                else -> r.copy(nights = r.nights + 1)
            }
        }
        return out to broken
    }

    /** Lê a resposta da IA. Regra que já casa com a tela de agora (e não é defeito apontado) nem entra. */
    fun parse(answer: String?, screen: String, texts: List<String>, existing: List<Rule>, now: Long): Review {
        val o = answer?.let { a -> val i = a.indexOf('{'); val j = a.lastIndexOf('}')
            if (i < 0 || j <= i) null else runCatching { JSONObject(a.substring(i, j + 1)) }.getOrNull() } ?: return Review(emptyList(), emptyList())
        val defects = o.optJSONArray("defects")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).trim().take(160).ifEmpty { null } } }.orEmpty().take(5)
        val known = existing.map { it.pattern }.toSet()
        val rules = ArrayList<Rule>()
        val arr = o.optJSONArray("rules") ?: JSONArray()
        for (k in 0 until arr.length()) {
            if (rules.size >= PER_NIGHT) break
            val r = arr.optJSONObject(k) ?: continue
            val kind = runCatching { Kind.valueOf(r.optString("kind")) }.getOrNull() ?: continue
            val pattern = r.optString("pattern")
            val re = safe(pattern) ?: continue
            if (pattern in known || rules.any { it.pattern == pattern }) continue
            if (kind == Kind.RANGE && (re.toPattern().matcher("").groupCount() < 1 || !r.has("min") || !r.has("max") || r.optDouble("min") > r.optDouble("max"))) continue
            val sc = r.optString("screen", "*").ifEmpty { "*" }.take(30)
            val rule = Rule(id = idOf(pattern, kind, sc), kind = kind, screen = sc, pattern = pattern,
                min = r.optDouble("min", 0.0), max = r.optDouble("max", 0.0), why = r.optString("why").take(120), at = now)
            // Casa com a tela de agora: só entra se a IA apontou defeito nela; o texto vira o "defeito conhecido".
            val now0 = if (sc == "*" || sc == screen) violation(rule, texts) else null
            if (now0 != null && defects.isEmpty()) continue
            rules += if (now0 != null) rule.copy(example = now0) else rule
        }
        return Review(rules, defects)
    }

    // ---------------------------------------------------------------- segunda opinião (Paulo, 25/11/2026)

    /** Outra IA julga cada regra nova antes de ela entrar na sombra: menos alarme falso, custo de uma pergunta por noite. */
    const val SECOND_SYSTEM = "Another reviewer proposed checking rules for the visible texts of one TV screen. For each rule, " +
        "decide if it catches a REAL defect class (impossible or contradictory values, placeholders, absurd durations) and " +
        "would NOT fire on correct texts in other languages, dates, places or seasons. Answer ONLY with JSON " +
        "{\"keep\":[\"<id>\",...]} listing the ids you agree with. No other text."

    fun secondPrompt(screen: String, texts: List<String>, candidates: List<Rule>): String = buildString {
        append("Screen: ").append(screen).append('\n')
        append("Texts (one per line):\n")
        // Texto longo cortado AQUI ganha "[cut]" (27/11/2026): sem a marca, 2 dos 4 modelos acusaram "linha cortada no fim"
        // no Diagnóstico da Sala, e o corte era nosso.
        texts.map { it.replace('\n', ' ').let { t -> if (t.length > TEXT_MAX) t.take(TEXT_MAX) + " [cut]" else t } }.distinct().take(80).forEach { append("- ").append(it).append('\n') }
        append("Proposed rules:\n")
        candidates.forEach { r -> append("- id=").append(r.id).append(' ').append(r.kind).append(' ').append(r.pattern)
            if (r.kind == Kind.RANGE) append(" min=").append(r.min).append(" max=").append(r.max)
            append(" why: ").append(r.why).append('\n') }
    }

    /** As regras com que a segunda IA concordou. Resposta ilegível ou ausente = nenhuma (tenta de novo outra noite). */
    fun agreed(answer: String?, candidates: List<Rule>): List<Rule> {
        val o = answer?.let { a -> val i = a.indexOf('{'); val j = a.lastIndexOf('}')
            if (i < 0 || j <= i) null else runCatching { JSONObject(a.substring(i, j + 1)) }.getOrNull() } ?: return emptyList()
        val keep = o.optJSONArray("keep")?.let { a -> (0 until a.length()).map { a.optString(it).trim() } }.orEmpty().toSet()
        return candidates.filter { it.id in keep }
    }

    fun idOf(pattern: String, kind: Kind, screen: String) = "r" + Integer.toHexString((pattern + kind + screen).hashCode())

    // ---------------------------------------------------------------- outras casas (passo 4)

    /** Palavras de 4+ letras que podem sair da casa; o resto (nome, cidade, evento) nunca vai ao servidor. */
    val SHARE_WORDS = setOf("null", "undefined", "infinity", "error", "true", "false", "none", "nan", "http", "https")
    private val LETTERS = Regex("\\p{L}{4,}")

    /** O padrão pode ir para outras casas? Seguro e sem palavra que possa ser da casa (o servidor confere de novo). */
    fun shareable(pattern: String): Boolean {
        if (safe(pattern) == null) return false
        val bare = pattern.replace(Regex("\\\\."), " ").replace(Regex("\\[[^\\]]*\\]"), " ")
        return LETTERS.findAll(bare).all { it.value.lowercase() in SHARE_WORDS }
    }

    /** O que vai ao servidor: só as regras que JÁ VALEM aqui e podem sair. Sem exemplo, sem motivo, sem hora. */
    fun communityRules(rules: List<Rule>): JSONArray = JSONArray().apply {
        rules.filter { it.state == State.ACTIVE && shareable(it.pattern) }.take(10).forEach { r ->
            put(JSONObject().put("kind", r.kind.name).put("screen", r.screen).put("pattern", r.pattern).apply {
                if (r.kind == Kind.RANGE) put("min", r.min).put("max", r.max) })
        }
    }

    /** As regras da lista pública (vistas em [minHomes]+ casas): entram na SOMBRA desta TV, como as da IA daqui. */
    fun fromCommunity(o: JSONObject?, minHomes: Int, now: Long): List<Rule> {
        val a = o?.optJSONArray("rules") ?: return emptyList()
        return (0 until minOf(a.length(), 200)).mapNotNull { i ->
            val x = a.optJSONObject(i) ?: return@mapNotNull null
            val kind = runCatching { Kind.valueOf(x.optString("kind")) }.getOrNull() ?: return@mapNotNull null
            val pattern = x.optString("pattern").takeIf { shareable(it) } ?: return@mapNotNull null
            val homes = x.optInt("homes", 0).takeIf { it >= minHomes } ?: return@mapNotNull null
            val sc = x.optString("screen", "*").ifEmpty { "*" }.take(30)
            if (kind == Kind.RANGE && (safe(pattern)!!.toPattern().matcher("").groupCount() < 1 || x.optDouble("min") > x.optDouble("max"))) return@mapNotNull null
            Rule(idOf(pattern, kind, sc), kind, sc, pattern, x.optDouble("min", 0.0), x.optDouble("max", 0.0), "outras $homes casas", at = now)
        }
    }

    /** Junta as novas, tirando as mais velhas que caíram quando passa de [MAX_RULES]. */
    fun merge(rules: List<Rule>, new: List<Rule>): List<Rule> {
        var all = rules + new.filter { n -> rules.none { it.id == n.id } }
        while (all.size > MAX_RULES) {
            val drop = all.filter { it.state == State.DROPPED }.minByOrNull { it.at } ?: all.minByOrNull { it.at }!!
            all = all - drop
        }
        return all
    }

    fun toJson(rules: List<Rule>): String = JSONArray().apply { rules.forEach { r -> put(JSONObject().put("id", r.id).put("kind", r.kind.name)
        .put("screen", r.screen).put("pattern", r.pattern).put("min", r.min).put("max", r.max).put("why", r.why)
        .put("state", r.state.name).put("nights", r.nights).put("example", r.example).put("at", r.at)) } }.toString()

    fun fromJson(s: String?): List<Rule> = runCatching { val a = JSONArray(s ?: "[]"); (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { o ->
        Rule(o.getString("id"), Kind.valueOf(o.getString("kind")), o.optString("screen", "*"), o.getString("pattern"),
            o.optDouble("min", 0.0), o.optDouble("max", 0.0), o.optString("why"), State.valueOf(o.optString("state", "SHADOW")),
            o.optInt("nights"), o.optString("example"), o.optLong("at")) } } }.getOrDefault(emptyList())
}
