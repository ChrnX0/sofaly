package br.paulo.tvlauncher.settings

import android.content.Context
import br.paulo.tvlauncher.home.ContinueOption
import br.paulo.tvlauncher.home.HomeLooks
import br.paulo.tvlauncher.home.StripBackground
import br.paulo.tvlauncher.home.StripHome
import org.json.JSONObject

/**
 * Configurar por objetivo (Paulo, 25/11/2026, onda D da simplificação): 4 botões grandes no alto das Configurações.
 *
 * Porquê: quem abre as Configurações quase sempre quer uma coisa ("a letra está pequena", "tem coisa demais na tela"),
 * não uma opção. Para quê: um OK muda as várias opções que levam àquele objetivo; o mesmo botão desfaz.
 * Como: cada objetivo é uma lista "chave -> número" das preferências que já existem (Extras); a foto de antes fica
 * guardada ([KEY_FOTO]) até desfazer, inclusive depois de reiniciar. "Proteger as crianças" não mexe aqui: abre a receita
 * Crianças, que já tem o PIN e o resumo do que liga (ReceitasActivity).
 * Referências: Mobbin (Joi e Cal AI: objetivo primeiro, um toque aplica; Jomo: desfazer no mesmo lugar). Puro em [Rules].
 */
object SettingsGoals {
    enum class Goal(val valores: Map<String, Int>) {
        /** Letra 120%, cards grandes com o nome no foco e foco em quadro branco (o visual "Fácil de ver"). */
        READ(HomeLooks.Look.EASY.valores + (TamanhoTexto.CHAVE to 120)),
        /** O visual "Mínimo", sem o Continuar e com a frase fixa no lugar da descrição que muda. */
        CLEAN(HomeLooks.Look.MINIMAL.valores + mapOf(ContinueOption.KEY to 0, StripHome.KEY_DESC to StripHome.Desc.FIXED.ordinal)),
        /** Sem foto de fundo e com a luz calma no ícone do mundo (menos desenho); o movimento vai ao econômico. */
        ENERGY(mapOf(StripBackground.KEY to StripBackground.Choice.THEME.ordinal,
            StripHome.KEY_WORLD_ANIM to StripHome.WorldAnim.LIGHT.ordinal)),
        /** Abre a receita Crianças (catálogo infantil, hora de dormir, card Jogos), com o PIN dela. */
        KIDS(emptyMap());
    }

    const val KEY_ATIVO = "goal_active"
    const val KEY_FOTO = "goal_snapshot"
    /** No objetivo ENERGY: como estava o movimento forçado ("" = automático). */
    private const val MOVIMENTO = "~movimento"

    object Rules {
        /** A foto do que o objetivo vai mudar (null = a chave nunca foi gravada), em JSON para guardar. */
        fun foto(g: Goal, ler: (String) -> Int?): String =
            JSONObject().apply { for (k in g.valores.keys) put(k, ler(k) ?: JSONObject.NULL) }.toString()

        /** O que gravar para desfazer: chave -> número, ou null para apagar a chave. Foto ilegível = nada. */
        fun desfazer(foto: String?): Map<String, Int?> {
            val o = runCatching { JSONObject(foto ?: return emptyMap()) }.getOrNull() ?: return emptyMap()
            return o.keys().asSequence().filter { !it.startsWith("~") }.associateWith { k -> if (o.isNull(k)) null else o.optInt(k) }
        }
    }

    fun ativo(ctx: Context): Goal? = Goal.entries.firstOrNull { it.name == Extras.texto(ctx, KEY_ATIVO) }

    /** Aplica [g] (desfazendo antes outro objetivo ativo, para a foto ser sempre a de antes de todos). */
    fun aplicar(ctx: Context, g: Goal) {
        if (g == Goal.KIDS) return
        ativo(ctx)?.let { desfazer(ctx) }
        val ler: (String) -> Int? = { k -> Extras.numero(ctx, k, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } }
        val foto = JSONObject(Rules.foto(g, ler))
        if (g == Goal.ENERGY) {
            foto.put(MOVIMENTO, ctx.getSharedPreferences("movimento", Context.MODE_PRIVATE).getString("forcado", "") ?: "")
            br.paulo.tvlauncher.home.MovimentoAdaptativo.comando(ctx, "economico")
        }
        Extras.definirTexto(ctx, KEY_FOTO, foto.toString())
        Extras.definirTexto(ctx, KEY_ATIVO, g.name)
        for ((k, v) in g.valores) Extras.definirNumero(ctx, k, v)
        FocusStyle.ler(ctx)
    }

    fun desfazer(ctx: Context) {
        val foto = Extras.texto(ctx, KEY_FOTO)
        for ((k, v) in Rules.desfazer(foto)) if (v == null) Extras.apagar(ctx, k) else Extras.definirNumero(ctx, k, v)
        runCatching { JSONObject(foto) }.getOrNull()?.takeIf { it.has(MOVIMENTO) }?.let { o ->
            val antes = o.optString(MOVIMENTO)
            br.paulo.tvlauncher.home.MovimentoAdaptativo.comando(ctx, antes.ifEmpty { "auto" }.lowercase())
        }
        Extras.apagar(ctx, KEY_FOTO); Extras.apagar(ctx, KEY_ATIVO)
        FocusStyle.ler(ctx)
    }
}
