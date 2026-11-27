package br.paulo.tvlauncher.settings

import android.app.Activity
import android.content.Context

/**
 * Ideia 47 (14/11/2026): um tamanho de texto só, para todas as telas do launcher (antes só Filmes, Séries e TV ao vivo
 * tinham a letra maior, Estilo.Leitura). O LauncherApp aplica [aplicar] em cada tela antes do onCreate dela.
 *
 * Como: todo texto do launcher é em sp, e sp = dp × scaledDensity. Mudar o scaledDensity da tela muda tudo de uma vez,
 * sem mexer em cada arquivo. Medidas em dp (alturas de linha, cards) não mudam, por isso o máximo é 120%: acima disso
 * o texto começa a não caber nos blocos de altura fixa (painel rápido, grade de pôsteres). Se o Android já estiver com
 * letra aumentada na acessibilidade, ele usa uma conversão própria (não linear) e esta escolha não se soma a ela.
 *
 * Decisão anterior que isto toca: a letra maior de Filmes/Séries/TV (13/11). Ela continua; o tamanho escolhido aqui
 * vale por cima dela também. Padrão = 100% (nada muda).
 *
 * 80% e 90% (Paulo, 26/11/2026: "a fonte deixa pro user poder escolher"): o Projectivy usa letra de 14 sp nos ajustes;
 * a Sofaly fica nos 18 sp de fábrica, e quem quiser a letra menor dele escolhe 80% (18 x 0,8 = 14,4 sp).
 */
object TamanhoTexto {
    val OPCOES = listOf(80, 90, 100, 110, 120)
    const val NORMAL = 100
    const val CHAVE = "tamanho_texto"

    /** Próxima opção (dá a volta: 120 -> 80); valor fora da lista volta ao normal. */
    fun proximo(atual: Int): Int = if (atual !in OPCOES) NORMAL else OPCOES[(OPCOES.indexOf(atual) + 1).mod(OPCOES.size)]

    fun valido(v: Int): Int = if (v in OPCOES) v else NORMAL

    fun atual(ctx: Context): Int = valido(Extras.numero(ctx, CHAVE, 100))
    fun definir(ctx: Context, v: Int) = Extras.definirNumero(ctx, CHAVE, valido(v))

    /** Ajusta o scaledDensity da tela [a] ao tamanho escolhido (respeitando a escala de letra do sistema). */
    fun aplicar(a: Activity) {
        val pct = atual(a)
        val dm = a.resources.displayMetrics
        val sistema = a.resources.configuration.fontScale
        @Suppress("DEPRECATION")
        dm.scaledDensity = dm.density * sistema * pct / 100f
    }
}
