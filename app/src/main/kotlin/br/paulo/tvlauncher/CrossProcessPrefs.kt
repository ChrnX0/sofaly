package br.paulo.tvlauncher

import android.content.Context
import java.io.File

/**
 * Ajustes vistos pelo processo `:player` (teste da noite, 25/11/2026).
 *
 * Por quê: Rádio, Podcasts, Gravações, Filmes, Séries e os players rodam no processo `:player`, que vive horas. O
 * Android guarda cada arquivo de preferências na memória do processo e só relê do disco com MODE_MULTI_PROCESS; o que
 * as Configurações (processo principal) mudavam não chegava lá até o `:player` morrer. Visto na Sala: trocar o jeito
 * das telas para Barra deixava Podcasts igual nos 3 jeitos (mesmo MD5 da foto). O mesmo valia para tamanho do texto,
 * alto contraste, cor de destaque, forma do foco e qualquer chave do Extras.
 *
 * Como: antes de cada tela do `:player` (LauncherApp.onActivityPreCreated), pede cada arquivo pequeno de preferências
 * com MODE_MULTI_PROCESS. O Android só relê o que mudou (compara data e tamanho do arquivo) e entrega a MESMA instância
 * que o resto do código já usa, então todo `getSharedPreferences(..., MODE_PRIVATE)` passa a ver o valor novo.
 * Arquivo grande (> [MAX_BYTES]) é cache, não ajuste: fica de fora para não encher a memória do `:player`.
 */
object CrossProcessPrefs {
    const val MAX_BYTES = 64 * 1024L

    val inPlayer: Boolean by lazy {
        runCatching { android.app.Application.getProcessName() }.getOrNull()?.endsWith(":player") == true
    }

    /** Os nomes (sem .xml) dos arquivos de ajustes desta pasta que valem a releitura. Puro, testado. */
    fun settingsFiles(dir: File?): List<String> =
        dir?.listFiles()?.filter { it.isFile && it.name.endsWith(".xml") && it.length() <= MAX_BYTES }
            ?.map { it.name.removeSuffix(".xml") }?.sorted().orEmpty()

    @Suppress("DEPRECATION")
    fun refresh(ctx: Context, here: Boolean = inPlayer) {
        if (!here) return
        runCatching {
            for (nome in settingsFiles(File(ctx.applicationInfo.dataDir, "shared_prefs")))
                ctx.getSharedPreferences(nome, Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS)
        }
    }
}
