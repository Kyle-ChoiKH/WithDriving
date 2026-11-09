package io.vroomi.app.audio

/**
 * What the optional theme sound may do, as a pure state machine (unit-tested without Android):
 * - it plays only while the user's toggle is on, the theme is shown and the app is in the foreground;
 * - a transient focus loss pauses it, "can duck" (and the app's own TTS or navigation guidance) drops it to 20 %;
 * - a permanent loss stops it until the user opens the theme again; going to the background stops it;
 * - a safety warning fades it out over 300 ms.
 */
class AudioFocusPolicy {
    enum class Focus { NONE, GAIN, LOSS_TRANSIENT, CAN_DUCK, LOSS }
    enum class State { IDLE, PLAYING, PAUSED, DUCKED, LOST }

    var state = State.IDLE; private set
    private var focus = Focus.NONE
    private var wanted = false
    private var externalDuck = false
    private var safety = false

    /** The toggle is on, the theme is shown and the app is in the foreground. */
    fun want(on: Boolean) {
        if (!on) { wanted = false; if (state != State.LOST) state = State.IDLE; focus = Focus.NONE; return }
        wanted = true
        if (state == State.IDLE && focus == Focus.GAIN) state = State.PLAYING
    }

    /** The user reopened the theme: a permanent loss may be retried. */
    fun reopen() { if (state == State.LOST) { state = State.IDLE; focus = Focus.NONE } }

    /** Should focus be requested now? */
    val needsFocus: Boolean get() = wanted && state == State.IDLE && focus == Focus.NONE

    fun onFocus(f: Focus) {
        focus = f
        state = when (f) {
            Focus.GAIN -> if (wanted) State.PLAYING else State.IDLE
            Focus.LOSS_TRANSIENT -> if (state == State.LOST) State.LOST else State.PAUSED
            Focus.CAN_DUCK -> if (state == State.LOST) State.LOST else State.DUCKED
            Focus.LOSS -> State.LOST
            Focus.NONE -> state
        }
    }

    fun onBackground() { wanted = false; if (state != State.LOST) state = State.IDLE; focus = Focus.NONE }

    fun setExternalDuck(on: Boolean) { externalDuck = on }
    fun setSafety(on: Boolean) { safety = on }

    /** Should the audio thread run (it may still be silent)? */
    val running: Boolean get() = state == State.PLAYING || state == State.PAUSED || state == State.DUCKED

    /** Target output gain 0..1. */
    fun targetGain(): Float = when {
        safety -> 0f
        state == State.PLAYING -> if (externalDuck) DUCK else 1f
        state == State.DUCKED -> DUCK
        else -> 0f
    }

    /** Seconds to reach the target from the current gain (fade lengths). */
    fun rampSeconds(fromGain: Float, toGain: Float): Float = when {
        safety -> 0.3f
        toGain > fromGain && fromGain < 0.01f -> FADE_IN_S
        else -> 0.15f
    }

    companion object {
        const val DUCK = 0.2f
        const val FADE_IN_S = 1.5f
    }
}
