package br.paulo.tvlauncher.settings

import android.content.Context
import br.paulo.tvlauncher.Fundo
import br.paulo.tvlauncher.panel.PainelExtras
import br.paulo.tvlauncher.privilege.ExtrasDO

/**
 * Ligar ou desligar uma chave de [Extras] pela TV, com o efeito colateral de cada uma. Um ponto só, usado pela tela
 * Extras e pelas abas das Configurações (ideia 46); antes isso ficava dentro da ExtrasActivity.
 */
object ExtrasTv {
    fun alternar(ctx: Context, c: Extras.Chave) {
        val app = ctx.applicationContext
        Extras.definir(app, c, !Extras.ligado(app, c))
        when (c) {
            Extras.Chave.FILTRO_NOTURNO -> FiltroNoturno.avaliar()
            Extras.Chave.JANELA_OTA, Extras.Chave.TRAVAR_CRITICOS, Extras.Chave.DIARIO_DNS ->
                Fundo.rodar { ExtrasDO.aplicar(app) }   // chamadas do DO fora da thread da tela
            else -> PainelExtras.aoMudar(app, c)
        }
    }

    /** O rótulo de cada chave vem de "extras_<NOME>" nas strings (pt e en). */
    fun rotulo(ctx: Context, c: Extras.Chave): String {
        val id = ctx.resources.getIdentifier("extras_${c.name}", "string", ctx.packageName)
        return if (id != 0) ctx.getString(id) else c.name
    }

    /**
     * O rótulo longo em título + explicação, como as outras linhas das Configurações (teste 100%, 23/11/2026: "Pular
     * abertura (IntroDB): nos episódios…" cortava com "…" numa linha só). Corta no primeiro ": "; sem ele, num "(…)" no
     * fim. Curto, fica inteiro no título.
     */
    fun partes(rotulo: String): Pair<String, String> {
        val t = rotulo.trim()
        val doisPontos = t.indexOf(": ")
        // ": " dentro de um "(…)" não corta ("…no dia (ex.: dia do lixo)…" virava título "…no dia (ex.", Sala 24/11/2026).
        val dentroDeParenteses = doisPontos >= 0 && t.lastIndexOf('(', doisPontos) > t.lastIndexOf(')', doisPontos)
        if (doisPontos in 3..60 && !dentroDeParenteses) return t.substring(0, doisPontos).trim() to maiuscula(t.substring(doisPontos + 2))
        val abre = t.indexOf(" (")
        if (t.length > 32 && abre in 3..60 && t.endsWith(")")) return t.substring(0, abre).trim() to maiuscula(t.substring(abre + 2, t.length - 1))
        // "(…)" no meio: título até ele, o resto vira a explicação sem os parênteses.
        val fecha = if (abre >= 0) t.indexOf(')', abre) else -1
        if (t.length > 32 && abre in 3..60 && fecha > abre) return t.substring(0, abre).trim() to
            maiuscula((t.substring(abre + 2, fecha) + t.substring(fecha + 1)).trim())
        return t to ""
    }

    private fun maiuscula(s: String) = s.trim().replaceFirstChar { it.uppercase() }
}
