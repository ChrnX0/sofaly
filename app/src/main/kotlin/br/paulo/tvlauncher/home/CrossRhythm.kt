package br.paulo.tvlauncher.home

import android.content.Context
import br.paulo.tvlauncher.settings.Extras

/**
 * Ritmo da cruz: as medidas (dp) que dão o espaçamento da tela inicial (21/11/2026, refino pedido pelo Paulo).
 *
 * Porquê: na foto da Sala (v467869) sobrava uma faixa vazia de ~150 dp entre o nome do mundo e a linha, a coluna
 * descia até cortar o 3º vizinho na borda, e o nome dos itens pequenos ficava no alto do ícone (parecia torto).
 * Para quê: o Paulo escolhe vendo na TV; as 3 propostas e o ritmo de antes ficam trocáveis no Ajuste fino.
 * Como: [HomeCruz] lê as medidas daqui ([atual]); trocar o ritmo remonta a cruz (assinatura do layout da MainActivity).
 * Referências: grade de 8 dp e bordas alinhadas (Taste Skill, "equal edges"); quadros iguais para ícones de desenhos
 * diferentes e ar entre os grupos (Projectivy e Apple TV, que põem cada app num quadro do mesmo tamanho).
 */
enum class CrossRhythm(
    val linha: Float, val coluna: Float, val rotuloY: Float,
    val iconeL: Int, val iconeA: Int, val catL: Int, val catA: Int,
    val escalaPequeno: Float, val passoColuna: Float, val vaoEscolhido: Float, val vaoCima: Float,
    val catEsquerda: Float, val catDireita: Float, val passoCats: Float, val setaY: Float,
    /** Nome do item pequeno no meio da altura do ícone (antes: no alto). */
    val nomeNoMeio: Boolean,
    /** Vão entre o ícone e o texto do item. */
    val vaoTexto: Int,
    /** Transparência das categorias da linha que não estão em foco. */
    val alfaCategoria: Float,
    /** Quantos vizinhos aparecem embaixo do escolhido (1 a 3). */
    val vizinhos: Int,
    /** Cada ícone num quadro do mesmo tamanho (unifica conjuntos de desenho diferente). */
    val quadro: Boolean,
) {
    /** Como era até 21/11/2026. */
    BEFORE(250f, 225f, 96f, 112, 94, 84, 70, 0.6f, 71f, 94f, 89f, 97f, 670f, 104f, 526f, false, 20, 1f, 3, false),
    /**
     * A "Ajustada": mesma cruz, sem a faixa vazia, nomes no meio. 23/11/2026 (Paulo, aprovado no artefato da cruz):
     * a linha desce 36 dp (228 -> 264) e só 2 itens aparecem embaixo do escolhido (eram 3). Mesmo dia, vendo a Sala:
     * "precisa descer um pouco" -> mais 24 dp (288); a seta ▼ fica no pé do 2º (500). O resto da coluna continua lá.
     */
    TIGHT(288f, 225f, 192f, 112, 94, 84, 70, 0.6f, 66f, 92f, 86f, 97f, 660f, 104f, 500f, true, 16, 1f, 2, false),
    /** B "Quadros": grade de 8 dp, cada ícone num quadro igual, mais ar entre os mundos. */
    FRAMED(240f, 224f, 136f, 104, 96, 80, 72, 0.6f, 72f, 96f, 88f, 96f, 664f, 112f, 520f, true, 24, 1f, 3, true),
    /** C "Ar": ícones menores, mundos de fora mais calmos, 2 vizinhos; a tela respira. */
    AIRY(236f, 232f, 144f, 100, 84, 68, 58, 0.56f, 64f, 88f, 80f, 104f, 656f, 116f, 470f, true, 20, 0.62f, 2, false);

    companion object {
        const val KEY = "cruz_ritmo"
        /** Lido pela [HomeCruz] ao montar; a MainActivity põe o valor de [ler] antes de criar a cruz. */
        // Padrão = A "Ajustada" (22/11/2026: escolhida para ir à Sala; as outras seguem no Ajuste fino).
        @Volatile var atual: CrossRhythm = TIGHT
        fun ler(ctx: Context): CrossRhythm = entries.getOrElse(Extras.numero(ctx, KEY, TIGHT.ordinal)) { TIGHT }
    }
}
