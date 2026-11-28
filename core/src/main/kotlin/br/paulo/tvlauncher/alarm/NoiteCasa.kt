package br.paulo.tvlauncher.alarm

import android.content.Context
import br.paulo.tvlauncher.settings.Extras

/**
 * A "noite da casa" (Brainstorm 26/11/2026, item 12): UM horário de noite que o Não perturbe e o filtro noturno usam
 * por padrão.
 *
 * Por quê: eram três relógios. O Não perturbe era fixo 23h-7h (o README promete "no horário que você escolher"), o
 * filtro noturno tinha 22h-6h próprio, e a pessoa ajustava cada um num lugar. Como: o Não perturbe segue sempre a noite
 * da casa; o filtro segue enquanto ninguém mexer no horário dele (mexeu = ajuste próprio; voltou ao mesmo horário da
 * noite = volta a seguir). A hora de dormir das crianças NÃO segue: é uma trava (apps e volume), e mudar o padrão dela
 * em silêncio deixaria a criança acordada até mais tarde em casas que nunca mexeram no horário.
 */
object NoiteCasa {
    const val CHAVE_INICIO = "noite_inicio_min"   // minutos desde 0h, nas prefs das Extras (vão no backup com elas)
    const val CHAVE_FIM = "noite_fim_min"
    /** O antigo horário fixo do Não perturbe: quem nunca mexeu vê o mesmo comportamento de antes. */
    const val PADRAO_INICIO = NaoPerturbe.MADRUGADA_INICIO_MIN
    const val PADRAO_FIM = NaoPerturbe.MADRUGADA_FIM_MIN

    fun inicio(ctx: Context): Int = Extras.numero(ctx, CHAVE_INICIO, PADRAO_INICIO)
    fun fim(ctx: Context): Int = Extras.numero(ctx, CHAVE_FIM, PADRAO_FIM)

    /** Valor de minuto aceito (0..23:59); fora disso, null (a rota do painel recusa). */
    fun minutoValido(v: Int): Int? = v.takeIf { it in 0 until 24 * 60 }

    /**
     * Grava o ajuste próprio de um recurso que segue a noite da casa: igual à noite = apaga a chave (volta a seguir),
     * diferente = grava (passa a ter horário próprio). Puro, para o teste: devolve null para "apagar".
     */
    fun proprio(valor: Int, daNoite: Int): Int? = if (valor == daNoite) null else valor
}
