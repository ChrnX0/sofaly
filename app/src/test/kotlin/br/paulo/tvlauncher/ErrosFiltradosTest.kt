package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.ErrosFiltrados
import br.paulo.tvlauncher.observer.RegistroErros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Auditoria 10/11/2026 (privilegios-5): a versão gravada pelo RegistroErros é "nome (código)" e precisa ser lida. */
class ErrosFiltradosTest {
    @Test fun versaoRealDoRegistro() {
        val t = RegistroErros.montarTexto(0L, "0.2 (12345)", "Android 14 · X", "main", RuntimeException("x"))
        val f = ErrosFiltrados.filtrar(t)
        assertEquals("0.2 (12345)", f.versao)
        assertEquals(12345L, ErrosFiltrados.codigo(f.versao))
    }

    @Test fun contaSoAMesmaBuild() {
        fun erro(v: String) = ErrosFiltrados.filtrar(RegistroErros.montarTexto(0L, v, "A", "main", RuntimeException("x")))
        val todos = listOf(erro("0.2 (100)"), erro("0.2 (100)"), erro("0.2 (99)"), erro("?"))
        assertEquals(2, ErrosFiltrados.nestaVersao(todos, 100L))
        assertNull(ErrosFiltrados.codigo("?"))
    }

    /** 23/11/2026: os frames do APK de Release (R8) têm hífen no arquivo e eram descartados. */
    @Test fun framesDoR8Ficam() {
        val t = """Erro: NullPointerException
            |	at v3.p.d(r8-map-id-35d4de9c982a39a91cf98339ed57d033a567109bd12191232708a4a3366ca9c1:65)
            |	at v3.l.run(r8-map-id-35d4de9c982a39a91cf98339ed57d033a567109bd12191232708a4a3366ca9c1:34)
            |	at android.os.Handler.handleCallback(Handler.java:958)""".trimMargin()
        val p = ErrosFiltrados.filtrar(t).pilha
        assertEquals(3, p.size)
        assertEquals("at v3.p.d(r8-map-id-35d4de9c982a39a91cf98339ed57d033a567109bd12191232708a4a3366ca9c1:65)", p[0])
    }
}
