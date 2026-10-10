package com.nmsevinf.studyfit

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Pequeño kit de interfaz hecho a mano (sin librerías externas → APK ligera y offline). */
object C {
    val PRIMARY = Color.parseColor("#1F7A6B")
    val PRIMARY_DARK = Color.parseColor("#14594E")
    val ACCENT = Color.parseColor("#F2994A")
    val BG = Color.parseColor("#F4F6F5")
    val CARD = Color.WHITE
    val TEXT = Color.parseColor("#1D2624")
    val MUTED = Color.parseColor("#5E6B68")
    val SOFT = Color.parseColor("#E3F1EE")
    val DANGER = Color.parseColor("#C0392B")
    val BLUE = Color.parseColor("#2D6CDF")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) =
    GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

/** Constructor de pantallas: columna con scroll. */
class Screen(val ctx: Activity, val dark: Boolean = false) {
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = ctx.dp(16)
        setPadding(p, p, p, ctx.dp(32))
    }
    val scroll = ScrollView(ctx).apply {
        isFillViewport = true
        setBackgroundColor(if (dark) C.PRIMARY_DARK else C.BG)
        addView(root)
    }

    fun show() { ctx.setContentView(scroll) }
}

fun LinearLayout.title(text: String, color: Int = C.TEXT): TextView = text(text, 26f, color, bold = true, bottom = 4)
fun LinearLayout.h2(text: String, color: Int = C.TEXT): TextView = text(text, 19f, color, bold = true, top = 14, bottom = 6)
fun LinearLayout.body(text: String, color: Int = C.TEXT): TextView = text(text, 16f, color)
fun LinearLayout.muted(text: String, color: Int = C.MUTED): TextView = text(text, 14f, color)

fun LinearLayout.text(
    s: String, size: Float, color: Int, bold: Boolean = false,
    top: Int = 2, bottom: Int = 2, center: Boolean = false
): TextView {
    val tv = TextView(context).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.15f)
        if (center) gravity = Gravity.CENTER_HORIZONTAL
    }
    addView(tv, LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = context.dp(top); bottomMargin = context.dp(bottom)
    })
    return tv
}

fun LinearLayout.card(bg: Int = C.CARD, build: LinearLayout.() -> Unit): LinearLayout {
    val c = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val p = context.dp(14)
        setPadding(p, p, p, p)
        background = rounded(bg, context.dpf(16f))
        elevation = context.dpf(1.5f)
    }
    c.build()
    addView(c, LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = context.dp(6); bottomMargin = context.dp(6)
    })
    return c
}

fun LinearLayout.row(build: LinearLayout.() -> Unit): LinearLayout {
    val r = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    r.build()
    addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(4) })
    return r
}

fun LinearLayout.button(
    label: String, color: Int = C.PRIMARY, textColor: Int = Color.WHITE,
    weight: Float = 0f, big: Boolean = false, onClick: (View) -> Unit
): Button {
    val b = Button(context).apply {
        text = label; isAllCaps = false
        textSize = if (big) 19f else 15f
        setTextColor(textColor)
        typeface = Typeface.DEFAULT_BOLD
        background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#33000000")),
            rounded(color, context.dpf(14f)), null
        )
        stateListAnimator = null
        val v = context.dp(if (big) 16 else 10)
        setPadding(context.dp(12), v, context.dp(12), v)
        setOnClickListener(onClick)
    }
    val lp = if (weight > 0f) LinearLayout.LayoutParams(0, -2, weight) else LinearLayout.LayoutParams(-1, -2)
    lp.topMargin = context.dp(4); lp.bottomMargin = context.dp(4)
    if (weight > 0f) { lp.leftMargin = context.dp(3); lp.rightMargin = context.dp(3) }
    addView(b, lp)
    return b
}

fun LinearLayout.outlineButton(label: String, color: Int = C.PRIMARY, weight: Float = 0f, onClick: (View) -> Unit): Button {
    val b = button(label, Color.TRANSPARENT, color, weight, onClick = onClick)
    b.background = RippleDrawable(
        ColorStateList.valueOf(Color.parseColor("#22000000")),
        rounded(Color.TRANSPARENT, context.dpf(14f), context.dp(2), color), null
    )
    return b
}

/** Botón-opción seleccionable (chips). */
class ChoiceGroup<T>(val parent: LinearLayout, val options: List<Pair<T, String>>, initial: T?, val columns: Int = 2, val onPick: (T) -> Unit = {}) {
    var selected: T? = initial
    private val buttons = mutableListOf<Pair<T, Button>>()

    init {
        options.chunked(columns).forEach { chunk ->
            parent.row {
                chunk.forEach { (value, label) ->
                    val b = button(label, weight = 1f) { pick(value) }
                    buttons += value to b
                }
                repeat(columns - chunk.size) {
                    addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                }
            }
        }
        paint()
    }

    fun pick(v: T) { selected = v; paint(); onPick(v) }

    private fun paint() {
        val ctx = parent.context
        buttons.forEach { (v, b) ->
            val on = v == selected
            b.setTextColor(if (on) Color.WHITE else C.PRIMARY)
            b.background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#22000000")),
                rounded(if (on) C.PRIMARY else C.SOFT, ctx.dpf(14f)), null
            )
        }
    }
}

fun LinearLayout.input(hint: String, value: String = "", number: Boolean = false): EditText {
    val e = EditText(context).apply {
        this.hint = hint; setText(value); textSize = 16f
        setTextColor(C.TEXT); setHintTextColor(C.MUTED)
        background = rounded(Color.WHITE, context.dpf(12f), context.dp(1), Color.parseColor("#CFD8D6"))
        val p = context.dp(12); setPadding(p, p, p, p)
        if (number) inputType = InputType.TYPE_CLASS_NUMBER
    }
    addView(e, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(4); bottomMargin = context.dp(4) })
    return e
}

fun LinearLayout.divider() {
    addView(View(context).apply { setBackgroundColor(Color.parseColor("#E1E7E5")) },
        LinearLayout.LayoutParams(-1, context.dp(1)).apply { topMargin = context.dp(8); bottomMargin = context.dp(8) })
}

fun View.lp(w: Int, h: Int): View { layoutParams = ViewGroup.LayoutParams(w, h); return this }

fun fmtMin(totalMin: Int): String =
    if (totalMin < 60) "$totalMin min" else "${totalMin / 60} h ${"%02d".format(totalMin % 60)} min"

fun fmtClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}
