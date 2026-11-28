package br.paulo.onetv.celular

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

/**
 * As poucas peças das telas nativas do app, com os MESMOS tokens da TV e do painel (design/tokens.json, 20/11/2026).
 * Por quê: antes o celular tinha fundo, letra e botão próprios e parecia outro produto. Para quê: quem passa da TV para
 * o celular reconhece o Sofaly. Como: cores de [DesignTokens]; tema claro quando o celular está claro; alto contraste
 * quando o Android pede texto de alto contraste; títulos em Nunito (a letra do logo).
 */
object Visual {
    /** O celular está no modo claro? (lido a cada tela: a pessoa pode trocar com o app aberto) */
    var claro = false
        private set
    /** Alto contraste: "Texto em alto contraste" do Android ligado, ou a chave da TV (vem do painel). */
    var altoContraste = false

    /**
     * Tema do sistema escolhido na TV (28/11/2026: Aconchego, Azulejos ou Vidro valem também no celular). Vem do
     * /api/estado ("visual") e fica guardado para a próxima abertura; o painel (WebView) já segue sozinho.
     */
    var look: String = "cozy"
        private set

    fun guardarLook(ctx: Context, nome: String?) {
        val n = nome?.takeIf { it in setOf("cozy", "tiles", "glass", "glass_color") } ?: return
        look = n
        runCatching { ctx.getSharedPreferences("visual", Context.MODE_PRIVATE).edit().putString("look", n).apply() }
    }

    fun atualizar(ctx: Context) {
        look = runCatching { ctx.getSharedPreferences("visual", Context.MODE_PRIVATE).getString("look", null) }.getOrNull() ?: look
        claro = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO
        altoContraste = altoContraste || runCatching {
            Settings.Secure.getInt(ctx.contentResolver, "high_text_contrast_enabled", 0) == 1
        }.getOrDefault(false)
    }

    val FUNDO: Int get() = if (claro) DesignTokens.PAPER else DesignTokens.NIGHT
    /** Cartão: um degrau acima do fundo, como os cartões translúcidos do painel. */
    val SUPERFICIE: Int get() = when {
        claro -> 0xFFFFFFFF.toInt()
        look == "cozy" -> 0xFF292826.toInt()    // grafite com um toque quente (o mesmo do painel)
        look == "tiles" -> 0xFF282826.toInt()
        else -> 0xFF1F1F1D.toInt()               // vidro: o cartão de sempre, translúcido no painel
    }
    val TEXTO: Int get() = if (claro) DesignTokens.NIGHT else DesignTokens.CREAM
    val TEXTO_2: Int get() = when {
        altoContraste -> TEXTO
        claro -> 0xFF4D4C48.toInt()
        else -> 0xFFB8B0A7.toInt()
    }
    /** Botão principal = a cor do foco (branco no escuro, Noite no claro), igual ao painel e à TV. */
    val PRINCIPAL: Int get() = if (claro) DesignTokens.NIGHT else DesignTokens.FOCUS
    val TEXTO_PRINCIPAL: Int get() = if (claro) DesignTokens.CREAM else DesignTokens.NIGHT
    /** Laranja: só marca, ligado e progresso (nunca botão comum). */
    val ACENTO: Int get() = when {
        claro -> DesignTokens.ORANGE_ON_LIGHT
        altoContraste -> DesignTokens.ORANGE_HIGH
        else -> DesignTokens.ORANGE
    }

    private var titulo: Typeface? = null
    /** Nunito 700 (res/font), a família dos títulos na TV, no painel e no site. */
    fun fonteTitulo(ctx: Context): Typeface =
        titulo ?: (runCatching { ctx.resources.getFont(R.font.nunito_700) }.getOrNull() ?: Typeface.DEFAULT_BOLD).also { titulo = it }

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun texto(ctx: Context, s: String, sp: Float = 17f, cor: Int = TEXTO, negrito: Boolean = false) = TextView(ctx).apply {
        text = s; setTextColor(cor); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (negrito) typeface = fonteTitulo(ctx)
        setLineSpacing(0f, 1.2f)
    }

    fun botao(ctx: Context, s: String, primario: Boolean = true, acao: () -> Unit) = TextView(ctx).apply {
        text = s; gravity = Gravity.CENTER; setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); typeface = fonteTitulo(ctx)
        setTextColor(if (primario) TEXTO_PRINCIPAL else TEXTO)
        minHeight = dp(ctx, 56); setPadding(dp(ctx, 20), 0, dp(ctx, 20), 0)
        background = GradientDrawable().apply {
            cornerRadius = dp(ctx, 28).toFloat(); setColor(if (primario) PRINCIPAL else SUPERFICIE)
            if (altoContraste && !primario) setStroke(dp(ctx, 2), TEXTO)
        }
        isClickable = true; isFocusable = true
        setOnClickListener { acao() }
    }

    fun cartao(ctx: Context) = GradientDrawable().apply {
        cornerRadius = dp(ctx, DesignTokens.RADIUS_MEDIUM).toFloat(); setColor(SUPERFICIE)
        if (altoContraste) setStroke(dp(ctx, 2), TEXTO_2)
    }
}
