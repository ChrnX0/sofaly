// Parte "translate" (refatoração G, 27/11/2026): o motor de tradução de legenda (ML Kit, 5,7 MB no APK de 32 bits).
// Só existe no build com partes (-Psplits): o app vira base + partes e esta é baixada por quem liga a tradução.
// No build de sempre (sem -Psplits) o app compila esta MESMA pasta junto (ver app/build.gradle.kts): um código só.
plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.paulo.tvlauncher.features.translate"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    // Lib nativa comprimida no APK e extraída na instalação, como na base (a parte baixa ~metade do tamanho).
    packaging { jniLibs { useLegacyPackaging = true } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":app"))
    implementation(project(":core"))   // a interface SubtitleEngine (o app a usa como implementation, não passa adiante)
    implementation("com.google.mlkit:translate:17.0.3")
}
