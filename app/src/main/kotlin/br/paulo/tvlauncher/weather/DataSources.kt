package br.paulo.tvlauncher.weather

import android.content.Context
import br.paulo.tvlauncher.alarm.FaixaDias
import br.paulo.tvlauncher.player.ServicosContas
import br.paulo.tvlauncher.settings.Planos
import br.paulo.tvlauncher.weather.WeatherSource.Kind

/**
 * De onde vêm os dados de fora (19/11/2026, regra do Paulo): "o que existir de alternativa grátis hoje usa para o plano
 * básico, o que for pago começa no pro e para o ultra ficam os mais especiais". Ponto único: nenhuma tela escolhe a
 * fonte sozinha. Cada fonte paga tem DUAS travas: o plano ([Planos]) e a licença/contrato (as constantes abaixo). Sem a
 * licença, todo mundo fica na grátis, inclusive a Sala (que é Ultra por não ter venda ativa).
 *
 * | Dado     | Básico (grátis, uso comercial liberado)       | Pro (pago)                          | Ultra (especial)                 |
 * |----------|-----------------------------------------------|-------------------------------------|----------------------------------|
 * | Clima    | 4 modelos abertos pelo api.sofaly.tv (24/11)  | WeatherAPI pelo api.sofaly.tv       | Open-Meteo pago: 3 modelos + ar  |
 * | Fichas   | Trakt (developer.trakt.tv/docs/api-use-policy)| TMDB (contrato comercial)           | (igual ao Pro)                   |
 * | Simkl    | nota de quem conectou a própria conta         | nota para todos (licença US$ 100/mês)| (igual ao Pro)                  |
 *
 * Clima do Básico (24/11/2026): consenso de ECMWF/AIFS/GFS/ICON ([OpenModelsSource]); servidor sem a fonte ou fora do
 * ar = MET Norway (api.met.no/doc/FAQ), como antes. A WeatherAPI do Pro que falhar cai nele também.
 *
 * Puro (DataSourcesTest), menos as funções que recebem Context.
 */
object DataSources {
    // Clima: as travas de licença e a ordem de reserva moram no módulo :weather (WeatherSource); os nomes seguem aqui.
    const val OPEN_METEO_LICENSED = WeatherSource.OPEN_METEO_LICENSED
    const val WEATHERAPI_ENABLED = WeatherSource.WEATHERAPI_ENABLED
    const val OPEN_MODELS_ENABLED = WeatherSource.OPEN_MODELS_ENABLED
    /** TMDB comercial: só com contrato escrito (themoviedb.org/api-terms-of-use). A chave pessoal de hoje não serve. */
    const val TMDB_LICENSED = false
    /** Simkl: nota para quem não conectou a conta exige a licença paga (Simkl, 17/11/2026). */
    const val SIMKL_LICENSED = false

    fun weather(plano: Planos.Plano, openMeteoLicensed: Boolean = OPEN_METEO_LICENSED,
                weatherApiEnabled: Boolean = WEATHERAPI_ENABLED, weatherApiDown: Boolean = false,
                openModelsEnabled: Boolean = OPEN_MODELS_ENABLED, openModelsDown: Boolean = false): Kind = when {
        openMeteoLicensed && Planos.permite(plano, Planos.Recurso.CLIMA_3_MODELOS) -> Kind.OPEN_METEO
        weatherApiEnabled && !weatherApiDown && Planos.permite(plano, Planos.Recurso.CLIMA_PRO) -> Kind.WEATHERAPI
        openModelsEnabled && !openModelsDown -> Kind.OPEN_MODELS
        else -> Kind.MET_NORWAY
    }

    fun weather(ctx: Context): Kind = weather(Planos.atual(ctx), weatherApiDown = WeatherApiSource.emPausa(),
        openModelsDown = OpenModelsSource.emPausa())

    /** Fichas do TMDB: só com contrato E plano Pro E a chave no build. Fora disso, as fichas vêm do Trakt. */
    fun tmdb(plano: Planos.Plano, temChave: Boolean, licensed: Boolean = TMDB_LICENSED): Boolean =
        licensed && temChave && Planos.permite(plano, Planos.Recurso.FICHAS_TMDB)

    fun tmdb(ctx: Context): Boolean = tmdb(Planos.atual(ctx), ServicosContas.chavesApp().tmdb.isNotBlank())

    /** Nota do Simkl para quem NÃO conectou a conta: licença + Pro. Quem conectou vê a nota em qualquer plano. */
    fun simklSemLogin(plano: Planos.Plano, licensed: Boolean = SIMKL_LICENSED): Boolean =
        licensed && Planos.permite(plano, Planos.Recurso.SIMKL_NOTA_TODOS)

    fun simklSemLogin(ctx: Context): Boolean = simklSemLogin(Planos.atual(ctx))

    /** A previsão da fonte deste aparelho (a ordem de reserva está em [WeatherSource.forecast]). */
    fun forecast(ctx: Context, lat: Double, lon: Double, desdeAgora: Boolean, dias: Int = FaixaDias.DIAS): org.json.JSONObject? =
        WeatherSource.forecast(weather(ctx), lat, lon, desdeAgora, dias)
}
