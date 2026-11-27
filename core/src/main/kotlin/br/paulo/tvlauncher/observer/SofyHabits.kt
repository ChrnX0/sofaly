package br.paulo.tvlauncher.observer

import java.util.Calendar
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hábitos de cada pessoa (24/11/2026, ideias 16 a 22 do estudo "extrapolar a Sofy").
 *
 * Por quê: até aqui a Sofy aprendia a CASA (a hora em que a TV fica em uso, ObserverHabit). Numa casa com duas pessoas a
 * média das duas não é o hábito de ninguém. Para quê: cada perfil tem a sua ficha e dela saem as propostas desta parte:
 *  - 16: a ficha por pessoa: que app abre a cada hora já vem da ficha dos apps (AppProfile, por perfil; aqui chega como
 *    pacote → aberturas por hora) e o volume de cada hora fica em [Person]. A legenda é do sistema, não do perfil: fora.
 *  - 17: quem está vendo, sem câmera ([whoIsWatching]): os apps de agora batem com a ficha de outro perfil = oferece trocar.
 *  - 18: rotina vira automação ([routine]): o mesmo app aberto na mesma meia hora em 4 de 7 dias = propõe uma Rotina.
 *  - 19: a HOME pela hora ([usual]): o app de costume desta hora ganha uma marca; a ORDEM nunca muda (freio da ideia).
 *  - 20: dormiu vendo ([dozedNights]): a TV se desligou sozinha de madrugada em 3 de 7 noites = propõe desligar mais cedo.
 *  - 21: volume da noite ([nightCeiling]): de madrugada o volume fica bem acima do de dia = propõe um teto da noite.
 *  - 22: alarme que se ajusta ([snoozeShift]): o mesmo despertador foi adiado em 3 dos últimos 5 dias em que tocou =
 *    propõe tocar mais tarde (o tempo médio das sonecas).
 * Tudo vira pergunta pelas Sugestões (uma voz só, SofyTv.mayAsk); nada muda sem o "Ligar".
 * Como: médias que andam devagar; o perfil do volume é guardado por um resumo da chave (nunca o e-mail). Pura: SofyHabitsTest.
 */
object SofyHabits {
    /** "De costume" (19): pelo menos isto de aberturas nesta hora e esta fração delas. */
    const val USUAL_MIN = 4.0
    const val USUAL_SHARE = 0.4
    /** Quem está vendo (17): a ficha precisa de tanta abertura para valer, e o outro perfil precisa ganhar por tanto. */
    const val WHO_MIN_OPENS = 20.0
    const val WHO_FACTOR = 2.0
    /** Rotina (18): dias de 7 e o tamanho da janela. */
    const val ROUTINE_DAYS = 4
    const val ROUTINE_WINDOW_MIN = 30
    /** Dormiu vendo (20): noites de 7. */
    const val DOZE_NIGHTS = 3
    /** Volume da noite (21): amostras por hora, mínimo de volume à noite e a folga sobre o dia (pontos de %). */
    const val VOL_MIN_SAMPLES = 3
    const val VOL_NIGHT_MIN = 50.0
    const val VOL_GAP = 10.0
    /** Alarme (22): dias de soneca nos últimos [SNOOZE_WINDOW] dias em que tocou, e o adiamento máximo. */
    const val SNOOZE_DAYS = 3
    const val SNOOZE_WINDOW = 5
    const val SNOOZE_MAX_MIN = 30

    val NIGHT_HOURS = setOf(23, 0, 1, 2, 3, 4)
    val DAY_HOURS = (8..21).toSet()

    /** A chave do perfil vira um resumo curto: a ficha viaja no backup e não precisa do e-mail. "" = sem conta. */
    fun profileId(key: String): String = if (key.isEmpty()) "_" else "p" + Integer.toHexString(key.hashCode())

    /** Volume de cada hora (% do máximo), média que anda devagar, e quantas amostras. */
    class Person {
        val volume = DoubleArray(24)
        val volumeN = IntArray(24)
    }

    /** Pacote → aberturas por hora (24), como na ficha dos apps. */
    private fun around(a: List<Double>, h: Int) = a[Math.floorMod(h - 1, 24)] + a[h] + a[(h + 1) % 24]

    fun noteVolume(p: Person, hour: Int, pct: Int) {
        val n = p.volumeN[hour]
        val a = maxOf(0.1, 1.0 / (n + 1))
        p.volume[hour] = if (n == 0) pct.toDouble() else p.volume[hour] + a * (pct - p.volume[hour])
        p.volumeN[hour] = minOf(n + 1, 10_000)
    }

    /** Ideia 19: o app de costume desta hora (a vizinha conta meio), ou null. */
    fun usual(opens: Map<String, List<Double>>, hour: Int): String? {
        fun w(a: List<Double>) = a[hour] + 0.5 * (a[Math.floorMod(hour - 1, 24)] + a[(hour + 1) % 24])
        val best = opens.maxByOrNull { w(it.value) } ?: return null
        val top = w(best.value)
        val all = opens.values.sumOf { w(it) }
        return best.key.takeIf { top >= USUAL_MIN && all > 0 && top / all >= USUAL_SHARE }
    }

    /**
     * Ideia 17: os apps abertos agora ([recent], hora e pacote) são mais "a cara" de outro perfil? Devolve a chave dele,
     * ou null. Cada perfil precisa de [WHO_MIN_OPENS] aberturas na ficha; o outro precisa ganhar por [WHO_FACTOR].
     */
    fun whoIsWatching(people: Map<String, Map<String, List<Double>>>, active: String, recent: List<Pair<Int, String>>): String? {
        if (recent.size < 2) return null
        val cur = people[active] ?: return null
        fun score(p: Map<String, List<Double>>): Double {
            val tot = p.values.sumOf { it.sum() }
            if (tot < WHO_MIN_OPENS) return -1.0
            return recent.sumOf { (h, pkg) -> p[pkg]?.let { (it.sum() + 2 * around(it, h)) / tot } ?: 0.0 } / recent.size
        }
        val mine = score(cur)
        if (mine < 0) return null
        val (id, s) = people.filterKeys { it != active }.mapValues { score(it.value) }.maxByOrNull { it.value } ?: return null
        return id.takeIf { s >= 0.3 && s >= WHO_FACTOR * mine }
    }

    /** [weekdays]: um bit por dia da semana em que aconteceu (bit 0 = domingo), o formato das Rotinas. */
    data class Routine(val pkg: String, val minuteOfDay: Int, val days: Int, val weekdays: Int)

    /**
     * Ideia 18: das aberturas da semana ([opens]: instante e pacote), o app aberto na mesma meia hora em [ROUTINE_DAYS]
     * dias diferentes. A hora proposta é a mais cedo dos dias da janela, arredondada para baixo em 5 min (a Rotina abre
     * um pouco antes, nunca depois). Pacotes em [skip] (o próprio launcher, os já agendados) ficam de fora.
     */
    fun routine(opens: List<Pair<Long, String>>, tz: TimeZone, skip: Set<String> = emptySet()): Routine? {
        val cal = Calendar.getInstance(tz)
        // Por app: por dia, o primeiro minuto do dia em que abriu (o resto do dia é continuação).
        val first = HashMap<String, HashMap<Int, Int>>()
        val weekday = HashMap<Int, Int>()
        for ((t, pkg) in opens) {
            if (pkg in skip) continue
            cal.timeInMillis = t
            val day = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
            val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            weekday[day] = cal.get(Calendar.DAY_OF_WEEK) - 1
            val d = first.getOrPut(pkg) { HashMap() }
            d[day] = minOf(d[day] ?: m, m)
        }
        var best: Routine? = null
        for ((pkg, days) in first) {
            for (start in days.values) {
                val inWin = days.filterValues { it >= start && it < start + ROUTINE_WINDOW_MIN }
                if (inWin.size >= ROUTINE_DAYS && (best == null || inWin.size > best.days))
                    best = Routine(pkg, inWin.values.min() / 5 * 5, inWin.size, inWin.keys.fold(0) { m, d -> m or (1 shl (weekday[d] ?: 0)) })
            }
        }
        return best
    }

    /** Ideia 20: noites diferentes (a madrugada conta como a noite anterior) em que a TV se desligou sozinha entre 22 h e 5 h. */
    fun dozedNights(offs: List<Long>, tz: TimeZone): Int {
        val cal = Calendar.getInstance(tz)
        val nights = HashSet<Int>()
        for (t in offs) {
            cal.timeInMillis = t
            val h = cal.get(Calendar.HOUR_OF_DAY)
            if (h in 5 until 22) continue
            if (h < 5) cal.add(Calendar.DAY_OF_YEAR, -1)
            nights += cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        }
        return nights.size
    }

    /** O desligar sozinha mais curto a propor: o degrau abaixo do atual nas [options], nunca menos de 60 min. */
    fun shorterOff(currentMin: Int, options: List<Int>): Int? =
        options.filter { it in 60 until currentMin }.maxOrNull()

    private fun avg(p: Person, hours: Set<Int>): Double? {
        val hs = hours.filter { p.volumeN[it] >= VOL_MIN_SAMPLES }
        if (hs.size < 2) return null
        return hs.sumOf { p.volume[it] * p.volumeN[it] } / hs.sumOf { p.volumeN[it] }
    }

    /** Ideia 21: o teto da noite a propor (% do máximo, em 5), ou null. De madrugada alto e bem acima do dia. */
    fun nightCeiling(p: Person): Int? {
        val night = avg(p, NIGHT_HOURS) ?: return null
        val day = avg(p, DAY_HOURS) ?: return null
        if (night < VOL_NIGHT_MIN || night < day + VOL_GAP) return null
        return (Math.round(day / 5) * 5).toInt().coerceIn(20, 60)
    }

    /**
     * Ideia 22: dos eventos do despertador no barramento ("rang" e "snoozed", detalhe = id do alarme), o alarme a adiar e
     * quantos minutos. Conta os últimos [SNOOZE_WINDOW] dias em que ELE tocou; em [SNOOZE_DAYS] deles houve soneca =
     * adia pela média de sonecas × [snoozeMin], no máximo [SNOOZE_MAX_MIN], em 5.
     */
    fun snoozeShift(events: List<SofyBus.Event>, tz: TimeZone, snoozeMin: Int = 10): Pair<Int, Int>? {
        val cal = Calendar.getInstance(tz)
        fun day(t: Long): Int { cal.timeInMillis = t; return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR) }
        val byAlarm = events.filter { it.source == SofyBus.Source.ALARM && (it.kind == "rang" || it.kind == "snoozed") }
            .mapNotNull { e -> e.detail.toIntOrNull()?.let { it to e } }.groupBy({ it.first }, { it.second })
        var best: Pair<Int, Int>? = null
        for ((id, evs) in byAlarm) {
            val rangDays = evs.filter { it.kind == "rang" }.map { day(it.at) }.distinct().sortedDescending().take(SNOOZE_WINDOW).toSet()
            if (rangDays.size < SNOOZE_DAYS) continue
            val snoozes = evs.filter { it.kind == "snoozed" }.groupingBy { day(it.at) }.eachCount().filterKeys { it in rangDays }
            if (snoozes.size < SNOOZE_DAYS) continue
            val avgSnoozes = snoozes.values.sum().toDouble() / snoozes.size
            val shift = (Math.round(avgSnoozes * snoozeMin / 5.0) * 5).toInt().coerceIn(5, SNOOZE_MAX_MIN)
            if (best == null || shift > best.second) best = id to shift
        }
        return best
    }

    // ---------------------------------------------------------------- guardar

    fun toJson(people: Map<String, Person>): JSONObject = JSONObject().put("v", 1).apply {
        val o = JSONObject()
        for ((id, p) in people) o.put(id, JSONObject()
            .put("v", JSONArray().apply { p.volume.forEach { put(Math.round(it * 10) / 10.0) } })
            .put("n", JSONArray().apply { p.volumeN.forEach { put(it) } }))
        put("p", o)
    }

    fun fromJson(o: JSONObject?): HashMap<String, Person> {
        val out = HashMap<String, Person>()
        val ps = o?.optJSONObject("p") ?: return out
        for (id in ps.keys()) {
            val j = ps.optJSONObject(id) ?: continue
            val p = Person()
            j.optJSONArray("v")?.takeIf { it.length() == 24 }?.let { v -> for (h in 0 until 24) p.volume[h] = v.optDouble(h, 0.0) }
            j.optJSONArray("n")?.takeIf { it.length() == 24 }?.let { n -> for (h in 0 until 24) p.volumeN[h] = n.optInt(h, 0) }
            out[id] = p
        }
        return out
    }
}
