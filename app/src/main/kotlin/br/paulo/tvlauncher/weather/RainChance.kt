package br.paulo.tvlauncher.weather

import android.content.Context
import br.paulo.tvlauncher.Prefs
import br.paulo.tvlauncher.resources.R

/**
 * Como a chance de chuva aparece (24/11/2026, Paulo: "palavras e/ou %"): só o número ("37%"), só a palavra ("Média")
 * ou os dois ("Média 37%", numa linha só: as células da Previsão não têm altura para duas). Vale na Previsão, no descanso e na pílula da HOME; a voz continua falando o número.
 * Fica nas prefs "clima" (entra no backup e no Desfazer como os outros ajustes do clima).
 */
object RainChance {
    enum class Modo { NUMERO, PALAVRA, AMBOS }

    private const val CHAVE = "chance_modo"

    fun modo(ctx: Context): Modo = ctx.getSharedPreferences(Prefs.CLIMA, Context.MODE_PRIVATE).getString(CHAVE, null)
        ?.let { s -> Modo.entries.firstOrNull { it.name == s } } ?: Modo.NUMERO

    fun definir(ctx: Context, m: Modo) =
        ctx.getSharedPreferences(Prefs.CLIMA, Context.MODE_PRIVATE).edit().putString(CHAVE, m.name).apply()

    /** O próximo da lista (a linha das Configurações anda em roda). */
    fun proximo(m: Modo) = Modo.entries[(m.ordinal + 1) % Modo.entries.size]

    /** Faixa da palavra: 0 Baixa (< 34), 1 Média (34-66), 2 Alta (67+): terços, palavras curtas que cabem na célula da hora. */
    fun faixa(pct: Int): Int = when {
        pct < 34 -> 0
        pct < 67 -> 1
        else -> 2
    }

    private val PALAVRAS = intArrayOf(R.string.rc_baixa, R.string.rc_media, R.string.rc_alta)

    fun palavra(ctx: Context, pct: Int): String = ctx.getString(PALAVRAS[faixa(pct)])

    /** O texto da chance no modo escolhido. */
    fun texto(ctx: Context, pct: Int, modo: Modo = modo(ctx)): String = when (modo) {
        Modo.NUMERO -> "$pct%"
        Modo.PALAVRA -> palavra(ctx, pct)
        Modo.AMBOS -> "${palavra(ctx, pct)} $pct%"
    }

    fun nomeModo(ctx: Context, m: Modo): String = ctx.getString(when (m) {
        Modo.NUMERO -> R.string.rc_modo_numero
        Modo.PALAVRA -> R.string.rc_modo_palavra
        Modo.AMBOS -> R.string.rc_modo_ambos
    })
}
