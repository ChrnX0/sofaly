package br.paulo.tvlauncher

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import br.paulo.tvlauncher.relay.RelayCasas
import br.paulo.tvlauncher.relay.RelayHouses
import br.paulo.tvlauncher.relay.RelayTv

/**
 * "Outras casas" (Configurações › Família): liga esta TV à TV de outra casa para Assistir junto, sem Tailscale, pelo
 * relay do api.sofaly.tv ([RelayCasas]). Uma TV mostra um código de 8 letras, a outra digita; quem mostrou confere os
 * 4 números com a outra casa (por telefone) e confirma.
 *
 * Referências: Stardust "Enter their code" (as duas ações na mesma tela: o seu código e o campo para o código do
 * outro, https://mobbin.com/screens/b7d94a47-32b2-49b9-b4ee-6159a2727976) e ChatGPT "Get pairing code" (um passo por
 * vez, texto curto, https://mobbin.com/screens/49fe96ac-29e0-4466-92fe-dcd89b0e1eda). Letra grande (régua 18 sp+),
 * código em DISPLAY para ler em voz alta.
 */
class CasasActivity : Activity() {
    private lateinit var estado: TextView
    private lateinit var codigoGrande: TextView
    private var conferir: android.app.Dialog? = null
    private val vigia = android.os.Handler(android.os.Looper.getMainLooper())

    /** Se a escuta (WebSocket) estiver em espera, busca a caixa postal a cada 4 s enquanto há pareamento aberto. */
    private val buscar = object : Runnable {
        override fun run() {
            if (!RelayCasas.emAndamento()) return
            val app = applicationContext
            Fundo.rodar("casas-caixa") { RelayTv.receber(app) { a, chegou -> RelayTv.tratar(app, a, chegou) } }
            vigia.postDelayed(this, 4_000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (VigiaAtualizacao.fechada(this)) return
        montar()
    }

    override fun onResume() {
        super.onResume()
        RelayCasas.aoMudar = { runOnUiThread { if (!isDestroyed) atualizar() } }
        atualizar()
    }

    override fun onPause() {
        super.onPause()
        RelayCasas.aoMudar = null
        vigia.removeCallbacks(buscar)
    }

    private fun montar() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Estilo.tituloTela(this, getString(R.string.casas_titulo)))
        col.addView(Estilo.texto(this, Estilo.Papel.CORPO, getString(R.string.casas_texto)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        val mostrar = Estilo.linhaOpcao(this, getString(R.string.casas_mostrar), getString(R.string.casas_mostrar_det)).apply {
            setOnClickListener { pedirCodigo() }
        }
        col.addView(mostrar, LinearLayout.LayoutParams(dp(420), -2).apply { topMargin = dp(20) })
        col.addView(Estilo.linhaOpcao(this, getString(R.string.casas_digitar), getString(R.string.casas_digitar_det)).apply {
            setOnClickListener { digitarCodigo() }
        }, LinearLayout.LayoutParams(dp(420), -2).apply { topMargin = dp(8) })
        val casas = RelayCasas.casas(this)
        if (casas.isNotEmpty()) col.addView(Estilo.secao(this, getString(R.string.casas_conectadas)),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(24) })
        for (c in casas) col.addView(Estilo.linhaOpcao(this, c.nome, getString(R.string.casas_remover_det)).apply {
            setOnClickListener { remover(c) }
        }, LinearLayout.LayoutParams(dp(420), -2).apply { topMargin = dp(8) })

        // Direita: o código grande e o que está acontecendo.
        val lado = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        codigoGrande = Estilo.texto(this, Estilo.Papel.DISPLAY).apply { gravity = Gravity.CENTER; letterSpacing = 0.08f }
        estado = Estilo.texto(this, Estilo.Papel.LEGENDA).apply { gravity = Gravity.CENTER }
        lado.addView(codigoGrande, LinearLayout.LayoutParams(-2, -2))
        lado.addView(estado, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Estilo.fundoTela(context)
            setPaddingRelative(dp(Estilo.MARGEM_LATERAL), dp(Estilo.MARGEM_VERTICAL + 9), dp(Estilo.MARGEM_LATERAL), dp(Estilo.MARGEM_VERTICAL))
            addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            addView(lado, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(40) })
        }
        setContentView(raiz)
        mostrar.requestFocus()
    }

    /** Mostra o que o [RelayCasas] tem agora: código, pergunta de conferência ou resultado. */
    private fun atualizar() {
        val p = RelayCasas.pendente
        val f = RelayCasas.fim
        val c = RelayCasas.codigo
        when {
            p != null -> { codigoGrande.text = p.numeros; estado.text = getString(if (p.devolver) R.string.casas_chegou else R.string.casas_confirmou, p.pedido.nome); perguntar(p) }
            f != null && !f.erro -> {
                codigoGrande.text = f.numeros ?: ""
                estado.text = getString(R.string.casas_pronto, f.nome ?: "")
                RelayCasas.limpar()
                recreateDepois()
            }
            f != null -> { codigoGrande.text = ""; estado.text = getString(R.string.casas_falhou); RelayCasas.limpar() }
            c != null -> { codigoGrande.text = RelayHouses.mostrar(c); estado.text = getString(R.string.casas_codigo_det) }
            RelayCasas.emAndamento() -> { codigoGrande.text = ""; estado.text = getString(R.string.casas_esperando) }
        }
        vigia.removeCallbacks(buscar)
        if (RelayCasas.emAndamento()) vigia.postDelayed(buscar, 4_000)
    }

    /** A lista de casas mudou: remonta a tela depois de dar tempo de ler o resultado. */
    private fun recreateDepois() = vigia.postDelayed({ if (!isDestroyed) { val t = estado.text; val n = codigoGrande.text; montar(); estado.text = t; codigoGrande.text = n } }, 300)

    private fun pedirCodigo() {
        val app = applicationContext
        estado.text = getString(R.string.casas_pedindo); codigoGrande.text = ""
        Fundo.rodar("casas-codigo") {
            val c = RelayCasas.abrirCodigo(app)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (c == null) { estado.text = getString(R.string.casas_sem_rede); return@runOnUiThread }
                atualizar()
            }
        }
    }

    private fun digitarCodigo() {
        val campo = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true; hint = "ABCD EFGH"
            Estilo.aplicar(this, Estilo.Papel.TITULO)   // código grande para ler do sofá (régua do Estilo, não tamanho à mão)
        }
        val moldura = FrameLayout(this).apply { setPaddingRelative(dp(20), dp(8), dp(20), 0); addView(campo) }
        val d = CaixaTv(this).setTitle(getString(R.string.casas_digitar)).setView(moldura)
            .setPositiveButton(getString(R.string.casas_conectar)) { _, _ -> usar(campo.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null).create()
        CampoTv.enterConfirma(campo, d)
        d.setOnShowListener { campo.requestFocus() }
        d.show()
    }

    private fun usar(texto: String) {
        if (RelayHouses.codigo(texto) == null) { estado.text = getString(R.string.casas_codigo_errado); return }
        val app = applicationContext
        estado.text = getString(R.string.casas_conectando); codigoGrande.text = ""
        Fundo.rodar("casas-usar") {
            val ok = RelayCasas.usarCodigo(app, texto)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (!ok) estado.text = getString(R.string.casas_codigo_errado) else atualizar()
            }
        }
    }

    private fun perguntar(p: RelayCasas.Pendente) {
        if (conferir?.isShowing == true) return
        val cancelar = Estilo.botao(this, getString(android.R.string.cancel), primario = false)
        val conectar = Estilo.botao(this, getString(R.string.casas_conectar))
        val d = android.app.Dialog(this)
        Estilo.vestir(d, Estilo.dialogo(this, null, getString(R.string.casas_conferir_titulo, p.pedido.nome),
            getString(R.string.casas_conferir_texto, p.numeros), listOf(cancelar, conectar)))
        cancelar.setOnClickListener { d.dismiss(); RelayCasas.recusar(); estado.text = ""; codigoGrande.text = "" }
        conectar.setOnClickListener {
            d.dismiss()
            val app = applicationContext
            estado.text = getString(R.string.casas_conectando)
            Fundo.rodar("casas-confirmar") { RelayCasas.confirmar(app) }   // aoMudar mostra o resultado
        }
        d.setOnCancelListener { RelayCasas.recusar() }
        conferir = d
        d.show()
        cancelar.post { cancelar.requestFocus() }
    }

    private fun remover(c: RelayTv.Pareado) {
        Estilo.lista(this, getString(R.string.casas_remover_titulo, c.nome), listOf(getString(R.string.casas_remover), getString(android.R.string.cancel))) { i ->
            if (i == 0) { RelayCasas.remover(this, c.id); montar() }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
