package br.paulo.tvlauncher.network

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

/**
 * "A internet caiu? Em uma frase" (10/11/2026). Junta as medições de agora (Wi-Fi, conexão sem DNS, DNS sem cache,
 * página de teste do Google), o que o roteador UniFi acha da internet (se configurado) e o histórico de rede, e
 * responde em português simples, com a causa mais provável. Ex.: "O Wi-Fi está ok, mas a internet do provedor caiu
 * às 18:12; o roteador também vê a internet fora (há 6 min)."
 *
 * Só lógica, sem nada do Android, para testar no PC (InternetEmUmaFraseTest). Quem mede é [DiagnosticoInternet].
 * A ordem das perguntas segue a ordem em que um problema causa o outro: sem Wi-Fi nada funciona; sem internet o DNS
 * também falha; com internet e DNS, sobra a página de teste (bloqueio) e a qualidade (lentidão, quedas recentes).
 */
object InternetEmUmaFrase {
    enum class Causa { OK, QUEDA_RECENTE, LENTA, VPN_DESLIGADA, SEM_WIFI, ROTEADOR, PROVEDOR, ENTRE_TV_E_ROTEADOR, VPN, DNS, BLOQUEIO }

    /** O que o roteador diz (stat/health da API antiga do UniFi). Status: "ok", "error", "warning" ou null. */
    class Roteador(val wan: String?, val internet: String?, val latenciaMs: Int?) {
        /** O roteador perdeu o provedor: o cabo/modem ("wan") ou a internet em si ("www"). */
        val internetFora get() = wan == "error" || internet == "error"
        val internetOk get() = !internetFora && (internet == "ok" || wan == "ok")
    }

    /**
     * Medições de agora. [tcpMs]: conexão até 1.1.1.1:443 (internet SEM depender de DNS); [dnsMs]: resolução que pula
     * o cache; [httpCodigo]: resposta da página de teste do Google (204 = ok); null em qualquer uma = falhou.
     * [dbm] 0 = sem informação (cabo). [roteador] null = não consultado; [roteadorMudo] = configurado e não respondeu.
     */
    class Medicoes(
        val agora: Long,
        val conectada: Boolean,
        val dbm: Int = 0,
        val tcpMs: Int? = null,
        val dnsMs: Long? = null,
        val dnsNome: String? = null,
        val httpCodigo: Int? = null,
        val httpMs: Long? = null,
        val vpnFixada: Boolean = false,
        val vpnDePe: Boolean = false,
        val roteador: Roteador? = null,
        val roteadorMudo: Boolean = false,
        val historico: List<ResumoRede.Amostra> = emptyList(),
    )

    /** [nivel]: 0 = ok, 1 = atenção, 2 = falha (as mesmas cores do Diagnóstico). [sinais]: o que foi medido, curto. */
    class Resultado(val frase: String, val causa: Causa, val nivel: Int, val sinais: List<String>) {
        fun json(): JSONObject = JSONObject().put("frase", frase).put("causa", causa.name.lowercase()).put("nivel", nivel)
            .put("sinais", org.json.JSONArray(sinais))
    }

    /** Sinal abaixo disso (dBm) já trava vídeo. Resposta acima disso = "devagar". */
    const val SINAL_FRACO_DBM = -75
    const val TCP_LENTO_MS = 300
    const val HTTP_LENTO_MS = 2000L
    private const val DIA_MS = 24 * 3_600_000L
    /** Medido pelo menos isso das últimas 24 h para dizer "sem quedas nas últimas 24 h". */
    private const val COBERTURA_DIA_MS = 20 * 3_600_000L

    /**
     * Os textos da frase, por chave (padrão: português, o que o MCP e os testes usam). Na TV, [DiagnosticoInternet] passa
     * os do idioma do aparelho (strings `ief_*`): o diagnóstico seguia em português numa TV em inglês.
     */
    fun interface Textos { fun t(chave: String, vararg a: Any): String }

    val PT = Textos { chave, a -> String.format(java.util.Locale.forLanguageTag("pt-BR"), PT_TEXTOS.getValue(chave), *a) }

    /** Chave -> texto em português. As mesmas chaves existem nos strings.xml de cada idioma com o prefixo `ief_`. */
    val PT_TEXTOS: Map<String, String> = linkedMapOf(
        "sem_wifi" to "A TV está sem Wi-Fi%1\$s. Veja se o roteador está ligado ou se o nome e a senha da rede mudaram.",
        "roteador_sem_modem" to "o roteador perdeu a ligação com o modem do provedor",
        "roteador_ve_fora" to "o roteador também vê a internet fora",
        "ha" to " (há %1\$s)",
        "provedor_caiu" to "O Wi-Fi está ok, mas a internet do provedor caiu%1\$s; %2\$s%3\$s. Não é a TV: é esperar o provedor ou reiniciar o modem.",
        "vpn_travada" to "O roteador tem internet, mas a TV não passa%1\$s: a VPN deve estar travada. Desligar e ligar a VPN costuma resolver.",
        "entre_tv_roteador" to "O roteador tem internet, mas a TV não chega nela%1\$s: o problema está entre a TV e o roteador%2\$s Reconectar a TV ao Wi-Fi costuma resolver.",
        "sinal_fraco_entre" to " (o sinal do Wi-Fi está fraco, %1\$d dBm).",
        "ponto" to ".",
        "roteador_travado" to "A TV está no Wi-Fi, mas nem o roteador nem a internet respondem%1\$s: o roteador pode ter travado. Desligar e ligar o roteador costuma resolver.",
        "sem_internet" to "O Wi-Fi está ok, mas a internet não chega%1\$s; deve ser o provedor ou o roteador%2\$s",
        "vpn_culpada" to " (a VPN ligada também pode ser a culpada).",
        "dns" to "A internet chega, mas o DNS%1\$s não responde%2\$s: os apps não acham os servidores. Trocar o DNS para Automático resolve.",
        "entre_parenteses" to " (%1\$s)",
        "bloqueio" to "A internet e o DNS respondem, mas a página de teste do Google %1\$s: algo no caminho (VPN, bloqueio do DNS ou rede pedindo login) está barrando.",
        "nao_abriu" to "não abriu",
        "codigo_diferente" to "respondeu diferente (código %1\$d)",
        "vpn_desligada" to "A internet está ok; só a VPN está desligada (os apps funcionam sem ela).%1\$s",
        "lenta" to "A internet funciona, mas devagar: %1\$s.%2\$s",
        "lenta_sinal" to "o sinal do Wi-Fi está fraco (%1\$d dBm); aproximar o roteador ou usar cabo ajuda",
        "lenta_tcp" to "a internet demora %1\$d ms para responder%2\$s",
        "lenta_roteador_igual" to ", e o roteador vê o mesmo: é o provedor",
        "lenta_http" to "a página de teste levou %1\$.1f s para abrir",
        "queda_recente" to "A internet está ok agora; %1\$s.",
        "tudo_ok" to "Está tudo ok: Wi-Fi, internet e DNS respondem.%1\$s%2\$s",
        "sem_quedas_24h" to " Sem quedas nas últimas 24 h.",
        "sem_quedas_parcial" to " Nenhuma queda registrada (a TV mediu só parte das últimas 24 h).",
        "roteador_avisa" to " (O roteador avisa problema na internet, mas a TV navega normal.)",
        "caiu_dns" to "o DNS falhou",
        "caiu_internet" to "a internet caiu",
        "queda_sem_medir" to "%1\$s %2\$s (depois a TV ficou sem medir; não dá para saber quando voltou)",
        "quedas_n" to "; foram %1\$d quedas em 24 h",
        "queda_voltou" to "%1\$s %2\$s e voltou %3\$s (%4\$s)",
        "quedas_total" to "; foram %1\$d quedas em 24 h, %2\$s sem internet no total",
        "quando_hoje" to "às %1\$s",
        "quando_ontem" to "ontem às %1\$s",
        "quando_data" to "em %1\$s às %2\$s",
        "desde_hoje" to "desde as %1\$s",
        "desde_ontem" to "desde ontem às %1\$s",
        "desde_data" to "desde %1\$s às %2\$s",
        "dur_menos_1" to "menos de 1 min",
        "dur_min" to "%1\$d min",
        "dur_h" to "%1\$d h",
        "dur_h_min" to "%1\$d h %2\$d min",
        "data_dm" to "dd/MM",
        "s_wifi_desconectado" to "Wi-Fi: desconectado",
        "s_wifi_dbm" to "Wi-Fi: %1\$d dBm",
        "s_rede_conectada" to "rede: conectada",
        "s_internet" to "internet sem DNS: %1\$s",
        "s_dns" to "DNS sem cache: %1\$s%2\$s",
        "s_ms" to "%1\$d ms",
        "s_falhou" to "falhou",
        "s_pagina" to "página de teste: %1\$s",
        "s_ok" to "ok",
        "s_ok_em" to "ok em %1\$d ms",
        "s_codigo" to "código %1\$d",
        "s_vpn_ligada" to "VPN: ligada",
        "s_vpn_desligada" to "VPN: desligada",
        "s_roteador_fora" to "roteador: vê a internet fora",
        "s_roteador_ok" to "roteador: vê a internet ok%1\$s",
        "s_roteador_sem_opiniao" to "roteador: sem opinião",
        "s_roteador_mudo" to "roteador: não respondeu",
        "s_roteador_nao_consultado" to "roteador: não consultado",
        "s_historico" to "histórico: %1\$d medições em 24 h",
    )

    fun explicar(m: Medicoes, zona: ZoneId = ZoneId.systemDefault(), tx: Textos = PT): Resultado {
        val sinais = sinais(m, tx)
        fun r(frase: String, causa: Causa, nivel: Int) = Resultado(frase, causa, nivel, sinais)
        fun desdeOu(t: Long?) = t?.let { " " + desde(it, m.agora, zona, tx) } ?: ""
        val internetOk = m.tcpMs != null || m.httpCodigo == 204
        val sinalFraco = m.dbm in -126 until SINAL_FRACO_DBM
        val h = m.historico.sortedBy { it.t }

        // 1) Sem Wi-Fi (nem cabo): nada mais importa.
        if (!m.conectada) {
            val desde = inicioDaFalha(h, m.agora) { it.dbm == 0 && !it.internet }
            return r(tx.t("sem_wifi", desdeOu(desde)), Causa.SEM_WIFI, 2)
        }

        // 2) No Wi-Fi, mas a internet não chega (nem a conexão sem DNS nem a página de teste).
        if (!internetOk) {
            val desde = inicioDaFalha(h, m.agora) { !it.internet }
            val ro = m.roteador
            return when {
                ro != null && ro.internetFora -> {
                    val oque = tx.t(if (ro.wan == "error") "roteador_sem_modem" else "roteador_ve_fora")
                    val ha = desde?.let { tx.t("ha", duracao(m.agora - it, tx)) } ?: ""
                    r(tx.t("provedor_caiu", desde?.let { " " + quando(it, m.agora, zona, tx) } ?: "", oque, ha), Causa.PROVEDOR, 2)
                }
                ro != null && ro.internetOk && m.vpnDePe -> r(tx.t("vpn_travada", desdeOu(desde)), Causa.VPN, 2)
                ro != null && ro.internetOk -> r(tx.t("entre_tv_roteador", desdeOu(desde),
                    if (sinalFraco) tx.t("sinal_fraco_entre", m.dbm) else tx.t("ponto")), Causa.ENTRE_TV_E_ROTEADOR, 2)
                m.roteadorMudo -> r(tx.t("roteador_travado", desdeOu(desde)), Causa.ROTEADOR, 2)
                else -> {
                    val ha = desde?.let { " " + quando(it, m.agora, zona, tx) + tx.t("ha", duracao(m.agora - it, tx)) } ?: ""
                    r(tx.t("sem_internet", ha, if (m.vpnDePe) tx.t("vpn_culpada") else tx.t("ponto")), Causa.PROVEDOR, 2)
                }
            }
        }

        // 3) A internet chega, mas os nomes não resolvem: para os apps é o mesmo que "sem internet".
        if (m.dnsMs == null) {
            val desde = inicioDaFalha(h, m.agora) { it.internet && it.dnsMs < 0 }
            val qual = m.dnsNome?.let { tx.t("entre_parenteses", it) } ?: ""
            return r(tx.t("dns", qual, desdeOu(desde)), Causa.DNS, 2)
        }

        // 4) Internet e DNS ok, mas a página de teste não abre: alguém no caminho está barrando.
        if (m.httpCodigo != 204) {
            val como = if (m.httpCodigo == null) tx.t("nao_abriu") else tx.t("codigo_diferente", m.httpCodigo)
            return r(tx.t("bloqueio", como), Causa.BLOQUEIO, 2)
        }

        // 5) Funciona agora. Sobram a VPN, a lentidão e as quedas recentes.
        val queda = ultimaQueda(h, m.agora, zona, tx)
        val complemento = queda?.let { " " + it.replaceFirstChar { c -> c.uppercase() } + tx.t("ponto") } ?: ""
        if (m.vpnFixada && !m.vpnDePe) return r(tx.t("vpn_desligada", complemento), Causa.VPN_DESLIGADA, 1)
        val lentoTcp = (m.tcpMs ?: 0) > TCP_LENTO_MS
        val lentoHttp = (m.httpMs ?: 0) > HTTP_LENTO_MS
        if (sinalFraco || lentoTcp || lentoHttp) {
            val porque = when {
                sinalFraco -> tx.t("lenta_sinal", m.dbm)
                lentoTcp -> tx.t("lenta_tcp", m.tcpMs ?: 0,
                    if (m.roteador?.latenciaMs?.let { it > TCP_LENTO_MS } == true) tx.t("lenta_roteador_igual") else "")
                // Segundos com uma casa (vírgula ou ponto conforme o idioma: o %.1f do texto formata).
                else -> tx.t("lenta_http", (m.httpMs ?: 0) / 100 / 10.0)
            }
            return r(tx.t("lenta", porque, complemento), Causa.LENTA, 1)
        }
        if (queda != null) {
            val recente = m.historico.isNotEmpty() && quedas(h, m.agora).lastOrNull()?.let { m.agora - it.fim < 3_600_000L } == true
            return r(tx.t("queda_recente", queda), Causa.QUEDA_RECENTE, if (recente) 1 else 0)
        }
        // "Sem quedas nas últimas 24 h" só com o dia quase todo medido; senão, só o que foi registrado.
        val cobertura = h.filter { it.t >= m.agora - DIA_MS }.let { if (it.isEmpty()) -1L else ResumoRede.resumir(it, m.agora - DIA_MS, m.agora).coberturaMs }
        val semQuedas = when {
            cobertura >= COBERTURA_DIA_MS -> tx.t("sem_quedas_24h")
            cobertura >= 0 -> tx.t("sem_quedas_parcial")
            else -> ""
        }
        val avisoRoteador = if (m.roteador?.internetFora == true) tx.t("roteador_avisa") else ""
        return r(tx.t("tudo_ok", semQuedas, avisoRoteador), Causa.OK, if (avisoRoteador.isEmpty()) 0 else 1)
    }

    // ---------------------------------------------------------------- partes (públicas para teste)

    /**
     * A falha do UniFi quer dizer "o roteador não responde" (travado)? Só o tempo esgotado e a conexão recusada
     * (mensagens de UnifiCliente.traduzir). 404 da API antiga ("não respondeu nesse caminho"), chave recusada, TLS e
     * erro desconhecido ("não consegui falar…") = o roteador está de pé ou não sabemos: "não consultado", não "mudo".
     */
    fun roteadorMudo(mensagem: String?): Boolean =
        mensagem != null && (mensagem.contains("não respondeu a tempo") || mensagem.contains("nada respondeu nesse endereço"))

    /**
     * Desde quando dura a falha de AGORA, pelo histórico: a última amostra precisa ser recente e falhar, e volta enquanto
     * as anteriores também falham, sem buraco de dados no meio (buraco = não sabemos o que houve). Null = sem histórico.
     */
    fun inicioDaFalha(h: List<ResumoRede.Amostra>, agora: Long, falha: (ResumoRede.Amostra) -> Boolean): Long? {
        val ultima = h.lastOrNull() ?: return null
        if (agora - ultima.t > ResumoRede.BURACO_MS || !falha(ultima)) return null
        var i = h.size - 1
        while (i > 0 && falha(h[i - 1]) && h[i].t - h[i - 1].t <= ResumoRede.BURACO_MS) i--
        return h[i].t
    }

    /**
     * Quedas já terminadas nas últimas 24 h, da mais velha para a mais nova. O resumo fecha uma queda também quando ela
     * só MUDA de tipo (internet -> DNS): aqui os pedaços colados viram uma queda só, que termina quando tudo voltou
     * (senão a frase diria "voltou" com o DNS ainda falhando). Tipo da queda junta = "internet" se algum pedaço for.
     */
    fun quedas(h: List<ResumoRede.Amostra>, agora: Long): List<ResumoRede.Queda> {
        val juntas = mutableListOf<ResumoRede.Queda>()
        for (q in pedacos(h, agora)) {
            val ant = juntas.lastOrNull()
            if (ant != null && !ant.aberta && ant.fim == q.inicio)
                juntas[juntas.size - 1] = ResumoRede.Queda(ant.inicio, q.fim, if (ant.tipo == "internet" || q.tipo == "internet") "internet" else "dns", q.aberta)
            else juntas += q
        }
        return juntas.filter { !it.aberta }
    }

    private fun pedacos(h: List<ResumoRede.Amostra>, agora: Long) =
        ResumoRede.resumir(h.filter { it.t >= agora - DIA_MS }, agora - DIA_MS, agora).quedas

    /** "a internet caiu às 18:12 e voltou às 18:20 (8 min)" [+ "; foram 3 quedas em 24 h, 25 min fora"], ou null. */
    fun ultimaQueda(h: List<ResumoRede.Amostra>, agora: Long, zona: ZoneId, tx: Textos = PT): String? {
        val q = quedas(h, agora)
        val u = q.lastOrNull() ?: return null
        val oque = tx.t(if (u.tipo == "dns") "caiu_dns" else "caiu_internet")
        // Sem amostra no fim da queda = a TV parou de medir (desligada da tomada etc.): não dá para dizer quando voltou.
        if (h.none { it.t == u.fim }) return tx.t("queda_sem_medir", oque, quando(u.inicio, agora, zona, tx)) +
            (if (q.size > 1) tx.t("quedas_n", q.size) else "")
        // Voltou no mesmo dia em que caiu: só a hora ("às 18:20"), sem repetir "ontem".
        val fim = if (mesmoDia(u.inicio, u.fim, zona)) tx.t("quando_hoje", hm(u.fim, zona)) else quando(u.fim, agora, zona, tx)
        var s = tx.t("queda_voltou", oque, quando(u.inicio, agora, zona, tx), fim, duracao(u.duracao, tx))
        if (q.size > 1) s += tx.t("quedas_total", q.size,
            duracao(pedacos(h, agora).filter { it.tipo == "internet" && !it.aberta }.sumOf { it.duracao }, tx))
        return s
    }

    /** O que foi medido, em pedaços curtos (vai junto na resposta do MCP e na tela, embaixo da frase). */
    fun sinais(m: Medicoes, tx: Textos = PT): List<String> = buildList {
        add(if (!m.conectada) tx.t("s_wifi_desconectado") else if (m.dbm in -126..-1) tx.t("s_wifi_dbm", m.dbm) else tx.t("s_rede_conectada"))
        add(tx.t("s_internet", m.tcpMs?.let { tx.t("s_ms", it) } ?: tx.t("s_falhou")))
        add(tx.t("s_dns", m.dnsMs?.let { tx.t("s_ms", it.toInt()) } ?: tx.t("s_falhou"), m.dnsNome?.let { tx.t("entre_parenteses", it) } ?: ""))
        add(tx.t("s_pagina", when (m.httpCodigo) {
            null -> tx.t("s_falhou")
            204 -> m.httpMs?.let { tx.t("s_ok_em", it.toInt()) } ?: tx.t("s_ok")
            else -> tx.t("s_codigo", m.httpCodigo)
        }))
        if (m.vpnFixada || m.vpnDePe) add(tx.t(if (m.vpnDePe) "s_vpn_ligada" else "s_vpn_desligada"))
        add(when {
            m.roteador != null -> when {
                m.roteador.internetFora -> tx.t("s_roteador_fora")
                m.roteador.internetOk -> tx.t("s_roteador_ok", m.roteador.latenciaMs?.let { tx.t("entre_parenteses", tx.t("s_ms", it)) } ?: "")
                else -> tx.t("s_roteador_sem_opiniao")
            }
            m.roteadorMudo -> tx.t("s_roteador_mudo")
            else -> tx.t("s_roteador_nao_consultado")
        })
        add(tx.t("s_historico", m.historico.count { it.t >= m.agora - DIA_MS }))
    }

    /** Lê o stat/health do UniFi: {"data":[{"subsystem":"wan","status":"ok"},{"subsystem":"www","status":"error",...}]}. */
    fun lerSaudeRoteador(texto: String): Roteador? = try {
        val dados = JSONObject(texto).optJSONArray("data")
        var wan: String? = null; var www: String? = null; var lat: Int? = null
        if (dados != null) for (i in 0 until dados.length()) {
            val s = dados.optJSONObject(i) ?: continue
            val st = s.optString("status").lowercase().takeIf { it in setOf("ok", "error", "warning") }
            when (s.optString("subsystem")) {
                "wan" -> wan = st
                "www" -> { www = st; if (s.has("latency") && !s.isNull("latency")) lat = s.optInt("latency").takeIf { it in 0..60_000 } }
            }
        }
        if (wan == null && www == null) null else Roteador(wan, www, lat)
    } catch (e: Exception) { null }

    // ---------------------------------------------------------------- textos de tempo

    private val HM = DateTimeFormatter.ofPattern("HH:mm")
    private fun hm(t: Long, zona: ZoneId) = Instant.ofEpochMilli(t).atZone(zona).format(HM)

    private fun mesmoDia(a: Long, b: Long, zona: ZoneId) =
        Instant.ofEpochMilli(a).atZone(zona).toLocalDate() == Instant.ofEpochMilli(b).atZone(zona).toLocalDate()

    /** "às 18:12" (hoje), "ontem às 18:12" ou "em 08/11 às 18:12". */
    fun quando(t: Long, agora: Long, zona: ZoneId, tx: Textos = PT): String = momento(t, agora, zona, tx, "quando")

    /** "desde as 18:12", "desde ontem às 18:12". */
    private fun desde(t: Long, agora: Long, zona: ZoneId, tx: Textos) = momento(t, agora, zona, tx, "desde")

    private fun momento(t: Long, agora: Long, zona: ZoneId, tx: Textos, tipo: String): String {
        val d = Instant.ofEpochMilli(t).atZone(zona)
        val hoje = Instant.ofEpochMilli(agora).atZone(zona).toLocalDate()
        val hm = d.format(HM)
        return when (d.toLocalDate()) {
            hoje -> tx.t("${tipo}_hoje", hm)
            hoje.minusDays(1) -> tx.t("${tipo}_ontem", hm)
            else -> tx.t("${tipo}_data", d.format(DateTimeFormatter.ofPattern(tx.t("data_dm"))), hm)
        }
    }

    /** "menos de 1 min", "6 min", "2 h", "1 h 20 min". */
    fun duracao(ms: Long, tx: Textos = PT): String {
        val min = ms / 60_000
        return when {
            min < 1 -> tx.t("dur_menos_1")
            min < 60 -> tx.t("dur_min", min.toInt())
            min % 60 == 0L -> tx.t("dur_h", (min / 60).toInt())
            else -> tx.t("dur_h_min", (min / 60).toInt(), (min % 60).toInt())
        }
    }
}
