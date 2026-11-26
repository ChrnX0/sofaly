package br.paulo.tvlauncher.settings

import android.widget.LinearLayout
import br.paulo.tvlauncher.ConfigLauncherActivity
import br.paulo.tvlauncher.R

/**
 * "Ajuste fino" na aba Aparência: uma linha só, que abre o Ajuste fino com a HOME à vista.
 *
 * Porquê (Paulo, 25/11/2026: "a gente tem opção pra caramba"; onda A da simplificação, "cada opção num lugar só"):
 * nome das categorias, jeito da barra, Continuar, letra e formato/cor da hora existiam aqui E no Ajuste fino. Duas
 * portas para a mesma chave confundem (qual vale?) e dobram a lista. Ficam só no Ajuste fino, que mostra o resultado
 * na hora; aqui fica a porta para ele.
 */
internal fun ConfigLauncherActivity.linhasAjusteFino(c: LinearLayout) {
    c.addView(linha(getString(R.string.ft_abrir), getString(R.string.ft_abrir_det), seta = true) {
        br.paulo.tvlauncher.FineTuneActivity.abrir(this)
    }, lp())
}
