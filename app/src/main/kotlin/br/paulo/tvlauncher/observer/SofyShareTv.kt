package br.paulo.tvlauncher.observer

import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.network.EntreTvs
import br.paulo.tvlauncher.player.JuntoRegras
import org.json.JSONObject
import java.security.SecureRandom

/** A parte do Android de [SofyShare] (ideia 40): manda as lições às outras TVs da conta e recebe as delas. */
object SofyShareTv {
    private const val TAG = "TvLauncher.SofyShare"
    private const val SEND_GAP_MS = 20 * 3_600_000L
    private const val NONCE_MS = 5 * 60_000L
    private const val MAX_RECEIVED_HOUR = 10

    /** Os nonces dos pacotes que ESTA TV mandou (quem recebe pergunta de volta "foi você?"). */
    private val sent = HashMap<String, Long>()
    private val received = ArrayList<Long>()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(ObserverTv.PREFS, Context.MODE_PRIVATE)

    /** Uma vez por dia (pelo autoexame), só com "TVs da família" ligado e outra TV da conta online. */
    fun daily(ctx: Context, now: Long) {
        val app = ctx.applicationContext
        if (!EntreTvs.ligado(app)) return
        val p = prefs(app)
        if (now - p.getLong("share_sent_at", 0L) < SEND_GAP_MS) return
        val others = EntreTvs.outras(app).filter { it.online }
        val me = EntreTvs.meuHost(app) ?: return
        if (others.isEmpty()) return
        p.edit().putLong("share_sent_at", now).apply()
        val lessons = SofyShare.lessons(AppProfileTv.allCards(app), now)
        if (lessons.isEmpty()) return
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        synchronized(sent) { sent.entries.removeAll { now - it.value > NONCE_MS }; sent[nonce] = now }
        val body = SofyShare.toJson(SofyShare.Packet(me, nonce, now, lessons))
        var ok = 0
        for (tv in others) if (EntreTvs.chamar(app, tv.host, "/sofy/licoes", body).codigo == 200) ok++
        SofyBusTv.emit(app, SofyBus.Source.SOFY, "lessons_sent", ok = ok > 0, detail = "${lessons.size} lições, $ok de ${others.size} TVs")
        ObserverTv.logShadow(app, "lessons_sent", "${lessons.size} lições para ${others.size} TVs da casa ($ok receberam)")
    }

    /** A outra TV pergunta se este pacote saiu daqui. */
    fun confirm(ctx: Context, nonce: String): Pair<Int, JSONObject> {
        if (!EntreTvs.ligado(ctx.applicationContext)) return 403 to JSONObject().put("erro", "desligado")
        val at = synchronized(sent) { sent[nonce] }
        return if (at != null && System.currentTimeMillis() - at < NONCE_MS) 200 to JSONObject().put("ok", true)
        else 404 to JSONObject().put("ok", false)
    }

    /** Chega um pacote de lições (rota pública do túnel: tudo é conferido). Só vira recado; nunca ação. */
    fun receive(ctx: Context, o: JSONObject?): Pair<Int, JSONObject> {
        val app = ctx.applicationContext
        if (!EntreTvs.ligado(app)) return 403 to JSONObject().put("erro", "desligado")
        val now = System.currentTimeMillis()
        val p = SofyShare.fromJson(o, now) { JuntoRegras.hostCurto(it) } ?: return 400 to JSONObject().put("erro", "pacote")
        if (EntreTvs.outras(app).none { it.host == p.from }) return 403 to JSONObject().put("erro", "tv")
        // A cota conta só pacote bem formado de uma TV da conta (revisor, 25/11/2026): lixo vindo da internet pelo túnel
        // gastava a cota e calava as lições da casa por 1 h. Ela ainda limita as conferências de volta (rede).
        synchronized(received) {
            received.removeAll { now - it > 3_600_000L }
            if (received.size >= MAX_RECEIVED_HOUR) return 429 to JSONObject().put("erro", "limite")
            received += now
        }
        val volta = EntreTvs.chamar(app, p.from, "/sofy/${p.nonce}/conferir")
        if (volta.codigo != 200 || volta.json?.optBoolean("ok") != true) return 403 to JSONObject().put("erro", "conferir")
        Fundo.rodar("sofy-licoes") { runCatching { apply(app, p, now) }.onFailure { Log.w(TAG, "lições: $it") } }
        return 200 to JSONObject().put("ok", true)
    }

    private fun apply(app: Context, p: SofyShare.Packet, now: Long) {
        val pm = app.packageManager
        val installed = p.lessons.mapNotNull { l -> runCatching { l.pkg to pm.getPackageInfo(l.pkg, 0).longVersionCode }.getOrNull() }.toMap()
        val pr = prefs(app)
        val told = runCatching { JSONObject(pr.getString("share_told", null) ?: "{}") }.getOrDefault(JSONObject())
        told.keys().asSequence().toList().forEach { if (now - told.optLong(it) > SofyShare.LESSON_TTL_MS) told.remove(it) }
        val news = SofyShare.relevant(p.lessons, installed, told.keys().asSequence().toSet())
        SofyBusTv.emit(app, SofyBus.Source.SOFY, "lessons_received", detail = "${p.lessons.size} lições, ${news.size} valem aqui")
        val tv = JuntoRegras.nomeBonito(p.from)
        for (l in news) {
            told.put(SofyShare.key(l), now)
            val nome = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(l.pkg, 0)).toString() }.getOrDefault(l.pkg)
            ObserverTv.logShadow(app, "lesson_from_tv", "$tv: ${l.pkg} v${l.ver} ${l.kind}")
            val kind = if (l.kind == "crashy") ObserverCore.Kind.OTHER_TV_CRASHY else ObserverCore.Kind.OTHER_TV_SLOWER
            ObserverTv.enqueueNote(app, ObserverCore.sofyNote(kind, now, ObserverCore.Step.OBSERVED, listOf(nome, tv)))
        }
        pr.edit().putString("share_told", told.toString()).apply()
    }
}
