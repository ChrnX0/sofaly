package br.paulo.tvlauncher.settings

/**
 * A cor exclusiva de cada ícone (Paulo, 28/11/2026 02:58Z: "ícones com cor não é a cor de mundo, são cores exclusivas
 * para cada ícone"). Vale no tema [SystemLook.COZY] (o desenho do ícone sai nesta cor) e no azulejo de [SystemLook.TILES].
 * Regras: nunca rosa nem magenta (Paulo detesta rosa); nada perto do laranja da marca (laranja é só marca, foco e botão
 * principal); todas claras o bastante para ler sobre a caixa grafite. [PaletaIconesTest] confere as três.
 * Puro: sem Android, testável na JVM.
 */
object IconPalette {
    const val VERMELHO = 0xFFF0675E.toInt()
    const val AMBAR = 0xFFF2B33D.toInt()
    const val AMARELO = 0xFFEDD54C.toInt()
    const val LIMA = 0xFFB7D65A.toInt()
    const val VERDE = 0xFF6CC97A.toInt()
    const val MENTA = 0xFF4FCFA6.toInt()
    const val TURQUESA = 0xFF46C1C8.toInt()
    const val CEU = 0xFF5FB3EE.toInt()
    const val AZUL = 0xFF7392F2.toInt()
    const val INDIGO = 0xFF9488F0.toInt()
    const val VIOLETA = 0xFFB283EE.toInt()
    const val AREIA = 0xFFD8BB8E.toInt()
    const val OLIVA = 0xFFB2B86E.toInt()
    const val ACO = 0xFFA3B4C0.toInt()

    val TODAS = listOf(VERMELHO, AMBAR, AMARELO, LIMA, VERDE, MENTA, TURQUESA, CEU, AZUL, INDIGO, VIOLETA, AREIA, OLIVA, ACO)

    /** Os ícones desenhados (IconeCard, linhas, apps): o nome do tipo, como o IconeCard recebe. */
    private val POR_ICONE = mapOf(
        "wifi" to CEU, "bluetooth" to AZUL, "dns" to INDIGO, "sistema" to ACO, "tela" to TURQUESA, "tv" to VIOLETA,
        "audio" to AMARELO, "acessibilidade" to VERDE, "launcher" to LIMA, "protetor" to MENTA, "dormir" to INDIGO,
        "energia" to VERMELHO, "familia" to AREIA, "filtro" to TURQUESA, "chave" to AMBAR, "letra" to AREIA,
        "grade" to CEU, "modo" to VIOLETA, "buscar" to ACO, "camera" to AREIA, "casa" to LIMA, "cena" to VIOLETA,
        "cortina" to OLIVA, "despertador" to AMBAR, "lua" to INDIGO, "pausar" to ACO, "tocar" to VERDE, "proxima" to ACO,
        "fechar" to VERMELHO, "porta" to OLIVA, "rotina" to CEU, "termometro" to VERMELHO, "timer" to AMARELO,
        "tomada" to AMARELO, "ventilador" to TURQUESA, "ar" to MENTA, "lampada" to AMARELO, "visita" to VERDE,
        // ícones de linha (protetor, clima, Ilha)
        "horizonte" to AMBAR, "gota" to CEU, "chuva" to AZUL, "calendario" to VERMELHO, "recado" to AMARELO,
        "sol" to AMARELO, "folha" to VERDE, "fone" to VIOLETA, "pessoa" to AREIA, "gravar" to VERMELHO,
        // itens da tela inicial (IconeCruz.Tipo em minúsculas; tv, dormir, despertador e casa já estão acima)
        "continuar" to AMBAR, "assistir" to VIOLETA, "ouvir" to VERDE, "jogar" to VERMELHO, "aplicativos" to CEU,
        "series" to AZUL, "filmes" to VERMELHO, "rede" to TURQUESA, "addons" to LIMA, "radio" to AMARELO,
        "podcasts" to MENTA, "jogos" to VERDE, "sofa" to AREIA, "todos_apps" to CEU, "plugin" to OLIVA, "central" to ACO,
        "ferramentas" to ACO, "clima" to CEU, "ajuste" to TURQUESA, "config" to ACO, "painel" to VIOLETA, "ajuda" to VERDE,
        // Ouvir e Jogos (IconeOuvir.Tipo em minúsculas; buscar, dormir, pausar, continuar, radio, rede e sofa já estão acima)
        "favoritas" to VERMELHO, "recentes" to CEU, "perto" to VERDE, "pais" to AZUL, "generos" to VIOLETA, "em_alta" to AMBAR,
        "novos" to LIMA, "meus" to AREIA, "episodios" to INDIGO, "adicionar" to VERDE, "celular" to AZUL, "podcast" to MENTA,
        "tocando" to VERDE, "voltar" to CEU, "avancar" to TURQUESA, "velocidade" to AMARELO, "silencio" to INDIGO,
        "parar" to VERMELHO, "sair" to ACO, "controle" to VERMELHO, "turma" to VIOLETA, "pc" to AZUL, "opcoes" to ACO,
        // coluna do player (IconePlayer.Tipo e a lupa; recentes, grade e radio já estão acima)
        "estrela" to AMARELO, "filtros" to TURQUESA, "cadeado" to AREIA, "play" to VERDE, "visto" to AZUL, "todos" to ACO,
        "esporte" to LIMA, "oculto" to OLIVA, "gravacoes" to VERMELHO, "novo" to LIMA, "lupa" to ACO,
    )

    /** Os tipos com cor fixa (para o teste e para a foto da paleta). */
    val ICONES: Set<String> get() = POR_ICONE.keys

    /** A cor do ícone [tipo]; um tipo novo sem cor fixa ganha sempre a mesma da paleta, pelo nome. */
    fun cor(tipo: String): Int {
        val t = tipo.trim().lowercase()
        return POR_ICONE[t] ?: TODAS[Math.floorMod(t.hashCode(), TODAS.size)]
    }

    /**
     * Os ícones do painel web (sprite `#i-<nome>` do index.html), 28/11/2026: o tema vale também no painel. Na ordem da
     * coluna da esquerda do painel (as 17 primeiras): duas vizinhas nunca têm a mesma cor, e quem faz o mesmo papel de
     * um ícone da TV usa a cor dele (tv, config, família, energia, casa, rede).
     */
    private val PAINEL = linkedMapOf(
        "inicio" to LIMA, "controle" to AMBAR, "sofy" to TURQUESA, "tv" to VIOLETA, "apps" to CEU, "loja" to VERDE,
        "arquivos" to AREIA, "config" to ACO, "paleta" to INDIGO, "filme" to AMARELO, "familia" to AREIA, "energia" to VERMELHO,
        "celular" to AZUL, "casa" to LIMA, "acessos" to OLIVA, "saude" to MENTA, "extras" to VERDE,
        "tudo" to ACO, "rede" to TURQUESA, "roteador" to AZUL, "claude" to OLIVA, "ia" to INDIGO, "backup" to VERMELHO,
        "estrela" to AMARELO, "mais" to ACO, "fotos" to AMARELO,
    )
    /** Na ordem da coluna do painel; os 17 primeiros são a navegação. */
    val ICONES_PAINEL: List<String> get() = PAINEL.keys.toList()

    fun corDoPainel(id: String): Int = PAINEL[id] ?: cor(id)
}
