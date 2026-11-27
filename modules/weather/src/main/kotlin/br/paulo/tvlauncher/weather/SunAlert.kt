package br.paulo.tvlauncher.weather

/**
 * Aviso de sol forte na Ilha (pedido do Paulo, 24/11/2026: "a ilha é inteligente; recados de grande importância").
 * Porquê: UV 8 ou mais (muito alto, na escala da OMS) queima a pele em ~15 min; quem está no sofá não olha a Previsão
 * antes de sair. Para quê: um lembrete curto de protetor solar só nas horas em que ele importa. Como: o UV por hora vem no
 * MESMO pedido de clima da HOME (sem chamada nova); a pílula aparece com UV ≥ [UV_MUITO_ALTO] agora ou na próxima hora,
 * de dia e com céu aberto. Céu fechado ou chuva: nada, porque o MET Norway (plano Básico) manda o UV de céu limpo, um
 * teto do real, e avisar num dia nublado seria alarme falso. Pura (SunAlertTest).
 */
object SunAlert {
    /** OMS: 8-10 = muito alto, 11+ = extremo. Abaixo disso o aviso viraria ruído de todo dia de verão. */
    const val UV_MUITO_ALTO = 8.0
    /** Código WMO a partir do qual o céu está fechado (3 = nublado) ou com chuva/neblina. */
    private const val CEU_FECHADO = 3

    /**
     * O UV (arredondado) para avisar agora, ou null. [horas] no formato "2026-11-24T13:00" e [uvs] na mesma ordem;
     * [horaAgora] = "2026-11-24T13" (a hora cheia de agora, no fuso das [horas]).
     */
    fun uvParaAviso(horas: List<String>, uvs: List<Double>, horaAgora: String, codigo: Int, chovendo: Boolean, eDia: Boolean): Int? {
        if (!eDia || chovendo || codigo >= CEU_FECHADO) return null
        val i = horas.indexOfFirst { it.take(13) == horaAgora }.takeIf { it >= 0 } ?: return null
        val uv = listOfNotNull(uvs.getOrNull(i), uvs.getOrNull(i + 1)).filter { !it.isNaN() }.maxOrNull() ?: return null
        return if (uv >= UV_MUITO_ALTO) Math.round(uv).toInt() else null
    }
}
