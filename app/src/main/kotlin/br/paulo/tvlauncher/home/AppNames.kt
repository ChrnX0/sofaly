package br.paulo.tvlauncher.home

import android.content.Context

/**
 * Nome trocado de um app (22/11/2026, ideia do Projectivy: "renomear"). Ex.: "Prime Video" vira "Amazon".
 * Prefs "app_names": pacote -> nome. Vazio = volta ao nome do app. Lido onde o nome do app é montado
 * (cruz, "Todos os apps", busca), sempre por [name].
 */
object AppNames {
    private const val PREFS = "app_names"
    const val MAX = 24

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun custom(ctx: Context, pkg: String): String? = p(ctx).getString(pkg, null)?.takeIf { it.isNotBlank() }

    /** O nome que a tela mostra: o trocado, ou [original]. */
    fun name(ctx: Context, pkg: String, original: String): String = custom(ctx, pkg) ?: original

    fun set(ctx: Context, pkg: String, name: String?) {
        val n = clean(name)
        p(ctx).edit().apply { if (n == null) remove(pkg) else putString(pkg, n) }.apply()
    }

    fun signature(ctx: Context): Int = p(ctx).all.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }.hashCode()

    /** Limpa espaços e corta em [MAX]; vazio = null (volta ao original). Pura, testada na JVM. */
    fun clean(name: String?): String? = name?.trim()?.replace(Regex("\\s+"), " ")?.take(MAX)?.trim()?.takeIf { it.isNotEmpty() }
}
