package br.paulo.onetv.celular

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import br.paulo.tvlauncher.relay.RelayCrypto
import br.paulo.tvlauncher.relay.RelayEnvelope
import br.paulo.tvlauncher.relay.RelayHouses
import br.paulo.tvlauncher.relay.RelayPairing
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * Controle de fora de casa pelo api.sofaly.tv (decisão A do Paulo, 26/11/2026), lado do celular.
 *
 * Porquê: fora da rede de casa o https da TV não responde, e o Tailscale exige conta e app a mais.
 * Como: em casa, depois de uma chamada que deu certo (sessão do dono aberta), o celular manda as chaves públicas dele em
 * POST /api/relay/parear e guarda as da TV. Fora de casa, tecla e texto viram envelopes cifrados de ponta a ponta
 * (relay-core, o mesmo código da TV) que o servidor só entrega: não lê nem forja.
 * Identidade: semente Ed25519 + privada X25519 cifradas por uma chave do Keystore (prefs "relay"; allowBackup=false).
 */
object RelayCelular {
    private const val SERVIDOR = "https://api.sofaly.tv"
    private const val ALIAS = "sofaly-relay"
    private const val PREFS = "relay"

    class Eu(val ed: ByteArray, val x: ByteArray) { val id = RelayCrypto.idDe(RelayCrypto.edPublica(ed)) }

    @Volatile private var cache: Eu? = null

    @Synchronized fun eu(ctx: Context): Eu? {
        cache?.let { return it }
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val guardado = p.getString("chaves", null)
        val e = if (guardado != null) runCatching {
            val (iv, ct) = ChaveAparelho.separar(RelayCrypto.deB64(guardado)) ?: return null
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, segredo(), GCMParameterSpec(128, iv)) }
            val b = c.doFinal(ct); Eu(b.copyOfRange(0, 32), b.copyOfRange(32, 64))
        }.getOrNull() ?: return null   // não recria por cima: a TV conhece este id
        else {
            val novo = Eu(RelayCrypto.aleatorios(32), RelayCrypto.aleatorios(32))
            val ok = runCatching {
                val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, segredo()) }
                p.edit().putString("chaves", RelayCrypto.b64(ChaveAparelho.juntar(c.iv, c.doFinal(novo.ed + novo.x)))).commit()
            }.getOrDefault(false)
            if (!ok) return null
            novo
        }
        cache = e
        return e
    }

    private fun segredo(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    // ------------------------------------------------------------ a TV do outro lado (por Pareamento.chave)

    data class TvRelay(val id: String, val x: ByteArray)

    fun tv(ctx: Context, tv: Pareamento.Tv): TvRelay? = runCatching {
        val o = JSONObject(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tv:" + Pareamento.chave(tv), null) ?: return null)
        val x = RelayCrypto.deB64url(o.getString("x"))
        val id = o.getString("id")
        if (RelayCrypto.ID_RE.matches(id) && x.size == 32) TvRelay(id, x) else null
    }.getOrNull()

    /** Corpo do POST /api/relay/parear. */
    fun pedido(ctx: Context): JSONObject? {
        val e = eu(ctx) ?: return null
        return JSONObject().put("id", e.id).put("ed", RelayCrypto.b64url(RelayCrypto.edPublica(e.ed)))
            .put("x", RelayCrypto.b64url(RelayCrypto.xPublica(e.x))).put("name", android.os.Build.MODEL ?: "Celular")
    }

    /** Esquece as chaves do relay desta TV: o próximo uso em casa troca de novo (e liga à chave de aparelho nova). */
    fun esquecer(ctx: Context, tv: Pareamento.Tv) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("tv:" + Pareamento.chave(tv)).apply()

    /** Guarda a resposta da TV ({id, x}). false = resposta ruim. */
    fun guardar(ctx: Context, tv: Pareamento.Tv, resposta: JSONObject?): Boolean {
        val id = resposta?.optString("id") ?: return false
        val x = runCatching { RelayCrypto.deB64url(resposta.optString("x")) }.getOrNull() ?: return false
        if (!RelayCrypto.ID_RE.matches(id) || x.size != 32) return false
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("tv:" + Pareamento.chave(tv), JSONObject().put("id", id).put("x", RelayCrypto.b64url(x)).toString()).apply()
        return true
    }

    /**
     * Manda tecla ou texto pela caixa postal da TV. true = o servidor guardou (a TV recebe na hora pela conexão aberta,
     * ou quando voltar a ligar, até 7 dias). Bloqueante: fora da thread da tela.
     */
    fun enviar(ctx: Context, tv: Pareamento.Tv, m: RelayEnvelope.Mensagem): Boolean {
        val e = eu(ctx) ?: return false
        val destino = tv(ctx, tv) ?: return false
        val env = RelayEnvelope.fechar(e.id, e.x, destino.id, destino.x, m)
        val caminho = "/v1/envelope/${destino.id}"
        return runCatching {
            val c = URL(SERVIDOR + caminho).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "POST"; c.connectTimeout = 8_000; c.readTimeout = 10_000; c.instanceFollowRedirects = false
                for ((k, v) in RelayCrypto.cabecalhos(e.ed, "POST", caminho, System.currentTimeMillis() / 1000, env)) c.setRequestProperty(k, v)
                c.setRequestProperty("Content-Type", "application/octet-stream")
                c.doOutput = true; c.setFixedLengthStreamingMode(env.size)
                c.outputStream.use { it.write(env) }
                c.responseCode == 201
            } finally { c.disconnect() }
        }.getOrDefault(false)
    }

    /** O caminho do painel vira mensagem do relay? Só tecla e texto (a mesma lista fechada da TV). Puro. */
    fun mensagem(caminho: String, corpo: JSONObject?, agora: Long): RelayEnvelope.Mensagem? = when (caminho) {
        "/api/tela/tecla" -> corpo?.optString("tecla")?.takeIf { it.isNotEmpty() }?.let { RelayPairing.tecla(it, agora) }
        "/api/tela/texto" -> corpo?.optString("texto")?.takeIf { it.isNotEmpty() }?.let { RelayPairing.texto(it, agora) }
        else -> null
    }

    /**
     * Atalho pelo celular para o Assistir junto entre casas: a mensagem que vai para a TV [a] apresenta a [b] e vice-versa.
     * Null se for a mesma TV. Puro (RelayCelularTest).
     */
    fun apresentar(a: TvRelay, nomeA: String, b: TvRelay, nomeB: String, agora: Long): Pair<RelayEnvelope.Mensagem, RelayEnvelope.Mensagem>? {
        if (a.id == b.id) return null
        val paraA = RelayHouses.casa(b.id, b.x, nomeB, agora) ?: return null
        val paraB = RelayHouses.casa(a.id, a.x, nomeA, agora) ?: return null
        return paraA to paraB
    }
}
