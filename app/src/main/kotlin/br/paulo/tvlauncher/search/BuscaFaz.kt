package br.paulo.tvlauncher.search

import br.paulo.tvlauncher.mcp.CaixaComando

/**
 * Busca que faz (elevação 3.11, onda 1, 17/11/2026): o que a pessoa escreve na busca da HOME pode ser um pedido
 * ("desligar em 30", "wifi visita", "letra maior"). A regra, pura (BuscaFazTest); a tela é a BuscaActivity, que mostra
 * o pedido entendido como a PRIMEIRA linha do resultado ("Fazer"). Nada acontece sem o OK da pessoa nessa linha.
 *
 * Duas famílias:
 *  - do catálogo fechado (ComandosIa), pelo mesmo leitor da caixa de comando do celular ([CaixaComando]): timer de sono,
 *    timer, volume, mudo, pausar/continuar, abrir tela, abrir app, canal, "o que passa", modo visita, filtro noturno.
 *    Execução pelo caminho de sempre (PedidoIa.propor: conferir, Confirmações, executar).
 *  - locais desta tela, que não mexem em nada fora do launcher: mostrar o QR do Wi-Fi das visitas, letra maior e
 *    letra menor (TamanhoTexto; uma desfaz a outra).
 *
 * O que já aparece como resultado comum NÃO vira pedido: "netflix" sozinho já está nos Apps, "globo" já está nos Canais.
 * Só vira pedido quando há mais que o nome (um verbo: "abre a netflix", "coloca na globo").
 */
object BuscaFaz {
    /**
     * PROBLEMA_* (Sofy, ideia 44, 24/11/2026): o problema dito em palavras ("sem som", "internet lenta") vira uma
     * checagem da Sofy, local, sem nuvem. SOFY_HOJE (ideia 49): "o que você fez hoje".
     */
    enum class Local { WIFI_VISITA, LETRA_MAIOR, LETRA_MENOR, PROBLEMA_INTERNET, PROBLEMA_SOM, PROBLEMA_LENTA, PROBLEMA_CONTROLE, SOFY_HOJE }

    /** O que fazer: um pedido do catálogo [pedido] OU uma ação [local]. */
    data class Faz(val pedido: CaixaComando.Pedido? = null, val local: Local? = null)

    private val reWifi = Regex("""\b(?:wi ?fi|wireless|senha da rede|senha do wi ?fi)\b.*\b(?:visita\w*|convidad\w*|guest\w*|hospede\w*)\b|\b(?:visita\w*|guest\w*|convidad\w*|hospede\w*)\b.*\b(?:wi ?fi|wireless)\b""")
    private val reLetra = Regex("""\b(?:letra\w*|fonte|texto|text|font|tamanho do texto|tamanho da letra)\b""")
    private val reMaior = Regex("""\b(?:maior\w*|aument\w*|grande|bigger|larger|increase|mais)\b""")
    private val reMenor = Regex("""\b(?:menor\w*|diminu\w*|pequen\w*|normal|smaller|decrease|reduz\w*|menos)\b""")
    private val reVisita = Regex("""\b(?:modo visita|modo de visita\w*|guest mode|modo hospede)\b""")
    private val reFiltro = Regex("""\b(?:filtro noturno|luz noturna|night light|night filter|filtro de luz azul|luz azul)\b""")
    private val reHoje = Regex("""\b(?:o que (?:voce|a sofy|vc) fez hoje|o que fez hoje|what did you do today|que hiciste hoy|sofy hoje|sofy today)\b""")
    private val reProbInternet = Regex("""\b(?:sem internet|internet (?:esta |ta )?(?:lenta|caiu|nao funciona|nao ta|nao esta|ruim|travando|fora|caindo)|wi ?fi (?:esta |ta )?(?:caiu|ruim|fraco|nao funciona|caindo)|no internet|internet (?:is )?(?:slow|down|not working)|wi ?fi (?:is )?(?:slow|down|not working)|sin internet)\b""")
    private val reProbSom = Regex("""\b(?:sem som|sem audio|nao (?:tem|sai|ta saindo|esta saindo) (?:som|audio)|som (?:nao|baixo|sumiu|parou)|audio (?:nao|sumiu)|no sound|no audio|sound (?:is )?(?:not working|gone)|sin sonido)\b""")
    private val reProbLenta = Regex("""\b(?:(?:tv|televisao|app|tudo|ela) (?:esta |ta )?(?:lenta|lento|travando|travada|travado|devagar)|(?:esta|ta) (?:lenta|travando|devagar)|(?:tv|it) (?:is )?(?:slow|lagging|freezing))\b""")
    private val reProbControle = Regex("""\b(?:controle (?:nao funciona|nao responde|parou|falhando|ruim|com atraso|travando)|remote (?:is )?(?:not working|stopped|lagging)|control (?:no funciona))\b""")
    private val reDesliga = Regex("""\b(?:deslig\w*|tira\w*|sai\w*|turn off|off|disable|stop)\b""")

    /**
     * [frase] -> o que fazer, ou null (só busca). [apps] = pacote -> nome (os da HOME). [canalPt] = o leitor de frases de
     * canal da voz (CanalBusca), como na caixa do celular.
     */
    fun entender(frase: String?, apps: Map<String, String>, canalPt: (String) -> CaixaComando.Pedido? = { null }): Faz? {
        val f = CaixaComando.normalizar(frase?.take(CaixaComando.MAX_FRASE) ?: return null)
        if (f.length < 3) return null
        // Locais (antes do catálogo: "wifi visita" não pode virar "modo visita").
        if (reWifi.containsMatchIn(f)) return Faz(local = Local.WIFI_VISITA)
        if (reHoje.containsMatchIn(f)) return Faz(local = Local.SOFY_HOJE)
        if (reProbInternet.containsMatchIn(f)) return Faz(local = Local.PROBLEMA_INTERNET)
        if (reProbSom.containsMatchIn(f)) return Faz(local = Local.PROBLEMA_SOM)
        if (reProbControle.containsMatchIn(f)) return Faz(local = Local.PROBLEMA_CONTROLE)
        if (reProbLenta.containsMatchIn(f)) return Faz(local = Local.PROBLEMA_LENTA)
        if (reLetra.containsMatchIn(f)) {
            if (reMenor.containsMatchIn(f)) return Faz(local = Local.LETRA_MENOR)
            if (reMaior.containsMatchIn(f)) return Faz(local = Local.LETRA_MAIOR)
        }
        if (reVisita.containsMatchIn(f)) return Faz(CaixaComando.Pedido("modo_visita", numero = if (reDesliga.containsMatchIn(f)) 0 else 1))
        if (reFiltro.containsMatchIn(f)) return Faz(CaixaComando.Pedido("filtro_noturno", numero = if (reDesliga.containsMatchIn(f)) 0 else 1))
        val p = CaixaComando.interpretar(frase, apps, canalPt) ?: return null
        // Só o nome (de app ou de canal): já está nos resultados comuns.
        val alvo = when (p.tipo) {
            "abrir_app" -> apps[p.alvo]
            "tocar_canal", "o_que_passa" -> p.alvo
            else -> null
        }
        if (alvo != null && CaixaComando.normalizar(alvo) == f) return null
        return Faz(p)
    }
}
