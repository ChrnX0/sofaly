package br.paulo.tvlauncher

import br.paulo.tvlauncher.player.Vod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Nota da lista (teste 100%, 24/11/2026): "Noturno ★ 10,0" na lista e na HOME, ★ 7,6 na ficha. */
class NotaDaListaTest {
    @Test fun dezRedondoNaoConta() {
        assertNull(Vod.notaDaLista(10.0))
        assertNull(Vod.notaDaLista(0.0))
        assertNull(Vod.notaDaLista(null))
    }

    @Test fun notaNormalFica() {
        assertEquals(7.6f, Vod.notaDaLista(7.6)!!, 0.001f)
        assertEquals(9.9f, Vod.notaDaLista(9.9)!!, 0.001f)
    }
}
