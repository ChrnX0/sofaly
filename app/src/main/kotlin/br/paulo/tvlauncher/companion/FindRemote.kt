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
    /** Nome do controle -> hora (ms) da última tecla. Só na memória: é passageiro e não diz nada da pessoa. */
    private val ultimo = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun touched(e: KeyEvent) {
        if (e.action != KeyEvent.ACTION_DOWN) return
        val d = e.device ?: return
        if (d.isVirtual) return
        ultimo[d.name.take(40)] = System.currentTimeMillis()
    }

    data class Remote(val name: String, val lastUse: Long?, val canVibrate: Boolean, val gamepad: Boolean)

    /** Os controles ligados agora (sem os virtuais e sem o teclado interno). */
    fun remotes(): List<Remote> = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
        .filter { !it.isVirtual && (it.sources and (InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD)) != 0 }
        .filter { it.name != null && !it.name.contains("builtin", true) && !it.name.contains("cec", true) }
        .distinctBy { it.name }
        .map { Remote(it.name.take(40), ultimo[it.name.take(40)], vibra(it), (it.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) }

    private fun vibra(d: InputDevice): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runCatching { d.vibratorManager.vibratorIds.isNotEmpty() }.getOrDefault(false)
        else @Suppress("DEPRECATION") d.vibrator.hasVibrator()

    /** Faz tremer, por ~8 s em pulsos, cada controle com motor. Devolve os nomes que tremeram. */
    fun ring(ctx: Context): List<String> {
        val padrao = VibrationEffect.createWaveform(LongArray(16) { if (it % 2 == 0) 250L else 250L }, -1)
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
