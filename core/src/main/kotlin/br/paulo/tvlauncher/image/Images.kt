package br.paulo.tvlauncher.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.drawable.Drawable
import java.io.InputStream

/**
 * A única porta para criar bitmaps no app (refatoração G, etapa de imagens, 27/11/2026). Hoje só repassa para o
 * Android e anota no [ImageLedger] de onde veio a imagem ([origin] = pasta/arquivo, ex. "home/Fundos"); o teste
 * `ImagesGateTest` recusa `BitmapFactory.decode*`, `Bitmap.createBitmap` etc. fora desta pasta. Assim a régua mede
 * quem ocupa memória e, nas próximas partes, o corte de tamanho entra num lugar só.
 */
object Images {
    private fun t(origin: String, b: Bitmap?): Bitmap? = ImageLedger.track(origin, b)
    private fun n(origin: String, b: Bitmap): Bitmap { ImageLedger.track(origin, b); return b }

    fun decodeFile(origin: String, path: String): Bitmap? = t(origin, BitmapFactory.decodeFile(path))
    fun decodeFile(origin: String, path: String, opts: BitmapFactory.Options?): Bitmap? = t(origin, BitmapFactory.decodeFile(path, opts))
    fun decodeStream(origin: String, input: InputStream?): Bitmap? = t(origin, BitmapFactory.decodeStream(input))
    fun decodeStream(origin: String, input: InputStream?, pad: Rect?, opts: BitmapFactory.Options?): Bitmap? =
        t(origin, BitmapFactory.decodeStream(input, pad, opts))
    fun decodeByteArray(origin: String, data: ByteArray, offset: Int, length: Int): Bitmap? =
        t(origin, BitmapFactory.decodeByteArray(data, offset, length))
    fun decodeByteArray(origin: String, data: ByteArray, offset: Int, length: Int, opts: BitmapFactory.Options?): Bitmap? =
        t(origin, BitmapFactory.decodeByteArray(data, offset, length, opts))

    fun create(origin: String, width: Int, height: Int, config: Bitmap.Config): Bitmap = n(origin, Bitmap.createBitmap(width, height, config))
    fun create(origin: String, colors: IntArray, width: Int, height: Int, config: Bitmap.Config): Bitmap =
        n(origin, Bitmap.createBitmap(colors, width, height, config))
    fun create(origin: String, src: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap =
        n(origin, Bitmap.createBitmap(src, x, y, width, height))
    fun create(origin: String, src: Bitmap, x: Int, y: Int, width: Int, height: Int, m: Matrix?, filter: Boolean): Bitmap =
        n(origin, Bitmap.createBitmap(src, x, y, width, height, m, filter))
    fun scaled(origin: String, src: Bitmap, width: Int, height: Int, filter: Boolean): Bitmap =
        n(origin, Bitmap.createScaledBitmap(src, width, height, filter))
    fun copy(origin: String, src: Bitmap, config: Bitmap.Config, mutable: Boolean): Bitmap? = t(origin, src.copy(config, mutable))

    fun decodeDrawable(origin: String, source: ImageDecoder.Source, listener: ImageDecoder.OnHeaderDecodedListener): Drawable =
        ImageLedger.trackDrawable(origin, ImageDecoder.decodeDrawable(source, listener))
}
