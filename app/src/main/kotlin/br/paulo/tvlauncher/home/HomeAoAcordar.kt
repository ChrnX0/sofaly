package br.paulo.tvlauncher.home

/**
 * #5 do teste 100% (23/11/2026, Sala): ao acordar, a TV mostrava "Escolha uma conta" (o seletor de conta do Google,
 * launcherx .profile.chooser, pedido de novo pelo app que estava aberto antes de dormir, ex.: YouTube) em vez da Sofaly.
 * Regra pura (HomeAoAcordarTest): esse seletor, aparecendo logo depois de a tela acender e SEM ninguém ter apertado
 * tecla desde então, vira a nossa HOME. Se a pessoa já mexeu no controle (abriu o YouTube ao ligar), o seletor fica:
 * é ela que pediu. Outros apps retomados ao acordar ficam como estão (um Cast acorda a TV e abre o app dele).
 */
object HomeAoAcordar {
    private const val LAUNCHERX = Home.LAUNCHERX
    /** Telas do seletor de conta do Google (dumpsys package launcherx, Sala, 24/11/2026). */
    val SELETORES = setOf(
        "$LAUNCHERX.profile.chooser.ProfileChooserActivity",
        "$LAUNCHERX.profile.managedprofile.delegate.ProxyProfileChooserActivity",
    )
    /** Até quanto tempo depois de acender a tela o seletor conta como "apareceu sozinho ao acordar". */
    const val JANELA_MS = 20_000L
    /** Tecla nos primeiros instantes é a que acordou a TV (liga/desliga), não um pedido de app. */
    const val FOLGA_TECLA_MS = 1_500L

    /**
     * @param agora, acordouEm, teclaEm relógio desde o boot; [acordouEm] = Long.MAX_VALUE com a tela apagada;
     * [teclaEm] = 0 se nenhuma tecla foi vista neste processo.
     */
    fun desviar(classe: String?, agora: Long, acordouEm: Long, teclaEm: Long): Boolean {
        if (classe !in SELETORES || acordouEm == Long.MAX_VALUE) return false
        if (agora - acordouEm !in 0..JANELA_MS) return false
        return teclaEm <= acordouEm + FOLGA_TECLA_MS
    }
}
