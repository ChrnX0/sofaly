package br.paulo.tvlauncher.home

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.player.item

/**
 * A tela inicial em cruz (17/11/2026): a linha de categorias e a coluna da categoria em foco. A lógica das teclas é a
 * de [CruzLogica]; aqui só o desenho.
 *
 * Desempenho (aceite da especificação): todas as views são criadas UMA vez por conjunto de itens ([definir]) e cada
 * tecla só anima translationX/Y, alpha e escala (propriedades do RenderNode, sem medir nem desenhar de novo). Os
 * itens que não aparecem ficam INVISIBLE (não desenham). A view inteira é UM foco só: a cruz trata as setas sozinha e
 * devolve ▲ do 1º item ao Android, que leva o foco para a barra de cima.
 *
 * Medidas em dp numa tela de 960 x 540 (a amostra é de 1280 x 720, x 0,75), todas aqui em cima para o dono refinar.
 */
class HomeCruz(ctx: Context, private val fonte: Typeface, private val fonteForte: Typeface) : FrameLayout(ctx) {

    /**
     * Um item da coluna. [abrir] roda no OK; [opcoes] no OK segurado (null = segurar é igual a apertar).
     * [progresso] (0..1): quanto já foi visto (filme, série, programa da TV ao vivo); vira uma barra embaixo do ícone.
     * [situacao]: o que está acontecendo agora com o item (no ar, parou em…): 1ª linha do canto de baixo à direita.
     */
    class Item(
        val id: String, val nome: String, val sub: String, val icone: Drawable,
        val abrir: () -> Unit, val opcoes: (() -> Unit)? = null, val aoEscolher: (() -> Unit)? = null,
        val progresso: Float? = null, val situacao: CruzLogica.Situacao? = null,
    ) {
        /** O mesmo item com outro menu do OK segurado (o "Trocar ícone" dos itens do Sofaly, HomeMotion). */
        fun comOpcoes(o: () -> Unit) = Item(id, nome, sub, icone, abrir, o, aoEscolher, progresso, situacao)
    }
    class Categoria(val cat: CruzLogica.Categoria, val nome: String, val icone: Drawable, val itens: List<Item>,
                    /** Só o ícone na linha (opção da categoria); o nome some mas guarda o lugar, nada se mexe. */
                    val soIcone: Boolean = false)

    companion object {
        // ---- posições (dp) e tamanhos: o lugar único para refinar
        val LINHA get() = CrossRhythm.atual.linha          // centro vertical da linha de categorias (onde a coluna cruza)
        val COLUNA get() = CrossRhythm.atual.coluna         // centro horizontal da coluna (onde a linha cruza)
        val ICONE_L get() = CrossRhythm.atual.iconeL         // ícone do item escolhido (o grande da cruz)
        val ICONE_A get() = CrossRhythm.atual.iconeA
        val CAT_L get() = CrossRhythm.atual.catL            // ícone de categoria na linha
        val CAT_A get() = CrossRhythm.atual.catA
        val ESCALA_PEQUENO get() = CrossRhythm.atual.escalaPequeno // item fora da escolha: 112 -> 67 dp; nome 32 -> 19 sp
        val PASSO_COLUNA get() = CrossRhythm.atual.passoColuna    // distância entre itens pequenos na coluna
        val VAO_ESCOLHIDO get() = CrossRhythm.atual.vaoEscolhido   // do centro do escolhido ao centro do vizinho de baixo (o grande ocupa mais)
        val VAO_CIMA get() = CrossRhythm.atual.vaoCima        // do centro do escolhido ao centro do vizinho de cima
        // Até 4 linhas por item nas telas de Ouvir (CruzOuvir); o item tem SUB_SOBRA dp a mais embaixo do ícone para o texto.
        const val SUB_LINHAS = 4
        const val SUB_SOBRA = 76
        // Na HOME, embaixo do nome fica UMA linha (Paulo, 18/11/2026 12:15 local); o resto vai para o canto de baixo à
        // direita, onde a tela está vazia ([CruzLogica.partes]). Aparece quando o foco para, para não piscar navegando.
        const val DETALHE_L = 330         // largura do bloco de detalhes
        const val DETALHE_MARGEM = 48     // da borda direita
        const val DETALHE_BAIXO = 44      // da borda de baixo
        // 23/11/2026 (Paulo, foto da Sala): com a cruz mais baixa os detalhes saíram do canto de baixo e foram para o
        // canto de cima à direita, abaixo da pílula da hora (que vai até ~72 dp), onde a tela está vazia.
        const val DETALHE_TOPO = 96
        const val DETALHE_LINHAS = 5
        const val DETALHE_ESPERA_MS = 300L
        // A informação de baixo é UMA, mas pode quebrar em 2 linhas na palavra (cortar "Vídeos, músicas e fotos da rede
        // da …" a 3 letras do fim e repetir inteiro no canto ficava pior); passou de 2, termina em "…" e vai inteira para o canto.
        const val ITEM_LINHAS = 2
        const val TEXTO_TOPO = 9        // do topo do ícone ao topo do nome (o nome fica na metade de cima do ícone)
        const val PONTO = 10            // ponto laranja à esquerda do nome do escolhido (era uma barra embaixo dele)
        const val TRACO_L = 4           // régua 22/11/2026: o ponto virou traço vertical de 4 x 14 dp, pontas redondas
        const val TRACO_A = 14
        val CAT_ESQUERDA get() = CrossRhythm.atual.catEsquerda    // centro da categoria logo à esquerda da coluna
        val CAT_DIREITA get() = CrossRhythm.atual.catDireita    // centro da 1ª categoria à direita (depois do texto mais longo)
        val PASSO_CATS get() = CrossRhythm.atual.passoCats     // entre categorias à direita
        val ROTULO_Y get() = CrossRhythm.atual.rotuloY
        const val ROTULO_VAO = 12f        // ritmos novos: do pé do nome do mundo ao topo do que está logo abaixo dele        // nome da categoria em foco, acima da linha à esquerda
        const val LARGURA_ITEM = 470    // ícone + texto do item
        // Largura do nome/linha de baixo do escolhido: termina FOLGA_CAT antes do ícone da 1ª categoria à direita, em
        // qualquer ritmo (22/11/2026, Sala v468193: em Ouvir o "Programas, notícias e conversas..." de Podcasts
        // encostava a ~13 px do Jogar porque a largura era fixa em 300 dp e o ritmo A aproximou as categorias).
        const val TEXTO_TETO = 300
        const val FOLGA_CAT = 32
        val TEXTO_MAX: Int get() = CruzLogica.larguraTexto(COLUNA, ICONE_L, CrossRhythm.atual.vaoTexto, PONTO + 10,
            CAT_DIREITA, CAT_L, FOLGA_CAT, TEXTO_TETO)
        const val TELA_L = 960f
        const val TELA_A = 540f
        // ---- "Barra embaixo" (BottomBar, 23/11/2026; visual com referências do Mobbin: Quizlet/Tubi na categoria escolhida,
        // Hulu na barra de largura toda, Apple Music no título da linha com "›").
        const val BARRA_MARGEM = 48f     // das bordas da tela até a placa da barra
        const val BARRA_BAIXO = 28f      // da borda de baixo até a placa
        const val BARRA_PLACA_A = 132f   // altura da placa das categorias
        const val BARRA_APPS_VAO = 44f   // da placa até o pé da linha de apps (cabe o nome do app escolhido)
        const val BARRA_PASSO_APPS = 150f // entre os centros dos apps na linha
        const val BARRA_ESCALA_APP = 0.8f // app fora da escolha na linha
        const val BARRA_ALFA_APP = 0.7f
        const val BARRA_ALFA_CAT = 0.8f   // categoria não escolhida
        // ---- fontes (sp): tabela do estudo 32 / 20 / 16 / 18
        const val FONTE_ESCOLHIDO = 32f // nome do item escolhido (fora da escolha fica 0,6 x = ~19)
        const val FONTE_ROTULO = 22f    // nome da categoria em foco (estudo da HOME, 18/11/2026: 20 -> 22)
        const val FONTE_SUB = 18f       // linha de baixo do item escolhido (18 sp = texto padrão do Google para TV; era 16)
        const val FONTE_CATEGORIA = 18f // nome das categorias na linha
        // ---- transparência dos vizinhos na coluna (posição relativa -> alfa)
        // Estudo da HOME (18/11/2026): nenhum nome visível abaixo de 0,6, que a 3 m some (era 0,5 em cima e 0,35 no 3º).
        private val ALFA_NORMAL = mapOf(-1 to 0.6f, 1 to 0.9f, 2 to 0.72f, 3 to 0.6f)
        // Alto contraste (crítica de design 19/11/2026): nada apagado demais; a distância do foco fica só na escala.
        private val ALFA_CONTRASTE = mapOf(-1 to 0.9f, 1 to 1f, 2 to 0.95f, 3 to 0.9f)
        val ALFA_COLUNA: Map<Int, Float> get() = (if (Estilo.altoContraste) ALFA_CONTRASTE else ALFA_NORMAL).filterKeys { it <= CrossRhythm.atual.vizinhos }
        /** Item escolhido de outra coluna (fora do foco): 75% normal, inteiro no alto contraste. */
        val ALFA_ESCOLHIDO_FORA: Float get() = if (Estilo.altoContraste) 1f else 0.75f
        // ---- movimento (Paulo 18/11/2026: "mais suave e orgânico"): mola em vez de tempo fixo. A mola guarda a velocidade
        // quando a tecla vem no meio do caminho (nada "freia e recomeça"), e os vizinhos saem um pouco depois (onda).
        const val MOLA_W = 17f          // rigidez (rad/s): ~0,3 s até assentar, perto dos 220 ms de antes
        const val MOLA_AMORTECE = 0.86f // < 1 passa um tiquinho do alvo e volta (1 % na posição); 1 = sem passar
        const val ONDA_MS = 16L         // atraso por passo de distância do item escolhido
        const val ONDA_MAX_MS = 64L
        // Trocar de categoria é um salto lateral grande: sem passar do alvo (amortecimento 1), para não "balançar".
        const val MOLA_AMORTECE_CATEGORIA = 1f
        // Segurando a seta (a partir da 3ª repetição): mola 40% mais rígida e sem onda, para atravessar a lista sem fila.
        const val REPETICAO_RAPIDA = 2
        const val RAPIDA_W = 1.4f
        // O som, segurando a seta, toca 1 vez a cada 3 itens (não metralha).
        const val SOM_A_CADA = 3
        const val APERTO = 0.92f        // "aperto" do ícone no OK: resposta no mesmo quadro do toque
        const val APERTO_MS = 70L
        const val MOVER_AVISO_MS = 600_000L   // a dica do reordenar fica até terminar
        const val NAO_ABRIU_MS = 4000L  // "Não deu para abrir" no lugar da linha de baixo
        const val FONTE_POSICAO = 18f   // "3 de 9"
        const val BARRA_A = 5f          // altura da barra de progresso (dp)
        const val BARRA_L = 0.72f       // largura da barra em fração do ícone
        // Seta ▼ "tem mais embaixo": abaixo do 3º vizinho (o último visível), no centro dos ícones pequenos.
        const val SETA_L = 28
        const val SETA_A = 12
        val SETA_Y get() = CrossRhythm.atual.setaY
        const val SETA_DX = 64f        // a seta sai do eixo dos ícones e fica sob os nomes
        const val DICA_MS = 1500L       // cruz parada por este tempo: a seta aparece (some na próxima tecla)
        // O ícone escolhido começa a animação dele depois que a coluna assenta (~0,3 s de mola): durante o movimento a view
        // tem camada de GPU, e um desenho que muda a cada quadro obrigaria a refazer a camada inteira a cada 33 ms.
        const val ICONE_ESPERA_MS = 450L
        const val COR_CATEGORIA = 0xFFE8E2DA.toInt()
        const val COR_SUB = 0xFFE2D8CF.toInt()
    }

    /** Quanto os ícones se mexem (MovimentoAdaptativo); quem usa decide pelo aparelho e pelos quadros medidos. */
    var nivel: MovimentoAdaptativo.Nivel = MovimentoAdaptativo.Nivel.COMPLETO
        set(v) { if (field != v) { field = v; posicionar(animar = false) } }
    /** ▲ no 1º item saiu da cruz; o número é o repeatCount da tecla (0 = toque novo, >0 = vinha segurando). */
    var aoSubirTopo: ((Int) -> Unit)? = null
    /** Andou com uma seta (para a arte deslizar ao contrário: paralaxe, onda 3). Não vem na HOME nem na borda. */
    var aoPasso: ((CruzLogica.Tecla) -> Unit)? = null
    /** Som de cada passo (SonsCruz): quem usa decide se toca. */
    var aoSom: ((SonsCruz.Som, CruzLogica.Categoria?) -> Unit)? = null
    /** Mudou o item em foco (para o destaque/capa de quem usa) */
    var aoMudar: ((Categoria, Item) -> Unit)? = null
    /** OK num item (antes de [Item.abrir]): quem usa grava o "lembrado" da categoria. */
    var aoAbrir: ((CruzLogica.Categoria, String) -> Unit)? = null
    /** Barra "Relógio" ocupa o canto de baixo com a hora grande: os detalhes vão para o canto de cima, à direita. */
    var detalheEmCima = true
        set(v) {
            if (field == v) return
            field = v
            (detalhe.layoutParams as LayoutParams).apply {
                gravity = (if (v) Gravity.TOP else Gravity.BOTTOM) or Gravity.END
                topMargin = if (v) dpi(DETALHE_TOPO) else 0
                bottomMargin = if (v) 0 else dpi(DETALHE_BAIXO)
            }
            detalhe.gravity = (if (v) Gravity.TOP else Gravity.BOTTOM) or Gravity.END
            detalhe.requestLayout()
        }
    /**
     * Brilho atrás do ícone escolhido (Ajuste fino, CardStyle.Glow): 0 = sem (padrão). Na cor de destaque, só no item
     * escolhido da coluna em foco. Quem usa define antes de montar a cruz.
     */
    var brilho = 0f
    /** Itens lembrados (id da categoria -> id do item), lidos por quem usa; valem só na 1ª montagem. */
    var lembrados: Map<String, String> = emptyMap()
    /** O último item aberto ("categoria|item"); quem usa grava e devolve aqui na 1ª montagem (HOME refeita). */
    var ultimoAberto: String? = null

    /**
     * "Barra embaixo" ([BottomBar]): as categorias paradas na linha de baixo e a linha de apps só com ▲. Quem usa define
     * antes de montar; trocar depois refaz o lugar de tudo (a MainActivity refaz a HOME quando o ajuste muda).
     */
    var barra = false
        set(v) { if (field == v) return; field = v; naApps = false; aplicarModo(); posicionar(animar = false) }
    /** Na barra: a linha de apps está aberta (o foco está num app) ou fechada (o foco está na categoria). */
    private var naApps = false

    /**
     * "Barra única" ([StripHome], 24/11/2026): mundo acima (▲ ▼) e uma linha de cards embaixo (◀ ▶). O desenho é do
     * [StripLayer]; o estado e as teclas continuam aqui. Quem usa define antes de montar.
     */
    var faixa = false
        set(v) { if (field == v) return; field = v; aplicarModo(); posicionar(animar = false) }
    /** Opções dos cards e da descrição da barra única (Ajuste fino). */
    var opcoesFaixa: StripHome.Options
        get() = strip.opcoes
        set(v) { strip.opcoes = v; if (faixa) posicionar(animar = false) }
    /** Banner de TV de um item da barra única (só apps); quem usa responde na thread principal. */
    var bannerDe: ((Item, (Drawable?) -> Unit) -> Unit)?
        get() = strip.bannerDe
        set(v) { strip.bannerDe = v; rows.bannerDe = v }
    /**
     * "Fileiras" ([RowsHome], 26/11/2026, clone do Projectivy): uma fileira por categoria. Usa as teclas e o estado da
     * barra única ([faixa] fica ligada junto); só o desenho muda ([RowsLayer]).
     */
    var fileiras = false
        set(v) { if (field == v) return; field = v; aplicarModo(); posicionar(animar = false) }
    var opcoesFileiras: RowsHome.Options
        get() = rows.opcoes
        set(v) { rows.opcoes = v; if (fileiras) posicionar(animar = false) }
    /** Engrenagem da pílula do alto nas Fileiras (abre as Configurações). */
    var aoEngrenagem: (() -> Unit)?
        get() = rows.aoEngrenagem
        set(v) { rows.aoEngrenagem = v }
    /** A engrenagem das Fileiras, para o ▲ da 1ª fileira chegar nela. */
    internal fun engrenagem(): View = rows.engrenagem
    /** Distância (dp) do pé da tela até o topo da linha do mundo, a cada mudança (camada dos widgets). */
    var aoMudarBase: ((Float) -> Unit)?
        get() = strip.aoMudarBase
        set(v) { strip.aoMudarBase = v }
    /** De onde o mundo veio no último passo (-1 de cima, +1 de baixo): a linha do mundo entra por esse lado. */
    private var direcaoFaixa = 0

    private var cats: List<Categoria> = emptyList()
    private var mapa = CruzLogica.Mapa(emptyList(), emptyList())
    private var estado = CruzLogica.Estado(0, emptyList())
    private var assinatura: List<String>? = null

    private val viewsCat = ArrayList<View>()
    /** A camada da barra única ([faixa]); some nos outros jeitos. */
    private val strip: StripLayer
    /** A camada das Fileiras ([fileiras]). */
    private lateinit var rows: RowsLayer
    private val veu: View
    private val placa: View
    private val realce: View
    /** Barra embaixo: "Ouvir ›" acima da linha de apps e o nome do app escolhido embaixo dele. */
    private val tituloApps: TextView
    private val nomeApp: TextView
    private val viewsItem = ArrayList<List<ItemView>>()
    private val rotulo: TextView
    /** "3 de 9" ao lado do nome da categoria: quanto falta na coluna (estudo da HOME, onda 2). */
    private val posicao: TextView
    /** Seta ▼ embaixo da coluna quando há itens escondidos lá embaixo; aparece com a cruz parada ([DICA_MS]). */
    private val maisAbaixo: View
    /** Os detalhes do item em foco, no canto de baixo à direita. */
    private val detalhe: TextView
    private val dm = resources.displayMetrics
    private fun dp(v: Float) = v * dm.density
    private fun dpi(v: Int) = (v * dm.density + 0.5f).toInt()

    /** O traço do escolhido no jeito de foco atual (Ajuste fino > Foco): laranja no Fio, creme no Véu e no Quadro. */
    private fun tracoDoFoco() = GradientDrawable().apply {
        cornerRadius = dp(TRACO_L / 2f)
        setColor(if (Estilo.focoAtual(context) == br.paulo.tvlauncher.settings.FocusStyle.LINE) Estilo.ACENTO else Estilo.TEXTO)
    }

    /** As partes de um item que mudam com a escolha. */
    private inner class ItemView(val raiz: LinearLayout, val icone: ImageView, val nome: TextView, val sub: TextView, val traco: View) {
        /** Linhas da linha de baixo na última arrumação (1 ou 2; com 2, os vizinhos de baixo descem uma linha). */
        var linhas = 1
        var voltarSub: Runnable? = null
        /** A linha de baixo (só a principal: [CruzLogica.partes]), para voltar depois do "Não deu para abrir". */
        var principal: String = ""
        /** Barra de "quanto já foi visto", criada só no item que tem progresso. */
        var barra: BarraProgresso? = null
    }

    /** Barra fina e arredondada no pé do ícone (como nas miniaturas de vídeo): trilho claro, parte vista na cor de destaque. */
    private inner class BarraProgresso : Drawable() {
        var valor = 0f
            set(v) { field = v.coerceIn(0f, 1f); invalidateSelf() }
        private val trilho = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x59FFFFFF }
        private val cheio = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = Estilo.ACENTO }
        private val r = android.graphics.RectF()
        override fun draw(c: android.graphics.Canvas) {
            val b = bounds
            val a = dp(BARRA_A); val l = b.width() * BARRA_L
            r.set(b.exactCenterX() - l / 2f, b.bottom - a - dp(2f), b.exactCenterX() + l / 2f, b.bottom - dp(2f))
            c.drawRoundRect(r, a / 2f, a / 2f, trilho)
            if (valor > 0f) { r.right = r.left + maxOf(a, l * valor); c.drawRoundRect(r, a / 2f, a / 2f, cheio) }
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** Nome do mundo em foco + "1 de 4" (declarado antes do init, que o cria). */
    private lateinit var cabecalho: LinearLayout
    private val curvaRotulo = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)

    init {
        isFocusable = true
        isFocusableInTouchMode = false
        // A cruz é desenhada por posição absoluta (translationX a partir da esquerda). Em árabe a direção da view virava
        // da direita para a esquerda e todos os filhos começavam na borda direita: a coluna saía da tela (telas ar,
        // 18/11/2026). Fica sempre da esquerda para a direita; o texto em árabe continua escrito na direção dele.
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        clipChildren = false
        clipToPadding = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        // Véu escuro atrás da coluna (contraste com qualquer tema ou foto): da esquerda até depois do texto, some antes
        // das categorias da direita. Uma camada só, parada.
        veu = View(ctx).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xCC141413.toInt(), 0x99141413.toInt(), 0x00141413))
        }
        addView(veu, LayoutParams(dpi(600), LayoutParams.MATCH_PARENT))
        // Barra embaixo: a placa (vidro escuro de cantos redondos, largura toda) e o realce da categoria escolhida.
        // Ficam atrás das categorias (que entram depois, em [definir]); somem na cruz.
        placa = View(ctx).apply {
            background = GradientDrawable().apply { cornerRadius = dp(28f); setColor(0xB30B0B0A.toInt()); setStroke(dpi(1), 0x14FFFFFF) }
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(placa, LayoutParams(dpi((TELA_L - 2 * BARRA_MARGEM).toInt()), dpi(BARRA_PLACA_A.toInt())))
        realce = View(ctx).apply { visibility = View.GONE; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        addView(realce, LayoutParams(dpi(150), dpi((BARRA_PLACA_A - 16f).toInt())))
        rotulo = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, FONTE_ROTULO)
            // Régua de estilo (22/11/2026): o nome do mundo em creme 72%, leve e sem espaçamento; o laranja ficou só
            // para detalhe (progresso e o traço do foco).
            setTextColor(Estilo.TEXTO_2)
            typeface = fonteForte
            setShadowLayer(dp(6f), 0f, 0f, 0x99000000.toInt())
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        posicao = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, FONTE_POSICAO)
            setTextColor(COR_SUB)
            typeface = fonte
            includeFontPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setShadowLayer(dp(6f), 0f, 0f, 0x99000000.toInt())
            // Régua 22/11/2026 ("informação sob demanda"): o "1 de 4" sai da tela; o TalkBack continua lendo (descrição).
            visibility = View.GONE
        }
        cabecalho = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(rotulo, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(posicao, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dpi(14) })
        }
        addView(cabecalho, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        cabecalho.translationX = dp(COLUNA - ICONE_L / 2f)
        cabecalho.translationY = dp(ROTULO_Y)
        // a altura do nome só existe depois de medir (e muda com a letra): reposiciona quando ela muda
        cabecalho.addOnLayoutChangeListener { _, _, t, _, b, _, ot, _, ob -> if (b - t != ob - ot) posicionarRotulo(false) }
        maisAbaixo = Seta(ctx).apply { alpha = 0f; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        addView(maisAbaixo, LayoutParams(dpi(SETA_L), dpi(SETA_A)))
        // Ao lado dos ícones, embaixo do nome (crítica de design 19/11/2026): no eixo, a seta caía em cima do logo do 4º app.
        maisAbaixo.translationX = dp(COLUNA + SETA_DX - SETA_L / 2f)
        maisAbaixo.translationY = dp(SETA_Y)
        detalhe = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, FONTE_SUB)
            setTextColor(COR_SUB)
            typeface = fonte
            // à direita, colado no canto (a cruz é sempre da esquerda para a direita: END = direita)
            gravity = Gravity.END or Gravity.TOP
            maxLines = DETALHE_LINHAS
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            setLineSpacing(dp(6f), 1f)
            setShadowLayer(dp(8f), 0f, 0f, 0xCC000000.toInt())
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO   // o TalkBack já ouve tudo no item
        }
        addView(detalhe, LayoutParams(dpi(DETALHE_L), LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            marginEnd = dpi(DETALHE_MARGEM); topMargin = dpi(DETALHE_TOPO)
        })
        tituloApps = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, FONTE_ROTULO)
            setTextColor(Estilo.TEXTO)
            typeface = fonteForte
            maxLines = 1
            includeFontPadding = false
            setShadowLayer(dp(6f), 0f, 0f, 0x99000000.toInt())
            alpha = 0f; visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(tituloApps, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        nomeApp = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, FONTE_CATEGORIA)
            setTextColor(Estilo.TEXTO)
            typeface = fonte
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_HORIZONTAL
            includeFontPadding = false
            setShadowLayer(dp(6f), 0f, 0f, 0x99000000.toInt())
            alpha = 0f; visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(nomeApp, LayoutParams(dpi(BARRA_PASSO_APPS.toInt() + 40), LayoutParams.WRAP_CONTENT))
        strip = StripLayer(ctx).apply { visibility = View.GONE }
        addView(strip, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        rows = RowsLayer(ctx).apply { visibility = View.GONE }
        addView(rows, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnFocusChangeListener { _, tem -> posicionar(animar = false); anunciar(); atualizarDetalhe(); if (tem) dicaDepois() else esconderDica() }
    }

    /** Mostra as peças de um jeito e esconde as do outro (cruz: véu, nome do mundo, seta; barra: placa, realce, títulos). */
    private fun aplicarModo() {
        veu.visibility = if (barra || faixa) View.GONE else View.VISIBLE
        for (v in listOf(placa, realce, tituloApps, nomeApp)) v.visibility = if (barra && !faixa) View.VISIBLE else View.GONE
        cabecalho.alpha = if (barra || faixa) 0f else 1f
        if (barra || faixa) esconderDica()
        for (l in viewsItem) for (iv in l) textosDe(iv)?.alpha = if (barra) 0f else 1f
        // Barra única: as peças da cruz ficam fora (não desenham); só a camada dela aparece.
        val cruzVis = if (faixa) View.GONE else View.VISIBLE
        for (v in viewsCat) v.visibility = cruzVis
        for (l in viewsItem) for (iv in l) iv.raiz.visibility = cruzVis
        if (::cabecalho.isInitialized) cabecalho.visibility = if (faixa) View.GONE else View.VISIBLE
        strip.visibility = if (faixa && !fileiras) View.VISIBLE else View.GONE
        if (::rows.isInitialized) rows.visibility = if (faixa && fileiras) View.VISIBLE else View.GONE
        if (faixa) { detalhe.animate().cancel(); detalhe.alpha = 0f }
    }

    private fun textosDe(iv: ItemView): View? = iv.nome.parent?.parent as? View

    /**
     * Troca o conteúdo. Mesmos ids (só textos/ícones novos): atualiza no lugar. Ids diferentes: refaz as views uma vez e
     * mantém o lugar ([CruzLogica.reajustar]).
     */
    fun definir(novas: List<Categoria>) {
        // nome e "só o ícone" entram na assinatura: renomear ou esconder o nome refaz a linha das categorias (23/11/2026)
        val sig = novas.flatMap { c -> listOf("#" + c.cat.id + "|" + c.nome + "|" + c.soIcone) + c.itens.map { it.id } }
        val novoMapa = CruzLogica.Mapa(novas.map { it.cat }, novas.map { c -> c.itens.map { it.id } })
        if (sig == assinatura) {
            val antes = itemAtual()
            cats = novas
            for ((j, c) in novas.withIndex()) for ((i, it) in c.itens.withIndex()) preencher(viewsItem[j][i], it)
            if (faixa && !fileiras) strip.atualizarTexto(cats, estado.cat)
            // O item em foco mudou de texto ou ícone (canal novo, episódio novo): a arte e o TalkBack acompanham.
            val agora = itemAtual()
            if (agora != null && (antes?.nome != agora.nome || antes.sub != agora.sub || antes.icone !== agora.icone || antes.situacao != agora.situacao)) avisarMudanca()
            return
        }
        estado = if (assinatura == null) CruzLogica.inicial(novoMapa, lembrados, ultimoAberto?.substringAfter('|'))
            else CruzLogica.reajustar(mapa, estado, novoMapa)
        // Reordenando: o foco segue o card que andou
        movendo?.let { id -> CruzLogica.focar(novoMapa, estado, id, cats.getOrNull(estado.cat)?.cat)?.let { estado = it } }
        if (voltarPendente) ultimoAberto?.let { u ->
            CruzLogica.focar(novoMapa, estado, u.substringAfter('|'), CruzLogica.Categoria.entries.firstOrNull { it.id == u.substringBefore('|') })
                ?.let { estado = it; voltarPendente = false }
        }
        assinatura = sig
        cats = novas
        mapa = novoMapa
        for (v in viewsCat) removeView(v)
        for (l in viewsItem) for (v in l) removeView(v.raiz)
        viewsCat.clear(); viewsItem.clear()
        for (c in novas) {
            viewsCat += criarCategoria(c).also { addView(it, LayoutParams(dpi(150), LayoutParams.WRAP_CONTENT)) }
            viewsItem += c.itens.map { item -> criarItem().also { preencher(it, item); addView(it.raiz, LayoutParams(dpi(LARGURA_ITEM), dpi(ICONE_A + SUB_SOBRA))); if (barra) textosDe(it)?.alpha = 0f } }
        }
        if (faixa) aplicarModo()
        posicionar(animar = false)
        avisarMudanca()
    }

    /**
     * Voltando de um app (HOME ou VOLTAR): o foco vai para o item que abriu o último app, na categoria e posição dele
     * (dono, 18/11/2026; antes ia sempre para Assistir, quase sempre na TV). Se o item não está na cruz, fica onde estava.
     */
    fun voltarDoApp() {
        val u = ultimoAberto ?: return
        val cat = CruzLogica.Categoria.entries.firstOrNull { it.id == u.substringBefore('|') }
        // O app pode ainda não estar na coluna (aberto pela grade "Todos os apps": a coluna só o põe no topo quando a cruz
        // se refaz pelo último uso). Fica pendente e [atualizar] foca nele assim que ele aparecer.
        val e = CruzLogica.focar(mapa, estado, u.substringAfter('|'), cat) ?: run { voltarPendente = true; return }
        voltarPendente = false
        // Na barra, voltando de um app a linha de apps fica aberta no app que estava aberto (dá para ver onde se está).
        if (e == estado && (!barra || naApps)) return
        estado = e
        if (barra) naApps = true
        posicionar(animar = false); avisarMudanca()
    }

    private var voltarPendente = false

    /**
     * Reordenar (menu do card, como no Projectivy, 26/11/2026): com o id do card aqui, ◀ ▶ chamam [aoMover] (-1/+1, quem
     * usa grava a ordem nova e refaz a HOME; o foco segue o card) e OK, ▲ ▼ ou VOLTAR terminam ([pararDeMover]).
     */
    var movendo: String? = null
        private set
    private var aoMover: ((Int) -> Unit)? = null
    fun comecarAMover(id: String, mover: (Int) -> Unit) { movendo = id; aoMover = mover; avisoDeMover() }
    fun pararDeMover(): Boolean {
        if (movendo == null) return false
        movendo = null; aoMover = null
        if (fileiras) rows.fimDoAviso() else strip.aviso(itemAtual()?.nome.orEmpty(), 1)
        return true
    }
    private fun avisoDeMover() {
        val t = context.getString(R.string.rw_reordenar_dica)
        if (fileiras) rows.aviso(t, MOVER_AVISO_MS) else if (faixa) strip.aviso(t, MOVER_AVISO_MS)
        announceForAccessibility(t)
    }

    /**
     * Um app aberto FORA da cruz (grade "Todos os apps"): ao voltar, o foco vai nele, não em "Todos os apps"
     * (Paulo, 21/11/2026: "o destaque seja exatamente o último aplicativo selecionado"). Sem categoria: procura em todas.
     */
    fun marcarAberto(itemId: String) { ultimoAberto = "|$itemId" }

    /** HOME: Assistir; HOME de novo: a TV. */
    fun home() { if (barra && !faixa) teclaBarra(CruzLogica.Tecla.HOME) else aplicar(CruzLogica.Tecla.HOME) }

    fun itemAtual(): Item? = cats.getOrNull(estado.cat)?.itens?.getOrNull(estado.sel.getOrElse(estado.cat) { 0 })
    fun categoriaAtual(): Categoria? = cats.getOrNull(estado.cat)
    /** Os mundos montados agora, na ordem da linha (testes de tela). */
    fun categorias(): List<Categoria> = cats

    // ------------------------------------------------------------------ teclas

    /** Quantas vezes a tecla de agora já se repetiu (segurada): muda a mola e o som (ver [REPETICAO_RAPIDA]). */
    private var repeticao = 0

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        repeticao = event.repeatCount
        esconderDica()
        val t = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> CruzLogica.Tecla.ESQUERDA
            KeyEvent.KEYCODE_DPAD_RIGHT -> CruzLogica.Tecla.DIREITA
            KeyEvent.KEYCODE_DPAD_UP -> CruzLogica.Tecla.CIMA
            KeyEvent.KEYCODE_DPAD_DOWN -> CruzLogica.Tecla.BAIXO
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                event.startTracking()   // OK abre no SOLTAR; segurado vira opções (onKeyLongPress)
                return true
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        if (movendo != null) {
            when (t) {
                CruzLogica.Tecla.ESQUERDA -> { if (repeticao == 0 || repeticao % 3 == 0) aoMover?.invoke(-1); avisoDeMover() }
                CruzLogica.Tecla.DIREITA -> { if (repeticao == 0 || repeticao % 3 == 0) aoMover?.invoke(+1); avisoDeMover() }
                else -> pararDeMover()
            }
            return true
        }
        if (faixa) return teclaFaixa(t)
        if (barra) return teclaBarra(t)
        if (t == CruzLogica.Tecla.CIMA && CruzLogica.passo(mapa, estado, t) is CruzLogica.Resultado.SubirTopo) {
            aoSubirTopo?.invoke(repeticao)
            return false   // o Android procura o foco acima (a barra de cima)
        }
        aplicar(t)
        return true
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER && keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) return false
        if (barra && !faixa && !naApps) return false   // na linha das categorias o OK só abre a linha de apps
        val op = itemAtual()?.opcoes ?: return false   // sem opções: o soltar abre normalmente
        op()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (!ok) return super.onKeyUp(keyCode, event)
        if (movendo != null) { pararDeMover(); return true }   // OK termina o reordenar (não abre o app)
        if (event.isTracking && !event.isCanceled) { if (barra && !faixa) teclaBarra(CruzLogica.Tecla.OK) else aplicar(CruzLogica.Tecla.OK) }
        return true
    }

    /**
     * Uma tecla na barra embaixo: [BottomBar.passo] traduz para a tecla da cruz (◀ ▶ nos apps = ▲ ▼ da coluna), abre
     * ou fecha a linha de apps. Devolve false só no ▲ que sai para a barra de cima (o Android procura o foco acima).
     */
    private fun teclaBarra(t: CruzLogica.Tecla): Boolean {
        val p = BottomBar.passo(t, naApps, estado.sel.getOrElse(estado.cat) { 0 })
        if (p.subirTopo) { aoSubirTopo?.invoke(repeticao); return false }
        val mudouLinha = p.naApps != naApps
        naApps = p.naApps
        if (p.tecla != null) aplicar(p.tecla)
        if (mudouLinha) {
            if (repeticao == 0) aoSom?.invoke(SonsCruz.Som.ANDAR, categoriaAtual()?.cat)
            rigidez = MOLA_W; amortecer = MOLA_AMORTECE; semOnda = false
            posicionar(animar = true); anunciar(); atualizarDetalhe()
        } else if (p.tecla == null && repeticao == 0) aoSom?.invoke(SonsCruz.Som.BORDA, categoriaAtual()?.cat)
        return true
    }

    /**
     * Uma tecla na barra única: [StripHome.passo] traduz (◀ ▶ = ▲ ▼ da coluna; ▲ ▼ = ◀ ▶ das categorias). Devolve false
     * só no ▲ do 1º mundo, que sai para a barra de cima (o Android procura o foco acima).
     */
    private fun teclaFaixa(t: CruzLogica.Tecla): Boolean {
        val sel = estado.sel.getOrElse(estado.cat) { 0 }
        val n = categoriaAtual()?.itens?.size ?: 0
        // Grade: a barra tem a de 2 linhas do jeito dos cards; as Fileiras, as linhas do mundo (menu do mundo)
        val linhas = categoriaAtual()?.let { if (fileiras) WorldOptions.of(context, it.cat).lines else if (strip.formato(it.cat).grade) 2 else 1 } ?: 1
        val p = StripHome.passo(t, estado.cat, sel, n, linhas = linhas)
        if (p.subirTopo) { aoSubirTopo?.invoke(repeticao); return false }
        p.paraSel?.let { irParaFaixa(it); return true }
        val tecla = p.tecla
        if (tecla == null) { if (repeticao == 0) aoSom?.invoke(SonsCruz.Som.BORDA, categoriaAtual()?.cat); return true }
        direcaoFaixa = when (tecla) { CruzLogica.Tecla.ESQUERDA -> -1; CruzLogica.Tecla.DIREITA -> 1; else -> 0 }
        aplicar(tecla)
        direcaoFaixa = 0
        return true
    }

    /** Grade de 2 linhas: pula direto para o card [alvo] do mesmo mundo (o mesmo som e aviso de um passo comum). */
    private fun irParaFaixa(alvo: Int) {
        val c = estado.cat
        if (alvo == estado.sel.getOrElse(c) { 0 }) return
        val rapida = repeticao >= REPETICAO_RAPIDA
        if (!rapida || repeticao % SOM_A_CADA == 0) aoSom?.invoke(SonsCruz.Som.ANDAR, categoriaAtual()?.cat)
        estado = estado.copy(sel = estado.sel.toMutableList().also { while (it.size <= c) it += 0; it[c] = alvo })
        posicionar(animar = true)
        avisarMudanca()
    }

    private fun aplicar(t: CruzLogica.Tecla) {
        val r = CruzLogica.passo(mapa, estado, t)
        if (r is CruzLogica.Resultado.Abrir) {
            aoSom?.invoke(SonsCruz.Som.ABRIR, categoriaAtual()?.cat)
            ultimoAberto = r.categoria.id + "|" + r.item
            aoAbrir?.invoke(r.categoria, r.item)
            apertar()
            // Um app que não abre (desinstalado no meio, sem tela de TV, recusado pelo Android) não falha em silêncio.
            try { itemAtual()?.abrir?.invoke() } catch (e: Exception) { naoAbriu() }
            return
        }
        val rapida = t != CruzLogica.Tecla.HOME && repeticao >= REPETICAO_RAPIDA
        val soar = !rapida || repeticao % SOM_A_CADA == 0
        if (r.estado == estado) {
            if (t != CruzLogica.Tecla.HOME && repeticao == 0) aoSom?.invoke(SonsCruz.Som.BORDA, categoriaAtual()?.cat)   // fim da coluna/linha
            return
        }
        val trocouCategoria = r.estado.cat != estado.cat
        if (faixa && t == CruzLogica.Tecla.HOME && trocouCategoria) direcaoFaixa = if (r.estado.cat < estado.cat) -1 else 1
        if (soar) aoSom?.invoke(if (trocouCategoria) SonsCruz.Som.GRUPO else SonsCruz.Som.ANDAR, cats.getOrNull(r.estado.cat)?.cat)
        estado = r.estado
        amortecer = if (trocouCategoria) MOLA_AMORTECE_CATEGORIA else MOLA_AMORTECE
        rigidez = if (rapida) MOLA_W * RAPIDA_W else MOLA_W
        semOnda = rapida
        posicionar(animar = true)
        avisarMudanca()
        if (t != CruzLogica.Tecla.HOME && !rapida) aoPasso?.invoke(t)
    }

    // Parâmetros da mola para o passo de agora (lidos por [mover]).
    private var amortecer = MOLA_AMORTECE
    private var rigidez = MOLA_W
    private var semOnda = false

    /** O ícone do item escolhido "aperta" e volta: o toque foi entendido, no mesmo quadro. */
    private fun apertar() {
        if (faixa && fileiras) { rows.apertar(estado.cat, estado.sel.getOrElse(estado.cat) { 0 }); return }
        if (faixa) { strip.apertar(estado.sel.getOrElse(estado.cat) { 0 }); return }
        val iv = viewAtual() ?: return
        if (semAnimacao()) return
        iv.icone.animate().cancel()
        iv.icone.pivotX = iv.icone.width / 2f; iv.icone.pivotY = iv.icone.height / 2f
        iv.icone.animate().scaleX(APERTO).scaleY(APERTO).setDuration(APERTO_MS).withEndAction {
            iv.icone.animate().scaleX(1f).scaleY(1f).setDuration(APERTO_MS * 3)
                .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
        }.start()
    }

    /**
     * Quem usa avisa que o item não abriu (ou a abertura deu erro): a linha de baixo vira "Não deu para abrir" na cor de
     * destaque por [NAO_ABRIU_MS], e o leitor de tela fala. Depois volta ao texto de antes.
     */
    fun naoAbriu() {
        if (faixa) { context.getString(R.string.cruz_nao_abriu).let { if (fileiras) rows.aviso(it, NAO_ABRIU_MS) else strip.aviso(it, NAO_ABRIU_MS); announceForAccessibility(it) }; return }
        val iv = viewAtual() ?: return
        val aviso = context.getString(R.string.cruz_nao_abriu)
        iv.sub.text = aviso
        iv.sub.setTextColor(Estilo.ACENTO)
        iv.sub.visibility = View.VISIBLE
        announceForAccessibility(aviso)
        iv.sub.removeCallbacks(iv.voltarSub)
        iv.voltarSub = Runnable {
            // o texto de AGORA (o item pode ter mudado nesses 4 s: programa novo, minutos que passaram)
            val agora = iv.principal
            iv.sub.setTextColor(COR_SUB)
            iv.sub.text = agora
            iv.sub.visibility = if (agora.isEmpty()) View.GONE else View.VISIBLE
        }
        iv.sub.postDelayed(iv.voltarSub, NAO_ABRIU_MS)
    }

    // ---- dica de "tem mais embaixo" (idosos acham a coluna mais rápido com dica de seta; notas do estudo, IJHCI 2018)

    private val mostrarDica = Runnable {
        val n = categoriaAtual()?.itens?.size ?: 0
        val sel = estado.sel.getOrElse(estado.cat) { 0 }
        val tem = !barra && !faixa && sel + ALFA_COLUNA.keys.max() < n - 1 && (hasFocus() || isFocused)
        maisAbaixo.animate().cancel()
        if (tem) maisAbaixo.animate().alpha(0.8f).setDuration(if (semAnimacao()) 0L else 300L).start() else maisAbaixo.alpha = 0f
    }

    private fun dicaDepois() { removeCallbacks(mostrarDica); postDelayed(mostrarDica, DICA_MS) }

    private fun esconderDica() { removeCallbacks(mostrarDica); maisAbaixo.animate().cancel(); maisAbaixo.alpha = 0f }

    /** Um "v" de traço arredondado, na cor da linha de baixo. Desenhado à mão: nenhuma fonte garante o glifo ▾. */
    private class Seta(ctx: Context) : View(ctx) {
        private val tinta = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = COR_SUB
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            setShadowLayer(4f, 0f, 0f, 0x99000000.toInt())
        }
        private val caminho = android.graphics.Path()
        override fun onDraw(c: android.graphics.Canvas) {
            val e = height * 0.28f
            tinta.strokeWidth = e
            caminho.reset()
            caminho.moveTo(e, e); caminho.lineTo(width / 2f, height - e); caminho.lineTo(width - e, e)
            c.drawPath(caminho, tinta)
        }
    }

    /** Para o teste de tela: o texto de baixo do item em foco, como está desenhado. */
    internal fun subAtual(): TextView? = viewAtual()?.sub
    internal fun nomeAtual(): TextView? = viewAtual()?.nome

    private fun viewAtual(): ItemView? = viewsItem.getOrNull(estado.cat)?.getOrNull(estado.sel.getOrElse(estado.cat) { 0 })

    private fun avisarMudanca() {
        val c = categoriaAtual() ?: return
        val i = itemAtual() ?: return
        rotulo.text = c.nome   // régua 22/11/2026: sem maiúsculas
        val n = c.itens.size
        val sel = estado.sel.getOrElse(estado.cat) { 0 }
        posicao.text = if (n > 1) context.getString(R.string.cruz_posicao, sel + 1, n) else ""
        dicaDepois()
        i.aoEscolher?.invoke()
        aoMudar?.invoke(c, i)
        anunciar()
        atualizarDetalhe()
    }

    // ---- detalhes no canto de baixo

    private val mostrarDetalhe = Runnable {
        if (faixa || (barra && !naApps)) { detalhe.animate().cancel(); detalhe.alpha = 0f; detalhe.text = ""; return@Runnable }
        val i = itemAtual()
        val iv = viewAtual()
        val linhas = if (i == null || iv == null || !(hasFocus() || isFocused)) emptyList() else {
            val p = CruzLogica.partes(i.sub, CruzLogica.tirarDoItem(i.situacao))
            val cortada = p.principal.isNotEmpty() && android.text.StaticLayout.Builder
                .obtain(p.principal, 0, p.principal.length, iv.sub.paint, dpi(TEXTO_MAX)).setIncludePad(false).build().lineCount > ITEM_LINHAS
            // Barra embaixo: a linha de baixo do item não aparece; sem isto o título ("Faces da Verdade") sumia da tela (Sala 24/11/2026).
            CruzLogica.detalhes(p, principalCortada = cortada || barra)
        }
        val s = i?.situacao?.takeIf { it.texto.isNotBlank() && iv != null && (hasFocus() || isFocused) }
        // Nome que não coube ("What universal fre…", visto na Sala 18/11/2026 no Continuar): vai inteiro no topo do canto.
        val nomeInteiro = i?.nome?.takeIf { n -> iv != null && (hasFocus() || isFocused) &&
            android.text.TextPaint(iv.nome.paint).apply {
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, FONTE_ESCOLHIDO, resources.displayMetrics)
            }.measureText(n) > dpi(TEXTO_MAX) }
        detalhe.animate().cancel()
        if (linhas.isEmpty() && s == null && nomeInteiro == null) { detalhe.alpha = 0f; detalhe.text = ""; return@Runnable }
        // 1ª linha: a situação, com o rótulo em negrito (laranja é só marca, foco e progresso; estudo global 20/11) ("Parou em  T1 E1 · 35% · faltam 43 min"); depois os detalhes.
        val texto = android.text.SpannableStringBuilder().apply {
            if (nomeInteiro != null) {
                append(nomeInteiro, android.text.style.ForegroundColorSpan(Estilo.TEXTO), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (s != null || linhas.isNotEmpty()) append("\n")
            }
            if (s != null) {
                val ini = length
                append(s.rotulo, android.text.style.ForegroundColorSpan(Estilo.TEXTO), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), ini, length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                append("  ").append(CruzLogica.colar(s.texto))
                if (linhas.isNotEmpty()) append("\n")
            }
            append(linhas.joinToString("\n") { CruzLogica.colar(it) })
        }
        if (detalhe.text.toString() != texto.toString()) detalhe.text = texto
        detalhe.animate().alpha(1f).setDuration(if (semAnimacao()) 0L else 220L).start()
    }

    /** O foco mudou (ou o texto do item): os detalhes somem na hora e voltam quando o foco para ([DETALHE_ESPERA_MS]). */
    private fun atualizarDetalhe() {
        removeCallbacks(mostrarDetalhe)
        detalhe.animate().cancel()
        detalhe.alpha = 0f
        postDelayed(mostrarDetalhe, if (semAnimacao()) 0L else DETALHE_ESPERA_MS)
    }

    /** Para o teste de tela: os detalhes do item em foco, como estão no canto de baixo. */
    internal fun detalheAtual(): TextView = detalhe

    // ------------------------------------------------------------------ desenho

    private fun criarCategoria(c: Categoria): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(ImageView(context).apply {
            setImageDrawable(c.icone)
            if (CrossRhythm.atual.quadro) { background = quadro(); setPaddingRelative(dpi(10), dpi(8), dpi(10), dpi(8)) }
        }, LinearLayout.LayoutParams(dpi(CAT_L), dpi(CAT_A)))
        addView(TextView(context).apply {
            text = c.nome
            setTextSize(TypedValue.COMPLEX_UNIT_SP, FONTE_CATEGORIA)
            setTextColor(COR_CATEGORIA)
            typeface = fonteForte
            maxLines = 1
            includeFontPadding = false
            gravity = Gravity.CENTER_HORIZONTAL
            setShadowLayer(dp(6f), 0f, 0f, 0x99000000.toInt())
            if (c.soIcone) visibility = View.INVISIBLE
        }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(6) })
        if (c.soIcone) contentDescription = c.nome   // leitor de tela continua dizendo o nome
        // a altura vem do conteúdo; o centro do ícone é que fica na linha
        pivotX = dp(75f); pivotY = dp(CAT_A / 2f)
    }

    private fun criarItem(): ItemView {
        val icone = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            // Halo radial atrás do ícone (só existe com brilho ligado; apagado até o item ser o escolhido).
            val halo = if (brilho > 0f) GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dp(ICONE_A / 2f)
                colors = intArrayOf(Estilo.ACENTO, Estilo.alfa(Estilo.ACENTO, 0))
                alpha = 0
            } else null
            // Ritmo B "Quadros": o ícone num quadro igual para todos (o halo, se houver, fica por cima do quadro).
            background = if (CrossRhythm.atual.quadro) {
                setPaddingRelative(dpi(12), dpi(10), dpi(12), dpi(10))
                if (halo != null) android.graphics.drawable.LayerDrawable(arrayOf(quadro(), halo)) else quadro()
            } else halo
        }
        val nome = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, FONTE_ESCOLHIDO)
            setTextColor(Estilo.TEXTO)
            typeface = fonteForte
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            maxWidth = dpi(TEXTO_MAX)
            setShadowLayer(dp(8f), 0f, 0f, 0x99000000.toInt())
        }
        // O traço do escolhido (Paulo, 18/11/2026: ponto à esquerda do nome; 22/11/2026: traço VERTICAL curto de pontas
        // redondas, para não parecer a barra de progresso). Laranja no foco Fio; creme no Véu e no Quadro.
        val traco = View(context).apply { background = tracoDoFoco() }
        val sub = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, FONTE_SUB)
            setTextColor(COR_SUB)   // creme claro (contraste > 4,5:1 sobre o véu)
            typeface = fonte
            // Uma informação, até 2 linhas (o resto vai para o canto de baixo: [detalhe]).
            maxLines = ITEM_LINHAS
            setLineSpacing(dp(3f), 1f)
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            setLineSpacing(dp(3f), 1f)
            setShadowLayer(dp(6f), 0f, 0f, 0x99000000.toInt())
        }
        val linhaNome = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(traco, LinearLayout.LayoutParams(dpi(TRACO_L), dpi(TRACO_A)).apply { marginEnd = dpi(PONTO + 10 - TRACO_L) })
            addView(nome, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        val textos = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPaddingRelative(0, dpi(TEXTO_TOPO), 0, 0)
            addView(linhaNome, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            // o texto de baixo começa embaixo do nome (alinhado com ele, não com o ponto)
            addView(sub, LinearLayout.LayoutParams(dpi(TEXTO_MAX), LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(8); marginStart = dpi(PONTO + 10) })
        }
        // Sem "sobreposição" (20/11/2026): com transparência < 1 (os vizinhos apagados da coluna, 0,6 a 0,9) o Android
        // desenhava o item inteiro numa camada à parte e depois a colava, a CADA quadro em que algo se mexia na cruz
        // (medido na Sala: ~1,5 camadas de 940 x 340 px por quadro, "alpha caused saveLayer"). O ícone e os textos ficam
        // lado a lado, então aplicar a transparência em cada desenho dá o mesmo resultado; a única diferença é a sombra
        // do texto aparecer sob a letra transparente, sobre o véu escuro (~1% de brilho, invisível).
        val raiz = object : LinearLayout(context) {
            override fun hasOverlappingRendering() = false
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            clipChildren = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(icone, LinearLayout.LayoutParams(dpi(ICONE_L), dpi(ICONE_A)))
            addView(textos, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply { marginStart = dpi(CrossRhythm.atual.vaoTexto) })
            pivotX = 0f; pivotY = dp(ICONE_A / 2f)
            visibility = View.INVISIBLE
            alpha = 0f
        }
        return ItemView(raiz, icone, nome, sub, traco).also { iv ->
            // A informação quebrou em 2 linhas (ou voltou a 1): a coluna se rearruma.
            sub.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val n = sub.lineCount.coerceIn(1, ITEM_LINHAS)
                if (n != iv.linhas) { iv.linhas = n; post { posicionar(animar = true) } }
            }
        }
    }

    private fun preencher(v: ItemView, item: Item) {
        if (v.icone.drawable !== item.icone) v.icone.setImageDrawable(item.icone)
        if (v.nome.text.toString() != item.nome) v.nome.text = item.nome
        val principal = CruzLogica.partes(item.sub, CruzLogica.tirarDoItem(item.situacao)).principal
        if (v.principal != principal || v.sub.text.toString() != principal) { v.principal = principal; v.sub.text = principal }
        v.sub.visibility = if (principal.isEmpty()) View.GONE else View.VISIBLE
        val p = item.progresso
        if (p == null) { if (v.icone.foreground != null) v.icone.foreground = null }
        else {
            val b = v.barra ?: BarraProgresso().also { v.barra = it }
            b.valor = p
            if (v.icone.foreground !== b) v.icone.foreground = b
        }
    }

    private fun onda(distancia: Int) = if (semOnda) 0L else minOf(distancia * ONDA_MS, ONDA_MAX_MS)

    override fun onWindowFocusChanged(temFoco: Boolean) {
        super.onWindowFocusChanged(temFoco)
        posicionar(animar = false)   // o ponto do escolhido some com um menu por cima e volta quando ele fecha
    }

    /** Põe cada view no lugar do estado atual (animado ou de uma vez). */
    private fun posicionar(animar: Boolean) {
        if (faixa && fileiras) {
            rows.nivel = if (semAnimacao()) MovimentoAdaptativo.Nivel.PARADO else nivel
            rows.lembrar(estado.sel)
            rows.mostrar(cats, estado.cat, estado.sel.getOrElse(estado.cat) { 0 }, hasFocus() || isFocused, animar)
            return
        }
        if (faixa) {
            strip.nivel = if (semAnimacao()) MovimentoAdaptativo.Nivel.PARADO else nivel
            strip.mostrar(cats, estado.cat, estado.sel.getOrElse(estado.cat) { 0 }, hasFocus() || isFocused, animar, direcaoFaixa)
            return
        }
        if (barra) { posicionarBarra(animar); return }
        posicionarRotulo(animar)
        val f = estado.cat
        val focada = hasFocus() || isFocused
        val semMovimento = semAnimacao()
        semMov = semMovimento   // lido por [mover] em cada view: uma leitura do ajuste por arrumação, não ~40
        val nivelAgora = if (semMovimento) MovimentoAdaptativo.Nivel.PARADO else nivel
        val descEsc = MovimentoAdaptativo.descansoEscolhido(nivelAgora)
        for ((j, v) in viewsCat.withIndex()) {
            val k = j - f
            val cx = when {
                k == 0 -> COLUNA
                k < 0 -> CAT_ESQUERDA + (k + 1) * PASSO_CATS
                else -> CAT_DIREITA + (k - 1) * PASSO_CATS
            }
            val visivel = k != 0 && cx > 30f && cx < TELA_L - 40f
            mover(v, dp(cx - 75f), dp(LINHA - CAT_A / 2f), if (visivel) CrossRhythm.atual.alfaCategoria else 0f, 1f, animar, onda(kotlin.math.abs(k)))
            // As categorias dos lados tocam a animação delas de vez em quando, uma de cada vez (atraso pela posição).
            // Quanto: MovimentoAdaptativo (em ciclo, uma vez ao chegar, ou nada), conforme o aparelho aguenta.
            val descCat = MovimentoAdaptativo.descansoCategoria(nivelAgora)
            ((v as? ViewGroup)?.getChildAt(0) as? ImageView)?.drawable.let { it as? IconeCruz }
                ?.animar(visivel && focada && descCat != null, descCat ?: IconeCruz.DESCANSO_CATEGORIA_MS, MovimentoAdaptativo.atrasoCategoria(nivelAgora, j))
        }
        for ((j, lista) in viewsItem.withIndex()) {
            val k = j - f
            val sel = estado.sel.getOrElse(j) { 0 }
            // A informação do escolhido em 2 linhas: os vizinhos de baixo descem a linha a mais.
            val extra = lista.getOrNull(sel)?.takeIf { k == 0 && it.sub.visibility == View.VISIBLE }
                ?.let { (it.linhas - 1).coerceAtLeast(0) * it.sub.lineHeight / dm.density } ?: 0f
            for ((i, iv) in lista.withIndex()) {
                val rel = i - sel
                if (k != 0) {
                    // categoria fora de foco: os itens se recolhem no ícone dela e somem
                    val cx = if (k < 0) CAT_ESQUERDA + (k + 1) * PASSO_CATS else CAT_DIREITA + (k - 1) * PASSO_CATS
                    mover(iv.raiz, dp(cx - ICONE_L * ESCALA_PEQUENO / 2f), dp(LINHA - ICONE_A / 2f), 0f, ESCALA_PEQUENO, animar, onda(kotlin.math.abs(rel)))
                    if (iv.icone.isActivated) iv.icone.isActivated = false
                    (iv.icone.drawable as? IconeCruz)?.animar(false)
                    continue
                }
                val cy = when {
                    rel == 0 -> LINHA
                    rel < 0 -> LINHA - VAO_CIMA + (rel + 1) * PASSO_COLUNA
                    else -> LINHA + VAO_ESCOLHIDO + extra + (rel - 1) * PASSO_COLUNA
                }
                val alfa = if (rel == 0) (if (focada) 1f else ALFA_ESCOLHIDO_FORA) else ALFA_COLUNA[rel] ?: 0f
                val escala = if (rel == 0) 1f else ESCALA_PEQUENO
                val x = if (rel == 0) COLUNA - ICONE_L / 2f else COLUNA - ICONE_L * ESCALA_PEQUENO / 2f
                mover(iv.raiz, dp(x), dp(cy - ICONE_A / 2f), alfa, escala, animar, onda(kotlin.math.abs(rel)))
                // Sem o ponto quando outra janela (menu do app, lista) está por cima: um foco só na tela (crítica 19/11/2026).
                // Com o painel do Ajuste fino por cima, o ponto e o brilho ficam: é justamente o que se está ajustando.
                val semMenu = hasWindowFocus() || br.paulo.tvlauncher.FineTuneActivity.aberto
                iv.traco.alpha = if (rel == 0 && focada && semMenu) 1f else 0f
                if (rel == 0 && focada) iv.traco.background = tracoDoFoco()
                haloDe(iv.icone)?.alpha = if (rel == 0 && focada && semMenu) (brilho * 255).toInt() else 0
                // Ícones "Linha" (IconeCruz): o escolhido toca a animação dele (a cor é de cada ícone, sempre).
                val aceso = rel == 0 && focada
                if (iv.icone.isActivated != aceso) iv.icone.isActivated = aceso
                (iv.icone.drawable as? IconeCruz)?.animar(aceso && descEsc != null, descEsc ?: IconeCruz.DESCANSO_ESCOLHIDO_MS, if (animar) ICONE_ESPERA_MS else 0L)
                iv.sub.alpha = if (rel == 0) 1f else 0f
                textosDe(iv)?.let { if (it.alpha != 1f) it.alpha = 1f }
                nomeNoMeio(iv, rel != 0, animar)
            }
        }
    }

    /** Altura da tela em dp (a TV é 960 x 540; antes de medir, vale isso). */
    private fun telaA() = if (height > 0) height / dm.density else TELA_A

    /**
     * A barra embaixo ([BottomBar]): as categorias espalhadas por igual na placa de baixo (largura toda) e, com a linha
     * aberta, os apps da categoria numa linha acima dela, o escolhido inteiro e os outros em 80%. Mesmas molas da cruz.
     */
    private fun posicionarBarra(animar: Boolean) {
        val f = estado.cat
        val focada = hasFocus() || isFocused
        val semMovimento = semAnimacao()
        semMov = semMovimento
        val nivelAgora = if (semMovimento) MovimentoAdaptativo.Nivel.PARADO else nivel
        val descEsc = MovimentoAdaptativo.descansoEscolhido(nivelAgora)
        val n = viewsCat.size.coerceAtLeast(1)
        val largura = TELA_L - 2 * BARRA_MARGEM
        val slot = largura / n
        val yPlaca = telaA() - BARRA_BAIXO - BARRA_PLACA_A
        placa.translationX = dp(BARRA_MARGEM); placa.translationY = dp(yPlaca)
        val catTopo = yPlaca + (BARRA_PLACA_A - (CAT_A + 30f)) / 2f
        fun centro(j: Int) = BARRA_MARGEM + slot * (j + 0.5f)
        for ((j, v) in viewsCat.withIndex()) {
            val sel = j == f
            mover(v, dp(centro(j) - 75f), dp(catTopo), if (sel) 1f else BARRA_ALFA_CAT, 1f, animar)
            // Escolhida: nome na cor da categoria sobre o fundo leve dela (Quizlet/Tubi no Mobbin); as outras em creme.
            val cor = if (sel) BottomBar.cor(cats.getOrNull(j)?.cat) else COR_CATEGORIA
            ((v as? ViewGroup)?.getChildAt(1) as? TextView)?.let { if (it.currentTextColor != cor) it.setTextColor(cor) }
            // O ícone fica com as cores dele (conjuntos Vintage, Joia e Fotorrealista são coloridos); só o nome muda de cor.
            ((v as? ViewGroup)?.getChildAt(0) as? ImageView)?.let { iv ->
                (iv.drawable as? IconeCruz)?.animar(sel && focada && !naApps && descEsc != null, descEsc ?: IconeCruz.DESCANSO_ESCOLHIDO_MS)
            }
        }
        // O realce da escolhida: fundo leve na cor dela; o anel laranja só com o foco na barra (linha de apps fechada).
        val rl = minOf(slot - 12f, 176f)
        (realce.layoutParams as LayoutParams).let { if (it.width != dpi(rl.toInt())) { it.width = dpi(rl.toInt()); realce.layoutParams = it } }
        val corCat = BottomBar.cor(cats.getOrNull(f)?.cat)
        val anel = focada && !naApps && (hasWindowFocus() || br.paulo.tvlauncher.FineTuneActivity.aberto)
        realce.background = GradientDrawable().apply {
            cornerRadius = dp(br.paulo.tvlauncher.Estilo.RAIO.toFloat())
            setColor(Estilo.alfa(corCat, 0x29))
            if (anel) setStroke(dpi(3), Estilo.ACENTO)
        }
        mover(realce, dp(centro(f) - rl / 2f), dp(yPlaca + 8f), 1f, 1f, animar)
        // A linha de apps: o pé do ícone escolhido fica BARRA_APPS_VAO acima da placa.
        val topoApps = yPlaca - BARRA_APPS_VAO - ICONE_A
        val cabem = (largura / BARRA_PASSO_APPS).toInt().coerceAtLeast(1)
        for ((j, lista) in viewsItem.withIndex()) {
            val sel = estado.sel.getOrElse(j) { 0 }
            val aberta = naApps && j == f
            val ini = BottomBar.primeiro(sel, lista.size, cabem)
            for ((i, iv) in lista.withIndex()) {
                iv.traco.alpha = 0f
                iv.sub.alpha = 0f
                haloDe(iv.icone)?.alpha = 0
                textosDe(iv)?.let { if (it.alpha != 0f) it.alpha = 0f }
                if (!aberta) {
                    // linha fechada: os apps se recolhem na categoria deles e somem
                    mover(iv.raiz, dp(centro(j) - ICONE_L * BARRA_ESCALA_APP / 2f), dp(catTopo), 0f, BARRA_ESCALA_APP, animar)
                    if (iv.icone.isActivated) iv.icone.isActivated = false
                    (iv.icone.drawable as? IconeCruz)?.animar(false)
                    continue
                }
                val escolhido = i == sel
                val visivel = i - ini in 0 until cabem
                val esc = if (escolhido) 1f else BARRA_ESCALA_APP
                val cx = BARRA_MARGEM + BARRA_PASSO_APPS * (i - ini + 0.5f)
                // pivô do item = (0, meio do ícone): x é a borda esquerda do ícone já na escala
                val alfa = if (!visivel) 0f else if (escolhido) (if (focada) 1f else ALFA_ESCOLHIDO_FORA) else BARRA_ALFA_APP
                mover(iv.raiz, dp(cx - ICONE_L * esc / 2f), dp(topoApps), alfa, esc, animar, onda(kotlin.math.abs(i - sel)))
                val aceso = escolhido && focada
                if (iv.icone.isActivated != aceso) iv.icone.isActivated = aceso
                (iv.icone.drawable as? IconeCruz)?.animar(aceso && descEsc != null, descEsc ?: IconeCruz.DESCANSO_ESCOLHIDO_MS, if (animar) ICONE_ESPERA_MS else 0L)
                if (escolhido) {
                    nomeApp.text = cats.getOrNull(j)?.itens?.getOrNull(i)?.nome.orEmpty()
                    nomeApp.translationX = dp(cx) - (nomeApp.layoutParams.width / 2f)
                    nomeApp.translationY = dp(topoApps + ICONE_A + 8f)
                }
            }
        }
        // "Ouvir ›" acima da linha (Apple Music no Mobbin) e o nome do escolhido embaixo dele: só com a linha aberta.
        tituloApps.text = cats.getOrNull(f)?.nome?.let { "$it  ›" }.orEmpty()
        tituloApps.translationX = dp(BARRA_MARGEM + 8f)
        tituloApps.translationY = dp(topoApps - 44f)
        val alvo = if (naApps) 1f else 0f
        for (v in listOf(tituloApps, nomeApp)) {
            if (v.alpha == alvo) continue
            if (animar && !semMovimento) v.animate().alpha(alvo).setDuration(if (naApps) 220L else 120L).start()
            else { v.animate().cancel(); v.alpha = alvo }
        }
    }

    /** Para o teste de tela da barra única: a linha do mundo, a descrição e os cards visíveis. */
    internal fun linhaDoMundo(): View = strip.linhaDoMundo()
    internal fun descricaoDoMundo(): TextView = strip.descricao()
    internal fun cardsDaFaixa(): List<View> = if (fileiras) rows.cardsVisiveis() else strip.cardsVisiveis()
    /** Para o teste de tela das Fileiras: o nome grande do card em foco. */
    internal fun nomeDoFoco(): TextView = rows.nomeDoFoco()

    /** Para o teste de tela: a linha de apps da barra está aberta. */
    internal fun appsAbertos(): Boolean = barra && naApps

    /** Para o teste de tela: o nome do mundo em foco ("ASSISTIR  1 de 4"). */
    internal fun rotuloAtual(): View = cabecalho

    /** Nome do mundo na coluna (false = ele mora na pílula do topo, [WorldLabel]). Some sem mudar a posição de nada. */
    fun nomeNaColuna(mostrar: Boolean) { cabecalho.visibility = if (mostrar) View.VISIBLE else View.INVISIBLE }

    /**
     * Nome do mundo ("ASSISTIR  1 de 4") logo acima do que está em cima na coluna (ritmos novos, 22/11/2026).
     * Na Sala, com o ritmo A o nome ficava fixo a 132 dp e, a partir do 2º item, caía em cima do vizinho de cima
     * ("FerramentasAlarme"). Agora ele fica 12 dp acima do vizinho de cima quando há um, e 12 dp acima do escolhido
     * quando é o 1º item. O ritmo de antes mantém o lugar fixo dele (que já ficava acima do vizinho).
     */
    private fun posicionarRotulo(animar: Boolean) {
        if (!::cabecalho.isInitialized) return
        if (CrossRhythm.atual == CrossRhythm.BEFORE) { cabecalho.translationY = dp(ROTULO_Y); return }
        // Posição FIXA (22/11/2026, Paulo na Sala: "o nome do mundo se move, tem que ficar numa posição segura e fixa").
        // Fica sempre acima do lugar do vizinho de cima, o ponto mais alto que a coluna ocupa; com o 1º item escolhido
        // sobra ar entre o nome e o ícone, mas o nome não anda ao trocar de item.
        val altura = if (cabecalho.height > 0) cabecalho.height.toFloat() else dp(30f)
        val base = LINHA - VAO_CIMA - ICONE_A * ESCALA_PEQUENO / 2f
        val alvo = dp(base - ROTULO_VAO) - altura
        if (cabecalho.translationY == alvo) return
        if (animar && !semAnimacao()) cabecalho.animate().translationY(alvo).setDuration(br.paulo.tvlauncher.DesignTokens.MS_FAST).setInterpolator(curvaRotulo).start()
        else { cabecalho.animate().cancel(); cabecalho.translationY = alvo }
    }

    /** Quadro do ritmo B: o mesmo retângulo suave atrás de cada ícone, para desenhos diferentes terem o mesmo peso. */
    private fun quadro() = GradientDrawable().apply {
        cornerRadius = dp(Estilo.RAIO_PEQUENO.toFloat())
        setColor(0x14FFFFFF)
        setStroke(dpi(1), 0x1FFFFFFF)
    }

    private fun haloDe(v: ImageView): android.graphics.drawable.Drawable? = when (val b = v.background) {
        is android.graphics.drawable.LayerDrawable -> b.getDrawable(b.numberOfLayers - 1).takeIf { b.numberOfLayers > 1 }
        is GradientDrawable -> b.takeIf { !CrossRhythm.atual.quadro }
        else -> null
    }

    /**
     * Item pequeno com o nome no meio da altura do ícone (ritmos novos, 21/11/2026): no alto do ícone ele parecia torto
     * na foto da Sala. O escolhido mantém o nome no alto, porque embaixo dele vem a linha de informação.
     */
    private fun nomeNoMeio(iv: ItemView, pequeno: Boolean, animar: Boolean) {
        val textos = iv.nome.parent?.parent as? View ?: return
        // Altura da letra pela fonte (a view pode ainda não ter sido medida na 1ª arrumação).
        val fm = iv.nome.paint.fontMetrics
        val alvo = if (pequeno && CrossRhythm.atual.nomeNoMeio)
            (dp(ICONE_A.toFloat()) - (fm.descent - fm.ascent)) / 2f - dp(TEXTO_TOPO.toFloat()) else 0f
        if (textos.translationY == alvo) return
        if (animar && !semMov) textos.animate().translationY(alvo).setDuration(br.paulo.tvlauncher.DesignTokens.MS_FAST).setInterpolator(Estilo.curva()).start()
        else { textos.animate().cancel(); textos.translationY = alvo }
    }

    private fun mover(v: View, x: Float, y: Float, alfa: Float, escala: Float, animar: Boolean, atrasoMs: Long = 0L) {
        val m = molas.getOrPut(v) { Mola(v) }
        if (alfa > 0f) v.visibility = View.VISIBLE
        if (!animar || v.visibility != View.VISIBLE || semMov) {
            m.parar(x, y, alfa, escala)
            return
        }
        m.mirar(floatArrayOf(x, y, alfa, escala), android.os.SystemClock.uptimeMillis() + atrasoMs, rigidez, amortecer)
        if (m !in ativas) ativas += m
        if (!quadroPedido) { quadroPedido = true; ultimoQuadro = 0L; Choreographer.getInstance().postFrameCallback(quadro) }
    }

    private var semMov = false

    /** "Remover animações" (acessibilidade) ou escala de animação 0 nas opções do desenvolvedor: tudo vai direto. */
    private fun semAnimacao() =
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

    // ------------------------------------------------------------------ mola

    /**
     * Uma view com quatro propriedades em mola (x, y, transparência, escala), cada uma com posição e velocidade. A conta é a
     * de um amortecedor: aceleração = -w²·(posição - alvo) - 2·ζ·w·velocidade. Um quadro do Choreographer anda todas.
     */
    private inner class Mola(val v: View) {
        val pos = FloatArray(4); val vel = FloatArray(4); val alvo = FloatArray(4)
        private var proximo: FloatArray? = null
        private var trocaEm = 0L
        private var camada = false

        fun ler() { pos[0] = v.translationX; pos[1] = v.translationY; pos[2] = v.alpha; pos[3] = v.scaleX }

        fun parar(x: Float, y: Float, alfa: Float, escala: Float) {
            ativas.remove(this); proximo = null
            vel.fill(0f); alvo[0] = x; alvo[1] = y; alvo[2] = alfa; alvo[3] = escala
            pos.indices.forEach { pos[it] = alvo[it] }
            aplicar(); semCamada()
            if (alfa == 0f) v.visibility = View.INVISIBLE
        }

        private var w = MOLA_W
        private var zeta = MOLA_AMORTECE

        fun mirar(novo: FloatArray, quando: Long, rigidez: Float, amortece: Float) {
            if (this !in ativas) { ler(); vel.fill(0f); pos.indices.forEach { alvo[it] = pos[it] } }
            proximo = novo; trocaEm = quando; w = rigidez; zeta = amortece
        }

        /** Anda dt segundos; devolve true se ainda está em movimento. */
        fun andar(agora: Long, dt: Float): Boolean {
            proximo?.let { if (agora >= trocaEm) { it.copyInto(alvo); proximo = null } }
            val zw2 = 2f * zeta * w; val ww = w * w
            var t = dt
            while (t > 0f) {   // passos de até 4 ms: estável mesmo se um quadro atrasar
                val h = minOf(t, 0.004f); t -= h
                for (i in 0..3) {
                    // transparência sem passar do alvo (amortecimento crítico): piscar de alfa chama atenção
                    val z = if (i == 2) 2f * w else zw2
                    vel[i] += (-ww * (pos[i] - alvo[i]) - z * vel[i]) * h
                    pos[i] += vel[i] * h
                }
            }
            pos[2] = pos[2].coerceIn(0f, 1f)
            val parado = proximo == null &&
                kotlin.math.abs(pos[0] - alvo[0]) < 0.5f && kotlin.math.abs(pos[1] - alvo[1]) < 0.5f &&
                kotlin.math.abs(pos[2] - alvo[2]) < 0.004f && kotlin.math.abs(pos[3] - alvo[3]) < 0.002f &&
                kotlin.math.abs(vel[0]) < 8f && kotlin.math.abs(vel[1]) < 8f
            if (parado) { pos.indices.forEach { pos[it] = alvo[it] }; vel.fill(0f) }
            // Escala ou transparência mudando: camada de GPU (texto e vetor rasterizados UMA vez, não a cada quadro; foi a
            // cauda lenta medida na Sala, p95 42 ms). Só translação não precisa.
            if (!parado && (kotlin.math.abs(pos[2] - alvo[2]) > 0.004f || kotlin.math.abs(pos[3] - alvo[3]) > 0.002f)) comCamada()
            aplicar()
            if (parado) { semCamada(); if (alvo[2] == 0f) v.visibility = View.INVISIBLE }
            return !parado
        }

        private fun aplicar() {
            v.translationX = pos[0]; v.translationY = pos[1]; v.alpha = pos[2]; v.scaleX = pos[3]; v.scaleY = pos[3]
        }
        private fun comCamada() { if (!camada && v.isAttachedToWindow) { camada = true; v.setLayerType(View.LAYER_TYPE_HARDWARE, null) } }
        private fun semCamada() { if (camada) { camada = false; v.setLayerType(View.LAYER_TYPE_NONE, null) } }
    }

    private val molas = HashMap<View, Mola>()
    private val ativas = ArrayList<Mola>()
    private var quadroPedido = false
    private var ultimoQuadro = 0L
    private val quadro: Choreographer.FrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            val dt = if (ultimoQuadro == 0L) 1f / 60f else ((t - ultimoQuadro) / 1e9f).coerceIn(0f, 1f / 20f)
            ultimoQuadro = t
            val agora = android.os.SystemClock.uptimeMillis()
            ativas.removeAll { !it.andar(agora, dt) }
            if (ativas.isNotEmpty() && isAttachedToWindow) Choreographer.getInstance().postFrameCallback(this)
            else quadroPedido = false
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Choreographer.getInstance().removeFrameCallback(quadro); quadroPedido = false
        removeCallbacks(mostrarDica)
        // fora da tela: tudo direto no lugar (nada fica no meio do caminho)
        ativas.toList().forEach { m -> m.andar(Long.MAX_VALUE, 5f) }
        ativas.clear()
    }

    // ------------------------------------------------------------------ TalkBack

    /** O leitor de tela ouve "Assistir, Séries, T1 E4", e a dica de teclas na 1ª vez que a cruz ganha o foco. */
    private fun anunciar() {
        val c = categoriaAtual() ?: return
        val i = itemAtual() ?: return
        // As linhas do item sem o que a situação já diz, como na tela (o leitor falava "falta 1 h 36 min" duas vezes;
        // teste 100%, 24/11/2026).
        val linhas = CruzLogica.partes(i.sub, CruzLogica.tirarDoItem(i.situacao)).let { listOf(it.principal) + it.detalhes }.filter { it.isNotEmpty() }.joinToString("\n")
        contentDescription = listOf(c.nome, i.nome, i.situacao?.let { it.rotulo + " " + it.texto }.orEmpty(), linhas, posicao.text.toString()).filter { it.isNotEmpty() }.joinToString(", ")
        if (isFocused) sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Button::class.java.name
        info.isClickable = true
    }

    override fun performClick(): Boolean { super.performClick(); if (barra && !faixa) teclaBarra(CruzLogica.Tecla.OK) else aplicar(CruzLogica.Tecla.OK); return true }
}
