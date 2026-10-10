package com.nmsevinf.studyfit

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/** Pantalla de estudio: cuenta atrás, pasos guiados de la técnica, sonido ambiente y salida de emergencia. */
class SessionActivity : Activity() {

    private lateinit var s: Store
    private val h = Handler(Looper.getMainLooper())
    private var timer: TextView? = null
    private var bar: ProgressBar? = null
    private var stepView: TextView? = null
    private var builtFor = ""
    private var lastStep = -1
    private var playing = -1

    private val tick = object : Runnable {
        override fun run() { update(); h.postDelayed(this, 1000) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        s = Store(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        builtFor = ""
        h.post(tick)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(tick)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!s.inSession) Ambient.stop()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (s.phase == Phase.STUDY) Toast.makeText(this, "Estás en un bloque de estudio 📚", Toast.LENGTH_SHORT).show()
        else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun key() = "${s.phase}-${s.cycleIndex}-${s.breakKind}"

    private fun update() {
        if (!s.inSession) {
            Ambient.stop(); playing = -1
            unlock()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish(); return
        }
        if (key() != builtFor) build()
        val now = System.currentTimeMillis()
        val left = s.phaseEnd - now
        if (left <= -3000) Engine.scheduleAll(this) // por si la alarma se retrasa
        timer?.text = fmtClock(left)
        val total = (s.phaseEnd - s.phaseStart).coerceAtLeast(1)
        bar?.progress = (((now - s.phaseStart) * 1000) / total).toInt().coerceIn(0, 1000)
        if (s.phase == Phase.STUDY) {
            val elapsedMin = ((now - s.phaseStart) * Engine.speed / Engine.MIN).toInt()
            val steps = s.technique.steps
            val idx = steps.indexOfLast { it.at <= elapsedMin }.coerceAtLeast(0)
            if (idx != lastStep) {
                lastStep = idx
                stepView?.text = steps[idx].text
                stepsBox?.let { box ->
                    for (i in 0 until box.childCount) (box.getChildAt(i) as TextView).apply {
                        alpha = if (i == idx) 1f else 0.45f
                        setTypeface(null, if (i == idx) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                    }
                }
            }
        }
    }

    private var stepsBox: LinearLayout? = null

    private fun build() {
        builtFor = key(); lastStep = -1
        val study = s.phase == Phase.STUDY
        val t = s.technique
        val sc = Screen(this, dark = study)
        val r = sc.root
        val fg = if (study) Color.WHITE else C.TEXT

        r.text(if (study) "📚 BLOQUE ${s.cycleIndex} DE ${s.cycles}" else "🌿 DESCANSO", 14f, if (study) Color.parseColor("#BFE3DB") else C.PRIMARY, bold = true, center = true)
        r.text(t.name + if (s.subject.isNotBlank()) " · ${s.subject}" else "", 16f, fg, center = true)
        timer = r.text("--:--", 72f, fg, bold = true, center = true, top = 8)
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progressTintList = android.content.res.ColorStateList.valueOf(C.ACCENT)
        }
        r.addView(bar, LinearLayout.LayoutParams(-1, dp(10)).apply { bottomMargin = dp(12) })

        if (study) {
            r.card(Color.parseColor("#1B6B5E")) {
                text("AHORA", 12f, Color.parseColor("#BFE3DB"), bold = true)
                stepView = text("", 19f, Color.WHITE, bold = true)
            }
            r.text("Guía de la técnica", 14f, Color.parseColor("#BFE3DB"), bold = true, top = 10)
            stepsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            t.steps.forEach { st -> stepsBox!!.text("min ${st.at} · ${st.text}", 14f, Color.WHITE, top = 3, bottom = 3) }
            r.addView(stepsBox)

            r.text("Sonido ambiente", 14f, Color.parseColor("#BFE3DB"), bold = true, top = 14)
            ChoiceGroup(r, Ambient.names.indices.map { it to Ambient.names[it] }, s.ambient) { k ->
                s.ambient = k; Ambient.play(k); playing = k
            }
            if (playing != s.ambient) { Ambient.play(s.ambient); playing = s.ambient }
            if (s.strict) lock()
        } else {
            Ambient.stop(); playing = -1
            unlock()
            val (title, type) = when (s.breakKind) {
                BreakKind.EXERCISE -> "Toca moverse 🏃" to CallType.EXERCISE
                BreakKind.SNACK -> "Pausa para tentempié 🍎" to CallType.SNACK
                BreakKind.LONG -> "Descanso largo: ejercicio + tentempié 🌿" to CallType.LONG
            }
            r.card {
                text(title, 18f, C.TEXT, bold = true)
                muted("Lugar: ${s.place.emoji} ${s.place.label}")
                button("Ver qué hacer") {
                    startActivity(Intent(this@SessionActivity, CallActivity::class.java).putExtra("type", type).putExtra("silent", true))
                }
            }
            r.card(C.SOFT) {
                text("💧 Bebe agua", 16f, C.TEXT, bold = true)
                row {
                    muted("Llevas ${s.waterToday()} vasos hoy")
                    button("+1 vaso", C.BLUE) { s.addWater(); builtFor = "" }
                }
            }
            r.muted("En el descanso puedes usar el móvil. Te llamaré cuando toque volver.")
        }

        // Emergencias
        r.divider()
        r.row {
            outlineButton("📞 Llamar al 112", if (study) Color.WHITE else C.DANGER, weight = 1f) {
                unlock()
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
            }
            val exit = outlineButton("Salir (mantén pulsado)", if (study) Color.WHITE else C.MUTED, weight = 1f) {
                Toast.makeText(this@SessionActivity, "Mantén pulsado para salir de la sesión", Toast.LENGTH_SHORT).show()
            }
            exit.setOnLongClickListener { confirmExit(); true }
        }
        sc.show()
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("¿Terminar la sesión?")
            .setMessage("Se guardará como interrumpida. Si es una urgencia, sal sin problema.")
            .setPositiveButton("Sí, salir") { _, _ ->
                Engine.finish(this, completed = false)
                Ambient.stop(); unlock()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
            .setNegativeButton("Seguir estudiando", null)
            .show()
    }

    private fun isLocked() = getSystemService(android.app.ActivityManager::class.java).lockTaskModeState !=
        android.app.ActivityManager.LOCK_TASK_MODE_NONE
    private fun lock() {
        if (isLocked()) return
        try { startLockTask() } catch (_: Exception) { }
    }
    private fun unlock() {
        if (!isLocked()) return
        try { stopLockTask() } catch (_: Exception) { }
    }

    @Suppress("unused")
    private fun center(v: View) { (v as? TextView)?.gravity = Gravity.CENTER }
}
