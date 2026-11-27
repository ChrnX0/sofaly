package br.paulo.tvlauncher

import org.json.JSONObject

/**
 * Versões que o servidor manda segurar (Brainstorm 26/11/2026, item 22; rota GET /v1/updates/hold, server/api/src/updates.js).
 *
 * Por quê: a vigia de cada TV ([VigiaRegras]) só aprende com as quedas DELA. Quando um Release trava em várias casas,
 * o Paulo põe o número na lista do servidor e as TVs que ainda não instalaram esperam a próxima versão. Falha de
 * rede ou resposta estranha = não segura (fail-open): o servidor fora do ar nunca trava a atualização de ninguém, e a
 * vigia local continua valendo.
 */
object VersoesSeguradas {
    const val URL = "https://api.sofaly.tv/v1/updates/hold"

    /** {"hold":[475010, ...]} -> os números (puro, VersoesSeguradasTest). Qualquer coisa fora disso = nenhuma. */
    fun ler(texto: String?): Set<Long> = runCatching {
        val a = JSONObject(texto ?: return emptySet()).optJSONArray("hold") ?: return emptySet()
        (0 until minOf(a.length(), 50)).mapNotNull { i -> a.optLong(i, 0L).takeIf { it > 0L } }.toSet()
    }.getOrDefault(emptySet())

    /** Bloqueante (o Atualizador já roda fora da tela). 5 s no máximo: é um extra, não pode atrasar a atualização. */
    fun segurada(codigo: Long): Boolean = codigo in ler(Http.getTextOrNull(URL, connectMs = 5_000, readMs = 5_000, maxBytes = 8_192))
}
