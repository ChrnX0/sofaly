package br.paulo.onetv.celular

import android.content.Context
import android.webkit.CookieManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.io.InputStream
import java.net.URL
import java.net.URLEncoder
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Conversa direta com o painel da TV, sem a página (estudo global 20/11/2026, ideias 14-16): o controle nativo, a
 * notificação e o bloco das Configurações rápidas mandam teclas por aqui.
 *
 * Segurança igual à do WebView ([PainelActivity]): só o https de casa com o certificado que veio no QR ([Pareamento.confere]),
 * o mesmo cookie de sessão que o painel ganhou ao entrar com o PIN, e os cabeçalhos que o painel exige num POST
 * (Origin igual ao próprio endereço e X-Painel: 1). Sem cookie, a TV responde 401 e o app abre o painel para entrar.
 * Nunca roda na thread da tela: quem chama usa [emSegundoPlano].
 */
object ApiTv {
    /** Última TV usada no controle: a notificação e o bloco rápido falam com ela. */
    fun ultima(ctx: Context): Pareamento.Tv? =
        Tvs.porChave(ctx, ctx.getSharedPreferences("controle", Context.MODE_PRIVATE).getString("ultima", null))

    fun esquecerUltima(ctx: Context) =
        ctx.getSharedPreferences("controle", Context.MODE_PRIVATE).edit().remove("ultima").apply()

    fun lembrar(ctx: Context, tv: Pareamento.Tv) =
        ctx.getSharedPreferences("controle", Context.MODE_PRIVATE).edit().putString("ultima", Pareamento.chave(tv)).apply()

    /** O controle nativo só existe com a TV pareada pelo QR (certificado fixado). Sem isso, o painel com o PIN. */
    fun podeUsar(tv: Pareamento.Tv): Boolean = tv.fp.isNotEmpty()

    class Resposta(val codigo: Int, val json: JSONObject?, val cookies: List<String> = emptyList())

    @Volatile private var app: Context? = null

    /** [CelularApp] chama ao abrir o processo: a chave de aparelho fica nas prefs e no Keystore. */
    fun iniciar(ctx: Context) { app = ctx.applicationContext }

    /** Até quando a TV conta como "fora de casa" (o IP de casa não respondeu e o relay entregou). */
    private val foraAte = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val FORA_MS = 30_000L
    private fun foraDeCasa(tv: Pareamento.Tv) = (foraAte[Pareamento.chave(tv)] ?: 0L) > System.currentTimeMillis()

    /**
     * Sessão caiu e a TV disse "logado: false" num 200 (o /api/estado não dá 401): troca a chave de aparelho, se houver,
     * por uma sessão nova. true = entrou sem PIN (revisor 26/11: o controle e o painel pediam o PIN mesmo com a chave).
     */
    fun reentrar(tv: Pareamento.Tv): Boolean {
        val ctx = app ?: return false
        val chave = ChaveAparelho.ler(ctx, tv) ?: return false
        return entrarComChave(ctx, tv, chave)
    }

    /** TVs que já recusaram dar a chave neste processo (painel antigo, sem https): não pede de novo a cada tecla. */
    private val semChave = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * Item 9 (Brainstorm 26/11): sessão caiu (401) e o app tem a chave desta TV? Troca a chave por uma sessão nova
     * (sem PIN, sem elevação) e tenta UMA vez de novo. Chave recusada (403: revogada no painel) é esquecida; aí volta o
     * caminho de sempre, o painel com o PIN. Pura (ChaveAparelhoTest).
     */
    fun deveReentrar(codigo: Int, temChave: Boolean, caminho: String): Boolean =
        codigo == 401 && temChave && !caminho.startsWith("/api/aparelho")

    /** Depois de uma chamada que deu certo, sem chave guardada: pede uma (só pelo https fixado, que é o único aqui). */
    fun devePedirChave(codigo: Int, temChave: Boolean, caminho: String, jaRecusou: Boolean): Boolean =
        codigo in 200..299 && !temChave && !jaRecusou && !caminho.startsWith("/api/aparelho")

    /**
     * A TV respondeu com OUTRO certificado (Brainstorm 26/11, item 18): renovado no painel, ou outro aparelho no IP.
     * Diferente de "não respondeu": a pessoa precisa ler o QR da TV de novo, e o app diz isso.
     */
    const val CERTIFICADO_TROCOU = -2

    /** A TV aceitou a conexão mas não respondeu a tempo: talvez tenha feito. Não reenvia pelo relay. */
    const val DEMOROU = -3

    /** Chamada bloqueante. Código 0 = não chegou na TV (desligada, fora de casa); [CERTIFICADO_TROCOU] = outro certificado. */
    fun chamar(tv: Pareamento.Tv, metodo: String, caminho: String, corpo: JSONObject? = null): Resposta {
        // Fora de casa há pouco (revisor 26/11): tecla e texto vão direto pelo relay, sem esperar 4 s pelo IP de casa.
        app?.let { ctx -> if (foraDeCasa(tv)) pelaCaixa(ctx, tv, caminho, corpo)?.let { return it } }
        val r = chamarUmaVez(tv, metodo, caminho, corpo)
        val ctx = app ?: return r
        // Decisão A (26/11/2026): a TV não respondeu pela rede de casa (fora de casa)? Tecla e texto vão pelo relay.
        if (r.codigo == 0) {
            val viaRelay = pelaCaixa(ctx, tv, caminho, corpo)
            if (viaRelay != null) foraAte[Pareamento.chave(tv)] = System.currentTimeMillis() + FORA_MS
            return viaRelay ?: r
        }
        foraAte.remove(Pareamento.chave(tv))
        val chave = ChaveAparelho.ler(ctx, tv)
        if (deveReentrar(r.codigo, chave != null, caminho)) {
            return if (entrarComChave(ctx, tv, chave!!)) chamarUmaVez(tv, metodo, caminho, corpo) else r
        }
        if (devePedirChave(r.codigo, chave != null, caminho, Pareamento.chave(tv) in semChave)) pedirChave(ctx, tv)
        if (deveParearRelay(r.codigo, RelayCelular.tv(ctx, tv) != null, caminho, recusouRelay(tv))) parearRelay(ctx, tv)
        return r
    }

    /**
     * TVs que não aceitaram as chaves do relay: até quando não tentar de novo. Pediu o PIN (403) = de novo em 60 s, até
     * o PIN ser digitado no painel; outra recusa (versão antiga, chaves inválidas) = não neste processo.
     */
    private val semRelay = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private fun recusouRelay(tv: Pareamento.Tv) = (semRelay[Pareamento.chave(tv)] ?: 0L) > System.currentTimeMillis()

    /** Depois de uma chamada que deu certo em casa, sem as chaves do relay desta TV: troca agora. Pura (RelayCelularTest). */
    fun deveParearRelay(codigo: Int, temRelay: Boolean, caminho: String, jaRecusou: Boolean): Boolean =
        codigo in 200..299 && !temRelay && !jaRecusou && !caminho.startsWith("/api/aparelho") && !caminho.startsWith("/api/relay")

    private fun parearRelay(ctx: Context, tv: Pareamento.Tv) {
        val pedido = RelayCelular.pedido(ctx) ?: return
        // Com a chave de aparelho (se já tiver), a TV liga este pareamento à linha do celular em "Celulares com acesso":
        // revogar lá corta o acesso de fora também.
        val r = chamarUmaVez(tv, "POST", "/api/relay/parear", pedido, ChaveAparelho.ler(ctx, tv)?.let { mapOf("X-Aparelho" to it) } ?: emptyMap())
        if (r.codigo != 200 || !RelayCelular.guardar(ctx, tv, r.json))
            semRelay[Pareamento.chave(tv)] = if (r.codigo <= 0 || esperaPin(r.codigo, r.json?.optBoolean("precisaPin") == true)) System.currentTimeMillis() + ESPERA_PIN_MS else Long.MAX_VALUE
    }

    /**
     * "Usar fora de casa?" (27/11/2026). Por quê: quem entrou só pelo QR tem sessão sem PIN, e a TV só dá a chave de
     * aparelho e as chaves do relay com o PIN digitado há pouco; nada pedia esse PIN, e o celular nunca ficava pronto
     * para fora de casa. Para quê: o controle pergunta uma vez e pede o PIN ali mesmo. Como: o PIN vai para o mesmo
     * /api/confirmar do painel (eleva a sessão por 5 min); na hora pede a chave e troca as chaves do relay.
     */
    enum class Fora { ATIVADO, PIN_ERRADO, ESPERAR, SEM_SESSAO, SEM_TV, RECUSOU }

    /** Oferece só em casa (a TV respondeu pelo https agora, não pelo relay), com o QR lido e sem as chaves do relay. Pura. */
    fun deveOferecerFora(codigo: Int, podeUsar: Boolean, temRelay: Boolean): Boolean =
        codigo in 200..299 && codigo != 202 && podeUsar && !temRelay

    /**
     * A resposta do /api/confirmar vira o que a tela diz. 401 COM "restantes" é PIN errado; 401 sem é sessão vencida
     * (a TV responde "entre com o PIN" antes de olhar o PIN). Pura (ForaDeCasaTest).
     */
    fun foraPeloPin(codigo: Int, json: JSONObject?): Fora = when {
        codigo == 200 -> Fora.ATIVADO
        codigo == 401 && json?.has("restantes") == true -> Fora.PIN_ERRADO
        codigo == 401 -> Fora.SEM_SESSAO
        codigo == 429 -> Fora.ESPERAR
        codigo == 0 || codigo == DEMOROU || codigo == CERTIFICADO_TROCOU -> Fora.SEM_TV
        else -> Fora.RECUSOU
    }

    /** PIN da TV no formato do painel (pinValido em app.js): 6 a 12 algarismos. Pura. */
    fun pinValido(pin: String): Boolean = Regex("[0-9]{6,12}").matches(pin)

    /**
     * Bloqueante. Nunca repete o PIN sozinho (cada PIN errado conta no bloqueio da TV): só entra de novo pela chave de
     * aparelho quando a sessão venceu, e aí confere o PIN uma vez. [esperarSeg] recebe a espera da TV no 429.
     */
    fun ativarForaDeCasa(ctx: Context, tv: Pareamento.Tv, pin: String, esperarSeg: (Int) -> Unit = {}): Fora {
        val corpo = JSONObject().put("pin", pin)
        var r = chamarUmaVez(tv, "POST", "/api/confirmar", corpo)
        if (foraPeloPin(r.codigo, r.json) == Fora.SEM_SESSAO && reentrar(tv)) r = chamarUmaVez(tv, "POST", "/api/confirmar", corpo)
        val f = foraPeloPin(r.codigo, r.json)
        if (f == Fora.ESPERAR) esperarSeg(r.json?.optInt("esperarSeg", 0) ?: 0)
        if (f != Fora.ATIVADO) return f
        val k = Pareamento.chave(tv)
        ultimoPedido.remove(k); semChave.remove(k); semRelay.remove(k)
        // Sem chave ainda: pede agora (a sessão está elevada). Com chave nova, pedirChave esquece as chaves velhas do relay.
        if (ChaveAparelho.ler(ctx, tv) == null) pedirChave(ctx, tv)
        if (RelayCelular.tv(ctx, tv) == null) parearRelay(ctx, tv)
        return if (RelayCelular.tv(ctx, tv) != null) Fora.ATIVADO else Fora.RECUSOU
    }

    /** Tecla ou texto pelo api.sofaly.tv. 202 = entregue ao servidor (a TV recebe na hora se estiver ligada). */
    private fun pelaCaixa(ctx: Context, tv: Pareamento.Tv, caminho: String, corpo: JSONObject?): Resposta? {
        val m = RelayCelular.mensagem(caminho, corpo, System.currentTimeMillis()) ?: return null
        return if (RelayCelular.enviar(ctx, tv, m)) Resposta(202, JSONObject().put("ok", true).put("relay", true)) else null
    }

    private fun chamarUmaVez(tv: Pareamento.Tv, metodo: String, caminho: String, corpo: JSONObject?,
                             cabecalhos: Map<String, String> = emptyMap()): Resposta =
        conversar(tv, metodo, caminho, 8_000) { c ->
            cabecalhos.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (metodo == "POST") {
                c.setRequestProperty("Content-Type", "application/json")
                c.doOutput = true
                c.outputStream.use { it.write((corpo ?: JSONObject()).toString().toByteArray()) }
            }
        }

    /** Chave -> cookie de sessão novo, gravado no mesmo CookieManager do WebView (o painel também volta a abrir sem PIN). */
    private fun entrarComChave(ctx: Context, tv: Pareamento.Tv, chave: String): Boolean {
        val r = conversar(tv, "POST", "/api/aparelho/entrar", 8_000) { c ->
            c.setRequestProperty("X-Aparelho", chave)
            c.setRequestProperty("Content-Type", "application/json")
            c.doOutput = true
            c.outputStream.use { it.write("{}".toByteArray()) }
        }
        // Revogada: só o QR traz de volta. As chaves do relay desta TV também saem (a TV já esqueceu este celular), então
        // fora de casa o app para de dizer "enviado pela internet" para uma TV que descarta tudo.
        if (r.codigo == 403) { ChaveAparelho.esquecer(ctx, tv); RelayCelular.esquecer(ctx, tv); return false }
        if (r.codigo != 200 || r.cookies.isEmpty()) return false
        val base = "https://${tv.ip}:${Pareamento.PORTA_HTTPS}/"
        // WebView atualizando ou ausente lança aqui; sem o try derrubava o app pela thread de fundo.
        return runCatching {
            val cm = CookieManager.getInstance()
            r.cookies.forEach { cm.setCookie(base, it) }
            cm.flush()
        }.isSuccess
    }

    private fun pedirChave(ctx: Context, tv: Pareamento.Tv) {
        val k = Pareamento.chave(tv)
        val agora = System.currentTimeMillis()
        // Atômico: a pergunta de estado e uma tecla ao mesmo tempo emitiam duas chaves (uma ficava órfã na TV).
        var minha = false
        ultimoPedido.compute(k) { _, antes -> if (agora - (antes ?: 0L) < ESPERA_PIN_MS) antes else { minha = true; agora } }
        if (!minha) return
        val r = chamarUmaVez(tv, "POST", "/api/aparelho/emitir", JSONObject().put("nome", android.os.Build.MODEL ?: ""))
        val chave = r.json?.optString("chave")
        if (r.codigo == 200 && ChaveAparelho.valida(chave) && ChaveAparelho.guardar(ctx, tv, chave!!)) {
            // Chave nova (primeira vez, ou depois de revogado e lido o QR de novo): troca as chaves do relay de novo, já
            // ligadas a ela, para que "Revogar" no painel corte também o acesso de fora deste celular.
            RelayCelular.esquecer(ctx, tv)
            return
        }
        // Código 0 (rede caiu no meio) = tenta depois, não é recusa.
        if (r.codigo > 0 && !esperaPin(r.codigo, r.json?.optBoolean("precisaPin") == true)) semChave += k
    }

    /**
     * Revisor 26/11: a TV só dá a chave com a sessão elevada (PIN digitado no painel deste celular). Sem PIN ainda, o app
     * não desiste: tenta de novo depois, no máximo a cada [ESPERA_PIN_MS]. Qualquer outra recusa vale para a sessão do app.
     * Pura (ChaveAparelhoTest).
     */
    fun esperaPin(codigo: Int, precisaPin: Boolean): Boolean = codigo == 403 && precisaPin

    private const val ESPERA_PIN_MS = 60_000L
    private val ultimoPedido = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Manda um arquivo para uma pasta da TV (Brainstorm 26/11, item 16), pela mesma rota do painel
     * (`/api/arquivos/enviar`, corpo cru). Em fluxo, com o tamanho exato: a TV confere os bytes e só então dá o nome final.
     * [progresso] recebe os bytes já enviados. 409 = já existe um arquivo com esse nome na pasta.
     */
    fun enviar(tv: Pareamento.Tv, pasta: String, nome: String, tamanho: Long, entrada: InputStream,
               progresso: (Long) -> Unit = {}): Resposta {
        val q = "/api/arquivos/enviar?pasta=" + URLEncoder.encode(pasta, "UTF-8") + "&nome=" + URLEncoder.encode(nome, "UTF-8")
        // 2 min de espera pela resposta: no fim a TV grava no disco (fd.sync) antes de dizer "ok".
        return conversar(tv, "POST", q, 120_000) { c ->
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.doOutput = true
            c.setFixedLengthStreamingMode(tamanho)   // sem isso o Android guardaria o vídeo inteiro na memória
            c.outputStream.use { saida ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = entrada.read(buf)
                    if (n < 0) break
                    saida.write(buf, 0, n)
                    total += n
                    progresso(total)
                }
            }
        }
    }

    /** O que todas as chamadas têm em comum: certificado fixado, cookie do painel e os cabeçalhos de um POST. */
    /**
     * Cabeçalhos e corpo ANTES de conectar. Depois do connect() o HttpURLConnection recusa setRequestProperty e
     * setDoOutput ("Already connected"); com a ordem ao contrário todo POST do app (teclas do controle, texto, PIN do
     * "Usar fora de casa", chave do aparelho, envio de arquivo) caía no catch e virava "A TV não respondeu"
     * (achado no emulador, 27/11/2026). No POST o outputStream já conecta; no GET quem conecta é o connect().
     */
    internal fun <C : HttpURLConnection> prepararEConectar(c: C, escrever: (C) -> Unit) {
        escrever(c)
        c.connect()
    }

    private fun conversar(tv: Pareamento.Tv, metodo: String, caminho: String, espera: Int,
                          escrever: (HttpsURLConnection) -> Unit): Resposta {
        if (!podeUsar(tv)) return Resposta(0, null)
        val base = "https://${tv.ip}:${Pareamento.PORTA_HTTPS}"
        var trocou = false
        var conectou = false
        return try {
            val c = URL(base + caminho).openConnection() as HttpsURLConnection
            val fixo = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, tipo: String) = throw java.security.cert.CertificateException()
                override fun checkServerTrusted(chain: Array<X509Certificate>, tipo: String) {
                    if (chain.isEmpty() || !Pareamento.confere(tv, chain[0].encoded)) {
                        trocou = chain.isNotEmpty()
                        throw java.security.cert.CertificateException("não é o certificado da TV")
                    }
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            c.sslSocketFactory = SSLContext.getInstance("TLS").apply { init(null, arrayOf(fixo), null) }.socketFactory
            c.hostnameVerifier = javax.net.ssl.HostnameVerifier { h, _ -> h == tv.ip }   // o certificado já foi fixado
            c.instanceFollowRedirects = false
            c.connectTimeout = 4_000; c.readTimeout = espera
            c.requestMethod = metodo
            CookieManager.getInstance().getCookie("$base/")?.let { c.setRequestProperty("Cookie", it) }
            if (metodo == "POST") { c.setRequestProperty("Origin", base); c.setRequestProperty("X-Painel", "1") }
            prepararEConectar(c, escrever); conectou = true
            try {
                val cod = c.responseCode
                val txt = runCatching { (if (cod in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                val cookies = c.headerFields.entries.filter { it.key.equals("Set-Cookie", ignoreCase = true) }.flatMap { it.value }
                Resposta(cod, txt?.let { runCatching { JSONObject(it) }.getOrNull() }, cookies)
            } finally { c.disconnect() }
        } catch (_: Exception) {
            // Conectou e depois parou (tempo de leitura): a TV pode ter feito. Não é 0, senão o relay mandava de novo e a
            // tecla saía em dobro (revisor 26/11).
            Resposta(if (trocou) CERTIFICADO_TROCOU else if (conectou) DEMOROU else 0, null)
        }
    }

    fun tecla(tv: Pareamento.Tv, nome: String): Resposta = chamar(tv, "POST", "/api/tela/tecla", JSONObject().put("tecla", nome))
    fun texto(tv: Pareamento.Tv, t: String): Resposta = chamar(tv, "POST", "/api/tela/texto", JSONObject().put("texto", t))
    fun estado(tv: Pareamento.Tv): Resposta = chamar(tv, "GET", "/api/estado")

    fun emSegundoPlano(bloco: () -> Unit) = Thread(bloco, "ApiTv").start()

    /** Teclas e texto numa fila só (revisor 26/11): uma Thread por tecla deixava "baixo, baixo, direita" chegar trocado. */
    private val fila = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "ApiTv-teclas") }
    fun emOrdem(bloco: () -> Unit) { fila.execute(bloco) }
}
