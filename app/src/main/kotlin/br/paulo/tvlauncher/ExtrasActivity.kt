package br.paulo.tvlauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import br.paulo.tvlauncher.alarm.HoraDormir
import br.paulo.tvlauncher.alarm.HoraDormirTv
import br.paulo.tvlauncher.family.PinTv
import br.paulo.tvlauncher.family.TempoTela
import br.paulo.tvlauncher.family.TempoTelaTv
import br.paulo.tvlauncher.network.AcordarPc
import br.paulo.tvlauncher.network.ConsumoRedeTv
import br.paulo.tvlauncher.network.DiarioDns
import br.paulo.tvlauncher.observer.Caches
import br.paulo.tvlauncher.panel.PainelArmazem
import br.paulo.tvlauncher.privilege.ExtrasDO
import br.paulo.tvlauncher.settings.ComandosExtras
import br.paulo.tvlauncher.settings.Extras
import br.paulo.tvlauncher.settings.ExtrasAbas
import br.paulo.tvlauncher.settings.FiltroNoturno

/**
 * Ajustes finos e ferramentas das ideias de 10/11/2026 ([Extras]). As chaves liga/desliga foram para as abas das
 * Configurações em 14/11/2026 (ideia 46, [ExtrasAbas]).
 *
 * Linhas de número (início e fim do filtro noturno, força): OK avança, ◀ ▶ ajustam para trás e para a frente.
 * Abre pelo adb (`--es o extras`, ver [ComandosExtras]) e, depois da costura final, por uma entrada nas Configurações.
 */
class ExtrasActivity : Activity() {
    companion object {
        /** Título da linha que recebe o foco ao abrir (vem da busca dos ajustes). */
        const val EXTRA_FOCO = "foco"
        /** Linhas do Extras que a busca dos ajustes acha (ConfigSystemTab as põe no índice; #38 do teste 100%). */
        val LINHAS_DA_BUSCA = listOf(R.string.pb_filtro, R.string.dormir_titulo, R.string.dormir_teto, R.string.tela_titulo,
            R.string.extras_caches, R.string.extras_consumo, R.string.extras_tocando, R.string.tec_titulo, R.string.extras_teclas,
            R.string.extras_calibracao, R.string.casa_titulo, R.string.extras_acordar_pc, R.string.extras_diario_ver,
            R.string.extras_diario_apagar)
    }
    private lateinit var coluna: LinearLayout
    private lateinit var tela: ColumnsScreen

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (VigiaAtualizacao.fechada(this)) return   // modo seguro: tela que derrubou esta versão (VigiaAtualizacao)
        // Molde das telas de ajuste em três colunas (Paulo, 26/11/2026): seções à esquerda, ajustes com o valor à
        // direita no meio e, à direita, o que o ajuste faz agora. Antes: uma coluna só esticada nos 960 dp.
        tela = ColumnsScreen(this, getString(R.string.extras_titulo))
        montar()
        setContentView(tela.root)
        atualizarTextos()
        // Vindo da busca: foco (e rolagem) na linha procurada; seção (ex.: "Hora de dormir") = a 1ª linha dela.
        val foco = intent?.getStringExtra(EXTRA_FOCO)
        tela.start(foco?.let { f -> linhas.firstOrNull { it.rotulo().startsWith(f) }?.view ?: tela.firstRowOf(f) })
    }

    override fun onResume() { super.onResume(); atualizarTextos() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!tela.back()) @Suppress("DEPRECATION") super.onBackPressed()
    }

    /** Linha dos ajustes: a linha, o rótulo e o valor à direita, e de onde vem o texto de cada um. */
    private class Linha(val view: android.view.View, val label: TextView, val value: TextView, val rotulo: () -> String, val valor: () -> String?)
    private val linhas = mutableListOf<Linha>()

    private fun montar() {
        // As chaves liga/desliga moraram aqui até 14/11/2026; agora cada uma está na aba dela das Configurações (ideia 46,
        // ExtrasAbas). Aqui ficam os ajustes finos e as ferramentas.
        // Agrupado em seções (crítica de design 19/11/2026): antes eram 20 linhas iguais com "Hora de dormir:" repetido.
        secao(R.string.pb_filtro) { infoFiltro() }
        numero(R.string.extras_filtro_inicio, FiltroNoturno.CHAVE_INICIO, FiltroNoturno.PADRAO_INICIO, 30, 0, 24 * 60 - 30, hora = true)
        numero(R.string.extras_filtro_fim, FiltroNoturno.CHAVE_FIM, FiltroNoturno.PADRAO_FIM, 30, 0, 24 * 60 - 30, hora = true)
        numero(R.string.extras_filtro_forca, FiltroNoturno.CHAVE_FORCA, FiltroNoturno.PADRAO_FORCA, 5, 5, 100, hora = false)
        secao(R.string.dormir_titulo) { infoDormir() }
        // Hora de dormir por dia: a linha "dia" escolhe o que as duas de baixo editam (todos = o padrão; um dia = só ele).
        linhaDormir({ getString(R.string.dormir_dia) }, { textoDia() }, 1, protegida = false) { delta ->
            diaSel = Math.floorMod(diaSel + 1 + delta, 8) - 1   // -1 (todos), 0 (dom) .. 6 (sáb)
        }
        linhaDormir({ getString(R.string.dormir_inicio) }, { hhmm(janelaSel().inicio) }, 30) { d -> mudarJanela(d, 0) }
        linhaDormir({ getString(R.string.dormir_fim) }, { hhmm(janelaSel().fim) }, 30) { d -> mudarJanela(0, d) }
        linhaDormir({ getString(R.string.dormir_voltar_padrao) }, { null }, 0) { _ ->
            if (diaSel >= 0) HoraDormirTv.definirDias(this, HoraDormirTv.dias(this).toMutableList().also { it[diaSel] = null })
            // Com "todos" já é o padrão: diz isso em vez de não fazer nada (TodoBotaoFazAlgoTest, 25/11/2026).
            else aviso(getString(R.string.dormir_dia) + ": " + textoDia())
        }
        linhaDormir({ getString(R.string.dormir_teto) }, { "${Extras.numero(this, HoraDormir.CHAVE_TETO, HoraDormir.PADRAO_TETO)}%" }, 5) { d ->
            val v = Math.floorMod(Extras.numero(this, HoraDormir.CHAVE_TETO, HoraDormir.PADRAO_TETO) + d, 105)   // 0..100, dá a volta
            Extras.definirNumero(this, HoraDormir.CHAVE_TETO, v)
        }
        secao(R.string.tela_titulo) { infoTela() }
        // Tempo de tela por dia (ideia 33): a cota (OK = +30 min; ← →) e "mais 30 min hoje". Mudar pede o PIN com a chave ligada.
        linhaTela({ getString(R.string.col_screen_daily) }, { TempoTela.texto(TempoTelaTv.cota(this) * 60_000L, getString(R.string.tela_h), getString(R.string.tela_min)) }) { d ->
            TempoTelaTv.definirCota(this, TempoTela.proximaCota(TempoTelaTv.cota(this), d * TempoTela.PASSO_COTA))
        }
        linhaTela({ getString(R.string.tela_mais, TempoTela.EXTRA_MIN) }, { null }, setas = false) { _ ->
            TempoTelaTv.darMais(this); aviso(getString(R.string.tela_mais_ok, TempoTela.EXTRA_MIN))
        }
        secao(R.string.cfg2_sec_ferramentas)
        linha({ getString(R.string.extras_caches) }, { getString(R.string.extras_livre, Caches.livreTexto()) }) {
            emSegundoPlano { Caches.limpar(it).textoTv(it) }
        }
        linha({ getString(R.string.extras_consumo) }) { emSegundoPlano(dialogo = R.string.extras_consumo) { ConsumoRedeTv.texto(it, 7) } }   // o rótulo diz "7 dias": antes media 1
        linha({ getString(R.string.extras_tocando) }) { emSegundoPlano(dialogo = R.string.extras_tocando) { TocandoAgoraTv.texto(it) } }
        linha({ getString(R.string.tec_titulo) }) { startActivity(Intent(this, TeclasActivity::class.java)) }   // ideia 6
        linha({ getString(R.string.extras_teclas) }) { startActivity(Intent(this, TesteTeclasActivity::class.java)) }
        linha({ getString(R.string.extras_calibracao) }) { startActivity(Intent(this, CalibracaoActivity::class.java)) }
        linha({ getString(R.string.casa_titulo) }) { startActivity(Intent(this, CasaActivity::class.java)) }
        linha({ getString(R.string.extras_acordar_pc) }, { AcordarPc.macGuardado(this).ifEmpty { null } }) {
            acordarPc()
        }
        linha({ getString(R.string.extras_diario_ver) }, { DiarioDns.erro(this).ifEmpty { null } }) {
            verDiario()
        }
        linha({ getString(R.string.extras_diario_apagar) }) {
            Fundo.rodar { DiarioDns.apagarTudo(applicationContext) }
            aviso(getString(R.string.extras_diario_apagado))
        }
        // OK confere de novo e diz o resultado (25/11/2026, TodoBotaoFazAlgoTest: a linha focava e o OK não fazia nada).
        linha({ if (ExtrasDO.atualizacaoPendente(this) != null) getString(R.string.extras_ota_pendente) else getString(R.string.extras_ota_nenhuma) }) {
            aviso(if (ExtrasDO.atualizacaoPendente(this) != null) getString(R.string.extras_ota_pendente) else getString(R.string.extras_ota_nenhuma))
        }
    }


    /** Rótulo de seção do sistema de design (o mesmo das Configurações). */
    private fun secao(texto: Int, info: (() -> ColumnsScreen.Info?)? = null) { coluna = tela.section(getString(texto), info) }

    private fun linha(rotulo: () -> String, valor: () -> String? = { null }, ao: () -> Unit): android.view.View {
        // A linha das Configurações: rótulo à esquerda, valor à direita.
        val (v, label, value) = tela.valueRow()
        v.setOnClickListener { ao(); atualizarTextos() }
        coluna.addView(v, tela.lpRow())
        linhas += Linha(v, label, value, rotulo, valor)
        return v
    }

    private fun agoraMin(): Int = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }

    /** Coluna da direita do filtro noturno: ligado agora, ou a que horas liga; e o que ele faz. */
    private fun infoFiltro(): ColumnsScreen.Info {
        val ini = Extras.numero(this, FiltroNoturno.CHAVE_INICIO, FiltroNoturno.PADRAO_INICIO)
        val fim = Extras.numero(this, FiltroNoturno.CHAVE_FIM, FiltroNoturno.PADRAO_FIM)
        val forca = Extras.numero(this, FiltroNoturno.CHAVE_FORCA, FiltroNoturno.PADRAO_FORCA)
        val agora = when {
            !Extras.ligado(this, Extras.Chave.FILTRO_NOTURNO) -> getString(R.string.col_filter_off)
            FiltroNoturno.dentro(agoraMin(), ini, fim) -> getString(R.string.col_filter_on)
            else -> getString(R.string.col_filter_at, hhmm(ini))
        }
        return ColumnsScreen.Info(getString(R.string.col_now), agora, getString(R.string.col_filter_body, hhmm(ini), hhmm(fim), forca))
    }

    /** Hora de dormir: o estado de hoje e o que vale na janela do dia em edição. */
    private fun infoDormir(): ColumnsScreen.Info {
        val hoje = HoraDormir.doDia(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1, HoraDormirTv.dias(this), padraoDormir())
        val agora = when {
            !Extras.ligado(this, Extras.Chave.HORA_DORMIR) -> getString(R.string.col_sleep_off)
            HoraDormir.dentro(agoraMin(), hoje.inicio, hoje.fim) -> getString(R.string.col_sleep_now)
            else -> getString(R.string.col_sleep_at, hhmm(hoje.inicio))
        }
        val j = janelaSel()
        val teto = Extras.numero(this, HoraDormir.CHAVE_TETO, HoraDormir.PADRAO_TETO)
        return ColumnsScreen.Info(getString(R.string.col_now), agora, getString(R.string.col_sleep_body, hhmm(j.inicio), hhmm(j.fim), teto))
    }

    /** Tempo de tela: quanto falta hoje e o que acontece quando acaba. */
    private fun infoTela(): ColumnsScreen.Info {
        val agora = if (!Extras.ligado(this, Extras.Chave.TEMPO_TELA)) getString(R.string.col_screen_off)
            else getString(R.string.col_screen_left, TempoTela.texto(TempoTelaTv.restanteMs(this), getString(R.string.tela_h), getString(R.string.tela_min)))
        return ColumnsScreen.Info(getString(R.string.col_now), agora, getString(R.string.col_screen_body))
    }

    private fun numero(rotulo: Int, chave: String, padrao: Int, passo: Int, min: Int, max: Int, hora: Boolean) {
        fun valor() = Extras.numero(this, chave, padrao)
        fun mudar(delta: Int) {
            var v = valor() + delta
            if (v > max) v = min; if (v < min) v = max   // dá a volta
            Extras.definirNumero(this, chave, v)
            FiltroNoturno.avaliar()
            atualizarTextos()
        }
        val t = linha({ getString(rotulo) }, { if (hora) hhmm(valor()) else "${valor()}%" }) { mudar(passo) }
        t.setOnKeyListener { _, code, e ->
            if (e.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { mudar(-passo); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { mudar(passo); true }
                else -> false
            }
        }
    }

    /** Dia que as linhas de horário editam: -1 = todos (o padrão), 0 = domingo ... 6 = sábado. */
    private var diaSel = -1

    private fun padraoDormir() = HoraDormir.Janela(HoraDormirTv.inicio(this), HoraDormirTv.fim(this))
    private fun janelaSel(): HoraDormir.Janela =
        if (diaSel < 0) padraoDormir() else HoraDormir.doDia(diaSel, HoraDormirTv.dias(this), padraoDormir())

    private fun textoDia(): String {
        if (diaSel < 0) return getString(R.string.dormir_todos)
        val proprio = HoraDormirTv.dias(this)[diaSel] != null
        return HoraDormir.NOMES_DIAS[diaSel] + " (" + getString(if (proprio) R.string.dormir_proprio else R.string.dormir_segue) + ")"
    }

    /** Anda o início/fim do dia escolhido (ou do padrão) em [di]/[df] minutos, dando a volta no relógio. */
    private fun mudarJanela(di: Int, df: Int) {
        val j = janelaSel()
        val nova = HoraDormir.Janela(Math.floorMod(j.inicio + di, 24 * 60), Math.floorMod(j.fim + df, 24 * 60))
        if (diaSel < 0) {
            Extras.definirNumero(this, HoraDormir.CHAVE_INICIO, nova.inicio); Extras.definirNumero(this, HoraDormir.CHAVE_FIM, nova.fim)
        } else HoraDormirTv.definirDias(this, HoraDormirTv.dias(this).toMutableList().also { it[diaSel] = nova })
    }

    /**
     * Linha da hora de dormir: OK = [passo] para a frente; ← → = para trás/para a frente. Com a hora de dormir ligada,
     * mudar pede o PIN (senão a criança adianta o fim); escolher o dia ([protegida] = false) não muda nada e não pede.
     */
    private fun linhaDormir(texto: () -> String, valor: () -> String?, passo: Int, protegida: Boolean = true, aplicar: (Int) -> Unit) {
        fun mudar(delta: Int) {
            val fazer = { aplicar(delta); atualizarTextos() }
            if (protegida && Extras.ligado(this, Extras.Chave.HORA_DORMIR) && PainelArmazem.temPin(this))
                PinTv.pedir(this, getString(R.string.dormir_pin), familia = true) { certo -> if (certo) fazer() }
            else fazer()
        }
        val t = linha(texto, valor) { mudar(passo) }
        t.setOnKeyListener { _, code, e ->
            if (e.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { mudar(-passo); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { mudar(passo); true }
                else -> false
            }
        }
    }

    /** Linha do tempo de tela: com a chave ligada, mudar pede o PIN (das crianças ou do painel). [aplicar] recebe +1 ou -1. */
    private fun linhaTela(texto: () -> String, valor: () -> String?, setas: Boolean = true, aplicar: (Int) -> Unit) {
        fun mudar(d: Int) {
            val fazer = { aplicar(d); atualizarTextos() }
            if (Extras.ligado(this, Extras.Chave.TEMPO_TELA) && PainelArmazem.temPin(this))
                PinTv.pedir(this, getString(R.string.tela_pin), familia = true) { certo -> if (certo) fazer() }
            else fazer()
        }
        val t = linha(texto, valor) { mudar(1) }
        if (setas) t.setOnKeyListener { _, code, e ->
            if (e.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { mudar(-1); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { mudar(1); true }
                else -> false
            }
        }
    }

    /** Roda [tarefa] fora da thread da tela e mostra o resultado (aviso curto, ou caixa com [dialogo] de título). */
    private fun emSegundoPlano(dialogo: Int? = null, tarefa: (android.content.Context) -> String) {
        val app = applicationContext
        // "Um momento…" só se demorar: antes ficava na tela por cima da caixa do resultado (teste 100%, Sala, 23/11/2026).
        val pronto = java.util.concurrent.atomic.AtomicBoolean(false)
        val aguarde = Runnable { if (!pronto.get() && !isDestroyed) aviso(getString(R.string.extras_aguarde)) }
        window.decorView.postDelayed(aguarde, 700)
        Fundo.rodar {
            val r = tarefa(app)
            pronto.set(true)
            runOnUiThread {
                window.decorView.removeCallbacks(aguarde)
                if (isDestroyed) return@runOnUiThread
                if (dialogo == null) aviso(r)
                else CaixaTv(this).setTitle(dialogo).setMessage(r).setPositiveButton(android.R.string.ok, null).show()
                atualizarTextos()
            }
        }
    }

    private fun hhmm(min: Int) = "%02d:%02d".format(min / 60, min % 60)


    private fun acordarPc() {
        if (AcordarPc.macGuardado(this).isEmpty()) {
            aviso(getString(R.string.extras_acordar_sem_mac)); return
        }
        val app = applicationContext
        Fundo.rodar {
            val ok = AcordarPc.acordar(app)
            runOnUiThread { aviso(getString(if (ok) R.string.extras_acordar_ok else R.string.extras_acordar_falhou)) }
        }
    }

    private fun verDiario() {
        val app = applicationContext
        Fundo.rodar {
            val texto = DiarioDns.resumo(app)
            runOnUiThread {
                // A tela pode ter fechado enquanto o diário carregava: mostrar a caixa numa tela destruída derruba o app.
                if (isFinishing || isDestroyed) return@runOnUiThread
                CaixaTv(this).setTitle(R.string.extras_diario_ver).setMessage(texto)
                    .setPositiveButton(android.R.string.ok, null).show()
            }
        }
    }

    /** Aviso padrão (a pílula do sistema de design) em vez do Toast do Android. */
    private fun aviso(texto: String) = AvisoTv.rapido(this, getString(R.string.extras_titulo), texto)

    private fun atualizarTextos() {
        for (l in linhas) {
            l.label.text = l.rotulo()
            val v = l.valor()
            l.value.text = v ?: ""
            l.value.visibility = if (v.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        }
        if (::tela.isInitialized) tela.refresh()
    }
}
