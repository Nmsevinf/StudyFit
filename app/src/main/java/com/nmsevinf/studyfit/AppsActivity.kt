package com.nmsevinf.studyfit

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.LinearLayout

/** Elegir qué apps se pueden abrir durante un bloque de estudio (PDF, calculadora, diccionario…). */
class AppsActivity : Activity() {

    private data class App(val pkg: String, val label: String)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val s = Store(this)
        val sc = Screen(this)
        val r = sc.root
        r.title("Apps permitidas")
        r.muted("Marca solo las que necesitas para estudiar. El teléfono y las emergencias siempre están permitidos.")
        val search = r.input("Buscar app…")
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        r.addView(list)
        r.button("Guardar") { finish() }
        sc.show()

        val selected = s.allowedApps.toMutableSet()
        list.muted("Cargando apps…")
        Thread {
            val pm = packageManager
            val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != packageName }
                .distinctBy { it.pkg }
                .sortedWith(compareBy({ it.pkg !in selected }, { it.label.lowercase() }))
            runOnUiThread {
                fun render(q: String) {
                    list.removeAllViews()
                    apps.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }.forEach { a ->
                        list.addView(CheckBox(this).apply {
                            text = a.label; textSize = 16f; setTextColor(C.TEXT)
                            isChecked = a.pkg in selected
                            setPadding(dp(4), dp(8), 0, dp(8))
                            setOnCheckedChangeListener { _, v ->
                                if (v) selected += a.pkg else selected -= a.pkg
                                s.allowedApps = selected.toSet()
                            }
                        })
                    }
                }
                render("")
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                    override fun afterTextChanged(e: Editable?) { render(e?.toString() ?: "") }
                })
            }
        }.start()
    }
}
