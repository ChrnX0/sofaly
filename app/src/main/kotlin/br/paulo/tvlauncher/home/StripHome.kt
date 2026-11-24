package br.paulo.tvlauncher.home

import android.content.Context
import br.paulo.tvlauncher.settings.Extras

/**
 * "Barra única": a tela inicial nova (Paulo, 24/11/2026; estudo escolhas-home v2, opções que ele marcou = padrão).
 *
 * Porquê: a cruz ocupava o meio da tela; o Paulo quis "mais espaço livre de tela no geral para trabalhar o fundo".
 * Para quê: UMA linha de cards de apps embaixo (◀ ▶ escolhem o app) e, logo acima, o ícone do mundo + nome + descrição
 * numa linha só; ▲ ▼ trocam o mundo. A Ilha inteligente continua no topo. Cards personalizáveis como no Projectivy.
 * Como: a [HomeCruz] guarda o mesmo estado da cruz ([CruzLogica.Estado]) e só traduz a tecla aqui ([passo]); o desenho é
 * do [StripLayer]. Lembrar o último app, HOME, abrir e o OK segurado continuam iguais em todos os jeitos.
 * Tudo que é conta (tecla, tamanho do card, janela visível, descrição viva) fica aqui, puro, com teste na JVM.
 */
object StripHome {

    // ---- opções (Extras, números); padrão = o que o Paulo marcou no estudo em 24/11/2026 12:59Z
    const val KEY_DESC = "strip_desc"         // 0 frase fixa, 1 o que tem agora
    const val KEY_FORMAT = "strip_format"     // 0 16:9, 1 4:3, 2 quadrado
    const val KEY_SIZE = "strip_size"         // 0 pequeno, 1 médio, 2 grande
    const val KEY_GAP = "strip_gap"           // 0 justo, 1 normal, 2 largo
    const val KEY_CORNERS = "strip_corners"   // 0 retos, 1 suaves, 2 redondos
    const val KEY_GLASS = "strip_glass"       // 0 sem placa, 1 com placa de vidro atrás
    const val KEY_NAME = "strip_name"         // 0 sem nome, 1 nome no card em foco

    enum class Desc { FIXED, LIVE }
    enum class Format(val w: Float, val h: Float) { WIDE(122f, 69f), CLASSIC(96f, 72f), SQUARE(76f, 76f) }
    enum class Size(val k: Float) { SMALL(0.85f), MEDIUM(1f), LARGE(1.15f) }
    enum class Gap(val dp: Float) { TIGHT(12f), NORMAL(20f), WIDE(30f) }
    enum class Corners(val dp: Float) { SQUARE(4f), SOFT(12f), ROUND(22f) }

    data class Options(
        val desc: Desc = Desc.LIVE, val format: Format = Format.WIDE, val size: Size = Size.SMALL,
        val gap: Gap = Gap.WIDE, val corners: Corners = Corners.ROUND, val glass: Boolean = false, val name: Boolean = false,
    ) {
        /** Largura e altura do card (dp). */
        val cardW get() = Math.round(format.w * size.k).toFloat()
        val cardH get() = Math.round(format.h * size.k).toFloat()
        /** Muda o desenho da HOME: entra na assinatura que a refaz. */
        fun signature() = "${desc.ordinal}${format.ordinal}${size.ordinal}${gap.ordinal}${corners.ordinal}${if (glass) 1 else 0}${if (name) 1 else 0}"
    }

    val DEFAULT = Options()

    fun options(ctx: Context): Options {
        fun <E : Enum<E>> ler(key: String, todos: Array<E>, padrao: E): E = todos.getOrNull(Extras.numero(ctx, key, padrao.ordinal)) ?: padrao
        return Options(
            desc = ler(KEY_DESC, Desc.entries.toTypedArray(), DEFAULT.desc),
            format = ler(KEY_FORMAT, Format.entries.toTypedArray(), DEFAULT.format),
            size = ler(KEY_SIZE, Size.entries.toTypedArray(), DEFAULT.size),
            gap = ler(KEY_GAP, Gap.entries.toTypedArray(), DEFAULT.gap),
            corners = ler(KEY_CORNERS, Corners.entries.toTypedArray(), DEFAULT.corners),
            glass = Extras.numero(ctx, KEY_GLASS, 0) == 1,
            name = Extras.numero(ctx, KEY_NAME, 0) == 1,
        )
    }

    /** Gira uma opção ([passo] +1/-1) e devolve o índice novo. */
    fun next(ctx: Context, key: String, atual: Int, total: Int, passo: Int): Int =
        Math.floorMod(atual + passo, total).also { Extras.definirNumero(ctx, key, it) }

    // ---- medidas (dp numa tela de 960 x 540): grade 15 x 11 do estudo
    const val MARGEM = 60f          // borda esquerda da linha do mundo e do 1º card
    const val TRILHO_X = 32f        // os pontinhos dos mundos, à esquerda da linha
    const val LINHA_DO_FUNDO = 130f // topo da linha do mundo, medido de baixo (540 - 410)
    const val LINHA_A = 32f
    const val CARDS_DO_FUNDO = 49f  // centro da linha de cards, medido de baixo (540 - 491)
    const val ICONE = 30f
    const val FONTE_NOME = 24f
    const val FONTE_DESC = 18f
    const val SUBIR_FOCO = 4f       // o card em foco sobe 4 dp
    const val ANEL = 3f             // anel laranja (px no estudo; aqui dp/2 arredondado na tela)
    const val PLACA = 8f            // folga da placa de vidro em volta dos cards
    const val MS = 250L             // passo das animações (token "fast" do sistema)
    const val ESCALONA_MS = 40L     // os cards entram um depois do outro

    // ---- teclas

    /** Resultado de uma tecla: a tecla da cruz a aplicar (null = nada, som de borda) ou sair para a barra de cima. */
    data class Passo(val tecla: CruzLogica.Tecla?, val subirTopo: Boolean = false)

    /**
     * ◀ ▶ andam nos apps do mundo (sem dar a volta), ▲ ▼ trocam o mundo; ▲ no 1º mundo sobe para a barra de cima (Ilha).
     * Na cruz, mundo = coluna e app = linha da coluna: ◀ ▶ viram ▲ ▼ da cruz e ▲ ▼ viram ◀ ▶.
     */
    fun passo(t: CruzLogica.Tecla, cat: Int, sel: Int): Passo = when (t) {
        CruzLogica.Tecla.ESQUERDA -> Passo(if (sel > 0) CruzLogica.Tecla.CIMA else null)
        CruzLogica.Tecla.DIREITA -> Passo(CruzLogica.Tecla.BAIXO)
        CruzLogica.Tecla.CIMA -> if (cat > 0) Passo(CruzLogica.Tecla.ESQUERDA) else Passo(null, subirTopo = true)
        CruzLogica.Tecla.BAIXO -> Passo(CruzLogica.Tecla.DIREITA)
        else -> Passo(t)
    }

    /**
     * Primeiro card visível: a linha rola para o escolhido ficar à vista com, se houver, um vizinho depois dele (dá para
     * ver que tem mais). [larguras] de cada card (o escolhido pode ser mais largo, com o nome), [vao] entre eles, [limite]
     * = largura útil. Mesma conta do estudo. Devolve (primeiro, quantos cabem a partir dele).
     */
    fun janela(sel: Int, larguras: List<Float>, vao: Float, limite: Float): Pair<Int, Int> {
        val n = larguras.size
        if (n == 0) return 0 to 0
        fun cabem(a: Int): Int {
            var x = 0f; var c = 0
            for (i in a until n) { x += larguras[i] + (if (c > 0) vao else 0f); if (x > limite) break; c++ }
            return c.coerceAtLeast(1)
        }
        var ini = 0
        while (ini < sel && ini + cabem(ini) <= sel + 1 && ini + cabem(ini) < n) ini++
        return ini to cabem(ini)
    }

    // ---- descrição

    /** O "o que tem agora" de um mundo; null = sem nada a dizer (fica a frase fixa). Negrito entre ** **. */
    data class Itens(val id: String, val nome: String, val sub: String, val situacao: CruzLogica.Situacao?)

    fun viva(cat: CruzLogica.Categoria, itens: List<Itens>, appsTexto: (Int) -> String): String? {
        fun primeira(s: String) = s.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        itens.firstOrNull { it.situacao != null && it.situacao.texto.isNotBlank() }?.situacao?.let { s ->
            return "**${s.rotulo}** ${semGritar(s.texto)}".trim()
        }
        return when (cat) {
            CruzLogica.Categoria.FERRAMENTAS -> listOfNotNull(
                itens.firstOrNull { it.id == "t:despertador" }?.let { primeira(it.sub) },
                itens.firstOrNull { it.id == "t:clima" }?.let { primeira(it.sub) },
            ).filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
            CruzLogica.Categoria.APLICATIVOS -> itens.count { it.id.startsWith("app:") }.takeIf { it > 0 }?.let(appsTexto)
            // Jogar: sem jogo em andamento fica a frase fixa (24/11/2026: "RetroArch · Emulador..." com o foco na Central
            // parecia falar do card escolhido).
            else -> null
        }
    }

    /**
     * Título que chega TODO EM MAIÚSCULAS (muito podcast é assim) vira Cada Palavra Com Maiúscula: na linha do mundo, em
     * 18 sp, caixa alta grita e ocupa ~30% mais largura. Texto normal passa igual.
     */
    fun semGritar(s: String): String {
        val letras = s.filter { it.isLetter() }
        if (letras.length < 8 || letras.count { it.isUpperCase() } < letras.length * 0.8) return s
        return s.lowercase().split(" ").mapIndexed { i, w -> if (i > 0 && w in MIUDAS) w else w.replaceFirstChar { it.titlecase() } }.joinToString(" ")
    }

    /** Palavras pequenas que ficam minúsculas no meio do título (pt, es, en, fr, it). */
    private val MIUDAS = setOf("e", "de", "da", "do", "das", "dos", "a", "o", "as", "os", "em", "no", "na", "y", "del", "la", "el",
        "and", "of", "the", "to", "in", "et", "le", "les", "des", "di", "il")

    /** Partes de um texto com ** ** (negrito) para quem desenha: (texto, negrito?). */
    fun partes(s: String): List<Pair<String, Boolean>> =
        s.split("**").mapIndexedNotNull { i, p -> if (p.isEmpty()) null else p to (i % 2 == 1) }
}
