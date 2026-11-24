package br.paulo.tvlauncher.companion

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * O celular como microfone (24/11/2026, proposta B; no Apple TV é o "iPhone como microfone" do Apple Music Sing).
 *
 * Por quê: cantar junto (karaokê do YouTube), dar um recado em voz alta ou ler para alguém sem levantar. Para quê: a voz
 * sai no som da TV, por cima do que estiver tocando. Como: o celular manda PCM 16 bits, mono, 16 kHz pelo WebSocket do
 * painel ([br.paulo.tvlauncher.panel.PanelStream]); aqui vai direto para um AudioTrack de fila curta. Se a fila passar de
 * [MAX_QUEUE_MS] (Wi-Fi engasgou), o excesso é jogado fora: melhor perder um pedaço do que a voz ficar atrás da boca.
 * Um celular por vez. Nada é gravado; a Sofy recebe só "começou/terminou" e quanto durou.
 *
 * Atraso esperado (não medido ainda numa TV real): ~80 ms do celular + Wi-Fi + ~100 ms da fila. Dá para cantar junto,
 * mas não é o retorno instantâneo de um microfone com fio.
 */
object PhoneMicTv {
    const val RATE = 16_000
    private const val MAX_QUEUE_MS = 180
    @Volatile private var track: AudioTrack? = null
    @Volatile private var escritos = 0L
    @Volatile private var desde = 0L

    /** false = outro celular já está com o microfone (um por vez): esta conexão fecha sem mexer no dele. */
    @Synchronized fun start(ctx: Context): Boolean {
        if (track != null) return false
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(min, RATE * 2 * 300 / 1000)).setTransferMode(AudioTrack.MODE_STREAM)
            .apply { if (android.os.Build.VERSION.SDK_INT >= 26) setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) }
            .build().also { it.play() }
        escritos = 0; desde = System.currentTimeMillis()
        CompanionTv.sofy(ctx, "phone_mic", true, "start")
        return true
    }

    /** Um pedaço do celular (PCM 16 bits little-endian). */
    fun chunk(ctx: Context, b: ByteArray) {
        val t = track ?: return
        if (b.size < 2) return
        val fila = escritos - t.playbackHeadPosition.toLong().and(0xffffffffL)
        if (fila * 1000 / RATE > MAX_QUEUE_MS) return   // atrasado demais: descarta este pedaço
        val n = t.write(b, 0, b.size and 1.inv(), AudioTrack.WRITE_NON_BLOCKING)
        if (n > 0) escritos += n / 2
    }

    @Synchronized fun stop(ctx: Context) {
        if (track == null) return
        stopTrack()
        CompanionTv.sofy(ctx, "phone_mic", true, "stop s=${(System.currentTimeMillis() - desde) / 1000}")
    }

    private fun stopTrack() {
        track?.let { runCatching { it.pause(); it.flush(); it.stop() }; it.release() }
        track = null
    }
}
