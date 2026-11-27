package br.paulo.tvlauncher.telas

import android.app.Activity
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import br.paulo.tvlauncher.MainActivity
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/**
 * Submenus (auditoria visual de 27/11/2026, Paulo: "SÃO TODAS AS TELAS E MENUS E SUBMENUS"). A [AuditoriaTelasTest]
 * fotografa cada tela como ela abre; esta entra nela: para cada elemento com foco das telas de [TELAS], abre a tela de
 * novo, põe o foco nele (abas laterais trocam o conteúdo só com o foco) e aperta OK. Fotografa o que mudou: a tela com
 * a aba nova, a janela que abriu por cima (Dialog) ou anota a outra tela que ela abriria (essa já está na auditoria).
 *
 * Só gera fotos para um olho humano conferir (grafite sem azul, sem rosa, letra grande, foco no padrão, sem dica de
 * tecla); não reprova nada. Grava em app/build/telas/submenus/ e um índice submenus-<idioma>.md.
 * Roda com: ./gradlew :app:testReleaseUnitTest -Ptelas -Psubmenus --tests '*SubmenusTelasTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class SubmenusTelasTest {
    private val saida = File("build/telas/submenus")

    @Config(qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
    @Test fun portugues() = andar("pt")

    private fun andar(idioma: String) {
        assumeTrue(System.getProperty("telas.submenus") == "true")
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        saida.mkdirs()
        val app = RuntimeEnvironment.getApplication()
        br.paulo.tvlauncher.home.SplashOverlay.setEnabled(app, false)
        // Sem rede: nada de servidor de fora no meio da foto.
        System.setProperty("https.proxyHost", "127.0.0.1"); System.setProperty("https.proxyPort", "9")
        System.setProperty("http.proxyHost", "127.0.0.1"); System.setProperty("http.proxyPort", "9")
        val filtro = System.getProperty("telas.submenus.so").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val porNome = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES).activities.orEmpty()
            .associate { it.name.substringAfterLast('.') to it.name }
        val md = StringBuilder("# Submenus ($idioma)\n\n")
        for (curto in TELAS.filter { filtro.isEmpty() || it in filtro }) {
            val nome = porNome[curto] ?: run { md.append("## $curto\n- não está no manifesto\n\n"); null } ?: continue
            @Suppress("UNCHECKED_CAST") val cls = Class.forName(nome) as Class<out Activity>
            md.append("## $curto\n")
            val vistas = mutableSetOf<Long>()
            val total = try {
                val a = abrir(cls)
                val n = focaveis(a).size
                guardar(a.window.decorView, "$idioma-$curto-00-abre", vistas)
                a.finish(); n
            } catch (e: Throwable) { md.append("- não abriu: ${e.javaClass.simpleName}\n\n"); continue }
            md.append("$total elementos com foco\n\n")
            for (i in 0 until minOf(total, MAX)) {
                try {
                    ShadowDialog.reset()
                    val a = abrir(cls)
                    val alvos = focaveis(a)
                    val v = alvos.getOrNull(i) ?: run { a.finish(); null } ?: continue
                    val rot = rotulo(v)
                    val num = "%02d".format(i + 1)
                    v.requestFocus(); idle()
                    val fFoco = guardar(a.window.decorView, "$idioma-$curto-$num-foco", vistas)
                    shadowOf(a).nextStartedActivity   // esquece o que a tela abriu ao nascer
                    v.performClick(); idle()
                    val outra = shadowOf(a).nextStartedActivity
                    val dlg: Dialog? = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
                    val fClique = when {
                        dlg != null -> guardar(dlg.window!!.decorView, "$idioma-$curto-$num-janela", vistas, a.window.decorView)
                        outra != null -> null
                        else -> guardar(a.window.decorView, "$idioma-$curto-$num-ok", vistas)
                    }
                    md.append("- $num \"$rot\"")
                    if (fFoco != null) md.append(" · foco: $fFoco")
                    if (outra != null) md.append(" · abre ${outra.component?.shortClassName ?: outra.action ?: outra.data}")
                    if (fClique != null) md.append(" · OK: $fClique")
                    md.append('\n')
                    dlg?.dismiss()
                    a.finish()
                } catch (e: Throwable) {
                    md.append("- %02d erro: ${e.javaClass.simpleName}: ${e.message?.lineSequence()?.firstOrNull()?.take(80)}\n".format(i + 1))
                }
            }
            md.append('\n')
        }
        File(saida, "submenus-$idioma.md").writeText(md.toString())
    }

    private fun abrir(cls: Class<out Activity>): Activity {
        val a = Robolectric.buildActivity(cls).setup().get()
        if (cls == MainActivity::class.java) repeat(10) { Thread.sleep(100); idle() } else idle()
        return a
    }

    private fun idle() = ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)

    /** Os alvos do controle, na ordem da árvore (a mesma regra da auditoria). */
    private fun focaveis(a: Activity): List<View> {
        val out = mutableListOf<View>()
        fun andar(v: View) {
            if (v.visibility != View.VISIBLE || v.alpha == 0f) return
            val vis = Rect()
            val aparece = v.width > 0 && v.height > 0 && v.getGlobalVisibleRect(vis)
            if (aparece && v.isFocusable && (v !is ViewGroup || v.descendantFocusability == ViewGroup.FOCUS_BLOCK_DESCENDANTS)) out += v
            if (v is ViewGroup) for (i in 0 until v.childCount) andar(v.getChildAt(i))
        }
        andar(a.window.decorView)
        return out
    }

    private fun rotulo(v: View): String {
        v.contentDescription?.let { return it.toString().take(40) }
        if (v is TextView && v.text.isNotBlank()) return v.text.toString().replace('\n', ' ').take(40)
        if (v is ViewGroup) {
            val sb = StringBuilder()
            fun t(x: View) { if (x is TextView && x.text.isNotBlank() && sb.length < 40) sb.append(x.text.toString().replace('\n', ' ')).append(' '); if (x is ViewGroup) for (i in 0 until x.childCount) t(x.getChildAt(i)) }
            t(v); if (sb.isNotBlank()) return sb.toString().trim().take(40)
        }
        return v.javaClass.simpleName
    }

    /**
     * Fotografa [raiz] (com [fundo] por baixo, para a janela aparecer sobre a tela como na TV) e grava, a não ser que
     * uma foto igual já tenha saído desta tela. Devolve o nome do arquivo ou null (repetida).
     */
    private fun guardar(raiz: View, arquivo: String, vistas: MutableSet<Long>, fundo: View? = null): String? {
        val base = fundo ?: raiz
        val w = base.width.takeIf { it > 0 } ?: 1920; val h = base.height.takeIf { it > 0 } ?: 1080
        if (raiz.width == 0 || raiz.height == 0) {
            raiz.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST))
            raiz.layout(0, 0, raiz.measuredWidth, raiz.measuredHeight)
        }
        val foto = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(foto)
        if (fundo != null) {
            fundo.draw(c); c.drawColor(0x99000000.toInt())
            c.save(); c.translate(((w - raiz.width) / 2f).coerceAtLeast(0f), ((h - raiz.height) / 2f).coerceAtLeast(0f))
            raiz.draw(c); c.restore()
        } else raiz.draw(c)
        // Impressão digital barata: 32x18 pontos.
        var hash = 1125899906842597L
        for (y in 0 until 18) for (x in 0 until 32) hash = 31 * hash + (foto.getPixel(x * w / 32, y * h / 18) and 0xF0F0F0)
        if (!vistas.add(hash)) return null
        File(saida, "$arquivo.png").outputStream().use { foto.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "$arquivo.png"
    }

    companion object {
        private const val MAX = 30

        /** As telas com abas, menus e janelas de escolha: os apps nossos e as Configurações. */
        val TELAS = listOf(
            "ConfigLauncherActivity", "ExtrasActivity", "RadioActivity", "PodcastsActivity", "JogosActivity", "CasaActivity",
            "FilmesActivity", "SeriesActivity", "AlarmesActivity", "AutomacoesActivity", "CuidarDeLongeActivity", "PluginsActivity",
            "PainelRapidoActivity", "BackupNoturnoActivity", "GravacoesActivity", "MidiaRedeActivity", "SofyMemoryActivity",
            "AddonsActivity", "FamiliaConectadaActivity", "ServicosFilmesActivity", "ConhecerProActivity", "ReceitasActivity",
            "FineTuneActivity", "CasasActivity", "CenasActivity", "StorageActivity", "DnsActivity", "AgendaFamiliaActivity",
        )
    }
}
