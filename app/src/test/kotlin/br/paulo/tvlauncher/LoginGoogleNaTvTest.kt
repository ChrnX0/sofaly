package br.paulo.tvlauncher

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Toda mensagem de falha do LoginGoogle (em português no código) tem tradução na TV (LoginGoogle.textoNaTv): a tela
 * da foto do perfil e a do Drive mostravam o motivo em português numa TV em inglês.
 */
class LoginGoogleNaTvTest {
    @Test fun todaMensagemTemTraducaoNaTv() {
        val src = CodeSource.file("LoginGoogle.kt").readText()
        val tabela = src.substringAfter("private val NA_TV = mapOf(").substringBefore("fun textoNaTv")
        val fixas = Regex("""mensagem = "([^"$]+)"""").findAll(src).map { it.groupValues[1] } +
            Regex("""IllegalStateException\("([^"$]+)"\)""").findAll(src).map { it.groupValues[1] }
        val faltam = fixas.filter { "\"$it\"" !in tabela }.toList()
        assertTrue("sem tradução na TV: $faltam", faltam.isEmpty())
    }
}
