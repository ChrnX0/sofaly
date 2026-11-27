package br.paulo.tvlauncher

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Process
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * O dono das preferências (CrossProcessPrefs.kt): roda no processo principal (sem android:process no manifesto) e é o
 * único que grava no disco. O `:player` lê e grava por aqui ([RemotePrefs]). Não exportado; mesmo assim confere o uid.
 */
class PrefsProvider : ContentProvider() {

    /** Ouvintes que avisam o `:player` quando o processo principal grava. Referência forte: o Android guarda fraca. */
    private val ouvindo = ConcurrentHashMap<String, SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun onCreate(): Boolean = true

    private fun ouvir(ctx: Context, name: String, p: SharedPreferences) {
        ouvindo.getOrPut(name) {
            SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                runCatching { ctx.contentResolver.notifyChange(CrossProcessPrefs.uri(ctx, name), null) }
            }.also { p.registerOnSharedPreferenceChangeListener(it) }
        }
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (android.os.Binder.getCallingUid() != Process.myUid()) return null
        val name = arg?.takeIf { it.isNotEmpty() && '/' !in it } ?: return null
        val p = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        return when (method) {
            CrossProcessPrefs.CALL_ALL -> {
                val xml = File(ctx.applicationInfo.dataDir, "shared_prefs/$name.xml")
                if (xml.length() > CrossProcessPrefs.MAX_BYTES) Bundle().apply { putBoolean(CrossProcessPrefs.KEY_BIG, true) }
                else { ouvir(ctx, name, p); CrossProcessPrefs.toBundle(p.all) }
            }
            CrossProcessPrefs.CALL_EDIT -> {
                ouvir(ctx, name, p)
                val ok = CrossProcessPrefs.applyEdit(p.edit(), extras ?: Bundle()).commit()
                Bundle().apply { putBoolean("ok", ok) }
            }
            else -> null
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
