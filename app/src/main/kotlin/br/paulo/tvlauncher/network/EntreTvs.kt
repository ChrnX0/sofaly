package br.paulo.tvlauncher.network

import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.JsonSeguro
import br.paulo.tvlauncher.mcp.McpServidor
import br.paulo.tvlauncher.mcp.TunelTailscale
import br.paulo.tvlauncher.player.JuntoRegras
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/**
 * Conversa entre as TVs da mesma conta Tailscale (degrau 4, 17/11/2026): Sessão junto e "Continuar em outra TV".
 * Regras em [JuntoRegras]. Desligado de fábrica: [ligado] é o opt-in "TVs da família" em Configurações › Família ›
 * Família conectada. Ligado, o servidor do túnel sobe (McpServidor.precisaServidor) e o túnel publica a lista de
 * aparelhos da conta (TUNEL_PARES), sem abrir o painel na rede Tailscale.
 *
 * Cada TV fala com a outra pelo endereço PÚBLICO dela (https://onetv-<nome>.<rede>.ts.net, o mesmo do link da
 * família), com TLS verificado normalmente. Quem recebe um convite confere de volta, na TV que diz ter mandado, que o
 * convite existe (ninguém se passa por outra TV da conta). Só vão comandos e posições; nenhum vídeo.
 */
object EntreTvs {
    private const val TAG = "TvLauncher.EntreTvs"
    private const val PREFS = "entre_tvs"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun ligado(ctx: Context) = prefs(ctx).getBoolean("ligado", false)

    fun definir(ctx: Context, sim: Boolean) {
        prefs(ctx).edit().putBoolean("ligado", sim).commit()
        Fundo.rodar("entre-tvs") { McpServidor.ajustar(ctx); TunelTailscale.reiniciar(); TunelTailscale.ajustar(ctx) }
    }

    /** As outras TVs da conta (lista que o túnel publica). Vazio: opção desligada, túnel parado ou sem outras TVs. */
    fun outras(ctx: Context): List<JuntoRegras.Tv> =
        if (!ligado(ctx)) emptyList() else JuntoRegras.outrasTvs(TunelTailscale.pares, TunelTailscale.nome(ctx))

    /**
     * As TVs com quem dá para assistir junto: as da conta Tailscale ([outras]) e as casas ligadas pelo código de 8
     * letras (relay do api.sofaly.tv, [br.paulo.tvlauncher.relay.RelayCasas]; host "relay:<id>", sempre "online": a
     * caixa postal guarda o convite até a TV de lá ligar). "Continuar em outra TV" segue só com [outras].
     */
    fun paraJunto(ctx: Context): List<JuntoRegras.Tv> =
        outras(ctx) + br.paulo.tvlauncher.relay.RelayCasas.casas(ctx).map { JuntoRegras.Tv(br.paulo.tvlauncher.relay.RelayCasas.host(it.id), true) }

    /** Nome para mostrar: o que a casa do relay disse ao conectar, ou o nome curto do Tailscale. */
    fun nome(ctx: Context, host: String): String =
        br.paulo.tvlauncher.relay.RelayCasas.idDoHost(host)?.let { id -> br.paulo.tvlauncher.relay.RelayCasas.casas(ctx).firstOrNull { it.id == id }?.nome }
            ?: JuntoRegras.nomeBonito(host)

    fun meuHost(ctx: Context): String? = JuntoRegras.hostCurto(TunelTailscale.nome(ctx))

    /** Resposta de uma chamada: código HTTP (0 = sem rede), corpo JSON e quanto levou (ida e volta). */
    data class Resposta(val codigo: Int, val json: JSONObject?, val ms: Long)

    /**
     * POST (ou GET, sem [corpo]) em https://<host da outra TV><caminho>. Bloqueia (até 6 s): chamar fora da thread da
     * tela. Só vai para TV da lista da conta, montada pelo sufixo do NOSSO nome (nada de endereço vindo de fora).
     */
    fun chamar(ctx: Context, host: String, caminho: String, corpo: JSONObject? = null): Resposta {
        // Casa do relay: vai cifrado pela caixa postal, sem resposta da outra TV (só "o servidor guardou").
        br.paulo.tvlauncher.relay.RelayCasas.idDoHost(host)?.let { id ->
            val t0 = System.currentTimeMillis()
            val ok = br.paulo.tvlauncher.relay.RelayCasas.enviarJunto(ctx, id, caminho, corpo)
            return Resposta(if (ok) 200 else 0, JSONObject().put("ok", ok), System.currentTimeMillis() - t0)
        }
        val endereco = JuntoRegras.enderecoDoPar(TunelTailscale.nome(ctx), host) ?: return Resposta(0, null, 0)
        val inicio = System.currentTimeMillis()
        var c: HttpURLConnection? = null
        return try {
            c = (URL("https://$endereco$caminho").openConnection() as HttpsURLConnection).apply {
                connectTimeout = 4_000; readTimeout = 6_000; instanceFollowRedirects = false; useCaches = false
                setRequestProperty("Accept", "application/json")
                if (corpo != null) {
                    requestMethod = "POST"; doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    val b = corpo.toString().toByteArray()
                    setFixedLengthStreamingMode(b.size)
                    outputStream.use { it.write(b) }
                }
            }
            val cod = c.responseCode
            val texto = (if (cod in 200..299) c.inputStream else c.errorStream)?.use { s -> s.readBytes().take(64 * 1024).toByteArray().toString(Charsets.UTF_8) }
            Resposta(cod, texto?.let { JsonSeguro.objeto(it) }, System.currentTimeMillis() - inicio)
        } catch (e: Exception) {
            Log.w(TAG, "$host$caminho: ${e.javaClass.simpleName}")
            Resposta(0, null, System.currentTimeMillis() - inicio)
        } finally { c?.disconnect() }
    }
}
