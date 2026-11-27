package br.paulo.tvlauncher.screensaver

import android.content.Context

/**
 * Estilo do nosso protetor de tela. Desde 21/11/2026 o protetor é a home que adormece ([SleepingHome]), com o fundo do
 * tema; o estilo é sempre "galeria". As escolhas do "Protetor Vivo 2" (relógio, posição, tamanho, widgets, elementos)
 * saíram em 24/11/2026: nenhuma tela as gravava nem as lia mais (teste de código, regra da inteligência global).
 * A pref antiga "protetor_estilo" continua no backup (BackupConfig) só para restaurar backups antigos sem erro.
 */
object Protetor {
    const val CHAVE = "protetor_estilo"
    const val GALERIA = "galeria"

    /**
     * Sempre Galeria (dono, 11/11/2026: o protetor do Escuro tem que ser IGUAL ao da Galeria, só com o wallpaper escuro).
     * A pref antiga "classico" (vinda de um backup ou da opção que existia nas Configurações) é ignorada.
     */
    @Suppress("UNUSED_PARAMETER")
    fun estilo(ctx: Context): String = GALERIA
}
