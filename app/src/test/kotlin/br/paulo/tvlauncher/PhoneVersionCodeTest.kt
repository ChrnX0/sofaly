package br.paulo.tvlauncher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * O Release confere que sofaly-phone.apk tem o MESMO versionCode do launcher. Em 27/11/2026 ele caiu duas vezes
 * ("versionCode 476201, esperado 476200" e "476228, esperado 476227"): o celular recalculava o número pelo relógio, e
 * bastava o minuto virar entre o `date` do release.yml e a configuração do Gradle. Os dois módulos leem `-PversionCode`.
 */
class PhoneVersionCodeTest {
    private val raiz = listOf(File(".."), File(".")).first { File(it, ".github/workflows/release.yml").isFile }

    @Test fun celularUsaONumeroDoRelease() {
        for (m in listOf("app", "celular")) {
            val g = File(raiz, "$m/build.gradle.kts").readText()
            val linha = g.lines().first { Regex("""^\s*versionCode\s*=""").containsMatchIn(it) }
            assertTrue("$m: versionCode precisa vir de -PversionCode: $linha", "findProperty(\"versionCode\")" in linha)
        }
    }

    @Test fun releaseCompilaOCelularComONumero() {
        val yml = File(raiz, ".github/workflows/release.yml").readText()
        val linha = yml.lines().first { "\$CEL" in it && "gradlew" in it }
        assertTrue(linha, "-PversionCode=\$CODIGO" in linha)
    }
}
