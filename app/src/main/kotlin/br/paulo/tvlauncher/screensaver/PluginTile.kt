package br.paulo.tvlauncher.screensaver

/**
 * Botões de outros apps no painel rápido (24/11/2026, proposta B das extensões). Porquê: o Android de TV não tem os
 * "blocos rápidos" do celular (o TileService só a interface do sistema pode ligar), então app de VPN, luz ou caixa de
 * som não tem como pôr um atalho no painel. Para quê: segurar VOLTAR e ligar/desligar a coisa sem abrir o app. Como:
 * contrato de DADOS, igual ao do cartão ([PluginCartao]); quem desenha é o painel rápido, no nosso estilo.
 *
 * O app declara um ContentProvider exportado com `<action android:name="tv.sofaly.plugin.TILE"/>`. O painel lê
 * `content://<autoridade>/tile` (UMA linha: [COLUNAS]) e, no OK, chama `ContentProvider.call(..., "click", ...)`; o
 * app faz a ação dele e o painel lê a linha de novo. Ícone: só um nome da nossa lista ([ICONES]), desenhado por nós.
 *
 * Mesmas regras dos outros plugins: só aparece com o OK do dono (PIN), preso ao certificado; no máximo [MAX]; a guarda
 * de saúde ([ExtensionHealth]) desliga quem não responde. O painel não manda nada ao app além do "click".
 * Liga com: PainelRapidoActivity (desenha), PluginsActivity e PanelPlugins (o OK), Rotinas (a ação de clicar pode virar
 * passo de rotina, depois). Só a parte pura (PluginTileTest).
 */
object PluginTile {
    const val ACAO = "tv.sofaly.plugin.TILE"
    const val CAMINHO = "tile"
    const val METODO_CLIQUE = "click"
    const val MAX = 2
    val COLUNAS = arrayOf("title", "detail", "state", "icon")
    /**
     * Ícones que o plugin pode pedir: nome público (em inglês, é contrato) -> o nosso desenho do painel rápido e da Casa.
     * Outro nome = [ICONE_PADRAO].
     */
    val ICONES = mapOf("light" to "lampada", "plug" to "tomada", "fan" to "ventilador", "curtain" to "cortina",
        "scene" to "cena", "routine" to "rotina", "key" to "chave", "ac" to "ar", "thermometer" to "termometro",
        "camera" to "camera", "door" to "porta", "home" to "casa", "dns" to "dns", "audio" to "audio", "timer" to "timer",
        "filter" to "filtro", "wifi" to "wifi", "bluetooth" to "bluetooth", "screensaver" to "protetor", "sleep" to "dormir")
    const val ICONE_PADRAO = "launcher"

    enum class Estado { LIGADO, DESLIGADO, NENHUM }

    data class Botao(val pacote: String, val titulo: String, val detalhe: String, val estado: Estado, val icone: String)

    private fun texto(t: String?, max: Int) = (t ?: "").map { if (it.isISOControl()) ' ' else it }.joinToString("")
        .replace(Regex("\\s+"), " ").trim().take(max)

    /** A linha do app -> o botão, ou null (sem título). Título até 14 letras e detalhe até 16: cabem no bloco. */
    fun limpar(pacote: String, linha: Map<String, String?>): Botao? {
        val titulo = texto(linha["title"], 14)
        if (titulo.isEmpty()) return null
        val estado = when (linha["state"]?.trim()?.lowercase()) { "on" -> Estado.LIGADO; "off" -> Estado.DESLIGADO; else -> Estado.NENHUM }
        val icone = linha["icon"]?.trim()?.lowercase()?.let { ICONES[it] } ?: ICONE_PADRAO
        return Botao(pacote, titulo, texto(linha["detail"], 16), estado, icone)
    }
}
