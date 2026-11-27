package br.paulo.tvlauncher

import java.io.File

/**
 * Os .kt do app para os testes que leem o código (desde 21/11/2026 em subpacotes por área: home/, player/, panel/...).
 * Desde a refatoração G (27/11/2026) o código fica em mais de um módulo (:app e :core): [roots] junta todos.
 */
object CodeSource {
    val root: File = listOf(File("src/main/kotlin/br/paulo/tvlauncher"), File("app/src/main/kotlin/br/paulo/tvlauncher")).first { it.isDirectory }
    /** Módulos além do :app com código do launcher (declarado antes de [roots]: ordem de inicialização). */
    private val MODULES = listOf("core", "features/translate")
    /** A pasta do código de cada módulo, a partir da pasta do :app ([root]). */
    val roots: List<File> = listOf(root) + MODULES.map { File(root, "../../../../../../../$it/src/main/kotlin/br/paulo/tvlauncher").normalize() }.filter { it.isDirectory }
    fun all(): Sequence<File> = roots.asSequence().flatMap { r -> r.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") } }
    fun file(name: String): File = all().firstOrNull { it.name == name } ?: error("$name não achado em ${roots.joinToString { it.path }}")
}
