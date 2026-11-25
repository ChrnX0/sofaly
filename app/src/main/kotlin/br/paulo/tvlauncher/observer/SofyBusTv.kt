package br.paulo.tvlauncher.observer

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/**
 * A parte do Android do barramento ([SofyBus]). Um arquivo de texto em filesDir, com trava de arquivo: o player roda
 * em outro processo (":player") e o tratador de quedas escreve com o processo morrendo, então nada de memória
 * compartilhada nem de thread nova. [emit] custa uma escrita de uma linha (bem menos de 1 ms) e nunca lança erro.
 *
 * Fica fora do backup (é a história DESTA TV, como o registro "observer"): o que a Sofy aprende com os eventos vai
 * para "sofy_learning", que viaja.
 */
object SofyBusTv {
    private const val TAG = "TvLauncher.SofyBus"
    private const val FILE = "sofy-events.tsv"
    /** Passou disso, a próxima escrita reescreve o arquivo só com o que [SofyBus.trim] guarda. */
    private const val TRIM_AT_BYTES = 96 * 1024L

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, FILE)

    private val lastByKey = HashMap<String, Long>()

    /**
     * Conta à Sofy que algo aconteceu. Qualquer processo, qualquer thread. Sem dado pessoal em [detail].
     * [minGapMs]: o mesmo "fonte.tipo" repetido antes disso (neste processo) não é gravado de novo (ex.: um app que
     * pisca tocar/pausar não enche o arquivo).
     */
<<<<<<< HEAD
=======
    /**
     * A trava de arquivo separa os PROCESSOS (:player); dentro do mesmo processo, duas threads pedindo FileChannel.lock()
     * no mesmo arquivo dão OverlappingFileLockException (não esperam): o evento sumia e a leitura vinha vazia (revisor,
     * 25/11/2026). Esta trava faz as threads do processo esperarem a vez.
     */
    private val processLock = Any()

>>>>>>> origin/main
    fun emit(ctx: Context, source: SofyBus.Source, kind: String, ok: Boolean = true, detail: String = "", minGapMs: Long = 0L) {
        try {
            val now = System.currentTimeMillis()
            if (minGapMs > 0) synchronized(lastByKey) {
                val k = "${source.name}.$kind"
                val antes = lastByKey[k]
                if (antes != null && now - antes in 0 until minGapMs) return
                lastByKey[k] = now
            }
            val linha = SofyBus.line(SofyBus.Event(now, source, kind, ok, detail)) + "\n"
<<<<<<< HEAD
            RandomAccessFile(file(ctx), "rw").use { raf ->
=======
            synchronized(processLock) { RandomAccessFile(file(ctx), "rw").use { raf ->
>>>>>>> origin/main
                raf.channel.lock().use {
                    if (raf.length() > TRIM_AT_BYTES) {
                        raf.seek(0)
                        val todas = ByteArray(raf.length().toInt()).also { raf.readFully(it) }.toString(Charsets.UTF_8).lines()
                        val fica = SofyBus.trim(todas.filter { it.isNotBlank() }, now).joinToString("\n", postfix = "\n")
                        raf.setLength(0)
                        raf.write(fica.toByteArray(Charsets.UTF_8))
                    }
                    raf.seek(raf.length())
                    raf.write(linha.toByteArray(Charsets.UTF_8))
                }
<<<<<<< HEAD
            }
=======
            } }
>>>>>>> origin/main
        } catch (t: Throwable) { Log.w(TAG, "emit $source/$kind: ${t.javaClass.simpleName}") }
    }

    /** Os eventos guardados (até 7 dias), do mais velho ao mais novo. */
    fun events(ctx: Context): List<SofyBus.Event> = try {
        val f = file(ctx)
<<<<<<< HEAD
        if (!f.exists()) emptyList() else RandomAccessFile(f, "rw").use { raf ->
=======
        if (!f.exists()) emptyList() else synchronized(processLock) { RandomAccessFile(f, "rw").use { raf ->
>>>>>>> origin/main
            raf.channel.lock().use {
                raf.seek(0)
                ByteArray(raf.length().toInt()).also { raf.readFully(it) }.toString(Charsets.UTF_8)
            }
<<<<<<< HEAD
        }.lineSequence().mapNotNull { SofyBus.parse(it) }.filter { System.currentTimeMillis() - it.at <= SofyBus.KEEP_MS }.toList()
=======
        } }.lineSequence().mapNotNull { SofyBus.parse(it) }.filter { System.currentTimeMillis() - it.at <= SofyBus.KEEP_MS }.toList()
>>>>>>> origin/main
    } catch (t: Throwable) { Log.w(TAG, "ler: ${t.javaClass.simpleName}"); emptyList() }

    fun toJson(e: SofyBus.Event): JSONObject = JSONObject().put("at", e.at).put("source", e.source.name.lowercase())
        .put("kind", e.kind).put("ok", e.ok).apply { if (e.detail.isNotEmpty()) put("detail", e.detail) }

    /** Para o MCP `observer`: os [max] mais novos, a contagem e as falhas das últimas 24 h. Só leitura. */
    fun json(ctx: Context, max: Int = 30): JSONObject {
        val ev = events(ctx)
        val desde = System.currentTimeMillis() - 86_400_000L
        val recentes = JSONArray()
        for (e in ev.takeLast(max).asReversed()) recentes.put(toJson(e))
        val falhas = JSONArray()
        for (e in SofyBus.failures(ev, desde).take(20)) falhas.put(toJson(e))
        return JSONObject().put("total", ev.size).put("counts_24h", JSONObject(SofyBus.counts(ev, desde) as Map<*, *>))
            .put("failures_24h", falhas).put("recent", recentes)
    }
}
