package br.paulo.tvlauncher

import br.paulo.tvlauncher.weather.Lua
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class LuaTest {
    private val sp = TimeZone.getTimeZone("America/Sao_Paulo")

    // 17/11/2026 10:56 em São Paulo (13:56 UTC): a cheia cai em 24/11, faltando 7,1 dias -> "em 7 d" (antes, 8).
    @Test fun diasPeloCalendario() {
        val agora = 1_794_923_760_000L
        assertEquals(7, Lua.diasNoCalendario(agora, 7.1, sp))
        assertEquals(1, Lua.diasNoCalendario(agora, 0.2, sp))      // ainda hoje: mínimo 1
        assertEquals(1, Lua.diasNoCalendario(agora, 0.6, sp))      // passa da meia-noite: amanhã
        assertEquals(30 - 17, Lua.diasNoCalendario(agora, 12.9, sp))
    }

    // Fonte da idade: PyEphem 4.x (lua nova anterior 9/11/2026 07:02 UTC), reescalada ao ciclo médio como no Lua.idade.
    @Test fun idadeConhecida() {
        assertEquals(IDADE_17_11, Lua.idade(1_794_923_760_000L), 0.01)
    }

    /**
     * Sala, 25/11/2026: o protetor dizia lua nova 09/12 e cheia 24/12; o Paulo lembrava de 8/12. Fonte externa de
     * verdade: PyEphem 4.x (`ephem.next_new_moon`/`next_full_moon`), todas as fases de 2026 a 2030, em ms UTC.
     * Tolerância de 3 min: dia certo em qualquer fuso, salvo um evento a < 3 min da meia-noite.
     */
    @Test fun fasesBatemComOEphem() {
        var pior = 0L
        for (s in EPHEM) {
            val cheia = s[0] == 'C'; val real = s.substring(1).toLong()
            val calc = Lua.proxima(real - 3 * 86_400_000L, cheia)
            pior = maxOf(pior, kotlin.math.abs(calc - real))
            assertEquals("fase $s", real.toDouble(), calc.toDouble(), 180_000.0)
        }
        println("Lua: pior diferença contra o PyEphem = ${pior / 1000} s em ${EPHEM.size} fases")
    }

    // O caso da Sala: em 25/11/2026 06:38 BRT a próxima nova é 8/12 (21:51 BRT) e a cheia 23/12 (22:28 BRT).
    @Test fun proximasDaSalaNoFusoDeBrasilia() {
        val agora = 1_795_599_480_000L   // 25/11/2026 09:38 UTC
        val f = java.text.SimpleDateFormat("dd/MM").apply { timeZone = sp }
        assertEquals("08/12", f.format(java.util.Date(Lua.proxima(agora, cheia = false))))
        assertEquals("23/12", f.format(java.util.Date(Lua.proxima(agora, cheia = true))))
        assertEquals(13, Lua.diasNoCalendario(agora, (Lua.proxima(agora, false) - agora) / 86_400_000.0, sp))
    }

    private companion object {
        const val IDADE_17_11 = 8.2283
        val EPHEM = listOf(
            "C1767434570576", "N1768765914528", "C1769983750448", "N1771329664586", "C1772537869508", "N1773883404618",
            "C1775095914061", "N1776426704167", "C1777656186539", "N1778961658206", "C1780217107627", "N1781492044818",
            "C1782777395513", "N1784022211017", "C1785335737458", "N1786556199178", "C1787890706631", "N1789097215060",
            "C1790441337958", "N1791647401013", "C1792987904550", "N1794207723113", "C1795532009239", "N1796777506746",
            "C1798075689426", "N1799353458661", "C1800620238258", "N1801929362502", "C1803165814366", "N1804498164446",
            "C1805712223217", "N1807055465048", "C1808260024670", "N1809601112782", "C1810810735866", "N1812138015121",
            "C1813365854932", "N1814670118987", "C1815925488987", "N1817201108191", "C1818487715501", "N1819734065578",
            "C1821049406199", "N1822271759768", "C1823608015603", "N1824816988952", "C1826162750676", "N1827372261035",
            "C1828714122327", "N1829938334428", "C1831262579568", "N1832512345813", "C1833807820863", "N1835087840041",
            "C1836349559620", "N1837657874079", "C1838888791974", "N1840218410306", "C1841428130159", "N1842768972187",
            "C1843970921978", "N1845311246169", "C1846519841823", "N1847847695862", "C1849075782272", "N1850381024921",
            "C1851637649184", "N1852914219144", "C1854203093306", "N1855450602659", "C1856769435618", "N1857993475504",
            "C1859334008237", "N1860545173143", "C1861894105022", "N1863105864831", "C1864447410896", "N1865673085131",
            "C1866993009013", "N1868242749127", "C1869531978233", "N1870810803849", "C1872067001342", "N1873374121399",
            "C1874601442866", "N1875930625055", "C1877138532888", "N1878479455802", "C1879680937605", "N1881021339529",
            "C1882230665630", "N1883558655491", "C1884788951083", "N1886094866087", "C1887355647017", "N1888633440325",
            "C1889928171319", "N1891176720646", "C1892501184033", "N1893725366447", "C1895068455072", "N1896278843924",
            "C1897625981547", "N1898836472720", "C1900173382263", "N1901397744708", "C1902712793682", "N1903961520859",
            "C1905247140678", "N1906525271967", "C1907779252118", "N1909085659885", "C1910311910551", "N1911640251115",
            "C1912848255035", "N1914188832910", "C1915391866791", "N1916733272073", "C1917946002334", "N1919276211362",
            "C1920511810358", "N1921819581212", "C1923086419879", "N1924363925493", "C1925663142305",
        )
    }
}
