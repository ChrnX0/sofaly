package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.SofyFleet
import br.paulo.tvlauncher.observer.SofyRepair
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** O pacote do dia da rede de Sofys: só campos fixos, e nada da casa sai (Paulo, 27/11/2026). */
class SofyFleetTest {
    private val tv = SofyFleet.Device("Google", "Chromecast HD", googleTv = true, androidTv = true, sdk = 31, is64 = false, ramMb = 1536, diskGb = 6)
    private val noite = SofyFleet.State(uptimeMs = 5 * 3_600_000L, memoryLow = false, net = "wifi", hour = 21)

    /** Uma TV cheia de dados pessoais falsos: nenhum deles pode aparecer no pacote. */
    private val segredos = listOf("Ana Maria", "Jornal Nacional", "Aniversário", "Wi-Fi Casa Silva", "192.168.1.42", "rua das Flores",
        "ana@exemplo.com", "21:37", "Netflix: Round 6", "sala da Ana")

    private fun casoCheio() = SofyRepair.Case(
        key = "CRASH:app:com.globo.play", type = SofyRepair.Type.CRASH, subject = "app:com.globo.play", needs = setOf(SofyRepair.Need.NETWORK),
        firstAt = 1_000L, lastAt = 2_000L, count = 3,
        tried = listOf(SofyRepair.Attempt(SofyRepair.Tool.CLEAR_APP_CACHE, 1_500L, SofyRepair.Result.FIXED)),
        state = SofyRepair.State.FIXED,
        context = listOf("update.app_updated(Netflix: Round 6)", "house.calendar(Aniversário da Ana Maria)",
            "network.wifi_changed(Wi-Fi Casa Silva 192.168.1.42)", "laço: algo repetiu 3 vezes"),
        aiPlan = listOf(SofyRepair.Tool.CLEAR_APP_CACHE),
        detail = "Jornal Nacional travou na sala da Ana às 21:37, ana@exemplo.com, rua das Flores",
    )

    private fun pacote(vararg casos: SofyRepair.Case) =
        SofyFleet.packet("0123456789abcdef", 474900, tv, casos.toList(), emptyMap(), noite)

    @Test fun nadaDaCasaSai() {
        val txt = pacote(casoCheio()).toString()
        for (s in segredos) assertFalse("vazou \"$s\": $txt", txt.contains(s, ignoreCase = true))
        assertFalse(txt.contains("detail")); assertFalse(txt.contains("hour"))
    }

    @Test fun soCamposPermitidos() {
        val p = pacote(casoCheio())
        assertEquals(setOf("v", "code", "app", "device", "cases"), p.keySet())
        assertEquals(setOf("maker", "model", "platform", "sdk", "abi", "ram", "disk"), p.getJSONObject("device").keySet())
        val c = p.getJSONArray("cases").getJSONObject(0)
        assertEquals(setOf("type", "area", "target", "cause", "before", "state", "tries", "outcome", "relapse", "count"), c.keySet())
        assertEquals(setOf("uptime", "memory", "net", "period"), c.getJSONObject("state").keySet())
        assertEquals(setOf("tool", "by", "result"), c.getJSONArray("tries").getJSONObject(0).keySet())
    }

    @Test fun oCasoVemCertoEmFaixas() {
        val c = pacote(casoCheio()).getJSONArray("cases").getJSONObject(0)
        assertEquals("app", c.getString("area")); assertEquals("com.globo.play", c.getString("target"))
        assertEquals(listOf("app_updated", "network_changed", "other"), (0 until c.getJSONArray("before").length()).map { c.getJSONArray("before").getString(it) })
        assertEquals("evening", c.getJSONObject("state").getString("period"))
        assertEquals("1-24h", c.getJSONObject("state").getString("uptime"))
        assertEquals("ai", c.getJSONArray("tries").getJSONObject(0).getString("by"))
        assertEquals("fixed", c.getString("outcome"))
        val d = pacote().getJSONObject("device")
        assertEquals("1-2", d.getString("ram")); assertEquals("4-8", d.getString("disk")); assertEquals("32", d.getString("abi"))
        assertEquals("googletv", d.getString("platform"))
    }

    @Test fun alvoSoPacoteOuNomeDoCodigo() {
        assertEquals("mcp", SofyFleet.target("feature:mcp"))
        assertEquals("", SofyFleet.target("feature:Sala da Ana"))
        assertEquals("", SofyFleet.target("app:rm -rf"))
        assertEquals("", SofyFleet.target("shell"))
        assertEquals("other", SofyFleet.area("qualquer:coisa"))
        assertEquals("", SofyFleet.target("qualquer:coisa"))
    }

    @Test fun casoDeTesteNaoVai() {
        val teste = casoCheio().copy(detail = SofyRepair.TEST_MARK + " pelo adb")
        assertNull(SofyFleet.case(teste, noite))
        assertEquals(0, pacote(teste).getJSONArray("cases").length())
    }

    @Test fun codigoTrocaTodoDia() {
        val seg = "segredo-da-tv".toByteArray()
        val a = SofyFleet.dayCode(seg, 20_000); val b = SofyFleet.dayCode(seg, 20_001)
        assertTrue(Regex("^[0-9a-f]{16}$").matches(a)); assertNotEquals(a, b)
        assertEquals(a, SofyFleet.dayCode(seg, 20_000))
        assertNotEquals(a, SofyFleet.dayCode("outra-tv".toByteArray(), 20_000))
    }

    @Test fun aparelhoSoComCaracteresSimples() {
        val d = SofyFleet.device(tv.copy(maker = "Xiaomi™ <b>", model = ""))
        assertTrue(Regex("^[A-Za-z0-9 ._+-]{1,40}$").matches(d.getString("maker")))
        assertEquals("unknown", d.getString("model"))
    }

    /** As listas fechadas daqui e do servidor (server/api/src/fleet.js) têm de bater: senão o servidor recusa o pacote. */
    @Test fun listasIguaisAsDoServidor() {
        val js = listOf(File("../server/api/src/fleet.js"), File("server/api/src/fleet.js")).first { it.isFile }.readText()
        fun set(nome: String): Set<String> {
            val m = Regex("export const $nome = new Set\\(\\[([^\\]]*)\\]\\)").find(js) ?: error("$nome não achado")
            return Regex("'([^']+)'").findAll(m.groupValues[1]).map { it.groupValues[1] }.toSet()
        }
        assertEquals(SofyFleet.AREAS, set("AREAS"))
        assertEquals(SofyFleet.CAUSES, set("CAUSES"))
        assertEquals(SofyFleet.BEFORE, set("BEFORE"))
        assertEquals(SofyRepair.Type.entries.map { it.name }.toSet(), set("TYPES"))
        assertEquals(SofyRepair.Tool.entries.map { it.name }.toSet(), set("TOOLS"))
        // Os valores que o pacote usa estão todos nas listas do servidor.
        val c = pacote(casoCheio()).getJSONArray("cases").getJSONObject(0)
        assertTrue(c.getString("cause") in set("CAUSES"))
        val b: JSONArray = c.getJSONArray("before"); for (i in 0 until b.length()) assertTrue(b.getString(i) in set("BEFORE"))
        val d: JSONObject = pacote().getJSONObject("device")
        assertTrue(d.getString("ram") in set("RAM")); assertTrue(d.getString("disk") in set("DISK"))
    }
}
