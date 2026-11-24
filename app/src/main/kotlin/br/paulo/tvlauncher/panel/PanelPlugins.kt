package br.paulo.tvlauncher.panel

import android.content.Context
import android.content.pm.PackageManager
import br.paulo.tvlauncher.BuildConfig
import br.paulo.tvlauncher.PluginTilesTv
import br.paulo.tvlauncher.PluginsTv
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.home.PacoteIcones
import br.paulo.tvlauncher.player.LojaCurada
import br.paulo.tvlauncher.screensaver.ExtensionHealth
import br.paulo.tvlauncher.screensaver.PluginCartao
import br.paulo.tvlauncher.screensaver.PluginCatalog
import br.paulo.tvlauncher.screensaver.PluginTile
import br.paulo.tvlauncher.screensaver.WallpaperPlugins
import br.paulo.tvlauncher.settings.Planos
import br.paulo.tvlauncher.settings.Tema
import org.json.JSONArray
import org.json.JSONObject

/**
 * Plugins no painel web (24/11/2026, proposta B das extensões): o mesmo que Configurações › Tela inicial › Plugins na
 * TV ([br.paulo.tvlauncher.PluginsActivity]), para quem prefere o celular. Fundos, pacotes de ícones, cartões e a
 * vitrine curada. Todo texto vem da TV já traduzido (strings wp_/plg_), então o painel não ganha frase nova para os 14
 * idiomas. Toda mudança pede o PIN de novo (muda o que a tela inicial mostra e dá lugar a app de terceiro).
 *
 *   GET  /api/plugins                  estado + textos
 *   POST /api/plugins/fundo    {id}    usar o plugin de fundo id ("" = parar o que está em uso)      (PIN)
 *   POST /api/plugins/icones   {pacote} usar o pacote ("" = voltar aos ícones da Sofaly)               (PIN)
 *   POST /api/plugins/cartao   {pacote, ligado}                                                       (PIN)
 *   POST /api/plugins/botao    {pacote, ligado}   botão de outro app no painel rápido                (PIN)
 *   POST /api/plugins/instalar {id}    instala da vitrine pelo Release oficial (fora da Play)         (PIN)
 *
 * O registro do painel (Diário, MCP diario_tv) só leva o evento, nunca o nome do plugin (app de terceiro).
 */
object PanelPlugins {
    private fun ok(o: JSONObject) = 200 to o.put("ok", true)
    private fun erro(codigo: Int, msg: String, motivo: String) = codigo to JSONObject().put("erro", msg).put("motivo", motivo)
    private fun pin() = 403 to JSONObject().put("erro", "confirme o PIN").put("precisaPin", true)

    private fun instaladoNoSistema(app: Context, p: String) =
        runCatching { app.packageManager.getApplicationInfo(p, PackageManager.MATCH_DISABLED_COMPONENTS); true }.getOrDefault(false)

    fun estado(app: Context): JSONObject {
        val fundos = WallpaperPlugins.instalados(app)
        val pacotes = PacoteIcones.deFora(app)
        val cartoes = PluginsTv.instalados(app)
        val escolhido = WallpaperPlugins.escolhidoId(app)
        val icones = PacoteIcones.escolhido(app)
        val temPlay = instaladoNoSistema(app, "com.android.vending")
        val ja = (fundos.map { it.pacote } + pacotes.map { it.first }).toSet()
        return JSONObject()
            .put("pro", Planos.permite(app, Planos.Recurso.PLUGINS))
            .put("max", PluginCartao.MAX_PLUGINS)
            .put("textos", JSONObject()
                .put("titulo", app.getString(R.string.plg_titulo)).put("det", app.getString(R.string.wp_det))
                .put("fundos", app.getString(R.string.wp_secao_fundos)).put("icones", app.getString(R.string.wp_secao_icones))
                .put("cartoes", app.getString(R.string.wp_secao_cartoes)).put("descobrir", app.getString(R.string.wp_secao_descobrir))
                .put("nenhumFundo", app.getString(R.string.wp_nenhum_fundo)).put("nenhumIcone", app.getString(R.string.wp_nenhum_icone))
                .put("nenhumCartao", app.getString(R.string.plg_nenhum)).put("emUso", app.getString(R.string.wp_em_uso))
                .put("usarFundo", app.getString(R.string.wp_usar)).put("parar", app.getString(R.string.wp_parar))
                .put("usarIcones", app.getString(R.string.wp_usar_icones)).put("iconesDet", app.getString(R.string.wp_icones_det))
                .put("ligado", app.getString(R.string.plg_ligado)).put("desligado", app.getString(R.string.plg_desligado))
                .put("instalado", app.getString(R.string.wp_instalado)).put("instalar", app.getString(R.string.wp_instalar))
                .put("instalando", app.getString(R.string.wp_instalando)).put("abrirPlay", app.getString(R.string.wp_abrir_play))
                .put("seguranca", app.getString(R.string.plg_seguranca))
                .put("limite", app.getString(R.string.plg_limite, PluginCartao.MAX_PLUGINS))
                .put("semCert", app.getString(R.string.wp_sem_cert))
                .put("saude", app.getString(R.string.ext_off_health))
                .put("botoes", app.getString(R.string.ext_secao_botoes)).put("nenhumBotao", app.getString(R.string.ext_nenhum_botao)))
            .put("fundos", JSONArray().apply {
                fundos.forEach { f ->
                    val emUso = f.id == escolhido && Tema.atual(app) == Tema.PLUGIN && WallpaperPlugins.permitido(app, f.pacote)
                    put(JSONObject().put("id", f.id).put("nome", PluginCatalog.nomeDoPacote(f.pacote) ?: f.nome).put("emUso", emUso)
                        .put("saude", !emUso && ExtensionHealth.desligadoEm(app, f.pacote) > 0))
                }
            })
            .put("icones", JSONArray().apply {
                pacotes.forEach { (pac, nome) ->
                    put(JSONObject().put("pacote", pac).put("nome", PluginCatalog.nomeDoPacote(pac) ?: nome).put("emUso", pac == icones))
                }
            })
            .put("cartoes", JSONArray().apply {
                cartoes.forEach { c ->
                    val ligado = PluginsTv.permitido(app, c.pacote)
                    put(JSONObject().put("pacote", c.pacote).put("nome", c.nome).put("ligado", ligado)
                        .put("saude", !ligado && ExtensionHealth.desligadoEm(app, c.pacote) > 0))
                }
            })
            .put("botoes", JSONArray().apply {
                PluginTilesTv.instalados(app).forEach { c ->
                    val ligado = PluginTilesTv.permitido(app, c.pacote)
                    put(JSONObject().put("pacote", c.pacote).put("nome", c.nome).put("ligado", ligado)
                        .put("saude", !ligado && ExtensionHealth.desligadoEm(app, c.pacote) > 0))
                }
            })
            .put("vitrine", JSONArray().apply {
                PluginsTv.vitrine(app).forEach { e ->
                    val pac = e.pacote ?: e.lojaId?.let { LojaCurada.pacoteGuardado(app, it) }
                    val instalado = pac != null && (pac in ja || instaladoNoSistema(app, pac))
                    put(JSONObject().put("id", e.id).put("nome", e.nome).put("descricao", PluginsTv.descricao(app, e))
                        .put("tipo", app.getString(if (e.tipo == PluginCatalog.Tipo.FUNDO) R.string.wp_tipo_fundo else R.string.wp_tipo_icones))
                        .put("instalado", instalado).put("pagina", PluginCatalog.pagina(e) ?: JSONObject.NULL)
                        .put("acao", PluginCatalog.acao(e, BuildConfig.PLAY, temPlay).name))
                }
            })
    }

    fun rotear(app: Context, metodo: String, rota: String, corpo: JSONObject?, elevada: Boolean, ip: String): Pair<Int, JSONObject>? {
        if (metodo == "GET" && rota == "") {
            br.paulo.tvlauncher.Fundo.rodar("plugin-catalog") { PluginsTv.atualizarVitrine(app) }
            return ok(estado(app))
        }
        if (metodo != "POST" || rota !in setOf("/fundo", "/icones", "/cartao", "/botao", "/instalar")) return null
        if (!elevada) return pin()
        val c = corpo ?: JSONObject()
        // Ligar algo de terceiro é do Pro (como na TV); desligar/voltar ao nosso vale sempre.
        val liga = when (rota) {
            "/fundo" -> c.optString("id").isNotEmpty()
            "/icones" -> c.optString("pacote").isNotEmpty()
            "/cartao", "/botao" -> c.optBoolean("ligado")
            else -> true
        }
        if (liga && !Planos.permite(app, Planos.Recurso.PLUGINS)) return erro(402, app.getString(R.string.pl_pro), "PRO")
        when (rota) {
            "/fundo" -> {
                val id = c.optString("id")
                if (id.isEmpty()) {
                    WallpaperPlugins.escolhido(app)?.let { WallpaperPlugins.parar(app, it) } ?: WallpaperPlugins.esquecer(app)
                } else {
                    val f = WallpaperPlugins.instalados(app).firstOrNull { it.id == id } ?: return erro(404, "plugin não instalado", "INVALIDO")
                    if (!WallpaperPlugins.usar(app, f)) return erro(422, app.getString(R.string.wp_sem_cert), "SEM_CERT")
                }
                PainelArmazem.registrar(app, ip, "plugin de fundo trocado", "ok")
            }
            "/icones" -> {
                val pac = c.optString("pacote")
                if (pac.isNotEmpty() && PacoteIcones.deFora(app).none { it.first == pac }) return erro(404, "pacote não instalado", "INVALIDO")
                PacoteIcones.escolher(app, pac)
                PainelArmazem.registrar(app, ip, "pacote de ícones trocado", "ok")
            }
            "/cartao" -> {
                val pac = c.optString("pacote")
                if (PluginsTv.instalados(app).none { it.pacote == pac }) return erro(404, "plugin não instalado", "INVALIDO")
                if (!PluginsTv.definir(app, pac, c.optBoolean("ligado")))
                    return erro(422, app.getString(R.string.plg_limite, PluginCartao.MAX_PLUGINS), "CHEIO")
                PainelArmazem.registrar(app, ip, "plugin de cartão trocado", "ok")
            }
            "/botao" -> {
                val pac = c.optString("pacote")
                if (PluginTilesTv.instalados(app).none { it.pacote == pac }) return erro(404, "plugin não instalado", "INVALIDO")
                if (!PluginTilesTv.definir(app, pac, c.optBoolean("ligado")))
                    return erro(422, app.getString(R.string.plg_limite, PluginTile.MAX), "CHEIO")
                PainelArmazem.registrar(app, ip, "botão de plugin trocado", "ok")
            }
            "/instalar" -> {
                val e = PluginsTv.vitrine(app).firstOrNull { it.id == c.optString("id") } ?: return erro(404, "fora da vitrine", "INVALIDO")
                val temPlay = instaladoNoSistema(app, "com.android.vending")
                val id = e.lojaId
                if (PluginCatalog.acao(e, BuildConfig.PLAY, temPlay) != PluginCatalog.Acao.INSTALAR || id == null)
                    return erro(422, "instale pela página", "SO_PAGINA")
                val r = LojaCurada.instalar(app, id)
                if (r.startsWith("erro:")) return erro(502, app.getString(R.string.wp_nao_instalou, r.removePrefix("erro:").trim()), "FALHOU")
                PainelArmazem.registrar(app, ip, "plugin instalado", "ok")
            }
        }
        return ok(estado(app))
    }
}
