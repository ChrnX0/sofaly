package tv.sofaly.exemplo.contagem

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import java.time.LocalDate

/**
 * O mesmo número como WIDGET (opcional). A Sofaly mostra uma foto do widget como cartão em Apps; outros launchers
 * mostram o widget em si. Widget é um AppWidgetProvider comum do Android: nada específico da Sofaly aqui.
 */
class ContagemWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, awm: AppWidgetManager, ids: IntArray) {
        val alvo = Preferencias.data(ctx)
        val dias = alvo?.let { Contagem.diasAte(LocalDate.now(), it) }
        val v = RemoteViews(ctx.packageName, R.layout.widget).apply {
            setTextViewText(R.id.valor, dias?.let { Contagem.valor(it) } ?: "?")
            setTextViewText(R.id.titulo, if (alvo == null) ctx.getString(R.string.escolha) else Preferencias.titulo(ctx))
        }
        for (id in ids) awm.updateAppWidget(id, v)
    }
}
