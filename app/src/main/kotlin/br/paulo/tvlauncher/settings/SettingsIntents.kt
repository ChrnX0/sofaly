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
            .replace(Regex("[^a-z0-9 ]+"), " ").replace(Regex("\\s+"), " ").trim()

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
}
