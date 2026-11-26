package br.paulo.tvlauncher.relay

import android.content.Context
import android.content.Intent
import android.util.Log
import br.paulo.tvlauncher.panel.PainelVizinhos
import org.json.JSONObject

/**
 * TVs de outras casas pelo api.sofaly.tv (Assistir junto sem Tailscale, 26/11/2026). A parte pura (código, apresentação,
 * números de conferência, a lista fechada da Sessão junto) está em [RelayHouses]; a tela é a [CasasActivity].
 *
 * Quem MOSTRA o código ([abrirCodigo]) é quem confirma: quando a apresentação da outra TV chega, a tela pergunta com os
 * 4 números; no OK ([confirmar]) as duas se guardam. Quem DIGITA o código ([usarCodigo]) só guarda a outra quando a
 * apresentação dela chega de volta, e isso só acontece depois do OK de lá.
 *
 * Estado em memória de propósito: um pareamento que o launcher perdeu no meio (reinício) só recomeça com código novo.
 */
object RelayCasas {
    private const val TAG = "TvLauncher.Casas"
    /** O código vale 10 min no servidor (LIMITS.pairingTtlSec); aqui a espera é a mesma. */
    const val VALIDADE_MS = 10 * 60_000L

    /** A outra TV chegou e espera o OK daqui (lado que mostrou o código). */
    data class Pendente(val pedido: RelayPairing.Pedido, val numeros: String)
    /** Terminou: [nome] da outra casa e os números para conferir; [erro] quando não deu. */
    data class Fim(val nome: String?, val numeros: String?, val erro: Boolean)

    @Volatile var codigo: String? = null; private set
    @Volatile private var codigoAte = 0L
    @Volatile private var esperando: String? = null
    @Volatile private var esperandoAte = 0L
    @Volatile var pendente: Pendente? = null; private set
    @Volatile var fim: Fim? = null; private set
    /** A tela aberta escuta aqui (chamado fora da thread da tela). */
    @Volatile var aoMudar: (() -> Unit)? = null

    fun emAndamento(): Boolean {
        val agora = System.currentTimeMillis()
        return (codigo != null && agora < codigoAte) || (esperando != null && agora < esperandoAte) || pendente != null
    }

    fun casas(ctx: Context): List<RelayTv.Pareado> = RelayTv.pareados(ctx).filter { it.tv }

    private fun minha(ctx: Context): ByteArray? {
        val eu = RelayTv.identidade(ctx) ?: return null
        return RelayHouses.apresentacao(eu.id, eu.edPub, eu.xPub, PainelVizinhos.nomeDaTv(ctx))
    }

    private fun avisar() = runCatching { aoMudar?.invoke() }

    fun limpar() { codigo = null; esperando = null; pendente = null; fim = null }

    /** Lado A: pede um código ao servidor. Bloqueia (rede). Null = não deu (sem internet, servidor fora). */
    fun abrirCodigo(ctx: Context): String? {
        limpar()
        val r = runCatching { RelayTv.pedir(ctx, "POST", "/v1/pairing") }.getOrNull() ?: return null
        val c = if (r.codigo == 201) runCatching { JSONObject(r.corpo).optString("code") }.getOrNull() else null
        if (c == null || !RelayHouses.CODIGO.matches(c)) { Log.w(TAG, "código: HTTP ${r.codigo}"); return null }
        codigo = c; codigoAte = System.currentTimeMillis() + VALIDADE_MS
        RelayTv.ajustar(ctx)
        return c
    }

    /** Lado B: usa o código digitado. Bloqueia. false = código errado/vencido ou sem rede. */
    fun usarCodigo(ctx: Context, digitado: String): Boolean {
        limpar()
        val c = RelayHouses.codigo(digitado) ?: return false
        val corpo = minha(ctx) ?: return false
        val r = runCatching { RelayTv.pedir(ctx, "POST", "/v1/pairing/$c", corpo) }.getOrNull() ?: return false
        val tv = if (r.codigo == 200) runCatching { JSONObject(r.corpo).optString("tv") }.getOrNull() else null
        if (tv == null || !RelayCrypto.ID_RE.matches(tv)) { Log.w(TAG, "usar código: HTTP ${r.codigo}"); return false }
        if (tv == RelayTv.identidade(ctx)?.id) return false   // o código desta própria TV
        esperando = tv; esperandoAte = System.currentTimeMillis() + VALIDADE_MS
        RelayTv.ajustar(ctx)
        return true
    }

    /**
     * Algo chegou na caixa postal. true = era uma apresentação (tratada ou descartada aqui); false = não é comigo
     * (envelope cifrado: segue o caminho normal do RelayTv).
     */
    fun chegou(ctx: Context, de: String, dados: ByteArray): Boolean {
        if (dados.isEmpty() || dados[0] != RelayHouses.APRESENTACAO) return false
        val p = RelayHouses.lerApresentacao(dados, de) ?: return true
        val eu = RelayTv.identidade(ctx) ?: return true
        val agora = System.currentTimeMillis()
        val numeros = RelayHouses.conferencia(eu.xPub, p.xPub)
        when {
            // B: a TV do código confirmou do lado de lá.
            esperando == p.id && agora < esperandoAte -> {
                guardar(ctx, p)
                esperando = null
                fim = Fim(p.nome, numeros, erro = false)
                avisar()
            }
            // A: alguém digitou o nosso código. Uma vez só (o servidor também apaga o código).
            codigo != null && agora < codigoAte && pendente == null -> {
                codigo = null
                pendente = Pendente(p, numeros)
                avisar()
                if (aoMudar == null) abrirTela(ctx)
            }
            else -> Log.i(TAG, "apresentação de $de fora de hora: ignorada")
        }
        return true
    }

    /** A: OK na tela. Guarda a outra TV e manda a apresentação daqui. Bloqueia (rede). */
    fun confirmar(ctx: Context): Boolean {
        val p = pendente ?: return false
        pendente = null
        val corpo = minha(ctx) ?: return false
        val ok = runCatching { RelayTv.pedir(ctx, "POST", "/v1/envelope/${p.pedido.id}", corpo).codigo == 201 }.getOrDefault(false)
        if (ok) guardar(ctx, p.pedido)
        fim = Fim(p.pedido.nome, p.numeros, erro = !ok)
        avisar()
        return ok
    }

    fun recusar() { pendente = null; avisar() }

    private fun guardar(ctx: Context, p: RelayPairing.Pedido) {
        RelayTv.parear(ctx, RelayTv.Pareado(p.id, p.xPub, p.nome, tv = true))
        RelayTv.ajustar(ctx)
        Log.i(TAG, "casa conectada: ${p.id}")
    }

    fun remover(ctx: Context, id: String) { if (casas(ctx).any { it.id == id }) RelayTv.esquecer(ctx, id) }

    private fun abrirTela(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(ctx, br.paulo.tvlauncher.CasasActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "tela: ${it.javaClass.simpleName}") }
    }

    // ---------------------------------------------------------------- Sessão junto pelo relay

    /** Host que a Sessão junto usa para uma casa do relay (as do Tailscale são "onetv-<nome>"). */
    const val PREFIXO = "relay:"
    fun host(id: String) = PREFIXO + id
    fun idDoHost(host: String): String? = host.removePrefix(PREFIXO).takeIf { host.startsWith(PREFIXO) && RelayCrypto.ID_RE.matches(it) }

    /** Manda uma chamada da Sessão junto para a casa [id]. Bloqueia. true = o servidor guardou. */
    fun enviarJunto(ctx: Context, id: String, caminho: String, corpo: JSONObject?): Boolean {
        val casa = casas(ctx).firstOrNull { it.id == id } ?: return false
        val m = RelayHouses.junto(caminho, corpo, System.currentTimeMillis()) ?: return false
        return RelayTv.enviar(ctx, casa, m)
    }

    fun receberJunto(ctx: Context, de: RelayTv.Pareado, m: RelayEnvelope.Mensagem) {
        val (caminho, corpo) = RelayHouses.lerJunto(m) ?: run { Log.w(TAG, "fora da lista: ${m.tipo.take(20)}"); return }
        br.paulo.tvlauncher.SessaoJunto.doRelay(ctx, host(de.id), caminho, corpo)
    }
}
