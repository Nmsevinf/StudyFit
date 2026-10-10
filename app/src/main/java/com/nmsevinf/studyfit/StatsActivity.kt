package com.nmsevinf.studyfit

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StatsActivity : Activity() {

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val s = Store(this)
        val logs = s.sessions()
        val sc = Screen(this)
        val r = sc.root
        r.title("Estadísticas")

        val now = System.currentTimeMillis()
        val today0 = Store.startOfDay(now)
        val week0 = today0 - 6 * Store.DAY
        val month0 = today0 - 29 * Store.DAY
        fun sum(from: Long) = logs.filter { it.start >= from }.sumOf { it.minutes }

        r.card {
            text("Tiempo de estudio", 17f, C.TEXT, bold = true)
            body("Hoy: ${fmtMin(sum(today0))}")
            body("Últimos 7 días: ${fmtMin(sum(week0))}")
            body("Últimos 30 días: ${fmtMin(sum(month0))}")
            body("Racha: ${MainActivity.streak(logs)} días seguidos 🔥")
            val done = logs.count { it.completed }
            muted("Sesiones completas: $done · interrumpidas: ${logs.size - done}")
        }

        // Gráfico de barras de los últimos 7 días
        val days = (6 downTo 0).map { back ->
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) }
            val key = Store.dayKey(c.timeInMillis)
            val label = SimpleDateFormat("EEE", Locale("es", "ES")).format(c.time).take(2)
            label to logs.filter { Store.dayKey(it.start) == key }.sumOf { it.minutes }
        }
        r.card {
            text("Últimos 7 días", 17f, C.TEXT, bold = true)
            addView(Bars(this@StatsActivity, days), LinearLayout.LayoutParams(-1, dp(180)))
        }

        // Qué técnica te funciona mejor
        r.card {
            text("Tus técnicas", 17f, C.TEXT, bold = true)
            val byTech = logs.groupBy { it.tech }
            if (byTech.isEmpty()) muted("Aún no hay sesiones. ¡Empieza una!")
            val rated = byTech.mapValues { (_, l) -> l.filter { it.focus > 0 }.map { it.focus }.average() }
                .filterValues { !it.isNaN() }
            val best = rated.maxByOrNull { it.value }
            byTech.entries.sortedByDescending { e -> e.value.sumOf { it.minutes } }.forEach { (id, l) ->
                val f = rated[id]
                body("• ${Techniques.byId(id).name}: ${l.size} sesiones, ${fmtMin(l.sumOf { it.minutes })}" +
                    (if (f != null) " · concentración ${"%.1f".format(f)}/5" else ""))
            }
            if (best != null && rated.size >= 2)
                text("⭐ La que mejor te funciona: ${Techniques.byId(best.key).name}", 15f, C.PRIMARY, bold = true, top = 8)
        }

        // Por asignatura
        val bySubj = logs.filter { it.subject.isNotBlank() }.groupBy { it.subject.trim().lowercase() }
        if (bySubj.isNotEmpty()) r.card {
            text("Por asignatura", 17f, C.TEXT, bold = true)
            bySubj.entries.sortedByDescending { e -> e.value.sumOf { it.minutes } }.take(10).forEach { (_, l) ->
                body("• ${l.first().subject}: ${fmtMin(l.sumOf { it.minutes })}")
            }
        }

        // Estrés
        val st = logs.filter { it.stress > 0 }
        if (st.isNotEmpty()) r.card {
            text("Estrés y ánimo", 17f, C.TEXT, bold = true)
            val last = st.takeLast(5).map { it.stress }.average()
            val prev = st.dropLast(5).takeLast(5).map { it.stress }.average()
            body("Estrés medio reciente: ${"%.1f".format(last)}/5")
            if (!prev.isNaN()) muted(if (last < prev) "Va bajando 👍" else if (last > prev) "Ha subido: cuida descansos y sueño." else "Estable.")
            if (last >= 4) body(Tips.stress.random())
        }
        sc.show()
    }

    private class Bars(ctx: Context, val data: List<Pair<String, Int>>) : View(ctx) {
        private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.PRIMARY }
        private val today = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.ACCENT }
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = C.MUTED; textSize = ctx.dpf(12f); textAlign = Paint.Align.CENTER
        }
        override fun onDraw(c: Canvas) {
            val max = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(30)
            val w = width / data.size.toFloat()
            val top = context.dpf(18f); val bottom = height - context.dpf(20f)
            data.forEachIndexed { i, (label, m) ->
                val x = i * w
                val hgt = (bottom - top) * m / max
                val rect = RectF(x + w * 0.2f, bottom - hgt, x + w * 0.8f, bottom)
                c.drawRoundRect(rect, context.dpf(6f), context.dpf(6f), if (i == data.size - 1) today else bar)
                c.drawText(label, x + w / 2, height - context.dpf(4f), txt)
                if (m > 0) c.drawText(if (m >= 60) "${m / 60}h${m % 60}" else "$m'", x + w / 2, bottom - hgt - context.dpf(4f), txt)
            }
        }
    }
}
