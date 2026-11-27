package br.paulo.tvlauncher.network

import android.content.Context
import br.paulo.tvlauncher.resources.R
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import br.paulo.tvlauncher.privilege.PrivilegioDO
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * A parte que MEDE do "a internet caiu? em uma frase" (10/11/2026). A frase em si sai de [InternetEmUmaFrase]
 * (lógica pura, com teste). Só lê e pergunta: nada de DNS, VPN ou rede é alterado.
 *
 * As medições rodam ao mesmo tempo (cada uma espera a rede, não a CPU) e dividem UM prazo só ([LIMITE_MS]): a resposta
 * sai em até ~7 s mesmo com tudo fora do ar (o que não chegou a tempo conta como "falhou"). Chamar FORA da thread da tela.
 */
object DiagnosticoInternet {
    private const val LIMITE_MS = 7000L

    fun medir(ctx: Context): InternetEmUmaFrase.Medicoes {
        val app = ctx.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val redes = cm.allNetworks.mapNotNull { n -> cm.getNetworkCapabilities(n)?.let { n to it } }
        val casa = redes.firstOrNull { (_, c) -> c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) }
        val vpnDePe = redes.any { (_, c) -> c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
        @Suppress("DEPRECATION")
        val dbm = (app.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).connectionInfo?.rssi ?: -127
        val dnsFixo = Dns.atual(app).let { if (it.modo == Dns.Modo.HOST) it.host?.let { h -> Dns.nomeDe(app, h) ?: h } else null }

        val exec = Executors.newFixedThreadPool(4)
        try {
            val tcp = exec.submit(Callable { tcpMs() })
            // DNS e página de teste pela rede ATIVA: é o caminho dos apps (com a VPN ligada, passa por ela).
            val dns = exec.submit(Callable { cm.activeNetwork?.let { Dns.resolverSemCache(it) } })
            val http = exec.submit(Callable { http() })
            val roteador = exec.submit(Callable { roteador(app) })
            val hist = try { HistoricoRede.amostras(app, 24) } catch (_: Exception) { emptyList() }
            // Prazo único: cada get() espera só o que SOBRA até o fim (antes cada um esperava 7 s de novo, em fila).
            val fim = SystemClock.elapsedRealtime() + LIMITE_MS
            fun <T> pegar(f: java.util.concurrent.Future<T>, padrao: T): T = try {
                f.get(maxOf(0L, fim - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS)
            } catch (_: Exception) { padrao }
            val (cod, ms) = pegar(http, null to null)
            val (ro, mudo) = pegar(roteador, null to false)
            return InternetEmUmaFrase.Medicoes(
                agora = System.currentTimeMillis(),
                conectada = casa != null,
                dbm = if (dbm in -126..-1) dbm else 0,
                tcpMs = pegar(tcp, null),
                dnsMs = pegar(dns, null),
                dnsNome = dnsFixo,
                httpCodigo = cod, httpMs = ms,
                vpnFixada = try { PrivilegioDO.vpnFixada(app) != null } catch (_: Exception) { false },
                vpnDePe = vpnDePe,
                roteador = ro, roteadorMudo = mudo,
                historico = hist,
            )
        } finally { exec.shutdownNow() }
    }

    /** Os textos da frase no idioma da TV (strings ief_*, mesmas chaves de InternetEmUmaFrase.PT_TEXTOS). */
    private val TEXTOS = mapOf(
        "sem_wifi" to R.string.ief_sem_wifi,
        "roteador_sem_modem" to R.string.ief_roteador_sem_modem,
        "roteador_ve_fora" to R.string.ief_roteador_ve_fora,
        "ha" to R.string.ief_ha,
        "provedor_caiu" to R.string.ief_provedor_caiu,
        "vpn_travada" to R.string.ief_vpn_travada,
        "entre_tv_roteador" to R.string.ief_entre_tv_roteador,
        "sinal_fraco_entre" to R.string.ief_sinal_fraco_entre,
        "ponto" to R.string.ief_ponto,
        "roteador_travado" to R.string.ief_roteador_travado,
        "sem_internet" to R.string.ief_sem_internet,
        "vpn_culpada" to R.string.ief_vpn_culpada,
        "dns" to R.string.ief_dns,
        "entre_parenteses" to R.string.ief_entre_parenteses,
        "bloqueio" to R.string.ief_bloqueio,
        "nao_abriu" to R.string.ief_nao_abriu,
        "codigo_diferente" to R.string.ief_codigo_diferente,
        "vpn_desligada" to R.string.ief_vpn_desligada,
        "lenta" to R.string.ief_lenta,
        "lenta_sinal" to R.string.ief_lenta_sinal,
        "lenta_tcp" to R.string.ief_lenta_tcp,
        "lenta_roteador_igual" to R.string.ief_lenta_roteador_igual,
        "lenta_http" to R.string.ief_lenta_http,
        "queda_recente" to R.string.ief_queda_recente,
        "tudo_ok" to R.string.ief_tudo_ok,
        "sem_quedas_24h" to R.string.ief_sem_quedas_24h,
        "sem_quedas_parcial" to R.string.ief_sem_quedas_parcial,
        "roteador_avisa" to R.string.ief_roteador_avisa,
        "caiu_dns" to R.string.ief_caiu_dns,
        "caiu_internet" to R.string.ief_caiu_internet,
        "queda_sem_medir" to R.string.ief_queda_sem_medir,
        "quedas_n" to R.string.ief_quedas_n,
        "queda_voltou" to R.string.ief_queda_voltou,
        "quedas_total" to R.string.ief_quedas_total,
        "quando_hoje" to R.string.ief_quando_hoje,
        "quando_ontem" to R.string.ief_quando_ontem,
        "quando_data" to R.string.ief_quando_data,
        "desde_hoje" to R.string.ief_desde_hoje,
        "desde_ontem" to R.string.ief_desde_ontem,
        "desde_data" to R.string.ief_desde_data,
        "dur_menos_1" to R.string.ief_dur_menos_1,
        "dur_min" to R.string.ief_dur_min,
        "dur_h" to R.string.ief_dur_h,
        "dur_h_min" to R.string.ief_dur_h_min,
        "data_dm" to R.string.ief_data_dm,
        "s_wifi_desconectado" to R.string.ief_s_wifi_desconectado,
        "s_wifi_dbm" to R.string.ief_s_wifi_dbm,
        "s_rede_conectada" to R.string.ief_s_rede_conectada,
        "s_internet" to R.string.ief_s_internet,
        "s_dns" to R.string.ief_s_dns,
        "s_ms" to R.string.ief_s_ms,
        "s_falhou" to R.string.ief_s_falhou,
        "s_pagina" to R.string.ief_s_pagina,
        "s_ok" to R.string.ief_s_ok,
        "s_ok_em" to R.string.ief_s_ok_em,
        "s_codigo" to R.string.ief_s_codigo,
        "s_vpn_ligada" to R.string.ief_s_vpn_ligada,
        "s_vpn_desligada" to R.string.ief_s_vpn_desligada,
        "s_roteador_fora" to R.string.ief_s_roteador_fora,
        "s_roteador_ok" to R.string.ief_s_roteador_ok,
        "s_roteador_sem_opiniao" to R.string.ief_s_roteador_sem_opiniao,
        "s_roteador_mudo" to R.string.ief_s_roteador_mudo,
        "s_roteador_nao_consultado" to R.string.ief_s_roteador_nao_consultado,
        "s_historico" to R.string.ief_s_historico,
    )

    fun textos(ctx: Context) = InternetEmUmaFrase.Textos { chave, a -> ctx.getString(TEXTOS.getValue(chave), *a) }

    /** Mede e explica, no idioma da TV (tela Diagnóstico). */
    fun explicar(ctx: Context): InternetEmUmaFrase.Resultado =
        InternetEmUmaFrase.explicar(medir(ctx), tx = textos(ctx.applicationContext))

    /** Para a ferramenta MCP diagnostico_rede: {frase, causa, nivel, sinais}, em português (o que a IA lê). */
    fun json(ctx: Context): JSONObject = InternetEmUmaFrase.explicar(medir(ctx)).json()

    /** Conexão até 1.1.1.1:443: prova que a internet chega SEM depender de DNS. ms ou null. */
    fun tcpMs(): Int? {   // público: o teste de rede do card Jogos (JogosTv) também usa
        val ini = SystemClock.elapsedRealtime()
        return try { Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 3000) }; (SystemClock.elapsedRealtime() - ini).toInt() }
        catch (_: Exception) { null }
    }

    /** A página de teste do Google (responde 204, vazia). (código, ms) ou (null, null) se não conectou. */
    private fun http(): Pair<Int?, Long?> {
        val ini = SystemClock.elapsedRealtime()
        return try {
            val c = URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection
            c.connectTimeout = 4000; c.readTimeout = 4000; c.useCaches = false; c.instanceFollowRedirects = false
            try { c.responseCode to (SystemClock.elapsedRealtime() - ini) } finally { c.disconnect() }
        } catch (_: Exception) { null to null }
    }

    /**
     * O que o roteador UniFi acha (só se a integração estiver pronta). (opinião, mudo): mudo = não respondeu na rede
     * de casa (sinal de roteador travado). Recusa de chave, API ausente etc. = "não consultado", não "mudo".
     */
    private fun roteador(app: Context): Pair<InternetEmUmaFrase.Roteador?, Boolean> {
        if (!UnifiArmazem.pronto(app)) return null to false
        return try { InternetEmUmaFrase.lerSaudeRoteador(UnifiCliente.saude(app)) to false }
        catch (e: UnifiCliente.Falha) { null to InternetEmUmaFrase.roteadorMudo(e.message) }
        catch (_: Exception) { null to false }
    }
}
