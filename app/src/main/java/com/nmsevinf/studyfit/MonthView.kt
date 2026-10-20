package com.nmsevinf.studyfit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import java.util.Calendar

/**
 * Calendario mensual dibujado a mano: número de cada día y puntitos de colores
 * con lo que hay ese día (examen, entrega, otro, repaso). Al tocar un día se selecciona.
 */
class MonthView(ctx: Context, var onPick: (Long) -> Unit) : View(ctx) {

    var year = 0; var month = 0               // month: 0 = enero
    var selected: Long = Store.startOfDay(System.currentTimeMillis())
    /** día de inicio (ms) → colores de los puntos */
    var marks: Map<Long, List<Int>> = emptyMap()

    private val names = listOf("L", "M", "X", "J", "V", "S", "D")
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = C.REGULAR }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        val c = Calendar.getInstance()
        year = c.get(Calendar.YEAR); month = c.get(Calendar.MONTH)
        isClickable = true
    }

    private val headerH get() = context.dpf(28f)
    private val rowH get() = context.dpf(48f)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (headerH + 6 * rowH).toInt())
    }

    private fun firstCell(): Calendar = Calendar.getInstance().apply {
        clear(); set(year, month, 1)
        // lunes = primera columna
        val shift = (get(Calendar.DAY_OF_WEEK) + 5) % 7
        add(Calendar.DAY_OF_MONTH, -shift)
    }

    override fun onDraw(c: Canvas) {
        val cw = width / 7f
        txt.textSize = context.dpf(12f); txt.color = C.MUTED
        names.forEachIndexed { i, n -> c.drawText(n, cw * i + cw / 2, headerH * 0.7f, txt) }
        val day = firstCell()
        val today = Store.startOfDay(System.currentTimeMillis())
        ring.strokeWidth = context.dpf(1.5f); ring.color = C.PRIMARY
        for (cell in 0 until 42) {
            val col = cell % 7; val row = cell / 7
            val cx = cw * col + cw / 2; val cy = headerH + row * rowH + rowH * 0.42f
            val t0 = Store.startOfDay(day.timeInMillis)
            val inMonth = day.get(Calendar.MONTH) == month
            val r = context.dpf(15f)
            when {
                t0 == selected -> { fill.color = C.TEXT; c.drawCircle(cx, cy, r, fill) }
                t0 == today -> c.drawCircle(cx, cy, r, ring)
            }
            txt.textSize = context.dpf(15f)
            txt.color = when { t0 == selected -> C.BG; !inMonth -> C.LINE; else -> C.TEXT }
            c.drawText(day.get(Calendar.DAY_OF_MONTH).toString(), cx, cy + context.dpf(5f), txt)
            // puntos
            val dots = marks[t0].orEmpty().take(3)
            val dr = context.dpf(2.6f); val gap = context.dpf(7f)
            dots.forEachIndexed { i, color ->
                fill.color = color; fill.alpha = if (inMonth) 255 else 90
                c.drawCircle(cx + (i - (dots.size - 1) / 2f) * gap, cy + r + context.dpf(5f), dr, fill)
            }
            fill.alpha = 255
            day.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP && e.y > headerH) {
            val col = (e.x / (width / 7f)).toInt().coerceIn(0, 6)
            val row = ((e.y - headerH) / rowH).toInt().coerceIn(0, 5)
            val d = firstCell().apply { add(Calendar.DAY_OF_MONTH, row * 7 + col) }
            selected = Store.startOfDay(d.timeInMillis)
            if (d.get(Calendar.MONTH) != month) { year = d.get(Calendar.YEAR); month = d.get(Calendar.MONTH) }
            invalidate(); onPick(selected); performClick()
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
