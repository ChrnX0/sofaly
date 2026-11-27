package br.paulo.tvlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.paulo.tvlauncher.family.PinTv
import br.paulo.tvlauncher.home.SofalySymbol
import br.paulo.tvlauncher.observer.SofyCommunityTv
import br.paulo.tvlauncher.observer.SofyFleetTv
import br.paulo.tvlauncher.panel.PainelArmazem

/**
 * "Ajudar outras casas" (rede de Sofys, passo 3; Paulo 27/11/2026: opt-in, nunca ligado de fábrica, com o sofáTV).
 *
 * Por quê: a TV só manda o pacote do dia ([SofyFleetTv]) com o consentimento de quem mora aqui, e consentimento só vale
 * se a pessoa entendeu o que vai e o que não vai. Para quê: uma pergunta curta em letra grande ("Vamos ajudar outras
 * casas?") e, para quem quer saber, "Como funciona?" com as duas listas: o que vai e o que nunca sai. Proposta C do
 * estudo (artefato "Ajudar outras casas"), referências do Mobbin: Alan (pergunta curta), Outlook (lista do que não vai),
 * Skype e Duolingo ABC (o mascote falando).
 *
 * Regras de consentimento: "Sim" e "Agora não" do mesmo tamanho, o foco começa em "Agora não" (ninguém aceita sem
 * querer) e o mesmo lugar desliga ([MODE_SETTINGS], em "O que a Sofy sabe"). O sofáTV dá um pulinho só ao abrir e fica
 * parado; com as animações desligadas no Android ele nem pula (regra do mascote: nada em laço, reduzir = parado).
 */
class HelpOtherHomesActivity : Activity() {
    companion object {
        /** Vindo das Configurações: mostra o estado (ligado/desligado) e as ações em vez da pergunta. */
        const val EXTRA_MODE = "mode"
        const val MODE_SETTINGS = "settings"
        private const val KEY_ASKED = "help_homes_asked"

        /** Na configuração inicial: pergunta uma vez só (quem já respondeu ou já ligou não vê de novo). */
        fun askOnce(ctx: Context) {
            val p = ctx.getSharedPreferences(Prefs.LAUNCHER, Context.MODE_PRIVATE)
            if (p.getBoolean(KEY_ASKED, false) || SofyCommunityTv.sharing(ctx)) return
            p.edit().putBoolean(KEY_ASKED, true).apply()
            ctx.startActivity(Intent(ctx, HelpOtherHomesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private enum class View0 { ASK, HOW, SETTINGS, TODAY }
    private var tela = View0.ASK

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (VigiaAtualizacao.fechada(this)) return
        tela = if (intent.getStringExtra(EXTRA_MODE) == MODE_SETTINGS) View0.SETTINGS else View0.ASK
        montar(pular = true)
    }

    @Deprecated("Activity simples, sem AndroidX")
    override fun onBackPressed() {
        // Voltar no detalhe volta para a pergunta; na pergunta, BACK = "Agora não" (ninguém fica preso aqui).
        when (tela) {
            View0.HOW -> { tela = View0.ASK; montar() }
            View0.TODAY -> { tela = View0.SETTINGS; montar() }
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    private fun montar(pular: Boolean = false) = when (tela) {
        View0.ASK -> ask(pular)
        View0.HOW -> how()
        View0.SETTINGS -> settings()
        View0.TODAY -> today()
    }

    private fun raiz(central: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Estilo.fundoTela(context)
        gravity = if (central) Gravity.CENTER else Gravity.TOP
        clipChildren = false; clipToPadding = false
        setPaddingRelative(dp(Estilo.MARGEM_LATERAL), dp(Estilo.MARGEM_VERTICAL + 9), dp(Estilo.MARGEM_LATERAL), dp(Estilo.MARGEM_VERTICAL))
    }

    /** O sofáTV (o mesmo desenho do logo). [pular]: um pulinho de 0,6 s ao abrir, só se o Android anima. */
    private fun mascote(larguraDp: Int, pular: Boolean) = ImageView(this).apply {
        setImageDrawable(SofalySymbol())
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(larguraDp), dp(larguraDp * 90 / 96)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        val anima = android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        if (pular && anima) post {
            pivotY = height.toFloat()
            animate().translationY(-height * 0.08f).scaleY(1.02f).setStartDelay(400).setDuration(180).withEndAction {
                animate().translationY(0f).scaleY(0.98f).setStartDelay(0).setDuration(160).withEndAction {
                    animate().scaleY(1f).setDuration(120).start()
                }.start()
            }.start()
        }
    }

    private fun titulo(t: String) = Estilo.texto(this, Estilo.Papel.TITULO, t).apply { gravity = Gravity.CENTER }

    private fun frase(t: CharSequence) = Estilo.texto(this, Estilo.Papel.LEGENDA, t).apply {
        textSize = Estilo.Papel.CORPO.sp; gravity = Gravity.CENTER; maxWidth = dp(620); setLineSpacing(0f, 1.25f)
    }

    private fun botoes(vararg bs: TextView) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; clipChildren = false; clipToPadding = false
        bs.forEachIndexed { i, b -> addView(b, LinearLayout.LayoutParams(dp(220), -2).apply { if (i > 0) marginStart = dp(Estilo.E2) }) }
    }

    private fun botao(rotulo: Int, aoClicar: () -> Unit) = Estilo.botao(this, getString(rotulo), primario = false).apply { setOnClickListener { aoClicar() } }

    private fun sim() = botao(R.string.hoh_yes) { escolher(true) }
    private fun agoraNao() = botao(R.string.hoh_not_now) { escolher(false) }

    private fun escolher(on: Boolean) {
        SofyCommunityTv.setSharing(this, on, fleet = on)   // #541: só esta tela, com a lista do que vai, dá o consentimento da frota
        PainelArmazem.registrar(this, "tv", getString(R.string.hoh_title_short), if (on) "ligado" else "desligado")
        if (on) AvisoTv.rapido(applicationContext, getString(R.string.hoh_title_short), getString(R.string.hoh_thanks))
        finish()
    }

    // ------------------------------------------------------------------ tela 1: a pergunta

    private fun ask(pular: Boolean) {
        val r = raiz(central = true)
        r.addView(mascote(150, pular))
        r.addView(titulo(getString(R.string.hoh_question)), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Estilo.E3) })
        r.addView(frase(getString(R.string.hoh_line)), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(Estilo.E1); gravity = Gravity.CENTER_HORIZONTAL })
        val nao = agoraNao()
        r.addView(botoes(sim(), nao, botao(R.string.hoh_how) { tela = View0.HOW; montar() }),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Estilo.E3 + 8) })
        setContentView(r)
        nao.requestFocus()
    }

    // ------------------------------------------------------------------ tela 2: como funciona

    private fun coluna(cab: Int, corCab: Int, itens: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Estilo.fundo(context, Estilo.SUPERFICIE, Estilo.RAIO, Estilo.BORDA)
        setPaddingRelative(dp(Estilo.E3), dp(Estilo.E2), dp(Estilo.E3), dp(Estilo.E2))
        addView(Estilo.texto(context, Estilo.Papel.CORPO, getString(cab)).apply { setTextColor(corCab); typeface = Typeface.DEFAULT_BOLD })
        resources.getStringArray(itens).forEach { item ->
            addView(Estilo.texto(context, Estilo.Papel.LEGENDA, "•  $item"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }

    private fun how() {
        val r = raiz(central = false)
        r.addView(Estilo.tituloTela(this, getString(R.string.hoh_how_title)))
        r.addView(Estilo.texto(this, Estilo.Papel.LEGENDA, getString(R.string.hoh_how_line)), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        r.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(coluna(R.string.hoh_goes, DesignTokens.OK, R.array.hoh_goes_list), LinearLayout.LayoutParams(0, -2, 1f))
            addView(coluna(R.string.hoh_never, Estilo.ERRO, R.array.hoh_never_list), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(Estilo.E2) })
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Estilo.E3) })
        r.addView(Estilo.texto(this, Estilo.Papel.LEGENDA, getString(R.string.hoh_back_here)), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(Estilo.E2) })
        val nao = agoraNao()
        r.addView(botoes(sim(), nao).apply { gravity = Gravity.START }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Estilo.E3) })
        setContentView(r)
        nao.requestFocus()
    }

    // ------------------------------------------------------------------ nas Configurações: estado e ações

    private fun settings() {
        val on = SofyCommunityTv.sharing(this)
        val (raiz, lista) = Estilo.telaLista(this, getString(R.string.hoh_title_short), getString(if (on) R.string.hoh_state_on else R.string.hoh_state_off))
        fun linha(t: Int, d: Int, aoClicar: () -> Unit) = lista.addView(Estilo.linhaOpcao(this, getString(t), getString(d)).apply {
            setOnClickListener { aoClicar() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        })
        if (on) linha(R.string.hoh_stop, R.string.hoh_stop_det) { comPin { SofyCommunityTv.setSharing(this, false); montar() } }
        else linha(R.string.hoh_start, R.string.hoh_start_det) { tela = View0.HOW; montar() }
        linha(R.string.hoh_today, R.string.hoh_today_det) { tela = View0.TODAY; montar() }
        linha(R.string.hoh_forget, R.string.hoh_forget_det) {
            comPin {
                SofyFleetTv.forget(this)
                AvisoTv.rapido(applicationContext, getString(R.string.hoh_title_short), getString(R.string.hoh_forgot))
            }
        }
        setContentView(raiz)
        lista.getChildAt(0)?.requestFocus()
    }

    /** Desligar e esquecer pedem o PIN quando a casa tem PIN (como "O que a Sofy sabe"). Ligar não: é só um pedido. */
    private fun comPin(feito: () -> Unit) {
        if (PainelArmazem.temPin(this)) PinTv.pedir(this, getString(R.string.hoh_title_short)) { certo -> if (certo) feito() } else feito()
    }

    // ------------------------------------------------------------------ o que vai hoje, campo por campo

    private fun today() {
        val (raiz, lista) = Estilo.telaLista(this, getString(R.string.hoh_today), getString(R.string.hoh_today_sub))
        val texto = runCatching { SofyFleetTv.today(this).toString(2) }.getOrDefault("{}")
        val t = Estilo.texto(this, Estilo.Papel.LEGENDA, texto).apply {
            typeface = Typeface.MONOSPACE; isFocusable = true; setTextIsSelectable(false)
            background = Estilo.fundo(context, Estilo.SUPERFICIE, Estilo.RAIO, Estilo.BORDA)
            setPaddingRelative(dp(Estilo.E3), dp(Estilo.E2), dp(Estilo.E3), dp(Estilo.E2))
        }
        // O pacote pode ser maior que a tela: a coluna já rola; o texto ganha foco para o controle descer com ele.
        lista.addView(t, LinearLayout.LayoutParams(-1, -2))
        setContentView(raiz)
        t.requestFocus()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
