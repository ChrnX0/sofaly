package br.paulo.tvlauncher.screensaver

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import br.paulo.tvlauncher.DesignTokens
import br.paulo.tvlauncher.DicasClima
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.Prefs
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.alarm.Agenda
import br.paulo.tvlauncher.alarm.Despertador
import br.paulo.tvlauncher.home.MovimentoAdaptativo
import br.paulo.tvlauncher.home.FundoMeu
import br.paulo.tvlauncher.home.criarFundo
import br.paulo.tvlauncher.observer.SofyDrawable
import br.paulo.tvlauncher.observer.SofyPolicy
import br.paulo.tvlauncher.settings.Tema
import br.paulo.tvlauncher.weather.ArcoSol
import br.paulo.tvlauncher.weather.Astronomia
import br.paulo.tvlauncher.weather.Forecast
import br.paulo.tvlauncher.weather.ForecastData
import br.paulo.tvlauncher.weather.ForecastText
import br.paulo.tvlauncher.weather.IconesClima
import br.paulo.tvlauncher.weather.Lua
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * A home que adormece (21/11/2026, Paulo: "a tela de descanso nada mais é do que a tela de clima, mas mais simplificada").
 *
 * Porquê: o descanso antigo era outro mundo (pintura, fotos da NASA, 14 liga/desliga). O Paulo quer uma coisa só: a
 * mesma tela inicial, com o MESMO fundo, que só troca a cruz pela hora e pelo tempo quando ninguém mexe.
 * Para quê: quem passa pela sala lê de longe a hora, a temperatura e a frase do dia; quem quer mais aperta ↑/↓ (ou
 * espera) e a faixa de baixo passa por horas, dias, dicas, medidas, ar, sol, lua e agenda, uma linha por vez.
 * Fino e calmo (Paulo: "nada gritante ou bruto"): pesos leves, sem maiúsculas, laranja só no fio que conta o tempo.
 * Como: um FrameLayout com o fundo do tema ([criarFundo]), um véu leve para o texto ler em qualquer fundo, o topo
 * (o widget de hora e tempo, no formato do relógio clássico do Android) e a faixa, que começa pela frase do dia. A faixa roda sozinha a cada [rotateSeconds] (0 = só o controle). Contra marcar
 * a tela: depois de [ESCURECER_MS] sem tecla tudo escurece devagar até ficar pálido, e a informação anda uns dp a cada
 * 2 min. Protótipo aprovado: https://claude.ai/artifact/59oNVmEGdFx7FMevpSoW8F (versão 3).
 * Referências: modo ambiente do Google TV/Apple TV, resumo em uma frase do Google Weather, dicas do AccuWeather; tempos
 * do transitions.dev (troca da faixa 450 ms, curva suave 0.22,1,0.36,1; escurecer em 6 s).
 */
class SleepingHome(private val ctx: Context) {

    /** O que uma tecla faz no descanso (fora do companion para ser SleepingHome.Acao). */
    enum class Acao { ANTERIOR, PROXIMA, PASSAR, FECHAR }

    companion object {
        /** Prefs "launcher" (entram no backup). */
        const val KEY_ROTATE = "sleeping_rotate_s"
        const val KEY_GLASS = "sleeping_glass"
        val ROTATE_OPTIONS = listOf(15, 30, 60, 0)
        const val ESCURECER_MS = 10 * 60_000L
        const val ESCURECER_ANIM_MS = 6_000L
        const val ESCURO_ALFA = 0.55f
        const val DESLOCAR_MS = 2 * 60_000L
        const val ATUALIZAR_MS = 15 * 60_000L
        const val TROCA_MS = 450L
        /** Marca a janela da faixa (o teste de telas acha as linhas por ela). */
        const val TAG_JANELA = "sleeping-strip"

        fun rotateSeconds(ctx: Context): Int = prefs(ctx).getInt(KEY_ROTATE, 15).let { if (it in ROTATE_OPTIONS) it else 15 }
        fun nextRotate(atual: Int): Int = ROTATE_OPTIONS[(ROTATE_OPTIONS.indexOf(atual).coerceAtLeast(0) + 1) % ROTATE_OPTIONS.size]
        fun setRotate(ctx: Context, s: Int) = prefs(ctx).edit().putInt(KEY_ROTATE, s).apply()
        /**
         * O fundo do descanso (Paulo, 22/11/2026: "a priori é o mesmo da home, mas nas configs o user pode escolher se passa
         * várias imagens/vídeos ou se quer uma imagem na home e outra no descanso... deixar o user fazer o que quiser"):
         *  - [BG_HOME] (padrão): o tema da home;
         *  - o nome de um [Tema]: outro tema só no descanso;
         *  - [BG_FILE]: uma foto, GIF ou vídeo só do descanso ([KEY_FILE]);
         *  - [BG_PHOTOS]: fotos e vídeos passando (painel, pasta da rede e as pastas da TV), por cima do tema da home.
         * Um valor que não vale mais (tema sem arquivo, arquivo apagado) cai na home.
         */
        const val KEY_BACKGROUND = "sleeping_background"
        const val KEY_FILE = "sleeping_file"
        const val BG_HOME = "home"
        const val BG_FILE = "file"
        const val BG_PHOTOS = "photos"
        fun file(ctx: Context): java.io.File? = prefs(ctx).getString(KEY_FILE, null)?.let { java.io.File(it) }?.takeIf { it.isFile && it.canRead() }
        fun setFile(ctx: Context, f: java.io.File?) = prefs(ctx).edit().putString(KEY_FILE, f?.path).apply()
        fun backgroundOptions(ctx: Context): List<String> =
            listOf(BG_HOME) + Tema.disponiveis(ctx).map { it.name } + listOf(BG_FILE, BG_PHOTOS)
        fun background(ctx: Context): String {
            val v = prefs(ctx).getString(KEY_BACKGROUND, BG_HOME) ?: BG_HOME
            return when {
                v == BG_FILE -> if (file(ctx) != null) v else BG_HOME
                v in backgroundOptions(ctx) -> v
                else -> BG_HOME
            }
        }
        fun setBackground(ctx: Context, v: String) = prefs(ctx).edit().putString(KEY_BACKGROUND, v).apply()
        /** O tema que o descanso desenha embaixo de tudo (no "arquivo só do descanso", ele fica no lugar). */
        fun backgroundTheme(ctx: Context): Tema = background(ctx).let { v -> Tema.disponiveis(ctx).firstOrNull { it.name == v } } ?: Tema.atual(ctx)
        /** Próxima opção da lista (dá a volta). Pura sobre a lista. */
        fun nextOf(opcoes: List<String>, atual: String): String = opcoes[(opcoes.indexOf(atual).coerceAtLeast(-1) + 1) % opcoes.size]

        fun glass(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_GLASS, false)
        fun setGlass(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_GLASS, v).apply()
        private fun prefs(ctx: Context) = ctx.getSharedPreferences(Prefs.LAUNCHER, Context.MODE_PRIVATE)

        /** O que uma tecla faz no descanso. Pura (SleepingHomeTest). */
        fun acao(keyCode: Int): Acao = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> Acao.ANTERIOR
            KeyEvent.KEYCODE_DPAD_DOWN -> Acao.PROXIMA
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> Acao.PASSAR   // volume não acorda
            else -> Acao.FECHAR   // HOME, VOLTAR e as outras acordam a TV e devolvem a cruz
        }

        /** Próxima linha da faixa, em carrossel (da última volta à primeira). Pura. */
        fun girar(atual: Int, passo: Int, total: Int): Int = if (total <= 0) 0 else ((atual + passo) % total + total) % total
    }

    private val ui = Handler(Looper.getMainLooper())
    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d + 0.5f).toInt()
    private val suave = Estilo.TEXTO_2
    private val azulChuva = 0xD98EC5FF.toInt()
    private val curva = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    val raiz = FrameLayout(ctx).apply { setBackgroundColor(DesignTokens.NIGHT_DEEP) }
    /** O que anda contra marcar a tela (o fundo fica parado). */
    val camadaInfo = FrameLayout(ctx)
    private val escuro = View(ctx).apply { setBackgroundColor(Color.BLACK); alpha = 0f }
    private val topoDireita = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.START }
    /** Roboto Thin/Light do sistema: o traço fino do widget de referência. Onde não houver, o Android cai na padrão. */
    private val fina: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    private val leve: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val lugar = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f); setTextColor(0xD98EC5FF.toInt()); typeface = leve; visibility = View.GONE; maxLines = 1
        setShadowLayer(10 * ctx.resources.displayMetrics.density, 0f, ctx.resources.displayMetrics.density, 0x59000000)
    }
    private val faixa = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val fio = View(ctx).apply { setBackgroundColor(DesignTokens.ORANGE); alpha = 0.7f; pivotX = 0f; scaleX = 0f }
    private val janela = FrameLayout(ctx).apply { clipChildren = true; tag = TAG_JANELA }
    private var sofy: SofyDrawable? = null

    private class Linha(val nome: String, val view: View)
    private var linhas: List<Linha> = emptyList()
    private var atual = 0
    private var progresso: ObjectAnimator? = null
    private var passo = 0
    private var escurecido = false
    private var solto = false

    // ------------------------------------------------------------------ montar

    fun montar(): View {
        val noFundo: (() -> Unit) -> Unit = { bloco -> Fundo.rodar("descanso-fundo") { bloco() } }
        val arquivoProprio = if (background(ctx) == BG_FILE) file(ctx) else null
        raiz.addView(if (arquivoProprio != null) FundoMeu(ctx, noFundo, arquivoProprio).apply { setBackgroundColor(Color.BLACK) }
            else criarFundo(ctx, backgroundTheme(ctx), decoracao = false, noFundo), FrameLayout.LayoutParams(-1, -1))
        // Véu: só luz, não caixa. Escurece um pouco em cima e embaixo, onde mora o texto, para ler em qualquer fundo.
        raiz.addView(View(ctx).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x73000000, 0x1A000000, 0x1A000000, 0x8C000000.toInt()))
        }, FrameLayout.LayoutParams(-1, -1))

        val coluna = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPaddingRelative(dp(48), dp(30), dp(48), dp(22)) }
        // Topo: o widget clássico de hora e tempo do Android (referência que o Paulo mandou, 21/11/2026): hora bem fina,
        // data e a dica do guarda-chuva alinhadas à direita, um fio vertical, e o tempo ao lado (ícone, temperatura, máxima/mínima).
        val topo = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topo.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.END
            addView(TextClock(ctx).apply {
                format12Hour = "h:mm"; format24Hour = "HH:mm"
                tamanho(this, 76f); setTextColor(Estilo.TEXTO); typeface = fina; includeFontPadding = false; sombra(this)
            })
            addView(TextClock(ctx).apply {
                format12Hour = ctx.getString(R.string.fmt_data_longa); format24Hour = ctx.getString(R.string.fmt_data_longa)
                tamanho(this, 20f); setTextColor(Estilo.TEXTO); typeface = leve; sombra(this)
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
            addView(lugar, LinearLayout.LayoutParams(-2, -2))
        }, LinearLayout.LayoutParams(-2, -2))
        topo.addView(View(ctx).apply { setBackgroundColor(0x8CFFFFFF.toInt()) },
            LinearLayout.LayoutParams(dp(1), -1).apply { marginStart = dp(22); marginEnd = dp(22); topMargin = dp(6); bottomMargin = dp(6) })
        topo.addView(topoDireita, LinearLayout.LayoutParams(-2, -2))
        coluna.addView(topo, LinearLayout.LayoutParams(-2, -2))
        coluna.addView(View(ctx), LinearLayout.LayoutParams(-1, 0, 1f))   // o meio é do fundo

        // Sem fileira de nomes (Paulo, 21/11: "informação demais"): só um fio fino, que se enche de laranja até a
        // próxima troca. Com o vidro ligado, o vidro faz a separação e o fio some.
        if (glass(ctx)) {
            faixa.background = Estilo.vidro(ctx); faixa.setPaddingRelative(dp(20), dp(10), dp(20), dp(6))
        } else {
            faixa.addView(FrameLayout(ctx).apply {
                setBackgroundColor(0x1AFFFFFF)
                addView(fio, FrameLayout.LayoutParams(-1, -1))
            }, LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(12) })
        }
        faixa.addView(janela, LinearLayout.LayoutParams(-1, dp(92)))   // 3 linhas (nome, valor, detalhe) com folga para a sombra
        coluna.addView(faixa, LinearLayout.LayoutParams(-1, -2))
        camadaInfo.addView(coluna, FrameLayout.LayoutParams(-1, -1))
        raiz.addView(camadaInfo, FrameLayout.LayoutParams(-1, -1))
        raiz.addView(escuro, FrameLayout.LayoutParams(-1, -1))

        // Antes dos dados chegarem: o que a TV sabe sem rede (o topo guardado pela HOME, sol, lua e agenda).
        preencher(Forecast.recente())
        Fundo.rodar("descanso-previsao") {
            val f = Forecast.recenteOuBusca(ctx)
            val agenda = lerAgenda()
            ui.post { if (!solto) { this.agenda = agenda; preencher(f) } }
        }
        ui.postDelayed(escurecer, ESCURECER_MS)
        ui.postDelayed(deslocar, DESLOCAR_MS)
        ui.postDelayed(atualizar, ATUALIZAR_MS)
        return raiz
    }

    // ------------------------------------------------------------------ dados

    private var agenda: List<Triple<String, String, String>> = emptyList()   // ícone, título, quando

    private fun lerAgenda(): List<Triple<String, String, String>> {
        val agora = System.currentTimeMillis()
        val saida = mutableListOf<Triple<String, String, String>>()
        try {
            Agenda.eventosDaTv(ctx, agora, agora + 2 * 86_400_000L).filter { it.fim > agora }.take(3).forEach { e ->
                saida += Triple("calendar", e.titulo, quando(e.inicio, e.diaInteiro))
            }
        } catch (_: Exception) {}
        try {
            Despertador.proximo(ctx)?.let { (a, ms) ->
                if (ms - agora < 2 * 86_400_000L) saida += Triple("alarm", a.nome.ifBlank { ctx.getString(R.string.lbl_despertador) }, quando(ms, false))
            }
        } catch (_: Exception) {}
        return saida.take(3)
    }

    /** "hoje, 21:00" / "amanhã, 9:00" / "segunda, 21:00". */
    private fun quando(ms: Long, diaInteiro: Boolean): String {
        val hoje = Calendar.getInstance(); val c = Calendar.getInstance().apply { timeInMillis = ms }
        val dias = ((c.apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis - hoje.apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis) / 86_400_000L).toInt()
        val dia = when (dias) {
            0 -> ctx.getString(R.string.pv_hoje)
            1 -> ctx.getString(R.string.pr_amanha)
            else -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ms))
        }
        return if (diaInteiro) dia else "$dia, ${android.text.format.DateFormat.getTimeFormat(ctx).format(Date(ms))}"
    }

    private fun preencher(f: ForecastData?) {
        val topo = Forecast.topo(ctx)
        f?.let { x -> topo.codigo?.let { x.codigo = it }; topo.umidade?.let { x.umidade = it } }
        val clima = ctx.getSharedPreferences(Prefs.CLIMA, Context.MODE_PRIVATE)
        val lat = f?.ponto?.lat ?: clima.getFloat("lat", br.paulo.tvlauncher.Config.LATITUDE.toFloat()).toDouble()
        val lon = f?.ponto?.lon ?: clima.getFloat("lon", br.paulo.tvlauncher.Config.LONGITUDE.toFloat()).toDouble()
        val chovendo = topo.chovendo
        val tempAgora = topo.temp ?: f?.let { Math.round(it.temp).toInt() }
        val codigo = f?.codigo ?: topo.codigo
        val agoraMs = System.currentTimeMillis()
        val eDia = Astronomia.eDia(agoraMs, lat, lon, f?.eDia ?: true)

        // ---- topo direito: ícone do céu, temperatura, máxima/mínima; e a dica do guarda-chuva embaixo da data
        topoDireita.removeAllViews(); sofy?.stop(); sofy = null
        val entrada = if (f != null && tempAgora != null) f.entrada(tempAgora, chovendo) else null
        val fr = entrada?.let { DicasClima.frase(it) }
        // Embaixo da data, a dica que mais pesa no dia (Paulo: "tipo se vai chover?"): a do guarda-chuva, em azul de chuva.
        val dicaTopo = entrada?.let { "${ctx.getString(R.string.cl_d_guarda)}: ${ForecastText.dica(ctx, DicasClima.guardaChuva(it)).replaceFirstChar { c -> c.lowercase(ctx.resources.configuration.locales[0]) }}" }
        lugar.text = dicaTopo ?: ""; lugar.visibility = if (dicaTopo == null) View.GONE else View.VISIBLE
        if (tempAgora != null) {
            if (codigo != null) topoDireita.addView(IconesClima.imagem(ctx, IconesClima.chave(codigo, eDia, chovendo)),
                LinearLayout.LayoutParams(dp(44), dp(44)))
            topoDireita.addView(texto(44f, Estilo.TEXTO, leve).apply { text = "$tempAgora°"; includeFontPadding = false },
                LinearLayout.LayoutParams(-2, -2))   // sem isto a coluna vertical dá MATCH_PARENT e o texto fica da largura do ícone
            if (f != null) {
                val maxHoje = max(Math.round(f.dMax[0]).toInt(), tempAgora); val minHoje = min(Math.round(f.dMin[0]).toInt(), tempAgora)
                topoDireita.addView(texto(20f, Estilo.TEXTO, leve).apply { text = "$maxHoje°/$minHoje°" }, LinearLayout.LayoutParams(-2, -2))
            }
        }

        // ---- as linhas da faixa (só as que têm dado)
        val l = mutableListOf<Linha>()
        if (fr != null) l += Linha("resumo", linhaResumo(ForecastText.frase(ctx, fr), when {
            chovendo || fr.tipo == DicasClima.TipoFrase.CHUVA_AS -> SofyPolicy.Expression.WORRIED
            fr.tipo == DicasClima.TipoFrase.CALOR || fr.tipo == DicasClima.TipoFrase.FRIO -> SofyPolicy.Expression.SURPRISED
            !eDia -> SofyPolicy.Expression.SLEEPING
            else -> SofyPolicy.Expression.HAPPY
        }))
        if (f != null && tempAgora != null && entrada != null) {
            l += Linha(ctx.getString(R.string.sh_horas), linhaHoras(f, tempAgora))
            l += Linha(ctx.getString(R.string.sh_dias), linhaDias(f))
            l += Linha(ctx.getString(R.string.sh_dicas), linhaDicas(entrada))
            l += Linha(ctx.getString(R.string.pv_agora), linhaAgora(f, tempAgora))
            l += Linha(ctx.getString(R.string.sh_ar), linhaAr(f))
        }
        linhaSol(agoraMs, lat, lon)?.let { l += Linha(ctx.getString(R.string.pt_sol), it) }
        l += Linha(ctx.getString(R.string.pt_lua), linhaLua(agoraMs, lat))
        if (agenda.isNotEmpty()) l += Linha(ctx.getString(R.string.lbl_agenda), linhaItens(agenda))
        val nomeAntes = linhas.getOrNull(atual)?.nome
        linhas = l
        atual = l.indexOfFirst { it.nome == nomeAntes }.takeIf { it >= 0 } ?: 0
        janela.removeAllViews()
        l.forEachIndexed { i, x -> janela.addView(x.view, FrameLayout.LayoutParams(-1, -1)); x.view.visibility = if (i == atual) View.VISIBLE else View.INVISIBLE }
        reiniciarRoda()
    }

    // ------------------------------------------------------------------ linhas

    private fun linhaBase() = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    /** Uma célula de 3 andares: rótulo pequeno, valor grande, nota pequena (a nota pode ter cor de alerta/chuva). */
    private fun celula(rot: String, valor: String, nota: String?, corNota: Int = suave, centro: Boolean = false) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = if (centro) Gravity.CENTER_HORIZONTAL else Gravity.START
        addView(texto(18f, suave, Estilo.fonte(400)).apply { text = rot; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        addView(texto(24f, Estilo.TEXTO, Estilo.fonte(400)).apply { text = valor; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        if (nota != null) addView(texto(18f, corNota, Estilo.fonte(400)).apply { text = nota; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
    }

    /** O resumo do dia: a Sofy pequena, reagindo ao tempo, e a frase (a mesma da 1ª tela do Clima). */
    private fun linhaResumo(fraseDia: String, cara: SofyPolicy.Expression): View = linhaBase().apply {
        val s = SofyDrawable(ctx, cara)
        addView(ImageView(ctx).apply { setImageDrawable(s); alpha = 0.85f; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            LinearLayout.LayoutParams(dp(40), dp(37)).apply { marginEnd = dp(16) })
        addView(texto(23f, Estilo.TEXTO, Estilo.fonte(500)).apply { text = fraseDia; maxLines = 2; ellipsize = TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(0, -2, 1f))
        if (!semMovimento()) s.start()
        sofy = s
    }

    private fun linhaHoras(f: ForecastData, tempAgora: Int): View = linhaBase().apply {
        val i0 = f.i0
        for (k in 0 until 8) {
            val i = i0 + k; if (i >= f.hTempo.size) break
            val rot = if (k == 0) ctx.getString(R.string.pv_agora) else ForecastText.hora(ctx, f.hTempo[i].substring(11, 13).toInt())
            val t = if (k == 0) tempAgora else Math.round(f.hTemp[i]).toInt()
            addView(celula(rot, "$t°", "${f.hChuva[i]}%", azulChuva, centro = true), LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun linhaDias(f: ForecastData): View = linhaBase().apply {
        val fmt = SimpleDateFormat("EEE", Locale.getDefault()); val ler = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        for (i in 0 until min(7, f.dData.size)) {
            val nome = if (i == 0) ctx.getString(R.string.pv_hoje)
                else fmt.format(ler.parse(f.dData[i]) ?: Date()).trimEnd('.').replaceFirstChar { it.titlecase(Locale.getDefault()) }
            addView(celula(nome, "${Math.round(f.dMax[i]).toInt()}° ${Math.round(f.dMin[i]).toInt()}°", "${f.dChuva[i]}%", azulChuva, centro = true),
                LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun item(icone: String?, titulo: String, nota: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        // O alpha é do ícone e só existe com ícone: a linha da agenda (linhaItens) passa null, e o getChildAt(0) fora do
        // if dava NPE que derrubava o launcher inteiro ao abrir o descanso (6 vezes na Sala entre 22 e 23/11/2026).
        if (icone != null) addView(IconesClima.imagem(ctx, IconesClima.PREFIXO + "3d/" + icone).apply { alpha = 0.9f },
            LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(12) })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(texto(21f, Estilo.TEXTO, Estilo.fonte(500)).apply { text = titulo; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            addView(texto(18f, suave).apply { text = nota; maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun linhaDicas(e: DicasClima.Entrada): View = linhaBase().apply {
        listOf(
            Triple("cloud_with_rain", R.string.cl_d_guarda, DicasClima.guardaChuva(e)),
            Triple("wind_face", R.string.cl_d_varal, DicasClima.varal(e)),
            Triple("sun", R.string.cl_d_sair, DicasClima.sair(e)),
        ).forEach { (ic, rot, dica) -> addView(item(ic, ctx.getString(rot), ForecastText.dica(ctx, dica)), LinearLayout.LayoutParams(0, -2, 1f)) }
    }

    @androidx.annotation.VisibleForTesting
    internal fun linhaItens(itens: List<Triple<String, String, String>>): View = linhaBase().apply {
        // Sem ícone: os ícones 3D são do tempo (não há calendário nem relógio); o título e o "quando" bastam.
        itens.forEach { (_, t, q) -> addView(item(null, t, q), LinearLayout.LayoutParams(0, -2, 1f)) }
        repeat(3 - itens.size) { addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f)) }
    }

    private fun linhaAgora(f: ForecastData, tempAgora: Int): View = linhaBase().apply {
        val i0 = f.i0
        val sens = Math.round(f.sensacao + (tempAgora - f.temp)).toInt()
        val sensTexto = ctx.getString(when { sens > tempAgora + 1 -> R.string.pv_mais_quente; sens < tempAgora - 1 -> R.string.pv_mais_frio; else -> R.string.pv_igual })
        val uv = f.hUv.getOrElse(i0) { 0.0 }
        val lp = { LinearLayout.LayoutParams(0, -2, 1f) }
        addView(celula(ctx.getString(R.string.pv_chuva_hoje), "${f.dChuva[0]}%", "${"%.1f".format(Locale.getDefault(), f.dChuvaMm)} mm", azulChuva), lp())
        addView(celula(ctx.getString(R.string.pv_sensacao), "$sens°", sensTexto), lp())
        addView(celula(ctx.getString(R.string.pt_umidade), "${f.umidade}%",
            ctx.getString(R.string.sh_orvalho, Math.round(f.hOrvalho.getOrElse(i0) { f.temp }).toInt())), lp())
        addView(celula(ctx.getString(R.string.pv_vento), "${Math.round(f.vento).toInt()} km/h", ForecastText.rosa(ctx, f.ventoDir)), lp())
        addView(celula(ctx.getString(R.string.pv_uv), "${Math.round(uv).toInt()}", ForecastText.uv(ctx, uv),
            if (ForecastText.uvAlto(uv)) DesignTokens.ORANGE else suave), lp())
    }

    private fun linhaAr(f: ForecastData): View = linhaBase().apply {
        val lp = { LinearLayout.LayoutParams(0, -2, 1f) }
        f.aqi?.let { a -> val (rot, cor) = ForecastText.aqi(ctx, a); addView(celula(ctx.getString(R.string.pv_ar), "$a", rot, cor), lp()) }
        addView(celula(ctx.getString(R.string.pv_pressao), "${Math.round(f.pressao).toInt()} hPa", null), lp())
        val vis = f.hVisib.getOrElse(f.i0) { 10000.0 } / 1000
        addView(celula(ctx.getString(R.string.pv_visib), if (vis >= 10) "10+ km" else "${"%.1f".format(Locale.getDefault(), vis)} km", null), lp())
        if (f.nuvens >= 0) addView(celula(ctx.getString(R.string.sh_nuvens), "${f.nuvens}%", null), lp())
    }

    private fun linhaSol(agoraMs: Long, lat: Double, lon: Double): View? {
        val (nasce, poe) = Astronomia.nascerEPor(agoraMs, lat, lon) ?: return null
        val hf = android.text.format.DateFormat.getTimeFormat(ctx)
        val durMin = ((poe - nasce) / 60_000L).toInt()
        val fracao = ((agoraMs - nasce).toFloat() / (poe - nasce)).coerceIn(-0.05f, 1.05f)
        return linhaBase().apply {
            addView(celula(ctx.getString(R.string.sh_nascer), hf.format(Date(nasce)), null), LinearLayout.LayoutParams(0, -2, 1f))
            addView(ArcoSol(ctx, fracao), LinearLayout.LayoutParams(dp(150), dp(56)).apply { marginEnd = dp(24) })
            addView(celula(ctx.getString(R.string.sh_por), hf.format(Date(poe)), null), LinearLayout.LayoutParams(0, -2, 1f))
            addView(celula(ctx.getString(R.string.sh_duracao), ctx.getString(R.string.sh_horas_min, durMin / 60, durMin % 60), null), LinearLayout.LayoutParams(0, -2, 1f))
            // Hora dourada: a última hora antes do pôr do sol (luz baixa e quente; regra usual de fotografia).
            addView(celula(ctx.getString(R.string.sh_dourada), hf.format(Date(poe - 60 * 60_000L)), null), LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun linhaLua(agoraMs: Long, lat: Double): View = linhaBase().apply {
        // resumo = "🌔  Crescente gibosa  ·  63%  ·  cheia em 5 d"
        val p = Lua.resumo(agoraMs, lat).split("  ·  ")
        addView(IconesClima.imagem(ctx, Lua.chaveIcone(agoraMs, lat)), LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(16) })
        addView(celula(ctx.getString(R.string.sh_fase), p[0].substringAfter("  ").trim(), ctx.getString(R.string.sh_iluminada, p.getOrElse(1) { "" })),
            LinearLayout.LayoutParams(0, -2, 1.4f))
        val idade = Lua.idade(agoraMs); val ciclo = 29.530588853; val metade = ciclo / 2
        val paraCheia = if (idade < metade) metade - idade else ciclo - idade + metade
        val paraNova = ciclo - idade
        fun evento(rot: Int, dias: Double) = celula(ctx.getString(rot), diaMes(agoraMs + (dias * 86_400_000).toLong()),
            ctx.getString(R.string.sh_em_dias, Lua.diasNoCalendario(agoraMs, dias)))
        addView(evento(R.string.sh_cheia, paraCheia), LinearLayout.LayoutParams(0, -2, 1f))
        addView(evento(R.string.sh_nova, paraNova), LinearLayout.LayoutParams(0, -2, 1f))
    }

    /** "26/11" no formato da língua (dia e mês, sem o ano). */
    private fun diaMes(ms: Long): String =
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "dM"), Locale.getDefault()).format(Date(ms))

    // ------------------------------------------------------------------ nomes, troca e roda

    fun mover(passoFaixa: Int) {
        if (linhas.size < 2) return
        val antes = linhas[atual].view
        atual = girar(atual, passoFaixa, linhas.size)
        val depois = linhas[atual].view
        val h = janela.height.toFloat().coerceAtLeast(dp(92).toFloat())
        if (semMovimento()) {
            antes.visibility = View.INVISIBLE; depois.visibility = View.VISIBLE; depois.translationY = 0f; depois.alpha = 1f
        } else {
            val dir = if (passoFaixa > 0) 1 else -1
            antes.animate().cancel(); depois.animate().cancel()
            antes.animate().translationY(-dir * h * 0.6f).alpha(0f).setDuration(TROCA_MS).setInterpolator(curva)
                .withEndAction { antes.visibility = View.INVISIBLE; antes.translationY = 0f; antes.alpha = 1f }.start()
            depois.translationY = dir * h * 0.6f; depois.alpha = 0f; depois.visibility = View.VISIBLE
            depois.animate().translationY(0f).alpha(1f).setDuration(TROCA_MS).setInterpolator(curva).start()
        }
        reiniciarRoda()
    }

    private val rodar = Runnable { mover(1) }

    private fun reiniciarRoda() {
        ui.removeCallbacks(rodar); progresso?.cancel(); progresso = null; fio.scaleX = 0f
        val s = rotateSeconds(ctx)
        if (s <= 0 || linhas.size < 2) return
        ui.postDelayed(rodar, s * 1000L)
        if (!semMovimento()) progresso = ObjectAnimator.ofFloat(fio, View.SCALE_X, 0f, 1f).apply { duration = s * 1000L; interpolator = LinearInterpolator(); start() }
    }

    // ------------------------------------------------------------------ teclas, escurecer, deslocar

    /** Qualquer tecla (que não fecha) clareia de novo e reinicia a conta do escurecer. */
    fun tecla() {
        ui.removeCallbacks(escurecer); ui.postDelayed(escurecer, ESCURECER_MS)
        if (escurecido) { escurecido = false; escuro.animate().cancel(); escuro.animate().alpha(0f).setDuration(400).start() }
    }

    private val escurecer = Runnable {
        escurecido = true
        // Devagar (6 s) para ninguém perceber o salto: a tela vai ficando pálida.
        escuro.animate().alpha(ESCURO_ALFA).setDuration(if (semMovimento()) 0 else ESCURECER_ANIM_MS).start()
    }

    private val deslocar = object : Runnable {
        override fun run() {
            // Caminho em "oito": 8 posições ao longo de 16 min, até ±10 dp. withLayer: a camada anda como textura
            // (sem isso a GPU guardava cada quadro da sombra da hora; medido na Sala em 12/11/2026).
            passo = (passo + 1) % 8
            val dx = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)[passo] * dp(10)
            val dy = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)[passo] * dp(6)
            camadaInfo.animate().translationX(dx.toFloat()).translationY(dy.toFloat()).setDuration(2000).withLayer().start()
            ui.postDelayed(this, DESLOCAR_MS)
        }
    }

    private val atualizar = object : Runnable {
        override fun run() {
            Fundo.rodar("descanso-previsao") {
                val f = try { Forecast.load(ctx) } catch (_: Exception) { null } ?: Forecast.recente()
                val ag = lerAgenda()
                ui.post { if (!solto) { agenda = ag; preencher(f) } }
            }
            ui.postDelayed(this, ATUALIZAR_MS)
        }
    }

    fun soltar() {
        solto = true
        ui.removeCallbacksAndMessages(null)
        progresso?.cancel(); sofy?.stop()
    }

    // ------------------------------------------------------------------ utilidades

    private fun semMovimento(): Boolean {
        val sistema = runCatching {
            android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
        if (sistema || Estilo.altoContraste) return true
        return runCatching { MovimentoAdaptativo.atual(ctx, sistema) == MovimentoAdaptativo.Nivel.PARADO }.getOrDefault(false)
    }

    private fun tamanho(t: TextView, sp: Float) = t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    private fun sombra(t: TextView) = t.setShadowLayer(10 * d, 0f, 1 * d, 0x59000000)

    private fun texto(sp: Float, cor: Int, fonte: Typeface? = null) = TextView(ctx).apply {
        tamanho(this, sp); setTextColor(cor); typeface = fonte ?: Estilo.fonte(500); sombra(this)
    }
}
