package br.paulo.tvlauncher.companion

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.view.InputDevice
import android.view.KeyEvent

/**
 * Achar o controle (24/11/2026, proposta B; no Apple TV o iPhone acha o Siri Remote).
 *
 * Por quê: controle perdido no sofá é a reclamação mais comum de TV. Para quê: achar o controle de jogo pelo tremor e,
 * para o controle da TV, saber quando ele foi usado pela última vez (e seguir pelo celular enquanto isso). Como: o funil
 * das teclas ([br.paulo.tvlauncher.AutorizaShizukuService.tecla]) anota a hora de cada controle ([touched]); o painel
 * pede [ring], que faz tremer todo controle que tem motor de vibração.
 *
 * Limite verificado na Sala (24/11/2026): o controle do Chromecast (18d1:9450) não tem alto-falante nem motor, então
 * não há como fazê-lo apitar. O que dá: controle de jogo (DualSense, Xbox) treme; o da TV mostra a última vez usado.
 */
object FindRemote {
    /**
     * Nome do controle -> hora (ms) da última tecla. Também vai para as prefs (no máximo 1 gravação por minuto por
     * controle): só na memória, todo Release ou reinício apagava e a página mostrava "nunca usado" (Sala, 25/11/2026).
     */
    private val ultimo = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val gravado = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val CHAVE = "remote_last."

    fun touched(ctx: Context, e: KeyEvent) {
        if (e.action != KeyEvent.ACTION_DOWN) return
        val d = e.device ?: InputDevice.getDevice(e.deviceId) ?: return
        if (d.isVirtual) return
        val nome = d.name.take(40)
        val agora = System.currentTimeMillis()
        ultimo[nome] = agora
        if (agora - (gravado[nome] ?: 0L) > 60_000L) {
            gravado[nome] = agora
            runCatching { CompanionTv.prefs(ctx).edit().putLong(CHAVE + nome, agora).apply() }
        }
    }

    private fun lastUse(ctx: Context, nome: String): Long? =
        ultimo[nome] ?: runCatching { CompanionTv.prefs(ctx).getLong(CHAVE + nome, 0L) }.getOrDefault(0L).takeIf { it > 0 }

    data class Remote(val name: String, val lastUse: Long?, val canVibrate: Boolean, val gamepad: Boolean)

    /**
     * Os controles que a pessoa segura: só aparelhos EXTERNOS (Bluetooth/USB). Na Sala (25/11/2026) a lista mostrava
     * também peças internas do Chromecast (gpio_keypad = botão do aparelho, vad_keypad = voz, input_btrcu) e os
     * controles "virtual-*" de apps de celular, que ninguém perde no sofá.
     */
    fun remotes(ctx: Context): List<Remote> = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
        .filter { !it.isVirtual && (it.sources and (InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD)) != 0 }
        .filter { it.name != null && CompanionRules.isHandRemote(it.name, externo(it)) }
        .distinctBy { it.name }
        .map { Remote(it.name.take(40), lastUse(ctx, it.name.take(40)), vibra(it), (it.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) }

    private fun externo(d: InputDevice): Boolean? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) d.isExternal else null

    private fun vibra(d: InputDevice): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching { d.vibratorManager.vibratorIds.isNotEmpty() }.getOrDefault(false)
        else @Suppress("DEPRECATION") d.vibrator.hasVibrator()

    /** Faz tremer, por ~8 s em pulsos, cada controle com motor. Devolve os nomes que tremeram. */
    fun ring(ctx: Context): List<String> {
        // createWaveform alterna desligado/ligado começando por desligado: 32 trechos de 250 ms = 8 s, metade tremendo.
        val padrao = VibrationEffect.createWaveform(LongArray(32) { 250L }, -1)
        val tremeu = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }.filter { !it.isVirtual && vibra(it) }
            .mapNotNull { d ->
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) d.vibratorManager.defaultVibrator.vibrate(padrao)
                    else @Suppress("DEPRECATION") d.vibrator.vibrate(padrao)
                    d.name.take(40)
                }.getOrNull()
            }.distinct()
        CompanionTv.sofy(ctx, "remote_find", tremeu.isNotEmpty(), "vibrated=${tremeu.size}")
        return tremeu
    }
}
