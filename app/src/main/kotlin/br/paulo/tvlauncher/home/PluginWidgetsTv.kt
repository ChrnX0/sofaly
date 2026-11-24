package br.paulo.tvlauncher.home

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import br.paulo.tvlauncher.DesignTokens
import br.paulo.tvlauncher.privilege.GuardiaoShizuku
import br.paulo.tvlauncher.screensaver.ExtensionHealth
import br.paulo.tvlauncher.screensaver.PluginWidget
import br.paulo.tvlauncher.settings.Planos

/**
 * Widgets de outros apps na tela inicial (24/11/2026). Regra pura e o porquê em [PluginWidget].
 *
 * - Ligar: [adicionar] (fora da thread da tela: pode chamar o shell). Sem a tela de consentimento do Android, o
 *   launcher se dá a permissão de ligar widgets com `appwidget grantbind` pelo shell próprio, uma vez; sem shell, só
 *   com o adb (o mesmo comando). Widget com tela de ajustes abre a dele ([abrirAjuste]).
 * - Mostrar: [Camada], o widget VIVO na área livre da HOME, em grade. A mesma camada, com [Camada.editando], é o modo
 *   de edição (WidgetLayoutActivity).
 * - Atualizar: o host escuta só com a HOME (ou a edição) na frente ([ouvir]/[parar]).
 */
object PluginWidgetsTv {
    private const val PREFS = "plugin_widgets"
    private var host: AppWidgetHost? = null

    enum class Resultado { OK, LIMITE, SEM_PERMISSAO, BLOQUEADO, CHEIO, FALHOU }

    /** O aparelho tem widgets (o Google TV tem; outra TV pode não ter): sem isso a seção nem aparece. */
    fun existe(ctx: Context): Boolean = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)

    fun escolhidos(ctx: Context): List<PluginWidget.Escolhido> =
        PluginWidget.ler(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", null))

    fun salvar(ctx: Context, l: List<PluginWidget.Escolhido>) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", PluginWidget.gravar(l)).apply()

    /** Os widgets que a HOME mostra agora: Pro, fora da lista de bloqueio. */
    fun visiveis(ctx: Context): List<PluginWidget.Escolhido> =
        if (!Planos.permite(ctx, Planos.Recurso.PLUGINS)) emptyList()
        else escolhidos(ctx).filterNot { ExtensionHealth.bloqueado(ctx, it.pacote) }

    /** Widgets de outros apps instalados (o nosso fica de fora). */
    fun disponiveis(ctx: Context): List<AppWidgetProviderInfo> = runCatching {
        AppWidgetManager.getInstance(ctx).installedProviders
            .filter { it.provider.packageName != ctx.packageName }
            .sortedBy { nome(ctx, it).lowercase() }
    }.getOrDefault(emptyList())

    fun info(ctx: Context, id: Int): AppWidgetProviderInfo? = runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id) }.getOrNull()

    fun nome(ctx: Context, info: AppWidgetProviderInfo): String {
        val pm = ctx.packageManager
        val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }.getOrDefault(info.provider.packageName)
        val w = runCatching { info.loadLabel(pm) }.getOrNull()?.takeIf { it.isNotBlank() && it != app }
        return if (w == null) app else "$app · $w"
    }

    private fun host(ctx: Context): AppWidgetHost = host ?: AppWidgetHost(ctx.applicationContext, PluginWidget.HOST_ID).also { host = it }

    /** Widget com tela de ajustes própria (a de quando entra e a de "Ajustes do widget"). */
    fun temAjuste(info: AppWidgetProviderInfo?): Boolean = info?.configure != null

    /**
     * Liga um widget e já dá um lugar a ele na grade. Fora da thread da tela (pode chamar o shell). Com OK devolve o
     * id; quem chama abre a tela de ajustes se [temAjuste] e, se a pessoa desistir dela, chama [remover].
     */
    fun adicionar(ctx: Context, info: AppWidgetProviderInfo, celL: Float, celA: Float): Pair<Resultado, Int> {
        val l = escolhidos(ctx)
        val prov = info.provider.flattenToString()
        if (!PluginWidget.cabe(l, prov) || !PluginWidget.componenteValido(prov)) return Resultado.LIMITE to -1
        if (ExtensionHealth.bloqueado(ctx, info.provider.packageName)) return Resultado.BLOQUEADO to -1
        val lim = limites(info, celL, celA)
        val lugar = PluginWidget.lugar(l, lim.minW.coerceAtLeast(3), lim.minH.coerceAtLeast(2), lim) ?: return Resultado.CHEIO to -1
        val awm = AppWidgetManager.getInstance(ctx)
        val h = host(ctx)
        val id = runCatching { h.allocateAppWidgetId() }.getOrElse { return Resultado.FALHOU to -1 }
        fun ligar() = runCatching { awm.bindAppWidgetIdIfAllowed(id, info.provider) }.getOrDefault(false)
        var ok = ligar()
        if (!ok) {
            // Sem a tela de consentimento: o shell (uid 2000 tem MODIFY_APPWIDGET_BIND_PERMISSIONS) libera o launcher.
            val usuario = android.os.Process.myUid() / 100_000
            GuardiaoShizuku.comando(ctx, "appwidget grantbind --package ${ctx.packageName} --user $usuario", 15_000)
            ok = ligar()
        }
        if (!ok) { runCatching { h.deleteAppWidgetId(id) }; return Resultado.SEM_PERMISSAO to -1 }
        val (x, y, t) = lugar
        salvar(ctx, l + PluginWidget.Escolhido(id, prov, x, y, t.first, t.second))
        ExtensionHealth.perdoar(ctx, info.provider.packageName)
        return Resultado.OK to id
    }

    fun limites(info: AppWidgetProviderInfo, celL: Float, celA: Float): PluginWidget.Limites {
        val maxL = if (android.os.Build.VERSION.SDK_INT >= 31) info.maxResizeWidth else 0
        val maxA = if (android.os.Build.VERSION.SDK_INT >= 31) info.maxResizeHeight else 0
        // Redimensionável: o mínimo é o minResize (menor que o min); senão, o min.
        val minL = if (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0 && info.minResizeWidth > 0) info.minResizeWidth else info.minWidth
        val minA = if (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0 && info.minResizeHeight > 0) info.minResizeHeight else info.minHeight
        val l = PluginWidget.limites(minL, minA, maxL, maxA, celL, celA)
        // Sem redimensionar naquele sentido: o tamanho fica preso ao mínimo.
        return l.copy(
            maxW = if (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL == 0) l.minW else l.maxW,
            maxH = if (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL == 0) l.minH else l.maxH,
        )
    }

    /** Abre a tela de ajustes do widget (resultado no onActivityResult de [act] com [pedido]). false = não abriu. */
    fun abrirAjuste(act: Activity, id: Int, pedido: Int): Boolean =
        runCatching { host(act).startAppWidgetConfigureActivityForResult(act, id, 0, pedido, null); true }.getOrDefault(false)

    /** Tira o widget da HOME e devolve o id ao sistema. */
    fun remover(ctx: Context, id: Int) {
        salvar(ctx, escolhidos(ctx).filterNot { it.id == id })
        runCatching { host(ctx).deleteAppWidgetId(id) }
    }

    /** Desliga todos os widgets de um pacote (lista `blocked` do servidor, guarda de saúde). */
    fun removerPacote(ctx: Context, pacote: String) {
        for (e in escolhidos(ctx).filter { it.pacote == pacote }) remover(ctx, e.id)
    }

    /** HOME (ou a edição) na frente: o host recebe os avisos dos widgets. Só com widget ligado (custo zero sem). */
    fun ouvir(ctx: Context) {
        if (escolhidos(ctx).isEmpty()) return
        runCatching { host(ctx).startListening() }
    }

    fun parar() { runCatching { host?.stopListening() } }

    /** Avisa o widget do tamanho em que ele está (dp), para ele se desenhar para esse tamanho. */
    private fun avisarTamanho(ctx: Context, id: Int, lPx: Int, aPx: Int) {
        val d = ctx.resources.displayMetrics.density
        val l = (lPx / d).toInt(); val a = (aPx / d).toInt()
        runCatching {
            AppWidgetManager.getInstance(ctx).updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, l); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, l)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, a); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, a)
            })
        }
    }

    /** Onde a camada mora na HOME (dp): entre a ilha de cima e o nome do mundo + barra de baixo. */
    const val TOPO_DP = 90
    const val BAIXO_DP = 142
    const val LADO_DP = 48

    fun lpCamada(ctx: Context) = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
        val d = ctx.resources.displayMetrics.density
        topMargin = (TOPO_DP * d).toInt(); bottomMargin = (BAIXO_DP * d).toInt()
        marginStart = (LADO_DP * d).toInt(); marginEnd = (LADO_DP * d).toInt()
    }

    /**
     * A área dos widgets: grade de [PluginWidget.COLS] x [PluginWidget.LINHAS]. Na HOME só mostra (sem foco: a barra
     * continua dona do controle). Com [editando], desenha a grade e um anel laranja no [escolhido] (como o foco "Anel").
     */
    class Camada(ctx: Context) : FrameLayout(ctx) {
        var editando = false
            set(v) { field = v; setWillNotDraw(!v); invalidate() }
        var escolhido = -1
            set(v) { field = v; invalidate() }
        var lista: List<PluginWidget.Escolhido> = emptyList()
            private set
        private val vistas = HashMap<Int, AppWidgetHostView>()
        private val d = ctx.resources.displayMetrics.density
        private val ponto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF }
        private val anel = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3 * d; color = DesignTokens.ORANGE }

        init {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            isFocusable = false
            // A grade é absoluta (x da esquerda), como o anel desenhado: em árabe a camada não espelha os lugares.
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            clipChildren = true
        }

        val celL get() = width / PluginWidget.COLS.toFloat()
        val celA get() = height / PluginWidget.LINHAS.toFloat()

        /** Lê os widgets e (re)cria as views. Na thread da tela. Widget que sumiu (app desinstalado) sai da lista. */
        fun atualizar(l: List<PluginWidget.Escolhido> = visiveis(context)) {
            val ctx = context
            val validos = l.filter { e ->
                val ok = info(ctx, e.id) != null
                if (!ok) { ExtensionHealth.registrar(ctx, e.pacote, false, 0); remover(ctx, e.id) }
                ok
            }
            for (id in vistas.keys.toList()) if (validos.none { it.id == id }) removeView(vistas.remove(id))
            for (e in validos) if (e.id !in vistas) {
                val i = info(ctx, e.id) ?: continue
                val v = runCatching { host(ctx).createView(ctx, e.id, i) }.getOrNull() ?: continue
                v.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                // Cantos arredondados como nos launchers desde o Android 12 (o widget quadrado parecia colado na tela).
                v.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, view.width, view.height, 16 * d) }
                }
                v.clipToOutline = true
                vistas[e.id] = v
                addView(v)
            }
            lista = validos
            visibility = if (validos.isEmpty() && !editando) View.GONE else View.VISIBLE
            posicionar()
        }

        /**
         * Recria todas as views. O host guarda UMA view por widget (a última criada): depois do modo de edição, que cria
         * as dele, a HOME recria as suas para voltar a receber as atualizações.
         */
        fun recriar() { removeAllViews(); vistas.clear(); atualizar() }

        /** Troca a lista (modo de edição) sem recriar as views. */
        fun mostrar(l: List<PluginWidget.Escolhido>) { lista = l; posicionar() }

        private fun posicionar() {
            if (width == 0) { post { if (width > 0) posicionar() }; return }
            for (e in lista) {
                val v = vistas[e.id] ?: continue
                // Widget sem lugar (arquivo velho): ganha um agora.
                val p = if (e.colocado) e else info(context, e.id)?.let { i ->
                    PluginWidget.lugar(lista.filter { it.colocado }, 4, 2, limites(i, celL, celA))
                        ?.let { (x, y, t) -> e.copy(x = x, y = y, w = t.first, h = t.second) }
                } ?: continue
                if (p !== e) { lista = lista.map { if (it.id == e.id) p else it }; salvar(context, lista) }
                val lp = (v.layoutParams as? LayoutParams) ?: LayoutParams(0, 0)
                val nl = (p.w * celL).toInt(); val na = (p.h * celA).toInt()
                val mudou = lp.width != nl || lp.height != na
                lp.width = nl; lp.height = na
                lp.marginStart = (p.x * celL).toInt(); lp.topMargin = (p.y * celA).toInt()
                v.layoutParams = lp
                if (mudou) avisarTamanho(context, e.id, nl, na)
            }
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); post { posicionar() } }

        override fun dispatchDraw(c: Canvas) {
            if (editando) {
                // A grade: um ponto em cada canto de célula (como a grade de edição dos launchers de celular, Mobbin).
                for (i in 0..PluginWidget.COLS) for (j in 0..PluginWidget.LINHAS) c.drawCircle(i * celL, j * celA, 2 * d, ponto)
            }
            super.dispatchDraw(c)
            if (editando) lista.firstOrNull { it.id == escolhido && it.colocado }?.let { e ->
                val r = RectF(e.x * celL, e.y * celA, (e.x + e.w) * celL, (e.y + e.h) * celA).apply { inset(2 * d, 2 * d) }
                c.drawRoundRect(r, 14 * d, 14 * d, anel)
            }
        }
    }
}
