package br.paulo.tvlauncher.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.util.LruCache
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.Http
import br.paulo.tvlauncher.settings.Extras

/**
 * Cartões dos apps na cruz (21/11/2026, itens 3 e 4 do plano para passar o Projectivy).
 *
 * Porquê: YouTube, Prime Video, Max e Play Filmes publicam fileiras (canais de prévia do Android TV) com capas; o
 * Projectivy mostra essas fileiras e a Sofaly não mostrava nada. Verificado na Sala em 21/11: com READ_TV_LISTINGS
 * (o Device Owner concede) lemos 10 fileiras e 43 cartões com capa.
 * Para quê: o Paulo quer ver as duas propostas funcionando antes de escolher, então as duas existem e se trocam no Ajuste
 * fino: [Mode.COLUMN] (proposta A: até [MAX_NA_COLUNA] cartões entram na coluna do mundo do app, depois dos apps) e
 * [Mode.SHOWCASE] (proposta B: a vitrine do app escolhido aparece no alto à direita, AppShowcase).
 * Como: [ler] fora da tela (uma consulta a cada volta para a HOME); capas pequenas em cache ([capa]); OK abre o
 * INTENT_URI que o próprio app gravou no cartão ([abrir]), e sem ele, o app.
 */
object AppChannels {
    const val KEY_MODE = "app_rows"
    enum class Mode { OFF, COLUMN, SHOWCASE }
    // Padrão DESLIGADO (21/11, Paulo viu a vitrine na Sala: "poluiu"). A e B seguem no Ajuste fino para quem quiser.
    fun mode(ctx: Context) = Mode.entries.getOrElse(Extras.numero(ctx, KEY_MODE, Mode.OFF.ordinal)) { Mode.OFF }

    const val MAX_NA_COLUNA = 6
    const val MAX_VITRINE = 3
    /** Cards de conteúdo no fim de cada mundo da barra única (24/11/2026). */
    const val MAX_NA_BARRA = 10

    class Card(val id: Long, val pacote: String, val canal: String, val titulo: String, val detalhe: String, val poster: String?, val intent: String?)

    /** Os cartões publicados pelos outros apps (nunca os nossos), na ordem do provedor. Vazio sem a permissão. */
    fun ler(ctx: Context, max: Int = 200): List<Card> = runCatching {
        val canais = HashMap<Long, String>()
        ctx.contentResolver.query(TvContract.Channels.CONTENT_URI,
            arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) canais[c.getLong(0)] = c.getString(1) ?: ""
        }
        val l = ArrayList<Card>()
        ctx.contentResolver.query(TvContract.PreviewPrograms.CONTENT_URI, arrayOf(
            TvContract.PreviewPrograms._ID, TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME, TvContract.PreviewPrograms.COLUMN_CHANNEL_ID,
            TvContract.PreviewPrograms.COLUMN_TITLE, TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE,
            TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
            TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI, TvContract.PreviewPrograms.COLUMN_INTENT_URI), null, null, null)?.use { c ->
            while (c.moveToNext() && l.size < max) {
                val pacote = c.getString(1) ?: continue
                if (pacote == ctx.packageName) continue
                val titulo = c.getString(3)?.trim().orEmpty()
                if (titulo.isEmpty()) continue
                l += Card(c.getLong(0), pacote, canais[c.getLong(2)].orEmpty(), titulo,
                    (c.getString(4) ?: c.getString(5))?.trim()?.lineSequence()?.firstOrNull().orEmpty(),
                    c.getString(6)?.takeIf { it.isNotBlank() } ?: c.getString(7)?.takeIf { it.isNotBlank() }, c.getString(8))
            }
        }
        l
    }.getOrDefault(emptyList())

    /**
     * Até [max] cartões, um de cada app por vez (sem isso a coluna seria só do app com mais cartões). Puro
     * (AppChannelsTest); a ordem dentro de cada app é a do provedor (o app põe o mais importante primeiro).
     */
    fun intercalar(cards: List<Card>, max: Int): List<Card> {
        val porApp = LinkedHashMap<String, ArrayDeque<Card>>()
        for (c in cards) porApp.getOrPut(c.pacote) { ArrayDeque() }.addLast(c)
        val r = ArrayList<Card>()
        while (r.size < max && porApp.values.any { it.isNotEmpty() })
            for (fila in porApp.values) { if (r.size >= max) break; fila.removeFirstOrNull()?.let { r += it } }
        return r
    }

    /** Os cartões de um app, com o nome da 1ª fileira dele (para o título da vitrine). Puro. */
    fun doApp(cards: List<Card>, pacote: String): List<Card> = cards.filter { it.pacote == pacote }

    /** Abre o cartão como o app pediu; sem intent (ou intent inválido), abre o app. */
    fun abrir(ctx: Context, card: Card): Boolean {
        val i = card.intent?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
            ?: ctx.packageManager.getLeanbackLaunchIntentForPackage(card.pacote) ?: ctx.packageManager.getLaunchIntentForPackage(card.pacote)
            ?: return false
        // O cartão é de outro app: o intent fica preso ao pacote dele (um cartão não abre um terceiro app).
        if (i.`package` == null && i.component == null) i.`package` = card.pacote
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(i); true }.getOrDefault(false)
    }

    // ------------------------------------------------------------------ capas

    /** Capas já baixadas, reduzidas (~4 MB no total: a TV é de 32 bits e a HOME é medida). */
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount }
    private val pedidas = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    fun capaPronta(uri: String): Bitmap? = cache.get(uri)

    /** Busca a capa fora da tela (content://, android.resource:// ou https) e avisa em [pronta] (na thread de fundo). */
    fun pedirCapa(ctx: Context, uri: String, larguraPx: Int, pronta: (Bitmap) -> Unit) {
        cache.get(uri)?.let { pronta(it); return }
        if (!pedidas.add(uri)) return
        val app = ctx.applicationContext
        Fundo.rodar("capa-canal") {
            try {
                val bytes = if (uri.startsWith("http")) {
                    if (!uri.startsWith("https://")) return@rodar   // nada de http aberto
                    val c = java.net.URL(uri).openConnection() as java.net.HttpURLConnection
                    try {
                        c.connectTimeout = 6000; c.readTimeout = 8000; c.setRequestProperty("User-Agent", Http.USER_AGENT)
                        if (c.responseCode != 200) null else c.inputStream.use { Http.readLimited(it, 3_000_000, false) }
                    } finally { c.disconnect() }
                } else app.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { Http.readLimited(it, 3_000_000, false) }
                val b = bytes ?: return@rodar
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(b, 0, b.size, o)
                if (o.outWidth <= 0) return@rodar
                var amostra = 1
                while (o.outWidth / (amostra * 2) >= larguraPx) amostra *= 2
                val bmp = BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = amostra }) ?: return@rodar
                cache.put(uri, bmp)
                pronta(bmp)
            } catch (e: Exception) {
                android.util.Log.w("TvLauncher.Canais", "capa não veio: ${e.javaClass.simpleName}")
            } finally { pedidas.remove(uri) }
        }
    }

    /** Pouca memória: solta as capas. */
    fun soltar() = cache.evictAll()
}

/**
 * Capa de um cartão, cortada ao centro (16:9 dentro de qualquer quadro) com os cantos do Estilo. Enquanto a capa não
 * chega, desenha [reserva] (o ícone do app). Sem tamanho próprio: ocupa o quadro que a view der.
 */
class PosterDrawable(ctx: Context, private val uri: String?, private val reserva: Drawable?) : Drawable() {
    private var bmp: Bitmap? = uri?.let { AppChannels.capaPronta(it) }
    private val pinta = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val origem = Rect()
    private val destino = RectF()
    private val molde = Path()
    private val raio = Estilo.RAIO_PEQUENO * ctx.resources.displayMetrics.density

    init {
        if (bmp == null && uri != null) {
            val ui = android.os.Handler(android.os.Looper.getMainLooper())
            AppChannels.pedirCapa(ctx, uri, (240 * ctx.resources.displayMetrics.density).toInt()) { b -> ui.post { bmp = b; invalidateSelf() } }
        }
    }

    override fun draw(c: Canvas) {
        val b = bounds
        val img = bmp
        if (img == null) { reserva?.let { it.bounds = b; it.draw(c) }; return }
        // Corte central: a parte da imagem com a proporção do quadro.
        val k = maxOf(b.width().toFloat() / img.width, b.height().toFloat() / img.height)
        val w = (b.width() / k).toInt(); val h = (b.height() / k).toInt()
        origem.set((img.width - w) / 2, (img.height - h) / 2, (img.width + w) / 2, (img.height + h) / 2)
        destino.set(b)
        molde.reset(); molde.addRoundRect(destino, raio, raio, Path.Direction.CW)
        c.save(); c.clipPath(molde); c.drawBitmap(img, origem, destino, pinta); c.restore()
    }

    override fun setAlpha(alpha: Int) { pinta.alpha = alpha; reserva?.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { pinta.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
