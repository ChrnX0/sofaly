package br.paulo.onetv.celular

import android.app.Application

/** Só para o [ApiTv] ter um Context em qualquer ponto de entrada (tela, notificação, bloco rápido, compartilhar). */
class CelularApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiTv.iniciar(this)
    }
}
