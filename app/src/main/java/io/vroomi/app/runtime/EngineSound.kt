package io.vroomi.app.runtime

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import io.vroomi.character.engine.EngineMechanics
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Optional sound for the Cutaway theme, synthesised in code (no sample files): a very quiet looping hum
 * whose pitch follows the estimated rpm (ICE only; silent for an EV), a click when the A/C clutch engages
 * and the blinker relay click. Off by default and only reachable from the developer panel, because
 * audio-focus behaviour on Pleos Connect has not been verified yet. It never imitates a real engine.
 */
class EngineSound {
    private val rate = 22_050
    /** One second of the hum at the idle firing order (2 × 750 / 60 = 25 Hz fundamental + overtones). */
    private val hum: AudioTrack? = loop(humSamples())
    private val clutch = oneShot(click(0.010, 0.10, 5))
    private val relay = oneShot(click(0.004, 0.08, 9))
    private var humOn = false

    /** [rpm] 0 = engine stopped or EV. [ducked]: navigation guidance, a safety warning or mute -> silence. */
    fun onFrame(events: List<EngineMechanics.Event>, rpm: Double, ducked: Boolean) {
        val want = !ducked && rpm > 300
        hum?.let { t ->
            runCatching {
                if (want) {
                    t.playbackRate = (rate * (rpm / 750.0)).toInt().coerceIn(rate / 2, rate * 6)
                    if (!humOn) { t.play(); humOn = true }
                } else if (humOn) { t.pause(); humOn = false }
            }
        }
        if (ducked) return
        for (e in events) when (e) {
            EngineMechanics.Event.CLUTCH_ON -> fire(clutch)
            EngineMechanics.Event.RELAY -> fire(relay)
            else -> Unit
        }
    }

    fun release() = listOf(hum, clutch, relay).forEach { runCatching { it?.release() } }

    private fun fire(t: AudioTrack?) {
        t ?: return
        runCatching { t.stop(); t.reloadStaticData(); t.play() }
    }

    private fun attrs() = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    private fun build(samples: ShortArray): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(attrs())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(samples.size * 2)
        .build().also { it.write(samples, 0, samples.size) }

    private fun oneShot(samples: ShortArray): AudioTrack? = runCatching { build(samples) }.getOrNull()

    private fun loop(samples: ShortArray): AudioTrack? = runCatching {
        build(samples).also { it.setLoopPoints(0, samples.size, -1) }
    }.getOrNull()

    /** A soft, low hum: whole periods of 25 Hz so the loop is seamless. Very quiet on purpose. */
    private fun humSamples(): ShortArray {
        val n = rate // one second = 25 whole periods
        return ShortArray(n) { i ->
            val t = i / rate.toDouble()
            val v = sin(2 * PI * 25 * t) * 0.55 + sin(2 * PI * 50 * t) * 0.30 + sin(2 * PI * 100 * t) * 0.15
            (Short.MAX_VALUE * 0.05 * v).toInt().toShort()
        }
    }

    private fun click(seconds: Double, volume: Double, seed: Int): ShortArray {
        val n = (rate * seconds).toInt().coerceAtLeast(16)
        val r = Random(seed)
        return ShortArray(n) { i -> (Short.MAX_VALUE * volume * exp(-i / (n / 4.0)) * (r.nextDouble() * 2 - 1)).toInt().toShort() }
    }
}
