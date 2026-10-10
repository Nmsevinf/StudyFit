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
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Pantalla de estudio. Principio: una sola cosa a la vista.
 * Estudio = fondo tierra profunda, anillo de progreso, el paso actual y nada más.
 * Descanso = fondo claro con oliva.
 */
class SessionActivity : Activity() {

    private lateinit var s: Store
    private val h = Handler(Looper.getMainLooper())
    private var timer: TextView? = null
    private var ring: Ring? = null
    private var stepView: TextView? = null
    private var stepsBox: LinearLayout? = null
    private var builtFor = ""
    private var lastStep = -1
    private var playing = -1
    private var guideOpen = false

    private val tick = object : Runnable {
        override fun run() { update(); h.postDelayed(this, 1000) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        s = Store(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() { super.onResume(); builtFor = ""; h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }
    override fun onDestroy() { super.onDestroy(); if (!s.inSession) Ambient.stop() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (s.phase == Phase.STUDY) Toast.makeText(this, "Estás en un bloque de estudio", Toast.LENGTH_SHORT).show()
        else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun key() = "${s.phase}-${s.cycleIndex}-${s.breakKind}"

    private fun update() {
        if (!s.inSession) {
            Ambient.stop(); playing = -1; unlock(); immersive(false)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish(); return
        }
        if (key() != builtFor) build()
        val now = System.currentTimeMillis()
        val left = s.phaseEnd - now
        if (left <= -3000) Engine.scheduleAll(this)
        timer?.text = fmtClock(left)
        val total = (s.phaseEnd - s.phaseStart).coerceAtLeast(1)
        ring?.progress = (now - s.phaseStart).toFloat() / total
        if (s.phase == Phase.STUDY) {
            val elapsedMin = ((now - s.phaseStart) * Engine.speed / Engine.MIN).toInt()
            val steps = s.technique.steps
            val idx = steps.indexOfLast { it.at <= elapsedMin }.coerceAtLeast(0)
            if (idx != lastStep) {
                lastStep = idx
                stepView?.text = steps[idx].text
                paintSteps()
            }
        }
    }

    private fun paintSteps() {
        val box = stepsBox ?: return
        for (i in 0 until box.childCount) (box.getChildAt(i) as TextView).apply {
            setTextColor(if (i == lastStep) C.NIGHT_TEXT else C.NIGHT_MUTED)
            alpha = if (i < lastStep) 0.5f else 1f
        }
    }

    private fun build() {
        builtFor = key(); lastStep = -1
        if (s.phase == Phase.STUDY) buildStudy() else buildBreak()
    }

    // ---------------- Estudio ----------------
    private fun buildStudy() {
        val t = s.technique
        val sc = Screen(this, dark = true)
        val r = sc.root

        // Cabecera discreta
        r.row {
            text("Bloque ${s.cycleIndex} de ${s.cycles}", 14f, C.NIGHT_MUTED).layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            val exit = textButton("Salir", C.NIGHT_MUTED) {
                Toast.makeText(this@SessionActivity, "Mantén pulsado para salir", Toast.LENGTH_SHORT).show()
            }
            exit.textSize = 14f
            exit.layoutParams = LinearLayout.LayoutParams(-2, -2)
            exit.setOnLongClickListener { confirmExit(); true }
        }

        // Anillo + temporizador
        val frame = FrameLayout(this)
        ring = Ring(this, C.NIGHT_SURFACE, C.SAND)
        frame.addView(ring, FrameLayout.LayoutParams(-1, -1))
        val center = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        timer = TextView(this).apply {
            textSize = 64f; setTextColor(C.NIGHT_TEXT); typeface = C.LIGHT
            gravity = Gravity.CENTER; fontFeatureSettings = "tnum"; letterSpacing = -0.02f
        }
        center.addView(timer)
        center.addView(TextView(this).apply {
            text = t.name; textSize = 15f; setTextColor(C.NIGHT_MUTED); gravity = Gravity.CENTER
        })
        frame.addView(center, FrameLayout.LayoutParams(-1, -1))
        val ringSize = (resources.displayMetrics.widthPixels * 0.72f).toInt()
        r.addView(frame, LinearLayout.LayoutParams(ringSize, ringSize).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(28); bottomMargin = dp(36)
        })

        // El paso actual: lo único que hay que leer
        stepView = r.text("", 21f, C.NIGHT_TEXT).apply { setLineSpacing(0f, 1.3f) }
        if (s.subject.isNotBlank()) r.text(s.subject, 14f, C.NIGHT_MUTED, top = 8)

        // Guía completa, plegada
        stepsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (guideOpen) View.VISIBLE else View.GONE }
        val toggle = r.textButton(if (guideOpen) "Ocultar la guía" else "Ver la guía completa", C.NIGHT_MUTED) { }
        toggle.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        toggle.setPadding(0, toggle.paddingTop, 0, toggle.paddingBottom)
        (toggle.layoutParams as LinearLayout.LayoutParams).topMargin = dp(24)
        toggle.setOnClickListener {
            guideOpen = !guideOpen
            stepsBox?.visibility = if (guideOpen) View.VISIBLE else View.GONE
            toggle.text = if (guideOpen) "Ocultar la guía" else "Ver la guía completa"
        }
        t.steps.forEach { st -> stepsBox!!.text("Min ${st.at}   ${st.text}", 14f, C.NIGHT_MUTED, top = 5, bottom = 5) }
        r.addView(stepsBox)

        // Sonido
        r.text("Sonido de fondo", 14f, C.NIGHT_MUTED, top = 28, bottom = 4)
        ChoiceGroup(r, Ambient.names.indices.map { it to Ambient.names[it] }, s.ambient, columns = 4, dark = true) { k ->
            s.ambient = k; Ambient.play(k); playing = k
        }
        if (playing != s.ambient) { Ambient.play(s.ambient); playing = s.ambient }

        r.divider(C.NIGHT_SURFACE)
        r.textButton("Llamada de emergencia (112)", C.NIGHT_MUTED) {
            unlock(); immersive(false)
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        }.textSize = 14f

        if (s.strict) lock()
        sc.show()
        immersive(true)
    }

    // ---------------- Descanso ----------------
    private fun buildBreak() {
        Ambient.stop(); playing = -1
        unlock(); immersive(false)
        val sc = Screen(this)
        val r = sc.root
        val (title, sub, type) = when (s.breakKind) {
            BreakKind.EXERCISE -> Triple("Toca moverse", "Unos minutos de movimiento y vuelves con la cabeza despejada.", CallType.EXERCISE)
            BreakKind.SNACK -> Triple("Pausa para comer algo", "Algo ligero, agua, y de vuelta.", CallType.SNACK)
            BreakKind.LONG -> Triple("Descanso largo", "Muévete, bebe agua y come algo ligero.", CallType.LONG)
        }
        r.text("Descanso", 14f, C.OLIVE, bottom = 0)

        val frame = FrameLayout(this)
        ring = Ring(this, C.OLIVE_SOFT, C.OLIVE)
        frame.addView(ring, FrameLayout.LayoutParams(-1, -1))
        timer = TextView(this).apply {
            textSize = 44f; setTextColor(C.TEXT); typeface = C.LIGHT; gravity = Gravity.CENTER; fontFeatureSettings = "tnum"
        }
        frame.addView(timer, FrameLayout.LayoutParams(-1, -1))
        val size = (resources.displayMetrics.widthPixels * 0.48f).toInt()
        r.addView(frame, LinearLayout.LayoutParams(size, size).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(20); bottomMargin = dp(24)
        })

        r.title(title)
        r.muted(sub)
        r.button("Ver qué hacer", C.OLIVE, big = true) {
            startActivity(Intent(this@SessionActivity, CallActivity::class.java).putExtra("type", type).putExtra("silent", true))
        }.also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(16) }

        r.card(C.WATER_SOFT) {
            row {
                val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                col.text("Agua", 16f, C.TEXT, bold = true)
                col.text("${s.waterToday()} de ${s.waterGoal} vasos hoy", 14f, C.MUTED)
                addView(col, LinearLayout.LayoutParams(0, -2, 1f))
                button("+1 vaso", C.WATER) { s.addWater(); builtFor = "" }.layoutParams = LinearLayout.LayoutParams(-2, -2)
            }
        }
        r.muted("En el descanso puedes usar el móvil. Te aviso cuando toque volver.")
        r.divider()
        val exit = r.textButton("Terminar la sesión (mantén pulsado)", C.MUTED) {
            Toast.makeText(this, "Mantén pulsado para terminar", Toast.LENGTH_SHORT).show()
        }
        exit.setOnLongClickListener { confirmExit(); true }
        sc.show()
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("¿Terminar la sesión?")
            .setMessage("Se guardará como interrumpida. Si es una urgencia, sal sin problema.")
            .setPositiveButton("Terminar") { _, _ ->
                Engine.finish(this, completed = false)
                Ambient.stop(); unlock(); immersive(false)
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                finish()
            }
            .setNegativeButton("Seguir", null)
            .show()
    }

    /** Oculta barras de estado y navegación mientras estudias (menos estímulos). */
    private fun immersive(on: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val c = window.insetsController ?: return
            if (on) {
                c.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else c.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (on)
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            else 0
        }
    }

    private fun isLocked() = getSystemService(android.app.ActivityManager::class.java).lockTaskModeState !=
        android.app.ActivityManager.LOCK_TASK_MODE_NONE
    private fun lock() { if (!isLocked()) try { startLockTask() } catch (_: Exception) { } }
    private fun unlock() { if (isLocked()) try { stopLockTask() } catch (_: Exception) { } }

    @Suppress("unused") private val unused = Color.TRANSPARENT
}
