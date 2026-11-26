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

    /**
     * USAGE = como sempre foi (mais usados primeiro); AZ = pelo nome; MANUAL = a ordem que a pessoa arrumou;
     * LAST = o aberto por último primeiro; RANDOM = embaralhado (a mesma ordem o dia todo, para o foco não pular).
     * As 5 ordens do Projectivy (26/11/2026: Personalizada, Alfabética, Mais usados, Últimos usados, Aleatória).
     */
    enum class Sort(val id: String) { USAGE("usage"), AZ("az"), MANUAL("manual"), LAST("last"), RANDOM("random") }

    /** O que se guarda de um mundo. [name] null = o nome de fábrica, no idioma da TV. */
    data class Options(
        val sort: Sort = Sort.USAGE,
        val manual: List<String> = emptyList(),
        val name: String? = null,
        val hidden: Boolean = false,
        /** Só o ícone na linha da cruz, sem o nome embaixo (Paulo, 22/11/2026). O nome segue na pílula do topo com o foco. */
        val iconOnly: Boolean = false,
        /** Jeito dos cards deste mundo na barra única (número de [StripHome.Format]); null = segue o do Ajuste fino. */
        val cards: Int? = null,
        /** Tamanho dos cards deste mundo nas Fileiras (número de [RowsHome.Size]); null = segue o do Ajuste fino. */
        val size: Int? = null,
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
                iconOnly = o.optBoolean("iconOnly", false),
                cards = if (o.has("cards")) o.optInt("cards", -1).takeIf { it >= 0 } else null,
                size = if (o.has("size")) o.optInt("size", -1).takeIf { it >= 0 } else null,
            )
        }.getOrDefault(Options())   // JSON estragado = como era, nunca derruba a HOME

        fun write(o: Options): String = JSONObject().apply {
            put("sort", o.sort.id)
            if (o.manual.isNotEmpty()) put("manual", JSONArray(o.manual))
            o.name?.let { put("name", it) }
            if (o.hidden) put("hidden", true)
            if (o.iconOnly) put("iconOnly", true)
            o.cards?.let { put("cards", it) }
            o.size?.let { put("size", it) }
        }.toString()

        /**
         * Arruma os [ids] de um mundo que já chegam na ordem de uso ([CruzLogica.ordenarApps], com os fixados no topo).
         * USAGE: não mexe. AZ: pelo nome ([names], sem acento nem caixa; sem nome, pelo id), fixados continuam no topo.
         * MANUAL: primeiro os da ordem à mão que ainda existem, depois os novos na ordem de uso (app instalado depois
         * aparece no fim, não some).
         */
        fun arrange(ids: List<String>, sort: Sort, names: Map<String, String> = emptyMap(),
                    manual: List<String> = emptyList(), pinned: Collection<String> = emptyList(),
                    lastUsed: Map<String, Long> = emptyMap(), seed: Long = 0L): List<String> = when (sort) {
            Sort.USAGE -> ids
            Sort.LAST -> {
                // fixados no topo; depois do aberto mais recente ao mais antigo; nunca aberto fica na ordem de uso, no fim
                val (top, rest) = ids.partition { it in pinned }
                top + rest.withIndex().sortedWith(compareByDescending<IndexedValue<String>> { lastUsed[it.value] ?: 0L }.thenBy { it.index }).map { it.value }
            }
            Sort.RANDOM -> {
                val (top, rest) = ids.partition { it in pinned }
                top + rest.sorted().shuffled(java.util.Random(seed))
            }
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
