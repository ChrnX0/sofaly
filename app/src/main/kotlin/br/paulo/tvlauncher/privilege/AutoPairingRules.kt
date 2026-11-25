package br.paulo.tvlauncher.privilege

import java.text.Normalizer

/**
 * Regras puras do autorreparo do pareamento ([AutoPairing], 25/11/2026). Sem Android, com teste JVM (AutoPairingRulesTest).
 *
 * Por quê: em 25/11/2026 às 06:19Z a Sala esqueceu a chave do launcher ("ADB pairing is required"), o shell caiu e o
 * botão do Protetor parou. A Sofy via a queda, mas só sabia avisar, e o aviso ficou atrás do protetor de tela. Paulo:
 * "ela não precisa explicar nada, só identificar e corrigir". Para quê: o acesso interno volta sem ninguém encostar.
 */
object AutoPairingRules {
    /** O shell precisa estar fora há pelo menos isto (umas duas sondas): não reage a um soluço do adb. */
    const val MIN_DOWN_MS = 20 * 60_000L
    /**
     * Ritmo que se calibra (Paulo, 25/11/2026: "ela tem que saber se calibrar sozinha"; antes eram 3 h fixas e 4 por
     * dia). Recuo exponencial: a 1ª repetição espera [FIRST_GAP_MS] e cada tentativa sem sucesso dobra a espera, até
     * [MAX_GAP_MS]. Sem teto por dia: o shell fora é grave, então ela nunca para de tentar, só espaça. Tentativa que deu
     * certo zera a conta ([AutoPairing] limpa a lista quando o shell volta).
     */
    const val FIRST_GAP_MS = 15 * 60_000L
    const val MAX_GAP_MS = 6 * 3_600_000L
    /** A partir de tantas tentativas sem sucesso no dia, o aviso de "acesso caiu" pode aparecer (ela segue tentando). */
    const val SILENT_TRIES = 4
    const val DAY_MS = 24 * 3_600_000L
    /** Prazo de uma tentativa inteira (abrir, navegar, ler código, parear). */
    const val ATTEMPT_MS = 75_000L

    data class State(
        val enabled: Boolean,            // regra da Play (PlayPolicy.READ_PAIRING_CODE) e Config
        val shellExpected: Boolean,      // o shell já funcionou nesta TV (nunca pareada = não é conserto, é instalação)
        val shellDownForMs: Long?,       // null = o shell não está fora
        val screensaverOpen: Boolean,    // o NOSSO protetor está na frente: TV ligada e ninguém mexendo
        val accessibilityOn: Boolean,    // é por ela que a Sofy lê e toca na tela de pareamento
        val wifiOn: Boolean,             // sem Wi-Fi não existe depuração sem fio
        val attempts: List<Long>,        // horários (parede) das tentativas
        val now: Long,
    )

    enum class Decision { TRY, WAIT, NO }

    fun decide(s: State): Decision = when {
        !s.enabled || !s.shellExpected || !s.accessibilityOn -> Decision.NO
        s.shellDownForMs == null || s.shellDownForMs < MIN_DOWN_MS || !s.wifiOn -> Decision.NO
        s.now < nextAt(s) -> Decision.WAIT
        // Só com o protetor na frente: com alguém vendo, abrir as Configurações por cima é atrapalhar.
        !s.screensaverOpen -> Decision.WAIT
        else -> Decision.TRY
    }

    /** O freio que barrou, em poucas palavras (para o log e o relatório). Vazio = pode tentar. */
    fun why(s: State): String = when {
        !s.enabled -> "desligado neste build"
        !s.shellExpected -> "shell nunca funcionou aqui"
        !s.accessibilityOn -> "sem acessibilidade"
        s.shellDownForMs == null -> "shell não está fora"
        s.shellDownForMs < MIN_DOWN_MS -> "fora há só ${s.shellDownForMs / 60_000} min"
        !s.wifiOn -> "sem Wi-Fi"
        s.now < nextAt(s) -> "recuo: ${recent(s.attempts, s.now).size} tentativa(s) hoje, próxima em ${(nextAt(s) - s.now + 59_999) / 60_000} min"
        !s.screensaverOpen -> "protetor não está na frente"
        else -> ""
    }

    fun recent(attempts: List<Long>, now: Long) = attempts.filter { now - it in 0 until DAY_MS }

    /** Espera depois de [n] tentativas sem sucesso: dobra a cada uma, até [MAX_GAP_MS]. */
    fun gapMs(n: Int): Long = if (n <= 0) 0 else minOf(MAX_GAP_MS, FIRST_GAP_MS shl (n - 1).coerceAtMost(10))

    /** Quando pode tentar de novo (0 = já). */
    fun nextAt(s: State): Long {
        val r = recent(s.attempts, s.now)
        return if (r.isEmpty()) 0 else r.max() + gapMs(r.size)
    }

    /** Enquanto a Sofy ainda pode consertar sozinha, o aviso de "acesso caiu" não aparece (ela não explica, conserta). */
    fun stillTrying(s: State): Boolean = s.enabled && s.shellExpected && s.accessibilityOn && recent(s.attempts, s.now).size < SILENT_TRIES

    // ---------------------------------------------------------------- navegação na tela das Configurações

    enum class Step { OPEN_WIRELESS, OPEN_PAIRING, READ_CODE }

    /**
     * Onde estamos, pelos textos da tela: com o código de 6 dígitos na tela, falta lê-lo; com "parear com código", falta
     * entrar nele; senão, falta abrir a "depuração sem fio". [pairLabels]/[wirelessLabels] vêm dos recursos das
     * próprias Configurações (na língua da TV) mais os de reserva abaixo.
     */
    fun step(texts: List<String>, pairLabels: List<String>): Step = when {
        CodigoPareamento.achar(texts) != null -> Step.READ_CODE
        texts.any { t -> pairLabels.any { matches(t, it) } } -> Step.OPEN_PAIRING
        else -> Step.OPEN_WIRELESS
    }

    /** O texto da tela [text] é o rótulo [label]? Sem acento e sem caixa; começa com ele (o Android às vezes põe "…"). */
    fun matches(text: String, label: String): Boolean {
        val t = norm(text); val l = norm(label)
        return l.isNotEmpty() && (t == l || t.startsWith(l))
    }

    private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .lowercase().replace(Regex("\\s+"), " ").trim().trimEnd('.', '…')

    /** Nomes dos textos nas Configurações (AOSP Settings e TvSettings) e reservas em inglês/português. */
    val WIRELESS_RES = listOf("enable_adb_wireless", "adb_wireless_settings")
    val PAIR_RES = listOf("adb_pair_method_code_title")
    val WIRELESS_FALLBACK = listOf("Wireless debugging", "Depuração por Wi-Fi", "Depuração sem fio", "Depuração via Wi-Fi")
    val PAIR_FALLBACK = listOf("Pair device with pairing code", "Parear o dispositivo com um código de pareamento",
        "Parear dispositivo com código de pareamento", "Parear dispositivo com código")
}
