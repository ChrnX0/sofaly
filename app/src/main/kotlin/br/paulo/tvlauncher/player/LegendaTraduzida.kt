package br.paulo.tvlauncher.player

import android.content.Context
import android.util.Log
import br.paulo.tvlauncher.Prefs
import br.paulo.tvlauncher.resources.R
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ideia 90 (15/11/2026): sem legenda no idioma da TV, baixa a inglesa do OpenSubtitles e traduz NA TV (ML Kit, sem
 * mandar o texto para fora). A lógica de ordem e de texto está em [TraducaoLegenda]; aqui só o tradutor e o disco.
 *
 * Fluxo: os 10 min a partir de onde o vídeo está primeiro (vira a legenda na hora), o resto em seguida, à frente da cena
 * (a legenda completa troca a parcial uma vez só). Marcada como "Tradução automática" na faixa e no aviso.
 * Roda no processo :player, chamado pelo VodActivity, sempre fora da thread da tela (bloqueia).
 */
object LegendaTraduzida {
    private const val TAG = "TvLauncher.Traducao"
    private const val ESPERA_MODELO_S = 120L
    private const val ESPERA_FALA_S = 20L

    /** O motor ([SubtitleEngines]) traduz de inglês para o idioma da TV? (Inglês para inglês não faz sentido.) */
    fun suporta(idiomaTv: String): Boolean =
        idiomaTv != TraducaoLegenda.ORIGEM && SubtitleEngines.provider?.supports(idiomaTv) == true

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(Prefs.PLAYER, Context.MODE_PRIVATE)

    /** A legenda deste vídeo é tradução automática (para o rótulo da faixa). */
    fun ehTraducao(ctx: Context, chave: String) = prefs(ctx).getBoolean("legtrad:$chave", false)

    /** Ficou pela metade (o player fechou durante a 2ª parte): na próxima vez traduz de novo. */
    fun pelaMetade(ctx: Context, chave: String) = prefs(ctx).getBoolean("legtrad_parcial:$chave", false)

    private fun marcar(ctx: Context, chave: String, parcial: Boolean) =
        prefs(ctx).edit().putBoolean("legtrad:$chave", true).putBoolean("legtrad_parcial:$chave", parcial).apply()

    /** Esquece a marca (a pessoa trocou por outra legenda). */
    fun esquecer(ctx: Context, chave: String) = prefs(ctx).edit().remove("legtrad:$chave").remove("legtrad_parcial:$chave").apply()

    private fun chaveOrigem(chave: String) = "$chave~${TraducaoLegenda.ORIGEM}"

    /** A legenda em inglês: a guardada de antes ou uma nova do OpenSubtitles (a feita para este arquivo primeiro). */
    fun origem(ctx: Context, chave: String, url: String?, b: LegendaExterna.Busca): Result<String> = runCatching {
        LegendaBaixar.guardada(ctx, chaveOrigem(chave))?.let { return@runCatching it.readText() }
        val hash = url?.let { runCatching { LegendaBaixar.hashDoVideo(it) }.getOrNull() }
        val lista = LegendaBaixar.buscar(ctx, b.copy(hash = hash), TraducaoLegenda.ORIGEM).getOrThrow()
        val c = LegendaExterna.escolherAuto(lista) ?: error(ctx.getString(R.string.legenda_nenhuma))
        LegendaBaixar.baixar(ctx, chaveOrigem(chave), c).getOrThrow().readText()
    }

    /**
     * Traduz [srt] para [para]. [aoPrimeiro] recebe o arquivo com o primeiro bloco (10 min a partir de [posicaoMs]);
     * [aoFim], o arquivo completo. Para quando [parar] vira true (o player fechou ou trocou de vídeo).
     */
    fun traduzir(ctx: Context, chave: String, srt: String, para: String, posicaoMs: Long, parar: AtomicBoolean,
                 aoPrimeiro: (File) -> Unit, aoFim: (File) -> Unit): Result<Unit> = runCatching {
        val motor = SubtitleEngines.provider ?: error("sem tradutor")
        if (!motor.supports(para)) error("idioma sem tradutor: $para")
        val falas = TraducaoLegenda.ler(srt)
        if (falas.isEmpty()) error(ctx.getString(R.string.legenda_invalida))
        motor.open(ctx, TraducaoLegenda.ORIGEM, para).use { tradutor ->
            val t0 = System.currentTimeMillis()
            tradutor.prepare(ESPERA_MODELO_S)
            val (primeiro, resto) = TraducaoLegenda.ordem(falas, posicaoMs)
            val feitas = HashMap<Int, String>(falas.size * 2)
            fun lote(indices: List<Int>): Boolean {
                for (i in indices) {
                    if (parar.get()) return false
                    val partes = TraducaoLegenda.paraTraduzir(falas[i].texto).map { p ->
                        tradutor.translate(p, ESPERA_FALA_S)
                    }
                    feitas[i] = TraducaoLegenda.montar(partes)
                }
                return true
            }
            if (!lote(primeiro)) return@runCatching
            marcar(ctx, chave, parcial = true)
            aoPrimeiro(LegendaBaixar.gravarTraduzida(ctx, chave, TraducaoLegenda.escrever(TraducaoLegenda.parcial(falas, feitas))))
            Log.i(TAG, "primeiro bloco: ${primeiro.size} falas em ${System.currentTimeMillis() - t0} ms")
            if (!lote(resto)) return@runCatching
            val f = LegendaBaixar.gravarTraduzida(ctx, chave, TraducaoLegenda.escrever(TraducaoLegenda.parcial(falas, feitas)))
            marcar(ctx, chave, parcial = false)
            Log.i(TAG, "${falas.size} falas em ${System.currentTimeMillis() - t0} ms")
            aoFim(f)
        }
    }
}
