package br.paulo.tvlauncher

import android.app.Activity
import java.util.Collections
import java.util.WeakHashMap

/**
 * recreate() uma vez só por tela (#619, 27/11/2026).
 *
 * Por quê: duas chamadas de recreate() na mesma Activity antes da primeira rodar enfileiram dois "relaunch" locais; o
 * segundo tenta tirar uma janela que o primeiro já tirou e o app cai em WindowManagerGlobal.findViewLocked
 * (IllegalArgumentException, "not attached"). A HOME já tinha essa trava (MainActivity.recriar, 23/11); as outras telas
 * do processo principal não, e a pilha voltou em 26/11 (474947, 474968): o Ajuste fino, que fica por cima da HOME, recria
 * a cada ◀/▶ do "Jeito da tela inicial" por um post, e dois toques rápidos davam dois recreate().
 *
 * Como: toda tela chama [recreateOnce] em vez de recreate() (SafeRecreateRulesTest confere o código). A Activity que já pediu fica marcada (fraca: some com a instância velha); a nova instância começa livre.
 */
object SafeRecreate {
    private val pediram: MutableSet<Activity> = Collections.newSetFromMap(WeakHashMap())

    /** Pode recriar agora? Não se já pediu, se está fechando ou se já foi destruída. Puro o bastante para testar. */
    fun permitido(jaPediu: Boolean, fechando: Boolean, destruida: Boolean) = !jaPediu && !fechando && !destruida

    @Synchronized
    fun pedir(a: Activity): Boolean {
        if (!permitido(a in pediram, a.isFinishing, a.isDestroyed)) return false
        pediram += a
        return true
    }
}

/** recreate() que ignora o segundo pedido da mesma tela (ver [SafeRecreate]). */
fun Activity.recreateOnce() { if (SafeRecreate.pedir(this)) recreate() }
