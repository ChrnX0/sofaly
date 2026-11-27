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

    /** Lê a resposta do servidor. Receita com qualquer campo fora das listas daqui é descartada, sem aviso. */
    fun parse(o: JSONObject?): List<Recipe> {
        if (o == null || o.optInt("v") != VERSION) return emptyList()
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
