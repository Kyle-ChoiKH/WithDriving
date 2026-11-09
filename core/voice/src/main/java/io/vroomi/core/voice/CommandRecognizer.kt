package io.vroomi.core.voice

import android.content.Context
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class ListenState { UNAVAILABLE, IDLE, LISTENING }

/**
 * Push-to-talk speech recognition with Pleos SpeechToText SDK 3.3.1 (never records continuously).
 * Public API read from the AAR's classes.jar:
 *   SpeechToText(context, mode), initialize(OnInitializeListener), addListener(ResultListener), setLocale(Locale),
 *   request(), stop(), release()
 *   ResultListener: onUpdated(text, isFinal), onUpdatedEpdData, onStartedRecognition, onEndedRecognition,
 *                   onError(ErrorType), onReady, onUpdatedRms
 * The microphone is owned by the Pleos speech service, so the app does not declare RECORD_AUDIO.
 * TODO(verify): RECORD_AUDIO requirement and the Boolean meaning of onUpdated on the target image:
 *   https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro
 */
class CommandRecognizer(context: Context, private val onFinalText: (String) -> Unit) {
    private val _state = MutableStateFlow(ListenState.UNAVAILABLE)
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val stt: SpeechToText? = try {
        SpeechToText(context, Mode.ON_DEVICE)
    } catch (t: Throwable) {
        Log.i(TAG, "Pleos STT unavailable: ${t.message}"); null
    }

    init {
        stt?.let { s ->
            runCatching {
                s.addListener(object : ResultListener {
                    override fun onUpdated(text: String, isFinal: Boolean) {
                        _partial.value = text
                        if (isFinal && text.isNotBlank()) {
                            onFinalText(text)
                            _state.value = ListenState.IDLE
                        }
                    }

                    override fun onUpdatedEpdData(epdOn: Long, epdOff: Long) {}
                    override fun onStartedRecognition() { _state.value = ListenState.LISTENING }
                    override fun onEndedRecognition() { _state.value = ListenState.IDLE }
                    override fun onError(errorType: ErrorType) {
                        Log.w(TAG, "stt error $errorType"); _state.value = ListenState.IDLE
                    }

                    override fun onReady() {}
                    override fun onUpdatedRms(rms: Double) {}
                })
                s.initialize(object : OnInitializeListener {
                    override fun onSuccess() {
                        runCatching { s.setLocale(Locale.KOREAN) }
                        _state.value = ListenState.IDLE
                    }

                    override fun onFailure(errorType: ErrorType) {
                        Log.i(TAG, "STT init failed: $errorType")
                        _state.value = ListenState.UNAVAILABLE
                    }
                })
            }
        }
    }

    fun listen() {
        val s = stt ?: return
        if (_state.value != ListenState.IDLE) return
        _partial.value = ""
        _state.value = ListenState.LISTENING
        runCatching { s.request() }.onFailure { _state.value = ListenState.IDLE }
    }

    fun cancel() {
        runCatching { stt?.stop() }
        if (_state.value == ListenState.LISTENING) _state.value = ListenState.IDLE
    }

    fun release() { runCatching { stt?.release() } }

    private companion object { const val TAG = "VroomiStt" }
}
