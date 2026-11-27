package br.paulo.tvlauncher.settings

import br.paulo.tvlauncher.settings.SettingsIntents.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Onda E (25/11/2026): frases do jeito que a pessoa fala viram o pedido certo; busca de uma palavra continua busca. */
class SettingsIntentsTest {
    private fun frases(pasta: String): Map<Action, List<Set<String>>> {
        val f = listOf("src/main/res/$pasta/strings_settings_intents.xml", "app/src/main/res/$pasta/strings_settings_intents.xml")
            .map(::File).first { it.isFile }
        val t = Regex("""name="si_frases">([^<]*)<""").find(f.readText())!!.groupValues[1]
        return SettingsIntents.modelos(t)
    }
    private val pt by lazy { frases("values-pt") }
    private val en by lazy { frases("values") }

    @Test fun portugues() {
        assertEquals(Action.TEXT_BIGGER, SettingsIntents.entender("Deixa a letra maior", pt))
        assertEquals(Action.TEXT_BIGGER, SettingsIntents.entender("aumenta essa letra aí", pt))
        assertEquals(Action.TEXT_SMALLER, SettingsIntents.entender("letra menor por favor", pt))
        assertEquals(Action.GOAL_CLEAN, SettingsIntents.entender("quero a tela mais limpa", pt))
        assertEquals(Action.GOAL_ENERGY, SettingsIntents.entender("gastar menos energia", pt))
        assertEquals(Action.GOAL_READ, SettingsIntents.entender("não enxergo direito", pt))
        assertEquals(Action.LOOK_NIGHT, SettingsIntents.entender("põe o visual noite", pt))
    }

    @Test fun ingles() {
        assertEquals(Action.TEXT_BIGGER, SettingsIntents.entender("Make the text bigger", en))
        assertEquals(Action.GOAL_ENERGY, SettingsIntents.entender("save some energy", en))
    }

    /** #550: antes a normalização apagava tudo fora de a-z, e as frases em russo (e grego, árabe...) viravam nada. */
    @Test fun russo() {
        val ru = frases("values-ru")
        assertEquals(Action.TEXT_BIGGER, SettingsIntents.entender("Сделай шрифт больше", ru))
        assertEquals(Action.GOAL_ENERGY, SettingsIntents.entender("хочу экономить энергию", ru))
    }

    @Test fun umaPalavraEhBuscaNaoPedido() {
        assertNull(SettingsIntents.entender("letra", pt))
        assertNull(SettingsIntents.entender("wifi senha", pt))
    }

    @Test fun todaAcaoTemFraseNosDoisIdiomas() {
        assertEquals(Action.entries.toSet(), pt.keys)
        assertEquals(Action.entries.toSet(), en.keys)
    }
}
