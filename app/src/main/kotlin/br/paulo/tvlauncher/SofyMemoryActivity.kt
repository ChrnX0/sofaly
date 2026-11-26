package br.paulo.tvlauncher

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import br.paulo.tvlauncher.family.PinTv
import br.paulo.tvlauncher.observer.ObserverTv
import br.paulo.tvlauncher.observer.SofyHabitsTv
import br.paulo.tvlauncher.observer.SofyNormsTv
import br.paulo.tvlauncher.observer.SofyRulesTv
import br.paulo.tvlauncher.panel.PainelArmazem

/**
 * "O que a Sofy sabe" (Brainstorm item 28, 26/11/2026). Por quê: a Sofy aprende o normal da casa e age sozinha, mas
 * nenhuma tela mostrava o que ela guardou. Transparência segura a confiança numa assistente que age sem perguntar.
 * Para quê: cada coisa que ela aprendeu numa linha (o que é, quanto ela já sabe) e OK = Esquecer (com o PIN, quando
 * a casa tem PIN). Esquecer apaga a memória e a cópia gravada: ela recomeça a aprender do zero.
 * Referência visual: Pi, "Saved memories" com Esquecer (Mobbin), no mesmo molde das listas das Configurações.
 */
class SofyMemoryActivity : Activity() {
    private lateinit var lista: LinearLayout

    /** Uma memória: título, o que ela sabe agora e como esquecer. */
    private class Memoria(val titulo: Int, val det: Int, val vazio: Int, val quanto: (Activity) -> Int, val esquecer: (Activity) -> Unit)

    private val memorias = listOf(
        Memoria(R.string.smem_uso, R.string.smem_uso_det, R.string.smem_vazio, ObserverTv::knownUseHours, ObserverTv::forgetUseHours),
        Memoria(R.string.smem_rede, R.string.smem_rede_det, R.string.smem_vazio, ObserverTv::knownNetworkSignals, ObserverTv::forgetNetworkNormal),
        Memoria(R.string.smem_pessoas, R.string.smem_pessoas_det, R.string.smem_vazio, SofyHabitsTv::peopleCount, SofyHabitsTv::forget),
        Memoria(R.string.smem_casa, R.string.smem_casa_det, R.string.smem_vazio, SofyNormsTv::daysKnown, SofyNormsTv::forget),
        Memoria(R.string.smem_regras, R.string.smem_regras_det, R.string.smem_vazio, SofyRulesTv::liveCount, SofyRulesTv::forget),
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (VigiaAtualizacao.fechada(this)) return
        val (raiz, coluna) = Estilo.telaLista(this, getString(R.string.smem_titulo), getString(R.string.smem_sub))
        lista = coluna
        setContentView(raiz)
        montar(focar = 0)
    }

    /** Os números são lidos na hora (cada um é uma leitura curta de prefs já carregadas). */
    private fun montar(focar: Int) {
        lista.removeAllViews()
        val linhas = memorias.map { m ->
            val n = m.quanto(this)
            linha(getString(m.titulo), if (n > 0) getString(m.det, n) else getString(m.vazio)) { esquecer(listOf(m), memorias.indexOf(m)) }
        }
        linhas.forEach { lista.addView(it) }
        lista.addView(linha(getString(R.string.smem_tudo), getString(R.string.smem_tudo_det)) { esquecer(memorias, memorias.size) })
        lista.getChildAt(focar.coerceIn(0, lista.childCount - 1))?.requestFocus()
    }

    private fun esquecer(quais: List<Memoria>, linha: Int) {
        val feito = {
            quais.forEach { m -> runCatching { m.esquecer(this) } }
            AvisoTv.rapido(applicationContext, getString(R.string.smem_titulo), getString(R.string.smem_esqueceu))
            montar(linha)
        }
        if (PainelArmazem.temPin(this)) PinTv.pedir(this, getString(R.string.smem_pin)) { certo -> if (certo) feito() }
        else feito()
    }

    private fun linha(titulo: String, detalhe: String, aoClicar: () -> Unit) = Estilo.linhaOpcao(this, titulo, detalhe).apply {
        setOnClickListener { aoClicar() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = (8 * resources.displayMetrics.density).toInt() }
    }
}
