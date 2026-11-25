package br.paulo.tvlauncher

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.paulo.tvlauncher.home.FundoEscuroPronto
import br.paulo.tvlauncher.home.HomeCruz
import br.paulo.tvlauncher.home.IconeApp
import br.paulo.tvlauncher.home.IconeLinha
import br.paulo.tvlauncher.home.SofalySymbol
import br.paulo.tvlauncher.home.SonsCruz
import br.paulo.tvlauncher.home.World
import br.paulo.tvlauncher.panel.entrar
import br.paulo.tvlauncher.player.JanelaIcones
import br.paulo.tvlauncher.settings.Fontes

/**
 * O sistema de design do launcher (10/11/2026, dono: "todo o launcher precisa ser conciso e coerente").
 * Proposta aprovada: https://claude.ai/artifact/3a6coAJtQT1s9E37Gb3JVw
 *
 * Regra de uso: tela nova (ou tela mexida) NÃO escreve cor, tamanho de letra, raio nem tempo de animação na mão.
 * Pede aqui: `Estilo.texto(ctx, Papel.TITULO, "Configurações")`, `Estilo.linhaOpcao(...)`, `Estilo.pilula(...)` etc.
 * Assim, mudar o visual do app inteiro é mudar este arquivo.
 *
 * Medidas em dp/sp da TV: a Sala tem 1920x1080 px com densidade 320, ou seja, 960 x 540 dp.
 */
object Estilo {

    // ------------------------------------------------------------------ cores
    /**
     * Alto contraste (elevação 16.3, 17/11/2026): texto secundário, bordas, acento e erro sobem para pelo menos 7:1
     * (WCAG AAA) sobre o fundo. Ligado em Configurações; vale para a tela aberta depois de ligar (as abertas redesenham
     * ao voltar). Lido de Extras.Chave.ALTO_CONTRASTE pelo [LauncherApp] e ao trocar a chave.
     */
    @Volatile var altoContraste = false

    // Formato 0xAARRGGBB: os dois primeiros dígitos são a opacidade (FF = sólido, 00 = invisível).

    /** Fundo de toda tela sem imagem atrás (era o fundo mais usado no código antigo). */
    const val FUNDO = DesignTokens.NIGHT
    /** Superfície de card/linha em tela lisa: branco a 6%. */
    const val SUPERFICIE = 0x0FFFFFFF
    /** Superfície um nível acima (linha focada sem virar pílula, chip ativo): branco a 12%. */
    const val SUPERFICIE_ALTA = 0x1FFFFFFF
    /** Fio de borda das superfícies: branco a 8%. */
    val BORDA: Int get() = if (altoContraste) 0x8CFFFFFF.toInt() else 0x14FFFFFF
    /** Vidro (sobre imagem): tinta escura translúcida + fio claro. Igual à pílula da HOME que o dono aprovou. */
    const val VIDRO_TINTA = 0x8C121528.toInt()
    const val VIDRO_BORDA = 0x38FFFFFF

    /** Texto: principal, explicação (70%) e rótulo apagado (48%). Nunca branco puro em bloco grande: cansa a vista. */
    const val TEXTO = DesignTokens.CREAM
    val TEXTO_2: Int get() = if (altoContraste) 0xFFFFF3E6.toInt() else 0xB3FFF3E6.toInt()
    val TEXTO_3: Int get() = if (altoContraste) 0xD9FFF3E6.toInt() else 0x86FFF3E6.toInt()   // era 0x7A: 4,4:1 nos cartões (pente fino 19/11/2026); 0x86 dá ≥ 4,7:1 em todo fundo da casa
    /**
     * Texto em cima do foco. Só o Quadro (foco branco) escurece o texto; no Fio e no Véu o fundo continua escuro,
     * então o texto focado fica creme (régua de estilo, 22/11/2026: no painel rápido ele sumia, escuro sobre escuro).
     */
    private val focoBranco: Boolean get() = br.paulo.tvlauncher.settings.FocusStyle.atual == br.paulo.tvlauncher.settings.FocusStyle.BOX
    val TEXTO_NO_FOCO: Int get() = if (focoBranco) DesignTokens.NIGHT else TEXTO
    /**
     * Texto em cima de um fundo SEMPRE branco ([FOCO]: pílula do alarme, anéis do painel rápido, ações da ficha), em
     * qualquer estilo de foco. O [TEXTO_NO_FOCO] é creme no Fio e no Véu: em cima do branco o botão ficava vazio
     * (teste 100%, Sala, 23/11/2026: "Parar" do timer e ações da ficha invisíveis).
     */
    const val TEXTO_SOBRE_FOCO = DesignTokens.NIGHT
    const val TEXTO_SOBRE_FOCO_2 = 0xA6121528.toInt()
    val TEXTO_NO_FOCO_2: Int get() = if (!focoBranco) TEXTO_2 else if (altoContraste) DesignTokens.NIGHT else 0xA6121528.toInt()

    /** O ÚNICO acento: "ligado", valor escolhido, progresso. Foco NÃO usa acento: foco é branco. */
    val ACENTO: Int get() = if (altoContraste) acentoAlto else acentoBase
    /** Acento legível sobre o foco branco. */
    val ACENTO_NO_FOCO: Int get() = if (!focoBranco) ACENTO else if (altoContraste && acentoBase == DesignTokens.ORANGE) 0xFF8A2900.toInt() else acentoNoFocoBase
    /** A cor de destaque escolhida no Ajuste fino (CardStyle.aplicar); laranja da marca de fábrica. */
    @Volatile var acentoBase = DesignTokens.ORANGE
    @Volatile var acentoAlto = DesignTokens.ORANGE_HIGH
    @Volatile var acentoNoFocoBase = DesignTokens.ORANGE_ON_LIGHT
    /** Semânticas: atenção (chuva, vencimento) e erro/ao vivo. Não contam como acento. */
    const val ATENCAO = DesignTokens.ATTENTION
    val ERRO: Int get() = if (altoContraste) 0xFFFFA08C.toInt() else DesignTokens.ERROR
    /** Foco: branco. A TV é vista a 3 m; o foco tem que ser a coisa mais óbvia da tela. */
    const val FOCO = DesignTokens.FOCUS

    // ------------------------------------------------------------------ tipografia (5 papéis, Nunito)

    /** Papel do texto: tamanho em sp, peso pedido (escala antiga 400-600; [Fontes.nunito] sobe um degrau) e cor. Caixa alta + espaçamento só na SECAO. */
    enum class Papel(val sp: Float, val peso: Int, private val nivel: Int, val caixaAlta: Boolean = false, val espaco: Float = 0f) {
        /** Hora, temperatura grande. */
        DISPLAY(56f, 200, 1),      // régua 22/11/2026: número fino (era 800)
        /** Título de tela, nome do item em destaque. */
        TITULO(32f, 300, 1),       // era 28, depois 30·600 (régua 22/11/2026: leve, sem negrito)
        /** Linhas, cards, botões. */
        CORPO(20f, 400, 1),        // era 16, depois 18·500 (régua 22/11/2026: 20 sp regular)
        /** Explicação, metadado. */
        LEGENDA(18f, 400, 2),      // era 13, 15, 17 (régua 22/11/2026: nada na TV abaixo de 18 sp)
        /** Nome de grupo ("PRÓXIMOS 9 DIAS"). */
        SECAO(18f, 400, 3);   // era 13·caixa alta, depois 15 (régua 22/11/2026: sem maiúsculas; o grupo se separa por tamanho e cor)

        /** A cor do papel na hora (o alto contraste, 16.3, muda TEXTO_2 e TEXTO_3). */
        val cor: Int get() = when (nivel) { 1 -> TEXTO; 2 -> TEXTO_2; else -> TEXTO_3 }
    }

    fun fonte(peso: Int): Typeface = Fontes.nunito(peso)

    /**
     * Filmes, Séries e TV ao vivo (13/11/2026: letra maior para ler a 3 m). São telas de LER, a 3 m do sofá, então
     * ficam um degrau acima de [Papel.CORPO]/[Papel.LEGENDA]: ~+15% nos títulos e +20-30% no texto corrido e nas listas.
     * As três telas usam estes mesmos números para continuarem iguais entre si.
     */
    object Leitura {
        // Ficha de filme/série
        const val TITULO = 32f          // era 28 (filme) / 26 (série)
        const val DADOS = 18f           // régua 22/11: mín. 18 (era 17); ano · duração · nota · gênero; era 14 / 13,5
        const val SINOPSE = 18f         // era 15 / 13,5
        const val CREDITOS = 18f        // régua 22/11: mín. 18 (era 16); Direção, Elenco; era 13
        const val AVISO = 18f           // régua 22/11: mín. 18 (era 15); "carregando…", "sem episódios"; era 13 / 14
        const val BOTAO = 18f           // Assistir, Trailer…; era 16
        const val BOTAO_PEQUENO = 18f   // régua 22/11: mín. 18 (era 16); temporadas; era 13,5
        // Linhas de lista: episódio, canal
        const val ITEM = 18f            // régua 22/11: mín. 18 (era 17); número e nome; era 14 / 14,5
        const val ITEM_DETALHE = 18f    // régua 22/11: mín. 18 (era 15); sinopse do episódio, programa do canal; era 12
        // Coluna de categorias e cabeçalhos
        const val CATEGORIA = 18f       // régua 22/11: mín. 18 (era 16); era 14,5
        const val CONTAGEM = 18f        // régua 22/11: mín. 18 (era 15); número ao lado da categoria; era 13,5 (19/11/2026: mínimo 15)
        const val ROTULO = 18f          // régua 22/11: mín. 18 (era 15); "CATEGORIAS", "A SEGUIR"; era 13 (19/11/2026: mínimo 15)
        const val DICA = 18f            // régua 22/11: mín. 18 (era 15); linha de teclas no rodapé; era 12,5 (19/11/2026: mínimo 15)
        // Grade de pôsteres e painel do canal
        const val CARD = 18f            // régua 22/11: mín. 18 (era 15); nome embaixo do pôster; era 14,5 (19/11/2026: mínimo 15)
        // Pergunta grande do "Cuidar de longe" (15/11/2026): para quem lê com dificuldade, de longe, às vezes com pressa.
        const val PERGUNTA = 40f        // "Mandar pedido de ajuda para Ana?"
        const val PERGUNTA_TEXTO = 24f  // a explicação embaixo
        const val PERGUNTA_BOTAO = 26f  // Mandar / Desistir, Feito / Depois
        const val PERGUNTA_MENOR = 32f  // pergunta com lista embaixo (consentimento): ainda o TITULO da ficha
        const val PERGUNTA_ITEM = 20f   // itens da lista do consentimento
        const val COLUNA_CATEGORIAS = 230   // largura (dp) da coluna esquerda do player (14/11/2026; era 210)
        const val GRADE_COLUNAS = 5     // pôsteres por linha em Filmes e Séries; eram 6 (pôster ~20% maior)
        const val POSTER_ALTURA = 180   // dp; era 150 (mantém a proporção 2:3 com 5 colunas)
        const val FOCADO = 18f          // régua 22/11: mín. 18 (era 16); linha do item em foco acima da grade; era 14
        const val PROGRAMA = 22f        // título do programa no detalhe do canal; era 19
    }

    // ------------------------------------------------------------------ forma, espaço, movimento

    /** Só 3 raios: chip pequeno, card/painel e pílula (botão, foco de lista, avisos). */
    /** Cantos do Ajuste fino (CardStyle.Corners): multiplica os raios abaixo. A pílula continua pílula. */
    @Volatile var escalaRaio = 1f
    val RAIO_PEQUENO: Int get() = br.paulo.tvlauncher.settings.CardStyle.raio(DesignTokens.RADIUS_SMALL, escalaRaio)
    val RAIO: Int get() = br.paulo.tvlauncher.settings.CardStyle.raio(DesignTokens.RADIUS_MEDIUM, escalaRaio)
    const val RAIO_PILULA = DesignTokens.RADIUS_PILL

    /** Grade de 8 dp. */
    const val E1 = 8
    const val E2 = 16
    const val E3 = 24
    const val E6 = 48
    /** Margem segura da TV (algumas cortam a borda da imagem). */
    const val MARGEM_LATERAL = 48
    const val MARGEM_VERTICAL = 27

    /** Só 2 tempos, os mesmos do painel e do celular (design/tokens.json, escala do transitions.dev): foco 150 ms (o controle
     *  tem que parecer instantâneo) e entrar/sair 250 ms, sempre com a curva smooth-out (começa rápido, pousa macio). */
    const val FOCO_MS = DesignTokens.MS_QUICK
    const val TRANSICAO_MS = DesignTokens.MS_FAST
    private val desacelera = DesignTokens.EASE_OUT.let { PathInterpolator(it[0], it[1], it[2], it[3]) }

    /** Quanto cresce o que ganha foco: card 8%, linha de lista 2%. */
    const val ESCALA_CARD = 1.05f   // era 1,08 (régua 22/11/2026: nada "pula" na tela)
    const val ESCALA_LINHA = 1f      // era 1,02: linha de lista não cresce; o foco basta

    /** Véu do foco [FocusStyle.VEIL]: branco a 10% com fio branco a 38%. */
    const val VEU_TINTA = 0x1AFFFFFF
    const val VEU_BORDA = 0x61FFFFFF
    /** Anel do foco [FocusStyle.RING]: a espessura do anel da barra (StripHome.ANEL) e um véu laranja a 12%. */
    const val ANEL_DP = 3
    const val ANEL_TINTA = 0x1FFF6A2B
    /** Fio que separa as opções de uma lista (régua: separar por fio, não por caixa): branco a 12%. */
    const val FIO = 0x1FFFFFFF

    /** O jeito de foco escolhido (Ajuste fino > Foco). Lido a cada mudança de foco: trocar vale na hora. */
    fun focoAtual(ctx: Context): br.paulo.tvlauncher.settings.FocusStyle = br.paulo.tvlauncher.settings.FocusStyle.ler(ctx)

    /**
     * O traço do foco [FocusStyle.LINE] (Paulo, 22/11/2026): laranja, VERTICAL (horizontal parecia barra de progresso),
     * curto (30% da altura, entre 10 e 14 dp; 22/11 00:30 "um tiquinho menor e mais gordinho": 4 dp de largura) e de pontas redondas, encostado à esquerda do item.
     */
    fun traco(ctx: Context, cor: Int = ACENTO): Drawable = Traco(dp(ctx, 4).toFloat(), dp(ctx, 6).toFloat(), dp(ctx, 10).toFloat(), dp(ctx, 14).toFloat(), cor)

    private class Traco(val larg: Float, val margem: Float, val minA: Float, val maxA: Float, cor: Int) : Drawable() {
        private val tinta = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = cor }
        private val r = android.graphics.RectF()
        override fun draw(c: android.graphics.Canvas) {
            val b = bounds
            val a = (b.height() * 0.3f).coerceIn(minA, maxA)
            val x = b.left + margem
            r.set(x, b.exactCenterY() - a / 2f, x + larg, b.exactCenterY() + a / 2f)
            c.drawRoundRect(r, larg / 2f, larg / 2f, tinta)
        }
        override fun setAlpha(alpha: Int) { tinta.alpha = alpha }
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** Fio de 1 dp embaixo da linha de lista, com recuo dos lados (régua: opção sem caixa, separada por fio). */
    fun fioBaixo(ctx: Context): Drawable = FioBaixo(dp(ctx, 1).toFloat(), dp(ctx, E2).toFloat())

    private class FioBaixo(val esp: Float, val recuo: Float) : Drawable() {
        private val tinta = android.graphics.Paint().apply { color = FIO }
        override fun draw(c: android.graphics.Canvas) {
            val b = bounds
            c.drawRect(b.left + recuo, b.bottom - esp, b.right - recuo, b.bottom.toFloat(), tinta)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /**
     * O fundo de um item focado, no jeito escolhido. [base] = o fundo que o item tem sem foco (no Fio ele fica, com o
     * traço por cima; no Véu e no Quadro o foco o substitui).
     */
    fun fundoFocado(ctx: Context, raioDp: Int, base: Drawable? = null): Drawable = when (focoAtual(ctx)) {
        br.paulo.tvlauncher.settings.FocusStyle.BOX -> fundo(ctx, FOCO, raioDp)
        br.paulo.tvlauncher.settings.FocusStyle.VEIL -> fundo(ctx, VEU_TINTA, raioDp, VEU_BORDA)
        br.paulo.tvlauncher.settings.FocusStyle.LINE ->
            if (base == null) traco(ctx) else android.graphics.drawable.LayerDrawable(arrayOf(base, traco(ctx)))
        // Anel laranja de 3 dp, como o da barra da HOME, sobre um véu laranja bem leve (o texto continua claro).
        br.paulo.tvlauncher.settings.FocusStyle.RING -> fundo(ctx, ANEL_TINTA, raioDp, ACENTO, ANEL_DP)
    }

    /** O foco escurece o texto? Só no Quadro (texto escuro sobre branco); no Fio e no Véu o texto acende para o creme pleno. */
    fun focoEscurece(ctx: Context): Boolean = focoAtual(ctx) == br.paulo.tvlauncher.settings.FocusStyle.BOX

    /**
     * Cor do texto focado. No Fio, o texto ganha uma sombra difusa (6 dp, preto a 45%): some no fundo escuro e segura a
     * leitura quando o fundo atrás é claro, o ponto fraco do Fio (coordenador, 22/11/2026).
     */
    private fun pintarFocado(t: TextView, foco: Boolean, escura: Int) {
        val ctx = t.context
        if (focoEscurece(ctx)) { if (foco) t.setTextColor(escura); return }
        if (foco) t.setTextColor(TEXTO)
        if (focoAtual(ctx) == br.paulo.tvlauncher.settings.FocusStyle.LINE) {
            if (foco) t.setShadowLayer(dp(ctx, 6).toFloat(), 0f, 0f, 0x73000000) else t.setShadowLayer(0f, 0f, 0f, 0)
        }
    }

    /**
     * Matiz escuro de uma cor (dono, 10/11/2026, decisão 3): mistura [cor] com o [FUNDO], ficando só [quanto] da cor.
     * Os cards coloridos da HOME mantêm a identidade (TV coral, Séries roxo…) sem brigar com banners e com a pintura.
     */
    fun matiz(cor: Int, quanto: Float = 0.42f): Int {
        fun canal(desloc: Int) = ((cor shr desloc and 0xFF) * quanto + (FUNDO shr desloc and 0xFF) * (1 - quanto)).toInt()
        return (0xFF shl 24) or (canal(16) shl 16) or (canal(8) shl 8) or canal(0)
    }

    /** A mesma cor com outra opacidade ([a] de 0 a 255): o véu escuro do player é o [FUNDO] a 80%, por exemplo. */
    fun alfa(cor: Int, a: Int): Int = (a.coerceIn(0, 255) shl 24) or (cor and 0xFFFFFF)

    fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    // ------------------------------------------------------------------ peças básicas

    /** Um TextView já no papel certo (tamanho, peso, cor). [conteudo] pode ficar vazio e ser trocado depois. */
    fun texto(ctx: Context, papel: Papel, conteudo: CharSequence = ""): TextView = TextView(ctx).apply {
        aplicar(this, papel)
        text = conteudo
    }

    /** Põe um TextView que já existe no papel (para telas antigas migrarem aos poucos). */
    fun aplicar(t: TextView, papel: Papel) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, papel.sp)
        t.typeface = fonte(papel.peso)
        t.setTextColor(papel.cor)
        t.isAllCaps = papel.caixaAlta
        t.letterSpacing = papel.espaco
        t.includeFontPadding = false   // sem o "colchão" de cima e de baixo da fonte: o espaço fica por conta da grade
    }

    /** Letra de mostrador (o relógio do descanso, 18-96 sp): fora da escala dos [Papel], que é para telas de ler. */
    fun letraMostrador(t: TextView, sp: Float) = t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)

    /** O dourado do pôr do sol (régua do descanso). */
    const val OURO = 0xFFFFC27A.toInt()

    /** Fundo arredondado. [borda] = cor do fio (0 = sem fio). */
    fun fundo(ctx: Context, cor: Int, raioDp: Int = RAIO, borda: Int = 0, espessuraDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(cor)
            cornerRadius = dp(ctx, raioDp).toFloat()
            if (borda != 0) setStroke(dp(ctx, espessuraDp), borda)
        }

    /** Vidro sem desfoque (o desfoque de verdade só existe em janela; ver [AvisoTv]): tinta escura + fio claro. */
    fun vidro(ctx: Context, raioDp: Int = RAIO): GradientDrawable = fundo(ctx, VIDRO_TINTA, raioDp, VIDRO_BORDA)

    // ------------------------------------------------------------------ foco (UMA regra no app inteiro)

    /**
     * Foco de CARD: cresce 8%, ganha anel branco de 3 dp e sobe (sombra). [anel] desenha por cima do card, então serve
     * para card com imagem (banner de app). Chame uma vez por card.
     */
    fun focoCard(v: View) {
        val ctx = v.context
        v.setOnFocusChangeListener { view, foco ->
            val jeito = focoAtual(ctx)
            view.foreground = if (!foco) null else when (jeito) {
                br.paulo.tvlauncher.settings.FocusStyle.BOX -> fundo(ctx, 0, RAIO + 3, FOCO, 3)
                br.paulo.tvlauncher.settings.FocusStyle.VEIL -> fundo(ctx, VEU_TINTA, RAIO + 1, VEU_BORDA)
                br.paulo.tvlauncher.settings.FocusStyle.LINE -> traco(ctx)
                br.paulo.tvlauncher.settings.FocusStyle.RING -> fundo(ctx, 0, RAIO + ANEL_DP, ACENTO, ANEL_DP)
            }
            view.animate().scaleX(if (foco) ESCALA_CARD else 1f).scaleY(if (foco) ESCALA_CARD else 1f)
                .translationZ(if (foco && jeito == br.paulo.tvlauncher.settings.FocusStyle.BOX) dp(ctx, 8).toFloat() else 0f)
                .setDuration(FOCO_MS).setInterpolator(desacelera).start()
        }
    }

    /**
     * Foco de LINHA de lista: a linha vira pílula branca e os textos dela escurecem. [textos] = os TextViews da linha
     * com o papel de cada um, para a cor voltar certa quando o foco sai.
     */
    fun focoLinha(v: View, vararg textos: Pair<TextView, Papel>) {
        val ctx = v.context
        // Régua 22/11/2026: a opção não tem caixa; um fio embaixo separa uma da outra.
        val normal = fioBaixo(ctx)
        v.background = normal
        v.setOnFocusChangeListener { view, foco ->
            view.background = if (foco) fundoFocado(ctx, RAIO, normal) else normal
            for ((t, papel) in textos) {
                if (!foco) t.setTextColor(t.getTag(R.id.estilo_cor_normal) as? Int ?: papel.cor)
                pintarFocado(t, foco, when {
                    papel == Papel.LEGENDA || papel == Papel.SECAO -> TEXTO_NO_FOCO_2
                    t.getTag(R.id.estilo_cor_normal) == ACENTO -> ACENTO_NO_FOCO
                    else -> TEXTO_NO_FOCO
                })
                // Legenda e explicação continuam um tom abaixo do título no Fio e no Véu (só o título acende).
                if (foco && !focoEscurece(ctx) && (papel == Papel.LEGENDA || papel == Papel.SECAO)) t.setTextColor(TEXTO_2)
            }
            view.animate().scaleX(if (foco) ESCALA_LINHA else 1f).scaleY(if (foco) ESCALA_LINHA else 1f)
                .setDuration(FOCO_MS).setInterpolator(desacelera).start()
        }
    }

    // ------------------------------------------------------------------ componentes

    /** Nome de grupo, em caixa alta e apagado ("CLIMA", "PRÓXIMOS 9 DIAS"). */
    fun secao(ctx: Context, nome: String): TextView = texto(ctx, Papel.SECAO, nome).apply {
        setPaddingRelative(dp(ctx, 4), dp(ctx, E3), 0, dp(ctx, E1))
    }

    /**
     * Linha de opção, a peça de toda lista (Configurações, Extras, Rotinas…): título, explicação embaixo e, à direita,
     * [direita] (valor "Escuro ›", um interruptor ou nada). Já vem focável, com o foco padrão.
     */
    fun linhaOpcao(ctx: Context, titulo: String, explicacao: String? = null, direita: View? = null): LinearLayout {
        val t = texto(ctx, Papel.CORPO, titulo)
        val e = explicacao?.let { texto(ctx, Papel.LEGENDA, it).apply { setPaddingRelative(0, dp(ctx, 4), 0, 0) } }
        val coluna = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(t)
            if (e != null) addView(e)
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ctx, 56)
            setPaddingRelative(dp(ctx, E2 + 4), dp(ctx, 12), dp(ctx, E2 + 4), dp(ctx, 12))
            isFocusable = true
            isClickable = true
            addView(coluna, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (direita != null) addView(direita, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(ctx, E2) })
            val pares = mutableListOf(t to Papel.CORPO)
            if (e != null) pares += e to Papel.LEGENDA
            if (direita is TextView) pares += direita to Papel.CORPO
            focoLinha(this, *pares.toTypedArray())
        }
    }

    /**
     * O valor à direita de uma linha ("Escuro ›"), em branco suave. Era laranja; a crítica de design (19/11/2026) deixou
     * o laranja só para marca, foco e progresso, e valor não é nenhum dos três.
     */
    fun valor(ctx: Context, texto: String, seta: Boolean = true): TextView =
        texto(ctx, Papel.CORPO, if (seta) "$texto  ›" else texto).apply {
            setTextColor(TEXTO_2); setTag(R.id.estilo_cor_normal, TEXTO_2)
        }

    /**
     * Pílula: o chip do topo da HOME (clima, despertador, timer, "diga o que quer ver"), que o dono aprovou
     * (10/11/2026: "gosto dela"). Vidro escuro com fio claro; com foco, fica branca e o texto escurece.
     * [icone] opcional, 20 dp. Os textos vão em [addView] depois, como num LinearLayout comum.
     */
    /** [vidro] = false: sem fundo próprio (pílula dentro de uma cápsula de vidro, como no topo da HOME); o foco é o mesmo. */
    fun pilula(ctx: Context, icone: Drawable? = null, focavel: Boolean = true, vidro: Boolean = true): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPaddingRelative(dp(ctx, 12), 0, dp(ctx, 14), 0)
        minimumHeight = dp(ctx, 32)
        if (icone != null) addView(ImageView(ctx).apply { setImageDrawable(icone) },
            LinearLayout.LayoutParams(dp(ctx, 20), dp(ctx, 20)).apply { marginEnd = dp(ctx, 8) })
        val normal = if (vidro) vidro(ctx, RAIO_PILULA) else null
        background = normal
        if (focavel) {
            isFocusable = true
            isClickable = true
            setOnFocusChangeListener { v, foco ->
                v.background = if (foco) fundoFocado(ctx, RAIO_PILULA, normal) else normal
                tingir(v as ViewGroup, if (foco) (if (focoEscurece(ctx)) TEXTO_NO_FOCO else TEXTO) else null)
                v.animate().scaleX(if (foco) ESCALA_LINHA else 1f).scaleY(if (foco) ESCALA_LINHA else 1f)
                    .setDuration(FOCO_MS).setInterpolator(desacelera).start()
            }
        }
    }

    /** Cor de texto que escurece sozinha com o foco (para botões com fundo StateListDrawable: foco = branco). */
    fun textoFoco(): android.content.res.ColorStateList = android.content.res.ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(TEXTO_NO_FOCO, TEXTO))

    /** Superfície de linha/botão de lista: repouso = branco 6% com fio; foco = branco sólido (use com [focarLinha]). */
    fun superficie(ctx: Context, focado: Boolean): Drawable {
        val normal = fundo(ctx, SUPERFICIE, RAIO, BORDA)
        return if (focado) fundoFocado(ctx, RAIO, normal) else normal
    }

    /**
     * Foco de linha para telas que montam a linha do seu jeito: troca o fundo ([superficie]) e escurece TODOS os textos
     * e ícones de linha de dentro dela (guardando a cor de cada um para devolver). Chame no setOnFocusChangeListener.
     */
    fun focarLinha(v: View, foco: Boolean) {
        v.background = superficie(v.context, foco)
        val cor = if (focoEscurece(v.context)) TEXTO_NO_FOCO else TEXTO
        if (v is ViewGroup) tingir(v, if (foco) cor else null)
        else if (v is TextView) {
            if (v.getTag(R.id.estilo_cor_normal) == null) v.setTag(R.id.estilo_cor_normal, v.currentTextColor)
            v.setTextColor(if (foco) cor else v.getTag(R.id.estilo_cor_normal) as Int)
        }
    }

    /** Pinta textos e ícones de dentro de [g] com [cor]; null = volta cada um à cor que tinha. */
    private fun tingir(g: ViewGroup, cor: Int?) {
        for (i in 0 until g.childCount) when (val f = g.getChildAt(i)) {
            is TextView -> {
                if (f.getTag(R.id.estilo_cor_normal) == null) f.setTag(R.id.estilo_cor_normal, f.currentTextColor)
                f.setTextColor(cor ?: f.getTag(R.id.estilo_cor_normal) as Int)
            }
            // Só ícone de linha muda de cor; ilustração (o sol 3D do clima) fica como está.
            is ImageView -> if (f.drawable is IconeLinha || f.drawable is IconeApp) {
                if (cor != null) f.setColorFilter(cor) else f.clearColorFilter()
            }
            is ViewGroup -> tingir(f, cor)
        }
    }

    /** Botão: pílula sólida (primário) ou translúcida (secundário). Foco = branco + cresce. */
    fun botao(ctx: Context, rotulo: String, primario: Boolean = true): TextView = texto(ctx, Papel.CORPO, rotulo).apply {
        gravity = Gravity.CENTER
        setPaddingRelative(dp(ctx, E3), dp(ctx, 10), dp(ctx, E3), dp(ctx, 10))
        isFocusable = true
        isClickable = true
        val normal = fundo(ctx, if (primario) SUPERFICIE_ALTA else SUPERFICIE, RAIO_PILULA, BORDA)
        background = normal
        setOnFocusChangeListener { v, foco ->
            v.background = if (foco) fundoFocado(ctx, RAIO_PILULA, normal) else normal
            (v as TextView).setTextColor(TEXTO)
            pintarFocado(v, foco, TEXTO_NO_FOCO)
            v.animate().scaleX(if (foco) ESCALA_CARD else 1f).scaleY(if (foco) ESCALA_CARD else 1f)
                .setDuration(FOCO_MS).setInterpolator(desacelera).start()
        }
    }

    /**
     * Barra de dicas do controle, embaixo, em toda tela que não é a HOME: pares tecla → ação.
     * Ex.: `barraDicas(ctx, "▲▼" to "escolher", "OK" to "mudar", "VOLTAR" to "sair")`.
     */
    /**
     * A barra de dicas do controle (tecla numa pílula + o que ela faz). ÚNICA no sistema (crítica de design 19/11/2026):
     * as telas de ajustes, a cruz (Rádio, Jogos, Despertador), as listas e o menu usam esta mesma peça, com o mesmo
     * tamanho, cor e espaço. Antes havia 4 desenhos diferentes.
     */
    fun barraDicas(ctx: Context, vararg dicas: Pair<String, String>): ViewGroup = LinhaQuebravel(ctx, dp(ctx, 20), dp(ctx, 8)).apply {
        // Estudo global 20/11 (ideia 5): a barra de dicas ocupava a tela para sempre (Paulo já tinha tirado a de Filmes).
        // Por quê: depois de aprender as teclas, a dica é só ruído. Para quê: a tela fica com o que importa. Como: cada
        // tela mostra as dicas nas DICAS_VEZES primeiras aberturas e depois some (a ajuda continua em Configurações > Ajuda).
        if (!dicaAindaAjuda(ctx)) visibility = View.GONE
        for ((tecla, acao) in dicas) addView(LinearLayout(ctx).apply {
            // tecla + ação juntas: numa linha que não cabe, a dupla quebra inteira (nunca a tecla de um lado e a ação do outro)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(ctx).apply {
                // Régua 22/11/2026: a tecla sem moldura e sem negrito; só um tom acima da ação.
                text = tecla; setTextSize(TypedValue.COMPLEX_UNIT_SP, LISTA_FONTE_DICA); typeface = fonte(400); setTextColor(TEXTO_2)
                includeFontPadding = false
            })
            addView(TextView(ctx).apply {
                text = acao; setTextSize(TypedValue.COMPLEX_UNIT_SP, LISTA_FONTE_DICA); typeface = fonte(400); setTextColor(TEXTO_3)
                includeFontPadding = false; setShadowLayer(dp(ctx, 6).toFloat(), 0f, 0f, 0x99000000.toInt())
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(ctx, 8) })
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /**
     * Dicas escritas numa frase só ("OK abrir · segure OK: mais opções · VOLTAR categorias") viram a [barraDicas] padrão:
     * cada trecho separado por " · " vira tecla + ação (antes do ":" é a tecla; sem ":", a primeira palavra).
     */
    /** Quantas vezes cada tela mostra as dicas antes de sumir. */
    const val DICAS_VEZES = 3
    private val dicasContadas = java.util.WeakHashMap<Context, Boolean>()

    /** Conta uma abertura por tela (o mesmo Context redesenhando não conta de novo). Testes de tela: sempre mostram. */
    fun dicaAindaAjuda(ctx: Context): Boolean {
        val tela = ctx.javaClass.name
        val p = ctx.getSharedPreferences("dicas_vistas", Context.MODE_PRIVATE)
        val vistas = p.getInt(tela, 0)
        if (dicasContadas.put(ctx, true) == null) p.edit().putInt(tela, vistas + 1).apply()
        return vistas < DICAS_VEZES
    }

    fun dicasDeTexto(ctx: Context, frase: String): ViewGroup = barraDicas(ctx, *paresDeDicas(frase).toTypedArray())

    /** A parte pura de [dicasDeTexto] (testável sem Android). */
    fun paresDeDicas(frase: String): List<Pair<String, String>> = frase.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }.map { t ->
        val dois = t.indexOfFirst { it == ':' || it == '：' }
        if (dois > 0) t.substring(0, dois).trim() to t.substring(dois + 1).trim()
        else t.indexOf(' ').let { e -> if (e > 0) t.substring(0, e) to t.substring(e + 1).trim() else t to "" }
    }

    /**
     * Estado vazio ou de erro: ícone, o que aconteceu ([titulo]), o que a TV já está fazendo ou o que fazer ([texto]) e
     * até dois botões. O foco cai no primeiro botão (quem chama faz requestFocus nele se quiser). Nunca "Erro" sozinho.
     */
    fun estadoVazio(ctx: Context, icone: Drawable?, titulo: String, texto: String, vararg botoes: TextView): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPaddingRelative(dp(ctx, E6), dp(ctx, E6), dp(ctx, E6), dp(ctx, E6))
            // Estudo global 20/11 (ideia 6): o mascote fala nos erros e vazios. Por quê: um erro era uma frase solta no
            // canto e parecia que a TV tinha quebrado. Para quê: a pessoa vê que o Sofaly sabe o que houve e mostra a saída.
            // Como: a TV-sofá (o mesmo desenho do logo) em cima; o ícone do assunto, quando há, fica pequeno ao lado dela.
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                addView(ImageView(ctx).apply { setImageDrawable(SofalySymbol()); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
                    LinearLayout.LayoutParams(dp(ctx, 96), dp(ctx, 90)))
                if (icone != null) addView(ImageView(ctx).apply { setImageDrawable(icone); alpha = 0.7f },
                    LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36)).apply { marginStart = dp(ctx, E1) })
            // MATCH_PARENT de propósito: se esta linha tivesse largura fixa, o LinearLayout "largura do conteúdo" igualaria
            // os textos (que são MATCH_PARENT) à largura do mascote, e o título ficava cortado em 96 dp.
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(ctx, E2) })
            addView(texto(ctx, Papel.TITULO, titulo).apply { textSize = 26f; gravity = Gravity.CENTER })
            addView(texto(ctx, Papel.LEGENDA, texto).apply {
                gravity = Gravity.CENTER; maxWidth = dp(ctx, 520); textSize = Papel.CORPO.sp   // 18 sp: recado do sofá
                setLineSpacing(0f, 1.25f); setPaddingRelative(0, dp(ctx, E1 + 2), 0, dp(ctx, E3))
            // Largura do conteúdo: assim o maxWidth (linha curta, fácil de ler a 3 m) vale; em MATCH_PARENT ele é ignorado.
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            clipChildren = false; clipToPadding = false   // o botão cresce no foco: sem isso a borda de baixo saía cortada
            if (botoes.isNotEmpty()) addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false; clipToPadding = false
                botoes.forEachIndexed { i, b ->
                    addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { if (i > 0) marginStart = dp(ctx, E2) })
                }
            })
        }

    /**
     * Título de tela, com a margem segura da TV. Uma só altura e um só tamanho em todas as telas
     * (antes: Roboto Light 34 sp nas Configurações, nada na Previsão, 24 sp em outras).
     */
    /** Marca do título de tela ([tituloTela]): a Moldura o acha para montar o cabeçalho da barra única. */
    const val TAG_TITULO = "estilo-titulo-tela"

    fun tituloTela(ctx: Context, titulo: String): TextView = texto(ctx, Papel.TITULO, titulo).apply {
        maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        tag = TAG_TITULO
        // Título sempre à esquerda, como no inglês: em árabe o texto se alinharia à direita e ficaria por baixo do
        // relógio do canto (o app não espelha o layout; o manifest não declara supportsRtl, e por isso o Android ignora
        // textAlignment: só a gravidade ABSOLUTA, LEFT em vez de START, segura o alinhamento).
        gravity = (gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK) or android.view.Gravity.START
    }

    /** Entrada suave de uma tela/painel: some → aparece em [TRANSICAO_MS], subindo 12 dp. */
    fun entrar(v: View) {
        v.alpha = 0f; v.translationY = dp(v.context, 12).toFloat()
        v.animate().alpha(1f).translationY(0f).setDuration(TRANSICAO_MS).setInterpolator(desacelera).start()
    }

    /** Fundo padrão de tela lisa: [FUNDO] com um brilho azulado bem suave no alto à esquerda (como a HOME Escuro). */
    /**
     * Fundo de TODA tela que não é a HOME (10/11/2026, dono: "tem menu fora de padrão"): o MESMO grafite da HOME no tema
     * Escuro (Fundos.kt, desenharFundoEscuro), em vez da textura pontilhada antiga (drawable fundo.jpg) e dos cinzas
     * soltos de cada tela. Telas de lista ficam em fundo liso mesmo no tema Galeria (decisão 1: vidro só com imagem atrás).
     */
    fun fundoTela(ctx: Context): Drawable =
        // Alto contraste (crítica de design 19/11/2026): fundo chapado preto, sem brilho nem degradê atrás do texto.
        // Com a barra única, a foto da barra borrada (GlassBackground, coerência das telas 24/11/2026); senão o grafite.
        if (altoContraste) android.graphics.drawable.ColorDrawable(0xFF000000.toInt())
        else br.paulo.tvlauncher.home.GlassBackground.of(ctx) ?: FundoGrafite(World.of(ctx))

    /**
     * Fundo das telas de ENTRADA (Configurações, Rádio, Podcasts, Jogos): no jeito Barra (ScreenStyle.STRIP) é a foto
     * nítida da HOME escurecendo embaixo, atrás da fileira; nos outros jeitos, o mesmo de [fundoTela].
     */
    fun fundoEntrada(ctx: Context): Drawable =
        (if (br.paulo.tvlauncher.home.ScreenStyle.barra(ctx)) br.paulo.tvlauncher.home.GlassBackground.strip(ctx) else null) ?: fundoTela(ctx)

    /**
     * Tela cuja raiz já pinta o grafite na tela toda: a cor de fundo da janela (tema) por baixo é uma camada de tela
     * cheia a mais em cada quadro, que ninguém vê (13/11/2026, medido na Sala com "mostrar sobreposição"). Tira essa cor.
     * Chamado para toda tela pelo LauncherApp (onActivityStarted); só age quando a raiz é o grafite e ocupa a janela.
     */
    fun tirarCorDaJanelaSeCoberta(a: android.app.Activity) {
        val conteudo = a.findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        val raiz = conteudo.getChildAt(0) ?: return
        val lp = raiz.layoutParams ?: return
        if ((raiz.background !is FundoGrafite && raiz.background !is br.paulo.tvlauncher.home.GlassDrawable) || lp.width != android.view.ViewGroup.LayoutParams.MATCH_PARENT ||
            lp.height != android.view.ViewGroup.LayoutParams.MATCH_PARENT) return
        a.window.setBackgroundDrawable(null)
    }

    private class FundoGrafite(private val mundo: World) : Drawable() {
        override fun draw(c: android.graphics.Canvas) {
            c.save(); c.translate(bounds.left.toFloat(), bounds.top.toFloat())
            FundoEscuroPronto.desenhar(c, bounds.width().toFloat(), bounds.height().toFloat(), mundo)   // uma imagem pronta (Fundos.kt)
            c.restore()
        }
        override fun setAlpha(alpha: Int) {}   // opaco sempre (ninguém usa alfa neste fundo)
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.OPAQUE
    }

    /**
     * Molde de tela de lista (um só para o app inteiro): fundo da HOME, margem da HOME, título no tamanho de título e a
     * coluna onde a tela põe as linhas. Devolve (raiz para o setContentView, coluna).
     */
    fun telaLista(ctx: Context, titulo: String, subtitulo: String? = null, dicas: Boolean = true): Pair<View, LinearLayout> {
        // Título e subtítulo fixos; só as linhas rolam (12/11/2026, moldura da HOME): rolando tudo, as linhas passavam por
        // baixo da hora da moldura (Moldura.kt), que fica parada no alto à direita.
        val raiz = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = fundoTela(ctx)
            // clipChildren = true: as linhas roladas ficam dentro da rolagem e não passam por cima do título (15/11/2026).
            clipChildren = true; clipToPadding = false
            setPaddingRelative(dp(ctx, MARGEM_LATERAL), dp(ctx, MARGEM_VERTICAL + 9), dp(ctx, MARGEM_LATERAL), 0)
            addView(tituloTela(ctx, titulo))
            if (subtitulo != null) addView(texto(ctx, Papel.LEGENDA, subtitulo), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(ctx, 4) })
        }
        val coluna = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            // Folga de cima (E2) dentro da rolagem: a linha focada cresce 2% e não pode ser cortada pela borda dela.
            setPaddingRelative(0, dp(ctx, E2), 0, dp(ctx, MARGEM_VERTICAL + 16))
        }
        raiz.addView(android.widget.ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; isFillViewport = true
            // Sem foco próprio: o foco vai direto na 1ª linha (no teste de telas ele parava na rolagem e nada aparecia focado).
            isFocusable = false
            // Esmaecer nas pontas (crítica de design 19/11/2026): quando há mais linhas acima/abaixo, a borda some em
            // degradê em vez de cortar a linha seca; o Android só desenha o degradê do lado que ainda tem o que rolar.
            isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(dp(ctx, 48))
            // Folga E2 dos lados (padding aqui, margem negativa abaixo): as linhas ficam no mesmo lugar, e a focada,
            // 2% maior, ainda cabe no recorte da raiz.
            setPaddingRelative(dp(ctx, E2), 0, dp(ctx, E2), 0)
            addView(coluna)
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { marginStart = -dp(ctx, E2); marginEnd = -dp(ctx, E2) })
        // Regra das telas (23/11/2026): toda lista termina com a mesma barra de dicas do controle, parada embaixo.
        // Quem tem teclas próprias (teclado, teste de teclas, a home simples) passa dicas = false e põe a sua.
        if (dicas) raiz.addView(barraDicas(ctx, "▲▼" to ctx.getString(R.string.cl_dica_escolher),
            "OK" to ctx.getString(R.string.cl_dica_mudar), ctx.getString(R.string.cl_tecla_voltar) to ctx.getString(R.string.cl_dica_sair)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, E1); bottomMargin = dp(ctx, MARGEM_VERTICAL) })
        return raiz to coluna
    }

    /**
     * Linha de texto focável, a mesma das Configurações: superfície clara, foco = pílula branca com texto escuro e um
     * leve aumento. Para telas que só têm uma frase por linha (Extras, Casa, Calibração...).
     */
    fun linhaTexto(ctx: Context, conteudo: CharSequence = ""): TextView = texto(ctx, Papel.CORPO, conteudo).apply {
        isFocusable = true; isClickable = true
        setPaddingRelative(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 14))
        // Régua 22/11/2026, como a linha das Configurações: sem caixa, um fio embaixo; o foco segue Ajuste fino > Foco
        // (teste 100%, 23/11/2026: Extras e as telas desta linha ainda tinham a caixa antiga).
        background = fioBaixo(ctx)
        setOnFocusChangeListener { v, foco ->
            focarLinha(v, foco)
            v.background = if (foco) fundoFocado(v.context, RAIO, fioBaixo(v.context)) else fioBaixo(v.context)
            v.animate().scaleX(if (foco) ESCALA_LINHA else 1f).scaleY(if (foco) ESCALA_LINHA else 1f).setDuration(FOCO_MS).start()
        }
    }

    /**
     * Informação do item em foco num catálogo (Filmes, Séries; coerência visual parte 2, 12/11/2026): EM CIMA da grade,
     * como o destaque da HOME. Nome em 24 sp e, na mesma linha, a estrela da nota em âmbar e o resto em LEGENDA:
     * "Resident Evil   ★ 7,3  ·  2026  ·  Prime Video  ·  você parou em 20%".
     */
    fun infoFoco(nome: String, nota: Float?, resto: List<String>): CharSequence {
        val sb = android.text.SpannableStringBuilder(nome)
        sb.setSpan(android.text.style.AbsoluteSizeSpan(24, true), 0, sb.length, 0)
        sb.setSpan(android.text.style.ForegroundColorSpan(TEXTO), 0, sb.length, 0)
        sb.setSpan(android.text.style.TypefaceSpan(fonte(600)), 0, sb.length, 0)
        val ini = sb.length
        sb.append("   ")
        if (nota != null && nota > 0f) {
            val i = sb.length
            sb.append("★ " + String.format(java.util.Locale.getDefault(), "%.1f", nota))
            sb.setSpan(android.text.style.ForegroundColorSpan(ATENCAO), i, sb.length, 0)
            if (resto.isNotEmpty()) sb.append("  ·  ")
        }
        sb.append(resto.joinToString("  ·  "))
        sb.setSpan(android.text.style.AbsoluteSizeSpan(14, true), ini, sb.length, 0)
        return sb
    }

    /** LayoutParams de uma linha dentro da coluna do [telaLista]: largura toda, 8 dp entre linhas. */
    fun lpLinha(ctx: Context) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, E1) }

    /** Para quem precisa de um container com o fundo padrão e a margem segura já aplicados. */
    fun telaBase(ctx: Context): FrameLayout = FrameLayout(ctx).apply {
        background = fundoTela(ctx)
        setPaddingRelative(dp(ctx, MARGEM_LATERAL), dp(ctx, MARGEM_VERTICAL), dp(ctx, MARGEM_LATERAL), dp(ctx, MARGEM_VERTICAL))
    }

    /** Escurecido atrás de um diálogo: a tela de trás fica visível mas não compete. */
    const val VEU = 0xB3000000.toInt()
    /** Cartão de diálogo: quase opaco (lê-se por cima de qualquer coisa), com o fio do vidro. */
    const val CARTAO = 0xF5191C30.toInt()

    /**
     * Põe [tela] (um [dialogo], já com o véu) numa janela [d] de tela cheia, sem título e sem o fundo do tema. As
     * cópias destas 5 linhas em cada diálogo viraram esta função (refatoração 21/11/2026).
     */
    fun vestir(d: android.app.Dialog, tela: View) {
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        d.setContentView(tela)
        d.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    /**
     * Diálogo único (perguntas por cima de qualquer tela: confirmar, permitir, aviso parado). Cartão opaco no padrão
     * dos painéis (raio 16, fio claro), rótulo SECAO em cima, TITULO, texto em LEGENDA e os botões em pílula
     * ([botao]) lado a lado. Devolve a tela inteira, já com o véu, para o setContentView.
     * O primeiro botão da lista é o seguro (Cancelar/Negar) e fica à esquerda; quem chama dá o foco nele.
     */
    fun dialogo(ctx: Context, sobre: String?, titulo: CharSequence, mensagem: CharSequence?, botoes: List<TextView>,
                icone: Drawable? = null, larguraDp: Int = 640): FrameLayout {
        val cartao = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPaddingRelative(dp(ctx, E6 - 8), dp(ctx, E3 + 8), dp(ctx, E6 - 8), dp(ctx, E3 + 8))
            background = fundo(ctx, CARTAO, RAIO, VIDRO_BORDA)
            if (icone != null) addView(android.widget.ImageView(ctx).apply { setImageDrawable(icone) },
                LinearLayout.LayoutParams(dp(ctx, 56), dp(ctx, 56)).apply { bottomMargin = dp(ctx, E2) })
            if (sobre != null) addView(texto(ctx, Papel.SECAO, sobre).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(ctx, E1) })
            addView(texto(ctx, Papel.TITULO, titulo).apply { textSize = 24f; gravity = Gravity.CENTER })
            if (!mensagem.isNullOrEmpty()) addView(texto(ctx, Papel.LEGENDA, mensagem).apply {
                textSize = 16f; gravity = Gravity.CENTER; setLineSpacing(0f, 1.25f)
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(ctx, E2) })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                clipChildren = false
                botoes.forEachIndexed { i, b ->
                    addView(b, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) marginStart = dp(ctx, E2) })
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(ctx, E3 + 8) })
        }
        return FrameLayout(ctx).apply {
            setBackgroundColor(VEU)
            addView(cartao, FrameLayout.LayoutParams(dp(ctx, larguraDp), -2, Gravity.CENTER))
        }
    }

    /**
     * Lista de escolha por cima da tela, no jeito da cruz (18/11/2026, Paulo: "todo o sistema tem que ter a mesma leitura
     * visual"; antes era um cartão no meio que cobria o filme inteiro, com uma caixa por linha). Agora:
     *  - um PAINEL à direita sobre um véu em degradê: o vídeo continua à mostra à esquerda (dá para ver a legenda mudar);
     *  - nome da lista em caixa alta no laranja + "3 de 14" na mesma linha, igual ao nome da categoria na HOME;
     *  - uma COLUNA sem caixas: a linha escolhida grande e branca com o ponto laranja à esquerda; as outras menores e
     *    mais apagadas quanto mais longe (a mesma escada de opacidade da cruz, [HomeCruz.ALFA_COLUNA]);
     *  - a coluna rola para manter a escolhida no meio; marcadas ganham "✓" no acento;
     *  - 2ª passada (Paulo: "dava pra ser melhor"): letra de peso fixo (o texto não pula de largura no foco), linhas entram
     *    em onda ao abrir, o ponto "acende", som de passo da cruz (mudo com som tocando) e as dicas "OK escolher ·
     *    VOLTAR fechar" no pé, como na tela do Rádio.
     * O que entra continua igual para quem chama: OK numa linha fecha e chama [aoEscolher]; VOLTAR fecha sem escolher.
     * Os rótulos guardam spans (ícones de [JanelaIcones.comIcone], que o toString() antigo apagava).
     */
    fun lista(ctx: Context, titulo: CharSequence, itens: List<CharSequence>, marcadas: Collection<Int> = emptyList(),
              aoFechar: (() -> Unit)? = null, rodape: CharSequence? = null, focoNaMarcada: Boolean = true,
              focoInicial: Int? = null, entrar: Boolean = true, separarUltimo: Boolean = false,
              aoVoltar: (() -> Unit)? = null, valores: List<CharSequence?> = emptyList(),
              aoEscolher: (Int) -> Unit): android.app.Dialog {
        val d = android.app.Dialog(ctx)
        val rtl = ctx.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val forte = fonte(800)
        val media = fonte(500)
        val sombra = 0x99000000.toInt()
        val anima = android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        val suave = DecelerateInterpolator(1.6f)
        val nomeLista = titulo.toString()   // régua: sem maiúsculas
        // Cabeçalho só com o nome da lista (crítica de design 19/11/2026: o "3 de 14" ao lado era ruído; o foco já diz onde se está).
        val cabecalho = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, HomeCruz.FONTE_ROTULO); typeface = fonte(300); setTextColor(HomeCruz.COR_SUB)   // rótulo: cinza (laranja só no mundo da cruz)
            includeFontPadding = false; maxLines = 3; setLineSpacing(dp(ctx, 4).toFloat(), 1f)
            setShadowLayer(dp(ctx, 6).toFloat(), 0f, 0f, sombra)
            text = nomeLista
        }
        val linhas = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(0, dp(ctx, E1), 0, dp(ctx, E1))
        }
        // As dicas vêm logo embaixo da lista; a rolagem deixa o lugar delas (sem isso ela pegava todo o resto do painel).
        val reservaDicas = dp(ctx, LISTA_RESERVA_DICAS)
        val rolagem = object : android.widget.ScrollView(ctx) {
            override fun onMeasure(larg: Int, alt: Int) {
                val h = View.MeasureSpec.getSize(alt)
                super.onMeasure(larg, if (View.MeasureSpec.getMode(alt) == View.MeasureSpec.UNSPECIFIED) alt
                    else View.MeasureSpec.makeMeasureSpec((h - reservaDicas).coerceAtLeast(0), View.MeasureSpec.AT_MOST))
            }
        }.apply {
            isVerticalScrollBarEnabled = false
            // Borda que esmaece em cima e embaixo: mostra que há mais itens.
            isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(dp(ctx, 56))
            addView(linhas)
        }
        class Linha(val raiz: LinearLayout, val ponto: View, val nome: TextView, val valor: TextView? = null)
        val vistas = ArrayList<Linha>()
        var atual = -1
        fun arrumar(focada: Int, animar: Boolean) {
            for ((i, l) in vistas.withIndex()) {
                val dist = kotlin.math.abs(i - focada)
                val alfa = if (dist == 0) 1f else HomeCruz.ALFA_COLUNA[dist] ?: LISTA_ALFA_LONGE
                val escala = if (dist == 0) 1f else LISTA_ESCALA
                val ponto = if (dist == 0) 1f else 0f
                if (animar) {
                    l.nome.animate().alpha(alfa).scaleX(escala).scaleY(escala).setDuration(LISTA_FOCO_MS).setInterpolator(suave).start()
                    l.valor?.animate()?.alpha(alfa)?.setDuration(LISTA_FOCO_MS)?.setInterpolator(suave)?.start()
                    // o ponto "acende": cresce de 40% e aparece
                    if (dist == 0) { l.ponto.scaleX = 0.4f; l.ponto.scaleY = 0.4f }
                    l.ponto.animate().alpha(ponto).scaleX(1f).scaleY(1f).setDuration(LISTA_FOCO_MS).setInterpolator(suave).start()
                } else {
                    l.nome.alpha = alfa; l.nome.scaleX = escala; l.nome.scaleY = escala; l.ponto.alpha = ponto; l.valor?.alpha = alfa
                }
            }
        }
        itens.forEachIndexed { i, rotulo ->
            val ponto = View(ctx).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ACENTO) }
                alpha = 0f
            }
            val nome = TextView(ctx).apply {
                text = rotulo
                // peso fixo: trocar a fonte no foco mudava a largura e a quebra de linha (o texto "pulava")
                setTextSize(TypedValue.COMPLEX_UNIT_SP, LISTA_FONTE); typeface = fonte(700); setTextColor(TEXTO)
                includeFontPadding = false; setLineSpacing(dp(ctx, 3).toFloat(), 1f)
                setShadowLayer(dp(ctx, 8).toFloat(), 0f, 0f, sombra)
                // encolhe pelo começo da linha (do lado do ponto), como os vizinhos da cruz
                addOnLayoutChangeListener { v, l, t, r, _, _, _, _, _ -> v.pivotX = if (rtl) (r - l).toFloat() else 0f; v.pivotY = dp(ctx, 14).toFloat() }
                alpha = LISTA_ALFA_LONGE; scaleX = LISTA_ESCALA; scaleY = LISTA_ESCALA
            }
            // [valores]: o valor de agora à direita, na mesma linha (menu do player, 20/11/2026: "Legenda   Desligada ›",
            // como na Apple TV). Numa linha com ✓ ele não aparece: o ✓ já diz tudo.
            val valorV = valores.getOrNull(i)?.takeIf { it.isNotBlank() && i !in marcadas }?.let { v ->
                TextView(ctx).apply {
                    text = v; setTextSize(TypedValue.COMPLEX_UNIT_SP, LISTA_FONTE_VALOR); typeface = fonte(600); setTextColor(HomeCruz.COR_SUB)
                    includeFontPadding = false; maxLines = 1; maxWidth = dp(ctx, 190)
                    setShadowLayer(dp(ctx, 6).toFloat(), 0f, 0f, sombra)
                }
            }
            val raiz = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                // em cima: com 2-3 linhas, o ponto e o ✓ ficam na altura da 1ª (como na cruz), não no meio
                gravity = Gravity.TOP
                minimumHeight = dp(ctx, 50)
                setPaddingRelative(0, dp(ctx, 10), 0, dp(ctx, 10))
                isFocusable = true; isClickable = true
                contentDescription = rotulo.toString().trim() + (valorV?.let { " · " + it.text } ?: "") + if (i in marcadas) " ✓" else ""
                addView(ponto, LinearLayout.LayoutParams(dp(ctx, HomeCruz.PONTO), dp(ctx, HomeCruz.PONTO)).apply { marginEnd = dp(ctx, 14); topMargin = dp(ctx, 11) })
                addView(nome, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (valorV != null) addView(valorV, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = dp(ctx, E2); topMargin = dp(ctx, 1) })
                if (i in marcadas) addView(TextView(ctx).apply {
                    text = "✓"; setTextSize(TypedValue.COMPLEX_UNIT_SP, LISTA_FONTE_MARCA); typeface = forte; setTextColor(ACENTO)
                    includeFontPadding = false; setShadowLayer(dp(ctx, 6).toFloat(), 0f, 0f, sombra)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(ctx, E2); topMargin = dp(ctx, 3) })
                setOnClickListener { SonsCruz.tocar(ctx, SonsCruz.Som.ABRIR); d.dismiss(); aoEscolher(i) }
                setOnFocusChangeListener { v, foco ->
                    if (!foco) return@setOnFocusChangeListener
                    // som de passo da cruz (só quando anda, não ao abrir; mudo com o filme tocando: SonsCruz se cala)
                    if (atual >= 0 && atual != i) SonsCruz.tocar(ctx, SonsCruz.Som.ANDAR)
                    atual = i
                    arrumar(i, animar = anima)
                    // a escolhida fica no meio da coluna (a cruz não deixa o foco grudar na borda)
                    rolagem.post { rolagem.smoothScrollTo(0, (v.top + v.height / 2 - rolagem.height / 2).coerceAtLeast(0)) }
                }
            }
            vistas += Linha(raiz, ponto, nome, valorV)
            // [separarUltimo]: a ação destrutiva (Desinstalar) fica afastada das comuns, por último (crítica 19/11/2026).
            linhas.addView(raiz, LinearLayout.LayoutParams(-1, -2).apply {
                if (i > 0) topMargin = dp(ctx, if (separarUltimo && i == itens.size - 1) 22 else 2)
            })
        }
        // Dicas logo embaixo da lista, no mesmo desenho das do Rádio/Podcasts (tecla numa pílula + o que ela faz).
        // Dicas logo embaixo da lista: a mesma barra de todo o sistema.
        val dicas = barraDicas(ctx, ctx.getString(R.string.grv_dica_ok) to ctx.getString(R.string.lista_dica_escolher),
            // [aoVoltar]: sublista (menu do player, 20/11/2026): VOLTAR volta à lista de cima em vez de fechar tudo.
            ctx.getString(R.string.rede_tecla_voltar) to (if (aoVoltar != null) ctx.getString(R.string.lista_dica_voltar)
                else ctx.getString(R.string.fp_fechar)).lowercase())
        val recuo = dp(ctx, HomeCruz.PONTO + 14)   // textos do cabeçalho alinhados com o texto das linhas, não com o ponto
        val painel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            // embaixo sobra a faixa da legenda do filme: nem a lista nem as dicas passam por cima dela
            setPaddingRelative(dp(ctx, 8), dp(ctx, 44), dp(ctx, 56), dp(ctx, 96))
            addView(cabecalho, LinearLayout.LayoutParams(-1, -2).apply { marginStart = recuo })
            // Crédito de quem forneceu os itens (ex.: "Subtitles provided by OpenSubtitles.com"), embaixo do nome da lista:
            // no pé ele sumia quando a lista enchia o painel (a rolagem fica com todo o espaço que sobra).
            if (rodape != null) addView(TextView(ctx).apply {
                text = rodape; setTextSize(TypedValue.COMPLEX_UNIT_SP, LISTA_FONTE_POSICAO); typeface = media; setTextColor(HomeCruz.COR_SUB)
                alpha = 0.85f; includeFontPadding = false; setShadowLayer(dp(ctx, 6).toFloat(), 0f, 0f, sombra)
            }, LinearLayout.LayoutParams(-1, -2).apply { marginStart = recuo; topMargin = dp(ctx, 8) })
            addView(rolagem, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(ctx, E2) })
            addView(dicas, LinearLayout.LayoutParams(-2, -2).apply { marginStart = recuo; topMargin = dp(ctx, 14) })
        }
        val raiz = FrameLayout(ctx).apply {
            // Véu só do lado do painel: transparente à esquerda, quase opaco atrás do texto. O meio ficou mais forte
            // (19/11/2026, Sala): o ícone "Aplicativos" da cruz aparecia através de "Trocar ícone".
            background = GradientDrawable(if (rtl) GradientDrawable.Orientation.RIGHT_LEFT else GradientDrawable.Orientation.LEFT_RIGHT,
                // 19/11/2026 (crítica de design): ainda vazava; agora fica quase opaco já do meio da tela para a direita.
                intArrayOf(0x00000000, 0x40000000, 0xE6080A10.toInt(), 0xF5080A10.toInt(), 0xF5080A10.toInt()))
            // com valores à direita o painel alarga: na TV (20/11/2026) "Volume parecido entre canais" quebrava em 2 linhas
            addView(painel, FrameLayout.LayoutParams(dp(ctx, if (valores.any { !it.isNullOrBlank() }) LISTA_LARGURA_VALORES else LISTA_LARGURA), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
        }
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        d.setContentView(raiz)
        d.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setWindowAnimations(0)   // a entrada é nossa (abaixo); a do sistema somava um "pulo" cinza
        }
        // focoNaMarcada: numa escolha única o foco começa na opção atual; numa lista de liga/desliga (opções do player)
        // começa no topo, senão o foco caía no meio da lista, no primeiro efeito ligado (visto na Sala, 17/11/2026).
        d.setOnShowListener {
            // focoInicial: a lista reaberta logo depois de uma escolha (dias do despertador) volta na mesma linha, sem a entrada.
            val i = focoInicial?.takeIf { it in vistas.indices } ?: marcadas.firstOrNull { focoNaMarcada && it in vistas.indices } ?: 0
            arrumar(i, animar = false)
            vistas.getOrNull(i)?.raiz?.requestFocus()
            if (!anima || !entrar) return@setOnShowListener
            // Entrada: o véu aparece, o painel desliza da direita e as linhas chegam em onda (como os vizinhos da cruz).
            val lado = if (rtl) -1 else 1
            raiz.background?.alpha = 0; android.animation.ValueAnimator.ofInt(0, 255).apply {
                duration = LISTA_ENTRAR_MS; interpolator = suave
                addUpdateListener { raiz.background?.alpha = it.animatedValue as Int }
            }.start()
            painel.translationX = dp(ctx, 48).toFloat() * lado; painel.alpha = 0f
            painel.animate().translationX(0f).alpha(1f).setDuration(LISTA_ENTRAR_MS).setInterpolator(suave).start()
            for ((k, l) in vistas.withIndex()) {
                val atraso = (kotlin.math.abs(k - i) * LISTA_ONDA_MS).coerceAtMost(LISTA_ONDA_MAX_MS)
                l.raiz.translationX = dp(ctx, 28).toFloat() * lado
                l.raiz.animate().translationX(0f).setStartDelay(atraso).setDuration(LISTA_ENTRAR_MS).setInterpolator(suave).start()
            }
            dicas.alpha = 0f
            dicas.animate().alpha(1f).setStartDelay(LISTA_ENTRAR_MS / 2).setDuration(LISTA_ENTRAR_MS).start()
        }
        if (aoFechar != null) d.setOnDismissListener { aoFechar() }
        // só a tecla VOLTAR cancela; escolher um item fecha sem cancelar
        if (aoVoltar != null) d.setOnCancelListener { aoVoltar() }
        d.show()
        return d
    }

    /**
     * Rótulo de [lista] com a informação útil numa 2ª linha menor e creme, como a linha de baixo da cruz
     * ("Dune 2021 1080p WEBRip" / "Português (Brasil) · ↓ 44 mil").
     */
    fun comInfo(nome: CharSequence, info: String): CharSequence {
        if (info.isBlank()) return nome
        val sb = android.text.SpannableStringBuilder(nome).append('\n')
        val ini = sb.length
        sb.append(info)
        sb.setSpan(android.text.style.RelativeSizeSpan(LISTA_FONTE_INFO / LISTA_FONTE), ini, sb.length, 0)
        sb.setSpan(android.text.style.ForegroundColorSpan(HomeCruz.COR_SUB), ini, sb.length, 0)
        return sb
    }

    /** Medidas da [lista] (dp/sp numa tela de 960 x 540). */
    const val LISTA_LARGURA = 470
    const val LISTA_LARGURA_VALORES = 580
    const val LISTA_FONTE = 24f
    const val LISTA_FONTE_POSICAO = 17f
    /** A 2ª linha de um rótulo [comInfo]: 18 sp, o texto padrão do Google para TV (o mesmo da linha de baixo da cruz). */
    const val LISTA_FONTE_INFO = 18f
    const val LISTA_FONTE_VALOR = 20f
    /** Vizinhos: 24 sp viram ~20 sp na tela (a escada da cruz é 0,6; aqui a lista é de ler, então encolhe menos). */
    const val LISTA_ESCALA = 0.85f
    const val LISTA_ALFA_LONGE = 0.5f
    const val LISTA_FONTE_MARCA = 20f
    /** Dicas do pé: 16 sp, o mínimo da casa (idosos); as do Rádio usam 14 na tecla. */
    const val LISTA_FONTE_DICA = 18f   // régua 22/11/2026: mínimo 18 sp
    const val LISTA_FOCO_MS = 180L
    const val LISTA_ENTRAR_MS = 260L
    /** Cada linha chega este tanto depois da vizinha (a "onda" da cruz é 16 ms por passo). */
    const val LISTA_ONDA_MS = 18L
    const val LISTA_ONDA_MAX_MS = 120L
    /** Altura guardada para as dicas embaixo da lista (pílula de ~26 dp + 14 de vão). */
    const val LISTA_RESERVA_DICAS = 44
}
