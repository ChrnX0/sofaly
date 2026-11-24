package br.paulo.tvlauncher.telas

import android.view.View
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import br.paulo.tvlauncher.home.DataDuasLinhas
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/** A data do topo vira uma linha quando duas não cabem na altura, também em latim (revisor, 24/11/2026). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34], qualifiers = "pt-w960dp-h540dp-land-television-xhdpi")
class DataDuasLinhasTest {
    private fun data(): DataDuasLinhas {
        val ctx = RuntimeEnvironment.getApplication()
        return DataDuasLinhas(ctx, "d MMM").apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(ctx).apply { text = "Quinta-feira"; textSize = 24f })
            addView(TextClock(ctx).apply { textSize = 24f; format12Hour = "d 'de' MMMM"; format24Hour = "d 'de' MMMM" })
        }
    }
    private fun medir(d: DataDuasLinhas, alturaPx: Int) =
        d.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(alturaPx, View.MeasureSpec.AT_MOST))

    @Test fun latimQueNaoCabeViraUmaLinha() {
        val d = data(); medir(d, 60)
        assertEquals(View.GONE, d.getChildAt(0).visibility)
    }

    @Test fun latimQueCabeFicaEmDuas() {
        val d = data(); medir(d, 600)
        assertEquals(View.VISIBLE, d.getChildAt(0).visibility)
        assertEquals(true, d.measuredHeight > 0)
    }
}
