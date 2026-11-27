package br.paulo.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * O APK final passa pelo R8, que renomeia classes e métodos nossos. Procurar uma classe ou método NOSSO pelo nome
 * (Class.forName("br.paulo.tvlauncher...") + methods.first { it.name == ... }) funciona nos testes e quebra só no APK
 * (revisor, 25/11/2026: os eventos do Celular e TV sumiam). Chamada direta, ou uma regra -keep no proguard-rules.pro.
 */
class ReflexaoPropriaTest {
    @Test fun nenhumMetodoNossoPorNome() {
        val ruim = Regex("""Class\.forName\("br\.paulo\.tvlauncher\.[^"]*"\)[^\n]*|\.methods\.first\s*\{\s*it\.name\s*==""")
        val achados = CodeSource.all().flatMap { f ->
            f.readLines().withIndex().filter { ruim.containsMatchIn(it.value) }.map { "${f.name}:${it.index + 1}" }
        }.toList()
        assertEquals("reflexão em código nosso (o R8 renomeia)", emptyList<String>(), achados)
    }
}
