package br.paulo.tvlauncher.home

import android.content.Context
import br.paulo.tvlauncher.settings.Extras

/**
 * As 3 formas da tela "Todos os apps" (22/11/2026, Paulo: "podemos ficar com as 3... o user escolher o que agradar
 * mais"; "padrão tem que ser a A porque é mais familiar"). Estudo: https://claude.ai/artifact/1pTVkTFBnMWMwrnuoA1KsZ
 *
 * SHELVES (A, padrão) = prateleiras por categoria na largura toda, o app em foco numa linha embaixo.
 * FOLDERS (B) = Favoritos e Pastas no topo, prateleiras embaixo, painel do app com os canais dele à direita.
 * LIBRARY (C) = trilho de filtros à esquerda (Tudo, Favoritos, Pastas, Mais usados, Novos...) e a grade à direita.
 * Trocável no Ajuste fino (`all_apps_layout`). A ordem dos apps dentro das prateleiras: `all_apps_sort` (0 uso, 1 A–Z).
 */
object AllAppsLayout {
    const val KEY = "all_apps_layout"
    const val KEY_SORT = "all_apps_sort"

    enum class Mode { SHELVES, FOLDERS, LIBRARY }

    /** Filtros do trilho da Biblioteca, na ordem em que aparecem. */
    enum class Filter(val id: String) { ALL("all"), FAVORITES("favorites"), FOLDERS("folders"), MOST_USED("most_used"), NEW("new"), FROZEN("frozen"), HIDDEN("hidden") }

    fun mode(ctx: Context): Mode = Mode.entries.getOrElse(Extras.numero(ctx, KEY, Mode.SHELVES.ordinal)) { Mode.SHELVES }
    fun sort(ctx: Context): WorldOptions.Sort = if (Extras.numero(ctx, KEY_SORT, 0) == 1) WorldOptions.Sort.AZ else WorldOptions.Sort.USAGE
    fun setSort(ctx: Context, s: WorldOptions.Sort) = Extras.definirNumero(ctx, KEY_SORT, if (s == WorldOptions.Sort.AZ) 1 else 0)

    /** "Novos" = instalados nos últimos [NEW_DAYS] dias, do mais novo para o mais velho. Pura (teste na JVM). */
    const val NEW_DAYS = 30
    fun newest(installed: Map<String, Long>, now: Long): List<String> =
        installed.filter { now - it.value <= NEW_DAYS * 86_400_000L }.entries.sortedByDescending { it.value }.map { it.key }
}
