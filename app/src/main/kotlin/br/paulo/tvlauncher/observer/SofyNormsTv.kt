package br.paulo.tvlauncher.observer

import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.family.Diario
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * A parte Android dos padrões da casa ([SofyNorms]). Pelo autoexame (a cada 6 h): na primeira passada de cada dia guarda
 * as contagens do dia que passou (barramento) e compara com o normal da casa. Cada quebra vira sinal INCOHERENT para o
 * conserto ([SofyRepairTv]): os consertos de dado primeiro, depois a IA do dono, e o relato. O livro fica no prefs do
 * observador (é da TV, não da pessoa: não vai no backup).
 */
object SofyNormsTv {
    private const val TAG = "TvLauncher.SofyNorms"
    private const val KEY = "norms_book"
    private const val KEY_SEEN = "norms_seen"
    private const val KEY_PENDING = "norms_pending"
    private const val INCOMPLETE = "_incomplete"
    private const val KEY_LAST = "norms_last"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(ObserverTv.PREFS, Context.MODE_PRIVATE)

    /** Número do dia local (dias desde 1970 no fuso da TV) e o começo dele em ms. */
    private fun dayOf(ms: Long): Pair<Int, Long> {
        val c = Calendar.getInstance().apply { timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val off = c.timeZone.getOffset(c.timeInMillis)
        return ((c.timeInMillis + off) / 86_400_000L).toInt() to c.timeInMillis
    }

    /**
     * A cada autoexame (6 h): soma os eventos novos do barramento nos dias deles (o barramento guarda só 500 linhas,
     * então contar aos poucos evita perder o começo do dia numa TV movimentada). Dia que acabou é conferido e guardado.
     * Se linhas se perderam entre duas passadas, o dia fica marcado incompleto e não conta (contaria falta onde não houve).
     */
    fun daily(ctx: Context, now: Long) {
        val app = ctx.applicationContext
        val p = prefs(app)
        val events = runCatching { SofyBusTv.events(app) }.getOrDefault(emptyList()).sortedBy { it.at }
        val seen = p.getLong(KEY_SEEN, 0L)
        val pending = runCatching { JSONObject(p.getString(KEY_PENDING, "{}")!!) }.getOrDefault(JSONObject())
        val fresh = events.filter { it.at > seen }
        val (today, _) = dayOf(now)
        // Primeira vez, ou linhas perdidas: o dia da linha mais velha que temos começa incompleto.
        if (seen == 0L || (events.isNotEmpty() && events.first().at > seen)) {
            val d = if (seen == 0L) today else dayOf(events.first().at).first
            pending.put(d.toString(), (pending.optJSONObject(d.toString()) ?: JSONObject()).put(INCOMPLETE, 1.0))
        }
        for ((d, evs) in fresh.groupBy { dayOf(it.at).first }) {
            val o = pending.optJSONObject(d.toString()) ?: JSONObject()
            for (e in evs) { val k = SofyNorms.keyOf(e); o.put(k, o.optDouble(k, 0.0) + 1) }
            pending.put(d.toString(), o)
        }
        if (fresh.isNotEmpty()) p.edit().putLong(KEY_SEEN, fresh.last().at).apply()
        var book = SofyNorms.fromJson(p.getString(KEY, null))
        val done = pending.keys().asSequence().mapNotNull { it.toIntOrNull() }.filter { it < today }.sorted().toList()
        val found = ArrayList<SofyNorms.Finding>()
        val newNormal = ArrayList<SofyNorms.Finding>()
        for (d in done) {
            val o = pending.getJSONObject(d.toString()); pending.remove(d.toString())
            if (o.has(INCOMPLETE)) { Log.i(TAG, "dia $d incompleto; não conta"); continue }
            val day = SofyNorms.Day(d, o.keys().asSequence().associateWith { o.getDouble(it) })
            val f = SofyNorms.check(book, day)
            val (settled, nn) = SofyNorms.settle(book, d, f)
            book = SofyNorms.record(settled, day)
            found += f.filter { x -> nn.none { it.id == x.id } }; newNormal += nn
            Log.i(TAG, "dia $d: ${day.values.size} chaves, ${book.days.size} dias de história, ${f.size} quebras")
        }
        val e = p.edit().putString(KEY_PENDING, pending.toString())
        if (done.isNotEmpty()) e.putString(KEY, SofyNorms.toJson(book))
            .putString(KEY_LAST, JSONArray().apply { found.forEach { put(JSONObject().put("kind", it.kind.name).put("key", it.key).put("why", it.detail)) } }.toString())
        e.apply()
        for (f in newNormal) ObserverTv.logShadow(app, "norm_new", "${f.key}: ${f.detail} (${SofyNorms.ACCEPT_AFTER} dias seguidos = novo normal)")
        for (f in found) {
            ObserverTv.logShadow(app, "norm_broken", "${f.kind.name} ${f.key}: ${f.detail}")
            runCatching { Diario.anotar(app, "tv", "Sofy: fora do normal da casa", "${f.key}: ${f.detail}") }
            SofyRepairTv.signal(app, SofyRepair.Type.INCOHERENT, "norm:${f.key}".take(90), "${f.kind.name} ${f.detail}")
        }
    }

    /** Para o MCP/painel ("o que ela aprendeu"): dias de história e as quebras do último dia conferido. */
    fun json(ctx: Context): JSONObject {
        val p = prefs(ctx)
        val book = SofyNorms.fromJson(p.getString(KEY, null))
        return JSONObject().put("days", book.days.size).put("min_days", SofyNorms.MIN_DAYS)
            .put("keys", book.days.flatMap { it.values.keys }.toSet().size)
            .put("accepted", book.accepted.size)
            .put("last", runCatching { JSONArray(p.getString(KEY_LAST, "[]")) }.getOrDefault(JSONArray()))
    }
}
