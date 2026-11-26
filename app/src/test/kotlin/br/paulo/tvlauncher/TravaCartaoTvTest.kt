package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.TravaCartaoTv
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TravaCartaoTvTest {
    @Test fun appTravadoSoPassaDepoisDoPin() {
        assertTrue(TravaCartaoTv.barrar("com.netflix", setOf("com.netflix"), emptySet()))
        assertFalse(TravaCartaoTv.barrar("com.netflix", setOf("com.netflix"), setOf("com.netflix")))
        assertFalse(TravaCartaoTv.barrar("com.youtube", setOf("com.netflix"), emptySet()))
    }
}
