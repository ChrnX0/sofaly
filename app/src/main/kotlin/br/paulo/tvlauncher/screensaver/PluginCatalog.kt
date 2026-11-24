package br.paulo.tvlauncher.screensaver

import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.player.LojaCurada
import org.json.JSONObject

/**
 * Vitrine de plugins (18/11/2026): a lista ESCOLHIDA por nós de plugins que funcionam no Sofaly, com onde conseguir cada
 * um. Nada de busca livre. Fontes: a página pública de plugins do ecossistema de TV e os repositórios de cada projeto
 * (lidos em 18/11/2026). Pacotes Android marcados [pacote] foram lidos na Play ou no repositório; null = não conferido
 * (o "instalado" vem do que a loja guardou ao instalar).
 *
 * Na TV: Play Store quando o plugin está lá ([play]); senão, no APK do site, instala direto do Release oficial pela
 * [LojaCurada] (mesma conferência de assinatura); sempre com um QR para a página. O build da Play nunca baixa APK.
 * Só a lista e as regras puras (PluginCatalogTest).
 *
 * 24/11/2026 (extensões, proposta B): a lista também vem do servidor ([URL], `server/api/src/plugins.js`), para trocar a
 * vitrine sem Release. O servidor só SUGERE e só pode: reordenar, tirar um plugin da vitrine (um que quebrou) e acrescentar
 * um que esteja na Play. Ele nunca muda pacote nem repositório de uma entrada embutida e nunca dá repositório novo: instalar
 * APK direto do Release continua só para as entradas embutidas (código revisado), com a conferência da LojaCurada.
 */
object PluginCatalog {
    enum class Tipo { FUNDO, ICONES }

    /** [descricao] = texto do app (R.string); [texto] = descrição que veio do servidor, já no idioma da TV (então descricao = 0). */
    class Entrada(
        val id: String, val tipo: Tipo, val nome: String, val descricao: Int,
        val pacote: String? = null, val play: String? = null, val repo: String? = null, val texto: String? = null,
    ) {
        /** Id na LojaCurada (só quem tem repositório e um APK que serve para TV). */
        val lojaId: String? get() = repo?.let { "pl-$id" }
    }

    const val URL = "https://api.sofaly.tv/v1/plugins/recommended"
    /** A TV pergunta ao servidor no máximo 1 vez por dia (a vitrine muda raramente). */
    const val REFRESH_MS = 24L * 60 * 60 * 1000
    const val MAX = 40
    private val ID = Regex("[a-z0-9][a-z0-9-]{0,39}")
    private val PACOTE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    /**
     * Resposta do servidor (campos em inglês): `{"plugins":[{"id","type":"wallpaper"|"icons","name","play"?,"package"?,
     * "description":{"en":..,"pt":..}}]}`. Uma entrada com id embutido vale como a embutida (o servidor só a mantém na
     * vitrine); uma nova precisa de `play`. Nome com o de concorrente, id repetido, campo fora do formato: a linha é
     * ignorada. Resposta estragada ou vazia = null (fica a lista embutida). [idioma] = língua da TV ("pt", "en"...).
     */
    fun parse(text: String?, idioma: String): List<Entrada>? = runCatching {
        val a = JSONObject(text ?: return null).optJSONArray("plugins") ?: return null
        val out = ArrayList<Entrada>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            if (!ID.matches(id) || out.any { it.id == id }) continue
            val embutida = LISTA.firstOrNull { it.id == id }
            if (embutida != null) { out += embutida; continue }
            val tipo = when (o.optString("type")) { "wallpaper" -> Tipo.FUNDO; "icons" -> Tipo.ICONES; else -> continue }
            val nome = o.optString("name").filter { !it.isISOControl() }.trim()
            if (nome.isEmpty() || nome.length > 40 || nome.contains("projectivy", ignoreCase = true)) continue
            val play = o.optString("play").trim().takeIf { PACOTE.matches(it) && it.length <= 150 } ?: continue
            val pacote = o.optString("package").trim().ifEmpty { play }.takeIf { PACOTE.matches(it) && it.length <= 150 } ?: continue
            val d = o.optJSONObject("description")
            val texto = (d?.optString(idioma).orEmpty().ifEmpty { d?.optString("en").orEmpty() })
                .filter { !it.isISOControl() }.trim().take(120)
            out += Entrada(id, tipo, nome, 0, pacote, play, texto = texto)
            if (out.size >= MAX) break
        }
        out.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** Idioma que o servidor usa para a descrição: o da TV, com o código antigo do Java para indonésio corrigido. */
    fun idioma(lingua: String): String = when (lingua) { "in" -> "id"; "nb", "no" -> "nb"; else -> lingua }

    val LISTA = listOf(
        Entrada("aerialviews", Tipo.FUNDO, "Aerial Views", R.string.wp_d_aerial, "com.neilturner.aerialviews", play = "com.neilturner.aerialviews"),
        Entrada("overflight", Tipo.FUNDO, "Overflight", R.string.wp_d_overflight, "tv.projectivy.plugin.wallpaperprovider.overflight",
            play = "tv.projectivy.plugin.wallpaperprovider.overflight", repo = "spocky/projectivy-plugin-wallpaper-overflight"),
        Entrada("dailyphotos", Tipo.FUNDO, "Daily Photos", R.string.wp_d_daily, repo = "spocky/projectivy-plugin-wallpaper-bingwallpaper"),
        Entrada("weather", Tipo.FUNDO, "Weather Wallpaper", R.string.wp_d_weather, repo = "shortusername/projectivy-weather-wallpaper"),
        Entrada("movieart", Tipo.FUNDO, "Movie Art", R.string.wp_d_movieart, "com.danielkorgel.projectivy.plugin.cinemaglow",
            play = "com.danielkorgel.projectivy.plugin.cinemaglow"),
        Entrada("sofadrop", Tipo.FUNDO, "Sofadrop", R.string.wp_d_sofadrop, repo = "rfmarcos/projectivy-plugin-sofadrop"),
        Entrada("immich", Tipo.FUNDO, "Immich Wallpaper", R.string.wp_d_immich, repo = "raizer88/immich-projectivy-provider"),
        // O app se chama "Projectivy Icon Pack": aqui e na nossa tela mostramos um nome neutro (regra: nada de nome de concorrente).
        Entrada("tvicons", Tipo.ICONES, "TV Icon Pack", R.string.wp_d_tvicons, "dev.projectivy.blueprint.app", repo = "SicMundus86/ProjectivyIconPack"),
        Entrada("minimalist", Tipo.ICONES, "Minimalist Icon Pack", R.string.wp_d_minimalist, repo = "hqn-scl/android-tv-minimalist-icon-pack"),
        // Arcticons: traço fino, combina com o estilo Linha da cruz. O Release tem vários APKs (claro/escuro): só pela página.
        Entrada("arcticons", Tipo.ICONES, "Arcticons", R.string.wp_d_arcticons, "com.donnnno.arcticons", play = "com.donnnno.arcticons"),
    )

    /** Nome que mostramos para um pacote instalado que está na vitrine (o rótulo do app pode citar outro launcher). */
    fun nomeDoPacote(pacote: String, lista: List<Entrada> = LISTA): String? =
        (LISTA.firstOrNull { it.pacote == pacote } ?: lista.firstOrNull { it.pacote == pacote })?.nome

    /** Página para o QR: a da Play quando existe, senão o último Release no GitHub. */
    fun pagina(e: Entrada): String? = when {
        e.play != null -> "https://play.google.com/store/apps/details?id=${e.play}"
        e.repo != null -> "https://github.com/${e.repo}/releases/latest"
        else -> null
    }

    /** O que o botão principal faz neste build. */
    enum class Acao { PLAY, INSTALAR, SO_QR }

    fun acao(e: Entrada, buildPlay: Boolean, temPlayStore: Boolean): Acao = when {
        e.play != null && temPlayStore -> Acao.PLAY
        !buildPlay && e.lojaId != null -> Acao.INSTALAR
        else -> Acao.SO_QR
    }

    /** Itens para a LojaCurada (painel e TV): só os com repositório. A descrição do painel fica em português. */
    fun itensLoja(): List<LojaCurada.Item> = LISTA.mapNotNull { e ->
        e.lojaId?.let { LojaCurada.Item(it, e.nome, e.repo!!, if (e.tipo == Tipo.FUNDO) "Plugin de fundo" else "Pacote de ícones") }
    }
}
