package io.vroomi.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.max

/**
 * The optional theme sound (default OFF): one streaming AudioTrack (48 kHz mono float) fed by a dedicated
 * render thread that never allocates. Notes arrive through a lock-free single-producer ring. Audio focus uses
 * the standard Android AudioManager (no Pleos API) through [AudioFocusPolicy]. Pleos' own audio-focus
 * behaviour is not verified yet (see docs/submission-checklist.md).
 */
class ThemeAudioEngine(context: Context, private val synth: ThemeSynth) {
    private val am = context.getSystemService(AudioManager::class.java)
    val policy = AudioFocusPolicy()

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attrs)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { change ->
            synchronized(policy) {
                policy.onFocus(when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> AudioFocusPolicy.Focus.GAIN
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> AudioFocusPolicy.Focus.LOSS_TRANSIENT
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AudioFocusPolicy.Focus.CAN_DUCK
                    else -> AudioFocusPolicy.Focus.LOSS
                })
                targetGain = policy.targetGain()
            }
        }
        .build()

    // Lock-free note ring (main thread writes, audio thread reads).
    private val qMidi = IntArray(Q); private val qVel = FloatArray(Q); private val qKind = IntArray(Q)
    @Volatile private var qHead = 0
    @Volatile private var qTail = 0

    @Volatile private var targetGain = 0f
    @Volatile private var rampS = AudioFocusPolicy.FADE_IN_S
    @Volatile private var running = false
    private var thread: Thread? = null
    private var hasFocus = false

    /** Queue a note (dropped when the ring is full or the sound is not running). */
    fun note(midi: Int, vel: Float, kind: Int) {
        if (!running) return
        val h = qHead
        if (h - qTail >= Q) return
        val i = h and (Q - 1)
        qMidi[i] = midi; qVel[i] = vel; qKind[i] = kind
        qHead = h + 1
    }

    /**
     * Called every frame by the app: [wanted] = toggle on, theme shown and app in the foreground (and not muted);
     * [duck] = the app's TTS or navigation guidance; [safety] = a safety warning.
     */
    fun update(wanted: Boolean, duck: Boolean, safety: Boolean) {
        synchronized(policy) {
            policy.want(wanted)
            policy.setExternalDuck(duck)
            policy.setSafety(safety)
            if (policy.needsFocus) {
                val r = runCatching { am.requestAudioFocus(focusRequest) }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
                hasFocus = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                policy.onFocus(if (hasFocus) AudioFocusPolicy.Focus.GAIN else AudioFocusPolicy.Focus.LOSS_TRANSIENT)
            }
            val g = policy.targetGain()
            rampS = policy.rampSeconds(currentGain, g)
            targetGain = g
            if (policy.running && !running) start()
            if (!policy.running && running) stop()
        }
    }

    /** The user opened the theme again: a permanent focus loss may be retried. */
    fun reopen() = synchronized(policy) { policy.reopen() }

    fun release() {
        synchronized(policy) { policy.onBackground() }
        stop()
    }

    @Volatile private var currentGain = 0f

    private fun start() {
        val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(max(minBuf, BLOCK * 4 * 4))
                .build()
        }.getOrNull() ?: return
        running = true
        currentGain = 0f
        thread = Thread({ renderLoop(track) }, "vroomi-theme-audio").also { it.priority = Thread.MAX_PRIORITY; it.start() }
    }

    private fun stop() {
        running = false
        thread?.join(500)
        thread = null
        if (hasFocus) { runCatching { am.abandonAudioFocusRequest(focusRequest) }; hasFocus = false }
    }

    private fun renderLoop(track: AudioTrack) {
        val buf = FloatArray(BLOCK)
        runCatching { track.play() }
        var gain = 0f
        while (running) {
            // Drain notes.
            while (qTail < qHead) {
                val i = qTail and (Q - 1)
                synth.noteOn(qMidi[i], qVel[i], qKind[i])
                qTail++
            }
            synth.render(buf, BLOCK)
            val target = targetGain
            val step = 1f / (max(0.02f, rampS) * RATE)
            for (k in 0 until BLOCK) {
                gain = if (gain < target) minOf(target, gain + step) else maxOf(target, gain - step)
                buf[k] = SoftLimiter.apply(buf[k] * 2.2f) * gain
            }
            currentGain = gain
            runCatching { track.write(buf, 0, BLOCK, AudioTrack.WRITE_BLOCKING) }
        }
        runCatching { track.pause(); track.flush(); track.release() }
    }

    companion object {
        const val RATE = 48_000
        const val BLOCK = 256
        private const val Q = 256
    }
}
