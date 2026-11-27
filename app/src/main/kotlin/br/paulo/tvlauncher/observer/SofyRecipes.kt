package br.paulo.tvlauncher.observer

import org.json.JSONObject

/**
 * As receitas da rede de Sofys (passo 4; servidor em server/api/src/recipes.js), a parte pura.
 *
 * Por quê: o banco de todos os Sofalys sabe qual ferramenta conserta cada tipo de defeito nas outras casas. Para quê:
 * esta TV tentar primeiro o que já deu certo lá fora. Como, e com que cuidado: uma receita é SÓ DADO (tipo, área, alvo,
 * causa e até 3 ferramentas da lista fechada [SofyRepair.Tool]); tudo que não casar com as listas daqui é descartado.
 * E toda receita começa em SOMBRA: [judge] só confere, quando um caso daqui é consertado, se a receita teria acertado
 * (a ferramenta que consertou era a primeira dela). Nada muda no conserto. Usar de verdade é um passo à parte, só
 * depois de noites de acerto. Pura: SofyRecipesTest.
 */
object SofyRecipes {
    const val VERSION = 1
    const val MAX_TOOLS = 3
    const val MAX_RECIPES = 200

    private val NAME = Regex("^[a-z0-9_.]{0,60}$")
    private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    /** Pedir à IA e relatar não consertam nada sozinhos (igual ao servidor). */
    private val NOT_A_FIX = setOf(SofyRepair.Tool.ASK_AI, SofyRepair.Tool.REPORT)

    data class Recipe(val id: String, val type: SofyRepair.Type, val area: String, val target: String, val cause: String,
                      val tools: List<SofyRepair.Tool>, val homes: Int)

    enum class Verdict { HIT, MISS }

    /**
     * A chave pública das receitas (ECDSA P-256, SPKI em base64). A privada é o segredo RECIPES_SIGNING_KEY do Worker
     * (cópias só nos arquivos do Paulo, nunca no git). Trocar de chave = trocar aqui e lançar versão nova.
     */
    const val PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEV6qUDkEbBqF412fRzFl0TwJh7PpH4z89xSeYwJ9te+RSXZkksymkti7ajAeeRZQHMs6zndoeOZ4gK4jsYyWB+w=="
    /** Lista mais velha que isto (em dias) é recusada: ninguém reenvia uma lista antiga assinada para sempre. */
    const val MAX_AGE_DAYS = 7
    private const val DAY_MS = 86_400_000L

    /**
     * Abre a resposta do servidor: `{v, data, sig}`, onde `data` é o texto JSON ({day, recipes}) e `sig` a assinatura
     * crua (R||S, 64 bytes, base64) sobre esse texto exato. Sem assinatura certa, versão certa ou lista recente: null.
     */
    fun open(o: JSONObject?, now: Long, key: String = PUBLIC_KEY): JSONObject? {
        if (o == null || o.optInt("v") != VERSION) return null
        val data = o.optString("data").takeIf { it.isNotEmpty() } ?: return null
        val sig = o.optString("sig").takeIf { it.isNotEmpty() && it != "null" } ?: return null
        val ok = runCatching {
            val pub = java.security.KeyFactory.getInstance("EC")
                .generatePublic(java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(key)))
            java.security.Signature.getInstance("SHA256withECDSA").run {
                initVerify(pub); update(data.toByteArray(Charsets.UTF_8)); verify(rawToDer(java.util.Base64.getDecoder().decode(sig)))
            }
        }.getOrDefault(false)
        if (!ok) return null
        val inner = runCatching { JSONObject(data) }.getOrNull() ?: return null
        val day = inner.optLong("day", -1L)
        if (day < 0 || now / DAY_MS - day > MAX_AGE_DAYS) return null
        return inner
    }

    /** Assinatura crua R||S (o que a WebCrypto devolve) -> DER (o que o Java confere). */
    fun rawToDer(raw: ByteArray): ByteArray {
        require(raw.size == 64) { "assinatura crua deve ter 64 bytes" }
        fun inteiro(b: ByteArray): ByteArray {
            var i = 0
            while (i < b.size - 1 && b[i] == 0.toByte()) i++
            val t = b.copyOfRange(i, b.size)
            return if (t[0] < 0) byteArrayOf(0) + t else t
        }
        val r = inteiro(raw.copyOfRange(0, 32)); val s = inteiro(raw.copyOfRange(32, 64))
        val corpo = byteArrayOf(2, r.size.toByte()) + r + byteArrayOf(2, s.size.toByte()) + s
        return byteArrayOf(0x30, corpo.size.toByte()) + corpo
    }

    /** Lê a lista já aberta por [open]. Receita com qualquer campo fora das listas daqui é descartada, sem aviso. */
    fun parse(o: JSONObject?): List<Recipe> {
        if (o == null) return emptyList()
        val a = o.optJSONArray("recipes") ?: return emptyList()
        return (0 until minOf(a.length(), MAX_RECIPES)).mapNotNull { i -> runCatching { one(a.getJSONObject(i)) }.getOrNull() }
    }

    private fun one(r: JSONObject): Recipe? {
        val m = r.getJSONObject("match")
        val type = SofyRepair.Type.entries.firstOrNull { it.name == m.getString("type") } ?: return null
        val area = m.getString("area").takeIf { it in SofyFleet.AREAS } ?: return null
        val cause = m.getString("cause").takeIf { it in SofyFleet.CAUSES } ?: return null
        val target = m.optString("target")
        if (target.isNotEmpty() && !(if (area == "app") PKG.matches(target) && target.length <= 150 else NAME.matches(target))) return null
        val ta = r.getJSONArray("tools")
        if (ta.length() !in 1..MAX_TOOLS) return null
        val tools = (0 until ta.length()).map { i -> SofyRepair.Tool.entries.firstOrNull { it.name == ta.getString(i) } ?: return null }
        if (tools.any { it in NOT_A_FIX } || tools.distinct().size != tools.size) return null
        val homes = r.optJSONObject("evidence")?.optInt("homes", 0) ?: 0
        return Recipe("${type.name}|$area|$target|$cause", type, area, target, cause, tools, homes)
    }

    /** A receita que vale para este caso (mesmo tipo, área, alvo e causa, lidos do jeito que a TV manda). */
    fun forCase(c: SofyRepair.Case, recipes: List<Recipe>, memoryLow: Boolean = false): Recipe? {
        val area = SofyFleet.area(c.subject)
        val target = SofyFleet.target(c.subject)
        val cause = SofyFleet.cause(c, SofyFleet.before(c.context), memoryLow)
        return recipes.firstOrNull { it.type == c.type && it.area == area && it.target == target && it.cause == cause }
    }

    /** Sombra: o caso foi consertado aqui; a receita teria acertado de primeira? null = sem receita ou sem conserto. */
    fun judge(c: SofyRepair.Case, recipes: List<Recipe>, memoryLow: Boolean = false): Pair<Recipe, Verdict>? {
        if (c.state != SofyRepair.State.FIXED || SofyRepair.isTest(c)) return null
        val fixedBy = c.tried.lastOrNull { it.result == SofyRepair.Result.FIXED }?.tool ?: return null
        val r = forCase(c, recipes, memoryLow) ?: return null
        return r to if (r.tools.first() == fixedBy) Verdict.HIT else Verdict.MISS
    }
}
