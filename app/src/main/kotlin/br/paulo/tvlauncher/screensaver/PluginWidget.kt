package br.paulo.tvlauncher.screensaver

import org.json.JSONArray
import org.json.JSONObject

/**
 * Widgets de outros apps na HOME (24/11/2026, Paulo: "widget é widget. É para ser posicionado na home livremente dentro
 * das devidas proporções"). A regra, sem Android (PluginWidgetTest); o lado Android é `home/PluginWidgetsTv.kt`.
 *
 * O Google TV TEM o serviço de widgets (feature android.software.app_widgets, verificado na Sala), mas não tem a tela
 * de consentimento (ACTION_APPWIDGET_BIND). O launcher ganha a permissão de ligar widgets pelo shell próprio
 * (`appwidget grantbind`, o mesmo que o adb faz), uma vez.
 *
 * Como mora na HOME: o widget VIVO (a view dele, atualizando sozinha) numa camada na área livre da tela inicial, entre a
 * ilha de cima e a barra de baixo. A área é uma grade de [COLS] x [LINHAS] células; cada widget ocupa um retângulo de
 * células, sem sobrepor outro, do tamanho mínimo ao máximo que o próprio widget declara. A pessoa move e muda o tamanho
 * pelo controle (WidgetLayoutActivity). Mesmas regras dos plugins: Pro, no máximo [MAX], guarda de saúde, lista
 * `blocked` do servidor.
 */
object PluginWidget {
    const val MAX = 3
    /** Id do nosso AppWidgetHost (qualquer número fixo do app; trocar perde os widgets já ligados). */
    const val HOST_ID = 0x50FA
    /** A grade da área livre da HOME. */
    const val COLS = 12
    const val LINHAS = 6

    /** Um widget ligado: [id] do AppWidgetManager, [provedor] = componente "pacote/classe", e o lugar na grade. */
    data class Escolhido(val id: Int, val provedor: String, val x: Int = 0, val y: Int = 0, val w: Int = 0, val h: Int = 0) {
        val pacote: String get() = provedor.substringBefore('/')
        val colocado: Boolean get() = w > 0 && h > 0
    }

    /** O que o widget aceita, em células: mínimo e máximo de largura e altura. */
    data class Limites(val minW: Int, val minH: Int, val maxW: Int, val maxH: Int)

    private val COMPONENTE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+/[a-zA-Z0-9_.$]+$")

    fun componenteValido(c: String?): Boolean = c != null && c.length <= 300 && COMPONENTE.matches(c)

    fun ler(t: String?): List<Escolhido> = runCatching {
        val a = JSONArray(t ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optInt("id", -1)
            val p = o.optString("provider")
            if (id < 0 || !componenteValido(p)) return@mapNotNull null
            val e = Escolhido(id, p, o.optInt("x"), o.optInt("y"), o.optInt("w"), o.optInt("h"))
            // Lugar fora da grade (grade mudou, arquivo velho): fica sem lugar e ganha um na próxima montagem.
            if (e.colocado && dentro(e.x, e.y, e.w, e.h)) e else e.copy(x = 0, y = 0, w = 0, h = 0)
        }.distinctBy { it.id }.take(MAX)
    }.getOrDefault(emptyList())

    fun gravar(l: List<Escolhido>): String = JSONArray().apply {
        l.forEach { put(JSONObject().put("id", it.id).put("provider", it.provedor).put("x", it.x).put("y", it.y).put("w", it.w).put("h", it.h)) }
    }.toString()

    /** Cabe mais um? No máximo [MAX], e o mesmo widget (provedor) uma vez só. */
    fun cabe(l: List<Escolhido>, provedor: String): Boolean = l.size < MAX && l.none { it.provedor == provedor }

    /**
     * Limites em células a partir do que o widget declara (px) e do tamanho de uma célula (px). Sem máximo declarado
     * (0), vale a grade toda. O mínimo nunca passa da grade.
     */
    fun limites(minL: Int, minA: Int, maxL: Int, maxA: Int, celL: Float, celA: Float): Limites {
        fun cel(px: Int, c: Float) = if (c <= 0f) 1 else kotlin.math.ceil(px / c.toDouble()).toInt().coerceAtLeast(1)
        val mw = cel(minL, celL).coerceAtMost(COLS)
        val mh = cel(minA, celA).coerceAtMost(LINHAS)
        val xw = if (maxL > 0) cel(maxL, celL).coerceIn(mw, COLS) else COLS
        val xh = if (maxA > 0) cel(maxA, celA).coerceIn(mh, LINHAS) else LINHAS
        return Limites(mw, mh, xw, xh)
    }

    private fun dentro(x: Int, y: Int, w: Int, h: Int) = x >= 0 && y >= 0 && w > 0 && h > 0 && x + w <= COLS && y + h <= LINHAS

    private fun cruza(a: Escolhido, x: Int, y: Int, w: Int, h: Int) =
        a.colocado && x < a.x + a.w && a.x < x + w && y < a.y + a.h && a.y < y + h

    /** O retângulo cabe na grade sem cruzar outro widget já colocado (o próprio [id] não conta). */
    fun livre(l: List<Escolhido>, id: Int, x: Int, y: Int, w: Int, h: Int): Boolean =
        dentro(x, y, w, h) && l.none { it.id != id && cruza(it, x, y, w, h) }

    /**
     * Um lugar para um widget novo de [w] x [h] células: o primeiro livre de cima para baixo, da DIREITA para a
     * esquerda (o canto de cima à direita é o lugar dos widgets nas TVs; a esquerda fica com o nome do mundo e o fundo).
     * Sem lugar do tamanho pedido, tenta menores até o mínimo; null = a grade está cheia.
     */
    fun lugar(l: List<Escolhido>, w: Int, h: Int, lim: Limites): Triple<Int, Int, Pair<Int, Int>>? {
        var ww = w.coerceIn(lim.minW, lim.maxW); var hh = h.coerceIn(lim.minH, lim.maxH)
        while (true) {
            for (y in 0..LINHAS - hh) for (x in COLS - ww downTo 0) if (livre(l, -1, x, y, ww, hh)) return Triple(x, y, ww to hh)
            if (ww > lim.minW) ww-- else if (hh > lim.minH) hh-- else return null
        }
    }

    /** Move uma célula; contra a borda ou outro widget, fica onde está. */
    fun mover(l: List<Escolhido>, id: Int, dx: Int, dy: Int): List<Escolhido> = l.map { e ->
        if (e.id != id || !e.colocado) e
        else if (livre(l, id, e.x + dx, e.y + dy, e.w, e.h)) e.copy(x = e.x + dx, y = e.y + dy) else e
    }

    /**
     * Muda o tamanho uma célula (canto de cima à esquerda parado), dentro dos [lim] do widget. Encostado na borda da
     * direita/de baixo, cresce para o outro lado (o widget anda uma célula) em vez de travar.
     */
    fun redimensionar(l: List<Escolhido>, id: Int, dw: Int, dh: Int, lim: Limites): List<Escolhido> = l.map { e ->
        if (e.id != id || !e.colocado) return@map e
        val w = (e.w + dw).coerceIn(lim.minW, lim.maxW)
        val h = (e.h + dh).coerceIn(lim.minH, lim.maxH)
        when {
            w == e.w && h == e.h -> e
            livre(l, id, e.x, e.y, w, h) -> e.copy(w = w, h = h)
            livre(l, id, e.x - (w - e.w).coerceAtLeast(0), e.y - (h - e.h).coerceAtLeast(0), w, h) ->
                e.copy(x = e.x - (w - e.w).coerceAtLeast(0), y = e.y - (h - e.h).coerceAtLeast(0), w = w, h = h)
            else -> e
        }
    }
}
