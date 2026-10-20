package com.nmsevinf.studyfit

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/** Un calendario del móvil (Google, Outlook/Teams, Samsung…). */
data class ExtCal(val id: Long, val name: String, val account: String, val color: Int)

/** Un evento de otro calendario o de un archivo .ics importado. */
data class ExtEvent(
    val title: String, val begin: Long, val end: Long, val allDay: Boolean,
    val color: Int, val source: String, val location: String,
) {
    /** 0 examen, 1 entrega, 3 clase, null otro (según palabras del título). */
    val kind: Int? get() = ExternalCal.kindOf(title)
}

/**
 * Une en StudyFit los calendarios que ya tienes:
 *  - Calendarios del móvil (Google/Gmail, Outlook con Teams, Samsung) mediante CalendarContract (solo lectura).
 *  - Archivos .ics importados (p. ej. el horario de la UPV).
 */
object ExternalCal {
    const val REQ_PERMISSION = 7101
    const val REQ_ICS = 7102

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun requestPermission(a: Activity) = a.requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQ_PERMISSION)

    fun calendars(ctx: Context): List<ExtCal> {
        if (!hasPermission(ctx)) return emptyList()
        val out = ArrayList<ExtCal>()
        try {
            ctx.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_COLOR),
                null, null, CalendarContract.Calendars.ACCOUNT_NAME
            )?.use { c ->
                while (c.moveToNext()) out += ExtCal(c.getLong(0), c.getString(1) ?: "Calendario", c.getString(2) ?: "", c.getInt(3))
            }
        } catch (_: Exception) { }
        return out
    }

    /** Todos los eventos externos entre [from] y [to] (ms), ya ordenados. */
    fun events(ctx: Context, from: Long, to: Long): List<ExtEvent> {
        val s = Store(ctx)
        val out = ArrayList<ExtEvent>()
        if (s.calendarsOn && hasPermission(ctx)) {
            val hidden = s.hiddenCals
            try {
                val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                    .appendPath(from.toString()).appendPath(to.toString()).build()
                ctx.contentResolver.query(uri, arrayOf(
                    CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
                    CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Instances.EVENT_LOCATION,
                ), null, null, CalendarContract.Instances.BEGIN)?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getLong(5).toString() in hidden) continue
                        val allDay = c.getInt(3) == 1
                        var b = c.getLong(1); var e = c.getLong(2)
                        if (allDay) { b = utcDayToLocal(b); e = utcDayToLocal(e) - 1 }
                        out += ExtEvent(c.getString(0) ?: "(sin título)", b, e, allDay, c.getInt(4),
                            c.getString(6) ?: "", c.getString(7) ?: "")
                    }
                }
            } catch (_: Exception) { }
        }
        out += icsEvents(s, from, to)
        return out.sortedBy { it.begin }
    }

    /** Los eventos de día completo vienen a medianoche UTC: los pasamos a medianoche local. */
    private fun utcDayToLocal(t: Long): Long {
        val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = t }
        return Calendar.getInstance().apply {
            clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    fun kindOf(title: String): Int? {
        val t = title.lowercase()
        return when {
            listOf("examen", "parcial", "final ", "prueba", "test ", "recuperación", "recuperacion").any { it in "$t " } -> 0
            listOf("entrega", "entregable", "deadline", "límite", "limite", "práctica", "practica", "tarea").any { it in t } -> 1
            listOf("clase", "teoría", "teoria", "laboratorio", "lab ", "seminario", "tutoría", "tutoria").any { it in "$t " } -> 3
            else -> null
        }
    }

    // ---------------- Archivos .ics ----------------

    fun pickIcs(a: Activity) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/calendar", "text/x-vcalendar", "application/ics", "application/octet-stream", "text/plain"))
        }
        try { a.startActivityForResult(i, REQ_ICS) } catch (_: Exception) { }
    }

    /** Lee un .ics y guarda sus eventos. Devuelve cuántos eventos se han importado (o -1 si falla). */
    fun importIcs(ctx: Context, uri: Uri): Int {
        return try {
            val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return -1
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')?.removeSuffix(".ics") ?: "Calendario"
            val evs = parseIcs(text)
            if (evs.length() == 0) return 0
            val s = Store(ctx)
            val all = JSONArray(s.p.getString("icsSources", "[]"))
            val src = JSONObject().apply { put("id", System.nanoTime()); put("name", name); put("events", evs) }
            all.put(src)
            s.p.edit().putString("icsSources", all.toString()).apply()
            evs.length()
        } catch (_: Exception) { -1 }
    }

    data class IcsSource(val id: Long, val name: String, val count: Int)

    fun icsSources(ctx: Context): List<IcsSource> {
        val a = JSONArray(Store(ctx).p.getString("icsSources", "[]"))
        return (0 until a.length()).map { a.getJSONObject(it) }.map { IcsSource(it.getLong("id"), it.getString("name"), it.getJSONArray("events").length()) }
    }

    fun removeIcs(ctx: Context, id: Long) {
        val s = Store(ctx)
        val a = JSONArray(s.p.getString("icsSources", "[]")); val b = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getLong("id") != id) b.put(a.getJSONObject(i))
        s.p.edit().putString("icsSources", b.toString()).apply()
    }

    private val ICS_COLOR = Color.parseColor("#7E6A9E")

    /** Expande los eventos .ics (incluidas repeticiones semanales/diarias) dentro del rango. */
    private fun icsEvents(s: Store, from: Long, to: Long): List<ExtEvent> {
        val out = ArrayList<ExtEvent>()
        val a = try { JSONArray(s.p.getString("icsSources", "[]")) } catch (_: Exception) { return out }
        for (i in 0 until a.length()) {
            val src = a.getJSONObject(i); val name = src.getString("name"); val evs = src.getJSONArray("events")
            for (j in 0 until evs.length()) {
                val e = evs.getJSONObject(j)
                val b0 = e.getLong("b"); val dur = e.getLong("e") - b0
                val freq = e.optString("freq"); val interval = e.optInt("int", 1).coerceAtLeast(1)
                val until = e.optLong("until", Long.MAX_VALUE); val count = e.optInt("count", Int.MAX_VALUE)
                val byday = e.optInt("byday", 0)
                val ex = e.optJSONArray("ex")?.let { x -> (0 until x.length()).map { x.getLong(it) }.toSet() } ?: emptySet()
                fun add(b: Long) {
                    if (b + dur >= from && b <= to && b !in ex)
                        out += ExtEvent(e.getString("t"), b, b + dur, e.optBoolean("ad"), ICS_COLOR, name, e.optString("loc"))
                }
                if (freq.isEmpty()) { add(b0); continue }
                val c = Calendar.getInstance().apply { timeInMillis = b0 }
                var n = 0; var guard = 0
                while (c.timeInMillis <= minOf(to, until) && n < count && guard < 5000) {
                    guard++
                    if (freq == "WEEKLY" && byday != 0) {
                        // recorre la semana de c, días marcados en byday (bit0 = lunes)
                        val week = c.clone() as Calendar
                        week.add(Calendar.DAY_OF_MONTH, -((week.get(Calendar.DAY_OF_WEEK) + 5) % 7))
                        for (d in 0 until 7) {
                            val dc = week.clone() as Calendar; dc.add(Calendar.DAY_OF_MONTH, d)
                            if (byday and (1 shl d) != 0 && dc.timeInMillis >= b0 && dc.timeInMillis <= until && n < count) { add(dc.timeInMillis); n++ }
                        }
                        c.add(Calendar.WEEK_OF_YEAR, interval)
                    } else {
                        add(c.timeInMillis); n++
                        when (freq) {
                            "DAILY" -> c.add(Calendar.DAY_OF_MONTH, interval)
                            "WEEKLY" -> c.add(Calendar.WEEK_OF_YEAR, interval)
                            "MONTHLY" -> c.add(Calendar.MONTH, interval)
                            else -> break
                        }
                    }
                }
            }
        }
        return out
    }

    /** Parser .ics sencillo: SUMMARY, DTSTART/DTEND (con TZID, Z o fecha), LOCATION, RRULE y EXDATE. */
    fun parseIcs(raw: String): JSONArray {
        val lines = raw.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split("\n")
        val out = JSONArray()
        var cur: JSONObject? = null
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> cur = JSONObject()
                line == "END:VEVENT" -> { cur?.let { if (it.has("b") && it.has("t")) { if (!it.has("e")) it.put("e", it.getLong("b") + if (it.optBoolean("ad")) Store.DAY - 1 else 3600_000L); out.put(it) } }; cur = null }
                cur != null -> {
                    val k = line.substringBefore(':'); val v = line.substringAfter(':', "")
                    val key = k.substringBefore(';')
                    when (key) {
                        "SUMMARY" -> cur.put("t", unescape(v))
                        "LOCATION" -> cur.put("loc", unescape(v))
                        "DTSTART" -> { val (t, ad) = icsTime(k, v); cur.put("b", t); if (ad) cur.put("ad", true) }
                        "DTEND" -> { val (t, ad) = icsTime(k, v); cur.put("e", if (ad) t - 1 else t) }
                        "EXDATE" -> {
                            val arr = cur.optJSONArray("ex") ?: JSONArray().also { cur.put("ex", it) }
                            v.split(",").forEach { arr.put(icsTime(k, it).first) }
                        }
                        "RRULE" -> v.split(";").forEach { part ->
                            val pk = part.substringBefore('='); val pv = part.substringAfter('=')
                            when (pk) {
                                "FREQ" -> cur.put("freq", pv)
                                "INTERVAL" -> cur.put("int", pv.toIntOrNull() ?: 1)
                                "COUNT" -> cur.put("count", pv.toIntOrNull() ?: 0)
                                "UNTIL" -> cur.put("until", icsTime("UNTIL", pv).first + Store.DAY)
                                "BYDAY" -> cur.put("byday", pv.split(",").sumOf { d ->
                                    listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU").indexOf(d.takeLast(2)).let { if (it >= 0) 1 shl it else 0 }
                                })
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    private fun unescape(v: String) = v.replace("\\n", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()

    /** Devuelve (instante en ms, es día completo). */
    private fun icsTime(key: String, v: String): Pair<Long, Boolean> {
        val d = v.trim()
        val tzid = Regex("TZID=([^;:]+)").find(key)?.groupValues?.get(1)
        val y = d.substring(0, 4).toInt(); val mo = d.substring(4, 6).toInt() - 1; val da = d.substring(6, 8).toInt()
        if (d.length == 8 || key.contains("VALUE=DATE") && !d.contains("T")) {
            return Calendar.getInstance().apply { clear(); set(y, mo, da) }.timeInMillis to true
        }
        val h = d.substring(9, 11).toInt(); val mi = d.substring(11, 13).toInt(); val se = d.substring(13, 15).toIntOrNull() ?: 0
        val tz = when { d.endsWith("Z") -> TimeZone.getTimeZone("UTC"); tzid != null -> TimeZone.getTimeZone(tzid); else -> TimeZone.getDefault() }
        return Calendar.getInstance(tz).apply { clear(); set(y, mo, da, h, mi, se) }.timeInMillis to false
    }
}
