package br.paulo.tvlauncher.observer

import br.paulo.tvlauncher.observer.SofyRepair.Attempt
import br.paulo.tvlauncher.observer.SofyRepair.Case
import br.paulo.tvlauncher.observer.SofyRepair.Need
import br.paulo.tvlauncher.observer.SofyRepair.Result
import br.paulo.tvlauncher.observer.SofyRepair.Tool
import br.paulo.tvlauncher.observer.SofyRepair.Type
import br.paulo.tvlauncher.observer.SofyRules.Kind
import br.paulo.tvlauncher.observer.SofyRules.Rule
import org.json.JSONObject

/**
 * Banco de prova dos modelos da IA (Paulo, 26/11/2026: "faz esse teste direito e com uma amostragem maior... relatório
 * completo com os valores, os tokens, capacidades e eficiência de todos os modelos"). Regras puras: SofyBenchTest.
 *
 * Três provas com GABARITO, as mesmas perguntas que a Sofy faz de verdade ([SofyAiRules.SYSTEM], [SofyRules.SYSTEM],
 * [SofyRules.SECOND_SYSTEM]), para todos os modelos responderem a mesma coisa:
 * - REPAIR: casos de defeito com a ferramenta certa conhecida ([good]) e as que fariam mal ([bad]).
 * - REVIEW: telas com UM texto errado plantado ([ReviewItem.bad]) e telas limpas (null): a IA aponta o defeito? a regra
 *   que ela escreve pega o texto errado e fica calada num [CORPUS] de textos certos (outras línguas, lugares, datas)?
 * - SECOND: regras candidatas com rótulo (boa/ruim); a IA, como segunda opinião, mantém só as boas?
 * O gabarito é conferido por teste: cada regra "boa" pega o seu defeito e não dispara no corpus; cada "ruim" dispara.
 */
object SofyBench {
    enum class Task { REPAIR, REVIEW, SECOND, REAL }

    data class RepairItem(val id: String, val case: Case, val good: Set<Tool>, val bad: Set<Tool>)
    data class ReviewItem(val id: String, val screen: String, val texts: List<String>, val bad: String?)
    data class JudgeItem(val id: String, val screen: String, val texts: List<String>, val rules: List<Rule>, val keep: Set<String>)

    private fun case(id: String, type: Type, subject: String, count: Int = 2, detail: String = "", needs: Set<Need> = emptySet(),
                     tried: List<Tool> = emptyList(), context: List<String> = emptyList()) =
        Case("${type.name}:$subject", type, subject, needs, 0, 0, count,
            tried = tried.map { Attempt(it, 0, Result.NOT_FIXED) }, context = context, detail = detail)

    private val HEAVY = setOf(Tool.REBOOT, Tool.RESTART_LAUNCHER, Tool.CLEAR_APP_CACHE, Tool.UNDO_LAST_CHANGE, Tool.DISABLE_FEATURE)

    /** Casos de conserto. O certo depende do contexto (o que mudou antes, o que já falhou): é aí que o modelo mostra se lê. */
    val REPAIR: List<RepairItem> = listOf(
        RepairItem("r01", case("r01", Type.ACCESS_LOST, "shell", context = listOf("system update installed; wireless debugging turned off")),
            setOf(Tool.RELIGHT_WIRELESS_DEBUG), HEAVY),
        RepairItem("r02", case("r02", Type.ACCESS_LOST, "shell", 5, tried = listOf(Tool.RELIGHT_WIRELESS_DEBUG, Tool.RECONNECT_SHELL),
            context = listOf("adb key not used for 7 days: authorization revoked")), setOf(Tool.REPAIR_SHELL), HEAVY),
        RepairItem("r03", case("r03", Type.ACCESS_LOST, "accessibility", context = listOf("launcher updated 5 min ago")),
            setOf(Tool.RELIGHT_ACCESSIBILITY), setOf(Tool.REBOOT, Tool.CLEAR_APP_CACHE, Tool.UNDO_LAST_CHANGE, Tool.DISABLE_FEATURE)),
        RepairItem("r04", case("r04", Type.ACCESS_LOST, "tunnel", context = listOf("Wi-Fi reconnected; TV IP address changed")),
            setOf(Tool.RESTART_TUNNEL), HEAVY),
        RepairItem("r05", case("r05", Type.PERMISSION_LOST, "perm:overlay", needs = setOf(Need.OVERLAY), context = listOf("overlay permission revoked")),
            setOf(Tool.GRANT_PERMISSIONS), HEAVY),
        RepairItem("r06", case("r06", Type.ERROR, "data:weather", detail = "weather source answered HTTP 503", context = listOf("network ok")),
            setOf(Tool.REFRESH_DATA), HEAVY),
        RepairItem("r07", case("r07", Type.ERROR, "feature:radio", 4, detail = "stream fails to open", tried = listOf(Tool.REFRESH_DATA),
            context = listOf("setting radio_quality changed 2 min before the first error")), setOf(Tool.UNDO_LAST_CHANGE),
            setOf(Tool.REBOOT, Tool.FREE_MEMORY, Tool.RESTART_TUNNEL, Tool.GRANT_PERMISSIONS)),
        RepairItem("r08", case("r08", Type.CRASH, "feature:widgets", 3, detail = "crash in the new widget layer", tried = listOf(Tool.RESTART_LAUNCHER),
            context = listOf("launcher updated 1 h ago")), setOf(Tool.DISABLE_FEATURE, Tool.UNDO_LAST_CHANGE, Tool.REPORT),
            setOf(Tool.FREE_MEMORY, Tool.RESTART_TUNNEL, Tool.GRANT_PERMISSIONS, Tool.REBOOT)),
        RepairItem("r09", case("r09", Type.SLOW, "home", detail = "free memory 80 MB, 14 apps in background"),
            setOf(Tool.FREE_MEMORY), setOf(Tool.UNDO_LAST_CHANGE, Tool.DISABLE_FEATURE, Tool.GRANT_PERMISSIONS, Tool.REBOOT)),
        RepairItem("r10", case("r10", Type.NO_EFFECT, "app:com.netflix.ninja", detail = "launch sent, app did not come to front"),
            setOf(Tool.RESTART_APP), setOf(Tool.REBOOT, Tool.RESTART_LAUNCHER, Tool.UNDO_LAST_CHANGE, Tool.DISABLE_FEATURE)),
        RepairItem("r11", case("r11", Type.NO_EFFECT, "app:com.google.android.youtube.tv", 4, detail = "launch sent, app did not come to front",
            tried = listOf(Tool.RESTART_APP, Tool.RESTART_APP)), setOf(Tool.CLEAR_APP_CACHE, Tool.FREE_MEMORY),
            setOf(Tool.RESTART_TUNNEL, Tool.GRANT_PERMISSIONS, Tool.UNDO_LAST_CHANGE, Tool.DISABLE_FEATURE)),
        RepairItem("r12", case("r12", Type.NO_EFFECT, "tile:bluetooth", needs = setOf(Need.SECURE_SETTINGS),
            context = listOf("WRITE_SECURE_SETTINGS missing")), setOf(Tool.GRANT_PERMISSIONS), HEAVY + Tool.FREE_MEMORY),
        RepairItem("r13", case("r13", Type.INCOHERENT, "data:calendar", detail = "same event shown twice with different names"),
            setOf(Tool.REFRESH_DATA), setOf(Tool.REBOOT, Tool.CLEAR_APP_CACHE, Tool.GRANT_PERMISSIONS, Tool.FREE_MEMORY)),
        RepairItem("r14", case("r14", Type.INCOHERENT, "norm:memory", detail = "memory use 40% above the house normal for 3 days"),
            setOf(Tool.FREE_MEMORY), setOf(Tool.UNDO_LAST_CHANGE, Tool.GRANT_PERMISSIONS, Tool.RESTART_TUNNEL, Tool.DISABLE_FEATURE)),
        RepairItem("r15", case("r15", Type.SCREEN_BROKEN, "screen:config", detail = "text cut: Configurações"),
            setOf(Tool.REPORT), setOf(Tool.REBOOT, Tool.FREE_MEMORY, Tool.CLEAR_APP_CACHE, Tool.RESTART_TUNNEL, Tool.GRANT_PERMISSIONS)),
        RepairItem("r16", case("r16", Type.ERROR, "app:com.spotify.tv.android", detail = "app not responding (ANR)", context = listOf("free memory ok")),
            setOf(Tool.RESTART_APP), setOf(Tool.RESTART_LAUNCHER, Tool.REBOOT, Tool.UNDO_LAST_CHANGE, Tool.DISABLE_FEATURE)),
        RepairItem("r17", case("r17", Type.SLOW, "app:com.globo.globotv", 9, detail = "free memory 60 MB",
            tried = listOf(Tool.FREE_MEMORY, Tool.RESTART_APP, Tool.CLEAR_APP_CACHE), context = listOf("night, nobody watching")),
            setOf(Tool.REBOOT), setOf(Tool.GRANT_PERMISSIONS, Tool.RESTART_TUNNEL, Tool.UNDO_LAST_CHANGE)),
        RepairItem("r18", case("r18", Type.ACCESS_LOST, "wireless_debug", context = listOf("Wi-Fi network changed")),
            setOf(Tool.RELIGHT_WIRELESS_DEBUG), HEAVY),
        RepairItem("r19", case("r19", Type.ERROR, "feature:mcp", detail = "server port bind failed: address in use",
            context = listOf("launcher process restarted twice in 1 min")), setOf(Tool.RESTART_LAUNCHER),
            setOf(Tool.GRANT_PERMISSIONS, Tool.CLEAR_APP_CACHE, Tool.FREE_MEMORY, Tool.RELIGHT_ACCESSIBILITY)),
        RepairItem("r20", case("r20", Type.PERMISSION_LOST, "perm:usage_stats", needs = setOf(Need.USAGE_STATS)),
            setOf(Tool.GRANT_PERMISSIONS), HEAVY),
        RepairItem("r21", case("r21", Type.ACCESS_LOST, "tunnel", 6, tried = listOf(Tool.RESTART_TUNNEL, Tool.RESTART_TUNNEL, Tool.RESTART_TUNNEL),
            context = listOf("internet down: DNS fails for every host", "router rebooting")), setOf(Tool.REPORT),
            setOf(Tool.REBOOT, Tool.RESTART_LAUNCHER, Tool.CLEAR_APP_CACHE, Tool.REPAIR_SHELL, Tool.UNDO_LAST_CHANGE)),
        RepairItem("r22", case("r22", Type.CRASH, "feature:screensaver", 3, detail = "crash in the photo decoder",
            context = listOf("a 48 MP photo was added to the screensaver album")), setOf(Tool.DISABLE_FEATURE, Tool.REPORT),
            setOf(Tool.REBOOT, Tool.GRANT_PERMISSIONS, Tool.RESTART_TUNNEL, Tool.FREE_MEMORY)),
        RepairItem("r23", case("r23", Type.NO_EFFECT, "home", needs = setOf(Need.ACCESSIBILITY), detail = "HOME key did nothing",
            context = listOf("accessibility service crashed")), setOf(Tool.RELIGHT_ACCESSIBILITY),
            setOf(Tool.REBOOT, Tool.CLEAR_APP_CACHE, Tool.UNDO_LAST_CHANGE, Tool.DISABLE_FEATURE)),
        RepairItem("r24", case("r24", Type.ERROR, "data:epg", 3, detail = "guide refreshed fine, shows yesterday",
            tried = listOf(Tool.REFRESH_DATA), context = listOf("TV clock 1 day behind: network time off")), setOf(Tool.REPORT),
            setOf(Tool.REBOOT, Tool.CLEAR_APP_CACHE, Tool.FREE_MEMORY, Tool.RESTART_TUNNEL)),
    )

    /** Textos CERTOS de muitas casas (línguas, hemisférios, unidades): regra boa nunca dispara aqui. */
    val CORPUS: List<String> = listOf(
        "Agora 30°", "Agora −3°", "-12°C em Oslo", "104°F em Phoenix", "Hoje 31° 22°", "Máx 31°  Mín 19°", "Chance de chuva 0%",
        "Chance de chuva 100%", "Atualizado há 2 h", "Ligada há 72 h", "23:59", "00:00", "07:30", "Reunião às 14:00",
        "Duração 120 min", "Duração: 2 h 15 min", "Toca em 7 h 45 min", "Temporada 12 · Episódio 24", "Season 3, Episode 10",
        "Episódio 1", "S01E01", "Natal · 25/12", "Thanksgiving Day", "Livre: 12,4 GB de 32 GB", "Pendrive: 931 GB livres",
        "Bateria do controle: 100%", "Bateria do controle: 15%", "Versão 474353", "2026", "Ouvido 100%", "Ouvido 37%",
        "Wi-Fi 5 GHz · −61 dBm", "Ping 23 ms", "Download 412 Mbps", "Assistir", "TV", "HDMI 2", "4K HDR", "Dolby 5.1",
        "Últimos 7 dias", "Faltam 3 dias", "1º lugar", "Configurações", "Modo: Completo", "Sobre", "Ajuda", "Netflix",
        "YouTube", "Globoplay", "Ouvir", "Jogar", "Rádio Nova 101,3", "Bom dia", "Guten Morgen", "おはよう", "صباح الخير",
        "Quinta, 26 de novembro", "Thursday, November 26", "Amanhã 28° 17°", "Umidade 64%", "Vento 12 km/h",
    )

    /** Telas com um texto errado plantado (bad) e telas limpas (bad = null). Os outros textos são todos certos. */
    val REVIEW: List<ReviewItem> = listOf(
        ReviewItem("v01", "previsao", listOf("Hoje", "Agora 21°", "Máx 18°  Mín 22°", "Chance de chuva 30%", "Amanhã 25° 17°"), "Máx 18°  Mín 22°"),
        ReviewItem("v02", "previsao", listOf("Hoje", "Agora 24°", "Máx 29°  Mín 18°", "Chance de chuva 130%", "Umidade 70%"), "Chance de chuva 130%"),
        ReviewItem("v03", "armazenamento", listOf("Armazenamento", "Interno: 32 GB", "Livre: -2,3 GB", "Apps 14,1 GB", "Pendrive: 931 GB livres"), "Livre: -2,3 GB"),
        ReviewItem("v04", "diagnostico", listOf("Diagnóstico", "Wi-Fi 5 GHz · −58 dBm", "Ligada há 498783 h", "Ping 21 ms", "DNS automático"), "Ligada há 498783 h"),
        ReviewItem("v05", "agenda", listOf("Agenda", "Hoje", "Reunião às 25:70", "Dentista às 16:30", "Amanhã", "Futebol às 21:00"), "Reunião às 25:70"),
        ReviewItem("v06", "filmes", listOf("Filmes", "Duna: Parte Dois", "Duração: null min", "Ficção científica · 2024", "Assistir"), "Duração: null min"),
        ReviewItem("v07", "series", listOf("Séries", "The Bear", "Temporada 3 · Episódio {ep}", "Continuar", "Episódio 1"), "Temporada 3 · Episódio {ep}"),
        ReviewItem("v08", "despertador", listOf("Despertador", "06:30 · seg a sex", "Toca em 7 h 75 min", "Som: Rádio"), "Toca em 7 h 75 min"),
        ReviewItem("v09", "podcasts", listOf("Podcasts", "Café da Manhã", "Ouvido 145%", "Episódio 312", "Novo"), "Ouvido 145%"),
        ReviewItem("v10", "config", listOf("Configurações", "Modo: Completo", "Versão NaN", "Idioma: Português"), "Versão NaN"),
        ReviewItem("v11", "sobre", listOf("Sobre", "Bateria do controle: 250%", "Sofaly", "Versão 474353"), "Bateria do controle: 250%"),
        ReviewItem("v12", "agenda", listOf("Agenda", "Quinta, 26 de novembro", "Dentista às 16:30", "Dentista às 16:30 (cópia)", "Futebol às 21:00"), "Dentista às 16:30 (cópia)"),
        ReviewItem("c01", "previsao", listOf("Hoje", "Agora −2°", "Máx 3°  Mín −6°", "Chance de chuva 100%", "Neve fraca", "Vento 30 km/h"), null),
        ReviewItem("c02", "diagnostico", listOf("Diagnóstico", "Ligada há 72 h", "Wi-Fi 2,4 GHz · −70 dBm", "Ping 145 ms", "Download 8 Mbps"), null),
        ReviewItem("c03", "agenda", listOf("Agenda", "Thursday, November 26", "Thanksgiving Day", "Dinner at 19:00", "Black Friday sale"), null),
        ReviewItem("c04", "series", listOf("Séries", "Grey's Anatomy", "Temporada 21 · Episódio 18", "Duração 43 min", "Continuar"), null),
        ReviewItem("c05", "armazenamento", listOf("Armazenamento", "Interno: 8 GB", "Livre: 0,4 GB", "Pouco espaço", "Pendrive: 1,8 TB livres"), null),
        ReviewItem("c06", "despertador", listOf("Despertador", "00:05 · todos os dias", "Toca em 23 h 59 min", "Som: Bipe"), null),
    )

    private fun r(id: String, kind: Kind, screen: String, pattern: String, min: Double = 0.0, max: Double = 0.0, why: String = "") =
        Rule(id, kind, screen, pattern, min, max, why)

    /** Segunda opinião: em cada item, 2 regras boas ([keep]) e 2 ruins (disparam em texto certo de outra casa). */
    val SECOND: List<JudgeItem> = listOf(
        JudgeItem("j1", "previsao", listOf("Agora 21°", "Chance de chuva 130%", "Sensação null°", "Máx 29°  Mín 18°"), listOf(
            r("j1a", Kind.RANGE, "*", """chuva (\d+)%""", 0.0, 100.0, "rain chance above 100%"),
            r("j1b", Kind.FORBIDDEN, "*", """\bnull\b|\bNaN\b""", why = "placeholder shown"),
            r("j1c", Kind.FORBIDDEN, "*", """°""", why = "degree sign is suspicious"),
            r("j1d", Kind.RANGE, "*", """(\d+)°""", 0.0, 40.0, "temperature above 40")), setOf("j1a", "j1b")),
        JudgeItem("j2", "diagnostico", listOf("Ligada há 498783 h", "Ping 21 ms", "Livre: -2,3 GB"), listOf(
            r("j2a", Kind.RANGE, "*", """há (\d+) h""", 0.0, 2000.0, "uptime of decades"),
            r("j2b", Kind.FORBIDDEN, "*", """\bh$""", why = "hours suffix"),
            r("j2c", Kind.RANGE, "*", """(\d+) GB""", 0.0, 64.0, "more storage than the TV has"),
            r("j2d", Kind.FORBIDDEN, "*", """-\d+[,.]?\d* GB""", why = "negative free space")), setOf("j2a", "j2d")),
        JudgeItem("j3", "agenda", listOf("Reunião às 25:70", "Thanksgiving Day", "Futebol às 21:00"), listOf(
            r("j3a", Kind.FORBIDDEN, "*", """\b(2[4-9]|[3-9]\d):\d\d\b""", why = "hour above 23"),
            r("j3b", Kind.FORBIDDEN, "*", """Thanksgiving""", why = "holiday of another country"),
            r("j3c", Kind.FORBIDDEN, "*", """[A-Z]{2,}""", why = "shouting"),
            r("j3d", Kind.RANGE, "*", """\d:(\d\d)\b""", 0.0, 59.0, "minutes above 59")), setOf("j3a", "j3d")),
        JudgeItem("j4", "series", listOf("Temporada 3 · Episódio {ep}", "Episódio 1", "Assistido 180%", "2024"), listOf(
            r("j4a", Kind.FORBIDDEN, "*", """\{\w+\}""", why = "template placeholder"),
            r("j4b", Kind.FORBIDDEN, "*", """\d{4}""", why = "year shown"),
            r("j4c", Kind.FORBIDDEN, "*", """Episódio 1\b""", why = "first episode"),
            r("j4d", Kind.RANGE, "*", """(\d+)%""", 0.0, 100.0, "percentage above 100")), setOf("j4a", "j4d")),
        JudgeItem("j5", "despertador", listOf("Toca em 7 h 75 min", "06:30 · seg a sex", "Volume NaN", "Som: Rádio"), listOf(
            r("j5a", Kind.RANGE, "*", """h (\d+) min""", 0.0, 59.0, "minutes part above 59"),
            r("j5b", Kind.RANGE, "*", """(\d+) min""", 0.0, 59.0, "minutes above 59"),
            r("j5c", Kind.FORBIDDEN, "*", """\bNaN\b""", why = "not a number"),
            r("j5d", Kind.FORBIDDEN, "*", """Toca""", why = "rings")), setOf("j5a", "j5c")),
    )

    // ---------------------------------------------------------------- notas

    data class RepairScore(val valid: Boolean, val top1: Boolean, val any: Boolean, val harm: Int, val tools: List<Tool>)

    fun scoreRepair(item: RepairItem, answer: String?): RepairScore {
        val (tools, _) = SofyAiRules.plan(answer)
        return RepairScore(tools.isNotEmpty(), tools.firstOrNull() in item.good, tools.any { it in item.good }, tools.count { it in item.bad }, tools)
    }

    /**
     * [flagged] = apontou algum defeito. [raw] = regras na resposta; [kept] = as que passaram no filtro da TV;
     * [catch] = regras que pegam o texto plantado; [falseFire] = regras que disparam em texto certo (corpus ou a própria tela).
     */
    data class ReviewScore(val valid: Boolean, val flagged: Int, val raw: Int, val kept: Int, val catch: Int, val falseFire: Int)

    fun scoreReview(screen: String, texts: List<String>, bad: String?, answer: String?, existing: List<Rule> = emptyList()): ReviewScore {
        val o = json(answer) ?: return ReviewScore(false, 0, 0, 0, 0, 0)
        val raw = o.optJSONArray("rules")?.length() ?: 0
        val rv = SofyRules.parse(answer, screen, texts, existing, 0)
        val certos = CORPUS + texts.filter { it != bad }
        val catch = if (bad == null) 0 else rv.rules.count { SofyRules.violation(it, listOf(bad)) != null }
        val falso = rv.rules.count { SofyRules.violation(it, certos) != null }
        return ReviewScore(true, rv.defects.size, raw, rv.rules.size, catch, falso)
    }

    data class JudgeScore(val valid: Boolean, val tp: Int, val fp: Int, val fn: Int, val tn: Int)

    fun scoreJudge(item: JudgeItem, answer: String?): JudgeScore {
        if (json(answer) == null) return JudgeScore(false, 0, 0, 0, 0)
        val kept = SofyRules.agreed(answer, item.rules).map { it.id }.toSet()
        val ids = item.rules.map { it.id }
        return JudgeScore(true, ids.count { it in kept && it in item.keep }, ids.count { it in kept && it !in item.keep },
            ids.count { it !in kept && it in item.keep }, ids.count { it !in kept && it !in item.keep })
    }

    private fun json(answer: String?): JSONObject? = answer?.let { a -> val i = a.indexOf('{'); val j = a.lastIndexOf('}')
        if (i < 0 || j <= i) null else runCatching { JSONObject(a.substring(i, j + 1)) }.getOrNull() }

    /** Uma linha por chamada, só números e nomes (nada da tela nem da chave): vai para o logcat e para o arquivo. */
    fun line(task: Task, model: String, effort: String, item: String, rep: Int, http: Int, ms: Long, inTok: Long, outTok: Long,
             cacheRead: Long, stop: String, score: String) =
        "B task=${task.name} model=$model eff=${effort.ifEmpty { "-" }} item=$item rep=$rep http=$http ms=$ms in=$inTok out=$outTok " +
            "cache=$cacheRead stop=${stop.ifEmpty { "-" }} $score"

    fun fmt(s: RepairScore) = "valid=${s.valid.b} top1=${s.top1.b} any=${s.any.b} harm=${s.harm} tools=${s.tools.joinToString(",") { it.name.lowercase() }.ifEmpty { "-" }}"
    fun fmt(s: ReviewScore) = "valid=${s.valid.b} flagged=${s.flagged} raw=${s.raw} kept=${s.kept} catch=${s.catch} false=${s.falseFire}"
    fun fmt(s: JudgeScore) = "valid=${s.valid.b} tp=${s.tp} fp=${s.fp} fn=${s.fn} tn=${s.tn}"
    private val Boolean.b get() = if (this) 1 else 0
}
