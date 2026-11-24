package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.AppProfile
import br.paulo.tvlauncher.observer.SofyShare
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SofyShareTest {
    private val now = 1_800_000_000_000L
    private val host: (String) -> String? = { h -> h.takeIf { it.matches(Regex("^[a-z0-9-]+$")) } }
    private val nonce = "0123456789abcdef0123456789abcdef"

    @Test fun licoesDestaTv() {
        val trava = AppProfile.Card("com.app.x", now - 9 * 86_400_000L, ver = 50, crashes = listOf(now - 1000, now - 2000, now - 3000))
        val lenta = AppProfile.Card("com.app.y", now - 9 * 86_400_000L, ver = 7, startsMs = listOf(3000, 3100, 2900), startsBefore = 1500)
        val ok = AppProfile.Card("com.app.z", now - 9 * 86_400_000L, ver = 3, startsMs = listOf(1600, 1500, 1550), startsBefore = 1500)
        val semVersao = AppProfile.Card("com.app.w", now, crashes = listOf(now, now, now))
        assertEquals(listOf(SofyShare.Lesson("com.app.x", 50, "crashy"), SofyShare.Lesson("com.app.y", 7, "slower")),
            SofyShare.lessons(listOf(trava, lenta, ok, semVersao), now))
    }

    @Test fun pacoteIdaEVolta() {
        val p = SofyShare.Packet("onetv-quarto", nonce, now, listOf(SofyShare.Lesson("com.app.x", 50, "crashy")))
        assertEquals(p, SofyShare.fromJson(JSONObject(SofyShare.toJson(p).toString()), now + 60_000, host))
        assertNull(SofyShare.fromJson(SofyShare.toJson(p), now + 11 * 60_000L, host))                         // velho
        assertNull(SofyShare.fromJson(SofyShare.toJson(p.copy(nonce = "abc")), now, host))                    // nonce ruim
        assertNull(SofyShare.fromJson(SofyShare.toJson(p.copy(from = "Quarto Do Pai")), now, host))           // host ruim
        assertNull(SofyShare.fromJson(SofyShare.toJson(p.copy(lessons = listOf(SofyShare.Lesson("com.app.x", 50, "apagar")))), now, host))
        assertNull(SofyShare.fromJson(SofyShare.toJson(p.copy(lessons = listOf(SofyShare.Lesson("rm -rf", 50, "crashy")))), now, host))
        assertNull(SofyShare.fromJson(SofyShare.toJson(p.copy(lessons = List(31) { SofyShare.Lesson("com.a.b$it", 1, "crashy") })), now, host))
        assertNotNull(SofyShare.fromJson(SofyShare.toJson(p.copy(lessons = emptyList())), now, host))
    }

    @Test fun soOQueValeAqui() {
        val l = listOf(SofyShare.Lesson("com.app.x", 50, "crashy"), SofyShare.Lesson("com.app.y", 7, "slower"), SofyShare.Lesson("com.app.z", 1, "crashy"))
        val instalados = mapOf("com.app.x" to 50L, "com.app.y" to 8L)   // y em outra versão; z não instalado
        assertEquals(listOf(l[0]), SofyShare.relevant(l, instalados, emptySet()))
        assertEquals(emptyList<SofyShare.Lesson>(), SofyShare.relevant(l, instalados, setOf(SofyShare.key(l[0]))))   // já contou
    }
}
