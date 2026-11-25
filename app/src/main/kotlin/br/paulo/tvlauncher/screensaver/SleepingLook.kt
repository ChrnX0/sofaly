package br.paulo.tvlauncher.screensaver

import android.content.Context
import br.paulo.tvlauncher.Prefs

/**
 * O jeito dos widgets do descanso (25/11/2026, Paulo: "melhorou todas. deixa pro user poder escolher" e "deixa o user
 * poder escolher o local da tela onde deixar"). Três escolhas, todas nas Configurações › Tela e energia e no painel:
 *  - [Clock]: 7 jeitos de desenhar a hora (página aprovada https://claude.ai/artifact/XGViigHABRcGorMF9sdbN8; 5-7 vêm
 *    do Mobbin: TIDE, (Not Boring) Weather + Tempo da Apple, Oura + Pillow);
 *  - [Card]: o que fica embaixo: a faixa de sempre, o cartão vivo (A) ou a pilha (B);
 *  - [Place]: o canto da hora e do tempo; o cartão vai para a borda oposta (a pilha também para o lado oposto).
 * Prefs "launcher" (entram no backup), gravadas pelo NOME do enum: acrescentar um jeito não muda os já gravados.
 */
object SleepingLook {
    const val KEY_CLOCK = "sleeping_clock"
    const val KEY_CARD = "sleeping_card"
    const val KEY_PLACE = "sleeping_place"

    /** THIN = a hora fina de antes (padrão). */
    enum class Clock { THIN, SPLIT, STACKED, MINUTE, RING, RULER, PHRASE }
    /** LINE = a faixa de antes (padrão; o "vidro atrás" das Configurações vale só para ela). */
    enum class Card { LINE, CARD, STACK }
    enum class Place(val top: Boolean, val side: Int) {   // side: -1 começo, 0 centro, 1 fim
        TOP_START(true, -1), TOP_CENTER(true, 0), TOP_END(true, 1),
        BOTTOM_START(false, -1), BOTTOM_CENTER(false, 0), BOTTOM_END(false, 1)
    }

    fun clock(ctx: Context): Clock = ler(ctx, KEY_CLOCK, Clock.THIN)
    fun card(ctx: Context): Card = ler(ctx, KEY_CARD, Card.LINE)
    fun place(ctx: Context): Place = ler(ctx, KEY_PLACE, Place.TOP_START)
    fun set(ctx: Context, chave: String, nome: String) = prefs(ctx).edit().putString(chave, nome).apply()

    /** Valor gravado -> enum; nome desconhecido (ou de versão futura) cai no padrão. Pura sobre a string. */
    inline fun <reified E : Enum<E>> deNome(v: String?, padrao: E): E = enumValues<E>().firstOrNull { it.name.equals(v, true) } ?: padrao

    /** O lado do cartão: a faixa e o cartão ficam do mesmo lado da hora; a pilha vai para o outro (como na página B). */
    fun cardSide(card: Card, place: Place): Int = if (card == Card.STACK) (if (place.side == 0) 1 else -place.side) else place.side

    private inline fun <reified E : Enum<E>> ler(ctx: Context, chave: String, padrao: E): E = deNome(prefs(ctx).getString(chave, null), padrao)
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(Prefs.LAUNCHER, Context.MODE_PRIVATE)
}

/** Os nomes na tela de cada opção, na ordem dos enums (Configurações e testes). */
object SleepingLookNomes {
    val CLOCK = listOf(br.paulo.tvlauncher.R.string.slk_c_thin, br.paulo.tvlauncher.R.string.slk_c_split, br.paulo.tvlauncher.R.string.slk_c_stacked,
        br.paulo.tvlauncher.R.string.slk_c_minute, br.paulo.tvlauncher.R.string.slk_c_ring, br.paulo.tvlauncher.R.string.slk_c_ruler,
        br.paulo.tvlauncher.R.string.slk_c_phrase)
    val CARD = listOf(br.paulo.tvlauncher.R.string.slk_k_line, br.paulo.tvlauncher.R.string.slk_k_card, br.paulo.tvlauncher.R.string.slk_k_stack)
    val PLACE = listOf(br.paulo.tvlauncher.R.string.slk_p_ts, br.paulo.tvlauncher.R.string.slk_p_tc, br.paulo.tvlauncher.R.string.slk_p_te,
        br.paulo.tvlauncher.R.string.slk_p_bs, br.paulo.tvlauncher.R.string.slk_p_bc, br.paulo.tvlauncher.R.string.slk_p_be)
}
