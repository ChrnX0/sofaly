// Módulo :core (refatoração G, 27/11/2026): a lógica pura do app, sem tela, sem R e sem BuildConfig.
// Por quê: o app inteiro era um bloco só (todo pacote dependia de todo outro); aqui o compilador garante a fronteira,
// e os testes desta parte rodam sem montar o app. Para quê: compilar e testar mais rápido e saber o dono de cada código.
// Como: os arquivos vieram de app/ SEM mudar pacote nem nome de classe (br.paulo.tvlauncher...), então nada muda na TV
// (manifest, Device Owner, prefs e adb continuam iguais). O :app depende deste módulo; este não enxerga o :app.
// Regra: nada aqui pode usar R, BuildConfig, telas ou classes do :app. Precisa disso? O arquivo fica no :app.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.paulo.tvlauncher.core"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    buildFeatures { buildConfig = false; resValues = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = false }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.annotation:annotation:1.9.1")   // @VisibleForTesting
    testImplementation("junit:junit:4.13.2")
    // O org.json do android.jar é só casca nos testes do computador ("Stub!"): estes testes usam o de verdade.
    testImplementation("org.json:json:20260814")
}
