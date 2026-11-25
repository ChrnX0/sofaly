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
    /** Uma tentativa a cada 3 h, no máximo 4 por dia: se não pegar, não fica abrindo as Configurações a toda hora. */
    const val GAP_MS = 3 * 3_600_000L
    const val MAX_PER_DAY = 4
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
        recent(s.attempts, s.now).size >= MAX_PER_DAY -> Decision.WAIT
        s.attempts.any { s.now - it in 0 until GAP_MS } -> Decision.WAIT
        // Só com o protetor na frente: com alguém vendo, abrir as Configurações por cima é atrapalhar.
        !s.screensaverOpen -> Decision.WAIT
        else -> Decision.TRY
    }

    fun recent(attempts: List<Long>, now: Long) = attempts.filter { now - it in 0 until DAY_MS }

    /** Enquanto a Sofy ainda pode consertar sozinha, o aviso de "acesso caiu" não aparece (ela não explica, conserta). */
    fun stillTrying(s: State): Boolean = s.enabled && s.shellExpected && s.accessibilityOn && recent(s.attempts, s.now).size < MAX_PER_DAY

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
