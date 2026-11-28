package br.paulo.tvlauncher.observer

/**
 * Enxugar o launcher do Google sem desligá-lo (22/11/2026, Paulo escolheu a opção A).
 *
 * Porquê: o launcherx precisa ficar LIGADO (voz/Gemini, busca, seletor de contas, painel de notificações; ver Config.kt),
 * mas o processo principal dele fica vivo em segundo plano só por ter sido a última HOME aberta. Medido na Sala em
 * 22/11: 32 MB de PSS, 0% de CPU, parado. O processo ":coreservices" (avisos e notificações, 26 MB) tem serviço em
 * primeiro plano e fica.
 * Para quê: devolver essa memória aos apps que a família usa, sem perder nada (ele volta sozinho quando é chamado).
 * Como: a cada batida do Observador (5 min), se o launcherx não está na frente há [AWAY_MS] e a última vez foi há mais
 * de [EVERY_MS], o shell roda `am kill` nele. `am kill` só encerra processos que o Android já considera descartáveis
 * (em cache); o ":coreservices", com serviço em primeiro plano, fica de pé.
 * Liga-se a: [ObserverTv] (batida e relatório), JanelaNaFrente (quem está na frente), GuardiaoShizuku (shell).
 */
object GoogleHomeTrim {
    const val PACKAGE = "com.google.android.apps.tv.launcherx"
    /** Longe da frente por 2 min: quem acabou de usar a voz pode chamar de novo logo em seguida. */
    const val AWAY_MS = 2 * 60_000L
    /** No máximo uma vez a cada 10 min: se ele volta sozinho (ex.: notificação), não fica num vaivém. */
    const val EVERY_MS = 10 * 60_000L

    /** O comando: só age se o processo principal existe (pidof sem ":" acha só ele) e diz o pid encerrado. */
    const val COMMAND = "p=\$(pidof $PACKAGE); if [ -n \"\$p\" ]; then am kill $PACKAGE && echo \"killed \$p\"; else echo none; fi"

    /**
     * Pode enxugar agora? [foreground] null = não se sabe quem está na frente (sem acessibilidade): não mexe.
     * [lastFrontAt] = a última vez que o launcherx esteve na frente (0 = nunca visto desde que o launcher subiu).
     */
    fun shouldTrim(foreground: String?, lastFrontAt: Long, lastTrimAt: Long, now: Long): Boolean =
        foreground != null && foreground != PACKAGE &&
            now - lastFrontAt >= AWAY_MS && now - lastTrimAt >= EVERY_MS

    /** A saída do [COMMAND]: o pid encerrado, ou null se não havia processo (ou a saída não é a esperada). */
    fun killedPid(out: String?): Int? =
        out?.lineSequence()?.map { it.trim() }?.firstOrNull { it.startsWith("killed ") }
            ?.removePrefix("killed ")?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.toIntOrNull()
}
