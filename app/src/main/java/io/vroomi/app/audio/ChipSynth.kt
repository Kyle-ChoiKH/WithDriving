package io.vroomi.app.audio

import io.vroomi.character.pixel.PixelSound
import kotlin.math.pow

/**
 * The chiptune voices of Pixel World (pure Kotlin, no samples, no allocation while rendering), as on old hardware:
 * two pulse channels (duty 12.5 / 25 / 50 %), one triangle and one noise channel (15-bit LFSR). Each channel is
 * monophonic; a note sets its pitch, length and a short envelope.
 */
class ChipSynth(private val rate: Int = 48_000) : ThemeSynth {
    private val phase = DoubleArray(4)
    private val inc = DoubleArray(4)
    private val left = IntArray(4)
    private val amp = FloatArray(4)
    private val level = FloatArray(4)
    private val duty = DoubleArray(4) { 0.25 }
    private var lfsr = 1
    private var noiseAcc = 0.0
    private var noiseOut = 1f

    override fun noteOn(midi: Int, vel: Float, kind: Int) {
        val ch = PixelSound.channel(kind)
        val f = 440.0 * 2.0.pow((midi - 69) / 12.0)
        inc[ch] = f / rate
        left[ch] = (PixelSound.lengthMs(kind) * rate / 1000).coerceAtLeast(1)
        level[ch] = vel * when (ch) { PixelSound.TRIANGLE -> 0.9f; PixelSound.NOISE -> 0.35f; else -> 0.5f }
        duty[ch] = when (PixelSound.duty(kind)) { 0 -> 0.125; 2 -> 0.5; else -> 0.25 }
        if (ch == PixelSound.NOISE) phase[ch] = 0.0
    }

    override fun render(out: FloatArray, n: Int) {
        for (i in 0 until n) {
            var sum = 0f
            for (ch in 0 until 4) {
                // Envelope: 2 ms attack, hold, 25 ms release after the note's length.
                val target = if (left[ch] > 0) level[ch] else 0f
                val step = if (target > amp[ch]) 1f / (0.002f * rate) else 1f / (0.025f * rate)
                amp[ch] = if (amp[ch] < target) minOf(target, amp[ch] + step * level[ch].coerceAtLeast(0.1f)) else maxOf(target, amp[ch] - step * 0.6f)
                if (left[ch] > 0) left[ch]--
                if (amp[ch] < 1e-5f) continue
                phase[ch] += inc[ch]
                if (phase[ch] >= 1.0) phase[ch] -= 1.0
                val p = phase[ch]
                val s = when (ch) {
                    PixelSound.PULSE_A, PixelSound.PULSE_B -> if (p < duty[ch]) 1f else -1f
                    PixelSound.TRIANGLE -> (if (p < 0.5) 4.0 * p - 1.0 else 3.0 - 4.0 * p).toFloat()
                    else -> {
                        noiseAcc += inc[ch] * 8.0
                        while (noiseAcc >= 1.0) {
                            noiseAcc -= 1.0
                            val bit = (lfsr xor (lfsr shr 1)) and 1
                            lfsr = (lfsr shr 1) or (bit shl 14)
                            noiseOut = if (lfsr and 1 == 1) 1f else -1f
                        }
                        noiseOut
                    }
                }
                sum += s * amp[ch]
            }
            out[i] = sum * 0.35f
        }
    }
}
