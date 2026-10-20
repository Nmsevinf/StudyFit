package com.nmsevinf.studyfit

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Diseño "blanco y tierra": el color indica el modo.
 *  - Normal: blanco con arena y barro tostado.
 *  - Estudio: tierra profunda, texto hueso, nada brillante.
 *  - Descanso: blanco con oliva.
 */
object C {
    // Base clara
    val BG = Color.parseColor("#FFFFFF")
    val SURFACE = Color.parseColor("#F6F1EA")   // arena muy clara
    val LINE = Color.parseColor("#E7DDD0")
    val TEXT = Color.parseColor("#3A2E25")      // tierra oscura
    val MUTED = Color.parseColor("#8A7867")
    val PRIMARY = Color.parseColor("#8C5A3C")   // barro tostado
    val PRIMARY_SOFT = Color.parseColor("#F1E6DB")
    val OLIVE = Color.parseColor("#6F7448")     // descansos
    val OLIVE_SOFT = Color.parseColor("#ECEDE0")
    val WATER = Color.parseColor("#5E7C86")     // agua: azul apagado
    val WATER_SOFT = Color.parseColor("#E5ECEE")
    val WARN_SOFT = Color.parseColor("#F7E9DA")
    val DANGER = Color.parseColor("#A4483A")

    // Modo estudio
    val NIGHT = Color.parseColor("#2A221C")
    val NIGHT_SURFACE = Color.parseColor("#352B24")
    val NIGHT_TEXT = Color.parseColor("#EEE5D9")
    val NIGHT_MUTED = Color.parseColor("#A89A8A")
    val SAND = Color.parseColor("#D4B188")      // progreso

    // Alias usados en pantallas antiguas
    val CARD get() = SURFACE
    val SOFT get() = PRIMARY_SOFT
    val ACCENT get() = PRIMARY
    val BLUE get() = WATER
    val PRIMARY_DARK get() = NIGHT

    val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val REGULAR: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

/** ¿El usuario ha desactivado las animaciones del sistema? */
fun Context.reducedMotion(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) =
    GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

/** Constructor de pantallas: columna con scroll. */
class Screen(val ctx: Activity, val dark: Boolean = false, val bg: Int = if (dark) C.NIGHT else C.BG) {
    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dp(22), ctx.dp(20), ctx.dp(22), ctx.dp(40))
    }
    val scroll = ScrollView(ctx).apply {
        isFillViewport = true
        setBackgroundColor(bg)
        addView(root)
    }

    fun show() {
        ctx.window.statusBarColor = bg
        ctx.window.navigationBarColor = bg
        val light = !dark
        @Suppress("DEPRECATION")
        ctx.window.decorView.systemUiVisibility =
            if (light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        ctx.setContentView(scroll)
    }
}

// ---------- Texto ----------
fun LinearLayout.title(text: String, color: Int = C.TEXT): TextView =
    text(text, 28f, color, top = 4, bottom = 2).apply { typeface = C.MEDIUM; letterSpacing = -0.01f }

fun LinearLayout.h2(text: String, color: Int = C.TEXT): TextView =
    text(text, 18f, color, top = 26, bottom = 8).apply { typeface = C.MEDIUM }

fun LinearLayout.body(text: String, color: Int = C.TEXT): TextView = text(text, 16f, color)
fun LinearLayout.muted(text: String, color: Int = C.MUTED): TextView = text(text, 14f, color)

fun LinearLayout.text(
    s: String, size: Float, color: Int, bold: Boolean = false,
    top: Int = 2, bottom: Int = 2, center: Boolean = false
): TextView {
    val tv = TextView(context).apply {
        text = s; textSize = size; setTextColor(color)
        typeface = if (bold) C.MEDIUM else C.REGULAR
        setLineSpacing(0f, 1.25f)
        if (center) gravity = Gravity.CENTER_HORIZONTAL
    }
    // En una fila horizontal el texto ocupa el espacio que sobra; en una columna, todo el ancho.
    val lp = if (orientation == LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2)
    addView(tv, lp.apply { topMargin = context.dp(top); bottomMargin = context.dp(bottom) })
    return tv
}

// ---------- Superficies ----------
/** Bloque de contenido: plano, sin sombra, esquinas suaves. */
fun LinearLayout.card(bg: Int = C.SURFACE, build: LinearLayout.() -> Unit): LinearLayout {
    val c = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(18), context.dp(16), context.dp(18), context.dp(16))
        background = rounded(bg, context.dpf(20f))
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

// ---------- Botones ----------
fun LinearLayout.button(
    label: String, color: Int = C.PRIMARY, textColor: Int = Color.WHITE,
    weight: Float = 0f, big: Boolean = false, onClick: (View) -> Unit
): Button {
    val b = Button(context).apply {
        text = label; isAllCaps = false
        textSize = if (big) 18f else 15f
        setTextColor(textColor)
        typeface = C.MEDIUM
        letterSpacing = 0f
        background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#22000000")),
            rounded(color, context.dpf(if (big) 20f else 999f)), null
        )
        stateListAnimator = null
        val v = context.dp(if (big) 18 else 11)
        setPadding(context.dp(16), v, context.dp(16), v)
        minHeight = context.dp(if (big) 60 else 46)
        setOnClickListener(onClick)
    }
    // En una fila horizontal, sin peso, el botón ocupa solo lo que necesita (si no, aplasta al resto).
    val lp = when {
        weight > 0f -> LinearLayout.LayoutParams(0, -2, weight)
        orientation == LinearLayout.HORIZONTAL -> LinearLayout.LayoutParams(-2, -2).apply { leftMargin = context.dp(6) }
        else -> LinearLayout.LayoutParams(-1, -2)
    }
    lp.topMargin = context.dp(4); lp.bottomMargin = context.dp(4)
    if (weight > 0f) { lp.leftMargin = context.dp(3); lp.rightMargin = context.dp(3) }
    addView(b, lp)
    return b
}

fun LinearLayout.outlineButton(label: String, color: Int = C.PRIMARY, weight: Float = 0f, onClick: (View) -> Unit): Button {
    val b = button(label, Color.TRANSPARENT, color, weight, onClick = onClick)
    b.background = RippleDrawable(
        ColorStateList.valueOf(Color.parseColor("#18000000")),
        rounded(Color.TRANSPARENT, context.dpf(999f), context.dp(1), color), null
    )
    return b
}

/** Botón de texto, sin fondo (acciones secundarias). */
fun LinearLayout.textButton(label: String, color: Int = C.PRIMARY, onClick: (View) -> Unit): Button {
    val b = button(label, Color.TRANSPARENT, color, onClick = onClick)
    b.background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#14000000")), null, rounded(Color.WHITE, context.dpf(999f)))
    b.typeface = C.REGULAR
    return b
}

/** Opciones seleccionables (chips). */
class ChoiceGroup<T>(
    val parent: LinearLayout, val options: List<Pair<T, String>>, initial: T?,
    val columns: Int = 2, val dark: Boolean = false, val onPick: (T) -> Unit = {}
) {
    var selected: T? = initial
    private val buttons = mutableListOf<Pair<T, Button>>()

    init {
        options.chunked(columns).forEach { chunk ->
            parent.row {
                chunk.forEach { (value, label) ->
                    val b = button(label, weight = 1f) { pick(value) }
                    b.textSize = 14f; b.minHeight = context.dp(42)
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
            val (bg, fg, stroke) = when {
                dark && on -> Triple(C.NIGHT_TEXT, C.NIGHT, 0)
                dark -> Triple(Color.TRANSPARENT, C.NIGHT_MUTED, ctx.dp(1))
                on -> Triple(C.PRIMARY, Color.WHITE, 0)
                else -> Triple(Color.TRANSPARENT, C.TEXT, ctx.dp(1))
            }
            b.setTextColor(fg)
            b.background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#18000000")),
                rounded(bg, ctx.dpf(999f), stroke, if (dark) C.NIGHT_SURFACE else C.LINE), null
            )
        }
    }
}

fun LinearLayout.input(hint: String, value: String = "", number: Boolean = false): EditText {
    val e = EditText(context).apply {
        this.hint = hint; setText(value); textSize = 16f
        setTextColor(C.TEXT); setHintTextColor(C.MUTED)
        background = rounded(C.BG, context.dpf(14f), context.dp(1), C.LINE)
        val p = context.dp(14); setPadding(p, p, p, p)
        if (number) inputType = InputType.TYPE_CLASS_NUMBER
    }
    addView(e, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(4); bottomMargin = context.dp(4) })
    return e
}

fun LinearLayout.divider(color: Int = C.LINE) {
    addView(View(context).apply { setBackgroundColor(color) },
        LinearLayout.LayoutParams(-1, context.dp(1)).apply { topMargin = context.dp(14); bottomMargin = context.dp(14) })
}

fun View.lp(w: Int, h: Int): View { layoutParams = ViewGroup.LayoutParams(w, h); return this }

/** Anillo de progreso: el elemento central de la pantalla de estudio. */
class Ring(ctx: Context, var track: Int, var fill: Int) : View(ctx) {
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val r = RectF()
    override fun onDraw(c: Canvas) {
        val sw = context.dpf(6f)
        p.strokeWidth = sw
        val size = minOf(width, height) - sw * 2
        val l = (width - size) / 2f; val t = (height - size) / 2f
        r.set(l, t, l + size, t + size)
        p.color = track; c.drawArc(r, 0f, 360f, false, p)
        p.color = fill; c.drawArc(r, -90f, 360f * progress, false, p)
    }
}

/** Círculos que "respiran" despacio en la pantalla de llamada (sin parpadeos). */
class Halo(ctx: Context, val color: Int, val label: String, val labelColor: Int) : View(ctx) {
    private var phase = 0f
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = C.LIGHT; color = labelColor
    }
    private val anim: ValueAnimator? = if (ctx.reducedMotion()) null else
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2800; repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
        }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim?.start() }
    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val base = minOf(width, height) / 2f * 0.55f
        for (i in 0..1) {
            val f = (phase + i * 0.5f) % 1f
            fillP.color = color; fillP.alpha = ((1f - f) * 60).toInt()
            c.drawCircle(cx, cy, base + base * 0.8f * f, fillP)
        }
        fillP.color = color; fillP.alpha = 255
        c.drawCircle(cx, cy, base, fillP)
        txt.textSize = base * 0.75f
        c.drawText(label, cx, cy - (txt.descent() + txt.ascent()) / 2, txt)
    }
}

fun fmtMin(totalMin: Int): String =
    if (totalMin < 60) "$totalMin min" else "${totalMin / 60} h ${"%02d".format(totalMin % 60)} min"

fun fmtClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

/** Botón de texto alineado a la izquierda, sin sangría. */
fun android.widget.TextView.leftAligned(): android.widget.TextView = apply {
    gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
    setPadding(0, paddingTop, 0, paddingBottom)
}
