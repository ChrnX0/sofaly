package br.paulo.tvlauncher.screensaver

import org.json.JSONArray
import org.json.JSONObject

/**
 * Widgets de outros apps na HOME (24/11/2026, Paulo: "não dá para fazer? já instalei launcher com widgets"). A regra,
 * sem Android (PluginWidgetTest); o lado Android é `home/PluginWidgetsTv.kt`.
 *
 * O Google TV TEM o serviço de widgets (feature android.software.app_widgets, verificado na Sala), mas não tem a tela
 * de consentimento (ACTION_APPWIDGET_BIND). O launcher ganha a permissão de ligar widgets pelo shell próprio
 * (`appwidget grantbind`, o mesmo que o adb faz), uma vez.
 *
 * Como mora na HOME: a HOME não desenha a view viva do widget no meio da cruz (o widget seria um pedaço de tela que o
 * nosso estilo não controla). Ela tira uma FOTO do widget (a view desenhada fora da tela) e a usa como imagem de um
 * cartão de plugin no mundo Apps; OK abre o app dono do widget. Mesmas regras dos cartões: Pro, no máximo [MAX],
 * guarda de saúde, lista `blocked` do servidor.
 */
object PluginWidget {
    const val MAX = 2
    /** Id do nosso AppWidgetHost (qualquer número fixo do app; trocar perde os widgets já ligados). */
    const val HOST_ID = 0x50FA
    private const val PREFIXO = "widget:"
    /** A foto vale por até 5 min sem aviso de mudança (widgets de lista mudam sem chamar updateAppWidget). */
    const val FOTO_MS = 5 * 60_000L

    /** Um widget ligado: [id] do AppWidgetManager, [provedor] = componente "pacote/classe". */
    data class Escolhido(val id: Int, val provedor: String) {
        val pacote: String get() = provedor.substringBefore('/')
    }

    private val COMPONENTE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+/[a-zA-Z0-9_.$]+$")

    fun componenteValido(c: String?): Boolean = c != null && c.length <= 300 && COMPONENTE.matches(c)

    fun ler(t: String?): List<Escolhido> = runCatching {
        val a = JSONArray(t ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optInt("id", -1)
            val p = o.optString("provider")
            if (id < 0 || !componenteValido(p)) null else Escolhido(id, p)
        }.distinctBy { it.id }.take(MAX)
    }.getOrDefault(emptyList())

    fun gravar(l: List<Escolhido>): String =
        JSONArray().apply { l.forEach { put(JSONObject().put("id", it.id).put("provider", it.provedor)) } }.toString()

    /** Cabe mais um? No máximo [MAX], e o mesmo widget (provedor) uma vez só. */
    fun cabe(l: List<Escolhido>, provedor: String): Boolean = l.size < MAX && l.none { it.provedor == provedor }

    /**
     * Endereço da foto no cartão: muda quando o widget avisa mudança ([versao]) ou a cada [FOTO_MS], para a HOME só
     * redesenhar quando a foto pode ter mudado (ela compara a lista de cartões).
     */
    fun imagem(id: Int, versao: Int, agora: Long): String = "$PREFIXO$id:$versao:${agora / FOTO_MS}"

    fun ehWidget(u: String?): Boolean = u?.startsWith(PREFIXO) == true

    /** O id do widget num endereço de [imagem]; null se não for de widget. */
    fun idDaImagem(u: String?): Int? = u?.takeIf { ehWidget(it) }?.removePrefix(PREFIXO)?.substringBefore(':')?.toIntOrNull()

    /** Tamanho da foto (px): o mínimo do widget, preso entre 2 e 4,5 colunas de 80 dp e 1 e 3 linhas de 100 dp. */
    fun tamanho(minL: Int, minA: Int, densidade: Float): Pair<Int, Int> {
        val l = minL.coerceIn((160 * densidade).toInt(), (360 * densidade).toInt())
        val a = minA.coerceIn((100 * densidade).toInt(), (300 * densidade).toInt())
        return l to a
    }
}
