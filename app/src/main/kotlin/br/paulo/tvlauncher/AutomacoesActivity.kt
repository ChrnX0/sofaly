package br.paulo.tvlauncher

import br.paulo.tvlauncher.resources.R
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import br.paulo.tvlauncher.alarm.Despertador
import br.paulo.tvlauncher.family.Perfil
import br.paulo.tvlauncher.input.RemapTeclas
import br.paulo.tvlauncher.network.Automacoes
import br.paulo.tvlauncher.network.Cenas
import br.paulo.tvlauncher.player.salvar

/**
 * Automações (14/11/2026, ideia 1): o editor único de "quando X, faça Y" ([Automacoes]). Aberta pelas Configurações
 * do launcher, no lugar da antiga linha Rotinas. Quatro grupos:
 *  - Quando for hora: as rotinas; OK abre o editor da rotina (RotinasActivity direto no editor);
 *  - Quando apertar um botão: OK abre o editor do botão (TeclasActivity direto);
 *  - Quando algo acontecer: cada evento muda aqui mesmo com OK / ◀ ▶ entre nada e as cenas (grava na hora);
 *  - Por link: as cenas (criar e editar no painel web); OK abre a tela Cenas.
 * Volta a desenhar no onResume, para mostrar o que os editores mudaram. Visual igual ao das Rotinas.
 */
class AutomacoesActivity : Activity() {
    override fun onCreate(b: android.os.Bundle?) {
        super.onCreate(b)
        VigiaDesfazer.ligar(this, getString(R.string.desf_rot_automacoes)) { desenhar() }   // elevação 2.11
    }

    override fun onResume() {
        super.onResume()
        if (VigiaAtualizacao.fechada(this)) return   // modo seguro: tela que derrubou esta versão (VigiaAtualizacao)
        desenhar()
    }

    // Molde das telas de ajuste (ColumnsScreen; passada das telas 27/11/2026: esta ainda era a coluna única esticada, com
    // "+ Nova rotina" numa caixa da largura da tela). Os quatro grupos viram o trilho; a explicação vai para a direita.
    private var tela: ColumnsScreen? = null

    override fun onBackPressed() {
        if (tela?.back() != true) @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun desenhar() {
        val focoAntes = currentFocus?.tag as? String
        val t = ColumnsScreen(this, getString(R.string.aut_titulo), getString(R.string.aut_det))
        tela = t
        val regras = Automacoes.listar(this)
        val cenas = Cenas.lista(this).map { it.nome }

        val hora = t.section(getString(R.string.aut_horario))
        linha(t, hora, "nova-rotina", getString(R.string.rot_nova_mais), "", getString(R.string.rot_nova_det)) {
            startActivity(Intent(this, RotinasActivity::class.java).putExtra(RotinasActivity.EXTRA_ROTINA, -1))
        }
        for (r in Rotinas.listar(this)) {
            val dias = Despertador.diasTexto(this, r.dias) + if (r.ativa) "" else getString(R.string.rot_desligada_sufixo)
            linha(t, hora, "rotina:${r.id}", "%02d:%02d  ·  %s".format(r.hora, r.minuto, Rotinas.descricao(this, r)), dias, null) {
                startActivity(Intent(this, RotinasActivity::class.java).putExtra(RotinasActivity.EXTRA_ROTINA, r.id))
            }
        }

        val botao = t.section(getString(R.string.aut_botao))
        linha(t, botao, "novo-botao", getString(R.string.tec_nova), "", getString(R.string.tec_nova_det)) {
            startActivity(Intent(this, TeclasActivity::class.java).putExtra(TeclasActivity.EXTRA_TECLA, 0))
        }
        for (tecla in regras.mapNotNull { (it.quando as? Automacoes.Quando.Botao)?.tecla }.distinct()) {
            val det = regras.filter { (it.quando as? Automacoes.Quando.Botao)?.tecla == tecla }
                .joinToString("  ·  ") { "${nomeGesto((it.quando as Automacoes.Quando.Botao).gesto)}: ${rotulo(it.faz)}" }
            linha(t, botao, "tecla:$tecla", RemapTeclas.nome(this, tecla), "", det) {
                startActivity(Intent(this, TeclasActivity::class.java).putExtra(TeclasActivity.EXTRA_TECLA, tecla))
            }
        }

        val evento = t.section(getString(R.string.aut_evento))
        val gatilhos = Gatilhos.todos(this).toMutableMap()
        for (e in Gatilhos.Evento.values()) {
            // "Evento → o que a TV faz" (crítica de design 19/11/2026): o evento à esquerda, a cena escolhida à direita.
            fun valor() = "‹  ${gatilhos[e] ?: getString(R.string.aut_nenhuma_acao)}  ›"
            lateinit var v: TextView
            fun girar(passo: Int) {
                // Sem cena nenhuma, girar não tem o que escolher: diz como criar uma, em vez de não fazer nada (25/11/2026,
                // TodoBotaoFazAlgoTest: as 15 linhas de evento focavam e o OK ficava mudo).
                if (cenas.isEmpty()) { AvisoTv.rapido(this, nomeEvento(e), getString(R.string.rot_cena_sem)); return }
                val tinha = gatilhos[e] != null
                val novo = Cenas.girar(cenas, gatilhos[e] ?: "", passo, comNenhuma = true)
                if (novo.isEmpty()) gatilhos.remove(e) else gatilhos[e] = novo
                Gatilhos.salvar(this, gatilhos)
                if (tinha != (gatilhos[e] != null)) desenhar()   // a linha "só se" aparece ou some (o foco volta a esta)
                else { v.text = valor(); t.refresh() }
            }
            val row = linha(t, evento, "evento:${e.chave}", nomeEvento(e), valor(),
                if (cenas.isEmpty()) getString(R.string.rot_cena_sem) else null) { girar(1) }
            v = row.second
            girarComSetas(row.first) { girar(it) }
            // Elevação 10.5: com cena escolhida, uma linha "só se" logo abaixo (◀ ▶ / OK trocam a condição).
            if (gatilhos[e] != null) {
                lateinit var w: TextView
                val opcoes = SoSe.opcoes(runCatching { Perfil.contas(this) }.getOrDefault(emptyList()))
                fun girarSoSe(passo: Int) {
                    val m = Gatilhos.soSe(this).toMutableMap()
                    val atual = m[e].orEmpty()
                    // Combinação montada no painel (fora das opções da TV) entra na roda como a 1ª: girar não a apaga sem querer.
                    val roda = if (atual in opcoes) opcoes else listOf(atual) + opcoes
                    val novo = roda[(roda.indexOf(atual) + passo).mod(roda.size)]
                    if (novo.isEmpty()) m.remove(e) else m[e] = novo
                    Gatilhos.salvarSoSe(this, m)
                    w.text = textoSoSe(novo)
                    t.refresh()
                }
                val ss = linha(t, evento, "soSe:${e.chave}", textoSoSe(Gatilhos.soSe(this)[e].orEmpty()), "", null,
                    recuo = true) { girarSoSe(1) }
                w = ss.third
                girarComSetas(ss.first) { girarSoSe(it) }
            }
        }

        val link = t.section(getString(R.string.aut_link))
        linha(t, link, "links", resources.getQuantityString(R.plurals.aut_links, cenas.size, cenas.size), "", getString(R.string.aut_link_det)) {
            startActivity(Intent(this, CenasActivity::class.java))
        }

        setContentView(t.root)
        // Volta ao item que estava em foco (os editores voltam para cá e a tela se redesenha no onResume).
        t.start(focoAntes?.let { t.root.findViewWithTag<View>(it) })
    }

    /** ◀ ▶ trocam o valor da linha (e não voltam ao trilho). */
    private fun girarComSetas(v: View, girar: (Int) -> Unit) = v.setOnKeyListener { _, k, ev ->
        if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
        when (k) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { girar(-1); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { girar(1); true }
            else -> false
        }
    }

    private fun nomeGesto(g: RemapTeclas.Gesto) = getString(when (g) {
        RemapTeclas.Gesto.TOQUE -> R.string.tec_toque
        RemapTeclas.Gesto.SEGURAR -> R.string.tec_segurar
        RemapTeclas.Gesto.DUPLO -> R.string.tec_duplo
        RemapTeclas.Gesto.TRIPLO -> R.string.tec_triplo
    })

    private fun nomeEvento(e: Gatilhos.Evento) = getString(when (e) {
        Gatilhos.Evento.MIDIA_TOCOU -> R.string.ev_midia_tocou
        Gatilhos.Evento.MIDIA_PAUSOU -> R.string.ev_midia_pausou
        Gatilhos.Evento.TV_LIGOU -> R.string.ev_tv_ligou
        Gatilhos.Evento.TODOS_SAIRAM -> R.string.ev_todos_sairam
        Gatilhos.Evento.CHUVA -> R.string.ev_chuva
        Gatilhos.Evento.FILME_ACABOU -> R.string.elv_ev_filme_acabou
        Gatilhos.Evento.PROGRAMA_COMECOU -> R.string.elv_ev_programa_comecou
        Gatilhos.Evento.NASCER_DO_SOL -> R.string.elv_ev_nascer_do_sol
        Gatilhos.Evento.POR_DO_SOL -> R.string.elv_ev_por_do_sol
        Gatilhos.Evento.CAMPAINHA -> R.string.elv_ev_campainha
        Gatilhos.Evento.ALGUEM_CHEGOU -> R.string.elv_ev_alguem_chegou
        Gatilhos.Evento.INTERNET_CAIU -> R.string.elv_ev_internet_caiu
        Gatilhos.Evento.INTERNET_VOLTOU -> R.string.elv_ev_internet_voltou
        Gatilhos.Evento.RECADO_CHEGOU -> R.string.elv_ev_recado_chegou
        Gatilhos.Evento.ANIVERSARIO -> R.string.elv_ev_aniversario
        Gatilhos.Evento.COMPROMISSO -> R.string.elv_ev_compromisso
    })

    /** Elevação 10.5: o "só se" em palavras ("Só se alguém em casa e depois do pôr do sol"). */
    private fun textoSoSe(l: List<SoSe.Condicao>): String = if (l.isEmpty()) getString(R.string.elv_ss_sempre)
        else getString(R.string.elv_ss_so_se, l.joinToString(getString(R.string.elv_ss_e)) { c ->
            when (c) {
                SoSe.Condicao.AlguemEmCasa -> getString(R.string.elv_ss_em_casa)
                SoSe.Condicao.DepoisDoPorDoSol -> getString(R.string.elv_ss_noite)
                SoSe.Condicao.DiaUtil -> getString(R.string.elv_ss_dia_util)
                is SoSe.Condicao.Perfil -> getString(R.string.elv_ss_perfil, Perfil.usuario(c.conta))
            }
        })

    private fun rotulo(faz: String): String = when {
        faz.startsWith("app:") -> faz.removePrefix("app:").let { Rotinas.rotuloApp(this, it) ?: it }
        faz.startsWith("cena:") -> getString(R.string.tec_cena, faz.removePrefix("cena:"))
        else -> faz
    }

    // ---------------------------------------------------------------- peças

    /**
     * Linha do molde ([ColumnsScreen.valueRow]): [titulo] à esquerda, [valor] à direita, [explica] na coluna da direita.
     * [recuo]: a linha "só se", que pertence ao evento de cima. Devolve a linha, o texto do valor e o do título.
     */
    private fun linha(t: ColumnsScreen, sec: LinearLayout, chave: String, titulo: String, valor: String, explica: String?,
                      recuo: Boolean = false, aoClicar: () -> Unit): Triple<LinearLayout, TextView, TextView> {
        val (row, label, value) = t.valueRow()
        row.tag = chave
        label.text = titulo
        value.text = valor
        row.setOnClickListener { aoClicar() }
        t.describe(row) { ColumnsScreen.Info(headline = label.text, body = explica) }
        sec.addView(row, t.lpRow().apply { if (recuo) marginStart = dp(32) })
        return Triple(row, value, label)
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density + 0.5f).toInt()
}
