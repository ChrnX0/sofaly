package br.paulo.tvlauncher

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import br.paulo.tvlauncher.family.UsoTv
import br.paulo.tvlauncher.observer.RegistroErros
import br.paulo.tvlauncher.observer.VitaisTv
import android.widget.EditText
import android.widget.TextView
import br.paulo.tvlauncher.panel.QrCodigo
import br.paulo.tvlauncher.relay.RelayTv
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Relatar um problema de dentro do app (degrau 3, 16/11/2026). Configurações › Sistema › "Relatar um problema" mostra
 * um QR: o celular abre o formulário de "issue" do repositório público já preenchido com os dados TÉCNICOS (versão,
 * Android, modelo, uso da TV, quedas da semana e o tipo do último erro). Nada de nome, conta, rede, IP ou cidade, e a
 * tela mostra o texto inteiro que vai junto, antes de a pessoa enviar (ela ainda escreve o que aconteceu).
 * A montagem do texto e do link é pura (RelatarProblemaTest).
 *
 * 17/11/2026 (revisão dos degraus, item 2a): quem não é técnico não tem conta no GitHub, e o repositório de código vai
 * fechar. Com um e-mail de suporte no build (BuildConfig.SUPORTE_EMAIL), o QR vira um "mailto:" que abre o app de
 * e-mail do celular já preenchido; sem ele, continua o formulário do GitHub. Quando o servidor da Sofaly existir, o
 * formulário sem conta entra aqui como terceira opção, na frente das duas.
 *
 * 26/11/2026 (Brainstorm, item 6): o link do GitHub saiu. O repositório é público e a issue ficava aberta para o mundo,
 * com os dados da TV. Agora a pessoa escreve na própria TV (o teclado tem microfone) e o relato vai ASSINADO pela TV
 * para o api.sofaly.tv (POST /v1/report, server/api/src/reports.js), que guarda em particular, limpa e-mail, endereço,
 * IP e números longos e só aceita 5 por dia por TV. A Sofy lê a fila e abre issue só com o que é técnico. O e-mail de
 * suporte (quando o build tem um) continua como QR de reserva.
 */
object RelatarProblema {
    data class Dados(val versao: String, val codigo: Long, val android: String, val modelo: String, val uso: String,
                     val quedas7d: Int, val travadas7d: Int, val ultimoErro: String?)

    /** Os nomes das linhas, no idioma da TV (21/11/2026: a tela mostrava "version, use, crashes" em inglês). */
    data class Rotulos(val oQue: String = "What happened", val versao: String = "Version", val uso: String = "Mode",
                       val quedas: String = "Crashes / freezes (7 days)", val erro: String = "Last error")

    fun rotulos(ctx: Context) = Rotulos(ctx.getString(R.string.rel_l_oque), ctx.getString(R.string.rel_l_versao),
        ctx.getString(R.string.rel_l_uso), ctx.getString(R.string.rel_l_quedas), ctx.getString(R.string.rel_l_erro))

    /** O que vai junto, linha por linha (a TV mostra exatamente isto, e o QR leva o mesmo texto). */
    fun corpo(d: Dados, r: Rotulos = Rotulos()): String = buildString {
        append("**${r.oQue}:**\n\n\n\n---\n")
        append("- ${r.versao}: ${d.versao} (${d.codigo})\n")
        append("- Android: ${d.android} · ${d.modelo}\n")
        append("- ${r.uso}: ${d.uso}\n")
        append("- ${r.quedas}: ${d.quedas7d}/${d.travadas7d}\n")
        if (!d.ultimoErro.isNullOrBlank()) append("- ${r.erro}: ${tipoDoErro(d.ultimoErro)}\n")
    }

    /**
     * Do resumo de um erro guardado (RegistroErros), só o TIPO (ex.: "IllegalStateException"): a mensagem do erro pode
     * trazer nome de arquivo, endereço ou texto digitado, então não vai.
     */
    fun tipoDoErro(resumo: String): String =
        Regex("""([A-Za-z_$][\w$]*(?:Exception|Error))""").find(resumo)?.groupValues?.get(1) ?: "?"

    private fun c(t: String) = URLEncoder.encode(t, "UTF-8").replace("+", "%20")

    /** E-mail plausível (um @, domínio com ponto, sem espaço nem caractere que quebre o mailto). */
    fun emailValido(e: String) = Regex("""^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$""").matches(e.trim())

    /** O link do e-mail: assunto com versão e modelo (a caixa de entrada já separa por aparelho), corpo igual ao do GitHub. */
    fun mailto(email: String, d: Dados, r: Rotulos = Rotulos()): String =
        "mailto:${email.trim()}?subject=${c("[TV] ${d.modelo} · ${d.codigo}")}&body=${c(corpo(d, r).replace("**", ""))}"

    /** O QR de reserva: só o e-mail de suporte, quando o build tem um válido. Nunca mais o GitHub público. */
    fun destino(email: String, d: Dados, r: Rotulos = Rotulos()): String? =
        if (emailValido(email)) mailto(email, d, r) else null

    /** O relato que vai ao servidor (campos em inglês, src/reports.js). [texto] é o que a pessoa escreveu. */
    fun envio(d: Dados, texto: String): JSONObject = JSONObject()
        .put("kind", "tv").put("version", d.versao.filter { it.isLetterOrDigit() || it in ".+-_" }.take(40)).put("code", d.codigo)
        .put("android", d.android.take(20)).put("model", d.modelo.take(60)).put("mode", d.uso.take(40))
        .put("crashes", d.quedas7d).put("freezes", d.travadas7d).put("text", texto.trim().take(2000))
        .put("errors", JSONArray().apply {
            if (!d.ultimoErro.isNullOrBlank()) put(JSONObject().put("type", tipoDoErro(d.ultimoErro)).put("version", d.codigo))
        })

    /**
     * O relato do painel (Erros do launcher): os erros já filtrados da TV (ErrosFiltrados: tipo, dia, versão e só os
     * quadros da pilha, sem a mensagem). [erros] é o JSON de ErrosFiltrados.json.
     */
    fun envioErros(d: Dados, erros: JSONObject, texto: String = ""): JSONObject = envio(d, texto).put("kind", "errors")
        .put("errors", JSONArray().apply {
            val a = erros.optJSONArray("erros") ?: JSONArray()
            for (i in 0 until minOf(a.length(), 5)) {
                val e = a.optJSONObject(i) ?: continue
                val pilha = e.optJSONArray("pilha") ?: JSONArray()
                put(JSONObject().put("type", e.optString("tipo").takeIf { Regex("^[A-Za-z_$][\\w$]*$").matches(it) } ?: "error")
                    .put("day", e.optString("dia").takeIf { Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it) } ?: "")
                    .put("version", codigoDe(e.optString("versao")))
                    .put("stack", JSONArray().apply { for (j in 0 until minOf(pilha.length(), 15)) put(pilha.optString(j)) }))
            }
        })

    /** "3.4 (475033)" ou "475033" -> 475033; sem número, 0. */
    fun codigoDe(versao: String?): Long =
        Regex("""\((\d{1,12})\)""").find(versao ?: "")?.groupValues?.get(1)?.toLongOrNull()
            ?: versao?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: 0

    enum class Resultado { ENVIADO, COTA, FALHOU }

    /** Manda ao servidor, assinado pela TV. Bloqueia: chame fora da thread da tela. */
    fun enviar(ctx: Context, relato: JSONObject): Resultado = runCatching {
        when (RelayTv.pedir(ctx, "POST", "/v1/report", relato.toString().toByteArray()).codigo) {
            201 -> Resultado.ENVIADO
            429 -> Resultado.COTA
            else -> Resultado.FALHOU
        }
    }.getOrDefault(Resultado.FALHOU)

    fun dados(ctx: Context): Dados {
        val v = VitaisTv.resumo7(ctx)
        return Dados(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong(), Build.VERSION.RELEASE ?: "?", Build.MODEL ?: "?",
            ctx.getString(when (UsoTv.atual(ctx)) {
                UsoTv.SIMPLES -> R.string.uso_simples; UsoTv.COMPLETO -> R.string.uso_completo; UsoTv.CUIDA -> R.string.uso_cuida
            }), v.quedas, v.travadas, RegistroErros.listar(ctx).firstOrNull { it.daVersao(BuildConfig.VERSION_CODE.toLong()) }?.resumo)
    }
}

class RelatarProblemaActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = RelatarProblema.dados(this)
        val r = RelatarProblema.rotulos(this)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Estilo.fundoTela(context)
            setPaddingRelative(dp(Estilo.MARGEM_LATERAL), dp(Estilo.MARGEM_VERTICAL + 9), dp(Estilo.MARGEM_LATERAL), dp(Estilo.MARGEM_VERTICAL))
        }
        val texto = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texto.addView(Estilo.tituloTela(this, getString(R.string.rel_titulo)))
        texto.addView(Estilo.texto(this, Estilo.Papel.CORPO, getString(R.string.rel_texto)).apply { textSize = 20f },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        // O que a pessoa escreve (o teclado da TV tem microfone). Vai junto no relato, limpo no servidor.
        val campo = EditText(this).apply {
            hint = getString(R.string.rel_escreva)
            val ref = Estilo.texto(context, Estilo.Papel.CORPO)
            textSize = ref.textSize / resources.displayMetrics.scaledDensity   // o tamanho do Papel CORPO, não um número à mão
            setTextColor(ref.currentTextColor)
            setHintTextColor(Estilo.texto(context, Estilo.Papel.LEGENDA).currentTextColor)
            maxLines = 3
            filters = arrayOf(android.text.InputFilter.LengthFilter(2000))
        }
        texto.addView(campo, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        texto.addView(Estilo.texto(this, Estilo.Papel.TITULO, getString(R.string.rel_vai_junto)),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
        // O que vai junto, sem o cabeçalho em branco para a pessoa escrever.
        texto.addView(Estilo.texto(this, Estilo.Papel.LEGENDA, RelatarProblema.corpo(d, r).substringAfter("---\n").trim()).apply { textSize = 18f },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        val situacao: TextView = Estilo.texto(this, Estilo.Papel.CORPO, "").apply { visibility = android.view.View.GONE }
        val botoes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val enviar = Estilo.linhaOpcao(this, getString(R.string.rel_enviar))
        enviar.setOnClickListener {
            enviar.isEnabled = false
            situacao.visibility = android.view.View.VISIBLE; situacao.text = getString(R.string.rel_enviando)
            val relato = RelatarProblema.envio(d, campo.text?.toString().orEmpty())
            Fundo.rodar("Relatar") {
                val res = RelatarProblema.enviar(applicationContext, relato)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    situacao.text = getString(when (res) {
                        RelatarProblema.Resultado.ENVIADO -> R.string.rel_enviado
                        RelatarProblema.Resultado.COTA -> R.string.rel_cota
                        RelatarProblema.Resultado.FALHOU -> R.string.rel_falhou
                    })
                    enviar.isEnabled = res == RelatarProblema.Resultado.FALHOU   // falhou: pode tentar de novo
                }
            }
        }
        val fechar = Estilo.linhaOpcao(this, getString(R.string.fp_fechar)).apply { setOnClickListener { finish() } }
        botoes.addView(enviar, LinearLayout.LayoutParams(dp(260), -2))
        botoes.addView(fechar, LinearLayout.LayoutParams(dp(200), -2).apply { marginStart = dp(16) })
        texto.addView(botoes, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20) })
        texto.addView(situacao, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        raiz.addView(texto, LinearLayout.LayoutParams(0, -2, 1f))
        // Reserva: o QR do e-mail de suporte, só quando o build tem um.
        RelatarProblema.destino(BuildConfig.SUPORTE_EMAIL, d, r)?.let { mailto ->
            val lado = (resources.displayMetrics.heightPixels * 0.42f).toInt()
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            col.addView(ImageView(this).apply { setImageBitmap(QrCodigo.bitmap(mailto, lado)) }, LinearLayout.LayoutParams(lado, lado))
            col.addView(Estilo.texto(this, Estilo.Papel.LEGENDA, getString(R.string.rel_email)).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(lado, -2).apply { topMargin = dp(8) })
            raiz.addView(col, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(40) })
        }
        setContentView(raiz)
        campo.requestFocus()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
}
