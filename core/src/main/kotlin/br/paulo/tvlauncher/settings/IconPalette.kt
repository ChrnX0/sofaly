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
    )

    /** Os tipos com cor fixa (para o teste e para a foto da paleta). */
    val ICONES: Set<String> get() = POR_ICONE.keys

    /** A cor do ícone [tipo]; um tipo novo sem cor fixa ganha sempre a mesma da paleta, pelo nome. */
    fun cor(tipo: String): Int {
        val t = tipo.trim().lowercase()
        return POR_ICONE[t] ?: TODAS[Math.floorMod(t.hashCode(), TODAS.size)]
    }
}
