package br.paulo.tvlauncher.weather

import android.content.Context
import java.net.HttpURLConnection

/**
 * O que o módulo do clima precisa do app e não pode buscar sozinho (o :weather não enxerga o :app). O LauncherApp
 * preenche na partida do processo, na mesma linha em que guarda a própria instância.
 */
object WeatherHost {
    /** Context do app, para textos traduzidos (fases da lua, nomes do tempo) nos `object` que não recebem um. */
    lateinit var app: Context

    /**
     * Assina um pedido ao api.sofaly.tv com a identidade da TV (cota do dia por TV, item 23). Sem o app (teste, outro
     * processo) fica vazio: o pedido segue sem assinatura e conta só na cota da rede, como já acontecia sem identidade.
     */
    @Volatile var sign: (HttpURLConnection) -> Unit = {}

    /** A fonte de previsão pelo plano (DataSources.weather, no app). Sem o app: a do Básico ([WeatherSource.semPlano]). */
    @Volatile var kind: (Context) -> WeatherSource.Kind = { WeatherSource.semPlano() }

    fun txt(id: Int, vararg args: Any): String = if (args.isEmpty()) app.getString(id) else app.getString(id, *args)
}
