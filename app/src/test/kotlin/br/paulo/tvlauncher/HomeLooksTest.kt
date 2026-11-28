package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.HomeLooks
import br.paulo.tvlauncher.home.HomeLooks.Look
import br.paulo.tvlauncher.home.StripBackground
import br.paulo.tvlauncher.home.StripHome
import br.paulo.tvlauncher.settings.FocusStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Visuais prontos (onda B, 25/11/2026): o de fábrica bate com os padrões de cada peça, e o "Como estava" volta igual. */
class HomeLooksTest {
    @Test fun tvNovaApareceComoSofaly() = assertEquals(Look.SOFALY, HomeLooks.Rules.atual { null })

    @Test fun sofalyEhOPadraoDeCadaPeca() {
        val d = StripHome.DEFAULT
        val v = Look.SOFALY.valores
        assertEquals(StripBackground.resolve(-1).ordinal, v[StripBackground.KEY])
        assertEquals(d.format.ordinal, v[StripHome.KEY_FORMAT]); assertEquals(d.size.ordinal, v[StripHome.KEY_SIZE])
        assertEquals(d.gap.ordinal, v[StripHome.KEY_GAP]); assertEquals(d.corners.ordinal, v[StripHome.KEY_CORNERS])
        assertEquals(if (d.glass) 1 else 0, v[StripHome.KEY_GLASS]); assertEquals(if (d.name) 1 else 0, v[StripHome.KEY_NAME])
        assertEquals(d.own.ordinal, v[StripHome.KEY_OWN]); assertEquals(d.mood.ordinal, v[StripHome.KEY_MOOD])
        assertEquals(d.worldAnim.ordinal, v[StripHome.KEY_WORLD_ANIM])
        // O foco sem escolha agora vem do tema do sistema (SystemLook, 28/11/2026); o visual Sofaly da cruz segue com o anel.
        assertEquals(FocusStyle.RING.ordinal, v[FocusStyle.KEY])
    }

    @Test fun cadaVisualMexeNasMesmasChavesEEhDiferente() {
        for (l in Look.entries) assertEquals(l.name, HomeLooks.CHAVES, l.valores.keys)
        assertEquals(Look.entries.size, Look.entries.map { it.valores }.toSet().size)
    }

    @Test fun reconheceCadaVisualGravado() {
        for (l in Look.entries) assertEquals(l, HomeLooks.Rules.atual { l.valores[it] })
    }

    @Test fun umaPecaMexidaAMaoNaoEhNenhumVisual() {
        val v = Look.NIGHT.valores + (StripHome.KEY_SIZE to StripHome.Size.LARGE.ordinal)
        assertNull(HomeLooks.Rules.atual { v[it] })
    }

    @Test fun fotoGuardaChaveQueNuncaFoiGravada() {
        val gravado = mapOf(StripHome.KEY_OWN to 1)
        val f = HomeLooks.Rules.foto { gravado[it] }
        assertEquals(1, f[StripHome.KEY_OWN]); assertNull(f[StripBackground.KEY]); assertEquals(HomeLooks.CHAVES, f.keys)
    }
}
