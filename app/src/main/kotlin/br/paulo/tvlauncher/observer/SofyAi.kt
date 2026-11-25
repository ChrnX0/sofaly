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

    enum class Provider(val code: String, val format: Format, val base: String, val model: String, val needsKey: Boolean = true) {
        CLAUDE("claude", Format.CLAUDE, "https://api.anthropic.com", "claude-haiku-5-5"),
        OPENAI("openai", Format.OPENAI, "https://api.openai.com/v1", "gpt-4o-mini"),
        PERPLEXITY("perplexity", Format.OPENAI, "https://api.perplexity.ai", "sonar"),
        GEMINI("gemini", Format.OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash"),
        GROQ("groq", Format.OPENAI, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        OPENROUTER("openrouter", Format.OPENAI, "https://openrouter.ai/api/v1", "openrouter/auto"),
        /** IA local na rede da casa (Ollama, LM Studio...) ou qualquer outro endereço compatível: o dono escreve. */
        CUSTOM("custom", Format.OPENAI, "", "", needsKey = false);

        companion object { fun of(code: String?) = entries.firstOrNull { it.code == code } }
    }

    data class Settings(val provider: Provider, val base: String, val model: String, val key: String)

    /** O endereço escolhido (o de fábrica do provedor, ou o que o dono escreveu) e o modelo. Null = não dá para usar. */
    fun resolve(provider: Provider?, base: String?, model: String?, key: String?): Settings? {
        provider ?: return null
        val b = (base?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() && provider == Provider.CUSTOM } ?: provider.base)
        val m = model?.trim()?.takeIf { it.isNotEmpty() } ?: provider.model
        val k = key?.trim().orEmpty()
        if (b.isEmpty() || m.isEmpty() || !allowed(b)) return null
        if (provider.needsKey && k.isEmpty()) return null
        return Settings(provider, b, m, k)
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

    fun body(s: Settings, c: SofyRepair.Case): JSONObject = when (s.provider.format) {
        Format.CLAUDE -> JSONObject().put("model", s.model).put("max_tokens", 300).put("system", SYSTEM)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt(c))))
        Format.OPENAI -> JSONObject().put("model", s.model).put("max_tokens", 300)
            .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM))
                .put(JSONObject().put("role", "user").put("content", prompt(c))))
    }

    /** O texto da resposta, nos dois formatos. */
    fun text(format: Format, response: JSONObject): String? = when (format) {
        Format.CLAUDE -> response.optJSONArray("content")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.takeIf { o -> o.optString("type") == "text" }?.optString("text") }.joinToString("").ifEmpty { null }
        }
        Format.OPENAI -> response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.ifEmpty { null }
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
