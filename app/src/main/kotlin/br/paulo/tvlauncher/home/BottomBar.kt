package br.paulo.tvlauncher.home

import android.content.Context
import br.paulo.tvlauncher.settings.Extras

/**
 * "Barra embaixo": 2º jeito da tela inicial (Paulo, 23/11/2026; simulação aprovada em home-refino/barra-embaixo-2026-11-23.html).
 *
 * Porquê: a cruz é o padrão, mas o Paulo quis uma opção com as categorias paradas na linha de baixo, como uma doca.
 * Para quê: ◀ ▶ trocam a categoria, ▲ abre a linha de apps acima dela, ◀ ▶ andam nos apps, ▼ fecha a linha e volta
 * para a categoria. A linha de apps só aparece com o ▲ (escolha dele: "apenas ao apertar a tecla cima").
 * Como: a [HomeCruz] usa o mesmo estado da cruz ([CruzLogica.Estado]) e só traduz as teclas aqui ([passo]); assim
 * "lembrar o último app", HOME, abrir e as opções do OK segurado continuam iguais nos dois jeitos.
 * Liga no Ajuste fino; fica numa pref do Extras ([KEY]: 0 cruz, 1 barra, 2 barra única).
 * 24/11/2026 (Paulo): a "barra única" ([StripHome]) virou o jeito de fábrica; cruz e barra embaixo continuam opções.
 */
object BottomBar {
    const val KEY = "home_bottom_bar"
    /** Os jeitos da tela inicial, na ordem gravada na pref. */
    enum class Mode { CROSS, BAR, STRIP }
    val DEFAULT = Mode.STRIP
    fun mode(ctx: Context): Mode = Mode.entries.getOrNull(Extras.numero(ctx, KEY, DEFAULT.ordinal)) ?: DEFAULT
    fun setMode(ctx: Context, m: Mode) = Extras.definirNumero(ctx, KEY, m.ordinal)
    /** O próximo jeito (Ajuste fino: ◀ ▶ giram). */
    fun next(ctx: Context, passo: Int): Mode = Mode.entries[Math.floorMod(mode(ctx).ordinal + passo, Mode.entries.size)].also { setMode(ctx, it) }
    /** Liga a barra embaixo (true) ou volta à cruz (false); os testes de tela usam. */
    fun set(ctx: Context, on: Boolean) = setMode(ctx, if (on) Mode.BAR else Mode.CROSS)
    fun on(ctx: Context): Boolean = mode(ctx) == Mode.BAR
    fun strip(ctx: Context): Boolean = mode(ctx) == Mode.STRIP

    /** A cor da categoria (a mesma das prateleiras de "Todos os apps"); Ferramentas fica com o âmbar. */
    fun cor(c: CruzLogica.Categoria?): Int = when (c) {
        CruzLogica.Categoria.ASSISTIR -> br.paulo.tvlauncher.DesignTokens.WORLD_WATCH
        CruzLogica.Categoria.OUVIR -> br.paulo.tvlauncher.DesignTokens.WORLD_LISTEN
        CruzLogica.Categoria.JOGAR -> br.paulo.tvlauncher.DesignTokens.WORLD_PLAY
        CruzLogica.Categoria.APLICATIVOS -> br.paulo.tvlauncher.DesignTokens.WORLD_APPS
        else -> br.paulo.tvlauncher.DesignTokens.WORLD_CONTINUE
    }

    /** Resultado de uma tecla na barra: o que a cruz faz ([tecla], null = nada) e se a linha de apps fica aberta. */
    data class Passo(val tecla: CruzLogica.Tecla?, val naApps: Boolean, val subirTopo: Boolean = false)

    /**
     * Tradução pura da tecla. [sel] = posição do app escolhido na categoria atual.
     * - Na linha das categorias: ◀ ▶ trocam; ▲ ou OK abrem a linha de apps; ▼ não faz nada.
     * - Na linha de apps: ◀ ▶ andam (sem dar a volta; ◀ no 1º não sobe para a barra de cima), OK abre,
     *   ▼ fecha a linha, ▲ sobe para a barra de cima.
     * - HOME: a regra da cruz (Assistir; de novo, a TV) e a linha de apps fecha.
     */
    fun passo(t: CruzLogica.Tecla, naApps: Boolean, sel: Int): Passo = when {
        t == CruzLogica.Tecla.HOME -> Passo(CruzLogica.Tecla.HOME, false)
        !naApps -> when (t) {
            CruzLogica.Tecla.ESQUERDA, CruzLogica.Tecla.DIREITA -> Passo(t, false)
            CruzLogica.Tecla.CIMA, CruzLogica.Tecla.OK -> Passo(null, true)
            else -> Passo(null, false)
        }
        else -> when (t) {
            CruzLogica.Tecla.ESQUERDA -> Passo(if (sel > 0) CruzLogica.Tecla.CIMA else null, true)
            CruzLogica.Tecla.DIREITA -> Passo(CruzLogica.Tecla.BAIXO, true)
            CruzLogica.Tecla.OK -> Passo(CruzLogica.Tecla.OK, true)
            CruzLogica.Tecla.BAIXO, CruzLogica.Tecla.VOLTAR -> Passo(null, false)
            CruzLogica.Tecla.CIMA -> Passo(null, true, subirTopo = true)
            else -> Passo(null, true)
        }
    }

    /**
     * Primeiro app visível na linha (a linha rola para o escolhido ficar à vista, com [antes] apps à esquerda dele).
     * [n] apps, [cabem] na tela. Puro.
     */
    fun primeiro(sel: Int, n: Int, cabem: Int, antes: Int = 1): Int =
        (sel - antes).coerceAtMost(n - cabem).coerceAtLeast(0)
}
