package io.github.skitknasctrl.mathchallange1

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File

/** Plays the synthesized effects through a SoundPool (written once to the app's cache folder). */
class Sound(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ids = IntArray(Sfx.values().size)
    var enabled = true

    init {
        for (s in Sfx.values()) {
            ids[s.ordinal] = try {
                val f = File(context.cacheDir, "sfx_${s.name.lowercase()}.wav")
                f.writeBytes(Synth.wav(Synth.make(s)))
                pool.load(f.path, 1)
            } catch (e: Exception) {
                0
            }
        }
    }

    fun play(s: Sfx) {
        val id = ids[s.ordinal]
        if (enabled && id != 0) pool.play(id, 0.7f, 0.7f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
