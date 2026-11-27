package br.paulo.tvlauncher

import java.text.Normalizer

/**
 * Nome da TV na rede Tailscale: "<prefixo>-<nome do aparelho>", ex.: "sofaly-sala".
 *
 * Prefixo (27/11/2026, Paulo aprovou): TV nova nasce "sofaly-"; TV que já entrou no Tailscale com o nome antigo
 * ("onetv-", de 10/11/2026) continua com ele até a troca combinada (outro nome = o endereço público muda, e o conector
 * MCP e as outras TVs da casa precisam do novo). Quem procura TVs na conta aceita os dois ([ehTv], [semPrefixo]).
 *
 * O nome do aparelho é o das configurações da TV (Settings.Global "device_name", ex.: "Sala"). O resultado vira o
 * começo do endereço (sofaly-sala.tail1234.ts.net), então precisa valer como nome DNS: só a-z, 0-9 e hífen, sem hífen
 * nas pontas, até 63 caracteres. "Sala de Estar" -> "sofaly-sala-de-estar"; "Cozinha Nº 2" -> "sofaly-cozinha-n-2".
 * Puro (sem Android): testado na JVM em NomeNoTest.
 */
object NomeNo {
    const val PREFIXO = "sofaly"          // a marca (27/11/2026)
    const val PREFIXO_ANTIGO = "onetv"    // TVs que entraram no Tailscale antes da troca
    val PREFIXOS = listOf(PREFIXO, PREFIXO_ANTIGO)
    private const val MAX = 63    // limite de um rótulo DNS

    fun montar(nomeAparelho: String?, prefixo: String = PREFIXO): String {
        val p = prefixo.takeIf { it in PREFIXOS } ?: PREFIXO
        // Tira o acento: "ç" vira "c" + sinal solto (NFD), e o sinal some no filtro abaixo.
        val semAcento = Normalizer.normalize(nomeAparelho ?: "", Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").lowercase()
        val miolo = semAcento.replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val nome = if (miolo.isEmpty()) "$p-tv" else "$p-$miolo"
        return nome.take(MAX).trimEnd('-')
    }

    /** O prefixo de um nome curto ("onetv-sala" -> "onetv"), ou null se não for o nome de uma TV nossa. */
    fun prefixoDe(host: String): String? = PREFIXOS.firstOrNull { host.startsWith("$it-") && host.length > it.length + 1 }

    /** É o nome de uma TV com o launcher (qualquer um dos dois prefixos)? */
    fun ehTv(host: String): Boolean = prefixoDe(host) != null

    /** "sofaly-quarto-dos-pais" ou "onetv-quarto-dos-pais" -> "quarto-dos-pais"; outro nome volta como veio. */
    fun semPrefixo(host: String): String = prefixoDe(host)?.let { host.removePrefix("$it-") } ?: host

    /**
     * Qual prefixo esta TV usa. [salvo]: o que ela já usa (fica para sempre, até a troca pedida). Sem nada salvo:
     * se o endereço que ela já recebeu ([enderecoSalvo], "onetv-sala.tail1234.ts.net") ou o crachá do Tailscale
     * ([jaEntrou]) mostram que ela entrou com o código antigo, fica o antigo; senão é TV nova e nasce com o novo.
     */
    fun prefixoPara(salvo: String?, enderecoSalvo: String?, jaEntrou: Boolean): String = when {
        salvo in PREFIXOS -> salvo!!
        enderecoSalvo != null -> prefixoDe(enderecoSalvo.substringBefore('.').lowercase()) ?: PREFIXO_ANTIGO
        jaEntrou -> PREFIXO_ANTIGO
        else -> PREFIXO
    }
}
