package br.paulo.tvlauncher

import br.paulo.tvlauncher.settings.Planos
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import br.paulo.tvlauncher.network.EntreTvs
import br.paulo.tvlauncher.player.JuntoRegras
import org.json.JSONObject

/**
 * "Enviar para outra TV" (degrau 4, item 5, 17/11/2026): parou na sala, continua no quarto. Para quem não tem Trakt/Simkl
 * e para o que eles não cobrem (arquivos da rede de casa e canais). Só entre TVs da mesma conta ([EntreTvs], mesmo
 * opt-in "TVs da família"). Vai só título, chave, posição e, para arquivo da rede da casa, o endereço (sem senha:
 * FontesMidia não guarda senha em endereço). Regras e validação em [JuntoRegras.Continuar].
 *
 * Na outra TV aparece o cartão "Continuar de onde parou?". Arquivo da rede: abre no mesmo ponto. Canal: abre a TV ao
 * vivo no canal de mesmo nome. Filme/série dos serviços: o cartão diz onde parou e abre a busca (cada casa tem a
 * própria fonte; não dá para abrir o mesmo vídeo de lá).
 */
object ContinuarOutraTv {
    const val ACAO_ENVIAR = "br.paulo.tvlauncher.CONTINUAR_ENVIAR"

    @Volatile private var pendente: Pair<JuntoRegras.Continuar, Long>? = null
    /** Tokens dos pedidos que ESTA TV mandou (5 min): a outra TV confere aqui antes de mostrar o cartão. */
    private val enviados = HashMap<String, Long>()
    /** Pedidos recebidos na última hora (limite igual ao dos convites). */
    private val recebidosHora = ArrayList<Long>()

    /** Do player (processo ":player"): manda o pedido ao principal, que fala com a outra TV. */
    fun enviarDoPlayer(a: Activity, host: String, titulo: String, chave: String, posMs: Long, url: String?) {
        a.sendBroadcast(Intent(ACAO_ENVIAR).setClassName(a.packageName, JuntoReceiver::class.java.name)
            .putExtra("host", host).putExtra("titulo", titulo).putExtra("chave", chave).putExtra("pos", posMs).putExtra("url", url))
    }

    fun doPlayer(ctx: Context, i: Intent) {
        val app = ctx.applicationContext
        val host = i.getStringExtra("host") ?: return
        val eu = EntreTvs.meuHost(app) ?: return
        val c = JuntoRegras.Continuar(i.getStringExtra("titulo").orEmpty(), i.getStringExtra("chave") ?: return,
            i.getLongExtra("pos", 0), i.getStringExtra("url"), eu, br.paulo.tvlauncher.network.Cenas.gerarToken())
        synchronized(enviados) {
            val agora = System.currentTimeMillis()
            enviados.entries.removeAll { agora - it.value > 5 * 60_000L }
            enviados[c.token] = agora
        }
        Fundo.rodar("continuar-enviar") {
            val r = EntreTvs.chamar(app, host, "/continuar", JuntoRegras.continuarParaJson(c))
            val nome = JuntoRegras.nomeBonito(host)
            AvisoTv.rapido(app, app.getString(R.string.cont_titulo),
                if (r.codigo == 200) app.getString(R.string.cont_enviado, nome) else app.getString(R.string.cont_falhou, nome))
        }
    }

    /** POST /continuar vindo de outra TV da conta. */
    fun receber(ctx: Context, corpo: JSONObject?): Pair<Int, JSONObject> {
        val app = ctx.applicationContext
        if (!EntreTvs.ligado(app) || !Planos.permite(app, Planos.Recurso.CONTINUAR_OUTRA_TV))   // plano: Pro
            return 403 to JSONObject().put("erro", "desligado")
        val c = JuntoRegras.continuarDeJson(corpo) ?: return 400 to JSONObject().put("erro", "pedido")
        if (EntreTvs.outras(app).none { it.host == c.de }) return 403 to JSONObject().put("erro", "tv")
        synchronized(recebidosHora) {
            val (pode, nova) = JuntoRegras.contarConvite(recebidosHora, System.currentTimeMillis())
            recebidosHora.clear(); recebidosHora.addAll(nova)
            if (!pode) return 429 to JSONObject().put("erro", "limite")
        }
        // Confere de volta, como o convite da Sessão junto: a TV "de" mandou mesmo este pedido? (revisor de segurança,
        // 24/11/2026: sem isto qualquer um na internet abria o cartão com um endereço dele.)
        val volta = EntreTvs.chamar(app, c.de, "/continuar/${c.token}/estado")
        if (volta.codigo != 200 || volta.json?.optBoolean("ok") != true) return 403 to JSONObject().put("erro", "conferir")
        pendente = c to System.currentTimeMillis()
        try {
            app.startActivity(Intent(app, ContinuarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            AvisoTv.mostrar(app, app.getString(R.string.cont_pergunta), "${c.titulo} · ${JuntoRegras.tempo(c.posMs)}", 30,
                som = true, guardarSeApagada = true)
        }
        return 200 to JSONObject().put("ok", true)
    }

    /** GET /continuar/<token>/estado: a outra TV conferindo que o pedido saiu daqui. Uso único. */
    fun estado(token: String): Pair<Int, JSONObject> {
        val agora = System.currentTimeMillis()
        val ok = synchronized(enviados) {
            val k = enviados.keys.firstOrNull { java.security.MessageDigest.isEqual(it.toByteArray(), token.toByteArray()) }
            val t = k?.let { enviados.remove(it) }
            t != null && agora - t <= 5 * 60_000L
        }
        return if (ok) 200 to JSONObject().put("ok", true) else 404 to JSONObject().put("erro", "não existe")
    }

    /** O pedido vale 30 min (depois disso o cartão não abre mais). */
    fun pendente(): JuntoRegras.Continuar? = pendente?.takeIf { System.currentTimeMillis() - it.second < 30 * 60_000L }?.first
    fun esquecer() { pendente = null }
    internal fun paraTeste(c: JuntoRegras.Continuar?) { pendente = c?.let { it to System.currentTimeMillis() } }
}

/** O cartão "Continuar de onde parou?" (processo principal). */
class ContinuarActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (VigiaAtualizacao.fechada(this)) return
        // Sem convite valendo (venceu enquanto a pessoa chegava aqui): diz o motivo em vez de piscar e sumir.
        val c = ContinuarOutraTv.pendente() ?: run {
            AvisoTv.rapido(this, getString(R.string.aviso_continuar_titulo), getString(R.string.aviso_continuar_texto)); finish(); return
        }
        val tipo = JuntoRegras.tipoDaChave(c.chave)
        val podeAbrir = c.url != null || tipo == JuntoRegras.Tipo.CANAL
        val depois = Estilo.botao(this, getString(R.string.junto_agora_nao), primario = false).apply {
            setOnClickListener { ContinuarOutraTv.esquecer(); finish() }
        }
        val continuar = Estilo.botao(this, getString(if (podeAbrir) R.string.cont_continuar else R.string.cont_buscar)).apply {
            setOnClickListener {
                ContinuarOutraTv.esquecer()
                try {
                    when {
                        c.url != null -> VodActivity.abrirUrlEm(this@ContinuarActivity, c.url, c.titulo, c.posMs)
                        tipo == JuntoRegras.Tipo.CANAL -> startActivity(Intent(this@ContinuarActivity, PlayerActivity::class.java)
                            .putExtra(PlayerActivity.EXTRA_CANAL_NOME, c.chave.removePrefix("canal:")))
                        else -> startActivity(Intent(this@ContinuarActivity, BuscaActivity::class.java))
                    }
                } catch (_: Exception) {}
                finish()
            }
        }
        val texto = getString(if (tipo == JuntoRegras.Tipo.CANAL) R.string.cont_texto_canal else R.string.cont_texto,
            c.titulo, JuntoRegras.tempo(c.posMs), JuntoRegras.nomeBonito(c.de))
        setContentView(Estilo.dialogo(this, null, getString(R.string.cont_pergunta), texto, listOf(depois, continuar), larguraDp = 760))
        continuar.post { continuar.requestFocus() }
    }
}
