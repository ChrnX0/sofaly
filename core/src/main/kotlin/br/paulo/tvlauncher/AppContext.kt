package br.paulo.tvlauncher

import android.content.Context

/**
 * O Context do app para quem não recebe um (os `object` de texto e de dados, também nos módulos de área). O LauncherApp
 * preenche na partida de cada processo, na mesma linha em que guarda a própria instância (refatoração G, 27/11/2026:
 * antes só o :app enxergava LauncherApp.instancia, e cada módulo precisaria de um gancho próprio).
 */
object AppContext {
    lateinit var app: Context
}

/**
 * Frase traduzida pelo idioma da TV (inglês é o padrão, res/values; português em res/values-pt).
 * Sem argumentos, NÃO passa pelo String.format: "Barras de cor 75%" derrubava o launcher ao abrir a Calibração
 * (UnknownFormatConversionException no onDraw, Sala 16/11/2026). getString(id, *vazio) formata mesmo assim.
 */
fun txt(id: Int, vararg args: Any): String =
    if (args.isEmpty()) AppContext.app.getString(id) else AppContext.app.getString(id, *args)

/** Plural traduzido (res/values/plurals): "1 minute" / "5 minutes", "1 minuto" / "5 minutos". */
fun txtN(id: Int, n: Int): String = AppContext.app.resources.getQuantityString(id, n, n)
