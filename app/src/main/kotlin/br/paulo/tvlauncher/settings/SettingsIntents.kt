package br.paulo.tvlauncher.settings

import java.text.Normalizer

/**
 * Pedir em vez de procurar (Paulo, 25/11/2026, onda E da simplificação): na busca das Configurações, uma frase como
 * "deixa a letra maior" vira um botão "Fazer agora" no topo dos resultados; o mesmo botão desfaz. Falar no microfone do
 * teclado da TV escreve a frase no campo, então vale para voz também.
 *
 * Porquê: a busca achava a OPÇÃO, mas a pessoa ainda tinha que entender a opção e o valor. Para quê: pedir o resultado.
 * Como (puro, SettingsIntentsTest): cada pedido tem frases-modelo por idioma (string `si_frases`); a frase digitada casa
 * com um modelo quando TODAS as palavras do modelo aparecem nela (sem acento, sem maiúscula, em qualquer ordem). Só ações
 * que já existem e que se desfazem sozinhas: tamanho da letra, os objetivos e os visuais prontos. Nunca nada de rede,
 * dono, PIN ou apagar. Referência: Mobbin, busca do Google (resposta acima da lista) e Joi (comando em frase).
 */
object SettingsIntents {
    enum class Action { TEXT_BIGGER, TEXT_SMALLER, GOAL_READ, GOAL_CLEAN, GOAL_ENERGY, LOOK_SOFALY, LOOK_NIGHT, LOOK_MOUNTAIN, LOOK_MINIMAL }

    fun normalizar(t: String): String =
        Normalizer.normalize(t.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            // Letras de qualquer escrita (russo, grego, árabe...), não só a-z (#550).
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ").replace(Regex("\\s+"), " ").trim()

    /** "TEXT_BIGGER=letra maior|aumenta letra;GOAL_CLEAN=tela limpa" -> ação -> modelos (já normalizados, em palavras). */
    fun modelos(frases: String): Map<Action, List<Set<String>>> = frases.split(';').mapNotNull { par ->
        val acao = Action.entries.firstOrNull { it.name == par.substringBefore('=').trim() } ?: return@mapNotNull null
        acao to par.substringAfter('=', "").split('|').map { normalizar(it).split(' ').filter { w -> w.isNotEmpty() }.toSet() }
            .filter { it.isNotEmpty() }
    }.toMap()

    /** O pedido que a [frase] faz, ou null. Vários casando: o de modelo mais longo (mais específico) ganha. */
    fun entender(frase: String, modelos: Map<Action, List<Set<String>>>): Action? {
        val palavras = normalizar(frase).split(' ').toSet()
        if (palavras.size < 2) return null   // uma palavra só é busca, não pedido ("letra" procura a opção)
        return modelos.flatMap { (a, ms) -> ms.filter { palavras.containsAll(it) }.map { a to it.size } }
            .maxByOrNull { it.second }?.first
    }

    /** Faz [a] e devolve como desfazer. */
    fun fazer(ctx: android.content.Context, a: Action): () -> Unit {
        fun objetivo(g: SettingsGoals.Goal): () -> Unit { SettingsGoals.aplicar(ctx, g); return { SettingsGoals.desfazer(ctx) } }
        fun visual(l: br.paulo.tvlauncher.home.HomeLooks.Look): () -> Unit {
            val foto = br.paulo.tvlauncher.home.HomeLooks.foto(ctx)
            br.paulo.tvlauncher.home.HomeLooks.aplicar(ctx, l)
            return { br.paulo.tvlauncher.home.HomeLooks.voltar(ctx, foto) }
        }
        return when (a) {
            Action.TEXT_BIGGER, Action.TEXT_SMALLER -> {
                val antes = TamanhoTexto.atual(ctx)
                val i = TamanhoTexto.OPCOES.indexOf(antes) + if (a == Action.TEXT_BIGGER) 1 else -1
                TamanhoTexto.definir(ctx, TamanhoTexto.OPCOES[i.coerceIn(0, TamanhoTexto.OPCOES.lastIndex)])
                ({ TamanhoTexto.definir(ctx, antes) })
            }
            Action.GOAL_READ -> objetivo(SettingsGoals.Goal.READ)
            Action.GOAL_CLEAN -> objetivo(SettingsGoals.Goal.CLEAN)
            Action.GOAL_ENERGY -> objetivo(SettingsGoals.Goal.ENERGY)
            Action.LOOK_SOFALY -> visual(br.paulo.tvlauncher.home.HomeLooks.Look.SOFALY)
            Action.LOOK_NIGHT -> visual(br.paulo.tvlauncher.home.HomeLooks.Look.NIGHT)
            Action.LOOK_MOUNTAIN -> visual(br.paulo.tvlauncher.home.HomeLooks.Look.MOUNTAIN)
            Action.LOOK_MINIMAL -> visual(br.paulo.tvlauncher.home.HomeLooks.Look.MINIMAL)
        }
    }
}
