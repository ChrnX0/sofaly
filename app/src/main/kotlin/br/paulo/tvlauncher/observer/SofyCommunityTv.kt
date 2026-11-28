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
 *  - MANDAR (desligado de fábrica, "Ajudar outras casas"): só pacote + versão + tipo das lições desta TV, sem remetente,
 *    e as regras do revisor que JÁ VALEM aqui, só padrão e faixa, sem palavra que possa ser da casa ([SofyRules.shareable]).
 */
object SofyCommunityTv {
    const val URL_LESSONS = "https://api.sofaly.tv/v1/community/lessons"
    /** "Ajudar outras casas" (Extras, vai no backup): [SofyConsent.SHARE_VERSION] = manda as lições desta TV. 0 = só recebe. */
    const val KEY_SHARE = "sofy_community_share"
    private const val GAP_MS = 20 * 3_600_000L

    /**
     * Consentimento da rede de Sofys (#541), à parte: só quem disse "Sim" na tela do sofáTV (HelpOtherHomesActivity),
     * que lista o que vai e o que nunca sai, manda o pacote da frota. Ligar pelo painel (cujo texto promete só o nome e
     * a versão dos apps que travaram) ou pelo adb liga só as fichas. Desligar, por qualquer lado, desliga os dois.
     */
    const val KEY_FLEET = "sofy_fleet_consent"

    // As chaves guardam a VERSÃO do texto aceito ([SofyConsent], 28/11/2026): um "Sim" a um texto velho vale desligado.
    private fun share(ctx: Context) = Extras.numero(ctx, KEY_SHARE, 0)
    private fun fleet(ctx: Context) = Extras.numero(ctx, KEY_FLEET, 0)
    fun sharing(ctx: Context) = SofyConsent.shareOn(share(ctx))
    fun fleetConsent(ctx: Context) = SofyConsent.fleetOn(share(ctx), fleet(ctx))
    /** Disse "Sim" a um texto mais velho do que o de agora: a TV pergunta de novo, uma vez. */
    fun needsReask(ctx: Context) = SofyConsent.reask(share(ctx), fleet(ctx))

    /**
     * Ligar ou desligar. [fleet] = o "Sim" da tela do sofáTV, que vale para as duas metades. Desligar, por qualquer lado,
     * desliga as duas e já esquece o que foi ([SofyFleetTv.forget]: código novo, o que foi não se liga mais a esta TV).
     */
    fun setSharing(ctx: Context, on: Boolean, fleet: Boolean = false) {
        val (s, f) = SofyConsent.values(on, fleet, fleet(ctx))
        Extras.definirNumero(ctx, KEY_SHARE, s)
        Extras.definirNumero(ctx, KEY_FLEET, f)
        if (!on) SofyFleetTv.forget(ctx)
    }

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

    /**
     * O corpo que iria hoje para [URL_LESSONS] (null = nada a mandar). Também aparece em "Ver o que vai hoje"
     * (HelpOtherHomesActivity), ao lado do pacote da frota: a tela mostra tudo o que este switch manda.
     */
    fun todayBody(ctx: Context): JSONObject? {
        val app = ctx.applicationContext
        val lessons = SofyShare.lessons(AppProfileTv.allCards(app), System.currentTimeMillis())
        val rules = SofyRules.communityRules(SofyRulesTv.rules(app))
        if (lessons.isEmpty() && rules.length() == 0) return null
        return SofyShare.communityBody(lessons).apply { if (rules.length() > 0) put("rules", rules) }
    }

    private fun send(app: Context) {
        val obj = todayBody(app) ?: return
        val lessons = obj.optJSONArray("lessons")?.length() ?: 0
        val rules = obj.optJSONArray("rules")?.length() ?: 0
        val body = obj.toString().toByteArray()
        val c = URL(URL_LESSONS).openConnection() as HttpURLConnection
        val code = try {
            c.connectTimeout = 8_000; c.readTimeout = 8_000; c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("User-Agent", Http.USER_AGENT); c.setRequestProperty("Content-Type", "application/json")
            br.paulo.tvlauncher.relay.RelayTv.assinar(c, body)   // cota do dia por TV (item 23)
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            c.responseCode
        } catch (e: Exception) { 0 } finally { c.disconnect() }
        SofyBusTv.emit(app, SofyBus.Source.SOFY, "community_sent", ok = code == 200, detail = "$lessons lições, $rules regras")
    }

    private fun receive(app: Context, now: Long) {
        val text = Http.getTextOrNull(URL_LESSONS, maxBytes = 256 * 1024) ?: return
        val obj = runCatching { JSONObject(text) }.getOrNull()
        // Regras do revisor vindas de outras casas (passo 4): entram na sombra daqui; só valem se passarem 3 noites.
        runCatching { SofyRulesTv.addFromCommunity(app, SofyRules.fromCommunity(obj, SofyShare.COMMUNITY_MIN_HOMES, now)) }
        val list = SofyShare.fromCommunity(obj)
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
