package com.nmsevinf.studyfit

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import java.util.Calendar

/** Tipos de "llamada" a pantalla completa. */
object CallType {
    const val EXERCISE = "exercise"
    const val SNACK = "snack"
    const val LONG = "long"
    const val BACK = "back"
    const val END = "end"
    const val START = "start"
    const val SLEEP = "sleep"
}

object Notifs {
    const val CH_CALL = "calls_v2"
    const val CH_REMIND = "reminders_v1"
    const val CH_SESSION = "session_v1"
    const val ID_SESSION = 1
    const val ID_CALL = 2
    const val ID_REMIND = 3

    fun ensure(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_CALL) != null) return
        nm.deleteNotificationChannel("calls_v1")
        nm.createNotificationChannel(NotificationChannel(CH_CALL, "Llamadas de StudyFit", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Ejercicio, volver a estudiar, comer algo y fin de sesión"
            setSound(null, null)          // el sonido lo controla la app (canal de alarma)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Recordatorios de salud", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Agua, vista 20-20-20, postura, sueño"
        })
        nm.createNotificationChannel(NotificationChannel(CH_SESSION, "Sesión en curso", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        })
    }

    private fun pi(ctx: Context, cls: Class<*>, rc: Int, extra: String? = null): PendingIntent {
        val i = Intent(ctx, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (extra != null) i.putExtra("type", extra)
        return PendingIntent.getActivity(ctx, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Llamada: suena (servicio de alarma), notifica y abre la pantalla si Android lo permite. */
    fun call(ctx: Context, type: String) {
        ensure(ctx)
        RingService.start(ctx, type)
    }

    fun callNotification(ctx: Context, type: String): Notification {
        ensure(ctx)
        val (title, text) = when (type) {
            CallType.EXERCISE -> "Hora de moverte" to "Toca para ver tu rutina de ejercicio"
            CallType.LONG -> "Descanso largo" to "Ejercicio, agua y algo de comer"
            CallType.SNACK -> "Pausa para comer algo" to "Toca para ver qué comer"
            CallType.BACK -> "Vuelve a estudiar" to "Tu siguiente bloque ya ha empezado"
            CallType.END -> "Sesión terminada" to "Cuéntame qué tal ha ido"
            CallType.START -> "Es tu hora de estudiar" to "Toca para empezar"
            CallType.SLEEP -> "Hora de dormir" to "Dormir bien es parte de estudiar bien"
            else -> "StudyFit" to ""
        }
        val full = pi(ctx, CallActivity::class.java, 50, type)
        return Notification.Builder(ctx, CH_CALL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title).setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(Notification.Action.Builder(null, "Silenciar", RingService.stopPendingIntent(ctx)).build())
            .setAutoCancel(true)
            .build()
    }

    fun cancelCall(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(ID_CALL)

    fun remind(ctx: Context, title: String, text: String) {
        ensure(ctx)
        val n = Notification.Builder(ctx, CH_REMIND)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title).setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi(ctx, MainActivity::class.java, 51))
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID_REMIND, n)
    }

    fun session(ctx: Context) {
        ensure(ctx)
        val s = Store(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!s.inSession) { nm.cancel(ID_SESSION); return }
        val t = s.technique
        val title = when {
            s.phase == Phase.STUDY -> "Estudiando: ${t.name} (${s.cycleIndex}/${s.cycles})"
            s.breakWaiting -> "Descanso listo: pulsa para empezar"
            else -> "Descanso: ${t.name}"
        }
        val n = Notification.Builder(ctx, CH_SESSION)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(if (s.subject.isNotBlank()) s.subject else "Sesión de estudio")
            .setOngoing(true)
            .setUsesChronometer(!s.breakWaiting)
            .setChronometerCountDown(true)
            .setWhen(if (s.breakWaiting) System.currentTimeMillis() else s.phaseEnd)
            .setShowWhen(!s.breakWaiting)
            .setContentIntent(pi(ctx, SessionActivity::class.java, 52))
            .build()
        nm.notify(ID_SESSION, n)
    }
}

/** Motor de la sesión: técnica → bloques de estudio y descansos programados con alarmas exactas. */
object Engine {
    private const val RC_PHASE = 100
    private const val RC_EYES = 101
    private const val RC_POSTURE = 102
    private const val RC_DAILY = 300
    private const val RC_SLEEP_WARN = 301
    private const val RC_SLEEP = 302
    private const val RC_SCHED_BASE = 400
    const val MIN = 60_000L

    /** Factor de tiempo: 1 = real. (Se deja aquí por si quieres un "modo demo" acelerado.) */
    var speed = 1L

    fun start(ctx: Context, techId: String, place: Place, subject: String, cycles: Int, strict: Boolean) {
        val s = Store(ctx)
        if (s.pendingMood) saveSession(ctx, 0, 0)
        val now = System.currentTimeMillis()
        val t = Techniques.byId(techId)
        s.techId = techId; s.place = place; s.subject = subject; s.cycles = cycles; s.strict = strict
        s.cycleIndex = 1; s.studiedMs = 0; s.sessionStart = now
        s.phase = Phase.STUDY; s.phaseStart = now; s.phaseEnd = now + t.studyMin * MIN / speed
        scheduleAll(ctx)
        Notifs.session(ctx)
    }

    /** Llamado por la alarma al acabar cada fase. */
    fun onPhaseEnd(ctx: Context) {
        val s = Store(ctx)
        val t = s.technique
        val now = System.currentTimeMillis()
        when (s.phase) {
            Phase.STUDY -> {
                s.studiedMs = s.studiedMs + (now.coerceAtMost(s.phaseEnd) - s.phaseStart).coerceAtLeast(0)
                if (s.cycleIndex >= s.cycles) {
                    finish(ctx, completed = true)
                    Notifs.call(ctx, CallType.END)
                    return
                }
                val i = s.cycleIndex
                val kind = when {
                    t.longEvery in 1..s.cycles && i % t.longEvery == 0 -> BreakKind.LONG
                    i == 2 || (i > 2 && (i - 2) % 3 == 0) -> BreakKind.SNACK
                    else -> BreakKind.EXERCISE
                }
                val len = if (kind == BreakKind.LONG) t.longBreakMin else t.breakMin
                s.breakKind = kind
                s.phase = Phase.BREAK; s.breakLenMs = len * MIN / speed
                if (s.manualBreak) {
                    // El descanso espera a que pulses "Empezar descanso"
                    s.breakWaiting = true; s.phaseStart = now; s.phaseEnd = Long.MAX_VALUE
                } else {
                    s.breakWaiting = false; s.phaseStart = now; s.phaseEnd = now + s.breakLenMs
                }
                scheduleAll(ctx); Notifs.session(ctx)
                Notifs.call(ctx, when (kind) {
                    BreakKind.EXERCISE -> CallType.EXERCISE
                    BreakKind.SNACK -> CallType.SNACK
                    BreakKind.LONG -> CallType.LONG
                })
            }
            Phase.BREAK -> {
                s.cycleIndex = s.cycleIndex + 1
                s.phase = Phase.STUDY; s.phaseStart = now; s.phaseEnd = now + t.studyMin * MIN / speed
                scheduleAll(ctx); Notifs.session(ctx)
                Notifs.call(ctx, CallType.BACK)
            }
            else -> {}
        }
    }

    /** Empieza la cuenta atrás del descanso (modo "empezar cuando yo diga"). */
    fun startBreak(ctx: Context) {
        val s = Store(ctx)
        if (s.phase != Phase.BREAK || !s.breakWaiting) return
        val now = System.currentTimeMillis()
        s.breakWaiting = false; s.phaseStart = now; s.phaseEnd = now + s.breakLenMs
        scheduleAll(ctx); Notifs.session(ctx)
    }

    /** Termina la sesión (completada o por emergencia). Queda pendiente la encuesta de ánimo. */
    fun finish(ctx: Context, completed: Boolean) {
        val s = Store(ctx)
        if (s.phase == Phase.STUDY) {
            val now = System.currentTimeMillis()
            s.studiedMs = s.studiedMs + (now.coerceAtMost(s.phaseEnd) - s.phaseStart).coerceAtLeast(0)
        }
        s.phase = Phase.DONE
        s.breakWaiting = false
        s.pendingMood = true
        s.pendingCompleted = completed
        cancel(ctx, RC_PHASE); cancel(ctx, RC_EYES); cancel(ctx, RC_POSTURE)
        Notifs.session(ctx)
        scheduleDaily(ctx)
    }

    fun saveSession(ctx: Context, focus: Int, stress: Int) {
        val s = Store(ctx)
        if (!s.pendingMood) return
        val minutes = (s.studiedMs / MIN * speed).toInt()
        s.addSession(SessionLog(
            s.sessionStart, System.currentTimeMillis(), minutes, s.techId, s.subject,
            s.place.name, s.pendingCompleted, focus, stress
        ))
        s.pendingMood = false
        s.phase = Phase.IDLE
    }

    // ---------------- Alarmas ----------------

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun pi(ctx: Context, rc: Int, action: String, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
        PendingIntent.getBroadcast(
            ctx, rc, Intent(ctx, AlarmReceiver::class.java).setAction(action),
            flags or PendingIntent.FLAG_IMMUTABLE
        )

    private fun canExact(ctx: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(ctx).canScheduleExactAlarms()

    /** Alarma "de despertador": la más fiable, suena aunque el móvil esté en reposo. */
    private fun alarmClock(ctx: Context, rc: Int, action: String, at: Long) {
        val op = pi(ctx, rc, action)!!
        if (canExact(ctx)) {
            val show = PendingIntent.getActivity(ctx, rc, Intent(ctx, SessionActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            am(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), op)
        } else am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
    }

    private fun exact(ctx: Context, rc: Int, action: String, at: Long) {
        val op = pi(ctx, rc, action)!!
        if (canExact(ctx)) am(ctx).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
        else am(ctx).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, op)
    }

    private fun cancel(ctx: Context, rc: Int) {
        val p = PendingIntent.getBroadcast(ctx, rc, Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        // Las acciones difieren, así que cancelamos por cada acción conocida.
        for (a in ACTIONS) pi(ctx, rc, a, PendingIntent.FLAG_NO_CREATE)?.let { am(ctx).cancel(it); it.cancel() }
        p?.let { am(ctx).cancel(it) }
    }

    const val A_PHASE = "com.nmsevinf.studyfit.PHASE"
    const val A_EYES = "com.nmsevinf.studyfit.EYES"
    const val A_POSTURE = "com.nmsevinf.studyfit.POSTURE"
    const val A_DAILY = "com.nmsevinf.studyfit.DAILY"
    const val A_SLEEP_WARN = "com.nmsevinf.studyfit.SLEEP_WARN"
    const val A_SLEEP = "com.nmsevinf.studyfit.SLEEP"
    const val A_SCHED = "com.nmsevinf.studyfit.SCHED"
    private val ACTIONS = listOf(A_PHASE, A_EYES, A_POSTURE, A_DAILY, A_SLEEP_WARN, A_SLEEP, A_SCHED)

    /** Reprograma todo según el estado actual (se llama al iniciar, al cambiar de fase y al reiniciar el móvil). */
    fun scheduleAll(ctx: Context) {
        val s = Store(ctx)
        val now = System.currentTimeMillis()
        if (s.inSession && s.breakWaiting) {
            // Descanso en pausa: no hay alarma de fin de fase hasta que lo empieces
            cancel(ctx, RC_PHASE); cancel(ctx, RC_EYES); cancel(ctx, RC_POSTURE); cancel(ctx, RC_DAILY)
        } else if (s.inSession) {
            if (s.phaseEnd <= now) {
                // Si el móvil estuvo apagado y la fase ya pasó, avanzamos ya.
                onPhaseEnd(ctx); return
            }
            alarmClock(ctx, RC_PHASE, A_PHASE, s.phaseEnd)
            if (s.phase == Phase.STUDY) { scheduleEyes(ctx); schedulePosture(ctx) } else { cancel(ctx, RC_EYES); cancel(ctx, RC_POSTURE) }
            cancel(ctx, RC_DAILY)
        } else {
            cancel(ctx, RC_PHASE); cancel(ctx, RC_EYES); cancel(ctx, RC_POSTURE)
            scheduleDaily(ctx)
        }
        scheduleSleep(ctx)
        scheduleStudyTimes(ctx)
    }

    fun scheduleEyes(ctx: Context) = scheduleMicro(ctx, RC_EYES, A_EYES, 20 * MIN / speed, Store(ctx).eyesOn)
    fun schedulePosture(ctx: Context) = scheduleMicro(ctx, RC_POSTURE, A_POSTURE, 30 * MIN / speed, Store(ctx).postureOn)

    private fun scheduleMicro(ctx: Context, rc: Int, action: String, every: Long, on: Boolean) {
        val s = Store(ctx)
        val at = System.currentTimeMillis() + every
        if (on && at < s.phaseEnd - 60_000 / speed) exact(ctx, rc, action, at) else cancel(ctx, rc)
    }

    /** Recordatorios del día (agua y moverse) cada 90 min dentro del horario, fuera de sesión. */
    fun scheduleDaily(ctx: Context) {
        val s = Store(ctx)
        if (!s.dailyOn || s.inSession) { cancel(ctx, RC_DAILY); return }
        val c = Calendar.getInstance().apply { add(Calendar.MINUTE, 90) }
        val h = c.get(Calendar.HOUR_OF_DAY)
        if (h < s.dayStart || h >= s.dayEnd) {
            if (h >= s.dayEnd) c.add(Calendar.DAY_OF_YEAR, 1)
            c.set(Calendar.HOUR_OF_DAY, s.dayStart); c.set(Calendar.MINUTE, 30); c.set(Calendar.SECOND, 0)
        }
        exact(ctx, RC_DAILY, A_DAILY, c.timeInMillis)
    }

    fun scheduleSleep(ctx: Context) {
        val s = Store(ctx)
        if (!s.sleepOn) { cancel(ctx, RC_SLEEP_WARN); cancel(ctx, RC_SLEEP); return }
        exact(ctx, RC_SLEEP_WARN, A_SLEEP_WARN, nextTime(s.bedHour, s.bedMinute, -30))
        alarmClockOrExact(ctx, RC_SLEEP, A_SLEEP, nextTime(s.bedHour, s.bedMinute, 0))
    }

    private fun alarmClockOrExact(ctx: Context, rc: Int, action: String, at: Long) = exact(ctx, rc, action, at)

    /** Horarios de estudio: cada uno programa su próxima aparición. */
    fun scheduleStudyTimes(ctx: Context) {
        val s = Store(ctx)
        for (i in 0 until 20) cancel(ctx, RC_SCHED_BASE + i)
        s.schedules().forEachIndexed { i, sc ->
            if (i >= 20) return@forEachIndexed
            val at = nextOccurrence(sc) ?: return@forEachIndexed
            alarmClock(ctx, RC_SCHED_BASE + i, A_SCHED, at)
        }
    }

    fun nextOccurrence(sc: StudySchedule): Long? {
        if (sc.days == 0) return null
        val now = System.currentTimeMillis()
        val c = Calendar.getInstance()
        for (d in 0..7) {
            val k = Calendar.getInstance().apply {
                timeInMillis = now; add(Calendar.DAY_OF_YEAR, d)
                set(Calendar.HOUR_OF_DAY, sc.hour); set(Calendar.MINUTE, sc.minute)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            // Calendar: domingo=1 … sábado=7 → índice lunes=0 … domingo=6
            val idx = (k.get(Calendar.DAY_OF_WEEK) + 5) % 7
            if (sc.days and (1 shl idx) != 0 && k.timeInMillis > now + 1000) return k.timeInMillis
        }
        c.timeInMillis = now
        return null
    }

    fun nextTime(h: Int, m: Int, offsetMin: Int): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, offsetMin)
        }
        if (c.timeInMillis <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** ¿Es ya hora de dormir (o casi)? */
    fun isLate(ctx: Context): Boolean {
        val s = Store(ctx)
        if (!s.sleepOn) return false
        val c = Calendar.getInstance()
        val nowMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val bed = s.bedHour * 60 + s.bedMinute
        // Entre la hora de dormir y las 5:00 de la mañana
        return if (bed > 5 * 60) nowMin >= bed || nowMin < 5 * 60 else nowMin in bed until 5 * 60
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val s = Store(ctx)
        when (intent.action) {
            Engine.A_PHASE -> if (s.inSession) Engine.onPhaseEnd(ctx)
            Engine.A_EYES -> if (s.phase == Phase.STUDY) {
                Notifs.remind(ctx, "Descansa la vista", Tips.EYES)
                Engine.scheduleEyes(ctx)
            }
            Engine.A_POSTURE -> if (s.phase == Phase.STUDY) {
                Notifs.remind(ctx, "Revisa tu postura", Tips.posture.random())
                Engine.schedulePosture(ctx)
            }
            Engine.A_DAILY -> {
                if (!s.inSession) {
                    val water = s.waterToday()
                    if (water < s.waterGoal && (System.currentTimeMillis() / 60000 / 90) % 2 == 0L)
                        Notifs.remind(ctx, "Agua: $water de ${s.waterGoal} vasos hoy", Tips.WATER)
                    else
                        Notifs.remind(ctx, "Muévete un poco", "Llevas rato quieta: levántate, estira o da un paseo de 2 minutos.")
                }
                Engine.scheduleDaily(ctx)
            }
            Engine.A_SLEEP_WARN -> {
                Notifs.remind(ctx, "En 30 minutos, a dormir", "Ve cerrando: anota por dónde vas para mañana y deja el móvil lejos de la cama.")
                Engine.scheduleSleep(ctx)
            }
            Engine.A_SLEEP -> {
                if (!s.inSession) Notifs.call(ctx, CallType.SLEEP)
                else Notifs.remind(ctx, "Ya es tu hora de dormir", "Termina este bloque y descansa: el sueño consolida lo que has estudiado.")
                Engine.scheduleSleep(ctx)
            }
            Engine.A_SCHED -> {
                if (!s.inSession) Notifs.call(ctx, CallType.START)
                Engine.scheduleStudyTimes(ctx)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Engine.scheduleAll(ctx)
        Notifs.session(ctx)
    }
}
