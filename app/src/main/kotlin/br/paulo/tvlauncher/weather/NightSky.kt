package br.paulo.tvlauncher.weather

import br.paulo.tvlauncher.screensaver.WidgetsTempo
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * O céu da noite (24/11/2026, Paulo: "interessante é sim, mas tem que ser bem usado para ficar útil e BONITO").
 *
 * Porquê: o protetor antigo mostrava curiosidades do céu (Estação Espacial, meteoros, superlua, eclipse) TODO dia, como
 * frase fixa; quando ele virou a home que adormece, o cálculo ficou sem tela e o README seguia prometendo "The night sky".
 * Para quê: avisar só quando vale sair na janela: um evento de verdade, visível DAQUI, com céu limpo na hora dele.
 * Noite sem evento = nada (silêncio também é informação).
 * Como: escolhe no máximo UM evento ([hoje]); a Ilha mostra a partir de [AVISO_ANTES_MS] antes, o descanso da tela
 * a partir de [DESCANSO_ANTES_MS] antes, e os dois somem no fim. Puro (NightSkyTest); o lado Android é [NightSkyTv].
 */
object NightSky {
    enum class Kind { ECLIPSE_SUN, ISS, METEORS, SUPERMOON }

    data class Event(
        val kind: Kind, val start: Long, val end: Long,
        val pass: CeuNoturno.Passagem? = null,
        val shower: CeuNoturno.ChuvaMeteoros? = null,
    )

    /** A Ilha avisa 3 h antes (dá tempo de combinar de olhar). */
    const val AVISO_ANTES_MS = 3 * 3_600_000L
    /** O descanso da tela mostra meia hora antes e durante. */
    const val DESCANSO_ANTES_MS = 30 * 60_000L
    /** Estação rasante some atrás de prédio e árvore: só passagem alta (vale a pena sair). */
    const val ISS_ALTURA_MIN = 40
    /** Chuvas fracas (Líridas 18/h, Leônidas 15/h) quase não se veem da cidade. */
    const val METEOROS_POR_HORA_MIN = 50
    /** Lua muito clara apaga os meteoros. */
    const val METEOROS_LUA_MAX = 0.6
    /** O que a previsão chama de céu aberto (WMO 0 limpo, 1 quase limpo, 2 parcialmente nublado) e chance de chuva baixa. */
    const val CODIGO_LIMPO_MAX = 2
    const val CHUVA_MAX = 20

    /**
     * Céu limpo em [ms] segundo a previsão hora a hora ([horas]). null = não sei (sem previsão para essa hora):
     * quem chama trata como "não mostra" (avisar e o céu estar fechado é pior que não avisar).
     */
    fun limpo(horas: List<WidgetsTempo.Marca>, ms: Long): Boolean? {
        val m = horas.lastOrNull { it.ms <= ms && ms - it.ms < 90 * 60_000L } ?: return null
        return m.codigo <= CODIGO_LIMPO_MAX && (m.chuva < 0 || m.chuva <= CHUVA_MAX)
    }

    /** Fração iluminada da lua (0 nova, 1 cheia), pela idade. */
    fun luaIluminada(ms: Long): Double = (1 - cos(2 * PI * Lua.idade(ms) / 29.530588853)) / 2

    /**
     * O evento da noite, ou null. Ordem de importância: eclipse do Sol (raro e no lugar), Estação Espacial alta,
     * chuva de meteoros forte no pico com lua fraca, superlua. Todos com céu limpo na hora.
     * [iss]: a próxima passagem visível (calculada fora, com o TLE); [eclipses]: os eclipses do Sol vistos daqui.
     */
    fun hoje(agora: Long, lat: Double, lon: Double, horas: List<WidgetsTempo.Marca>, iss: CeuNoturno.Passagem?,
             eclipses: List<EclipseSolar>, fuso: TimeZone = TimeZone.getDefault()): Event? {
        fun ok(e: Event, olharEm: Long) = e.takeIf { it.end > agora && it.start - agora <= 24 * 3_600_000L && limpo(horas, olharEm) == true }
        eclipses.firstOrNull { it.fimMs > agora && it.inicioMs - agora <= 24 * 3_600_000L }
            ?.let { ok(Event(Kind.ECLIPSE_SUN, it.inicioMs, it.fimMs), it.maximoMs) }?.let { return it }
        iss?.takeIf { it.alturaMax >= ISS_ALTURA_MIN && it.inicio - agora <= 12 * 3_600_000L }
            ?.let { ok(Event(Kind.ISS, it.inicio, it.fim, pass = it), (it.inicio + it.fim) / 2) }?.let { return it }
        val noite = CeuNoturno.janelaDaNoite(agora, lat, lon, fuso) ?: return null
        val meio = (noite.first + noite.second) / 2
        meteoros(agora, lat, fuso)?.takeIf { luaIluminada(meio) <= METEOROS_LUA_MAX }
            ?.let { ok(Event(Kind.METEORS, noite.first, noite.second, shower = it), meio) }?.let { return it }
        if (abs(Lua.idade(meio) - 29.530588853 / 2) <= 0.75 && CeuNoturno.distanciaLua(meio) < CeuNoturno.SUPERLUA_KM) {
            ok(Event(Kind.SUPERMOON, noite.first, minOf(noite.second, noite.first + 3 * 3_600_000L)), noite.first + 3_600_000L)?.let { return it }
        }
        return null
    }

    /** Só a noite do pico (±1 dia) das chuvas fortes; [CeuNoturno.meteoros] já confere se o radiante sobe daqui. */
    fun meteoros(agora: Long, lat: Double, fuso: TimeZone = TimeZone.getDefault()): CeuNoturno.ChuvaMeteoros? {
        val m = CeuNoturno.meteoros(agora, lat, fuso)?.takeIf { it.porHora >= METEOROS_POR_HORA_MIN } ?: return null
        val ano = Calendar.getInstance(fuso).apply { timeInMillis = agora }.get(Calendar.YEAR)
        val pico = Calendar.getInstance(fuso).apply { clear(); set(ano, m.mes - 1, m.dia, 12, 0) }.timeInMillis
        return m.takeIf { abs(agora - pico) <= 86_400_000L }
    }

    /** A Ilha mostra? */
    fun naIlha(e: Event, agora: Long) = agora >= e.start - AVISO_ANTES_MS && agora < e.end
    /** O descanso da tela mostra? */
    fun noDescanso(e: Event, agora: Long) = agora >= e.start - DESCANSO_ANTES_MS && agora < e.end
}
