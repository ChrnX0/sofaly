// Módulo :resources (refatoração G, etapa 3, 27/11/2026): os recursos do app (textos em 26+ línguas, desenhos, fontes, xml).
// Por quê: o R ficava preso no :app, então nenhum módulo de área (painel, MCP, rede, Sofy) podia mostrar um texto sem
// depender do app inteiro. Para quê: cada módulo novo depende só deste e do :core.
// Como: a pasta continua em app/src/main/res (scripts, testes e outras threads seguem usando o mesmo caminho); só o
// dono mudou. A classe R passa a ser br.paulo.tvlauncher.resources.R. Arquivo novo que usa R: importe essa.
// Na TV nada muda: os recursos entram no mesmo APK, com o mesmo pacote (tv.sofaly.launcher) e os mesmos nomes.
// BuildConfig (refatoração G, etapa 5a, 27/11/2026): a classe br.paulo.tvlauncher.BuildConfig também nasce aqui (o :app
// não gera mais a dele). Mesmo nome e mesmas constantes de antes, então nenhum arquivo muda de import, e os módulos de
// área enxergam PLAY, VERSION_CODE etc. sem depender do :app. As constantes são `static final` de verdade: o Kotlin as
// copia no código na hora de compilar, e o `if (BuildConfig.PLAY)` continua cortando o que a Play não aceita.
import java.util.Properties

plugins {
    id("com.android.library")
}

// 05/11/2026: dados PESSOAIS (cidade, coordenadas, apps preferidos, eclipses da cidade) saíram do Config.kt, porque o
// repositório é público. Eles vêm do arquivo pessoal.properties, na raiz do repo, que NÃO vai para o git (.gitignore).
// Sem o arquivo (quem baixa do GitHub, ou o build do GitHub Actions), valem padrões neutros. Modelo comentado em
// pessoal.properties.exemplo. Cada valor vira uma constante da classe BuildConfig, que o Config.kt lê.
val pessoal = Properties().apply {
    val f = rootProject.file("pessoal.properties")
    if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }   // UTF-8: acentos (ã, ç) saem certos
}
// Texto Java entre aspas, com \ e " escapados (senão um valor com aspas quebra a compilação do BuildConfig).
fun textoJava(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
// Build da Play (-Pplay, 17/11/2026): sai do PC do dono, que tem o pessoal.properties; cidade, coordenadas, apps fixos,
// METAR e eclipses dele NÃO podem ir para o app de todos os clientes. Na Play só as chaves de serviço (chaveApp) vêm de lá.
val play = project.hasProperty("play")
val dadosPessoais = if (play) Properties() else pessoal
fun pessoalTexto(chave: String, padrao: String = "") = textoJava(dadosPessoais.getProperty(chave, padrao).trim())
fun pessoalNumero(chave: String, padrao: Double) = (dadosPessoais.getProperty(chave)?.trim()?.toDoubleOrNull() ?: padrao).toString()
// Chaves dos APLICATIVOS de filmes e séries (ServicosFilmes.kt, 14/11/2026): uma por projeto, nunca no git. No PC vêm do
// pessoal.properties; no GitHub Actions, das variáveis de ambiente que o release.yml preenche com os secrets de mesmo nome.
// Sem a chave, o serviço some da tela do launcher.
fun chaveApp(nome: String) = textoJava((System.getenv(nome) ?: pessoal.getProperty(nome.lowercase(), "")).trim())

// Nome -> (tipo Java, valor já escrito como literal Java). A ordem é a do arquivo gerado.
val campos = linkedMapOf<String, Pair<String, String>>().apply {
    put("VERSION_CODE", "int" to (rootProject.extra["sofalyVersionCode"] as Int).toString())   // o mesmo do APK
    put("VERSION_NAME", "String" to textoJava("1.0"))
    // Dados pessoais (ver pessoal.properties acima). Padrões neutros entre aspas ou como número.
    put("CIDADE", "String" to pessoalTexto("cidade"))
    put("LATITUDE", "double" to pessoalNumero("latitude", 0.0))
    put("LONGITUDE", "double" to pessoalNumero("longitude", 0.0))
    put("USAR_FAHRENHEIT", "boolean" to (dadosPessoais.getProperty("fahrenheit")?.trim() == "true").toString())
    put("ESTACAO_METAR", "String" to pessoalTexto("estacao_metar"))
    // Quantas línguas o app traz (pastas values-* com strings.xml + o inglês padrão): o Placar diz o número certo
    // sem ninguém lembrar de trocar o texto a cada língua nova (estava "14" com 26, 24/11/2026).
    // Línguas distintas: values-pt e values-pt-rPT contam uma (português); + 1 = o inglês de values/.
    put("IDIOMAS", "int" to (rootProject.file("app/src/main/res").listFiles { d -> d.name.startsWith("values-") && d.resolve("strings.xml").exists() }!!
        .map { it.name.removePrefix("values-").substringBefore("-r") }.toSet().size + 1).toString())
    put("APPS_FIXOS", "String" to pessoalTexto("apps_fixos"))
    put("APPS_ORDEM", "String" to pessoalTexto("apps_ordem",
        "com.google.android.youtube.tv,com.google.android.youtube.tvmusic,com.netflix.ninja,com.android.vending"))
    put("BOTAO_NETFLIX", "String" to pessoalTexto("botao_netflix", "com.netflix.ninja"))
    put("ECLIPSES", "String" to pessoalTexto("eclipses"))
    // Cuidar de longe (15/11/2026, decisão do Paulo): a Play proíbe app que avisa terceiros sobre um adulto, mesmo
    // com consentimento (regra de stalkerware). Build da Play = `-Pplay`: só fica o pedido de ajuda (77); o aviso à
    // família do lembrete (78) e da TV que não ligou (79) só existe no APK do site.
    put("CUIDAR_FAMILIA", "boolean" to (!play).toString())
    // Build da Play (`-Pplay`, 16/11/2026): o produto Sofaly. PLAY liga a cobrança da Play e desliga o que a loja
    // não aceita (atualizador do GitHub, instalar APK). A fonte do clima não depende mais do build (19/11/2026): é o
    // plano e a licença que escolhem, em DataSources.kt (MET Norway no Básico).
    put("PLAY", "boolean" to play.toString())
    // Build da Play: sem TMDB (chave pessoal; uso comercial pede contrato) até haver contrato. O Simkl entra (19/11/2026)
    // só no caminho grátis que eles aceitaram: a conta da própria pessoa e o convite "Conecte sua conta"; a nota sem
    // login fica para o Pro com licença (DataSources.kt). Trakt (Standard) e OpenSubtitles (login da pessoa) ficam.
    val semLicencaNaPlay = setOf("TMDB_CHAVE")
    for (k in listOf("TMDB_CHAVE", "TRAKT_ID", "TRAKT_SEGREDO", "SIMKL_ID", "OPENSUBTITLES_CHAVE"))
        put(k, "String" to if (play && k in semLicencaNaPlay) "\"\"" else chaveApp(k))
    // Degrau 2a (17/11/2026): e-mail que recebe o "Relatar um problema" (quem não tem conta no GitHub). Fora do git:
    // Padrão = o e-mail do produto (Paulo, 17/11/2026: contact@sofaly.tv). pessoal.properties "suporte_email" ou a
    // variável SUPORTE_EMAIL do repo trocam; um valor inválido faz o QR voltar ao formulário do GitHub.
    put("SUPORTE_EMAIL", "String" to textoJava((System.getenv("SUPORTE_EMAIL")?.takeIf { it.isNotBlank() }
        ?: pessoal.getProperty("suporte_email")?.takeIf { it.isNotBlank() } ?: "contact@sofaly.tv").trim()))
}

/** Escreve BuildConfig.java. Entrada = o texto inteiro: mudou um valor (ou o minuto do versionCode), gera de novo. */
abstract class GerarBuildConfig : DefaultTask() {
    @get:Input abstract val texto: Property<String>
    @get:OutputDirectory abstract val saida: DirectoryProperty
    @TaskAction fun gerar() {
        val f = saida.get().file("br/paulo/tvlauncher/BuildConfig.java").asFile
        f.parentFile.mkdirs()
        f.writeText(texto.get())
    }
}
val gerarBuildConfig = tasks.register<GerarBuildConfig>("generateSofalyBuildConfig") {
    texto.set(buildString {
        append("// Gerado por resources/build.gradle.kts (não editar).\npackage br.paulo.tvlauncher;\n\n")
        append("public final class BuildConfig {\n    private BuildConfig() {}\n")
        for ((nome, tv) in campos) append("    public static final ${tv.first} $nome = ${tv.second};\n")
        append("}\n")
    })
    saida.set(layout.buildDirectory.dir("generated/sofalyBuildConfig"))
}
androidComponents {
    onVariants { v -> v.sources.java?.addGeneratedSourceDirectory(gerarBuildConfig, GerarBuildConfig::saida) }
}

android {
    namespace = "br.paulo.tvlauncher.resources"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    buildFeatures { buildConfig = false }   // o do AGP sairia em br.paulo.tvlauncher.resources: o nosso é gerado acima
    sourceSets["main"].res.setSrcDirs(listOf(rootProject.file("app/src/main/res")))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
