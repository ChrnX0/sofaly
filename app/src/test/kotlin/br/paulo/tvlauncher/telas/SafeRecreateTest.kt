package br.paulo.tvlauncher.telas

import android.app.Activity
import br.paulo.tvlauncher.recreateOnce
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** #619: dois pedidos de recriar a mesma tela viram um só (o segundo derrubava o app em findViewLocked). Só com -Ptelas. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class SafeRecreateTest {
    class Contadora : Activity() {
        var vezes = 0
        override fun recreate() { vezes++ }   // conta em vez de recriar: o que importa é quantos pedidos passaram
    }

    @Test fun doisPedidosSeguidosViramUm() {
        val a = Robolectric.buildActivity(Contadora::class.java).setup().get()
        a.recreateOnce()
        a.recreateOnce()
        assertEquals(1, a.vezes)
        val outra = Robolectric.buildActivity(Contadora::class.java).setup().get()
        outra.recreateOnce()
        assertEquals("a instância nova começa livre", 1, outra.vezes)
        val fechando = Robolectric.buildActivity(Contadora::class.java).setup().get().apply { finish() }
        fechando.recreateOnce()
        assertEquals("tela fechando não recria", 0, fechando.vezes)
    }
}
