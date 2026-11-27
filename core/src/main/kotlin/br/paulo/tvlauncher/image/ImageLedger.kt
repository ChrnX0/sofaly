package br.paulo.tvlauncher.image

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * Livro das imagens (refatoração G, 27/11/2026): cada bitmap que o app cria passa por [Images], que anota aqui de onde
 * ele veio. Guarda só uma referência FRACA (não segura a imagem na memória); a conta mostra o que ainda está vivo.
 * Custo: um objeto pequeno por imagem criada; a lista se limpa sozinha quando dobra de tamanho.
 */
object ImageLedger {
    private class Item(val origin: String, bmp: Bitmap) { val ref = WeakReference(bmp) }

    private val items = ArrayList<Item>()
    private var limit = 256

    fun track(origin: String, bmp: Bitmap?): Bitmap? {
        if (bmp != null) synchronized(items) {
            if (items.size >= limit) {
                items.removeAll { it.ref.get()?.isRecycled != false }
                limit = maxOf(256, items.size * 2)
            }
            items.add(Item(origin, bmp))
        }
        return bmp
    }

    /** Ícones e banners que vêm prontos do sistema (PackageManager) também ocupam memória nossa: anota o bitmap deles. */
    fun <D : Drawable?> trackDrawable(origin: String, d: D): D {
        (d as? BitmapDrawable)?.bitmap?.let { track(origin, it) }
        return d
    }

    /** Os bitmaps vivos agora (cada um uma vez só, mesmo anotado duas vezes). */
    fun live(): List<ImageTally.Entry> = synchronized(items) {
        val seen = IdentityHashMap<Bitmap, Boolean>()
        items.mapNotNull { item ->
            val b = item.ref.get()?.takeIf { !it.isRecycled } ?: return@mapNotNull null
            if (seen.put(b, true) != null) return@mapNotNull null
            ImageTally.Entry(item.origin, b.width, b.height, runCatching { b.allocationByteCount.toLong() }.getOrDefault(0L))
        }
    }

    /** Para a régua (`EXTRAS --es o leveza`, campo "imagens"): total e as linhas por origem. */
    fun json(): JSONObject {
        val lines = ImageTally.group(live())
        val arr = JSONArray()
        for (l in lines) arr.put(JSONObject().put("origem", l.origin).put("qtd", l.count).put("mb", ImageTally.mb(l.bytes))
            .put("maior", "${l.largestWidth}x${l.largestHeight}"))
        return JSONObject().put("totalMb", ImageTally.mb(lines.sumOf { it.bytes })).put("qtd", lines.sumOf { it.count })
            .put("nativoMb", ImageTally.mb(android.os.Debug.getNativeHeapAllocatedSize())).put("porOrigem", arr)
    }
}
