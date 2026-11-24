package br.paulo.tvlauncher.telas

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.player.Canal
import br.paulo.tvlauncher.player.Guia
import br.paulo.tvlauncher.player.PlayerListaTela
import br.paulo.tvlauncher.player.Programa
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/**
 * A tela de canais da TV com o guia DE VERDADE da Sala (24/11/2026). O Paulo viu a lista "DEPOIS" cortada ao meio e o
 * nome do programa reduzido a "A Thou…" ao lado de "falta 1 h 4 min", depois de o teste dizer que estava tudo certo:
 * o teste antigo nem desenhava esta tela. Aqui:
 *  - nenhum texto cortado na vertical (a não ser linhas de lista que rolam);
 *  - na lista de canais, o nome do programa não leva reticências quando cabe na largura toda;
 *  - "DEPOIS" só aparece com linhas inteiras, em ordem de horário.
 * A sinopse é o único texto que pode terminar em "…": ela é longa por natureza e a ficha não tem para onde crescer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class PlayerTvTelaTest {

    @Config(qualifiers = "pt-w960dp-h540dp-land-television-xhdpi") @Test fun pt() = desenhar("tv-canais-pt")
    @Config(qualifiers = "en-w960dp-h540dp-land-television-xhdpi") @Test fun en() = desenhar("tv-canais-en")
    @Config(qualifiers = "de-w960dp-h540dp-land-television-xhdpi") @Test fun de() = desenhar("tv-canais-de")
    @Config(qualifiers = "ar-w960dp-h540dp-land-television-xhdpi") @Test fun ar() = desenhar("tv-canais-ar")

    private fun desenhar(arquivo: String) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val agora = System.currentTimeMillis()
        val min = 60_000L
        fun p(id: String, ini: Long, fim: Long, t: String, d: String = "", c: String = "") = Programa(id, agora + ini * min, agora + fim * min, t, d, c)
        val sinopse = "Após um encontro com um guru espiritual, Jack McCall encontra uma misteriosa árvore no seu jardim. Ele descobre " +
            "que a cada nova palavra que pronuncia, uma folha cai e a árvore fica mais próxima do fim, o que poderá causar a sua própria morte."
        val programas = listOf(
            p("globo", -36, 64, "A Thousand Words", sinopse, "Filme"),
            p("globo", 64, 104, "Vale a Pena Ver de Novo - A Dona do Pedaço"),
            p("globo", 104, 144, "Vale a Pena Ver de Novo - Avenida Brasil"),
            p("globo", 144, 184, "Lá Na Minha Terra"),
            p("band", -31, 89, "Brasil Urgente"),
            p("aswim", -43, 17, "Justiça Jovem"),
            p("tnt", -49, 11, "Iludida"),
            p("jogos", -59, 1, "No Mundo das Nuvens"),
            p("disc", -37, 23, "Sabores da América: Bacon"),
        )
        val canais = listOf(
            Canal("globo", "Globo SP", 2, "Abertos", url = "http://x/1", guiaId = "globo"),
            Canal("band", "Band SP", 5, "Abertos", url = "http://x/2", guiaId = "band"),
            Canal("aswim", "Adult Swim", 12, "Abertos", url = "http://x/3", guiaId = "aswim"),
            Canal("tnt", "TNT Novelas", 9, "Abertos", url = "http://x/4", guiaId = "tnt"),
            Canal("jogos", "Jogos de hoje HD", 1, "Abertos", url = "http://x/5", guiaId = "jogos"),
            Canal("pato", "[24H] Pato Donald", 621, "Abertos", url = "http://x/6"),
            Canal("disc", "Discovery World", 156, "Abertos", url = "http://x/7", guiaId = "disc"),
        )
        val tela = PlayerListaTela(a, { _, _ -> }, {}, {}, {}, {}, { _, _ -> })
        a.setContentView(tela.raiz)
        tela.abrir(canais, Guia(programas), emptySet(), emptyList(), "g:Abertos", canais[0])
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        val raiz = a.window.decorView
        raiz.requestLayout(); raiz.measure(View.MeasureSpec.makeMeasureSpec(raiz.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(raiz.height, View.MeasureSpec.EXACTLY))
        raiz.layout(0, 0, raiz.width, raiz.height)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        val problemas = MedirTela.medir(a, arquivo, arquivo, 18f, cartao = false).filterNot {
            // A sinopse longa termina em "…" de propósito (ver o KDoc).
            it.contains("Após um encontro") || it.contains("nada tem o foco")
        }.toMutableList()
        // Na lista de canais, o programa de agora tem de caber inteiro (os títulos acima são curtos o bastante).
        val textos = mutableListOf<TextView>()
        fun andar(v: View) { if (v.visibility != View.VISIBLE) return; if (v is TextView) textos += v; if (v is ViewGroup) for (i in 0 until v.childCount) andar(v.getChildAt(i)) }
        andar(raiz)
        // "DEPOIS": linhas em ordem de horário, nenhuma cortada.
        val horas = textos.map { it.text.toString() }.mapNotNull { Regex("^(\\p{Nd}\\p{Nd}:\\p{Nd}\\p{Nd})\\s{3}").find(it)?.groupValues?.get(1) }
        if (horas != horas.sorted()) problemas += "$arquivo: DEPOIS fora de ordem: $horas"
        if (horas.isEmpty()) problemas += "$arquivo: DEPOIS sem nenhum programa (o espaço não deu nem para uma linha)"
        assertTrue(problemas.joinToString("\n"), problemas.isEmpty())
    }
}
