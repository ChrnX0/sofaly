package br.paulo.tvlauncher.weather

import br.paulo.tvlauncher.LauncherApp
import br.paulo.tvlauncher.resources.R
import br.paulo.tvlauncher.txt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Fase da lua, calculada no próprio aparelho (sem internet).
 *
 * A lua repete o ciclo a cada ~29,53 dias (mês sinódico). Contamos o tempo desde uma lua nova conhecida
 * (6/1/2000, 18:14 UTC) e tiramos o resto da divisão por 29,53: isso é a "idade" da lua.
 * O erro é de algumas horas — suficiente para o nome da fase e a porcentagem iluminada.
 */
object Lua {
    private const val CICLO_DIAS = 29.530588853
    private const val LUA_NOVA_REFERENCIA_MS = 947_182_440_000L   // 2000-01-06 18:14 UTC

    // Os nomes das fases ficam em res/values/strings.xml ("fases_lua"), traduzidos pelo idioma da TV.
    private val NOMES get() = LauncherApp.instancia.resources.getStringArray(R.array.fases_lua)

    // Emojis na ordem das fases, como vistos do hemisfério norte (iluminada à direita enquanto cresce).
    private val EMOJIS = arrayOf("🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘")

    /**
     * Idade da lua em dias: 0 = nova, ~14,77 = cheia, volta a 0 em ~29,53. Contada a partir da lua nova VERDADEIRA
     * anterior ([fase], Meeus), não do ciclo médio: o médio erra até ~14 h e mostrou a lua nova de 8/12/2026 em 9/12
     * (Sala, 25/11/2026). Reescalada para o ciclo médio, para a fase e a porcentagem seguirem as mesmas contas.
     */
    fun idade(agoraMs: Long): Double {
        val antes = anterior(agoraMs, cheia = false)
        val depois = proxima(agoraMs, cheia = false)
        return (agoraMs - antes).toDouble() / (depois - antes) * CICLO_DIAS
    }

    /** O instante (ms UTC) da próxima lua nova ou cheia DEPOIS de [agoraMs]. */
    fun proxima(agoraMs: Long, cheia: Boolean): Long {
        val k0 = Math.floor((agoraMs - LUA_NOVA_REFERENCIA_MS) / (CICLO_DIAS * 86_400_000.0)).toLong() - 1
        return (0..3).map { fase(k0 + it + if (cheia) 0.5 else 0.0) }.first { it > agoraMs }
    }

    /** O instante (ms UTC) da última lua nova ou cheia ATÉ [agoraMs]. */
    fun anterior(agoraMs: Long, cheia: Boolean): Long {
        val k0 = Math.floor((agoraMs - LUA_NOVA_REFERENCIA_MS) / (CICLO_DIAS * 86_400_000.0)).toLong() + 1
        return (0..3).map { fase(k0 - it + if (cheia) 0.5 else 0.0) }.first { it <= agoraMs }
    }

    /**
     * Instante da lua nova (k inteiro) ou cheia (k + 0,5) número k contado de 6/1/2000, pelo método de Jean Meeus,
     * "Astronomical Algorithms", 2ª ed., cap. 49 (termos periódicos; os planetários, < 1 min, ficam de fora). Erro de
     * poucos minutos (LuaTest compara com o PyEphem de 2026 a 2030). Puro: ms UTC.
     */
    fun fase(k: Double): Long {
        val t = k / 1236.85
        val t2 = t * t; val t3 = t2 * t; val t4 = t3 * t
        var jde = 2451550.09766 + 29.530588861 * k + 0.00015437 * t2 - 0.000000150 * t3 + 0.00000000073 * t4
        val e = 1 - 0.002516 * t - 0.0000074 * t2
        fun r(g: Double) = Math.toRadians(g % 360)
        val m = r(2.5534 + 29.10535670 * k - 0.0000014 * t2 - 0.00000011 * t3)
        val ml = r(201.5643 + 385.81693528 * k + 0.0107582 * t2 + 0.00001238 * t3 - 0.000000058 * t4)
        val f = r(160.7108 + 390.67050284 * k - 0.0016118 * t2 - 0.00000227 * t3 + 0.000000011 * t4)
        val om = r(124.7746 - 1.56375588 * k + 0.0020672 * t2 + 0.00000215 * t3)
        val cheia = k - Math.floor(k) > 0.25
        jde += (if (cheia) -0.40614 else -0.40720) * sin(ml) +
            (if (cheia) 0.17302 else 0.17241) * e * sin(m) +
            (if (cheia) 0.01614 else 0.01608) * sin(2 * ml) +
            (if (cheia) 0.01043 else 0.01039) * sin(2 * f) +
            (if (cheia) 0.00734 else 0.00739) * e * sin(ml - m) -
            (if (cheia) 0.00515 else 0.00514) * e * sin(ml + m) +
            (if (cheia) 0.00209 else 0.00208) * e * e * sin(2 * m) -
            0.00111 * sin(ml - 2 * f) - 0.00057 * sin(ml + 2 * f) + 0.00056 * e * sin(2 * ml + m) -
            0.00042 * sin(3 * ml) + 0.00042 * e * sin(m + 2 * f) + 0.00038 * e * sin(m - 2 * f) -
            0.00024 * e * sin(2 * ml - m) - 0.00017 * sin(om) - 0.00007 * sin(ml + 2 * m) +
            0.00004 * sin(2 * ml - 2 * f) + 0.00004 * sin(3 * m) + 0.00003 * sin(ml + m - 2 * f) +
            0.00003 * sin(2 * ml + 2 * f) - 0.00003 * sin(ml + m + 2 * f) + 0.00003 * sin(ml - m + 2 * f) -
            0.00002 * sin(ml - m - 2 * f) - 0.00002 * sin(3 * ml + m) + 0.00002 * sin(4 * ml)
        // JDE é tempo dinâmico; o relógio civil (UTC) fica ~69 s atrás (ΔT de 2026).
        return ((jde - 2440587.5) * 86_400_000.0).toLong() - 69_000L
    }

    /** Qual das 8 fases (0..7). Cada fase ocupa 1/8 do ciclo, centrada no seu ponto (por isso o +0,5). */
    private fun indiceDaFase(idade: Double): Int = (((idade / CICLO_DIAS) * 8) + 0.5).toInt() % 8

    /**
     * Só o emoji da fase. [latitude] negativa = hemisfério sul: lá a lua aparece espelhada
     * (cresce com o lado iluminado para a esquerda), então espelhamos o emoji.
     * Também é o ícone do clima à noite, no lugar do sol.
     */
    fun emoji(agoraMs: Long, latitude: Double): String {
        val i = indiceDaFase(idade(agoraMs))
        return if (latitude < 0) EMOJIS[(8 - i) % 8] else EMOJIS[i]
    }

    private val ARQUIVOS = arrayOf("moon-new", "moon-waxing-crescent", "moon-first-quarter", "moon-waxing-gibbous",
        "moon-full", "moon-waning-gibbous", "moon-last-quarter", "moon-waning-crescent")

    /** Chave do ícone-imagem da fase (ver IconesClima.kt); no hemisfério sul a fase é espelhada, como no emoji. */
    fun chaveIcone(agoraMs: Long, latitude: Double): String {
        val i = indiceDaFase(idade(agoraMs))
        return IconesClima.PREFIXO + (if (latitude < 0) ARQUIVOS[(8 - i) % 8] else ARQUIVOS[i])
    }

    /** Ex.: "🌔  Minguante gibosa  ·  75%  ·  nova em 10 d". */
    fun resumo(agoraMs: Long, latitude: Double): String {
        val idade = idade(agoraMs)
        val fracao = idade / CICLO_DIAS
        // Fração iluminada do disco: 0% na nova, 100% na cheia (curva de cosseno).
        val iluminada = ((1 - cos(2 * PI * fracao)) / 2 * 100).roundToInt()

        val diasParaCheia = (proxima(agoraMs, cheia = true) - agoraMs) / 86_400_000.0
        val diasParaNova = (proxima(agoraMs, cheia = false) - agoraMs) / 86_400_000.0
        // Já com o nome "Lua cheia" (ou "Lua nova"), "cheia em 1 d" ao lado se contradizia (teste 100%, Sala, 23/11/2026):
        // nessa fase o resumo fala do OUTRO evento.
        val fase = indiceDaFase(idade)
        val falarDaCheia = when (fase) { 4 -> false; 0 -> true; else -> diasParaCheia <= diasParaNova }
        val proxima = if (falarDaCheia) {
            txt(R.string.lua_cheia_em, diasNoCalendario(agoraMs, diasParaCheia))
        } else {
            txt(R.string.lua_nova_em, diasNoCalendario(agoraMs, diasParaNova))
        }
        return "${emoji(agoraMs, latitude)}  ${NOMES[indiceDaFase(idade)]}  ·  $iluminada%  ·  $proxima"
    }

    /**
     * Em quantos dias do calendário cai o evento (no fuso da TV). Antes era o teto das horas: a cheia de 24/11 aparecia
     * "em 8 d" no dia 17 (faltavam 7,1 dias; Sala, 17/11/2026). Mínimo 1: no próprio dia o nome da fase já diz.
     */
    fun diasNoCalendario(agoraMs: Long, dias: Double, fuso: java.util.TimeZone = java.util.TimeZone.getDefault()): Int {
        val evento = agoraMs + (dias * 86_400_000).toLong()
        fun dia(ms: Long) = Math.floorDiv(ms + fuso.getOffset(ms), 86_400_000L)
        return maxOf(1, (dia(evento) - dia(agoraMs)).toInt())
    }

}
