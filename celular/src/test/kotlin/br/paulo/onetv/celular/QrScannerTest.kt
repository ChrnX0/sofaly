package br.paulo.onetv.celular

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Leitor do QR da TV (27/11/2026). Paulo pareou o celular e viu "um leitor de barras e não de QR": a CaptureActivity do
 * ZXing abre deitada (sensorLandscape), com janela larga e linha vermelha. Estes testes seguram o conserto.
 */
class QrScannerTest {
    private fun arquivo(caminho: String): String =
        listOf(File(caminho), File("celular/$caminho")).first { it.isFile }.readText()

    @Test fun quadradoCabeEmPeEDeitado() {
        assertEquals(756, JanelaQr.lado(1080, 2400))   // celular comum em pé: 70% da largura
        assertEquals(756, JanelaQr.lado(2400, 1080))   // deitado: o mesmo quadrado
        assertEquals(224, JanelaQr.lado(320, 480))     // tela pequena: continua cabendo
    }

    @Test fun abreONossoLeitorEmPe() {
        val tvs = arquivo("src/main/kotlin/br/paulo/onetv/celular/TvsActivity.kt")
        assertTrue("TvsActivity precisa abrir o QrScannerActivity", "QrScannerActivity::class.java" in tvs)
        assertFalse("CaptureActivity padrão do ZXing abre deitada", "CaptureActivity::class.java" in tvs)
        val manifesto = arquivo("src/main/AndroidManifest.xml")
        val bloco = Regex("""<activity android:name="\.QrScannerActivity"[^>]*/>""").find(manifesto)?.value
        assertTrue("QrScannerActivity precisa estar no manifesto", bloco != null)
        assertTrue("o leitor tem de ficar em pé", "android:screenOrientation=\"portrait\"" in bloco!!)
        assertTrue("sem o tema do ZXing a tela não fica cheia", "@style/zxing_CaptureTheme" in bloco)
    }

    @Test fun janelaQuadradaSemLinhaVermelha() {
        val leitor = arquivo("src/main/kotlin/br/paulo/onetv/celular/QrScannerActivity.kt")
        assertTrue("janela quadrada", "framingRectSize = Size(lado, lado)" in leitor)
        assertTrue("sem a linha vermelha de leitor de barras", "setLaserVisibility(false)" in leitor)
    }
}
