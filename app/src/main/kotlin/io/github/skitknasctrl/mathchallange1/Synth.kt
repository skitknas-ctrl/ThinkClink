package io.github.skitknasctrl.mathchallange1

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Generates the game's short sound effects as 16-bit mono PCM, so the app needs no audio files. */
object Synth {
    const val RATE = 22050

    fun make(s: Sfx): ShortArray = when (s) {
        Sfx.COIN -> tones(listOf(1568f to 0.05f, 2093f to 0.11f), 0.45f, 22f)
        Sfx.BILL -> rustle(0.3f, 0.5f)
        Sfx.GEM -> tones(listOf(1319f to 0.07f, 1661f to 0.07f, 1976f to 0.07f, 2637f to 0.2f), 0.38f, 14f)
        Sfx.LIFE -> sweep(440f, 140f, 0.45f, 0.45f)
        Sfx.BUY -> tones(listOf(784f to 0.08f, 1175f to 0.16f), 0.42f, 12f)
        Sfx.WRONG -> buzz(150f, 0.32f, 0.35f)
        Sfx.LEVEL -> tones(listOf(523f to 0.1f, 659f to 0.1f, 784f to 0.1f, 1047f to 0.3f), 0.42f, 7f)
    }

    private fun tones(notes: List<Pair<Float, Float>>, volume: Float, decay: Float): ShortArray {
        val out = ArrayList<Short>()
        for ((freq, dur) in notes) {
            val n = (dur * RATE).toInt()
            for (i in 0 until n) {
                val t = i.toFloat() / RATE
                val env = minOf(1f, t / 0.003f) * exp(-t * decay)
                val x = 2 * PI.toFloat() * freq * t
                out.add(pcm((sin(x) + 0.3f * sin(2 * x)) / 1.3f * env * volume))
            }
        }
        return out.toShortArray()
    }

    private fun rustle(dur: Float, volume: Float): ShortArray {
        val rnd = Random(7)
        val n = (dur * RATE).toInt()
        var low = 0f
        return ShortArray(n) { i ->
            val t = i.toFloat() / RATE
            low += 0.35f * (rnd.nextFloat() * 2 - 1 - low)
            val am = 0.5f + 0.5f * kotlin.math.abs(sin(2 * PI.toFloat() * 18f * t))
            pcm(low * am * (1 - t / dur) * volume * 2.2f)
        }
    }

    private fun sweep(f0: Float, f1: Float, dur: Float, volume: Float): ShortArray {
        val n = (dur * RATE).toInt()
        var phase = 0f
        return ShortArray(n) { i ->
            val p = i.toFloat() / n
            phase += 2 * PI.toFloat() * (f0 + (f1 - f0) * p) / RATE
            val square = if (sin(phase) >= 0f) 1f else -1f
            pcm((0.7f * sin(phase) + 0.3f * square) * (1 - p) * volume)
        }
    }

    private fun buzz(freq: Float, dur: Float, volume: Float): ShortArray {
        val n = (dur * RATE).toInt()
        return ShortArray(n) { i ->
            val t = i.toFloat() / RATE
            val saw = 2f * ((t * freq) % 1f) - 1f
            pcm(saw * exp(-t * 6f) * volume)
        }
    }

    private fun pcm(v: Float): Short = (v.coerceIn(-1f, 1f) * 32767).toInt().toShort()

    /** Wraps PCM samples in a WAV container. */
    fun wav(samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        val b = ByteArray(44 + dataSize)
        fun str(at: Int, s: String) { for (i in s.indices) b[at + i] = s[i].code.toByte() }
        fun int(at: Int, v: Int) { for (i in 0 until 4) b[at + i] = (v shr (8 * i)).toByte() }
        fun short(at: Int, v: Int) { b[at] = v.toByte(); b[at + 1] = (v shr 8).toByte() }
        str(0, "RIFF"); int(4, 36 + dataSize); str(8, "WAVE")
        str(12, "fmt "); int(16, 16); short(20, 1); short(22, 1); int(24, RATE); int(28, RATE * 2); short(32, 2); short(34, 16)
        str(36, "data"); int(40, dataSize)
        for (i in samples.indices) short(44 + i * 2, samples[i].toInt())
        return b
    }
}
