package br.paulo.tvlauncher.network

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Conta AdGuard DNS do dono ligada à TV (27/11/2026, opção 2 escolhida pelo Paulo: conta do dono + controle dos pais).
 *
 * Porquê: o DNS privado da TV (Dns.kt) já aceita os perfis pessoais do AdGuard, mas o dono tinha de criar o perfil no
 * site, copiar o endereço e digitar na TV; e o que a Família decide (hora de dormir) parava nos apps da TV.
 * Para quê: colar a chave no painel uma vez e a TV se cadastra sozinha, liga o DNS privado, mostra no Diagnóstico o que
 * foi consultado e bloqueado, e leva a hora de dormir, os serviços bloqueados e o YouTube seguro para o DNS.
 * Como: API oficial (li em https://api.adguard-dns.io/swagger/openapi.json, versão 1.17.4), cabeçalho
 * "Authorization: ApiKey <chave>". A TV cria UM servidor DNS só dela, com o nome começando por [PREFIXO_SERVIDOR], e
 * um aparelho SMART_TV dentro dele. REGRA: só mexe no servidor que ela mesma criou ([podeMexer]); os servidores e
 * aparelhos que o dono já tinha na conta nunca são alterados nem apagados.
 *
 * Aqui só fica o que roda sem Android (testado no PC em AdGuardDnsTest). A chave fica cifrada em AdGuardArmazem (:app).
 */
object AdGuardDns {
    const val BASE = "https://api.adguard-dns.io/oapi/v1"
    /** Começo do nome do servidor que a TV cria. Também serve de trava: sem ele, a TV não mexe. */
    const val PREFIXO_SERVIDOR = "Sofaly · "
    private const val NOME_MAX = 64   // maxLength de name em DNSServerCreate e DeviceCreate

    /** Dias como a API escreve, na ordem do NoiteRegras (0 = domingo, igual a Calendar.DAY_OF_WEEK - 1). */
    val DIAS = listOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")

    // ---------------------------------------------------------------- chave e nomes

    /**
     * A chave colada no painel, limpa, ou null se não parece uma chave. O formato exato não está na documentação:
     * aceitamos 16 a 256 caracteres de um alfabeto seguro para cabeçalho HTTP (sem espaço, sem quebra de linha).
     */
    fun chaveValida(entrada: String?): String? {
        val k = entrada?.trim()?.removePrefix("ApiKey ")?.trim() ?: return null
        return k.takeIf { it.length in 16..256 && it.all { c -> c.isLetterOrDigit() && c.code < 128 || c in "-_.~+/=" } }
    }

    fun nomeServidor(nomeTv: String): String = (PREFIXO_SERVIDOR + nomeTv.trim().ifEmpty { "TV" }).take(NOME_MAX)
    fun nomeAparelho(nomeTv: String): String = nomeTv.trim().ifEmpty { "Sofaly TV" }.take(NOME_MAX)

    // ---------------------------------------------------------------- o que vem da API

    class Servidor(val id: String, val nome: String, val padrao: Boolean, val aparelhos: Int, val parental: JSONObject?)
    class Aparelho(val id: String, val nome: String, val tipo: String, val servidorId: String, val hostDot: String?)
    class Dominio(val nome: String, val consultas: Long, val bloqueadas: Long)
    class Resumo(val consultas: Long, val bloqueadas: Long, val dominios: List<Dominio>) {
        /** Parte bloqueada, de 0 a 100 (inteiro). Sem consultas = 0. */
        val porcentoBloqueado: Int get() = if (consultas <= 0) 0 else ((bloqueadas * 100) / consultas).toInt()
    }
    class Servico(val id: String, val nome: String)

    fun lerServidores(json: String): List<Servidor> {
        val a = JSONArray(json)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifEmpty { return@mapNotNull null }
            val ids = o.optJSONArray("device_ids")
            Servidor(id, o.optString("name"), o.optBoolean("default"),
                if (o.has("devices_count")) o.optInt("devices_count") else ids?.length() ?: 0,
                o.optJSONObject("settings")?.optJSONObject("parental_control_settings"))
        }
    }

    fun lerAparelho(o: JSONObject): Aparelho? {
        val id = o.optString("id").ifEmpty { return null }
        val tls = o.optJSONObject("dns_addresses")?.optString("dns_over_tls_url")
        return Aparelho(id, o.optString("name"), o.optString("device_type"), o.optString("dns_server_id"), hostDeTls(tls))
    }

    fun lerAparelhos(json: String): List<Aparelho> {
        val a = JSONArray(json)
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::lerAparelho) }
    }

    private val HOST_ADGUARD = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*\\.adguard-dns\\.com$")

    /**
     * "tls://b3e82cd1.adguard-dns.com" -> "b3e82cd1.adguard-dns.com" (o que vai no DNS privado do Android).
     * Só aceita nome dentro de adguard-dns.com: uma resposta adulterada não consegue mandar o DNS da TV para outro lugar.
     */
    fun hostDeTls(url: String?): String? {
        val h = url?.trim()?.lowercase()?.removePrefix("tls://")?.substringBefore('/')?.substringBefore(':') ?: return null
        return h.takeIf { it.length <= 253 && HOST_ADGUARD.matches(it) }
    }

    /** Soma de /stats/time (consultas e bloqueadas no período pedido). */
    fun lerTotais(json: String): Pair<Long, Long> {
        val a = JSONObject(json).optJSONArray("stats") ?: return 0L to 0L
        var q = 0L; var b = 0L
        for (i in 0 until a.length()) {
            val v = a.optJSONObject(i)?.optJSONObject("value") ?: continue
            q += v.optLong("queries"); b += v.optLong("blocked")
        }
        return q to b
    }

    /** /stats/domains, os [limite] mais consultados primeiro. */
    fun lerDominios(json: String, limite: Int = 10): List<Dominio> {
        val a = JSONObject(json).optJSONArray("stats") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val v = o.optJSONObject("value") ?: return@mapNotNull null
            Dominio(o.optString("domain").ifEmpty { return@mapNotNull null }, v.optLong("queries"), v.optLong("blocked"))
        }.sortedByDescending { it.consultas }.take(limite)
    }

    /** /web_services: os serviços que dá para bloquear (YouTube, TikTok...), em ordem de nome. */
    fun lerServicos(json: String): List<Servico> {
        val a = JSONArray(json)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Servico(o.optString("id").ifEmpty { return@mapNotNull null }, o.optString("name"))
        }.sortedBy { it.nome.lowercase() }
    }

    // ---------------------------------------------------------------- a trava: só o servidor da TV

    /**
     * A TV pode alterar este servidor? Só se for o que ela guardou ([idSalvo]), com o nome que ela dá e não for o
     * servidor padrão da conta. O dono pode renomear o servidor no site para "tirar" a TV do controle dele.
     */
    fun podeMexer(idSalvo: String?, s: Servidor): Boolean =
        idSalvo != null && s.id == idSalvo && s.nome.startsWith(PREFIXO_SERVIDOR) && !s.padrao

    // ---------------------------------------------------------------- hora de dormir -> horário da API

    /** Um período bloqueado num dia, em minutos desde 0h, os DOIS inclusivos (é assim que a API conta). */
    data class Periodo(val inicio: Int, val fim: Int)

    /**
     * Converte as janelas da hora de dormir (uma por dia, 0 = domingo; inicio/fim em minutos com FIM EXCLUSIVO, podendo
     * virar a meia-noite, como em NoiteRegras; inicio == fim ou null = dia livre) nos períodos por dia da API.
     * A API não aceita período que passe da meia-noite nem períodos encostados: 21:00–06:30 de sexta vira
     * 21:00–23:59 na sexta e 00:00–06:29 no sábado; pedaços que se tocam no mesmo dia são juntados.
     */
    fun periodosDaSemana(janelas: List<Pair<Int, Int>?>): List<List<Periodo>> {
        val dias = List(7) { mutableListOf<Periodo>() }
        for (d in 0 until 7) {
            val (i, f) = janelas.getOrNull(d) ?: continue
            if (i == f || i !in 0 until 1440 || f !in 0 until 1440) continue
            if (i < f) dias[d] += Periodo(i, f - 1)
            else {
                dias[d] += Periodo(i, 1439)
                if (f > 0) dias[(d + 1) % 7] += Periodo(0, f - 1)
            }
        }
        return dias.map { juntar(it) }
    }

    private fun juntar(l: List<Periodo>): List<Periodo> {
        val r = mutableListOf<Periodo>()
        for (p in l.sortedBy { it.inicio }) {
            val u = r.lastOrNull()
            if (u != null && p.inicio <= u.fim + 1) r[r.size - 1] = Periodo(u.inicio, maxOf(u.fim, p.fim)) else r += p
        }
        return r
    }

    private fun hora(min: Int) = JSONObject().put("hours", min / 60).put("minutes", min % 60)

    /** screen_time_schedule com os 7 dias (lista vazia = dia livre; a API limpa o dia). */
    fun agendaJson(periodos: List<List<Periodo>>): JSONObject {
        val dias = JSONArray()
        for (d in 0 until 7) {
            val ps = JSONArray()
            for (p in periodos.getOrNull(d).orEmpty()) ps.put(JSONObject().put("start", hora(p.inicio)).put("end", hora(p.fim)))
            dias.put(JSONObject().put("day_of_week", DIAS[d]).put("periods", ps))
        }
        return JSONObject().put("daily_schedule", dias)
    }

    /** O que a Família manda para o DNS da TV. */
    class RegrasFamilia(
        val ligado: Boolean,
        val janelasDormir: List<Pair<Int, Int>?>,   // vazia = sem hora de dormir no DNS
        val servicosBloqueados: Set<String>,
        val youtubeSeguro: Boolean,
        val buscaSegura: Boolean,
        val bloquearAdulto: Boolean,
    )

    /**
     * Corpo do PUT /dns_servers/{id}/settings. [servicosAntes] = os que estavam bloqueados no servidor: cada serviço vai
     * com enabled true/false (a API trata a lista como ações), então quem saiu da lista precisa ir como false.
     */
    fun configuracoesJson(r: RegrasFamilia, servicosAntes: Set<String>): JSONObject {
        val servicos = JSONArray()
        for (id in (servicosAntes + r.servicosBloqueados).sorted()) servicos.put(JSONObject().put("id", id).put("enabled", id in r.servicosBloqueados))
        val parental = JSONObject()
            .put("enabled", r.ligado)
            .put("blocked_services", servicos)
            .put("youtube_safe_search_enabled", r.ligado && r.youtubeSeguro)
            .put("engines_safe_search_enabled", r.ligado && r.buscaSegura)
            .put("block_adult_websites_enabled", r.ligado && r.bloquearAdulto)
            .put("screen_time_schedule", agendaJson(if (r.ligado) periodosDaSemana(r.janelasDormir) else emptyList()))
        return JSONObject().put("protection_enabled", true).put("parental_control_settings", parental)
    }

    /** Ids bloqueados agora no servidor (parental_control_settings.blocked_services com enabled = true). */
    fun servicosBloqueados(parental: JSONObject?): Set<String> {
        val a = parental?.optJSONArray("blocked_services") ?: return emptySet()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.takeIf { o -> o.optBoolean("enabled") }?.optString("id")?.ifEmpty { null } }.toSet()
    }

    // ---------------------------------------------------------------- erros

    enum class Erro { CHAVE_RECUSADA, PLANO_NAO_PERMITE, LIMITE_DA_CONTA, MUITOS_PEDIDOS, SERVIDOR_NAO_E_NOSSO, REDE, OUTRO }

    class Falha(val erro: Erro, val codigo: Int = 0) : IOException("adguard: $erro ($codigo)")

    /** Traduz a resposta de erro (código HTTP + corpo ErrorResponse) para o que a tela sabe explicar. Nunca o corpo inteiro. */
    fun erroDe(codigo: Int, corpo: String?): Erro {
        val c = runCatching { JSONObject(corpo ?: "").optString("error_code") }.getOrDefault("")
        return when {
            codigo == 401 || codigo == 403 || c == "ACCESS_FORBIDDEN" -> Erro.CHAVE_RECUSADA
            c == "INSUFFICIENT_LICENSE_TYPE" -> Erro.PLANO_NAO_PERMITE
            c == "RESOURCE_REACHED_LIMIT" || c == "FIELD_REACHED_LIMIT" -> Erro.LIMITE_DA_CONTA
            codigo == 429 || c == "TOO_MANY_REQUESTS" -> Erro.MUITOS_PEDIDOS
            else -> Erro.OUTRO
        }
    }

    // ---------------------------------------------------------------- pedidos

    class Resposta(val codigo: Int, val corpo: String)

    /** Quem faz o pedido HTTP. O de verdade é [HttpTransporte]; o teste passa um falso. */
    fun interface Transporte {
        @Throws(IOException::class)
        fun pedir(metodo: String, url: String, chave: String, corpo: String?): Resposta
    }

    object HttpTransporte : Transporte {
        override fun pedir(metodo: String, url: String, chave: String, corpo: String?): Resposta {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = metodo; c.connectTimeout = 8_000; c.readTimeout = 15_000
                c.instanceFollowRedirects = false   // um redirecionamento levaria a chave para outro host
                c.setRequestProperty("Authorization", "ApiKey $chave")
                c.setRequestProperty("Accept", "application/json")
                c.setRequestProperty("User-Agent", br.paulo.tvlauncher.Http.USER_AGENT)
                if (corpo != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    c.outputStream.use { it.write(corpo.toByteArray(Charsets.UTF_8)) }
                }
                val cod = c.responseCode
                val s = (if (cod in 200..299) c.inputStream else c.errorStream)
                val txt = s?.use { br.paulo.tvlauncher.Http.readLimited(it, 2_000_000, cut = false) }?.toString(Charsets.UTF_8).orEmpty()
                return Resposta(cod, txt)
            } finally { c.disconnect() }
        }
    }

    class Api(private val chave: String, private val transporte: Transporte = HttpTransporte) {
        private fun pedir(metodo: String, caminho: String, corpo: JSONObject? = null): String {
            val r = try { transporte.pedir(metodo, BASE + caminho, chave, corpo?.toString()) } catch (e: Falha) { throw e } catch (e: IOException) { throw Falha(Erro.REDE) }
            if (r.codigo !in 200..299) throw Falha(erroDe(r.codigo, r.corpo), r.codigo)
            return r.corpo
        }
        fun servidores() = lerServidores(pedir("GET", "/dns_servers"))
        fun aparelhos() = lerAparelhos(pedir("GET", "/devices"))
        fun servicos() = lerServicos(pedir("GET", "/web_services"))
        fun criarServidor(nome: String): Servidor = lerServidores("[" + pedir("POST", "/dns_servers",
            JSONObject().put("name", nome).put("settings", JSONObject().put("protection_enabled", true))) + "]").first()
        fun criarAparelho(nome: String, servidorId: String): Aparelho = lerAparelho(JSONObject(pedir("POST", "/devices",
            JSONObject().put("name", nome).put("device_type", "SMART_TV").put("dns_server_id", servidorId))))
            ?: throw Falha(Erro.OUTRO)
        fun gravarConfiguracoes(servidorId: String, corpo: JSONObject) { pedir("PUT", "/dns_servers/${enc(servidorId)}/settings", corpo) }
        fun resumo24h(servidorId: String): Resumo {
            val filtro = "timespan=LAST_HOUR_24&dns_servers=" + enc(servidorId)
            val (q, b) = lerTotais(pedir("GET", "/stats/time?$filtro"))
            return Resumo(q, b, lerDominios(pedir("GET", "/stats/domains?$filtro&limit=10")))
        }
        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }

    // ---------------------------------------------------------------- ligar a TV na conta

    /** O que a TV guarda depois de ligar: os ids dela na conta e o nome para o DNS privado. */
    class Ligacao(val servidorId: String, val aparelhoId: String, val hostDot: String)

    /**
     * Acha (ou cria) o servidor e o aparelho DESTA TV. Reaproveita os ids guardados quando ainda existem e ainda são
     * nossos; nunca pega um servidor do dono que não começa com [PREFIXO_SERVIDOR].
     */
    fun ligar(api: Api, nomeTv: String, servidorSalvo: String?, aparelhoSalvo: String?): Ligacao {
        val servidor = api.servidores().firstOrNull { podeMexer(servidorSalvo, it) } ?: api.criarServidor(nomeServidor(nomeTv))
        if (!servidor.nome.startsWith(PREFIXO_SERVIDOR) || servidor.padrao) throw Falha(Erro.SERVIDOR_NAO_E_NOSSO)
        val aparelho = api.aparelhos().firstOrNull { it.id == aparelhoSalvo && it.servidorId == servidor.id }
            ?: api.criarAparelho(nomeAparelho(nomeTv), servidor.id)
        val host = aparelho.hostDot ?: throw Falha(Erro.OUTRO)
        return Ligacao(servidor.id, aparelho.id, host)
    }

    /** Leva as regras da Família para o servidor da TV. Recusa se o servidor não for o nosso. */
    fun aplicarFamilia(api: Api, servidorId: String, regras: RegrasFamilia) {
        val s = api.servidores().firstOrNull { it.id == servidorId } ?: throw Falha(Erro.SERVIDOR_NAO_E_NOSSO)
        if (!podeMexer(servidorId, s)) throw Falha(Erro.SERVIDOR_NAO_E_NOSSO)
        api.gravarConfiguracoes(servidorId, configuracoesJson(regras, servicosBloqueados(s.parental)))
    }
}
