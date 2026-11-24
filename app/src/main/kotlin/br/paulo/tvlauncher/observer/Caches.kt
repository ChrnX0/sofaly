package br.paulo.tvlauncher.observer

import android.content.Context
import android.os.Environment
import android.os.StatFs
import br.paulo.tvlauncher.R
import br.paulo.tvlauncher.alarm.ConsumoRede
import br.paulo.tvlauncher.privilege.GuardiaoShizuku

/**
 * Limpar caches (10/11/2026, 2ª leva das ideias; li no TV Remote, "Clean caches"): pede ao Android para apagar o cache
 * de TODOS os apps (`pm trim-caches`, pelo shell próprio, uid 2000). Logins e dados dos apps ficam: só o cache sai.
 * O Chromecast tem 8 GB e enche com o cache de YouTube, Netflix e Play. Chamar fora da thread da tela.
 */
object Caches {
    private fun livre(): Long = StatFs(Environment.getDataDirectory().path).availableBytes

    /** O resultado: [liberado] != null = limpou. Antes era só um texto em português que a manutenção da noite
     *  conferia por `startsWith("liberado")` e a TV mostrava em português em qualquer idioma (teste de código 24/11/2026). */
    class Limpeza(val liberado: Long?, val livre: Long, val semShell: Boolean = false, val falha: String? = null) {
        val limpou get() = liberado != null
        /** Painel (traduz no navegador), adb e registro. */
        fun texto(): String = when {
            semShell -> "sem shell (o adb próprio está desligado?)"
            liberado == null -> "falhou: $falha"
            else -> "liberado ${ConsumoRede.texto(liberado)}; livre agora ${ConsumoRede.texto(livre)}"
        }
        /** Tela da TV, no idioma dela. */
        fun textoTv(ctx: Context): String = when {
            semShell -> ctx.getString(R.string.ca_sem_shell)
            liberado == null -> ctx.getString(R.string.ca_falhou, falha ?: "?")
            else -> ctx.getString(R.string.ca_liberado, ConsumoRede.texto(liberado), ConsumoRede.texto(livre))
        }
    }

    fun limpar(ctx: Context): Limpeza {
        val antes = livre()
        // "Quero 1 TB livre": o Android apaga todo cache que puder para chegar perto disso.
        val saida = GuardiaoShizuku.comando(ctx, "pm trim-caches 1000G") ?: return Limpeza(null, antes, semShell = true)
        if (saida.contains("Error", ignoreCase = true) || saida.contains("Exception")) return Limpeza(null, livre(), falha = saida.trim().take(120))
        val depois = livre()
        return Limpeza((depois - antes).coerceAtLeast(0), depois)
    }

    fun livreTexto(): String = ConsumoRede.texto(livre())
}
