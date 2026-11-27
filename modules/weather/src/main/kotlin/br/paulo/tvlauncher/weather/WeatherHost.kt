package br.paulo.tvlauncher.weather

import android.content.Context

/**
 * O que o módulo do clima precisa do app e não pode buscar sozinho (o :weather não enxerga o :app). O LauncherApp
 * preenche na partida do processo. O Context para textos vem do AppContext e a assinatura do relay do RelaySign, ambos no :core.
 */
object WeatherHost {
    /** A fonte de previsão pelo plano (DataSources.weather, no app). Sem o app: a do Básico ([WeatherSource.semPlano]). */
    @Volatile var kind: (Context) -> WeatherSource.Kind = { WeatherSource.semPlano() }
}
