package com.nmsevinf.studyfit

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var s: Store

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        s = Store(this)
        Notifs.ensure(this)
        Engine.scheduleAll(this)
        handleStartIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStartIntent(intent)
    }

    /** Etiqueta NFC o enlace studyfit://start → arranque rápido. */
    private fun handleStartIntent(i: Intent?) {
        val data = i?.data ?: return
        if (data.scheme == "studyfit" && (i.action == NfcAdapter.ACTION_NDEF_DISCOVERED || i.action == Intent.ACTION_VIEW)) {
            setIntent(Intent(this, MainActivity::class.java))
            if (s.inSession) startActivity(Intent(this, SessionActivity::class.java))
            else startActivity(Intent(this, StartActivity::class.java).putExtra("quick", true))
        }
    }

    override fun onResume() {
        super.onResume()
        build()
        if (s.pendingMood) askMood()
    }

    private fun build() {
        val sc = Screen(this)
        val r = sc.root
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello = when (h) { in 6..13 -> "Buenos días"; in 14..20 -> "Buenas tardes"; else -> "Buenas noches" }
        r.text("$hello 👋", 15f, C.MUTED)
        r.title("StudyFit")
        r.muted("Estudia mejor, cuida tu cuerpo.")

        // Permisos pendientes
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            r.card(C.ACCENT) {
                text("⚠️ Falta configurar ${missing.size} permiso(s)", 16f, android.graphics.Color.WHITE, bold = true)
                text(missing.joinToString(" · "), 14f, android.graphics.Color.WHITE)
                button("Configurar ahora", android.graphics.Color.WHITE, C.ACCENT) {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
            }
        }

        // Botón principal
        if (s.inSession) {
            r.card(C.PRIMARY) {
                val what = if (s.phase == Phase.STUDY) "Estudiando" else "En descanso"
                text("$what · ${s.technique.name}", 17f, android.graphics.Color.WHITE, bold = true)
                text("Quedan ${fmtClock(s.phaseEnd - System.currentTimeMillis())} en este bloque", 14f, android.graphics.Color.WHITE)
                button("Volver a la sesión", android.graphics.Color.WHITE, C.PRIMARY, big = true) {
                    startActivity(Intent(this@MainActivity, SessionActivity::class.java))
                }
            }
        } else {
            if (Engine.isLate(this)) {
                r.card(C.SOFT) {
                    text("🌙 Ya es tu hora de dormir", 16f, C.TEXT, bold = true)
                    muted("Dormir consolida lo que has estudiado. Si es urgente, haz un bloque corto y a la cama.")
                }
            }
            r.button("▶  Empezar a estudiar", big = true) {
                startActivity(Intent(this, StartActivity::class.java))
            }
        }

        // Hoy
        val logs = s.sessions()
        val today0 = Store.startOfDay(System.currentTimeMillis())
        val todayMin = logs.filter { it.start >= today0 }.sumOf { it.minutes }
        val streak = streak(logs)
        r.card {
            text("Hoy", 17f, C.TEXT, bold = true)
            row {
                stat(this, fmtMin(todayMin), "estudiado")
                stat(this, "$streak 🔥", "días seguidos")
                stat(this, "${s.waterToday()}/${s.waterGoal}", "vasos de agua")
            }
            row {
                button("+1 vaso 💧", C.BLUE, weight = 1f) { s.addWater(1); build() }
                outlineButton("−1", C.BLUE, weight = 0.5f) { s.addWater(-1); build() }
            }
        }

        // Exámenes y repasos
        val now = System.currentTimeMillis()
        val nextExam = s.exams().firstOrNull { it.date >= today0 }
        val due = s.topics().count { it.nextDue <= now }
        r.card {
            text("Plan", 17f, C.TEXT, bold = true)
            if (nextExam != null) {
                val days = ((nextExam.date - today0) / Store.DAY).toInt()
                body("📅 ${nextExam.subject}: " + when (days) { 0 -> "¡HOY!"; 1 -> "mañana"; else -> "en $days días" })
                muted("Recomendado hoy: ${PlannerActivity.recommendedMinutes(days)} de estudio")
            } else muted("Sin exámenes apuntados.")
            body(if (due > 0) "🔁 $due tema(s) para repasar hoy" else "🔁 Nada que repasar hoy")
            button("Abrir planificador") { startActivity(Intent(this@MainActivity, PlannerActivity::class.java)) }
        }

        // Consejo si hay estrés alto en las últimas sesiones
        val recent = logs.takeLast(3).filter { it.stress > 0 }
        if (recent.isNotEmpty() && recent.map { it.stress }.average() >= 4) {
            r.card(C.SOFT) {
                text("💚 Te noto con estrés", 16f, C.TEXT, bold = true)
                body(Tips.stress.random())
            }
        }

        r.row {
            button("📊 Estadísticas", weight = 1f) { startActivity(Intent(this@MainActivity, StatsActivity::class.java)) }
            button("⚙️ Ajustes", C.MUTED, weight = 1f) { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        sc.show()
    }

    private fun stat(parent: LinearLayout, big: String, small: String) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.text(big, 18f, C.PRIMARY, bold = true, center = true)
        col.text(small, 12f, C.MUTED, center = true)
        parent.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun missingPermissions(): List<String> {
        val out = mutableListOf<String>()
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) out += "Notificaciones"
        if (!BlockerService.isEnabled(this)) out += "Bloqueo de apps"
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) out += "Pantalla completa"
        return out
    }

    private fun askMood() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val mins = (s.studiedMs / Engine.MIN * Engine.speed).toInt()
        box.body(if (s.pendingCompleted) "¡Sesión completada! Has estudiado ${fmtMin(mins)}. 🎉" else "Sesión interrumpida (${fmtMin(mins)} estudiados).")
        box.h2("¿Qué tal tu concentración?")
        val focus = ChoiceGroup(box, (1..5).map { it to listOf("😵", "😕", "😐", "🙂", "🤩")[it - 1] }, 3, columns = 5)
        box.h2("¿Nivel de estrés?")
        val stress = ChoiceGroup(box, (1..5).map { it to listOf("😌", "🙂", "😐", "😟", "😫")[it - 1] }, 2, columns = 5)
        AlertDialog.Builder(this)
            .setTitle("¿Cómo ha ido?")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("Guardar") { _, _ ->
                val st = stress.selected ?: 0
                Engine.saveSession(this, focus.selected ?: 0, st)
                if (st >= 4) {
                    AlertDialog.Builder(this).setTitle("💚 Un momento para ti")
                        .setMessage(Tips.stress.joinToString("\n\n"))
                        .setPositiveButton("Gracias", null).show()
                }
                build()
            }
            .setNegativeButton("Saltar") { _, _ -> Engine.saveSession(this, 0, 0); build() }
            .show()
    }

    companion object {
        fun streak(logs: List<SessionLog>): Int {
            val days = logs.filter { it.minutes > 0 }.map { Store.dayKey(it.start) }.toSet()
            val c = Calendar.getInstance()
            if (Store.dayKey(c.timeInMillis) !in days) c.add(Calendar.DAY_OF_YEAR, -1)
            var n = 0
            while (Store.dayKey(c.timeInMillis) in days) { n++; c.add(Calendar.DAY_OF_YEAR, -1) }
            return n
        }
    }
}
