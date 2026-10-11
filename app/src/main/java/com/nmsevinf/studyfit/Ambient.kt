package com.nmsevinf.studyfit

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sonido de fondo durante el estudio. Todo funciona sin internet:
 *  1 Lluvia y 2 Ruido: generados en el móvil.
 *  3 Bach: Preludio en Do mayor BWV 846 (1722, dominio público) tocado por un piano sintetizado.
 *  4 Piano: piano lento y tranquilo, generado al momento (nunca se repite igual).
 *  5 Tu música: archivos de audio que elijas del móvil, en bucle.
 */
object Ambient {
    const val NONE = 0; const val RAIN = 1; const val NOISE = 2; const val BACH = 3; const val PIANO = 4; const val OWN = 5
    val names = listOf("Nada", "Lluvia", "Ruido", "Bach", "Piano", "Tu música")

    private const val RATE = 22050
    @Volatile private var running = false
    private var thread: Thread? = null
    private var player: MediaPlayer? = null
    private var ownIndex = 0

    fun play(ctx: Context, k: Int) {
        stop()
        when (k) {
            NONE -> return
            OWN -> playOwn(ctx.applicationContext)
            else -> {
                running = true
                thread = Thread {
                    when (k) {
                        BACH -> pianoLoop { bachPrelude(it) }
                        PIANO -> pianoLoop { calmPiano(it) }
                        else -> noiseLoop(k)
                    }
                }.apply { isDaemon = true; priority = Thread.MAX_PRIORITY; start() }
            }
        }
    }

    fun stop() {
        running = false
        thread?.join(400); thread = null
        try { player?.stop() } catch (_: Exception) { }
        player?.release(); player = null
    }

    private fun track(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minBuf * 2, 16384))
            .build()
    }

    // ---------------- Lluvia y ruido ----------------
    private fun noiseLoop(kind: Int) {
        val t = track(); val buf = ShortArray(2048); val rnd = Random(System.nanoTime())
        var brown = 0.0; var lp = 0.0; var drop = 0.0; var fade = 0.0
        t.play()
        while (running) {
            for (i in buf.indices) {
                val w = rnd.nextDouble() * 2 - 1
                fade = (fade + 1.0 / (RATE * 2)).coerceAtMost(1.0)
                val v = if (kind == NOISE) {
                    brown = (brown + w * 0.02) / 1.02; brown * 3.2
                } else {
                    lp += (w - lp) * 0.25
                    if (rnd.nextDouble() < 0.0009) drop = 0.5 + rnd.nextDouble() * 0.4
                    drop *= 0.992
                    lp * 0.35 + w * drop * 0.25
                }
                buf[i] = ((v * fade).coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.8).toInt().toShort()
            }
            t.write(buf, 0, buf.size)
        }
        t.stop(); t.release()
    }

    // ---------------- Piano sintetizado ----------------
    /** Una nota a tocar: [at] segundos desde el inicio del fragmento, nota MIDI y volumen. */
    class Note(val at: Double, val midi: Int, val vel: Double)

    private val cache = HashMap<Int, FloatArray>()

    /** Sonido de piano: armónicos con caída exponencial (más rápida en los agudos). */
    private fun tone(midi: Int): FloatArray = cache.getOrPut(midi) {
        val f = 440.0 * 2.0.pow((midi - 69) / 12.0)
        val len = (RATE * 3.2).toInt()
        val out = FloatArray(len)
        val decay = 0.9 + (midi - 40).coerceAtLeast(0) * 0.045
        val amps = doubleArrayOf(1.0, 0.45, 0.22, 0.12, 0.06, 0.03)
        for (i in 0 until len) {
            val t = i.toDouble() / RATE
            var s = 0.0
            for (h in amps.indices) {
                val fh = f * (h + 1) * (1 + 0.0004 * h * h)    // ligera inarmonicidad, como un piano real
                if (fh > RATE / 2.2) break
                s += amps[h] * sin(2 * PI * fh * t) * exp(-t * decay * (1 + h * 0.6))
            }
            val attack = (t / 0.004).coerceAtMost(1.0)
            out[i] = (s * attack).toFloat()
        }
        out
    }

    private class Voice(val data: FloatArray, val vel: Float) { var pos = 0 }

    /**
     * Reproduce fragmentos de notas sin parar. [next] devuelve el siguiente fragmento
     * (lista de notas y duración en segundos).
     */
    private fun pianoLoop(next: (Random) -> Pair<List<Note>, Double>) {
        val t = track()
        val block = 1024
        val buf = ShortArray(block)
        val rnd = Random(System.nanoTime())
        val voices = ArrayList<Voice>()
        // Reverberación sencilla (dos ecos con realimentación): da sensación de sala
        val d1 = FloatArray((RATE * 0.083).toInt()); var p1 = 0
        val d2 = FloatArray((RATE * 0.127).toInt()); var p2 = 0
        var fade = 0f
        var (notes, dur) = next(rnd)
        notes.map { it.midi }.distinct().forEach { tone(it) }   // prepara los sonidos antes de empezar
        t.play()
        var sample = 0L
        var idx = 0
        while (running) {
            for (i in 0 until block) {
                val now = sample.toDouble() / RATE
                while (idx < notes.size && notes[idx].at <= now) {
                    val n = notes[idx]; voices += Voice(tone(n.midi), n.vel.toFloat()); idx++
                }
                var s = 0f
                val iter = voices.iterator()
                while (iter.hasNext()) {
                    val v = iter.next()
                    if (v.pos >= v.data.size) { iter.remove(); continue }
                    s += v.data[v.pos++] * v.vel
                }
                val e1 = d1[p1]; val e2 = d2[p2]
                d1[p1] = s + e1 * 0.42f; d2[p2] = s + e2 * 0.36f
                p1 = (p1 + 1) % d1.size; p2 = (p2 + 1) % d2.size
                fade = (fade + 1f / (RATE * 2)).coerceAtMost(1f)
                val out = (s + (e1 + e2) * 0.22f) * 0.16f * fade
                buf[i] = (out.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                sample++
                if (now >= dur) { val n = next(rnd); notes = n.first; dur = n.second; idx = 0; sample = 0 }
            }
            t.write(buf, 0, block)
        }
        t.stop(); t.release()
    }

    /** Nombre de nota → número MIDI ("C4" = 60, "F#3", "Bb2"). */
    private fun m(n: String): Int {
        val base = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)[n[0]]!!
        var i = 1; var acc = 0
        while (n[i] == '#' || n[i] == 'b') { acc += if (n[i] == '#') 1 else -1; i++ }
        return 12 * (n.substring(i).toInt() + 1) + base + acc
    }

    /**
     * J. S. Bach, Preludio en Do mayor BWV 846 (El clave bien temperado, 1722).
     * Cada compás es un acorde de 5 notas tocado con el mismo patrón de arpegio, dos veces.
     */
    private val bachBars = listOf(
        "C4 E4 G4 C5 E5", "C4 D4 A4 D5 F5", "B3 D4 G4 D5 F5", "C4 E4 G4 C5 E5",
        "C4 E4 A4 E5 A5", "C4 D4 F#4 A4 D5", "B3 D4 G4 D5 G5", "B3 C4 E4 G4 C5",
        "A3 C4 E4 G4 C5", "D3 A3 D4 F#4 C5", "G3 B3 D4 G4 B4", "G3 Bb3 E4 G4 C#5",
        "F3 A3 D4 A4 D5", "F3 Ab3 D4 F4 B4", "E3 G3 C4 G4 C5", "E3 F3 A3 C4 F4",
        "D3 F3 A3 C4 F4", "G2 D3 G3 B3 F4", "C3 E3 G3 C4 E4", "C3 G3 Bb3 C4 E4",
        "F2 F3 A3 C4 E4", "F#2 C3 A3 C4 Eb4", "Ab2 F3 B3 C4 D4", "G2 F3 G3 B3 D4",
        "G2 E3 G3 C4 E4", "G2 D3 G3 C4 F4", "G2 D3 G3 B3 F4", "G2 Eb3 A3 C4 F#4",
        "G2 E3 G3 C4 G4", "G2 D3 G3 C4 F4", "G2 D3 G3 B3 F4", "C2 C3 G3 Bb3 E4",
        "C2 C3 F3 A3 C4", "C2 B2 G3 B3 D4",
    ).map { bar -> bar.split(" ").map { m(it) } }

    private fun bachPrelude(@Suppress("UNUSED_PARAMETER") rnd: Random): Pair<List<Note>, Double> {
        val six = 60.0 / 66 / 4          // semicorchea a negra = 66
        val out = ArrayList<Note>()
        var t = 0.0
        for (bar in bachBars) {
            repeat(2) {
                // patrón: 1 2 3 4 5 3 4 5
                val order = intArrayOf(0, 1, 2, 3, 4, 2, 3, 4)
                for ((k, n) in order.withIndex()) {
                    val vel = when (k) { 0 -> 0.95; 1 -> 0.75; else -> 0.6 }
                    out += Note(t + k * six, bar[n], vel)
                }
                t += 8 * six
            }
        }
        // Acorde final, más lento
        val end = listOf("C2", "C3", "E4", "G4", "C5").map { m(it) }
        end.forEachIndexed { i, n -> out += Note(t + i * 0.09, n, 0.7) }
        return out to t + 6.0    // silencio antes de volver a empezar
    }

    /** Piano tranquilo: alterna dos acordes suaves y una melodía lenta que va cambiando. */
    private var melodyNote = m("F#5")
    private fun calmPiano(rnd: Random): Pair<List<Note>, Double> {
        val beat = 60.0 / 54
        val chords = listOf(
            listOf("G2", "B3", "D4", "F#4"),   // Sol maj7
            listOf("D2", "A3", "C#4", "F#4"),  // Re maj7
            listOf("E2", "B3", "D4", "G4"),    // Mi m7
            listOf("D2", "A3", "C#4", "F#4"),
        ).map { c -> c.map { m(it) } }
        val scale = listOf("D5", "E5", "F#5", "A5", "B5", "C#6", "D6").map { m(it) }
        val out = ArrayList<Note>()
        var t = 0.0
        for (c in chords) {
            out += Note(t, c[0], 0.75)                                           // bajo, tiempo 1
            c.drop(1).forEachIndexed { i, n -> out += Note(t + beat + i * 0.02, n, 0.45) }  // acorde, tiempo 2
            // melodía: 0–2 notas por compás, moviéndose por grados
            val count = listOf(0, 1, 1, 2).random(rnd)
            repeat(count) { j ->
                var idx = scale.indexOf(melodyNote).takeIf { it >= 0 } ?: 2
                idx = (idx + listOf(-2, -1, -1, 0, 1, 1, 2).random(rnd)).coerceIn(0, scale.size - 1)
                melodyNote = scale[idx]
                out += Note(t + (if (count == 1) 0.0 else j * 1.5 * beat), melodyNote, 0.55)
            }
            t += 3 * beat
        }
        return out.sortedBy { it.at } to t
    }

    // ---------------- Tu música ----------------
    private fun playOwn(ctx: Context) {
        val uris = Store(ctx).ownMusic
        if (uris.isEmpty()) return
        if (ownIndex >= uris.size) ownIndex = 0
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            p.setDataSource(ctx, Uri.parse(uris[ownIndex]))
            p.setOnCompletionListener {
                ownIndex = (ownIndex + 1) % uris.size
                play(ctx, OWN)
            }
            p.setOnErrorListener { _, _, _ -> ownIndex = (ownIndex + 1) % uris.size; true }
            p.prepare(); p.start()
            player = p
        } catch (_: Exception) {
            p.release()
        }
    }
}
