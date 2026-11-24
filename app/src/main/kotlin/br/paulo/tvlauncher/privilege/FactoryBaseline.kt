package br.paulo.tvlauncher.privilege

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import br.paulo.tvlauncher.AppIdentity
import br.paulo.tvlauncher.family.Diario
import br.paulo.tvlauncher.home.IconPackFreezer
import br.paulo.tvlauncher.network.Dns
import br.paulo.tvlauncher.player.OnDemandTv

/**
 * "Como estava antes da Sofaly" (22/11/2026, estudo do Tellyscope, proposta C).
 *
 * Porquê: com a limpeza para qualquer TV ([DebloatCatalog]) a Sofaly passa a ocultar apps em TVs que nunca vimos, e um
 * app pode ser ocultado por vários caminhos (limpeza, menu do app, painel, Observador). O "desfazer" volta um passo por
 * vez e esquece depois de alguns dias.
 * Para quê: um lugar que diz o que a Sofaly mudou nesta TV e volta tudo de uma vez, com um "desfazer a restauração".
 * Como: antes da primeira mudança ([garantirFoto], chamado pelo [PrivilegioDO.ocultar] e pelas Configurações) tira a
 * foto do que já estava oculto/desligado e do DNS privado. [restaurar] mostra de volta só o que foi ocultado depois
 * (o que o fabricante desligou continua desligado) e devolve o DNS; guarda o que fez para [desfazerRestauracao].
 * Não mexe em: apps "abre só quando chamado" ([OnDemandTv]) e pacotes de ícones congelados ([IconPackFreezer]), que
 * ficam congelados por função; o próprio launcher; o protetor de tela e o "dormir", que a Sofaly mantém porque é a HOME.
 * Liga com: [Debloat] (a lista "ocultei" dele é tirada da foto numa TV já mexida), [Dns], o Diário, o painel e o adb.
 */
object FactoryBaseline {
    private const val TAG = "TvLauncher.Baseline"
    const val PREFS = "factory_baseline"   // fora do backup (BackupCompleto.FORA_DE_PROPOSITO): é desta TV
    private const val FOTO = "foto"
    private const val RESTAURACAO = "antes_de_restaurar"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun foto(ctx: Context): FactoryBaselineRules.Snapshot? = FactoryBaselineRules.deJson(prefs(ctx).getString(FOTO, null))

    /** Apps deste usuário ocultos (Device Owner) ou desligados (pm disable-user / fabricante). Lento: fora da tela. */
    fun ocultosAgora(ctx: Context): Set<String> {
        @Suppress("DEPRECATION")
        val todos = runCatching {
            ctx.packageManager.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
        }.getOrDefault(emptyList())
        return todos.filter { (it.flags and ApplicationInfo.FLAG_INSTALLED) != 0 }
            .filter { !it.enabled || PrivilegioDO.estaOculto(ctx, it.packageName) }
            .map { it.packageName }.toSet()
    }

    private fun dnsAgora(ctx: Context): Pair<String?, String?> = runCatching {
        Settings.Global.getString(ctx.contentResolver, "private_dns_mode") to Settings.Global.getString(ctx.contentResolver, "private_dns_specifier")
    }.getOrDefault(null to null)

    /** Tira a foto uma vez (antes da 1ª mudança). Devolve a foto existente ou a nova. */
    @Synchronized fun garantirFoto(ctx: Context): FactoryBaselineRules.Snapshot {
        foto(ctx)?.let { return it }
        val (m, h) = dnsAgora(ctx)
        val f = FactoryBaselineRules.foto(System.currentTimeMillis(), ocultosAgora(ctx), Debloat.ocultosPorAqui(ctx), m, h)
        prefs(ctx).edit().putString(FOTO, FactoryBaselineRules.paraJson(f)).commit()
        Log.i(TAG, "foto tirada: ${f.ocultos.size} já ocultos, dns=$m")
        return f
    }

    /** Pacotes que outra função mantém congelados de propósito, com o motivo. */
    fun gerenciados(ctx: Context): Map<String, String> = buildMap {
        runCatching { OnDemandTv.entries(ctx).forEach { put(it.pkg, "on_demand") } }
        runCatching { IconPackFreezer.congelados(ctx).forEach { put(it.first, "icon_pack") } }
    }

    private fun nunca(ctx: Context) = AppIdentity.PACKAGES + ctx.packageName

    /** O que voltaria se restaurasse agora. Null = ainda não há foto (a Sofaly não mexeu em nada). Lento: fora da tela. */
    fun plano(ctx: Context): FactoryBaselineRules.Plano? {
        val f = foto(ctx) ?: return null
        val (m, h) = dnsAgora(ctx)
        return FactoryBaselineRules.plano(f, ocultosAgora(ctx), m, h, gerenciados(ctx), nunca(ctx))
    }

    data class Resultado(val voltaram: Int, val falharam: Int, val dns: Boolean)

    /** Volta tudo ao da foto. Bloqueia (sem Device Owner vai pelo shell): nunca na thread da tela. */
    @Synchronized fun restaurar(ctx: Context, origem: String = "tv"): Resultado? {
        val f = foto(ctx) ?: return null
        val p = plano(ctx) ?: return null
        if (p.vazio) return Resultado(0, 0, false)
        val (m, h) = dnsAgora(ctx)
        // Guarda o que vai mudar ANTES de mudar: é o que o "desfazer a restauração" usa.
        prefs(ctx).edit().putString(RESTAURACAO, FactoryBaselineRules.paraJson(
            FactoryBaselineRules.Snapshot(System.currentTimeMillis(), p.mostrar.toSet(), m, h))).commit()
        val daLimpeza = Debloat.ocultosPorAqui(ctx)
        var ok = 0
        for (pkg in p.mostrar) {
            // O que a limpeza ocultou volta pelo Debloat (mantém a lista "ocultei" dele certa); o resto, direto.
            if (pkg in daLimpeza) Debloat.definir(ctx, pkg, false) else PrivilegioDO.ocultar(ctx, pkg, false)
            if (!PrivilegioDO.estaOculto(ctx, pkg) && !Congelar.congelado(ctx, pkg)) ok++
        }
        var dns = false
        if (p.dnsMudou) dns = aplicarDns(ctx, f.dnsModo, f.dnsHost)
        val r = Resultado(ok, p.mostrar.size - ok, dns)
        runCatching { Diario.anotar(ctx, origem, "Restaurou como estava antes da Sofaly", "apps=${r.voltaram}/${p.mostrar.size} dns=${r.dns}") }
        return r
    }

    fun podeDesfazerRestauracao(ctx: Context) = prefs(ctx).contains(RESTAURACAO)

    /** Oculta de novo o que a restauração mostrou e devolve o DNS de antes dela. Uma vez só. */
    @Synchronized fun desfazerRestauracao(ctx: Context, origem: String = "tv"): Resultado? {
        val antes = FactoryBaselineRules.deJson(prefs(ctx).getString(RESTAURACAO, null)) ?: return null
        var ok = 0
        for (pkg in antes.ocultos.sorted()) {
            PrivilegioDO.ocultar(ctx, pkg, true)
            if (PrivilegioDO.estaOculto(ctx, pkg)) ok++
        }
        val dns = aplicarDns(ctx, antes.dnsModo, antes.dnsHost)
        prefs(ctx).edit().remove(RESTAURACAO).commit()
        val r = Resultado(ok, antes.ocultos.size - ok, dns)
        runCatching { Diario.anotar(ctx, origem, "Desfez a restauração", "apps=${r.voltaram}/${antes.ocultos.size} dns=${r.dns}") }
        return r
    }

    private fun aplicarDns(ctx: Context, modo: String?, host: String?): Boolean = when (FactoryBaselineRules.modoNormal(modo)) {
        "off" -> Dns.aplicar(ctx, Dns.Modo.DESLIGADO)
        "hostname" -> !host.isNullOrBlank() && Dns.aplicar(ctx, Dns.Modo.HOST, host)
        else -> Dns.aplicar(ctx, Dns.Modo.AUTOMATICO)
    }

    /** Para o adb e o MCP. */
    fun relatorio(ctx: Context): String {
        val f = foto(ctx) ?: return "sem foto (a Sofaly ainda não mexeu em apps nesta TV)"
        val p = plano(ctx)
        return "foto=${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.util.Date(f.quando))} " +
            "ja_ocultos=${f.ocultos.size} voltariam=${p?.mostrar} mantidos=${p?.mantidos} dns_mudou=${p?.dnsMudou} " +
            "pode_desfazer=${podeDesfazerRestauracao(ctx)}"
    }
}
