package br.paulo.tvlauncher.observer

/**
 * Conferência das telas pela árvore de acessibilidade (parte C do pedido do Paulo, 25/11/2026). Por quê: o teste de
 * telas do repositório (`-Ptelas`, Robolectric) acha texto cortado no PC, mas a TV de verdade tem outra fonte, outro
 * tamanho de tela e o texto que vem de dados reais (nome de app, cidade, título de filme). Para quê: a TV confere as
 * próprias telas de madrugada e trata o que achar como defeito, sem ninguém olhar.
 *
 * Esta parte é pura (sem Android): recebe as caixas do que está escrito e devolve os defeitos. [Caixa] é um nó de
 * texto da árvore: o texto e onde ele está, em pixels da tela.
 *
 * O que conta como defeito (o mesmo da régua do teste de telas):
 *  - texto que sai da tela (cortado na borda);
 *  - texto com caixa de tamanho zero (está escrito e não aparece);
 *  - dois textos por cima um do outro (acima de [SOBRA_MAX] da área do menor);
 *  - tela sem nada para focar (o controle não sai de lá).
 * NÃO é defeito: texto que termina em "…" (ellipsize é escolha nossa) e tela sem texto nenhum (vídeo, foto, jogo).
 */
object SofyScreenCheck {
    data class Caixa(val texto: String, val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
        val largura get() = x2 - x1
        val altura get() = y2 - y1
        val area get() = largura.coerceAtLeast(0).toLong() * altura.coerceAtLeast(0)
    }

    /** [confirmados] = os achados já conferidos em duas leituras (null = conferir agora). */
    data class Tela(val nome: String, val largura: Int, val altura: Int, val caixas: List<Caixa>, val temFoco: Boolean,
                    val confirmados: List<Achado>? = null)

    enum class Defeito { CORTADO, INVISIVEL, SOBREPOSTO, SEM_FOCO }
    /** [onde] = o começo do texto (compara duas leituras); [caixa] = onde ele estava e o tamanho da tela, para a issue. */
    data class Achado(val defeito: Defeito, val onde: String, val caixa: String = "")

    /** Dois textos encostados não são defeito; só conta quando passa de 20% da área do menor. */
    const val SOBRA_MAX = 0.20
    /** A borda da tela da TV é cortada pelos televisores antigos; 2 px de folga evitam acusar quem está na borda certa. */
    const val FOLGA = 2

    fun conferir(t: Tela): List<Achado> {
        t.confirmados?.let { return it }
        val achados = ArrayList<Achado>()
        val comTexto = t.caixas.filter { it.texto.isNotBlank() }
        for (c in comTexto) {
            if (c.largura <= 0 || c.altura <= 0) { achados += Achado(Defeito.INVISIVEL, rotulo(c)); continue }
            if (c.x1 < -FOLGA || c.y1 < -FOLGA || c.x2 > t.largura + FOLGA || c.y2 > t.altura + FOLGA)
                achados += Achado(Defeito.CORTADO, rotulo(c), "[${c.x1},${c.y1}-${c.x2},${c.y2} em ${t.largura}x${t.altura}]")
        }
        val bons = comTexto.filter { it.largura > 0 && it.altura > 0 }
        for (i in bons.indices) for (j in i + 1 until bons.size) {
            val a = bons[i]; val b = bons[j]
            val s = sobra(a, b)
            if (s > 0 && s > SOBRA_MAX * minOf(a.area, b.area)) achados += Achado(Defeito.SOBREPOSTO, "${rotulo(a)} / ${rotulo(b)}")
        }
        // Tela sem foco só é defeito quando há algo escrito nela (uma tela de vídeo pode não ter foco nenhum).
        if (!t.temFoco && comTexto.isNotEmpty()) achados += Achado(Defeito.SEM_FOCO, t.nome)
        return achados.distinct()
    }

    /**
     * Só o que aparece nas DUAS leituras da mesma tela (#518, 26/11/2026). Por quê: a tela que ainda carrega (Armazenamento
     * lendo o pendrive, Previsão buscando os dados) não tem foco nem texto no lugar na primeira olhada; isso não é defeito.
     */
    fun confirmados(primeira: List<Achado>, segunda: List<Achado>): List<Achado> =
        primeira.filter { a -> segunda.any { it.defeito == a.defeito && it.onde == a.onde } }

    private fun sobra(a: Caixa, b: Caixa): Long {
        val w = (minOf(a.x2, b.x2) - maxOf(a.x1, b.x1)).coerceAtLeast(0)
        val h = (minOf(a.y2, b.y2) - maxOf(a.y1, b.y1)).coerceAtLeast(0)
        return w.toLong() * h
    }

    /** O começo do texto, para o relatório. Não vai o texto inteiro: pode ter nome de cidade, de perfil, de filme. */
    private fun rotulo(c: Caixa) = c.texto.trim().take(18).replace('\n', ' ')

    /** Uma linha curta para a issue/o diário: "previsao: CORTADO Sexta-feira". */
    fun resumo(tela: String, achados: List<Achado>) =
        if (achados.isEmpty()) "" else "$tela: " + achados.take(3).joinToString("; ") { "${it.defeito.name} ${it.onde}" + (if (it.caixa.isEmpty()) "" else " ${it.caixa}") }
}
