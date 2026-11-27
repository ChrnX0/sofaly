package br.paulo.tvlauncher.observer

import android.content.Context
import br.paulo.tvlauncher.Http
import org.json.JSONObject

/**
 * A parte Android de [SofyRecipes]: uma vez por dia baixa as receitas da rede de Sofys e, EM SOMBRA, confere cada caso
 * que esta TV consertou contra elas (a receita teria acertado de primeira?). Nada do conserto muda aqui.
 *
 * Receber não depende de "Ajudar outras casas" (igual às fichas comunitárias): o pedido é igual para todas as TVs e não
 * leva nada desta casa. O placar (acertos e erros por receita) fica nas prefs "sofy_recipes", fora do backup: é desta
 * TV e se refaz sozinho.
 */
object SofyRecipesTv {
    const val URL_RECIPES = "https://api.sofaly.tv/v1/community/recipes"
    const val PREFS = "sofy_recipes"
    private const val GAP_MS = 20 * 3_600_000L
    private const val MAX_JUDGED = 200

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Pelo autoexame diário. Rede: chamar fora da tela. */
    fun daily(ctx: Context, now: Long) {
        val app = ctx.applicationContext
        val p = prefs(app)
        if (now - p.getLong("fetched_at", 0L) >= GAP_MS) {
            p.edit().putLong("fetched_at", now).apply()
            Http.getTextOrNull(URL_RECIPES, maxBytes = 256 * 1024)?.let { text ->
                val list = SofyRecipes.parse(runCatching { JSONObject(text) }.getOrNull())
                p.edit().putString("list", text).apply()
                SofyBusTv.emit(app, SofyBus.Source.SOFY, "recipes_received", detail = "${list.size} receitas")
            }
        }
        judge(app)
    }

    fun recipes(ctx: Context): List<SofyRecipes.Recipe> =
        SofyRecipes.parse(runCatching { JSONObject(prefs(ctx).getString("list", null) ?: "{}") }.getOrNull())

    /** Confere os casos consertados ainda não conferidos. Cada caso conta uma vez (pela chave + quando consertou). */
    @Synchronized private fun judge(app: Context) {
        val recipes = recipes(app)
        if (recipes.isEmpty()) return
        val p = prefs(app)
        val judged = runCatching { org.json.JSONArray(p.getString("judged", "[]")) }.getOrDefault(org.json.JSONArray())
        val seen = (0 until judged.length()).map { judged.optString(it) }.toMutableList()
        val score = runCatching { JSONObject(p.getString("score", null) ?: "{}") }.getOrDefault(JSONObject())
        var mudou = false
        for (c in SofyRepairTv.cases(app)) {
            val mark = "${c.key}@${c.lastAt}"
            if (mark in seen) continue
            val (r, v) = SofyRecipes.judge(c, recipes) ?: continue
            seen += mark; mudou = true
            val s = score.optJSONObject(r.id) ?: JSONObject().put("hit", 0).put("miss", 0)
            val k = if (v == SofyRecipes.Verdict.HIT) "hit" else "miss"
            score.put(r.id, s.put(k, s.optInt(k) + 1))
            ObserverTv.logShadow(app, "recipe_shadow", "${r.id} ${v.name} (receita: ${r.tools.joinToString(",")})")
        }
        if (mudou) p.edit().putString("score", score.toString())
            .putString("judged", org.json.JSONArray(seen.takeLast(MAX_JUDGED)).toString()).apply()
    }

    /** O placar da sombra (painel/MCP): por receita, quantas vezes ela teria acertado e errado. */
    fun score(ctx: Context): JSONObject = runCatching { JSONObject(prefs(ctx).getString("score", null) ?: "{}") }.getOrDefault(JSONObject())
}
