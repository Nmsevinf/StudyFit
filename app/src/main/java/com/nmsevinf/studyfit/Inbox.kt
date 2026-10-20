package com.nmsevinf.studyfit

import android.app.AlertDialog
import android.app.Activity
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.widget.LinearLayout
import org.json.JSONArray
import org.json.JSONObject

/** Un aviso de correo o Teams guardado mientras estudias. */
data class InboxItem(val app: String, val pkg: String, val from: String, val text: String, val time: Long) {
    val important: Boolean get() = Inbox.IMPORTANT.containsMatchIn("$from $text")
}

/**
 * Resumen de correos y mensajes: lee las notificaciones de Gmail, Outlook y Teams
 * (solo remitente y asunto/primer texto, sin abrir los correos) y las agrupa.
 * Todo se queda en el móvil.
 */
object Inbox {
    val APPS = linkedMapOf(
        "com.microsoft.office.outlook" to "Outlook",
        "com.microsoft.teams" to "Teams",
        "com.google.android.gm" to "Gmail",
        "com.samsung.android.email.provider" to "Correo",
    )
    val IMPORTANT = Regex("(?i)examen|entrega|plazo|urgente|cambio de aula|aula|cancela|aplaza|nota|calificaci|recuperaci|tutor[ií]a|hoy|mañana")
    private const val KEY = "inbox"

    fun hasAccess(ctx: Context): Boolean =
        (Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "")
            .contains(ComponentName(ctx, InboxListener::class.java).flattenToString())

    fun openAccessSettings(ctx: Context) {
        ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("studyfit_inbox", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<InboxItem> = try {
        val a = JSONArray(prefs(ctx).getString(KEY, "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            InboxItem(it.getString("a"), it.getString("p"), it.getString("f"), it.getString("t"), it.getLong("w"))
        }
    } catch (_: Exception) { emptyList() }

    /** Avisos aún no vistos en el resumen. */
    fun unseen(ctx: Context): List<InboxItem> {
        val seen = prefs(ctx).getLong("seen", 0)
        return all(ctx).filter { it.time > seen }
    }

    fun markSeen(ctx: Context) { prefs(ctx).edit().putLong("seen", System.currentTimeMillis()).apply() }

    @Synchronized
    fun add(ctx: Context, item: InboxItem) {
        val list = all(ctx).toMutableList()
        // misma notificación actualizada → no duplicar
        if (list.any { it.pkg == item.pkg && it.from == item.from && it.text == item.text }) return
        list += item
        val cut = System.currentTimeMillis() - 3 * Store.DAY
        val keep = list.filter { it.time > cut }.takeLast(80)
        val a = JSONArray()
        keep.forEach { a.put(JSONObject().put("a", it.app).put("p", it.pkg).put("f", it.from).put("t", it.text).put("w", it.time)) }
        prefs(ctx).edit().putString(KEY, a.toString()).apply()
    }

    /** Texto corto tipo "3 de Outlook, 2 de Teams". */
    fun headline(items: List<InboxItem>): String =
        items.groupBy { it.app }.entries.joinToString(", ") { "${it.value.size} de ${it.key}" }

    /**
     * Ventana "Mientras estudiabas": primero lo que parece importante (exámenes, entregas,
     * cambios de aula…), luego por app y remitente. Devuelve false si no hay nada nuevo.
     */
    fun showDigest(act: Activity, title: String = "Mientras estudiabas"): Boolean {
        if (!Store(act).inboxOn) return false
        val items = unseen(act)
        if (items.isEmpty() || act.isFinishing) return false
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(act.dp(22), act.dp(18), act.dp(22), act.dp(4)) }
        box.text(title, 20f, C.TEXT, bold = true)
        box.muted(headline(items))
        val imp = items.filter { it.important }.sortedByDescending { it.time }
        if (imp.isNotEmpty()) {
            box.text("Puede ser importante", 14f, C.DANGER, bold = true, top = 12, bottom = 2)
            imp.take(4).forEach { box.text("${it.from}: ${it.text}", 15f, C.TEXT, top = 2, bottom = 2) }
        }
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        items.filter { !it.important }.groupBy { it.app }.forEach { (app, list) ->
            box.text(app, 14f, C.MUTED, bold = true, top = 12, bottom = 2)
            list.groupBy { it.from }.entries.sortedByDescending { e -> e.value.maxOf { it.time } }.take(4).forEach { (from, l) ->
                val last = l.maxByOrNull { it.time }!!
                box.text(if (l.size > 1) "$from (${l.size}): ${last.text}" else "$from: ${last.text}", 15f, C.TEXT, top = 2, bottom = 2).apply { maxLines = 2 }
            }
        }
        // Botones para abrir cada app
        items.map { it.pkg }.distinct().forEach { pkg ->
            box.textButton("Abrir ${APPS[pkg] ?: "app"}", C.PRIMARY) {
                act.packageManager.getLaunchIntentForPackage(pkg)?.let { act.startActivity(it) }
                markSeen(act); dialogRef[0]?.dismiss()
            }.leftAligned()
        }
        val sv = android.widget.ScrollView(act).apply { addView(box) }
        val d = AlertDialog.Builder(act).setView(sv)
            .setPositiveButton("Visto") { _, _ -> markSeen(act) }
            .setNegativeButton("Luego", null).create()
        dialogRef[0] = d
        d.window?.setBackgroundDrawable(rounded(C.BG, act.dpf(24f)))
        d.show()
        return true
    }
}

/** Escucha las notificaciones de las apps de correo y Teams. No las borra ni las contesta. */
class InboxListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = Inbox.APPS[sbn.packageName] ?: return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val ex = n.extras ?: return
        val from = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT) ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))
            ?.toString()?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        if (from.isEmpty() && text.isEmpty()) return
        Inbox.add(this, InboxItem(app, sbn.packageName, from.ifEmpty { app }, text.take(140), sbn.postTime))
    }
}
