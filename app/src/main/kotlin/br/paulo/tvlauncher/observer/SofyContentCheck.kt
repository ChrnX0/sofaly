package br.paulo.tvlauncher.observer

import br.paulo.tvlauncher.alarm.Agenda

/**
 * Conferência do CONTEÚDO das telas: o que a tela diz precisa ser verdade aqui. Por quê (Paulo, 25/11/2026: "manda a
 * thread de revisão ensinar tudo o que ela aprendeu para a Sofy"): os defeitos que passaram por todos os testes vieram
 * de dados reais da Sala, não de código novo. Exemplos: feriado dos EUA numa TV no Brasil, o mesmo feriado com dois
 * nomes, ícone de nuvem ao lado de "Pode chover a qualquer momento", "Hoje 27°" com 30° agora e uma ficha com
 * "498783 h". O [SofyScreenCheck] confere a forma (texto cortado, sobreposto, sem foco); este confere o sentido.
 * Lições completas, com o caso real de cada uma: /mnt/project-files/tv-launcher/sofy/licoes-da-revisao-2026-11-25.md.
 *
 * É pura (sem Android). Recebe o texto das telas (as mesmas [SofyScreenCheck.Tela] lidas pela acessibilidade) e um
 * [Retrato] do que a TV sabe. Devolve os defeitos. Quem liga na TV decide o reparo: os defeitos de dado vão para
 * REFRESH_DATA ([deDado]); os de código viram relato.
 */
object SofyContentCheck {
    enum class Defeito {
        /** Feriado de outro país na tela, quando a conta assina a agenda de feriados do país da TV (#497, #500). */
        FERIADO_DE_FORA,
        /** O mesmo evento duas vezes na mesma tela com outro nome ("Natal" e "Dia de Natal", #497). */
        REPETIDO,
        /** A frase diz que pode chover agora e o ícone de alguma tela não é de chuva (#498). */
        CHUVA_DESENCONTRADA,
        /** A máxima de hoje abaixo da temperatura de agora, ou a mínima acima (protetor, 25/11/2026). */
        HOJE_NAO_CONTA_AGORA,
        /** Duração de milhares de horas: data zerada ou cache antigo ("498783 h", #497). */
        DURACAO_ABSURDA,
        /** Porcentagem acima de 100. */
        PORCENTO_FORA_DE_FAIXA,
        /** OK num item com foco e nada mudou: nem janela, nem foco, nem texto (TodoBotaoFazAlgoTest, na TV). */
        BOTAO_MUDO,
    }

    data class Achado(val defeito: Defeito, val tela: String, val onde: String)

    /**
     * O que a TV sabe na hora da conferência. [pais] = país da TV (FeriadosTv.pais()); [eventos] = os eventos crus da
     * agenda (antes do Agenda.juntar), com o dono de cada um; [agendas] = os donos das agendas assinadas e visíveis;
     * [chuvaAgora] = a frase do clima anuncia chuva agora (DicasClima.TipoFrase.CHUVA_AGORA); [icones] = a chave do
     * ícone do clima mostrada em cada tela ("home" -> "clima:3d/cloud"); [tempAgora] = a temperatura de agora;
     * [rotulosHoje] = como "hoje" aparece escrito nas faixas de dias ("Hoje", "Today"...).
     */
    data class Retrato(
        val pais: String? = null,
        val eventos: List<Agenda.Evento> = emptyList(),
        val agendas: List<String> = emptyList(),
        val chuvaAgora: Boolean = false,
        val icones: Map<String, String> = emptyMap(),
        val tempAgora: Int? = null,
        val rotulosHoje: Set<String> = setOf("Hoje", "Today"),
    )

    /** Defeitos que um REFRESH_DATA pode resolver (o dado ficou velho); os outros são de código. */
    val deDado = setOf(Defeito.CHUVA_DESENCONTRADA, Defeito.DURACAO_ABSURDA, Defeito.PORCENTO_FORA_DE_FAIXA)

    private val HORAS = Regex("""(?<![\d.,])(\d{4,})\s?h\b""")
    private val PORCENTO = Regex("""(?<![\d.,])(\d{1,4})\s?%""")
    private val MAX_MIN = Regex("""(-?\d+)°\s*/?\s*(-?\d+)°""")
    private val ICONE_CHUVA = Regex("rain|lightning|drizzle|shower|storm|snow|chuva")

    fun conferir(telas: List<SofyScreenCheck.Tela>, r: Retrato): List<Achado> {
        val achados = ArrayList<Achado>()
        val deFora = feriadosDeFora(r)
        for (t in telas) {
            val textos = t.caixas.map { it.texto.trim() }.filter { it.isNotEmpty() }
            // Feriado de fora: o título de um feriado de outro país aparece escrito na tela.
            for (f in deFora) if (textos.any { it.equals(f, true) }) achados += Achado(Defeito.FERIADO_DE_FORA, t.nome, f)
            // Repetido: dois títulos DIFERENTES de eventos da agenda, que são o mesmo nome sem enfeite, na mesma tela.
            val titulos = r.eventos.map { it.titulo.trim() }.toSet()
            textos.filter { it in titulos }.distinct().groupBy { Agenda.nomeDeFeriado(it) }
                .filter { (nome, l) -> nome.length >= 3 && l.size > 1 }
                .forEach { (_, l) -> achados += Achado(Defeito.REPETIDO, t.nome, l.joinToString(" / ")) }
            for (x in textos) {
                HORAS.find(x)?.let { achados += Achado(Defeito.DURACAO_ABSURDA, t.nome, x.take(40)) }
                PORCENTO.findAll(x).firstOrNull { it.groupValues[1].toInt() > 100 }
                    ?.let { achados += Achado(Defeito.PORCENTO_FORA_DE_FAIXA, t.nome, x.take(40)) }
            }
            r.tempAgora?.let { agora -> hojeNaFaixa(t, r.rotulosHoje)?.let { (mx, mn) ->
                if (mx < agora || mn > agora) achados += Achado(Defeito.HOJE_NAO_CONTA_AGORA, t.nome, "$mx° $mn° com $agora° agora")
            } }
        }
        if (r.chuvaAgora) for ((tela, chave) in r.icones) {
            if (!ICONE_CHUVA.containsMatchIn(chave.lowercase())) achados += Achado(Defeito.CHUVA_DESENCONTRADA, tela, chave)
        }
        return achados
    }

    /**
     * Títulos dos feriados de outro país, só quando a agenda do país da TV está ASSINADA. Vale a assinatura, não o que
     * cai na janela: o 1º conserto (#497) olhava a janela, e a do Brasil sem evento ali deixou os dos EUA (#500).
     */
    fun feriadosDeFora(r: Retrato): Set<String> {
        val pais = r.pais?.uppercase() ?: return emptySet()
        val donos = (r.agendas + r.eventos.map { it.dono }).filter { "#holiday@" in it }.distinct()
        if (donos.none { Agenda.paisFeriados(it) == pais }) return emptySet()
        return r.eventos.filter { e -> Agenda.paisFeriados(e.dono).let { it != null && it != pais } }.map { it.titulo.trim() }.toSet()
    }

    /**
     * A máxima e a mínima de hoje numa faixa de dias: a caixa com "Hoje" e, logo abaixo dela (mesma coluna, até 3
     * alturas de linha), a caixa com "27° 22°". null se a tela não tem faixa de dias.
     */
    fun hojeNaFaixa(t: SofyScreenCheck.Tela, rotulosHoje: Set<String>): Pair<Int, Int>? {
        val hoje = t.caixas.firstOrNull { c -> rotulosHoje.any { c.texto.trim().equals(it, true) } } ?: return null
        val alt = hoje.altura.coerceAtLeast(1)
        val valor = t.caixas.filter { c ->
            c !== hoje && c.y1 >= hoje.y1 && c.y1 <= hoje.y2 + 3 * alt && kotlin.math.abs(c.x1 - hoje.x1) <= 2 * alt && MAX_MIN.containsMatchIn(c.texto)
        }.minByOrNull { it.y1 } ?: return null
        val m = MAX_MIN.find(valor.texto) ?: return null
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    /**
     * Botão mudo: depois do OK, a mesma janela, o mesmo foco e os mesmos textos. [antes]/[depois] = os textos da tela;
     * [focoAntes]/[focoDepois] = um rótulo do item com foco; [janelaMudou] = outra janela (ou app) ficou na frente.
     */
    fun botaoMudo(antes: List<String>, depois: List<String>, focoAntes: String?, focoDepois: String?, janelaMudou: Boolean): Boolean =
        !janelaMudou && focoAntes == focoDepois && antes == depois

    fun resumo(achados: List<Achado>) =
        if (achados.isEmpty()) "conteúdo ok"
        else achados.groupBy { it.defeito }.entries.joinToString("; ") { (d, l) -> "$d x${l.size} (${l.first().tela}: ${l.first().onde})" }
}
