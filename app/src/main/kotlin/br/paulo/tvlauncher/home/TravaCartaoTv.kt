package br.paulo.tvlauncher.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import br.paulo.tvlauncher.MainActivity

/**
 * Trava por PIN do cartão (menu do cartão, CardMenu) num funil só (revisor 26/11): o app travado abria sem PIN pela
 * busca, pelos botões do controle e pelo modo simples, porque só o cartão da HOME conferia. Agora o serviço de
 * acessibilidade vê a janela do app travado e, sem PIN digitado, manda para a HOME, que pede o PIN e abre.
 * O PIN vale até a HOME voltar para a frente.
 */
object TravaCartaoTv {
    const val EXTRA_PACOTE = "trava_cartao_pacote"
    private val liberados = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Decisão pura (TravaCartaoTvTest): o app da frente precisa do PIN agora? */
    fun barrar(pacote: String, travados: Set<String>, liberados: Set<String>): Boolean =
        pacote in travados && pacote !in liberados

    fun liberar(pacote: String) { liberados += pacote }

    fun janela(servico: AccessibilityService, pacote: CharSequence?, classe: CharSequence?) {
        val p = pacote?.toString() ?: return
        if (p == servico.packageName) {
            if (classe?.toString() == MainActivity::class.java.name) liberados.clear()   // voltou à HOME: trava de novo
            return
        }
        if (!barrar(p, CardMenu.locked(servico), liberados.toSet())) return
        servico.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        servico.startActivity(Intent(servico, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_PACOTE, p))
    }
}
