package br.paulo.onetv.celular

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout

/**
 * O quadro "Usar fora de casa?" do controle (27/11/2026; [ApiTv.ativarForaDeCasa] faz a parte da rede).
 *
 * Por quê: o PIN que libera o uso pela internet só era pedido se a pessoa achasse sozinha algo no painel que pedisse
 * o PIN. Para quê: uma pergunta, um campo, um botão, como o "Set up Remote" do ChatGPT e o "Set up a PIN" do Grab
 * (Mobbin): título curto, uma frase do porquê, ação principal e "Agora não" ao lado. Como: as peças de [Visual]
 * (mesmas cores da TV), teclado numérico, e o resultado escrito no próprio quadro, sem fechar quando o PIN erra.
 */
object ForaDeCasa {
    /** Perguntou sozinho uma vez por TV; depois fica só o link no controle. */
    fun jaPerguntou(a: Activity, tv: Pareamento.Tv): Boolean =
        a.getSharedPreferences("controle", android.content.Context.MODE_PRIVATE).getBoolean("fora_perguntou:" + Pareamento.chave(tv), false)

    private fun marcarPerguntou(a: Activity, tv: Pareamento.Tv) =
        a.getSharedPreferences("controle", android.content.Context.MODE_PRIVATE).edit().putBoolean("fora_perguntou:" + Pareamento.chave(tv), true).apply()

    /** [pronto] roda na thread da tela quando o celular ficou pronto para fora de casa. */
    fun perguntar(a: Activity, tv: Pareamento.Tv, pronto: () -> Unit) {
        if (a.isFinishing || a.isDestroyed) return
        marcarPerguntou(a, tv)
        val ui = Handler(Looper.getMainLooper())
        fun dp(v: Int) = Visual.dp(a, v)
        val d = Dialog(a)
        val situacao = Visual.texto(a, "", 15f, Visual.TEXTO_2).apply { setPadding(0, dp(8), 0, 0) }
        val campo = EditText(a).apply {
            hint = a.getString(R.string.cel_fora_pin)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(12))
            setSingleLine(); textSize = 22f; gravity = Gravity.CENTER
            setTextColor(Visual.TEXTO); setHintTextColor(Visual.TEXTO_2)
            background = Visual.cartao(a); setPadding(dp(16), dp(14), dp(16), dp(14))
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        var ocupado = false
        lateinit var ativar: android.widget.TextView
        fun enviar() {
            if (ocupado) return
            val pin = campo.text.toString()
            if (!ApiTv.pinValido(pin)) { situacao.text = a.getString(R.string.cel_fora_pin_formato); return }
            ocupado = true; ativar.alpha = 0.5f; situacao.text = a.getString(R.string.cel_fora_conferindo)
            ApiTv.emSegundoPlano {
                var espera = 0
                val r = ApiTv.ativarForaDeCasa(a.applicationContext, tv, pin) { espera = it }
                ui.post {
                    ocupado = false; ativar.alpha = 1f
                    if (a.isFinishing || a.isDestroyed || !d.isShowing) return@post
                    when (r) {
                        ApiTv.Fora.ATIVADO -> { d.dismiss(); pronto() }
                        ApiTv.Fora.PIN_ERRADO -> { situacao.text = a.getString(R.string.cel_fora_pin_errado); campo.setText("") }
                        ApiTv.Fora.ESPERAR -> situacao.text = a.getString(R.string.cel_fora_esperar, maxOf(espera, 1))
                        ApiTv.Fora.SEM_SESSAO -> situacao.text = a.getString(R.string.cel_ctrl_entrar)
                        ApiTv.Fora.SEM_TV -> situacao.text = a.getString(R.string.cel_ctrl_sem_tv)
                        ApiTv.Fora.RECUSOU -> situacao.text = a.getString(R.string.cel_fora_falhou)
                    }
                }
            }
        }
        campo.setOnEditorActionListener { _, _, _ -> enviar(); true }
        ativar = Visual.botao(a, a.getString(R.string.cel_fora_ativar)) { enviar() }
        val corpo = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(20))
            background = GradientDrawable().apply { cornerRadius = dp(DesignTokens.RADIUS_MEDIUM).toFloat(); setColor(Visual.FUNDO) }
            addView(Visual.texto(a, a.getString(R.string.cel_fora_titulo), 22f, negrito = true))
            addView(Visual.texto(a, a.getString(R.string.cel_fora_explica), 16f, Visual.TEXTO_2).apply { setPadding(0, dp(8), 0, dp(16)) })
            addView(campo, LinearLayout.LayoutParams(-1, -2))
            addView(situacao)
            addView(LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Visual.botao(a, a.getString(R.string.cel_fora_agora_nao), primario = false) { d.dismiss() },
                    LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
                addView(ativar, LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        }
        d.setContentView(corpo)
        d.window?.let { w ->
            w.setBackgroundDrawableResource(android.R.color.transparent)
            w.setLayout((a.resources.displayMetrics.widthPixels * 0.9).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        d.show()
        campo.requestFocus()
    }
}
