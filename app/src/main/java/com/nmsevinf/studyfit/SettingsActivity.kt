package com.nmsevinf.studyfit

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var s: Store

    override fun onCreate(b: Bundle?) { super.onCreate(b); s = Store(this) }
    override fun onResume() { super.onResume(); build() }
    override fun onPause() { super.onPause(); stopNfc() }

    private fun build() {
        val sc = Screen(this)
        val r = sc.root
        r.title("Ajustes")

        // ---------- Permisos ----------
        r.h2("Permisos")
        r.muted("Necesarios para que la app te bloquee distracciones y te llame aunque el móvil esté bloqueado.")
        val nm = getSystemService(NotificationManager::class.java)
        perm(r, "Notificaciones", "Para avisarte de descansos, agua y sueño.", nm.areNotificationsEnabled()) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            else openAppSettings()
        }
        perm(r, "Bloqueo de apps (Accesibilidad)",
            "Busca «StudyFit · Bloqueo de estudio» y actívalo.\n" +
            "Si sale gris o «Ajuste restringido»: ve a Ajustes › Aplicaciones › StudyFit › menú ⋮ (arriba a la derecha) › «Permitir ajustes restringidos», y vuelve a intentarlo.",
            BlockerService.isEnabled(this)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        if (Build.VERSION.SDK_INT >= 34) {
            perm(r, "Llamadas a pantalla completa", "Para que la «llamada» de ejercicio aparezca aunque el móvil esté bloqueado.", nm.canUseFullScreenIntent()) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(AlarmManager::class.java)
            perm(r, "Alarmas exactas", "Para que los descansos empiecen al minuto exacto.", am.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        val pm = getSystemService(PowerManager::class.java)
        perm(r, "Sin restricción de batería", "Evita que Android duerma la app (importante en Xiaomi, Samsung, Huawei…).", pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (_: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
        r.card(C.SURFACE) {
            text("Modo estricto (fijar pantalla)", 15f, C.TEXT, bold = true)
            muted("Si lo usas, activa antes: Ajustes › Seguridad › «Fijar aplicaciones» (en algunos móviles: «Anclar pantalla»). Para salir de una sesión fijada usa el botón «Salir» de StudyFit.")
        }

        // ---------- Apps permitidas ----------
        r.h2("Apps permitidas mientras estudias")
        val allowed = s.allowedApps
        r.muted(if (allowed.isEmpty()) "Ninguna (solo teléfono y emergencias)." else "${allowed.size} app(s) permitidas.")
        r.button("Elegir apps (lector de PDF, calculadora…)") { startActivity(Intent(this, AppsActivity::class.java)) }

        // ---------- Horarios de estudio ----------
        r.h2("Empezar a una hora fija")
        r.muted("A esa hora StudyFit te llama para empezar a estudiar.")
        val names = listOf("L", "M", "X", "J", "V", "S", "D")
        s.schedules().forEach { sc2 ->
            r.card {
                row {
                    val d = names.indices.filter { sc2.days and (1 shl it) != 0 }.joinToString(" ") { names[it] }
                    text("%02d:%02d, %s".format(sc2.hour, sc2.minute, d), 16f, C.TEXT, bold = true).layoutParams =
                        LinearLayout.LayoutParams(0, -2, 1f)
                    textButton("Borrar", C.DANGER) { s.removeSchedule(sc2.id); Engine.scheduleStudyTimes(this@SettingsActivity); build() }
                }
            }
        }
        r.button("+ Añadir horario") { addSchedule() }

        // ---------- Calendarios ----------
        r.h2("Calendarios")
        r.muted("StudyFit puede mostrar en el Plan los calendarios de tu móvil (Google/Gmail, Outlook con las reuniones de Teams, Samsung) y archivos .ics como el horario de la UPV. Solo los lee, nunca los cambia.")
        if (!ExternalCal.hasPermission(this)) {
            r.button("Conectar calendarios del móvil") { ExternalCal.requestPermission(this) }
        } else {
            switch(r, "Mostrar calendarios del móvil en el Plan", s.calendarsOn) { s.calendarsOn = it; build() }
            if (s.calendarsOn) {
                val cals = ExternalCal.calendars(this)
                if (cals.isEmpty()) r.muted("No hay calendarios en el móvil. Añade tu cuenta de Google o activa la sincronización en Outlook.")
                cals.forEach { cal ->
                    val sw = Switch(this).apply {
                        text = cal.name + if (cal.account.isNotBlank() && cal.account != cal.name) "\n" + cal.account else ""
                        isChecked = cal.id.toString() !in s.hiddenCals; textSize = 15f; setTextColor(C.TEXT)
                        setPadding(0, dp(6), 0, dp(6))
                        thumbTintList = android.content.res.ColorStateList.valueOf(cal.color)
                        setOnCheckedChangeListener { _, v ->
                            s.hiddenCals = if (v) s.hiddenCals - cal.id.toString() else s.hiddenCals + cal.id.toString()
                        }
                    }
                    r.addView(sw)
                }
            }
        }
        ExternalCal.icsSources(this).forEach { src ->
            r.row {
                text("${src.name} (${src.count} eventos)", 15f, C.TEXT)
                textButton("Quitar", C.DANGER) { ExternalCal.removeIcs(this@SettingsActivity, src.id); build() }
            }
        }
        r.outlineButton("Importar archivo .ics (horario UPV)", C.PRIMARY) { ExternalCal.pickIcs(this) }
        r.card(C.SURFACE) {
            text("Cómo traer cada calendario", 15f, C.TEXT, bold = true)
            muted("Gmail: si tu cuenta de Google está en el móvil, ya aparece.\n" +
                "Teams: instala Outlook, añade tu cuenta de la UPV y en Ajustes de la cuenta activa «Sincronizar calendarios».\n" +
                "Horario UPV: en la web de horarios, genera tu horario y pulsa «Descargar archivo de calendario ICS»; luego impórtalo aquí. Si cambia el horario, vuelve a importarlo.")
        }

        // ---------- NFC ----------
        r.h2("Etiqueta NFC en tu mesa")
        val nfc = NfcAdapter.getDefaultAdapter(this)
        when {
            nfc == null -> r.muted("Tu móvil no tiene NFC. Usa el horario automático.")
            else -> {
                r.muted("Compra etiquetas NFC NTAG213 o NTAG215 (pegatinas, unos 5–8 € el pack). Pega una en la mesa, graba aquí y luego basta con apoyar el móvil encima para empezar.")
                if (!nfc.isEnabled) r.muted("Activa el NFC en los ajustes rápidos del móvil.")
                nfcBtn = r.button("Grabar etiqueta") { startNfc() }
            }
        }

        // ---------- Salud ----------
        r.h2("Descansos")
        switch(r, "Empezar el descanso cuando yo lo pulse", s.manualBreak) { s.manualBreak = it }
        r.muted("Al acabar un bloque, el descanso queda en pausa: eliges el ejercicio y la cuenta atrás empieza al pulsar «Empezar descanso».")

        r.h2("Recordatorios de salud")
        switch(r, "Vista 20-20-20 cada 20 min de estudio", s.eyesOn) { s.eyesOn = it }
        switch(r, "Postura a los 10 min de cada bloque y luego cada 30 min", s.postureOn) { s.postureOn = it }
        switch(r, "Agua y movimiento cada 90 min (fuera de sesión)", s.dailyOn) { s.dailyOn = it; Engine.scheduleDaily(this, force = true) }
        r.muted("Si no te salen como ventana emergente: Ajustes del móvil › Notificaciones › Estilo de notificación emergente › Breve o Detallado, y comprueba que StudyFit esté permitida.")
        r.muted("Horario de agua y movimiento: de ${s.dayStart}:00 a ${s.dayEnd}:00")
        r.row {
            outlineButton("Desde ${s.dayStart}h", weight = 1f) {
                TimePickerDialog(this@SettingsActivity, { _, h, _ ->
                    if (h >= s.dayEnd) Toast.makeText(this@SettingsActivity, "La hora de inicio tiene que ser antes de las ${s.dayEnd}:00", Toast.LENGTH_LONG).show()
                    else { s.dayStart = h; Engine.scheduleDaily(this@SettingsActivity, force = true); build() }
                }, s.dayStart, 0, true).show()
            }
            outlineButton("Hasta ${s.dayEnd}h", weight = 1f) {
                TimePickerDialog(this@SettingsActivity, { _, h, _ ->
                    if (h <= s.dayStart) Toast.makeText(this@SettingsActivity, "La hora de fin tiene que ser después de las ${s.dayStart}:00", Toast.LENGTH_LONG).show()
                    else { s.dayEnd = h; Engine.scheduleDaily(this@SettingsActivity, force = true); build() }
                }, s.dayEnd, 0, true).show()
            }
        }
        r.muted("Objetivo de agua: ${s.waterGoal} vasos al día")
        r.row {
            outlineButton("−", weight = 1f) { s.waterGoal = (s.waterGoal - 1).coerceAtLeast(4); build() }
            outlineButton("+", weight = 1f) { s.waterGoal = (s.waterGoal + 1).coerceAtMost(14); build() }
        }

        // ---------- Sueño ----------
        r.h2("Sueño")
        switch(r, "Avisarme de la hora de dormir", s.sleepOn) { s.sleepOn = it; Engine.scheduleSleep(this) }
        r.button("Hora de dormir: %02d:%02d".format(s.bedHour, s.bedMinute)) {
            TimePickerDialog(this, { _, h, m -> s.bedHour = h; s.bedMinute = m; Engine.scheduleSleep(this); build() }, s.bedHour, s.bedMinute, true).show()
        }
        r.muted("Te aviso 30 min antes y a la hora. Si intentas estudiar más tarde, te lo recordaré.")

        r.h2("Sobre StudyFit")
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "" }
        r.muted("Versión $ver. Todo se guarda solo en tu móvil y funciona sin internet.")
        sc.show()
    }

    private fun perm(r: LinearLayout, name: String, desc: String, ok: Boolean, fix: () -> Unit) {
        r.card(if (ok) C.SURFACE else C.WARN_SOFT) {
            text(name, 16f, C.TEXT, bold = true)
            if (ok) text("Activado", 13f, C.OLIVE)
            muted(desc)
            if (!ok) button("Activar", C.PRIMARY) { fix() }
        }
    }

    private fun switch(r: LinearLayout, label: String, value: Boolean, on: (Boolean) -> Unit) {
        val sw = Switch(this).apply {
            text = label; isChecked = value; textSize = 15f; setTextColor(C.TEXT)
            setPadding(0, dp(6), 0, dp(6))
            setOnCheckedChangeListener { _, v -> on(v) }
        }
        r.addView(sw)
    }

    private fun openAppSettings() =
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != ExternalCal.REQ_PERMISSION && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) openAppSettings()
        build()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ExternalCal.REQ_ICS && resultCode == RESULT_OK) {
            val n = data?.data?.let { ExternalCal.importIcs(this, it) } ?: -1
            Toast.makeText(this, if (n > 0) "Importados $n eventos" else "No se ha podido leer el archivo .ics", Toast.LENGTH_LONG).show()
            build()
        }
    }

    private fun addSchedule() {
        val names = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
        val checked = booleanArrayOf(true, true, true, true, true, false, false)
        AlertDialog.Builder(this).setTitle("¿Qué días?")
            .setMultiChoiceItems(names.toTypedArray(), checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("Elegir hora") { _, _ ->
                val mask = checked.indices.filter { checked[it] }.sumOf { 1 shl it }
                if (mask == 0) {
                    Toast.makeText(this, "Elige al menos un día", Toast.LENGTH_LONG).show(); return@setPositiveButton
                }
                TimePickerDialog(this, { _, h, m ->
                    s.addSchedule(mask, h, m); Engine.scheduleStudyTimes(this); build()
                }, 17, 0, true).show()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    // ---------- Grabar NFC ----------
    private var nfcBtn: Button? = null

    private fun startNfc() {
        val nfc = NfcAdapter.getDefaultAdapter(this) ?: return
        if (!nfc.isEnabled) { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)); return }
        nfcBtn?.text = "Acerca la etiqueta a la parte de atrás del móvil…"
        nfc.enableReaderMode(this, { tag -> writeTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
    }

    private fun stopNfc() {
        NfcAdapter.getDefaultAdapter(this)?.let { try { it.disableReaderMode(this) } catch (_: Exception) { } }
    }

    private fun writeTag(tag: Tag) {
        val msg = NdefMessage(arrayOf(
            NdefRecord.createUri("studyfit://start"),
            NdefRecord.createApplicationRecord(packageName),
        ))
        val result = try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                val res = when {
                    !ndef.isWritable -> "La etiqueta está bloqueada (solo lectura)."
                    ndef.maxSize < msg.toByteArray().size -> "La etiqueta es demasiado pequeña."
                    else -> { ndef.writeNdefMessage(msg); "ok" }
                }
                ndef.close()
                res
            } else {
                val f = NdefFormatable.get(tag)
                if (f != null) { f.connect(); f.format(msg); f.close(); "ok" } else "Esta etiqueta no es compatible (usa NTAG213/215)."
            }
        } catch (e: Exception) { "No se pudo grabar: mantén la etiqueta quieta y reintenta." }
        runOnUiThread {
            stopNfc()
            if (result == "ok") {
                Toast.makeText(this, "Etiqueta grabada. Pégala en tu mesa.", Toast.LENGTH_LONG).show()
                nfcBtn?.text = "Grabar otra etiqueta"
            } else {
                Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                nfcBtn?.text = "Reintentar"
            }
        }
    }
}
