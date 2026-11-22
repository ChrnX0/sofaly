package br.paulo.tvlauncher.home

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pastas de apps feitas pela pessoa (22/11/2026, base das 3 formas de "Todos os apps" e do item 3 do estudo do
 * Projectivy: "fileiras/pastas sem quebrar os 5 mundos").
 *
 * Porquê: as prateleiras automáticas (AppShelf) agrupam pelo tipo do app, não pelo jeito da casa ("Crianças", "Esportes").
 * Para quê: a pessoa cria, renomeia, apaga e enche pastas; uma pasta pode ir para a cruz como item de um mundo.
 * Como: prefs "app_folders", uma lista JSON em ordem. Um app pode estar em mais de uma pasta (como atalho; o app
 * continua no mundo dele). A parte que decide ([Rules]) é pura e tem teste na JVM.
 */
object AppFolders {
    private const val PREFS = "app_folders"
    private const val KEY = "list"

    /** [pinnedTo] = id do mundo onde a pasta aparece como item (null = só em "Todos os apps"). */
    data class Folder(val id: String, val name: String, val apps: List<String> = emptyList(), val pinnedTo: String? = null)

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Folder> = Rules.read(p(ctx).getString(KEY, null))
    fun save(ctx: Context, l: List<Folder>) { p(ctx).edit().putString(KEY, Rules.write(l)).apply() }
    fun change(ctx: Context, f: (List<Folder>) -> List<Folder>) = save(ctx, f(all(ctx)))
    fun signature(ctx: Context): Int = (p(ctx).getString(KEY, "") ?: "").hashCode()

    object Rules {
        const val MAX_NAME = 24

        fun read(json: String?): List<Folder> = runCatching {
            val a = JSONArray(json ?: return emptyList())
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val id = o.optString("id"); val name = o.optString("name").trim()
                if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
                val apps = o.optJSONArray("apps")
                Folder(id, name, if (apps == null) emptyList() else (0 until apps.length()).map { apps.getString(it) }.distinct(),
                    o.optString("pinned").takeIf { it.isNotEmpty() })
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())

        fun write(l: List<Folder>): String = JSONArray().apply {
            l.forEach { f -> put(JSONObject().put("id", f.id).put("name", f.name).put("apps", JSONArray(f.apps)).apply { f.pinnedTo?.let { put("pinned", it) } }) }
        }.toString()

        /** Nova pasta com nome limpo (até [MAX_NAME] letras) e id único ("f1", "f2"...). Nome vazio = nada muda. */
        fun create(l: List<Folder>, name: String, apps: List<String> = emptyList()): List<Folder> {
            val n = clean(name) ?: return l
            val used = l.map { it.id }.toSet()
            val id = generateSequence(1) { it + 1 }.map { "f$it" }.first { it !in used }
            return l + Folder(id, n, apps.distinct())
        }

        fun rename(l: List<Folder>, id: String, name: String): List<Folder> {
            val n = clean(name) ?: return l
            return l.map { if (it.id == id) it.copy(name = n) else it }
        }

        fun delete(l: List<Folder>, id: String) = l.filter { it.id != id }

        /** Põe ou tira [app] da pasta [id] (o mesmo botão no menu do app). */
        fun toggle(l: List<Folder>, id: String, app: String): List<Folder> = l.map { f ->
            if (f.id != id) f else f.copy(apps = if (app in f.apps) f.apps - app else f.apps + app)
        }

        fun pin(l: List<Folder>, id: String, world: String?) = l.map { if (it.id == id) it.copy(pinnedTo = world) else it }

        /** Tira das pastas os apps que não existem mais (desinstalados); a pasta fica, mesmo vazia. */
        fun prune(l: List<Folder>, installed: Set<String>) = l.map { it.copy(apps = it.apps.filter { a -> a in installed }) }

        /** Pastas que aparecem como item no mundo [world], na ordem da lista. */
        fun pinned(l: List<Folder>, world: String) = l.filter { it.pinnedTo == world }

        private fun clean(name: String): String? = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).trim().takeIf { it.isNotEmpty() }
    }
}
