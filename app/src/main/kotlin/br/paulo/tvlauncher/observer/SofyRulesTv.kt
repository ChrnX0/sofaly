package br.paulo.tvlauncher.observer

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * A parte Android do revisor noturno ([SofyRules]). Chamado pela conferência das telas de madrugada, com a tela já lida
 * (os textos que a TV mostra de verdade): confere as regras aprendidas e, se o dono configurou uma IA e o teto do mês
 * deixa, pergunta a ela o que está errado e que regras novas valem. Uma pergunta por noite (uma tela por noite).
 * As regras ficam no prefs do conserto (são desta TV; não vão no backup).
 */
object SofyRulesTv {
    private const val TAG = "TvLauncher.SofyRegras"
    private const val PREFS = "sofy_repair"
    private const val KEY = "ai_rules"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun rules(ctx: Context) = SofyRules.fromJson(prefs(ctx).getString(KEY, null))

    /** Rede: chamar fora da tela (já roda no Fundo da conferência). */
    fun onScreen(ctx: Context, screen: String, texts: List<String>) {
        val app = ctx.applicationContext
        var rules = rules(app)
        val (after, broken) = SofyRules.evaluate(rules, screen, texts)
        for ((r, v) in broken) {
            val why = "regra ${r.id} (${r.kind.name.lowercase()} ${r.pattern}) em $screen: \"${v.take(60)}\""
            Log.w(TAG, why)
            ObserverTv.logShadow(app, "self_defect", why)   // a tarefa diária abre a issue a partir daqui
            SofyRepairTv.signal(app, SofyRepair.Type.SCREEN_BROKEN, "rule:${r.id}", v.take(80))
        }
        for (r in after) rules.firstOrNull { it.id == r.id }?.let { old ->
            if (old.state != r.state) ObserverTv.logShadow(app, "ai_rule_${r.state.name.lowercase()}", "${r.pattern} ($screen)" +
                if (r.state == SofyRules.State.DROPPED) ": alarme falso em \"${r.example}\"" else "")
        }
        rules = after
        if (SofyAi.configured(app) && texts.isNotEmpty()) {
            val answer = SofyAi.ask(app, SofyRules.SYSTEM, SofyRules.prompt(screen, texts, rules))
            val review = SofyRules.parse(answer, screen, texts, rules, System.currentTimeMillis())
            for (d in review.defects) {
                Log.w(TAG, "IA em $screen: $d")
                ObserverTv.logShadow(app, "self_defect", "IA viu em $screen: $d")
                SofyRepairTv.signal(app, SofyRepair.Type.SCREEN_BROKEN, "screen:$screen", ("IA: $d").take(120))
            }
            // Segunda opinião: só entra na sombra a regra com que OUTRA IA concorda (Paulo, 25/11/2026).
            val aceitas = if (review.rules.isEmpty()) emptyList()
                else SofyRules.agreed(SofyAi.second(app, SofyRules.SECOND_SYSTEM, SofyRules.secondPrompt(screen, texts, review.rules)), review.rules)
            for (r in review.rules - aceitas.toSet()) ObserverTv.logShadow(app, "ai_rule_rejected", "${r.kind.name} ${r.pattern} ($screen): a segunda IA não concordou")
            for (r in aceitas) ObserverTv.logShadow(app, "ai_rule_new", "${r.kind.name} ${r.pattern} ($screen): ${r.why}")
            rules = SofyRules.merge(rules, aceitas)
            Log.i(TAG, "$screen: ${review.defects.size} defeitos, ${aceitas.size}/${review.rules.size} regras novas aceitas, ${rules.size} guardadas")
        }
        prefs(app).edit().putString(KEY, SofyRules.toJson(rules)).apply()
    }

    /** Regras que vieram de outras casas: as que esta TV ainda não tem (nem derrubou) entram na sombra. */
    fun addFromCommunity(ctx: Context, incoming: List<SofyRules.Rule>): Int {
        val app = ctx.applicationContext
        val rules = rules(app)
        val news = incoming.filter { n -> rules.none { it.id == n.id } }.take(SofyRules.PER_NIGHT)
        if (news.isEmpty()) return 0
        for (r in news) ObserverTv.logShadow(app, "ai_rule_new", "${r.kind.name} ${r.pattern} (${r.screen}): ${r.why}")
        prefs(app).edit().putString(KEY, SofyRules.toJson(SofyRules.merge(rules, news))).apply()
        return news.size
    }

    fun json(ctx: Context): JSONObject {
        val rs = rules(ctx)
        return JSONObject().put("shadow", rs.count { it.state == SofyRules.State.SHADOW })
            .put("active", rs.count { it.state == SofyRules.State.ACTIVE }).put("dropped", rs.count { it.state == SofyRules.State.DROPPED })
            .put("rules", JSONArray(SofyRules.toJson(rs)))
    }
}
