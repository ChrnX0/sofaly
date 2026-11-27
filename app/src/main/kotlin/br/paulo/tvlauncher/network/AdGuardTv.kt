package br.paulo.tvlauncher.network

import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.alarm.HoraDormirTv
import br.paulo.tvlauncher.settings.Extras

/**
 * A conta AdGuard DNS do dono, do lado da TV (27/11/2026). Liga as peças que já existiam:
 * - DNS privado ([Dns]) com a rede de segurança ([DnsSeguro]): se o servidor novo não resolver nomes em ~27 s, volta ao
 *   que estava antes, sozinho.
 * - Hora de dormir da Família ([HoraDormirTv]): o mesmo horário (por dia) vai para o DNS da TV.
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

    /** As regras da Família de agora, no formato que vai para o DNS. */
    fun regras(ctx: Context): AdGuardDns.RegrasFamilia {
        val dormir = AdGuardStore.dormirNoDns(ctx) && Extras.ligado(ctx, Extras.Chave.HORA_DORMIR)
        val padrao = br.paulo.tvlauncher.alarm.HoraDormir.Janela(HoraDormirTv.inicio(ctx), HoraDormirTv.fim(ctx))
        val dias = HoraDormirTv.dias(ctx)
        val janelas = if (!dormir) emptyList() else (0 until 7).map { d ->
            br.paulo.tvlauncher.alarm.HoraDormir.doDia(d, dias, padrao).let { it.inicio to it.fim }
        }
        return AdGuardDns.RegrasFamilia(AdGuardStore.familiaLigada(ctx), janelas, AdGuardStore.servicos(ctx),
            AdGuardStore.youtubeSeguro(ctx), AdGuardStore.buscaSegura(ctx), AdGuardStore.bloquearAdulto(ctx))
    }

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

    // ---------------------------------------------------------------- vigia da hora de dormir

    /** Chaves das Extras que mudam o que vai para o DNS: a chave da hora de dormir e os horários. */
    private val CHAVES_FAMILIA = setOf(Extras.Chave.HORA_DORMIR.name, br.paulo.tvlauncher.alarm.HoraDormir.CHAVE_INICIO,
        br.paulo.tvlauncher.alarm.HoraDormir.CHAVE_FIM, br.paulo.tvlauncher.alarm.HoraDormir.CHAVE_DIAS)
    private val principal by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    @Volatile private var ouvinte: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null   // referência forte

    /**
     * Mudou a hora de dormir (no painel ou na TV)? Espera 5 s (a pessoa costuma mexer em vários campos) e grava no DNS.
     * Também confere uma vez 1 min depois de abrir (pega mudanças feitas com a TV sem internet).
     */
    fun vigiarFamilia(ctx: Context) {
        val app = ctx.applicationContext
        if (ouvinte != null) return
        val gravar = Runnable { br.paulo.tvlauncher.Fundo.rodar("adguard") { sincronizarFamilia(app) } }
        ouvinte = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, chave ->
            if (chave in CHAVES_FAMILIA && AdGuardStore.ligada(app)) { principal.removeCallbacks(gravar); principal.postDelayed(gravar, 5_000) }
        }.also { Extras.ouvir(app, it) }
        principal.postDelayed(gravar, 60_000)
    }
}
