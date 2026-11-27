package br.paulo.tvlauncher

import br.paulo.tvlauncher.resources.R
import android.app.Activity
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import br.paulo.tvlauncher.observer.Falhas
import br.paulo.tvlauncher.observer.FalhasTv
import br.paulo.tvlauncher.observer.MedidasTv
import br.paulo.tvlauncher.observer.Placar
import br.paulo.tvlauncher.observer.VitaisLocais
import br.paulo.tvlauncher.observer.VitaisTv
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * "Como esta TV está indo" (elevação 2.1, onda 0, 17/11/2026): os 7 pilares do produto, cada um com o número medido
 * nesta TV, o alvo e a data da medida. Pilar sem medida diz "ainda não medido" (nunca o alvo no lugar do número).
 * As regras moram em Placar.kt; as medidas, em MedidasTv. Aberta pelas Configurações (aba Sistema) e por
 * `EXTRAS --es o placar_tela`.
 *
 * Nunca só pela cor: cada linha leva também o sinal (✓ dentro do alvo, ! fora, — sem medida) e a palavra.
 */
class PlacarActivity : Activity() {
    companion object {
        /** Vírgula ou ponto, conforme a língua da TV ("0,8 s" / "0.8 s"). */
        fun decimal(): Char = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
    }

    private lateinit var tela: CardsScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tela de LEITURA em cartões (Paulo, 26/11/2026, "B na leitura"): os 7 pilares lado a lado, número grande,
        // tudo de uma vez sem rolar. Antes: uma coluna de linhas esticada nos 960 dp.
        tela = CardsScreen(this, getString(R.string.elv_placar_titulo), getString(R.string.elv_placar_sub))
        setContentView(tela.root)
        // Sete arquivos de poucos centésimos de KB: ler na hora custa menos que um quadro e a tela já abre completa.
        val linhas = runCatching { MedidasTv.linhas(this) }.getOrElse { Placar.linhas(Placar.Entradas()) }
        val quedas = runCatching { VitaisTv.resumo30(this) }.getOrNull()
        // Elevação 2.3: o que precisa de atenção vem antes dos pilares. Sem rede primeiro (abre na hora); depois, com a
        // hora da internet (relógio errado), redesenha se mudou.
        val falhas = runCatching { FalhasTv.atuais(this, comRede = false) }.getOrDefault(emptyList())
        desenhar(linhas, quedas, falhas)
        Fundo.rodar("placar-falhas") {
            val comRede = runCatching { FalhasTv.atuais(this, comRede = true) }.getOrNull() ?: return@rodar
            if (comRede != falhas) runOnUiThread { if (!isDestroyed) desenhar(linhas, quedas, comRede) }
        }
    }

    private fun nome(p: Placar.Pilar) = getString(when (p) {
        Placar.Pilar.LIGA -> R.string.elv_pilar_liga
        Placar.Pilar.AO_VIVO -> R.string.elv_pilar_ao_vivo
        Placar.Pilar.PLAYER -> R.string.elv_pilar_player
        Placar.Pilar.CASA -> R.string.elv_pilar_casa
        Placar.Pilar.CONTROLE -> R.string.elv_pilar_controle
        Placar.Pilar.TODOS -> R.string.elv_pilar_todos
        Placar.Pilar.INSTALA -> R.string.elv_pilar_instala
    })

    private fun promessa(p: Placar.Pilar) = if (p == Placar.Pilar.TODOS) getString(R.string.elv_prom_todos, BuildConfig.IDIOMAS) else getString(when (p) {
        Placar.Pilar.LIGA -> R.string.elv_prom_liga
        Placar.Pilar.AO_VIVO -> R.string.elv_prom_ao_vivo
        Placar.Pilar.PLAYER -> R.string.elv_prom_player
        Placar.Pilar.CASA -> R.string.elv_prom_casa
        Placar.Pilar.CONTROLE -> R.string.elv_prom_controle
        Placar.Pilar.TODOS -> R.string.elv_prom_todos
        Placar.Pilar.INSTALA -> R.string.elv_prom_instala
    })

    private fun numero(n: Placar.Numero): String = when (n) {
        is Placar.Numero.Tempo -> Placar.tempoTexto(n.ms, decimal())
        is Placar.Numero.Contagem -> getString(R.string.elv_n_de_n, n.certos, n.total)
        is Placar.Numero.Quedas -> resources.getQuantityString(R.plurals.elv_quedas, n.quedas, n.quedas, n.dias)
    }

    private fun desenhar(linhas: List<Placar.Linha>, quedas: VitaisLocais.Resumo?, falhas: List<Falhas.Tipo> = emptyList()) {
        tela.clear()
        var primeira: View? = null
        if (falhas.isNotEmpty()) {
            // Elevação 2.3: o que precisa de atenção vem antes dos pilares, um cartão largo por falha, com o conserto.
            tela.group(getString(R.string.elv_f_titulo))
            for (f in falhas) {
                val c = tela.card(wide = true)
                tela.name(c, FalhasTv.frase(this, f))
                tela.caption(c, FalhasTv.causa(this, f))
                tela.caption(c, "▶  " + FalhasTv.botao(this, f), Estilo.ATENCAO)
                c.setOnClickListener { FalhasTv.consertar(this, f) }
                if (primeira == null) primeira = c
            }
            tela.group(getString(R.string.elv_placar_titulo))
        }
        val agora = System.currentTimeMillis()
        for (l in linhas) {
            val c = tela.card()
            tela.name(c, nome(l.pilar))
            if (l.numero == null) tela.caption(c, getString(R.string.elv_nao_medido))
            else {
                tela.number(c, numero(l.numero))
                l.alvo?.let { tela.caption(c, getString(R.string.elv_alvo, numero(it))) }
                l.medidoEm?.let {
                    tela.caption(c, if (agora - it < DateUtils.MINUTE_IN_MILLIS) getString(R.string.elv_agora)
                        else DateUtils.getRelativeTimeSpanString(it, agora, DateUtils.MINUTE_IN_MILLIS).toString())
                }
            }
            if (l.pilar == Placar.Pilar.LIGA && quedas != null) {
                val n = quedas.quedas + quedas.travadas
                tela.caption(c, if (n == 0) getString(R.string.elv_sem_quedas, VitaisLocais.DIAS)
                    else resources.getQuantityString(R.plurals.elv_quedas, n, n, VitaisLocais.DIAS))
            }
            tela.spacer(c)
            // Nunca só pela cor: sinal e palavra (✓ dentro do alvo, ! fora, — sem medida).
            val (sinal, palavra, cor) = when (l.dentro) {
                true -> Triple("✓", getString(R.string.elv_dentro), Estilo.ACENTO)
                false -> Triple("!", getString(R.string.elv_fora), Estilo.ATENCAO)
                null -> Triple("—", "", Estilo.TEXTO_3)
            }
            tela.caption(c, if (palavra.isEmpty()) sinal else "$sinal  $palavra", cor).apply { typeface = Estilo.fonte(700) }
            // OK no cartão: a promessa do pilar (o que o número quer dizer), num aviso curto.
            c.setOnClickListener { AvisoTv.rapido(this, nome(l.pilar), promessa(l.pilar)) }
            if (primeira == null) primeira = c
        }
        tela.start(primeira)
    }

}
