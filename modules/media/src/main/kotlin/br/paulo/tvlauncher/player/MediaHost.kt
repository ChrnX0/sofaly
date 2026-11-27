package br.paulo.tvlauncher.player

import android.content.Context

/**
 * O que o módulo :media precisa do app e não pode buscar sozinho (o :media não enxerga o :app). O LauncherApp preenche
 * na partida do processo. O Context para textos vem do AppContext, no :core.
 */
object MediaHost {
    /**
     * Fichas pelo TMDB (DataSources.tmdb: contrato, plano e chave, no app). Sem o app (teste, outro processo): false,
     * ou seja, as fichas vêm do Trakt, como no plano Básico.
     */
    @Volatile var tmdb: (Context) -> Boolean = { false }

    /** Nota do Simkl para quem não conectou a conta (DataSources.simklSemLogin: licença + plano). Sem o app: false. */
    @Volatile var simklSemLogin: (Context) -> Boolean = { false }

    /** Palavras do "Meu time" para separar as partidas ao baixar o guia (LembretesTv, no app). Sem o app: nenhuma. */
    @Volatile var guideWords: (Context) -> List<String> = { emptyList() }

    /** Guia novo gravado: os lembretes do "Meu time" se refazem com ele (LembretesTv.varrerDepois, no app). */
    @Volatile var onGuideUpdated: (Context) -> Unit = {}

    /** Ids do Simkl já sabidos pela biblioteca sincronizada da pessoa (ServicosSync, no app). Sem o app: nenhum. */
    @Volatile var simklLibraryIds: (Context) -> Map<String, Int> = { emptyMap() }
}
