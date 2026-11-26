package br.paulo.tvlauncher

import android.app.Activity
import android.content.Context
import android.os.Bundle
import br.paulo.tvlauncher.settings.RoteiroConferencia

/**
 * Tela "Conferir na TV" (14/11/2026, ideia 63; Configurações > Sistema > Esta versão). Lista o que conferir com o
 * controle nesta versão (strings_versoes.xml); OK num item pergunta Sim/Não e grava (RoteiroConferencia). As conversas
 * leem o resultado pelo adb: EXTRAS "roteiro". Na maquete era ▲/▼ direto na linha, mas ▲/▼ já movem o foco na lista:
 * a resposta foi para um cartão de Sim/Não.
 */
class ConferirTvActivity : Activity() {
    companion object {
        private const val PREFS = "roteiro"
        private const val CHAVE = "estado"

        fun ids(ctx: Context): List<String> = ctx.resources.getStringArray(R.array.roteiro_ids).toList()
        fun estado(ctx: Context) = RoteiroConferencia.ler(
            ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getString(CHAVE, null), BuildConfig.VERSION_CODE.toLong())
        private fun gravar(ctx: Context, e: RoteiroConferencia.Estado) =
            ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CHAVE, RoteiroConferencia.gravar(e)).apply()
        /** Para o adb (EXTRAS "roteiro"). */
        fun relatorio(ctx: Context): String = RoteiroConferencia.relatorio(estado(ctx), ids(ctx))
    }

    private var focoEm = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (VigiaAtualizacao.fechada(this)) return   // modo seguro: tela que derrubou esta versão (VigiaAtualizacao)
        montar()
    }

    private fun montar() {
        // Molde das telas de ajuste (Paulo, 26/11/2026): os itens no meio, a pergunta do item em foco à direita.
        val ids = ids(this)
        val e0 = estado(this)
        val conferidos = getString(R.string.ver_conferido, RoteiroConferencia.conferidos(e0, ids), ids.size)
        val tela = ColumnsScreen(this, getString(R.string.ver_conferir),
            getString(R.string.ver_conferir_sub, BuildConfig.VERSION_CODE.toString(), ids.size) + "  ·  " + conferidos)
        val titulos = resources.getStringArray(R.array.roteiro_titulos)
        val perguntas = resources.getStringArray(R.array.roteiro_perguntas)
        val e = estado(this)
        val coluna = tela.section(conferidos)
        val linhas = mutableListOf<android.view.View>()
        ids.forEachIndexed { i, id ->
            val resp = e.respostas[id]
            val marca = when (resp) { true -> "✓"; false -> "✕"; null -> "${i + 1}" }
            val resposta = when (resp) { true -> getString(R.string.ver_respondido_sim); false -> getString(R.string.ver_respondido_nao); null -> null }
            val linha = Estilo.linhaOpcao(this, titulos.getOrElse(i) { id }, resposta, Estilo.valor(this, marca, seta = false))
            tela.describe(linha) { ColumnsScreen.Info(titulos.getOrElse(i) { id }, null, perguntas.getOrElse(i) { "" }) }
            linha.setOnClickListener {
                focoEm = i
                Estilo.lista(this, titulos.getOrElse(i) { id }, listOf(getString(R.string.ver_sim), getString(R.string.ver_nao)),
                    marcadas = when (resp) { true -> listOf(0); false -> listOf(1); null -> emptyList() }) { escolha ->
                    gravar(this, RoteiroConferencia.responder(estado(this), id, escolha == 0))
                    // Próximo item sem resposta (ou o mesmo, se todos já responderam): segue o roteiro sem caçar o foco.
                    val novo = estado(this)
                    focoEm = (i + 1 until ids.size).firstOrNull { ids[it] !in novo.respostas } ?: i
                    montar()
                }
            }
            coluna.addView(linha, tela.lpRow())
            linhas += linha
        }
        setContentView(tela.root)
        tela.start(linhas.getOrNull(focoEm))
    }
}
