package br.paulo.tvlauncher.home

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import br.paulo.tvlauncher.HomeSeguraActivity
import br.paulo.tvlauncher.MainActivity
import br.paulo.tvlauncher.ModoSimples
import br.paulo.tvlauncher.ModoSimplesActivity
import br.paulo.tvlauncher.resources.R
import br.paulo.tvlauncher.family.PinNaTela

/**
 * Trava por PIN do cartão (menu do cartão, CardMenu) num funil só (revisor 26/11): o app travado abria sem PIN pela
 * busca, pelos botões do controle e pelo modo simples, porque só o cartão da HOME conferia. Agora o serviço de
 * acessibilidade vê a janela do app travado e, sem PIN digitado, manda para a tela inicial em uso (HOME ou modo
 * simples), que pede o PIN e abre. O PIN vale até uma tela inicial nossa voltar para a frente ou a TV apagar.
 */
object TravaCartaoTv {
    const val EXTRA_PACOTE = "trava_cartao_pacote"
    private const val JANELA_REPETIDA_MS = 2_000L   // o app abre a tela de entrada e logo a principal: 2 eventos
    private const val PEDINDO_MAX_MS = 60_000L

    private val liberados = java.util.Collections.synchronizedSet(HashSet<String>())
    private var ultimoPacote: String? = null
    private var ultimoEm = 0L
    @Volatile private var pedindoDesde = 0L

    /** As nossas telas iniciais: com uma delas na frente, a liberação acaba. */
    private val INICIAIS = setOf(MainActivity::class.java.name, ModoSimplesActivity::class.java.name, HomeSeguraActivity::class.java.name)

    /** Decisão pura (TravaCartaoTvTest): o app da frente precisa do PIN agora? */
    fun barrar(pacote: String, travados: Set<String>, liberados: Set<String>): Boolean =
        pacote in travados && pacote !in liberados

    /** Mesmo pacote outra vez em pouco tempo (pura): não manda para a HOME de novo, senão empilham caixas de PIN. */
    fun repetida(pacote: String, ultimo: String?, agora: Long, ultimoEm: Long): Boolean =
        pacote == ultimo && agora - ultimoEm < JANELA_REPETIDA_MS

    fun liberar(pacote: String) { liberados += pacote }

    /** A TV apagou: quem destravou ontem não deixa o app aberto para a criança de manhã. */
    fun telaApagou() = liberados.clear()

    fun janela(servico: AccessibilityService, pacote: CharSequence?, classe: CharSequence?) {
        val p = pacote?.toString() ?: return
        if (p == servico.packageName) {
            if (classe?.toString() in INICIAIS) liberados.clear()
            return
        }
        if (!barrar(p, CardMenu.locked(servico), liberados.toSet())) return
        val agora = SystemClock.elapsedRealtime()
        if (repetida(p, ultimoPacote, agora, ultimoEm)) return
        ultimoPacote = p; ultimoEm = agora
        servico.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        val destino = if (ModoSimples.ligado(servico)) ModoSimplesActivity::class.java else MainActivity::class.java
        servico.startActivity(Intent(servico, destino)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_PACOTE, p))
    }

    /**
     * A tela inicial pede o PIN e, certo, abre o app com [abrir]. Uma caixa por vez: um pedido que chega com outra
     * aberta é ignorado (a pessoa já está digitando).
     */
    fun pedirEAbrir(tela: Activity, pacote: String, abrir: () -> Unit) {
        val agora = SystemClock.elapsedRealtime()
        if (pedindoDesde != 0L && agora - pedindoDesde < PEDINDO_MAX_MS) return
        pedindoDesde = agora
        PinNaTela.pedir(tela, tela.getString(R.string.rw_travado), aoCancelar = { pedindoDesde = 0L }) {
            pedindoDesde = 0L
            liberar(pacote)
            abrir()
        }
    }

    /** O pedido que veio no Intent (onCreate ou onNewIntent da tela inicial). Tira o extra para não repetir. */
    fun doIntent(i: Intent?): String? = i?.getStringExtra(EXTRA_PACOTE)?.also { i.removeExtra(EXTRA_PACOTE) }

    fun travado(ctx: Context, pacote: String) = CardMenu.isLocked(ctx, pacote)
}
