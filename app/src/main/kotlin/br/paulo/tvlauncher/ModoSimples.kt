package br.paulo.tvlauncher

import android.app.Activity
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.paulo.tvlauncher.family.PinTv
import br.paulo.tvlauncher.home.Organizacao
import br.paulo.tvlauncher.home.abrir
import br.paulo.tvlauncher.panel.PainelArmazem
import br.paulo.tvlauncher.player.PlayerArmazem
import br.paulo.tvlauncher.settings.Extras
import java.util.Locale

/**
 * Modo simples (12/11/2026, ideia 10), para quem tem dificuldade com o controle: no lugar da HOME, até [MAX] cards
 * grandes (a TV ao vivo e os apps escolhidos), o nome do card em foco é falado, e um card "Pedir ajuda" manda um aviso
 * para o celular da família (Avisos, tipo AJUDA).
 *
 * Feito para o Quarto, mas DESLIGADO (chave [Extras.Chave.MODO_SIMPLES]) até o dono avisar. Ligar é livre; desligar
 * pede o PIN (na TV e no painel), senão quem está usando sai sem querer.
 * Os apps vêm da lista escolhida no painel ([CHAVE_APPS]); sem lista, os mais usados nos últimos 14 dias.
 */
object ModoSimples {
    const val CHAVE_APPS = "simples_apps"
    const val MAX = 6

    /**
     * Regra pura (ModoSimplesTest): os escolhidos que estão instalados, na ordem, completados com os mais usados, sem
     * repetir, até [max]. [vagas] = quantos cards sobram para apps (a TV ao vivo ocupa um quando há conta).
     */
    fun escolher(escolhidos: List<String>, instalados: Set<String>, maisUsados: List<String>, vagas: Int = MAX,
                 ocultos: Set<String> = emptySet()): List<String> =
        // Os mais usados não trazem app oculto (Sala, 18/11/2026: "Teste do Shizuku", oculto na HOME, virava card
        // grande no modo simples). O que a pessoa escolheu à mão entra mesmo oculto: foi escolha dela.
        (escolhidos + maisUsados.filter { it !in ocultos }).filter { it in instalados }.distinct().take(vagas.coerceAtLeast(0))

    fun lerLista(texto: String): List<String> = texto.split(',', '\n').map { it.trim() }
        .filter { Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$").matches(it) }.distinct().take(MAX)

    fun escolhidos(ctx: Context): List<String> = lerLista(Extras.texto(ctx, CHAVE_APPS))
    fun definirEscolhidos(ctx: Context, pacotes: List<String>) = Extras.definirTexto(ctx, CHAVE_APPS, lerLista(pacotes.joinToString(",")).joinToString(","))

    fun ligado(ctx: Context) = Extras.ligado(ctx, Extras.Chave.MODO_SIMPLES)

    /** Pacotes com tela de abrir pela HOME (TV ou celular), sem o próprio launcher. */
    fun instalados(ctx: Context): Map<String, String> = try {
        val pm = ctx.packageManager
        listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).flatMap { cat ->
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
        }.filter { it.activityInfo.packageName != ctx.packageName }
            .associate { it.activityInfo.packageName to it.loadLabel(pm).toString() }
    } catch (_: Exception) { emptyMap() }

    /** Mais usados (tempo na frente) nos últimos 14 dias. */
    fun maisUsados(ctx: Context): List<String> = try {
        val agora = System.currentTimeMillis()
        ctx.getSystemService(UsageStatsManager::class.java).queryAndAggregateUsageStats(agora - 14 * 86_400_000L, agora)
            .filterValues { it.totalTimeInForeground > 60_000L }.entries.sortedByDescending { it.value.totalTimeInForeground }.map { it.key }
    } catch (_: Exception) { emptyList() }
}

/** A HOME do modo simples. A [MainActivity] manda para cá enquanto a chave estiver ligada. */
class ModoSimplesActivity : Activity() {
    private var voz: TextToSpeech? = null
    private var vozPronta = false
    private lateinit var grade: LinearLayout
    /** Brainstorm item 19 (26/11/2026): faixa de cima com o tempo, o próximo despertador e o recado da família. */
    private lateinit var faixa: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (VigiaAtualizacao.fechada(this)) return
        val (raiz, coluna) = Estilo.telaLista(this, getString(R.string.ms_ola), getString(R.string.ms_ola_det), dicas = false)
        faixa = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE }
        coluna.addView(faixa, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })
        grade = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        coluna.addView(grade, LinearLayout.LayoutParams(-1, -2))
        setContentView(raiz)
        voz = TextToSpeech(this) { s ->
            if (s != TextToSpeech.SUCCESS) return@TextToSpeech
            val r = voz?.setLanguage(Locale.getDefault()) ?: TextToSpeech.LANG_NOT_SUPPORTED
            vozPronta = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onResume() {
        super.onResume()
        // Desligaram o modo simples (painel, celular, Configurações): a tela some e diz por quê.
        if (!ModoSimples.ligado(this)) {
            AvisoTv.rapido(this, getString(R.string.aviso_simples_titulo), getString(R.string.aviso_simples_texto)); finish(); return
        }
        Fundo.rodar("simples") {
            val instalados = ModoSimples.instalados(this)
            val temTv = PlayerArmazem.fontes(this).isNotEmpty()
            val pacotes = ModoSimples.escolher(ModoSimples.escolhidos(this), instalados.keys, ModoSimples.maisUsados(this),
                ModoSimples.MAX - if (temTv) 1 else 0, Organizacao.ocultos(this))
            // Cada card na cor do mundo dele (Assistir, Ouvir, Jogar, Apps), como na home; antes era tudo azul.
            val escolhasCat = br.paulo.tvlauncher.home.CategoriaApps.escolhas(this)
            val cores = pacotes.associateWith { p ->
                val cat = runCatching {
                    br.paulo.tvlauncher.home.CategoriaApps.destino(p, packageManager.getApplicationInfo(p, 0).category,
                        br.paulo.tvlauncher.games.JogosTv.ehJogo(this, p), escolhasCat[p])
                }.getOrDefault(br.paulo.tvlauncher.home.CruzLogica.Categoria.APLICATIVOS)
                br.paulo.tvlauncher.home.BottomBar.cor(cat)
            }
            val itensFaixa = lerFaixa()
            runOnUiThread { if (!isDestroyed) { montarFaixa(itensFaixa); montar(temTv, pacotes.map { Triple(it, instalados[it] ?: it, cores[it] ?: DesignTokens.WORLD_APPS) }) } }
        }
    }

    // Desligado de fora (receita desfeita, painel, Claude) com a tela aberta: sai na hora. Antes ficava até o próximo HOME.
    private val ouvinteChave = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == Extras.Chave.MODO_SIMPLES.name && !ModoSimples.ligado(this)) runOnUiThread { if (!isFinishing) finish() }
    }
    override fun onStart() { super.onStart(); Extras.ouvir(this, ouvinteChave) }
    override fun onStop() { super.onStop(); Extras.parar(this, ouvinteChave) }

    override fun onDestroy() { super.onDestroy(); voz?.shutdown(); voz = null }

    /** VOLTAR não sai daqui: esta é a HOME. */
    @Deprecated("Activity") override fun onBackPressed() {}

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** O que a faixa de cima diz (fora da thread da tela): tempo agora, despertador das próximas 24 h, recado não lido. */
    private fun lerFaixa(): List<String> {
        val l = ArrayList<String>(3)
        runCatching { br.paulo.tvlauncher.weather.Forecast.topo(this).temp }.getOrNull()?.let { l += "${getString(R.string.pv_agora)}  $it°" }
        runCatching { br.paulo.tvlauncher.alarm.Despertador.proximo(this) }.getOrNull()
            ?.takeIf { it.second - System.currentTimeMillis() in 0..86_400_000L }
            ?.let { l += "${getString(R.string.lbl_despertador)} · ${android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(it.second))}" }
        runCatching {
            br.paulo.tvlauncher.family.Mural.linhaIlha(br.paulo.tvlauncher.family.Mural.visiveis(this),
                { getString(R.string.mur_ilha_um, it) }, { n, u -> getString(R.string.mur_ilha_varios, n, u) })
        }.getOrNull()?.let { l += it }
        return l
    }

    private fun montarFaixa(itens: List<String>) {
        faixa.removeAllViews()
        itens.forEachIndexed { i, t ->
            if (i > 0) faixa.addView(View(this).apply { setBackgroundColor(Estilo.TEXTO_3) }, LinearLayout.LayoutParams(dp(2), dp(28)).apply { marginStart = dp(20); marginEnd = dp(20) })
            faixa.addView(Estilo.texto(this, Estilo.Papel.CORPO, t).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-2, -2).apply { weight = if (i == itens.lastIndex) 1f else 0f })
        }
        faixa.visibility = if (itens.isEmpty()) View.GONE else View.VISIBLE
        // Quem lê com dificuldade ouve a faixa ao abrir (o nome do card em foco vem logo depois).
        faixa.contentDescription = itens.joinToString(". ")
    }

    private fun montar(temTv: Boolean, apps: List<Triple<String, String, Int>>) {
        grade.removeAllViews()
        val cards = ArrayList<View>()
        if (temTv) cards += card("TV", null, DesignTokens.WORLD_WATCH) {
            try { startActivity(Intent(this, PlayerActivity::class.java)) } catch (_: Exception) {}
        }
        for ((pacote, nome, cor) in apps) cards += card(nome, pacote, cor) {
            val i = packageManager.getLeanbackLaunchIntentForPackage(pacote) ?: packageManager.getLaunchIntentForPackage(pacote)
            if (i != null) try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
        // Só aparece quando há como avisar (Brainstorm 26/11, item 1): sem celular inscrito o card prometia ajuda que não
        // vinha. E só diz "avisei" depois que pelo menos um celular aceitou o aviso; senão manda ligar para alguém.
        if (Avisos.podeAvisar(this, Avisos.Tipo.AJUDA)) cards += card(getString(R.string.ms_pedir_ajuda), null, Estilo.ATENCAO) {
            val app = applicationContext
            val tv = android.provider.Settings.Global.getString(contentResolver, "device_name") ?: "TV"
            val titulo = getString(R.string.ms_pedido_ajuda)
            val texto = getString(R.string.ms_alguem_pediu, tv)
            Fundo.rodar("pedir-ajuda") {
                val (aceitos, _) = Avisos.enviarAgora(app, Avisos.Tipo.AJUDA, titulo, texto)
                runOnUiThread {
                    if (aceitos > 0) {
                        falar(getString(R.string.ms_avisei_falado))
                        AvisoTv.rapido(app, titulo, getString(R.string.ms_avisei))
                    } else {
                        falar(getString(R.string.ms_ninguem_recebeu))
                        AvisoTv.rapido(app, titulo, getString(R.string.ms_ninguem_recebeu))
                    }
                }
            }
        }
        // 3 por linha, cards grandes (o dobro dos da HOME).
        cards.chunked(3).forEach { linha ->
            grade.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false
                linha.forEach { addView(it, LinearLayout.LayoutParams(dp(250), dp(150)).apply { marginEnd = dp(24); bottomMargin = dp(24) }) }
            })
        }
        // Sair: linha discreta no fim, com o PIN do painel.
        grade.addView(Estilo.linhaTexto(this, getString(R.string.ms_voltar)).apply {
            textSize = Estilo.Papel.CORPO.sp   // Modo simples é para quem lê com dificuldade: nada menor que o corpo
            setOnClickListener {
                val sair = { Extras.definir(this@ModoSimplesActivity, Extras.Chave.MODO_SIMPLES, false); finish() }
                if (PainelArmazem.temPin(this@ModoSimplesActivity)) PinTv.pedir(this@ModoSimplesActivity, getString(R.string.player_pin_titulo), familia = true) { certo -> if (certo) sair() }
                else sair()
            }
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        cards.firstOrNull()?.requestFocus()
    }

    private fun card(nome: String, pacote: String?, cor: Int, abrir: () -> Unit): View = FrameLayout(this).apply {
        isFocusable = true; isClickable = true
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Estilo.matiz(cor, 0.6f), Estilo.matiz(cor, 0.35f)))
            .apply { cornerRadius = dp(Estilo.RAIO).toFloat() }
        if (pacote != null) {
            val icone = try { packageManager.getApplicationBanner(pacote) ?: packageManager.getApplicationIcon(pacote) } catch (_: Exception) { null }
            if (icone != null) addView(ImageView(context).apply { setImageDrawable(icone); scaleType = ImageView.ScaleType.FIT_CENTER },
                FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(24), dp(16), dp(24), dp(48)) })
        }
        addView(TextView(context).apply {
            text = nome; setTextColor(Estilo.TEXTO); setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Estilo.fonte(700); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END   // "Amazon Prime Vid…" em vez de letra cortada ao meio
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(18), 0, dp(12), dp(12)) })
        setOnClickListener { abrir() }
        setOnFocusChangeListener { v, foco ->
            v.foreground = if (foco) GradientDrawable().apply { cornerRadius = dp(Estilo.RAIO).toFloat(); setStroke(dp(5), Estilo.FOCO) } else null
            v.animate().scaleX(if (foco) 1.06f else 1f).scaleY(if (foco) 1.06f else 1f).setDuration(Estilo.FOCO_MS).start()
            if (foco) falar(nome)
        }
    }

    private fun falar(t: String) {
        if (vozPronta) voz?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "simples")
    }
}
