package br.paulo.tvlauncher

import android.content.ComponentCallbacks2

/**
 * Quando soltar memória (12/11/2026, Sala com 2 GB e a ZRAM cheia). Puro: teste JVM em MemoriaTest.
 * Os números são as constantes do ComponentCallbacks2 (RUNNING_MODERATE 5, RUNNING_LOW 10, RUNNING_CRITICAL 15,
 * UI_HIDDEN 20, BACKGROUND 40, MODERATE 60, COMPLETE 80).
 */
object Memoria {
    /** Caches que se refazem do disco (logos e pôsteres): soltar já no "apertado" com o app na frente. */
    fun soltarCaches(nivel: Int): Boolean =
        nivel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && nivel != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            nivel >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND

    /** Capas da HOME: só com a HOME fora da tela; com ela na frente, soltar faria a grade piscar. */
    fun soltarCapasDaHome(nivel: Int, homeNaFrente: Boolean): Boolean = !homeNaFrente && soltarCaches(nivel)

    /**
     * GPU da HOME (refatoração G, etapa 4b, 27/11/2026). A acessibilidade deixa o processo em primeiro plano
     * (procstate 6) o tempo todo, e o Android só manda TRIM_MEMORY_UI_HIDDEN a um processo que saiu dele: a HOME nunca
     * recebia o aviso, nem com outro app na frente nem com a tela apagada (o próprio `am send-trim-memory` recusa:
     * "foreground process"), e o cache de texturas do HWUI ficava inteiro (28 MB de GL na Sala dormindo). [GpuTrim]
     * faz o que o Android faria. O HWUI só reconhece estes números EXATOS (CacheManager::trimMemory):
     * 20 = solta o que não está em uso; 40 = solta tudo e o contexto de GPU (refeito no primeiro quadro ao acordar).
     */
    const val GPU_HOME_ESCONDIDA = ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
    const val GPU_TELA_APAGADA = ComponentCallbacks2.TRIM_MEMORY_BACKGROUND

    /** Espera depois de a tela apagar: um apagar e acender rápido (CEC, engano no controle) não refaz o contexto. */
    const val GPU_ESPERA_APAGADA_MS = 30_000L
}
