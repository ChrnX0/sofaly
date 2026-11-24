package br.paulo.tvlauncher.privilege

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import br.paulo.tvlauncher.AppIdentity
import org.json.JSONObject

/**
 * Sugestões de limpeza para QUALQUER TV (22/11/2026, estudo do Tellyscope, proposta A).
 *
 * Porquê: a Sofaly é vendida no mundo todo, mas a [Debloat.LISTA] só conhece o Chromecast da Sala (2 apps testados).
 * Para quê: numa TCL, Sony ou Philips a pessoa vê quais apps de fábrica dá para ocultar e o que eles fazem, com desfazer.
 * Como: `assets/debloat/catalog.json` (gerado por `scripts/debloat-catalog.py` a partir das listas MIT do
 * tellyscope-devices / cleanyourtv, ~500 modelos; só os DADOS, nada do código GPL do Tellyscope) diz, por pacote, em
 * quantas listas ele é desligado e de quais marcas. Aqui viram sugestões só os apps DE SISTEMA instalados nesta TV que
 * aparecem em [MIN_LISTS]+ listas ou numa lista da mesma marca, sempre fora de:
 *  - [Debloat.NUNCA] e de tudo o que a [Debloat.LISTA]/comunidade já decide (o que foi testado manda);
 *  - [PALAVRAS_PROTEGIDAS] (a mesma regra do script, de novo aqui: o catálogo é dado de fora);
 *  - a proteção em tempo real: apps com tela HOME, o teclado atual, serviços de acessibilidade ligados, o WebView, apps
 *    persistentes e o nosso próprio pacote.
 * Nunca vêm marcadas: cada uma é uma chave que o dono vira, uma por vez, e o [Desfazer] registra.
 * Liga com: [Debloat.definir] (aceita o pacote quando [permite]), [DebloatPosOta] (oculta de novo após update do
 * sistema), "Mostrar todos de volta", o Diário/MCP via [br.paulo.tvlauncher.Desfazer], e o relatório adb.
 */
object DebloatCatalog {
    private const val TAG = "TvLauncher.DebloatCat"
    private const val ARQUIVO = "debloat/catalog.json"
    const val MIN_LISTS = 3
    const val MAX_SHOWN = 20

    /** Mesmo filtro do script: sustenta a TV (entrada, rede, som, atualização, HOME, voz, Cast...). */
    val PALAVRAS_PROTEGIDAS = listOf("launcher", "setup", "settings", "inputmethod", "keyboard", "webview", "wifi",
        "bluetooth", "update", "ota", "hdmi", "cec", "audio", "speaker", "systemui", "provider", "permission", "installer",
        "framework", "remote", "tvinput", "tv.input", "livetv", "mediashell", "cast", "voice", "katniss", "gms", "gsf",
        "dream", "backdrop")

    enum class Kind { TELEMETRY, PRELOADED, OTHER }

    data class Entry(val pkg: String, val lists: Int, val brands: Set<String>, val kind: Kind, val note: String?)

    data class Catalog(val devices: Int, val entries: Map<String, Entry>)

    data class Suggestion(val entry: Entry, val sameBrand: Boolean) { val pkg get() = entry.pkg }

    // ------------------------------------------------------------------------------------------- regras puras

    private val PREFIXOS = setOf("com", "android", "google", "apps", "app", "tv", "atv", "org", "net", "br", "de", "us")

    /**
     * Nome para ler quando o app sumido não tem rótulo (teste 100%, 23/11/2026: "com.android.ondevicepersonalization.services"
     * aparecia cru). Tira os pedaços genéricos do começo, separa camelCase e "_", e põe maiúscula:
     * "com.android.ondevicepersonalization.services" -> "Ondevicepersonalization services".
     */
    fun readableName(pkg: String): String {
        val partes = pkg.split('.').filter { it.isNotBlank() }
        val uteis = partes.dropWhile { it.lowercase() in PREFIXOS }.ifEmpty { partes.takeLast(1) }
        return uteis.joinToString(" ") { p -> p.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ') }
            .lowercase().trim().replaceFirstChar { it.uppercase() }
    }

    /** Lê o catálogo; entrada malformada é pulada (dado de fora). JSON inválido = catálogo vazio. */
    fun parse(json: String): Catalog = runCatching {
        val root = JSONObject(json)
        if (root.optInt("schema") != 1) return Catalog(0, emptyMap())
        val pk = root.getJSONObject("packages")
        val out = HashMap<String, Entry>(pk.length() * 2)
        for (pkg in pk.keys()) {
            val o = pk.optJSONObject(pkg) ?: continue
            if (!pacoteValido(pkg)) continue
            val lists = o.optInt("lists", 0).takeIf { it > 0 } ?: continue
            val brands = o.optJSONArray("brands")?.let { a -> (0 until a.length()).map { a.optString(it).lowercase() }.filter { it.isNotBlank() }.toSet() } ?: emptySet()
            val kind = when (o.optString("kind")) { "telemetry" -> Kind.TELEMETRY; "preloaded" -> Kind.PRELOADED; else -> Kind.OTHER }
            val note = o.optString("note").trim().takeIf { it.isNotEmpty() && '\n' !in it }?.take(140)
            out[pkg] = Entry(pkg, lists, brands, kind, note)
        }
        Catalog(root.optInt("devices", 0), out)
    }.getOrElse { Catalog(0, emptyMap()) }

    fun pacoteValido(p: String) = p.length in 3..150 && '.' in p && p.all { it.isLetterOrDigit() || it == '.' || it == '_' }

    fun protegidoPorNome(pkg: String): Boolean { val l = pkg.lowercase(); return PALAVRAS_PROTEGIDAS.any { it in l } }

    /** "TCL", "Sony Corporation", "Xiaomi" → "tcl", "sony", "xiaomi" (as marcas do catálogo são o 1º pedaço do id). */
    fun marcas(vararg nomes: String?): Set<String> = nomes.filterNotNull()
        .flatMap { it.lowercase().split(Regex("[^a-z0-9]+")) }.filter { it.length >= 2 }.toSet()

    /**
     * As sugestões para uma TV. [instalados]: apps de sistema instalados (ocultos/desligados contam). [protegidos]:
     * tudo o que nunca entra (NUNCA + proteção em tempo real). [jaDecididos]: o que a lista testada já cobre.
     */
    fun sugerir(cat: Catalog, instalados: Collection<String>, marcasTv: Set<String>, protegidos: Set<String>,
                jaDecididos: Set<String>): List<Suggestion> = instalados.asSequence()
        .distinct()
        .mapNotNull { cat.entries[it] }
        .filter { it.pkg !in protegidos && it.pkg !in jaDecididos && !protegidoPorNome(it.pkg) }
        .map { Suggestion(it, it.brands.any { b -> b in marcasTv }) }
        .filter { it.sameBrand || it.entry.lists >= MIN_LISTS }
        .sortedWith(compareByDescending<Suggestion> { it.sameBrand }.thenByDescending { it.entry.lists }.thenBy { it.pkg })
        .take(MAX_SHOWN)
        .toList()

    // ------------------------------------------------------------------------------------------- Android

    @Volatile private var cache: Catalog? = null

    fun catalogo(ctx: Context): Catalog = cache ?: runCatching {
        ctx.assets.open(ARQUIVO).bufferedReader().use { parse(it.readText()) }
    }.getOrElse { Log.w(TAG, "catálogo ilegível: $it"); Catalog(0, emptyMap()) }.also { cache = it }

    /** Proteção que só a TV sabe: quem é HOME, o teclado, a acessibilidade ligada, o WebView, os persistentes. */
    fun protegidosAgora(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val s = HashSet<String>(Debloat.NUNCA)
        s += ctx.packageName; s += AppIdentity.NEW_PACKAGE; s += AppIdentity.LEGACY_PACKAGE
        runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
                .forEach { s += it.activityInfo.packageName }
        }
        runCatching { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')?.let { s += it } }
        runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.mapNotNull { it.substringBefore('/').takeIf { p -> p.isNotBlank() } }?.let { s += it }
        }
        if (Build.VERSION.SDK_INT >= 26) runCatching { android.webkit.WebView.getCurrentWebViewPackage()?.packageName?.let { s += it } }
        return s
    }

    /** Apps de SISTEMA instalados neste usuário, incluindo os ocultos e os desligados (para poder mostrar de volta). */
    fun sistemaInstalado(ctx: Context): Pair<List<String>, Set<String>> {
        @Suppress("DEPRECATION")
        val todos = runCatching {
            ctx.packageManager.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
        }.getOrDefault(emptyList())
        val doUsuario = todos.filter { (it.flags and ApplicationInfo.FLAG_INSTALLED) != 0 }
        val sistema = doUsuario.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }.map { it.packageName }
        val persistentes = doUsuario.filter { (it.flags and ApplicationInfo.FLAG_PERSISTENT) != 0 }.map { it.packageName }.toSet()
        return sistema to persistentes
    }

    fun sugestoes(ctx: Context): List<Suggestion> {
        val (sistema, persistentes) = sistemaInstalado(ctx)
        val jaDecididos = Debloat.todos(ctx).map { it.pacote }.toSet()
        return sugerir(catalogo(ctx), sistema, marcas(Build.MANUFACTURER, Build.BRAND), protegidosAgora(ctx) + persistentes, jaDecididos)
    }

    /** O [Debloat.definir] pergunta aqui antes de ocultar um pacote que não está na lista testada. */
    fun permite(ctx: Context, pacote: String): Boolean = sugestoes(ctx).any { it.pkg == pacote }
}
