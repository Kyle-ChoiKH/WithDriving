package io.vroomi.app.runtime

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import io.vroomi.character.calibre.CalibreMechanics
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Optional mechanical sound for the Calibre theme, synthesised in code (no sample files):
 * a very quiet 8 Hz tick while parked, ratchet clicks while the crown winds, gong tones for the
 * blinker and the arrival chime. Off by default and only reachable from the developer panel,
 * because audio-focus behaviour on Pleos Connect has not been verified yet.
 */
class MechanicalSound {
    private val rate = 22_050
    private val tick = track(click(0.003, 0.05, 3))
    private val ratchet = track(click(0.006, 0.12, 7))
    private val gongLow = track(gong(660.0, 0.9, 0.22))
    private val gongHigh = track(gong(990.0, 0.8, 0.20))
    private var lastTickMs = 0L
    private var lastClickMs = 0L

    /** [ducked]: navigation guidance, a voice line or mute -> silence. */
    fun onFrame(events: List<CalibreMechanics.Event>, parked: Boolean, ducked: Boolean, nowMs: Long) {
        if (ducked) return
        if (parked && nowMs - lastTickMs >= 125) { lastTickMs = nowMs; fire(tick) }
        for (e in events) when (e) {
            CalibreMechanics.Event.CLICK -> if (nowMs - lastClickMs >= 60) { lastClickMs = nowMs; fire(ratchet) }
            CalibreMechanics.Event.HAMMER_LEFT -> fire(gongLow)
            CalibreMechanics.Event.HAMMER_RIGHT -> fire(gongHigh)
            else -> Unit
        }
    }

    fun release() = listOf(tick, ratchet, gongLow, gongHigh).forEach { runCatching { it?.release() } }

    private fun fire(t: AudioTrack?) {
        t ?: return
        runCatching { t.stop(); t.reloadStaticData(); t.play() }
    }

    private fun track(samples: ShortArray): AudioTrack? = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build().also { it.write(samples, 0, samples.size) }
    }.getOrNull()

    /** A short decaying noise burst (escapement tick / click). */
    private fun click(seconds: Double, volume: Double, seed: Int): ShortArray {
        val n = (rate * seconds).toInt().coerceAtLeast(16)
        val r = Random(seed)
        return ShortArray(n) { i -> (Short.MAX_VALUE * volume * exp(-i / (n / 4.0)) * (r.nextDouble() * 2 - 1)).toInt().toShort() }
    }

    /** A gong: a fundamental and an inharmonic partial with a long decay. */
    private fun gong(hz: Double, seconds: Double, volume: Double): ShortArray {
        val n = (rate * seconds).toInt()
        return ShortArray(n) { i ->
            val t = i / rate.toDouble()
            val v = sin(2 * PI * hz * t) * 0.7 + sin(2 * PI * hz * 2.76 * t) * 0.3
            (Short.MAX_VALUE * volume * v * exp(-t * 4.0) * (1 - exp(-t * 400))).toInt().toShort()
        }
    }
}
