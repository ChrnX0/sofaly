package br.paulo.tvlauncher

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import br.paulo.tvlauncher.settings.BackgroundStyle
import br.paulo.tvlauncher.settings.CardStyle
import br.paulo.tvlauncher.settings.ClockStyle
import br.paulo.tvlauncher.settings.FontChoice

/**
 * Ajuste fino com prévia ao vivo (21/11/2026, proposta B do plano para passar o Projectivy).
 *
 * Porquê: no Projectivy cada ajuste visual se vê na hora; na Sofaly era mudar em Configurações e voltar para ver.
 * Para quê: ajustar letra, hora, cor de destaque, cantos, brilho, tamanho da cruz e o fundo VENDO a tela inicial.
 * Como: um painel de vidro à direita (janela translúcida) por cima da HOME. Cada linha troca o valor com OK, ▶ ou ◀;
 * a HOME ouve as preferências e se refaz atrás do painel (MainActivity.ouvinteAjuste). Abre a HOME antes, para que a
 * prévia seja dela e não da tela de Configurações. Movimento: painel entra em 400 ms e sai em 350 ms, curva
 * "smooth-out" (0.22, 1, 0.36, 1), os tokens de painel do transitions.dev.
 * Liga com: as mesmas chaves das linhas de Configurações > Aparência (ConfigFineTuneRows.kt).
 */
class FineTuneActivity : Activity() {

    private lateinit var painel: LinearLayout
    private var fechando = false
    private val curva = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    /** Uma linha: título e o valor atual; [trocar] recebe +1/-1 e devolve o nome do novo valor. */
    private class Ajuste(val titulo: Int, val valor: () -> String, val trocar: (Int) -> String)

    override fun onCreate(salvo: Bundle?) {
        super.onCreate(salvo)
        aberto = true
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d + 0.5f).toInt()

        val lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        painel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(28), dp(32), dp(28), dp(24))
            background = android.graphics.drawable.GradientDrawable().apply {
                // Opaco (teste 100%, 24/11/2026): com 0xEB o texto da HOME vazava por trás da dica e das linhas na Sala.
                setColor(Estilo.VIDRO_TINTA or (0xFF shl 24))   // a tinta do vidro do Estilo, sem transparência
                val r = dp(Estilo.RAIO + 8).toFloat()
                cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)   // canto arredondado só do lado da HOME
                setStroke(dp(1), Estilo.VIDRO_BORDA)
            }
            addView(Estilo.tituloTela(this@FineTuneActivity, getString(R.string.ft_titulo)))
            addView(Estilo.texto(this@FineTuneActivity, Estilo.Papel.LEGENDA, getString(R.string.ft_dica)),
                LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4); bottomMargin = dp(12) })
            addView(ScrollView(this@FineTuneActivity).apply {
                isVerticalScrollBarEnabled = false; clipChildren = false; clipToPadding = false; isFocusable = false
                isVerticalFadingEdgeEnabled = true; setFadingEdgeLength(dp(32))
                addView(lista)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        for (a in ajustes()) lista.addView(linha(a), Estilo.lpLinha(this))

        setContentView(FrameLayout(this).apply {
            addView(painel, FrameLayout.LayoutParams(dp(LARGURA), -1, Gravity.END))
        })
        if (salvo == null) {   // refeito por troca de letra: sem entrar de novo
            painel.translationX = dp(LARGURA).toFloat()
            painel.alpha = 0f
            painel.animate().translationX(0f).alpha(1f).setDuration(ABRIR_MS).setInterpolator(curva).start()
        }
        lista.getChildAt(0)?.requestFocus()
    }

    private fun linha(a: Ajuste): View {
        val valor = Estilo.valor(this, a.valor(), seta = false)
        fun mostrar(t: String) { valor.text = "‹  $t  ›" }
        mostrar(a.valor())
        return Estilo.linhaOpcao(this, getString(a.titulo), null, valor).apply {
            setOnClickListener { mostrar(a.trocar(1)) }
            setOnKeyListener { _, code, ev ->
                if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (code) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { mostrar(a.trocar(1)); true }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { mostrar(a.trocar(-1)); true }
                    else -> false
                }
            }
        }
    }

    private fun ajustes(): List<Ajuste> = listOf(
        // Espaçamento da cruz (21/11/2026): as 3 propostas de ritmo e o de antes, para o Paulo comparar na TV.
        Ajuste(R.string.ac_ritmo, { nomeRitmo(br.paulo.tvlauncher.home.CrossRhythm.ler(this)) }) { passo ->
            val r = br.paulo.tvlauncher.home.CrossRhythm.entries
            nomeRitmo(r[Math.floorMod(br.paulo.tvlauncher.home.CrossRhythm.ler(this).ordinal + passo, r.size)]
                .also { br.paulo.tvlauncher.settings.Extras.definirNumero(this, br.paulo.tvlauncher.home.CrossRhythm.KEY, it.ordinal) })
        },
    ) + (if (br.paulo.tvlauncher.home.BottomBar.strip(this)) emptyList() else listOf(
        // Esconder o que não vale (Paulo, 25/11/2026, onda A da simplificação): na barra única a cruz some (HomeCruz
        // deixa a cruz GONE) e o nome do mundo tem linha própria (mundoNaIlha); estas duas linhas não mudariam nada.
        // Nome do mundo (22/11/2026, Paulo): junto da hora, na pílula do topo, ou na coluna como era.
        Ajuste(R.string.wl_titulo, { nomeLugarMundo(br.paulo.tvlauncher.home.WorldLabel.place(this)) }) { passo ->
            val m = br.paulo.tvlauncher.home.WorldLabel.Place.entries
            nomeLugarMundo(m[Math.floorMod(br.paulo.tvlauncher.home.WorldLabel.place(this).ordinal + passo, m.size)]
                .also { br.paulo.tvlauncher.settings.Extras.definirNumero(this, br.paulo.tvlauncher.home.WorldLabel.KEY, it.ordinal) })
        },
        // Nome das categorias na cruz (Paulo, 22/11/2026): com nome ou só o ícone, em todas de uma vez. Cada categoria
        // também troca sozinha no menu dela (segurar OK); aqui mostra "Só o ícone" só quando todas estão assim.
        Ajuste(R.string.wo_nomes_titulo, { nomeDasCategorias() }) { _ ->
            val todasSo = soIconeEmTodas()
            for (c in br.paulo.tvlauncher.home.CruzLogica.Categoria.entries)
                br.paulo.tvlauncher.home.WorldOptions.update(this, c) { it.copy(iconOnly = !todasSo) }
            nomeDasCategorias()
        },
    )) + listOf(
        // Jeito da tela inicial (BottomBar): barra única (padrão desde 24/11/2026), cruz ou barra embaixo. A lista de
        // ajustes muda com o jeito (as opções dos cards só valem na barra única): recria o painel.
        Ajuste(R.string.bb_titulo, { nomeJeito(br.paulo.tvlauncher.home.BottomBar.mode(this)) }) { passo ->
            nomeJeito(br.paulo.tvlauncher.home.BottomBar.next(this, passo)).also { window.decorView.post { recreate() } }
        },
    ) + (if (br.paulo.tvlauncher.home.BottomBar.strip(this)) ajustesFaixa() else emptyList()) +
        (if (br.paulo.tvlauncher.home.BottomBar.rows(this)) ajustesFileiras() else emptyList()) + listOf(
        // Continuar no topo de cada categoria (Paulo, 22/11/2026): desligado de fábrica (ContinueOption).
        Ajuste(R.string.co_titulo, { getString(if (br.paulo.tvlauncher.home.ContinueOption.on(this)) R.string.co_mostrar else R.string.co_esconder) }) { _ ->
            val on = !br.paulo.tvlauncher.home.ContinueOption.on(this)
            br.paulo.tvlauncher.home.ContinueOption.set(this, on)
            getString(if (on) R.string.co_mostrar else R.string.co_esconder)
        },
        // Forma de "Todos os apps" (22/11/2026, Paulo: as 3; padrão Prateleiras, "mais familiar").
        Ajuste(R.string.aa_layout_titulo, { nomeFormaTodos(br.paulo.tvlauncher.home.AllAppsLayout.mode(this)) }) { passo ->
            val m = br.paulo.tvlauncher.home.AllAppsLayout.Mode.entries
            nomeFormaTodos(m[Math.floorMod(br.paulo.tvlauncher.home.AllAppsLayout.mode(this).ordinal + passo, m.size)]
                .also { br.paulo.tvlauncher.settings.Extras.definirNumero(this, br.paulo.tvlauncher.home.AllAppsLayout.KEY, it.ordinal) })
        },
        // Cartões dos apps (itens 3+4, 21/11/2026): as duas propostas para o Paulo comparar vendo a tela.
        Ajuste(R.string.ac_cartoes, { nomeCartoes(br.paulo.tvlauncher.home.AppChannels.mode(this)) }) { passo ->
            val m = br.paulo.tvlauncher.home.AppChannels.Mode.entries
            nomeCartoes(m[Math.floorMod(br.paulo.tvlauncher.home.AppChannels.mode(this).ordinal + passo, m.size)]
                .also { br.paulo.tvlauncher.settings.Extras.definirNumero(this, br.paulo.tvlauncher.home.AppChannels.KEY_MODE, it.ordinal) })
        },
        Ajuste(R.string.fc_titulo, { FontChoice.current(this).name }) { passo -> trocarFonte(passo) },
        Ajuste(R.string.ck_formato, { nomeFormato(ClockStyle.format(this)) }) { passo ->
            nomeFormato(ClockStyle.Format.entries[Math.floorMod(ClockStyle.format(this).ordinal + passo, ClockStyle.Format.entries.size)]
                .also { br.paulo.tvlauncher.settings.Extras.definirNumero(this, ClockStyle.KEY_FORMAT, it.ordinal) })
        },
        Ajuste(R.string.ck_cor, { nomeCorHora(ClockStyle.color(this)) }) { passo ->
            nomeCorHora(ClockStyle.Color.entries[Math.floorMod(ClockStyle.color(this).ordinal + passo, ClockStyle.Color.entries.size)]
                .also { br.paulo.tvlauncher.settings.Extras.definirNumero(this, ClockStyle.KEY_COLOR, it.ordinal) })
        },
        Ajuste(R.string.cs_destaque, { nomeDestaque(CardStyle.accent(this)) }) { passo ->
            nomeDestaque(CardStyle.next(this, CardStyle.KEY_ACCENT, CardStyle.accent(this), passo))
        },
        Ajuste(R.string.cs_cantos, { nomeCantos(CardStyle.corners(this)) }) { passo ->
            nomeCantos(CardStyle.next(this, CardStyle.KEY_CORNERS, CardStyle.corners(this), passo))
        },
        Ajuste(R.string.cs_brilho, { nomeBrilho(CardStyle.glow(this)) }) { passo ->
            nomeBrilho(CardStyle.next(this, CardStyle.KEY_GLOW, CardStyle.glow(this), passo))
        },
    ) + (if (br.paulo.tvlauncher.home.BottomBar.mode(this) == br.paulo.tvlauncher.home.BottomBar.Mode.CROSS) listOf(
        // Tamanho da cruz só vale no jeito cruz (MainActivity ignora com a barra embaixo): na barra a linha não aparece.
        Ajuste(R.string.cs_tamanho, { nomeTamanho(CardStyle.size(this)) }) { passo ->
            nomeTamanho(CardStyle.next(this, CardStyle.KEY_SIZE, CardStyle.size(this), passo))
        }) else emptyList()) + listOf(
        // Fundo (BackgroundStyle, item 2): cor por cima / sólida, cor, desfoque, transição e "novo fundo agora".
        Ajuste(R.string.bg_tintura, { nomeTintura(BackgroundStyle.tint(this)) }) { passo ->
            nomeTintura(BackgroundStyle.Tint.entries[BackgroundStyle.next(this, BackgroundStyle.KEY_TINT, BackgroundStyle.tint(this), BackgroundStyle.Tint.entries.size, passo)])
        },
        Ajuste(R.string.bg_cor, { nomeCorFundo(BackgroundStyle.tintColor(this)) }) { passo ->
            nomeCorFundo(BackgroundStyle.TintColor.entries[BackgroundStyle.next(this, BackgroundStyle.KEY_TINT_COLOR, BackgroundStyle.tintColor(this), BackgroundStyle.TintColor.entries.size, passo)])
        },
    ) + (if (BackgroundStyle.blurPossivel) listOf(
        Ajuste(R.string.bg_desfoque, { nomeDesfoque(BackgroundStyle.blur(this)) }) { passo ->
            nomeDesfoque(BackgroundStyle.Blur.entries[BackgroundStyle.next(this, BackgroundStyle.KEY_BLUR, BackgroundStyle.blur(this), BackgroundStyle.Blur.entries.size, passo)])
        }) else emptyList()) + listOf(
        Ajuste(R.string.bg_transicao, { nomeTransicao(BackgroundStyle.fade(this)) }) { passo ->
            nomeTransicao(BackgroundStyle.Fade.entries[BackgroundStyle.next(this, BackgroundStyle.KEY_FADE, BackgroundStyle.fade(this), BackgroundStyle.Fade.entries.size, passo)])
        },
        Ajuste(R.string.bg_novo, { "OK" }) { _ ->
            getString(if (BackgroundStyle.proximoFundo?.invoke() == true) R.string.bg_trocado else R.string.bg_nao_troca)
        },
    ) + (if (br.paulo.tvlauncher.network.CasaHA.configurado(this)) listOf(
        // Câmera da casa (Home Assistant) no fundo, e a da campainha quando ela toca (CameraBackground).
        Ajuste(R.string.bg_camera, { nomeCamera }) { _ -> escolherCamera(); nomeCamera },
        Ajuste(R.string.bg_campainha, { getString(if (BackgroundStyle.doorbell(this)) R.string.bg_ligado else R.string.cs_sem) }) { _ ->
            BackgroundStyle.setDoorbell(this, !BackgroundStyle.doorbell(this))
            getString(if (BackgroundStyle.doorbell(this)) R.string.bg_ligado else R.string.cs_sem)
        },
    ) else emptyList()) + listOf(
        // Foco (régua de estilo, 22/11/2026): Fio é o padrão (Paulo); Véu e Quadro para quem quer o foco mais visível.
        Ajuste(R.string.ft_foco, { nomeFoco(br.paulo.tvlauncher.settings.FocusStyle.ler(this)) }) { passo ->
            val f = br.paulo.tvlauncher.settings.FocusStyle.entries
            nomeFoco(f[Math.floorMod(br.paulo.tvlauncher.settings.FocusStyle.ler(this).ordinal + passo, f.size)]
                .also { br.paulo.tvlauncher.settings.FocusStyle.gravar(this, it) })
        },
    )

    private fun nomeJeito(m: br.paulo.tvlauncher.home.BottomBar.Mode) = getString(when (m) {
        br.paulo.tvlauncher.home.BottomBar.Mode.CROSS -> R.string.bb_cruz
        br.paulo.tvlauncher.home.BottomBar.Mode.BAR -> R.string.bb_barra
        br.paulo.tvlauncher.home.BottomBar.Mode.STRIP -> R.string.bb_faixa
        br.paulo.tvlauncher.home.BottomBar.Mode.ROWS -> R.string.bb_fileiras
    })

    /**
     * Opções das Fileiras (RowsHome; Paulo, 26/11/2026: "clone deles, com todas as opções"): as mesmas do Projectivy em
     * Aparência > Cartões e Categorias, na mesma ordem. Cada ◀ ▶ grava; a HOME atrás se refaz na volta.
     */
    private fun ajustesFileiras(): List<Ajuste> {
        val R2 = br.paulo.tvlauncher.home.RowsHome
        fun o() = R2.options(this)
        fun gira(key: String, atual: Int, total: Int, passo: Int) = R2.next(this, key, atual, total, passo)
        fun nomes(vararg ids: Int) = ids.map { getString(it) }
        val tam = nomes(R.string.rw_xs, R.string.rw_s, R.string.rw_m, R.string.rw_l, R.string.rw_xl)
        val esp = nomes(R.string.rw_nenhum, R.string.rw_justo, R.string.rw_normal, R.string.rw_largo, R.string.rw_mais_largo)
        val can = nomes(R.string.rw_reto, R.string.rw_leve, R.string.rw_suave, R.string.rw_redondo, R.string.rw_bem_redondo)
        val foc = nomes(R.string.rw_igual, R.string.rw_s, R.string.rw_m, R.string.rw_l)
        val efe = nomes(R.string.rw_nenhum, R.string.rw_brilho, R.string.rw_sombra_proj)
        val ani = nomes(R.string.rw_nenhum, R.string.rw_respirar, R.string.rw_esmaecer, R.string.rw_pulsar, R.string.rw_reluzir)
        val vao = listOf(getString(R.string.rw_auto)) + br.paulo.tvlauncher.home.RowsHome.Spacing.entries.drop(1).map { "${(it.pct * 100).toInt()}%" }
        val opa = listOf(getString(R.string.rw_escondidas)) + br.paulo.tvlauncher.home.RowsHome.Dim.entries.drop(1).map { "${(it.alpha * 100).toInt()}%" }
        val som = nomes(R.string.rw_nenhum, R.string.rw_fraca, R.string.rw_normal, R.string.rw_forte)
        val simNao = nomes(R.string.st_sem, R.string.st_com)
        val prop = listOf("16/9", "4/3", "3/2", "1/1", "2/3", getString(R.string.rw_poster))
        val comp = nomes(R.string.rw_curta, R.string.rw_media, R.string.rw_longa, R.string.rw_tela_toda)
        val B = br.paulo.tvlauncher.home.StripBackground
        val fundos = listOf(R.string.st_fundo_vale, R.string.st_fundo_serra, R.string.st_fundo_noite, R.string.st_fundo_tema)
        fun enumA(t: Int, nomes: List<String>, key: String, atual: () -> Int) =
            Ajuste(t, { nomes[atual()] }) { p -> nomes[gira(key, atual(), nomes.size, p)] }
        fun simA(t: Int, key: String, atual: () -> Boolean) =
            Ajuste(t, { simNao[if (atual()) 1 else 0] }) { p -> simNao[gira(key, if (atual()) 1 else 0, 2, p)] }
        return listOf(
            simA(R.string.rw_nome_card, R2.KEY_CARD_TITLE) { o().cardTitle },
            enumA(R.string.rw_cantos, can, R2.KEY_CORNERS) { o().corners.ordinal },
            enumA(R.string.rw_tamanho, tam, R2.KEY_SIZE) { o().size.ordinal },
            enumA(R.string.rw_espaco, esp, R2.KEY_GAP) { o().gap.ordinal },
            enumA(R.string.rw_foco_tamanho, foc, R2.KEY_FOCUS_SIZE) { o().focusSize.ordinal },
            enumA(R.string.rw_efeito, efe, R2.KEY_EFFECT) { o().effect.ordinal },
            enumA(R.string.rw_brilho_anim, ani, R2.KEY_GLOW_ANIM) { o().glowAnim.ordinal },
            simA(R.string.rw_titulo_fileira, R2.KEY_ROW_TITLE) { o().rowTitle },
            simA(R.string.rw_icone_fileira, R2.KEY_ROW_ICON) { o().rowIcon },
            enumA(R.string.rw_espaco_fileiras, vao, R2.KEY_SPACING) { o().spacing.ordinal },
            simA(R.string.rw_fixo, R2.KEY_FIXED) { o().fixed },
            simA(R.string.rw_esconder_acima, R2.KEY_HIDE_ABOVE) { o().hideAbove },
            enumA(R.string.rw_opacidade, opa, R2.KEY_DIM) { o().dim.ordinal },
            enumA(R.string.rw_sombra, som, R2.KEY_SHADOW) { o().shadow.ordinal },
            simA(R.string.rw_hora, R2.KEY_CLOCK) { o().clock },
            simA(R.string.rw_status, R2.KEY_STATUS) { o().status },
            // Cartões (onda 2): o resto de Aparência > Cartões dele, na ordem dele
            simA(R.string.rw_onda, R2.KEY_RIPPLE) { o().ripple },
            enumA(R.string.rw_fundo_card, nomes(R.string.rw_auto, R.string.rw_grafite, R.string.rw_preto, R.string.rw_branco, R.string.rw_destaque), R2.KEY_CARD_BG) { o().cardBg.ordinal },
            simA(R.string.rw_zoom, R2.KEY_ZOOM) { o().zoom },
            enumA(R.string.rw_brilho_tam, nomes(R.string.rw_fino, R.string.rw_normal, R.string.rw_grosso), R2.KEY_GLOW_SIZE) { o().glowSize.ordinal },
            enumA(R.string.rw_brilho_cor, nomes(R.string.rw_dinamica, R.string.rw_branco, R.string.rw_destaque), R2.KEY_GLOW_COLOR) { o().glowColor.ordinal },
            simA(R.string.rw_progresso, R2.KEY_PROGRESS) { o().progress },
            simA(R.string.rw_selo, R2.KEY_BADGE) { o().badge },
            enumA(R.string.rw_prop_tv, prop, R2.KEY_TV_RATIO) { o().tvRatio.ordinal },
            enumA(R.string.rw_prop_cel, prop, R2.KEY_MOBILE_RATIO) { o().mobileRatio.ordinal },
            enumA(R.string.rw_tinta, nomes(R.string.rw_creme, R.string.rw_preto, R.string.rw_destaque), R2.KEY_ICON_TINT) { o().iconTint.ordinal },
            // Categorias (bloco 3): margens das fileiras
            enumA(R.string.rw_margem_inicio, nomes(R.string.rw_justo, R.string.rw_s, R.string.rw_normal, R.string.rw_largo, R.string.rw_mais_largo), R2.KEY_START) { o().start.ordinal },
            enumA(R.string.rw_margem_fim, nomes(R.string.rw_nenhum, R.string.rw_s, R.string.rw_m, R.string.rw_l), R2.KEY_END) { o().end.ordinal },
            enumA(R.string.rw_margem_topo, nomes(R.string.rw_mais_alto, R.string.rw_alto, R.string.rw_normal, R.string.rw_baixo, R.string.rw_mais_baixo), R2.KEY_TOP) { o().top.ordinal },
            // Fundo (bloco 5): a foto, a sombra por cima e a troca sozinha
            Ajuste(R.string.st_fundo, { getString(fundos[B.choice(this).ordinal]) }) { p -> getString(fundos[B.next(this, p).ordinal]) },
            simA(R.string.rw_fundo_foco, R2.KEY_FOCUS_BG) { o().focusBg },
            enumA(R.string.rw_trocar_fundo, listOf(getString(R.string.rw_nunca)) + br.paulo.tvlauncher.home.RowsHome.BgEvery.entries.drop(1).map { if (it.min < 60) getString(R.string.rw_min, it.min) else getString(R.string.rw_horas, it.min / 60) }, R2.KEY_BG_EVERY) { o().bgEvery.ordinal },
            enumA(R.string.rw_sombra_cor, nomes(R.string.rw_preto, R.string.rw_grafite), R2.KEY_SHADOW_TINT) { o().shadowTint.ordinal },
            enumA(R.string.rw_sombra_larg, comp, R2.KEY_SHADOW_W) { o().shadowW.ordinal },
            enumA(R.string.rw_sombra_alt, comp, R2.KEY_SHADOW_H) { o().shadowH.ordinal },
            // Barra de status, hora e letra (bloco 6)
            simA(R.string.rw_ocultar_topo, R2.KEY_AUTOHIDE) { o().autohide },
            enumA(R.string.rw_status_cor, nomes(R.string.rw_creme, R.string.rw_branco, R.string.rw_destaque), R2.KEY_STATUS_COLOR) { o().statusColor.ordinal },
            enumA(R.string.rw_status_fundo, nomes(R.string.rw_vidro, R.string.rw_escuro, R.string.rw_nenhum), R2.KEY_STATUS_BOX) { o().statusBox.ordinal },
            enumA(R.string.rw_status_tam, nomes(R.string.rw_s, R.string.rw_m, R.string.rw_l), R2.KEY_STATUS_SIZE) { o().statusSize.ordinal },
            enumA(R.string.rw_hora_tam, nomes(R.string.rw_s, R.string.rw_m, R.string.rw_l, R.string.rw_xl), R2.KEY_CLOCK_SIZE) { o().clockSize.ordinal },
            enumA(R.string.rw_hora_linha2, nomes(R.string.rw_nenhum, R.string.rw_data, R.string.rw_dia_semana, R.string.rw_data_e_dia), R2.KEY_CLOCK_LINE2) { o().clockLine2.ordinal },
            enumA(R.string.rw_letra_tam, nomes(R.string.rw_m, R.string.rw_l, R.string.rw_xl), R2.KEY_TEXT_SIZE) { o().textSize.ordinal },
            simA(R.string.rw_negrito, R2.KEY_BOLD) { o().bold },
            simA(R.string.rw_italico, R2.KEY_ITALIC) { o().italic },
        )
    }

    /**
     * Opções da barra única (StripHome; Paulo, 24/11/2026: "customizável pelo usuário, como no Projectivy"): descrição do
     * mundo e o jeito dos cards. Cada ◀ ▶ grava; a HOME atrás se refaz na volta (assinatura do layout).
     */
    private fun ajustesFaixa(): List<Ajuste> {
        val S = br.paulo.tvlauncher.home.StripHome
        fun o() = S.options(this)
        fun gira(key: String, atual: Int, total: Int, passo: Int) = S.next(this, key, atual, total, passo)
        val descs = listOf(R.string.st_desc_fixa, R.string.st_desc_viva)
        val formatos = br.paulo.tvlauncher.home.nomesDosFormatos(this)
        val tamanhos = listOf(R.string.st_pequeno, R.string.st_medio, R.string.st_grande)
        val espacos = listOf(R.string.st_justo, R.string.st_normal, R.string.st_largo)
        val cantos = listOf(R.string.st_retos, R.string.st_suaves, R.string.st_redondos)
        val simNao = listOf(R.string.st_sem, R.string.st_com)
        val proprios = listOf(R.string.st_proprios_branco, R.string.st_proprios_vidro, R.string.st_proprios_escuro)
        val personalidades = listOf(R.string.st_pers_cor, R.string.st_pers_brilho, R.string.cruz_azulejo)
        val animsMundo = listOf(R.string.st_anim_cena, R.string.st_anim_luz, R.string.st_anim_pulo)
        val B = br.paulo.tvlauncher.home.StripBackground
        val jeitos = listOf(R.string.st_telas_gaveta, R.string.st_telas_vidro, R.string.st_telas_barra, R.string.st_telas_painel, R.string.st_telas_guiada)
        val fundos = listOf(R.string.st_fundo_vale, R.string.st_fundo_serra, R.string.st_fundo_noite, R.string.st_fundo_tema)
        return listOf(
            Ajuste(R.string.st_fundo, { getString(fundos[B.choice(this).ordinal]) }) { p -> getString(fundos[B.next(this, p).ordinal]) },
            // Jeito das outras telas (Paulo, 24/11/2026: Gaveta padrão, Vidro e Barra como opção; ScreenStyle).
            Ajuste(R.string.st_telas, { getString(jeitos[br.paulo.tvlauncher.home.ScreenStyle.of(this).ordinal]) }) { p ->
                getString(jeitos[br.paulo.tvlauncher.home.ScreenStyle.next(this, p).ordinal]) },
            Ajuste(R.string.st_desc, { getString(descs[o().desc.ordinal]) }) { p -> getString(descs[gira(S.KEY_DESC, o().desc.ordinal, 2, p)]) },
            Ajuste(R.string.st_formato, { formatos[o().format.ordinal] }) { p -> formatos[gira(S.KEY_FORMAT, o().format.ordinal, formatos.size, p)] },
            Ajuste(R.string.st_tamanho, { getString(tamanhos[o().size.ordinal]) }) { p -> getString(tamanhos[gira(S.KEY_SIZE, o().size.ordinal, 3, p)]) },
            Ajuste(R.string.st_espaco, { getString(espacos[o().gap.ordinal]) }) { p -> getString(espacos[gira(S.KEY_GAP, o().gap.ordinal, 3, p)]) },
            Ajuste(R.string.st_cantos, { getString(cantos[o().corners.ordinal]) }) { p -> getString(cantos[gira(S.KEY_CORNERS, o().corners.ordinal, 3, p)]) },
            Ajuste(R.string.st_proprios, { getString(proprios[o().own.ordinal]) }) { p -> getString(proprios[gira(S.KEY_OWN, o().own.ordinal, 3, p)]) },
            Ajuste(R.string.st_personalidade, { getString(personalidades[o().mood.ordinal]) }) { p -> getString(personalidades[gira(S.KEY_MOOD, o().mood.ordinal, 3, p)]) },
            // Animação do ícone grande do mundo (Paulo, 25/11/2026: os 3 estilos como opção, Pequena cena de padrão).
            Ajuste(R.string.st_anim_mundo, { getString(animsMundo[o().worldAnim.ordinal]) }) { p -> getString(animsMundo[gira(S.KEY_WORLD_ANIM, o().worldAnim.ordinal, 3, p)]) },
            Ajuste(R.string.st_placa, { getString(simNao[if (o().glass) 1 else 0]) }) { p -> getString(simNao[gira(S.KEY_GLASS, if (o().glass) 1 else 0, 2, p)]) },
            Ajuste(R.string.st_nome, { getString(simNao[if (o().name) 1 else 0]) }) { p -> getString(simNao[gira(S.KEY_NAME, if (o().name) 1 else 0, 2, p)]) },
        )
    }

    private fun soIconeEmTodas() = br.paulo.tvlauncher.home.CruzLogica.Categoria.entries
        .all { br.paulo.tvlauncher.home.WorldOptions.of(this, it).iconOnly }
    private fun nomeDasCategorias() = getString(if (soIconeEmTodas()) R.string.wo_so_icone_curto else R.string.wo_com_nome_curto)

    /** Nome da câmera escolhida (o id do HA, sem o "camera."), ou "Nenhuma". */
    private val nomeCamera: String get() = BackgroundStyle.camera(this).removePrefix("camera.").ifEmpty { getString(R.string.bg_nenhuma) }

    /** Busca as câmeras no Home Assistant (fora da tela) e abre a lista; a escolha refaz a HOME atrás e este painel. */
    private fun escolherCamera() {
        Fundo.rodar("camera-fundo") {
            val cams = runCatching { br.paulo.tvlauncher.network.CasaHA.listar(applicationContext).filter { it.camera } }.getOrDefault(emptyList())
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val ids = listOf("") + cams.map { it.id }
                val nomes = listOf(getString(R.string.bg_nenhuma)) + cams.map { it.nome }
                Estilo.lista(this, getString(R.string.bg_camera), nomes, marcadas = listOf(ids.indexOf(BackgroundStyle.camera(this)).coerceAtLeast(0))) { i ->
                    BackgroundStyle.setCamera(this, ids[i])
                    recreate()
                }
            }
        }
    }

    private fun nomeTintura(k: BackgroundStyle.Tint) = getString(when (k) {
        BackgroundStyle.Tint.OFF -> R.string.cs_sem
        BackgroundStyle.Tint.SOFT -> R.string.cs_suave
        BackgroundStyle.Tint.STRONG -> R.string.cs_forte
        BackgroundStyle.Tint.SOLID -> R.string.bg_solida
    })
    private fun nomeCorFundo(k: BackgroundStyle.TintColor) = getString(when (k) {
        BackgroundStyle.TintColor.ACCENT -> R.string.bg_destaque
        BackgroundStyle.TintColor.NIGHT -> R.string.bg_noite
        BackgroundStyle.TintColor.FOREST -> R.string.bg_mata
        BackgroundStyle.TintColor.EMBER -> R.string.bg_brasa
        BackgroundStyle.TintColor.GRAPHITE -> R.string.bg_grafite
    })
    private fun nomeDesfoque(k: BackgroundStyle.Blur) = getString(when (k) {
        BackgroundStyle.Blur.OFF -> R.string.cs_sem
        BackgroundStyle.Blur.SOFT -> R.string.cs_suave
        BackgroundStyle.Blur.STRONG -> R.string.cs_forte
    })
    private fun nomeFoco(k: br.paulo.tvlauncher.settings.FocusStyle) = getString(when (k) {
        br.paulo.tvlauncher.settings.FocusStyle.LINE -> R.string.ft_foco_fio
        br.paulo.tvlauncher.settings.FocusStyle.VEIL -> R.string.ft_foco_veu
        br.paulo.tvlauncher.settings.FocusStyle.BOX -> R.string.ft_foco_quadro
        br.paulo.tvlauncher.settings.FocusStyle.RING -> R.string.ft_foco_anel
    })
    private fun nomeCartoes(k: br.paulo.tvlauncher.home.AppChannels.Mode) = getString(when (k) {
        br.paulo.tvlauncher.home.AppChannels.Mode.OFF -> R.string.cs_sem
        br.paulo.tvlauncher.home.AppChannels.Mode.COLUMN -> R.string.ac_coluna
        br.paulo.tvlauncher.home.AppChannels.Mode.SHOWCASE -> R.string.ac_vitrine
    })

    private fun nomeLugarMundo(k: br.paulo.tvlauncher.home.WorldLabel.Place) = getString(when (k) {
        br.paulo.tvlauncher.home.WorldLabel.Place.COLUMN -> R.string.ac_coluna
        br.paulo.tvlauncher.home.WorldLabel.Place.PILL -> R.string.wl_pilula
    })

    private fun nomeFormaTodos(k: br.paulo.tvlauncher.home.AllAppsLayout.Mode) = getString(when (k) {
        br.paulo.tvlauncher.home.AllAppsLayout.Mode.SHELVES -> R.string.aa_layout_shelves
        br.paulo.tvlauncher.home.AllAppsLayout.Mode.FOLDERS -> R.string.aa_layout_folders
        br.paulo.tvlauncher.home.AllAppsLayout.Mode.LIBRARY -> R.string.aa_layout_library
    })

    private fun nomeTransicao(k: BackgroundStyle.Fade) = getString(when (k) {
        BackgroundStyle.Fade.CUT -> R.string.bg_corte
        BackgroundStyle.Fade.SOFT -> R.string.cs_suave
        BackgroundStyle.Fade.SLOW -> R.string.bg_lenta
    })

    /** Fonte: a próxima da lista; se precisar baixar, mostra "Baixando…" e só troca quando o arquivo chegou. */
    private fun trocarFonte(passo: Int): String {
        val fontes = FontChoice.FONTS
        val i = Math.floorMod(fontes.indexOfFirst { it.id == FontChoice.currentId(this) } + passo, fontes.size)
        FontChoice.choose(this, fontes[i].id) { ok ->
            if (isDestroyed) return@choose
            if (ok) recreate() else AvisoTv.rapido(this, getString(R.string.ft_titulo), getString(R.string.fc_falhou), 4)
        }
        return if (FontChoice.ready(this, fontes[i])) fontes[i].name else getString(R.string.fc_baixando)
    }

    private fun nomeFormato(f: ClockStyle.Format) = getString(when (f) {
        ClockStyle.Format.AUTO -> R.string.ck_auto
        ClockStyle.Format.H24 -> R.string.ck_24
        ClockStyle.Format.H12 -> R.string.ck_12
        ClockStyle.Format.H12_AMPM -> R.string.ck_12ampm
        ClockStyle.Format.SECONDS -> R.string.ck_seg
    })
    private fun nomeCorHora(k: ClockStyle.Color) = getString(when (k) {
        ClockStyle.Color.DEFAULT -> R.string.ck_padrao
        ClockStyle.Color.ORANGE -> R.string.ck_laranja
        ClockStyle.Color.CREAM -> R.string.ck_creme
        ClockStyle.Color.AMBER -> R.string.ck_ambar
        ClockStyle.Color.TEAL -> R.string.ck_verde
    })

    override fun onDestroy() {
        if (!isChangingConfigurations) aberto = false
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { fechar(); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun fechar() {
        if (fechando) return
        fechando = true
        painel.animate().translationX(painel.width.toFloat()).alpha(0f).setDuration(FECHAR_MS).setInterpolator(curva)
            .withEndAction { finish(); overridePendingTransition(0, 0) }.start()
    }

    companion object {
        /** O painel está na tela: a HOME atrás mostra o ponto e o brilho do escolhido mesmo sem o foco da janela. */
        @Volatile var aberto = false
        const val LARGURA = 400       // dp: a cruz (coluna em 225 dp) fica toda à vista à esquerda
        const val ABRIR_MS = 400L     // transitions.dev: panel open = --duration-slow
        const val FECHAR_MS = 350L    // panel close = --duration-medium

        /** Abre a HOME e o painel por cima dela (a prévia é da tela inicial, não da tela de onde veio). */
        fun abrir(a: Activity) {
            val home = android.content.Intent(a, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            a.startActivities(arrayOf(home, android.content.Intent(a, FineTuneActivity::class.java)))
        }

        fun Activity.nomeDestaque(k: CardStyle.Accent) = getString(when (k) {
            CardStyle.Accent.ORANGE -> R.string.ck_laranja
            CardStyle.Accent.AMBER -> R.string.ck_ambar
            CardStyle.Accent.TEAL -> R.string.ck_verde
            CardStyle.Accent.SKY -> R.string.cs_ceu
            CardStyle.Accent.LILAC -> R.string.cs_lilas
        })
        fun Activity.nomeCantos(k: CardStyle.Corners) = getString(when (k) {
            CardStyle.Corners.SQUARE -> R.string.cs_retos
            CardStyle.Corners.DEFAULT -> R.string.ck_padrao
            CardStyle.Corners.ROUND -> R.string.cs_redondos
        })
        fun Activity.nomeBrilho(k: CardStyle.Glow) = getString(when (k) {
            CardStyle.Glow.OFF -> R.string.cs_sem
            CardStyle.Glow.SOFT -> R.string.cs_suave
            CardStyle.Glow.STRONG -> R.string.cs_forte
        })
        fun Activity.nomeTamanho(k: CardStyle.Size) = getString(when (k) {
            CardStyle.Size.SMALL -> R.string.cs_menor
            CardStyle.Size.DEFAULT -> R.string.ck_padrao
            CardStyle.Size.LARGE -> R.string.cs_maior
        })
    }

    private fun nomeRitmo(r: br.paulo.tvlauncher.home.CrossRhythm) = getString(when (r) {
        br.paulo.tvlauncher.home.CrossRhythm.BEFORE -> R.string.rt_antes
        br.paulo.tvlauncher.home.CrossRhythm.TIGHT -> R.string.rt_a
        br.paulo.tvlauncher.home.CrossRhythm.FRAMED -> R.string.rt_b
        br.paulo.tvlauncher.home.CrossRhythm.AIRY -> R.string.rt_c
    })
}
