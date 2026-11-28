package br.paulo.tvlauncher.settings

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import br.paulo.tvlauncher.resources.R
import br.paulo.tvlauncher.games.JogosTv
import br.paulo.tvlauncher.txt
import java.util.Calendar

/**
 * Escurecer e esquentar a tela à noite (10/11/2026, ideia L; li no tv-remote: "dim or blue light filter").
 *
 * Uma película do tamanho da tela, por cima de tudo, sem foco e sem toque: âmbar semitransparente. A janela é do
 * serviço de acessibilidade (TYPE_ACCESSIBILITY_OVERLAY), igual ao AvisoTv: nenhum app consegue escondê-la e não precisa
 * da permissão de sobreposição. Liga e desliga sozinha pelo horário ([inicioMin]..[fimMin], pode passar da meia-noite),
 * conferido a cada minuto, só com a chave [Extras.Chave.FILTRO_NOTURNO] ligada.
 *
 * Não verificado: se algum app com DRM (Netflix) se recusa a tocar com uma janela por cima. A película não é "segura"
 * (não usa FLAG_SECURE), então foto da tela pelo painel continua saindo, já com o âmbar.
 */
object FiltroNoturno {
    const val CHAVE_INICIO = "filtro_inicio_min"
    const val CHAVE_FIM = "filtro_fim_min"
    const val CHAVE_FORCA = "filtro_forca"   // 0..100
    /** Sem ajuste próprio, o filtro segue a noite da casa (Brainstorm 26/11, item 12; antes era 22h-6h fixo). */
    fun inicio(ctx: android.content.Context): Int = Extras.numero(ctx, CHAVE_INICIO, br.paulo.tvlauncher.alarm.NoiteCasa.inicio(ctx))
    fun fim(ctx: android.content.Context): Int = Extras.numero(ctx, CHAVE_FIM, br.paulo.tvlauncher.alarm.NoiteCasa.fim(ctx))
    const val PADRAO_FORCA = 35
    // Ligar/desligar na hora, pelo catálogo de ações (cena, Claude, voz; 14/11/2026, ideia 2): vale até a próxima virada
    // do horário (início ou fim da janela), no máximo [MAX_FORCADO_MIN]; depois o horário volta a mandar.
    private const val PREFS_FORCADO = "filtro_forcado"
    const val MAX_FORCADO_MIN = 12 * 60

    private val ui by lazy { Handler(Looper.getMainLooper()) }   // lazy: os testes JVM usam só as regras puras
    private var pelicula: View? = null
    private var servico: AccessibilityService? = null
    private var vigiaTela: BroadcastReceiver? = null

    /** Regra pura (FiltroNoturnoTest): [minuto] (0..1439) está dentro da janela? A janela pode atravessar a meia-noite. */
    fun dentro(minuto: Int, inicio: Int, fim: Int): Boolean = when {
        inicio == fim -> false
        inicio < fim -> minuto in inicio until fim
        else -> minuto >= inicio || minuto < fim
    }

    /**
     * Regra pura (FiltroNoturnoTest): quantos minutos faltam, a partir de [minuto], para a janela virar (chegar ao
     * [inicio] ou ao [fim]), de 1 a [MAX_FORCADO_MIN]. É até quando vale o "ligar/desligar agora".
     */
    fun minutosAteVirar(minuto: Int, inicio: Int, fim: Int): Int {
        fun ate(alvo: Int) = ((alvo - minuto) % 1440 + 1440) % 1440
        val candidatos = listOf(ate(inicio), ate(fim)).filter { it > 0 }
        return (candidatos.minOrNull() ?: MAX_FORCADO_MIN).coerceIn(1, MAX_FORCADO_MIN)
    }

    /** Regra pura: o estado forçado vale enquanto [agoraMs] < [ateMs]; fora disso, o do horário ([agendado]). */
    fun deveLigar(agendado: Boolean, forcado: Boolean?, ateMs: Long, agoraMs: Long): Boolean =
        if (forcado != null && agoraMs < ateMs) forcado else agendado

    /** Regra pura (FiltroNoturnoTest): a película aparece só ligada, com a tela acesa e fora do modo jogo. */
    fun mostrar(ligar: Boolean, telaAcesa: Boolean, modoJogo: Boolean): Boolean = ligar && telaAcesa && !modoJogo

    /**
     * Liga ou desliga a película AGORA (ação "filtro_noturno" do catálogo). Null = feito; senão o motivo. Sem o serviço de
     * acessibilidade não há como pôr a película.
     */
    fun forcar(ctx: Context, ligado: Boolean): String? {
        val c = Calendar.getInstance()
        val minuto = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val min = minutosAteVirar(minuto, inicio(ctx), fim(ctx))
        ctx.getSharedPreferences(PREFS_FORCADO, Context.MODE_PRIVATE).edit()
            .putBoolean("ligado", ligado).putLong("ate", System.currentTimeMillis() + min * 60_000L).apply()
        if (servico == null) return txt(R.string.acao_filtro_sem_servico)
        ui.post { avaliar() }
        return null
    }

    /** Cor da película para a força 0..100: âmbar (luz azul cortada) com transparência proporcional. Máx. 70% opaca. */
    fun cor(forca: Int): Int {
        val f = forca.coerceIn(0, 100)
        val alfa = (f * 0.70f * 255 / 100).toInt()
        return Color.argb(alfa, 40, 18, 0)
    }

    private val relogio = object : Runnable {
        override fun run() { avaliar(); ui.postDelayed(this, 60_000) }
    }

    /** Chamado quando o serviço de acessibilidade conecta. */
    fun iniciar(svc: AccessibilityService) {
        servico = svc
        ui.removeCallbacks(relogio)
        ui.post(relogio)
        // Tela apagada = película fora (28/11/2026, medido na Sala): a janela do tamanho da tela segura ~11 MB de GPU
        // mesmo com a TV dormindo, sem ninguém vendo. Volta assim que a tela acende, sem esperar o relógio de 1 min.
        if (vigiaTela == null) vigiaTela = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { avaliar() }
        }.also { r ->
            try { svc.registerReceiver(r, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }) }
            catch (_: Exception) { vigiaTela = null }
        }
    }

    fun parar() {
        ui.removeCallbacks(relogio); tirar()
        vigiaTela?.let { r -> try { servico?.unregisterReceiver(r) } catch (_: Exception) {} }
        vigiaTela = null; servico = null
    }

    /** Liga ou tira a película conforme a chave e o horário. Também chamado ao mexer nos ajustes. */
    fun avaliar() {
        val svc = servico ?: return
        val c = Calendar.getInstance()
        val minuto = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val agendado = Extras.ligado(svc, Extras.Chave.FILTRO_NOTURNO) && dentro(minuto,
            inicio(svc), fim(svc))
        val f = svc.getSharedPreferences(PREFS_FORCADO, Context.MODE_PRIVATE)
        val ligar = deveLigar(agendado, if (f.contains("ligado")) f.getBoolean("ligado", false) else null, f.getLong("ate", 0), System.currentTimeMillis())
        // Modo jogo (card Jogos): com um jogo na frente a película sai e volta quando ele fecha (JogosTv.janela).
        // Tela apagada: ninguém vê a película, e ela segura um quadro inteiro na GPU (ver iniciar).
        val telaAcesa = svc.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        if (mostrar(ligar, telaAcesa, JogosTv.modoJogoAgora(svc))) por(svc, Extras.numero(svc, CHAVE_FORCA, PADRAO_FORCA)) else tirar()
    }

    private fun por(svc: AccessibilityService, forca: Int) {
        pelicula?.let { it.setBackgroundColor(cor(forca)); return }
        val v = View(svc).apply { setBackgroundColor(cor(forca)) }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        try { svc.getSystemService(WindowManager::class.java).addView(v, p); pelicula = v } catch (_: Exception) {}
    }

    private fun tirar() {
        val v = pelicula ?: return
        pelicula = null
        try { v.context.getSystemService(WindowManager::class.java).removeView(v) } catch (_: Exception) {}
    }
}
