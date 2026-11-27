package br.paulo.tvlauncher

import java.net.HttpURLConnection

/**
 * Assinatura X-Sofaly-* dos pedidos às rotas abertas do api.sofaly.tv (cota do dia por TV, item 23 do Brainstorm 26/11),
 * para os módulos de área (refatoração G). A chave mora no app (RelayTv, Keystore); o LauncherApp liga [headers] a ela.
 * Sem ela (testes JVM, ou antes do onCreate), vazio: o pedido segue sem assinatura e conta só na cota da rede.
 */
object RelaySign {
    /** (método, caminho sem a consulta, corpo) -> cabeçalhos. */
    @Volatile var headers: (String, String, ByteArray) -> Map<String, String> = { _, _, _ -> emptyMap() }

    /** Assina uma conexão já aberta (o caminho sai da própria URL). */
    fun sign(c: HttpURLConnection, body: ByteArray = ByteArray(0)) {
        for ((k, v) in headers(c.requestMethod, c.url.path, body)) c.setRequestProperty(k, v)
    }
}
