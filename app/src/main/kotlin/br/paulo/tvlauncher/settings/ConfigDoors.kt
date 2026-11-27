package br.paulo.tvlauncher.settings

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import br.paulo.tvlauncher.ConfigLauncherActivity
import br.paulo.tvlauncher.ConfigLauncherActivity.Companion.BUSCA
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.family.UsoTv
import br.paulo.tvlauncher.home.GlassDrawable
import br.paulo.tvlauncher.home.IconeCruz
import br.paulo.tvlauncher.home.ScreenStyle

private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

/**
 * As seis portas das Configurações (23/11/2026, caminho A da proposta "Configurações da Sofaly", escolhido pelo Paulo).
 *
 * Por quê: 9 abas lado a lado obrigavam a adivinhar onde estava cada coisa ("feio e confuso, tem muita opção").
 * Para quê: a primeira tela mostra 6 portas grandes, cada uma com o azulejo da área e a frase do que tem dentro; dentro
 * da porta, o menu da esquerda mostra só as abas dela. Como: as 9 abas continuam as mesmas (os índices que outras telas
 * usam não mudam); a porta é só um agrupamento delas. Referências do Mobbin: Brink e Expedia (grade de categorias com
 * ícone tingido), Apple Games, e o "Settings" do WhatsApp e do bunq para a página de dentro.
 */
internal object PortasConfig {
    /** Cada porta = as abas dela (índices de [ConfigLauncherActivity.abas]), na ordem do menu. */
    val PORTAS: List<List<Int>> = listOf(
        listOf(0),          // Tela inicial
        listOf(1),          // TV e filmes
        listOf(2),          // Família
        listOf(3),          // Energia
        listOf(4, 5),       // Casa e rede = Casa + Remoto
        listOf(6, 7, 8),    // Sistema = Sistema + Ajuda + Avançado
    )

    /** A porta que tem a aba [aba]. */
    fun daAba(aba: Int): Int = PORTAS.indexOfFirst { aba in it }.coerceAtLeast(0)

    /** As abas da porta [porta] que aparecem no uso [uso] (Simples esconde as técnicas, ver [UsoTv.ABAS_TECNICAS]). */
    fun abasVisiveis(porta: Int, uso: UsoTv): List<Int> = PORTAS[porta].filter { UsoTv.mostraAba(uso, it) }

    /**
     * Quantas linhas uma aba mostra no modo Simples antes de "Mais ajustes". Era 8; 5 desde 25/11/2026 (onda C da
     * simplificação: "no máximo 5 por porta, as mais usadas").
     */
    const val LINHAS_SIMPLES = 5

    /**
     * Quais linhas ficam à vista no Simples (puro, PortasConfigTest): as [max] mais usadas ([vezes] = quantas vezes cada
     * linha foi apertada, na ordem da aba); empate (e TV nova, tudo zero) fica com a ordem da aba, que já põe o
     * principal em cima. Devolve as posições que ficam.
     */
    fun noSimples(vezes: List<Int>, max: Int = LINHAS_SIMPLES): Set<Int> =
        vezes.indices.sortedWith(compareByDescending<Int> { vezes[it] }.thenBy { it }).take(max).toSet()
}

/** As portas estão na tela agora (e não uma porta aberta)? */
internal val ConfigLauncherActivity.naGrade: Boolean get() = grade.visibility == View.VISIBLE

/** Mostra a grade das portas no lugar do menu + conteúdo. [foco] = a porta que fica com o foco. */
internal fun ConfigLauncherActivity.mostrarPortas(foco: Int = PortasConfig.daAba(abaAtual)) {
    ui.removeCallbacks(trocarAba)
    colunas.visibility = View.GONE
    grade.visibility = View.VISIBLE
    grade.removeAllViews()
    val uso = UsoTv.atual(this)
    // Padrão das Fileiras (Paulo aprovou a prévia, 26/11/2026): fora do painel ao lado da HOME (estreito demais), as
    // portas são duas fileiras de 6 cartões em tela cheia, sobre a arte com véu, em qualquer jeito das telas.
    if (!janelaPainel) { portasEmCartoes(uso, foco); return }
    larguraCheia(false)

    // Busca e Simples/Completo numa linha só, em cima (Ajustes do Apple Watch e Reddit no Mobbin: a busca vem antes de tudo).
    // Guiado: a coluna da lista tem 400 dp e as duas pílulas lado a lado não cabem ("Modo: Completo" quebrava e cortava,
    // teste de telas 26/11/2026): uma embaixo da outra.
    val topo = LinearLayout(this).apply { orientation = if (guiado) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
    topo.addView(Estilo.pilula(this).apply {
        addView(Estilo.texto(context, Estilo.Papel.CORPO, "⌕  " + getString(R.string.elv_aj_buscar)).apply { maxLines = 1 })
        setOnClickListener { abrirBuscaDasPortas() }
    }, LinearLayout.LayoutParams(WRAP, WRAP).apply { if (guiado) bottomMargin = dp(Estilo.E1) else marginEnd = dp(Estilo.E2) })
    // Simples / Completo (proposta E), à vista: a mesma escolha de Sistema > "Como esta TV é usada".
    topo.addView(Estilo.pilula(this).apply {
        addView(Estilo.texto(context, Estilo.Papel.CORPO, getString(R.string.cfg3_modo, nomeUso(uso))).apply { maxLines = 1 })
        setOnClickListener {
            UsoTv.escolher(this@mostrarPortas, UsoTv.proximo(UsoTv.atual(this@mostrarPortas)))
            mostrarPortas(foco)
            grade.post { grade.findViewWithTag<View>(TAG_MODO)?.requestFocus() }
        }
        tag = TAG_MODO
    })
    grade.addView(topo, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(Estilo.E2); marginStart = dp(4) })
    objetivos(foco)

    val portas = PortasConfig.PORTAS.indices.filter { PortasConfig.abasVisiveis(it, uso).isNotEmpty() }
    if (painel) {
        if (janelaPainel) SidePanel.previa(this, false)
        guiaDasPortas()
        portasEmLista(portas, foco)
        mostrarDicaDasPortas()
        return
    }
    if (ScreenStyle.barra(this)) {
        (grade.parent as? View)?.background = Estilo.fundoEntrada(this)
        portasEmBarra(portas, foco)
        mostrarDicaDasPortas()
        return
    }
    sairDaBarra()
    val tabela = GridLayout(this).apply { columnCount = 3; clipChildren = false; clipToPadding = false }
    var focar: View? = null
    portas.forEachIndexed { n, p ->
        val cartao = cartaoDaPorta(p)
        tabela.addView(cartao, GridLayout.LayoutParams(GridLayout.spec(n / 3), GridLayout.spec(n % 3, 1f)).apply {
            width = 0; setMargins(dp(4), dp(4), dp(Estilo.E2), dp(Estilo.E2))
        })
        if (p == foco || focar == null) focar = cartao   // a porta pedida; sem ela (escondida no Simples), a primeira
    }
    grade.addView(tabela, LinearLayout.LayoutParams(MATCH, WRAP))

    mostrarDicaDasPortas()
    focar?.post { focar?.requestFocus() }
}

private const val TAG_MODO = "cfg3_modo"

/**
 * Os 4 objetivos (onda D, 25/11/2026; ver [SettingsGoals]) em 2 x 2 botões, logo abaixo da busca. O ativo mostra "✓" e
 * o mesmo OK desfaz. Só o nome, sem símbolo (26/11/2026, Sala: "Aa", "◻", "☾" e um emoji colorido, quatro jeitos
 * diferentes numa linha só; o protótipo aprovado tem pílulas só de texto). Na barra única e no jeito cruz, o que não existe no jeito atual só não aparece na tela (as chaves
 * ficam gravadas e valem se a pessoa mudar o jeito).
 */
private fun ConfigLauncherActivity.objetivos(foco: Int) {
    val ativo = SettingsGoals.ativo(this)
    val tabela = GridLayout(this).apply { columnCount = 2; clipChildren = false; clipToPadding = false }
    SettingsGoals.Goal.entries.forEachIndexed { n, g ->
        val nome = getString(when (g) {
            SettingsGoals.Goal.READ -> R.string.goal_read
            SettingsGoals.Goal.CLEAN -> R.string.goal_clean
            SettingsGoals.Goal.ENERGY -> R.string.goal_energy
            SettingsGoals.Goal.KIDS -> R.string.goal_kids
        })
        val b = Estilo.pilula(this).apply {
            // Duas linhas: no painel estreito (560 dp, 2 colunas) um nome longo em alemão não cabe numa.
            addView(Estilo.texto(context, Estilo.Papel.CORPO, (if (g == ativo) "✓  " else "") + nome).apply { maxLines = 2 })
            contentDescription = if (g == ativo) getString(R.string.goal_desfazer, nome) else nome
            tag = "goal:" + g.name
            setOnClickListener {
                when {
                    g == SettingsGoals.Goal.KIDS -> startActivity(android.content.Intent(this@objetivos, br.paulo.tvlauncher.ReceitasActivity::class.java)
                        .putExtra(br.paulo.tvlauncher.ReceitasActivity.EXTRA_CONFIRMAR, br.paulo.tvlauncher.settings.Receitas.Receita.CRIANCAS.name))
                    g == ativo -> { SettingsGoals.desfazer(this@objetivos); aviso(getString(R.string.goal_desfeito, nome)) }
                    else -> { SettingsGoals.aplicar(this@objetivos, g); aviso(getString(R.string.goal_feito, nome)) }
                }
                if (g == SettingsGoals.Goal.KIDS) return@setOnClickListener
                // A letra muda a própria tela das Configurações: refaz e volta o foco ao mesmo botão.
                if (g == SettingsGoals.Goal.READ) { recreate(); return@setOnClickListener }
                mostrarPortas(foco)
                grade.post { grade.findViewWithTag<View>("goal:" + g.name)?.requestFocus() }
            }
        }
        tabela.addView(b, GridLayout.LayoutParams(GridLayout.spec(n / 2), GridLayout.spec(n % 2, 1f)).apply {
            width = 0; setMargins(dp(4), dp(4), dp(Estilo.E1), dp(Estilo.E1))
        })
    }
    grade.addView(tabela, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(Estilo.E2) })
}

/**
 * Painel ao lado (A+B no estilo da E, 25/11/2026): as portas viram uma lista agrupada num cartão só (caminho A), cada
 * linha com o azulejo da área, o nome, o que tem dentro e "›". Porquê: no painel estreito a grade de 3 colunas não cabe,
 * e a lista em grupo lê de cima para baixo como os Ajustes do celular (Mobbin: Grok e Tide Guide, grupos de linhas num
 * cartão; WhatsApp e bunq, "Settings" com ícone tingido à esquerda).
 */
private fun ConfigLauncherActivity.portasEmLista(portas: List<Int>, foco: Int) {
    val lista = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false
        // Sem cartão em volta (protótipo aprovado 26/11/2026): só as linhas e o fio fino entre elas, sobre o vidro.
        setPaddingRelative(0, dp(4), 0, dp(4))
    }
    var focar: View? = null
    portas.forEachIndexed { n, p ->
        if (n > 0 && !guiado) lista.addView(View(this).apply { setBackgroundColor(Estilo.BORDA) },   // guiado: sem fio, como no Projectivy
            LinearLayout.LayoutParams(MATCH, dp(1)).apply { marginStart = dp(Estilo.E2 + 36 + 14); marginEnd = dp(Estilo.E2) })
        val l = linhaDaPorta(p)
        lista.addView(l, LinearLayout.LayoutParams(MATCH, WRAP))
        if (p == foco || focar == null) focar = l
    }
    // Guiado: o foco fica parado na mesma altura também nas portas (molde do Projectivy).
    grade.addView((if (guiado) br.paulo.tvlauncher.FixedFocusScrollView(this) else android.widget.ScrollView(this)).apply {
        isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false
        setPaddingRelative(dp(4), dp(4), dp(4), dp(4))
        addView(lista)
        // Corta em cima e embaixo (a lista rolada não passa por cima da barra de dicas; teste de telas, 25/11/2026),
        // com folga dos lados para o foco crescer. Igual à rolagem das opções.
        addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> v.clipBounds = android.graphics.Rect(-dp(16), 0, v.width + dp(16), v.height) }
    }, LinearLayout.LayoutParams(MATCH, 0, 1f))
    SidePanel.escalonar(this, lista)
    focar?.post { focar?.requestFocus() }
}

/**
 * As portas em cartões (prévia "Arte com véu", aprovada pelo Paulo em 26/11/2026; refs Mobbin na prévia): em cima
 * Buscar, Modo e os 4 objetivos; embaixo as portas. Tudo cabe sem rolar, ícone no centro e o nome embaixo, como as
 * Fileiras da HOME. O ícone do cartão em foco fica laranja.
 */
private fun ConfigLauncherActivity.portasEmCartoes(uso: UsoTv, foco: Int) {
    sairDaBarra()
    larguraCheia(true)
    if (guiado) grade.addView(Estilo.cabecaDeFila(this, "modo", getString(R.string.cfg_titulo)),
        LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(Estilo.E2) })
    val ativo = SettingsGoals.ativo(this)
    val atalhos = ArrayList<View>()
    atalhos += Estilo.cartao(this, "buscar", getString(R.string.elv_aj_buscar)) { abrirBuscaDasPortas() }
    atalhos += Estilo.cartao(this, "modo", getString(R.string.cfg3_modo, nomeUso(uso))) {
        UsoTv.escolher(this, UsoTv.proximo(UsoTv.atual(this)))
        mostrarPortas(foco)
        grade.post { grade.findViewWithTag<View>(TAG_MODO)?.requestFocus() }
    }.apply { tag = TAG_MODO }
    for (g in SettingsGoals.Goal.entries) {
        val nome = getString(when (g) {
            SettingsGoals.Goal.READ -> R.string.goal_read
            SettingsGoals.Goal.CLEAN -> R.string.goal_clean
            SettingsGoals.Goal.ENERGY -> R.string.goal_energy
            SettingsGoals.Goal.KIDS -> R.string.goal_kids
        })
        val icone = when (g) { SettingsGoals.Goal.READ -> "letra"; SettingsGoals.Goal.CLEAN -> "tela"; SettingsGoals.Goal.ENERGY -> "energia"; SettingsGoals.Goal.KIDS -> "criancas" }
        atalhos += Estilo.cartao(this, icone, (if (g == ativo) "✓ " else "") + nome) { objetivo(g, ativo, nome, foco) }.apply {
            tag = "goal:" + g.name
            if (g == ativo) contentDescription = getString(R.string.goal_desfazer, nome)
        }
    }
    grade.addView(Estilo.filaDeCartoes(this, atalhos, 6), LinearLayout.LayoutParams(MATCH, WRAP))

    grade.addView(Estilo.cabecaDeFila(this, "sistema", getString(R.string.cfg4_fila_portas)),
        LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(Estilo.E3 + 4); bottomMargin = dp(Estilo.E2) })
    val portas = PortasConfig.PORTAS.indices.filter { PortasConfig.abasVisiveis(it, uso).isNotEmpty() }
    var focar: View? = null
    val cartoes = portas.map { p ->
        Estilo.cartao(this, ICONES_PORTA[p], nomeDaPorta(p), getString(FRASES[p])) { abrirPorta(p) }.also {
            if (p == foco || focar == null) focar = it
        }
    }
    grade.addView(Estilo.filaDeCartoes(this, cartoes, 6), LinearLayout.LayoutParams(MATCH, WRAP))
    focar?.post { focar?.requestFocus() }
}

/** Os ícones das portas nos cartões (mesma ordem de [PortasConfig.PORTAS]). */
private val ICONES_PORTA = listOf("grade", "tv", "familia", "lua", "casa", "sistema")

/**
 * Guiado: nas portas a explicação da esquerda some e a lista ocupa a tela toda (6 cartões não cabem na coluna de 400 dp);
 * dentro de uma porta volta o molde guiado. Nos outros jeitos não faz nada.
 */
private fun ConfigLauncherActivity.larguraCheia(cheia: Boolean) {
    if (!guiado) return
    guia?.visibility = if (cheia) View.GONE else View.VISIBLE
    val raiz = grade.parent as? View ?: return
    val lp = raiz.layoutParams as? LinearLayout.LayoutParams ?: return
    lp.width = if (cheia) MATCH else dp(GuidedPane.LISTA_DP + Estilo.MARGEM_LATERAL)
    raiz.layoutParams = lp
    raiz.setPaddingRelative(if (cheia) dp(Estilo.MARGEM_LATERAL) else 0, raiz.paddingTop, raiz.paddingEnd, raiz.paddingBottom)
}

/** OK num objetivo: aplica, desfaz ou (Crianças) abre a receita; o mesmo das pílulas antigas. */
private fun ConfigLauncherActivity.objetivo(g: SettingsGoals.Goal, ativo: SettingsGoals.Goal?, nome: String, foco: Int) {
    when {
        g == SettingsGoals.Goal.KIDS -> { startActivity(android.content.Intent(this, br.paulo.tvlauncher.ReceitasActivity::class.java)
            .putExtra(br.paulo.tvlauncher.ReceitasActivity.EXTRA_CONFIRMAR, br.paulo.tvlauncher.settings.Receitas.Receita.CRIANCAS.name)); return }
        g == ativo -> { SettingsGoals.desfazer(this); aviso(getString(R.string.goal_desfeito, nome)) }
        else -> { SettingsGoals.aplicar(this, g); aviso(getString(R.string.goal_feito, nome)) }
    }
    if (g == SettingsGoals.Goal.READ) { recreate(); return }
    mostrarPortas(foco)
    grade.post { grade.findViewWithTag<View>("goal:" + g.name)?.requestFocus() }
}

/** Uma linha de porta no painel: azulejo, nome e frase, e "›" à direita. Foco = o jeito escolhido (Anel, Quadro...). */
private fun ConfigLauncherActivity.linhaDaPorta(p: Int): View {
    val primeira = abas[PortasConfig.PORTAS[p].first()]
    val nome = if (p == PORTA_CASA_REDE) getString(R.string.cfg3_porta_casa_rede) else primeira.titulo
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPaddingRelative(dp(Estilo.E2), dp(12), dp(Estilo.E2), dp(12))
        isFocusable = true; isClickable = true
        contentDescription = nome
        val ic = iconeDoMenu(primeira.icone)
        addView(View(context).apply { background = ic }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(16) })
        val tNome = Estilo.texto(context, Estilo.Papel.CORPO, nome).apply { maxLines = 2; typeface = Estilo.fonte(600) }
        val tFrase = Estilo.texto(context, Estilo.Papel.LEGENDA, getString(FRASES[p])).apply { setPaddingRelative(0, dp(2), 0, 0) }
        addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; addView(tNome); addView(tFrase) },
            LinearLayout.LayoutParams(0, WRAP, 1f))
        val tSeta = Estilo.texto(context, Estilo.Papel.CORPO, if (guiado) "▸" else "›").apply { setTextColor(Estilo.TEXTO_3) }
        addView(tSeta, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(Estilo.E1) })
        setOnFocusChangeListener { v, f ->
            v.background = if (f) Estilo.fundoFocado(context, Estilo.RAIO) else null   // o foco de todo o sistema, também na tela guiada
            val escura = f && !guiado && Estilo.focoEscurece(context)
            tNome.setTextColor(if (escura) Estilo.TEXTO_NO_FOCO else Estilo.TEXTO)
            tFrase.setTextColor(if (escura) Estilo.TEXTO_NO_FOCO_2 else Estilo.TEXTO_2)
            tSeta.setTextColor(if (escura) Estilo.TEXTO_NO_FOCO_2 else Estilo.TEXTO_3)
            acenderIcone(context, ic, f)
            val escala = if (f && !guiado) Estilo.ESCALA_LINHA else 1f
            v.animate().scaleX(escala).scaleY(escala).setDuration(Estilo.FOCO_MS).start()
            if (f) mostrarDicaDasPortas()
        }
        setOnClickListener { abrirPorta(p) }
    }
}

/** Um cartão de porta: o azulejo da área ao lado do nome e, embaixo, o que tem dentro em poucas palavras. */
private fun ConfigLauncherActivity.cartaoDaPorta(p: Int): View {
    val primeira = abas[PortasConfig.PORTAS[p].first()]
    val nome = if (p == PORTA_CASA_REDE) getString(R.string.cfg3_porta_casa_rede) else primeira.titulo
    val frase = getString(FRASES[p])
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPaddingRelative(dp(Estilo.E3), dp(Estilo.E2 + 4), dp(Estilo.E3), dp(Estilo.E2 + 4))
        isFocusable = true; isClickable = true
        val normal = Estilo.fundo(context, Estilo.SUPERFICIE, Estilo.RAIO + 4, Estilo.BORDA)
        background = normal
        val cabeca = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val lado = dp(40)
        val ic = iconeDoMenu(primeira.icone)
        cabeca.addView(View(context).apply { background = ic }, LinearLayout.LayoutParams(lado, lado).apply { marginEnd = dp(12) })
        val tNome = Estilo.texto(context, Estilo.Papel.CORPO, nome).apply { textSize = 22f; maxLines = 2 }
        cabeca.addView(tNome, LinearLayout.LayoutParams(0, WRAP, 1f))
        // Sem limite de linhas (24/11/2026): com maxLines = 2 e sem "…", a 3ª linha sumia calada em holandês e sueco.
        val tFrase = Estilo.texto(context, Estilo.Papel.LEGENDA, frase).apply { setPaddingRelative(0, dp(8), 0, 0) }
        addView(cabeca); addView(tFrase)
        setOnFocusChangeListener { v, foco ->
            v.background = if (foco) Estilo.fundoFocado(context, Estilo.RAIO + 4, Estilo.fundo(context, Estilo.SUPERFICIE_ALTA, Estilo.RAIO + 4, Estilo.BORDA)) else normal
            tNome.setTextColor(if (foco && Estilo.focoEscurece(context)) Estilo.TEXTO_NO_FOCO else Estilo.TEXTO)
            tFrase.setTextColor(if (foco && Estilo.focoEscurece(context)) Estilo.TEXTO_NO_FOCO else Estilo.TEXTO_2)
            acenderIcone(context, ic, foco)
            v.animate().scaleX(if (foco) Estilo.ESCALA_CARD else 1f).scaleY(if (foco) Estilo.ESCALA_CARD else 1f).setDuration(Estilo.FOCO_MS).start()
            if (foco) mostrarDicaDasPortas()
        }
        setOnClickListener { abrirPorta(p) }
    }
}

/**
 * Jeito Barra (proposta C, coerência das telas 24/11/2026): as portas viram a fileira de cards da HOME, embaixo, sobre a
 * foto nítida. Porquê: quem escolheu a Barra vê as Configurações com a mesma leitura da tela inicial. Como na HOME, o
 * card é só o azulejo; o nome e o que tem dentro da porta em foco ficam na linha logo acima da fileira.
 */
private fun ConfigLauncherActivity.portasEmBarra(portas: List<Int>, foco: Int) {
    grade.addView(View(this), LinearLayout.LayoutParams(MATCH, 0, 1f))
    val nome = Estilo.texto(this, Estilo.Papel.TITULO).apply { maxLines = 1 }
    val frase = Estilo.texto(this, Estilo.Papel.LEGENDA).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    val linha = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    linha.addView(nome, LinearLayout.LayoutParams(WRAP, WRAP))
    linha.addView(frase, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(Estilo.E2) })
    grade.addView(linha, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(Estilo.E1); marginStart = dp(4) })
    val fila = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false
        setPaddingRelative(dp(4), dp(Estilo.E1), dp(4), dp(Estilo.E1))
    }
    var focar: View? = null
    for (p in portas) {
        val card = cardDaPortaNaBarra(p) { n, f -> nome.text = n; frase.text = f }
        fila.addView(card, LinearLayout.LayoutParams(dp(BARRA_CARD_L), dp(BARRA_CARD_A)).apply { marginEnd = dp(BARRA_VAO) })
        if (p == foco || focar == null) focar = card
    }
    grade.addView(android.widget.HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; addView(fila)
    }, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(Estilo.E2) })
    focar?.post { focar?.requestFocus() }
}

/** Um card da Barra: o azulejo da área no meio de um vidro 16:9. O foco é o do sistema (Anel por padrão). */
private fun ConfigLauncherActivity.cardDaPortaNaBarra(p: Int, emFoco: (String, String) -> Unit): View {
    val primeira = abas[PortasConfig.PORTAS[p].first()]
    val nome = if (p == PORTA_CASA_REDE) getString(R.string.cfg3_porta_casa_rede) else primeira.titulo
    val frase = getString(FRASES[p])
    return android.widget.FrameLayout(this).apply {
        isFocusable = true; isClickable = true
        contentDescription = nome
        background = Estilo.vidro(context, BARRA_RAIO)
        val ic = iconeDoMenu(primeira.icone)
        addView(View(context).apply { background = ic }, android.widget.FrameLayout.LayoutParams(dp(BARRA_AZULEJO), dp(BARRA_AZULEJO), Gravity.CENTER))
        setOnFocusChangeListener { v, f ->
            v.foreground = if (f) Estilo.fundoFocado(context, BARRA_RAIO) else null
            acenderIcone(context, ic, f)
            v.animate().scaleX(if (f) Estilo.ESCALA_CARD else 1f).scaleY(if (f) Estilo.ESCALA_CARD else 1f).setDuration(Estilo.FOCO_MS).start()
            if (f) emFoco(nome, frase)
        }
        setOnClickListener { abrirPorta(p) }
    }
}

/** Saindo da Barra (porta aberta, busca): a raiz volta ao fundo das listas. Só mexe se o fundo for o da Barra. */
private fun ConfigLauncherActivity.sairDaBarra() {
    val raiz = grade.parent as? View ?: return
    if ((raiz.background as? GlassDrawable)?.barra == true) raiz.background = Estilo.fundoTela(this)
}

// Medidas do card da Barra (dp): seis portas cabem numa fileira de 960 dp sem rolar, como os apps na HOME.
private const val BARRA_CARD_L = 120
private const val BARRA_CARD_A = 68
private const val BARRA_VAO = 18
private const val BARRA_RAIO = 16
private const val BARRA_AZULEJO = 40

/** O que tem atrás de cada porta, em poucas palavras (as explicações das abas eram longas demais para o cartão). */
private val FRASES = intArrayOf(R.string.cfg3_porta_0_det, R.string.cfg3_porta_1_det, R.string.cfg3_porta_2_det,
    R.string.cfg3_porta_3_det, R.string.cfg3_porta_4_det, R.string.cfg3_porta_5_det)

private const val PORTA_CASA_REDE = 4

/** Abre a porta [p] na aba [aba] (a primeira visível dela, se não disser) e leva o foco para as opções. */
internal fun ConfigLauncherActivity.abrirPorta(p: Int, aba: Int? = null) {
    val alvo = aba ?: PortasConfig.abasVisiveis(p, UsoTv.atual(this)).firstOrNull() ?: PortasConfig.PORTAS[p].first()
    sairDaBarra()
    larguraCheia(false)
    grade.visibility = View.GONE
    colunas.visibility = View.VISIBLE
    // Painel: na "Tela inicial" a HOME atrás fica nítida e muda na hora (a prévia); nas outras portas ela escurece.
    if (janelaPainel) SidePanel.previa(this, p == 0)
    abaAtual = alvo
    mostrarAba(alvo)
    mostrarAbasDoUso()
    itensMenu.forEachIndexed { j, t -> pintarItemMenu(t, foco = false, escolhido = j == abaAtual) }
    conteudo.post { (primeiroFocavel(conteudo) ?: itensMenu[abaAtual]).requestFocus() }
}

/** A busca aberta pela pílula das portas: o menu mostra só "Buscar" e o foco vai para a caixa. */
private fun ConfigLauncherActivity.abrirBuscaDasPortas() {
    sairDaBarra()
    larguraCheia(false)
    grade.visibility = View.GONE
    colunas.visibility = View.VISIBLE
    mostrarBusca()
    mostrarAbasDoUso()
    conteudo.post { campoBusca?.requestFocus() }
}

/** Dicas do controle na grade: setas escolhem, OK abre, Voltar sai. */
internal fun ConfigLauncherActivity.mostrarDicaDasPortas() {
    dica.removeAllViews()
    dica.addView(Estilo.barraDicas(this, "▲▼◀▶" to getString(R.string.cl_dica_escolher), "OK" to getString(R.string.cl_dica_entrar),
        getString(R.string.cl_tecla_voltar) to getString(R.string.cl_dica_sair)))
}

/** Menu da esquerda com a porta aberta: só as abas dela (ou só "Buscar", na busca). */
internal fun ConfigLauncherActivity.menuDaPorta() {
    val uso = UsoTv.atual(this)
    val naBusca = abaMontada == BUSCA
    val porta = PortasConfig.PORTAS[PortasConfig.daAba(abaAtual)]
    itemBusca?.visibility = if (naBusca) View.VISIBLE else View.GONE
    itensMenu.forEachIndexed { i, t ->
        t.visibility = if (!naBusca && i in porta && (UsoTv.mostraAba(uso, i) || i == abaAtual)) View.VISIBLE else View.GONE
    }
    // Painel: a fileira das abas só aparece quando a porta tem mais de uma (sem menu de um item só, nem na busca).
    if (painel) caixaMenu.visibility = if (!naBusca && itensMenu.count { it.visibility == View.VISIBLE } > 1) View.VISIBLE else View.GONE
}

/**
 * Modo Simples (proposta E; onda C, 25/11/2026): a aba mostra só as [PortasConfig.LINHAS_SIMPLES] linhas mais usadas
 * nesta TV (contadas pelo UsoLocal a cada OK) e uma linha "Mais ajustes" que abre o resto. Título de grupo sem nenhuma
 * linha à vista some junto. Quem chega por busca ou "Conversa com" vê tudo (a aba entra em [abasAbertas]).
 */
internal fun ConfigLauncherActivity.recolherNoSimples(aba: Int, inicio: Int) {
    if (UsoTv.atual(this) != UsoTv.SIMPLES || aba in abasAbertas || indexando >= 0) return
    val linhas = (inicio until conteudo.childCount).filter { primeiroFocavel(conteudo.getChildAt(it)) != null }
    if (linhas.size <= PortasConfig.LINHAS_SIMPLES) return
    val uso = br.paulo.tvlauncher.family.UsoLocal.vezes(this)
    val vezes = linhas.map { k -> (conteudo.getChildAt(k) as? ConfigLauncherActivity.LinhaCfg)?.chaveUso?.let { uso[it] } ?: 0 }
    val ficam = PortasConfig.noSimples(vezes).map { linhas[it] }.toSet()
    val escondidas = linhas.filter { it !in ficam }
    escondidas.forEach { conteudo.getChildAt(it).visibility = View.GONE }
    // Título de grupo (texto sem foco) que ficou sem nenhuma linha à vista até o próximo título: some também.
    // Textos seguidos (título + explicação do grupo) contam como um cabeçalho só.
    val cabecalho = ArrayList<Int>(); var temLinha = false; var ultimoFoiTexto = false
    fun fecharGrupo() { if (!temLinha) cabecalho.forEach { conteudo.getChildAt(it).visibility = View.GONE } }
    for (k in inicio until conteudo.childCount) {
        val v = conteudo.getChildAt(k)
        if (v is TextView && !v.isFocusable && primeiroFocavel(v) == null) {
            if (!ultimoFoiTexto) { fecharGrupo(); cabecalho.clear(); temLinha = false }
            cabecalho += k; ultimoFoiTexto = true
        } else { ultimoFoiTexto = false; if (k in ficam) temLinha = true }
    }
    fecharGrupo()
    conteudo.addView(linha(getString(R.string.cfg3_mais_ajustes), getString(R.string.cfg3_mais_ajustes_det, escondidas.size), seta = true) {
        abasAbertas += aba
        recarregarAba()
    }, lp())
}

/**
 * Ícone dos menus (padrão do protótipo aprovado, Paulo 26/11/2026: os azulejos coloridos deixavam a tela "grosseira"):
 * o desenho da Linha em traço fino, creme apagado; acende em laranja com o foco ([acenderIcone]).
 */
internal fun iconeDoMenu(tipo: IconeCruz.Tipo): IconeCruz = IconeCruz(tipo, IconeCruz.Estilo.LINHA).apply { corFixa = Estilo.TEXTO_2 }

internal fun acenderIcone(ctx: android.content.Context, ic: IconeCruz, foco: Boolean) {
    ic.corFixa = when { !foco -> Estilo.TEXTO_2; Estilo.focoEscurece(ctx) -> Estilo.TEXTO_NO_FOCO; else -> Estilo.ACENTO }
}

/** Nome de uma porta (a da casa e rede tem nome próprio; as outras usam o da 1ª aba). */
private fun ConfigLauncherActivity.nomeDaPorta(p: Int): String =
    if (p == PORTA_CASA_REDE) getString(R.string.cfg3_porta_casa_rede) else abas[PortasConfig.PORTAS[p].first()].titulo

/** Guiado: a coluna da esquerda nas portas = "Sofaly", "Configurações" e o que se faz aqui. */
internal fun ConfigLauncherActivity.guiaDasPortas() {
    guia?.mostrar(getString(R.string.app_nome), getString(R.string.cfg_titulo), getString(R.string.gl_root_desc), IconeCruz.Tipo.AJUSTE)
}

/** Guiado: a coluna da esquerda numa aba = caminho (Configurações › porta), nome da aba, explicação e o ícone dela. */
internal fun ConfigLauncherActivity.guiaDaAba(i: Int) {
    val aba = abas.getOrNull(i) ?: return
    val porta = nomeDaPorta(PortasConfig.daAba(i))
    val trilha = getString(R.string.cfg_titulo) + if (porta != aba.titulo) "  ›  $porta" else ""
    guia?.mostrar(trilha, aba.titulo, aba.explica, aba.icone, ICONES_PORTA.getOrNull(PortasConfig.daAba(i)))
}
