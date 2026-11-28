package br.paulo.tvlauncher.network

import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.alarm.HoraDormirTv

/**
 * A conta AdGuard DNS do dono, do lado da TV (27/11/2026). Liga as peças que já existiam:
 * - DNS privado ([Dns]) com a rede de segurança ([DnsSeguro]): se o servidor novo não resolver nomes em ~27 s, volta ao
 *   que estava antes, sozinho.
 * - Diagnóstico: o resumo das últimas 24 h ([resumo]).
 * A lógica (pedidos, horário, a trava "só o servidor da TV") está em [AdGuardDns], testada no PC.
 * Tudo aqui faz rede: chamar FORA da thread da tela.
 */
object AdGuardTv {
    private const val TAG = "TvLauncher.AdGuard"
    private const val CACHE_RESUMO_MS = 5 * 60_000L
    @Volatile private var cacheResumo: Pair<Long, AdGuardDns.Resumo>? = null

    private fun api(ctx: Context): AdGuardDns.Api =
        AdGuardDns.Api(AdGuardStore.chave(ctx) ?: throw AdGuardDns.Falha(AdGuardDns.Erro.CHAVE_RECUSADA))

    private fun nomeTv(ctx: Context): String =
        android.provider.Settings.Global.getString(ctx.contentResolver, "device_name")?.trim()?.ifEmpty { null } ?: "TV"

    /** Só lê a conta (prova que a chave vale): quantos servidores e aparelhos ela tem. */
    fun conferirChave(ctx: Context): Pair<Int, Int> { val a = api(ctx); return a.servidores().size to a.aparelhos().size }

    /**
     * Cria (ou reencontra) o servidor e o aparelho da TV na conta e liga o DNS privado nele. Devolve false se o DNS
     * novo não resolveu nomes e a TV voltou ao DNS de antes (a ligação na conta fica feita).
     */
    fun ligar(ctx: Context): Boolean {
        val l = AdGuardDns.ligar(api(ctx), nomeTv(ctx), AdGuardStore.servidorId(ctx), AdGuardStore.aparelhoId(ctx))
        AdGuardStore.guardarLigacao(ctx, l)
        val antes = Dns.atual(ctx)
        AdGuardStore.guardarDnsAnterior(ctx, antes)
        val novo = Dns.Estado(Dns.Modo.HOST, l.hostDot)
        if (!Dns.aplicar(ctx, Dns.Modo.HOST, l.hostDot)) throw AdGuardDns.Falha(AdGuardDns.Erro.OUTRO)
        Dns.salvar(ctx, Dns.Servidor(ctx.getString(br.paulo.tvlauncher.resources.R.string.dns_adguard_pessoal), l.hostDot))
        val voltou = DnsSeguro.conferirDepoisDeTrocar(ctx, antes, novo)
        Log.i(TAG, "ligada à conta; DNS privado ${if (voltou) "não resolveu, voltei" else "ok"}")
        sincronizarFamilia(ctx, forcar = true)
        return !voltou
    }

    /** Desliga na TV: volta o DNS de antes e apaga a chave. A conta AdGuard não é alterada. */
    fun desligar(ctx: Context) {
        val host = AdGuardStore.hostDot(ctx)
        val antes = AdGuardStore.dnsAnterior(ctx) ?: Dns.Estado(Dns.Modo.AUTOMATICO, null)
        if (host != null && Dns.atual(ctx).let { it.modo == Dns.Modo.HOST && it.host.equals(host, true) }) Dns.aplicar(ctx, antes.modo, antes.host)
        AdGuardStore.esquecer(ctx)
        cacheResumo = null
    }

    /**
     * As regras da Família de agora, no formato que vai para o DNS. SEM horário (28/11/2026, teste na Sala): o
     * screen_time_schedule do AdGuard não corta a internet, só diz QUANDO as regras dos pais valem, e é lido em UTC.
     * Mandar a hora de dormir ali desligava o controle dos pais durante o dia. A hora de dormir fica só na TV
     * ([HoraDormirTv]); no DNS as regras valem o dia todo.
     */
    fun regras(ctx: Context): AdGuardDns.RegrasFamilia =
        AdGuardDns.RegrasFamilia(AdGuardStore.familiaLigada(ctx), emptyList(), AdGuardStore.servicos(ctx),
            AdGuardStore.youtubeSeguro(ctx), AdGuardStore.buscaSegura(ctx), AdGuardStore.bloquearAdulto(ctx))

    private fun assinatura(r: AdGuardDns.RegrasFamilia) =
        "${r.ligado}|${r.janelasDormir}|${r.servicosBloqueados.sorted()}|${r.youtubeSeguro}|${r.buscaSegura}|${r.bloquearAdulto}"

    /**
     * Grava as regras da Família no servidor da TV, só se mudaram desde a última vez (ou [forcar]). Quem chama: o painel
     * ao salvar, a ligação e a rodada diária. Erro fica guardado para o painel mostrar; nunca derruba quem chamou.
     */
    fun sincronizarFamilia(ctx: Context, forcar: Boolean = false): AdGuardDns.Erro? {
        if (!AdGuardStore.ligada(ctx)) return null
        val r = regras(ctx)
        val a = assinatura(r)
        if (!forcar && a == AdGuardStore.assinatura(ctx)) return null
        return try {
            AdGuardDns.aplicarFamilia(api(ctx), AdGuardStore.servidorId(ctx)!!, r)
            AdGuardStore.guardarAssinatura(ctx, a); AdGuardStore.guardarSincronia(ctx, "ok"); null
        } catch (e: AdGuardDns.Falha) {
            AdGuardStore.guardarSincronia(ctx, e.erro.name); Log.w(TAG, "família não gravada: ${e.erro}"); e.erro
        }
    }

    /** Consultas e bloqueios das últimas 24 h no servidor da TV (5 min de cache). Null se não está ligada. */
    fun resumo(ctx: Context, forcar: Boolean = false): AdGuardDns.Resumo? {
        if (!AdGuardStore.ligada(ctx)) return null
        val c = cacheResumo
        if (!forcar && c != null && System.currentTimeMillis() - c.first < CACHE_RESUMO_MS) return c.second
        val r = api(ctx).resumo24h(AdGuardStore.servidorId(ctx)!!)
        cacheResumo = System.currentTimeMillis() to r
        return r
    }

    fun servicos(ctx: Context): List<AdGuardDns.Servico> = api(ctx).servicos()
}
