package br.paulo.tvlauncher

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Nenhum texto da TV ensina tecla (Paulo, 26/11/2026: "essas dicas são absolutamente idiotas. remove de tudo").
 * Passa por TODAS as línguas (values*): um texto com "OK" em maiúsculas ou com seta (◀ ▶ ▲ ▼) reprova, a não ser que
 * a chave esteja em [PERMITIDOS], cada uma com o motivo. Em pt e en também reprova "segure/aperte/hold/press" seguido
 * do nome de uma tecla. O lugar das teclas é Configurações > Ajuda (chaves aj_*, montadas por nome em Manual.kt).
 */
class SemDicaDeTeclaTest {

    private val PERMITIDOS = mapOf(
        // O próprio botão ou tecla como rótulo (tela de teclas, Escolher tecla, Gravar, Jogos, Rede).
        "cuidar_ok" to "botão OK", "cuidar_tecla_ok" to "nome da tecla", "grv_dica_ok" to "nome da tecla",
        "grv_dica_segurar" to "nome da tecla", "casa_tecla_segurar_ok" to "nome da tecla", "jogos_tecla_segurar" to "nome da tecla",
        "rede_tecla_segurar" to "nome da tecla", "ver_ok" to "botão OK",
        // Status "ok" (rede ok) e o "Ok Google" do assistente, não a tecla.
        "diag_r_ok" to "status", "diag_r_vpn" to "status", "jogos_chip_ok" to "status", "ief_s_ok" to "status",
        "ief_s_ok_em" to "status", "dl_para_nao" to "Ok Google",
        // ▶ é o desenho de tocar no botão, não dica.
        "filmes_assistir" to "ícone de tocar", "series_assistir" to "ícone de tocar", "series_continuar_curto" to "ícone de tocar",
        // O passo é a própria tarefa: sem ele a pessoa não consegue terminar (confirmar, parear, medir, chegar a uma tela).
        "bv_dev_det" to "passo de Opções do desenvolvedor", "pn_confirme" to "confirmar o pareamento", "cc_confirme_na_tv" to "confirmar na TV",
        "cc_acoes_ok" to "regra de segurança", "fb_confirmar" to "confirmar ação que não volta", "elv_lp_det_depois" to "passo da medição",
        "cuidar_lembrete_explica" to "consentimento só por tecla física", "cuidar_lembrete_explica_aviso" to "consentimento só por tecla física",
        "famc_ok_vi" to "a única ação do aviso", "rede_ainda_assistindo" to "pergunta de ainda assistindo",
        "abertura_inicio_marcado" to "passo de marcar a abertura", "cl_cf_assistir_det" to "caminho até o filtro",
        "cl_cf_bloqueado_det" to "▲▼ é a troca de canal, não dica", "sob_demanda_ligado" to "explica o congelar",
        "wp_cfg_det" to "OK = permissão", "teclas_instrucao" to "tela de testar teclas",
        "rw_voltar2" to "nome do ajuste (a tecla é o ajuste)", "sofy_lost_text" to "a Sofy ensinando a voltar quando a pessoa se perde",
        // Só para o leitor de tela (contentDescription / announceForAccessibility), nunca aparece escrito.
        "mu_lido_desc" to "leitor de tela", "mu_nao_lido_desc" to "leitor de tela", "rw_reordenar_dica" to "leitor de tela",
    )

    private val linha = Regex("""<string name="([a-z0-9_]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val tecla = Regex("""\bOK\b|[◀▶▲▼]""")
    private val verbo = Regex("""\b(segure|aperte|hold|press)\s+(o\s+|a\s+)?(OK|VOLTAR|BACK|HOME|INÍCIO)\b""", RegexOption.IGNORE_CASE)
    private val segurado = Regex("""\b(VOLTAR|BACK|OK|HOME)\s+(segurado|held)\b""")

    @Test
    fun nenhumTextoEnsinaTecla() {
        val pastas = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") }!!
        assertTrue("achei poucas línguas", pastas.size >= 20)
        val achados = mutableListOf<String>()
        for (p in pastas.sortedBy { it.name }) for (x in p.listFiles { f -> f.extension == "xml" }!!.sortedBy { it.name }) {
            val baseOuPt = p.name == "values" || p.name == "values-pt"
            for (m in linha.findAll(x.readText())) {
                val (k, v) = m.destructured
                if (k.startsWith("aj_") || k in PERMITIDOS) continue
                if (tecla.containsMatchIn(v) || (baseOuPt && (verbo.containsMatchIn(v) || segurado.containsMatchIn(v))))
                    achados += "${p.name}/${x.name} $k: $v"
            }
        }
        assertTrue("Texto com dica de tecla (tire a dica, ou ponha a chave em PERMITIDOS com o motivo):\n" +
            achados.take(40).joinToString("\n"), achados.isEmpty())
    }

    @Test
    fun permitidosAindaExistem() {
        // Chave permitida que sumiu do app sai da lista (senão a lista vira um buraco).
        val todas = File("src/main/res/values").listFiles { f -> f.extension == "xml" }!!.flatMap { x -> linha.findAll(x.readText()).map { it.groupValues[1] } }.toSet()
        val sobrando = PERMITIDOS.keys - todas
        assertTrue("Em PERMITIDOS mas não existe mais: $sobrando", sobrando.isEmpty())
    }
}
