package br.paulo.tvlauncher

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Preferências entre o processo principal e o `:player` (troca do MODE_MULTI_PROCESS, 27/11/2026).
 *
 * O defeito: Rádio, Podcasts, Gravações, Filmes, Séries e os players rodam no `:player`, que vive horas. Cada processo
 * guarda uma cópia do arquivo na memória e, ao salvar, regrava o arquivo INTEIRO com a cópia dele. O arquivo "player"
 * é gravado pelos dois (as Configurações gravam os ajustes; o `:player` grava atraso de legenda, tamanho da legenda,
 * legenda baixada...): o último a salvar apagava o que o outro tinha acabado de gravar. O MODE_MULTI_PROCESS (obsoleto
 * desde o Android 6, "não funciona de forma confiável") só relia o arquivo quando alguém pedia as preferências de novo.
 *
 * A troca: um dono só. No processo principal nada muda (é ele quem grava no disco). No `:player`, toda tela, serviço e
 * receptor pede as preferências por [wrap], que devolve um [RemotePrefs]: lê uma cópia que o [PrefsProvider] (no processo
 * principal) manda e manda para ele cada gravação. Quando o principal grava, o provider avisa e a cópia é jogada fora.
 * Arquivo grande (> [MAX_BYTES]) é cache, não ajuste: continua local no `:player` (não cabe numa chamada entre processos).
 */
object CrossProcessPrefs {
    /** Acima disso o arquivo é cache e fica local: a cópia viaja numa chamada entre processos (limite do Android ~1 MB). */
    const val MAX_BYTES = 256 * 1024L

    val inPlayer: Boolean by lazy {
        runCatching { android.app.Application.getProcessName() }.getOrNull()?.endsWith(":player") == true
    }

    /** Os nomes (sem .xml) dos arquivos de ajustes desta pasta. Puro, testado. */
    fun settingsFiles(dir: File?): List<String> =
        dir?.listFiles()?.filter { it.isFile && it.name.endsWith(".xml") && it.length() <= MAX_BYTES }
            ?.map { it.name.removeSuffix(".xml") }?.sorted().orEmpty()

    fun authority(ctx: Context) = "${ctx.packageName}.prefs"
    fun uri(ctx: Context, name: String): Uri = Uri.parse("content://${authority(ctx)}/${Uri.encode(name)}")

    private val remotos = ConcurrentHashMap<String, SharedPreferences>()

    /** As preferências [name] que este processo deve usar: no `:player`, as do dono; fora dele, [local]. */
    fun prefs(ctx: Context, name: String, local: () -> SharedPreferences, here: Boolean = inPlayer): SharedPreferences =
        if (!here) local() else remotos.getOrPut(name) { RemotePrefs(ctx.applicationContext ?: ctx, name, local) }

    /** Contexto cujo getSharedPreferences passa por [prefs]. Fora do `:player` devolve o próprio [base]. */
    fun wrap(base: Context, here: Boolean = inPlayer): Context = if (!here) base else Wrapped(base)

    /** Antes de cada tela do `:player`: joga fora as cópias (o processo principal pode ter reiniciado e perdido os avisos). */
    fun refresh(here: Boolean = inPlayer) {
        if (!here) return
        for (p in remotos.values) (p as? RemotePrefs)?.invalidate()
    }

    /** Só para testes: esquece as instâncias remotas. */
    internal fun resetForTest() = remotos.clear()

    private class Wrapped(base: Context) : ContextWrapper(base) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            val n = name ?: "null"
            return prefs(baseContext, n, { baseContext.getSharedPreferences(name, mode) }, here = true)
        }
        override fun getApplicationContext(): Context = baseContext.applicationContext
    }

    // ---- o que vai e volta entre os processos (Bundle). Puro, testado.

    const val CALL_ALL = "all"
    const val CALL_EDIT = "edit"
    const val KEY_VALUES = "v"
    const val KEY_SETS = "s"
    const val KEY_BIG = "big"
    const val KEY_CLEAR = "clear"
    const val KEY_REMOVE = "rm"

    /** Mapa de preferências -> Bundle (conjuntos de texto vão à parte, como lista). */
    fun toBundle(map: Map<String, *>): Bundle {
        val v = Bundle(); val s = Bundle()
        for ((k, x) in map) when (x) {
            is Boolean -> v.putBoolean(k, x)
            is Int -> v.putInt(k, x)
            is Long -> v.putLong(k, x)
            is Float -> v.putFloat(k, x)
            is String -> v.putString(k, x)
            is Set<*> -> s.putStringArrayList(k, ArrayList(x.map { it.toString() }))
        }
        return Bundle().apply { putBundle(KEY_VALUES, v); putBundle(KEY_SETS, s) }
    }

    @Suppress("DEPRECATION")
    fun fromBundle(b: Bundle?): Map<String, Any> {
        val m = HashMap<String, Any>()
        val v = b?.getBundle(KEY_VALUES); val s = b?.getBundle(KEY_SETS)
        v?.keySet()?.forEach { k -> v.get(k)?.let { m[k] = it } }
        s?.keySet()?.forEach { k -> s.getStringArrayList(k)?.let { m[k] = HashSet(it) } }
        return m
    }

    /** Aplica num editor local o pedido de gravação que veio do `:player`. */
    fun applyEdit(e: SharedPreferences.Editor, b: Bundle): SharedPreferences.Editor {
        if (b.getBoolean(KEY_CLEAR)) e.clear()
        b.getStringArrayList(KEY_REMOVE)?.forEach { e.remove(it) }
        for ((k, x) in fromBundle(b)) when (x) {
            is Boolean -> e.putBoolean(k, x)
            is Int -> e.putInt(k, x)
            is Long -> e.putLong(k, x)
            is Float -> e.putFloat(k, x)
            is String -> e.putString(k, x)
            is Set<*> -> e.putStringSet(k, x.map { it.toString() }.toHashSet())
        }
        return e
    }
}

/**
 * SharedPreferences do `:player` que lê e grava pelo [PrefsProvider] do processo principal (ver [CrossProcessPrefs]).
 * Leitura: cópia em memória, buscada de novo só depois de um aviso de mudança. Gravação: a cópia muda na hora (quem
 * gravou lê o valor novo em seguida) e o pedido vai ao dono numa fila só (apply) ou na hora (commit).
 */
class RemotePrefs(private val ctx: Context, private val name: String, private val local: () -> SharedPreferences) :
    SharedPreferences {

    private val lock = Any()
    @Volatile private var cache: HashMap<String, Any>? = null
    @Volatile private var big: SharedPreferences? = null
    private val ouvintes = java.util.WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Boolean>()
    private val principal = Handler(Looper.getMainLooper())
    private val observador = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) = aoMudarNoDono()
    }

    init {
        runCatching { ctx.contentResolver.registerContentObserver(CrossProcessPrefs.uri(ctx, name), false, observador) }
    }

    fun invalidate() { synchronized(lock) { cache = null } }

    private fun chamar(metodo: String, extras: Bundle?): Bundle? =
        ctx.contentResolver.call(Uri.parse("content://${CrossProcessPrefs.authority(ctx)}"), metodo, name, extras)

    /** A cópia atual; busca no dono se foi jogada fora. Arquivo grande fica local ([big]). */
    private fun dados(): Map<String, Any> {
        big?.let { return it.all.filterValues { v -> v != null }.mapValues { it.value!! } }
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            esperarFila()   // uma gravação deste processo ainda a caminho: buscar antes dela leria o valor velho
            val r = runCatching { chamar(CrossProcessPrefs.CALL_ALL, null) }.getOrNull()
            if (r == null || r.getBoolean(CrossProcessPrefs.KEY_BIG)) {
                // Dono não respondeu ou é cache grande: usa o arquivo local (o comportamento de antes).
                val l = local(); if (r != null) big = l
                return l.all.filterValues { v -> v != null }.mapValues { it.value!! }
            }
            return HashMap(CrossProcessPrefs.fromBundle(r)).also { cache = it }
        }
    }

    private fun esperarFila() {
        if (pendentes.get() == 0 || Thread.currentThread().name == FILA) return
        runCatching { fila.submit { }.get(2, java.util.concurrent.TimeUnit.SECONDS) }
    }

    private fun aoMudarNoDono() {
        val antes = synchronized(lock) { cache.also { cache = null } } ?: return
        val ls = synchronized(ouvintes) { ouvintes.keys.toList() }
        if (ls.isEmpty()) return
        val depois = dados()
        val mudou = (antes.keys + depois.keys).filter { antes[it] != depois[it] }
        if (mudou.isNotEmpty()) principal.post { for (k in mudou) for (l in ls) l.onSharedPreferenceChanged(this, k) }
    }

    override fun getAll(): MutableMap<String, *> = HashMap(dados())
    override fun getString(key: String?, defValue: String?): String? = dados()[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (dados()[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = dados()[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = dados()[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = dados()[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = dados()[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = dados().containsKey(key)

    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {
        big?.let { it.registerOnSharedPreferenceChangeListener(l); return }
        if (l != null) synchronized(ouvintes) { ouvintes[l] = true }
    }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {
        big?.unregisterOnSharedPreferenceChangeListener(l)
        if (l != null) synchronized(ouvintes) { ouvintes.remove(l) }
    }

    override fun edit(): SharedPreferences.Editor {
        dados()   // decide se é grande antes de editar
        big?.let { return it.edit() }
        return Editor()
    }

    private inner class Editor : SharedPreferences.Editor {
        private val puts = HashMap<String, Any>()
        private val removes = LinkedHashSet<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { if (value == null) remove(key) else { puts[key] = value; removes.remove(key) } }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { if (values == null) remove(key) else { puts[key] = HashSet(values); removes.remove(key) } }
        override fun putInt(key: String, value: Int) = apply { puts[key] = value; removes.remove(key) }
        override fun putLong(key: String, value: Long) = apply { puts[key] = value; removes.remove(key) }
        override fun putFloat(key: String, value: Float) = apply { puts[key] = value; removes.remove(key) }
        override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value; removes.remove(key) }
        override fun remove(key: String) = apply { removes.add(key); puts.remove(key) }
        override fun clear() = apply { clear = true }

        private fun pedido(): Bundle = CrossProcessPrefs.toBundle(puts).apply {
            putBoolean(CrossProcessPrefs.KEY_CLEAR, clear)
            putStringArrayList(CrossProcessPrefs.KEY_REMOVE, ArrayList(removes))
        }

        /** A cópia local muda na hora, como no SharedPreferences do Android. */
        private fun aplicarNaCopia(): List<String> {
            val mudou = ArrayList<String>()
            synchronized(lock) {
                val c = cache ?: return@synchronized
                if (clear) { mudou += c.keys; c.clear() }
                for (k in removes) if (c.remove(k) != null) mudou += k
                for ((k, v) in puts) if (c.put(k, v) != v) mudou += k
            }
            val ls = synchronized(ouvintes) { ouvintes.keys.toList() }
            if (ls.isNotEmpty() && mudou.isNotEmpty()) principal.post { for (k in mudou.distinct()) for (l in ls) l.onSharedPreferenceChanged(this@RemotePrefs, k) }
            return mudou
        }

        override fun commit(): Boolean {
            aplicarNaCopia()
            esperarFila()   // na ordem: os apply() de antes chegam primeiro
            val r = runCatching { chamar(CrossProcessPrefs.CALL_EDIT, pedido()) }.getOrNull()
            if (r == null) invalidate()
            return r?.getBoolean("ok") == true
        }

        override fun apply() {
            aplicarNaCopia()
            val b = pedido()
            pendentes.incrementAndGet()
            fila.execute {
                try { if (runCatching { chamar(CrossProcessPrefs.CALL_EDIT, b) }.getOrNull() == null) invalidate() }
                finally { pendentes.decrementAndGet() }
            }
        }
    }

    companion object {
        /** Uma fila só para as gravações em segundo plano: chegam ao dono na ordem em que foram feitas. */
        private const val FILA = "prefs-remotas"
        private val fila = Executors.newSingleThreadExecutor { r -> Thread(r, FILA).apply { isDaemon = true } }
        private val pendentes = java.util.concurrent.atomic.AtomicInteger()
    }
}
