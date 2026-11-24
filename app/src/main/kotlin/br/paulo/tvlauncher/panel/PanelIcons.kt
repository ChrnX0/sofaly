package br.paulo.tvlauncher.panel

import android.content.Context
import br.paulo.tvlauncher.home.IconSetArt
import br.paulo.tvlauncher.home.IconeCruz
import br.paulo.tvlauncher.home.LayoutHome
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ícones da cruz pelo painel web (21/11/2026, o "Trocar ícone" da TV também no celular/PC).
 * GET  /api/extras/icons             -> o conjunto de Aparência, os conjuntos que existem e a troca de cada tipo;
 * POST /api/extras/icons             -> {"set": "VINTAGE"} troca o conjunto; {"type": "TV", "style": "FOTO" | ""} troca
 *                                       um tipo ("" = volta a seguir o conjunto);
 * GET  /api/extras/icons/image?style=VINTAGE&type=TV -> a prévia (WebP da arte; só os conjuntos de arte têm arquivo).
 * Nada aqui é destrutivo (volta com um toque), então não pede o PIN. A HOME se refaz quando volta à frente.
 */
object PanelIcons {

    fun state(app: Context): JSONObject {
        val trocados = LayoutHome.iconesTrocados(app)
        return JSONObject()
            .put("set", LayoutHome.iconesCruz(app).name)
            .put("styles", JSONArray().apply {
                for (e in IconeCruz.Estilo.entries) put(JSONObject().put("id", e.name).put("name", app.getString(LayoutHome.nomeIcones(e))).put("image", e.pasta != null)
                    // Os tipos que têm arte neste conjunto: sem isso o painel pedia a prévia de Ferramentas, Casa, Clima… e
                    // levava 8 erros 404 por abertura (teste 100%, painel no Dell, 23/11/2026).
                    .put("art", JSONArray(comArte(app, e.pasta))))
            })
            .put("types", JSONArray().apply {
                for (t in IconeCruz.Tipo.entries) put(JSONObject().put("id", t.name).put("style", trocados[t]?.name ?: ""))
            })
    }

    /** Muda o conjunto ou um tipo; null = corpo inválido. */
    fun change(app: Context, body: JSONObject): JSONObject? {
        if (body.has("set")) {
            val e = IconeCruz.Estilo.entries.firstOrNull { it.name == body.optString("set") } ?: return null
            LayoutHome.definirIconesCruz(app, e)
        } else {
            val t = IconeCruz.Tipo.entries.firstOrNull { it.name == body.optString("type") } ?: return null
            val nome = body.optString("style")
            val e = if (nome.isEmpty()) null else IconeCruz.Estilo.entries.firstOrNull { it.name == nome } ?: return null
            LayoutHome.trocarIcone(app, t, e)
        }
        return state(app)
    }

    private fun comArte(app: Context, pasta: String?): List<String> {
        val arquivos = pasta?.let { runCatching { app.assets.list("icon-sets/$it")?.toSet() }.getOrNull() }.orEmpty()
        return IconeCruz.Tipo.entries.filter { "${IconSetArt.arquivo(it)}.webp" in arquivos }.map { it.name }
    }

    /** Os bytes da prévia (só nomes do enum entram no caminho: nada de "../"). */
    fun image(app: Context, style: String?, type: String?): ByteArray? {
        val e = IconeCruz.Estilo.entries.firstOrNull { it.name == style }?.pasta ?: return null
        val t = IconeCruz.Tipo.entries.firstOrNull { it.name == type } ?: return null
        return runCatching { app.assets.open("icon-sets/$e/${IconSetArt.arquivo(t)}.webp").use { it.readBytes() } }.getOrNull()
    }
}
