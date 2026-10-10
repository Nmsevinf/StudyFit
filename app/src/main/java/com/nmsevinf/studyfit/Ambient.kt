package com.nmsevinf.studyfit

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.abs
import kotlin.random.Random

/**
 * Sonido ambiente generado en el propio móvil (sin descargar nada, 0 datos):
 * 1 = ruido blanco, 2 = ruido marrón (grave, tipo cascada), 3 = lluvia.
 */
object Ambient {
    val names = listOf("Silencio", "Ruido blanco", "Ruido marrón", "Lluvia")

    @Volatile private var running = false
    private var thread: Thread? = null
    @Volatile private var kind = 0

    fun play(k: Int) {
        stop()
        if (k == 0) return
        kind = k; running = true
        thread = Thread { loop() }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.join(300)
        thread = null
    }

    private fun loop() {
        val rate = 22050
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minBuf, 8192))
            .build()
        val buf = ShortArray(2048)
        val rnd = Random(System.nanoTime())
        var brown = 0.0
        var lp = 0.0
        var drop = 0.0
        track.play()
        // Fundido de entrada para que no "pegue" de golpe
        var fade = 0.0
        while (running) {
            for (i in buf.indices) {
                val w = rnd.nextDouble() * 2 - 1
                fade = (fade + 1.0 / (rate * 2)).coerceAtMost(1.0)
                val v = when (kind) {
                    1 -> w * 0.18
                    2 -> {
                        brown = (brown + w * 0.02) / 1.02
                        brown * 3.2
                    }
                    else -> {
                        // lluvia: ruido filtrado + gotas aleatorias
                        lp += (w - lp) * 0.25
                        if (rnd.nextDouble() < 0.0009) drop = 0.5 + rnd.nextDouble() * 0.4
                        drop *= 0.992
                        lp * 0.35 + w * drop * 0.25
                    }
                }
                val x = (v * fade).coerceIn(-1.0, 1.0)
                buf[i] = (x * Short.MAX_VALUE * 0.8).toInt().toShort()
            }
            track.write(buf, 0, buf.size)
        }
        track.stop(); track.release()
        if (abs(brown) > 10) brown = 0.0
    }
}
