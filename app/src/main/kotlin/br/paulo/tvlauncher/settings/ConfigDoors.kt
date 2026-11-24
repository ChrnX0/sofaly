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
import br.paulo.tvlauncher.home.IconeCruz

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

    /** Quantas linhas uma aba mostra no modo Simples antes de "Mais ajustes". */
    const val LINHAS_SIMPLES = 8
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

    // Busca e Simples/Completo numa linha só, em cima (Ajustes do Apple Watch e Reddit no Mobbin: a busca vem antes de tudo).
    val topo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
    topo.addView(Estilo.pilula(this).apply {
        addView(Estilo.texto(context, Estilo.Papel.CORPO, "⌕  " + getString(R.string.elv_aj_buscar)).apply { maxLines = 1 })
        setOnClickListener { abrirBuscaDasPortas() }
    }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(Estilo.E2) })
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

    val portas = PortasConfig.PORTAS.indices.filter { PortasConfig.abasVisiveis(it, uso).isNotEmpty() }
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
        cabeca.addView(View(context).apply {
            background = IconeCruz(primeira.icone, IconeCruz.Estilo.AZULEJO).apply {
                corAzulejo = primeira.area ?: ConfigLauncherActivity.AZULEJO_NEUTRO
            }
        }, LinearLayout.LayoutParams(lado, lado).apply { marginEnd = dp(12) })
        val tNome = Estilo.texto(context, Estilo.Papel.CORPO, nome).apply { textSize = 22f; maxLines = 2 }
        cabeca.addView(tNome, LinearLayout.LayoutParams(0, WRAP, 1f))
        // Sem limite de linhas (24/11/2026): com maxLines = 2 e sem "…", a 3ª linha sumia calada em holandês e sueco.
        val tFrase = Estilo.texto(context, Estilo.Papel.LEGENDA, frase).apply { setPaddingRelative(0, dp(8), 0, 0) }
        addView(cabeca); addView(tFrase)
        setOnFocusChangeListener { v, foco ->
            v.background = if (foco) Estilo.fundoFocado(context, Estilo.RAIO + 4, Estilo.fundo(context, Estilo.SUPERFICIE_ALTA, Estilo.RAIO + 4, Estilo.BORDA)) else normal
            tNome.setTextColor(if (foco && Estilo.focoEscurece(context)) Estilo.TEXTO_NO_FOCO else Estilo.TEXTO)
            tFrase.setTextColor(if (foco && Estilo.focoEscurece(context)) Estilo.TEXTO_NO_FOCO else Estilo.TEXTO_2)
            v.animate().scaleX(if (foco) Estilo.ESCALA_CARD else 1f).scaleY(if (foco) Estilo.ESCALA_CARD else 1f).setDuration(Estilo.FOCO_MS).start()
            if (foco) mostrarDicaDasPortas()
        }
        setOnClickListener { abrirPorta(p) }
    }
}

/** O que tem atrás de cada porta, em poucas palavras (as explicações das abas eram longas demais para o cartão). */
private val FRASES = intArrayOf(R.string.cfg3_porta_0_det, R.string.cfg3_porta_1_det, R.string.cfg3_porta_2_det,
    R.string.cfg3_porta_3_det, R.string.cfg3_porta_4_det, R.string.cfg3_porta_5_det)

private const val PORTA_CASA_REDE = 4

/** Abre a porta [p] na aba [aba] (a primeira visível dela, se não disser) e leva o foco para as opções. */
internal fun ConfigLauncherActivity.abrirPorta(p: Int, aba: Int? = null) {
    val alvo = aba ?: PortasConfig.abasVisiveis(p, UsoTv.atual(this)).firstOrNull() ?: PortasConfig.PORTAS[p].first()
    grade.visibility = View.GONE
    colunas.visibility = View.VISIBLE
    abaAtual = alvo
    mostrarAba(alvo)
    mostrarAbasDoUso()
    itensMenu.forEachIndexed { j, t -> pintarItemMenu(t, foco = false, escolhido = j == abaAtual) }
    conteudo.post { (primeiroFocavel(conteudo) ?: itensMenu[abaAtual]).requestFocus() }
}

/** A busca aberta pela pílula das portas: o menu mostra só "Buscar" e o foco vai para a caixa. */
private fun ConfigLauncherActivity.abrirBuscaDasPortas() {
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
}

/**
 * Modo Simples (proposta E): a aba mostra as primeiras [PortasConfig.LINHAS_SIMPLES] linhas e uma linha "Mais ajustes"
 * que abre o resto. Quem chega por busca ou "Conversa com" vê tudo (a aba entra em [abasAbertas]).
 */
internal fun ConfigLauncherActivity.recolherNoSimples(aba: Int, inicio: Int) {
    if (UsoTv.atual(this) != UsoTv.SIMPLES || aba in abasAbertas || indexando >= 0) return
    var vistas = 0
    var corte = -1
    for (k in inicio until conteudo.childCount) {
        if (primeiroFocavel(conteudo.getChildAt(k)) == null) continue
        vistas++
        if (vistas > PortasConfig.LINHAS_SIMPLES) { corte = k; break }
    }
    if (corte < 0) return
    // Um título de grupo logo antes do corte vai junto (não fica um título sozinho em cima de "Mais ajustes").
    while (corte > inicio && conteudo.getChildAt(corte - 1).let { it is TextView && !it.isFocusable }) corte--
    val escondidas = (corte until conteudo.childCount).map { conteudo.getChildAt(it) }
    val quantas = escondidas.count { primeiroFocavel(it) != null }
    escondidas.forEach { it.visibility = View.GONE }
    conteudo.addView(linha(getString(R.string.cfg3_mais_ajustes), getString(R.string.cfg3_mais_ajustes_det, quantas), seta = true) {
        abasAbertas += aba
        recarregarAba()
    }, lp())
}
