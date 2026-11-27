package br.paulo.onetv.celular

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Câmera negada (Caça 27/11/2026): o leitor do ZXing pedia a câmera sozinho e, quando a pessoa negava, mostrava
 * "Barcode Scanner: the Android camera encountered a problem, restart the device" (em inglês e falso). Agora a
 * TvsActivity pede a câmera ANTES de abrir o leitor e explica a recusa com textos próprios, em cada língua.
 */
class CameraNegadaTest {
    private val raiz = listOf(File(".."), File(".")).first { File(it, "celular/src/main/res/values").isDirectory }
    private val fonte = File(raiz, "celular/src/main/kotlin/br/paulo/onetv/celular/TvsActivity.kt").readText()
    private val novos = listOf("cel_camera_negada", "cel_camera_bloqueada", "cel_abrir_ajustes")

    private fun corpo(fun_: String): String {
        val i = fonte.indexOf("fun $fun_(")
        assertTrue("TvsActivity sem $fun_", i >= 0)
        val fim = fonte.indexOf("\n    }\n", i)
        return fonte.substring(i, fim)
    }

    @Test fun pedeACameraAntesDeAbrirOLeitor() {
        val ler = corpo("lerQr")
        val checa = ler.indexOf("checkSelfPermission(Manifest.permission.CAMERA)")
        val abre = ler.indexOf("abrirLeitor()")
        assertTrue("lerQr tem de conferir a câmera", checa >= 0)
        assertTrue("lerQr tem de pedir a câmera", ler.contains("requestPermissions("))
        assertTrue("lerQr só abre o leitor depois de conferir", abre > checa)
        // O leitor (QrScannerActivity) só é aberto por abrirLeitor, que só é chamado com a câmera liberada.
        assertEquals(1, Regex("QrScannerActivity::class").findAll(fonte).count())
        assertTrue(corpo("abrirLeitor").contains("QrScannerActivity::class"))
    }

    @Test fun recusaMostraTextoProprio() {
        val r = corpo("onRequestPermissionsResult")
        for (n in novos) assertTrue("resposta sem R.string.$n", r.contains("R.string.$n"))
        assertTrue("liberou: abre o leitor", r.contains("abrirLeitor()"))
        assertTrue("bloqueada de vez: leva aos ajustes do app", r.contains("ACTION_APPLICATION_DETAILS_SETTINGS"))
        assertTrue("negada uma vez: deixa tentar de novo", r.contains("lerQr()"))
    }

    @Test fun textosEmTodasAsLinguas() {
        val f = DocumentBuilderFactory.newInstance()
        val pastas = File(raiz, "celular/src/main/res").listFiles { d -> File(d, "strings.xml").isFile }.orEmpty()
        assertTrue("achei poucas línguas: ${pastas.map { it.name }}", pastas.size >= 16)
        fun textos(p: File): Map<String, String> {
            val nos = f.newDocumentBuilder().parse(File(p, "strings.xml")).getElementsByTagName("string")
            return (0 until nos.length).map { nos.item(it) }
                .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
        }
        val ingles = textos(File(raiz, "celular/src/main/res/values"))
        for (p in pastas) {
            val t = textos(p)
            for (n in novos) {
                val v = t[n]
                assertTrue("${p.name} sem $n", !v.isNullOrBlank())
                if (p.name != "values") assertNotEquals("${p.name}/$n ficou em inglês", ingles[n], v)
            }
        }
    }
}
