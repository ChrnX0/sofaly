package br.paulo.tvlauncher

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import br.paulo.tvlauncher.privilege.GuardiaoShizuku
import br.paulo.tvlauncher.privilege.JanelaNaFrente


/**
 * O lado do processo principal (onde mora o serviço de acessibilidade). O player (processo :player) avisa por
 * [TrailerReceiver] que abriu um trailer; daí em diante, enquanto o YouTube estiver na frente:
 * - VOLTAR (toque curto) fecha o YouTube e devolve a ficha de onde o trailer saiu (segurar continua abrindo o painel);
 * - o fim do trailer (sessão de mídia do YouTube, quando o acesso às notificações está ligado) faz o mesmo;
 * - se a pessoa sair por outro caminho (HOME, outro app), o YouTube é fechado do mesmo jeito.
 */
object VigiaTrailer {
    private const val TAG = "TvLauncher.Trailer"
    /** Depois disso a sessão vale como encerrada (alguém ficou no YouTube por conta própria). */
    private const val PRAZO_MS = 30 * 60_000L
    private const val OLHAR_MS = 2_000L
    private const val ESPERA_YOUTUBE_MS = 15_000L
    private const val CONFIRMA_MS = 1_200L

    private class Sessao(val inicio: Long, val origem: String?) {
        var viuYoutube = false
        var tocou = false
        var titulo: String? = null
    }

    @Volatile private var sessao: Sessao? = null
    private val principal = Handler(Looper.getMainLooper())
    private var olhando: Runnable? = null

    fun iniciar(ctx: Context, origem: String?) {
        sessao = Sessao(SystemClock.uptimeMillis(), origem?.takeIf { it.startsWith(AppIdentity.NAMESPACE + ".") })
            .apply { viuYoutube = JanelaNaFrente.pacote == TrailerLink.YOUTUBE_TV }
        olhar(ctx.applicationContext)
        Log.i(TAG, "trailer aberto")
    }

    /** O trailer está na frente: VOLTAR é nosso. */
    fun emCurso(): Boolean {
        val s = sessao ?: return false
        if (vencida(s)) { sessao = null; return false }
        return JanelaNaFrente.pacote == TrailerLink.YOUTUBE_TV
    }

    /** Passou do prazo, ou o YouTube nem chegou a abrir em [ESPERA_YOUTUBE_MS] (o pedido falhou). */
    private fun vencida(s: Sessao): Boolean {
        val passou = SystemClock.uptimeMillis() - s.inicio
        return passou > PRAZO_MS || (!s.viuYoutube && passou > ESPERA_YOUTUBE_MS)
    }

    /** Cada janela que abre (serviço de acessibilidade). */
    fun janela(ctx: Context, pacote: CharSequence?, classe: CharSequence? = null) {
        val s = sessao ?: return
        val p = pacote?.toString() ?: return
        if (p == "android" || p == "com.android.systemui") return
        // Painel rápido (VOLTAR segurado) e o nosso protetor por cima do trailer não contam como "saiu do YouTube".
        val c = classe?.toString().orEmpty()
        if (p == ctx.packageName && (c.contains("PainelRapido") || c.contains("Protetor") || c.contains("Dream"))) return
        if (p == TrailerLink.YOUTUBE_TV) { s.viuYoutube = true; return }
        if (!s.viuYoutube || !TrailerLink.saiuDoYoutube(ctx.packageName, p, c)) return
        // Saiu do YouTube por outro caminho (HOME, outro app, a ficha voltou)? Confere de novo daqui a pouco: visto na Sala
        // (v463053), um aviso por cima (janela do serviço de acessibilidade, pacote nosso) fechava o trailer em 2 s.
        Log.i(TAG, "janela fora do YouTube: $p/$c")
        principal.postDelayed({
            if (sessao === s && JanelaNaFrente.pacote != TrailerLink.YOUTUBE_TV) encerrar(ctx, trazer = false)
        }, CONFIRMA_MS)
    }

    /** VOLTAR no trailer ou fim do trailer: fecha o YouTube e traz a ficha. */
    fun voltar(ctx: Context) = encerrar(ctx, trazer = true)

    private fun encerrar(ctx: Context, trazer: Boolean) {
        val s = sessao ?: return
        sessao = null
        olhando?.let { principal.removeCallbacks(it) }; olhando = null
        val app = ctx.applicationContext
        Log.i(TAG, "trailer encerrado (trazer=$trazer)")
        Fundo.rodar("trailer-fechar") {
            // Com o nosso shell: force-stop. Visto na Sala: a ficha (por baixo) aparece na hora e o YouTube sai da memória.
            val feito = GuardiaoShizuku.comando(app, "am force-stop ${TrailerLink.YOUTUBE_TV}", 8_000) != null
            if (feito) return@rodar
            // Sem shell (aparelho sem o pareamento): traz a ficha por cima e mata o YouTube já em segundo plano.
            if (trazer) principal.post { trazerOrigem(app, s.origem) }
            principal.postDelayed({
                runCatching { app.getSystemService(ActivityManager::class.java)?.killBackgroundProcesses(TrailerLink.YOUTUBE_TV) }
            }, 1_500)
        }
    }

    private fun trazerOrigem(ctx: Context, origem: String?) {
        val i = if (origem != null) Intent().setClassName(ctx, origem) else Intent(ctx, MainActivity::class.java)
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
            .onFailure { Log.w(TAG, "não trouxe a ficha: $it") }
    }

    /** Olha a sessão de mídia do YouTube a cada 2 s: o trailer acabou -> volta. Sem acesso às notificações, só o VOLTAR. */
    private fun olhar(app: Context) {
        olhando?.let { principal.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                val s = sessao ?: return
                if (vencida(s)) { sessao = null; return }
                val c = controlador(app)
                if (c != null) {
                    val estado = c.playbackState?.state ?: PlaybackState.STATE_NONE
                    val titulo = c.metadata?.description?.title?.toString()
                    if (estado == PlaybackState.STATE_PLAYING && !s.tocou) { s.tocou = true; s.titulo = titulo }
                    if (s.tocou && s.titulo.isNullOrBlank()) s.titulo = titulo
                    if (JanelaNaFrente.pacote == TrailerLink.YOUTUBE_TV && TrailerLink.acabou(s.tocou, s.titulo, estado, titulo)) {
                        voltar(app); return
                    }
                } else if (s.tocou && JanelaNaFrente.pacote == TrailerLink.YOUTUBE_TV) {
                    // A sessão do YouTube sumiu depois de tocar (ele a solta quando o vídeo acaba e volta ao menu).
                    voltar(app); return
                }
                principal.postDelayed(this, OLHAR_MS)
            }
        }
        olhando = r
        principal.postDelayed(r, OLHAR_MS)
    }

    private fun controlador(ctx: Context): MediaController? = try {
        ctx.getSystemService(MediaSessionManager::class.java)
            ?.getActiveSessions(ComponentName(ctx, OuvinteNotificacoes::class.java))
            ?.firstOrNull { it.packageName == TrailerLink.YOUTUBE_TV }
    } catch (_: SecurityException) { null } catch (_: Exception) { null }
}

/** O player (processo :player) avisa que abriu um trailer. Não exportado. */
class TrailerReceiver : BroadcastReceiver() {
    companion object {
        const val ACAO = "br.paulo.tvlauncher.TRAILER_ABERTO"
        fun avisar(ctx: Context, origem: String?) {
            runCatching {
                ctx.sendBroadcast(Intent(ACAO).setComponent(ComponentName(ctx, TrailerReceiver::class.java)).putExtra("origem", origem))
            }
        }
    }

    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action != ACAO) return
        VigiaTrailer.iniciar(ctx, i.getStringExtra("origem"))
    }
}
