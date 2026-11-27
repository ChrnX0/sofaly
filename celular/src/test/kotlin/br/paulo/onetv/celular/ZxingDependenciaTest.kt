package br.paulo.onetv.celular

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * O leitor de QR (zxing-android-embedded 4.x) usa androidx.core sem declarar no POM. Na v475825 o app fechou ao tocar em
 * "Ler o QR da TV" porque a dependência faltava (27/11/2026). Este teste impede que ela saia de novo do build.
 */
class ZxingDependenciaTest {
    @Test fun leitorDeQrTemAndroidxCore() {
        val gradle = listOf(File("build.gradle.kts"), File("celular/build.gradle.kts")).first { it.isFile }.readText()
        if ("zxing-android-embedded" !in gradle) return
        assertTrue("zxing-android-embedded precisa de androidx.core declarado no celular/build.gradle.kts",
            Regex("""implementation\("androidx\.core:core:[0-9.]+"\)""").containsMatchIn(gradle))
    }
}
