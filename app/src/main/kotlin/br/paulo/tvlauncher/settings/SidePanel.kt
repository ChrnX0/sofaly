package br.paulo.tvlauncher.settings

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.home.MovimentoAdaptativo

/**
 * Painel ao lado (Paulo, 25/11/2026: "algo como A+B com o estilo do E").
 *
 * Porquê: as Configurações cobriam a TV inteira; quem mudava a aparência só via o resultado depois de sair.
 * Para quê: a janela das Configurações vira um painel de vidro à direita e a HOME de verdade continua à vista atrás.
 * Nas portas comuns a HOME escurece um pouco (o olho fica no painel); na porta "Tela inicial" ela fica nítida e muda na
 * hora a cada opção (a prévia da proposta B; a MainActivity se refaz sozinha quando a aparência muda).
 * Como: a janela da Activity (tema translúcido) fica com [LARGURA_DP] de largura, presa à direita (à esquerda em árabe e
 * hebraico, pelo Gravity.END); o fundo da janela é o vidro e o sistema borra o que está atrás dele
 * ([android.view.Window.setBackgroundBlurRadius], Android 12+). Sem borrão disponível, o vidro fica quase sólido.
 *
 * Referências: Mobbin (Sora f067f14d, painel por cima do conteúdo vivo; Grok e Tide Guide, listas em grupos; Circle,
 * valor à direita), Projectivy (painel à direita com a home viva), Ajustes do tvOS e do Google TV (painel lateral).
 * Movimento pelo transitions.dev: abrir 250 ms (desacelera), fechar 150 ms (acelera), linhas entrando a cada 40 ms.
 */
internal object SidePanel {
    /** Largura do painel (de 960 dp): cabe a linha de opção com título, explicação e valor sem apertar. */
    const val LARGURA_DP = 560
    /** Margem de dentro do painel. */
    const val MARGEM_DP = 28
    const val RAIO_DP = 28
    /** Quanto o sistema borra a HOME atrás do vidro (dp). */
    const val BORRAO_DP = 36
    /** Quanto a HOME escurece fora da prévia (0 = nada, 1 = preto). */
    const val ESCURECE = 0.45f
    const val ABRIR_MS = br.paulo.tvlauncher.DesignTokens.MS_FAST
    const val FECHAR_MS = br.paulo.tvlauncher.DesignTokens.MS_QUICK
    const val ESCALONAR_MS = br.paulo.tvlauncher.DesignTokens.MS_STAGGER
    /** Quantas linhas entram escalonadas (as de baixo, fora da tela, não precisam). */
    const val ESCALONAR_MAX = 8

    /** "Remover animações" do sistema, alto contraste ou aparelho no nível parado: tudo aparece de uma vez. */
    fun semMovimento(a: Activity): Boolean {
        val sistema = runCatching {
            android.provider.Settings.Global.getFloat(a.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
        if (sistema || Estilo.altoContraste) return true
        return runCatching { MovimentoAdaptativo.atual(a, sistema) == MovimentoAdaptativo.Nivel.PARADO }.getOrDefault(false)
    }

    /**
     * Monta a janela do painel: largura, lado, vidro e borrão. Chamado no onCreate, antes do setContentView.
     * Devolve false quando a tela deve ficar inteira (o tema translúcido vira opaco e a HOME para de desenhar atrás).
     */
    fun janela(a: Activity, painel: Boolean): Boolean {
        val w = a.window
        if (!painel) {
            if (Build.VERSION.SDK_INT >= 30) runCatching { a.setTranslucent(false) }
            w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Estilo.FUNDO))
            w.setWindowAnimations(android.R.style.Animation_Activity)   // o tema do painel tira a animação de janela
            return false
        }
        w.setGravity(Gravity.END or Gravity.TOP)
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        w.setDimAmount(0f)   // o escurecer entra junto com o painel ([entrar])
        esfumar(a, w, LARGURA_DP)
        return true
    }

    /**
     * O mesmo vidro numa janela de diálogo (menus, 25/11/2026: "os menus no mesmo vidro"). Usado pelo [Estilo.lista].
     * [video] = tem vídeo tocando atrás (player): o vídeo não borra nem escurece, e o vidro fica quase sólido para ler
     * por cima de qualquer quadro. Fora do vídeo: borra o que está atrás e escurece de leve, como o painel das Configurações.
     */
    fun vidroNoDialogo(ctx: android.content.Context, w: android.view.Window, larguraDp: Int, video: Boolean) {
        w.setGravity(Gravity.END or Gravity.TOP)
        esfumar(ctx, w, larguraDp)
        if (video) w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        else { w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); w.setDimAmount(MENU_ESCURECE) }
    }

    /** A faixa de dentro onde o vidro some aos poucos, sem conteúdo (protótipo: máscara em degradê). */
    const val ESFUMADO_DP = 200

    /**
     * Na Sala (26/11/2026, foto) o borrão do sistema cortava o painel numa linha reta: ele borra o retângulo inteiro da
     * janela e não aceita máscara. Porquê: o protótipo aprovado some aos poucos para a tela de trás. Para quê: o painel
     * parece pousado sobre a tela, não um bloco colado nela. Como: a janela cresce [ESFUMADO_DP] para dentro, sem borrão,
     * com o degradê grafite indo de transparente a firme; o conteúdo fica com o recuo dessa faixa e mantém a largura
     * [larguraDp]. Sem borrão também acaba o custo de refazê-lo a cada quadro (96% atrasados com a HOME animando atrás).
     */
    private fun esfumar(ctx: android.content.Context, w: android.view.Window, larguraDp: Int) {
        w.setLayout(Estilo.dp(ctx, larguraDp + ESFUMADO_DP), ViewGroup.LayoutParams.MATCH_PARENT)
        val rtl = ctx.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        w.setBackgroundDrawable(degrade(rtl, false))
        w.decorView.findViewById<View>(android.R.id.content)?.setPaddingRelative(Estilo.dp(ctx, ESFUMADO_DP), 0, 0, 0)
    }

    /**
     * O vidro do painel (protótipo aprovado, Paulo 26/11/2026: o bloco com moldura ficou "grosseiro"): um degradê grafite
     * sem borda nem canto, mais leve do lado de dentro e firme do lado da borda da tela, para a HOME atrás respirar.
     * Com borrão do sistema a tinta é mais leve; sem ele, mais firme, para o texto não brigar com a imagem.
     */
    fun degrade(rtl: Boolean, @Suppress("UNUSED_PARAMETER") borrao: Boolean = false): GradientDrawable {
        // Mais firme onde está o texto (Sala, 26/11/2026: os cartões da HOME apareciam atrás das linhas).
        val cores = intArrayOf(0x00141413, 0xB3141413.toInt(), 0xEB141413.toInt(), 0xF7141413.toInt())
        return GradientDrawable(if (rtl) GradientDrawable.Orientation.RIGHT_LEFT else GradientDrawable.Orientation.LEFT_RIGHT, cores)
    }

    /** Quanto a tela atrás de um menu escurece (menos que as Configurações: o menu é rápido, a tela continua a mesma). */
    const val MENU_ESCURECE = 0.3f

    private var animDim: ValueAnimator? = null

    /** A HOME atrás: nítida ([previa] = true, porta "Tela inicial") ou um pouco escura (as outras portas). */
    fun previa(a: Activity, previa: Boolean, ms: Long = ABRIR_MS) = escurecer(a, if (previa) 0f else ESCURECE, ms)

    private fun escurecer(a: Activity, alvo: Float, ms: Long) {
        val w = a.window
        val de = w.attributes.dimAmount
        animDim?.cancel()
        if (de == alvo) return
        if (semMovimento(a) || ms <= 0) { w.setDimAmount(alvo); return }
        animDim = ValueAnimator.ofFloat(de, alvo).apply {
            duration = ms
            interpolator = Estilo.curva()
            addUpdateListener { if (!a.isDestroyed) w.setDimAmount(it.animatedValue as Float) }
            start()
        }
    }

    /** O painel entra pelo lado de fora (24 dp) e aparece, em [ABRIR_MS]; a HOME escurece junto (se não for prévia). */
    fun entrar(a: Activity, raiz: View, previa: Boolean) {
        if (semMovimento(a)) { previa(a, previa, 0); return }
        val lado = if (raiz.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1
        raiz.alpha = 0f
        raiz.translationX = lado * Estilo.dp(a, 24).toFloat()
        raiz.animate().alpha(1f).translationX(0f).setDuration(ABRIR_MS).setInterpolator(Estilo.curva()).start()
        previa(a, previa)
    }

    /** Sai em [FECHAR_MS] (mais rápido que a entrada: fechar não pode parecer lento) e então [depois]. */
    fun sair(a: Activity, raiz: View, depois: () -> Unit) {
        if (semMovimento(a)) { depois(); return }
        val lado = if (raiz.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1 else 1
        escurecer(a, 0f, FECHAR_MS)
        raiz.animate().alpha(0f).translationX(lado * Estilo.dp(a, 16).toFloat()).setDuration(FECHAR_MS)
            .setInterpolator(Estilo.curva()).withEndAction(depois).start()
    }

    /** As primeiras linhas de [c] entram uma depois da outra (40 ms), subindo 8 dp. */
    fun escalonar(a: Activity, c: ViewGroup) {
        if (semMovimento(a)) return
        val sobe = Estilo.dp(a, 8).toFloat()
        var n = 0
        for (k in 0 until c.childCount) {
            val v = c.getChildAt(k)
            if (v.visibility != View.VISIBLE) continue
            if (n >= ESCALONAR_MAX) break
            // Fio separador (View pura) entra junto com a linha de cima, sem contar como uma linha.
            val fio = v.javaClass == View::class.java
            val atraso = (if (fio) (n - 1).coerceAtLeast(0) else n) * ESCALONAR_MS
            v.alpha = 0f; v.translationY = sobe
            // ObjectAnimator e não v.animate(): o atraso e o ritmo do ViewPropertyAnimator ficam grudados na linha, e a
            // animação do foco dela (que usa animate()) passaria a esperar os mesmos 40 ms.
            android.animation.AnimatorSet().apply {
                playTogether(android.animation.ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f),
                    android.animation.ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, sobe, 0f))
                startDelay = atraso; duration = ABRIR_MS; interpolator = Estilo.curva()
                start()
            }
            if (!fio) n++
        }
    }
}
