package br.paulo.tvlauncher.privilege

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import br.paulo.tvlauncher.BuildConfig
import br.paulo.tvlauncher.Fundo

/**
 * Compilação AOT depois de uma atualização (11/11/2026, etapa de desempenho do pente fino).
 *
 * Medido na Sala: depois da atualização automática (Atualizador.kt) o launcher ficou em `status=verify`, ou seja, rodando
 * interpretado/JIT. O build3.ps1 resolvia isso pelo adb (grava o perfil, `cmd package compile -m speed-profile -f`,
 * reinicia), mas a atualização pela própria TV não faz nada disso: o launcher só era compilado na manutenção noturna do
 * Android (BackgroundDexoptJobService), que exige a TV ociosa e pode demorar dias.
 *
 * Agora: quando a TV APAGA a tela (ninguém vendo) e a versão ainda não foi compilada, espera 1 min, grava o perfil
 * embutido (profileinstaller), compila pelo shell próprio e reinicia o processo da HOME para o código compilado valer.
 * Não reinicia se a tela acendeu de novo ou se há som tocando. Até [MAX_TENTATIVAS] por versão (sem shell, desiste).
 *
 * 13/11/2026: também compila quando o PROTETOR começa (medido na Sala: a tela nunca apaga, o protetor fica a tarde toda,
 * e a versão nova passou 70 min em `verify`). No protetor só compila: reiniciar mataria o protetor, que roda neste mesmo
 * processo. O reinício fica pendente e acontece no próximo apagar da tela ([reiniciarSePendente]).
 *
 * 28/11/2026 (SLOW_HOME na Sala, 99% dos quadros da HOME lentos às 09:17): a v477122 foi instalada às 05:27 e o processo
 * dela viveu 228 min sem reiniciar. A noite foi de protetor, não de tela apagada: compilar no protetor deixa o reinício
 * pendente, e o "apagar" só veio às 09:47 (desligar automático). Quem ligou a TV às 08:35 usou a HOME no código antigo
 * (interpretado/JIT) por mais de uma hora. Agora o reinício pendente também acontece quando o protetor SAI com a tela
 * acesa ([reiniciarAoSairDoProtetor]): a pessoa vê a HOME abrir do zero uma vez (~2 s), já no código compilado.
 * Cada compilação e reinício vira evento do observador (update.aot_*): o logcat da TV some em minutos.
 */
object CompilarAot {
    private const val TAG = "TvLauncher.Aot"
    const val MAX_TENTATIVAS = 3
    private const val ESPERA_MS = 60_000L
    private const val PREFS = "aot"

    /** Decisão pura (teste JVM): compila se esta versão ainda não foi feita e não esgotou as tentativas. */
    fun deveCompilar(versaoAtual: Int, versaoFeita: Int, tentativas: Int): Boolean =
        versaoAtual != versaoFeita && tentativas < MAX_TENTATIVAS

    /** A saída do `cmd package compile` diz "Success" quando deu certo. */
    fun deuCerto(saida: String?): Boolean = saida?.lineSequence()?.any { it.trim() == "Success" } == true

    @Volatile private var rodando = false
    /** O protetor está na tela (entre DREAMING_STARTED e DREAMING_STOPPED). */
    @Volatile private var noProtetor = false

    /** Decisão pura (teste JVM): ao apagar a tela, reinicia se esta versão já foi compilada mas o processo é anterior. */
    fun deveReiniciar(versaoAtual: Int, versaoFeita: Int, reiniciouEm: Int): Boolean =
        versaoFeita == versaoAtual && reiniciouEm != versaoAtual

    /** Liga o vigia (só no processo principal). Idempotente pelo registro único no LauncherApp. */
    fun iniciar(ctx: Context) {
        val app = ctx.applicationContext
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_DREAMING_STARTED -> { noProtetor = true; talvezCompilar(app) }
                    Intent.ACTION_DREAMING_STOPPED -> { noProtetor = false; reiniciarAoSairDoProtetor(app) }
                    Intent.ACTION_SCREEN_OFF -> { noProtetor = false; if (!reiniciarSePendente(app)) talvezCompilar(app) }
                }
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_DREAMING_STARTED); addAction(Intent.ACTION_DREAMING_STOPPED)
        })
        // 20/11/2026 (rodada de desempenho): a atualização costuma ser instalada com a tela já apagada, e aí o processo
        // novo nasce sem nunca receber o "apagou" — a versão nova ficava em `verify` (sem compilar) o dia seguinte inteiro,
        // até a tela apagar de novo (visto na Sala: v465338 instalada às 00:46 com a TV dormindo). Nasceu com a tela
        // apagada: segue o mesmo caminho do apagar (espera 1 min, confere de novo, compila, reinicia sem ninguém vendo).
        if (!telaAcesa(app)) talvezCompilar(app)
    }

    /**
     * O protetor saiu com a tela acesa (alguém apertou uma tecla) e esta versão foi compilada mas o processo é anterior:
     * reinicia JÁ, antes da HOME aparecer, em vez de deixar a pessoa usar o código antigo até a tela apagar.
     * Só com a NOSSA HOME na frente (o protetor também sai para o despertador tocar, ou de volta para um app que estava
     * aberto: aí não), sem som e fora da conferência de telas da madrugada (ela fecha o protetor de propósito). Espera
     * [ESPERA_SAIR_MS] para a tela de baixo aparecer na acessibilidade antes de decidir.
     */
    private fun reiniciarAoSairDoProtetor(app: Context) {
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val versao = BuildConfig.VERSION_CODE
        if (!deveReiniciar(versao, p.getInt("versao_feita", 0), p.getInt("reiniciou_em", 0))) return
        Fundo.rodar("aot-acordou") {
            SystemClock.sleep(ESPERA_SAIR_MS)
            val somTocando = app.getSystemService(AudioManager::class.java)?.isMusicActive == true
            if (!deveReiniciarAoAcordar(versao, p.getInt("versao_feita", 0), p.getInt("reiniciou_em", 0),
                    rodando, telaAcesa(app), somTocando, JanelaNaFrente.homeNaFrente(app),
                    br.paulo.tvlauncher.observer.SofyScreenCheckTv.ocupado())) return@rodar
            p.edit().putInt("reiniciou_em", versao).commit()   // commit: o processo morre logo abaixo
            evento(app, "aot_restart", true, "v$versao ao sair do protetor")
            Log.i(TAG, "reiniciando ao sair do protetor para valer o código compilado")
            SystemClock.sleep(300)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private const val ESPERA_SAIR_MS = 800L

    /** Decisão pura (teste JVM) do reinício ao sair do protetor. */
    fun deveReiniciarAoAcordar(versaoAtual: Int, versaoFeita: Int, reiniciouEm: Int, compilando: Boolean,
                               telaAcesa: Boolean, somTocando: Boolean, homeNaFrente: Boolean, conferindoTelas: Boolean): Boolean =
        deveReiniciar(versaoAtual, versaoFeita, reiniciouEm) && !compilando && telaAcesa && !somTocando &&
            homeNaFrente && !conferindoTelas

    /** Guarda no observador (eventos da Sofy) o que aconteceu: compilou, falhou, reiniciou. */
    private fun evento(app: Context, tipo: String, ok: Boolean, detalhe: String) = runCatching {
        br.paulo.tvlauncher.observer.SofyBusTv.emit(app, br.paulo.tvlauncher.observer.SofyBus.Source.UPDATE, tipo, ok, detalhe)
    }

    /** Compilada no protetor e ainda não reiniciada: reinicia agora que a tela apagou. Devolve true se cuidou disso. */
    private fun reiniciarSePendente(app: Context): Boolean {
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val versao = BuildConfig.VERSION_CODE
        if (rodando || !deveReiniciar(versao, p.getInt("versao_feita", 0), p.getInt("reiniciou_em", 0))) return false
        rodando = true
        Fundo.rodar("aot-reiniciar") {
            try {
                SystemClock.sleep(ESPERA_MS)
                reiniciarSeNinguemVendo(app, p, versao)
            } finally { rodando = false }
        }
        return true
    }

    /** O código compilado só vale num processo novo: reinicia só com a tela apagada (não no protetor) e sem som. */
    private fun reiniciarSeNinguemVendo(app: Context, p: android.content.SharedPreferences, versao: Int) {
        val somTocando = app.getSystemService(AudioManager::class.java)?.isMusicActive == true
        if (telaAcesa(app) || somTocando) return
        p.edit().putInt("reiniciou_em", versao).commit()   // commit: o processo morre logo abaixo
        evento(app, "aot_restart", true, "v$versao com a tela apagada")
        Log.i(TAG, "reiniciando para valer o código compilado")
        SystemClock.sleep(500)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun talvezCompilar(app: Context) {
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val versao = BuildConfig.VERSION_CODE
        val tentativas = if (p.getInt("versao_tentada", 0) == versao) p.getInt("tentativas", 0) else 0
        if (rodando || !deveCompilar(versao, p.getInt("versao_feita", 0), tentativas)) return
        rodando = true
        Fundo.rodar("compilar-aot") {
            try {
                SystemClock.sleep(ESPERA_MS)
                // Ligaram a TV de novo (fora do protetor): fica para o próximo apagar ou protetor.
                if (telaAcesa(app) && !noProtetor) return@rodar
                p.edit().putInt("versao_tentada", versao).putInt("tentativas", tentativas + 1).apply()
                // Grava o perfil embutido (baseline-prof.txt) na pasta de perfis; sem ele o ART cai em "verify".
                runCatching { androidx.profileinstaller.ProfileInstaller.writeProfile(app) }
                val saida = GuardiaoShizuku.comando(app, "cmd package compile -m speed-profile -f ${app.packageName}", 180_000)
                Log.i(TAG, "compilar v$versao: ${saida?.trim()?.take(200)}")
                evento(app, "aot_compile", deuCerto(saida),
                    "v$versao tentativa ${tentativas + 1}${if (noProtetor) " no protetor" else ""}: ${saida?.trim()?.take(80) ?: "sem shell"}")
                if (!deuCerto(saida)) return@rodar
                p.edit().putInt("versao_feita", versao).commit()
                // O código compilado só vale num processo novo. Reinicia só com a tela apagada e sem som (o player roda em
                // ":player", outro processo, mas não custa não arriscar). Mesmo caminho do REINICIAR do adb. No protetor,
                // telaAcesa() é true: não reinicia, e o próximo apagar da tela cuida disso (reiniciarSePendente).
                reiniciarSeNinguemVendo(app, p, versao)
            } catch (e: Exception) {
                Log.w(TAG, "falhou: ${e.javaClass.simpleName}")
            } finally { rodando = false }
        }
    }

    private fun telaAcesa(app: Context) = app.getSystemService(PowerManager::class.java)?.isInteractive != false
}
