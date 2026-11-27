package br.paulo.tvlauncher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * A logo em PNG (ícone do painel no celular, aba do navegador, ícone de rede no Windows, site) é a mesma da TV:
 * tela grafite #1E1E1E e placa #141413. Em 27/11/2026 esses PNG ainda eram da logo antiga azul-marinho (tela #1C2140,
 * placa #121528), porque só os SVG e o Kotlin tinham sido trocados. Regerar: `node scripts/render-brand-icons.js`.
 */
class BrandIconColorsTest {
    private val raiz = listOf(File("."), File("..")).first { File(it, "scripts/render-brand-icons.js").isFile }

    private val icones = listOf(
        "app/src/main/assets/painel/icone-192.png", "app/src/main/assets/painel/icone-512.png",
        "app/src/main/assets/painel/icone-apple.png", "app/src/main/assets/painel/icone-mascaravel.png",
        "app/src/main/assets/painel/sofaly-favicon-180.png", "app/src/main/assets/painel/sofaly-favicon-32.png",
        "app/src/main/assets/upnp/sofaly-120.png", "app/src/main/assets/upnp/sofaly-48.png",
        "website/assets/favicon-180.png", "site/assets/favicon-180.png", "website/assets/og.png", "site/assets/og.png",
    )

    /** Azul-marinho da logo antiga, com folga para o antisserrilhado. */
    private fun azulAntigo(rgb: Int): Boolean {
        val r = rgb shr 16 and 0xFF; val g = rgb shr 8 and 0xFF; val b = rgb and 0xFF
        return b - r >= 18 && b - g >= 14 && r < 0x40   // escuro puxado para o azul: a regra é escuro sem azul
    }

    @Test fun iconesSemAzulDaLogoAntiga() {
        for (caminho in icones) {
            val img = ImageIO.read(File(raiz, caminho)) ?: error("não li $caminho")
            var azuis = 0
            for (y in 0 until img.height) for (x in 0 until img.width) {
                val argb = img.getRGB(x, y)
                if (argb ushr 24 > 200 && azulAntigo(argb)) azuis++
            }
            assertTrue("$caminho tem $azuis pixels azul-marinho (logo antiga); rode node scripts/render-brand-icons.js", azuis == 0)
        }
    }

    @Test fun faviconsSvgComPlacaGrafite() {
        for (caminho in listOf("app/src/main/assets/painel/sofaly-favicon.svg", "website/assets/favicon.svg", "site/assets/favicon.svg")) {
            val svg = File(raiz, caminho).readText()
            assertTrue("$caminho ainda tem azul-marinho", "#121528" !in svg && "#1C2140" !in svg.uppercase())
        }
    }
}
