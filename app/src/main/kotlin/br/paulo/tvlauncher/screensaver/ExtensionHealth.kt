package br.paulo.tvlauncher.screensaver

import android.content.Context

/**
 * Guarda de saúde das extensões de terceiros (24/11/2026, proposta B). Porquê: um plugin lento ou quebrado não pode
 * deixar a tela inicial travando nem gastando bateria/rede de quem não sabe o que é um plugin. Para quê: a pessoa nunca
 * precisa descobrir sozinha qual app está atrapalhando. Como: cada pedido a um plugin (fundo ou cartão) conta como
 * certo ou falho (sem resposta no prazo, erro, serviço que não liga); [LIMITE_FALHAS] falhas SEGUIDAS dentro de
 * [JANELA_MS] desligam o plugin (tira o OK do dono) e deixam o motivo guardado, que a tela de Plugins (TV e painel) e
 * o Diário mostram. Uma resposta certa zera a conta. Para ligar de novo basta o dono dar o OK outra vez.
 *
 * Liga com: [WallpaperPlugins.buscar], [br.paulo.tvlauncher.PluginsTv.cartoes], PluginsActivity e PanelPlugins (o
 * aviso), e a loja aberta (proposta C) usa o mesmo placar para decidir o que sobe ou sai da vitrine.
 */
object ExtensionHealth {
    const val LIMITE_FALHAS = 5
    const val JANELA_MS = 6 * 3_600_000L
    /** Resposta acima disto conta como falha mesmo tendo chegado (a HOME esperou demais). */
    const val LENTO_MS = 2_500L

    /** O placar de um plugin: falhas seguidas e quando a primeira delas aconteceu. */
    data class Placar(val falhas: Int = 0, val desde: Long = 0L)

    /** Placar novo depois de um pedido. Pura. */
    fun contar(p: Placar, ok: Boolean, ms: Long, agora: Long): Placar = when {
        ok && ms <= LENTO_MS -> Placar()
        p.falhas == 0 || agora - p.desde > JANELA_MS -> Placar(1, agora)
        else -> Placar(p.falhas + 1, p.desde)
    }

    fun desligar(p: Placar): Boolean = p.falhas >= LIMITE_FALHAS

    // ---------------------------------------------------------------- Android

    private const val PREFS = "extension_health"
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Quando a guarda desligou [pacote] (0 = não desligou ou o dono já ligou de novo). */
    fun desligadoEm(ctx: Context, pacote: String): Long = prefs(ctx).getLong("off:$pacote", 0L)

    // ---- Bloqueio pela Sofaly (24/11/2026, proposta C): a vitrine do servidor pode listar pacotes com problema conhecido
    // (`blocked`). Só DESLIGA: o servidor nunca liga nada. Enquanto o pacote estiver na lista, ligar é recusado.

    fun bloqueado(ctx: Context, pacote: String): Boolean = pacote in (prefs(ctx).getStringSet("blocked", null) ?: emptySet())

    /** Guarda a lista nova; devolve os pacotes que ENTRARAM nela agora (quem chamou os desliga). */
    @Synchronized
    fun definirBloqueados(ctx: Context, pacotes: Set<String>): Set<String> {
        val antes = prefs(ctx).getStringSet("blocked", null) ?: emptySet()
        prefs(ctx).edit().putStringSet("blocked", HashSet(pacotes)).apply()
        return pacotes - antes
    }

    /** O dono ligou de novo: zera placar e aviso. */
    fun perdoar(ctx: Context, pacote: String) {
        prefs(ctx).edit().remove("n:$pacote").remove("d:$pacote").remove("off:$pacote").apply()
    }

    /**
     * Conta um pedido a [pacote]. Devolve true quando este pedido fez a guarda desligar o plugin: quem chamou tira o
     * OK (cada tipo tem o seu jeito) e a HOME volta ao normal.
     */
    @Synchronized
    fun registrar(ctx: Context, pacote: String, ok: Boolean, ms: Long, agora: Long = System.currentTimeMillis()): Boolean {
        val pr = prefs(ctx)
        val novo = contar(Placar(pr.getInt("n:$pacote", 0), pr.getLong("d:$pacote", 0L)), ok, ms, agora)
        val e = pr.edit().putInt("n:$pacote", novo.falhas).putLong("d:$pacote", novo.desde)
        val cai = desligar(novo)
        if (cai) e.putLong("off:$pacote", agora).putInt("n:$pacote", 0)
        e.apply()
        // A Sofy fica sabendo (Diário da TV, ferramenta diario_tv do MCP): só o evento, nunca o nome do app de terceiro.
        if (cai) runCatching { br.paulo.tvlauncher.panel.PainelArmazem.registrar(ctx, "sofy", "plugin desligado: parou de responder", "ok") }
        if (cai) br.paulo.tvlauncher.observer.SofyBusTv.emit(ctx, br.paulo.tvlauncher.observer.SofyBus.Source.PLUGIN, "disabled", ok = false)
        return cai
    }
}
