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
        Notifs.cancelCall(this)
        if (i.getBooleanExtra("silent", false)) content() else ringing()
    }

    override fun onDestroy() { stopRing(); h.removeCallbacksAndMessages(null); super.onDestroy() }

    // ---------- Fase 1: suena ----------
    private fun ringing() {
        val (emoji, title, sub) = when (type) {
            CallType.EXERCISE -> Triple("🏃", "¡Hora de moverte!", "Descanso activo · ${s.place.label}")
            CallType.LONG -> Triple("🌿", "Descanso largo", "Ejercicio + agua + tentempié")
            CallType.SNACK -> Triple("🍎", "Pausa para tentempié", "Recarga energía sin pasarte")
            CallType.BACK -> Triple("📚", "Vuelve a estudiar", "${s.technique.name} · bloque ${s.cycleIndex} de ${s.cycles}")
            CallType.END -> Triple("🎉", "¡Sesión terminada!", "Muy bien hecho")
            CallType.START -> Triple("📖", "Es tu hora de estudiar", "Siéntate, apoya el móvil y empezamos")
            CallType.SLEEP -> Triple("🌙", "Hora de dormir", "El sueño fija lo que has estudiado")
            else -> Triple("⏰", "StudyFit", "")
        }
        val sc = Screen(this, dark = true)
        val r = sc.root
        r.text("StudyFit te llama…", 15f, Color.parseColor("#BFE3DB"), center = true, top = 40)
        r.text(emoji, 90f, Color.WHITE, center = true, top = 30)
        r.text(title, 30f, Color.WHITE, bold = true, center = true, top = 10)
        r.text(sub, 17f, Color.parseColor("#BFE3DB"), center = true, bottom = 60)
        r.button("✅  Aceptar", Color.parseColor("#2EAD5B"), big = true) { stopRing(); content() }
        if (type == CallType.START || type == CallType.SLEEP) {
            r.outlineButton("Ahora no", Color.WHITE) { stopRing(); finish() }
        }
        sc.show()
        startRing()
    }

    private fun startRing() {
        try {
            ring = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                isLooping = true
                play()
            }
        } catch (_: Exception) { }
        val v = getSystemService(Vibrator::class.java)
        v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500, 700, 1200), 0))
        // deja de sonar sola al minuto
        h.postDelayed({ stopRing() }, 60_000)
    }

    private fun stopRing() {
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
            CallType.SNACK -> { r.title("Tentempié 🍎"); breakLeft(r); snack(r) }
            CallType.BACK -> {
                r.title("¡A estudiar! 📚")
                r.body("Bloque ${s.cycleIndex} de ${s.cycles} · ${s.technique.name}")
                r.card(C.SOFT) {
                    text("Primer paso", 13f, C.PRIMARY, bold = true)
                    body(s.technique.steps.first().text)
                }
                r.muted("El bloqueo de distracciones ya está activo.")
                r.button("Ir a la sesión", big = true) { goSession() }
                sc.show(); return
            }
            CallType.END -> {
                r.title("¡Enhorabuena! 🎉")
                r.body("Has terminado todos los bloques. Ahora descansa de verdad: sal a la calle, come algo o habla con alguien.")
                r.card(C.SOFT) { body(Tips.WATER) }
                r.button("Ver resumen", big = true) {
                    startActivity(Intent(this@CallActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    finish()
                }
                sc.show(); return
            }
            CallType.START -> {
                r.title("¿Empezamos? 📖")
                r.body("Prepara agua, apuntes y deja todo lo demás fuera de la mesa.")
                r.button("Elegir técnica y empezar", big = true) {
                    startActivity(Intent(this@CallActivity, StartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                }
                r.outlineButton("Hoy no") { finish() }
                sc.show(); return
            }
            CallType.SLEEP -> {
                r.title("A dormir 🌙")
                r.body("• Deja el móvil fuera de la cama (o en modo avión).\n• Luz baja y pantalla apagada 30 min antes.\n• Dormir 7–9 h mejora la memoria más que una hora extra de estudio.\n• Mañana retomas donde lo dejaste.")
                r.button("Buenas noches") { finish() }
                sc.show(); return
            }
        }
        r.divider()
        r.button("Hecho · volver a la sesión") { goSession() }
        sc.show()
    }

    private fun goSession() {
        val target = if (s.inSession) SessionActivity::class.java else MainActivity::class.java
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        finish()
    }

    private fun breakLeft(r: LinearLayout) {
        if (s.phase != Phase.BREAK) return
        val tv = r.muted("")
        val upd = object : Runnable {
            override fun run() {
                tv.text = "Descanso: quedan ${fmtClock(s.phaseEnd - System.currentTimeMillis())}"
                h.postDelayed(this, 1000)
            }
        }
        h.post(upd)
    }

    private fun exercise(r: LinearLayout, minutes: Int) {
        r.title("Muévete 🏃")
        breakLeft(r)
        r.muted("¿Dónde estás ahora?")
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ChoiceGroup(r, Place.values().map { it to "${it.emoji} ${it.label}" }, s.place) { p ->
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
        box.card(C.SOFT) {
            counter = text("", 13f, C.PRIMARY, bold = true)
            name = text("", 22f, C.TEXT, bold = true)
            how = body("")
            clock = text("", 44f, C.PRIMARY, bold = true, center = true)
            row {
                button("▶ Empezar", weight = 1f) { startEx(list, idx, clock) }
                outlineButton("Siguiente ›", weight = 1f) {
                    exTimer?.let { h.removeCallbacks(it) }
                    idx = (idx + 1) % list.size
                    show(list, idx, counter, name, how, clock)
                }
            }
        }
        box.text("Rutina completa", 15f, C.TEXT, bold = true, top = 8)
        list.forEach { box.muted("• ${it.name} — ${it.seconds} s") }
        show(list, 0, counter, name, how, clock)
    }

    private fun show(list: List<Exercise>, i: Int, counter: TextView, name: TextView, how: TextView, clock: TextView) {
        val e = list[i]
        counter.text = "EJERCICIO ${i + 1} DE ${list.size}"
        name.text = e.name; how.text = e.how; clock.text = fmtClock(e.seconds * 1000L)
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
                    clock.text = "¡Hecho! ✅"
                    getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        }
        h.post(exTimer!!)
    }

    private fun snack(r: LinearLayout) {
        r.h2("Ideas de tentempié")
        r.muted("Para concentrarte mejor sin engordar: proteína o grasa buena + fibra, raciones pequeñas.")
        Snacks.pick(3).forEach { sn ->
            r.card {
                text(sn.name, 16f, C.TEXT, bold = true)
                muted(sn.why)
            }
        }
        r.card(C.SOFT) { body(Snacks.TIPS) }
        r.button("💧 He bebido un vaso de agua", C.BLUE) { s.addWater(); (it as android.widget.Button).text = "💧 ¡Apuntado! (${s.waterToday()} hoy)" }
    }
}
