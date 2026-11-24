package br.paulo.tvlauncher.observer

import org.json.JSONArray
import org.json.JSONObject

/**
 * Uma casa, várias TVs (24/11/2026, ideia 40 do estudo "extrapolar a Sofy").
 *
 * Por quê: a TV do quarto descobre que a versão nova do app X trava; a da sala tem o mesmo app na mesma versão e ainda
 * não sabe. Para quê: a Sofy de uma TV avisa a outra, e a outra conta na Ilha antes de a pessoa sofrer. Como: só entre
 * as TVs da MESMA conta Tailscale com "TVs da família" ligado (EntreTvs); vai só pacote, versão e o tipo da lição
 * (nada de quem usa nem quando). Quem recebe confere de volta na TV que diz ter mandado. A lição vira só um recado:
 * a Sofy nunca age por causa de outra TV. Pura: SofyShareTest.
 */
object SofyShare {
    const val MAX_LESSONS = 30
    /** Pacote de lições mais velho que isto não vale (relógio das TVs pode diferir um pouco). */
    const val PACKET_MAX_AGE_MS = 10 * 60_000L
    /** Uma lição recebida vale por 14 dias; a mesma lição (pacote+versão+tipo) só vira recado uma vez. */
    const val LESSON_TTL_MS = 14 * 86_400_000L
    val KINDS = setOf("crashy", "slower")
    private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val NONCE = Regex("^[0-9a-f]{32}$")

    data class Lesson(val pkg: String, val ver: Long, val kind: String)
    data class Packet(val from: String, val nonce: String, val at: Long, val lessons: List<Lesson>)

    /** As lições desta TV: app que travou 3 vezes na semana nesta versão; app que abre bem mais devagar desde a versão nova. */
    fun lessons(cards: Collection<AppProfile.Card>, now: Long): List<Lesson> = buildList {
        for (c in cards) {
            if (c.ver <= 0 || !PKG.matches(c.pkg)) continue
            if (AppProfile.crashesWeek(c, now) >= AppProfile.CRASHY_COUNT) add(Lesson(c.pkg, c.ver, "crashy"))
            val m = AppProfile.median(c.startsMs)
            if (c.startsBefore > 0 && c.startsMs.size >= 3 && m * 2 >= c.startsBefore * 3) add(Lesson(c.pkg, c.ver, "slower"))
        }
    }.take(MAX_LESSONS)

    fun toJson(p: Packet): JSONObject = JSONObject().put("from", p.from).put("nonce", p.nonce).put("at", p.at)
        .put("lessons", JSONArray().apply { p.lessons.forEach { put(JSONObject().put("pkg", it.pkg).put("ver", it.ver).put("kind", it.kind)) } })

    /** Lê e confere: remetente é host curto válido, nonce no formato, recente, no máximo [MAX_LESSONS], cada lição bem formada. */
    fun fromJson(o: JSONObject?, now: Long, validHost: (String) -> String?): Packet? {
        o ?: return null
        val from = validHost(o.optString("from")) ?: return null
        val nonce = o.optString("nonce").takeIf { NONCE.matches(it) } ?: return null
        val at = o.optLong("at", -1)
        if (at <= 0 || kotlin.math.abs(now - at) > PACKET_MAX_AGE_MS) return null
        val a = o.optJSONArray("lessons") ?: return null
        if (a.length() > MAX_LESSONS) return null
        val l = List(a.length()) { a.optJSONObject(it) }.map { x ->
            val pkg = x?.optString("pkg")?.takeIf { it.length <= 150 && PKG.matches(it) } ?: return null
            val ver = x.optLong("ver", -1).takeIf { it > 0 } ?: return null
            val kind = x.optString("kind").takeIf { it in KINDS } ?: return null
            Lesson(pkg, ver, kind)
        }
        return Packet(from, nonce, at, l.distinct())
    }

    /** As lições que valem para esta TV: o app está instalado AQUI na MESMA versão, e ainda não virou recado. */
    fun relevant(lessons: List<Lesson>, installed: Map<String, Long>, told: Set<String>): List<Lesson> =
        lessons.filter { installed[it.pkg] == it.ver && key(it) !in told }

    fun key(l: Lesson) = "${l.pkg}@${l.ver}:${l.kind}"
}
