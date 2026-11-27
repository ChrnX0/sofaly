package br.paulo.onetv.celular

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.Uri
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanOptions
import java.net.Inet4Address

/**
 * Tela inicial do app: as TVs salvas (toque abre o painel), as achadas na rede (mDNS) e "Ler o QR da TV".
 * Também recebe o "Compartilhar" do Android: com uma TV só, manda direto; com várias, pergunta qual.
 */
class TvsActivity : Activity() {
    companion object {
        private const val PEDIDO_QR = 7
        private const val PEDIDO_CAMERA = 8
        /** Serviço que o launcher anuncia (PainelVizinhos.TIPO). */
        private const val TIPO_MDNS = "_tvlauncher._tcp."
    }

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var lista: LinearLayout
    private val achadas = linkedMapOf<String, String>()   // ip -> nome anunciado
    private var nsd: NsdManager? = null
    private var busca: NsdManager.DiscoveryListener? = null
    private var compartilhado: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Visual.atualizar(this)
        window.statusBarColor = Visual.FUNDO
        // Ícones escuros na barra de cima quando o fundo é claro (senão somem).
        if (Visual.claro) window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.navigationBarColor = Visual.FUNDO
        if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) compartilhado = intent
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Visual.FUNDO)
            setPadding(dp(20), dp(28), dp(20), dp(20))
        }
        raiz.addView(Visual.texto(this, getString(if (compartilhado != null) R.string.cel_mandar_para else R.string.cel_titulo), 26f, negrito = true))
        lista = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        raiz.addView(ScrollView(this).apply { addView(lista) }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        raiz.addView(Visual.botao(this, getString(R.string.cel_ler_qr)) { lerQr() }, LinearLayout.LayoutParams(-1, -2))
        setContentView(raiz)
        // Uma TV só e veio um "compartilhar": vai direto, sem perguntar.
        val salvas = Tvs.lista(this)
        if (compartilhado != null && salvas.size == 1) { abrir(salvas[0]); finish(); return }
    }

    override fun onResume() { super.onResume(); desenhar(); procurar() }
    override fun onPause() { parar(); super.onPause() }

    private fun dp(v: Int) = Visual.dp(this, v)

    private fun desenhar() {
        lista.removeAllViews()
        val salvas = Tvs.lista(this)
        if (salvas.isEmpty()) lista.addView(Visual.texto(this, getString(R.string.cel_vazio), 17f, Visual.TEXTO_2))
        // Várias casas (degrau 3): com mais de uma casa, as TVs aparecem agrupadas, com o nome da casa em cima.
        val grupos = Pareamento.porCasa(salvas)
        for ((casa, tvs) in grupos) {
            if (grupos.size > 1) lista.addView(Visual.texto(this, nomeDaCasa(casa), 15f, Visual.TEXTO_2).apply { setPadding(0, dp(10), 0, dp(6)) })
            for (tv in tvs) lista.addView(linha(tv.nome.ifEmpty { tv.ip },
                getString(if (tv.fp.isNotEmpty()) R.string.cel_pareada_segura else R.string.cel_pareada_http, tv.ip),
                { abrir(tv) }, { esquecer(tv) }))
        }
        // Duas TVs (de casas diferentes) que este celular já alcança pela internet: liga uma à outra para o Assistir junto.
        val comRelay = salvas.filter { RelayCelular.tv(this, it) != null }
        if (comRelay.size >= 2) lista.addView(Visual.botao(this, getString(R.string.cel_juntar_tvs), primario = false) { juntarTvs(comRelay) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(10) })
        val novas = achadas.filterKeys { ip -> salvas.none { it.ip == ip } }
        if (novas.isNotEmpty()) {
            lista.addView(Visual.texto(this, getString(R.string.cel_na_rede), 15f, Visual.TEXTO_2).apply { setPadding(0, dp(18), 0, dp(6)) })
            for ((ip, nome) in novas) lista.addView(linha(nome, getString(R.string.cel_achada, ip), {
                // Achada sem parear: abre o painel por http (entra com o PIN). Para o https fixado, ler o QR.
                AlertDialog.Builder(this).setTitle(nome).setMessage(R.string.cel_achada_explica)
                    .setPositiveButton(R.string.cel_ler_qr) { _, _ -> lerQr() }
                    .setNegativeButton(R.string.cel_abrir_pin) { _, _ -> abrir(Pareamento.Tv(nome, ip)) }
                    .show()
            }, null))
        }
    }

    private fun linha(titulo: String, detalhe: String, toque: () -> Unit, segurar: (() -> Unit)?) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = Visual.cartao(context)
        setPadding(dp(18), dp(14), dp(18), dp(14))
        addView(Visual.texto(context, titulo, 19f, negrito = true))
        addView(Visual.texto(context, detalhe, 15f, Visual.TEXTO_2))   // auditoria 27/11: 14 era a única letra abaixo de 15
        isClickable = true; isFocusable = true
        setOnClickListener { toque() }
        if (segurar != null) setOnLongClickListener { segurar(); true }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
    }

    /** Escolhe 2 TVs e manda a cada uma a chave da outra (cifrado, pelo api.sofaly.tv). */
    private fun juntarTvs(tvs: List<Pareamento.Tv>) {
        val marcadas = BooleanArray(tvs.size)
        AlertDialog.Builder(this).setTitle(R.string.cel_juntar_tvs)
            .setMultiChoiceItems(tvs.map { it.nome.ifEmpty { it.ip } }.toTypedArray(), marcadas) { _, i, v -> marcadas[i] = v }
            .setPositiveButton(R.string.cel_juntar_ligar) { _, _ ->
                val escolha = tvs.filterIndexed { i, _ -> marcadas[i] }
                if (escolha.size != 2) { avisar(getString(R.string.cel_juntar_duas)); return@setPositiveButton }
                val (a, b) = escolha
                val app = applicationContext
                Thread {
                    val ra = RelayCelular.tv(app, a); val rb = RelayCelular.tv(app, b)
                    val m = if (ra != null && rb != null) RelayCelular.apresentar(ra, a.nome, rb, b.nome, System.currentTimeMillis()) else null
                    val ok = m != null && RelayCelular.enviar(app, a, m.first) && RelayCelular.enviar(app, b, m.second)
                    runOnUiThread {
                        if (!isDestroyed) avisar(getString(if (ok) R.string.cel_juntar_pronto else R.string.cel_juntar_falhou,
                            a.nome.ifEmpty { a.ip }, b.nome.ifEmpty { b.ip }))
                    }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun avisar(t: String) = AlertDialog.Builder(this).setMessage(t).setPositiveButton(android.R.string.ok, null).show()

    private fun esquecer(tv: Pareamento.Tv) {
        AlertDialog.Builder(this).setTitle(tv.nome.ifEmpty { tv.ip }).setMessage(R.string.cel_esquecer_explica)
            .setPositiveButton(R.string.cel_esquecer) { _, _ -> Tvs.esquecer(this, tv); desenhar() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun abrir(tv: Pareamento.Tv, lido: Pareamento.Lido? = null) {
        // Ideia 14 (estudo global 20/11): TV pareada pelo QR e nada vindo do "Compartilhar" = o controle nativo. O painel
        // completo continua a 1 toque no canto do controle (e abre sozinho quando for preciso entrar com o PIN).
        if (lido == null && compartilhado == null && ApiTv.podeUsar(tv)) {
            startActivity(Intent(this, ControleActivity::class.java).putExtra(ControleActivity.EXTRA_CHAVE, Pareamento.chave(tv)))
            return
        }
        // Item 16 (Brainstorm 26/11): foto, vídeo, música ou arquivo vão pela tela de envio, não pelo painel.
        val arquivos = compartilhado?.let { arquivosDe(it) }.orEmpty()
        if (lido == null && arquivos.isNotEmpty()) {
            if (!ApiTv.podeUsar(tv)) { android.widget.Toast.makeText(this, R.string.cel_enviar_qr, android.widget.Toast.LENGTH_LONG).show(); return }
            val e = Intent(this, EnviarActivity::class.java).putExtra(EnviarActivity.EXTRA_CHAVE, Pareamento.chave(tv))
                .putParcelableArrayListExtra(EnviarActivity.EXTRA_ARQUIVOS, ArrayList(arquivos))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Repassa a leitura que a galeria deu a esta tela (a permissão vem junto com a Intent, não com o app).
            e.clipData = android.content.ClipData.newRawUri("", arquivos[0]).apply { arquivos.drop(1).forEach { addItem(android.content.ClipData.Item(it)) } }
            compartilhado = null
            startActivity(e)
            return
        }
        val i = Intent(this, PainelActivity::class.java).putExtra(PainelActivity.EXTRA_IP, tv.ip)
            .putExtra(PainelActivity.EXTRA_CHAVE, Pareamento.chave(tv))
        if (lido != null) i.putExtra(PainelActivity.EXTRA_FRAGMENTO, Pareamento.fragmento(lido))
        compartilhado?.let { c ->
            i.putExtra(PainelActivity.EXTRA_TITULO, c.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty())
            i.putExtra(PainelActivity.EXTRA_TEXTO, c.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            // Arquivo compartilhado sem TV pareada (revisor 26/11): o QR leva ao painel para entrar com o PIN e o arquivo
            // se perdia. Fica guardado; ao voltar desta tela, tocar na TV manda pela tela de envio.
            if (arquivos.isEmpty()) compartilhado = null
        }
        startActivity(i)
    }

    /** Arquivos que vieram do "Compartilhar" (um no SEND, vários no SEND_MULTIPLE). Texto puro não tem. */
    @Suppress("DEPRECATION")
    private fun arquivosDe(c: Intent): List<android.net.Uri> = when (c.action) {
        Intent.ACTION_SEND_MULTIPLE -> c.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> listOfNotNull(c.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
    }.filter { it.scheme == "content" }   // file:// de outro app seria ler arquivo alheio pelo caminho; só content://

    // ---------------------------------------------------------------- QR

    private fun lerQr() {
        // Caça 27/11/2026: sem a câmera liberada, o leitor (ZXing) pedia sozinho e, se a pessoa negava, mostrava
        // "Barcode Scanner: the Android camera encountered a problem, restart the device", em inglês e falso.
        // O app pede antes e, se negarem, explica do seu jeito (CameraNegadaTest confere).
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PEDIDO_CAMERA)
            return
        }
        abrirLeitor()
    }

    override fun onRequestPermissionsResult(pedido: Int, permissoes: Array<String>, resultados: IntArray) {
        super.onRequestPermissionsResult(pedido, permissoes, resultados)
        // Vazio = a pergunta foi fechada sem resposta (tocou fora): não insiste.
        if (pedido != PEDIDO_CAMERA || resultados.isEmpty()) return
        if (resultados[0] == PackageManager.PERMISSION_GRANTED) { abrirLeitor(); return }
        // Depois de "Não permitir" duas vezes o Android não pergunta mais: só os ajustes do app liberam.
        val deVez = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        val d = AlertDialog.Builder(this).setNegativeButton(android.R.string.cancel, null)
        if (deVez) d.setMessage(R.string.cel_camera_bloqueada).setPositiveButton(R.string.cel_abrir_ajustes) { _, _ ->
            runCatching {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
        } else d.setMessage(R.string.cel_camera_negada).setPositiveButton(R.string.cel_enviar_de_novo) { _, _ -> lerQr() }
        d.show()
    }

    private fun abrirLeitor() {
        val op = ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)
            .setPrompt(getString(R.string.cel_aponte)).setOrientationLocked(true)
        // QrScannerActivity: em pé e com janela quadrada (a CaptureActivity do ZXing abre deitada, cara de leitor de barras).
        startActivityForResult(op.createScanIntent(this).setClass(this, QrScannerActivity::class.java), PEDIDO_QR)
    }

    @Deprecated("Activity simples, sem AndroidX")
    override fun onActivityResult(pedido: Int, resultado: Int, dados: Intent?) {
        super.onActivityResult(pedido, resultado, dados)
        if (pedido != PEDIDO_QR || resultado != RESULT_OK) return
        val lido = Pareamento.ler(dados?.getStringExtra(Intents.Scan.RESULT))
        if (lido == null) {
            AlertDialog.Builder(this).setMessage(R.string.cel_qr_invalido).setPositiveButton(android.R.string.ok, null).show()
            return
        }
        val salvas = Tvs.lista(this)
        // A mesma TV de antes: pelo nome no Tailscale (único) ou, numa casa só, pelo IP. Com várias casas o IP não basta.
        val antiga = salvas.firstOrNull { lido.ts.isNotEmpty() && it.ts == lido.ts }
            ?: salvas.firstOrNull { lido.id.isNotEmpty() && it.id == lido.id }
            ?: salvas.filter { it.ip == lido.ip }.takeIf { it.size == 1 && Pareamento.casas(salvas).size <= 1 }?.first()
        val nome = achadas[lido.ip] ?: antiga?.nome ?: "TV"
        fun salvar(casa: String) {
            // Lido na tela da TV: este é o certificado em que o app passa a confiar (e só nele).
            val tv = Pareamento.Tv(nome, lido.ip, lido.fp.ifEmpty { antiga?.fp.orEmpty() }, lido.ts.ifEmpty { antiga?.ts.orEmpty() },
                lido.id.ifEmpty { antiga?.id.orEmpty() }, casa)
            Tvs.guardar(this, tv)
            abrir(tv, lido)
        }
        // TV conhecida, ou a primeira TV do app: sem pergunta. TV nova com outras já salvas: "Em qual casa fica?"
        if (antiga != null || salvas.isEmpty()) salvar(antiga?.casa.orEmpty()) else perguntarCasa(salvas) { salvar(it) }
    }

    /** Nome mostrado da casa ("" = a casa sem nome, "Casa"). */
    private fun nomeDaCasa(casa: String) = casa.ifEmpty { getString(R.string.cel_casa_padrao) }

    /** Escolher uma das casas salvas ou dar nome a uma nova (outra casa, a dos pais, a praia). */
    private fun perguntarCasa(salvas: List<Pareamento.Tv>, aoEscolher: (String) -> Unit) {
        val casas = Pareamento.casas(salvas)
        val itens = (casas.map { nomeDaCasa(it) } + getString(R.string.cel_casa_nova)).toTypedArray()
        AlertDialog.Builder(this).setTitle(R.string.cel_casa_qual).setItems(itens) { _, i ->
            if (i < casas.size) { aoEscolher(casas[i]); return@setItems }
            val campo = android.widget.EditText(this).apply { setHint(R.string.cel_casa_dica); isSingleLine = true }
            AlertDialog.Builder(this).setTitle(R.string.cel_casa_nova).setView(campo)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val n = Pareamento.nomeCasa(campo.text.toString())
                    // Sem nome, ou um nome igual a uma casa existente: fica nela (não cria duas casas iguais).
                    aoEscolher(n)
                }
                .setNegativeButton(android.R.string.cancel, null).show()
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    // ---------------------------------------------------------------- mDNS

    private fun procurar() {
        val m = getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsd = m
        val b = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onServiceFound(s: NsdServiceInfo) { ui.post { resolverEmFila(m, s) } }
        }
        try { m.discoverServices(TIPO_MDNS, NsdManager.PROTOCOL_DNS_SD, b); busca = b } catch (_: Exception) {}
    }

    /**
     * Uma resolução por vez (revisor 26/11): até o Android 13 a segunda resolveService ao mesmo tempo falha com
     * FAILURE_ALREADY_ACTIVE, e com duas TVs na rede só uma aparecia. Roda na thread da tela (a fila é dela).
     */
    private val filaResolver = ArrayDeque<NsdServiceInfo>()
    private var resolvendo = false

    private fun resolverEmFila(m: NsdManager, s: NsdServiceInfo) {
        if (filaResolver.none { it.serviceName == s.serviceName }) filaResolver.addLast(s)
        if (resolvendo) return
        val prox = filaResolver.removeFirstOrNull() ?: return
        resolvendo = true
        fun proximo() = ui.post {
            resolvendo = false
            val n = filaResolver.removeFirstOrNull() ?: return@post
            if (!isFinishing) resolverEmFila(m, n)
        }
        try {
            @Suppress("DEPRECATION")
            m.resolveService(prox, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(s: NsdServiceInfo, e: Int) { proximo() }
                    override fun onServiceResolved(s: NsdServiceInfo) {
                        proximo()
                        val ip = (if (Build.VERSION.SDK_INT >= 34) s.hostAddresses.firstOrNull { it is Inet4Address } else @Suppress("DEPRECATION") s.host)
                            ?.hostAddress ?: return
                        if (!Pareamento.privado(ip)) return
                        val id = Pareamento.idValido(s.attributes["id"]?.let { String(it, Charsets.UTF_8) })
                        val nome = Pareamento.nomeAnunciado(s.serviceName, s.attributes["nome"]?.let { String(it, Charsets.UTF_8) }, id)
                        ui.post {
                            // Item 8: TV salva que mudou de IP segue sozinha, sem ler o QR de novo.
                            Pareamento.seguir(Tvs.lista(this@TvsActivity), id, ip)?.let { Tvs.guardar(this@TvsActivity, it) }
                            // Caça 27/11: a salva com o mesmo id passa a ter o nome que a TV anuncia (não fica "TV").
                            Tvs.lista(this@TvsActivity).firstOrNull { id.isNotEmpty() && it.id == id }?.let { Tvs.renomear(this@TvsActivity, it, nome) }
                            if (id.isEmpty() || Tvs.lista(this@TvsActivity).none { it.id == id }) achadas[ip] = nome.ifEmpty { ip }
                            desenhar()
                        }
                    }
                })
        } catch (_: Exception) { proximo() }
    }

    private fun parar() {
        val b = busca ?: return
        busca = null
        try { nsd?.stopServiceDiscovery(b) } catch (_: Exception) {}
    }
}
