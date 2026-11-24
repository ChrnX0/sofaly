package br.paulo.tvlauncher.observer

import android.content.Context
import android.media.AudioManager
import android.util.Log
import br.paulo.tvlauncher.AvisoTv
import br.paulo.tvlauncher.family.Diario
import java.util.Calendar
import org.json.JSONObject

/**
 * A ponte Android da Sofy (20/11/2026): pega os recados do Observador ([ObserverTv.notes]) que ainda não foram vistos,
 * passa cada um pela regra ([SofyPolicy.decide]) e entrega o que é para agora: a pílula com a Sofy desenhada
 * ([SofyDrawable], movimento da expressão) e a voz ([SofyVoice]). Guarda o teto de falas e os tipos calados.
 *
 * Hoje só [SofyPolicy.Place.OVERLAY] aparece daqui. HOME_CARD fica na fila para o cartão da home (próximo passo), MORNING
 * vai para o resumo da manhã do Observador e DIARY já está no Diário; nenhum desses é marcado como visto aqui.
 */
object SofyTv {
    private const val TAG = "TvLauncher.Sofy"
    private const val PREFS = "sofy"
    private const val DAY_MS = 86_400_000L

    /** Olha a fila agora. Seguro de chamar de qualquer thread e a toda hora (só age em recado novo). */
    @Synchronized fun present(ctx: Context) {
        val app = ctx.applicationContext
        val now = now(app)
        val notes = runCatching { ObserverTv.notes(app) }.getOrDefault(emptyList()).filter { !it.seen }
        for (n in notes.reversed()) {   // do mais velho para o mais novo
            val d = SofyPolicy.decide(n, now)
            if (d.place != SofyPolicy.Place.OVERLAY) continue
            val title = texto(app, n.titleKey, n.args) ?: continue.also { Log.w(TAG, "sem frase para ${n.titleKey}") }
            val text = texto(app, n.textKey, n.args).orEmpty()
            val face = SofyDrawable(app, d.expression)
            val r = AvisoTv.mostrar(app, title, text, SofyPolicy.secondsOnScreen("$title $text"), false, false, icone = face)
            if (r != AvisoTv.Resultado.MOSTRADO) { Log.i(TAG, "${n.typeKey}: não mostrou ($r)"); continue }
            SofyVoice.say(app, d.voice, listOf(title, text).filter { it.isNotBlank() }.joinToString(". "), face)
            if (SofyPolicy.countsForBudget(n, d)) registrarFala(app, now.at)
            ObserverTv.markSeen(app, n.id)
            Log.i(TAG, "${n.typeKey}: ${d.place} ${d.expression} voz=${d.voice} (${d.why})")
            return   // uma por vez: a próxima espera a próxima olhada (e o teto)
        }
    }

    /** Uma linha da Sofy na Ilha da home: o título do recado, a cara dele e o id (para marcar visto). */
    data class IslandLine(val text: String, val expression: SofyPolicy.Expression, val ids: List<String>)

    /**
     * O recado da Sofy para a pílula da Ilha agora ([SofyPolicy.islandNote]), ou null. Fora da thread da tela (lê prefs).
     * A primeira vez que um recado entra na Ilha conta no teto de falas; depois ele só continua lá.
     */
    @Synchronized fun islandLine(ctx: Context): IslandLine? {
        val app = ctx.applicationContext
        val now = now(app)
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val on = p.getString("island", "").orEmpty().split(',').filter { it.isNotEmpty() }
        val notes = runCatching { ObserverTv.notes(app) }.getOrDefault(emptyList())
        val n = SofyPolicy.islandNote(notes, now, on.toSet()) ?: return null
        val title = texto(app, n.titleKey, n.args) ?: return null
        if (n.id !in on) {
            registrarFala(app, now.at)
            p.edit().putString("island", (on + n.id).takeLast(20).joinToString(",")).apply()
            runCatching { Diario.anotar(app, "tv", "Sofy na Ilha", title) }
        }
        return IslandLine(title, SofyPolicy.decide(n, now).expression, listOf(n.id))
    }

    /** O resumo da manhã para a Ilha ([SofyPolicy.morningNotes]): "Enquanto você dormia: …", ou null. */
    fun morningLine(ctx: Context): IslandLine? {
        val app = ctx.applicationContext
        val now = now(app)
        val notes = runCatching { ObserverTv.notes(app) }.getOrDefault(emptyList())
        val l = SofyPolicy.morningNotes(notes, now) { at -> Calendar.getInstance().apply { timeInMillis = at }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) } }
        val titles = l.mapNotNull { n -> texto(app, n.titleKey, n.args)?.let { n.id to it } }
        if (titles.isEmpty()) return null
        return IslandLine(app.getString(br.paulo.tvlauncher.R.string.sofy_while_away, titles.joinToString("  ·  ") { it.second }),
            SofyPolicy.Expression.HAPPY, titles.map { it.first })
    }

    /** A Ilha mostrou o resumo da manhã: os recados dele não voltam. */
    fun shown(ctx: Context, line: IslandLine) = line.ids.forEach { ObserverTv.markSeen(ctx.applicationContext, it) }

    /**
     * Uma voz só (24/11/2026, ideia 3): quem pergunta alguma coisa à pessoa fora da Sofy (Sugestões, e o que vier) pede
     * licença aqui e passa pelo MESMO freio dela: 1 fala por hora e 3 por dia, nada de madrugada, nada por cima de vídeo
     * ou música, e o tipo recusado 2 vezes seguidas fica calado 7 dias. [typeKey] = "origem.tipo" (ex. "suggestion.FILTRO_NOTURNO").
     */
    fun mayAsk(ctx: Context, typeKey: String): Boolean {
        val now = now(ctx.applicationContext)
        return !SofyPolicy.night(now.minuteOfDay) && !now.mediaPlaying && SofyPolicy.withinBudget(now.shownAt, now.at) &&
            !SofyPolicy.muted(typeKey, now.mutedUntil, now.at)
    }

    /** A pergunta apareceu: conta no teto de falas da Sofy e vai ao barramento. */
    fun asked(ctx: Context, typeKey: String) {
        val app = ctx.applicationContext
        registrarFala(app, System.currentTimeMillis())
        SofyBusTv.emit(app, SofyBus.Source.SOFY, "asked", detail = typeKey)
    }

    /** A pessoa fechou ([dismissed]) ou usou a fala de um tipo: 2 recusas seguidas calam o tipo por 7 dias. */
    @Synchronized fun answered(ctx: Context, typeKey: String, dismissed: Boolean) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val antes = SofyPolicy.Dismissals(lerMapa(p.getString("streak", null)) { it.toInt() }, lerMapa(p.getString("muted", null)) { it.toLong() })
        val depois = SofyPolicy.onAnswer(antes, typeKey, dismissed, System.currentTimeMillis())
        p.edit().putString("streak", JSONObject(depois.streak).toString()).putString("muted", JSONObject(depois.mutedUntil).toString()).apply()
        if (depois.mutedUntil[typeKey] != antes.mutedUntil[typeKey])
            runCatching { Diario.anotar(ctx, "tv", "Sofy calou: $typeKey", "recusado 2 vezes seguidas; volta em 7 dias") }
    }

    fun now(app: Context): SofyPolicy.Now {
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = System.currentTimeMillis()
        val c = Calendar.getInstance().apply { timeInMillis = at }
        val media = runCatching { app.getSystemService(AudioManager::class.java)?.isMusicActive == true }.getOrDefault(false)
        val shown = p.getString("shown", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        return SofyPolicy.Now(at, c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), media,
            SofyVoice.screenReaderOn(app), SofyVoice.voiceOn(app), shown, lerMapa(p.getString("muted", null)) { it.toLong() })
    }

    private fun registrarFala(app: Context, at: Long) {
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val shown = (p.getString("shown", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() } + at).filter { at - it < DAY_MS }
        p.edit().putString("shown", shown.joinToString(",")).apply()
    }

    /**
     * Painel (estudo do painel 20/11/2026, faixa da Sofy na Início): põe "title" e "text" em cada recado de [report]
     * (o JSON do [ObserverTv.reportJson]), na língua [lang] do navegador ("en", "pt"...). Língua inválida ou ausente =
     * a da TV. Recado sem frase fica sem os campos (o painel não mostra).
     */
    fun resolveNotes(ctx: Context, report: JSONObject, lang: String?) {
        val notes = report.optJSONArray("notes") ?: return
        val app = ctx.applicationContext
        val c = lang?.takeIf { it.matches(Regex("[a-z]{2}")) }?.let { l ->
            val conf = android.content.res.Configuration(app.resources.configuration)
            conf.setLocale(java.util.Locale.forLanguageTag(l))
            runCatching { app.createConfigurationContext(conf) }.getOrNull()
        } ?: app
        for (i in 0 until notes.length()) {
            val n = notes.optJSONObject(i) ?: continue
            val a = n.optJSONArray("args")
            val args = if (a == null) emptyList() else List(a.length()) { a.optString(it) }
            texto(c, n.optString("titleKey"), args)?.let { n.put("title", it) }
            texto(c, n.optString("textKey"), args)?.let { n.put("text", it) }
        }
    }

    /** A frase pelo nome do recurso (observer_<tipo>_title/_text), com os números do recado; null = ainda não existe. */
    private fun texto(app: Context, key: String, args: List<String>): String? {
        @Suppress("DiscouragedApi")
        val id = app.resources.getIdentifier(key, "string", app.packageName)
        if (id == 0) return null
        return runCatching { app.getString(id, *args.toTypedArray()) }.getOrNull()   // sem o número: sem a frase
    }

    private fun <T> lerMapa(s: String?, conv: (String) -> T): Map<String, T> = runCatching {
        val o = JSONObject(s ?: return emptyMap())
        o.keys().asSequence().associateWith { conv(o.get(it).toString()) }
    }.getOrDefault(emptyMap())
}
