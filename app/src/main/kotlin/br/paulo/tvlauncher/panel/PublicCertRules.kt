package br.paulo.tvlauncher.panel

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager

/**
 * Regras puras do certificado público da TV (item 26, opção A). Testadas na JVM em PublicCertRulesTest, com um
 * aperto de mão TLS de verdade.
 *
 * O painel https (porta 8444) passa a ter DOIS certificados, escolhidos pelo nome que o aparelho pede (SNI):
 *  - `192-168-1-153.<id>.lan.sofaly.tv` → o público (Let's Encrypt): abre sem instalar nada.
 *  - qualquer outro nome, ou IP (que não manda SNI) → o da própria TV, como antes. O app do celular entra pelo IP com
 *    o certificado fixado no QR e não muda nada; quem já instalou a CA da TV também segue igual.
 * Assim a "volta" para o certificado de hoje é automática: se o roteador bloquear nome público apontando para IP de
 * casa (proteção contra DNS rebinding), o nome não resolve, o cadeado.js do painel tenta o próximo endereço e cai no
 * nome/IP de sempre.
 */
object PublicCertRules {
    /** Uma chave e a cadeia (folha primeiro). */
    class Par(val chave: PrivateKey, val cadeia: Array<X509Certificate>)

    /**
     * Escolhe o certificado pelo SNI. [propria] sempre existe; [publica] pode ser null (ainda não emitido, vencido).
     * Os dois são lidos a cada aperto de mão: renovar o público não precisa reabrir a porta.
     */
    class PorNome(private val propria: () -> Par, private val publica: () -> Par?, private val sufixo: () -> String?) :
        X509ExtendedKeyManager() {
        private fun alias(tipo: String?, sessao: SSLSession?): String? {
            if (tipo != null && !tipo.startsWith("EC")) return null
            val pedido = (sessao as? ExtendedSSLSession)?.requestedServerNames
                ?.firstNotNullOfOrNull { (it as? SNIHostName)?.asciiName }
            return if (usaPublico(pedido, sufixo(), publica() != null)) PUBLICO else PROPRIO
        }
        override fun chooseServerAlias(tipo: String?, emissores: Array<out Principal>?, s: Socket?) =
            alias(tipo, (s as? SSLSocket)?.handshakeSession)
        override fun chooseEngineServerAlias(tipo: String?, emissores: Array<out Principal>?, e: SSLEngine?) =
            alias(tipo, e?.handshakeSession)
        override fun getServerAliases(tipo: String?, emissores: Array<out Principal>?) =
            if (tipo == null || tipo.startsWith("EC")) arrayOf(PROPRIO, PUBLICO) else null
        override fun getCertificateChain(a: String?): Array<X509Certificate>? = when (a) {
            PROPRIO -> propria().cadeia.clone()
            PUBLICO -> (publica() ?: propria()).cadeia.clone()
            else -> null
        }
        override fun getPrivateKey(a: String?): PrivateKey? = when (a) {
            PROPRIO -> propria().chave
            PUBLICO -> (publica() ?: propria()).chave
            else -> null
        }
        override fun getClientAliases(tipo: String?, emissores: Array<out Principal>?): Array<String>? = null
        override fun chooseClientAlias(tipos: Array<out String>?, emissores: Array<out Principal>?, s: Socket?): String? = null
        private companion object { const val PROPRIO = "painel"; const val PUBLICO = "publico" }
    }

    /** O nome pedido é um `<ip>.<id>.lan.sofaly.tv` desta TV, e há certificado público? (Um rótulo só antes do sufixo.) */
    fun usaPublico(pedido: String?, sufixo: String?, temPublico: Boolean): Boolean {
        if (!temPublico || pedido == null || sufixo.isNullOrEmpty()) return false
        val p = pedido.lowercase().trimEnd('.')
        if (!p.endsWith(".$sufixo")) return false
        val rotulo = p.removeSuffix(".$sufixo")
        return rotulo.isNotEmpty() && '.' !in rotulo
    }

    /**
     * Hora de pedir um certificado novo? Sem certificado, sim. Com um, quando falta 1/3 da validade (Let's Encrypt de
     * 90 dias: 30 dias antes; vale também para os de validade menor que o LE vai adotar). Depois de [falhas] seguidas,
     * espera [espera] desde a última tentativa.
     */
    fun deveTentar(inicio: Long?, fim: Long?, agora: Long, falhas: Int, ultimaTentativa: Long): Boolean {
        if (agora - ultimaTentativa < espera(falhas)) return false
        if (inicio == null || fim == null || fim <= agora) return true
        return fim - agora <= (fim - inicio) / 3
    }

    /** Espera depois de [falhas] seguidas: 0, 1 h, 2 h, 4 h ... até 24 h (o LE limita 5 falhas por hora por nome). */
    fun espera(falhas: Int): Long = if (falhas <= 0) 0L else minOf(HORA_MS shl minOf(falhas - 1, 5), DIA_MS)

    /** O certificado [cadeia] serve agora para a TV [id]? (Não venceu e tem o curinga dela.) */
    fun serve(cadeia: Array<X509Certificate>?, id: String?, agora: Long): Boolean {
        val folha = cadeia?.firstOrNull() ?: return false
        if (id == null || agora < folha.notBefore.time || agora >= folha.notAfter.time) return false
        val curinga = runCatching { AcmeClient.curinga(id) }.getOrNull() ?: return false
        return CertificadoX509.nomesDo(folha).contains(curinga)
    }

    /** Os nomes públicos da TV [id] para os IPs dela agora (os que o painel aceita no Host da porta https). */
    fun nomes(id: String?, ips: Collection<String>): Set<String> =
        if (id == null) emptySet() else ips.mapNotNull { runCatching { AcmeClient.nomeComIp(id, it) }.getOrNull() }.toSet()

    private const val HORA_MS = 3_600_000L
    private const val DIA_MS = 24 * HORA_MS
}
