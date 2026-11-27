// Módulo :weather (refatoração G, etapa 5, 27/11/2026): o primeiro módulo por ÁREA. Clima, previsão, céu noturno,
// astronomia e localização: as contas e as fontes, sem as telas (avisos de chuva/temperatura e o céu de fundo ficam
// no :app, porque dependem da HOME).
// Por quê: o clima era só uma pasta do :app; qualquer arquivo do app podia puxar qualquer coisa daqui e vice-versa.
// Para quê: o compilador garante a fronteira (este módulo não enxerga o :app) e mudar o clima não recompila o app todo.
// Como: mesmos pacotes e nomes de classe (br.paulo.tvlauncher.weather...), então nada muda na TV. O que vinha do app
// chega por [WeatherHost] (Context e assinatura do relay), preenchido pelo LauncherApp na partida, e por WeatherConfig.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.paulo.tvlauncher.weathermodule"
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
    implementation(project(":resources"))   // R (textos, ícones) e BuildConfig (coordenadas padrão, PLAY)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.annotation:annotation:1.9.1")
}
