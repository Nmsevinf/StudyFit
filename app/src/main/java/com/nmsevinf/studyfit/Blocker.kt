package com.nmsevinf.studyfit

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

/**
 * Bloqueo de distracciones: mientras estás en un bloque de ESTUDIO, si se abre una app
 * que no está permitida, te devuelve a la pantalla de estudio.
 * No lee el contenido de las apps: solo mira qué app está en primer plano.
 */
class BlockerService : AccessibilityService() {

    private var lastBounce = 0L

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        val s = Store(this)
        if (s.phase != Phase.STUDY) return
        if (isAllowed(pkg, s)) return
        val now = System.currentTimeMillis()
        if (now - lastBounce < 700) return
        lastBounce = now
        startActivity(Intent(this, SessionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        Toast.makeText(this, "📚 Ahora toca estudiar. Descanso en ${fmtClock(s.phaseEnd - now)}", Toast.LENGTH_SHORT).show()
    }

    private fun isAllowed(pkg: String, s: Store): Boolean {
        if (pkg == packageName) return true
        if (pkg in ALWAYS) return true
        if (pkg in s.allowedApps) return true
        // Teclados (para poder escribir en las apps permitidas)
        val imm = getSystemService(InputMethodManager::class.java)
        if (imm.enabledInputMethodList.any { it.packageName == pkg }) return true
        // Marcador de teléfono por defecto (llamadas y emergencias)
        val dialer = getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage
        if (pkg == dialer) return true
        return false
    }

    override fun onInterrupt() {}

    companion object {
        /** Siempre permitidas: sistema, teléfono y emergencias. */
        val ALWAYS = setOf(
            "android", "com.android.systemui",
            "com.android.phone", "com.android.server.telecom", "com.android.dialer",
            "com.google.android.dialer", "com.samsung.android.dialer", "com.samsung.android.incallui",
            "com.android.incallui", "com.android.emergency", "com.google.android.apps.safetyhub",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
        )

        fun isEnabled(ctx: Context): Boolean {
            val target = ComponentName(ctx, BlockerService::class.java).flattenToString()
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { it.equals(target, ignoreCase = true) }
        }
    }
}
