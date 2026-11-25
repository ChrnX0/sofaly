package br.paulo.tvlauncher.panel

import android.content.Context
import br.paulo.tvlauncher.companion.AudioSyncMath
import br.paulo.tvlauncher.companion.AudioSyncTv
import br.paulo.tvlauncher.companion.CompanionTv
import br.paulo.tvlauncher.companion.FindRemote
import br.paulo.tvlauncher.companion.PictureMath
import org.json.JSONArray
import org.json.JSONObject

/**
 * Painel web › "Celular e TV" (24/11/2026, proposta B): as rotas que o companion.js chama para medir a TV com o
 * celular. Só números entram (caixas e médias de cor da foto, que fica no celular); nenhuma imagem sai do celular.
 *
 * GET  ""          estado: a TV da frente e a medida dela
 * POST /pattern    {pattern}: a TV mostra um padrão de [PictureMath.Pattern]
 * POST /stop       a TV sai da tela de padrões
 * POST /picture    {part, save, ...números}: veredito e conselho; com save=true grava e avisa a Sofy
 * GET  /remotes    os controles ligados, a última vez que cada um foi usado e se treme
 * POST /remotes/ring   faz tremer os controles que têm motor ([FindRemote])
 * GET  /clock      a hora da TV (ms), para o celular acertar o relógio antes de ouvir os bipes
 * POST /audio/start   a TV toca os bipes ([AudioSyncTv])
 * POST /audio/result  {pings:[{s,t,g}], heard:[ms do celular]}: o atraso desta saída; grava e avisa a Sofy
 *
 * Medir não muda nada na TV além da tela de padrões, então não pede PIN (igual ao controle remoto do painel).
 */
object PanelCompanion {
    private fun ok(o: JSONObject = JSONObject()) = 200 to o.put("ok", true)
    private fun erro(codigo: Int, msg: String) = codigo to JSONObject().put("erro", msg)

    fun rotear(app: Context, metodo: String, rota: String, corpo: JSONObject?): Pair<Int, JSONObject>? {
        if (metodo == "GET" && rota == "") return ok(estado(app))
        if (metodo == "GET" && rota == "/remotes") return ok(remotes(app))
        if (metodo == "GET" && rota == "/clock") return 200 to JSONObject().put("t", AudioSyncTv.nowMs())
        if (metodo != "POST") return null
        val c = corpo ?: JSONObject()
        return when (rota) {
            "/pattern" -> {
                val p = PictureMath.Pattern.entries.firstOrNull { it.name == c.optString("pattern") } ?: return erro(400, "padrão desconhecido")
                CompanionTv.showPattern(app, p)
                ok()
            }
            "/stop" -> { CompanionTv.stopPattern(); ok() }
            "/picture" -> {
                val parte = c.optString("part")
                val r = PanelCompanionRules.judge(parte, c) ?: return erro(400, "medida incompleta")
                if (c.optBoolean("save")) CompanionTv.savePicture(app, parte, r)
                ok(PanelCompanionRules.toJson(r))
            }
            "/remotes/ring" -> ok(remotes(app).put("vibrated", JSONArray(FindRemote.ring(app))))
            "/audio/start" -> if (AudioSyncTv.startBeeps()) ok(JSONObject().put("beeps", AudioSyncMath.BEEPS).put("gapMs", AudioSyncMath.GAP_MS))
                else erro(409, "já tocando")
            "/audio/result" -> {
                val ouvidos = PanelCompanionRules.times(c.optJSONArray("heard")) ?: return erro(400, "medida incompleta")
                val relogio = PanelCompanionRules.pings(c.optJSONArray("pings"))?.let { AudioSyncMath.clockOffset(it) }
                    ?: return erro(400, "relógio inválido")
                val r = AudioSyncMath.delay(AudioSyncTv.lastWritten(), ouvidos, relogio.first, relogio.second)
                val rota = AudioSyncTv.route(app)
                AudioSyncTv.save(app, rota, r)
                ok(JSONObject().put("verdict", r.verdict.name).put("delay", r.delayMs).put("spread", r.spreadMs)
                    .put("heard", r.heard).put("route", rota).put("detail", r.detail).put("delayInUse", AudioSyncTv.delayInUse(app)))
            }
            else -> null
        }
    }

    private fun remotes(app: Context): JSONObject = JSONObject().put("remotes", JSONArray().apply {
        for (r in FindRemote.remotes(app)) put(JSONObject().put("name", r.name).put("lastUse", r.lastUse ?: JSONObject.NULL)
            .put("canVibrate", r.canVibrate).put("gamepad", r.gamepad))
    }).put("now", System.currentTimeMillis())

    fun estado(app: Context): JSONObject = JSONObject()
        .put("display", CompanionTv.displayKey(app))
        .put("displayName", CompanionTv.displayName(app) ?: JSONObject.NULL)
        .put("aspect", CompanionTv.aspect(app))   // largura/altura da tela de verdade: a moldura do visor segue a TV
        .put("picture", CompanionTv.picture(app))
        .put("measuring", CompanionTv.phonePattern?.name ?: JSONObject.NULL)
        .put("route", runCatching { AudioSyncTv.route(app) }.getOrDefault("hdmi"))
        .put("audio", AudioSyncTv.audio(app))
        .put("delayInUse", AudioSyncTv.delayInUse(app))
}

/** A parte pura das rotas (PanelCompanionTest): ler os números do celular com limites e julgar. */
object PanelCompanionRules {
    fun judge(part: String, c: JSONObject): PictureMath.Result? = when (part) {
        "overscan" -> {
            val v = box(c.optJSONObject("visible")); val i = box(c.optJSONObject("inset"))
            PictureMath.judgeOverscan(if (v != null && i != null) PictureMath.overscan(v, i) else null)
        }
        "black" -> lumas(c.optJSONArray("luma"))?.let {
            PictureMath.judgeBlack(it, c.optDouble("blackOverWhite").takeIf { x -> !x.isNaN() && x in 0.0..1.0 })
        }
        "white" -> lumas(c.optJSONArray("luma"))?.let { PictureMath.judgeWhite(it) }
        "color" -> {
            val r = num(c, "r"); val g = num(c, "g"); val b = num(c, "b")
            if (r == null || g == null || b == null) null else PictureMath.judgeColor(r, g, b, c.optBoolean("wbLocked"))
        }
        else -> null
    }

    fun toJson(r: PictureMath.Result): JSONObject =
        JSONObject().put("verdict", r.verdict.name).put("advice", r.advice.name).put("detail", r.detail)

    /** Uma caixa em pixels da câmera: 4 números finitos entre 0 e 10000. */
    fun box(o: JSONObject?): PictureMath.Box? {
        o ?: return null
        val v = listOf("l", "t", "r", "b").map { num(o, it) ?: return null }
        return PictureMath.Box(v[0], v[1], v[2], v[3])
    }

    /** Exatamente 7 brilhos entre 0 e 255. */
    fun lumas(a: JSONArray?): List<Double>? {
        if (a == null || a.length() != PictureMath.NEAR_BLACK_STEPS.size) return null
        return (0 until a.length()).map { a.optDouble(it).takeIf { x -> !x.isNaN() && x in 0.0..255.0 } ?: return null }
    }

    /** Até 40 horas do celular (ms, números finitos e positivos). */
    fun times(a: JSONArray?): List<Double>? {
        if (a == null || a.length() > 40) return null
        return (0 until a.length()).map { a.optDouble(it).takeIf { x -> !x.isNaN() && !x.isInfinite() && x >= 0 } ?: return null }
    }

    /** As idas e voltas do relógio: até 30, cada uma {s, t, g} (ms; envio e chegada no celular, hora da TV). */
    fun pings(a: JSONArray?): List<AudioSyncMath.Ping>? {
        if (a == null || a.length() == 0 || a.length() > 30) return null
        return (0 until a.length()).map { i ->
            val o = a.optJSONObject(i) ?: return null
            val v = listOf("s", "t", "g").map { k -> o.optDouble(k).takeIf { !it.isNaN() && !it.isInfinite() && it >= 0 } ?: return null }
            AudioSyncMath.Ping(v[0], v[1], v[2])
        }
    }

    private fun num(o: JSONObject, k: String): Double? =
        o.optDouble(k).takeIf { !it.isNaN() && !it.isInfinite() && it in 0.0..10_000.0 }
}
