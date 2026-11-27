package br.paulo.tvlauncher

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import br.paulo.tvlauncher.panel.PainelBlocos
import br.paulo.tvlauncher.panel.PainelBlocosTv

/**
 * Escolher os blocos do painel rápido (ideia 42). Uma linha por bloco: OK mostra ou esconde; ◀ ▶ trocam de lugar com
 * o vizinho (só entre os mostrados). Grava na hora; o painel lê ao abrir. Abre por Configurações > Aparência.
 */
class PainelBlocosActivity : Activity() {
    // Molde das telas de ajuste (ColumnsScreen; passada da Sala 27/11/2026: esta ainda era a coluna única esticada).
    // Uma seção só, então o trilho some; o estado de cada bloco fica à direita da linha, como nas Configurações.
    private var lista: List<String> = emptyList()
    private var focado: String? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (VigiaAtualizacao.fechada(this)) return   // modo seguro (VigiaAtualizacao)
        // sem subtítulo: era só teclas (Paulo, 26/11/2026: "essas dicas são absolutamente idiotas. remove de tudo")
        lista = PainelBlocosTv.escolhidos(this)
        montar()
    }

    private fun nome(id: String): String = getString(when (id) {
        "wifi" -> R.string.pb_wifi
        "bluetooth" -> R.string.pb_bluetooth
        "timer" -> R.string.pr_timer
        "dormir" -> R.string.pr_dormir
        "protetor" -> R.string.pr_protetor
        "audio" -> R.string.pp_audio
        "despertador" -> R.string.pr_despertador
        "visita" -> R.string.pr_visita
        "casa" -> R.string.pr_casa
        "cenas" -> R.string.pr_cenas
        "filtro" -> R.string.pb_filtro
        else -> R.string.pb_dns
    })

    /** Refaz a tela com as linhas na ordem da escolha e devolve o foco à linha que estava focada. */
    private fun montar() {
        val t = ColumnsScreen(this, getString(R.string.pb_titulo))
        val coluna = t.section(getString(R.string.pb_titulo))
        var focar: View? = null
        for (id in PainelBlocos.ordemDaEscolha(lista)) {
            val mostrado = id in lista
            val estado = Estilo.valor(this, getString(if (mostrado) R.string.pb_mostrado else R.string.pb_escondido), seta = false)
            val l = Estilo.linhaOpcao(this, nome(id), null, estado)
            l.setOnClickListener { mudar(id, PainelBlocos.alternar(lista, id)) }
            l.setOnKeyListener { _, code, e ->
                if (e.action != KeyEvent.ACTION_DOWN || id !in lista) return@setOnKeyListener false
                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { mudar(id, PainelBlocos.mover(lista, id, -1)); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { mudar(id, PainelBlocos.mover(lista, id, 1)); true }
                    else -> false
                }
            }
            coluna.addView(l, t.lpRow())
            if (focar == null && (focado == null || id == focado)) focar = l
        }
        val padrao = Estilo.linhaOpcao(this, getString(R.string.pb_padrao)).apply {
            setOnClickListener { PainelBlocosTv.voltarAoPadrao(this@PainelBlocosActivity); lista = PainelBlocosTv.escolhidos(this@PainelBlocosActivity); focado = null; montar() }
        }
        coluna.addView(padrao, t.lpRow())
        setContentView(t.root)
        t.start(focar ?: padrao)
    }

    private fun mudar(id: String, nova: List<String>) {
        if (nova == lista) return
        lista = nova
        focado = id
        PainelBlocosTv.definir(this, lista)
        montar()
    }
}
