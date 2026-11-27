package br.paulo.tvlauncher.image

/**
 * Soma das imagens vivas por origem (refatoração G, etapa de imagens, 27/11/2026). Lógica pura: o [ImageLedger] do app
 * junta os bitmaps vivos e chama [group]; a régua `EXTRAS --es o leveza` mostra o resultado em "imagens".
 * Por quê: na TV de Release não dá para tirar um heap dump; sem saber quem segura os ~58 MB do heap nativo, cortar
 * imagem seria chute.
 */
object ImageTally {
    /** Um bitmap vivo: de onde veio ([origin], ex. "home/Fundos"), tamanho em pixels e bytes que ocupa. */
    data class Entry(val origin: String, val width: Int, val height: Int, val bytes: Long)

    /** Uma linha do relatório: quantos bitmaps vivos da [origin], quantos bytes ao todo e o maior deles. */
    data class Line(val origin: String, val count: Int, val bytes: Long, val largestWidth: Int, val largestHeight: Int)

    /** Agrupa por origem, maior soma primeiro (empate: nome, para o relatório sair sempre igual). */
    fun group(entries: List<Entry>): List<Line> =
        entries.groupBy { it.origin }.map { (origin, list) ->
            val largest = list.maxByOrNull { it.bytes }!!
            Line(origin, list.size, list.sumOf { it.bytes }, largest.width, largest.height)
        }.sortedWith(compareByDescending<Line> { it.bytes }.thenBy { it.origin })

    /** Bytes em MB com uma casa (a mesma régua do resto da leveza). */
    fun mb(bytes: Long): Double = Math.round(bytes / 1048576.0 * 10) / 10.0

    /** "home/Fundos" a partir do caminho do arquivo-fonte: a pasta da área + o nome, sem ".kt". */
    fun originOf(sourcePath: String): String {
        val parts = sourcePath.replace('\\', '/').removeSuffix(".kt").split('/').filter { it.isNotEmpty() }
        val i = parts.lastIndexOf("tvlauncher")
        return if (i >= 0 && i < parts.size - 1) parts.drop(i + 1).joinToString("/") else parts.lastOrNull().orEmpty()
    }
}
