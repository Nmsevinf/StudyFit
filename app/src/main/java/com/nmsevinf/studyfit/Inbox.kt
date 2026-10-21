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

/** Aviso académico sacado de una notificación de correo o Teams. kind: 0 examen, 1 tarea/entrega, 2 aviso de clase. */
data class InboxItem(
    val app: String, val pkg: String, val from: String, val text: String, val time: Long,
    val kind: Int = 2, val due: Long = 0, val done: Boolean = false,
)

/**
 * Filtro académico de correo y Teams: de las notificaciones de Outlook, Teams y Gmail
 * guarda SOLO lo que habla de exámenes, tareas/entregas o cambios de clase
 * (remitente y asunto; nunca abre los correos). Todo se queda en el móvil.
 */
object Inbox {
    val APPS = linkedMapOf(
        "com.microsoft.office.outlook" to "Outlook",
        "com.microsoft.teams" to "Teams",
        "com.google.android.gm" to "Gmail",
        "com.samsung.android.email.provider" to "Correo",
    )
    val KINDS = listOf("Exámenes", "Tareas", "Avisos de clase")
    val ONE = listOf("Examen", "Tarea", "Aviso")
    val KIND_COLORS get() = listOf(PlannerActivity.typeColor(0), PlannerActivity.typeColor(1), C.WATER)

    private val EXAM = Regex("(?i)\\b(ex[aá]men(es)?|parcial(es)?|prueba (escrita|objetiva|de evaluaci)|test de evaluaci|recuperaci[oó]n|final de la asignatura|revisi[oó]n de (ex[aá]men|notas)|notas? (provisional(es)?|definitivas?|publicadas?)|calificaci[oó]n|exam)\\b")
    private val TASK = Regex("(?i)\\b(tarea|deberes|entrega(ble|r)?|pr[aá]ctica|boletín|ejercicios?|trabajo (final|en grupo|de la asignatura)|memoria (final|de pr[aá]cticas)|plazo|fecha l[ií]mite|cuestionario|quiz|assignment|due|poliformat|nueva actividad)\\b")
    private val CLASS = Regex("(?i)(cambio de (aula|hora|horario|fecha)|clase (cancelada|suspendida|aplazada|online|recuperada)|se cancela la clase|no habr[aá] clase|tutor[ií]a|seminario obligatorio|aula \\d|laboratorio \\w+ cambia)")
    // Ruido que nunca es una tarea aunque contenga alguna palabra
    private val NOISE = Regex("(?i)(newsletter|bolet[ií]n informativo|oferta|descuento|promoci|webinar|encuesta de satisfacci|unsubscribe|darse de baja|reaccion[oó]|ha reaccionado|le ha gustado|se ha unido)")

    /** null si el aviso no es académico. */
    fun classify(text: String): Int? {
        if (NOISE.containsMatchIn(text)) return null
        return when {
            EXAM.containsMatchIn(text) -> 0
            TASK.containsMatchIn(text) -> 1
            CLASS.containsMatchIn(text) -> 2
            else -> null
        }
    }

    private val MONTHS = listOf("enero","febrero","marzo","abril","mayo","junio","julio","agosto","septiembre","octubre","noviembre","diciembre")
    private val NUM_DATE = Regex("\\b(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{2,4}))?\\b")
    private val TXT_DATE = Regex("(?i)\\b(\\d{1,2}) de (" + MONTHS.joinToString("|") + ")")

    /** Intenta sacar la fecha (15/11, 15 de noviembre, hoy, mañana). 0 si no hay. */
    fun guessDate(text: String, now: Long = System.currentTimeMillis()): Long {
        val c = java.util.Calendar.getInstance()
        val year = c.get(java.util.Calendar.YEAR)
        fun mk(d: Int, m: Int, y: Int?): Long {
            if (d !in 1..31 || m !in 1..12) return 0
            val cal = java.util.Calendar.getInstance().apply { clear(); set(y ?: year, m - 1, d, 12, 0) }
            var t = cal.timeInMillis
            if (y == null && t < now - 30 * Store.DAY) { cal.add(java.util.Calendar.YEAR, 1); t = cal.timeInMillis }
            return Store.startOfDay(t)
        }
        TXT_DATE.find(text)?.let { return mk(it.groupValues[1].toInt(), MONTHS.indexOf(it.groupValues[2].lowercase()) + 1, null) }
        NUM_DATE.find(text)?.let { m ->
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            val t = mk(m.groupValues[1].toInt(), m.groupValues[2].toInt(), y); if (t > 0) return t
        }
        val low = text.lowercase()
        if ("pasado mañana" in low) return Store.startOfDay(now + 2 * Store.DAY)
        if ("mañana" in low) return Store.startOfDay(now + Store.DAY)
        if (Regex("\\bhoy\\b").containsMatchIn(low)) return Store.startOfDay(now)
        return 0
    }

    private const val KEY = "academic"

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
            InboxItem(it.getString("a"), it.getString("p"), it.getString("f"), it.getString("t"), it.getLong("w"),
                it.optInt("k", 2), it.optLong("d", 0), it.optBoolean("x", false))
        }
    } catch (_: Exception) { emptyList() }

    private fun save(ctx: Context, list: List<InboxItem>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("a", it.app).put("p", it.pkg).put("f", it.from).put("t", it.text).put("w", it.time)
                .put("k", it.kind).put("d", it.due).put("x", it.done))
        }
        prefs(ctx).edit().putString(KEY, a.toString()).apply()
    }

    /** Pendientes (no marcados como hechos). */
    fun open(ctx: Context): List<InboxItem> = all(ctx).filter { !it.done }

    /** Pendientes que han llegado desde la última vez que abriste el apartado. */
    fun unseen(ctx: Context): List<InboxItem> {
        val seen = prefs(ctx).getLong("seen", 0)
        return open(ctx).filter { it.time > seen }
    }

    fun markSeen(ctx: Context) { prefs(ctx).edit().putLong("seen", System.currentTimeMillis()).apply() }

    fun setDone(ctx: Context, item: InboxItem, done: Boolean) =
        save(ctx, all(ctx).map { if (it.time == item.time && it.text == item.text) it.copy(done = done) else it })

    fun remove(ctx: Context, item: InboxItem) =
        save(ctx, all(ctx).filterNot { it.time == item.time && it.text == item.text })

    @Synchronized
    fun add(ctx: Context, item: InboxItem) {
        val list = all(ctx).toMutableList()
        if (list.any { it.pkg == item.pkg && it.from == item.from && it.text == item.text }) return
        list += item
        // se guardan 30 días o 100 avisos
        val cut = System.currentTimeMillis() - 30 * Store.DAY
        save(ctx, list.filter { it.time > cut }.takeLast(100))
    }

    /** "2 exámenes, 1 tarea". */
    fun headline(items: List<InboxItem>): String = items.groupBy { it.kind }.toSortedMap().entries.joinToString(", ") { (k, l) ->
        val n = l.size
        when (k) { 0 -> if (n == 1) "1 examen" else "$n exámenes"; 1 -> if (n == 1) "1 tarea" else "$n tareas"; else -> if (n == 1) "1 aviso de clase" else "$n avisos de clase" }
    }

    /**
     * Aviso corto en los descansos: solo si han llegado exámenes/tareas nuevos.
     * Nunca muestra correos normales.
     */
    fun showDigest(act: Activity): Boolean {
        if (!Store(act).inboxOn || act.isFinishing) return false
        val items = unseen(act)
        if (items.isEmpty()) return false
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(act.dp(22), act.dp(18), act.dp(22), act.dp(4)) }
        box.text("Nuevo de clase", 20f, C.TEXT, bold = true)
        box.muted(headline(items))
        items.sortedBy { it.kind }.take(4).forEach { box.text("${ONE[it.kind]}: ${it.text}", 15f, C.TEXT, top = 4, bottom = 2).apply { maxLines = 2 } }
        val d = AlertDialog.Builder(act).setView(box)
            .setPositiveButton("Ver") { _, _ -> act.startActivity(Intent(act, InboxActivity::class.java)) }
            .setNegativeButton("Luego") { _, _ -> markSeen(act) }.create()
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
        // Solo exámenes, tareas/entregas y avisos de clase; el resto ni se guarda
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val all = "$from $text $big"
        val kind = Inbox.classify(all) ?: return
        Inbox.add(this, InboxItem(app, sbn.packageName, from.ifEmpty { app }, text.take(160), sbn.postTime, kind, Inbox.guessDate(all)))
    }
}
