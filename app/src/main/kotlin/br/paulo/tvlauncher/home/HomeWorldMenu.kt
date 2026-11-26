package br.paulo.tvlauncher.home

import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import br.paulo.tvlauncher.CaixaTv
import br.paulo.tvlauncher.CampoTv
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.MainActivity
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.family.TravaHome

/**
 * O menu de cada mundo da cruz (22/11/2026, Paulo: "um menu de opções para os mundos... ordenar alfabeticamente ou por
 * uso, trocar ícone"). Abre pelo OK segurado em qualquer item, na linha "Opções de <mundo>".
 * Porquê: a ordem, o nome e o desenho do mundo eram fixos. Para quê: cada casa arruma a cruz do seu jeito, sem ir às
 * Configurações. Como: guarda em [WorldOptions] e redesenha a cruz; nada aqui mexe nos apps em si.
 */

/** Itens de app do mundo em foco, na ordem da tela (ids "app:<pacote>" viram pacotes). */
private fun MainActivity.appsDoMundo(): List<String> =
    cruz?.categoriaAtual()?.itens.orEmpty().map { it.id }.filter { it.startsWith("app:") }.map { it.removePrefix("app:") }

/** O mundo em foco e o nome que a tela mostra para ele. */
internal fun MainActivity.mundoEmFoco(): Pair<CruzLogica.Categoria, String>? = cruz?.categoriaAtual()?.let { it.cat to it.nome }

/** Linha "Opções de <mundo>" para o fim de um menu de item. */
internal fun MainActivity.opcaoDoMundo(): MainActivity.Opcao? = mundoEmFoco()?.let { (cat, nome) ->
    MainActivity.Opcao(getString(R.string.wo_opcoes, nome)) { menuDoMundo(cat, nome) }
}

/** Subir/Descer do app no mundo em foco, só quando a ordem do mundo é "Do meu jeito". */
internal fun MainActivity.opcoesDeOrdem(pacote: String): List<MainActivity.Opcao> {
    val (cat, _) = mundoEmFoco() ?: return emptyList()
    val o = WorldOptions.of(this, cat)
    if (o.sort != WorldOptions.Sort.MANUAL) return emptyList()
    val atual = WorldOptions.Rules.arrange(appsDoMundo(), o.sort, manual = o.manual)
    val i = atual.indexOf(pacote)
    if (i < 0) return emptyList()
    fun mover(passo: Int) {
        WorldOptions.save(this, cat, o.copy(manual = WorldOptions.Rules.move(atual, pacote, passo)))
        atualizarAposMudanca("app:$pacote")
    }
    return listOfNotNull(
        MainActivity.Opcao(getString(R.string.wo_subir)) { mover(-1) }.takeIf { i > 0 },
        MainActivity.Opcao(getString(R.string.wo_descer)) { mover(+1) }.takeIf { i < atual.lastIndex },
    )
}

/** Os nomes dos jeitos de card ([StripHome.Format], na mesma ordem): Ajuste fino e o menu de cada mundo. */
internal fun nomesDosFormatos(ctx: android.content.Context): List<String> = listOf("16:9", "4:3",
    ctx.getString(R.string.st_quadrado), ctx.getString(R.string.st_logo_solto), ctx.getString(R.string.st_grade2),
    ctx.getString(R.string.st_redondo), ctx.getString(R.string.st_so_escolhido))

/**
 * Jeito dos cards só deste mundo (24/11/2026, Paulo: "Jeito dos cards" por mundo, como o Projectivy deixa cada fileira com
 * o seu): a 1ª linha volta a seguir o Ajuste fino; as outras fixam um jeito aqui.
 */
private fun MainActivity.escolherCards(cat: CruzLogica.Categoria) {
    val o = WorldOptions.of(this, cat)
    val nomes = nomesDosFormatos(this)
    val geral = nomes.getOrElse(StripHome.options(this).format.ordinal) { nomes[0] }
    val rotulos = listOf<CharSequence>(getString(R.string.wo_cards_geral, geral)) + nomes
    val marcada = o.cards?.takeIf { it in nomes.indices }?.let { it + 1 } ?: 0
    menuAberto = Estilo.lista(this, getString(R.string.wo_cards), rotulos, marcadas = listOf(marcada)) { i ->
        WorldOptions.save(this, cat, o.copy(cards = if (i == 0) null else i - 1)); atualizarAposMudanca(null)
    }
}

/**
 * Tamanho dos cards só deste mundo nas Fileiras (Projectivy: "Tamanho" de cada categoria, 26/11/2026): a 1ª linha volta
 * a seguir o Ajuste fino; as outras fixam um tamanho aqui.
 */
private fun MainActivity.escolherTamanho(cat: CruzLogica.Categoria) {
    val o = WorldOptions.of(this, cat)
    val nomes = listOf(R.string.rw_xs, R.string.rw_s, R.string.rw_m, R.string.rw_l, R.string.rw_xl).map { getString(it) }
    val geral = nomes.getOrElse(RowsHome.options(this).size.ordinal) { nomes[2] }
    val rotulos = listOf<CharSequence>(getString(R.string.wo_cards_geral, geral)) + nomes
    val marcada = o.size?.takeIf { it in nomes.indices }?.let { it + 1 } ?: 0
    menuAberto = Estilo.lista(this, getString(R.string.rw_tamanho), rotulos, marcadas = listOf(marcada)) { i ->
        WorldOptions.save(this, cat, o.copy(size = if (i == 0) null else i - 1)); atualizarAposMudanca(null)
    }
}

/** Linhas de cards do mundo nas Fileiras (bloco 3): 1 = fileira comum; 2 ou 3 = grade (◀ ▶ andam por coluna). */
private fun MainActivity.escolherLinhas(cat: CruzLogica.Categoria) {
    val o = WorldOptions.of(this, cat)
    val rotulos = (1..WorldOptions.MAX_LINES).map<Int, CharSequence> { if (it == 1) getString(R.string.rw_linhas_1) else getString(R.string.rw_linhas_n, it) }
    menuAberto = Estilo.lista(this, getString(R.string.rw_linhas), rotulos, marcadas = listOf(o.lines - 1)) { i ->
        WorldOptions.save(this, cat, o.copy(lines = i + 1)); atualizarAposMudanca(null)
    }
}

internal fun MainActivity.menuDoMundo(cat: CruzLogica.Categoria, nome: String) {
    if (TravaHome.precisaPin(this)) { TravaHome.comPin(this) { menuDoMundo(cat, nome) }; return }
    val o = WorldOptions.of(this, cat)
    val opcoes = mutableListOf<MainActivity.Opcao>()
    // Ordenar só arruma apps: em Ferramentas (só itens do Sofaly) a linha não aparece.
    if (appsDoMundo().isNotEmpty()) opcoes += MainActivity.Opcao(getString(R.string.wo_ordenar)) { escolherOrdem(cat, nome) }
    opcoes += MainActivity.Opcao(getString(R.string.wo_renomear_mundo)) {
        pedirNome(getString(R.string.wo_renomear_mundo), o.name ?: nome, AppNames.MAX) { novo ->
            WorldOptions.save(this, cat, o.copy(name = AppNames.clean(novo))); atualizarAposMudanca(null)
        }
    }
    opcoes += MainActivity.Opcao(getString(R.string.wo_icone_mundo)) {
        trocarIconeSofaly(WorldLabel.tipo(cat), nome, null, null)
    }
    if (BottomBar.strip(this)) opcoes += MainActivity.Opcao(getString(R.string.wo_cards)) { escolherCards(cat) }
    if (BottomBar.rows(this)) opcoes += MainActivity.Opcao(getString(R.string.rw_tamanho)) { escolherTamanho(cat) }
    if (BottomBar.rows(this)) opcoes += MainActivity.Opcao(getString(R.string.rw_linhas)) { escolherLinhas(cat) }
    // Só o ícone na linha (Paulo, 22/11/2026): o nome some da cruz e fica na pílula do topo quando a categoria tem o foco.
    // Na barra única a cruz não aparece: a troca não mudaria nada e sai do menu (onda A, 25/11/2026).
    if (!BottomBar.strip(this) && !BottomBar.rows(this)) opcoes += MainActivity.Opcao(getString(if (o.iconOnly) R.string.wo_com_nome else R.string.wo_so_icone)) {
        WorldOptions.save(this, cat, o.copy(iconOnly = !o.iconOnly)); atualizarAposMudanca(null)
    }
    if (WorldOptions.Rules.canHide(cat)) opcoes += MainActivity.Opcao(getString(R.string.wo_esconder)) {
        WorldOptions.save(this, cat, o.copy(hidden = true)); atualizarAposMudanca(null)
    }
    val escondidos = CruzLogica.Categoria.entries.filter { WorldOptions.Rules.canHide(it) && WorldOptions.of(this, it).hidden }
    if (escondidos.isNotEmpty()) opcoes += MainActivity.Opcao(getString(R.string.wo_mostrar, escondidos.size)) {
        escondidos.forEach { c -> WorldOptions.update(this, c) { it.copy(hidden = false) } }
        atualizarAposMudanca(null)
    }
    // Widgets de outros apps na HOME (24/11/2026): o modo de edição (setas movem, OK opções, VOLTAR salva).
    if (BottomBar.strip(this) && br.paulo.tvlauncher.settings.Planos.permite(this, br.paulo.tvlauncher.settings.Planos.Recurso.PLUGINS))
        opcoes += MainActivity.Opcao(getString(R.string.wdg_titulo)) { startActivity(android.content.Intent(this, br.paulo.tvlauncher.WidgetLayoutActivity::class.java)) }
    if (o != WorldOptions.Options()) opcoes += MainActivity.Opcao(getString(R.string.wo_padrao)) {
        WorldOptions.save(this, cat, WorldOptions.Options()); atualizarAposMudanca(null)
    }
    menuAberto = Estilo.lista(this, getString(R.string.wo_opcoes, nome), opcoes.map { it.rotulo }) { i -> opcoes[i].acao() }
}

private fun MainActivity.escolherOrdem(cat: CruzLogica.Categoria, nome: String) {
    val o = WorldOptions.of(this, cat)
    val modos = WorldOptions.Sort.entries
    // na ordem de WorldOptions.Sort (as duas últimas vieram do Projectivy, 26/11/2026)
    val rotulos = listOf<CharSequence>(getString(R.string.wo_por_uso), getString(R.string.wo_az), getString(R.string.wo_a_mao),
        getString(R.string.wo_ultimos), getString(R.string.wo_aleatorio))
    menuAberto = Estilo.lista(this, getString(R.string.wo_ordenar_de, nome), rotulos, marcadas = listOf(modos.indexOf(o.sort))) { i ->
        val novo = modos[i]
        if (novo == o.sort) return@lista
        // "Do meu jeito" começa da ordem que está na tela; depois Subir/Descer no OK segurado de cada app.
        val manual = if (novo == WorldOptions.Sort.MANUAL) appsDoMundo() else o.manual
        WorldOptions.save(this, cat, o.copy(sort = novo, manual = manual))
        atualizarAposMudanca(null)
    }
}

/** Pergunta um nome com o teclado da TV. Vazio = volta ao original (quem chama decide com AppNames.clean). */
internal fun MainActivity.pedirNome(titulo: String, atual: String, max: Int, aoSalvar: (String) -> Unit) {
    val campo = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; isSingleLine = true; textSize = 22f
        setText(atual); setSelection(text.length); hint = getString(R.string.wo_vazio_original)
        filters = arrayOf(android.text.InputFilter.LengthFilter(max))
    }
    val m = Estilo.dp(this, 20)
    val moldura = FrameLayout(this).apply { setPaddingRelative(m, Estilo.dp(this@pedirNome, 8), m, 0); addView(campo) }
    val d = CaixaTv(this).setTitle(titulo).setView(moldura)
        .setPositiveButton(android.R.string.ok) { _, _ -> aoSalvar(campo.text.toString()) }
        .setNegativeButton(android.R.string.cancel, null).create()
    CampoTv.enterConfirma(campo, d)
    d.setOnShowListener { campo.requestFocus() }
    d.show()
}
