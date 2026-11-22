package br.paulo.tvlauncher.privilege

import org.json.JSONArray
import org.json.JSONObject

/**
 * Regras puras do "Como estava antes da Sofaly" ([FactoryBaseline]); sem Android, testadas no computador.
 *
 * A foto ([Snapshot]) guarda o que já estava oculto/desligado na TV e o DNS privado ANTES da primeira mudança da Sofaly.
 * O plano compara com agora: volta a mostrar só o que a Sofaly ocultou depois da foto (o que o fabricante deixou
 * desligado fica como está) e devolve o DNS, deixando de fora o que outra função mantém de propósito ([plano]
 * recebe esses pacotes em `gerenciados`, com o motivo) e os pacotes que nunca se mexe.
 */
object FactoryBaselineRules {
    data class Snapshot(val quando: Long, val ocultos: Set<String>, val dnsModo: String?, val dnsHost: String?)

    data class Plano(
        /** Pacotes para mostrar de volta, em ordem. */
        val mostrar: List<String>,
        /** Pacotes ocultos depois da foto que ficam como estão, com o motivo (ex.: "on_demand"). */
        val mantidos: Map<String, String>,
        /** true = o DNS privado mudou desde a foto. */
        val dnsMudou: Boolean,
    ) { val vazio get() = mostrar.isEmpty() && !dnsMudou }

    /** "opportunistic", vazio ou nulo são o mesmo modo para o Android (o automático). */
    fun modoNormal(m: String?): String = m?.trim()?.takeIf { it.isNotEmpty() && it != "opportunistic" } ?: "opportunistic"

    fun dnsIgual(m1: String?, h1: String?, m2: String?, h2: String?): Boolean {
        val a = modoNormal(m1); val b = modoNormal(m2)
        if (a != b) return false
        return a != "hostname" || (h1?.trim()?.lowercase() ?: "") == (h2?.trim()?.lowercase() ?: "")
    }

    fun plano(foto: Snapshot, ocultosAgora: Set<String>, dnsModoAgora: String?, dnsHostAgora: String?,
              gerenciados: Map<String, String>, nunca: Set<String>): Plano {
        val novos = (ocultosAgora - foto.ocultos - nunca).sorted()
        val mantidos = novos.filter { it in gerenciados }.associateWith { gerenciados.getValue(it) }
        return Plano(novos.filter { it !in gerenciados }, mantidos, !dnsIgual(foto.dnsModo, foto.dnsHost, dnsModoAgora, dnsHostAgora))
    }

    /**
     * Foto para uma TV em que a Sofaly já mexeu antes desta versão: o que a limpeza registrou como "ocultei" não é do
     * fabricante, então sai da foto (fica para voltar). O resto que já estava oculto conta como de fábrica.
     */
    fun foto(quando: Long, ocultosAgora: Set<String>, ocultosPelaLimpeza: Set<String>, dnsModo: String?, dnsHost: String?) =
        Snapshot(quando, ocultosAgora - ocultosPelaLimpeza, dnsModo, dnsHost)

    fun paraJson(s: Snapshot): String = JSONObject().put("quando", s.quando).put("ocultos", JSONArray(s.ocultos.sorted()))
        .put("dnsModo", s.dnsModo ?: JSONObject.NULL).put("dnsHost", s.dnsHost ?: JSONObject.NULL).toString()

    fun deJson(t: String?): Snapshot? = runCatching {
        val o = JSONObject(t ?: return null)
        val a = o.getJSONArray("ocultos")
        Snapshot(o.getLong("quando"), (0 until a.length()).map { a.getString(it) }.toSet(),
            o.optString("dnsModo").takeIf { !o.isNull("dnsModo") && it.isNotEmpty() },
            o.optString("dnsHost").takeIf { !o.isNull("dnsHost") && it.isNotEmpty() })
    }.getOrNull()
}
