package br.paulo.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import br.paulo.tvlauncher.alarm.NaoPerturbe
import br.paulo.tvlauncher.weather.AvisoRegras

/**
 * O aviso na tela da TV, visto de fora do app (refatoração G, 28/11/2026). Quem mostra de verdade é o AvisoTv, que mora
 * no :app (usa o serviço de acessibilidade, o tema e a Sofy); os módulos de área não enxergam o :app, então pedem por
 * aqui. O LauncherApp liga o [display] na partida do processo. Sem ele (testes JVM, processo sem o app): nada aparece
 * e o resultado é [Result.NO_ACCESSIBILITY], o mesmo de uma TV sem o serviço ligado.
 */
object TvNotice {
    /** O que aconteceu com o aviso (espelha AvisoTv.Resultado). */
    enum class Result { SHOWN, KEPT, DROPPED, NO_ACCESSIBILITY }

    /** Quem desenha o aviso (o AvisoTv). */
    interface Display {
        fun show(
            ctx: Context, title: String, text: String, seconds: Int, sound: Boolean, keepIfScreenOff: Boolean,
            image: Bitmap?, icon: Drawable?, stay: AvisoRegras.Fica, history: Boolean, priority: NaoPerturbe.Tipo,
            seenId: String?,
        ): Result

        fun quick(ctx: Context, title: String, text: String, seconds: Int)
    }

    @Volatile var display: Display? = null

    /** Mesmos parâmetros e padrões de AvisoTv.mostrar. */
    fun show(
        ctx: Context, title: String, text: String, seconds: Int, sound: Boolean, keepIfScreenOff: Boolean,
        image: Bitmap? = null, icon: Drawable? = null, stay: AvisoRegras.Fica = AvisoRegras.Fica.PASSAGEIRO,
        history: Boolean = true, priority: NaoPerturbe.Tipo = NaoPerturbe.Tipo.COMUM, seenId: String? = null,
    ): Result = display?.show(ctx, title, text, seconds, sound, keepIfScreenOff, image, icon, stay, history, priority, seenId)
        ?: Result.NO_ACCESSIBILITY

    /** Confirmação curta (AvisoTv.rapido). */
    fun quick(ctx: Context, title: String, text: String, seconds: Int = 5) { display?.quick(ctx, title, text, seconds) }
}
