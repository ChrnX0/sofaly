package br.paulo.tvlauncher

import br.paulo.tvlauncher.observer.AppProfile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ficha que volta do backup ou de outra TV: só nome de pacote de verdade vira card (revisor, 25/11/2026). */
class AppProfilePkgTest {
    private fun cards(json: String) = AppProfile.fromJson(JSONObject(json))["p"]?.cards?.keys.orEmpty()

    @Test fun descartaPacoteInvalido() {
        val bom = AppProfile.toJson(mapOf("p" to AppProfile.State(mapOf("com.netflix.ninja" to AppProfile.Card("com.netflix.ninja", 1L))))).toString()
        assertEquals(setOf("com.netflix.ninja"), cards(bom))
        assertEquals(emptySet<String>(), cards(bom.replace("com.netflix.ninja", "../../etc;rm")))
        assertEquals(emptySet<String>(), cards(bom.replace("com.netflix.ninja", "a." + "b".repeat(200))))
    }
}
