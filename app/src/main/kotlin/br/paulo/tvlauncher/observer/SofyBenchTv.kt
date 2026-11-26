package br.paulo.tvlauncher.observer

import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.Fundo
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * A parte Android de [SofyBench]: roda as provas na TV, com a chave do dono, e grava uma linha por chamada no logcat
 * (TvLauncher.SofyBench) e em noBackupFilesDir/sofy_bench.log (lido pelo adb com `sofy_bench_dump`).
 * Só pelo adb (Paulo, 26/11/2026: teste da madrugada). Não muda nada da Sofy: não guarda regra nem abre caso.
 */
object SofyBenchTv {
    private const val TAG = "TvLauncher.SofyBench"
    private const val FILE = "sofy_bench.log"
    /** Pausa entre chamadas: não encosta no limite de pedidos por minuto da chave. */
    private const val PAUSA_MS = 700L

    @Volatile private var rodando: String? = null
    @Volatile private var parar = false
    @Volatile private var feitas = 0
    @Volatile private var total = 0

    /** As telas reais lidas pela conferência com `capture`: nome -> textos. Só na memória (podem ter a agenda da casa). */
    private val capturadas = ConcurrentHashMap<String, List<String>>()

    fun capture(screen: String, texts: List<String>) { capturadas[screen] = texts }

    private fun file(ctx: Context) = File(ctx.applicationContext.noBackupFilesDir, FILE)

    fun status(): String = JSONObject().put("running", rodando ?: "").put("done", feitas).put("total", total)
        .put("captured", capturadas.keys.sorted().joinToString(",")).toString()

    fun stop(): String { parar = true; return "parando" }

    /** Linhas [from]..[from]+150 do arquivo (as do mais antigo para o mais novo). */
    fun dump(ctx: Context, from: Int): String {
        val f = file(ctx)
        if (!f.exists()) return "lines=0"
        val ls = f.readLines()
        return "lines=${ls.size}\n" + ls.drop(from.coerceAtLeast(0)).take(150).joinToString("\n")
    }

    fun clear(ctx: Context): String { file(ctx).delete(); return "apagado" }

    /**
     * Começa uma prova: [task] repair|review|second|real; [models] na ordem (gira a cada repetição, para o horário não
     * favorecer ninguém); cada item é perguntado a todos os modelos antes do próximo.
     */
    fun start(ctx: Context, task: String, models: List<String>, reps: Int, effort: String): String {
        val app = ctx.applicationContext
        val t = runCatching { SofyBench.Task.valueOf(task.uppercase()) }.getOrNull() ?: return "task: repair|review|second|real"
        if (models.isEmpty()) return "--es models claude-haiku-5-5,claude-sonnet-5-5,..."
        if (rodando != null) return "já rodando: $rodando"
        if (SofyAi.settings(app) == null) return "sem a IA do dono configurada"
        val itens = when (t) {
            SofyBench.Task.REPAIR -> SofyBench.REPAIR.size
            SofyBench.Task.REVIEW -> SofyBench.REVIEW.size
            SofyBench.Task.SECOND -> SofyBench.SECOND.size
            SofyBench.Task.REAL -> capturadas.size
        }
        if (itens == 0) return "nenhuma tela capturada: rode screen_review --ez capture true antes"
        val r = reps.coerceIn(1, 20)
        rodando = "${t.name} x$r ${models.joinToString(",")} eff=${effort.ifEmpty { "-" }}"
        parar = false; feitas = 0; total = itens * models.size * r
        Fundo.rodar("sofy-banco") {
            try {
                for (rep in 1..r) {
                    val ordem = models.indices.map { models[(it + rep - 1) % models.size] }
                    when (t) {
                        SofyBench.Task.REPAIR -> for (it in SofyBench.REPAIR) for (m in ordem) {
                            if (parar) return@rodar
                            val c = SofyAi.bench(app, m, SofyAiRules.SYSTEM, SofyAiRules.prompt(it.case), effort)
                            grava(app, t, m, effort, it.id, rep, c, c?.let { x -> SofyBench.fmt(SofyBench.scoreRepair(it, x.text)) } ?: "valid=0")
                        }
                        SofyBench.Task.REVIEW -> for (it in SofyBench.REVIEW) for (m in ordem) {
                            if (parar) return@rodar
                            val c = SofyAi.bench(app, m, SofyRules.SYSTEM, SofyRules.prompt(it.screen, it.texts, emptyList()), effort)
                            grava(app, t, m, effort, it.id, rep, c, c?.let { x -> SofyBench.fmt(SofyBench.scoreReview(it.screen, it.texts, it.bad, x.text)) } ?: "valid=0")
                        }
                        SofyBench.Task.SECOND -> for (it in SofyBench.SECOND) for (m in ordem) {
                            if (parar) return@rodar
                            val c = SofyAi.bench(app, m, SofyRules.SECOND_SYSTEM, SofyRules.secondPrompt(it.screen, it.texts, it.rules), effort)
                            grava(app, t, m, effort, it.id, rep, c, c?.let { x -> SofyBench.fmt(SofyBench.scoreJudge(it, x.text)) } ?: "valid=0")
                        }
                        SofyBench.Task.REAL -> {
                            // As telas de verdade, com as regras que a TV já guarda: a mesma pergunta da noite, sem gravar nada.
                            val regras = SofyRulesTv.rules(app)
                            for ((tela, textos) in capturadas.toSortedMap()) for (m in ordem) {
                                if (parar) return@rodar
                                val c = SofyAi.bench(app, m, SofyRules.SYSTEM, SofyRules.prompt(tela, textos, regras), effort)
                                val s = c?.let { x -> SofyBench.scoreReview(tela, textos, null, x.text, regras) }
                                grava(app, t, m, effort, tela, rep, c, s?.let { SofyBench.fmt(it) } ?: "valid=0",
                                    extra = c?.text?.let { x -> SofyRules.parse(x, tela, textos, regras, 0).defects.joinToString(" | ") }.orEmpty())
                            }
                        }
                    }
                }
            } finally {
                Log.i(TAG, "fim: ${rodando}; $feitas de $total" + if (parar) " (parado)" else "")
                rodando = null
            }
        }
        return "começou: $rodando ($total chamadas)"
    }

    /** [extra] (os defeitos que a IA viu numa tela real) só vai para o arquivo, nunca para o logcat. */
    private fun grava(app: Context, t: SofyBench.Task, m: String, eff: String, item: String, rep: Int, c: SofyAi.BenchCall?, score: String, extra: String = "") {
        val linha = if (c == null) SofyBench.line(t, m, eff, item, rep, 0, 0, 0, 0, 0, "", "valid=0 err=config_or_budget")
            else SofyBench.line(t, m, eff, item, rep, c.http, c.ms, c.inTok, c.outTok, c.cacheRead, c.stop, score)
        Log.i(TAG, linha)
        runCatching { file(app).appendText(System.currentTimeMillis().toString() + " " + linha +
            (if (extra.isEmpty()) "" else " defects=" + extra.replace('\n', ' ').take(400)) + "\n") }
        feitas++
        Thread.sleep(PAUSA_MS)
    }
}
