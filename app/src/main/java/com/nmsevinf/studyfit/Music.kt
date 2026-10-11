package com.nmsevinf.studyfit

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Elegir canciones del móvil para "Tu música". */
object Music {
    const val REQUEST = 7001

    fun pick(a: Activity) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try { a.startActivityForResult(i, REQUEST) }
        catch (_: Exception) { Toast.makeText(a, "No se ha podido abrir el selector de archivos", Toast.LENGTH_LONG).show() }
    }

    /** Guarda las canciones elegidas. Devuelve cuántas hay. */
    fun onResult(a: Activity, requestCode: Int, resultCode: Int, data: Intent?): Int? {
        if (requestCode != REQUEST) return null
        if (resultCode != Activity.RESULT_OK || data == null) return Store(a).ownMusic.size
        val uris = mutableListOf<Uri>()
        data.clipData?.let { c -> for (k in 0 until c.itemCount) uris += c.getItemAt(k).uri }
        data.data?.let { if (it !in uris) uris += it }
        uris.forEach {
            try { a.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        }
        if (uris.isNotEmpty()) Store(a).ownMusic = uris.map { it.toString() }
        return Store(a).ownMusic.size
    }

    fun summary(a: Activity): String {
        val n = Store(a).ownMusic.size
        return when (n) { 0 -> "Aún no has elegido canciones"; 1 -> "1 canción elegida"; else -> "$n canciones elegidas" }
    }
}
