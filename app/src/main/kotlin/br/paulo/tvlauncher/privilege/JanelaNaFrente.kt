package br.paulo.tvlauncher.privilege

import br.paulo.tvlauncher.MainActivity

/**
 * Qual app está na frente agora, pelo que o serviço de acessibilidade vê (cada janela que abre).
 *
 * Até 12/11/2026 isto morava no ShizukuSobDemanda, que usava o mesmo sinal para subir o servidor do Shizuku embutido
 * quando um app cliente abria. O servidor saiu; o sinal ficou, porque o MCP (estado_tv), as rotinas, a Casa e a saúde
 * do acesso o usam.
 */
object JanelaNaFrente {
    @Volatile var pacote: String? = null
        private set

    /** A classe da tela da frente (só quando o sistema diz; ex.: "br.paulo.tvlauncher.MainActivity"). */
    @Volatile var classe: String? = null
        private set

    /** Chamado pelo serviço de acessibilidade a cada tela que abre. */
    fun apareceu(pacote: CharSequence?, classe: CharSequence? = null) {
        val p = pacote?.toString() ?: return
        if (p == "android" || p == "com.android.systemui") return   // diálogos e barras: não mudam quem está na frente
        // O nosso protetor de tela (sonho) por cima de um jogo não muda quem está "na frente": sem isto o modo jogo
        // (JogosTv) se perdia quando o protetor abria e fechava. O evento do sonho vem com a classe da raiz dele
        // (FrameLayout), não com ProtetorTela (Sala, 28/11/2026: a conferência de telas viu "na frente FrameLayout"):
        // por isso também vale "evento nosso, que não é uma tela nossa, com o protetor aberto". Outro app, ou uma tela
        // nossa (o despertador tocando), abrindo por cima dele ainda conta.
        val c = classe?.toString() ?: ""
        if (c.contains("ProtetorTela") || c.contains("Dream")) return
        if (br.paulo.tvlauncher.ProtetorTela.aberto() && p == br.paulo.tvlauncher.ProtetorTela.pacote() &&
            !c.startsWith(br.paulo.tvlauncher.AppIdentity.NAMESPACE + ".")) return
        this.pacote = p
        this.classe = classe?.toString()
        val agora = System.currentTimeMillis()
        vistoEm[p] = agora
        if (c.isNotEmpty()) vistoEm[c] = agora
    }

    /**
     * Quando cada pacote e cada tela apareceu por último (Sofy, 25/11/2026). Para conferir "abri X e ele apareceu":
     * vale mesmo se a pessoa já saiu dele (comparar só com [pacote] acusaria quem volta rápido).
     */
    val vistoEm = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** A NOSSA HOME está na frente (teclas coloridas e números remapeados só valem aqui, ver RemapTeclas). */
    fun homeNaFrente(ctx: android.content.Context): Boolean =
        pacote == ctx.packageName && classe == MainActivity::class.java.name
}
