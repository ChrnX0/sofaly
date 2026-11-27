package br.paulo.tvlauncher.observer

import org.json.JSONArray
import org.json.JSONObject

/**
 * A IA que a Sofy consulta para um defeito que nenhuma receita resolveu (25/11/2026). Regras puras: SofyAiTest.
 *
 * Paulo: a chave é do DONO de cada TV e vale QUALQUER IA que ele escolher, inclusive uma local na rede da casa.
 * Por isso há só dois formatos de conversa: o do Claude e o "compatível com OpenAI" (que OpenAI, Perplexity, Gemini,
 * Groq, OpenRouter, Ollama e LM Studio falam). Cada [Provider] é só um endereço e um modelo de fábrica.
 *
 * Freios: a IA recebe só a ficha do caso ([SofyRepair.brief]: tipo, assunto, contagem, o que já foi tentado e os
 * eventos do barramento, que por regra não levam dado pessoal) e a lista de ferramentas; a resposta só vale como nomes
 * de ferramentas DA CAIXA ([SofyRepair.parseAiPlan]). Ela não manda comando, não escreve código, não muda freio.
 */
object SofyAiRules {
    enum class Format { CLAUDE, OPENAI }

    /**
     * [model] = o de fábrica para CONSERTAR (escolher ferramentas de uma lista fechada: o pequeno e barato basta).
     * [review] = o de fábrica para a REVISÃO das telas (ler, achar o que está errado e escrever regra: pede um mais forte).
     * Paulo, 25/11/2026: "pode fazer os 3" (modelo por tarefa, IA reserva, segunda opinião).
     */
    enum class Provider(val code: String, val format: Format, val base: String, val model: String, val needsKey: Boolean = true, val review: String = model) {
        CLAUDE("claude", Format.CLAUDE, "https://api.anthropic.com", "claude-haiku-5-5", review = "claude-sonnet-5-5"),
        OPENAI("openai", Format.OPENAI, "https://api.openai.com/v1", "gpt-4o-mini", review = "gpt-4o"),
        /** Endereço compatível com OpenAI da Perplexity (docs.perplexity.ai, "Orchestrator API", lido em 25/11/2026). */
        PERPLEXITY("perplexity", Format.OPENAI, "https://api.perplexity.ai/orchestrator/v1", "perplexity/glm-5.3"),
        GEMINI("gemini", Format.OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash"),
        GROQ("groq", Format.OPENAI, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        OPENROUTER("openrouter", Format.OPENAI, "https://openrouter.ai/api/v1", "openrouter/auto"),
        /** IA local na rede da casa (Ollama, LM Studio...) ou qualquer outro endereço compatível: o dono escreve. */
        CUSTOM("custom", Format.OPENAI, "", "", needsKey = false);

        companion object { fun of(code: String?) = entries.firstOrNull { it.code == code } }
    }

    /** [ownModel] = o dono escreveu o modelo (então a revisão usa o mesmo, salvo se ele escolheu outro para ela). */
    data class Settings(val provider: Provider, val base: String, val model: String, val key: String, val ownModel: Boolean = false)

    /** As tarefas: consertar (barato), revisar as telas (mais forte) e dar a segunda opinião sobre uma regra nova. */
    enum class Task { REPAIR, REVIEW, SECOND }

    /**
     * O mais indicado para cada tarefa, em ordem de preferência (Paulo, 25/11/2026: "faz a Sofy escolher os mais indicados
     * para cada tipo de tarefa"). Conserto = escolher ferramentas de uma lista fechada: o pequeno e rápido basta.
     * Revisão = ler a tela, achar o erro de sentido e escrever regra: o intermediário forte. Segunda opinião = julgar a
     * regra de outro modelo: o mais capaz (é uma pergunta por noite, no máximo). A Sofy usa o primeiro que a chave alcança.
     */
    fun preferred(p: Provider, t: Task): List<String> = when (p) {
        Provider.CLAUDE -> when (t) {
            Task.REPAIR -> listOf("claude-haiku-5-5", "claude-haiku-4-5")
            // Banco de prova de 27/11/2026: na revisão o Haiku achou todos os defeitos plantados, não deu alarme em tela
            // limpa e escreveu menos regra que dispara em texto certo (7%, Sonnet 19%), a 1/12 do preço. Na segunda
            // opinião o Fable acertou 30 de 30 (é uma pergunta por noite, o preço pesa pouco).
            Task.REVIEW -> listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-sonnet-5", "claude-sonnet-4-6", "claude-opus-5-5")
            Task.SECOND -> listOf("claude-fable-5-2", "claude-opus-5-5", "claude-opus-5", "claude-opus-4-8", "claude-sonnet-5-5")
        }
        Provider.OPENAI -> when (t) { Task.REPAIR -> listOf("gpt-4o-mini"); else -> listOf("gpt-4o") }
        else -> listOf(if (t == Task.REPAIR) p.model else p.review).filter { it.isNotEmpty() }
    }

    /**
     * O modelo desta tarefa entre os que a chave alcança ([available], lido da própria API; vazio = não deu para ler,
     * vale a preferência). Um id com data no fim ("claude-x-20260101") conta como o apelido sem data.
     */
    fun pick(p: Provider, t: Task, available: Set<String>): String? {
        val pref = preferred(p, t)
        if (available.isEmpty()) return pref.firstOrNull()
        return pref.firstOrNull { m -> available.any { it == m || it.startsWith("$m-") } }
    }

    /** O modelo da revisão: o que o dono escolheu para ela; senão o dele; senão o mais indicado que a chave alcança. */
    fun forReview(s: Settings, modelReview: String?, available: Set<String> = emptySet()): Settings {
        val m = modelReview?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (s.ownModel) s.model else pick(s.provider, Task.REVIEW, available) ?: s.provider.review.ifEmpty { s.model }
        return s.copy(model = m)
    }

    /**
     * Escalada do conserto (banco de prova de 27/11/2026): o Haiku acerta a 1ª ferramenta em 69% dos casos; nos de
     * julgamento (relatar em vez de mexer, causa escondida no contexto) só o Fable acertou, e com effort baixo ele custa
     * menos da metade sem perder acerto. Então: a 1ª pergunta de um caso vai ao modelo do conserto; se o caso volta a
     * pedir a IA (o plano anterior não resolveu), pergunta ao Fable com effort baixo. Só quando o dono não escolheu modelo.
     */
    val ESCALATE_MODELS = listOf("claude-fable-5-2", "claude-opus-5-5")
    const val ESCALATE_EFFORT = "low"

    fun escalate(c: SofyRepair.Case) = c.tried.any { it.tool == SofyRepair.Tool.ASK_AI }

    fun forEscalation(s: Settings, available: Set<String> = emptySet()): Settings? {
        if (s.provider != Provider.CLAUDE || s.ownModel) return null
        val m = if (available.isEmpty()) ESCALATE_MODELS.first()
            else ESCALATE_MODELS.firstOrNull { m -> available.any { it == m || it.startsWith("$m-") } } ?: return null
        return s.copy(model = m)
    }

    /** O effort de uma pergunta de conserto: baixo só na escalada. */
    fun repairEffort(s: Settings): String? = if (s.model in ESCALATE_MODELS) ESCALATE_EFFORT else null

    /** O do conserto: o do dono; senão o mais indicado que a chave alcança. */
    fun forRepair(s: Settings, available: Set<String> = emptySet()): Settings =
        if (s.ownModel) s else s.copy(model = pick(s.provider, Task.REPAIR, available) ?: s.model)

    /**
     * A ordem de quem perguntar. Conserto e revisão: a principal e, se ela falhar, a reserva. Segunda opinião: OUTRA IA
     * (a reserva, com o modelo de revisão dela); sem reserva, a principal com o modelo mais capaz, se for diferente do da
     * revisão (outro modelo ainda é outro olhar); sem nada diferente, ninguém (a regra espera a próxima noite).
     */
    fun chain(task: Task, primary: Settings?, backup: Settings?, reviewPrimary: String?, reviewBackup: String?,
              availPrimary: Set<String> = emptySet(), availBackup: Set<String> = emptySet()): List<Settings> = when (task) {
        Task.REPAIR -> listOfNotNull(primary?.let { forRepair(it, availPrimary) }, backup?.let { forRepair(it, availBackup) })
        Task.REVIEW -> listOfNotNull(primary?.let { forReview(it, reviewPrimary, availPrimary) }, backup?.let { forReview(it, reviewBackup, availBackup) })
        Task.SECOND -> {
            val outra = backup?.let { forReview(it, reviewBackup, availBackup) }
            val mesma = primary?.takeIf { !it.ownModel }?.let { p ->
                val revisao = forReview(p, reviewPrimary, availPrimary).model
                pick(p.provider, Task.SECOND, availPrimary)?.takeIf { it != revisao }?.let { p.copy(model = it) }
                    ?: forRepair(p, availPrimary).takeIf { it.model != revisao }
            }
            listOfNotNull(outra ?: mesma)
        }
    }

    /** Onde perguntar à API que modelos a chave alcança (GET), nos dois formatos. */
    fun modelsUrl(s: Settings) = if (s.provider.format == Format.CLAUDE) "${s.base}/v1/models?limit=1000" else "${s.base}/models"

    /** Os ids da lista de modelos (os dois formatos usam {"data":[{"id":...}]}). */
    fun modelIds(response: JSONObject): Set<String> = response.optJSONArray("data")?.let { a ->
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("id")?.trim()?.ifEmpty { null } }.toSet() }.orEmpty()

    /** O endereço escolhido (o de fábrica do provedor, ou o que o dono escreveu) e o modelo. Null = não dá para usar. */
    fun resolve(provider: Provider?, base: String?, model: String?, key: String?): Settings? {
        provider ?: return null
        val b = (base?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() && provider == Provider.CUSTOM } ?: provider.base)
        val own = model?.trim()?.takeIf { it.isNotEmpty() }
        val m = own ?: provider.model
        val k = key?.trim().orEmpty()
        if (b.isEmpty() || m.isEmpty() || !allowed(b)) return null
        if (provider.needsKey && k.isEmpty()) return null
        return Settings(provider, b, m, k, ownModel = own != null)
    }

    /** https sempre; http só para um endereço de dentro de casa (a IA local não tem certificado). */
    fun allowed(base: String): Boolean {
        val m = Regex("^(https?)://([^/:]+)(:\\d+)?(/.*)?$").find(base) ?: return false
        return m.groupValues[1] == "https" || homeHost(m.groupValues[2])
    }

    fun homeHost(h: String): Boolean {
        val host = h.lowercase()
        if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home") || !host.contains('.')) return true
        val p = host.split('.').map { it.toIntOrNull() ?: return false }
        if (p.size != 4) return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31) || (p[0] == 100 && p[1] in 64..127)
    }

    fun url(s: Settings) = if (s.provider.format == Format.CLAUDE) "${s.base}/v1/messages" else "${s.base}/chat/completions"

    fun headers(s: Settings): Map<String, String> = when (s.provider.format) {
        Format.CLAUDE -> mapOf("x-api-key" to s.key, "anthropic-version" to "2023-06-01", "content-type" to "application/json")
        Format.OPENAI -> mapOf("content-type" to "application/json") + if (s.key.isEmpty()) emptyMap() else mapOf("authorization" to "Bearer ${s.key}")
    }

    const val SYSTEM = "You diagnose defects of a TV launcher app (Android TV, no root, the app is Device Owner). " +
        "You get one defect case and the list of repair tools. Answer ONLY with a JSON object " +
        "{\"tools\":[...],\"cause\":\"one short sentence\"} where tools are 1 to 4 names from the list, best first. " +
        "Pick tools that address the likely cause given what changed before and what was already tried. No other text."

    fun prompt(c: SofyRepair.Case): String {
        val tools = SofyRepair.Tool.entries.filter { it != SofyRepair.Tool.ASK_AI }.joinToString(", ") { it.name }
        return "Case: ${SofyRepair.brief(c)}\nTools: $tools"
    }

    fun body(s: Settings, c: SofyRepair.Case): JSONObject = bodyFor(s, SYSTEM, prompt(c))

    /** Qualquer pergunta (conserto, revisor noturno): instrução de sistema + texto do usuário, nos dois formatos. */
    fun bodyFor(s: Settings, system: String, user: String): JSONObject = when (s.provider.format) {
        // Modelos Claude atuais pensam por padrão (pensamento adaptativo). Com 300 tokens o pensamento gastava tudo e a
        // resposta vinha sem texto (Sala 25/11: "resposta sem texto" num caso real, o teste curto passava). Espaço
        // folgado; sem mexer no esforço (modelos antigos que o dono escolher recusam o campo).
        Format.CLAUDE -> JSONObject().put("model", s.model).put("max_tokens", 4096).put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        Format.OPENAI -> JSONObject().put("model", s.model).put("max_tokens", 2048) // modelos que "pensam" gastam parte disso antes de responder
            .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
    }

    /** O texto da resposta, nos dois formatos. */
    fun text(format: Format, response: JSONObject): String? = when (format) {
        Format.CLAUDE -> response.optJSONArray("content")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.takeIf { o -> o.optString("type") == "text" }?.optString("text") }.joinToString("").ifEmpty { null }
        }
        Format.OPENAI -> response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.ifEmpty { null }
    }

    /** Tokens gastos (entrada + saída) que a própria resposta informa; 0 se ela não diz. */
    fun usage(response: JSONObject): Long {
        val u = response.optJSONObject("usage") ?: return 0
        val claude = u.optLong("input_tokens") + u.optLong("output_tokens")
        return if (claude > 0) claude else u.optLong("prompt_tokens") + u.optLong("completion_tokens")
    }

    /** Entrada, saída e leitura de cache, separadas (o banco de prova precisa do preço: saída custa 5x a entrada). */
    fun usageParts(response: JSONObject): LongArray {
        val u = response.optJSONObject("usage") ?: return LongArray(3)
        val i = u.optLong("input_tokens"); val o = u.optLong("output_tokens")
        return if (i + o > 0) longArrayOf(i, o, u.optLong("cache_read_input_tokens"))
            else longArrayOf(u.optLong("prompt_tokens"), u.optLong("completion_tokens"), 0)
    }

    /** O esforço do pensamento (low, medium, high...), só no formato do Claude; vazio = o padrão do modelo. */
    fun withEffort(s: Settings, body: JSONObject, effort: String?): JSONObject {
        if (effort.isNullOrBlank() || s.provider.format != Format.CLAUDE) return body
        return body.put("output_config", JSONObject().put("effort", effort.trim()))
    }

    /** O mês corrente como número (202611), para zerar o contador na virada. */
    fun month(nowMs: Long): Int {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        return c.get(java.util.Calendar.YEAR) * 100 + c.get(java.util.Calendar.MONTH) + 1
    }

    /** Ainda cabe perguntar? [budget] 0 = sem teto (o dono escolheu). */
    fun withinBudget(used: Long, budget: Long) = budget <= 0 || used < budget

    /** Por que veio sem texto (para o relatório): o motivo de parada e os tipos de bloco, sem conteúdo. */
    fun shape(response: JSONObject): String {
        val stop = response.optString("stop_reason").ifEmpty { response.optJSONArray("choices")?.optJSONObject(0)?.optString("finish_reason").orEmpty() }
        val tipos = response.optJSONArray("content")?.let { a -> (0 until a.length()).map { a.optJSONObject(it)?.optString("type").orEmpty() } }.orEmpty()
        return "parada=${stop.ifEmpty { "?" }} blocos=${tipos.joinToString(",").ifEmpty { "-" }}"
    }

    /** As ferramentas que a IA escolheu (só as da caixa) e a causa em uma frase. Texto solto em volta do JSON é tolerado. */
    fun plan(answer: String?): Pair<List<SofyRepair.Tool>, String> {
        answer ?: return emptyList<SofyRepair.Tool>() to ""
        val i = answer.indexOf('{'); val j = answer.lastIndexOf('}')
        if (i < 0 || j <= i) return emptyList<SofyRepair.Tool>() to ""
        val o = runCatching { JSONObject(answer.substring(i, j + 1)) }.getOrNull() ?: return emptyList<SofyRepair.Tool>() to ""
        val names = o.optJSONArray("tools")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        return SofyRepair.parseAiPlan(names) to o.optString("cause").take(160)
    }
}
