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
    const val KEY_FORMAT = "strip_format"     // 0 16:9, 1 4:3, 2 quadrado, 3 logo solto, 4 grade de 2 linhas, 5 redondo, 6 só o escolhido
    const val KEY_SIZE = "strip_size"         // 0 pequeno, 1 médio, 2 grande
    const val KEY_GAP = "strip_gap"           // 0 justo, 1 normal, 2 largo
    const val KEY_CORNERS = "strip_corners"   // 0 retos, 1 suaves, 2 redondos
    const val KEY_GLASS = "strip_glass"       // 0 sem placa, 1 com placa de vidro atrás
    const val KEY_NAME = "strip_name"         // 0 sem nome, 1 nome no card em foco
    const val KEY_OWN = "strip_own"           // placa dos cards nossos (TV, Séries...): Own.ordinal
    const val KEY_MOOD = "strip_mood"         // personalidade dos ícones dos cards nossos: Mood.ordinal
    const val KEY_WORLD_ANIM = "strip_world_anim" // animação do ícone grande do mundo: WorldAnim.ordinal

    enum class Desc { FIXED, LIVE }
    /**
     * Jeito dos cards (24/11/2026, Paulo: "a variedade de layouts dos ícones e cards" do Projectivy). Os 3 primeiros são
     * placas; os 4 novos: LOGO = o logo solto, sem placa; GRID = grade de 2 linhas (◀ ▶ colunas, ▲ ▼ linhas e, na borda,
     * mundo); ROUND = redondo com o nome embaixo; ONLY = só o escolhido tem placa e arte, os outros são só o ícone, menores.
     * A ordem dos números não muda (fica guardada como número em Extras).
     */
    enum class Format(val w: Float, val h: Float) {
        WIDE(122f, 69f), CLASSIC(96f, 72f), SQUARE(76f, 76f), LOGO(96f, 64f), GRID(104f, 58f), ROUND(64f, 64f), ONLY(122f, 69f);
        /** Mostra o banner de TV do app (a arte 16:9). */
        val banner get() = this == WIDE || this == ONLY || this == GRID || this == LOGO
        /** Duas linhas de cards. */
        val grade get() = this == GRID
        /** Nome fora do card, embaixo (sempre visível). */
        val nomeEmbaixo get() = this == ROUND
        /** Nome dentro do card sem banner (16:9, grade e só o escolhido; nos quadrados só cabe o ícone). */
        val rotuloDentro get() = this == WIDE || this == ONLY || this == GRID
        /** Aceita o "nome no card em foco" (o card se alarga): não na grade nem no redondo, que já têm lugar fixo. */
        val nomeNoFoco get() = !grade && !nomeEmbaixo
    }
    /** Vão entre as 2 linhas da grade e espaço do nome embaixo do redondo (dp). */
    const val VAO_GRADE = 10f
    const val NOME_EMBAIXO = 20f
    enum class Size(val k: Float) { SMALL(0.85f), MEDIUM(1f), LARGE(1.15f) }
    enum class Gap(val dp: Float) { TIGHT(12f), NORMAL(20f), WIDE(30f) }
    /**
     * Placa dos cards nossos (TV, Séries, Filmes, Rede...), com o ícone na linha do nome (Paulo, 25/11/2026: o marrom
     * antigo "tinha um destaque feio"; quis as 3 propostas como opção). Branco = igual aos cards dos apps (padrão);
     * Vidro claro = véu claro fino, a foto aparece atrás; Vidro escuro = véu escuro, ícone na cor da marca.
     */
    enum class Own { WHITE, GLASS, DARK }
    /**
     * Personalidade dos ícones dos cards nossos (Paulo, 25/11/2026: "senti falta de personalidade; todos iguais, mesma
     * cor, nem se mexem mais como na cruz"). [COLOR]: cada ícone na cor dele e o do foco se mexe. [GLOW] (padrão): o
     * mesmo, e o card em foco acende na cor do item (How We Feel) enquanto os outros respiram devagar. [TILE]: o ícone
     * num azulejo colorido dentro do card, a peça das Configurações (Apple Games, Opera).
     */
    enum class Mood { COLOR, GLOW, TILE }

    /**
     * Animação do ícone grande do mundo escolhido (Paulo, 25/11/2026: "algo incrível, mas não exagerado"; os 3 como opção,
     * A padrão): SCENE = pequena cena (o ícone faz a ação do mundo), LIGHT = traço de luz correndo pelo contorno,
     * BOUNCE = as partes acendem em sequência e o ícone dá um pulinho no fim da volta. Voltas de 5 a 7 s.
     */
    enum class WorldAnim { SCENE, LIGHT, BOUNCE }
    enum class Corners(val dp: Float) { SQUARE(4f), SOFT(12f), ROUND(22f) }

    data class Options(
        val desc: Desc = Desc.LIVE, val format: Format = Format.WIDE, val size: Size = Size.SMALL,
        val gap: Gap = Gap.WIDE, val corners: Corners = Corners.ROUND, val glass: Boolean = false, val name: Boolean = false,
        val own: Own = Own.DARK,   // Paulo, 25/11/2026: prefere o vidro escuro
        val mood: Mood = Mood.GLOW,
        val worldAnim: WorldAnim = WorldAnim.SCENE,
    ) {
        /** Largura e altura do card (dp). */
        val cardW get() = Math.round(format.w * size.k).toFloat()
        val cardH get() = Math.round(format.h * size.k).toFloat()
        /** Altura do bloco de cards (2 linhas na grade; o nome embaixo do redondo). */
        val blocoH get() = when {
            format.grade -> 2 * cardH + VAO_GRADE
            format.nomeEmbaixo -> cardH + NOME_EMBAIXO
            else -> cardH
        }
        /** Raio dos cantos: o redondo é um círculo. */
        val raio get() = if (format == Format.ROUND) cardH / 2f else corners.dp
        /** Muda o desenho da HOME: entra na assinatura que a refaz. */
        fun signature() = "${desc.ordinal}${format.ordinal}${size.ordinal}${gap.ordinal}${corners.ordinal}${if (glass) 1 else 0}${if (name) 1 else 0}${own.ordinal}${mood.ordinal}${worldAnim.ordinal}"
    }

    val DEFAULT = Options()

    /** As opções de um mundo: as do Ajuste fino com o jeito dos cards dele, se ele tiver um ([WorldOptions.Options.cards]). */
    fun doMundo(geral: Options, cards: Int?): Options =
        cards?.let { Format.entries.getOrNull(it) }?.let { geral.copy(format = it) } ?: geral

    /** Coluna e linha do card [i] (a grade de 2 linhas enche coluna por coluna). */
    fun coluna(i: Int, grade: Boolean) = if (grade) i / 2 else i
    fun linhaDaGrade(i: Int, grade: Boolean) = if (grade) i % 2 else 0
    fun colunas(n: Int, grade: Boolean) = if (grade) (n + 1) / 2 else n

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
            own = ler(KEY_OWN, Own.entries.toTypedArray(), DEFAULT.own),
            mood = ler(KEY_MOOD, Mood.entries.toTypedArray(), DEFAULT.mood),
            worldAnim = ler(KEY_WORLD_ANIM, WorldAnim.entries.toTypedArray(), DEFAULT.worldAnim),
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
    const val MS_VOO = 680L         // o ícone do mundo voando do trilho para a linha (e de volta): lento e macio (Paulo 25/11: "mais gentil")
    const val ESCALONA_MS = 40L     // os cards entram um depois do outro

    // ---- teclas

    /** Resultado de uma tecla: a tecla da cruz a aplicar (null = nada, som de borda) ou sair para a barra de cima. */
    data class Passo(val tecla: CruzLogica.Tecla?, val subirTopo: Boolean = false, val paraSel: Int? = null)

    /**
     * ◀ ▶ andam nos apps do mundo (sem dar a volta), ▲ ▼ trocam o mundo; ▲ no 1º mundo sobe para a barra de cima (Ilha).
     * Na cruz, mundo = coluna e app = linha da coluna: ◀ ▶ viram ▲ ▼ da cruz e ▲ ▼ viram ◀ ▶.
     * Grade de 2 linhas ([grade], [n] itens): o item i fica na coluna i/2, linha i%2. ◀ ▶ pulam de coluna ([Passo.paraSel]);
     * ▼ na linha de cima desce para a de baixo, ▲ na de baixo sobe; nas bordas, troca o mundo como sempre.
     */
    fun passo(t: CruzLogica.Tecla, cat: Int, sel: Int, n: Int = 0, grade: Boolean = false): Passo {
        if (grade && n > 0) {
            val linha = sel % 2
            when (t) {
                CruzLogica.Tecla.ESQUERDA -> return Passo(null, paraSel = (sel - 2).takeIf { it >= 0 })
                CruzLogica.Tecla.DIREITA -> {
                    val alvo = when {
                        sel + 2 < n -> sel + 2
                        (n - 1) / 2 > sel / 2 -> n - 1   // a última coluna só tem a linha de cima
                        else -> null
                    }
                    return Passo(null, paraSel = alvo)
                }
                CruzLogica.Tecla.BAIXO -> if (linha == 0 && sel + 1 < n) return Passo(null, paraSel = sel + 1)
                CruzLogica.Tecla.CIMA -> if (linha == 1) return Passo(null, paraSel = sel - 1)
                else -> {}
            }
        }
        return when (t) {
            CruzLogica.Tecla.ESQUERDA -> Passo(if (sel > 0) CruzLogica.Tecla.CIMA else null)
            CruzLogica.Tecla.DIREITA -> Passo(CruzLogica.Tecla.BAIXO)
            CruzLogica.Tecla.CIMA -> if (cat > 0) Passo(CruzLogica.Tecla.ESQUERDA) else Passo(null, subirTopo = true)
            CruzLogica.Tecla.BAIXO -> Passo(CruzLogica.Tecla.DIREITA)
            else -> Passo(t)
        }
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

    /**
     * A descrição do card em foco: o que ele tem agora ("**No ar** Jornal Hoje · faltam 25 min"); sem isso, o nome dele
     * em negrito e a linha de baixo dele ("**Netflix** Filmes e séries", "**Clima** 34° · Previsão").
     */
    fun doCard(i: Itens): String {
        i.situacao?.takeIf { it.texto.isNotBlank() }?.let { return "**${it.rotulo}** ${semGritar(it.texto)}".trim() }
        val sub = i.sub.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.equals(i.nome, ignoreCase = true) }
            .distinct().joinToString(" · ")
        return if (sub.isEmpty()) "**${i.nome}**" else "**${i.nome}** ${semGritar(sub)}"
    }

    /** O mundo inteiro (sem card em foco): a 1ª situação viva, ou o resumo do mundo. */
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
        // Por trechos (24/11/2026, visto na Sala: "ORAÇÃO DO DIA NADA FALTANDO... | Pastora Ana" passava gritando porque o
        // fim normal baixava a conta): cada sequência de palavras em CAIXA ALTA com 8+ letras vira título; o resto fica.
        val ws = s.split(" ")
        fun alta(w: String) = w.any { it.isLetter() } && w.none { it.isLowerCase() }
        fun neutra(w: String) = w.none { it.isLetter() }
        val saida = ws.toMutableList()
        var i = 0
        while (i < ws.size) {
            if (!alta(ws[i])) { i++; continue }
            var j = i
            while (j < ws.size && (alta(ws[j]) || neutra(ws[j]))) j++
            val letras = (i until j).sumOf { k -> ws[k].count { it.isLetter() } }
            if (letras >= 8 && (i until j).count { alta(ws[it]) } >= 2) for (k in i until j) {
                val w = ws[k].lowercase()
                // Palavra pequena fica minúscula só no meio da frase; depois de "-", "·", ":" ou "?" ela abre frase nova.
                val abre = k == i || ws[k - 1].lastOrNull()?.let { !it.isLetterOrDigit() } ?: true
                saida[k] = if (!abre && w in MIUDAS) w else w.replaceFirstChar { it.titlecase() }
            }
            i = j
        }
        return saida.joinToString(" ")
    }

    /** Palavras pequenas que ficam minúsculas no meio do título (pt, es, en, fr, it). */
    private val MIUDAS = setOf("e", "de", "da", "do", "das", "dos", "a", "o", "as", "os", "em", "no", "na", "y", "del", "la", "el",
        "and", "of", "the", "to", "in", "et", "le", "les", "des", "di", "il")

    /** Partes de um texto com ** ** (negrito) para quem desenha: (texto, negrito?). */
    fun partes(s: String): List<Pair<String, Boolean>> =
        s.split("**").mapIndexedNotNull { i, p -> if (p.isEmpty()) null else p to (i % 2 == 1) }
}
