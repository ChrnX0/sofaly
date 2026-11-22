package br.paulo.tvlauncher.home

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Opções de cada mundo da cruz (22/11/2026, pedido do Paulo: "um menu de opções para os mundos... ordenar
 * alfabeticamente ou por uso, trocar ícone").
 *
 * Porquê: a ordem dos apps era só uma (uso), o desenho e o nome do mundo eram fixos.
 * Para quê: cada pessoa arruma o mundo do jeito dela; a cruz e "Todos os apps" leem o MESMO lugar.
 * Como: prefs "world_options", um JSON por mundo (id de [CruzLogica.Categoria]): ordem ([Sort]), ordem à mão,
 * nome trocado e se o mundo está escondido. O ícone do mundo já se troca pelo "Trocar ícone" item a item
 * (MainActivity.trocarIconeSofaly); o menu do mundo só leva até ele. Sem nada guardado, tudo fica como era (ordem por uso).
 * A parte que decide ([Rules]) é pura e tem teste na JVM; este objeto só guarda e lê.
 */
object WorldOptions {
    private const val PREFS = "world_options"

    /** USAGE = como sempre foi (mais aberto primeiro); AZ = pelo nome; MANUAL = a ordem que a pessoa arrumou. */
    enum class Sort(val id: String) { USAGE("usage"), AZ("az"), MANUAL("manual") }

    /** O que se guarda de um mundo. [name] null = o nome de fábrica, no idioma da TV. */
    data class Options(
        val sort: Sort = Sort.USAGE,
        val manual: List<String> = emptyList(),
        val name: String? = null,
        val hidden: Boolean = false,
    )

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun of(ctx: Context, world: CruzLogica.Categoria): Options = Rules.read(p(ctx).getString(world.id, null))

    fun save(ctx: Context, world: CruzLogica.Categoria, o: Options) {
        p(ctx).edit().apply { if (o == Options()) remove(world.id) else putString(world.id, Rules.write(o)) }.apply()
    }

    fun update(ctx: Context, world: CruzLogica.Categoria, f: (Options) -> Options) = save(ctx, world, f(of(ctx, world)))

    /** Muda quando qualquer opção muda (a HOME redesenha a cruz). */
    fun signature(ctx: Context): Int = p(ctx).all.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }.hashCode()

    object Rules {
        fun read(json: String?): Options = runCatching {
            val o = JSONObject(json ?: return Options())
            val m = o.optJSONArray("manual")
            Options(
                sort = Sort.entries.firstOrNull { it.id == o.optString("sort") } ?: Sort.USAGE,
                manual = if (m == null) emptyList() else (0 until m.length()).map { m.getString(it) },
                name = o.optString("name").trim().takeIf { it.isNotEmpty() },
                hidden = o.optBoolean("hidden", false),
            )
        }.getOrDefault(Options())   // JSON estragado = como era, nunca derruba a HOME

        fun write(o: Options): String = JSONObject().apply {
            put("sort", o.sort.id)
            if (o.manual.isNotEmpty()) put("manual", JSONArray(o.manual))
            o.name?.let { put("name", it) }
            if (o.hidden) put("hidden", true)
        }.toString()

        /**
         * Arruma os [ids] de um mundo que já chegam na ordem de uso ([CruzLogica.ordenarApps], com os fixados no topo).
         * USAGE: não mexe. AZ: pelo nome ([names], sem acento nem caixa; sem nome, pelo id), fixados continuam no topo.
         * MANUAL: primeiro os da ordem à mão que ainda existem, depois os novos na ordem de uso (app instalado depois
         * aparece no fim, não some).
         */
        fun arrange(ids: List<String>, sort: Sort, names: Map<String, String> = emptyMap(),
                    manual: List<String> = emptyList(), pinned: Collection<String> = emptyList()): List<String> = when (sort) {
            Sort.USAGE -> ids
            Sort.AZ -> {
                val (top, rest) = ids.partition { it in pinned }
                top + rest.sortedWith(compareBy<String>({ key(names[it] ?: it) }, { it }))
            }
            Sort.MANUAL -> {
                val present = ids.toSet()
                val first = manual.filter { it in present }.distinct()
                first + ids.filter { it !in first }
            }
        }

        /** Move [id] uma casa para cima (-1) ou para baixo (+1) e devolve a nova ordem à mão inteira. */
        fun move(current: List<String>, id: String, step: Int): List<String> {
            val l = current.toMutableList()
            val i = l.indexOf(id)
            if (i < 0) return l
            val j = (i + step).coerceIn(0, l.lastIndex)
            l.removeAt(i); l.add(j, id)
            return l
        }

        /**
         * Só Assistir, Ouvir e Jogar podem sumir: Ferramentas leva às Configurações e Aplicativos a "Todos os apps",
         * e sem eles não haveria caminho de volta pelo controle.
         */
        fun canHide(world: CruzLogica.Categoria) = world == CruzLogica.Categoria.ASSISTIR ||
            world == CruzLogica.Categoria.OUVIR || world == CruzLogica.Categoria.JOGAR

        fun visible(world: CruzLogica.Categoria, o: Options) = !(o.hidden && canHide(world))

        /** Chave de ordem alfabética: sem acento e sem caixa ("Órbita" junto de "orbita"). */
        fun key(s: String): String = java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").lowercase()
    }
}
