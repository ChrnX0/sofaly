package br.paulo.tvlauncher

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * "Licenças e créditos" (05/11/2026): mostra assets/licencas.txt numa tela rolável. Aberta pela última linha das
 * Configurações do launcher. O texto mora num arquivo (e não aqui no código) para ser o mesmo que o repositório público
 * mostra e para dar para atualizar sem mexer em Kotlin.
 */
class LicencasActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (VigiaAtualizacao.fechada(this)) return   // modo seguro: tela que derrubou esta versão (VigiaAtualizacao)
        // Língua (auditoria de telas, 27/11/2026): em inglês e nas outras línguas o texto aparecia em português.
        // Português lê licencas.txt (o mesmo do repositório público); as demais, licencas-en.txt (LicencasTextoTest
        // confere que os dois citam os mesmos endereços).
        val pt = resources.configuration.locales[0].language == "pt"
        val sufixo = if (pt) "" else "-en"
        var texto = try { assets.open("licencas$sufixo.txt").bufferedReader().use { it.readText() } } catch (e: Exception) { getString(R.string.lic_sem_arquivo) }
        // Build da Play (16/11/2026): as fontes de dados são outras (MET Norway, GeoNames, Trakt; sem TMDB, Open-Meteo,
        // Nominatim e IP). A seção "— Dados —" vem de src/play/assets/licencas-dados-play.txt no lugar da do site.
        if (BuildConfig.PLAY) runCatching { assets.open("licencas-dados-play$sufixo.txt").bufferedReader().use { it.readText() } }.getOrNull()?.let { dados ->
            val i = texto.indexOf(if (pt) "— Dados —" else "— Data —")
            val fim = texto.indexOf(if (pt) "Os sons do despertador" else "The alarm sounds", maxOf(i, 0))
            if (i >= 0 && fim > i) texto = texto.substring(0, i) + dados.trimEnd() + "\n\n" + texto.substring(fim)
        }
        fun dp(v: Int) = Estilo.dp(this, v)
        // Mesmas margens, fundo e título das outras telas (sistema de design).
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Estilo.fundoTela(context)
            setPaddingRelative(dp(Estilo.MARGEM_LATERAL), dp(Estilo.MARGEM_VERTICAL + 9), dp(Estilo.MARGEM_LATERAL), dp(Estilo.MARGEM_VERTICAL))
        }
        raiz.addView(Estilo.tituloTela(this, getString(R.string.cfg_licencas)))
        val corpo = Estilo.texto(this, Estilo.Papel.LEGENDA, texto).apply {
            setTextColor(Estilo.TEXTO_2)
            setLineSpacing(0f, 1.15f)
            setPaddingRelative(dp(20), dp(14), dp(20), dp(14))
        }
        // A ScrollView recebe o foco: as setas do controle rolam o texto, BACK fecha.
        val rolagem = ScrollView(this).apply { isFocusable = true; background = Estilo.superficie(context, false); addView(corpo) }
        raiz.addView(rolagem, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(Estilo.E2) })
        setContentView(raiz)
        rolagem.requestFocus()
    }
}
