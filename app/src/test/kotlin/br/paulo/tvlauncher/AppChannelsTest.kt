package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.AppChannels
import org.junit.Assert.assertEquals
import org.junit.Test

class AppChannelsTest {
    private fun c(id: Long, p: String) = AppChannels.Card(id, p, "", "t$id", "", null, null)

    @Test fun intercalaUmDeCadaAppPorVez() {
        val l = listOf(c(1, "prime"), c(2, "prime"), c(3, "prime"), c(4, "yt"), c(5, "yt"), c(6, "max"))
        assertEquals(listOf(1L, 4L, 6L, 2L, 5L, 3L), AppChannels.intercalar(l, 10).map { it.id })
        assertEquals(listOf(1L, 4L, 6L, 2L), AppChannels.intercalar(l, 4).map { it.id })
        assertEquals(emptyList<Long>(), AppChannels.intercalar(emptyList(), 6).map { it.id })
    }

    @Test fun doAppSoOsDele() {
        val l = listOf(c(1, "prime"), c(2, "yt"), c(3, "prime"))
        assertEquals(listOf(1L, 3L), AppChannels.doApp(l, "prime").map { it.id })
    }
}
