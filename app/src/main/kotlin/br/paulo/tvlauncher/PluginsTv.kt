package br.paulo.tvlauncher

import br.paulo.tvlauncher.settings.PlanosTv
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import br.paulo.tvlauncher.home.PacoteIcones
import br.paulo.tvlauncher.panel.QrCodigo
import br.paulo.tvlauncher.player.LojaCurada
import br.paulo.tvlauncher.screensaver.PluginCartao
import br.paulo.tvlauncher.screensaver.PluginCatalog
import br.paulo.tvlauncher.screensaver.PluginTile
import br.paulo.tvlauncher.screensaver.WallpaperPlugins
import br.paulo.tvlauncher.screensaver.ExtensionHealth
import br.paulo.tvlauncher.settings.Planos
import br.paulo.tvlauncher.settings.Tema
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Plugins de terceiros na HOME (degrau 3, 16/11/2026). O contrato e as regras puras estão em [PluginCartao]; aqui é a
 * parte Android: achar os plugins instalados, guardar o OK do dono por (pacote, certificado) e ler o cartão de cada um.
 *
 * Segurança, em ordem: (1) nada aparece sem o OK do dono, dado nesta TV com o PIN parental (Configurações › Aparência ›
 * Plugins); (2) o OK vale só para o certificado que assinou o plugin quando o dono aceitou, então um APK trocado por
 * outro de mesmo nome perde o lugar; (3) cada leitura tem 2 s de prazo, fora da thread da tela, para um plugin lento não
 * travar a HOME; (4) o launcher só RECEBE dados do plugin, nunca manda nada para ele.
 */
object PluginsTv {
    private const val PREFS = "plugins"
    private const val PRAZO_MS = 2_000L

    // ---- Vitrine (24/11/2026): a lista do servidor, guardada; sem ela, a embutida (PluginCatalog.parse explica as regras).
    private const val VITRINE = "plugin_catalog"

    /** A vitrine atual. Não acessa a rede. */
    fun vitrine(ctx: Context): List<PluginCatalog.Entrada> =
        PluginCatalog.parse(ctx.getSharedPreferences(VITRINE, Context.MODE_PRIVATE).getString("json", null), idioma())
            ?: PluginCatalog.LISTA

    private fun idioma() = PluginCatalog.idioma(java.util.Locale.getDefault().language)

    /** Texto da linha da vitrine: o do app (traduzido) ou o que veio do servidor. */
    fun descricao(ctx: Context, e: PluginCatalog.Entrada): String = if (e.descricao != 0) ctx.getString(e.descricao) else e.texto.orEmpty()

    /** Pergunta ao servidor no máximo 1 vez por dia (fora da thread da tela). Falhou: fica a lista que já havia. */
    fun atualizarVitrine(ctx: Context) {
        val p = ctx.getSharedPreferences(VITRINE, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - p.getLong("quando", 0) < PluginCatalog.REFRESH_MS) return
        p.edit().putLong("quando", System.currentTimeMillis()).apply()
        val texto = br.paulo.tvlauncher.player.AddonsTv.baixar(PluginCatalog.URL) ?: return
        if (PluginCatalog.parse(texto, idioma()) != null) p.edit().putString("json", texto).apply()
    }

    data class Instalado(val pacote: String, val autoridade: String, val nome: String)

    /** Plugins instalados (qualquer app com um provider que responde a [acao]; padrão: cartão). */
    fun instalados(ctx: Context, acao: String = PluginCartao.ACAO): List<Instalado> {
        val pm = ctx.packageManager
        return runCatching { pm.queryIntentContentProviders(Intent(acao), 0) }.getOrDefault(emptyList())
            .mapNotNull { r ->
                val p = r.providerInfo ?: return@mapNotNull null
                if (!p.exported || p.packageName == ctx.packageName) return@mapNotNull null
                // Um provider pode ter várias autoridades ("a;b"): usamos a primeira.
                val aut = p.authority?.split(';')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Instalado(p.packageName, aut, runCatching { p.applicationInfo.loadLabel(pm).toString() }.getOrDefault(p.packageName))
            }.distinctBy { it.pacote }.sortedBy { it.nome.lowercase() }
    }

    /** SHA-256 (hex) do certificado que assina [pacote], ou null (app sumiu ou tem mais de um assinante). */
    fun certificado(ctx: Context, pacote: String): String? = runCatching {
        val info = ctx.packageManager.getPackageInfo(pacote, PackageManager.GET_SIGNING_CERTIFICATES)
        val s = info.signingInfo ?: return null
        if (s.hasMultipleSigners()) return null
        val cert = s.signingCertificateHistory?.lastOrNull() ?: return null
        MessageDigest.getInstance("SHA-256").digest(cert.toByteArray()).joinToString("") { "%02x".format(it) }
    }.getOrNull()

    fun permitidos(ctx: Context): Map<String, String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun permitido(ctx: Context, pacote: String): Boolean = PluginCartao.permitido(permitidos(ctx), pacote, certificado(ctx, pacote))

    /** Liga (com o certificado de agora) ou desliga um plugin. Ligar só vale até [PluginCartao.MAX_PLUGINS]. */
    fun definir(ctx: Context, pacote: String, ligado: Boolean): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!ligado) { prefs.edit().remove(pacote).apply(); return true }
        val cert = certificado(ctx, pacote) ?: return false
        if (permitidos(ctx).count { it.key != pacote } >= PluginCartao.MAX_PLUGINS) return false
        prefs.edit().putString(pacote, cert).apply()
        ExtensionHealth.perdoar(ctx, pacote)
        return true
    }

    private val leitor = Executors.newCachedThreadPool { r -> Thread(r, "Plugin").apply { isDaemon = true } }

    /** Os cartões dos plugins permitidos (no máximo 3). Bloqueia até 2 s por plugin: chamar FORA da thread da tela. */
    fun cartoes(ctx: Context): List<PluginCartao.Cartao> {
        val ok = permitidos(ctx)
        if (ok.isEmpty() || !Planos.permite(ctx, Planos.Recurso.PLUGINS)) return emptyList()   // plano: Pro
        return instalados(ctx).filter { PluginCartao.permitido(ok, it.pacote, certificado(ctx, it.pacote)) }
            .take(PluginCartao.MAX_PLUGINS).mapNotNull { p ->
                val inicio = android.os.SystemClock.elapsedRealtime()
                val f = leitor.submit<Pair<Boolean, PluginCartao.Cartao?>> { ler(ctx, p) }
                val r = runCatching { f.get(PRAZO_MS, TimeUnit.MILLISECONDS) }.getOrElse { f.cancel(true); false to null }
                // Guarda de saúde: sem resposta no prazo ou erro conta falha (cartão vazio é resposta válida); seguidas, desliga.
                if (ExtensionHealth.registrar(ctx, p.pacote, r.first, android.os.SystemClock.elapsedRealtime() - inicio)) definir(ctx, p.pacote, false)
                r.second
            }
    }

    /** (respondeu, cartão): respondeu = o provider atendeu (mesmo sem linha); false = erro ou provider sumido. */
    private fun ler(ctx: Context, p: Instalado): Pair<Boolean, PluginCartao.Cartao?> = runCatching {
        val uri = Uri.parse("content://${p.autoridade}/${PluginCartao.CAMINHO}")
        // Sem projeção (todas as colunas): um plugin v1 feito com SQLite recusaria as colunas novas da v2 (world, image).
        val cursor = ctx.contentResolver.query(uri, null, null, null, null) ?: return false to null
        cursor.use { c ->
            if (!c.moveToFirst()) return true to null
            val linha = PluginCartao.COLUNAS.associateWith { col ->
                val i = c.getColumnIndex(col)
                if (i < 0) null else runCatching { c.getString(i) }.getOrNull()
            }
            // Imagem (v2) só de um provider do PRÓPRIO plugin: outra autoridade sai (o cartão fica, sem imagem).
            val c0 = PluginCartao.limpar(p.pacote, linha)
            val c1 = c0?.imagem?.let { u ->
                val dono = runCatching { ctx.packageManager.resolveContentProvider(PluginCartao.autoridade(u), 0)?.packageName }.getOrNull()
                if (dono == p.pacote) c0 else c0.copy(imagem = null)
            } ?: c0
            true to c1
        }
    }.getOrDefault(false to null)

    /** A imagem do cartão (v2), reduzida a no máximo 256 px, com o mesmo prazo de 2 s. FORA da thread da tela. */
    fun imagem(ctx: Context, c: PluginCartao.Cartao): android.graphics.drawable.Drawable? {
        val u = c.imagem ?: return null
        val f = leitor.submit<android.graphics.Bitmap?> {
            val uri = Uri.parse(u)
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
            if (o.outWidth <= 0 || o.outHeight <= 0 || o.outWidth > 4096 || o.outHeight > 4096) return@submit null
            var amostra = 1
            while (maxOf(o.outWidth, o.outHeight) / (amostra * 2) >= 256) amostra *= 2
            ctx.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = amostra })
            }
        }
        val b = runCatching { f.get(PRAZO_MS, TimeUnit.MILLISECONDS) }.getOrElse { f.cancel(true); null } ?: return null
        return android.graphics.drawable.BitmapDrawable(ctx.resources, b)
    }

    /** Abre a ação do cartão, sempre presa ao pacote do plugin (sem componente nem seletor), ou o próprio app. */
    fun abrir(ctx: Context, c: PluginCartao.Cartao) {
        val i = c.acao?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }?.apply {
            component = null; selector = null; setPackage(c.pacote)
        } ?: ctx.packageManager.getLeanbackLaunchIntentForPackage(c.pacote) ?: ctx.packageManager.getLaunchIntentForPackage(c.pacote)
        ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }
}

/**
 * Botões de outros apps no painel rápido ([PluginTile], 24/11/2026). Mesmas regras dos cartões: OK do dono com PIN
 * preso ao certificado, no máximo [PluginTile.MAX], prazo de 2 s por leitura/clique fora da thread da tela, guarda de
 * saúde. O OK fica em prefs próprias: ligar o cartão de um app não liga o botão dele, e vice-versa.
 */
object PluginTilesTv {
    private const val PREFS = "plugin_tiles"
    private const val PRAZO_MS = 2_000L
    private val leitor = Executors.newCachedThreadPool { r -> Thread(r, "PluginTile").apply { isDaemon = true } }

    fun instalados(ctx: Context) = PluginsTv.instalados(ctx, PluginTile.ACAO)

    private fun permitidos(ctx: Context): Map<String, String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun permitido(ctx: Context, pacote: String): Boolean = PluginCartao.permitido(permitidos(ctx), pacote, PluginsTv.certificado(ctx, pacote))

    fun definir(ctx: Context, pacote: String, ligado: Boolean): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!ligado) { prefs.edit().remove(pacote).apply(); return true }
        val cert = PluginsTv.certificado(ctx, pacote) ?: return false
        if (permitidos(ctx).count { it.key != pacote } >= PluginTile.MAX) return false
        prefs.edit().putString(pacote, cert).apply()
        ExtensionHealth.perdoar(ctx, pacote)
        return true
    }

    private fun comPrazo(ctx: Context, pacote: String, tarefa: () -> Pair<Boolean, PluginTile.Botao?>): PluginTile.Botao? {
        val inicio = android.os.SystemClock.elapsedRealtime()
        val f = leitor.submit<Pair<Boolean, PluginTile.Botao?>> { tarefa() }
        val r = runCatching { f.get(PRAZO_MS, TimeUnit.MILLISECONDS) }.getOrElse { f.cancel(true); false to null }
        if (ExtensionHealth.registrar(ctx, pacote, r.first, android.os.SystemClock.elapsedRealtime() - inicio)) definir(ctx, pacote, false)
        return r.second
    }

    private fun ler(ctx: Context, p: PluginsTv.Instalado): Pair<Boolean, PluginTile.Botao?> = runCatching {
        val cursor = ctx.contentResolver.query(Uri.parse("content://${p.autoridade}/${PluginTile.CAMINHO}"), PluginTile.COLUNAS, null, null, null)
            ?: return false to null
        cursor.use { c ->
            if (!c.moveToFirst()) return true to null
            true to PluginTile.limpar(p.pacote, PluginTile.COLUNAS.associateWith { col ->
                val i = c.getColumnIndex(col); if (i < 0) null else runCatching { c.getString(i) }.getOrNull()
            })
        }
    }.getOrDefault(false to null)

    /** Os botões ligados (no máximo [PluginTile.MAX]), com a autoridade para o clique. FORA da thread da tela. */
    fun botoes(ctx: Context): List<Pair<PluginsTv.Instalado, PluginTile.Botao>> {
        val ok = permitidos(ctx)
        if (ok.isEmpty() || !Planos.permite(ctx, Planos.Recurso.PLUGINS)) return emptyList()
        return instalados(ctx).filter { PluginCartao.permitido(ok, it.pacote, PluginsTv.certificado(ctx, it.pacote)) }
            .take(PluginTile.MAX).mapNotNull { p -> comPrazo(ctx, p.pacote) { ler(ctx, p) }?.let { p to it } }
    }

    /** OK no botão: o app faz a ação dele; devolve a linha nova (ou null se ele não respondeu). FORA da thread da tela. */
    fun clicar(ctx: Context, p: PluginsTv.Instalado): PluginTile.Botao? = comPrazo(ctx, p.pacote) {
        val uri = Uri.parse("content://${p.autoridade}/${PluginTile.CAMINHO}")
        val feito = runCatching { ctx.contentResolver.call(uri, PluginTile.METODO_CLIQUE, null, null); true }.getOrDefault(false)
        if (!feito) false to null else ler(ctx, p)
    }
}

/**
 * Configurações › Aparência › Plugins (o PIN parental já foi pedido para entrar). Uma tela só, em quatro grupos:
 * FUNDOS (plugins de fundo instalados: usar, ajustes do plugin, parar), ÍCONES (pacotes de ícones de outros
 * desenvolvedores; ficam congelados, IconPackFreezer), CARTÕES (o OK de cada cartão) e CONSEGUIR PLUGINS (a vitrine
 * PluginCatalog: Play Store, instalar do Release oficial ou QR). Pacotes e fundos de terceiros = plano Pro.
 */
class PluginsActivity : Activity() {
    private val trabalho = Executors.newSingleThreadExecutor { r -> Thread(r, "Plugins").apply { isDaemon = true } }
    private var aberto: android.app.Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (VigiaAtualizacao.fechada(this)) return
        montar()
        // A vitrine do servidor (1 vez por dia): vale na próxima vez que a tela abrir.
        runCatching { trabalho.execute { PluginsTv.atualizarVitrine(applicationContext) } }
    }

    override fun onDestroy() { super.onDestroy(); aberto?.dismiss(); trabalho.shutdown() }

    /** Volta de outro app (ajustes do plugin, Play Store): a lista pode ter mudado. */
    override fun onRestart() { super.onRestart(); montar() }

    private fun pro(): Boolean {
        if (Planos.permite(this, Planos.Recurso.PLUGINS)) return true
        startActivity(Intent(this, ConhecerProActivity::class.java))
        return false
    }

    private fun montar() {
        val (raiz, coluna) = Estilo.telaLista(this, getString(R.string.plg_titulo), getString(R.string.wp_det))
        val fundos = WallpaperPlugins.instalados(this)
        val pacotes = PacoteIcones.deFora(this)
        val cartoes = PluginsTv.instalados(this)

        // ---- Fundos
        coluna.addView(Estilo.secao(this, getString(R.string.wp_secao_fundos)))
        if (fundos.isEmpty()) coluna.addView(Estilo.texto(this, Estilo.Papel.LEGENDA, getString(R.string.wp_nenhum_fundo)))
        val escolhido = WallpaperPlugins.escolhidoId(this)
        for (f in fundos) {
            val emUso = f.id == escolhido && Tema.atual(this) == Tema.PLUGIN && WallpaperPlugins.permitido(this, f.pacote)
            val valor = Estilo.valor(this, if (emUso) getString(R.string.wp_em_uso) else "", seta = !emUso)
            // Desligado pela guarda de saúde (ExtensionHealth): o motivo no lugar do tipo, até o dono ligar de novo.
            val sub = if (!emUso && ExtensionHealth.desligadoEm(this, f.pacote) > 0) getString(R.string.ext_off_health) else getString(R.string.wp_tipo_fundo)
            coluna.addView(Estilo.linhaOpcao(this, f.nome, sub, valor).apply {
                setOnClickListener { menuFundo(f, emUso) }
            }, Estilo.lpLinha(this))
        }

        // ---- Ícones
        coluna.addView(Estilo.secao(this, getString(R.string.wp_secao_icones)).apply { setPaddingRelative(0, Estilo.dp(context, 24), 0, 0) })
        if (pacotes.isEmpty()) coluna.addView(Estilo.texto(this, Estilo.Papel.LEGENDA, getString(R.string.wp_nenhum_icone)))
        for ((pac, nome) in pacotes) {
            val emUso = PacoteIcones.escolhido(this) == pac
            val valor = Estilo.valor(this, if (emUso) getString(R.string.wp_em_uso) else getString(R.string.wp_usar_icones), seta = !emUso)
            coluna.addView(Estilo.linhaOpcao(this, nome, getString(R.string.wp_icones_det), valor).apply {
                setOnClickListener {
                    if (!pro()) return@setOnClickListener
                    PacoteIcones.escolher(this@PluginsActivity, if (emUso) "" else pac)   // de novo no que está em uso = desliga
                    montar()
                }
            }, Estilo.lpLinha(this))
        }

        // ---- Cartões
        coluna.addView(Estilo.secao(this, getString(R.string.wp_secao_cartoes)).apply { setPaddingRelative(0, Estilo.dp(context, 24), 0, 0) })
        if (cartoes.isEmpty()) coluna.addView(Estilo.texto(this, Estilo.Papel.LEGENDA, getString(R.string.plg_nenhum)))
        for (p in cartoes) {
            val valor = Estilo.valor(this, "", seta = false)
            fun pintar() { valor.text = getString(if (PluginsTv.permitido(this, p.pacote)) R.string.plg_ligado else R.string.plg_desligado) }
            pintar()
            val sub = if (ExtensionHealth.desligadoEm(this, p.pacote) > 0 && !PluginsTv.permitido(this, p.pacote)) getString(R.string.ext_off_health) else p.pacote
            coluna.addView(Estilo.linhaOpcao(this, p.nome, sub, valor).apply {
                setOnClickListener {
                    val novo = !PluginsTv.permitido(this@PluginsActivity, p.pacote)
                    if (novo && !PlanosTv.pedir(this@PluginsActivity, Planos.Recurso.PLUGINS)) return@setOnClickListener   // ligar: Pro
                    if (!PluginsTv.definir(this@PluginsActivity, p.pacote, novo))
                        android.widget.Toast.makeText(this@PluginsActivity, getString(R.string.plg_limite, PluginCartao.MAX_PLUGINS), android.widget.Toast.LENGTH_LONG).show()
                    pintar()
                }
            }, Estilo.lpLinha(this))
        }

        // ---- Botões do painel rápido (PluginTile, 24/11/2026)
        val botoes = PluginTilesTv.instalados(this)
        coluna.addView(Estilo.secao(this, getString(R.string.ext_secao_botoes)).apply { setPaddingRelative(0, Estilo.dp(context, 24), 0, 0) })
        if (botoes.isEmpty()) coluna.addView(Estilo.texto(this, Estilo.Papel.LEGENDA, getString(R.string.ext_nenhum_botao)))
        for (p in botoes) {
            val valor = Estilo.valor(this, "", seta = false)
            fun pintar() { valor.text = getString(if (PluginTilesTv.permitido(this, p.pacote)) R.string.plg_ligado else R.string.plg_desligado) }
            pintar()
            val sub = if (ExtensionHealth.desligadoEm(this, p.pacote) > 0 && !PluginTilesTv.permitido(this, p.pacote)) getString(R.string.ext_off_health) else p.pacote
            coluna.addView(Estilo.linhaOpcao(this, p.nome, sub, valor).apply {
                setOnClickListener {
                    val novo = !PluginTilesTv.permitido(this@PluginsActivity, p.pacote)
                    if (novo && !PlanosTv.pedir(this@PluginsActivity, Planos.Recurso.PLUGINS)) return@setOnClickListener
                    if (!PluginTilesTv.definir(this@PluginsActivity, p.pacote, novo))
                        android.widget.Toast.makeText(this@PluginsActivity, getString(R.string.plg_limite, PluginTile.MAX), android.widget.Toast.LENGTH_LONG).show()
                    pintar()
                }
            }, Estilo.lpLinha(this))
        }

        // ---- Conseguir plugins (vitrine)
        coluna.addView(Estilo.secao(this, getString(R.string.wp_secao_descobrir)).apply { setPaddingRelative(0, Estilo.dp(context, 24), 0, 0) })
        val instalados = (fundos.map { it.pacote } + pacotes.map { it.first }).toSet()
        val vitrine = PluginsTv.vitrine(this)
        for (e in vitrine) {
            val pac = e.pacote ?: e.lojaId?.let { LojaCurada.pacoteGuardado(this, it) }
            val ja = pac != null && (pac in instalados || instaladoNoSistema(pac))
            val tipo = getString(if (e.tipo == PluginCatalog.Tipo.FUNDO) R.string.wp_tipo_fundo else R.string.wp_tipo_icones)
            val valor = Estilo.valor(this, if (ja) getString(R.string.wp_instalado) else "", seta = !ja)
            coluna.addView(Estilo.linhaOpcao(this, e.nome, listOf(tipo, PluginsTv.descricao(this, e)).filter { it.isNotEmpty() }.joinToString(" · "), valor).apply {
                setOnClickListener { if (ja) montar() else oferecer(e) }
            }, Estilo.lpLinha(this))
        }
        coluna.addView(Estilo.texto(this, Estilo.Papel.LEGENDA, getString(R.string.plg_seguranca)).apply { setPaddingRelative(0, 24, 0, 0) })
        setContentView(raiz)
        coluna.getChildAt(1)?.takeIf { it.isFocusable }?.requestFocus() ?: run {
            for (k in 0 until coluna.childCount) if (coluna.getChildAt(k).isFocusable) { coluna.getChildAt(k).requestFocus(); break }
        }
    }

    private fun instaladoNoSistema(p: String) =
        runCatching { packageManager.getApplicationInfo(p, PackageManager.MATCH_DISABLED_COMPONENTS); true }.getOrDefault(false)

    /** Fundo instalado: usar como fundo, ajustes do próprio plugin, parar de usar. */
    private fun menuFundo(f: WallpaperPlugins.Provider, emUso: Boolean) {
        val opcoes = mutableListOf<Pair<String, () -> Unit>>()
        if (!emUso) opcoes += getString(R.string.wp_usar) to {
            if (pro()) {
                if (WallpaperPlugins.usar(this, f)) montar()
                else android.widget.Toast.makeText(this, getString(R.string.wp_sem_cert), android.widget.Toast.LENGTH_LONG).show()
            }
        }
        if (f.settings != null || emUso) opcoes += getString(R.string.wp_configurar) to { WallpaperPlugins.configurar(this, f); Unit }
        if (emUso) opcoes += getString(R.string.wp_parar) to { WallpaperPlugins.parar(this, f); montar() }
        aberto = Estilo.lista(this, f.nome, opcoes.map { it.first }) { i -> opcoes[i].second() }
    }

    /** Plugin da vitrine que não está na TV: Play Store, instalar do Release oficial (APK do site) e o QR da página. */
    private fun oferecer(e: PluginCatalog.Entrada) {
        val temPlay = runCatching { packageManager.getPackageInfo("com.android.vending", 0); true }.getOrDefault(false)
        val acao = PluginCatalog.acao(e, BuildConfig.PLAY, temPlay)
        val d = android.app.Dialog(this)
        val botoes = mutableListOf(Estilo.botao(this, getString(R.string.vq_fechar), primario = false).apply { setOnClickListener { d.dismiss() } })
        when (acao) {
            PluginCatalog.Acao.PLAY -> botoes += Estilo.botao(this, getString(R.string.wp_abrir_play)).apply {
                setOnClickListener {
                    d.dismiss()
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${e.play}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            PluginCatalog.Acao.INSTALAR -> botoes += Estilo.botao(this, getString(R.string.wp_instalar)).apply {
                setOnClickListener {
                    if (!pro()) return@setOnClickListener
                    isEnabled = false
                    text = getString(R.string.wp_instalando)
                    val id = e.lojaId ?: return@setOnClickListener
                    trabalho.execute {
                        val r = LojaCurada.instalar(applicationContext, id)
                        runOnUiThread {
                            if (isDestroyed) return@runOnUiThread
                            d.dismiss()
                            val ok = !r.startsWith("erro:")
                            android.widget.Toast.makeText(this@PluginsActivity, if (ok) getString(R.string.wp_instalou, e.nome)
                                else getString(R.string.wp_nao_instalou, r.removePrefix("erro:").trim()), android.widget.Toast.LENGTH_LONG).show()
                            montar()
                        }
                    }
                }
            }
            PluginCatalog.Acao.SO_QR -> {}
        }
        val tela = Estilo.dialogo(this, getString(if (e.tipo == PluginCatalog.Tipo.FUNDO) R.string.wp_tipo_fundo else R.string.wp_tipo_icones),
            e.nome, listOf(PluginsTv.descricao(this, e), getString(R.string.wp_qr)).filter { it.isNotEmpty() }.joinToString("\n\n"), botoes)
        PluginCatalog.pagina(e)?.let { link ->
            val lado = Estilo.dp(this, 220)
            val qr = android.widget.ImageView(this).apply { setImageBitmap(runCatching { QrCodigo.bitmap(link, lado) }.getOrNull()) }
            (tela.getChildAt(0) as? android.widget.LinearLayout)?.addView(qr, 0, android.widget.LinearLayout.LayoutParams(lado, lado).apply {
                bottomMargin = Estilo.dp(this@PluginsActivity, 16)
            })
        }
        Estilo.vestir(d, tela)
        d.show()
        aberto = d
        botoes.last().requestFocus()
    }
}
