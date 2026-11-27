package br.paulo.tvlauncher

import br.paulo.tvlauncher.resources.R

// txt e txtN moram no :core (AppContext.kt) desde a refatoração G: os módulos de área também usam.

/** "10 minutos", "1 hora e 30 minutos", "45 segundos", no idioma da TV. Para avisos e confirmações na tela. */
fun duracaoFalada(segundos: Int): String {
    val h = segundos / 3600; val m = (segundos % 3600) / 60; val s = segundos % 60
    val partes = mutableListOf<String>()
    if (h > 0) partes += txtN(R.plurals.dur_horas, h)
    if (m > 0) partes += txtN(R.plurals.dur_minutos, m)
    if (s > 0) partes += txtN(R.plurals.dur_segundos, s)
    return partes.joinToString(txt(R.string.dur_e)).ifEmpty { txtN(R.plurals.dur_segundos, 0) }
}
