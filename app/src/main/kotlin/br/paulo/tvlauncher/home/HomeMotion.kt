package br.paulo.tvlauncher.home

import android.content.Intent
import android.util.Log
import br.paulo.tvlauncher.AddonsActivity
import br.paulo.tvlauncher.AlarmesActivity
import br.paulo.tvlauncher.CasaActivity
import br.paulo.tvlauncher.ConfigLauncherActivity
import br.paulo.tvlauncher.FineTuneActivity
import br.paulo.tvlauncher.PainelRapidoActivity
import br.paulo.tvlauncher.Estilo
import br.paulo.tvlauncher.FilmesActivity
import br.paulo.tvlauncher.JogosActivity
import br.paulo.tvlauncher.MainActivity
import br.paulo.tvlauncher.MainActivity.Companion.CONTINUAR_MAX
import br.paulo.tvlauncher.MainActivity.Item
import br.paulo.tvlauncher.MidiaRedeActivity
import br.paulo.tvlauncher.PluginsTv
import br.paulo.tvlauncher.PodcastsActivity
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.RadioActivity
import br.paulo.tvlauncher.SeriesActivity
import br.paulo.tvlauncher.games.JogosTv
import br.paulo.tvlauncher.listen.Podcasts
import br.paulo.tvlauncher.observer.DesempenhoTv
import br.paulo.tvlauncher.player.AddonsTv
import br.paulo.tvlauncher.player.BarraTempo
import br.paulo.tvlauncher.player.PlayerArmazem
import br.paulo.tvlauncher.player.Posicao
import br.paulo.tvlauncher.player.Programa
import br.paulo.tvlauncher.player.Vod
import br.paulo.tvlauncher.player.item
import br.paulo.tvlauncher.screensaver.WallpaperPluginView

/** Movimento dos ícones da cruz (MovimentoAdaptativo). Saiu da MainActivity na refatoração de 21/11/2026. */

internal fun MainActivity.semAnimacoes() =
    runCatching { android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

/** Na volta da HOME: o nível que vale (aparelho, rebaixado, forçado pelo adb) e o juiz dos quadros. */
internal fun MainActivity.ligarMovimento() {
    val c = cruz ?: return
    c.nivel = MovimentoAdaptativo.atual(this, semAnimacoes())
    DesempenhoTv.limiteMs = MovimentoAdaptativo.limiteQuadroMs(runCatching { windowManager.defaultDisplay.refreshRate }.getOrDefault(60f))
    DesempenhoTv.aoPesado = if (c.nivel > MovimentoAdaptativo.Nivel.ECONOMICO) ({ ui.post { rebaixarMovimento() } }) else null
}

internal fun MainActivity.rebaixarMovimento() {
    val c = cruz ?: return
    if (!homeNaFrente) return
    val novo = MovimentoAdaptativo.descer(c.nivel)
    if (novo == c.nivel) { DesempenhoTv.aoPesado = null; return }
    Log.i("TvLauncher.Movimento", "quadros lentos demais: ${c.nivel} -> $novo")
    MovimentoAdaptativo.rebaixar(this, novo)
    c.nivel = novo
    if (novo <= MovimentoAdaptativo.Nivel.ECONOMICO) DesempenhoTv.aoPesado = null
}

internal fun MainActivity.espelho(acao: () -> Unit) = object : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun afterTextChanged(s: android.text.Editable?) = acao()
}

/**
 * Arte do item em foco (LayoutHome.arteCruz): a mesma capa à direita da tela antiga (CapaFoco, meia resolução,
 * escurecida), agora seguindo o item da cruz. TV, Séries, Filmes, Na rede e Jogos usam a arte do card (Séries e
 * Filmes: a cena do último título); um app, o banner dele; o resto, o degradê da cor do grupo.
 */
internal fun MainActivity.arteDoItem(item: HomeCruz.Item) {
    // Plugin de fundo que pediu o foco (a arte do item vem dele): nome e pacote do item, nada mais.
    (fundo as? WallpaperPluginView)?.focar(item.nome, pacoteDoItem(item.id))
    if (capa.desligada) return
    val id = item.id.removePrefix(PREFIXO_CONTINUAR)   // o Continuar de cada mundo mostra a arte do que ele retoma
    when {
        id == "tv" || id == "c:tv" -> mostrarCapaAtalho(IconeApp.Tipo.TV)
        id == "series" || id == "c:serie" -> mostrarCapaAtalho(IconeApp.Tipo.SERIES)
        id == "filmes" || id == "c:filme" -> mostrarCapaAtalho(IconeApp.Tipo.FILMES)
        id == "rede" -> mostrarCapaAtalho(IconeApp.Tipo.REDE)
        id == "central" -> mostrarCapaAtalho(IconeApp.Tipo.JOGOS)
        id.startsWith("app:") -> mostrarCapaDoApp(id.removePrefix("app:"), null)
        id.startsWith("c:jogo:") -> mostrarCapaDoApp(id.removePrefix("c:jogo:"), null)
        id.startsWith("c:midia:") -> mostrarCapaDoApp(id.removePrefix("c:midia:"), null)
        else -> {
            capaPedida = null
            val cor = when (cruz?.categoriaAtual()?.cat) {
                CruzLogica.Categoria.OUVIR -> 0xFF2C6E68.toInt()
                CruzLogica.Categoria.JOGAR -> 0xFF3A3F9E.toInt()
                CruzLogica.Categoria.APLICATIVOS -> 0xFF3A3640.toInt()
                else -> 0xFF8A4A2A.toInt()
            }
            capa.mostrar(null, intArrayOf(Estilo.matiz(cor, 0.6f), Estilo.matiz(cor, 0.35f)))
        }
    }
}

/** O pacote por trás de um item da cruz ("app:", "c:jogo:", "c:midia:"), ou null (atalhos nossos). */
internal fun MainActivity.pacoteDoItem(id0: String): String? = id0.removePrefix(PREFIXO_CONTINUAR).let { id -> when {
    id.startsWith("app:") -> id.removePrefix("app:")
    id.startsWith("c:jogo:") -> id.removePrefix("c:jogo:")
    id.startsWith("c:midia:") -> id.removePrefix("c:midia:")
    id.startsWith("card:") -> id.removePrefix("card:").toLongOrNull()?.let { n -> cartoesApps.firstOrNull { it.id == n }?.pacote }
    else -> null
} }

/** Proposta B (AppShowcase): com um app escolhido que tem cartões, a vitrine dele; qualquer outro item, nada. */
internal fun MainActivity.vitrineDoItem(item: HomeCruz.Item) {
    val v = vitrine ?: return
    val p = pacoteDoItem(item.id) ?: return v.esconder()
    v.mostrar(p, nomeDe(p), AppChannels.doApp(cartoesApps, p).take(AppChannels.MAX_VITRINE), item.icone)
}

/** Id do item "Continuar" no topo de cada mundo: este prefixo + o id do que ele retoma (não repete o id da coluna). */
internal const val PREFIXO_CONTINUAR = "cont:"

/**
 * O "Continuar" de um mundo (Paulo, 21/11/2026: "cada categoria recebe um continuar como primeiro item"): a última coisa
 * daquele mundo, com o nome "Continuar", o que é + o título embaixo, a barra de progresso e a mesma ação (OK continua
 * de onde parou). Segurar OK: os outros recentes do mundo e, se o item tinha, as opções dele. null = nada recente.
 */
internal fun MainActivity.itemContinuar(k: CruzLogica.Categoria, lista: List<Pair<Long, HomeCruz.Item>>): HomeCruz.Item? {
    val ordem = CruzLogica.recentes(lista, MainActivity.CONTINUAR_MAX)
    val r = ordem.firstOrNull() ?: return null
    val outros = ordem.drop(1)
    val menu: (() -> Unit)? = if (outros.isEmpty() && r.opcoes == null) null else ({
        val rotulos = outros.map { o -> listOfNotNull(o.nome, o.sub.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }).joinToString(" · ") } +
            listOfNotNull(r.opcoes?.let { getString(R.string.cont_mais_opcoes) })
        menuAberto = Estilo.lista(this, getString(R.string.cruz_continuar), rotulos) { i ->
            if (i < outros.size) outros[i].abrir() else r.opcoes?.invoke()
        }
    })
    return HomeCruz.Item(PREFIXO_CONTINUAR + r.id, getString(R.string.cruz_continuar), CruzLogica.linhas(r.nome, *r.sub.split("\n").toTypedArray()),
        icCruz(PREFIXO_CONTINUAR + k.id, IconeCruz.Tipo.CONTINUAR), r.abrir, opcoes = menu, aoEscolher = r.aoEscolher,
        progresso = r.progresso, situacao = r.situacao)
}

/**
 * Linha de baixo do canal de TV na cruz (Paulo, 18/11/2026: "as informações ali são ótimas, usar da melhor maneira"),
 * uma informação por linha, na ordem em que se lê: o canal; o programa de agora e quanto falta; o próximo e a hora.
 * [comPrograma] = false quando o programa já é o título do item (Continuar). Sem guia, só o canal.
 */
/** O programa no ar e quanto falta ("SP1 · faltam 58 min"); [comPrograma] = false só o quanto falta. */
internal fun MainActivity.linhaAgoraTv(agora: Programa?, ms: Long, comPrograma: Boolean): String {
    val falta = agora?.let { ((it.fim - ms) / 60_000).toInt() }?.takeIf { it > 0 }?.let { BarraTempo.faltam(this, duracaoTexto(it)) }
    return listOfNotNull(agora?.titulo?.trim()?.takeIf { comPrograma && it.isNotEmpty() }, falta).joinToString(" · ")
}

internal fun MainActivity.linhasTvCruz(u: PlayerArmazem.Ultimo, agora: Programa?, ms: Long, comPrograma: Boolean): String {
    val hora = android.text.format.DateFormat.getTimeFormat(this)
    val canal = if (u.nome.isBlank()) getString(R.string.home_dest_tv, u.numero) else getString(R.string.cruz_tv_canal, u.numero, u.nome.trim())
    val linhaAgora = linhaAgoraTv(agora, ms, comPrograma)
    val depois = u.programas.firstOrNull { it.inicio >= (agora?.fim ?: ms) && it.titulo.isNotBlank() }
        ?.let { getString(R.string.cruz_tv_depois, hora.format(java.util.Date(it.inicio)), it.titulo.trim()) }
    return CruzLogica.linhasTv(canal, linhaAgora, depois)
}

/** Refaz a lista da cruz com os dados de agora. Barato: a HomeCruz só recria views quando os ids mudam. */
internal fun MainActivity.atualizarCruz() {
    val c = cruz ?: return
    if (!cruzContinuarLido) return
    val agora = System.currentTimeMillis()
    val programa = ultimoTv?.programas?.firstOrNull { it.inicio <= agora && agora < it.fim && it.titulo.isNotBlank() }
    fun faltaFilme(f: PlayerArmazem.FichaHome): String? = posicoesHome[f.chave]?.takeIf { it.duracao > 0 && Vod.deveRetomar(it) }
        ?.let { ((it.duracao - it.ms) / 60_000).toInt() }?.takeIf { it > 0 }?.let { BarraTempo.faltam(this, duracaoTexto(it)) }
    // Quanto já foi visto (barra no pé do ícone em Continuar): filme/série pela posição salva; TV, o programa no ar.
    fun visto(f: PlayerArmazem.FichaHome, serie: Boolean): Float? = posicoesHome[f.chave]
        ?.takeIf { it.duracao > 0 && (serie || Vod.deveRetomar(it)) }?.let { (it.ms.toFloat() / it.duracao).coerceIn(0f, 1f) }
    val noAr = programa?.takeIf { it.fim > it.inicio }?.let { ((agora - it.inicio).toFloat() / (it.fim - it.inicio)).coerceIn(0f, 1f) }
    // Sem internet: Séries, Filmes, Rádio e Podcasts avisam antes do OK (Na rede, TV gravada e pendrive seguem normais).
    val precisa = if (semInternetCruz) getString(R.string.cruz_precisa_internet) else null
    // A situação de cada item (1ª linha do canto de baixo à direita), sempre "rótulo + o quê · % · quanto falta" (Paulo, 18/11/2026 14:15
    // local: "mais inteligente, coerente e lógico"). Gravando vence No ar; Tocando vence Parou em.
    fun situacaoTv(oQue: String?, tira: String?): CruzLogica.Situacao? =
        gravandoTv?.let { CruzLogica.Situacao(getString(R.string.cruz_canto_gravando), it, "tv") }
            ?: programa?.let { p -> CruzLogica.Situacao(getString(R.string.cruz_canto_no_ar),
                CruzLogica.andamento(oQue, noAr, linhaAgoraTv(p, agora, comPrograma = false)), "tv", listOfNotNull(tira)) }
                ?.takeIf { it.texto.isNotEmpty() }
    fun situacaoParou(oQue: String?, fracao: Float?, falta: String?, tira: String?, origem: String): CruzLogica.Situacao? =
        falta?.takeIf { it.isNotBlank() }?.let { CruzLogica.Situacao(getString(R.string.cruz_canto_parou), CruzLogica.andamento(oQue, fracao, it), origem, listOfNotNull(tira)) }
    fun situacaoTocando(tipo: String): CruzLogica.Situacao? = ouvindoCruz?.takeIf { it.first == tipo && it.second.isNotBlank() }
        ?.let { CruzLogica.Situacao(getString(R.string.cruz_canto_tocando), it.second, tipo, listOf(getString(R.string.cruz_tocando, it.second))) }
    fun fracaoPodcast(p: Podcasts.Posicao): Float? = if (p.durMs > 0) (p.ms.toFloat() / p.durMs).coerceIn(0f, 1f) else null
    val cats = ArrayList<HomeCruz.Categoria>()
    // Opções de cada mundo (WorldOptions, 22/11/2026): nome trocado e mundo escondido; a ordem entra em appsDe/jogos/apps.
    fun categoria(k: CruzLogica.Categoria, nome: Int, tipo: IconeCruz.Tipo, itens: List<HomeCruz.Item>) {
        val o = WorldOptions.of(this, k)
        if (itens.isNotEmpty() && WorldOptions.Rules.visible(k, o))
            cats += HomeCruz.Categoria(k, o.name ?: getString(nome), icCruz("cat:" + k.id, tipo), itens, soIcone = o.iconOnly)
    }
    // Arruma os ids de um mundo pela ordem que a pessoa escolheu (uso = como sempre; A–Z; à mão).
    fun arrumar(k: CruzLogica.Categoria, ids: List<String>, nomes: Map<String, String>, fixos: Collection<String>): List<String> {
        val o = WorldOptions.of(this, k)
        return WorldOptions.Rules.arrange(ids, o.sort, nomes, o.manual, fixos)
    }
    fun nomeApp(id: String, original: String) = AppNames.name(this, id, original)

    // Os apps visíveis (appsVisiveis), cada um na categoria dele (CategoriaApps: escolha manual "Mover para…" > tipo
    // que o app declara > Aplicativos). Os jogos moram em Jogar (jogosNaCruz).
    val porPacote = LinkedHashMap<String, Item>()
    for (item in visiveis) porPacote[item.pacote] = item
    val escolhasCat = CategoriaApps.escolhas(this)
    val porDestino = porPacote.keys.groupBy { CategoriaApps.destino(it, this@atualizarCruz.categoria(it), false, escolhasCat[it], catsFicha?.get(it)) }
    val frequencia = frequenciaApps()
    val fixados = fixadosCruz()
    fun appsDe(k: CruzLogica.Categoria): List<HomeCruz.Item> =
        arrumar(k, CruzLogica.ordenarApps(porDestino[k].orEmpty(), ultimoUso, frequencia, fixados),
            porDestino[k].orEmpty().associateWith { nomeApp(it, porPacote.getValue(it).nome) }, fixados).map { id -> porPacote.getValue(id).let { app ->
            val nome = nomeApp(id, app.nome)
            HomeCruz.Item("app:$id", nome, linhasApp(id, nome), icApp(app), { abrir(id) }, opcoes = { menuAppCruz(id) })
        } }

    // Continuar (18/11/2026, pedido do dono): o canal de TV primeiro (a TV da casa); depois, do mais recente para o mais
    // antigo, a série, o filme, a última rádio, o podcast pela metade, até 3 jogos e o último de cada app de mídia
    // (YouTube, Spotify, Netflix…; MidiaRecente). "Dormir em 30 min" no fim, à noite.
    // O nome de cada item é O QUE ele é (TV, Série, Filme, Rádio, Podcast, Jogo, ou o app), como nas outras categorias;
    // o título vem embaixo e o resto no canto (Paulo, 19/11/2026 12:30 local: "tem q mostrar o nome do que é… eu gosto
    // de coerência"). Antes o nome era o título, cortado ("Lei e Ordem: Unid…") e sem dizer o que era.
    // 21/11/2026 (Paulo): o Continuar deixou de ser um mundo. Cada mundo ganha um item "Continuar" no topo com a última
    // coisa dele (itemContinuar): TV, série, filme e apps de vídeo em Assistir; rádio, podcast e apps de música em Ouvir;
    // o último jogo em Jogar; o último app em Aplicativos. Os itens abaixo são os mesmos de antes, só vão para o mundo certo.
    val doMundo = HashMap<CruzLogica.Categoria, ArrayList<Pair<Long, HomeCruz.Item>>>()
    fun guardar(k: CruzLogica.Categoria, quando: Long, item: HomeCruz.Item) { doMundo.getOrPut(k) { ArrayList() } += quando to item }
    ultimoTv?.let { u ->
        guardar(CruzLogica.Categoria.ASSISTIR, ultimoTvEm, HomeCruz.Item("c:tv", getString(R.string.home_card_tv),
            CruzLogica.linhas(programa?.titulo, linhasTvCruz(u, programa, agora, comPrograma = false)), icCruz("c:tv", IconeCruz.Tipo.TV), { abrirTv() }, progresso = noAr,
            situacao = situacaoTv(null, linhaAgoraTv(programa, agora, comPrograma = false))))
    }
    val recentes = ArrayList<Pair<Long, HomeCruz.Item>>()
    if (cruzTemVod) {
        fichaSerie?.let { f -> recentes += (posicoesHome[f.chave]?.quando ?: 0L) to HomeCruz.Item("c:serie", getString(R.string.continue_series),
            CruzLogica.linhas(CruzLogica.semMarca(f.titulo), CruzLogica.juntar(epLegivel(f.episodio), faltaSerie(f)), metaFicha(f, serie = true)),
            icCruz("c:serie", IconeCruz.Tipo.SERIES), { abrirCatalogo(SeriesActivity::class.java) }, progresso = visto(f, serie = true),
            situacao = situacaoParou(epLegivel(f.episodio), visto(f, serie = true), faltaSerie(f),
                CruzLogica.juntar(epLegivel(f.episodio), faltaSerie(f)), "c:serie")) }
        fichaFilme?.let { f -> recentes += (posicoesHome[f.chave]?.quando ?: 0L) to HomeCruz.Item("c:filme", getString(R.string.cruz_filme),
            CruzLogica.linhas(CruzLogica.semMarca(f.titulo), faltaFilme(f), metaFicha(f, serie = false)),
            icCruz("c:filme", IconeCruz.Tipo.FILMES), { abrirCatalogo(FilmesActivity::class.java) }, progresso = visto(f, serie = false),
            situacao = situacaoParou(null, visto(f, serie = false), faltaFilme(f), faltaFilme(f), "c:filme")) }
    }
    radioCruz?.let { (r, ouvidaEm) -> recentes += ouvidaEm to HomeCruz.Item("c:radio", getString(R.string.cruz_radio),
        CruzLogica.linhas(r.nome, quando(ouvidaEm)?.let { getString(R.string.cruz_tocou, it) }),
        icCruz("c:radio", IconeCruz.Tipo.RADIO), { tocarRadioCruz(r) }, situacao = situacaoTocando("radio")?.takeIf { it.texto.contains(r.nome, true) }) }
    podcastCruz?.let { p -> recentes += p.em to HomeCruz.Item("c:podcast", getString(R.string.continue_podcast),
        CruzLogica.linhas(p.titulo, p.podcast, faltaPodcast(p), quando(p.em)?.let { getString(R.string.cruz_tocou, it) }),
        icCruz("c:podcast", IconeCruz.Tipo.PODCASTS), { retomarPodcastCruz(p) },
        progresso = fracaoPodcast(p),
        situacao = situacaoTocando("podcast") ?: situacaoParou(null, fracaoPodcast(p), faltaPodcast(p), faltaPodcast(p), "c:podcast")) }
    for ((pacote, item) in jogosRecentes()) recentes += (ultimoUso[pacote] ?: 0L) to HomeCruz.Item("c:jogo:$pacote", getString(R.string.cruz_jogo),
        CruzLogica.linhas(item.nome, quando(ultimoUso[pacote])?.let { getString(R.string.cruz_jogado, it) }), icApp(item), { abrir(pacote) }, opcoes = { menuAppCruz(pacote) })
    for (m in midiaCruz) {
        val nomeApp = nomeDe(m.registro.pacote)
        recentes += m.registro.quando to HomeCruz.Item("c:midia:${m.registro.pacote}", nomeApp,
            CruzLogica.linhas(m.registro.titulo, m.registro.sub, faltaMidia(m.registro), quando(m.registro.quando)?.let { getString(R.string.cruz_aberto, it) }),
            m.capa ?: instalados.firstOrNull { it.pacote == m.registro.pacote }?.let { icApp(it) } ?: icCruz("c:midia", IconeCruz.Tipo.CONTINUAR),
            { abrirMidiaCruz(m.registro.pacote) }, progresso = MidiaRecente.progresso(m.registro),
            opcoes = { menuMidiaCruz(m.registro.pacote, nomeApp) },
            situacao = situacaoParou(null, MidiaRecente.progresso(m.registro), faltaMidia(m.registro), faltaMidia(m.registro), "c:midia"))
    }
    for ((quando, item) in recentes) {
        val id = item.id
        val k = when {
            id == "c:serie" || id == "c:filme" -> CruzLogica.Categoria.ASSISTIR
            id == "c:radio" || id == "c:podcast" -> CruzLogica.Categoria.OUVIR
            id.startsWith("c:jogo:") -> CruzLogica.Categoria.JOGAR
            id.startsWith("c:midia:") -> id.removePrefix("c:midia:").let { p ->
                if (CategoriaApps.destino(p, this@atualizarCruz.categoria(p), false, escolhasCat[p], catsFicha?.get(p)) == CruzLogica.Categoria.OUVIR)
                    CruzLogica.Categoria.OUVIR else CruzLogica.Categoria.ASSISTIR }
            else -> CruzLogica.Categoria.ASSISTIR
        }
        guardar(k, quando, item)
    }
    // Aplicativos: os apps que moram lá, pelo último uso.
    for (id in porDestino[CruzLogica.Categoria.APLICATIVOS].orEmpty()) {
        val em = ultimoUso[id]?.takeIf { it > 0L } ?: continue
        val app = porPacote.getValue(id)
        guardar(CruzLogica.Categoria.APLICATIVOS, em, HomeCruz.Item("app:$id", app.nome,
            CruzLogica.linhas(app.nome, quando(em)?.let { getString(R.string.cruz_aberto, it) }), icApp(app), { abrir(id) }, opcoes = { menuAppCruz(id) }))
    }
    // Proposta A dos cartões dos apps (AppChannels.Mode.COLUMN): até 6 cartões no fim da coluna do mundo do app,
    // um de cada app por vez; só de apps visíveis.
    val modoCartoes = AppChannels.mode(this)
    fun cartoesColuna(k: CruzLogica.Categoria): List<HomeCruz.Item> = if (modoCartoes != AppChannels.Mode.COLUMN) emptyList() else
        AppChannels.intercalar(cartoesApps.filter { cd -> cd.pacote in porPacote &&
            CategoriaApps.destino(cd.pacote, this@atualizarCruz.categoria(cd.pacote), false, escolhasCat[cd.pacote], catsFicha?.get(cd.pacote)) == k },
            AppChannels.MAX_NA_COLUNA).map { cd ->
            HomeCruz.Item("card:${cd.id}", nomeDe(cd.pacote), CruzLogica.linhas(cd.titulo, cd.detalhe.takeIf { it.isNotBlank() }, cd.canal.takeIf { it.isNotBlank() }),
                PosterDrawable(this, cd.poster, porPacote[cd.pacote]?.let { icApp(it) }),
                { if (!AppChannels.abrir(this, cd)) cruz?.naoAbriu() }, opcoes = { menuAppCruz(cd.pacote) })
        }
    // Continuar é opção (Paulo, 22/11/2026: os apps já guardam onde pararam, o item repetia a função). Desligado de
    // fábrica; ligado, só retoma o que guarda a posição: TV, séries, filmes, rádio e podcast nossos e os apps que avisam
    // pela sessão de mídia onde pararam (c:midia). Jogo e app só "aberto por último" ficam de fora.
    val mostrarContinuar = ContinueOption.on(this)
    fun comContinuar(k: CruzLogica.Categoria, itens: List<HomeCruz.Item>): List<HomeCruz.Item> =
        (itens + cartoesColuna(k)).let { l -> if (!mostrarContinuar) l else
            itemContinuar(k, doMundo[k].orEmpty().filter { ContinueOption.keeps(it.second.id) })?.let { listOf(it) + l } ?: l }

    // Ferramentas (21/11/2026, Paulo: entra no lugar do Continuar, "vamos ver o q colocar aqui"): o que a casa usa
    // para cuidar da TV e do dia. Começa com o que já existe; a lista é dele para mudar.
    val hora = android.text.format.DateFormat.getTimeFormat(this)
    val ferramentas = ArrayList<HomeCruz.Item>()
    itemDormir(sempre = true)?.let { ferramentas += it }
    ferramentas += HomeCruz.Item("t:despertador", getString(R.string.pr_despertador),
        runCatching { br.paulo.tvlauncher.alarm.Despertador.proximo(this) }.getOrNull()?.let { (_, em) -> getString(R.string.tl_proximo, hora.format(java.util.Date(em))) }
            ?: getString(R.string.al_nenhum),
        icCruz("t:despertador", IconeCruz.Tipo.DESPERTADOR), { abrirCatalogo(AlarmesActivity::class.java) })
    ferramentas += HomeCruz.Item("t:clima", getString(R.string.cl_clima),
        CruzLogica.linhas(txtTemp.text?.toString()?.takeIf { it.isNotBlank() && it != "…" }, getString(R.string.tl_clima_sub)),
        icCruz("t:clima", IconeCruz.Tipo.CLIMA), { abrirPrevisao() })
    if (br.paulo.tvlauncher.network.CasaHA.configurado(this)) ferramentas += HomeCruz.Item("t:casa", getString(R.string.casa_titulo),
        getString(R.string.tl_casa_sub), icCruz("t:casa", IconeCruz.Tipo.CASA), { abrirCatalogo(CasaActivity::class.java) })
    ferramentas += HomeCruz.Item("t:ajuste", getString(R.string.ft_titulo), getString(R.string.ft_abrir_det),
        icCruz("t:ajuste", IconeCruz.Tipo.AJUSTE), { FineTuneActivity.abrir(this) })
    ferramentas += HomeCruz.Item("t:painel", getString(R.string.aj_painel_titulo), getString(R.string.tl_painel_sub),
        icCruz("t:painel", IconeCruz.Tipo.PAINEL), { PainelRapidoActivity.abrir(this) })
    ferramentas += HomeCruz.Item("t:config", getString(R.string.cfg_titulo), getString(R.string.tl_config_sub),
        icCruz("t:config", IconeCruz.Tipo.CONFIG), { abrirCatalogo(ConfigLauncherActivity::class.java) })
    categoria(CruzLogica.Categoria.FERRAMENTAS, R.string.cruz_ferramentas, IconeCruz.Tipo.FERRAMENTAS, ferramentas)

    // Assistir: os cards de hoje (TV, Séries, Filmes, Na rede, Addons, plugins), cada um só se o recurso existe.
    val assistir = ArrayList<HomeCruz.Item>()
    if (cruzTemTv) assistir += HomeCruz.Item("tv", getString(R.string.home_card_tv),
        (ultimoTv?.let { linhasTvCruz(it, programa, agora, comPrograma = true) } ?: getString(R.string.home_destaque_tv_sub)).split("\n").let { l ->
            // O jogo do seu time (HomeCards, chave CARTAO_PLACAR) logo depois do canal: as linhas param em 4.
            CruzLogica.linhas(l.first(), jogoCruz?.let { getString(R.string.hc_seu_time, it) }, *l.drop(1).toTypedArray()) },
        icCruz("tv", IconeCruz.Tipo.TV), { abrirTv() }, progresso = noAr,
        situacao = situacaoTv(programa?.titulo, programa?.let { linhaAgoraTv(it, agora, comPrograma = true) }))
    if (cruzTemVod) {
        assistir += HomeCruz.Item("series", getString(R.string.home_card_series),
            precisa ?: fichaSerie?.let { f -> CruzLogica.linhas(f.titulo, CruzLogica.juntar(epLegivel(f.episodio), faltaSerie(f)), metaFicha(f, serie = true), f.genero) }
                ?: getString(R.string.home_destaque_series),
            icCruz("series", IconeCruz.Tipo.SERIES), { abrirCatalogo(SeriesActivity::class.java) },
            situacao = fichaSerie?.takeIf { precisa == null }?.let { f ->
                situacaoParou(epLegivel(f.episodio), visto(f, serie = true), faltaSerie(f), CruzLogica.juntar(epLegivel(f.episodio), faltaSerie(f)), "series") })
        assistir += HomeCruz.Item("filmes", getString(R.string.home_card_filmes),
            precisa ?: fichaFilme?.let { f -> CruzLogica.linhas(f.titulo, metaFicha(f, serie = false), f.genero, faltaFilme(f)) }
                ?: getString(R.string.home_destaque_filmes),
            icCruz("filmes", IconeCruz.Tipo.FILMES), { abrirCatalogo(FilmesActivity::class.java) },
            situacao = fichaFilme?.takeIf { precisa == null }?.let { f -> situacaoParou(null, visto(f, serie = false), faltaFilme(f), faltaFilme(f), "filmes") })
    }
    val pendrives = pendrivesCruz()
    assistir += HomeCruz.Item("rede", getString(R.string.home_card_rede),
        CruzLogica.linhas(getString(R.string.home_destaque_rede), *pendrives.map { getString(R.string.cruz_pendrive, it) }.toTypedArray()),
        icCruz("rede", IconeCruz.Tipo.REDE), { abrirCatalogo(MidiaRedeActivity::class.java) },
        situacao = pendrives.takeIf { it.isNotEmpty() }?.let { pd ->
            CruzLogica.Situacao(getString(R.string.cruz_canto_conectado), pd.joinToString(" · "), "rede", pd.map { getString(R.string.cruz_pendrive, it) }) })
    if (AddonsTv.instalados(this).isNotEmpty()) assistir += HomeCruz.Item("addons", getString(R.string.ad_titulo),
        CruzLogica.linhas(getString(R.string.ad_destaque), getString(R.string.cruz_addons_n, AddonsTv.instalados(this).size)), icCruz("addons", IconeCruz.Tipo.ADDONS), { abrirCatalogo(AddonsActivity::class.java) })
    for ((i, p) in cartoesPlugin.withIndex()) assistir += HomeCruz.Item("plugin:${p.pacote}:$i", p.titulo.ifEmpty { p.valor },
        if (p.titulo.isEmpty()) p.detalhe else listOf(p.valor, p.detalhe).filter { it.isNotEmpty() }.joinToString(" · "),
        icCruz("plugin:${p.pacote}:$i", IconeCruz.Tipo.PLUGIN), { PluginsTv.abrir(this, p) })
    assistir += appsDe(CruzLogica.Categoria.ASSISTIR)   // YouTube, Netflix… (movidos ou pelo tipo do app)
    categoria(CruzLogica.Categoria.ASSISTIR, R.string.cruz_assistir, IconeCruz.Tipo.ASSISTIR, comContinuar(CruzLogica.Categoria.ASSISTIR, assistir))

    // Ouvir: as rádios (catálogo mundial, sem conta) e os podcasts (plano Básico, Paulo 17/11/2026).
    val ouvir = listOf(
        HomeCruz.Item("radio", getString(R.string.cruz_radio),
            CruzLogica.linhas(ouvindoCruz?.takeIf { it.first == "radio" }?.let { getString(R.string.cruz_tocando, it.second) } ?: precisa ?: getString(R.string.cruz_radio_sub),
                radioCruz?.let { (r, q) -> CruzLogica.juntar(getString(R.string.cruz_por_ultimo, r.nome), quando(q)) }),
            icCruz("radio", IconeCruz.Tipo.RADIO), {
            try { startActivity(Intent(this, RadioActivity::class.java)) }
            catch (e: Exception) { Log.w("TvLauncher.Cruz", "rádio não abriu: ${e.javaClass.simpleName}"); cruz?.naoAbriu() }
        }, situacao = situacaoTocando("radio")),
        HomeCruz.Item("podcasts", getString(R.string.pod_titulo),
            CruzLogica.linhas(ouvindoCruz?.takeIf { it.first == "podcast" }?.let { getString(R.string.cruz_tocando, it.second) } ?: precisa ?: getString(R.string.ra_podcasts_det),
                podcastCruz?.let { p -> CruzLogica.juntar(p.titulo, faltaPodcast(p)) },
                assinadosCruz.takeIf { it > 0 }?.let { getString(R.string.cruz_podcasts_n, it) }),
            icCruz("podcasts", IconeCruz.Tipo.PODCASTS), {
            try { startActivity(Intent(this, PodcastsActivity::class.java)) }
            catch (e: Exception) { Log.w("TvLauncher.Cruz", "podcasts não abriram: ${e.javaClass.simpleName}"); cruz?.naoAbriu() }
        }, situacao = situacaoTocando("podcast") ?: podcastCruz?.let { p ->
            situacaoParou(p.titulo, fracaoPodcast(p), faltaPodcast(p), CruzLogica.juntar(p.titulo, faltaPodcast(p)), "podcasts") })) + appsDe(CruzLogica.Categoria.OUVIR)
    categoria(CruzLogica.Categoria.OUVIR, R.string.cruz_ouvir, IconeCruz.Tipo.OUVIR, comContinuar(CruzLogica.Categoria.OUVIR, ouvir))

    // Jogar (18/11/2026, pedido do dono: "Jogos" e "Sofá" abriam a mesma tela): a Central de jogos no topo (o painel com
    // controles, turma do Discord, rede e recentes) e cada jogo instalado, do jogado por último ao mais antigo.
    val jogos = ArrayList<HomeCruz.Item>()
    if (JogosTv.cardLigado(this)) {
        jogos += HomeCruz.Item("central", getString(R.string.cruz_central), linhasCentral(), icCruz("central", IconeCruz.Tipo.CENTRAL),
            { abrirCatalogo(JogosActivity::class.java) })
        val doJogo = jogosNaCruz().associateBy { it.pacote }
        val fixosJogo = fixadosCruz()
        for (p in arrumar(CruzLogica.Categoria.JOGAR, CruzLogica.ordenarApps(doJogo.keys.sorted(), ultimoUso, emptyMap(), fixosJogo),
                doJogo.mapValues { nomeApp(it.key, it.value.nome) }, fixosJogo)) doJogo.getValue(p).let { app ->
            val nome = nomeApp(p, app.nome)
            jogos += HomeCruz.Item("app:$p", nome,
                CruzLogica.linhas(subApp(p, nome), quando(ultimoUso[p])?.let { getString(R.string.cruz_jogado, it) }),
                icApp(app), { abrir(p) }, opcoes = { menuAppCruz(p) })
        }
        categoria(CruzLogica.Categoria.JOGAR, R.string.cruz_jogar, IconeCruz.Tipo.JOGAR, comContinuar(CruzLogica.Categoria.JOGAR, jogos))
    }

    // Aplicativos: os 8 mais usados dos que moram aqui (nem movidos nem automáticos de Assistir/Ouvir) e "Todos os apps".
    // A coluna mostra os APPS_NA_COLUNA primeiros já na ordem escolhida para o mundo (A–Z corta pelo alfabeto, não pelo uso).
    val idsApps = porDestino[CruzLogica.Categoria.APLICATIVOS].orEmpty()
    val apps = (arrumar(CruzLogica.Categoria.APLICATIVOS, CruzLogica.ordenarApps(idsApps, ultimoUso, frequencia, fixados),
            idsApps.associateWith { nomeApp(it, porPacote.getValue(it).nome) }, fixados)
            .take(CruzLogica.APPS_NA_COLUNA) + CruzLogica.ID_TODOS_APPS).map { id ->
        if (id == CruzLogica.ID_TODOS_APPS) HomeCruz.Item(id, getString(R.string.cruz_todos_apps),
            CruzLogica.linhas(getString(R.string.cruz_todos_apps_sub), contaApps()),
            icCruz(id, IconeCruz.Tipo.TODOS_APPS), { abrirTodos() })
        else porPacote.getValue(id).let { app ->
            val nome = nomeApp(id, app.nome)
            HomeCruz.Item("app:$id", nome, linhasApp(id, nome), icApp(app), { abrir(id) }, opcoes = { menuAppCruz(id) })
        }
    }
    // Pastas mostradas na tela inicial (AppFolders, 22/11/2026): entram antes de "Todos os apps" e abrem a tela já nelas.
    val pastasNaCruz = AppFolders.Rules.pinned(AppFolders.all(this), CruzLogica.Categoria.APLICATIVOS.id).map { f ->
        HomeCruz.Item("pasta:${f.id}", f.name, getString(R.string.cruz_apps_n, f.apps.count { it in porPacote }),
            icCruz("pasta:${f.id}", IconeCruz.Tipo.TODOS_APPS), { abrirTodosNaPasta(f.id) }, opcoes = { menuPasta(f.id) })
    }
    val appsComPastas = if (pastasNaCruz.isEmpty()) apps else apps.dropLast(1) + pastasNaCruz + apps.last()
    categoria(CruzLogica.Categoria.APLICATIVOS, R.string.cruz_aplicativos, IconeCruz.Tipo.APLICATIVOS, comContinuar(CruzLogica.Categoria.APLICATIVOS, appsComPastas))

    // Trocar ícone item a item (21/11/2026): segurar OK num item do Sofaly que não tem menu próprio troca o desenho dele
    // ou o da categoria (MainActivity.trocarIconeSofaly). Itens de app e de mídia continuam com o menu deles.
    c.definir(cats.map { cat ->
        val tipoCat = (cat.icone as? IconeCruz)?.tipo
        HomeCruz.Categoria(cat.cat, cat.nome, cat.icone, cat.itens.map { item ->
            val ic = item.icone as? IconeCruz
            if (item.opcoes != null || ic == null) item else item.comOpcoes {
                // Itens do Sofaly: trocar o desenho e as opções do mundo (HomeWorldMenu, 22/11/2026).
                val l = listOf<CharSequence>(getString(R.string.wp_trocar_icone), getString(R.string.wo_opcoes, cat.nome))
                menuAberto = Estilo.lista(this, item.nome, l) { i ->
                    if (i == 0) trocarIconeSofaly(ic.tipo, item.nome, tipoCat, cat.nome) else menuDoMundo(cat.cat, cat.nome)
                }
            }
        })
    })
    c.itemAtual()?.let { vitrineDoItem(it) }   // os cartões chegam depois do 1º desenho: a vitrine se acerta
    if (focoInicialNaTv && !todosAbertos) { focoInicialNaTv = false; c.requestFocus() }
    else if (currentFocus == null && !todosAbertos) c.requestFocus()
}
