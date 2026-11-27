package br.paulo.tvlauncher

import br.paulo.tvlauncher.resources.R
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import br.paulo.tvlauncher.home.PluginWidgetsTv
import br.paulo.tvlauncher.screensaver.PluginWidget
import br.paulo.tvlauncher.settings.Planos
import br.paulo.tvlauncher.settings.PlanosTv
import java.util.concurrent.Executors

/**
 * Modo de edição dos widgets da tela inicial (24/11/2026, Paulo: "em modo de edição da home a gente pode escolher o
 * widget e posicionar ele com as teclas de direção e também mudar as configs dele e tamanho. Igual a qualquer launcher").
 *
 * Abre por cima da HOME (fundo translúcido escurecido): os widgets na mesma grade e no mesmo lugar da HOME
 * ([PluginWidgetsTv.Camada] com `editando`), o escolhido com o anel laranja do foco.
 * Setas movem o escolhido (ou mudam o tamanho, no modo Tamanho); OK abre as opções; VOLTAR salva e sai.
 * Referências (regra de design): o modo de edição da tela inicial do iOS e do Android (grade de pontos, alças de tamanho,
 * "concluir"), vistos no Mobbin (Insight Timer "Press and hold anywhere on your home screen", Givingli com alças nas
 * laterais, Google Health "Edit focus" com tamanhos). Na TV, sem toque: tudo por setas + OK + VOLTAR.
 */
class WidgetLayoutActivity : Activity() {
    private enum class Modo { MOVER, TAMANHO }

    private val trabalho = Executors.newSingleThreadExecutor { r -> Thread(r, "Widgets").apply { isDaemon = true } }
    private lateinit var camada: PluginWidgetsTv.Camada
    private lateinit var estado: TextView
    private var lista: List<PluginWidget.Escolhido> = emptyList()
    private var escolhido = -1
    private var modo = Modo.MOVER
    private var aberto: Dialog? = null
    private var novoPendente = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Planos.permite(this, Planos.Recurso.PLUGINS)) { PlanosTv.pedir(this, Planos.Recurso.PLUGINS); finish(); return }
        val raiz = FrameLayout(this).apply { setBackgroundColor(VEU) }
        camada = PluginWidgetsTv.Camada(this).apply { editando = true }
        raiz.addView(camada, PluginWidgetsTv.lpCamada(this))
        val cima = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(Estilo.tituloTela(this@WidgetLayoutActivity, getString(R.string.wdg_titulo)))
            estado = Estilo.texto(this@WidgetLayoutActivity, Estilo.Papel.LEGENDA, "")
            addView(estado)
        }
        raiz.addView(cima, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            marginStart = Estilo.dp(this@WidgetLayoutActivity, PluginWidgetsTv.LADO_DP); topMargin = Estilo.dp(this@WidgetLayoutActivity, 20)
        })
        val dicas = Estilo.barraDicas(this,
            "◀ ▶ ▲ ▼" to getString(R.string.wdg_d_setas), "OK" to getString(R.string.wdg_d_opcoes), getString(R.string.wdg_d_voltar) to getString(R.string.wdg_d_sair))
        raiz.addView(dicas, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
            marginStart = Estilo.dp(this@WidgetLayoutActivity, PluginWidgetsTv.LADO_DP); bottomMargin = Estilo.dp(this@WidgetLayoutActivity, 40)
        })
        setContentView(raiz)
        ler()
        if (lista.isEmpty()) camada.post { porWidget() }
    }

    override fun onStart() { super.onStart(); PluginWidgetsTv.editou = true; PluginWidgetsTv.ouvir(this) }
    override fun onStop() { super.onStop(); PluginWidgetsTv.parar() }
    override fun onDestroy() { super.onDestroy(); aberto?.dismiss(); trabalho.shutdown() }

    private fun ler() {
        PluginWidgetsTv.ligar(this)   // o primeiro widget acabou de entrar: o host passa a escutar
        camada.atualizar(PluginWidgetsTv.escolhidos(this))
        lista = camada.lista
        if (lista.none { it.id == escolhido }) escolhido = lista.firstOrNull()?.id ?: -1
        pintar()
    }

    private fun atual() = lista.firstOrNull { it.id == escolhido }

    private fun pintar() {
        camada.escolhido = escolhido
        val e = atual()
        val nome = e?.let { PluginWidgetsTv.info(this, it.id)?.let { i -> PluginWidgetsTv.nome(this, i) } ?: it.pacote }
        estado.text = when {
            e == null -> getString(R.string.wdg_vazio)
            modo == Modo.TAMANHO -> getString(R.string.wdg_estado_tamanho, nome, e.w, e.h)
            else -> getString(R.string.wdg_estado_mover, nome)
        }
    }

    private fun mudar(nova: List<PluginWidget.Escolhido>) {
        if (nova == lista) return
        lista = nova
        PluginWidgetsTv.salvar(this, nova)
        camada.mostrar(nova)
        pintar()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        lista = camada.lista   // a camada dá lugar, depois de medida, a quem ainda não tinha
        val e = atual()
        val (dx, dy) = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -1 to 0
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1 to 0
            KeyEvent.KEYCODE_DPAD_UP -> 0 to -1
            KeyEvent.KEYCODE_DPAD_DOWN -> 0 to 1
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { opcoes(); return true }
            else -> return super.onKeyDown(keyCode, event)
        }
        if (e == null) return true
        if (modo == Modo.MOVER) mudar(PluginWidget.mover(lista, e.id, dx, dy))
        else {
            val lim = PluginWidgetsTv.info(this, e.id)?.let { PluginWidgetsTv.limites(it, camada.celL, camada.celA) } ?: return true
            // Tamanho: ▶/▼ aumentam, ◀/▲ diminuem.
            mudar(PluginWidget.redimensionar(lista, e.id, dx, dy, lim))
        }
        return true
    }

    /** OK: as opções do widget escolhido (ou só "Pôr um widget", sem nenhum). */
    private fun opcoes() {
        val e = atual()
        val ops = mutableListOf<Pair<String, () -> Unit>>()
        if (e != null) {
            val info = PluginWidgetsTv.info(this, e.id)
            ops += getString(if (modo == Modo.MOVER) R.string.wdg_tamanho else R.string.wdg_mover) to {
                modo = if (modo == Modo.MOVER) Modo.TAMANHO else Modo.MOVER; pintar()
            }
            if (PluginWidgetsTv.temAjuste(info)) ops += getString(R.string.wdg_ajustes) to {
                if (!PluginWidgetsTv.abrirAjuste(this, e.id, PEDIDO_AJUSTE)) aviso(PluginWidgetsTv.Resultado.FALHOU)
            }
            if (lista.size > 1) ops += getString(R.string.wdg_proximo) to {
                escolhido = lista[(lista.indexOfFirst { it.id == e.id } + 1) % lista.size].id; modo = Modo.MOVER; pintar()
            }
            ops += getString(R.string.wdg_abrir) to {
                packageManager.getLeanbackLaunchIntentForPackage(e.pacote) ?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                Unit
            }
        }
        if (lista.size < PluginWidget.MAX) ops += getString(R.string.wdg_por) to { porWidget() }
        if (e != null) ops += getString(R.string.wdg_tirar) to { PluginWidgetsTv.remover(this, e.id); ler() }
        aberto = Estilo.lista(this, getString(R.string.wdg_titulo), ops.map { it.first }) { i -> ops[i].second() }
    }

    /** Lista dos widgets instalados; o escolhido é ligado fora da tela (pode chamar o shell) e já ganha um lugar. */
    private fun porWidget() {
        val disponiveis = PluginWidgetsTv.disponiveis(this)
        if (disponiveis.isEmpty()) { Toast.makeText(this, getString(R.string.ext_widget_nenhum), Toast.LENGTH_LONG).show(); return }
        aberto = Estilo.lista(this, getString(R.string.wdg_por), disponiveis.map { PluginWidgetsTv.nome(this, it) }) { i ->
            val info = disponiveis[i]
            val cl = camada.celL; val ca = camada.celA
            runCatching {
                trabalho.execute {
                    val (r, id) = PluginWidgetsTv.adicionar(applicationContext, info, cl, ca)
                    runOnUiThread {
                        if (isDestroyed) return@runOnUiThread
                        if (r != PluginWidgetsTv.Resultado.OK) { aviso(r); return@runOnUiThread }
                        escolhido = id; modo = Modo.MOVER
                        ler()
                        // Widget com tela de ajustes: abre agora; desistir dela tira o widget (como nos launchers).
                        if (PluginWidgetsTv.temAjuste(info)) {
                            novoPendente = id
                            if (!PluginWidgetsTv.abrirAjuste(this, id, PEDIDO_NOVO)) { novoPendente = -1; PluginWidgetsTv.remover(this, id); ler(); aviso(PluginWidgetsTv.Resultado.FALHOU) }
                        }
                    }
                }
            }
        }
    }

    @Deprecated("Activity.onActivityResult: a tela de ajustes do widget responde por aqui (AppWidgetHost)")
    override fun onActivityResult(pedido: Int, resultado: Int, dados: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(pedido, resultado, dados)
        if (pedido == PEDIDO_NOVO && novoPendente >= 0) {
            if (resultado != RESULT_OK) PluginWidgetsTv.remover(this, novoPendente)
            novoPendente = -1
        }
        ler()
    }

    private fun aviso(r: PluginWidgetsTv.Resultado) {
        val t = when (r) {
            PluginWidgetsTv.Resultado.LIMITE -> getString(R.string.plg_limite, PluginWidget.MAX)
            PluginWidgetsTv.Resultado.SEM_PERMISSAO -> getString(R.string.ext_widget_sem_permissao)
            PluginWidgetsTv.Resultado.BLOQUEADO -> getString(R.string.ext_off_blocked)
            PluginWidgetsTv.Resultado.CHEIO -> getString(R.string.wdg_cheio)
            else -> getString(R.string.ext_widget_falhou)
        }
        Toast.makeText(this, t, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val PEDIDO_NOVO = 0x5701
        const val PEDIDO_AJUSTE = 0x5702
        /** O véu sobre a HOME no modo de edição (a HOME aparece atrás, escurecida). */
        const val VEU = 0xB30B0B0B.toInt()
    }
}
