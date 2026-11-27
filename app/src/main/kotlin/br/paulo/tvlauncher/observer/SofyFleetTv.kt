package br.paulo.tvlauncher.observer

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import br.paulo.tvlauncher.BuscaActivity
import br.paulo.tvlauncher.BuildConfig
import br.paulo.tvlauncher.Http
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.Calendar

/**
 * A parte Android da rede de Sofys ([SofyFleet], servidor em server/api/src/fleet.js). Uma vez por dia, só com
 * "Ajudar outras casas" ligado (a MESMA chave das fichas comunitárias, Paulo 27/11/2026: um switch só), manda o
 * pacote do dia: o aparelho em faixas e os casos de conserto das últimas 24 h, só com campos de listas fechadas.
 *
 * O segredo do código do dia fica em noBackupFilesDir (fora do backup): uma TV restaurada de outra nunca tem o mesmo
 * código. O estado da TV de cada caso (memória, rede, tempo ligada, período) é guardado em faixas na hora em que o caso
 * abre ([snapshot]), porque no envio, horas depois, já seria outro.
 */
object SofyFleetTv {
    const val URL_CASES = "https://api.sofaly.tv/v1/community/cases"
    private const val PREFS = "sofy_fleet"
    private const val GAP_MS = 20 * 3_600_000L
    private const val WINDOW_MS = 24 * 3_600_000L
    private const val MAX_STATES = 60

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Pelo autoexame diário. Rede: chamar fora da tela. */
    fun daily(ctx: Context, now: Long) {
        val app = ctx.applicationContext
        if (!SofyCommunityTv.sharing(app)) return
        val p = prefs(app)
        if (now - p.getLong("sent_at", 0L) < GAP_MS) return
        p.edit().putLong("sent_at", now).apply()
        send(app, now)
    }

    /** O pacote que iria hoje (também para a tela "O que a Sofy compartilha", que mostra exatamente isto). */
    fun today(ctx: Context, now: Long = System.currentTimeMillis()): JSONObject {
        val app = ctx.applicationContext
        val cases = SofyRepairTv.cases(app).filter { now - it.lastAt < WINDOW_MS }
        return SofyFleet.packet(SofyFleet.dayCode(secret(app), now / 86_400_000L), BuildConfig.VERSION_CODE.toLong(),
            device(app), cases, states(app), state(app, now))
    }

    private fun send(app: Context, now: Long) {
        val packet = today(app, now)
        val body = packet.toString().toByteArray()
        val c = URL(URL_CASES).openConnection() as HttpURLConnection
        val code = try {
            c.connectTimeout = 8_000; c.readTimeout = 8_000; c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("User-Agent", Http.USER_AGENT); c.setRequestProperty("Content-Type", "application/json")
            br.paulo.tvlauncher.relay.RelayTv.assinar(c, body)   // cota do dia por TV
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            c.responseCode
        } catch (e: Exception) { 0 } finally { c.disconnect() }
        val n = packet.optJSONArray("cases")?.length() ?: 0
        SofyBusTv.emit(app, SofyBus.Source.SOFY, "fleet_sent", ok = code == 200, detail = "$n casos")
        ObserverTv.logShadow(app, "fleet_sent", "$n casos, http $code")
    }

    /** Guarda o estado da TV (em faixas, depois) na hora em que um caso abre. */
    fun snapshot(ctx: Context, key: String, now: Long = System.currentTimeMillis()) {
        val app = ctx.applicationContext
        val s = state(app, now)
        val all = runCatching { JSONObject(prefs(app).getString("states", null) ?: "{}") }.getOrDefault(JSONObject())
        all.put(key, JSONObject().put("up", s.uptimeMs).put("low", s.memoryLow).put("net", s.net).put("hour", s.hour).put("at", now))
        // Só os mais recentes: o envio olha 24 h para trás.
        val keys = all.keys().asSequence().toList().sortedBy { all.optJSONObject(it)?.optLong("at") ?: 0L }
        keys.dropLast(MAX_STATES).forEach { all.remove(it) }
        prefs(app).edit().putString("states", all.toString()).apply()
    }

    private fun states(app: Context): Map<String, SofyFleet.State> {
        val all = runCatching { JSONObject(prefs(app).getString("states", null) ?: "{}") }.getOrDefault(JSONObject())
        return all.keys().asSequence().mapNotNull { k ->
            all.optJSONObject(k)?.let { k to SofyFleet.State(it.optLong("up"), it.optBoolean("low"), it.optString("net"), it.optInt("hour")) }
        }.toMap()
    }

    private fun state(app: Context, now: Long): SofyFleet.State {
        val mem = ActivityManager.MemoryInfo().also { (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return SofyFleet.State(SystemClock.elapsedRealtime(), mem.lowMemory, net(app), hour)
    }

    private fun net(app: Context): String = runCatching {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "none"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            else -> "other"
        }
    }.getOrDefault("other")

    private fun device(app: Context): SofyFleet.Device {
        val mem = ActivityManager.MemoryInfo().also { (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        val disk = runCatching { StatFs(Environment.getDataDirectory().path).totalBytes }.getOrDefault(0L)
        return SofyFleet.Device(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty(), BuscaActivity.temGoogleTv(app),
            app.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK), Build.VERSION.SDK_INT,
            android.os.Process.is64Bit(), mem.totalMem / (1024 * 1024), disk / (1024L * 1024 * 1024))
    }

    /** 32 bytes aleatórios, criados uma vez, fora do backup. */
    @Synchronized private fun secret(app: Context): ByteArray {
        val f = File(app.noBackupFilesDir, "sofy-fleet.key")
        runCatching { f.readBytes() }.getOrNull()?.takeIf { it.size == 32 }?.let { return it }
        val b = ByteArray(32).also { SecureRandom().nextBytes(it) }
        runCatching { f.writeBytes(b) }
        return b
    }

    /** "Apagar": troca o segredo (o código antigo não volta) e esquece os estados guardados. */
    fun forget(ctx: Context) {
        val app = ctx.applicationContext
        runCatching { File(app.noBackupFilesDir, "sofy-fleet.key").delete() }
        prefs(app).edit().clear().apply()
    }
}
