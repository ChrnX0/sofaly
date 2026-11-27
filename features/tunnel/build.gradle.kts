// Parte "tunnel" (refatoração G, 27/11/2026): o túnel para a internet (libtsnet.so, Tailscale, 8 MB no APK de 32 bits).
// Só existe no build com partes (-Psplits); vem da mesma pasta que o app usa no build de sempre (compilarTunel).
// Sem código: o TunelTailscale (no app) roda o binário da pasta nativa, onde o Android extrai as libs das partes também.
plugins {
    id("com.android.dynamic-feature")
}

android {
    namespace = "br.paulo.tvlauncher.features.tunnel"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    // Lib nativa comprimida no APK e extraída na instalação, como na base (a parte baixa ~metade do tamanho).
    packaging { jniLibs { useLegacyPackaging = true } }
    sourceSets["main"].jniLibs.srcDir(project(":app").layout.buildDirectory.dir("tunel/jniLibs"))
}

dependencies {
    implementation(project(":app"))
}

tasks.named("preBuild") { dependsOn(":app:compilarTunel") }
