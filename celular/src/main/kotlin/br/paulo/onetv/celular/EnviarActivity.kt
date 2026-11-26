package br.paulo.onetv.celular

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Base64
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Foto, vídeo, música ou arquivo vindo do "Compartilhar" do Android vai para a TV (Brainstorm 26/11/2026, item 16).
 *
 * Por quê: o celular só mandava texto e link; o vídeo da viagem exigia abrir o painel, achar Arquivos e arrastar.
 * Como: a [TvsActivity] escolhe a TV e abre esta tela com os arquivos. Duas saídas, como no painel:
 *  - "Mostrar na TV": foto vira recado na tela (a mesma rota do cartão Foto, 960 px); vídeo e música vão para a pasta
 *    Movies e tocam no player do Sofaly (as mesmas rotas do "Ver na TV" do painel).
 *  - "Guardar na TV": cada arquivo vai para a pasta do seu tipo (Pictures, Movies, Download), sem apagar nada que já
 *    esteja lá (nome livre: "foto (2).jpg").
 * Só com a TV pareada pelo QR (certificado fixado, [ApiTv]); sem sessão, abre o painel para o PIN, como o controle.
 */
class EnviarActivity : Activity() {
    companion object {
        const val EXTRA_CHAVE = "chave"
        const val EXTRA_ARQUIVOS = "arquivos"   // ArrayList<Uri>
    }

    private class Item(val uri: Uri, val nome: String, val mime: String?, val tamanho: Long, val tipo: Compartilhar.Tipo)

    private lateinit var tv: Pareamento.Tv
    private var itens: List<Item> = emptyList()
    private lateinit var situacao: TextView
    private lateinit var barra: ProgressBar
    private lateinit var botoes: LinearLayout
    private val tela = Handler(Looper.getMainLooper())
    private var ocupado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Visual.atualizar(this)
        window.statusBarColor = Visual.FUNDO; window.navigationBarColor = Visual.FUNDO
        if (Visual.claro) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val achada = Tvs.porChave(this, intent.getStringExtra(EXTRA_CHAVE))
        @Suppress("DEPRECATION")
        val uris = intent.getParcelableArrayListExtra<Uri>(EXTRA_ARQUIVOS).orEmpty()
        if (achada == null || uris.isEmpty()) { finish(); return }
        tv = achada

        val dp = { v: Int -> Visual.dp(this, v) }
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Visual.FUNDO)
            setPadding(dp(20), dp(28), dp(20), dp(20))
        }
        raiz.addView(Visual.texto(this, getString(R.string.cel_enviar_titulo, tv.nome.ifEmpty { tv.ip }), 26f, negrito = true))
        situacao = Visual.texto(this, getString(R.string.cel_enviar_lendo), 18f, Visual.TEXTO_2).apply { setPadding(0, dp(12), 0, dp(12)) }
        raiz.addView(situacao)
        barra = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(Visual.ACENTO)
        }
        raiz.addView(barra, LinearLayout.LayoutParams(-1, dp(12)))
        botoes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(20), 0, 0) }
        raiz.addView(botoes)
        setContentView(raiz)

        ApiTv.emSegundoPlano {
            val lidos = uris.mapNotNull { ler(it) }
            tela.post { itens = lidos; oferecer() }
        }
    }

    /** Nome, tipo e tamanho de cada arquivo (o ContentResolver sabe; o nome passa pelas regras da TV). */
    private fun ler(uri: Uri): Item? = runCatching {
        var nome: String? = null; var tamanho = -1L
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                nome = c.getString(0)
                if (!c.isNull(1)) tamanho = c.getLong(1)
            }
        }
        val mime = contentResolver.getType(uri)
        if (tamanho < 0) tamanho = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (tamanho < 0) return@runCatching null   // a TV precisa do tamanho exato antes de receber
        val n = Compartilhar.nomeSeguro(nome ?: uri.lastPathSegment, mime)
        Item(uri, n, mime, tamanho, Compartilhar.tipo(mime, n))
    }.getOrNull()

    private fun oferecer() {
        botoes.removeAllViews()
        if (itens.isEmpty()) { situacao.text = getString(R.string.cel_enviar_nada); botoes.addView(Visual.botao(this, getString(R.string.cel_enviar_fechar), false) { finish() }); return }
        val um = itens.singleOrNull()
        situacao.text = if (um != null) um.nome else getString(R.string.cel_enviar_n, itens.size)
        val mostravel = um != null && (um.tipo == Compartilhar.Tipo.IMAGEM ||
            (um.tipo != Compartilhar.Tipo.OUTRO && Compartilhar.tocavel(um.tipo, um.nome)))
        val espaco = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Visual.dp(this@EnviarActivity, 12) }
        if (mostravel) botoes.addView(Visual.botao(this, getString(
            if (um!!.tipo == Compartilhar.Tipo.IMAGEM) R.string.cel_enviar_mostrar else R.string.cel_enviar_tocar)) { comecar { mostrar(um) } }, espaco)
        botoes.addView(Visual.botao(this, getString(R.string.cel_enviar_guardar), primario = !mostravel) { comecar { guardar() } }, espaco)
        botoes.addView(Visual.botao(this, getString(android.R.string.cancel), false) { finish() }, espaco)
    }

    private fun comecar(trabalho: () -> Int) {
        if (ocupado) return
        ocupado = true
        botoes.removeAllViews()
        situacao.text = getString(R.string.cel_enviar_mandando, 0)
        ApiTv.emSegundoPlano {
            val cod = runCatching { trabalho() }.getOrDefault(0)
            tela.post { terminar(cod) }
        }
    }

    // ------------------------------------------------------------ o trabalho (fora da thread da tela)

    /** Código HTTP do passo que decidiu (200 = tudo certo). */
    private fun mostrar(item: Item): Int {
        if (item.tipo == Compartilhar.Tipo.IMAGEM) {
            val base64 = fotoReduzida(item.uri) ?: return 415
            return ApiTv.chamar(tv, "POST", "/api/extras/recado-foto", JSONObject().put("imagem", base64).put("texto", "")).codigo
        }
        val pasta = Compartilhar.PASTA_TOCAR
        val existentes = conteudo(pasta) ?: return ultimoCodigo
        // O mesmo nome já na TV = o mesmo vídeo mandado de novo: toca o que já está lá (igual ao painel).
        if (item.nome !in existentes) {
            val r = mandar(item, pasta, item.nome, 0, 1)
            if (r !in 200..299) return r
        }
        return ApiTv.chamar(tv, "POST", "/api/arquivos/tocar", JSONObject().put("caminho", "$pasta/${item.nome}")).codigo
    }

    private fun guardar(): Int {
        val existentes = HashMap<String, MutableSet<String>>()
        itens.forEachIndexed { i, item ->
            val pasta = Compartilhar.pasta(item.tipo)
            val ja = existentes.getOrPut(pasta) { conteudo(pasta)?.toMutableSet() ?: return ultimoCodigo }
            val nome = Compartilhar.nomeLivre(item.nome, ja)
            val r = mandar(item, pasta, nome, i, itens.size)
            if (r !in 200..299) return r
            ja.add(nome)
        }
        return 200
    }

    private var ultimoCodigo = 0
    /** Espaço livre na TV (vem com a lista da pasta). Conferido antes: sem espaço a TV fecha a conexão no meio. */
    private var livre = Long.MAX_VALUE

    /** Nomes que já estão na pasta (cria a pasta se faltar). Null = a TV não respondeu ([ultimoCodigo] diz por quê). */
    private fun conteudo(pasta: String): Set<String>? {
        ApiTv.chamar(tv, "POST", "/api/arquivos/pasta", JSONObject().put("pasta", "").put("nome", pasta))   // 409 = já existe
        val r = ApiTv.chamar(tv, "GET", "/api/arquivos?caminho=" + Uri.encode(pasta))
        if (r.codigo !in 200..299) { ultimoCodigo = r.codigo; return null }
        livre = r.json?.optLong("livre", Long.MAX_VALUE) ?: Long.MAX_VALUE
        val lista = r.json?.optJSONArray("itens") ?: return emptySet()
        return (0 until lista.length()).mapNotNull { lista.optJSONObject(it)?.optString("nome") }.toSet()
    }

    private fun mandar(item: Item, pasta: String, nome: String, indice: Int, total: Int): Int {
        if (item.tamanho > livre) return 507
        val entrada = contentResolver.openInputStream(item.uri) ?: return 415
        var ultimo = 0L
        return entrada.use {
            ApiTv.enviar(tv, pasta, nome, item.tamanho, it) { feito ->
                val agora = System.currentTimeMillis()
                if (agora - ultimo < 200) return@enviar   // a tela não precisa de mais que 5 quadros por segundo
                ultimo = agora
                val parte = if (item.tamanho > 0) feito.toDouble() / item.tamanho else 1.0
                val geral = ((indice + parte) / total * 1000).toInt()
                tela.post { barra.visibility = View.VISIBLE; barra.progress = geral; situacao.text = getString(R.string.cel_enviar_mandando, geral / 10) }
            }.codigo.also { if (it in 200..299) livre -= item.tamanho }
        }
    }

    /** Foto em JPEG de até 960 px no lado maior, em base64: o que a rota do recado aceita (até 4 MB). */
    private fun fotoReduzida(uri: Uri): String? = runCatching {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0) return@runCatching null
        val op = BitmapFactory.Options().apply { inSampleSize = Compartilhar.amostra(limites.outWidth, limites.outHeight) }
        val lida = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, op) } ?: return@runCatching null
        val (l, a) = Compartilhar.reduzido(lida.width, lida.height)
        val foto = if (l == lida.width && a == lida.height) lida else Bitmap.createScaledBitmap(lida, l, a, true)
        val saida = ByteArrayOutputStream()
        foto.compress(Bitmap.CompressFormat.JPEG, 82, saida)
        Base64.encodeToString(saida.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    // ------------------------------------------------------------ resultado

    private fun terminar(cod: Int) {
        barra.visibility = View.GONE
        ocupado = false
        situacao.text = getString(when (cod) {
            in 200..299 -> R.string.cel_enviar_pronto
            401 -> R.string.cel_ctrl_entrar
            0 -> R.string.cel_ctrl_sem_tv
            ApiTv.CERTIFICADO_TROCOU -> R.string.cel_ctrl_cert_novo
            507 -> R.string.cel_enviar_sem_espaco
            415 -> R.string.cel_enviar_tipo
            else -> R.string.cel_ctrl_recusou
        })
        botoes.removeAllViews()
        if (cod == 401) {
            startActivity(Intent(this, PainelActivity::class.java)
                .putExtra(PainelActivity.EXTRA_IP, tv.ip).putExtra(PainelActivity.EXTRA_CHAVE, Pareamento.chave(tv)))
        }
        val espaco = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Visual.dp(this@EnviarActivity, 12) }
        if (cod !in 200..299 && cod != ApiTv.CERTIFICADO_TROCOU && cod != 415)
            botoes.addView(Visual.botao(this, getString(R.string.cel_enviar_de_novo)) { oferecer() }, espaco)
        botoes.addView(Visual.botao(this, getString(R.string.cel_enviar_fechar), primario = cod in 200..299) { finish() }, espaco)
    }
}
