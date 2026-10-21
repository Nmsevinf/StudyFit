package com.nmsevinf.studyfit

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Plan: calendario mensual con todo marcado, exámenes/entregas editables
 * y temas para repetición espaciada (1, 3, 7, 14, 30 días).
 */
class PlannerActivity : Activity() {

    private lateinit var s: Store
    private val es = Locale("es", "ES")
    private val df = SimpleDateFormat("EEE d MMM", es)
    private val dayTitle = SimpleDateFormat("EEEE d 'de' MMMM", es)
    private val monthTitle = SimpleDateFormat("MMMM yyyy", es)
    private val hm = SimpleDateFormat("HH:mm", es)
    private var selected = Store.startOfDay(System.currentTimeMillis())
    private var showPast = false
    private var calYear = -1; private var calMonth = -1

    override fun onCreate(b: Bundle?) { super.onCreate(b); s = Store(this) }
    override fun onResume() {
        super.onResume(); build()
        // Viene de "De clase" → abrir el formulario ya relleno
        intent.getStringExtra("preTitle")?.let { t ->
            val pre = Exam(-1, t, intent.getLongExtra("preDate", System.currentTimeMillis()), intent.getIntExtra("preType", 0), intent.getStringExtra("preNote") ?: "")
            intent.removeExtra("preTitle")
            editExam(null, pre.date, pre)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == ExternalCal.REQ_PERMISSION) build()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ExternalCal.REQ_ICS && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val n = ExternalCal.importIcs(this, uri)
            Toast.makeText(this, when {
                n > 0 -> "Importados $n eventos"
                n == 0 -> "El archivo no tiene eventos"
                else -> "No se ha podido leer el archivo .ics"
            }, Toast.LENGTH_LONG).show()
            build()
        }
    }

    /** Primer día de la rejilla (lunes) del mes mostrado. */
    private fun gridStart(): Long = Calendar.getInstance().apply {
        clear(); set(calYear, calMonth, 1)
        add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
    }.timeInMillis

    private fun build() {
        val sc = Screen(this)
        val r = sc.root
        r.title("Plan")
        val today0 = Store.startOfDay(System.currentTimeMillis())
        val exams = s.exams()
        val topics = s.topics()

        // ----- Calendario -----
        val cal = MonthView(this) { day -> selected = day; build() }
        if (calYear < 0) { val c = Calendar.getInstance().apply { timeInMillis = selected }; calYear = c.get(Calendar.YEAR); calMonth = c.get(Calendar.MONTH) }
        cal.year = calYear; cal.month = calMonth; cal.selected = selected
        cal.onPick = { day ->
            selected = day; calYear = cal.year; calMonth = cal.month; build()
        }
        val g0 = gridStart(); val g1 = g0 + 42 * Store.DAY
        val ext = ExternalCal.events(this, g0, g1)
        val marks = HashMap<Long, MutableList<Int>>()
        exams.forEach { marks.getOrPut(Store.startOfDay(it.date)) { mutableListOf() } += typeColor(it.type) }
        topics.forEach { t -> marks.getOrPut(Store.startOfDay(t.nextDue).coerceAtLeast(today0)) { mutableListOf() } += REVIEW_COLOR }
        ext.forEach { e ->
            var d = Store.startOfDay(e.begin)
            while (d <= e.end && d < g1) {
                val col = when (e.kind) { 0 -> typeColor(0); 1 -> typeColor(1); else -> e.color }
                marks.getOrPut(d) { mutableListOf() }.let { if (col !in it) it += col }
                d = Store.startOfDay(d + Store.DAY + 3_600_000)
            }
        }
        cal.marks = marks

        // Invitación a unir calendarios (una vez)
        if (!ExternalCal.hasPermission(this) && ExternalCal.icsSources(this).isEmpty() && !s.calHintHidden) {
            r.card(C.SURFACE) {
                text("Une tus calendarios", 16f, C.TEXT, bold = true)
                muted("Ve aquí tus clases, exámenes y reuniones de Google (Gmail), Outlook/Teams y el horario de la UPV.")
                button("Conectar calendarios del móvil") { ExternalCal.requestPermission(this@PlannerActivity) }
                outlineButton("Importar archivo .ics (horario UPV)", C.PRIMARY) { ExternalCal.pickIcs(this@PlannerActivity) }
                textButton("Ahora no", C.MUTED) { s.calHintHidden = true; build() }
            }
        }

        r.card(C.BG) {
            row {
                textButton("‹", C.TEXT) { moveMonth(-1) }.layoutParams = LinearLayout.LayoutParams(dp(48), -2)
                text(monthTitle.format(Calendar.getInstance().apply { clear(); set(calYear, calMonth, 1) }.time)
                    .replaceFirstChar { it.uppercase() }, 17f, C.TEXT, bold = true, center = true)
                    .layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                textButton("›", C.TEXT) { moveMonth(1) }.layoutParams = LinearLayout.LayoutParams(dp(48), -2)
            }
            addView(cal, LinearLayout.LayoutParams(-1, -2))
            // leyenda
            row {
                listOf("Examen" to typeColor(0), "Entrega" to typeColor(1), "Otro" to typeColor(2), "Repaso" to REVIEW_COLOR).forEach { (n, col) ->
                    addView(TextView(context).apply {
                        text = "●  $n"; textSize = 12f; setTextColor(C.MUTED)
                        val sp = android.text.SpannableString(text)
                        sp.setSpan(android.text.style.ForegroundColorSpan(col), 0, 1, 0)
                        text = sp; gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                }
            }
        }.also { it.background = rounded(C.BG, dpf(20f), dp(1), C.LINE) }

        // ----- Día seleccionado -----
        r.h2(dayTitle.format(selected).replaceFirstChar { it.uppercase() })
        val dayExams = exams.filter { Store.startOfDay(it.date) == selected }
        val dayTopics = topics.filter { Store.startOfDay(it.nextDue).coerceAtLeast(today0) == selected }
        val dayEnd = selected + Store.DAY
        val dayExt = ext.filter { it.begin < dayEnd && it.end >= selected }
        if (dayExams.isEmpty() && dayTopics.isEmpty() && dayExt.isEmpty()) r.muted("No tienes nada este día.")
        dayExams.forEach { examCard(r, it, today0) }
        dayTopics.forEach { t ->
            r.card(C.SURFACE) {
                text("Repaso: ${t.name}", 16f, C.TEXT, bold = true)
                if (t.subject.isNotBlank()) muted(t.subject)
                textButton("Editar", C.PRIMARY) { editTopic(t) }.leftAligned()
            }
        }
        dayExt.forEach { e -> extCard(r, e, exams) }
        r.button("+ Añadir en este día") { editExam(null, selected) }

        // ----- Próximos -----
        val upcoming = exams.filter { it.date >= today0 && Store.startOfDay(it.date) != selected }
        if (upcoming.isNotEmpty()) {
            r.h2("Próximos")
            upcoming.forEach { examCard(r, it, today0) }
        }
        val past = exams.filter { it.date < today0 }
        if (past.isNotEmpty()) {
            r.textButton(if (showPast) "Ocultar pasados" else "Ver pasados (${past.size})", C.MUTED) { showPast = !showPast; build() }.leftAligned()
            if (showPast) past.sortedByDescending { it.date }.forEach { examCard(r, it, today0) }
        }

        // ----- Repasos -----
        r.h2("Repasos")
        r.muted("Cuando aprendas un tema, añádelo aquí. Te lo pediré repasar a los 1, 3, 7, 14 y 30 días.")
        val now = System.currentTimeMillis()
        val due = topics.filter { it.nextDue <= now }
        val later = topics.filter { it.nextDue > now }
        if (due.isEmpty()) r.card { body("Nada que repasar hoy.") }
        due.forEach { t ->
            r.card(C.OLIVE_SOFT) {
                row {
                    text(t.name, 17f, C.TEXT, bold = true).layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    textButton("Editar", C.PRIMARY) { editTopic(t) }.layoutParams = LinearLayout.LayoutParams(-2, -2)
                }
                if (t.subject.isNotBlank()) muted(t.subject)
                muted("Repaso ${t.stage + 1}. Primero intenta recordarlo sin mirar.")
                row {
                    button("Lo recordaba", C.OLIVE, weight = 1f) { s.reviewTopic(t.id, true); build() }
                    outlineButton("Lo olvidé", C.OLIVE, weight = 1f) { s.reviewTopic(t.id, false); build() }
                }
            }
        }
        if (later.isNotEmpty()) {
            r.text("Próximos repasos", 15f, C.TEXT, bold = true, top = 10)
            later.take(30).forEach { t ->
                r.textButton("${t.name}, ${df.format(t.nextDue)}", C.TEXT) { editTopic(t) }.leftAligned()
            }
            r.muted("Toca un tema para editarlo.")
        }
        r.button("+ Añadir tema aprendido") { addTopic() }
        sc.show()
    }

    /** Evento de otro calendario: hora, título, origen. Si parece examen o entrega, se puede pasar al plan. */
    private fun extCard(r: LinearLayout, e: ExtEvent, exams: List<Exam>) {
        r.card(C.BG) {
            row {
                addView(android.view.View(context).apply { background = rounded(e.color, dpf(3f)) },
                    LinearLayout.LayoutParams(dp(5), dp(38)).apply { rightMargin = dp(12) })
                val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                col.text(if (e.allDay) "Todo el día" else "${hm.format(e.begin)}–${hm.format(e.end)}", 13f, C.MUTED, top = 0, bottom = 0)
                col.text(e.title, 16f, C.TEXT, bold = true, top = 0, bottom = 0)
                col.text(listOf(e.source, e.location).filter { it.isNotBlank() }.joinToString(", "), 13f, C.MUTED, top = 0, bottom = 0)
                addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            }
            val k = e.kind
            val already = exams.any { Store.startOfDay(it.date) == Store.startOfDay(e.begin) && it.subject.equals(e.title, true) }
            if ((k == 0 || k == 1) && !already) {
                textButton("Añadir a mi plan como ${TYPES[k].lowercase()}", C.PRIMARY) {
                    s.saveExam(null, e.title, Store.startOfDay(e.begin), k, listOf(e.source, e.location).filter { it.isNotBlank() }.joinToString(", "))
                    build()
                }.leftAligned()
            }
        }.also { it.background = rounded(C.BG, dpf(20f), dp(1), C.LINE) }
    }

    private fun moveMonth(d: Int) {
        val c = Calendar.getInstance().apply { clear(); set(calYear, calMonth, 1); add(Calendar.MONTH, d) }
        calYear = c.get(Calendar.YEAR); calMonth = c.get(Calendar.MONTH); build()
    }

    private fun examCard(r: LinearLayout, e: Exam, today0: Long) {
        val days = ((Store.startOfDay(e.date) - today0) / Store.DAY).toInt()
        val bg = when { days < 0 -> C.SURFACE; days <= 7 -> C.PRIMARY_SOFT; else -> C.SURFACE }
        r.card(bg) {
            text(TYPES[e.type.coerceIn(0, 2)], 13f, typeColor(e.type))
            text(e.subject, 17f, if (days < 0) C.MUTED else C.TEXT, bold = true)
            body("${df.format(e.date)}, " + when {
                days < 0 -> "ya pasó"
                days == 0 -> if (e.type == 0) "es hoy. Mucha suerte" else "es hoy"
                days == 1 -> "mañana"
                else -> "faltan $days días"
            })
            if (e.note.isNotBlank()) muted(e.note)
            if (days >= 0 && e.type == 0) muted("Recomendado: ${recommendedMinutes(days)} al día. " + advice(days))
            row {
                textButton("Editar", C.PRIMARY) { editExam(e, e.date) }.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).also { }
                textButton("Borrar", C.DANGER) {
                    confirm("¿Borrar «${e.subject}»?") { s.removeExam(e.id); build() }
                }.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
        }
    }

    /** Crear (e = null) o editar un examen/entrega. */
    private fun editExam(e: Exam?, day: Long, pre: Exam? = null) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val name = box.input("Asignatura o título (p. ej. Bases de Datos)", e?.subject ?: pre?.subject ?: "")
        box.text("Tipo", 14f, C.MUTED, top = 8)
        val type = ChoiceGroup(box, TYPES.indices.map { it to TYPES[it] }, e?.type ?: pre?.type ?: 0, columns = 3)
        var date = Store.startOfDay(e?.date ?: day)
        box.text("Fecha", 14f, C.MUTED, top = 8)
        lateinit var dateBtn: android.widget.Button
        dateBtn = box.outlineButton(df.format(date), C.TEXT) {
            val c = Calendar.getInstance().apply { timeInMillis = date }
            DatePickerDialog(this, { _, y, m, d ->
                date = Store.startOfDay(Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }.timeInMillis)
                dateBtn.text = df.format(date)
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
        val note = box.input("Nota (opcional): aula, temas, hora…", e?.note ?: pre?.note ?: "")
        val dlg = AlertDialog.Builder(this).setTitle(if (e == null) "Nuevo" else "Editar").setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val subj = name.text.toString().trim().ifEmpty { TYPES[type.selected ?: 0] }
                s.saveExam(e?.id, subj, date, type.selected ?: 0, note.text.toString().trim())
                selected = date
                val c = Calendar.getInstance().apply { timeInMillis = date }; calYear = c.get(Calendar.YEAR); calMonth = c.get(Calendar.MONTH)
                build()
            }
            .setNegativeButton("Cancelar", null)
        if (e != null) dlg.setNeutralButton("Borrar") { _, _ -> confirm("¿Borrar «${e.subject}»?") { s.removeExam(e.id); build() } }
        dlg.show()
    }

    private fun editTopic(t: Topic) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val name = box.input("Tema", t.name)
        val subj = box.input("Asignatura (opcional)", t.subject)
        var due = Store.startOfDay(t.nextDue)
        box.text("Próximo repaso", 14f, C.MUTED, top = 8)
        lateinit var dateBtn: android.widget.Button
        dateBtn = box.outlineButton(df.format(due), C.TEXT) {
            val c = Calendar.getInstance().apply { timeInMillis = due }
            DatePickerDialog(this, { _, y, m, d ->
                due = Store.startOfDay(Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }.timeInMillis)
                dateBtn.text = df.format(due)
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
        AlertDialog.Builder(this).setTitle("Editar tema").setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val n = name.text.toString().trim().ifEmpty { t.name }
                s.updateTopic(t.id, n, subj.text.toString().trim(), due); build()
            }
            .setNeutralButton("Borrar") { _, _ -> confirm("¿Borrar «${t.name}»?") { s.removeTopic(t.id); build() } }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun addTopic() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
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

    private fun android.widget.Button.leftAligned(): android.widget.Button {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL; setPadding(0, paddingTop, 0, paddingBottom); return this
    }

    companion object {
        val TYPES = listOf("Examen", "Entrega", "Otro")
        val REVIEW_COLOR: Int = Color.parseColor("#D4B188")
        fun typeColor(t: Int): Int = when (t) { 1 -> C.OLIVE; 2 -> C.WATER; else -> C.PRIMARY }

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
