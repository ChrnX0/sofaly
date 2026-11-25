package br.paulo.tvlauncher.observer

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import br.paulo.tvlauncher.AutorizaShizukuService
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.ProtetorTela
import br.paulo.tvlauncher.alarm.NaoPerturbe
import java.util.Calendar

/**
 * A TV confere as próprias telas de madrugada (parte C, 25/11/2026). Abre uma tela nossa por noite, lê a árvore de
 * acessibilidade, passa pela régua pura ([SofyScreenCheck]) e volta para a home. Um defeito vira caso SCREEN_BROKEN:
 * a caixa de ferramentas não conserta código, então ele segue para o relatório (e para a issue da tarefa diária).
 *
 * Freios (a TV é da casa, não bancada): só com a tela APAGADA ou o nosso protetor na frente, só na madrugada da casa,
 * só com a acessibilidade de pé (é ela que lê), uma tela por noite, e volta para a home no fim. Se alguém acordar a TV
 * no meio, para na hora.
 */
object SofyScreenCheckTv {
    private const val TAG = "TvLauncher.SofyTelas"
    private const val PREFS = "sofy_repair"
    /**
     * As telas, na ordem: uma por noite, em uma semana e meia passa por todas e começa de novo. São telas que só leem
     * (abrir não muda nada na casa): nada que grave, toque som ou mexa em outro app.
     */
    private val TELAS: List<Pair<String, Class<*>>> = listOf(
        "previsao" to br.paulo.tvlauncher.PrevisaoActivity::class.java,
        "agenda" to br.paulo.tvlauncher.CalendarioActivity::class.java,
        "despertador" to br.paulo.tvlauncher.AlarmesActivity::class.java,
        "config" to br.paulo.tvlauncher.ConfigLauncherActivity::class.java,
        "jogos" to br.paulo.tvlauncher.JogosActivity::class.java,
        "podcasts" to br.paulo.tvlauncher.PodcastsActivity::class.java,
        "filmes" to br.paulo.tvlauncher.FilmesActivity::class.java,
        "series" to br.paulo.tvlauncher.SeriesActivity::class.java,
        "diagnostico" to br.paulo.tvlauncher.DiagnosticoActivity::class.java,
        "armazenamento" to br.paulo.tvlauncher.StorageActivity::class.java,
        "ajuda" to br.paulo.tvlauncher.AjudaActivity::class.java,
        "sobre" to br.paulo.tvlauncher.AboutActivity::class.java,
    )
    private const val ESPERA_MS = 2_500L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Chamado pela batida da Sofy. Devolve true se conferiu alguma tela agora. */
    fun talvez(ctx: Context): Boolean {
        val app = ctx.applicationContext
        val s = AutorizaShizukuService.instancia ?: return false
        if (!paradaDeVerdade(app)) return false
        val cal = Calendar.getInstance()
        if (!NaoPerturbe.madrugada(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))) return false
        val p = prefs(app)
        val hoje = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        if (p.getString("telas_dia", "") == hoje) return false
        p.edit().putString("telas_dia", hoje).apply()
        val i = p.getInt("telas_i", 0)
        val (nome, classe) = TELAS[i % TELAS.size]
        p.edit().putInt("telas_i", i + 1).apply()
        Fundo.rodar("sofy-telas") { runCatching { conferir(app, s, nome, classe) }.onFailure { Log.w(TAG, "$nome: $it") } }
        return true
    }

    /** Tela apagada ou o nosso protetor na frente: ninguém está vendo. */
    private fun paradaDeVerdade(app: Context): Boolean {
        val pm = app.getSystemService(android.os.PowerManager::class.java)
        return pm?.isInteractive == false || ProtetorTela.aberto()
    }

    private fun conferir(app: Context, s: AutorizaShizukuService, nome: String, classe: Class<*>) {
        app.startActivity(Intent(app, classe).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(ESPERA_MS)
        if (!paradaDeVerdade(app)) { voltar(s); return }   // alguém acordou a TV: para na hora
        val raiz = runCatching { s.rootInActiveWindow }.getOrNull()
        if (raiz == null || raiz.packageName?.toString() != app.packageName) { voltar(s); return }
        val tela = ler(raiz, nome)
        voltar(s)
        // Revisor noturno (plano D, passo 3): regras aprendidas + IA do dono olhando o que a tela DIZ.
        runCatching { SofyRulesTv.onScreen(app, nome, tela.caixas.map { it.texto }) }.onFailure { Log.w(TAG, "revisor: $it") }
        val achados = SofyScreenCheck.conferir(tela)
        if (achados.isEmpty()) { Log.i(TAG, "$nome: ok (${tela.caixas.size} textos)"); return }
        val resumo = SofyScreenCheck.resumo(nome, achados)
        Log.w(TAG, resumo)
        ObserverTv.logShadow(app, "self_defect", resumo)   // a tarefa diária abre a issue a partir daqui
        SofyRepairTv.signal(app, SofyRepair.Type.SCREEN_BROKEN, "screen:$nome", achados.first().defeito.name)
    }

    private fun voltar(s: AutorizaShizukuService) {
        runCatching { s.performGlobalAction(AccessibilityNodeInfo.ACTION_DISMISS) }
        runCatching { s.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) }
    }

    /** Lê a árvore: cada nó com texto vira uma caixa; guarda se existe algo focável. */
    private fun ler(raiz: AccessibilityNodeInfo, nome: String): SofyScreenCheck.Tela {
        val tudo = Rect().also { raiz.getBoundsInScreen(it) }
        val caixas = ArrayList<SofyScreenCheck.Caixa>()
        var foco = false
        fun andar(n: AccessibilityNodeInfo?, nivel: Int) {
            if (n == null || nivel > 25) return
            if (n.isFocusable) foco = true
            val t = (n.text ?: n.contentDescription)?.toString()
            // Só o nó folha: um ViewGroup repete o texto dos filhos e sobreporia tudo.
            if (!t.isNullOrBlank() && n.childCount == 0) {
                val r = Rect().also { n.getBoundsInScreen(it) }
                caixas += SofyScreenCheck.Caixa(t, r.left, r.top, r.right, r.bottom)
            }
            for (k in 0 until n.childCount) andar(runCatching { n.getChild(k) }.getOrNull(), nivel + 1)
        }
        andar(raiz, 0)
        return SofyScreenCheck.Tela(nome, tudo.width(), tudo.height(), caixas, foco)
    }
}
