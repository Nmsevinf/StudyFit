package com.nmsevinf.studyfit

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class Phase { IDLE, STUDY, BREAK, DONE }
enum class BreakKind { EXERCISE, SNACK, LONG }

data class SessionLog(
    val start: Long, val end: Long, val minutes: Int, val tech: String, val subject: String,
    val place: String, val completed: Boolean, val focus: Int, val stress: Int,
)

data class Exam(val id: Long, val subject: String, val date: Long)

/** Tema para repetición espaciada. stage 0..4 → repasos a 1, 3, 7, 14, 30 días. */
data class Topic(val id: Long, val name: String, val subject: String, val stage: Int, val nextDue: Long)

data class StudySchedule(val id: Long, val days: Int /* bit0 = lunes */, val hour: Int, val minute: Int)

/** Todo se guarda en el móvil (SharedPreferences): funciona sin internet. */
class Store(ctx: Context) {
    val p: SharedPreferences = ctx.applicationContext.getSharedPreferences("studyfit", Context.MODE_PRIVATE)

    // ---------- Sesión actual ----------
    var phase: Phase
        get() = Phase.valueOf(p.getString("phase", Phase.IDLE.name)!!)
        set(v) = p.edit().putString("phase", v.name).apply()
    var breakKind: BreakKind
        get() = BreakKind.valueOf(p.getString("breakKind", BreakKind.EXERCISE.name)!!)
        set(v) = p.edit().putString("breakKind", v.name).apply()
    var techId: String
        get() = p.getString("techId", "pomodoro")!!
        set(v) = p.edit().putString("techId", v).apply()
    var place: Place
        get() = runCatching { Place.valueOf(p.getString("place", Place.CASA.name)!!) }.getOrDefault(Place.CASA)
        set(v) = p.edit().putString("place", v.name).apply()
    var subject: String
        get() = p.getString("subject", "")!!
        set(v) = p.edit().putString("subject", v).apply()
    var cycles: Int
        get() = p.getInt("cycles", 4)
        set(v) = p.edit().putInt("cycles", v).apply()
    var cycleIndex: Int
        get() = p.getInt("cycleIndex", 1)
        set(v) = p.edit().putInt("cycleIndex", v).apply()
    var phaseStart: Long
        get() = p.getLong("phaseStart", 0)
        set(v) = p.edit().putLong("phaseStart", v).apply()
    var phaseEnd: Long
        get() = p.getLong("phaseEnd", 0)
        set(v) = p.edit().putLong("phaseEnd", v).apply()
    var sessionStart: Long
        get() = p.getLong("sessionStart", 0)
        set(v) = p.edit().putLong("sessionStart", v).apply()
    var studiedMs: Long
        get() = p.getLong("studiedMs", 0)
        set(v) = p.edit().putLong("studiedMs", v).apply()
    var strict: Boolean
        get() = p.getBoolean("strict", false)
        set(v) = p.edit().putBoolean("strict", v).apply()
    var pendingMood: Boolean
        get() = p.getBoolean("pendingMood", false)
        set(v) = p.edit().putBoolean("pendingMood", v).apply()
    var pendingCompleted: Boolean
        get() = p.getBoolean("pendingCompleted", true)
        set(v) = p.edit().putBoolean("pendingCompleted", v).apply()

    val inSession get() = phase == Phase.STUDY || phase == Phase.BREAK
    val technique get() = Techniques.byId(techId)

    // ---------- Ajustes ----------
    var bedHour: Int
        get() = p.getInt("bedHour", 23)
        set(v) = p.edit().putInt("bedHour", v).apply()
    var bedMinute: Int
        get() = p.getInt("bedMinute", 30)
        set(v) = p.edit().putInt("bedMinute", v).apply()
    var sleepOn: Boolean
        get() = p.getBoolean("sleepOn", true)
        set(v) = p.edit().putBoolean("sleepOn", v).apply()
    var dailyOn: Boolean
        get() = p.getBoolean("dailyOn", true)
        set(v) = p.edit().putBoolean("dailyOn", v).apply()
    var dayStart: Int
        get() = p.getInt("dayStart", 9)
        set(v) = p.edit().putInt("dayStart", v).apply()
    var dayEnd: Int
        get() = p.getInt("dayEnd", 21)
        set(v) = p.edit().putInt("dayEnd", v).apply()
    var eyesOn: Boolean
        get() = p.getBoolean("eyesOn", true)
        set(v) = p.edit().putBoolean("eyesOn", v).apply()
    var postureOn: Boolean
        get() = p.getBoolean("postureOn", true)
        set(v) = p.edit().putBoolean("postureOn", v).apply()
    var waterGoal: Int
        get() = p.getInt("waterGoal", 8)
        set(v) = p.edit().putInt("waterGoal", v).apply()
    var ambient: Int // 0 apagado, 1 ruido blanco, 2 ruido marrón, 3 lluvia
        get() = p.getInt("ambient", 0)
        set(v) = p.edit().putInt("ambient", v).apply()
    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    var allowedApps: Set<String>
        get() = p.getStringSet("allowedApps", emptySet())!!.toSet()
        set(v) = p.edit().putStringSet("allowedApps", v).apply()

    // ---------- Agua ----------
    fun waterToday(): Int = p.getInt("water_" + dayKey(), 0)
    fun addWater(n: Int = 1) {
        val k = "water_" + dayKey()
        p.edit().putInt(k, (p.getInt(k, 0) + n).coerceAtLeast(0)).apply()
    }

    // ---------- Listas JSON ----------
    private fun arr(key: String) = JSONArray(p.getString(key, "[]"))
    private fun save(key: String, a: JSONArray) = p.edit().putString(key, a.toString()).apply()

    fun sessions(): List<SessionLog> {
        val a = arr("sessions")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            SessionLog(
                o.getLong("start"), o.getLong("end"), o.getInt("minutes"), o.getString("tech"),
                o.optString("subject"), o.optString("place"), o.getBoolean("completed"),
                o.optInt("focus", 0), o.optInt("stress", 0)
            )
        }
    }

    fun addSession(s: SessionLog) {
        val a = arr("sessions")
        a.put(JSONObject().apply {
            put("start", s.start); put("end", s.end); put("minutes", s.minutes); put("tech", s.tech)
            put("subject", s.subject); put("place", s.place); put("completed", s.completed)
            put("focus", s.focus); put("stress", s.stress)
        })
        // guarda como máximo 1000 sesiones
        while (a.length() > 1000) a.remove(0)
        save("sessions", a)
    }

    fun exams(): List<Exam> {
        val a = arr("exams")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i); Exam(o.getLong("id"), o.getString("subject"), o.getLong("date"))
        }.sortedBy { it.date }
    }

    fun addExam(subject: String, date: Long) {
        val a = arr("exams")
        a.put(JSONObject().apply { put("id", System.nanoTime()); put("subject", subject); put("date", date) })
        save("exams", a)
    }

    fun removeExam(id: Long) = removeById("exams", id)

    fun topics(): List<Topic> {
        val a = arr("topics")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Topic(o.getLong("id"), o.getString("name"), o.optString("subject"), o.getInt("stage"), o.getLong("nextDue"))
        }.sortedBy { it.nextDue }
    }

    fun addTopic(name: String, subject: String) {
        val a = arr("topics")
        a.put(JSONObject().apply {
            put("id", System.nanoTime()); put("name", name); put("subject", subject)
            put("stage", 0); put("nextDue", startOfDay(System.currentTimeMillis()) + INTERVALS[0] * DAY)
        })
        save("topics", a)
    }

    /** Marca un tema como repasado: avanza al siguiente intervalo (1, 3, 7, 14, 30 días). */
    fun reviewTopic(id: Long, remembered: Boolean) {
        val a = arr("topics")
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.getLong("id") == id) {
                val st = if (remembered) (o.getInt("stage") + 1).coerceAtMost(INTERVALS.size - 1) else 0
                o.put("stage", st)
                o.put("nextDue", startOfDay(System.currentTimeMillis()) + INTERVALS[st] * DAY)
            }
        }
        save("topics", a)
    }

    fun removeTopic(id: Long) = removeById("topics", id)

    fun schedules(): List<StudySchedule> {
        val a = arr("schedules")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            StudySchedule(o.getLong("id"), o.getInt("days"), o.getInt("hour"), o.getInt("minute"))
        }
    }

    fun addSchedule(days: Int, hour: Int, minute: Int) {
        val a = arr("schedules")
        a.put(JSONObject().apply { put("id", System.nanoTime() % 1_000_000); put("days", days); put("hour", hour); put("minute", minute) })
        save("schedules", a)
    }

    fun removeSchedule(id: Long) = removeById("schedules", id)

    private fun removeById(key: String, id: Long) {
        val a = arr(key); val b = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getLong("id") != id) b.put(a.getJSONObject(i))
        save(key, b)
    }

    companion object {
        const val DAY = 24L * 60 * 60 * 1000
        val INTERVALS = longArrayOf(1, 3, 7, 14, 30)

        fun dayKey(t: Long = System.currentTimeMillis()): String =
            SimpleDateFormat("yyyyMMdd", Locale.US).format(t)

        fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
