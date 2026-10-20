package com.nmsevinf.studyfit

import android.os.Handler
import android.os.Looper

/**
 * Si la pantalla de estudio está abierta, los recordatorios de salud salen como
 * ventana dentro de la app (con cuenta atrás de 20 s para la vista).
 * Si no, se usan las notificaciones emergentes.
 */
object HealthBus {
    const val EYES = 1
    const val POSTURE = 2
    @Volatile var listener: ((Int, String, String) -> Unit)? = null

    /** Devuelve true si lo ha mostrado dentro de la app. */
    fun show(kind: Int, title: String, text: String): Boolean {
        val l = listener ?: return false
        Handler(Looper.getMainLooper()).post { l(kind, title, text) }
        return true
    }
}
