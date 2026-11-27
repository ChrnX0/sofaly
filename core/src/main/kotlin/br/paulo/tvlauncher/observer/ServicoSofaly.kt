package br.paulo.tvlauncher.observer

import org.json.JSONObject

/**
 * Linha "Sofaly" do Diagnóstico (Brainstorm 26/11/2026, item 25): qual parte do nosso serviço (api.sofaly.tv) está
 * fora. Puro (ServicoSofalyTest); quem pergunta é o [Diagnostico.servico], em GET /v1/health?deep=1
 * (server/api/src/health.js), que diz por serviço se ele tem chave e binding, nunca o valor da chave.
 *
 * Por quê: clima, podcasts, transcrição e plugins moram no mesmo Worker; quando um parava, a TV só mostrava "não
 * funcionou" em cada tela, e ninguém sabia que era do servidor.
 */
object ServicoSofaly {
    const val URL = "https://api.sofaly.tv/v1/health?deep=1"

    /** Serviços que a TV mostra, na ordem. O clima do plano Pro fica de fora: só vale para quem tem o Pro. */
    val MOSTRAR = listOf("weather_open", "podcasts", "ai", "community", "mailbox")

    enum class Estado { OK, FALTA, DESLIGADO, SEM_RESPOSTA }

    class Resultado(val estado: Estado, val faltando: List<String>) {
        /** 0 = ok, 1 = atenção (parte fora ou desligado), 2 = sem resposta. */
        val nivel get() = when (estado) { Estado.OK -> 0; Estado.FALTA, Estado.DESLIGADO -> 1; Estado.SEM_RESPOSTA -> 2 }
    }

    /** [codigo] HTTP (-1 = não chegou) e o corpo. 503 = a chave geral DISABLED do servidor. */
    fun ler(codigo: Int, corpo: String?): Resultado {
        if (codigo == 503) return Resultado(Estado.DESLIGADO, emptyList())
        if (codigo != 200) return Resultado(Estado.SEM_RESPOSTA, emptyList())
        val s = runCatching { JSONObject(corpo ?: "").optJSONObject("services") }.getOrNull()
            ?: return Resultado(Estado.OK, emptyList())   // servidor antigo, sem ?deep: respondeu, então está de pé
        val falta = MOSTRAR.filter { s.has(it) && !s.optBoolean(it, true) }
        return Resultado(if (falta.isEmpty()) Estado.OK else Estado.FALTA, falta)
    }
}
