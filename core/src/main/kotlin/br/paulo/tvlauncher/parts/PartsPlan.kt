package br.paulo.tvlauncher.parts

import org.json.JSONObject

/**
 * Lógica pura das partes sob demanda (refatoração G, 27/11/2026). O Release publica, além do APK cheio de sempre, os
 * APKs da base e das partes e um `parts.json` que os descreve (feito pelo release.yml a partir de app/build/outputs/parts):
 *
 * ```
 * {"versionCode": 476305, "files": [
 *   {"name": "part-base.apk", "split": "base", "abi": "", "sha256": "…", "size": 25067008},
 *   {"name": "part-base-armeabi_v7a.apk", "split": "base", "abi": "armeabi-v7a", "sha256": "…", "size": 5337088}, …]}
 * ```
 *
 * "abi" vazio = o APK que vale para as duas arquiteturas (o "master" do bundletool: igual byte a byte nas duas).
 * Os nomes começam por "part-" de propósito: o Atualizador antigo procura nomes exatos (sofaly-launcher*.apk) e nunca
 * pega um destes.
 */
object PartsPlan {
    const val INDEX = "parts.json"
    const val BASE = "base"
    val KNOWN = setOf(BASE, "translate", "tunnel")
    val ABIS = setOf("armeabi-v7a", "arm64-v8a")

    data class File(val name: String, val split: String, val abi: String, val sha256: String, val size: Long)
    data class Index(val versionCode: Long, val files: List<File>)

    private val NOME = Regex("part-[a-z0-9_-]+\\.apk")
    private val SHA = Regex("[0-9a-f]{64}")
    /** Nenhum APK nosso passa disso (a base inteira tem ~24 MB); protege o disco de um índice estranho. */
    const val MAX_SIZE = 96L * 1024 * 1024

    /** Lê o índice; null se qualquer entrada for estranha (nome, parte, arquitetura, sha, tamanho) ou repetida. */
    fun parse(text: String): Index? = try {
        val o = JSONObject(text)
        val code = o.getLong("versionCode")
        val arr = o.getJSONArray("files")
        val files = (0 until arr.length()).map { i ->
            val f = arr.getJSONObject(i)
            File(f.getString("name"), f.getString("split"), f.optString("abi", ""), f.getString("sha256").lowercase(), f.getLong("size"))
        }
        val ok = code > 0 && files.isNotEmpty() && files.all {
            NOME.matches(it.name) && it.split in KNOWN && (it.abi.isEmpty() || it.abi in ABIS) && SHA.matches(it.sha256) &&
                it.size in 1..MAX_SIZE
        } && files.map { it.name }.toSet().size == files.size && files.map { it.split to it.abi }.toSet().size == files.size
        if (ok) Index(code, files) else null
    } catch (_: Exception) { null }

    /**
     * Os arquivos para instalar [parts] (além da base, que vai sempre) numa TV de arquitetura [abi]: para cada uma, o
     * APK comum e o da arquitetura. Null se faltar algum (Release incompleto): aí vale o APK cheio.
     */
    fun files(index: Index, abi: String, parts: Set<String>): List<File>? {
        if (abi !in ABIS) return null
        val wanted = listOf(BASE) + parts.filter { it != BASE }.sorted()
        if (wanted.any { it !in KNOWN }) return null
        val out = ArrayList<File>()
        for (split in wanted) {
            out += index.files.firstOrNull { it.split == split && it.abi.isEmpty() } ?: return null
            out += index.files.firstOrNull { it.split == split && it.abi == abi } ?: return null
        }
        return out
    }

    /**
     * Quais partes uma atualização leva. Instalada como partes: as que já estão (o Android apagaria as que não vierem)
     * mais as em uso. No APK cheio: só as em uso; as outras vêm sob demanda quando alguém liga a função.
     * [installedSplits] = `splitNames` do PackageManager (inclui os de arquitetura, como "config.arm64_v8a").
     */
    fun keep(installedSplits: Collection<String>, inUse: Set<String>): Set<String> =
        (installedSplits.filter { it in KNOWN && it != BASE } + inUse.filter { it in KNOWN && it != BASE }).toSortedSet()

    /** Arquitetura do processo, no nome que o bundletool usa nas pastas ("armeabi-v7a" / "arm64-v8a"). */
    fun abi(is64: Boolean): String = if (is64) "arm64-v8a" else "armeabi-v7a"
}
