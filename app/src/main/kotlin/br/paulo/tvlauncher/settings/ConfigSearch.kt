package br.paulo.tvlauncher.settings

import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import br.paulo.tvlauncher.ConfigLauncherActivity
import br.paulo.tvlauncher.ConfigLauncherActivity.Companion.BUSCA
import br.paulo.tvlauncher.ConfigLauncherActivity.Companion.TAG
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.R

private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

/** Busca nos ajustes (elevação 12.2). Saiu da ConfigLauncherActivity na refatoração de 21/11/2026: funções de extensão; o estado continua lá. */

internal fun ConfigLauncherActivity.voltarAoMenu() { (if (abaMontada == BUSCA) itemBusca else itensMenu[abaAtual])?.requestFocus() }

/** "Buscar" no alto do menu: foco nele mostra a caixa e a lista "mexidos há pouco" no lugar da aba. */
internal fun ConfigLauncherActivity.itemDaBusca(): TextView = TextView(this).apply {
    text = "⌕  " + getString(R.string.elv_aj_buscar)
    Estilo.aplicar(this, Estilo.Papel.CORPO)
    setPaddingRelative(dp(20), dp(11), dp(20), dp(11))
    maxLines = 1; ellipsize = TextUtils.TruncateAt.END
    isFocusable = true; isClickable = true
    pintarItemMenu(this, foco = false, escolhido = false)
    setOnFocusChangeListener { v, temFoco ->
        pintarItemMenu(this, foco = temFoco, escolhido = abaMontada == BUSCA)
        if (temFoco) { ui.removeCallbacks(trocarAba); if (abaMontada != BUSCA) mostrarBusca(); mostrarDica(noMenu = true) }
        v.animate().scaleX(if (temFoco) Estilo.ESCALA_LINHA else 1f).scaleY(if (temFoco) Estilo.ESCALA_LINHA else 1f).setDuration(Estilo.FOCO_MS).start()
    }
    setOnClickListener { if (abaMontada != BUSCA) mostrarBusca(); campoBusca?.requestFocus() }
    itemBusca = this
}

internal fun ConfigLauncherActivity.mostrarBusca() {
    abaMontada = BUSCA
    itensMenu.forEachIndexed { j, t -> pintarItemMenu(t, foco = t.hasFocus(), escolhido = false) }
    conteudo.removeAllViews()
    rolagem.scrollTo(0, 0)
    conteudo.addView(Estilo.texto(this, Estilo.Papel.TITULO, getString(R.string.elv_aj_buscar)).apply { textSize = 24f; setPaddingRelative(dp(8), dp(4), 0, 0) })
    conteudo.addView(Estilo.texto(this, Estilo.Papel.LEGENDA, getString(R.string.elv_aj_explica)).apply { setPaddingRelative(dp(8), dp(6), 0, dp(12)) })
    val campo = android.widget.EditText(this).apply {
        hint = getString(R.string.elv_aj_dica)
        setSingleLine()
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setTextColor(Estilo.TEXTO); setHintTextColor(Estilo.TEXTO_3); textSize = 20f
        background = Estilo.fundo(context, Estilo.SUPERFICIE_ALTA, Estilo.RAIO_PILULA, Estilo.BORDA)
        setPaddingRelative(dp(22), dp(14), dp(22), dp(14))
    }
    campoBusca = campo
    conteudo.addView(campo, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) })
    val resultados = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
    conteudo.addView(resultados, LinearLayout.LayoutParams(MATCH, WRAP))
    fun desenhar() {
        val termo = campo.text.toString().trim()
        resultados.removeAllViews()
        if (termo.length < 2) {
            val rec = BuscaAjustes.ler(prefsBusca.getString("recentes", null)).filter { it.aba in abas.indices }
            if (rec.isEmpty()) { resultados.addView(info(getString(R.string.elv_aj_recentes), getString(R.string.elv_aj_recentes_vazio)), lp()); return }
            secao(resultados, getString(R.string.elv_aj_recentes))
            val agora = System.currentTimeMillis()
            rec.forEach { r ->
                val quando = android.text.format.DateUtils.getRelativeTimeSpanString(r.em, agora, android.text.format.DateUtils.MINUTE_IN_MILLIS)
                resultados.addView(linha(r.titulo, "${abas[r.aba].titulo} · $quando", seta = true) { irPara(r.aba, r.titulo) }, lp())
            }
            return
        }
        // Pedido (onda E, 25/11/2026): "deixa a letra maior" vira um botão que já faz, acima da lista do que achou.
        SettingsIntents.entender(termo, SettingsIntents.modelos(getString(R.string.si_frases)))?.let { pedido(resultados, it) }
        val achados = BuscaAjustes.procurar(indiceDosAjustes(), termo, BuscaAjustes.grupos(getString(R.string.elv_aj_sinonimos)))
        if (achados.isEmpty()) { resultados.addView(info(getString(R.string.elv_aj_nada, termo), getString(R.string.elv_aj_nada_det)), lp()); return }
        achados.forEach { e ->
            resultados.addView(linha(e.titulo, "${abas[e.aba].titulo} · ${e.detalhe}", seta = true) {
                if (e.extras) startActivity(android.content.Intent(this, br.paulo.tvlauncher.ExtrasActivity::class.java).putExtra(br.paulo.tvlauncher.ExtrasActivity.EXTRA_FOCO, e.titulo))
                else irPara(e.aba, e.titulo)
            }, lp())
        }
    }
    val adiada = Runnable { if (abaMontada == BUSCA) desenhar() }
    campo.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(t: android.text.Editable?) { ui.removeCallbacks(adiada); ui.postDelayed(adiada, 300) }
    })
    desenhar()
}

/**
 * O índice: cada aba montada uma vez num lugar fora da tela, guardando o título e a explicação de cada linha (é o
 * mesmo que a pessoa vê ao descer o menu). Montado na primeira busca e guardado enquanto a tela estiver aberta.
 */
internal fun ConfigLauncherActivity.indiceDosAjustes(): List<BuscaAjustes.Entrada> {
    indice?.let { return it }
    val t0 = System.currentTimeMillis()
    indiceMontando.clear()
    abas.forEachIndexed { i, aba ->
        indexando = i
        try { aba.montar(LinearLayout(this)) } catch (e: Exception) { Log.w(TAG, "índice da aba $i: ${e.javaClass.simpleName}") }
    }
    indexando = -1
    return indiceMontando.toList().also { indice = it; Log.i(TAG, "índice dos ajustes: ${it.size} linhas em ${System.currentTimeMillis() - t0} ms") }
}

/** Abre a aba [aba] e põe o foco na linha [titulo] (rolando até ela). */
internal fun ConfigLauncherActivity.irPara(aba: Int, titulo: String) {
    abasAbertas += aba   // achado pela busca: a aba inteira, mesmo no modo Simples (a linha pode estar no "Mais ajustes")
    abaAtual = aba
    // O foco sai da linha da busca ANTES de a busca sumir (23/11/2026, achado na Sala): ao apagar o conteúdo, o Android
    // jogava o foco no "Buscar" do menu, e o foco nele remontava a busca por cima da aba aberta (OK "não fazia nada").
    // Agora o menu já vira o da porta da aba, o foco pousa no item dela e só então a aba é montada.
    abaMontada = aba
    mostrarAbasDoUso()
    itensMenu.getOrNull(aba)?.requestFocus()
    ui.removeCallbacks(trocarAba)
    mostrarAba(aba)
    itensMenu.forEachIndexed { j, t -> pintarItemMenu(t, foco = false, escolhido = j == abaAtual) }
    itemBusca?.let { pintarItemMenu(it, foco = false, escolhido = false) }
    conteudo.post {
        val alvo = focaveis(conteudo).firstOrNull { temTexto(it, titulo) } ?: primeiroFocavel(conteudo)
        alvo?.requestFocus()
        alvo?.let { v -> rolagem.post {
            val r = android.graphics.Rect(); v.getDrawingRect(r); conteudo.offsetDescendantRectToMyCoords(v, r)
            rolagem.smoothScrollTo(0, (r.top - dp(80)).coerceAtLeast(0))
        } }
    }
}

internal fun ConfigLauncherActivity.temTexto(v: View, t: String): Boolean = when (v) {
    is TextView -> v.text?.toString() == t
    is ViewGroup -> (0 until v.childCount).any { temTexto(v.getChildAt(it), t) }
    else -> false
}

internal fun ConfigLauncherActivity.lembrarRecente(aba: Int, titulo: String) {
    val l = BuscaAjustes.lembrar(BuscaAjustes.ler(prefsBusca.getString("recentes", null)), aba, titulo, System.currentTimeMillis())
    prefsBusca.edit().putString("recentes", BuscaAjustes.texto(l)).apply()
}

/** Como estava antes do pedido feito pela busca ("Desfazer" volta a ela). Só enquanto a tela está aberta. */
private var desfazerPedido: (() -> Unit)? = null
private var pedidoFeito: SettingsIntents.Action? = null

private fun ConfigLauncherActivity.nomePedido(a: SettingsIntents.Action): String = getString(when (a) {
    SettingsIntents.Action.TEXT_BIGGER -> R.string.si_maior
    SettingsIntents.Action.TEXT_SMALLER -> R.string.si_menor
    SettingsIntents.Action.GOAL_READ -> R.string.goal_read
    SettingsIntents.Action.GOAL_CLEAN -> R.string.goal_clean
    SettingsIntents.Action.GOAL_ENERGY -> R.string.goal_energy
    SettingsIntents.Action.LOOK_SOFALY -> R.string.hl_sofaly
    SettingsIntents.Action.LOOK_NIGHT -> R.string.hl_night
    SettingsIntents.Action.LOOK_MOUNTAIN -> R.string.hl_mountain
    SettingsIntents.Action.LOOK_MINIMAL -> R.string.hl_minimal
})

/** A linha "Fazer agora" (ou "Desfazer", se já foi feito nesta visita) no topo dos resultados. */
private fun ConfigLauncherActivity.pedido(resultados: LinearLayout, a: SettingsIntents.Action) {
    val nome = nomePedido(a)
    val feito = pedidoFeito == a && desfazerPedido != null
    resultados.addView(linha(getString(if (feito) R.string.si_desfazer else R.string.si_fazer, nome), getString(R.string.si_fazer_det), seta = true) {
        if (feito) { desfazerPedido?.invoke(); desfazerPedido = null; pedidoFeito = null; aviso(getString(R.string.goal_desfeito, nome)) }
        else { desfazerPedido = fazer(a); pedidoFeito = a; aviso(getString(R.string.goal_feito, nome)) }
        // A letra muda a própria tela: refaz (a busca some; a pessoa vê o resultado já maior). O resto redesenha a lista.
        if (a == SettingsIntents.Action.TEXT_BIGGER || a == SettingsIntents.Action.TEXT_SMALLER || a == SettingsIntents.Action.GOAL_READ) recreate()
        else campoBusca?.let { it.text = it.text }
    }, 0, lp())
}

/** Faz [a] e devolve como desfazer. */
private fun ConfigLauncherActivity.fazer(a: SettingsIntents.Action): () -> Unit {
    val ctx = this
    fun objetivo(g: SettingsGoals.Goal): () -> Unit { SettingsGoals.aplicar(ctx, g); return { SettingsGoals.desfazer(ctx) } }
    fun visual(l: br.paulo.tvlauncher.home.HomeLooks.Look): () -> Unit {
        val foto = br.paulo.tvlauncher.home.HomeLooks.foto(ctx)
        br.paulo.tvlauncher.home.HomeLooks.aplicar(ctx, l)
        return { br.paulo.tvlauncher.home.HomeLooks.voltar(ctx, foto) }
    }
    return when (a) {
        SettingsIntents.Action.TEXT_BIGGER, SettingsIntents.Action.TEXT_SMALLER -> {
            val antes = TamanhoTexto.atual(ctx)
            val i = TamanhoTexto.OPCOES.indexOf(antes) + if (a == SettingsIntents.Action.TEXT_BIGGER) 1 else -1
            TamanhoTexto.definir(ctx, TamanhoTexto.OPCOES[i.coerceIn(0, TamanhoTexto.OPCOES.lastIndex)])
            ({ TamanhoTexto.definir(ctx, antes) })
        }
        SettingsIntents.Action.GOAL_READ -> objetivo(SettingsGoals.Goal.READ)
        SettingsIntents.Action.GOAL_CLEAN -> objetivo(SettingsGoals.Goal.CLEAN)
        SettingsIntents.Action.GOAL_ENERGY -> objetivo(SettingsGoals.Goal.ENERGY)
        SettingsIntents.Action.LOOK_SOFALY -> visual(br.paulo.tvlauncher.home.HomeLooks.Look.SOFALY)
        SettingsIntents.Action.LOOK_NIGHT -> visual(br.paulo.tvlauncher.home.HomeLooks.Look.NIGHT)
        SettingsIntents.Action.LOOK_MOUNTAIN -> visual(br.paulo.tvlauncher.home.HomeLooks.Look.MOUNTAIN)
        SettingsIntents.Action.LOOK_MINIMAL -> visual(br.paulo.tvlauncher.home.HomeLooks.Look.MINIMAL)
    }
}
