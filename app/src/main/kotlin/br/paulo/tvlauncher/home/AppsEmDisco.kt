package br.paulo.tvlauncher.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import br.paulo.tvlauncher.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * A lista de apps da HOME guardada em disco (Brainstorm 26/11/2026, item 27).
 *
 * Por quê: na abertura a frio (TV ligou, launcher reiniciou) a HOME só desenhava os apps depois de carregar o ícone e o
 * banner de TODOS (PackageManager + desenho de cada um): o cache era só em memória e morria com o processo. Como: a cada
 * volta à HOME que mudou algo, grava nome, ícone redondo e o ícone da cruz já pronto de cada app; na abertura a frio a
 * HOME desenha com eles na hora e troca pelo carregamento de verdade quando ele termina. A chave de cada app (caminho do
 * APK + atividade + pacote de ícones) vai junto: app atualizado não reaproveita o ícone velho.
 *
 * Fica no cacheDir (o Android pode apagar; a HOME só perde a pressa). Formato: indice.json + <n>.i.png / <n>.c.png.
 */
object AppsEmDisco {
    /** Sobe quando o formato muda: índice de outra versão é ignorado. */
    const val FORMATO = 1

    data class Registro(
        val pacote: String, val nome: String, val chave: String, val categoria: Int,
        /** Ícone do pacote de ícones (ou trocado à mão): a cruz o redesenha por cima do banner. */
        val doPacote: Boolean,
        /** Último uso (ms), para a 1ª ordem já sair igual à de depois. */
        val uso: Long,
    )

    class Lido(val registro: Registro, val icone: Bitmap, val cruz: Bitmap?)

    // ------------------------------------------------------------ puro (AppsEmDiscoTest)

    /** O índice em texto. [estilo] = estilo dos ícones da cruz quando foram desenhados; [versao] = versão do app. */
    fun indice(lista: List<Registro>, estilo: String, versao: Int, densidade: Float): String = JSONObject()
        .put("formato", FORMATO).put("versao", versao).put("estilo", estilo).put("densidade", densidade.toDouble())
        .put("apps", JSONArray().apply {
            lista.forEach { r ->
                put(JSONObject().put("p", r.pacote).put("n", r.nome).put("k", r.chave).put("c", r.categoria)
                    .put("ip", r.doPacote).put("u", r.uso))
            }
        }).toString()

    class Indice(val estilo: String, val apps: List<Registro>)

    /**
     * Lê o índice. Null quando não serve: outro formato, outra versão do app (o desenho pode ter mudado) ou outra
     * densidade de tela (ícone do tamanho errado).
     */
    fun lerIndice(texto: String?, versao: Int, densidade: Float): Indice? = runCatching {
        val o = JSONObject(texto ?: return null)
        if (o.optInt("formato") != FORMATO || o.optInt("versao") != versao) return null
        if (Math.abs(o.optDouble("densidade") - densidade) > 0.001) return null
        val a = o.getJSONArray("apps")
        Indice(o.getString("estilo"), (0 until a.length()).map { i ->
            a.getJSONObject(i).let {
                Registro(it.getString("p"), it.getString("n"), it.getString("k"), it.optInt("c", -1), it.optBoolean("ip"), it.optLong("u"))
            }
        })
    }.getOrNull()

    /** Precisa gravar de novo? Só quando a lista, a ordem de uso ou os desenhos mudaram (não a cada volta à HOME). */
    fun assinatura(lista: List<Registro>, estilo: String, comCruz: Set<String>): String =
        estilo + "\n" + lista.joinToString("\n") { "${it.pacote}|${it.chave}|${it.nome}|${it.doPacote}|${it.uso / 3_600_000L}|${it.pacote in comCruz}" }

    // ------------------------------------------------------------ disco

    private fun pasta(ctx: Context) = File(ctx.cacheDir, "home-apps")

    fun ler(ctx: Context): Pair<String, List<Lido>>? = runCatching {
        val dir = pasta(ctx)
        val f = File(dir, "indice.json")
        if (!f.isFile) return null
        val ind = lerIndice(f.readText(), BuildConfig.VERSION_CODE, ctx.resources.displayMetrics.density) ?: return null
        val lidos = ind.apps.mapIndexedNotNull { n, r ->
            val ic = BitmapFactory.decodeFile(File(dir, "$n.i.png").path) ?: return@mapIndexedNotNull null
            Lido(r, ic, BitmapFactory.decodeFile(File(dir, "$n.c.png").path))
        }
        ind.estilo to lidos
    }.getOrNull()

    /** Grava tudo numa pasta nova e troca de uma vez: uma queda no meio nunca deixa índice apontando para PNG errado. */
    fun gravar(ctx: Context, estilo: String, itens: List<Triple<Registro, Drawable, Bitmap?>>) {
        val dir = pasta(ctx)
        val nova = File(ctx.cacheDir, "home-apps.novo")
        runCatching {
            nova.deleteRecursively(); nova.mkdirs()
            itens.forEachIndexed { n, (_, icone, cruz) ->
                png(bitmapDe(icone), File(nova, "$n.i.png"))
                cruz?.takeIf { !it.isRecycled }?.let { png(it, File(nova, "$n.c.png")) }
            }
            File(nova, "indice.json").writeText(indice(itens.map { it.first }, estilo, BuildConfig.VERSION_CODE,
                ctx.resources.displayMetrics.density))
            val velha = File(ctx.cacheDir, "home-apps.velho")
            velha.deleteRecursively()
            if (dir.exists() && !dir.renameTo(velha)) dir.deleteRecursively()
            if (!nova.renameTo(dir)) error("não deu para trocar a pasta")
            velha.deleteRecursively()
        }.onFailure { nova.deleteRecursively() }
    }

    private fun png(b: Bitmap, f: File) = FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    private fun bitmapDe(d: Drawable): Bitmap {
        (d as? BitmapDrawable)?.bitmap?.takeIf { !it.isRecycled }?.let { return it }
        val l = d.intrinsicWidth.takeIf { it > 0 } ?: 192
        val a = d.intrinsicHeight.takeIf { it > 0 } ?: 192
        return Bitmap.createBitmap(l, a, Bitmap.Config.ARGB_8888).also { d.setBounds(0, 0, l, a); d.draw(Canvas(it)) }
    }
}
