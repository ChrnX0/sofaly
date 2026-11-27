package br.paulo.tvlauncher.observer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import br.paulo.tvlauncher.Prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Diário de ajustes (25/11/2026): o que mudou nas preferências logo antes de um defeito. É o que dá à Sofy a
 * ferramenta "desfazer a última mudança" ([SofyRepair.Tool.UNDO_LAST_CHANGE]).
 *
 * Por que existe: muita coisa quebra porque alguém (ou uma atualização) mudou um ajuste. Sem diário a Sofy só sabe
 * reiniciar; com ele ela volta o ajuste, confere, e se NÃO resolveu põe o ajuste de volta como estava ([redo]),
 * porque desfazer a escolha de alguém sem ganho nenhum seria pior que o defeito.
 *
 * Esta parte é pura (sem Android), testada na JVM. Valores ficam codificados como texto: "b:true", "i:3", "s:texto";
 * null = a chave não existia.
 */
object SettingsJournal {
    data class Change(val file: String, val key: String, val old: String?, val new: String?, val at: Long, val undone: Boolean = false)

    const val MAX = 40
    /** Só vale como "causa provável" o que mudou até meia hora antes do primeiro sinal do defeito. */
    const val WINDOW_MS = 30 * 60_000L
    /** Chave que muda toda hora é estado interno (contador, "última vez"), não um ajuste de alguém. */
    const val NOISY = 3

    /** Nomes que são relógio/contador e nunca um ajuste (os arquivos misturam as duas coisas). */
    private val STATE = Regex("(_em|_at|_ms|_vez|_dia|ultim|last|cont|count|visto|seen|cache|versao|version)", RegexOption.IGNORE_CASE)

    /** Codifica um valor de preferência. null = tipo que o diário não acompanha (Long = relógio, Float, conjuntos). */
    fun encode(v: Any?): String? = when (v) {
        is Boolean -> "b:$v"
        is Int -> "i:$v"
        is String -> if (v.length <= 200) "s:$v" else null
        else -> null
    }

    fun tracked(key: String) = !STATE.containsMatchIn(key)
    /** Nas prefs "clima" só a escolha da pessoa: o resto é cache da previsão, gravado a cada 5 min (WeatherPrefs). */
    fun trackedIn(file: String, key: String) = file != "clima" || br.paulo.tvlauncher.weather.WeatherPrefs.isChoice(key)

    fun record(list: List<Change>, c: Change): List<Change> =
        if (c.old == c.new || !tracked(c.key) || !trackedIn(c.file, c.key)) list else (list + c).takeLast(MAX)

    /** A mudança a desfazer para um defeito que começou em [firstAt]: a mais recente na janela, que não seja ruído. */
    fun candidate(list: List<Change>, firstAt: Long): Change? {
        val janela = list.filter { !it.undone && it.at <= firstAt && firstAt - it.at <= WINDOW_MS }
        return janela.lastOrNull { c -> janela.count { it.file == c.file && it.key == c.key } < NOISY }
    }

    fun toJson(list: List<Change>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("f", it.file).put("k", it.key).put("o", it.old ?: JSONObject.NULL).put("n", it.new ?: JSONObject.NULL).put("t", it.at).put("u", it.undone)) }
    }.toString()

    fun fromJson(s: String?): List<Change> = runCatching {
        val a = JSONArray(s ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            Change(o.getString("f"), o.getString("k"), if (o.isNull("o")) null else o.getString("o"), if (o.isNull("n")) null else o.getString("n"), o.getLong("t"), o.optBoolean("u"))
        }
    }.getOrDefault(emptyList())
}

/** A parte Android: escuta os arquivos de ajustes e sabe desfazer/refazer. */
object SettingsJournalTv {
    private const val TAG = "TvLauncher.Journal"
    private const val STORE = "sofy_repair"
    /** Os arquivos onde moram ajustes de gente (não os de estado interno). */
    private val FILES = listOf(Prefs.LAUNCHER, Prefs.PLAYER, Prefs.CLIMA, Prefs.EXTRAS)

    // O SharedPreferences guarda o ouvinte por referência fraca: sem esta lista ele some na primeira limpeza de memória.
    private val listeners = ArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()
    private val mirror = HashMap<String, HashMap<String, String?>>()
    @Volatile private var started = false

    private fun store(ctx: Context) = ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    fun load(ctx: Context) = SettingsJournal.fromJson(store(ctx).getString("journal", null))
    private fun save(ctx: Context, l: List<SettingsJournal.Change>) = store(ctx).edit().putString("journal", SettingsJournal.toJson(l)).apply()

    /** Liga a escuta uma vez por processo. O espelho guarda o valor de ANTES (o ouvinte só avisa depois da troca). */
    @Synchronized fun ensure(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        for (f in FILES) {
            val sp = app.getSharedPreferences(f, Context.MODE_PRIVATE)
            val m = HashMap<String, String?>().also { mirror[f] = it }
            sp.all.forEach { (k, v) -> m[k] = SettingsJournal.encode(v) }
            val l = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                if (key == null) return@OnSharedPreferenceChangeListener
                val novo = SettingsJournal.encode(p.all[key])
                val antes = synchronized(mirror) { val a = m[key]; m[key] = novo; a }
                if (antes == novo) return@OnSharedPreferenceChangeListener
                synchronized(this) { save(app, SettingsJournal.record(load(app), SettingsJournal.Change(f, key, antes, novo, System.currentTimeMillis()))) }
            }
            listeners += l
            sp.registerOnSharedPreferenceChangeListener(l)
        }
    }

    fun hasCandidate(ctx: Context, firstAt: Long) = SettingsJournal.candidate(load(ctx), firstAt) != null

    /**
     * Escrita da própria Sofy (desfazer/refazer não é "mudança de alguém"): o espelho recebe o valor ANTES de gravar,
     * então o ouvinte, que roda depois na thread principal, vê "igual ao que eu já sabia" e não anota nada.
     */
    private fun write(ctx: Context, file: String, key: String, value: String?) {
        synchronized(mirror) { mirror[file]?.put(key, value) }
        val e = ctx.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
        when {
            value == null -> e.remove(key)
            value.startsWith("b:") -> e.putBoolean(key, value.substring(2).toBoolean())
            value.startsWith("i:") -> e.putInt(key, value.substring(2).toInt())
            else -> e.putString(key, value.substring(2))
        }
        e.apply()
    }

    /** Volta o ajuste que mudou logo antes do defeito. Devolve true se havia o que voltar. */
    @Synchronized fun undo(ctx: Context, caseKey: String, firstAt: Long): Boolean {
        val l = load(ctx)
        val c = SettingsJournal.candidate(l, firstAt) ?: return false
        write(ctx, c.file, c.key, c.old)
        save(ctx, l.map { if (it === c) it.copy(undone = true) else it })
        store(ctx).edit().putString("undo:$caseKey", SettingsJournal.toJson(listOf(c))).apply()
        Log.i(TAG, "desfez ${c.file}/${c.key}")
        return true
    }

    /** O desfazer não resolveu: o ajuste volta para o que a pessoa tinha escolhido. */
    @Synchronized fun redo(ctx: Context, caseKey: String) {
        val c = SettingsJournal.fromJson(store(ctx).getString("undo:$caseKey", null)).firstOrNull() ?: return
        write(ctx, c.file, c.key, c.new)
        store(ctx).edit().remove("undo:$caseKey").apply()
        Log.i(TAG, "refez ${c.file}/${c.key}")
    }

    /** Resolveu: esquece o caminho de volta (o ajuste fica desfeito). */
    fun keep(ctx: Context, caseKey: String) = store(ctx).edit().remove("undo:$caseKey").apply()
}
