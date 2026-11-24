package br.paulo.tvlauncher.observer

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.InputDevice
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.family.Diario
import org.json.JSONArray
import org.json.JSONObject

/**
 * A parte do Android da pilha do controle ([ControllerBattery]). A cada [GAP_MS], na batida da rede: lê a bateria de
 * cada controle/joystick externo (Android 12+; antes disso o Android não informa e nada acontece) e, quando a regra
 * manda, põe na fila da Sofy um recado SUGGESTS (Ilha), com a porcentagem. Quem já foi avisado fica em "observer"
 * (chave "battery_warned", por aparelho: fora do backup, é desta TV).
 */
object ControllerBatteryTv {
    private const val TAG = "TvLauncher.ControllerBattery"
    private const val GAP_MS = 6 * 3_600_000L
    @Volatile private var lastRun = 0L
    @Volatile var last: List<ControllerBattery.Reading> = emptyList(); private set

    fun read(): List<ControllerBattery.Reading> {
        if (Build.VERSION.SDK_INT < 31) return emptyList()
        val out = ArrayList<ControllerBattery.Reading>()
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            if (d.isVirtual || !d.isExternal) continue
            val b = runCatching { d.batteryState }.getOrNull() ?: continue
            if (!b.isPresent) continue
            out += ControllerBattery.Reading(d.descriptor ?: continue, b.capacity)
        }
        return out
    }

    @Synchronized fun tick(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRun < GAP_MS) return
        lastRun = now
        val app = ctx.applicationContext
        val readings = read().also { last = it }
        if (readings.isEmpty()) return
        val p = app.getSharedPreferences("observer", Context.MODE_PRIVATE)
        val warned = ControllerBattery.refilled(readings, parse(p.getString("battery_warned", null))).toMutableMap()
        val avisar = ControllerBattery.toWarn(now, readings, warned)
        for (r in avisar) {
            warned[r.id] = ControllerBattery.Warned(now, r.level)
            val pct = ControllerBattery.percent(r.level)
            ObserverTv.enqueueNote(app, ObserverCore.sofyNote(ObserverCore.Kind.REMOTE_BATTERY_LOW, now, ObserverCore.Step.SUGGESTS, listOf(pct.toString())))
            SofyBusTv.emit(app, SofyBus.Source.DEVICE, "battery_low", ok = false, detail = "$pct%")
            runCatching { Diario.anotar(app, "tv", "Sofy: pilha do controle", "$pct% de carga") }
            Log.i(TAG, "pilha no fim: $pct%")
        }
        p.edit().putString("battery_warned", toJson(warned)).apply()
        if (avisar.isNotEmpty()) Fundo.rodar { runCatching { SofyTv.present(app) } }
    }

    private fun parse(s: String?): Map<String, ControllerBattery.Warned> = runCatching {
        val o = JSONObject(s ?: return emptyMap())
        o.keys().asSequence().associateWith { k -> o.getJSONObject(k).let { ControllerBattery.Warned(it.getLong("at"), it.getDouble("level").toFloat()) } }
    }.getOrDefault(emptyMap())

    private fun toJson(m: Map<String, ControllerBattery.Warned>): String =
        JSONObject().apply { m.forEach { (k, w) -> put(k, JSONObject().put("at", w.at).put("level", w.level.toDouble())) } }.toString()

    /** Para o MCP: a carga de cada controle (sem o descritor inteiro). */
    fun json(): JSONArray = JSONArray().apply {
        last.forEach { put(JSONObject().put("id", it.id.take(8)).put("percent", if (it.level.isNaN()) JSONObject.NULL else ControllerBattery.percent(it.level))) }
    }
}
