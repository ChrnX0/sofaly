package br.paulo.tvlauncher.home

import android.content.Context
import br.paulo.tvlauncher.settings.Extras

/**
 * "Mostrar o Continuar" no topo de cada categoria (Paulo, 22/11/2026).
 *
 * Porquê: os apps já guardam o último estado ao sair, e o Continuar virou função repetida.
 * Para quê: quem quiser volta a ver o Continuar (Ajuste fino); de fábrica ele fica escondido.
 * Como: número 0/1 nas prefs do Extras ([KEY]); [keeps] diz quais itens valem quando ligado (só o que retoma do
 * ponto onde parou: os nossos e os apps que avisam pela sessão de mídia). Puro o bastante para teste na JVM.
 */
object ContinueOption {
    const val KEY = "cross_continue"
    fun on(ctx: Context): Boolean = Extras.numero(ctx, KEY, 0) == 1
    fun set(ctx: Context, on: Boolean) = Extras.definirNumero(ctx, KEY, if (on) 1 else 0)

    /** Ids do Continuar que retomam de onde parou: TV, série, filme, rádio, podcast e apps de mídia. */
    fun keeps(id: String): Boolean = id == "c:tv" || id == "c:serie" || id == "c:filme" || id == "c:radio" ||
        id == "c:podcast" || id.startsWith("c:midia:")
}
