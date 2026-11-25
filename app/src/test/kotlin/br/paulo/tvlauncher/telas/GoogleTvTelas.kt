package br.paulo.tvlauncher.telas

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * As telas do sistema que EXISTEM no Google TV da Sala (Chromecast, Android 14), conferidas com `pm resolve-activity`
 * em 25/11/2026. Os testes que apertam botões registram só estas: um botão que abre algo fora desta lista conta como
 * "não fez nada" até alguém conferir na TV e acrescentar aqui. android.settings.DREAM_SETTINGS NÃO existe (No activity found).
 */
object GoogleTvTelas {
    private val acoes = listOf(
        "android.settings.WIFI_SETTINGS" to "com.android.tv.settings/.connectivity.NetworkActivity",
        "com.android.systemui.TV_MEDIA_OUTPUT_DIALOG" to "com.android.systemui/.media.dialog.tv.TvMediaOutputDialogActivity",
        "android.settings.SOUND_SETTINGS" to "com.android.tv.settings/.device.displaysound.DisplaySoundActivity",
        "android.settings.SETTINGS" to "com.android.tv.settings/.MainSettings",
        "android.settings.ACCESSIBILITY_SETTINGS" to "com.android.tv.settings/.oemlink.AccessibilitySettingsActivity",
        "android.content.pm.action.REQUEST_PERMISSIONS" to "com.google.android.permissioncontroller/com.android.permissioncontroller.permission.ui.GrantPermissionsActivity",
        "android.app.action.TOGGLE_NOTIFICATION_HANDLER_PANEL" to "com.google.android.apps.tv.launcherx/.dashboard.DashboardHandler",
    )

    fun registrar() {
        val pm = shadowOf(RuntimeEnvironment.getApplication().packageManager)
        for ((acao, comp) in acoes) {
            val c = ComponentName.unflattenFromString(comp)!!
            pm.addActivityIfNotPresent(c)
            pm.addIntentFilterForActivity(c, IntentFilter(acao).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
        pm.addActivityIfNotPresent(ComponentName.unflattenFromString("com.android.tv.settings/.accessories.AddAccessoryActivity")!!)
        // Página de um app na Play (market://details?id=...): "Instalar" dos apps sugeridos.
        val loja = ComponentName.unflattenFromString("com.android.vending/com.google.android.finsky.inlinedetails.activities.tv.TvMarketDeepLinkHandlerActivity")!!
        pm.addActivityIfNotPresent(loja)
        pm.addIntentFilterForActivity(loja, IntentFilter(Intent.ACTION_VIEW).apply { addCategory(Intent.CATEGORY_DEFAULT); addDataScheme("market") })
    }
}
