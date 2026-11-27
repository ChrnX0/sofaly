package br.paulo.onetv.celular

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Os textos do app que ensinam onde fica o QR na TV ("Configurações › Casa e rede › Acesso remoto › Entrar pelo
 * celular") têm de ser os nomes que a TV mostra, em cada língua. Na v475825 o app dizia "Configurações › Acesso remoto",
 * mas a aba fica dentro de "Casa e rede" e o Paulo não achou (27/11/2026). Este teste monta o caminho com os textos da
 * própria TV (app/src/main/res) e exige que ele apareça nos três textos do celular.
 */
class CaminhoNaTvTest {
    private val raiz = listOf(File(".."), File(".")).first { File(it, "app/src/main/res/values").isDirectory }
    private val textosDoCelular = listOf("cel_vazio", "cel_achada_explica", "cel_qr_invalido")
    private val passosNaTv = listOf("cfg_titulo", "cfg3_porta_casa_rede", "cl_aba_remoto", "cl_entrar_celular")

    /** Todos os <string> de uma pasta values* (nome -> texto), lendo cada .xml dela. */
    private fun textos(pasta: File): Map<String, String> {
        val f = DocumentBuilderFactory.newInstance()
        return pasta.listFiles { a -> a.name.endsWith(".xml") }.orEmpty().flatMap { arq ->
            val doc = runCatching { f.newDocumentBuilder().parse(arq) }.getOrNull() ?: return@flatMap emptyList()
            val nos = doc.getElementsByTagName("string")
            (0 until nos.length).map { nos.item(it) }.map {
                it.attributes.getNamedItem("name").nodeValue to it.textContent.replace("\\'", "'")
            }
        }.toMap()
    }

    @Test fun caminhoIgualAoDaTvEmCadaLingua() {
        val linguas = File(raiz, "celular/src/main/res").listFiles { d -> d.isDirectory && d.name.startsWith("values") }
            .orEmpty().filter { File(it, "strings.xml").isFile }
        assertTrue("achei poucas línguas no celular: ${linguas.map { it.name }}", linguas.size >= 10)
        for (pasta in linguas) {
            val tv = textos(File(raiz, "app/src/main/res/${pasta.name}"))
            val caminho = passosNaTv.map { tv[it] ?: error("a TV não tem $it em ${pasta.name}") }.joinToString(" › ")
            val cel = textos(pasta)
            for (nome in textosDoCelular) {
                val texto = cel[nome] ?: error("o celular não tem $nome em ${pasta.name}")
                assertTrue("${pasta.name}/$nome deveria dizer \"$caminho\", diz \"$texto\"", caminho in texto.toString())
            }
        }
    }
}
