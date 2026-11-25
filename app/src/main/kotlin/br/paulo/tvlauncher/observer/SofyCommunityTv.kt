package br.paulo.tvlauncher.observer

import android.content.Context
import br.paulo.tvlauncher.Http
import br.paulo.tvlauncher.settings.Extras
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fichas comunitárias (ideia 41; servidor em server/api/src/community.js). Duas metades separadas de propósito:
 *  - RECEBER (ligado): uma vez por dia a TV baixa a lista pública (lições vistas em 5+ casas) e confere SOZINHA o que
 *    vale para os apps dela. Nada da TV sai nisso além do pedido em si (igual à previsão do tempo).
 *  - MANDAR (desligado de fábrica, "Ajudar outras casas"): só pacote + versão + tipo das lições desta TV, sem remetente.
 */
object SofyCommunityTv {
    const val URL_LESSONS = "https://api.sofaly.tv/v1/community/lessons"
    /** "Ajudar outras casas" (Extras, vai no backup): 1 = manda as lições desta TV. 0 = só recebe. */
    const val KEY_SHARE = "sofy_community_share"
    private const val GAP_MS = 20 * 3_600_000L

    fun sharing(ctx: Context) = Extras.numero(ctx, KEY_SHARE, 0) == 1
    fun setSharing(ctx: Context, on: Boolean) = Extras.definirNumero(ctx, KEY_SHARE, if (on) 1 else 0)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(ObserverTv.PREFS, Context.MODE_PRIVATE)

    /** Pelo autoexame diário. Rede: chamar fora da tela. */
    fun daily(ctx: Context, now: Long) {
        val app = ctx.applicationContext
        val p = prefs(app)
        if (now - p.getLong("community_at", 0L) < GAP_MS) return
        p.edit().putLong("community_at", now).apply()
        if (sharing(app)) send(app)
        receive(app, now)
    }

    private fun send(app: Context) {
        val lessons = SofyShare.lessons(AppProfileTv.allCards(app), System.currentTimeMillis())
        if (lessons.isEmpty()) return
        val body = SofyShare.communityBody(lessons).toString().toByteArray()
        val c = URL(URL_LESSONS).openConnection() as HttpURLConnection
        val code = try {
            c.connectTimeout = 8_000; c.readTimeout = 8_000; c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("User-Agent", Http.USER_AGENT); c.setRequestProperty("Content-Type", "application/json")
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            c.responseCode
        } catch (e: Exception) { 0 } finally { c.disconnect() }
        SofyBusTv.emit(app, SofyBus.Source.SOFY, "community_sent", ok = code == 200, detail = "${lessons.size} lições")
    }

    private fun receive(app: Context, now: Long) {
        val text = Http.getTextOrNull(URL_LESSONS, maxBytes = 256 * 1024) ?: return
        val list = SofyShare.fromCommunity(runCatching { JSONObject(text) }.getOrNull())
        if (list.isEmpty()) return
        val pm = app.packageManager
        val installed = list.mapNotNull { (l, _) -> runCatching { l.pkg to pm.getPackageInfo(l.pkg, 0).longVersionCode }.getOrNull() }.toMap()
        val pr = prefs(app)
        val told = runCatching { JSONObject(pr.getString("community_told", null) ?: "{}") }.getOrDefault(JSONObject())
        told.keys().asSequence().toList().forEach { if (now - told.optLong(it) > SofyShare.LESSON_TTL_MS) told.remove(it) }
        // A lição desta TV mesma não precisa vir do servidor: a Sofy daqui já contou.
        val mine = SofyShare.lessons(AppProfileTv.allCards(app), now).map { SofyShare.key(it) }.toSet()
        val news = SofyShare.relevant(list.map { it.first }, installed, told.keys().asSequence().toSet() + mine)
        val homes = list.associate { SofyShare.key(it.first) to it.second }
        SofyBusTv.emit(app, SofyBus.Source.SOFY, "community_received", detail = "${list.size} lições, ${news.size} valem aqui")
        for (l in news) {
            told.put(SofyShare.key(l), now)
            val nome = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(l.pkg, 0)).toString() }.getOrDefault(l.pkg)
            val n = homes[SofyShare.key(l)] ?: SofyShare.COMMUNITY_MIN_HOMES
            ObserverTv.logShadow(app, "community_lesson", "${l.pkg} v${l.ver} ${l.kind} em $n casas")
            val kind = if (l.kind == "crashy") ObserverCore.Kind.HOMES_CRASHY else ObserverCore.Kind.HOMES_SLOWER
            ObserverTv.enqueueNote(app, ObserverCore.sofyNote(kind, now, ObserverCore.Step.OBSERVED, listOf(nome, n.toString())))
        }
        pr.edit().putString("community_told", told.toString()).apply()
    }
}
