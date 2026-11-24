package br.paulo.tvlauncher.weather

import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.Config
import br.paulo.tvlauncher.Http
import br.paulo.tvlauncher.Prefs
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.screensaver.WidgetsTempo
import java.util.Date

/**
 * O lado Android do [NightSky]: lê o lugar e a previsão hora a hora que a HOME guardou (prefs "clima"), a órbita da
 * Estação ([EstacaoEspacial]) e os eclipses do lugar ([Config.ECLIPSES_SOLARES]), e guarda a resposta por
 * [GUARDAR_MS] (a Ilha pergunta a cada minuto; a conta da Estação varre 13 h em passos de 20 s). Fora da thread da tela.
 * Textos no idioma da TV, com as mesmas frases que o protetor antigo usava (strings_ceu_tempo.xml).
 */
object NightSkyTv {
    private const val GUARDAR_MS = 30 * 60_000L
    @Volatile private var guardado: Pair<Long, NightSky.Event?>? = null

    fun hoje(ctx: Context, agora: Long = System.currentTimeMillis()): NightSky.Event? {
        guardado?.let { (em, e) -> if (agora - em < GUARDAR_MS) return e?.takeIf { it.end > agora } }
        val c = ctx.getSharedPreferences(Prefs.CLIMA, Context.MODE_PRIVATE)
        val lat = c.getFloat("lat", Config.LATITUDE.toFloat()).toDouble()
        val lon = c.getFloat("lon", Config.LONGITUDE.toFloat()).toDouble()
        val horas = WidgetsTempo.lerHoras(c.getString("horas", "").orEmpty())
        // Sem previsão não há como saber se o céu está limpo: nem baixa a órbita.
        val e = if (horas.isEmpty()) null else runCatching {
            NightSky.hoje(agora, lat, lon, horas, EstacaoEspacial.proxima(ctx, agora, lat, lon), Config.ECLIPSES_SOLARES)
        }.getOrNull()
        guardado = agora to e
        return e
    }

    private val calculando = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Para quem não pode esperar rede (a Ilha roda na fila dos apps): devolve o que já está guardado e, se venceu,
     * recalcula em segundo plano (a órbita da Estação pode precisar baixar). O próximo tique já pega o novo.
     */
    fun semEsperar(ctx: Context, agora: Long = System.currentTimeMillis()): NightSky.Event? {
        val g = guardado
        if ((g == null || agora - g.first >= GUARDAR_MS) && calculando.compareAndSet(false, true)) {
            val app = ctx.applicationContext
            br.paulo.tvlauncher.Fundo.rodar("ceu-da-noite") { try { hoje(app) } finally { calculando.set(false) } }
        }
        return g?.second?.takeIf { it.end > agora }
    }

    private fun hm(ctx: Context, ms: Long) = android.text.format.DateFormat.getTimeFormat(ctx).format(Date(ms))

    /** Curto, para a pílula da Ilha: "Estação Espacial às 19:42", "Chuva de meteoros hoje". */
    fun textoIlha(ctx: Context, e: NightSky.Event): String = when (e.kind) {
        NightSky.Kind.ECLIPSE_SUN -> ctx.getString(R.string.cu_eclipse, hm(ctx, e.start))
        NightSky.Kind.ISS -> ctx.getString(R.string.cu_estacao, hm(ctx, e.start))
        NightSky.Kind.METEORS -> ctx.getString(R.string.cu_meteoros)
        NightSky.Kind.SUPERMOON -> ctx.getString(R.string.cu_superlua)
    }

    /** Para a faixa do descanso: (título, valor, nota). */
    fun celula(ctx: Context, e: NightSky.Event): Pair<String, String> {
        val r = ctx.resources
        return when (e.kind) {
            NightSky.Kind.ECLIPSE_SUN -> textoIlha(ctx, e) to ctx.getString(R.string.cu_eclipse_d)
            NightSky.Kind.ISS -> textoIlha(ctx, e) to e.pass!!.let { p ->
                val rumos = r.getStringArray(R.array.ceu_rumos)
                ctx.getString(R.string.cu_estacao_d, p.minutos, rumos[p.deRumo], rumos[p.paraRumo])
            }
            NightSky.Kind.METEORS -> textoIlha(ctx, e) to ctx.getString(R.string.cu_meteoros_d,
                r.getStringArray(R.array.ceu_meteoros)[e.shower!!.ordinal], e.shower.porHora)
            NightSky.Kind.SUPERMOON -> textoIlha(ctx, e) to ctx.getString(R.string.cu_lua_cheia_d)
        }
    }
}

/**
 * A Estação Espacial: o TLE (elementos da órbita, dado público do NORAD) vem do CelesTrak no máximo a cada 12 h e fica
 * nas prefs "ceu"; a passagem é calculada na TV ([CeuNoturno.proximaPassagem]). Sem rede ou sem TLE: sem a Estação.
 * Fora da thread da tela.
 */
object EstacaoEspacial {
    private const val TAG = "TvLauncher.Ceu"
    private const val URL_TLE = "https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=TLE"

    fun proxima(ctx: Context, agora: Long, lat: Double, lon: Double): CeuNoturno.Passagem? {
        val p = ctx.getSharedPreferences("ceu", Context.MODE_PRIVATE)
        var l1 = p.getString("tle1", null); var l2 = p.getString("tle2", null)
        if (l1 == null || agora - p.getLong("tle_em", 0) > 12 * 3_600_000L) {
            baixar()?.let { (a, b) -> l1 = a; l2 = b; p.edit().putString("tle1", a).putString("tle2", b).putLong("tle_em", agora).apply() }
        }
        val sat = try { Sgp4(l1 ?: return null, l2 ?: return null) } catch (e: Exception) { Log.w(TAG, "TLE: $e"); return null }
        // TLE de mais de 5 dias erra a passagem por minutos: melhor não mostrar.
        if (agora - sat.epocaMs > 5 * 86_400_000L) return null
        return CeuNoturno.proximaPassagem(sat, agora, lat, lon, horas = 13)
    }

    private fun baixar(): Pair<String, String>? = try {
        Http.getText(URL_TLE)?.lines()?.map { it.trimEnd() }?.let { linhas ->
            val a = linhas.firstOrNull { it.startsWith("1 25544") }; val b = linhas.firstOrNull { it.startsWith("2 25544") }
            if (a != null && b != null) a to b else null
        }
    } catch (e: Exception) { Log.w(TAG, "CelesTrak: ${e.javaClass.simpleName}"); null }
}
