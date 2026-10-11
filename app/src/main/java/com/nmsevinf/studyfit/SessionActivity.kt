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
        // Sin permiso de notificaciones Android oculta los avisos de descanso: lo pedimos al empezar
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2)
    }

    override fun onResume() { super.onResume(); builtFor = ""; h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }
    override fun onDestroy() { super.onDestroy(); if (!s.inSession) Ambient.stop() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (s.phase == Phase.STUDY && !s.paused) Toast.makeText(this, "Estás en un bloque de estudio", Toast.LENGTH_SHORT).show()
        else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun key() = "${s.phase}-${s.cycleIndex}-${s.breakKind}-${s.breakWaiting}-${s.paused}"

    private fun update() {
        if (!s.inSession) {
            Ambient.stop(); playing = -1; unlock(); immersive(false)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish(); return
        }
        if (key() != builtFor) build()
        val now = System.currentTimeMillis()
        val left = s.leftMs(now)
        if (s.breakWaiting) { timer?.text = fmtClock(left); ring?.progress = 0f; return }
        if (!s.paused && left <= -3000) Engine.scheduleAll(this)
        timer?.text = fmtClock(left)
        val total = s.phaseTotal.coerceAtLeast(1)
        ring?.progress = 1f - left.toFloat() / total
        if (s.phase == Phase.STUDY) {
            val elapsedMin = ((s.phaseTotal - left) * Engine.speed / Engine.MIN).toInt()
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
        when {
            s.paused -> buildPaused()
            s.phase == Phase.STUDY -> buildStudy()
            else -> buildBreak()
        }
    }

    // ---------------- En pausa ----------------
    private fun buildPaused() {
        Ambient.stop(); playing = -1
        unlock(); immersive(false)
        val study = s.phase == Phase.STUDY
        val sc = Screen(this)
        val r = sc.root
        r.text(if (study) "Estudio en pausa" else "Descanso en pausa", 14f, C.PRIMARY, bottom = 0)
        val frame = FrameLayout(this)
        ring = Ring(this, C.SURFACE, if (study) C.PRIMARY else C.OLIVE)
        frame.addView(ring, FrameLayout.LayoutParams(-1, -1))
        timer = TextView(this).apply {
            textSize = 44f; setTextColor(C.MUTED); typeface = C.LIGHT; gravity = Gravity.CENTER; fontFeatureSettings = "tnum"
        }
        frame.addView(timer, FrameLayout.LayoutParams(-1, -1))
        val size = (resources.displayMetrics.widthPixels * 0.48f).toInt()
        r.addView(frame, LinearLayout.LayoutParams(size, size).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(20); bottomMargin = dp(24)
        })
        r.title("Tómate el tiempo que necesites")
        r.muted("El tiempo está congelado y el bloqueo de apps desactivado. Cuando acabes, vuelve y reanuda: seguirás justo donde lo dejaste.")
        r.button("Reanudar", if (study) C.PRIMARY else C.OLIVE, big = true) {
            Engine.resume(this@SessionActivity); builtFor = ""
        }.also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(20) }
        val pausedMin = ((System.currentTimeMillis() - s.pausedAt) / 60000).toInt()
        if (pausedMin >= 1) r.muted("En pausa desde hace $pausedMin min.")
        r.divider()
        r.textButton("Llamada de emergencia (112)", C.MUTED) {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; setPadding(0, paddingTop, 0, paddingBottom) }
        val exit = r.textButton("Terminar la sesión (mantén pulsado)", C.MUTED) {
            Toast.makeText(this, "Mantén pulsado para terminar", Toast.LENGTH_SHORT).show()
        }
        exit.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL; setPadding(0, paddingTop, 0, paddingBottom) }
        exit.setOnLongClickListener { confirmExit(); true }
        sc.show()
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
            val pause = textButton("Pausar", C.NIGHT_TEXT) { Engine.pause(this@SessionActivity); builtFor = "" }
            pause.textSize = 14f
            pause.layoutParams = LinearLayout.LayoutParams(-2, -2)
            removeView(pause); addView(pause, childCount - 1)
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
        lateinit var ownRow: android.widget.LinearLayout
        ChoiceGroup(r, Ambient.names.indices.map { it to Ambient.names[it] }, s.ambient, columns = 3, dark = true) { k ->
            s.ambient = k
            ownRow.visibility = if (k == Ambient.OWN) View.VISIBLE else View.GONE
            if (k == Ambient.OWN && s.ownMusic.isEmpty()) { Ambient.stop(); playing = -1; Music.pick(this) }
            else { Ambient.play(this, k); playing = k }
        }
        ownRow = r.row {
            text(Music.summary(this@SessionActivity), 13f, C.NIGHT_MUTED).layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f)
            textButton("Cambiar", C.NIGHT_TEXT) { Music.pick(this@SessionActivity) }.layoutParams = android.widget.LinearLayout.LayoutParams(-2, -2)
        }
        ownRow.visibility = if (s.ambient == Ambient.OWN) View.VISIBLE else View.GONE
        if (playing != s.ambient) { Ambient.play(this, s.ambient); playing = s.ambient }

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
        if (s.breakWaiting) {
            r.muted("El descanso está en pausa. Elige qué vas a hacer y empieza cuando estés lista.")
            r.button("Elegir ejercicio y empezar", C.OLIVE, big = true) {
                startActivity(Intent(this@SessionActivity, CallActivity::class.java).putExtra("type", type).putExtra("silent", true))
            }.also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(16) }
            r.outlineButton("Empezar descanso ya", C.OLIVE) { Engine.startBreak(this@SessionActivity); builtFor = "" }
        } else {
            r.muted(sub)
            r.button("Ver qué hacer", C.OLIVE, big = true) {
                startActivity(Intent(this@SessionActivity, CallActivity::class.java).putExtra("type", type).putExtra("silent", true))
            }.also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(16) }
        }

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
        if (!s.breakWaiting) r.outlineButton("Pausar el descanso", C.OLIVE) { Engine.pause(this@SessionActivity); builtFor = "" }
        r.divider()
        val exit = r.textButton("Terminar la sesión (mantén pulsado)", C.MUTED) {
            Toast.makeText(this, "Mantén pulsado para terminar", Toast.LENGTH_SHORT).show()
        }
        exit.setOnLongClickListener { confirmExit(); true }
        sc.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        val n = Music.onResult(this, requestCode, resultCode, data) ?: return
        if (n > 0 && s.ambient == Ambient.OWN) { Ambient.play(this, Ambient.OWN); playing = Ambient.OWN }
        builtFor = ""   // redibuja para actualizar el número de canciones
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
