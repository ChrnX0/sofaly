package br.paulo.tvlauncher.observer

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * A rede de Sofys (Paulo, 27/11/2026, estudo "Sofy coletiva", proposta B+): o pacote do dia que esta TV manda ao banco de
 * todos os Sofalys (server/api/src/fleet.js), só com "Ajudar outras casas" ligado.
 *
 * Por quê: cada Sofaly conserta sozinho, mas só aprende com a própria casa. Para quê: o banco junta o que deu errado,
 * COMO (o que mudou antes, o estado da TV em faixas), POR QUÊ (causa numa lista fechada) e o que consertou, e devolve às
 * TVs as receitas que funcionam. Como: montado SÓ com campos fixos e listas fechadas. Daqui nunca sai texto livre: nem o
 * detalhe do caso, nem o detalhe dos eventos do barramento, nem frase da IA, nem hora exata, nem a lista de apps (só o
 * pacote do app que deu defeito). O código da casa troca a cada dia ([dayCode]). O servidor recusa qualquer campo a mais
 * (segunda trava). Pura: SofyFleetTest (inclui o teste "nunca sai", com uma TV cheia de dados falsos).
 */
object SofyFleet {
    const val VERSION = 1
    const val MAX_CASES = 20
    const val MAX_TRIES = 6
    const val MAX_BEFORE = 6

    // As listas fechadas, iguais às de fleet.js (SofyFleetTest confere uma contra a outra).
    val AREAS = setOf("shell", "accessibility", "wireless_debug", "tunnel", "home", "perm", "app", "tile", "screen", "data",
        "feature", "system", "bus", "other")
    val CAUSES = setOf("network", "app", "memory", "storage", "update", "setting", "permission", "access", "sofaly", "unknown")
    val BEFORE = setOf("app_updated", "sofaly_updated", "system_updated", "rebooted", "network_changed", "setting_changed",
        "memory_low", "plugin_changed", "backup_restored", "other")

    private val NAME = Regex("^[a-z0-9_.]{1,60}$")
    private val BUS_KEY = Regex("^([a-z]+)\\.([a-z0-9_]+)")

    private const val DAY_MS = 86_400_000L
    private const val WINDOW_MS = 24 * 3_600_000L
    private const val MAX_WINDOW_MS = 48 * 3_600_000L
    const val RETRY_MS = 3_600_000L

    /**
     * Hora de mandar? No máximo um envio que deu certo por dia UTC (#542): o servidor guarda um pacote por casa por dia
     * e o segundo do mesmo dia apagaria o primeiro. [okAt] = último envio aceito; [triedAt] = última tentativa.
     */
    fun shouldSend(now: Long, okAt: Long, triedAt: Long): Boolean =
        (okAt <= 0L || now / DAY_MS != okAt / DAY_MS) && now - triedAt >= RETRY_MS

    /** Desde quando vão os casos: desde o último envio aceito (até 48 h para trás); sem envio recente, 24 h. */
    fun since(now: Long, okAt: Long): Long = if (okAt in (now - MAX_WINDOW_MS)..now) okAt else now - WINDOW_MS

    /** O aparelho, como o Android conta ([maker]/[model] = Build.MANUFACTURER/Build.MODEL, de fábrica). */
    data class Device(val maker: String, val model: String, val googleTv: Boolean, val androidTv: Boolean, val sdk: Int,
                      val is64: Boolean, val ramMb: Long, val diskGb: Long)

    /** O estado da TV quando o caso abriu. Vira faixas: hora exata nunca sai. */
    data class State(val uptimeMs: Long, val memoryLow: Boolean, val net: String, val hour: Int)

    /** O código da casa no dia: hash de um segredo que só esta TV tem + o dia. Troca todo dia; não volta ao segredo. */
    fun dayCode(secret: ByteArray, day: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(secret); md.update("|$day".toByteArray())
        return md.digest().take(8).joinToString("") { "%02x".format(it) }
    }

    private fun deviceText(s: String) = s.filter { it.isLetterOrDigit() && it.code < 128 || it in " ._+-" }.trim().take(40).ifEmpty { "unknown" }

    fun net(n: String) = if (n in setOf("wifi", "ethernet", "none")) n else "other"

    /** Só o modelo (#541: "O modelo da TV" é o que o consentimento promete). Versão do Android, RAM e disco ficam. */
    fun device(d: Device): JSONObject = JSONObject()
        .put("maker", deviceText(d.maker)).put("model", deviceText(d.model))
        .put("platform", if (d.googleTv) "googletv" else if (d.androidTv) "androidtv" else "other")
        .put("abi", if (d.is64) "64" else "32")

    /** Onde: a parte do assunto antes de ":" ("app:com.x" -> app). */
    fun area(subject: String): String = subject.substringBefore(':').let { if (it in AREAS) it else "other" }

    /**
     * O alvo: um nome do nosso código (feature:mcp, perm:...). O pacote de um app NÃO sai (#541: "Seus apps" está em
     * "O que nunca sai"); o caso de app vai só como categoria. Qualquer outra coisa fica vazia.
     */
    fun target(subject: String): String {
        if (!subject.contains(':')) return ""
        val a = area(subject)
        if (a == "app" || a == "other") return ""
        return subject.substringAfter(':').lowercase().let { if (NAME.matches(it)) it else "" }
    }

    /** O que mudou antes, pela CHAVE do evento do barramento ("fonte.tipo"); o detalhe do evento nunca é lido. */
    fun before(context: List<String>): List<String> = context.mapNotNull { line ->
        val m = BUS_KEY.find(line) ?: return@mapNotNull null
        val (src, kind) = m.destructured
        when {
            src == "update" && (kind.contains("launcher") || kind.contains("sofaly") || kind.contains("self")) -> "sofaly_updated"
            src == "update" && (kind.contains("google") || kind.contains("system")) -> "system_updated"
            src == "update" -> "app_updated"
            kind.contains("boot") || kind.contains("reboot") -> "rebooted"
            src == "network" -> "network_changed"
            kind.contains("setting") || kind.contains("pref") || kind.contains("config") -> "setting_changed"
            kind.contains("memory") -> "memory_low"
            src == "plugin" -> "plugin_changed"
            src == "backup" && kind.contains("restor") -> "backup_restored"
            else -> "other"
        }
    }.distinct().sorted().take(MAX_BEFORE)

    /** Por quê, numa lista fechada. A IA central refina depois; aqui é a primeira leitura, sem frase. */
    fun cause(c: SofyRepair.Case, before: List<String>, memoryLow: Boolean): String = when {
        c.type == SofyRepair.Type.ACCESS_LOST -> "access"
        c.type == SofyRepair.Type.PERMISSION_LOST || c.subject.startsWith("perm:") -> "permission"
        c.subject.startsWith("system:") || "system_updated" in before -> "update"
        c.subject == "home" || c.subject.startsWith("feature:sofy") -> "sofaly"
        SofyRepair.Need.NETWORK in c.needs || c.subject == "tunnel" -> "network"
        memoryLow || "memory_low" in before -> "memory"
        "app_updated" in before && c.subject.startsWith("app:") -> "update"
        "setting_changed" in before -> "setting"
        c.subject.startsWith("app:") -> "app"
        else -> "unknown"
    }

    private fun result(r: SofyRepair.Result) = when (r) {
        SofyRepair.Result.FIXED -> "fixed"; SofyRepair.Result.NOT_FIXED -> "not_fixed"
        SofyRepair.Result.FAILED -> "failed"; SofyRepair.Result.PENDING -> "pending"
    }

    /** Um caso vira linha. Caso de teste (posto pelo adb) nunca vai. */
    fun case(c: SofyRepair.Case, s: State): JSONObject? {
        if (SofyRepair.isTest(c)) return null
        val b = before(c.context)
        return JSONObject()
            .put("type", c.type.name).put("area", area(c.subject)).put("target", target(c.subject))
            // #541: só o que o consentimento promete. O que mudou antes só serve para a categoria da causa (fica aqui);
            // tempo ligada, hora do dia, recaída e contagem não saem.
            .put("cause", cause(c, b, s.memoryLow))
            .put("state", JSONObject().put("memory", if (s.memoryLow) "low" else "ok").put("net", net(s.net)))
            .put("tries", JSONArray().apply {
                c.tried.takeLast(MAX_TRIES).forEach {
                    put(JSONObject().put("tool", it.tool.name).put("by", if (it.tool in c.aiPlan) "ai" else "rule").put("result", result(it.result)))
                }
            })
            .put("outcome", when (c.state) { SofyRepair.State.FIXED -> "fixed"; SofyRepair.State.GIVEN_UP -> "gave_up"; else -> "open" })
    }

    /** O pacote do dia inteiro. [states] = o estado de cada caso (pela chave); sem estado, o [fallback]. */
    fun packet(code: String, app: Long, d: Device, cases: List<SofyRepair.Case>, states: Map<String, State>, fallback: State): JSONObject =
        JSONObject().put("v", VERSION).put("code", code).put("app", app).put("device", device(d))
            .put("cases", JSONArray().apply {
                cases.sortedByDescending { it.lastAt }.mapNotNull { case(it, states[it.key] ?: fallback) }.take(MAX_CASES).forEach { put(it) }
            })
}
