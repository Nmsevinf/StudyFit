package com.nmsevinf.studyfit

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Planificador: exámenes con cuenta atrás y temas para repetición espaciada (1, 3, 7, 14, 30 días). */
class PlannerActivity : Activity() {

    private lateinit var s: Store
    private val df = SimpleDateFormat("EEE d MMM", Locale("es", "ES"))

    override fun onCreate(b: Bundle?) { super.onCreate(b); s = Store(this); build() }

    private fun build() {
        val sc = Screen(this)
        val r = sc.root
        r.title("Plan")

        // ----- Exámenes -----
        r.h2("Exámenes")
        val today0 = Store.startOfDay(System.currentTimeMillis())
        val exams = s.exams()
        if (exams.isEmpty()) r.muted("Añade tus exámenes y te digo cuánto estudiar cada día.")
        exams.forEach { e ->
            val days = ((e.date - today0) / Store.DAY).toInt()
            r.card(if (days in 0..7) C.PRIMARY_SOFT else C.SURFACE) {
                text(e.subject, 17f, C.TEXT, bold = true)
                body("${df.format(e.date)} · " + when {
                    days < 0 -> "ya pasó"
                    days == 0 -> "es hoy. Mucha suerte"
                    days == 1 -> "mañana"
                    else -> "faltan $days días"
                })
                if (days >= 0) muted("Recomendado: ${recommendedMinutes(days)} al día. " + advice(days))
                textButton("Borrar", C.DANGER) {
                    confirm("¿Borrar ${e.subject}?") { s.removeExam(e.id); build() }
                }
            }
        }
        r.button("+ Añadir examen") { addExam() }

        // ----- Repetición espaciada -----
        r.h2("Repasos")
        r.muted("Cuando aprendas un tema, añádelo aquí. Te lo pediré repasar a los 1, 3, 7, 14 y 30 días.")
        val now = System.currentTimeMillis()
        val topics = s.topics()
        val due = topics.filter { it.nextDue <= now }
        val later = topics.filter { it.nextDue > now }
        if (due.isEmpty()) r.card { body("Nada que repasar hoy.") }
        due.forEach { t ->
            r.card(C.OLIVE_SOFT) {
                text(t.name, 17f, C.TEXT, bold = true)
                if (t.subject.isNotBlank()) muted(t.subject)
                muted("Repaso nº ${t.stage + 1}. Primero intenta recordarlo sin mirar.")
                row {
                    button("Lo recordaba", C.OLIVE, weight = 1f) { s.reviewTopic(t.id, true); build() }
                    outlineButton("Lo olvidé", C.OLIVE, weight = 1f) { s.reviewTopic(t.id, false); build() }
                }
            }
        }
        if (later.isNotEmpty()) {
            r.text("Próximos repasos", 15f, C.TEXT, bold = true, top = 10)
            later.take(30).forEach { t ->
                r.row {
                    val tv = text("• ${t.name} — ${df.format(t.nextDue)}", 14f, C.MUTED)
                    tv.setOnLongClickListener { confirm("¿Borrar «${t.name}»?") { s.removeTopic(t.id); build() }; true }
                }
            }
            r.muted("(Mantén pulsado un tema para borrarlo)")
        }
        r.button("+ Añadir tema aprendido") { addTopic() }
        sc.show()
    }

    private fun addExam() {
        val box = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val name = box.input("Asignatura (p. ej. Bases de Datos)")
        AlertDialog.Builder(this).setTitle("Nuevo examen").setView(box)
            .setPositiveButton("Elegir fecha") { _, _ ->
                val subj = name.text.toString().trim().ifEmpty { "Examen" }
                val c = Calendar.getInstance()
                DatePickerDialog(this, { _, y, m, d ->
                    val cal = Calendar.getInstance().apply { set(y, m, d, 9, 0, 0); set(Calendar.MILLISECOND, 0) }
                    s.addExam(subj, Store.startOfDay(cal.timeInMillis)); build()
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
                    datePicker.minDate = System.currentTimeMillis() - 1000
                }.show()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun addTopic() {
        val box = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val name = box.input("Tema (p. ej. Normalización 3FN)")
        val subj = box.input("Asignatura (opcional)", s.subject)
        AlertDialog.Builder(this).setTitle("Tema aprendido hoy").setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isNotEmpty()) { s.addTopic(n, subj.text.toString().trim()); build() }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun confirm(msg: String, ok: () -> Unit) {
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton("Sí") { _, _ -> ok() }.setNegativeButton("No", null).show()
    }

    companion object {
        fun recommendedMinutes(days: Int): String = when {
            days <= 3 -> "3 h"
            days <= 7 -> "2 h"
            days <= 14 -> "1 h 30 min"
            days <= 30 -> "1 h"
            else -> "30 min"
        }

        fun advice(days: Int): String = when {
            days <= 1 -> "Solo repaso y recuerdo activo, nada nuevo. Duerme bien."
            days <= 7 -> "Haz exámenes antiguos y recuerdo activo."
            days <= 14 -> "Termina temario y empieza a hacer problemas."
            else -> "Avanza temario poco a poco y añade los temas a repasos."
        }
    }
}
