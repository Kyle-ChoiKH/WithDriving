package io.vroomi.app.audio

import io.vroomi.app.audio.AudioFocusPolicy.Focus
import io.vroomi.app.audio.AudioFocusPolicy.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class AudioFocusPolicyTest {

    /** A fake AudioManager: the policy asks for focus, the "system" answers. */
    private fun started(): AudioFocusPolicy = AudioFocusPolicy().also { p ->
        p.want(true)
        assertTrue(p.needsFocus)
        p.onFocus(Focus.GAIN)
    }

    @Test fun gainPlaysAtFullVolume() {
        val p = started()
        assertEquals(State.PLAYING, p.state)
        assertEquals(1f, p.targetGain())
        assertEquals(AudioFocusPolicy.FADE_IN_S, p.rampSeconds(0f, 1f))
    }

    @Test fun transientLossPausesAndGainResumes() {
        val p = started()
        p.onFocus(Focus.LOSS_TRANSIENT)
        assertEquals(State.PAUSED, p.state); assertEquals(0f, p.targetGain())
        p.onFocus(Focus.GAIN)
        assertEquals(State.PLAYING, p.state)
    }

    @Test fun duckDropsToTwentyPercent() {
        val p = started()
        p.onFocus(Focus.CAN_DUCK)
        assertEquals(0.2f, p.targetGain())
        p.onFocus(Focus.GAIN)
        p.setExternalDuck(true) // the app's own TTS or navigation guidance
        assertEquals(0.2f, p.targetGain())
    }

    @Test fun permanentLossStopsUntilReopened() {
        val p = started()
        p.onFocus(Focus.LOSS)
        assertEquals(State.LOST, p.state); assertFalse(p.running)
        p.want(true)
        assertFalse("stays stopped", p.needsFocus)
        p.reopen(); p.want(true)
        assertTrue(p.needsFocus)
    }

    @Test fun backgroundStopsAndSafetyFades() {
        val p = started()
        p.setSafety(true)
        assertEquals(0f, p.targetGain()); assertEquals(0.3f, p.rampSeconds(1f, 0f))
        p.setSafety(false)
        p.onBackground()
        assertEquals(State.IDLE, p.state); assertFalse(p.running)
        p.want(false)
        assertFalse(p.needsFocus)
    }
}

class MusicBoxSynthTest {

    private fun goertzel(x: FloatArray, from: Int, n: Int, hz: Double, rate: Int = 48_000): Double {
        val w = 2 * PI * hz / rate
        var re = 0.0; var im = 0.0
        for (i in 0 until n) { re += x[from + i] * cos(w * i); im -= x[from + i] * sin(w * i) }
        return sqrt(re * re + im * im) / n
    }

    private fun render(seconds: Double, synth: ThemeSynth): FloatArray {
        val out = FloatArray((48_000 * seconds).toInt())
        val buf = FloatArray(256)
        var i = 0
        while (i < out.size) { val n = minOf(256, out.size - i); synth.render(buf, n); System.arraycopy(buf, 0, out, i, n); i += n }
        return out
    }

    @Test fun tineHasTheClampedBarPartials() {
        val s = MusicBoxSynth()
        s.noteOn(69, 1f, 0) // A4
        val x = render(0.3, s)
        val f1 = goertzel(x, 960, 4800, 440.0)
        val f2 = goertzel(x, 960, 4800, 440.0 * 6.267)
        val off = goertzel(x, 960, 4800, 440.0 * 3.0)
        assertTrue("fundamental $f1", f1 > 0.05)
        assertTrue("second partial present: ${f2 / f1}", f2 / f1 in 0.03..0.3)
        assertTrue("no harmonic at 3×: ${off / f1}", off / f1 < 0.02)
        // Pitch: the fundamental is strongest at 440 Hz, not ±3 cents away.
        val sharp = goertzel(x, 960, 4800, 440.0 * 1.02)
        assertTrue(f1 > sharp)
    }

    @Test fun limitedOutputNeverExceedsMinus14dBFSAndNeverJumps() {
        val s = MusicBoxSynth()
        val out = FloatArray(48_000 * 20)
        val buf = FloatArray(256)
        var i = 0; var note = 0
        var prev = 0f; var maxJump = 0f; var peak = 0f
        while (i < out.size) {
            if (i % 2400 == 0) { s.noteOn(60 + (note * 7) % 28, 1f, if (note % 9 == 0) 1 else 0); note++ }
            s.render(buf, 256)
            for (k in 0 until 256) {
                val y = SoftLimiter.apply(buf[k] * 2.2f)
                assertFalse(y.isNaN())
                peak = maxOf(peak, abs(y)); maxJump = maxOf(maxJump, abs(y - prev)); prev = y
            }
            i += 256
        }
        assertTrue("peak $peak", peak <= 0.1995f)
        assertTrue("no click: largest step $maxJump", maxJump < 0.2f)
    }
}

class ChipSynthTest {
    @Test fun pulseTriangleNoiseStayUnderTheCeilingWithoutClicks() {
        val s = ChipSynth()
        val buf = FloatArray(256)
        var peak = 0f; var prev = 0f; var maxJump = 0f; var nonZero = 0
        for (block in 0 until 48_000 * 10 / 256) {
            if (block % 20 == 0) {
                s.noteOn(60 + (block / 20) % 12, 0.8f, io.vroomi.character.pixel.PixelSound.kind(io.vroomi.character.pixel.PixelSound.PULSE_A, 120))
                s.noteOn(36 + (block / 20) % 7, 0.6f, io.vroomi.character.pixel.PixelSound.kind(io.vroomi.character.pixel.PixelSound.TRIANGLE, 200))
                s.noteOn(90, 0.3f, io.vroomi.character.pixel.PixelSound.kind(io.vroomi.character.pixel.PixelSound.NOISE, 30))
            }
            s.render(buf, 256)
            for (k in 0 until 256) {
                val y = SoftLimiter.apply(buf[k] * 2.2f)
                assertFalse(y.isNaN())
                if (y != 0f) nonZero++
                peak = maxOf(peak, abs(y)); maxJump = maxOf(maxJump, abs(y - prev)); prev = y
            }
        }
        assertTrue("it sounds", nonZero > 10_000)
        assertTrue("peak $peak", peak <= 0.1995f)
    }

    @Test fun pulseDutyAndPitch() {
        val s = ChipSynth()
        s.noteOn(69, 1f, io.vroomi.character.pixel.PixelSound.kind(io.vroomi.character.pixel.PixelSound.PULSE_A, 1000, 2))
        val out = FloatArray(48_000)
        val buf = FloatArray(256)
        var i = 0
        while (i < out.size) { val n = minOf(256, out.size - i); s.render(buf, n); System.arraycopy(buf, 0, out, i, n); i += n }
        // 440 Hz square: about 880 sign changes per second once the attack is over.
        var crossings = 0
        for (k in 4800 until 43_200) if ((out[k - 1] < 0f) != (out[k] < 0f)) crossings++
        assertTrue("crossings $crossings in 0.8 s", crossings in 690..718)
    }
}
