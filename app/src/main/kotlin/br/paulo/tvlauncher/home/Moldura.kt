package br.paulo.tvlauncher.home

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.screensaver.RelogioTela

/**
 * Moldura da HOME nas telas de lista (coerência visual, parte 1, 12/11/2026; proposta B aprovada pelo dono): a hora e
 * a data no MESMO lugar e no mesmo desenho da barra da HOME (alto à direita, hora de 22 sp e a data em duas linhas ao
 * lado). Quem sai da HOME para uma lista vê a hora parada no mesmo ponto e sabe que continua no mesmo sistema.
 *
 * Como: um único gancho no ciclo de vida das telas (registrado no LauncherApp) põe o relógio por cima do conteúdo das
 * telas da lista [TELAS]. Nenhuma tela precisa mudar. O relógio não pega foco (a navegação não muda) e fica fora das
 * telas que já têm hora própria ou que usam o alto à direita (Calendário, Previsão, catálogos, player, diálogos).
 */
object Moldura {
    /** As telas de lista (o "miolo lista" da proposta B). Nome curto da classe. */
    private val TELAS = setOf(
        "AlarmesActivity", "BackupNoturnoActivity", "BoasVindasActivity", "BuscaActivity", "CasaActivity", "CenasActivity",
        "ConectarClaudeActivity", "ConfigLauncherActivity", "DiagnosticoActivity", "DnsActivity", "EntrarPeloQrActivity",
        "ExtrasActivity", "FotoPerfilActivity", "LicencasActivity", "AjudaActivity", "ModoSimplesActivity", "RotinasActivity", "AutomacoesActivity", "TeclasActivity", "TesteTeclasActivity",
        "MidiaRedeActivity",   // parte 2: Na rede já usa a margem e o título do sistema
    )
    private const val MARCA = "moldura-relogio"

    // A mesma régua da barra da HOME (MainActivity.TOPO_PALCO e ALTURA_TOPO): a hora não pula na troca de tela.
    private const val TOPO = 28
    private const val ALTURA = 48

    fun instalar(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            // Started e Resumed: a tela já fez o setContentView; e quem refaz a tela inteira (setContentView de novo,
            // ex.: Boas-vindas depois de uma permissão) apaga o relógio, que volta na próxima retomada.
            override fun onActivityStarted(a: Activity) = colocar(a)
            override fun onActivityResumed(a: Activity) = colocar(a)
            override fun onActivityCreated(a: Activity, salvo: Bundle?) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, s: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    private fun colocar(a: Activity) {
        if (a.javaClass.simpleName !in TELAS || a.isFinishing) return
        val conteudo = a.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return
        if (conteudo.childCount == 0 || conteudo.findViewWithTag<android.view.View>(MARCA) != null) return
        if (BottomBar.strip(a) && ScreenStyle.of(a) == ScreenStyle.DRAWER) gaveta(a, conteudo)
        if (BottomBar.strip(a)) cabecalho(a, conteudo)
        if (BottomBar.strip(a)) {
            // Barra única: a pílula da hora no alto, no centro, como na barra (coerência das telas, 24/11/2026).
            conteudo.addView(pilula(a), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Estilo.dp(a, PILULA_ALTURA),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = Estilo.dp(a, TOPO) })
            return
        }
        conteudo.addView(relogio(a), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Estilo.dp(a, ALTURA),
            Gravity.TOP or Gravity.END).apply {
            topMargin = Estilo.dp(a, TOPO)
            marginEnd = Estilo.dp(a, Estilo.MARGEM_LATERAL)
        })
    }

    /**
     * Jeito Gaveta (proposta B, padrão): a tela de lista desce para a parte de baixo e a foto da barra fica nítida em
     * cima. Como: a foto vai para o fundo do "content" da janela, a raiz da tela perde o vidro e ganha margem de cima
     * de [GlassBackground.GAVETA_TOPO] da altura. Só age quando a raiz usa o fundo do sistema (Estilo.fundoTela): uma
     * tela com fundo próprio fica como está. Chamado de novo na retomada: não faz duas vezes.
     */
    private fun gaveta(a: Activity, conteudo: FrameLayout) {
        val raiz = conteudo.getChildAt(0) ?: return
        if (raiz.background !is GlassDrawable) return
        val lp = raiz.layoutParams as? FrameLayout.LayoutParams ?: return
        val foto = GlassBackground.drawer(a) ?: return
        conteudo.background = foto
        raiz.background = null
        lp.topMargin = topoDaGaveta(a, raiz)
        raiz.layoutParams = lp
    }

    /**
     * Quanto a gaveta desce: o ideal é [GlassBackground.GAVETA_TOPO] da altura, mas a tela precisa caber inteira (na Sala
     * a fileira de baixo das Configurações saía cortada, 24/11/2026). Mede a altura que a tela pede e sobe a gaveta o
     * quanto faltar, até [GAVETA_MINIMO]; listas longas rolam dentro dela.
     */
    private fun topoDaGaveta(a: Activity, raiz: android.view.View): Int {
        val alto = a.resources.displayMetrics.heightPixels
        val largo = a.resources.displayMetrics.widthPixels
        val ideal = (alto * GlassBackground.GAVETA_TOPO).toInt() - Estilo.dp(a, Estilo.MARGEM_VERTICAL)
        val minimo = (alto * GAVETA_MINIMO).toInt() - Estilo.dp(a, Estilo.MARGEM_VERTICAL)
        raiz.measure(android.view.View.MeasureSpec.makeMeasureSpec(largo, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
        return (alto - raiz.measuredHeight).coerceIn(minimo.coerceAtLeast(0), ideal.coerceAtLeast(0))
    }

    /**
     * Cabeçalho da barra única (coerência das telas, parte 2, 24/11/2026; proposta A): o título da tela ganha, ao lado,
     * a mesma linha do mundo da HOME, "Título | Item em foco  descrição". Porquê: na HOME a linha de cima diz onde o
     * foco está; nas listas o olho tinha de achar a linha acesa. Como: um ouvinte global de foco lê os dois primeiros
     * textos do item focado (nome e explicação). Nenhuma tela muda; tela sem [Estilo.tituloTela] fica como está.
     */
    private fun cabecalho(a: Activity, conteudo: FrameLayout) {
        val titulo = conteudo.findViewWithTag<android.widget.TextView>(Estilo.TAG_TITULO) ?: return
        val pai = titulo.parent as? LinearLayout ?: return
        if (pai.getTag(R.id.moldura_cabecalho) != null) return
        val nome = Estilo.texto(a, Estilo.Papel.CORPO).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; typeface = Estilo.fonte(700) }
        val desc = Estilo.texto(a, Estilo.Papel.LEGENDA).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val traco = android.view.View(a).apply { setBackgroundColor(Estilo.TEXTO_3) }
        val linha = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; visibility = android.view.View.INVISIBLE
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(traco, LinearLayout.LayoutParams(Estilo.dp(a, 2), Estilo.dp(a, 28)).apply { marginEnd = Estilo.dp(a, Estilo.E2) })
            addView(nome, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(desc, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Estilo.dp(a, 10) })
        }
        val i = pai.indexOfChild(titulo)
        val lp = titulo.layoutParams
        pai.removeViewAt(i)
        val faixa = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(titulo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            // A linha fica à esquerda da pílula da hora (que está no centro do alto): não passa da metade da tela.
            addView(linha, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Estilo.dp(a, Estilo.E2) })
        }
        // Na Gaveta o título fica abaixo da pílula da hora e a linha usa a largura toda; nos outros jeitos ele fica na
        // altura da pílula (centro do alto), então a faixa para antes dela.
        val largura = if (ScreenStyle.of(a) == ScreenStyle.DRAWER) ViewGroup.LayoutParams.MATCH_PARENT
            else (a.resources.displayMetrics.widthPixels / 2 - Estilo.dp(a, PILULA_FOLGA) - pai.paddingStart).coerceAtLeast(titulo.measuredWidth)
        pai.addView(faixa, i, LinearLayout.LayoutParams(lp).apply { width = largura })
        pai.setTag(R.id.moldura_cabecalho, true)
        conteudo.viewTreeObserver.addOnGlobalFocusChangeListener { _, novo ->
            val (n, d) = textosDoFoco(novo) ?: (null to null)
            if (n.isNullOrBlank() || n == titulo.text.toString()) { linha.visibility = android.view.View.INVISIBLE; return@addOnGlobalFocusChangeListener }
            nome.text = n; desc.text = d.orEmpty()
            linha.visibility = android.view.View.VISIBLE
        }
    }

    /** Os dois primeiros textos visíveis do item focado (nome e explicação), ou a descrição dele. */
    internal fun textosDoFoco(v: android.view.View?): Pair<String, String?>? {
        if (v == null) return null
        val achados = ArrayList<String>(2)
        fun andar(x: android.view.View) {
            if (achados.size >= 2 || x.visibility != android.view.View.VISIBLE) return
            if (x is android.widget.TextView && x !is android.widget.EditText) {
                x.text?.toString()?.trim()?.replace('\n', ' ')?.takeIf { it.isNotEmpty() }?.let { achados += it }
            } else if (x is ViewGroup) for (k in 0 until x.childCount) andar(x.getChildAt(k))
        }
        andar(v)
        if (achados.isEmpty()) return v.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { it to null }
        return achados[0] to achados.getOrNull(1)
    }

    /** O mais alto que a gaveta sobe (fração da altura): acima disso a foto quase some e a Gaveta vira o Vidro. */
    private const val GAVETA_MINIMO = 0.2f

    private const val PILULA_ALTURA = 40
    /** Meia pílula da hora (a mais larga, "00:00" em negrito) + o vão até ela, em dp: onde o cabeçalho tem de parar. */
    private const val PILULA_FOLGA = 80
    private const val PILULA_HORA_SP = 20f

    /**
     * A pílula da hora da barra única (HomeIsland.montarIlha): ponto calmo + hora em negrito sobre a placa escura. Sem data:
     * na barra também não tem. Só desenho: sem foco, sem clique.
     */
    fun pilula(a: Activity) = LinearLayout(a).apply {
        tag = MARCA
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = false
        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = Estilo.dp(a, PILULA_ALTURA / 2).toFloat(); setColor(0xBF0A0C14.toInt())
        }
        setPaddingRelative(Estilo.dp(a, 16), 0, Estilo.dp(a, 18), 0)
        addView(android.view.View(a).apply {
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(br.paulo.tvlauncher.MainActivity.ILHA_PONTO_CALMO) }
        }, LinearLayout.LayoutParams(Estilo.dp(a, 6), Estilo.dp(a, 6)).apply { marginEnd = Estilo.dp(a, 12) })
        addView(RelogioTela(a, data = false).apply {
            format12Hour = "HH:mm"; format24Hour = "HH:mm"
            textSize = PILULA_HORA_SP
            setTextColor(android.graphics.Color.WHITE)
            typeface = Estilo.fonte(700)
            includeFontPadding = false
        })
    }

    /** Hora (22 sp) e a data em duas linhas (12 sp), como na HOME. Só desenho: sem foco, sem clique. */
    fun relogio(a: Activity) = LinearLayout(a).apply {
        tag = MARCA
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = false
        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO   // o MCP e o leitor de tela pulam
        addView(RelogioTela(a, data = false).apply {
            format12Hour = "HH:mm"; format24Hour = "HH:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Estilo.TEXTO)
            typeface = Estilo.fonte(500)
            letterSpacing = -0.01f
            includeFontPadding = false
        })
        // 26 dp entre a hora e a data: o mesmo vão da HOME (14 do fim da pílula da hora + 12 do começo da da data).
        // Mesma data que a HOME: em escrita alta (japonês, árabe, híndi…) vira uma linha só (DataDuasLinhas, 24/11/2026).
        addView(DataDuasLinhas(a, android.text.format.DateFormat.getBestDateTimePattern(a.resources.configuration.locales[0], "EEEMMMd")).apply {
            orientation = LinearLayout.VERTICAL
            for (fmt in listOf(R.string.fmt_data_dia, R.string.fmt_data_mes)) addView(RelogioTela(a, data = true).apply {
                format12Hour = a.getString(fmt); format24Hour = a.getString(fmt)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Estilo.Papel.LEGENDA.sp)   // era 12 sp
                maxLines = 1
                setTextColor(Estilo.TEXTO_2)
                typeface = Estilo.fonte(500)
                includeFontPadding = false
                setLineSpacing(0f, 1.1f)
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = Estilo.dp(a, 26)
        })
    }
}
