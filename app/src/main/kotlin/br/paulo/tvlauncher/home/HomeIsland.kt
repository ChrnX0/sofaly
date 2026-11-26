package br.paulo.tvlauncher.home

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.Gravacoes
import br.paulo.tvlauncher.LembretesTv
import br.paulo.tvlauncher.MainActivity
import br.paulo.tvlauncher.MainActivity.Companion.ALTURA_TOPO
import br.paulo.tvlauncher.MainActivity.Companion.ILHA_PONTO_CALMO
import br.paulo.tvlauncher.MainActivity.Companion.ILHA_TEXTO_MAX
import br.paulo.tvlauncher.MainActivity.Companion.ILHA_VERMELHO
import br.paulo.tvlauncher.MainActivity.Companion.TOPO_PALCO
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.alarm.Agenda
import br.paulo.tvlauncher.alarm.NaoPerturbeTv
import br.paulo.tvlauncher.family.ModoVisita
import br.paulo.tvlauncher.listen.BarrasSom
import br.paulo.tvlauncher.network.CasaHA
import br.paulo.tvlauncher.screensaver.RelogioTela
import br.paulo.tvlauncher.txt

private const val WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
private const val MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT

/** Barra Ilha da HOME (BarraIlha: A+B+D, 18/11/2026). Saiu da MainActivity na refatoração de 21/11/2026. */

/**
 * Monta as duas peças da Ilha: o véu (atrás da barra aberta) e a pílula do centro (ponto laranja, hora, e o que está
 * acontecendo no sistema). Nenhuma é focável: com ▲ a barra de sempre volta,
 * com os botões de verdade (pausar, cancelar o timer, abrir o Diagnóstico).
 */
internal fun MainActivity.montarIlha(raiz: FrameLayout, topo: ViewGroup) {
    val veu = View(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xC7080910.toInt(), 0x80080910.toInt(), 0x00080910))
        alpha = 0f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    raiz.addView(veu, raiz.indexOfChild(topo).coerceAtLeast(0), FrameLayout.LayoutParams(MATCH, dp(TOPO_PALCO + ALTURA_TOPO + 90)))
    ilhaVeu = veu

    val sombra = 0x99000000.toInt()
    // Ponto calmo em branco apagado (crítica de design 19/11/2026): laranja só no foco; âmbar/vermelho seguem como semáforo.
    val ponto = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ILHA_PONTO_CALMO) } }
    ilhaPontoView = ponto
    val hora = RelogioTela(this, data = false).apply {
        format12Hour = "HH:mm"; format24Hour = "HH:mm"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTextColor(Color.WHITE)
        typeface = Typeface.create(fonteUi, Typeface.BOLD)
        includeFontPadding = false
    }
    ilhaHora = hora
    val temp = texto(18f, textoClaro, fonteUi).apply { includeFontPadding = false; maxLines = 1 }
    // Trilho de estado (como os ícones de estado da Apple TV): gravando, não perturbe, visita, fone. Sem texto.
    val trilho = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        setPaddingRelative(dp(10), 0, 0, 0)
    }
    ilhaTrilho = trilho
    val contador = texto(Estilo.Leitura.DICA, textoClaro, fonteUi).apply {
        includeFontPadding = false; visibility = View.GONE
        background = GradientDrawable().apply { cornerRadius = dp(9).toFloat(); setStroke(dp(1), 0x40FFFFFF) }
        setPaddingRelative(dp(7), dp(2), dp(7), dp(2))
    }
    ilhaContador = contador
    fun copiarTemp() { temp.text = txtTemp.text.toString().takeIf { it.isNotBlank() && it != "…" }.orEmpty(); temp.visibility = if (temp.text.isEmpty() || ilhaEvento != null) View.GONE else View.VISIBLE }
    txtTemp.addTextChangedListener(espelho { copiarTemp() })
    val icone = ImageView(this).apply { visibility = View.GONE }
    val barras = BarrasSom(this).apply { visibility = View.GONE }
    val txt = texto(18f, Estilo.TEXTO, fonteUi).apply {
        includeFontPadding = false; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; maxWidth = dp(ILHA_TEXTO_MAX)
    }
    val extra = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        addView(icone, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) })
        addView(barras, LinearLayout.LayoutParams(dp(11), dp(12)).apply { marginEnd = dp(8) })
        addView(txt)
    }
    // Nome do mundo da cruz na pílula, com o ícone dele à esquerda (WorldLabel, Paulo 22/11/2026): lugar fixo, a
    // coluna fica sem nome. Um fio separa o mundo (onde estou) da hora (o sistema). Só aparece no modo PILL.
    val mundoIcone = ImageView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    val mundoNome = texto(18f, Estilo.TEXTO, fonteUi).apply { includeFontPadding = false; maxLines = 1 }
    val mundo = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        addView(mundoIcone, LinearLayout.LayoutParams(dp(34), dp(28)).apply { marginEnd = dp(10) })
        addView(mundoNome)
        addView(View(this@montarIlha).apply { setBackgroundColor(0x33FFFFFF) },
            LinearLayout.LayoutParams(dp(1), dp(18)).apply { marginStart = dp(14); marginEnd = dp(14) })
    }
    ilhaMundo = mundo; ilhaMundoIcone = mundoIcone; ilhaMundoNome = mundoNome
    val pilula = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat(); setColor(0xC70A0B14.toInt()); setStroke(dp(1), 0x1FFFFFFF)
        }
        setPaddingRelative(dp(14), 0, dp(16), 0)
        // O leitor de tela lê a fila inteira (antes a pílula era invisível para ele e para o MCP).
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        addView(mundo, LinearLayout.LayoutParams(WRAP, WRAP))
        addView(ponto, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(10) })
        addView(hora)
        addView(trilho)
        addView(temp, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(10) })
        addView(extra, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(12) })
        addView(contador, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(10) })
    }
    // Pílula SEMPRE no centro (Paulo, 22/11/2026: "dinâmica" = se adapta ao que aparece nela). Mora numa faixa da
    // largura da tela só dela: a troca de tamanho anima nessa faixa (cresce/encolhe para os dois lados igual, como a
    // Dynamic Island) sem animar mais nada da HOME.
    val palco = FrameLayout(this).apply { clipChildren = false; clipToPadding = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    palco.addView(pilula, FrameLayout.LayoutParams(WRAP, dp(40), Gravity.CENTER_HORIZONTAL))
    raiz.addView(palco, FrameLayout.LayoutParams(MATCH, dp(40), Gravity.TOP).apply {
        topMargin = dp(TOPO_PALCO + (ALTURA_TOPO - 40) / 2)
    })
    // 18sp, a menor letra da HOME (TelasTest, 25/11/2026: com 15sp a dica "amanhã 6:45 · ▲ para abrir" ficava abaixo).
    val dica = texto(18f, Estilo.TEXTO, fonteUi).apply {
        includeFontPadding = false; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; maxWidth = dp(ILHA_TEXTO_MAX + 200)
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(0xE60A0B14.toInt()); setStroke(dp(1), 0x26FFFFFF) }
        setPaddingRelative(dp(16), dp(8), dp(16), dp(8))
        alpha = 0f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO   // a pílula já lê a fila inteira
    }
    raiz.addView(dica, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
        topMargin = dp(TOPO_PALCO + (ALTURA_TOPO - 40) / 2 + 46)
    })
    ilhaDica = dica
    ilhaPilula = pilula; ilhaExtra = extra; ilhaIcone = icone; ilhaBarras = barras; ilhaTexto = txt; ilhaTemp = temp
    copiarTemp()
    // As pílulas da barra mudam por todo lado (timer a cada segundo, rádio, rede): a Ilha lê o estado delas a cada
    // arrumação da tela. Barato: monta um Estado pequeno e só mexe na pílula quando o evento muda.
    topo.viewTreeObserver.addOnGlobalLayoutListener { atualizarIlha() }
    atualizarIlha()
    ui.post(tarefaIlhaTique)
    cruz?.categoriaAtual()?.let { mundoNaIlha(it) }
}

/**
 * Põe o mundo em foco na pílula (modo [WorldLabel.Place.PILL]) e tira o nome da coluna. Sem pílula (outra barra) ou no
 * modo coluna, a coluna mostra o nome como antes.
 */
internal fun MainActivity.mundoNaIlha(c: HomeCruz.Categoria) {
    val mundo = ilhaMundo
    // Barra única: o mundo já está na linha dele, acima dos cards; a pílula fica só com a hora (sem repetir).
    if (cruz?.faixa == true) { mundo?.visibility = View.GONE; return }
    val naPilula = mundo != null && WorldLabel.place(this) == WorldLabel.Place.PILL
    cruz?.nomeNaColuna(!naPilula)
    if (!naPilula || mundo == null) return
    if (ilhaMundoNome?.text?.toString() == c.nome && mundo.visibility == View.VISIBLE) return
    // A pílula muda de largura deslizando para os dois lados (centro fixo); o mundo novo acende junto.
    if (capaPronta && !semAnimacoes()) {
        (ilhaPilula?.parent as? ViewGroup)?.let { android.transition.TransitionManager.beginDelayedTransition(it, android.transition.ChangeBounds().setDuration(br.paulo.tvlauncher.DesignTokens.MS_FAST).setInterpolator(Estilo.curva())) }
        mundo.alpha = 0.35f; mundo.animate().alpha(1f).setDuration(br.paulo.tvlauncher.DesignTokens.MS_FAST).start()
    }
    ilhaMundoIcone?.setImageDrawable(icCruz("ilha:" + c.cat.id, WorldLabel.tipo(c.cat)))   // outra cópia do desenho da categoria
    ilhaMundoNome?.text = c.nome
    mundo.visibility = View.VISIBLE
}

/** Fora da thread da tela: o que a Ilha lê de banco/agenda/sistema. Modo visita fecha a agenda (como no "Bom dia"). */
internal fun MainActivity.lerFontesIlha() {
    val agora = System.currentTimeMillis()
    val hm = android.text.format.DateFormat.getTimeFormat(this)
    ilhaLembrete = runCatching {
        LembretesTv.listar(this).filter { it.inicio + BarraIlha.LEMBRETE_ATRASO_MS > agora }.minByOrNull { it.inicio }
            ?.let { "${it.titulo} · ${hm.format(java.util.Date(it.inicio))}" to it.inicio }
    }.getOrNull()
    val visita = runCatching { ModoVisita.ativo(this) }.getOrDefault(false)
    ilhaAgenda = if (visita) null else runCatching {
        Agenda.eventosDaTv(this, agora - BarraIlha.AGENDA_ATRASO_MS, agora + BarraIlha.AGENDA_JANELA_MS)
            .filter { !it.diaInteiro && it.inicio + BarraIlha.AGENDA_ATRASO_MS > agora }.minByOrNull { it.inicio }
            ?.let { "${it.titulo} · ${hm.format(java.util.Date(it.inicio))}" to it.inicio }
    }.getOrNull()
    // Céu da noite: só evento de verdade, visível daqui, com céu limpo; a pílula entra 3 h antes e sai no fim.
    ilhaCeu = runCatching {
        br.paulo.tvlauncher.weather.NightSkyTv.semEsperar(this, agora)?.takeIf { br.paulo.tvlauncher.weather.NightSky.naIlha(it, agora) }
            ?.let { br.paulo.tvlauncher.weather.NightSkyTv.textoIlha(this, it) to it.kind }
    }.getOrNull()
    // A Sofy (24/11/2026): o recado dela para a home e, de manhã, o que ela viu e fez de madrugada.
    ilhaSofy = runCatching { br.paulo.tvlauncher.observer.SofyTv.islandLine(this) }.getOrNull()
    ilhaSofyManha = runCatching { br.paulo.tvlauncher.observer.SofyTv.morningLine(this) }.getOrNull()
    val ind = HashSet<BarraIlha.Indicador>()
    if (runCatching { Gravacoes.resumoParaHome(this) }.getOrNull() != null) ind += BarraIlha.Indicador.GRAVANDO   // grava no ":player"
    if (runCatching { NaoPerturbeTv.motivoAgora(this) }.getOrNull() != null) ind += BarraIlha.Indicador.NAO_PERTURBE
    if (visita) ind += BarraIlha.Indicador.VISITA
    if (foneLigado()) ind += BarraIlha.Indicador.FONE
    ilhaIndicadores = ind
}

/** Fone com fio, Bluetooth (A2DP/BLE) ou aparelho auditivo como saída de som agora. */
internal fun MainActivity.foneLigado(): Boolean = runCatching {
    val am = getSystemService(android.media.AudioManager::class.java) ?: return false
    val fones = setOf(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
        android.media.AudioDeviceInfo.TYPE_HEARING_AID)
    am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { it.type in fones }
}.getOrDefault(false)

/** A pílula da barra aberta que responde ao evento (null = o evento não tem botão próprio). */
internal fun MainActivity.botaoDoEvento(e: BarraIlha.Evento?): View? = when (e?.tipo) {
    BarraIlha.Tipo.SEM_INTERNET -> pilulaSemInternet
    BarraIlha.Tipo.TIMER -> pilulaTimer
    BarraIlha.Tipo.ALARME -> pilulaAlarme
    BarraIlha.Tipo.TOCANDO -> pilulaTocando
    BarraIlha.Tipo.ANIVERSARIO -> pilulaAniversario
    BarraIlha.Tipo.JOGO -> pilulaJogo
    BarraIlha.Tipo.VENCIMENTO -> pilulaVencimento
    BarraIlha.Tipo.SERVICO_FORA -> pilulaServico
    BarraIlha.Tipo.LEMBRETE_CASA -> pilulaLembreteCasa
    BarraIlha.Tipo.CONTAGEM -> pilulaContagem
    else -> null
}

internal fun MainActivity.pintarTrilho(t: BarraIlha.Trilho) {
    val box = ilhaTrilho ?: return
    if (t == ilhaTrilhoMostrado) return
    ilhaTrilhoMostrado = t
    box.removeAllViews()
    for (i in t.mostrar) {
        val ic: Drawable = when (i) {
            BarraIlha.Indicador.GRAVANDO -> IconeLinha(IconeLinha.Tipo.GRAVAR, cor = ILHA_VERMELHO)
            BarraIlha.Indicador.NAO_PERTURBE -> IconeLinha(IconeLinha.Tipo.LUA, fracaoLua = 0.22, cor = 0xFFD9D5CF.toInt())
            BarraIlha.Indicador.VISITA -> IconeLinha(IconeLinha.Tipo.PESSOA, cor = 0xFFD9D5CF.toInt())
            BarraIlha.Indicador.FONE -> IconeLinha(IconeLinha.Tipo.FONE, cor = 0xFFD9D5CF.toInt())
        }
        box.addView(ImageView(this).apply { setImageDrawable(ic) }, LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginStart = dp(6) })
    }
    if (t.resto > 0) box.addView(texto(Estilo.Leitura.DICA, textoClaro, fonteUi).apply { text = "+${t.resto}"; includeFontPadding = false },
        LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(6) })
    box.visibility = if (t.mostrar.isEmpty()) View.GONE else View.VISIBLE
}

internal fun MainActivity.atualizarIlha() {
    val pilula = ilhaPilula ?: return
    fun TextView.se(p: View) = text?.toString()?.takeIf { p.visibility == View.VISIBLE || p in cortadas }
    val agora = System.currentTimeMillis()
    val fila = BarraIlha.fila(BarraIlha.Estado(
        semInternet = txtSemInternet.se(pilulaSemInternet),
        campainha = CasaHA.campainhaEm.takeIf { it > 0 }?.let {
            "${getString(R.string.casa_campainha)} · ${android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(it))}" },
        campainhaHaMs = CasaHA.campainhaEm.takeIf { it > 0 }?.let { agora - it },
        timer = txtTimer.se(pilulaTimer),
        alarme = txtAlarme.se(pilulaAlarme),
        alarmeFaltaMs = alarmeEm?.let { it - agora },
        lembrete = ilhaLembrete?.first,
        lembreteFaltaMs = ilhaLembrete?.let { it.second - agora },
        tocandoTitulo = txtTocandoTitulo.se(capsulaTocando),
        tocando = barrasTocando.tocando,
        agenda = ilhaAgenda?.first,
        agendaFaltaMs = ilhaAgenda?.let { it.second - agora },
        aniversario = txtAniversario.se(pilulaAniversario),
        jogo = txtJogo.se(pilulaJogo),
        vencimento = txtVencimento.se(pilulaVencimento),
        servicoFora = txtServico.se(pilulaServico),
        lembreteCasa = txtLembreteCasa.se(pilulaLembreteCasa),
        contagem = txtContagem.se(pilulaContagem),
        ceu = ilhaCeu?.first,
        // O clima renova a cada 15 min; um aviso de mais de 90 min (TV sem rede) já não vale.
        sofy = ilhaSofy?.text,
        sol = ilhaUv?.takeIf { agora - it.second < 90 * 60_000L }?.let { getString(R.string.ilha_uv, it.first) },
    ))
    pintarTrilho(BarraIlha.trilho(ilhaIndicadores))
    if (fila != ilhaFila && BarraIlha.mesmaEstrutura(ilhaFila, fila)) {
        // Só o texto mudou (o timer conta a cada segundo): não reinicia o rodízio nem repete o anúncio do TalkBack.
        ilhaFila = fila
        BarraIlha.agora = BarraIlha.paraMcp(fila)
    } else if (fila != ilhaFila) {
        val novo = BarraIlha.chegou(ilhaFila, fila)
        ilhaPasso = BarraIlha.passoDepois(novo ?: ilhaEvento, fila)   // o que chegou vai para a tela na hora
        if (novo != null) crescerIlha(novo)
        ilhaFila = fila
        // Sem a hora quando há fila: a descrição só muda quando a fila muda, e a hora nela ficava velha (visto na Sala,
        // v464530: "11 : 17" às 11:20). O leitor de tela anuncia a fila; a hora ele lê quando a fila está vazia.
        val desc = if (fila.isEmpty()) ilhaHora?.text?.toString().orEmpty() else BarraIlha.descricao("", fila).removePrefix(". ")
        pilula.contentDescription = desc
        BarraIlha.agora = BarraIlha.paraMcp(fila)
        (ilhaPontoView?.background as? GradientDrawable)?.setColor(when (BarraIlha.ponto(fila)) {
            BarraIlha.Ponto.ALERTA -> ILHA_VERMELHO
            BarraIlha.Ponto.ATENCAO -> Estilo.ATENCAO
            BarraIlha.Ponto.CALMO -> ILHA_PONTO_CALMO
        })
        ui.removeCallbacks(tarefaIlhaGira)
        if (fila.size > 1 && homeNaFrente) ui.postDelayed(tarefaIlhaGira, BarraIlha.RODIZIO_MS)
    }
    aplicarIlha()
}

/** Ilha que cresce (como a Dynamic Island): por 5 s, embaixo da pílula, o que chegou e "▲ para abrir". */
internal fun MainActivity.crescerIlha(e: BarraIlha.Evento) {
    val abre = botaoDoEvento(e) != null
    // Sem nada para abrir e com o texto inteiro já na pílula, a 2ª pílula só repetia a 1ª (foto da Sala 24/11/2026:
    // "Oftalmo Yoshi · 14:00" duas vezes). Ela fica para o texto cortado (…) e para o "▲ para abrir".
    if (!abre && !ilhaCortada(e.texto)) return
    crescerIlhaTexto(if (abre) "${e.texto}  ·  ${getString(R.string.ilha_dica_abrir)}" else e.texto, e.atencao)
}

/**
 * [texto] vai sair cortado (reticências) na pílula? Mede com a letra da pílula contra a largura máxima dela; chamado
 * antes de a pílula trocar de texto, por isso não dá para olhar o layout. Sem a pílula, conta como cortado.
 */
private fun MainActivity.ilhaCortada(texto: String): Boolean {
    val t = ilhaTexto ?: return true
    val max = t.maxWidth.takeIf { it in 1 until Int.MAX_VALUE } ?: return true
    return t.paint.measureText(texto) > max - t.totalPaddingLeft - t.totalPaddingRight
}

internal fun MainActivity.crescerIlhaTexto(texto: String, atencao: Boolean) {
    val d = ilhaDica ?: return
    if (!homeNaFrente || !barraRecolhida) return
    d.text = texto
    d.setTextColor(if (atencao) Estilo.ATENCAO else Estilo.TEXTO)
    d.animate().cancel()
    if (semAnimacoes()) { d.alpha = 1f; d.translationY = 0f }
    else { d.translationY = -dp(6).toFloat(); d.animate().alpha(1f).translationY(0f).setDuration(br.paulo.tvlauncher.DesignTokens.MS_FAST).setInterpolator(Estilo.curva()).start() }
    ui.removeCallbacks(tarefaIlhaEncolhe)
    ui.postDelayed(tarefaIlhaEncolhe, BarraIlha.CRESCER_MS)
}

/** Mostra o evento da vez (rodízio) na pílula; só mexe nas views quando ele muda. */
internal fun MainActivity.aplicarIlha() {
    val pilula = ilhaPilula ?: return
    val fila = ilhaFila
    val e = BarraIlha.naVez(fila, ilhaPasso)
    ilhaContador?.let { c ->
        val t = BarraIlha.contador(fila, ilhaPasso)
        if (c.text.toString() != t) c.text = t
        c.visibility = if (t.isEmpty()) View.GONE else View.VISIBLE
    }
    if (e == ilhaEvento) return
    val mudouTipo = e?.tipo != ilhaEvento?.tipo
    if (mudouTipo && capaPronta && !semAnimacoes()) {
        android.transition.TransitionManager.beginDelayedTransition(pilula.parent as? ViewGroup ?: pilula, android.transition.AutoTransition().setDuration(br.paulo.tvlauncher.DesignTokens.MS_FAST).setInterpolator(Estilo.curva()))
    }
    ilhaEvento = e
    val extra = ilhaExtra ?: return
    if (e == null) {
        extra.visibility = View.GONE
        ilhaBarras?.tocando = false
    } else {
        ilhaTexto?.text = e.texto
        ilhaTexto?.setTextColor(if (e.atencao) Estilo.ATENCAO else Estilo.TEXTO)
        val ic: Drawable? = when (e.tipo) {
            BarraIlha.Tipo.SEM_INTERNET -> IconeApp(IconeApp.Tipo.SEM_REDE, Estilo.ATENCAO)
            BarraIlha.Tipo.TIMER -> IconeApp(IconeApp.Tipo.TIMER)
            BarraIlha.Tipo.ALARME -> IconeLinha(IconeLinha.Tipo.DESPERTADOR)
            BarraIlha.Tipo.JOGO, BarraIlha.Tipo.LEMBRETE -> IconeApp(IconeApp.Tipo.TV)
            BarraIlha.Tipo.AGENDA -> IconeLinha(IconeLinha.Tipo.CALENDARIO)
            BarraIlha.Tipo.SERVICO_FORA -> IconeApp(IconeApp.Tipo.SEM_REDE, Estilo.ATENCAO)
            BarraIlha.Tipo.LEMBRETE_CASA -> IconeLinha(IconeLinha.Tipo.RECADO)
            BarraIlha.Tipo.CONTAGEM -> IconeLinha(IconeLinha.Tipo.CALENDARIO)
            BarraIlha.Tipo.SOL -> IconeLinha(IconeLinha.Tipo.SOL)
            // A Sofy fala com o próprio rosto (a cara do recado), parado na pose final: na Ilha nada se mexe sozinho.
            BarraIlha.Tipo.SOFY -> br.paulo.tvlauncher.observer.SofyDrawable(this, ilhaSofy?.expression ?: br.paulo.tvlauncher.observer.SofyPolicy.Expression.ATTENTIVE)
            // Ícones provisórios: a arte do céu da noite é da thread Design.
            // Céu da noite: os 4 ícones de traço do estudo (24/11/2026), mesmo branco dos outros ícones da Ilha.
            BarraIlha.Tipo.CEU -> NightSkyGlyph(ilhaCeu?.second ?: br.paulo.tvlauncher.weather.NightSky.Kind.METEORS, android.graphics.Color.WHITE)
            else -> null
        }
        if (mudouTipo) { ilhaIcone?.setImageDrawable(ic); ilhaIcone?.visibility = if (ic != null) View.VISIBLE else View.GONE }
        ilhaBarras?.visibility = if (e.tipo == BarraIlha.Tipo.TOCANDO) View.VISIBLE else View.GONE
        ilhaBarras?.tocando = e.tipo == BarraIlha.Tipo.TOCANDO && e.tocando
        extra.visibility = View.VISIBLE
    }
    ilhaTemp?.visibility = if (e != null || ilhaTemp?.text.isNullOrEmpty()) View.GONE else View.VISIBLE
}

/** Ao acordar a TV (Ilha): a barra fica aberta por 4 s, para quem chega ver tudo, e recolhe se o foco está na cruz. */
internal fun MainActivity.abrirIlhaAoAcordar() {
    if (barraCruz != LayoutHome.BarraCruz.ILHA || ilhaPilula == null || !homeNaFrente) return
    recolherBarra(false)
    // Lê de novo agora (a Sofy da manhã só vale na janela das 6h ao meio-dia; a última leitura pode ser da noite).
    filaApps.execute { runCatching { lerFontesIlha() }; ui.post { atualizarIlha() } }
    ui.removeCallbacks(tarefaIlhaAcordou)
    ui.postDelayed(tarefaIlhaAcordou, BarraIlha.ACORDAR_ABERTA_MS)
}
