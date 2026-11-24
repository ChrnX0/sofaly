package br.paulo.tvlauncher.home

import android.content.Context
import android.graphics.BitmapFactory
import br.paulo.tvlauncher.Http
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * "Meu fundo" por link (24/11/2026, extensões, proposta B): baixa a FOTO de um link https (VisualCodigo.fundoLink já
 * conferiu o formato) para a pasta do app e a põe como "Meu fundo". Só imagem: o arquivo tem que se abrir como foto
 * (BitmapFactory), senão é apagado. Até [MAX_BYTES], sem seguir redirecionamento para http, um arquivo por vez (o anterior
 * baixado por link é apagado; o escolhido pela pessoa na TV nunca é tocado). FORA da thread da tela.
 */
object WallpaperLink {
    const val MAX_BYTES = 15L * 1024 * 1024
    private const val TEMPO_MS = 15_000

    /**
     * Extensão do arquivo pelo ÚLTIMO trecho do caminho (o domínio e o "?w=1920" não contam): jpg, png ou webp; qualquer
     * outra coisa vira jpg (o conteúdo é conferido como foto de qualquer jeito).
     */
    internal fun extensao(link: String): String {
        val trecho = link.substringBefore('#').substringBefore('?').substringAfter("://").substringAfter('/', "").substringAfterLast('/')
        val e = trecho.substringAfterLast('.', "").lowercase().let { if (it == "jpeg") "jpg" else it }
        return if (e in setOf("jpg", "png", "webp")) e else "jpg"
    }

    /** Null = feito; senão o motivo (curto, para o painel/adb). */
    fun baixar(ctx: Context, link: String): String? {
        val pasta = File(ctx.filesDir, "wallpaper-link").apply { mkdirs() }
        val ext = extensao(link)
        val nome = MessageDigest.getInstance("SHA-256").digest(link.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        val destino = File(pasta, "$nome.$ext"); val parcial = File(pasta, "$nome.part")
        var u = link
        try {
            repeat(4) {
                val c = URL(u).openConnection() as HttpURLConnection
                try {
                    c.connectTimeout = TEMPO_MS; c.readTimeout = TEMPO_MS; c.instanceFollowRedirects = false
                    c.setRequestProperty("User-Agent", Http.USER_AGENT)
                    val codigo = c.responseCode
                    if (codigo in 300..399) {
                        val prox = URL(URL(u), c.getHeaderField("Location") ?: return "redirecionamento sem destino").toString()
                        if (!prox.startsWith("https://")) return "o link saiu do https"
                        u = prox; return@repeat
                    }
                    if (codigo != 200) return "o site respondeu $codigo"
                    if (c.contentLengthLong > MAX_BYTES) return "foto grande demais"
                    var total = 0L
                    c.inputStream.use { ent -> parcial.outputStream().use { sai ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ent.read(buf); if (n < 0) break
                            total += n; if (total > MAX_BYTES) return "foto grande demais"
                            sai.write(buf, 0, n)
                        }
                    } }
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(parcial.path, o)
                    if (o.outWidth <= 0 || o.outHeight <= 0) return "o link não é uma foto"
                    // O fundo anterior só sai DEPOIS do novo estar guardado (o Meu fundo ainda pode usá-lo).
                    if (!parcial.renameTo(destino)) return "não consegui guardar"
                    pasta.listFiles()?.forEach { if (it != destino) it.delete() }
                    FundoMeuDados.definirDoLink(ctx, destino, link)
                    return null
                } finally { c.disconnect() }
            }
            return "redirecionamentos demais"
        } catch (e: Exception) {
            return e.message ?: e.javaClass.simpleName
        } finally {
            parcial.delete()
        }
    }
}
