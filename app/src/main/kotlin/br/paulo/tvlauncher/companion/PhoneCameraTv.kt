package br.paulo.tvlauncher.companion

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import br.paulo.tvlauncher.PhoneCameraActivity

/**
 * A câmera do celular na TV (24/11/2026, proposta B; no Apple TV é a "Câmera de Continuidade").
 *
 * Por quê: mostrar na tela grande o que o celular vê (o desenho da criança, o bebê dormindo no quarto, um documento,
 * quem canta no karaokê). Para quê: todo mundo da sala vê junto, sem cabo nem app. Como: o celular manda fotos JPEG
 * (~8 por segundo) pelo WebSocket do painel; a [PhoneCameraActivity] mostra a mais nova e fecha sozinha quando elas
 * param. Nada é gravado; a Sofy recebe só "começou/terminou".
 */
object PhoneCameraTv {
    @Volatile var latest: Bitmap? = null
        private set
    @Volatile var lastAt = 0L
        private set
    @Volatile private var listener: (() -> Unit)? = null
    @Volatile private var desde = 0L
    fun listen(l: () -> Unit) { listener = l }
    /** Só tira o ouvinte se ainda for [l]: a tela velha fechando não apaga o da tela nova (reconexão rápida). */
    fun unlisten(l: () -> Unit) { if (listener === l) listener = null }

    /** A pessoa fechou a tela com VOLTAR: as fotos que ainda chegam são ignoradas sem decodificar até a próxima conexão. */
    @Volatile var dismissed = false

    @Volatile private var ativa = false

    /** false = outro celular já está mostrando a câmera (um por vez). */
    @Synchronized fun start(ctx: Context): Boolean {
        if (ativa) return false
        ativa = true
        dismissed = false
        latest = null; lastAt = System.currentTimeMillis(); desde = lastAt
        ctx.startActivity(Intent(ctx, PhoneCameraActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        CompanionTv.sofy(ctx, "phone_camera", true, "start")
        return true
    }

    fun frame(ctx: Context, jpeg: ByteArray) {
        if (dismissed) return
        // Lê só o tamanho primeiro: um JPEG pequeno que diz ter 20000 px estouraria a memória da TV ao decodificar.
        val tam = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, tam)
        if (tam.outWidth !in 1..1920 || tam.outHeight !in 1..1920) return
        val o = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }   // metade da memória; foto de câmera não precisa de alfa
        val b = runCatching { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o) }.getOrNull() ?: return
        latest = b; lastAt = System.currentTimeMillis()
        listener?.invoke()
    }

    @Synchronized fun stop(ctx: Context) {
        if (!ativa) return
        ativa = false
        lastAt = 0L
        listener?.invoke()
        latest = null   // a última foto (até ~7 MB) não fica presa no processo depois de fechar (revisor, 25/11/2026)
        CompanionTv.sofy(ctx, "phone_camera", true, "stop s=${(System.currentTimeMillis() - desde) / 1000}")
    }
}
