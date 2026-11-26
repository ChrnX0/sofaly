package br.paulo.tvlauncher.home

import android.content.Context
import br.paulo.tvlauncher.settings.Extras
import br.paulo.tvlauncher.settings.FocusStyle

/**
 * Visuais prontos da barra única (Paulo, 25/11/2026: "a gente tem opção pra caramba"; onda B da simplificação).
 *
 * Porquê: a cara da HOME tinha uns 15 botões soltos (foto, formato, tamanho, vão, cantos, placa, nome, card nosso,
 * personalidade, animação, foco...). Quem só quer "uma TV bonita" não sabe por onde começar.
 * Para quê: 5 conjuntos prontos aparecem PRIMEIRO; um OK troca tudo junto e a HOME atrás do painel muda na hora. Os
 * botões soltos continuam no Ajuste fino, para quem quer mexer peça por peça.
 * Como: cada visual é só uma lista "chave -> número" das mesmas preferências que o Ajuste fino já grava (nada novo para
 * guardar, nada novo no backup). [foto] guarda como estava antes do primeiro OK; "Como estava" volta exatamente a ela.
 *
 * Referências: Mobbin (Muse e stoic.: temas prontos em cartões com prévia; Joi: um toque aplica, desfazer ao lado),
 * Projectivy (temas da home) e Apple TV (fundos prontos). Lógica pura em [Rules] (HomeLooksTest).
 */
object HomeLooks {
    enum class Look(val valores: Map<String, Int>) {
        /** O de fábrica: Vale, cards em vidro escuro com brilho da cor, cena no ícone do mundo. */
        SOFALY(mapOf(
            StripBackground.KEY to StripBackground.Choice.VALE.ordinal,
            StripHome.KEY_FORMAT to StripHome.Format.WIDE.ordinal, StripHome.KEY_SIZE to StripHome.Size.SMALL.ordinal,
            StripHome.KEY_GAP to StripHome.Gap.WIDE.ordinal, StripHome.KEY_CORNERS to StripHome.Corners.ROUND.ordinal,
            StripHome.KEY_GLASS to 0, StripHome.KEY_NAME to 0,
            StripHome.KEY_OWN to StripHome.Own.DARK.ordinal, StripHome.KEY_MOOD to StripHome.Mood.GLOW.ordinal,
            StripHome.KEY_WORLD_ANIM to StripHome.WorldAnim.SCENE.ordinal, FocusStyle.KEY to FocusStyle.RING.ordinal)),
        /** Noite: céu escuro, cards de vidro sobre uma placa, cor própria nos ícones, luz calma no mundo. */
        NIGHT(mapOf(
            StripBackground.KEY to StripBackground.Choice.NOITE.ordinal,
            StripHome.KEY_FORMAT to StripHome.Format.WIDE.ordinal, StripHome.KEY_SIZE to StripHome.Size.SMALL.ordinal,
            StripHome.KEY_GAP to StripHome.Gap.NORMAL.ordinal, StripHome.KEY_CORNERS to StripHome.Corners.SOFT.ordinal,
            StripHome.KEY_GLASS to 1, StripHome.KEY_NAME to 0,
            StripHome.KEY_OWN to StripHome.Own.GLASS.ordinal, StripHome.KEY_MOOD to StripHome.Mood.COLOR.ordinal,
            StripHome.KEY_WORLD_ANIM to StripHome.WorldAnim.LIGHT.ordinal, FocusStyle.KEY to FocusStyle.RING.ordinal)),
        /** Serra: foto clara, cards brancos 4:3 em azulejo, o ícone do mundo pula. */
        MOUNTAIN(mapOf(
            StripBackground.KEY to StripBackground.Choice.SERRA.ordinal,
            StripHome.KEY_FORMAT to StripHome.Format.CLASSIC.ordinal, StripHome.KEY_SIZE to StripHome.Size.SMALL.ordinal,
            StripHome.KEY_GAP to StripHome.Gap.NORMAL.ordinal, StripHome.KEY_CORNERS to StripHome.Corners.ROUND.ordinal,
            StripHome.KEY_GLASS to 0, StripHome.KEY_NAME to 0,
            StripHome.KEY_OWN to StripHome.Own.WHITE.ordinal, StripHome.KEY_MOOD to StripHome.Mood.TILE.ordinal,
            StripHome.KEY_WORLD_ANIM to StripHome.WorldAnim.BOUNCE.ordinal, FocusStyle.KEY to FocusStyle.RING.ordinal)),
        /** Fácil de ver: cards grandes com o nome no foco, foco em quadro branco, movimento calmo. */
        EASY(mapOf(
            StripBackground.KEY to StripBackground.Choice.VALE.ordinal,
            StripHome.KEY_FORMAT to StripHome.Format.WIDE.ordinal, StripHome.KEY_SIZE to StripHome.Size.LARGE.ordinal,
            StripHome.KEY_GAP to StripHome.Gap.NORMAL.ordinal, StripHome.KEY_CORNERS to StripHome.Corners.SOFT.ordinal,
            StripHome.KEY_GLASS to 1, StripHome.KEY_NAME to 1,
            StripHome.KEY_OWN to StripHome.Own.DARK.ordinal, StripHome.KEY_MOOD to StripHome.Mood.COLOR.ordinal,
            StripHome.KEY_WORLD_ANIM to StripHome.WorldAnim.LIGHT.ordinal, FocusStyle.KEY to FocusStyle.BOX.ordinal)),
        /** Mínimo: o fundo do tema (sem foto), logos soltos com o nome no foco, quase nada se mexe. */
        MINIMAL(mapOf(
            StripBackground.KEY to StripBackground.Choice.THEME.ordinal,
            StripHome.KEY_FORMAT to StripHome.Format.LOGO.ordinal, StripHome.KEY_SIZE to StripHome.Size.SMALL.ordinal,
            StripHome.KEY_GAP to StripHome.Gap.WIDE.ordinal, StripHome.KEY_CORNERS to StripHome.Corners.ROUND.ordinal,
            StripHome.KEY_GLASS to 0, StripHome.KEY_NAME to 1,
            StripHome.KEY_OWN to StripHome.Own.DARK.ordinal, StripHome.KEY_MOOD to StripHome.Mood.COLOR.ordinal,
            StripHome.KEY_WORLD_ANIM to StripHome.WorldAnim.LIGHT.ordinal, FocusStyle.KEY to FocusStyle.LINE.ordinal));

        /** A foto de fundo deste visual (null = o fundo do tema). */
        val foto: StripBackground.Choice get() = StripBackground.resolve(valores.getValue(StripBackground.KEY))
        val cards: StripHome.Own get() = StripHome.Own.entries[valores.getValue(StripHome.KEY_OWN)]
    }

    /** Todas as chaves que um visual mexe (as mesmas em todos). */
    val CHAVES: Set<String> = Look.entries.flatMap { it.valores.keys }.toSet()

    /** Lógica pura (sem Android). [ler] devolve o número gravado de uma chave, ou null se ela nunca foi gravada. */
    object Rules {
        /**
         * Qual visual a TV está usando agora, ou null se a pessoa mexeu peça por peça. Chave nunca gravada vale o padrão
         * de fábrica, que é o [Look.SOFALY]: uma TV nova aparece como "Sofaly".
         */
        fun atual(ler: (String) -> Int?): Look? = Look.entries.firstOrNull { l ->
            l.valores.all { (k, v) -> (ler(k) ?: Look.SOFALY.valores.getValue(k)) == v }
        }

        /** A foto de como está agora (null = a chave nunca foi gravada), para o "Como estava" devolver igualzinho. */
        fun foto(ler: (String) -> Int?): Map<String, Int?> = CHAVES.associateWith { ler(it) }
    }

    private fun ler(ctx: Context): (String) -> Int? = { k -> Extras.numero(ctx, k, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } }

    fun atual(ctx: Context): Look? = Rules.atual(ler(ctx))
    fun foto(ctx: Context): Map<String, Int?> = Rules.foto(ler(ctx))

    fun aplicar(ctx: Context, l: Look) {
        for ((k, v) in l.valores) Extras.definirNumero(ctx, k, v)
        FocusStyle.ler(ctx)   // as cores "no foco" do Estilo leem a escolha guardada em memória
    }

    /** Volta à [foto]: chave que não existia volta a não existir (vale o padrão de fábrica de novo). */
    fun voltar(ctx: Context, foto: Map<String, Int?>) {
        for ((k, v) in foto) if (v == null) Extras.apagar(ctx, k) else Extras.definirNumero(ctx, k, v)
        FocusStyle.ler(ctx)
    }
}
