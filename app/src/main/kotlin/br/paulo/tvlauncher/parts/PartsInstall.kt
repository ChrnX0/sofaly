package br.paulo.tvlauncher.parts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.PowerManager
import android.util.Log
import br.paulo.tvlauncher.Atualizador
import br.paulo.tvlauncher.Config
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.PartInstallResult
import br.paulo.tvlauncher.Prefs
import br.paulo.tvlauncher.mcp.McpServidor
import br.paulo.tvlauncher.mcp.TunelTailscale
import br.paulo.tvlauncher.player.SubtitleEngines
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Instala as partes sob demanda (refatoração G, 27/11/2026). Duas portas:
 *  - a atualização (Atualizador.atualizarPorPartes) leva a base + as partes já instaladas + as em uso ([inUse]);
 *  - [request]: a função precisa de uma parte que esta TV não tem (alguém ligou o acesso pela internet, o player quis
 *    traduzir uma legenda). Baixa do Release da versão instalada (tag v<versionCode>) e acrescenta a parte com
 *    MODE_INHERIT_EXISTING, sem fechar o launcher (setDontKillApp, Android 13+; antes disso, só com a tela apagada).
 *
 * No APK cheio de sempre nada disto age: [Parts.has] responde sim para tudo.
 */
object PartsInstall {
    private const val TAG = "TvLauncher.Parts"
    private const val PREFS = "parts"
    private const val INTERVALO_MS = 30 * 60_000L   // uma tentativa por parte a cada 30 min (rede fora, Release sem partes)
    const val ACTION = "br.paulo.tvlauncher.PARTE_RESULTADO"
    const val EXTRA_PART = "parte"

    /** Atualizar por partes: só no canal de teste até a prova na Sala. Depois, vale para todas as TVs. */
    fun enabled(testChannel: Boolean): Boolean = testChannel

    /** Funções que esta TV usa e que moram numa parte: a atualização leva essas mesmo vindo do APK cheio. */
    fun inUse(ctx: Context): Set<String> = buildSet {
        if (McpServidor.precisaServidor(ctx) || TunelTailscale.configurado(ctx)) add(Parts.TUNNEL)
        // Já traduziu alguma legenda nesta TV (a marca fica nas preferências do player).
        if (ctx.getSharedPreferences(Prefs.PLAYER, Context.MODE_PRIVATE).all.keys.any { it.startsWith("legtrad:") }) add(Parts.TRANSLATE)
    }

    /** Liga os pedidos automáticos (LauncherApp.onCreate, em todo processo: o player traduz no processo dele). */
    fun install(ctx: Context) {
        val app = ctx.applicationContext
        SubtitleEngines.onMissing = { request(app, Parts.TRANSLATE) }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val ocupado = AtomicBoolean(false)

    /** O que aconteceu com cada parte por último (para o EXTRAS partes). */
    fun status(ctx: Context): JSONObject = JSONObject().apply {
        for (p in Parts.ALL) prefs(ctx).getString("estado_$p", null)?.let { put(p, it) }
    }

    internal fun note(ctx: Context, part: String, text: String) {
        Log.i(TAG, "$part: $text")
        prefs(ctx).edit().putString("estado_$part", "${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.ROOT).format(java.util.Date())} $text").apply()
    }

    /** Pede a parte que falta, em fundo. Não faz nada no APK cheio, com a parte já instalada ou numa tentativa recente. */
    fun request(ctx: Context, part: String) {
        val app = ctx.applicationContext
        if (part !in Parts.ALL || Parts.has(app, part)) return
        val agora = System.currentTimeMillis()
        if (agora - prefs(app).getLong("tentou_$part", 0L) < INTERVALO_MS) return
        if (!ocupado.compareAndSet(false, true)) return
        prefs(app).edit().putLong("tentou_$part", agora).apply()
        Fundo.rodar("parte-$part") {
            try { note(app, part, installFromRelease(app, part)) }
            catch (e: Exception) { note(app, part, "erro: ${e.javaClass.simpleName} ${e.message}") }
            finally { ocupado.set(false) }
        }
    }

    private fun installFromRelease(app: Context, part: String): String {
        // Sem setDontKillApp (Android < 13), instalar fecha o launcher: só com a tela apagada (o pedido volta depois).
        if (Build.VERSION.SDK_INT < 33 && app.getSystemService(PowerManager::class.java)?.isInteractive != false) {
            prefs(app).edit().remove("tentou_$part").apply()
            return "esperando a tela apagar (Android ${Build.VERSION.SDK_INT})"
        }
        val codigo = Atualizador.instalado(app).longVersionCode
        val release = JSONObject(Atualizador.baixarTexto("https://api.github.com/repos/${Config.ATUALIZACAO_REPO}/releases/tags/v$codigo"))
        val links = HashMap<String, String>()
        val assets = release.optJSONArray("assets")
        for (i in 0 until (assets?.length() ?: 0)) assets!!.getJSONObject(i).let { links[it.optString("name")] = it.optString("browser_download_url") }
        val urlIndice = links[PartsPlan.INDEX] ?: return "v$codigo não tem ${PartsPlan.INDEX}"
        val indice = PartsPlan.parse(Atualizador.baixarTexto(urlIndice))?.takeIf { it.versionCode == codigo }
            ?: return "${PartsPlan.INDEX} de v$codigo ilegível ou de outra versão"
        val plano = PartsPlan.files(indice, PartsPlan.abi(android.os.Process.is64Bit()), setOf(part))
            ?.filter { it.split == part }?.takeIf { it.isNotEmpty() && it.all { f -> links[f.name] != null } }
            ?: return "v$codigo sem os arquivos da parte"
        val pasta = File(app.cacheDir, "parte-$part").apply { deleteRecursively(); mkdirs() }
        try {
            val arquivos = plano.map { f ->
                File(pasta, f.name).also { destino ->
                    Atualizador.baixarArquivo(links.getValue(f.name), destino)
                    if (destino.length() != f.size || Atualizador.sha256(destino) != f.sha256) return "${f.name}: tamanho ou SHA-256 não confere"
                }
            }
            commitInherit(app, part, arquivos)
            return "instalando (v$codigo, ${arquivos.sumOf { it.length() } / 1024} KB)"
        } finally {
            pasta.deleteRecursively()
        }
    }

    /**
     * Acrescenta [arquivos] (os APKs de UMA parte, mesma versão e chave do app instalado; o Android confere) sem mexer no
     * resto. O resultado chega em [PartInstallResult].
     */
    fun commitInherit(app: Context, part: String, arquivos: List<File>) {
        val instalador = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_INHERIT_EXISTING).apply {
            setAppPackageName(app.packageName)
            setSize(arquivos.sumOf { it.length() })
            if (Build.VERSION.SDK_INT >= 33) setDontKillApp(true)
        }
        val id = instalador.createSession(params)
        instalador.openSession(id).use { sessao ->
            try {
                for (a in arquivos) sessao.openWrite(a.name, 0, a.length()).use { saida ->
                    a.inputStream().use { it.copyTo(saida) }
                    sessao.fsync(saida)
                }
            } catch (e: Exception) {
                try { sessao.abandon() } catch (_: Exception) {}
                throw e
            }
            val aviso = Intent(app, PartInstallResult::class.java).setAction(ACTION).putExtra(EXTRA_PART, part)
            val pi = PendingIntent.getBroadcast(app, id, aviso, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            sessao.commit(pi.intentSender)
        }
    }

    /**
     * Só adb (EXTRAS --es o partes_local --es pasta <pasta legível pelo app>): instala os part-<parte>*.apk da pasta,
     * uma parte por sessão, sem baixar nada. Para provar a instalação num aparelho de teste antes de um Release.
     */
    fun installLocal(ctx: Context, pasta: File): String {
        val app = ctx.applicationContext
        val porParte = pasta.listFiles { f -> f.name.endsWith(".apk") }.orEmpty().groupBy { f ->
            Parts.ALL.firstOrNull { f.name.startsWith("part-$it") }
        }
        val feitas = porParte.filterKeys { it != null }.map { (part, arquivos) ->
            commitInherit(app, part!!, arquivos.sortedBy { it.name }); "$part(${arquivos.size})"
        }
        return if (feitas.isEmpty()) "nenhum part-translate*/part-tunnel*.apk em $pasta" else "instalando ${feitas.joinToString()}"
    }
}
