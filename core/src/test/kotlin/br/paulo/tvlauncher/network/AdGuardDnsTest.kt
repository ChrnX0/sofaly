package br.paulo.tvlauncher.network

import br.paulo.tvlauncher.network.AdGuardDns.Periodo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Formatos conferidos com https://api.adguard-dns.io/swagger/openapi.json (1.17.4) em 27/11/2026. */
class AdGuardDnsTest {

    @Test fun chave() {
        assertEquals("abcDEF0123456789_-", AdGuardDns.chaveValida("  abcDEF0123456789_-\n"))
        assertEquals("abcDEF0123456789xx", AdGuardDns.chaveValida("ApiKey abcDEF0123456789xx"))
        assertNull(AdGuardDns.chaveValida("curta"))
        assertNull(AdGuardDns.chaveValida("abc def 0123456789abc"))
        assertNull(AdGuardDns.chaveValida("abcdef0123456789\r\nX-Evil: 1"))
        assertNull(AdGuardDns.chaveValida("çãoçãoçãoçãoçãoçãoção"))
        assertNull(AdGuardDns.chaveValida(null))
    }

    @Test fun nomes() {
        assertEquals("Sofaly · Sala", AdGuardDns.nomeServidor(" Sala "))
        assertEquals("Sofaly · TV", AdGuardDns.nomeServidor(""))
        assertEquals(64, AdGuardDns.nomeServidor("x".repeat(100)).length)
        assertEquals("Sofaly TV", AdGuardDns.nomeAparelho("  "))
    }

    @Test fun hostSoDaAdguard() {
        assertEquals("b3e82cd1.adguard-dns.com", AdGuardDns.hostDeTls("tls://b3e82cd1.adguard-dns.com"))
        assertEquals("b3e82cd1.d.adguard-dns.com", AdGuardDns.hostDeTls("tls://B3E82CD1.d.adguard-dns.com:853"))
        assertNull(AdGuardDns.hostDeTls("tls://evil.com"))
        assertNull(AdGuardDns.hostDeTls("tls://adguard-dns.com.evil.com"))
        assertNull(AdGuardDns.hostDeTls("tls://x.evil-adguard-dns.com"))
        assertNull(AdGuardDns.hostDeTls(null))
    }

    @Test fun meiaNoiteViraDoisDias() {
        // sexta (5) 21:00–06:30 -> sexta 21:00–23:59 e sábado 00:00–06:29
        val janelas = MutableList<Pair<Int, Int>?>(7) { null }.also { it[5] = 21 * 60 to 6 * 60 + 30 }
        val p = AdGuardDns.periodosDaSemana(janelas)
        assertEquals(listOf(Periodo(1260, 1439)), p[5])
        assertEquals(listOf(Periodo(0, 389)), p[6])
        assertTrue(p[0].isEmpty())
    }

    @Test fun sabadoVoltaParaDomingo() {
        val janelas = MutableList<Pair<Int, Int>?>(7) { null }.also { it[6] = 22 * 60 to 7 * 60 }
        val p = AdGuardDns.periodosDaSemana(janelas)
        assertEquals(listOf(Periodo(0, 419)), p[0])
        assertEquals(listOf(Periodo(1320, 1439)), p[6])
    }

    @Test fun semanaInteiraJuntaOsPedacos() {
        // todo dia 21:00–06:30: cada dia tem 00:00–06:29 e 21:00–23:59, sem encostar
        val p = AdGuardDns.periodosDaSemana(List(7) { 1260 to 390 })
        for (d in 0 until 7) assertEquals(listOf(Periodo(0, 389), Periodo(1260, 1439)), p[d])
        // 18:00–24:00 + 00:00 seguinte encostam: vira um só no dia
        val q = AdGuardDns.periodosDaSemana(List(7) { 0 to 1080 } )
        assertEquals(listOf(Periodo(0, 1079)), q[3])
    }

    @Test fun diaLivreEValoresRuins() {
        val p = AdGuardDns.periodosDaSemana(listOf(600 to 600, null, -5 to 30, 30 to 5000))
        assertTrue(p.all { it.isEmpty() })
        val fimMeiaNoite = AdGuardDns.periodosDaSemana(listOf(1200 to 0))
        assertEquals(listOf(Periodo(1200, 1439)), fimMeiaNoite[0])
        assertTrue(fimMeiaNoite[1].isEmpty())
    }

    @Test fun agendaNoFormatoDaApi() {
        val j = AdGuardDns.agendaJson(AdGuardDns.periodosDaSemana(MutableList<Pair<Int, Int>?>(7) { null }.also { it[1] = 1260 to 390 }))
        val dias = j.getJSONArray("daily_schedule")
        assertEquals(7, dias.length())
        assertEquals("SUNDAY", dias.getJSONObject(0).getString("day_of_week"))
        val seg = dias.getJSONObject(1)
        assertEquals("MONDAY", seg.getString("day_of_week"))
        val per = seg.getJSONArray("periods").getJSONObject(0)
        assertEquals(21, per.getJSONObject("start").getInt("hours"))
        assertEquals(23, per.getJSONObject("end").getInt("hours"))
        assertEquals(59, per.getJSONObject("end").getInt("minutes"))
        val ter = dias.getJSONObject(2).getJSONArray("periods").getJSONObject(0)
        assertEquals(6, ter.getJSONObject("end").getInt("hours")); assertEquals(29, ter.getJSONObject("end").getInt("minutes"))
        assertEquals(0, dias.getJSONObject(4).getJSONArray("periods").length())
    }

    private fun regras(ligado: Boolean = true, servicos: Set<String> = setOf("tiktok")) =
        AdGuardDns.RegrasFamilia(ligado, List(7) { 1260 to 390 }, servicos, youtubeSeguro = true, buscaSegura = true, bloquearAdulto = false)

    @Test fun configuracoesDesmarcamQuemSaiu() {
        val j = AdGuardDns.configuracoesJson(regras(), servicosAntes = setOf("tiktok", "youtube"))
        val p = j.getJSONObject("parental_control_settings")
        val s = p.getJSONArray("blocked_services")
        val mapa = (0 until s.length()).associate { s.getJSONObject(it).getString("id") to s.getJSONObject(it).getBoolean("enabled") }
        assertEquals(mapOf("tiktok" to true, "youtube" to false), mapa)
        assertTrue(p.getBoolean("youtube_safe_search_enabled"))
        assertFalse(p.getBoolean("block_adult_websites_enabled"))
        assertTrue(j.getBoolean("protection_enabled"))
    }

    @Test fun desligadoLimpaHorarioESeguro() {
        val p = AdGuardDns.configuracoesJson(regras(ligado = false), emptySet()).getJSONObject("parental_control_settings")
        assertFalse(p.getBoolean("enabled"))
        assertFalse(p.getBoolean("youtube_safe_search_enabled"))
        val dias = p.getJSONObject("screen_time_schedule").getJSONArray("daily_schedule")
        assertEquals(7, dias.length())
        for (d in 0 until 7) assertEquals(0, dias.getJSONObject(d).getJSONArray("periods").length())
    }

    @Test fun estatisticas() {
        val t = """{"stats":[{"time_millis":1,"value":{"queries":86,"blocked":14,"companies":4}},{"time_millis":2,"value":{"queries":14,"blocked":6,"companies":1}}]}"""
        assertEquals(100L to 20L, AdGuardDns.lerTotais(t))
        val d = """{"navigation":{},"stats":[{"domain":"a.com","value":{"queries":3,"blocked":0,"companies":1}},{"domain":"ads.com","value":{"queries":9,"blocked":9,"companies":1}}]}"""
        val l = AdGuardDns.lerDominios(d)
        assertEquals(listOf("ads.com", "a.com"), l.map { it.nome })
        assertEquals(20, AdGuardDns.Resumo(100, 20, l).porcentoBloqueado)
        assertEquals(0, AdGuardDns.Resumo(0, 0, emptyList()).porcentoBloqueado)
    }

    @Test fun erros() {
        assertEquals(AdGuardDns.Erro.CHAVE_RECUSADA, AdGuardDns.erroDe(401, ""))
        assertEquals(AdGuardDns.Erro.PLANO_NAO_PERMITE, AdGuardDns.erroDe(400, """{"error_code":"INSUFFICIENT_LICENSE_TYPE","fields":[]}"""))
        assertEquals(AdGuardDns.Erro.LIMITE_DA_CONTA, AdGuardDns.erroDe(400, """{"error_code":"RESOURCE_REACHED_LIMIT","fields":[]}"""))
        assertEquals(AdGuardDns.Erro.MUITOS_PEDIDOS, AdGuardDns.erroDe(429, "não é json"))
        assertEquals(AdGuardDns.Erro.OUTRO, AdGuardDns.erroDe(500, null))
    }

    // ---------------------------------------------------------------- conta falsa: a trava "só o nosso"

    /** Conta falsa em memória. Guarda cada pedido que mexe em algo, para provar o que a TV alterou. */
    private class ContaFalsa(servidores: List<JSONObject>, aparelhos: List<JSONObject> = emptyList()) : AdGuardDns.Transporte {
        val servidores = servidores.toMutableList()
        val aparelhos = aparelhos.toMutableList()
        val escritas = mutableListOf<String>()
        var chaveVista: String? = null
        private var n = 0
        override fun pedir(metodo: String, url: String, chave: String, corpo: String?): AdGuardDns.Resposta {
            chaveVista = chave
            assertTrue(url.startsWith(AdGuardDns.BASE))
            val c = url.removePrefix(AdGuardDns.BASE)
            if (metodo != "GET") escritas += "$metodo $c"
            return when {
                metodo == "GET" && c == "/dns_servers" -> AdGuardDns.Resposta(200, JSONArray(servidores).toString())
                metodo == "GET" && c == "/devices" -> AdGuardDns.Resposta(200, JSONArray(aparelhos).toString())
                metodo == "POST" && c == "/dns_servers" -> {
                    val o = JSONObject().put("id", "novo${++n}").put("name", JSONObject(corpo!!).getString("name")).put("default", false).put("devices_count", 0)
                    servidores += o; AdGuardDns.Resposta(200, o.toString())
                }
                metodo == "POST" && c == "/devices" -> {
                    val b = JSONObject(corpo!!); val id = "ap${++n}"
                    val o = JSONObject().put("id", id).put("name", b.getString("name")).put("device_type", b.getString("device_type"))
                        .put("dns_server_id", b.getString("dns_server_id")).put("dns_addresses", JSONObject().put("dns_over_tls_url", "tls://$id.adguard-dns.com"))
                    aparelhos += o; AdGuardDns.Resposta(200, o.toString())
                }
                metodo == "PUT" && c.startsWith("/dns_servers/") -> AdGuardDns.Resposta(200, "")
                else -> AdGuardDns.Resposta(404, "")
            }
        }
    }

    private fun srv(id: String, nome: String, padrao: Boolean = false) =
        JSONObject().put("id", id).put("name", nome).put("default", padrao).put("devices_count", 3)

    @Test fun ligarCriaOProprioServidorENaoTocaNosDoDono() {
        val conta = ContaFalsa(listOf(srv("casa", "Casa", padrao = true), srv("filhos", "Filhos")))
        val l = AdGuardDns.ligar(AdGuardDns.Api("k".repeat(20), conta), "Sala", null, null)
        assertEquals("novo1", l.servidorId)
        assertEquals("ap2.adguard-dns.com", l.hostDot)
        assertEquals(listOf("POST /dns_servers", "POST /devices"), conta.escritas)
        assertEquals("Sofaly · Sala", conta.servidores.last().getString("name"))
        assertEquals("k".repeat(20), conta.chaveVista)
    }

    @Test fun ligarDeNovoReaproveita() {
        val conta = ContaFalsa(listOf(srv("s1", "Sofaly · Sala")),
            listOf(JSONObject().put("id", "a1").put("name", "Sala").put("device_type", "SMART_TV").put("dns_server_id", "s1")
                .put("dns_addresses", JSONObject().put("dns_over_tls_url", "tls://a1.adguard-dns.com"))))
        val l = AdGuardDns.ligar(AdGuardDns.Api("k".repeat(20), conta), "Sala", "s1", "a1")
        assertEquals("s1", l.servidorId); assertEquals("a1", l.aparelhoId)
        assertTrue(conta.escritas.isEmpty())
    }

    @Test fun servidorRenomeadoPeloDonoSaiDoControle() {
        val conta = ContaFalsa(listOf(srv("s1", "Meu servidor")))
        val api = AdGuardDns.Api("k".repeat(20), conta)
        try { AdGuardDns.aplicarFamilia(api, "s1", regras()); fail() } catch (e: AdGuardDns.Falha) {
            assertEquals(AdGuardDns.Erro.SERVIDOR_NAO_E_NOSSO, e.erro)
        }
        assertTrue(conta.escritas.isEmpty())
        // e ao ligar de novo, cria outro em vez de pegar o dele
        AdGuardDns.ligar(api, "Sala", "s1", null)
        assertEquals("POST /dns_servers", conta.escritas.first())
    }

    @Test fun aplicarFamiliaSoNoNosso() {
        val conta = ContaFalsa(listOf(srv("casa", "Casa", padrao = true), srv("s1", "Sofaly · Sala")))
        AdGuardDns.aplicarFamilia(AdGuardDns.Api("k".repeat(20), conta), "s1", regras())
        assertEquals(listOf("PUT /dns_servers/s1/settings"), conta.escritas)
        try { AdGuardDns.aplicarFamilia(AdGuardDns.Api("k".repeat(20), conta), "casa", regras()); fail() } catch (e: AdGuardDns.Falha) {
            assertEquals(AdGuardDns.Erro.SERVIDOR_NAO_E_NOSSO, e.erro)
        }
    }

    @Test fun chaveRecusadaViraErroClaro() {
        val api = AdGuardDns.Api("k".repeat(20)) { _, _, _, _ -> AdGuardDns.Resposta(401, """{"error_code":"ACCESS_FORBIDDEN"}""") }
        try { api.servidores(); fail() } catch (e: AdGuardDns.Falha) { assertEquals(AdGuardDns.Erro.CHAVE_RECUSADA, e.erro) }
        val semRede = AdGuardDns.Api("k".repeat(20)) { _, _, _, _ -> throw java.net.UnknownHostException("x") }
        try { semRede.servidores(); fail() } catch (e: AdGuardDns.Falha) { assertEquals(AdGuardDns.Erro.REDE, e.erro) }
    }
}
