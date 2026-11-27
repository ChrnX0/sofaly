package br.paulo.tvlauncher.weather

/**
 * As prefs "clima" (Prefs.CLIMA) misturam duas coisas: a ESCOLHA da pessoa (o modo da chance de chuva, RainChance) e o
 * CACHE da previsão que a HOME grava sozinha a cada 5 min (temp, max, min, chuva, horas, linha, lat, lon, em...).
 * Quem vigia mudanças de ajuste (o Desfazer e o diário de ajustes da Sofy) só pode contar a escolha: o cache não é
 * mudança de ninguém. Achado em 27/11/2026: o cache virava "Mudou: ... [Desfazer]" na tela aberta, o botão pegava o
 * foco e o VOLTAR só fechava o aviso (ClimaNaoViraMudancaTest); no diário da Sofy ele empurrava para fora os ajustes reais.
 * Lista de PERMITIDAS de propósito: chave nova de cache não volta a virar mudança; ajuste novo nas prefs "clima" entra aqui.
 */
object WeatherPrefs {
    /** Chaves das prefs "clima" que são escolha da pessoa. RainChance.CHAVE = "chance_modo" (RainChanceChaveTest confere). */
    val CHOICES = setOf("chance_modo")

    fun isChoice(key: String): Boolean = key.substringBefore('@') in CHOICES
}
