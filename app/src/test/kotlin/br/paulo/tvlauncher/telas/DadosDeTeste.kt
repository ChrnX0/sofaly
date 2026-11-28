package br.paulo.tvlauncher.telas

import android.content.Context
import br.paulo.tvlauncher.listen.PodcastFeed
import br.paulo.tvlauncher.listen.PodcastStarter
import br.paulo.tvlauncher.listen.Podcasts
import br.paulo.tvlauncher.listen.Radios
import br.paulo.tvlauncher.player.FonteTv
import br.paulo.tvlauncher.player.M3u
import br.paulo.tvlauncher.player.PlayerArmazem
import br.paulo.tvlauncher.player.PlayerDados
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dados falsos de teste (fixtures) por área, para fotografar as telas, abas, submenus e janelas como elas ficam numa TV
 * de verdade (28/11/2026, pedido da Design e do Paulo: "ver tudo no padrão"). Sem rede: tudo vai direto para o disco e
 * as preferências que as telas leem. Os nomes são inventados ou de obras conhecidas; nada de dado pessoal.
 *
 * Uso: [preparar] antes de CADA abertura de tela (um OK numa passada anterior pode ter mudado as preferências).
 */
object DadosDeTeste {

    /** As telas que [preparar] sabe encher (nome simples da Activity). */
    val TELAS = setOf("FilmesActivity", "SeriesActivity", "PlayerActivity", "VodActivity", "RadioActivity", "PodcastsActivity",
        "CasaActivity", "PrevisaoActivity", "JogosActivity", "ConfigLauncherActivity")

    /** Prepara os dados que a tela [tela] (nome simples da Activity) precisa, no idioma [lingua] ("pt"/"en"). */
    fun preparar(ctx: Context, tela: String, lingua: String) {
        ChaveiroDeTeste.instalar()
        when (tela) {
            "FilmesActivity", "SeriesActivity", "PlayerActivity", "VodActivity" -> listaDeTv(ctx, lingua)
            "RadioActivity" -> radios(ctx, lingua)
            "PodcastsActivity" -> podcasts(ctx, lingua)
            "CasaActivity" -> casa(ctx, lingua)
            "PrevisaoActivity" -> clima()
            "JogosActivity" -> jogos(ctx, lingua)
            // Configurações: tudo junto (lista de TV, Casa, rádios e podcasts), como numa TV em uso.
            "ConfigLauncherActivity" -> { listaDeTv(ctx, lingua); radios(ctx, lingua); podcasts(ctx, lingua); casa(ctx, lingua) }
        }
    }

    /** Desfaz o que não fica no disco do teste: a rede de mentira e a previsão guardada na memória. Chamar depois da foto. */
    fun limpar() {
        RedeDeTeste.desligar()
        br.paulo.tvlauncher.PrevisaoActivity.dadosDeTeste = null
        br.paulo.tvlauncher.weather.Forecast.dadosDeTeste = null
    }

    private fun pt(l: String, p: String, e: String) = if (l == "pt") p else e

    // ================================================================ TV ao vivo, Filmes e Séries (uma lista M3U)

    /** Uma lista de TV com canais, filmes e séries, já "baixada" (cache fresco): Filmes, Séries e TV abrem cheias. */
    fun listaDeTv(ctx: Context, l: String) {
        val fonte = PlayerArmazem.fontes(ctx).firstOrNull { it.nome == NOME_LISTA }
            ?: PlayerArmazem.adicionar(ctx, NOME_LISTA, FonteTv.TIPO_M3U, "http://192.168.0.5:8000/lista.m3u").getOrThrow()
        val canais = M3u.ler(m3u(l))
        File(PlayerArmazem.pasta(ctx), "${fonte.id}.canais.json").writeText(PlayerDados.canaisParaJson(canais, fonte))
    }

    private const val NOME_LISTA = "Lista da casa"

    private fun m3u(l: String): String {
        val b = StringBuilder("#EXTM3U\n")
        fun e(nome: String, grupo: String, url: String, tvgId: String = "") {
            b.append("#EXTINF:-1 tvg-id=\"").append(tvgId).append("\" group-title=\"").append(grupo).append("\",").append(nome).append('\n')
            b.append(url).append('\n')
        }
        val abertos = pt(l, "Abertos", "Free to air")
        val noticias = pt(l, "Notícias", "News")
        val esportes = pt(l, "Esportes", "Sports")
        val infantis = pt(l, "Infantis", "Kids")
        listOf("Canal 2 HD" to abertos, "Rede Sul" to abertos, "TV Cultura Viva" to abertos, "Canal Comunitário" to abertos,
            "Notícias 24h" to noticias, "Jornal do Mundo" to noticias, "Bolsa e Economia" to noticias,
            "Esporte Total" to esportes, "Futebol Ao Vivo" to esportes, "Tênis Clube" to esportes,
            "Desenhos Sem Parar" to infantis, "Turma da Ciência" to infantis)
            .forEachIndexed { i, (n, g) -> e(n, g, "http://192.168.0.5:8000/live/$i.ts", "c$i") }
        val acao = pt(l, "Filmes | Ação", "Movies | Action")
        val drama = pt(l, "Filmes | Drama", "Movies | Drama")
        val comedia = pt(l, "Filmes | Comédia", "Movies | Comedy")
        val familia = pt(l, "Filmes | Família", "Movies | Family")
        listOf(
            "Estrada de Fogo (2015)" to acao, "O Último Voo (2019)" to acao, "Cidade em Chamas (2021)" to acao, "Missão no Gelo (2018)" to acao,
            "Cartas para Lisboa (2017)" to drama, "O Pianista do Porto (2020)" to drama, "Raízes (2016)" to drama,
            "Casamento às Avessas (2014)" to comedia, "Férias Trocadas (2022)" to comedia, "O Vizinho Perfeito (2019)" to comedia,
            "A Floresta Encantada (2018)" to familia, "Robôs de Papel (2023)" to familia, "O Dragão Gentil (2016)" to familia,
        ).forEachIndexed { i, (n, g) -> e(n, g, "http://192.168.0.5:8000/movie/u/p/${100 + i}.mkv") }
        val series = pt(l, "Séries", "Series")
        for ((serie, temps) in listOf("Detetives do Litoral" to 2, "A Casa da Colina" to 1, "Cozinha de Família" to 3)) {
            for (t in 1..temps) for (ep in 1..6) e("$serie S%02dE%02d".format(t, ep), series, "http://192.168.0.5:8000/series/u/p/${serie.hashCode() and 0xffff}$t$ep.mkv")
        }
        return b.toString()
    }

    // ================================================================ Rádio

    /** Favoritas e recentes no disco + o catálogo (radio-browser) respondido pela [RedeDeTeste]. */
    fun radios(ctx: Context, l: String) {
        val fav = listOf(
            Radios.Radio("r1", "Rádio Cidade FM", "https://radio.exemplo/cidade", "BR", "pop, mpb", 128, "MP3"),
            Radios.Radio("r2", "Jazz da Madrugada", "https://radio.exemplo/jazz", "US", "jazz", 192, "AAC"),
            Radios.Radio("r3", pt(l, "Notícias 24 Horas", "News 24 Hours"), "https://radio.exemplo/news", "BR", "news, talk", 64, "AAC"),
            Radios.Radio("r4", "Clássica Viva", "https://radio.exemplo/classica", "AT", "classical", 256, "MP3"),
        )
        val rec = listOf(fav[1], Radios.Radio("r5", "Samba de Raiz", "https://radio.exemplo/samba", "BR", "samba, pagode", 128, "MP3"), fav[0])
        val pasta = PlayerArmazem.pasta(ctx)
        File(pasta, "radios.json").writeText(Radios.paraJson(fav).toString())
        File(pasta, "radios_recentes.json").writeText(Radios.paraJson(rec).toString())
        val nomes = listOf("Rádio Cidade FM", "Sertão Sertanejo", "Gospel Paz", "Rock Clássico 89", "Pop Hits Brasil", "Bossa Nova Rio",
            "Esporte na Rede", "Rádio Universitária", "Forró do Nordeste", "Eletrônica Noite", "Country Road", "Lo-fi Estudos")
        val estacoes = JSONArray().apply {
            nomes.forEachIndexed { i, n ->
                put(JSONObject().put("stationuuid", "c$i").put("name", n).put("url_resolved", "https://radio.exemplo/c$i")
                    .put("countrycode", if (i % 3 == 0) "PT" else "BR").put("tags", listOf("pop", "sertanejo", "gospel", "rock")[i % 4])
                    .put("bitrate", 128).put("codec", "MP3").put("favicon", "").put("lastcheckok", 1)
                    .put("geo_lat", -23.5 + i).put("geo_long", -46.6 + i))
            }
        }.toString()
        val paises = JSONArray().apply {
            listOf("BR" to "Brazil", "US" to "The United States Of America", "PT" to "Portugal", "AR" to "Argentina",
                "DE" to "Germany", "FR" to "France", "IT" to "Italy", "ES" to "Spain").forEachIndexed { i, (c, n) ->
                put(JSONObject().put("iso_3166_1", c).put("name", n).put("stationcount", 9000 - i * 900))
            }
        }.toString()
        RedeDeTeste.ligar { u ->
            when {
                !u.contains("radio-browser.info") -> null
                u.contains("/json/servers") -> """[{"name":"de1.api.radio-browser.info"}]"""
                u.contains("/json/countries") -> paises
                u.contains("/json/stations") -> estacoes
                else -> "[]"
            }
        }
    }

    // ================================================================ Podcasts

    /** 3 assinaturas com os feeds já guardados (sem rede), um episódio pela metade e o "primeira vez" já feito. */
    fun podcasts(ctx: Context, l: String) {
        PodcastStarter.marcarFeito(ctx)
        val agora = System.currentTimeMillis()
        val dia = 86_400_000L
        val pods = listOf(
            Triple(pt(l, "Café com Ciência", "Coffee and Science"), pt(l, "Ana e Bruno", "Ann and Ben"), "Science"),
            Triple(pt(l, "Histórias do Brasil", "Tales of History"), pt(l, "Rádio Escola", "School Radio"), "History"),
            Triple(pt(l, "Cozinha sem Pressa", "Slow Kitchen"), "Chef Lia", "Food"),
        )
        val urls = pods.indices.map { "https://podcast.exemplo/feed$it.xml" }
        Podcasts.editarAssinaturas(ctx) { _ ->
            pods.mapIndexed { i, (t, a, _) -> Podcasts.Assinatura(urls[i], t, a, "", false, verificadoEm = agora, desde = agora - 30 * dia) }
        }
        val pasta = File(PlayerArmazem.pasta(ctx), "podcasts").apply { mkdirs() }
        pods.forEachIndexed { i, (t, a, cat) ->
            val eps = (1..8).map { n ->
                PodcastFeed.Episodio("g$i-$n", pt(l, "Episódio $n: ", "Episode $n: ") + temas(l)[(i * 3 + n) % temas(l).size],
                    pt(l, "Uma conversa sem pressa sobre o assunto, com convidados.", "An unhurried talk about the topic, with guests."),
                    agora - (n - 1) * 3 * dia - i * 3600_000L, 1800 + n * 120, "https://podcast.exemplo/$i/$n.mp3", "audio/mpeg", 0, "", false,
                    0, n, emptyList(), "")
            }
            val f = PodcastFeed.Feed(t, a, pt(l, "Um podcast inventado para o teste de telas.", "A made-up podcast for the screen test."),
                "", "https://podcast.exemplo/$i", l, false, eps, listOf(cat))
            File(pasta, hash(urls[i]) + ".json").writeText(Podcasts.feedParaJson(f))
        }
        Podcasts.editarPosicoes(ctx) { _ ->
            val p = Podcasts.Posicao(urls[0], "g0-2", 14 * 60_000L, (1800 + 240) * 1000L, false, agora - 3600_000L,
                pods[0].first.let { pt(l, "Episódio 2: ", "Episode 2: ") + temas(l)[5 % temas(l).size] }, pods[0].first)
            mapOf(Podcasts.chave(p.feed, p.guid) to p)
        }
    }

    private fun temas(l: String) = if (l == "pt")
        listOf("o sono", "o café", "as estrelas", "o mar", "os vulcões", "a memória", "os pássaros", "o pão", "as abelhas", "o vento")
    else listOf("sleep", "coffee", "the stars", "the sea", "volcanoes", "memory", "birds", "bread", "bees", "the wind")

    /** O mesmo nome de arquivo que o Podcasts usa para a cópia do feed (SHA-1, 20 letras). */
    private fun hash(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    // ================================================================ Casa (Home Assistant)

    /** Um Home Assistant de mentira em 192.168.0.10: luzes, tomadas, cortina, ar, cenas e sensores, com cômodos. */
    fun casa(ctx: Context, l: String) {
        val erro = br.paulo.tvlauncher.network.CasaHA.configurar(ctx, "http://192.168.0.10:8123",
            "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0ZXN0ZS1kZS10ZWxhcyJ9.c2VtLXNlZ3JlZG8tdGVzdGU")
        check(erro == null) { "Casa não configurou: $erro" }
        val sala = pt(l, "Sala", "Living room"); val cozinha = pt(l, "Cozinha", "Kitchen"); val quarto = pt(l, "Quarto", "Bedroom")
        data class E(val id: String, val nome: String, val estado: String, val area: String, val atr: JSONObject = JSONObject())
        val ents = listOf(
            E("light.sala_teto", pt(l, "Luz do teto", "Ceiling light"), "on", sala,
                JSONObject().put("brightness", 191).put("supported_color_modes", JSONArray(listOf("brightness")))),
            E("light.abajur", pt(l, "Abajur", "Table lamp"), "off", sala, JSONObject().put("supported_color_modes", JSONArray(listOf("brightness")))),
            E("cover.cortina_sala", pt(l, "Cortina", "Curtain"), "open", sala),
            E("climate.ar_sala", pt(l, "Ar-condicionado", "Air conditioner"), "cool", sala,
                JSONObject().put("temperature", 23).put("current_temperature", 26.5).put("min_temp", 16).put("max_temp", 30)),
            E("sensor.temp_sala", pt(l, "Temperatura da sala", "Living room temperature"), "26.5", sala,
                JSONObject().put("device_class", "temperature").put("unit_of_measurement", "°C")),
            E("light.cozinha", pt(l, "Luz da cozinha", "Kitchen light"), "on", cozinha, JSONObject().put("supported_color_modes", JSONArray(listOf("onoff")))),
            E("switch.cafeteira", pt(l, "Cafeteira", "Coffee maker"), "off", cozinha),
            E("sensor.umidade_cozinha", pt(l, "Umidade da cozinha", "Kitchen humidity"), "58", cozinha,
                JSONObject().put("device_class", "humidity").put("unit_of_measurement", "%")),
            E("light.quarto", pt(l, "Luz do quarto", "Bedroom light"), "off", quarto, JSONObject().put("supported_color_modes", JSONArray(listOf("brightness")))),
            E("fan.ventilador", pt(l, "Ventilador", "Fan"), "on", quarto),
            E("scene.cinema", pt(l, "Modo cinema", "Movie mode"), "unknown", ""),
            E("script.boa_noite", pt(l, "Boa noite", "Good night"), "off", ""),
        )
        val estados = JSONArray().apply {
            ents.forEach { e -> put(JSONObject().put("entity_id", e.id).put("state", e.estado).put("attributes", e.atr.put("friendly_name", e.nome))) }
        }.toString()
        val areas = JSONArray().apply { ents.filter { it.area.isNotEmpty() }.forEach { put(JSONArray(listOf(it.id, it.area))) } }.toString()
        RedeDeTeste.ligar { u ->
            when {
                !u.startsWith("http://192.168.0.10:8123/") -> null
                u.endsWith("/api/states") -> estados
                u.endsWith("/api/template") -> areas
                else -> "{}"
            }
        }
    }

    // ================================================================ Clima

    /** A mesma previsão de 3 dias que a [ClimaTelaTest] usa (horas, dias, chuva e vento). */
    fun clima() {
        val p = ClimaTelaTest().previsao()
        br.paulo.tvlauncher.PrevisaoActivity.dadosDeTeste = p
        br.paulo.tvlauncher.weather.Forecast.dadosDeTeste = p
    }

    // ================================================================ Jogos

    /** Jogos instalados (categoria de jogo do Android) + um app comum, que não entra. */
    fun jogos(ctx: Context, l: String) {
        val pm = org.robolectric.Shadows.shadowOf(ctx.packageManager)
        listOf(
            "com.exemplo.corrida" to pt(l, "Corrida na Praia", "Beach Racing"),
            "com.exemplo.quebra" to pt(l, "Quebra-cabeça da Vovó", "Grandma's Puzzle"),
            "com.exemplo.futebol" to pt(l, "Futebol de Botão", "Button Football"),
            "com.exemplo.palavras" to pt(l, "Caça-palavras", "Word Search"),
            "com.exemplo.cartas" to pt(l, "Paciência", "Solitaire"),
            "com.exemplo.receitas" to pt(l, "Receitas", "Recipes"),
        ).forEachIndexed { i, (pacote, nome) ->
            val ai = android.content.pm.ApplicationInfo().apply {
                packageName = pacote; nonLocalizedLabel = nome; uid = 10_300 + i; flags = android.content.pm.ApplicationInfo.FLAG_INSTALLED
                category = if (pacote.endsWith("receitas")) android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY
                    else android.content.pm.ApplicationInfo.CATEGORY_GAME
            }
            // A atividade leva o mesmo ApplicationInfo (com a categoria): o JogosTv lê a de ri.activityInfo.applicationInfo.
            val c = android.content.ComponentName(pacote, "$pacote.Principal")
            val atv = android.content.pm.ActivityInfo().apply { packageName = pacote; name = c.className; applicationInfo = ai; exported = true }
            pm.installPackage(android.content.pm.PackageInfo().apply { packageName = pacote; applicationInfo = ai })
            pm.addOrUpdateActivity(atv)
            pm.addIntentFilterForActivity(c, android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER)
            })
        }
    }
}
