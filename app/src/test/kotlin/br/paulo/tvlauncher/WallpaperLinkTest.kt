package br.paulo.tvlauncher

import br.paulo.tvlauncher.home.WallpaperLink
import org.junit.Assert.assertEquals
import org.junit.Test

/** "Meu fundo" por link: a extensão sai só do último trecho do caminho (revisão de 24/11/2026). */
class WallpaperLinkTest {
    @Test fun extensaoPeloUltimoTrecho() {
        assertEquals("jpg", WallpaperLink.extensao("https://images.unsplash.com/photo-123?w=1920"))
        assertEquals("png", WallpaperLink.extensao("https://x.com/a/b.PNG"))
        assertEquals("jpg", WallpaperLink.extensao("https://x.com/fotos/praia.jpeg#topo"))
        assertEquals("webp", WallpaperLink.extensao("https://cdn.x.com/a.b/c.webp?v=2"))
        assertEquals("jpg", WallpaperLink.extensao("https://x.com"))
        assertEquals("jpg", WallpaperLink.extensao("https://x.com/a.b/arquivo.exe"))
    }
}
