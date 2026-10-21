package com.nmsevinf.studyfit

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Apartado "De clase": exámenes, tareas y avisos sacados del correo de la UPV, Teams y Gmail.
 * Solo aparece lo académico; los correos normales no se guardan.
 */
class InboxActivity : Activity() {
    private val es = Locale("es", "ES")
    private val when_ = SimpleDateFormat("d MMM, HH:mm", es)
    private val day = SimpleDateFormat("EEEE d 'de' MMMM", es)
    private var filter = -1
    private var showDone = false

    override fun onResume() { super.onResume(); build(); Inbox.markSeen(this) }

    private fun build() {
        val s = Store(this)
        val sc = Screen(this)
        val r = sc.root
        r.title("De clase")
        r.muted("Exámenes, tareas y avisos que te llegan por el correo de la UPV, Teams y Gmail. El resto de correos no se guarda.")

        if (!Inbox.hasAccess(this)) {
            r.card(C.SURFACE) {
                text("Activa el filtro", 16f, C.TEXT, bold = true)
                muted("StudyFit necesita leer las notificaciones de Outlook, Teams y Gmail (solo remitente y asunto). Busca «StudyFit» en la lista y actívalo.")
                button("Permitir leer notificaciones") { Inbox.openAccessSettings(this@InboxActivity) }
            }
        } else if (!s.inboxOn) {
            r.card(C.SURFACE) {
                muted("El filtro está apagado.")
                button("Encenderlo") { s.inboxOn = true; build() }
            }
        }

        val all = Inbox.all(this).filter { it.done == showDone }.sortedByDescending { it.time }
        ChoiceGroup(r, listOf(-1 to "Todo (${all.size})") + Inbox.KINDS.indices.map { k -> k to "${Inbox.KINDS[k]} (${all.count { it.kind == k }})" },
            filter, columns = 2) { filter = it; build() }
        val list = if (filter < 0) all else all.filter { it.kind == filter }

        if (list.isEmpty()) r.muted(if (showDone) "No hay nada marcado como hecho." else "No hay nada pendiente. Lo nuevo aparecerá aquí.")
        list.forEach { item -> itemCard(r, item) }

        r.textButton(if (showDone) "Ver pendientes" else "Ver hechos", C.MUTED) { showDone = !showDone; build() }.leftAligned()
        sc.show()
    }

    private fun itemCard(r: LinearLayout, item: InboxItem) {
        r.card(C.BG) {
            row {
                addView(android.view.View(context).apply { background = rounded(Inbox.KIND_COLORS[item.kind], dpf(3f)) },
                    LinearLayout.LayoutParams(dp(5), dp(40)).apply { rightMargin = dp(12) })
                val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                col.text("${Inbox.ONE[item.kind]} · ${item.app} · ${when_.format(item.time)}", 13f, C.MUTED, top = 0, bottom = 0)
                col.text(item.text.ifBlank { item.from }, 16f, C.TEXT, bold = true, top = 2, bottom = 0)
                col.text(item.from, 14f, C.MUTED, top = 0, bottom = 0)
                if (item.due > 0) col.text("Fecha: " + day.format(item.due), 14f, C.PRIMARY, top = 2, bottom = 0)
                addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            }
            row {
                if (item.kind <= 1 && !item.done) textButton("Añadir al plan", C.PRIMARY) {
                    startActivity(Intent(this@InboxActivity, PlannerActivity::class.java)
                        .putExtra("preTitle", item.text.ifBlank { item.from }.take(60))
                        .putExtra("preType", item.kind)
                        .putExtra("preDate", if (item.due > 0) item.due else Store.startOfDay(System.currentTimeMillis()))
                        .putExtra("preNote", "${item.app}: ${item.from}"))
                    Inbox.setDone(this@InboxActivity, item, true)
                }
                textButton(if (item.done) "Pendiente" else "Hecho", C.MUTED) { Inbox.setDone(this@InboxActivity, item, !item.done); build() }
                textButton("Abrir", C.MUTED) {
                    packageManager.getLaunchIntentForPackage(item.pkg)?.let { i -> startActivity(i) }
                }
            }
        }.also { v -> v.background = rounded(C.BG, dpf(20f), dp(1), C.LINE) }
    }
}
