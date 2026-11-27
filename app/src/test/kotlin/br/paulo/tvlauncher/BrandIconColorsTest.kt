package br.paulo.tvlauncher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater

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
            val pixels = Png.argb(File(raiz, caminho).readBytes())
            val azuis = pixels.count { argb -> argb ushr 24 > 200 && azulAntigo(argb) }
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

/**
 * Leitor mínimo de PNG (8 bits, RGB ou RGBA, sem entrelaçar: o que o Chromium grava). O teste roda no classpath do
 * Android (android.jar), onde não há javax.imageio, e Bitmap ali é de mentira: por isso o PNG é lido à mão.
 */
private object Png {
    fun argb(d: ByteArray): IntArray {
        require(d.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))) { "não é PNG" }
        fun u32(i: Int) = (d[i].toInt() and 0xFF shl 24) or (d[i + 1].toInt() and 0xFF shl 16) or
            (d[i + 2].toInt() and 0xFF shl 8) or (d[i + 3].toInt() and 0xFF)
        var i = 8; var w = 0; var h = 0; var canais = 0
        val idat = ByteArrayOutputStream()
        while (i < d.size) {
            val tam = u32(i); val tipo = String(d, i + 4, 4, Charsets.US_ASCII); val ini = i + 8
            when (tipo) {
                "IHDR" -> {
                    w = u32(ini); h = u32(ini + 4)
                    require(d[ini + 8].toInt() == 8 && d[ini + 12].toInt() == 0) { "só 8 bits sem entrelaçar" }
                    canais = when (d[ini + 9].toInt()) { 2 -> 3; 6 -> 4; else -> error("tipo de cor ${d[ini + 9]}") }
                }
                "IDAT" -> idat.write(d, ini, tam)
                "IEND" -> break
            }
            i = ini + tam + 4
        }
        val linha = w * canais
        val cru = ByteArray((linha + 1) * h)
        Inflater().run { setInput(idat.toByteArray()); var n = 0; while (n < cru.size && !finished()) n += inflate(cru, n, cru.size - n); end() }
        val px = ByteArray(linha * h)
        for (y in 0 until h) {
            val filtro = cru[y * (linha + 1)].toInt()
            for (x in 0 until linha) {
                val v = cru[y * (linha + 1) + 1 + x].toInt() and 0xFF
                val a = if (x >= canais) px[y * linha + x - canais].toInt() and 0xFF else 0
                val b = if (y > 0) px[(y - 1) * linha + x].toInt() and 0xFF else 0
                val c = if (x >= canais && y > 0) px[(y - 1) * linha + x - canais].toInt() and 0xFF else 0
                val pred = when (filtro) {
                    0 -> 0; 1 -> a; 2 -> b; 3 -> (a + b) / 2
                    4 -> { val p = a + b - c; val pa = Math.abs(p - a); val pb = Math.abs(p - b); val pc = Math.abs(p - c)
                        if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c }
                    else -> error("filtro $filtro")
                }
                px[y * linha + x] = (v + pred).toByte()
            }
        }
        return IntArray(w * h) { k ->
            val o = k * canais
            val r = px[o].toInt() and 0xFF; val g = px[o + 1].toInt() and 0xFF; val b = px[o + 2].toInt() and 0xFF
            val al = if (canais == 4) px[o + 3].toInt() and 0xFF else 0xFF
            (al shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
