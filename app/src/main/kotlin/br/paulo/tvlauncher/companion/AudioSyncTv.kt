package br.paulo.tvlauncher.companion

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.listen.SomNoFoneRegras
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.sin

/**
 * O lado da TV do "acertar o som pelo celular" ([AudioSyncMath]): toca os bipes, sabe qual é a saída de som de agora,
 * guarda um atraso por saída e o entrega a quem precisa ([forRoute], usado pelo Som no fone).
 *
 * A Sofy opera sozinha aqui: quando a saída muda (liga um fone Bluetooth, uma barra de som), [watch] percebe, troca o
 * atraso em uso pelo que já foi medido naquela saída e avisa o barramento; saída nunca medida volta ao chute padrão e
 * vira um evento "audio_delay_ms" com ok=false, para ela poder sugerir a medida.
 */
object AudioSyncTv {
    private const val RATE = 48_000
    /** Buffer do AudioTrack dos bipes = o do player (ExoPlayer usa ~250 ms em PCM): o atraso medido inclui essa fila. */
    private const val BUFFER_MS = 250

    /** Relógio desta medida: monotônico, em ms com fração (o celular acerta o dele contra este). */
    fun nowMs(): Double = System.nanoTime() / 1_000_000.0

    @Volatile private var written: List<Double> = emptyList()
    @Volatile private var playing = false

    /** Toca [AudioSyncMath.BEEPS] bipes numa thread própria; a hora de cada um fica em [written]. false = já tocando. */
    @Synchronized fun startBeeps(ctx: Context): Boolean {
        if (playing) return false
        playing = true; written = emptyList()
        Thread({
            // Pede o som só para os bipes: o app que está tocando (YouTube, Netflix, player) pausa e volta sozinho depois.
            // Sem isso a série continuava por baixo e o celular não separava o bipe da fala (revisão, 25/11/2026).
            val am = ctx.getSystemService(AudioManager::class.java)
            val foco = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener { }.build()
            runCatching { am?.requestAudioFocus(foco) }
            try { runCatching { beeps() } } finally { runCatching { am?.abandonAudioFocusRequest(foco) }; playing = false }
        }, "companion-beeps").start()
        return true
    }

    fun lastWritten(): List<Double> = written

    private fun beeps() {
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val buf = maxOf(min, RATE * 4 * BUFFER_MS / 1000)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(buf).setTransferMode(AudioTrack.MODE_STREAM).build()
        try {
            t.play()
            val bloco = RATE / 100   // 10 ms por escrita
            val silencio = ShortArray(bloco * 2)
            val gap = (RATE * AudioSyncMath.GAP_MS / 1000).toInt()
            val bipe = tone()
            val horas = ArrayList<Double>()
            // 1 s de silêncio enche a fila: daí em diante cada write espera vaga, como no player.
            repeat(100) { t.write(silencio, 0, silencio.size) }
            repeat(AudioSyncMath.BEEPS) {
                horas += nowMs()
                t.write(bipe, 0, bipe.size)
                var resto = gap - bipe.size / 2
                while (resto > 0) { val n = minOf(resto, bloco); t.write(silencio, 0, n * 2); resto -= n }
            }
            written = horas
            repeat(60) { t.write(silencio, 0, silencio.size) }   // deixa o último bipe sair antes de parar
        } finally {
            runCatching { t.stop() }; t.release()
        }
    }

    /** Bipe de 2 kHz, 30 ms, com rampa de 2 ms nas pontas (sem estalo), estéreo, a meio volume. */
    private fun tone(): ShortArray {
        val n = RATE * AudioSyncMath.BEEP_MS / 1000
        val rampa = RATE * 2 / 1000
        val s = ShortArray(n * 2)
        for (i in 0 until n) {
            val env = minOf(1.0, i / rampa.toDouble(), (n - i) / rampa.toDouble())
            val v = (sin(2 * PI * AudioSyncMath.BEEP_HZ * i / RATE) * env * 0.5 * Short.MAX_VALUE).toInt().toShort()
            s[2 * i] = v; s[2 * i + 1] = v
        }
        return s
    }

    // ------------------------------------------------------------------ saída de som

    /** A saída de som de agora, como chave: "hdmi", "speaker", "bt-<nome>", "usb-<nome>"... */
    fun route(ctx: Context): String {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return "hdmi"
        val d: AudioDeviceInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching<AudioDeviceInfo?> {
            am.getAudioDevicesForAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).firstOrNull()
        }.getOrNull() else null
        val dev = d ?: am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in BT } ?: return "hdmi"
        val tipo = when (dev.type) {
            in BT -> "bt"
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> "hdmi"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> "usb"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired"
            else -> "out${dev.type}"
        }
        return AudioSyncMath.routeKey(tipo, if (tipo == "hdmi" || tipo == "speaker") null else dev.productName?.toString())
    }

    private val BT = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_HEARING_AID)

    /** O atraso medido para [route] nesta TV (ms), ou null se nunca medido. */
    fun forRoute(ctx: Context, route: String = route(ctx)): Int? {
        val o = audio(ctx).optJSONObject(route) ?: return null
        return o.optInt("delay", -1).takeIf { it.toDouble() in AudioSyncMath.RANGE_MS && o.optString("verdict") == "GOOD" }
    }

    /** O atraso que o Som no fone usa agora: o medido nesta saída, ou o chute padrão. */
    fun delayInUse(ctx: Context): Int = forRoute(ctx) ?: SomNoFoneRegras.ATRASO_PADRAO_MS

    fun audio(ctx: Context): JSONObject =
        runCatching { JSONObject(CompanionTv.prefs(ctx).getString("audio.${CompanionTv.displayKey(ctx)}", null) ?: "{}") }.getOrDefault(JSONObject())

    @Synchronized fun save(ctx: Context, route: String, r: AudioSyncMath.Result) {
        val key = "audio.${CompanionTv.displayKey(ctx)}"
        val atual = audio(ctx)
        // UNSURE não apaga uma medida boa anterior da mesma saída: só anota a tentativa.
        val antiga = atual.optJSONObject(route)
        if (r.verdict == AudioSyncMath.Verdict.GOOD || antiga == null)
            atual.put(route, JSONObject().put("delay", r.delayMs).put("spread", r.spreadMs).put("verdict", r.verdict.name)
                .put("detail", r.detail).put("at", System.currentTimeMillis()))
        CompanionTv.prefs(ctx).edit().putString(key, atual.toString()).apply()
        CompanionTv.sofy(ctx, "audio_delay_ms", r.verdict == AudioSyncMath.Verdict.GOOD, "${CompanionRules.routeType(route)} ${r.delayMs} ${r.detail}".take(120))
    }

    // ------------------------------------------------------------------ a Sofy percebe a troca de saída

    @Volatile private var lastRoute: String? = null

    /** Chamado uma vez no início do app: a cada troca de saída, a Sofy fica sabendo qual atraso passou a valer. */
    fun watch(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AudioManager::class.java) ?: return
        lastRoute = runCatching { route(app) }.getOrNull()
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { changed() }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { changed() }
            private fun changed() { Fundo.rodar("companion-route") {
                Thread.sleep(1_500)   // o Android leva um instante para mudar a rota depois que o aparelho aparece
                val r = runCatching { route(app) }.getOrNull() ?: return@rodar
                if (r == lastRoute) return@rodar
                lastRoute = r
                val medido = forRoute(app, r)
                CompanionTv.sofy(app, "audio_route", medido != null,
                    if (medido != null) "${CompanionRules.routeType(r)} delay=$medido applied" else "${CompanionRules.routeType(r)} not_measured default=${SomNoFoneRegras.ATRASO_PADRAO_MS}")
            } }
        }, Handler(Looper.getMainLooper()))
    }
}
