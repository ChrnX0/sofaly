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

    fun lembrar(ctx: Context, tv: Pareamento.Tv) =
        ctx.getSharedPreferences("controle", Context.MODE_PRIVATE).edit().putString("ultima", Pareamento.chave(tv)).apply()

    /** O controle nativo só existe com a TV pareada pelo QR (certificado fixado). Sem isso, o painel com o PIN. */
    fun podeUsar(tv: Pareamento.Tv): Boolean = tv.fp.isNotEmpty()

    class Resposta(val codigo: Int, val json: JSONObject?, val cookies: List<String> = emptyList())

    @Volatile private var app: Context? = null

    /** [CelularApp] chama ao abrir o processo: a chave de aparelho fica nas prefs e no Keystore. */
    fun iniciar(ctx: Context) { app = ctx.applicationContext }

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

    /** Chamada bloqueante. Código 0 = não chegou na TV (desligada, fora de casa); [CERTIFICADO_TROCOU] = outro certificado. */
    fun chamar(tv: Pareamento.Tv, metodo: String, caminho: String, corpo: JSONObject? = null): Resposta {
        val r = chamarUmaVez(tv, metodo, caminho, corpo)
        val ctx = app ?: return r
        // Decisão A (26/11/2026): a TV não respondeu pela rede de casa (fora de casa)? Tecla e texto vão pelo relay.
        if (r.codigo == 0) return pelaCaixa(ctx, tv, caminho, corpo) ?: r
        val chave = ChaveAparelho.ler(ctx, tv)
        if (deveReentrar(r.codigo, chave != null, caminho)) {
            return if (entrarComChave(ctx, tv, chave!!)) chamarUmaVez(tv, metodo, caminho, corpo) else r
        }
        if (devePedirChave(r.codigo, chave != null, caminho, Pareamento.chave(tv) in semChave)) pedirChave(ctx, tv)
        if (deveParearRelay(r.codigo, RelayCelular.tv(ctx, tv) != null, caminho, Pareamento.chave(tv) in semRelay)) parearRelay(ctx, tv)
        return r
    }

    /** TVs que não aceitaram as chaves do relay neste processo (versão antiga, ou sessão que não é do dono). */
    private val semRelay = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Depois de uma chamada que deu certo em casa, sem as chaves do relay desta TV: troca agora. Pura (RelayCelularTest). */
    fun deveParearRelay(codigo: Int, temRelay: Boolean, caminho: String, jaRecusou: Boolean): Boolean =
        codigo in 200..299 && !temRelay && !jaRecusou && !caminho.startsWith("/api/aparelho") && !caminho.startsWith("/api/relay")

    private fun parearRelay(ctx: Context, tv: Pareamento.Tv) {
        val pedido = RelayCelular.pedido(ctx) ?: return
        val r = chamarUmaVez(tv, "POST", "/api/relay/parear", pedido)
        if (r.codigo != 200 || !RelayCelular.guardar(ctx, tv, r.json)) semRelay += Pareamento.chave(tv)
    }

    /** Tecla ou texto pelo api.sofaly.tv. 202 = entregue ao servidor (a TV recebe na hora se estiver ligada). */
    private fun pelaCaixa(ctx: Context, tv: Pareamento.Tv, caminho: String, corpo: JSONObject?): Resposta? {
        val m = RelayCelular.mensagem(caminho, corpo, System.currentTimeMillis()) ?: return null
        return if (RelayCelular.enviar(ctx, tv, m)) Resposta(202, JSONObject().put("ok", true).put("relay", true)) else null
    }

    private fun chamarUmaVez(tv: Pareamento.Tv, metodo: String, caminho: String, corpo: JSONObject?): Resposta =
        conversar(tv, metodo, caminho, 8_000) { c ->
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
        if (r.codigo == 403) { ChaveAparelho.esquecer(ctx, tv); return false }   // revogada: só o QR traz de volta
        if (r.codigo != 200 || r.cookies.isEmpty()) return false
        val base = "https://${tv.ip}:${Pareamento.PORTA_HTTPS}/"
        val cm = CookieManager.getInstance()
        r.cookies.forEach { cm.setCookie(base, it) }
        cm.flush()
        return true
    }

    private fun pedirChave(ctx: Context, tv: Pareamento.Tv) {
        val r = chamarUmaVez(tv, "POST", "/api/aparelho/emitir", JSONObject().put("nome", android.os.Build.MODEL ?: ""))
        val chave = r.json?.optString("chave")
        if (r.codigo == 200 && ChaveAparelho.valida(chave)) ChaveAparelho.guardar(ctx, tv, chave!!)
        else semChave += Pareamento.chave(tv)
    }

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
    private fun conversar(tv: Pareamento.Tv, metodo: String, caminho: String, espera: Int,
                          escrever: (HttpsURLConnection) -> Unit): Resposta {
        if (!podeUsar(tv)) return Resposta(0, null)
        val base = "https://${tv.ip}:${Pareamento.PORTA_HTTPS}"
        var trocou = false
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
            escrever(c)
            try {
                val cod = c.responseCode
                val txt = runCatching { (if (cod in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                val cookies = c.headerFields.entries.filter { it.key.equals("Set-Cookie", ignoreCase = true) }.flatMap { it.value }
                Resposta(cod, txt?.let { runCatching { JSONObject(it) }.getOrNull() }, cookies)
            } finally { c.disconnect() }
        } catch (_: Exception) { Resposta(if (trocou) CERTIFICADO_TROCOU else 0, null) }
    }

    fun tecla(tv: Pareamento.Tv, nome: String): Resposta = chamar(tv, "POST", "/api/tela/tecla", JSONObject().put("tecla", nome))
    fun texto(tv: Pareamento.Tv, t: String): Resposta = chamar(tv, "POST", "/api/tela/texto", JSONObject().put("texto", t))
    fun estado(tv: Pareamento.Tv): Resposta = chamar(tv, "GET", "/api/estado")

    fun emSegundoPlano(bloco: () -> Unit) = Thread(bloco, "ApiTv").start()
}
