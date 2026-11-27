package br.paulo.tvlauncher.parts

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PartsPlanTest {
    private val sha = "a".repeat(64)

    private fun entry(name: String, split: String, abi: String = "", size: Long = 1000) =
        JSONObject().put("name", name).put("split", split).put("abi", abi).put("sha256", sha).put("size", size)

    private fun full(): JSONObject {
        val arr = JSONArray()
        for (s in listOf("base", "translate", "tunnel")) {
            arr.put(entry("part-$s.apk", s))
            arr.put(entry("part-$s-armeabi_v7a.apk", s, "armeabi-v7a"))
            arr.put(entry("part-$s-arm64_v8a.apk", s, "arm64-v8a"))
        }
        return JSONObject().put("versionCode", 476305).put("files", arr)
    }

    @Test fun readsTheIndexTheWorkflowWrites() {
        val i = PartsPlan.parse(full().toString())!!
        assertEquals(476305L, i.versionCode)
        assertEquals(9, i.files.size)
    }

    @Test fun baseAloneIsMasterPlusAbi() {
        val i = PartsPlan.parse(full().toString())!!
        assertEquals(listOf("part-base.apk", "part-base-armeabi_v7a.apk"),
            PartsPlan.files(i, "armeabi-v7a", emptySet())!!.map { it.name })
    }

    @Test fun partsComeAfterTheBaseInAFixedOrder() {
        val i = PartsPlan.parse(full().toString())!!
        assertEquals(listOf("part-base.apk", "part-base-arm64_v8a.apk", "part-translate.apk", "part-translate-arm64_v8a.apk",
            "part-tunnel.apk", "part-tunnel-arm64_v8a.apk"),
            PartsPlan.files(i, "arm64-v8a", setOf("tunnel", "translate"))!!.map { it.name })
    }

    @Test fun missingFileMeansNoPlan() {
        val o = full()
        val arr = o.getJSONArray("files")
        val sem = JSONArray((0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.getString("name") != "part-tunnel-armeabi_v7a.apk" })
        val i = PartsPlan.parse(o.put("files", sem).toString())!!
        assertNull(PartsPlan.files(i, "armeabi-v7a", setOf("tunnel")))
        assertNotNull(PartsPlan.files(i, "armeabi-v7a", setOf("translate")))
        assertNotNull(PartsPlan.files(i, "arm64-v8a", setOf("tunnel")))
    }

    @Test fun unknownPartOrAbiMeansNoPlan() {
        val i = PartsPlan.parse(full().toString())!!
        assertNull(PartsPlan.files(i, "x86_64", emptySet()))
        assertNull(PartsPlan.files(i, "armeabi-v7a", setOf("ffmpeg")))
    }

    @Test fun rejectsStrangeEntries() {
        fun com(e: JSONObject) = PartsPlan.parse(full().apply { getJSONArray("files").put(e) }.toString())
        assertNull(com(entry("../sofaly-launcher.apk", "base", "x")))
        assertNull(com(entry("sofaly-launcher.apk", "base")))         // nome do APK cheio: nunca é parte
        assertNull(com(entry("part-x.apk", "ffmpeg")))
        assertNull(com(entry("part-x.apk", "base", "x86_64")))
        assertNull(com(entry("part-x.apk", "base", "", 0)))
        assertNull(com(entry("part-x.apk", "base", "", PartsPlan.MAX_SIZE + 1)))
        assertNull(com(entry("part-base.apk", "translate")))           // nome repetido
        assertNull(com(entry("part-y.apk", "base")))                   // base comum repetida
        assertNull(com(entry("part-x.apk", "base", "").put("sha256", "zz")))
        assertNull(PartsPlan.parse("{}"))
        assertNull(PartsPlan.parse("lixo"))
        assertNull(PartsPlan.parse(JSONObject().put("versionCode", 1).put("files", JSONArray()).toString()))
    }

    @Test fun keepsInstalledPartsAndAddsThoseInUse() {
        assertEquals(setOf("translate"), PartsPlan.keep(listOf("config.arm64_v8a", "translate", "translate.config.arm64_v8a"), emptySet()))
        assertEquals(setOf("translate", "tunnel"), PartsPlan.keep(listOf("translate"), setOf("tunnel")))
        assertEquals(setOf("tunnel"), PartsPlan.keep(emptyList(), setOf("tunnel", "base", "ffmpeg")))
        assertEquals(emptySet<String>(), PartsPlan.keep(emptyList(), emptySet()))
    }

    /** parts.json de verdade, saído de scripts/parts-publish.py (build local de 27/11/2026). */
    @Test fun readsTheRealIndexFromTheScript() {
        val texto = javaClass.getResourceAsStream("/parts/parts.json")!!.bufferedReader().readText()
        val i = PartsPlan.parse(texto)!!
        assertEquals(9, i.files.size)
        val base32 = PartsPlan.files(i, "armeabi-v7a", emptySet())!!
        assertEquals(listOf("part-base.apk", "part-base-armeabi_v7a.apk"), base32.map { it.name })
        assertEquals(29.0, base32.sumOf { it.size } / 1048576.0, 0.05)
        assertEquals(6, PartsPlan.files(i, "arm64-v8a", setOf("translate", "tunnel"))!!.size)
    }

    @Test fun abiNames() {
        assertEquals("arm64-v8a", PartsPlan.abi(true))
        assertEquals("armeabi-v7a", PartsPlan.abi(false))
    }
}
