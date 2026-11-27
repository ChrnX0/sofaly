// Módulo :listen (refatoração G, etapa 7, 27/11/2026): os dados do Ouvir (rádios, podcasts, feeds, transcrição), sem tela.
// Mesmos pacotes e nomes de classe: nada muda na TV. O que vem do app chega por ganchos (ListenHost), o Context pelo AppContext do :core.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.paulo.tvlauncher.listenmodule"
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
    implementation(project(":media"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.annotation:annotation:1.9.1")
}
