package com.nmsevinf.studyfit

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Pantalla tipo "llamada entrante" para ejercicio, tentempié, volver a estudiar, etc. */
class CallActivity : Activity() {

    private lateinit var s: Store
    private var ring: Ringtone? = null
    private val h = Handler(Looper.getMainLooper())
    private var type = CallType.EXERCISE

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        s = Store(this)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        open(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    private fun open(i: Intent) {
        type = i.getStringExtra("type") ?: CallType.EXERCISE
        if (i.getBooleanExtra("silent", false)) content() else ringing()
    }

    override fun onDestroy() { stopRing(); h.removeCallbacksAndMessages(null); super.onDestroy() }

    // ---------- Fase 1: suena ----------
    private fun ringing() {
        val t = s.technique
        val breakLen = if (type == CallType.LONG) t.longBreakMin else t.breakMin
        val bed = "%02d:%02d".format(s.bedHour, s.bedMinute)
        // (título, subtítulo, texto dentro del círculo, ¿pantalla oscura?)
        data class R(val title: String, val sub: String, val label: String, val dark: Boolean, val color: Int)
        val v = when (type) {
            CallType.EXERCISE -> R("Hora de moverte", "Descanso activo, ${s.place.label.lowercase()}", "$breakLen′", false, C.OLIVE)
            CallType.LONG -> R("Descanso largo", "Movimiento, agua y algo de comer", "$breakLen′", false, C.OLIVE)
            CallType.SNACK -> R("Pausa para comer algo", "Algo ligero que te dé energía", "$breakLen′", false, C.OLIVE)
            CallType.BACK -> R("Vuelve a estudiar", "${t.name}, bloque ${s.cycleIndex} de ${s.cycles}", "${t.studyMin}′", true, C.SAND)
            CallType.END -> R("Sesión terminada", "Buen trabajo. Ahora descansa de verdad.", "${(s.studiedMs / Engine.MIN * Engine.speed)}′", false, C.PRIMARY)
            CallType.START -> R("Es tu hora de estudiar", "Prepara agua y apuntes, y empezamos", "▶", true, C.SAND)
            CallType.SLEEP -> R("Hora de dormir", "Dormir fija lo que has estudiado hoy", bed, true, C.NIGHT_SURFACE)
            else -> R("StudyFit", "", "", false, C.PRIMARY)
        }
        val sc = Screen(this, dark = v.dark)
        val r = sc.root
        r.gravity = Gravity.CENTER_HORIZONTAL
        val fg = if (v.dark) C.NIGHT_TEXT else C.TEXT
        val mut = if (v.dark) C.NIGHT_MUTED else C.MUTED
        r.text("StudyFit", 14f, mut, center = true, top = 24)
        val haloLabel = if (v.dark && type != CallType.SLEEP) C.NIGHT else if (type == CallType.SLEEP) C.NIGHT_TEXT else Color.WHITE
        val size = (resources.displayMetrics.widthPixels * 0.7f).toInt()
        r.addView(Halo(this, v.color, v.label, haloLabel), LinearLayout.LayoutParams(size, size).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(16); bottomMargin = dp(8)
        })
        r.text(v.title, 28f, fg, center = true).typeface = C.MEDIUM
        r.text(v.sub, 16f, mut, center = true, bottom = 48)
        val btnColor = if (v.dark) C.NIGHT_TEXT else v.color
        r.button("Aceptar", btnColor, if (v.dark) C.NIGHT else Color.WHITE, big = true) { stopRing(); content() }
        if (type == CallType.START || type == CallType.SLEEP) {
            r.textButton("Ahora no", mut) { stopRing(); finish() }
        }
        sc.show()
        startRing()
    }

    private fun startRing() { /* el sonido lo pone RingService */ }

    private fun stopRing() {
        RingService.stop(this)
        stopRingOld()
    }

    private fun stopRingOld() {
        try { ring?.stop() } catch (_: Exception) { }
        ring = null
        getSystemService(Vibrator::class.java)?.cancel()
    }

    // ---------- Fase 2: contenido ----------
    private fun content() {
        val sc = Screen(this)
        val r = sc.root
        when (type) {
            CallType.EXERCISE -> { exercise(r, s.technique.breakMin) }
            CallType.LONG -> { exercise(r, (s.technique.longBreakMin - 5).coerceAtLeast(3)); snack(r) }
            CallType.SNACK -> { r.title("Algo de comer"); breakLeft(r); snack(r) }
            CallType.BACK -> {
                r.title("A estudiar")
                r.muted("Bloque ${s.cycleIndex} de ${s.cycles}, ${s.technique.name}")
                r.card(C.SURFACE) {
                    text("Para empezar", 14f, C.MUTED)
                    body(s.technique.steps.first().text)
                }
                r.muted("El bloqueo de distracciones ya está activo.")
                r.button("Ir a la sesión", big = true) { goSession() }
                sc.show(); return
            }
            CallType.END -> {
                r.title("Sesión terminada")
                r.body("Has terminado todos los bloques. Ahora descansa de verdad: sal a la calle, come algo o habla con alguien.")
                r.card(C.WATER_SOFT) { body(Tips.WATER) }
                r.button("Ver el resumen", big = true) {
                    startActivity(Intent(this@CallActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    finish()
                }
                sc.show(); return
            }
            CallType.START -> {
                r.title("¿Empezamos?")
                r.body("Prepara agua, apuntes y deja todo lo demás fuera de la mesa.")
                r.button("Elegir técnica y empezar", big = true) {
                    startActivity(Intent(this@CallActivity, StartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                }
                r.textButton("Hoy no", C.MUTED) { finish() }
                sc.show(); return
            }
            CallType.SLEEP -> {
                r.title("A dormir")
                r.body("• Deja el móvil fuera de la cama (o en modo avión).\n• Luz baja y pantalla apagada 30 min antes.\n• Dormir 7–9 h mejora la memoria más que una hora extra de estudio.\n• Mañana retomas donde lo dejaste.")
                r.button("Buenas noches") { finish() }
                sc.show(); return
            }
        }
        r.divider()
        r.button("Volver a la sesión", C.OLIVE) { goSession() }
        sc.show()
    }

    private fun goSession() {
        val target = if (s.inSession) SessionActivity::class.java else MainActivity::class.java
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        finish()
    }

    private fun breakLeft(r: LinearLayout) {
        if (s.phase != Phase.BREAK) return
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        r.addView(box)
        fillBreakLeft(box)
    }

    private fun fillBreakLeft(box: LinearLayout) {
        box.removeAllViews()
        if (s.breakWaiting) {
            box.muted("La cuenta atrás de ${fmtMin((s.breakLenMs / Engine.MIN * Engine.speed).toInt())} empieza cuando pulses el botón.")
            box.button("Empezar descanso", C.OLIVE, big = true) {
                Engine.startBreak(this)
                fillBreakLeft(box)
            }
            return
        }
        val tv = box.muted("")
        val upd = object : Runnable {
            override fun run() {
                tv.text = "Quedan ${fmtClock(s.phaseEnd - System.currentTimeMillis())} de descanso"
                h.postDelayed(this, 1000)
            }
        }
        h.post(upd)
    }

    private fun exercise(r: LinearLayout, minutes: Int) {
        r.title("Muévete")
        breakLeft(r)
        r.muted("¿Dónde estás ahora?")
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ChoiceGroup(r, Place.values().map { it to it.label }, s.place) { p ->
            s.place = p; fillRoutine(box, p, minutes)
        }
        r.addView(box)
        fillRoutine(box, s.place, minutes)
    }

    private var exTimer: Runnable? = null

    private fun fillRoutine(box: LinearLayout, p: Place, minutes: Int) {
        exTimer?.let { h.removeCallbacks(it) }
        box.removeAllViews()
        val list = Exercises.routine(p, minutes)
        var idx = 0
        lateinit var name: TextView
        lateinit var how: TextView
        lateinit var clock: TextView
        lateinit var counter: TextView
        lateinit var figure: android.widget.FrameLayout
        box.card(C.OLIVE_SOFT) {
            counter = text("", 13f, C.OLIVE)
            figure = android.widget.FrameLayout(context).apply { background = rounded(C.SURFACE, dpf(16f)) }
            addView(figure, LinearLayout.LayoutParams(-1, (resources.displayMetrics.widthPixels * 0.62f).toInt()).apply { topMargin = dp(8); bottomMargin = dp(8) })
            name = text("", 22f, C.TEXT, bold = true)
            how = body("")
            clock = text("", 48f, C.TEXT, center = true).apply { typeface = C.LIGHT; fontFeatureSettings = "tnum" }
            row {
                button("Empezar", C.OLIVE, weight = 1f) { startEx(list, idx, clock) }
                outlineButton("Siguiente", C.OLIVE, weight = 1f) {
                    exTimer?.let { h.removeCallbacks(it) }
                    idx = (idx + 1) % list.size
                    show(list, idx, counter, name, how, clock, figure)
                }
            }
        }
        box.text("Rutina completa", 15f, C.TEXT, bold = true, top = 14)
        box.muted("Toca un ejercicio para verlo.")
        list.forEachIndexed { k, e ->
            box.textButton("${e.name}, ${e.seconds} s", C.TEXT) {
                exTimer?.let { h.removeCallbacks(it) }
                idx = k; show(list, idx, counter, name, how, clock, figure)
                (box.parent?.parent as? android.widget.ScrollView)?.smoothScrollTo(0, 0)
            }.apply { gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL; setPadding(0, paddingTop, 0, paddingBottom) }
        }
        show(list, 0, counter, name, how, clock, figure)
    }

    private fun show(list: List<Exercise>, i: Int, counter: TextView, name: TextView, how: TextView, clock: TextView, figure: android.widget.FrameLayout) {
        val e = list[i]
        counter.text = "Ejercicio ${i + 1} de ${list.size}"
        name.text = e.name; how.text = e.how; clock.text = fmtClock(e.seconds * 1000L)
        figure.removeAllViews()
        val mv = MoveView(this, e.name)
        figure.visibility = if (mv.hasAnimation) android.view.View.VISIBLE else android.view.View.GONE
        figure.addView(mv, android.widget.FrameLayout.LayoutParams(-1, -1).apply { val p = dp(12); setMargins(p, p, p, p) })
    }

    private fun startEx(list: List<Exercise>, i: Int, clock: TextView) {
        exTimer?.let { h.removeCallbacks(it) }
        val end = System.currentTimeMillis() + list[i].seconds * 1000L
        exTimer = object : Runnable {
            override fun run() {
                val left = end - System.currentTimeMillis()
                clock.text = fmtClock(left)
                if (left > 0) h.postDelayed(this, 250)
                else {
                    clock.text = "Hecho"
                    getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        }
        h.post(exTimer!!)
    }

    private fun snack(r: LinearLayout) {
        r.h2("Ideas para comer")
        r.muted("Para concentrarte mejor sin engordar: proteína o grasa buena + fibra, raciones pequeñas.")
        Snacks.pick(3).forEach { sn ->
            r.card {
                text(sn.name, 16f, C.TEXT, bold = true)
                muted(sn.why)
            }
        }
        r.card(C.SURFACE) { body(Snacks.TIPS) }
        r.button("He bebido un vaso de agua", C.WATER) { s.addWater(); (it as android.widget.Button).text = "Apuntado: ${s.waterToday()} vasos hoy" }
    }
}
