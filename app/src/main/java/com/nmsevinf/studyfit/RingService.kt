package com.nmsevinf.studyfit

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Hace sonar la "llamada" (ejercicio, comer, volver a estudiar…).
 * Es un servicio en primer plano: suena y vibra aunque la app esté cerrada,
 * el móvil bloqueado o Android no deje abrir la pantalla de llamada.
 * Usa el canal de ALARMA, así que suena también en modo silencio.
 */
class RingService : Service() {

    private var player: MediaPlayer? = null
    private val h = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { finishRing(keepNotification = false); return START_NOT_STICKY }
        val type = intent?.getStringExtra("type") ?: CallType.EXERCISE
        val n = Notifs.callNotification(this, type)
        try {
            if (Build.VERSION.SDK_INT >= 29)
                startForeground(Notifs.ID_CALL, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(Notifs.ID_CALL, n)
        } catch (_: Exception) {
            // Si Android no deja el servicio en primer plano, al menos se muestra la notificación
            getSystemService(NotificationManager::class.java).notify(Notifs.ID_CALL, n)
        }
        startSound()
        // Intenta abrir la pantalla de llamada (funciona si la app está delante)
        try {
            startActivity(Intent(this, CallActivity::class.java).putExtra("type", type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (_: Exception) { }
        h.removeCallbacksAndMessages(null)
        h.postDelayed({ finishRing(keepNotification = true) }, 60_000)   // deja de sonar al minuto
        return START_NOT_STICKY
    }

    private fun startSound() {
        stopSound()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@RingService, uri)
                isLooping = true
                prepare()
                // Empieza bajito y va subiendo: avisa sin sobresaltar
                setVolume(0.25f, 0.25f)
                start()
            }
            var vol = 0.25f
            val up = object : Runnable {
                override fun run() {
                    vol = (vol + 0.15f).coerceAtMost(1f)
                    try { player?.setVolume(vol, vol) } catch (_: Exception) { }
                    if (vol < 1f) h.postDelayed(this, 3000)
                }
            }
            h.postDelayed(up, 3000)
        } catch (_: Exception) { player = null }

        val v = getSystemService(Vibrator::class.java) ?: return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 900, 500, 1800), 0)
        if (Build.VERSION.SDK_INT >= 33)
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun stopSound() {
        try { player?.stop() } catch (_: Exception) { }
        player?.release(); player = null
        getSystemService(Vibrator::class.java)?.cancel()
    }

    private fun finishRing(keepNotification: Boolean) {
        h.removeCallbacksAndMessages(null)
        stopSound()
        if (Build.VERSION.SDK_INT >= 24)
            stopForeground(if (keepNotification) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
        if (!keepNotification) getSystemService(NotificationManager::class.java).cancel(Notifs.ID_CALL)
        stopSelf()
    }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); stopSound(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "com.nmsevinf.studyfit.STOP_RING"

        fun start(ctx: Context, type: String) {
            val i = Intent(ctx, RingService::class.java).putExtra("type", type)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (_: Exception) {
                // Último recurso: solo la notificación
                Notifs.ensure(ctx)
                ctx.getSystemService(NotificationManager::class.java).notify(Notifs.ID_CALL, Notifs.callNotification(ctx, type))
            }
        }

        /** Para el sonido y quita la notificación (al aceptar la llamada). */
        fun stop(ctx: Context) {
            try { ctx.startService(Intent(ctx, RingService::class.java).setAction(ACTION_STOP)) }
            catch (_: Exception) { ctx.stopService(Intent(ctx, RingService::class.java)) }
            ctx.getSystemService(NotificationManager::class.java).cancel(Notifs.ID_CALL)
        }

        fun stopPendingIntent(ctx: Context): PendingIntent = PendingIntent.getService(
            ctx, 60, Intent(ctx, RingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
    }
}
