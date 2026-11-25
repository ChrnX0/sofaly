package br.paulo.tvlauncher.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import br.paulo.tvlauncher.Prefs

/**
 * Língua do app (24/11/2026): por padrão a mesma da TV; em Sistema › Língua dá para escolher uma das 27 do app.
 * Usa a língua por app do Android (LocaleManager, Android 13+): o sistema guarda a escolha, recria as telas e o
 * Locale.getDefault() do processo já vem na língua escolhida. Abaixo do Android 13 a linha não aparece.
 *
 * Modo nerd escondido: escolher a língua que já está marcada 3 vezes seguidas libera o klingon (tlhIngan Hol, ~40
 * textos da tela inicial e das Configurações em res/values-b+tlh; o resto cai no inglês).
 */
object AppLanguage {
    data class Option(val tag: String, val name: String)

    /** Nome de cada língua nela mesma (não se traduz). A ordem é a da lista na TV. */
    val OPTIONS = listOf(
        Option("en", "English"), Option("pt-BR", "Português (Brasil)"), Option("pt-PT", "Português (Portugal)"),
        Option("es", "Español"), Option("fr", "Français"), Option("de", "Deutsch"), Option("it", "Italiano"),
        Option("nl", "Nederlands"), Option("sv", "Svenska"), Option("da", "Dansk"), Option("nb", "Norsk bokmål"),
        Option("fi", "Suomi"), Option("pl", "Polski"), Option("cs", "Čeština"), Option("hu", "Magyar"),
        Option("ro", "Română"), Option("el", "Ελληνικά"), Option("ru", "Русский"), Option("uk", "Українська"),
        Option("tr", "Türkçe"), Option("ar", "العربية"), Option("hi", "हिन्दी"), Option("id", "Bahasa Indonesia"),
        Option("vi", "Tiếng Việt"), Option("th", "ไทย"), Option("ja", "日本語"), Option("ko", "한국어"),
        Option("zh-CN", "简体中文"),
    )
    val KLINGON = Option("tlh", "tlhIngan Hol")

    private const val KEY_KLINGON = "app_language_klingon"
    private const val KEY_REPEAT = "app_language_repeat"
    const val REPEATS_TO_UNLOCK = 3

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 33

    /** Tag escolhida no app, ou "" = a da TV. */
    fun current(ctx: Context): String {
        if (Build.VERSION.SDK_INT < 33) return ""
        val l = ctx.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return ""
        return if (l.isEmpty) "" else l[0].toLanguageTag()
    }

    /** "" volta para a língua da TV. O sistema recria as telas abertas. */
    fun set(ctx: Context, tag: String) {
        if (Build.VERSION.SDK_INT < 33) return
        ctx.getSystemService(LocaleManager::class.java)?.applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }

    fun klingonUnlocked(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_KLINGON, false)

    /** As opções da lista: as 27, mais o klingon depois de liberado. */
    fun options(ctx: Context): List<Option> = if (klingonUnlocked(ctx)) OPTIONS + KLINGON else OPTIONS

    /**
     * Conta quantas vezes seguidas a pessoa escolheu a língua que já estava marcada. Devolve true na vez que libera o
     * klingon. Escolher outra língua zera a conta.
     */
    fun countRepeat(ctx: Context, pickedCurrent: Boolean): Boolean {
        val p = prefs(ctx)
        if (!pickedCurrent || klingonUnlocked(ctx)) { p.edit().putInt(KEY_REPEAT, 0).apply(); return false }
        val n = nextRepeat(p.getInt(KEY_REPEAT, 0))
        if (n >= REPEATS_TO_UNLOCK) { p.edit().putBoolean(KEY_KLINGON, true).putInt(KEY_REPEAT, 0).apply(); return true }
        p.edit().putInt(KEY_REPEAT, n).apply()
        return false
    }

    internal fun nextRepeat(n: Int) = (n + 1).coerceAtMost(REPEATS_TO_UNLOCK)

    /** Mesma língua? "pt-BR" casa com "pt-BR"; "zh-CN" com "zh-Hans-CN" não importa aqui (a lista só grava as nossas). */
    fun sameTag(a: String, b: String) = a.equals(b, ignoreCase = true)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(Prefs.LAUNCHER, Context.MODE_PRIVATE)
}
