// Módulo :media (refatoração G, etapa 5, 27/11/2026): os dados do player, sem tela e sem o motor de vídeo.
// Catálogos (Xtream, M3U, Plex, Jellyfin, addons), fichas e legendas baixadas, contas e sincronização de serviços.
// Por quê: o player é a maior área do app (97 arquivos) e as telas dele (Filmes, Séries, Vod) mudam o tempo todo; os
// dados ficam num módulo que o compilador separa das telas. Mesmos pacotes e nomes de classe: nada muda na TV.
// O que vem do app chega por ganchos preenchidos pelo LauncherApp (como no :weather); o Context, pelo AppContext do :core.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.paulo.tvlauncher.mediamodule"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    buildFeatures { buildConfig = false; resValues = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":resources"))
    implementation(project(":weather"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.annotation:annotation:1.9.1")
}
