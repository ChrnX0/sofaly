package br.paulo.tvlauncher.home

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import br.paulo.tvlauncher.privilege.GuardiaoShizuku
import br.paulo.tvlauncher.screensaver.ExtensionHealth
import br.paulo.tvlauncher.screensaver.PluginCartao
import br.paulo.tvlauncher.screensaver.PluginWidget
import br.paulo.tvlauncher.settings.Planos
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Widgets de outros apps como cartões da HOME (24/11/2026). Regra pura e o porquê em [PluginWidget].
 *
 * - Ligar: [adicionar] (fora da thread da tela: pode chamar o shell). Sem a tela de consentimento do Android, o
 *   launcher se dá a permissão de ligar widgets com `appwidget grantbind` pelo shell próprio, uma vez; sem shell, só
 *   com o adb (o mesmo comando). Widget que pede tela de ajuste abre a dele antes ([precisaAjuste]).
 * - Mostrar: a HOME pede [cartoes] fora da tela; a FOTO é tirada na thread da tela (views só lá), com prazo.
 * - Atualizar: o host escuta só com a HOME na frente ([ouvir]/[parar]); cada aviso do widget muda a versão da foto.
 */
object PluginWidgetsTv {
    private const val PREFS = "plugin_widgets"
    private const val PRAZO_MS = 2_000L
    private val ui = Handler(Looper.getMainLooper())
    private var host: Host? = null
    private val vistas = HashMap<Int, AppWidgetHostView>()          // só na thread da tela
    private val versoes = ConcurrentHashMap<Int, Int>()
    private val fotos = ConcurrentHashMap<String, Drawable>()       // por endereço de PluginWidget.imagem

    enum class Resultado { OK, LIMITE, SEM_PERMISSAO, BLOQUEADO, FALHOU }

    /** O aparelho tem widgets (o Google TV tem; outra TV pode não ter): sem isso a seção nem aparece. */
    fun existe(ctx: Context): Boolean = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)

    fun escolhidos(ctx: Context): List<PluginWidget.Escolhido> =
        PluginWidget.ler(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", null))

    private fun salvar(ctx: Context, l: List<PluginWidget.Escolhido>) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", PluginWidget.gravar(l)).apply()

    /** Widgets de outros apps instalados (o nosso fica de fora). */
    fun disponiveis(ctx: Context): List<AppWidgetProviderInfo> = runCatching {
        AppWidgetManager.getInstance(ctx).installedProviders
            .filter { it.provider.packageName != ctx.packageName }
            .sortedBy { nome(ctx, it).lowercase() }
    }.getOrDefault(emptyList())

    fun nome(ctx: Context, info: AppWidgetProviderInfo): String {
        val pm = ctx.packageManager
        val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }.getOrDefault(info.provider.packageName)
        val w = runCatching { info.loadLabel(pm) }.getOrNull()?.takeIf { it.isNotBlank() && it != app }
        return if (w == null) app else "$app · $w"
    }

    private fun host(ctx: Context): Host = host ?: Host(ctx.applicationContext).also { host = it }

    /** Widget com tela de ajuste própria: abrir a tela dele antes de mostrar. */
    fun precisaAjuste(info: AppWidgetProviderInfo): Boolean = info.configure != null

    /**
     * Liga um widget. Fora da thread da tela (pode chamar o shell). Devolve o resultado e, com OK, o id. O id ainda
     * não é salvo se [precisaAjuste]: quem chama abre a tela de ajuste e depois chama [confirmar] ou [desfazer].
     */
    fun adicionar(ctx: Context, info: AppWidgetProviderInfo): Pair<Resultado, Int> {
        val l = escolhidos(ctx)
        val prov = info.provider.flattenToString()
        if (!PluginWidget.cabe(l, prov) || !PluginWidget.componenteValido(prov)) return Resultado.LIMITE to -1
        if (ExtensionHealth.bloqueado(ctx, info.provider.packageName)) return Resultado.BLOQUEADO to -1
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
        // O tamanho em que a HOME vai fotografar: o widget se desenha para ele.
        val d = ctx.resources.displayMetrics.density
        val (l2, a2) = PluginWidget.tamanho(info.minWidth, info.minHeight, d)
        runCatching {
            awm.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, (l2 / d).toInt()); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, (l2 / d).toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, (a2 / d).toInt()); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, (a2 / d).toInt())
            })
        }
        if (!precisaAjuste(info)) confirmar(ctx, id, prov)
        ExtensionHealth.perdoar(ctx, info.provider.packageName)
        return Resultado.OK to id
    }

    /** Abre a tela de ajuste do widget (resultado no onActivityResult de [act] com [pedido]). false = não abriu. */
    fun abrirAjuste(act: Activity, id: Int, pedido: Int): Boolean =
        runCatching { host(act).startAppWidgetConfigureActivityForResult(act, id, 0, pedido, null); true }.getOrDefault(false)

    fun confirmar(ctx: Context, id: Int, provedor: String) {
        val l = escolhidos(ctx)
        if (l.any { it.id == id }) return
        salvar(ctx, l + PluginWidget.Escolhido(id, provedor))
    }

    fun desfazer(ctx: Context, id: Int) { runCatching { host(ctx).deleteAppWidgetId(id) } }

    /** Tira o widget da HOME e devolve o id ao sistema. */
    fun remover(ctx: Context, id: Int) {
        salvar(ctx, escolhidos(ctx).filterNot { it.id == id })
        desfazer(ctx, id)
        versoes.remove(id)
        fotos.keys.removeAll { PluginWidget.idDaImagem(it) == id }
        ui.post { vistas.remove(id) }
    }

    /** Desliga todos os widgets de um pacote (lista `blocked` do servidor, guarda de saúde). */
    fun removerPacote(ctx: Context, pacote: String) {
        for (e in escolhidos(ctx).filter { it.pacote == pacote }) remover(ctx, e.id)
    }

    /** HOME na frente: o host recebe os avisos dos widgets. Só com widget ligado (custo zero sem). */
    fun ouvir(ctx: Context) {
        if (escolhidos(ctx).isEmpty()) return
        runCatching { host(ctx).startListening() }
    }

    fun parar() { runCatching { host?.stopListening() } }

    /**
     * Os cartões dos widgets ligados, com a foto de cada um. Chamar FORA da thread da tela (espera a foto lá, com
     * prazo). Sem Pro ou sem widget: lista vazia na hora.
     */
    fun cartoes(ctx: Context): List<Pair<PluginCartao.Cartao, Drawable>> {
        if (!Planos.permite(ctx, Planos.Recurso.PLUGINS)) return emptyList()
        val lista = escolhidos(ctx).filterNot { ExtensionHealth.bloqueado(ctx, it.pacote) }
        if (lista.isEmpty() || Looper.myLooper() == Looper.getMainLooper()) return emptyList()
        val app = ctx.applicationContext
        val agora = System.currentTimeMillis()
        return lista.mapNotNull { e ->
            val endereco = PluginWidget.imagem(e.id, versoes[e.id] ?: 0, agora)
            val inicio = SystemClock.elapsedRealtime()
            val foto = fotos[endereco] ?: run {
                val fim = CountDownLatch(1)
                var r: Bitmap? = null
                ui.post { r = runCatching { fotografar(app, e.id) }.getOrNull(); fim.countDown() }
                fim.await(PRAZO_MS, TimeUnit.MILLISECONDS)
                r?.let { b -> BitmapDrawable(app.resources, b).also { d -> fotos.keys.removeAll { PluginWidget.idDaImagem(it) == e.id }; fotos[endereco] = d } }
            }
            // Guarda de saúde: sem foto (widget sumiu, quebrou ou demorou) conta falha; seguidas, o widget sai da HOME.
            if (ExtensionHealth.registrar(app, e.pacote, foto != null, SystemClock.elapsedRealtime() - inicio)) removerPacote(app, e.pacote)
            foto ?: return@mapNotNull null
            val info = runCatching { AppWidgetManager.getInstance(app).getAppWidgetInfo(e.id) }.getOrNull()
            val titulo = info?.let { nome(app, it) } ?: e.pacote
            val acao = app.packageManager.getLeanbackLaunchIntentForPackage(e.pacote)?.toUri(android.content.Intent.URI_INTENT_SCHEME)
            PluginCartao.Cartao(e.pacote, titulo.take(30), "", "", br.paulo.tvlauncher.Estilo.CARTAO, acao, PluginCartao.Mundo.APPS, endereco) to foto
        }
    }

    /** Desenha o widget fora da tela num bitmap. Só na thread da tela. null = widget não existe mais. */
    private fun fotografar(ctx: Context, id: Int): Bitmap? {
        val awm = AppWidgetManager.getInstance(ctx)
        val info = awm.getAppWidgetInfo(id) ?: return null
        val v = vistas.getOrPut(id) { host(ctx).createView(ctx, id, info) }
        val (l, a) = PluginWidget.tamanho(info.minWidth, info.minHeight, ctx.resources.displayMetrics.density)
        v.measure(View.MeasureSpec.makeMeasureSpec(l, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(a, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, l, a)
        val b = Bitmap.createBitmap(l, a, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        return b
    }

    /** Host que conta os avisos de cada widget: versão nova = foto nova na próxima volta à HOME. */
    private class Host(ctx: Context) : AppWidgetHost(ctx, PluginWidget.HOST_ID) {
        override fun onCreateView(context: Context, id: Int, info: AppWidgetProviderInfo?): AppWidgetHostView = Vista(context, id)
        override fun onProviderChanged(id: Int, info: AppWidgetProviderInfo?) { super.onProviderChanged(id, info); versoes.merge(id, 1, Int::plus) }
    }

    private class Vista(ctx: Context, private val wid: Int) : AppWidgetHostView(ctx) {
        override fun updateAppWidget(remoteViews: RemoteViews?) { super.updateAppWidget(remoteViews); versoes.merge(wid, 1, Int::plus) }
    }
}
