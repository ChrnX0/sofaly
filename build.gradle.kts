// Arquivo raiz: declara as versões dos plugins. O app em si é configurado em app/build.gradle.kts.
// AGP 8.11.1 e Kotlin 2.2.20 (alinhadas em 04/11/2026 com o Shizuku embutido).
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
}

// versionCode do app (04/11/2026): minutos desde 01/01/2026 na hora do build, para sempre crescer; `-PversionCode=N`
// fixa o número (o Release monta a ponte e o app novo com o MESMO). Calculado UMA vez aqui (refatoração G, 27/11/2026):
// o :app (versionCode do APK) e o :resources (BuildConfig.VERSION_CODE) leem este, então nunca caem em minutos diferentes.
extra["sofalyVersionCode"] = (findProperty("versionCode") as String?)?.toInt()
    ?: ((System.currentTimeMillis() - 1_767_225_600_000L) / 60_000L).toInt()

// Configuração comum dos módulos do Shizuku (na origem isto ficava no build.gradle raiz do repositório deles).
// Sem NDK desde 15/11/2026: a parte nativa (librish.so, o terminal "rish") ficou de fora; o servidor não precisa dela.
subprojects {
    if (path == ":app" || path == ":celular" || path == ":core" || path == ":resources") return@subprojects
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.gradle.LibraryExtension> {
            compileSdk = 36
            buildToolsVersion = "36.0.0"
            defaultConfig { minSdk = 24 }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }
    }
}
