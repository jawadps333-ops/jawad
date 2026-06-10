package com.example.game.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

object GameAudio {

    fun playTone(frequency: Float, durationMs: Int, type: String = "sine", volume: Float = 0.15f) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sampleRate = 8000
                val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
                if (numSamples <= 0) return@launch
                val samples = FloatArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toFloat() / sampleRate
                    samples[i] = when (type) {
                        "square" -> if (sin(2 * PI * frequency * t) >= 0) volume else -volume
                        "saw" -> {
                            val cycle = sampleRate / frequency
                            val step = i % cycle
                            val fraction = if (cycle > 0) step / cycle else 0f
                            (fraction * 2f - 1f) * volume
                        }
                        "noise" -> (Math.random().toFloat() * 2f - 1f) * volume
                        else -> sin(2 * PI * frequency * t).toFloat() * volume
                    }
                }

                val buffer = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    buffer[i] = (samples[i] * Short.MAX_VALUE).toInt().toShort()
                }

                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    buffer.size * 2,
                    AudioTrack.MODE_STATIC
                )

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                delay(durationMs.toLong() + 100)
                audioTrack.release()
            } catch (e: Exception) {
                // Squelch audio channel initialization warnings on specific old configurations
            }
        }
    }

    // Classic G-Funk bass/snare sequence for retro GTA look list loads
    fun playIntroBeats() {
        CoroutineScope(Dispatchers.Default).launch {
            // Stylized 4-step sequence
            playTone(82.4f, 250, "saw", 0.2f) // E2
            delay(300)
            playTone(110f, 150, "square", 0.15f) // A2
            delay(200)
            playTone(98f, 200, "saw", 0.15f) // G2
            delay(250)
            playTone(1000f, 100, "noise", 0.08f) // Retro snare click
        }
    }

    fun playHijackSound() {
        // Dramatic squeal then motor hum
        playTone(300f, 120, "square", 0.15f)
        CoroutineScope(Dispatchers.Default).launch {
            delay(100)
            playTone(180f, 400, "saw", 0.25f)
        }
    }

    fun playGunshot() {
        // Explosion/noise effect
        playTone(120f, 150, "noise", 0.35f)
    }

    fun playCashEarned() {
        // High register registry sound
        playTone(1568f, 100, "sine", 0.2f)
        CoroutineScope(Dispatchers.Default).launch {
            delay(80)
            playTone(2093f, 200, "sine", 0.2f)
        }
    }

    fun playHitNpc() {
        playTone(90f, 200, "saw", 0.25f)
    }
}
