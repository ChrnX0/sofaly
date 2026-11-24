package br.paulo.tvlauncher.companion

/**
 * Acertar o atraso do som pelo microfone do celular (24/11/2026, proposta B; no Apple TV é o "Wireless Audio Sync").
 * Conta pura (AudioSyncMathTest).
 *
 * Por quê: o "Som no fone" (SomNoFoneRegras) toca no celular o som da TV em hora marcada = hora da TV + um atraso que
 * hoje é um chute fixo (250 ms) e cada pessoa acerta de ouvido. Para quê: o som do fone sai junto com o da TV e com a
 * boca de quem fala, sem ninguém mexer em controle. Como: a TV escreve bipes num AudioTrack igual ao do player e anota a
 * hora (relógio da TV) em que cada bipe entrou; o celular, com o relógio acertado com a TV ([clockOffset]), ouve cada
 * bipe pelo microfone; a diferença é o atraso do som desta TV nesta saída (alto-falante da TV, barra de som, fone
 * Bluetooth). A Sofy guarda um número por saída e o usa sozinha quando a saída muda ([CompanionTv]).
 */
object AudioSyncMath {
    /** Quantos bipes por medida e de quanto em quanto tempo (longe o bastante para o eco da sala não virar outro bipe). */
    const val BEEPS = 6
    const val GAP_MS = 700L
    const val BEEP_MS = 30
    const val BEEP_HZ = 2_000.0
    /** Medidas espalhadas por mais que isto não valem (relógio ou detecção ruins). */
    const val MAX_SPREAD_MS = 35.0
    /** Atraso que faz sentido: entre 0 e 1 s (Bluetooth chega a ~300 ms; mais que 1 s é bipe errado). */
    val RANGE_MS = 0.0..1_000.0

    /** Uma ida e volta para acertar o relógio: [sentMs] e [gotMs] no relógio do celular, [tvMs] o que a TV respondeu. */
    data class Ping(val sentMs: Double, val tvMs: Double, val gotMs: Double) {
        val rtt get() = gotMs - sentMs
    }

    /**
     * Quanto somar ao relógio do celular para ter o da TV. Usa as 3 idas e voltas mais rápidas (o Wi-Fi às vezes segura
     * um pacote) e supõe ida = volta. Erro máximo = metade da ida e volta, devolvido junto.
     */
    fun clockOffset(pings: List<Ping>): Pair<Double, Double>? {
        val bons = pings.filter { it.rtt in 0.0..500.0 }.sortedBy { it.rtt }.take(3)
        if (bons.isEmpty()) return null
        val offsets = bons.map { it.tvMs - (it.sentMs + it.gotMs) / 2 }
        return median(offsets) to bons.first().rtt / 2
    }

    enum class Verdict { GOOD, UNSURE }
    data class Result(val verdict: Verdict, val delayMs: Int, val spreadMs: Int, val heard: Int, val detail: String)

    /**
     * [writtenTv] = hora (relógio da TV) em que cada bipe entrou no AudioTrack; [heardPhone] = hora (relógio do celular)
     * em que o microfone ouviu um bipe, já descontada a demora do próprio microfone. Cada bipe ouvido casa com o bipe
     * escrito mais próximo ANTES dele; sobra (eco, barulho) fica de fora.
     */
    fun delay(writtenTv: List<Double>, heardPhone: List<Double>, offsetMs: Double, clockErrMs: Double): Result {
        val atrasos = heardPhone.map { it + offsetMs }.mapNotNull { h ->
            writtenTv.filter { it <= h }.maxOrNull()?.let { h - it }?.takeIf { it in RANGE_MS }
        }
        if (atrasos.size < 3) return Result(Verdict.UNSURE, 0, 0, atrasos.size, "heard=${atrasos.size}")
        val m = median(atrasos)
        // Bipe casado com o anterior (atraso > intervalo) seria detecção errada: fica só o grupo perto da mediana.
        val perto = atrasos.filter { kotlin.math.abs(it - m) <= MAX_SPREAD_MS * 2 }
        val med = median(perto)
        val spread = (perto.max() - perto.min())
        val detail = "n=${perto.size} spread=${spread.toInt()} clock=${clockErrMs.toInt()}"
        val ok = perto.size >= 3 && spread <= MAX_SPREAD_MS && clockErrMs <= 25
        return Result(if (ok) Verdict.GOOD else Verdict.UNSURE, Math.round(med).toInt(), spread.toInt(), perto.size, detail)
    }

    fun median(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** Nome da saída de som para a chave (só letras, números e "-"): "speaker", "hdmi", "bt-<nome>"... */
    fun routeKey(type: String, name: String?): String {
        val n = name.orEmpty().filter { it.isLetterOrDigit() }.take(20)
        return if (n.isEmpty()) type else "$type-$n"
    }
}
