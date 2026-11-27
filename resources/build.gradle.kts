// Módulo :resources (refatoração G, etapa 3, 27/11/2026): os recursos do app (textos em 26+ línguas, desenhos, fontes, xml).
// Por quê: o R ficava preso no :app, então nenhum módulo de área (painel, MCP, rede, Sofy) podia mostrar um texto sem
// depender do app inteiro. Para quê: cada módulo novo depende só deste e do :core.
// Como: a pasta continua em app/src/main/res (scripts, testes e outras threads seguem usando o mesmo caminho); só o
// dono mudou. A classe R passa a ser br.paulo.tvlauncher.resources.R. Arquivo novo que usa R: importe essa.
// Na TV nada muda: os recursos entram no mesmo APK, com o mesmo pacote (tv.sofaly.launcher) e os mesmos nomes.
plugins {
    id("com.android.library")
}

android {
    namespace = "br.paulo.tvlauncher.resources"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    buildFeatures { buildConfig = false }
    sourceSets["main"].res.setSrcDirs(listOf(rootProject.file("app/src/main/res")))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
