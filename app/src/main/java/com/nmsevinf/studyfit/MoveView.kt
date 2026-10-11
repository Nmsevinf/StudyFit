package com.nmsevinf.studyfit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.LinearInterpolator
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos

/**
 * Muñeco animado que muestra cómo hacer cada ejercicio.
 * Las poses están en assets/moves.json (generado con tools/moves_gen.py):
 * fotogramas clave en una caja de 100×100 que se interpolan suavemente.
 */
class MoveView(ctx: Context, exerciseName: String) : View(ctx) {

    private class Anim(val dur: Float, val frames: List<Map<String, FloatArray>>, val props: List<FloatArray>)

    private val anim: Anim? = load(ctx)?.let { (moves, map) ->
        val id = map[exerciseName] ?: return@let null
        moves[id]
    }

    private var u = 0f
    private val animator: ValueAnimator? = anim?.let { a ->
        if (ctx.reducedMotion()) null else ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (a.dur * 1000).toLong(); repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { u = it.animatedValue as Float; invalidate() }
        }
    }

    private val near = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.TEXT; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val far = Paint(near).apply { color = android.graphics.Color.parseColor("#C4B2A0") }
    private val prop = Paint(near).apply { color = C.LINE }
    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.PRIMARY }
    private val path = Path()

    override fun onAttachedToWindow() { super.onAttachedToWindow(); animator?.start() }
    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }

    val hasAnimation get() = anim != null

    override fun onDraw(c: Canvas) {
        val a = anim ?: return
        val s = minOf(width, height) / 100f
        val ox = (width - 100 * s) / 2f; val oy = (height - 100 * s) / 2f
        fun X(v: Float) = ox + v * s
        fun Y(v: Float) = oy + v * s
        near.strokeWidth = 3.2f * s; far.strokeWidth = 3.2f * s; prop.strokeWidth = 1.6f * s

        // suelo y objetos
        c.drawLine(X(0f), Y(91.5f), X(100f), Y(91.5f), prop)
        for (pl in a.props) {
            path.reset(); path.moveTo(X(pl[0]), Y(pl[1]))
            var i = 2; while (i < pl.size) { path.lineTo(X(pl[i]), Y(pl[i + 1])); i += 2 }
            c.drawPath(path, prop)
        }

        // pose interpolada
        val n = a.frames.size
        val x = u * n; val i = x.toInt() % n
        val t = ((1 - cos(PI * (x - x.toInt()))) / 2).toFloat()
        val f0 = a.frames[i]; val f1 = a.frames[(i + 1) % n]
        fun p(k: String): FloatArray? {
            val p0 = f0[k] ?: return null; val p1 = f1[k] ?: p0
            return floatArrayOf(X(p0[0] + (p1[0] - p0[0]) * t), Y(p0[1] + (p1[1] - p0[1]) * t))
        }
        fun seg(k1: String, k2: String, pt: Paint) {
            val p1 = p(k1) ?: return; val p2 = p(k2) ?: return
            c.drawLine(p1[0], p1[1], p2[0], p2[1], pt)
        }
        // De frente (con hombros "ls/rs" y caderas "lp/rp") todo va del mismo color;
        // de lado, el brazo y la pierna lejanos van más claros.
        val front = f0.containsKey("ls")
        val back = if (front) near else far
        val la = if (front) "ls" else "neck"; val ra = if (front) "rs" else "neck"
        val ll = if (front) "lp" else "hip"; val rl = if (front) "rp" else "hip"
        seg(ll, "lk", back); seg("lk", "lf", back); seg("lf", "lt", back)
        seg(la, "le", back); seg("le", "lh", back)
        // tronco (curvo si hay "spine", p. ej. gato-vaca)
        val sp = p("spine")
        if (sp != null) {
            val h = p("hip")!!; val nk = p("neck")!!
            path.reset(); path.moveTo(h[0], h[1]); path.quadTo(sp[0], sp[1], nk[0], nk[1]); c.drawPath(path, near)
        } else seg("hip", "neck", near)
        if (front) { seg("ls", "rs", near); seg("lp", "rp", near) }
        seg(rl, "rk", near); seg("rk", "rf", near); seg("rf", "rt", near)
        seg(ra, "re", near); seg("re", "rh", near)
        p("head")?.let { c.drawCircle(it[0], it[1], 5.5f * s, head) }
    }

    companion object {
        private var cache: Pair<Map<String, Anim>, Map<String, String>>? = null

        private fun load(ctx: Context): Pair<Map<String, Anim>, Map<String, String>>? {
            cache?.let { return it }
            return try {
                val root = JSONObject(ctx.assets.open("moves.json").bufferedReader().readText())
                val mv = root.getJSONObject("moves"); val mp = root.getJSONObject("map")
                val moves = HashMap<String, Anim>()
                for (id in mv.keys()) {
                    val o = mv.getJSONObject(id)
                    val frames = (0 until o.getJSONArray("frames").length()).map { k ->
                        val fo = o.getJSONArray("frames").getJSONObject(k)
                        fo.keys().asSequence().associateWith { j ->
                            val a = fo.getJSONArray(j); floatArrayOf(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())
                        }
                    }
                    val props = o.optJSONArray("props")?.let { pa ->
                        (0 until pa.length()).map { q ->
                            val pl = pa.getJSONArray(q)
                            FloatArray(pl.length() * 2) { r -> pl.getJSONArray(r / 2).getDouble(r % 2).toFloat() }
                        }
                    } ?: emptyList()
                    moves[id] = Anim(o.getDouble("dur").toFloat(), frames, props)
                }
                val map = mp.keys().asSequence().associateWith { mp.getString(it) }
                (moves to map).also { cache = it }
            } catch (_: Exception) { null }
        }
    }
}
