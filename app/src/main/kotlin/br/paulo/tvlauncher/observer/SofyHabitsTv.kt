package br.paulo.tvlauncher.observer

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.Rotinas
import br.paulo.tvlauncher.alarm.AutoDesligar
import br.paulo.tvlauncher.alarm.Despertador
import br.paulo.tvlauncher.family.ModoVisita
import br.paulo.tvlauncher.family.Perfil
import br.paulo.tvlauncher.family.PerfilDados
import br.paulo.tvlauncher.network.VolumeLg
import br.paulo.tvlauncher.settings.Extras
import java.util.TimeZone
import org.json.JSONObject

/**
 * A parte do Android dos hábitos de cada pessoa ([SofyHabits], ideias 16 a 22): junta os sinais (ficha dos apps por
 * perfil, volume de cada hora, barramento) e entrega às Sugestões o que propor. Nada aqui muda a TV sozinho.
 */
object SofyHabitsTv {
    private const val TAG = "TvLauncher.SofyHabits"
    /** Teto da noite (ideia 21): % do máximo entre 23 h e 6 h; 0 = desligado. Nas preferências do Extras (vai no backup). */
    const val KEY_NIGHT_CEILING = "sofy_night_ceiling"
    const val NIGHT_FROM = 23
    const val NIGHT_TO = 6
    private const val DAY_MS = 86_400_000L

    @Volatile private var people: HashMap<String, SofyHabits.Person>? = null
    @Volatile private var lastSave = 0L

    private fun load(ctx: Context): HashMap<String, SofyHabits.Person> = people ?: SofyHabits.fromJson(
        ObserverTv.learning(ctx).getString("habits", null)?.let { runCatching { JSONObject(it) }.getOrNull() }).also { people = it }

    private fun profileKey(ctx: Context) = runCatching { Perfil.chaveDados(ctx) }.getOrDefault("")

    /**
     * Ideia 16: o volume desta hora, na batida do Observador, só com a TV em uso e som tocando. Volume fixo no Android
     * (o som vai pelo HDMI/CEC) = lê a LG pareada, se houver. Sem nenhum dos dois, não aprende (honesto: sem dado).
     */
    fun sampleVolume(ctx: Context, inUse: Boolean, hour: Int) {
        if (!inUse || ModoVisita.ativo(ctx)) return
        val app = ctx.applicationContext
        val am = app.getSystemService(AudioManager::class.java) ?: return
        if (!am.isMusicActive) return
        val id = SofyHabits.profileId(profileKey(app))
        if (!am.isVolumeFixed) {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: return
            note(app, id, hour, am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max)
        } else if (VolumeLg.configurado(app)) Fundo.rodar("sofy-volume-lg") {
            val v = runCatching { VolumeLg.nivel(app) }.getOrNull() ?: return@rodar
            if (!v.mudo && v.maximo > 0) note(app, id, hour, v.nivel * 100 / v.maximo)
        }
    }

    @Synchronized private fun note(ctx: Context, id: String, hour: Int, pct: Int) {
        val all = load(ctx)
        SofyHabits.noteVolume(all.getOrPut(id) { SofyHabits.Person() }, hour, pct.coerceIn(0, 100))
        val now = System.currentTimeMillis()
        if (now - lastSave > 30 * 60_000L) { lastSave = now; save(ctx) }
    }

    @Synchronized fun save(ctx: Context) {
        val all = people ?: return
        ObserverTv.learning(ctx).edit().putString("habits", SofyHabits.toJson(all).toString()).apply()
    }

    fun json(ctx: Context): JSONObject = SofyHabits.toJson(load(ctx.applicationContext))

    // ---------------------------------------------------------------- o que as Sugestões perguntam

    /** Ideia 21: o teto da noite a propor para quem está na TV, ou null (já tem teto, ou sem dado). */
    fun nightCeiling(ctx: Context): Int? {
        if (Extras.numero(ctx, KEY_NIGHT_CEILING, 0) > 0) return null
        return load(ctx)[SofyHabits.profileId(profileKey(ctx))]?.let { SofyHabits.nightCeiling(it) }
    }

    /** O teto que vale AGORA (hora de dormir das crianças primeiro), em %; null = sem teto. Lido pela tecla de volume. */
    fun ceilingNow(ctx: Context, hour: Int): Int? {
        val t = Extras.numero(ctx, KEY_NIGHT_CEILING, 0)
        return t.takeIf { it > 0 && (hour >= NIGHT_FROM || hour < NIGHT_TO) }
    }

    /** Ideia 20: noites da semana em que ela desligou sozinha (barramento "house.auto_off") e o degrau mais curto. */
    fun dozed(ctx: Context, events: List<SofyBus.Event>): Pair<Int, Int?> {
        val offs = events.filter { it.source == SofyBus.Source.HOUSE && it.kind == "auto_off" }.map { it.at }
        val min = AutoDesligar.minutos(ctx)
        return SofyHabits.dozedNights(offs, TimeZone.getDefault()) to (if (min > 0) SofyHabits.shorterOff(min, AutoDesligar.OPCOES) else null)
    }

    /** Ideia 22: o alarme a adiar e quantos minutos, só se ele ainda existe, está ligado e não passa da meia-noite. */
    fun snooze(ctx: Context, events: List<SofyBus.Event>): Pair<Despertador.Alarme, Int>? {
        val (id, min) = SofyHabits.snoozeShift(events, TimeZone.getDefault()) ?: return null
        val a = runCatching { Despertador.listar(ctx) }.getOrNull()?.firstOrNull { it.id == id && it.ativo } ?: return null
        if (a.hora * 60 + a.minuto + min >= 24 * 60) return null
        return a to min
    }

    /** Ideia 18: a rotina a propor (das aberturas da semana), fora o que já está agendado para abrir. */
    fun routine(ctx: Context, now: Long): SofyHabits.Routine? {
        val opens = opens(ctx, now - 7 * DAY_MS, now)
        val have = runCatching { Rotinas.listar(ctx) }.getOrDefault(emptyList()).filter { it.acao == Rotinas.Acao.ABRIR }.map { it.pacote }.toSet()
        val skip = have + setOf(ctx.packageName, "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.android.tv.settings")
        val launchable = launchable(ctx)
        return SofyHabits.routine(opens.filter { launchable == null || it.second in launchable }, TimeZone.getDefault(), skip)
    }

    /**
     * Ideia 17: os apps abertos na última meia hora são a cara de outro perfil? Devolve a CONTA desse perfil (para
     * Perfil.definirAtiva), ou null. Só com duas contas ou mais na TV e fora do modo visita.
     */
    fun whoIsWatching(ctx: Context, now: Long): String? {
        if (ModoVisita.ativo(ctx)) return null
        val contas = Perfil.contas(ctx)
        if (contas.size < 2) return null
        val active = profileKey(ctx)
        val fichas = AppProfileTv.opensByProfile(ctx)
        val launchable = launchable(ctx)
        val recent = opens(ctx, now - 30 * 60_000L, now).filter { (launchable == null || it.second in launchable) && it.second != ctx.packageName }
            .map { ObserverTv.hours(it.first).second to it.second }.distinct()
        val other = SofyHabits.whoIsWatching(fichas, active, recent) ?: return null
        return contas.firstOrNull { PerfilDados.chaveDoPerfil(it, false) == other }
    }

    /** Ideia 19: o app de costume desta hora para quem está na TV (marca na HOME; a ordem não muda). */
    fun usualNow(ctx: Context, now: Long): String? =
        AppProfileTv.opensByProfile(ctx)[profileKey(ctx)]?.let { SofyHabits.usual(it, ObserverTv.hours(now).second) }

    private fun launchable(ctx: Context): Set<String>? = runCatching {
        val pm = ctx.packageManager
        val i = android.content.Intent(android.content.Intent.ACTION_MAIN)
        (pm.queryIntentActivities(android.content.Intent(i).addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER), 0) +
            pm.queryIntentActivities(android.content.Intent(i).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0))
            .map { it.activityInfo.packageName }.toSet()
    }.getOrNull()

    /** Aberturas de app (ACTIVITY_RESUMED) entre [from] e [to]: instante e pacote. Sem a permissão de uso, vazio. */
    private fun opens(ctx: Context, from: Long, to: Long): List<Pair<Long, String>> = try {
        val ev = ctx.getSystemService(UsageStatsManager::class.java).queryEvents(from, to)
        val e = UsageEvents.Event()
        val out = ArrayList<Pair<Long, String>>()
        var last: String? = null
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val p = e.packageName ?: continue
            if (p == last) continue   // trocar de tela dentro do mesmo app não é abrir de novo
            last = p
            out += e.timeStamp to p
        }
        out
    } catch (e: Exception) { Log.w(TAG, "uso: ${e.javaClass.simpleName}"); emptyList() }
}
