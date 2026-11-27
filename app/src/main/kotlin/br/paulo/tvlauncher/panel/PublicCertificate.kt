package br.paulo.tvlauncher.panel

import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.relay.RelayTv
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Certificado público da TV (item 26 do Brainstorm 26/11/2026, opção A): `*.<id>.lan.sofaly.tv` do Let's Encrypt,
 * para o painel abrir com cadeado em qualquer aparelho, sem instalar a CA da TV. As regras estão em [PublicCertRules],
 * o protocolo em [AcmeClient]. O que mora aqui é só o que precisa do Android: disco, Keystore, relay e a rede.
 *
 * - A chave do certificado e a da conta ACME nascem na TV e ficam cifradas com a mesma chave do Keystore do
 *   [PainelTls] (noBackupFilesDir/painel-tls/publico, fora de todo backup). Ao servidor vão só o CSR e o TXT.
 * - O TXT do desafio vai ao api.sofaly.tv assinado pela identidade do relay (PUT/DELETE /v1/dns/txt); as duas
 *   máquinas de DNS (server/dns) o mostram ao Let's Encrypt.
 * - [cuidar] roda ao ligar o https e a cada [CICLO_MS]; pede quando falta 1/3 da validade, com espera crescente
 *   quando falha. Sem identidade do relay, sem internet ou com o servidor fora, o painel segue com o certificado
 *   da TV: nada deixa de funcionar.
 * adb: `EXTRAS --es o cert_publico` (estado), `cert_publico_agora` (pede já, ignorando a espera).
 */
object PublicCertificate {
    private const val TAG = "TvLauncher.CertPublico"
    private const val PREFS = "painel_tls_publico"
    const val CICLO_MS = 6 * 3_600_000L

    @Volatile private var atual: PublicCertRules.Par? = null
    @Volatile private var carregado = false
    @Volatile private var ultimoErro: String? = null

    private fun pasta(ctx: Context) = File(ctx.noBackupFilesDir, "painel-tls/publico")

    /** O id da TV no relay (o mesmo do nome público), ou null sem identidade. */
    fun id(ctx: Context): String? = runCatching { RelayTv.identidade(ctx)?.id }.getOrNull()

    /** O certificado público em uso, se existe e ainda vale para esta TV. Lê do disco na primeira vez. */
    fun par(ctx: Context): PublicCertRules.Par? {
        if (!carregado) synchronized(this) { if (!carregado) { atual = runCatching { carregar(ctx) }.getOrNull(); carregado = true } }
        return atual?.takeIf { PublicCertRules.serve(it.cadeia, id(ctx), System.currentTimeMillis()) }
    }

    /** O sufixo `<id>.lan.sofaly.tv` que o SNI tem de pedir. */
    fun sufixo(ctx: Context): String? = id(ctx)?.let { runCatching { AcmeClient.curinga(it).removePrefix("*.") }.getOrNull() }

    /**
     * O endereço público do painel para o IP [ip], se há certificado e o DNS da casa resolve o nome para a própria TV.
     * Quando o roteador bloqueia (DNS rebinding), o nome não resolve aqui e devolve null: o painel nem oferece.
     */
    fun endereco(ctx: Context, ip: String?): String? {
        if (ip == null || par(ctx) == null) return null
        val nome = runCatching { AcmeClient.nomeComIp(id(ctx) ?: return null, ip) }.getOrNull() ?: return null
        if (!resolveParaMim(nome, ip)) return null
        return "https://$nome:${PainelTls.PORTA}/"
    }

    /** O DNS da rede (o mesmo que o celular usa, em geral o do roteador) diz que [nome] é [ip]? Com memória de 10 min. */
    private val resolucoes = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Boolean>>()
    private fun resolveParaMim(nome: String, ip: String): Boolean {
        val agora = System.currentTimeMillis()
        resolucoes[nome]?.let { (quando, ok) -> if (agora - quando < 600_000L) return ok }
        val ok = runCatching { InetAddress.getAllByName(nome).any { it.hostAddress == ip } }.getOrDefault(false)
        resolucoes[nome] = agora to ok
        if (!ok) Log.i(TAG, "$nome não resolve para $ip aqui (roteador bloqueia DNS rebinding?): fica o certificado da TV")
        return ok
    }

    /** Pede um certificado novo se for a hora ([PublicCertRules.deveTentar]); [forcar] ignora a espera. Bloqueia. */
    @Synchronized fun cuidar(ctx: Context, forcar: Boolean = false): String {
        val id = id(ctx) ?: return "sem identidade do relay".also { ultimoErro = it }
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val folha = par(ctx)?.cadeia?.firstOrNull()
        val agora = System.currentTimeMillis()
        val falhas = p.getInt("falhas", 0)
        if (!forcar && !PublicCertRules.deveTentar(folha?.notBefore?.time, folha?.notAfter?.time, agora, falhas, p.getLong("ultima", 0L)))
            return "em dia"
        p.edit().putLong("ultima", agora).apply()
        return try {
            val conta = contaAcme(ctx)
            val chave = AcmeClient.novaChave()
            val cadeia = AcmeClient(diretorio(ctx), transporte, conta).obter(listOf(AcmeClient.curinga(id)), chave,
                publicar = { ds -> for (d in ds) txt(ctx, "PUT", JSONObject().put("value", d.valor).toString()) },
                limpar = { txt(ctx, "DELETE", "") })
            val par = PublicCertRules.Par(chave.private, cadeia.toTypedArray())
            check(PublicCertRules.serve(par.cadeia, id, System.currentTimeMillis())) { "certificado não tem o nome da TV" }
            salvar(ctx, par)
            atual = par; carregado = true; ultimoErro = null
            p.edit().putInt("falhas", 0).apply()
            Log.i(TAG, "certificado público novo até ${cadeia.first().notAfter}")
            "novo, vale até ${cadeia.first().notAfter}"
        } catch (e: Exception) {
            p.edit().putInt("falhas", falhas + 1).apply()
            ultimoErro = e.message ?: e.toString()
            Log.w(TAG, "não consegui o certificado público (${falhas + 1}ª falha seguida): $e")
            "falhou: $ultimoErro"
        }
    }

    /** O texto do adb (`EXTRAS --es o cert_publico`). */
    fun texto(ctx: Context): String {
        val folha = par(ctx)?.cadeia?.firstOrNull()
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return "id=${id(ctx)} certificado=${folha?.let { "até ${it.notAfter}" } ?: "nenhum"} falhas=${p.getInt("falhas", 0)}" +
            " ultima=${p.getLong("ultima", 0L)} erro=${ultimoErro ?: "-"} diretorio=${diretorio(ctx)}"
    }

    /** Let's Encrypt de produção; `EXTRAS --es o cert_publico_teste --ez ligado true` usa o de teste (staging). */
    private fun diretorio(ctx: Context): String =
        if (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("teste", false)) AcmeClient.LETS_ENCRYPT_TESTE else AcmeClient.LETS_ENCRYPT

    fun usarTeste(ctx: Context, ligado: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("teste", ligado).putInt("falhas", 0).putLong("ultima", 0L).apply()
        File(pasta(ctx), "conta.bin").delete(); File(pasta(ctx), "conta.pub").delete()   // a conta é de um servidor só
    }

    private fun txt(ctx: Context, metodo: String, corpo: String) {
        val r = RelayTv.pedir(ctx, metodo, "/v1/dns/txt", corpo.toByteArray())
        if (r.codigo !in 200..299) throw IOException("api.sofaly.tv /v1/dns/txt $metodo: HTTP ${r.codigo}")
    }

    /** HTTP do ACME: TLS normal do Android (o Let's Encrypt tem certificado público), sem seguir redirecionamento. */
    private val transporte = AcmeClient.Transporte { metodo, url, corpo, tipo ->
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = metodo; c.connectTimeout = 10_000; c.readTimeout = 20_000; c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", br.paulo.tvlauncher.Http.USER_AGENT)
            if (corpo != null) {
                c.doOutput = true; c.setRequestProperty("Content-Type", tipo); c.setFixedLengthStreamingMode(corpo.size)
                c.outputStream.use { it.write(corpo) }
            }
            val cod = c.responseCode
            val bytes = (if (cod >= 400) c.errorStream else c.inputStream)?.use { br.paulo.tvlauncher.Http.readLimited(it, 200_000, cut = false) } ?: ByteArray(0)
            val cab = c.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.firstOrNull().orEmpty() }
            AcmeClient.Resposta(cod, cab, bytes)
        } finally { c.disconnect() }
    }

    // ---------------------------------------------------------------- disco

    private fun contaAcme(ctx: Context): KeyPair {
        val p = pasta(ctx).apply { mkdirs() }
        val priv = File(p, "conta.bin"); val pub = File(p, "conta.pub")
        runCatching {
            if (priv.exists() && pub.exists()) {
                val kf = KeyFactory.getInstance("EC")
                return KeyPair(kf.generatePublic(X509EncodedKeySpec(pub.readBytes())), kf.generatePrivate(PKCS8EncodedKeySpec(PainelTls.decifrar(priv.readBytes()))))
            }
        }
        val k = AcmeClient.novaChave()
        gravar(File(p, "conta.pub"), k.public.encoded); gravar(File(p, "conta.bin"), PainelTls.cifrar(k.private.encoded))
        return k
    }

    private fun carregar(ctx: Context): PublicCertRules.Par? {
        val p = pasta(ctx)
        val cad = File(p, "cadeia.pem"); val chave = File(p, "chave.bin")
        if (!cad.exists() || !chave.exists()) return null
        val pk = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(PainelTls.decifrar(chave.readBytes())))
        val cadeia = AcmeClient.cadeia(cad.readText()).toTypedArray()
        if (cadeia.isEmpty() || !PainelTls.mesmoPar(pk, cadeia[0].publicKey)) return null
        return PublicCertRules.Par(pk, cadeia)
    }

    /** Chave antes da cadeia, cada uma em .novo e trocada depois: uma queda no meio deixa o par velho ou nenhum. */
    private fun salvar(ctx: Context, par: PublicCertRules.Par) {
        val p = pasta(ctx).apply { mkdirs() }
        val pem = par.cadeia.joinToString("") { CertificadoX509.pem(it) }
        File(p, "cadeia.pem").delete()   // sem cadeia, [carregar] ignora a chave: nunca fica chave nova com cadeia velha
        gravar(File(p, "chave.bin"), PainelTls.cifrar(par.chave.encoded))
        gravar(File(p, "cadeia.pem"), pem.toByteArray())
    }

    private fun gravar(f: File, bytes: ByteArray) {
        val novo = File(f.path + ".novo")
        java.io.FileOutputStream(novo).use { it.write(bytes); it.fd.sync() }
        if (!novo.renameTo(f)) throw IOException("não troquei ${f.name}")
    }
}
