package io.vroomi.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale

/**
 * Pleos TextToSpeech SDK 3.3.1 (ai.pleos.playground:TextToSpeech:3.3.1).
 * Public API read from the AAR's classes.jar (no sources are published):
 *   TextToSpeech(context, mode: Mode = ...), initialize(OnInitializeListener), addEventListener(EventListener),
 *   setLocale(Locale), setAudioAttributes(AudioAttributes), speak(text: String, flush: Boolean), stop(), release()
 *   OnInitializeListener.onSuccess()/onFailure(ErrorType); EventListener.onStart/onReady/onDone/onError/onStop/onUpdatedRms
 * TODO(verify): the meaning of speak()'s Boolean (assumed "flush queue") and whether registerApp(...) is required
 * for ON_DEVICE mode on the target image: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro
 */
class PleosTtsSpeaker(context: Context, private val onReady: (Boolean) -> Unit = {}) : Speaker {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    @Volatile var ready = false
        private set
    private val tts: TextToSpeech? = try {
        TextToSpeech(context, Mode.ON_DEVICE)
    } catch (t: Throwable) {
        Log.i(TAG, "Pleos TTS unavailable: ${t.message}"); null
    }

    init {
        tts?.let { t ->
            runCatching {
                t.addEventListener(object : EventListener {
                    override fun onStart() {}
                    override fun onReady() {}
                    override fun onDone() { abandon() }
                    override fun onError(errorType: ErrorType) { Log.w(TAG, "tts error $errorType"); abandon() }
                    override fun onStop() { abandon() }
                    override fun onUpdatedRms(rms: Double) {}
                })
                t.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        ready = true
                        runCatching { t.setLocale(Locale.KOREAN) }
                        runCatching { t.setAudioAttributes(attributes) }
                        onReady(true)
                    }

                    override fun onFailure(errorType: ErrorType) {
                        Log.i(TAG, "Pleos TTS init failed: $errorType"); onReady(false)
                    }
                })
            }.onFailure { onReady(false) }
        } ?: onReady(false)
    }

    override fun speak(text: String) {
        val t = tts ?: return
        if (!ready) return
        audio?.requestAudioFocus(focus)
        runCatching { t.speak(text, true) }.onFailure { abandon() }
    }

    override fun stop() {
        runCatching { tts?.stop() }
        abandon()
    }

    override fun release() {
        runCatching { tts?.release() }
        abandon()
    }

    private fun abandon() {
        runCatching { audio?.abandonAudioFocusRequest(focus) }
    }

    private companion object { const val TAG = "VroomiTts" }
}

/** Android platform TTS, used when the Pleos speech service is not present (plain emulator images). */
class AndroidTtsSpeaker(context: Context) : Speaker {
    @Volatile private var ready = false
    private val tts: android.speech.tts.TextToSpeech = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
        ready = status == android.speech.tts.TextToSpeech.SUCCESS
    }.also { it.language = Locale.KOREAN }

    override fun speak(text: String) {
        if (ready) tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "vroomi")
    }

    override fun stop() { tts.stop() }
    override fun release() { tts.shutdown() }
}

/** Uses Pleos TTS when it initialized, otherwise the platform TTS. */
class FallbackSpeaker(private val primary: PleosTtsSpeaker, private val fallback: Speaker) : Speaker {
    override fun speak(text: String) = if (primary.ready) primary.speak(text) else fallback.speak(text)
    override fun stop() { primary.stop(); fallback.stop() }
    override fun release() { primary.release(); fallback.release() }
}
