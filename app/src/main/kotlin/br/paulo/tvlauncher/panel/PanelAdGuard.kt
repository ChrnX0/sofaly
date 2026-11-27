package br.paulo.tvlauncher.panel

import br.paulo.tvlauncher.network.AdGuardDns
import br.paulo.tvlauncher.network.AdGuardStore
import br.paulo.tvlauncher.network.AdGuardTv
import br.paulo.tvlauncher.network.Dns
import br.paulo.tvlauncher.panel.PainelWeb.Resposta
import org.json.JSONArray
import org.json.JSONObject

/**
 * Painel web: conta AdGuard DNS do dono (27/11/2026; ver AdGuardTv). Rotas /api/adguard/… (nomes novos em inglês).
 * A chave NUNCA volta para o navegador: só os 4 últimos caracteres. Esquecer pede o PIN de novo.
 */

/** Texto curto do erro para o painel (em português; o i18n do painel traduz). */
private fun textoErro(e: AdGuardDns.Erro): String = when (e) {
    AdGuardDns.Erro.CHAVE_RECUSADA -> "o AdGuard recusou a chave (confira se ela foi copiada inteira)"
    AdGuardDns.Erro.PLANO_NAO_PERMITE -> "o plano da conta AdGuard não permite isto"
    AdGuardDns.Erro.LIMITE_DA_CONTA -> "a conta AdGuard chegou ao limite de aparelhos ou servidores"
    AdGuardDns.Erro.MUITOS_PEDIDOS -> "o AdGuard pediu para esperar; tente de novo em 1 minuto"
    AdGuardDns.Erro.SERVIDOR_NAO_E_NOSSO -> "o servidor da TV no AdGuard foi renomeado ou apagado; ligue de novo"
    AdGuardDns.Erro.REDE -> "a TV não conseguiu falar com o AdGuard (internet?)"
    AdGuardDns.Erro.OUTRO -> "o AdGuard respondeu algo inesperado"
}

internal fun PainelWeb.adguardEstado(): JSONObject {
    val dns = Dns.atual(app)
    val host = AdGuardStore.hostDot(app)
    val s = AdGuardStore.ultimaSincronia(app)
    return JSONObject()
        .put("chave", AdGuardStore.finalDaChave(app) ?: JSONObject.NULL)
        .put("ligada", AdGuardStore.ligada(app))
        .put("dnsAtivo", host != null && dns.modo == Dns.Modo.HOST && dns.host.equals(host, ignoreCase = true))
        .put("familia", JSONObject()
            .put("ligada", AdGuardStore.familiaLigada(app))
            .put("dormir", AdGuardStore.dormirNoDns(app))
            .put("youtubeSeguro", AdGuardStore.youtubeSeguro(app))
            .put("buscaSegura", AdGuardStore.buscaSegura(app))
            .put("adulto", AdGuardStore.bloquearAdulto(app))
            .put("servicos", JSONArray(AdGuardStore.servicos(app).sorted())))
        .put("sincronia", if (s == null) JSONObject.NULL else JSONObject().put("em", s.first).put("resultado", s.second))
}

/** {"chave": "..."}: confere com uma leitura (servidores e aparelhos) ANTES de guardar. */
internal fun PainelWeb.adguardChave(req: Requisicao, ip: String): Resposta {
    val o = corpoJson(req) ?: return erro(400, "JSON inválido")
    val c = AdGuardDns.chaveValida(o.optString("chave")) ?: return erro(400, "chave inválida (cole a chave inteira criada no AdGuard DNS)")
    val (servidores, aparelhos) = try { AdGuardDns.Api(c).let { it.servidores().size to it.aparelhos().size } }
        catch (e: AdGuardDns.Falha) { PainelArmazem.registrar(app, ip, "adguard: chave", e.erro.name); return erro(502, textoErro(e.erro)) }
    if (!AdGuardStore.definirChave(app, c)) return erro(500, "não consegui guardar a chave cifrada")
    PainelArmazem.registrar(app, ip, "adguard: chave", "ok")
    return json(200, adguardEstado().put("conta", JSONObject().put("servidores", servidores).put("aparelhos", aparelhos)))
}

/** Cria (ou reencontra) o servidor "Sofaly · <TV>" e liga o DNS privado. Demora até ~30 s (teste de resolução). */
internal fun PainelWeb.adguardLigar(ip: String): Resposta {
    if (!AdGuardStore.temChave(app)) return erro(400, "salve a chave antes")
    val ok = try { AdGuardTv.ligar(app) } catch (e: AdGuardDns.Falha) {
        PainelArmazem.registrar(app, ip, "adguard: ligar", e.erro.name); return erro(502, textoErro(e.erro))
    }
    PainelArmazem.registrar(app, ip, "adguard: ligar", if (ok) "ok" else "DNS não resolveu; voltou ao anterior")
    return json(200, adguardEstado().put("resolveu", ok))
}

/** Família no DNS: {"ligada","dormir","youtubeSeguro","buscaSegura","adulto","servicos":[ids]}. */
internal fun PainelWeb.adguardFamilia(req: Requisicao, token: String?, ip: String): Resposta {
    // Mexer no controle dos pais pede o PIN de novo (como desligar a hora de dormir na TV): quem só pegou o celular
    // destravado não libera o YouTube.
    if (!sessoes.elevada(token)) return precisaPin()
    val o = corpoJson(req) ?: return erro(400, "JSON inválido")
    val ids = o.optJSONArray("servicos")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        .filter { it.matches(Regex("^[a-z0-9_.-]{1,64}$")) }.toSet()
    AdGuardStore.guardarFamilia(app, o.optBoolean("ligada"), o.optBoolean("dormir", true), o.optBoolean("youtubeSeguro", true),
        o.optBoolean("buscaSegura", true), o.optBoolean("adulto", true), ids)
    val e = AdGuardTv.sincronizarFamilia(app, forcar = true)
    PainelArmazem.registrar(app, ip, "adguard: família", e?.name ?: "ok")
    return if (e != null) erro(502, textoErro(e)) else json(200, adguardEstado())
}

internal fun PainelWeb.adguardServicos(): Resposta {
    val l = try { AdGuardTv.servicos(app) } catch (e: AdGuardDns.Falha) { return erro(502, textoErro(e.erro)) }
    val a = JSONArray()
    for (s in l) a.put(JSONObject().put("id", s.id).put("nome", s.nome))
    return json(200, JSONObject().put("servicos", a))
}

internal fun PainelWeb.adguardResumo(req: Requisicao): Resposta {
    val r = try { AdGuardTv.resumo(app, forcar = req.consulta["atualizar"] == "1") } catch (e: AdGuardDns.Falha) { return erro(502, textoErro(e.erro)) }
        ?: return erro(400, "a TV ainda não está ligada ao AdGuard")
    val d = JSONArray()
    for (x in r.dominios) d.put(JSONObject().put("nome", x.nome).put("consultas", x.consultas).put("bloqueadas", x.bloqueadas))
    return json(200, JSONObject().put("consultas", r.consultas).put("bloqueadas", r.bloqueadas)
        .put("porcento", r.porcentoBloqueado).put("dominios", d))
}

/** Desliga na TV (DNS de antes de volta, chave apagada). A conta AdGuard fica como está. Pede o PIN de novo. */
internal fun PainelWeb.adguardEsquecer(token: String?, ip: String): Resposta {
    if (!sessoes.elevada(token)) return precisaPin()
    AdGuardTv.desligar(app)
    PainelArmazem.registrar(app, ip, "adguard: esquecer", "ok")
    return json(200, adguardEstado())
}
