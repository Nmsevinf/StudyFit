package com.nmsevinf.studyfit

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Switch

/** Elegir técnica, lugar, asignatura y número de bloques antes de empezar. */
class StartActivity : Activity() {

    private lateinit var s: Store

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        s = Store(this)
        val sc = Screen(this)
        val r = sc.root
        r.title("Nueva sesión")
        r.muted("Elige cómo quieres estudiar. La app se encarga del resto: bloquea distracciones y te avisa para moverte, comer y volver.")

        r.h2("1 · Técnica de estudio")
        var chosen = s.techId
        var nCycles = listOf(2, 3, 4, 5, 6, 8).minByOrNull { kotlin.math.abs(it - s.cycles) }!!
        var est: android.widget.TextView? = null
        fun updateEst() {
            val t = Techniques.byId(chosen); val n = nCycles
            val total = t.studyMin * n + t.breakMin * (n - 1)
            est?.text = "≈ ${fmtMin(total)} en total, ${fmtMin(t.studyMin * n)} de estudio."
        }
        val techCards = mutableMapOf<String, LinearLayout>()
        fun paint() = techCards.forEach { (id, card) ->
            card.background = rounded(if (id == chosen) C.PRIMARY_SOFT else C.BG, dpf(20f), if (id == chosen) dp(2) else dp(1), if (id == chosen) C.PRIMARY else C.LINE)
        }
        Techniques.all.forEach { t ->
            val card = r.card {
                text(t.name, 17f, C.TEXT, bold = true)
                text(t.short, 14f, C.PRIMARY)
                muted(t.howTo)
            }
            card.setOnClickListener { chosen = t.id; paint(); updateEst() }
            techCards[t.id] = card
        }
        paint()

        r.h2("2 · ¿Dónde estás?")
        r.muted("Así te propongo ejercicios que puedas hacer ahí.")
        val place = ChoiceGroup(r, Place.values().map { it to it.label }, s.place)

        r.h2("3 · ¿Qué vas a estudiar?")
        val subject = r.input("Asignatura o tema (opcional)", s.subject)

        r.h2("4 · ¿Cuántos bloques?")
        ChoiceGroup(r, listOf(2, 3, 4, 5, 6, 8).map { it to "$it" }, nCycles, columns = 6) { nCycles = it; updateEst() }
        est = r.muted("")
        updateEst()

        r.h2("5 · Opciones")
        val strict = Switch(this).apply {
            text = "Modo estricto: fijar la pantalla (Android te pedirá confirmar)"
            isChecked = s.strict; textSize = 15f; setTextColor(C.TEXT)
        }
        r.addView(strict)
        r.muted("Sonido ambiente (generado en el móvil, no gasta datos):")
        val amb = ChoiceGroup(r, Ambient.names.indices.map { it to Ambient.names[it] }, s.ambient)

        if (!BlockerService.isEnabled(this)) {
            r.card(C.WARN_SOFT) {
                text("El bloqueo de apps está desactivado", 15f, C.TEXT, bold = true)
                text("La sesión funciona igual, pero no te sacará de otras apps.", 14f, C.MUTED)
                button("Activarlo", C.PRIMARY) {
                    startActivity(Intent(this@StartActivity, SettingsActivity::class.java))
                }
            }
        }

        r.button("Empezar", big = true) {
            val go = {
                s.ambient = amb.selected ?: 0
                Engine.start(this, chosen, place.selected ?: Place.CASA, subject.text.toString().trim(),
                    nCycles, strict.isChecked)
                startActivity(Intent(this, SessionActivity::class.java))
                finish()
            }
            if (Engine.isLate(this)) {
                AlertDialog.Builder(this).setTitle("Es tarde")
                    .setMessage("Ya pasó tu hora de dormir. Estudiar con sueño rinde poco y empeora la memoria de mañana. ¿Seguro?")
                    .setPositiveButton("Solo un rato") { _, _ -> go() }
                    .setNegativeButton("Mejor a dormir") { _, _ -> finish() }
                    .show()
            } else go()
        }
        sc.show()
    }
}
