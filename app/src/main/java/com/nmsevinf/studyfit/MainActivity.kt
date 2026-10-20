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
        r.text(hello, 15f, C.MUTED, top = 8)
        r.title("¿Qué estudiamos hoy?")

        // Permisos pendientes: aviso discreto, no alarmante
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            r.card(C.WARN_SOFT) {
                text("Falta configurar: " + missing.joinToString(", ").lowercase(), 15f, C.TEXT)
                textButton("Configurar", C.PRIMARY) {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }.apply { gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL; setPadding(0, paddingTop, 0, paddingBottom) }
            }
        }

        // Acción principal
        if (s.inSession) {
            r.card(C.NIGHT) {
                val what = if (s.phase == Phase.STUDY) "Estudiando" else "En descanso"
                text("$what, ${s.technique.name}", 16f, C.NIGHT_TEXT, bold = true)
                text(when {
                    s.paused -> "En pausa: quedan ${fmtClock(s.leftMs())}"
                    s.breakWaiting -> "El descanso espera a que lo empieces"
                    else -> "Quedan ${fmtClock(s.leftMs())} en este bloque"
                }, 14f, C.NIGHT_MUTED)
                button("Volver a la sesión", C.NIGHT_TEXT, C.NIGHT, big = true) {
                    startActivity(Intent(this@MainActivity, SessionActivity::class.java))
                }.also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(12) }
                textButton("Cancelar sesión", C.NIGHT_MUTED) {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("¿Cancelar la sesión?")
                        .setMessage("Se termina ahora y se guarda lo que llevas estudiado como sesión interrumpida.")
                        .setPositiveButton("Cancelar sesión") { _, _ ->
                            Engine.finish(this@MainActivity, completed = false)
                            Ambient.stop()
                            RingService.stop(this@MainActivity)
                            build(); askMood()
                        }
                        .setNegativeButton("Seguir", null).show()
                }
            }
        } else {
            if (Engine.isLate(this)) {
                r.card(C.SURFACE) {
                    text("Ya es tu hora de dormir", 16f, C.TEXT, bold = true)
                    muted("Dormir fija lo que has estudiado. Si es urgente, haz un bloque corto y a la cama.")
                }
            }
            r.button("Empezar a estudiar", big = true) {
                startActivity(Intent(this, StartActivity::class.java))
            }.also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(20) }
        }

        // Hoy: tres cifras, sin adornos
        val logs = s.sessions()
        val today0 = Store.startOfDay(System.currentTimeMillis())
        val todayMin = logs.filter { it.start >= today0 }.sumOf { it.minutes }
        r.h2("Hoy")
        r.row {
            stat(this, fmtMin(todayMin), "estudiado")
            stat(this, "${streak(logs)}", if (streak(logs) == 1) "día seguido" else "días seguidos")
            stat(this, "${s.waterToday()}/${s.waterGoal}", "vasos de agua")
        }
        r.row {
            button("+1 vaso de agua", C.WATER_SOFT, C.WATER, weight = 1f) { s.addWater(1); build() }
            button("−1", C.SURFACE, C.MUTED) { s.addWater(-1); build() }.layoutParams =
                LinearLayout.LayoutParams(dp(64), -2).apply { leftMargin = dp(3) }
        }

        // Plan
        val now = System.currentTimeMillis()
        val nextExam = s.exams().firstOrNull { it.date >= today0 }
        val due = s.topics().count { it.nextDue <= now }
        r.h2("Plan")
        r.card {
            if (nextExam != null) {
                val days = ((nextExam.date - today0) / Store.DAY).toInt()
                text(PlannerActivity.TYPES[nextExam.type.coerceIn(0, 2)], 13f, PlannerActivity.typeColor(nextExam.type))
                text(nextExam.subject + ": " + when (days) { 0 -> "hoy"; 1 -> "mañana"; else -> "en $days días" }, 16f, C.TEXT, bold = true)
                if (nextExam.type == 0) muted("Te recomiendo ${PlannerActivity.recommendedMinutes(days)} de estudio hoy.")
            } else muted("No tienes exámenes apuntados.")
            text(if (due > 0) "$due tema(s) para repasar hoy" else "Nada que repasar hoy", 15f, C.TEXT, top = 8)
            textButton("Abrir el plan y el calendario", C.PRIMARY) { startActivity(Intent(this@MainActivity, PlannerActivity::class.java)) }
                .apply { gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL; setPadding(0, paddingTop, 0, paddingBottom) }
        }

        // Estrés alto en las últimas sesiones
        val recent = logs.takeLast(3).filter { it.stress > 0 }
        if (recent.isNotEmpty() && recent.map { it.stress }.average() >= 4) {
            r.card(C.OLIVE_SOFT) {
                text("Has tenido sesiones con estrés", 16f, C.TEXT, bold = true)
                body(Tips.stress.random())
            }
        }

        r.divider()
        r.row {
            outlineButton("Estadísticas", C.TEXT, weight = 1f) { startActivity(Intent(this@MainActivity, StatsActivity::class.java)) }
            outlineButton("Ajustes", C.TEXT, weight = 1f) { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        sc.show()
    }

    private fun stat(parent: LinearLayout, big: String, small: String) {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.text(big, 22f, C.TEXT, top = 0, bottom = 0).typeface = C.LIGHT
        col.text(small, 13f, C.MUTED, top = 0)
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
        box.body(if (s.pendingCompleted) "Sesión completada: ${fmtMin(mins)} de estudio." else "Sesión interrumpida (${fmtMin(mins)} estudiados).")
        box.h2("¿Qué tal tu concentración?")
        box.muted("1 = muy baja, 5 = muy alta")
        val focus = ChoiceGroup(box, (1..5).map { it to "$it" }, 3, columns = 5)
        box.h2("¿Y tu nivel de estrés?")
        box.muted("1 = nada, 5 = mucho")
        val stress = ChoiceGroup(box, (1..5).map { it to "$it" }, 2, columns = 5)
        AlertDialog.Builder(this)
            .setTitle("¿Cómo ha ido?")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("Guardar") { _, _ ->
                val st = stress.selected ?: 0
                Engine.saveSession(this, focus.selected ?: 0, st)
                if (st >= 4) {
                    AlertDialog.Builder(this).setTitle("Un momento para ti")
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
