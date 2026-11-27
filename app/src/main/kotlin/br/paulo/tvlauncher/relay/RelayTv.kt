package br.paulo.tvlauncher.relay

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import br.paulo.tvlauncher.Http
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * O lado Android do relay (decisão A, 26/11/2026): a identidade desta TV e a conversa com a caixa postal do
 * api.sofaly.tv. A criptografia pura mora em [RelayCrypto]/[RelayEnvelope].
 *
 * - Identidade: semente Ed25519 + privada X25519 (32 bytes cada), nascem aqui e ficam cifradas por uma chave AES do
 *   Android Keystore. Prefs "relay" ficam FORA do backup: restaurar em outra TV não pode clonar a identidade desta
 *   (dois aparelhos com o mesmo id disputariam a mesma caixa postal).
 * - Aparelhos pareados: id -> X25519 pública. Só envelope de quem está aqui abre (o pareamento em si vem no passo A2).
 * - [receber]: busca os pendentes, abre, descarta repetidos e confirma TODOS (inclusive lixo), para a caixa não encher.
 */
object RelayTv {
    private const val TAG = "TvLauncher.Relay"
    const val SERVIDOR = "https://api.sofaly.tv"
    private const val APELIDO = "relay_identity"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("relay", Context.MODE_PRIVATE)

    class Identidade(val ed: ByteArray, val x: ByteArray) {
        val edPub: ByteArray = RelayCrypto.edPublica(ed)
        val xPub: ByteArray = RelayCrypto.xPublica(x)
        val id: String = RelayCrypto.idDe(edPub)
    }

    @Volatile private var cache: Identidade? = null

    /** A identidade desta TV (cria na primeira vez). Null só se o Keystore falhar. */
    @Synchronized fun identidade(ctx: Context): Identidade? {
        cache?.let { return it }
        val p = prefs(ctx)
        val cif = p.getString("chaves", null); val iv = p.getString("chaves_iv", null)
        val i = if (cif != null && iv != null) {
            runCatching {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(128, RelayCrypto.deB64(iv)))
                val b = c.doFinal(RelayCrypto.deB64(cif))
                Identidade(b.copyOfRange(0, 32), b.copyOfRange(32, 64))
            }.onFailure { Log.w(TAG, "identidade ilegível: ${it.javaClass.simpleName}") }.getOrNull()
                ?: return null   // não recria por cima: um Keystore com soluço não pode trocar o id da TV
        } else {
            val nova = Identidade(RelayCrypto.aleatorios(32), RelayCrypto.aleatorios(32))
            val ok = runCatching {
                val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, chave())
                p.edit().putString("chaves", RelayCrypto.b64(c.doFinal(nova.ed + nova.x)))
                    .putString("chaves_iv", RelayCrypto.b64(c.iv)).commit()
            }.onFailure { Log.w(TAG, "Keystore: ${it.javaClass.simpleName}") }.getOrDefault(false)
            if (!ok) return null
            Log.i(TAG, "identidade nova: ${nova.id}")
            nova
        }
        cache = i
        return i
    }

    /**
     * Troca de pacote (revisor 26/11): a identidade vai aberta ao app novo (pelo provider de assinatura, nunca a outro
     * app), que a tranca com o Keystore dele. Sem isso a cópia das prefs trazia a versão trancada pelo Keystore do
     * antigo, que não abre no novo, e o relay (controle de fora, Assistir junto, cota assinada) morria de vez.
     */
    internal fun paraMigracao(ctx: Context): String? = identidade(ctx)?.let { RelayCrypto.b64(it.ed + it.x) }

    @Synchronized internal fun deMigracao(ctx: Context, b64: String): Boolean {
        val b = runCatching { RelayCrypto.deB64(b64) }.getOrNull()?.takeIf { it.size == 64 } ?: return false
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, chave())
        val ok = prefs(ctx).edit().putString("chaves", RelayCrypto.b64(c.doFinal(b)))
            .putString("chaves_iv", RelayCrypto.b64(c.iv)).commit()
        if (ok) cache = Identidade(b.copyOfRange(0, 32), b.copyOfRange(32, 64))
        return ok
    }

    private fun chave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(APELIDO, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(APELIDO, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return g.generateKey()
    }

    // ---------------------------------------------------------------- aparelhos pareados

    /**
     * Um celular que pode mandar teclas e texto de fora de casa. [aparelho] = o id do mesmo celular em "Celulares com
     * acesso" do painel (AparelhosPareados), quando ele pareou com a chave de aparelho: revogar lá revoga aqui também.
     */
    data class Pareado(val id: String, val xPub: ByteArray, val nome: String, val aparelho: String? = null,
                       /** Uma TV de outra casa (RelayCasas: só Sessão junto), não um celular (só teclas e texto). */
                       val tv: Boolean = false)

    fun pareados(ctx: Context): List<Pareado> {
        val a = runCatching { JSONArray(prefs(ctx).getString("pareados", "[]")) }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id"); val x = runCatching { RelayCrypto.deB64(o.optString("x")) }.getOrNull()
            if (!RelayCrypto.ID_RE.matches(id) || x?.size != 32) null
            else Pareado(id, x, o.optString("nome"), o.optString("aparelho").ifEmpty { null }, o.optBoolean("tv"))
        }
    }

    private fun guardar(ctx: Context, lista: List<Pareado>) {
        prefs(ctx).edit().putString("pareados", JSONArray().apply {
            for (q in lista) put(JSONObject().put("id", q.id).put("x", RelayCrypto.b64(q.xPub)).put("nome", q.nome)
                .apply { q.aparelho?.let { put("aparelho", it) }; if (q.tv) put("tv", true) })
        }.toString()).apply()
    }

    @Synchronized fun parear(ctx: Context, p: Pareado) = guardar(ctx, pareados(ctx).filterNot { it.id == p.id } + p)

    /** true = havia esse celular. */
    @Synchronized fun esquecer(ctx: Context, id: String): Boolean {
        val antes = pareados(ctx)
        guardar(ctx, antes.filterNot { it.id == id })
        return antes.any { it.id == id }
    }

    /** O celular [aparelho] foi revogado no painel: sai daqui também. */
    @Synchronized fun esquecerDoAparelho(ctx: Context, aparelho: String) =
        guardar(ctx, pareados(ctx).filterNot { it.aparelho == aparelho })

    // ---------------------------------------------------------------- servidor (fora da thread da tela)

    class Resposta(val codigo: Int, val corpo: String)

    /**
     * Cabeçalhos X-Sofaly-* para um pedido às rotas abertas do api.sofaly.tv (clima, podcasts, fala, fichas; item 23 do
     * Brainstorm 26/11): com eles a cota do dia é desta TV, e uma TV com defeito não gasta a das outras. [caminho] é só
     * o caminho, sem a consulta (é o que o servidor confere). Sem identidade (Keystore com defeito), vazio: o pedido
     * segue sem assinatura e conta só na cota da rede.
     */
    fun assinatura(metodo: String, caminho: String, corpo: ByteArray = ByteArray(0)): Map<String, String> = runCatching {
        val eu = identidade(br.paulo.tvlauncher.LauncherApp.instancia) ?: return emptyMap()
        RelayCrypto.cabecalhos(eu.ed, metodo, caminho, System.currentTimeMillis() / 1000, corpo)
    }.getOrDefault(emptyMap())

    /** O mesmo, para uma conexão já aberta (o caminho sai da própria URL). */
    fun assinar(c: HttpURLConnection, corpo: ByteArray = ByteArray(0)) {
        for ((k, v) in assinatura(c.requestMethod, c.url.path, corpo)) c.setRequestProperty(k, v)
    }

    /** Pedido assinado ao api.sofaly.tv. Erro de rede vira IOException. */
    @Throws(IOException::class)
    fun pedir(ctx: Context, metodo: String, caminho: String, corpo: ByteArray = ByteArray(0)): Resposta {
        val eu = identidade(ctx) ?: throw IOException("sem identidade (Keystore)")
        val c = URL(SERVIDOR + caminho).openConnection() as HttpURLConnection
        try {
            c.requestMethod = metodo; c.connectTimeout = 8_000; c.readTimeout = 15_000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", Http.USER_AGENT)
            for ((k, v) in RelayCrypto.cabecalhos(eu.ed, metodo, caminho, System.currentTimeMillis() / 1000, corpo)) c.setRequestProperty(k, v)
            if (corpo.isNotEmpty()) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/octet-stream")
                c.setFixedLengthStreamingMode(corpo.size)
                c.outputStream.use { it.write(corpo) }
            }
            val code = c.responseCode
            val s = (if (code in 200..299) c.inputStream else c.errorStream)?.use { Http.readLimited(it, 2_000_000, cut = false) }
            return Resposta(code, s?.toString(Charsets.UTF_8) ?: "")
        } finally { c.disconnect() }
    }

    /** Manda [m] fechado para um aparelho pareado. true = o servidor guardou. */
    fun enviar(ctx: Context, para: Pareado, m: RelayEnvelope.Mensagem): Boolean {
        val eu = identidade(ctx) ?: return false
        val env = RelayEnvelope.fechar(eu.id, eu.x, para.id, para.xPub, m)
        return runCatching { pedir(ctx, "POST", "/v1/envelope/${para.id}", env).codigo == 201 }.getOrDefault(false)
    }

    private val vistos = RelayEnvelope.Vistos()
    @Volatile private var vistosCarregados = false

    /**
     * Busca e processa a caixa postal. [tratar] recebe cada mensagem nova (aberta, de um pareado, não repetida).
     * Devolve quantas foram tratadas, ou -1 se não falou com o servidor.
     */
    @Synchronized fun receber(ctx: Context, tratar: (RelayEnvelope.Aberto, Long) -> Unit): Int {
        val eu = identidade(ctx) ?: return -1
        if (!vistosCarregados) { vistos.carregar(prefs(ctx).getString("vistos", null)); vistosCarregados = true }
        val r = runCatching { pedir(ctx, "GET", "/v1/inbox/pending") }.getOrNull() ?: return -1
        if (r.codigo != 200) { Log.w(TAG, "caixa: HTTP ${r.codigo}"); return -1 }
        val lista = RelayEnvelope.lerPendentes(r.corpo)
        if (lista.isEmpty()) return 0
        val chaves = pareados(ctx).associate { it.id to it.xPub }
        val agora = System.currentTimeMillis()
        var n = 0
        for (e in lista) {
            if (RelayCasas.chegou(ctx, e.de, e.dados)) continue   // apresentação de outra TV (código de 8 letras)
            if (!RelayEnvelope.remetenteConfere(e)) { Log.w(TAG, "envelope ${e.caixaId}: remetente não confere"); continue }
            val a = RelayEnvelope.abrir(eu.id, eu.x, e.dados, agora) { chaves[it] }
            if (a == null) { Log.w(TAG, "envelope ${e.caixaId} de ${e.de}: não abriu"); continue }
            if (!vistos.primeiraVez(a.mensagem.id, a.mensagem.t, agora)) continue
            runCatching { tratar(a, e.criado) }.onFailure { Log.w(TAG, "tratar ${a.mensagem.tipo}: ${it.javaClass.simpleName}") }
            n++
        }
        prefs(ctx).edit().putString("vistos", vistos.paraJson()).apply()
        // Confirma tudo, até o que não abriu: lixo de quem não é pareado não pode ficar 7 dias ocupando a caixa.
        val ids = JSONArray(lista.map { it.caixaId })
        runCatching { pedir(ctx, "POST", "/v1/inbox/ack", JSONObject().put("ids", ids).toString().toByteArray()) }
        return n
    }

    // ---------------------------------------------------------------- escuta (WebSocket) e a lista fechada

    /** O que fazer com um comando de fora. Mesma porta do painel: as mesmas travas (pergunta na tela, texto válido). */
    fun tratar(ctx: Context, a: RelayEnvelope.Aberto, chegouSeg: Long = 0L) {
        val quem = pareados(ctx).firstOrNull { it.id == a.remetente }
        // TV de outra casa: só Sessão junto. Nunca tecla nem texto (e um celular nunca manda Sessão junto).
        if (quem?.tv == true) { RelayCasas.receberJunto(ctx, quem, a.mensagem); return }
        val nome = quem?.nome ?: a.remetente
        // Atalho pelo celular: o celular do dono apresenta a TV de outra casa (Assistir junto). Só de celular pareado.
        if (quem != null && a.mensagem.tipo == RelayHouses.CASA) {
            if (!br.paulo.tvlauncher.panel.PainelArmazem.podeLigar(ctx)) {
                br.paulo.tvlauncher.panel.PainelArmazem.registrar(ctx, "fora de casa: $nome", "ignorado", "painel desligado")
            } else RelayCasas.doCelular(ctx, quem, a.mensagem)
            return
        }
        val c = RelayPairing.comando(a.mensagem)
        // Painel desligado = nada de fora mexe na TV (o relay é outra porta para o mesmo controle do painel).
        if (c != null && !br.paulo.tvlauncher.panel.PainelArmazem.podeLigar(ctx)) {
            br.paulo.tvlauncher.panel.PainelArmazem.registrar(ctx, "fora de casa: $nome", "ignorado", "painel desligado")
            return
        }
        if (c != null && !RelayPairing.naHora(a.mensagem, System.currentTimeMillis(), chegouSeg)) {
            br.paulo.tvlauncher.panel.PainelArmazem.registrar(ctx, "fora de casa: $nome", "ignorado", "chegou atrasado (TV estava desligada)")
            return
        }
        val motivo = when (c) {
            is RelayPairing.Comando.Tecla -> br.paulo.tvlauncher.panel.PainelControle.tecla(ctx, c.nome)
            is RelayPairing.Comando.Texto -> br.paulo.tvlauncher.panel.PainelControle.digitar(ctx, c.texto)
            null -> "fora da lista (${a.mensagem.tipo.take(20)})"
        }
        // O texto em si não vai para o registro (pode ser uma senha), igual ao painel.
        val acao = when (c) { is RelayPairing.Comando.Tecla -> "tecla ${c.nome}"; is RelayPairing.Comando.Texto -> "digitar ${c.texto.length} caractere(s)"; null -> "ignorado" }
        br.paulo.tvlauncher.panel.PainelArmazem.registrar(ctx, "fora de casa: $nome", acao, motivo ?: "ok")
    }

    @Volatile private var escutando: Thread? = null
    @Volatile var conectado = false; private set

    /**
     * Liga a escuta quando há aparelho pareado (barato e idempotente: chamar à vontade, como o PainelWeb.ajustar).
     * Laço sem fim numa Thread própria (regra do CLAUDE.md para servidores e vigias): conecta, trata o que chega,
     * confirma, e reconecta com espera crescente (5 s até 5 min) quando cai.
     */
    @Synchronized fun ajustar(ctx: Context) {
        val app = ctx.applicationContext
        if (!precisaEscutar(app) || escutando?.isAlive == true) return
        escutando = Thread({ laco(app) }, "RelayEscuta").apply { isDaemon = true; start() }
    }

    /** Há quem possa mandar algo: aparelho pareado, ou um pareamento por código em andamento (RelayCasas). */
    private fun precisaEscutar(app: Context) = pareados(app).isNotEmpty() || RelayCasas.emAndamento()

    private fun laco(app: Context) {
        var espera = 5_000L
        while (precisaEscutar(app)) {
            val eu = identidade(app) ?: return
            try {
                val ws = RelaySocket.conectar(SERVIDOR.removePrefix("https://"), eu.ed, Http.USER_AGENT, System.currentTimeMillis() / 1000)
                conectado = true; espera = 5_000L
                Log.i(TAG, "escuta ligada (${eu.id})")
                ws.tempoDeLeitura(50_000)   // sem nada em 50 s: manda ping (o Cloudflare derruba conexão parada em ~100 s)
                try {
                    while (true) {
                        val msg = try { ws.receber() } catch (_: java.net.SocketTimeoutException) {
                            // Revisor 26/11: depois de a internet cair e voltar (NAT novo) o socket fica "ligado" sem
                            // receber nada por até meia hora, e os comandos vencem na caixa. Sem nem o pong do último
                            // ping (~90 s em silêncio), fecha e reconecta.
                            if (System.currentTimeMillis() - ws.ultimoQuadro > 90_000L) { Log.w(TAG, "escuta sem resposta: reconectando"); break }
                            ws.ping(); continue
                        } ?: break
                        processar(app, eu, msg)?.let { id -> ws.enviar(JSONObject().put("ack", JSONArray().put(id)).toString()) }
                    }
                } finally { ws.fechar(); conectado = false }
            } catch (e: IOException) {
                Log.w(TAG, "escuta caiu: ${e.message}")
            }
            Thread.sleep(espera)
            espera = minOf(espera * 2, 5 * 60_000L)
        }
        escutando = null
    }

    /** Uma mensagem do WebSocket ({id, from, created, data}). Devolve o id para o ack (sempre, até lixo), ou null. */
    private fun processar(app: Context, eu: Identidade, msg: String): String? {
        val r = RelayEnvelope.lerPendentes("{\"envelopes\":[$msg]}").firstOrNull() ?: return null
        synchronized(this) {
            if (!vistosCarregados) { vistos.carregar(prefs(app).getString("vistos", null)); vistosCarregados = true }
            if (RelayCasas.chegou(app, r.de, r.dados)) return r.caixaId
            if (!RelayEnvelope.remetenteConfere(r)) return r.caixaId
            val chaves = pareados(app).associate { it.id to it.xPub }
            val agora = System.currentTimeMillis()
            val a = RelayEnvelope.abrir(eu.id, eu.x, r.dados, agora) { chaves[it] } ?: return r.caixaId
            if (!vistos.primeiraVez(a.mensagem.id, a.mensagem.t, agora)) return r.caixaId
            prefs(app).edit().putString("vistos", vistos.paraJson()).apply()
            runCatching { tratar(app, a, r.criado) }.onFailure { Log.w(TAG, "tratar: ${it.javaClass.simpleName}") }
        }
        return r.caixaId
    }

    /** Pedido do painel (POST /api/relay/parear, só o dono): guarda o celular e devolve as chaves da TV. */
    fun parearPeloPainel(ctx: Context, corpo: JSONObject?, aparelho: String?): JSONObject? {
        val p = RelayPairing.lerPedido(corpo) ?: return null
        val eu = identidade(ctx) ?: return null
        parear(ctx, Pareado(p.id, p.xPub, p.nome, aparelho))
        ajustar(ctx)
        return RelayPairing.resposta(eu.id, eu.xPub)
    }

    /** adb: EXTRAS --es o relay. Mostra o id e testa a caixa postal (sem tratar nada). */
    fun texto(ctx: Context): String {
        val eu = identidade(ctx) ?: return "sem identidade: Keystore falhou"
        val teste = runCatching { pedir(ctx, "GET", "/v1/inbox/pending") }
            .fold({ r -> "HTTP ${r.codigo}, ${RelayEnvelope.lerPendentes(r.corpo).size} pendente(s)" }, { "sem rede: ${it.javaClass.simpleName}" })
        return "id ${eu.id}; pareados ${pareados(ctx).joinToString { it.nome }.ifEmpty { "nenhum" }}; escuta ${if (conectado) "ligada" else "desligada"}; caixa: $teste"
    }
}
