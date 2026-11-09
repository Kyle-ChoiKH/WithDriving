package io.vroomi.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pure-Kotlin synthesis for the theme sounds (no samples, no Android, no allocation while rendering).
 * Every voice is preallocated; [render] fills a mono float buffer.
 */
interface ThemeSynth {
    /** [midi] note (ignored by percussive kinds), [vel] 0..1, [kind] instrument-specific. */
    fun noteOn(midi: Int, vel: Float, kind: Int)
    fun render(out: FloatArray, n: Int)
}

/** Output stage: a soft limiter whose peak can never exceed −14 dBFS (0.2). */
object SoftLimiter {
    const val CEILING = 0.1995f
    fun apply(x: Float): Float = CEILING * x / (1f + abs(x))
}

/**
 * The music box: a modal tine (a clamped-free bar, partials 1 : 6.267 : 17.55 with amplitudes 1 : 0.25 : 0.06 and
 * decays of about 2.2 / 0.35 / 0.08 s at middle C, shorter as the pitch rises) plus a 3 ms high-passed pin click;
 * a soft ratchet tick and a blinker tick (filtered noise and a short wooden "tock").
 */
class MusicBoxSynth(private val rate: Int = 48_000) : ThemeSynth {
    private val voices = 24
    private val partials = 3
    // Rotating phasors: (re, im) advanced by (c, s) every sample; amplitude decays by d.
    private val re = FloatArray(voices * partials)
    private val im = FloatArray(voices * partials)
    private val c = FloatArray(voices * partials)
    private val s = FloatArray(voices * partials)
    private val amp = FloatArray(voices * partials)
    private val decay = FloatArray(voices * partials)
    private val clickLeft = IntArray(voices)
    private val clickAmp = FloatArray(voices)
    private val age = IntArray(voices)
    private var noise = 0x2545F491
    private var lastNoise = 0f
    // Percussion (one ticking voice).
    private var tickLeft = 0
    private var tickAmp = 0f
    private var tockRe = 0f; private var tockIm = 0f; private var tockC = 1f; private var tockS = 0f; private var tockAmp = 0f; private var tockDecay = 1f

    override fun noteOn(midi: Int, vel: Float, kind: Int) {
        if (kind != 0) { tick(vel, if (kind == 1) 700f else 1100f); return }
        val f = (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()
        // Steal the quietest voice.
        var v = 0; var best = Float.MAX_VALUE
        for (i in 0 until voices) { val a = amp[i * partials]; val score = a - age[i] * 1e-7f; if (score < best) { best = score; v = i } }
        val pitchK = (261.63f / f).toDouble().pow(0.55).toFloat()
        for (p in 0 until partials) {
            val k = v * partials + p
            val fp = f * RATIO[p]
            if (fp > rate * 0.45f) { amp[k] = 0f; continue }
            val w = (2 * PI * fp / rate)
            c[k] = cos(w).toFloat(); s[k] = sin(w).toFloat()
            re[k] = 0f; im[k] = 1f
            amp[k] = vel * AMPS[p]
            decay[k] = exp(-1.0 / (DECAYS[p] * pitchK * rate)).toFloat()
        }
        clickLeft[v] = (rate * 0.003f).toInt(); clickAmp[v] = 0.08f * vel; age[v] = 0
    }

    companion object {
        /** Clamped-free bar partials, their amplitudes and decay times (s) at middle C. */
        val RATIO = floatArrayOf(1f, 6.267f, 17.55f)
        val AMPS = floatArrayOf(1f, 0.25f, 0.06f)
        val DECAYS = floatArrayOf(2.2f, 0.35f, 0.08f)
    }

    private fun tick(vel: Float, hz: Float) {
        tickLeft = (rate * 0.006f).toInt(); tickAmp = 0.12f * vel
        val w = 2 * PI * hz / rate
        tockC = cos(w).toFloat(); tockS = sin(w).toFloat(); tockRe = 0f; tockIm = 1f
        tockAmp = 0.18f * vel; tockDecay = exp(-1.0 / (0.012 * rate)).toFloat()
    }

    private fun nextNoise(): Float {
        var x = noise
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        noise = x
        return (x and 0xFFFF) / 32768f - 1f
    }

    override fun render(out: FloatArray, n: Int) {
        for (i in 0 until n) {
            var sum = 0f
            for (v in 0 until voices) {
                age[v]++
                for (p in 0 until partials) {
                    val k = v * partials + p
                    val a = amp[k]
                    if (a < 1e-5f) continue
                    val r = re[k]; val m = im[k]
                    re[k] = r * c[k] - m * s[k]
                    im[k] = r * s[k] + m * c[k]
                    sum += im[k] * a
                    amp[k] = a * decay[k]
                }
                if (clickLeft[v] > 0) {
                    val nz = nextNoise()
                    sum += (nz - lastNoise) * clickAmp[v]   // first difference = a simple high-pass
                    lastNoise = nz
                    clickLeft[v]--
                }
            }
            if (tickLeft > 0) { val nz = nextNoise(); sum += (nz - lastNoise) * tickAmp; lastNoise = nz; tickLeft-- }
            if (tockAmp > 1e-5f) {
                val r = tockRe; val m = tockIm
                tockRe = r * tockC - m * tockS; tockIm = r * tockS + m * tockC
                sum += tockIm * tockAmp; tockAmp *= tockDecay
            }
            // Keep the phasors on the unit circle (tiny drift correction every 256 samples).
            out[i] = sum * 0.5f
        }
        renormalise()
    }

    private fun renormalise() {
        for (k in re.indices) {
            val m = re[k] * re[k] + im[k] * im[k]
            if (m > 0f) { val g = (1.5f - 0.5f * m); re[k] *= g; im[k] *= g }
        }
        val m = tockRe * tockRe + tockIm * tockIm
        if (m > 0f) { val g = 1.5f - 0.5f * m; tockRe *= g; tockIm *= g }
    }
}
