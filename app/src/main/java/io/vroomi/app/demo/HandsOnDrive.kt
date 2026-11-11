package io.vroomi.app.demo

import io.vroomi.character.calibre.SceneTheme
import io.vroomi.core.vehicle.model.DriveModeState
import io.vroomi.core.vehicle.model.GearState
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.core.vehicle.model.VehicleSnapshot
import io.vroomi.core.vehicle.sim.PARKED_CAR
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * The hands-on demo: the simulated car drives by itself at an easy speed and the user works its controls from a
 * strip of buttons (faster, brake, blinkers, wipers, lights, climate, drive modes). Each press changes the simulated
 * car's signals the way the real control would, and the caption says what the present theme does with it, so the
 * scene answers the user's own hand. Only controls the theme reacts to are offered ([controls]).
 *
 * Pure logic: [apply] receives every change to the simulated car, [tick] is called about ten times a second.
 */
class HandsOnDrive(private val apply: ((VehicleSnapshot) -> VehicleSnapshot) -> Unit) {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    /** The controls that are on (the momentary ones only while they act). */
    private val _on = MutableStateFlow<Set<Cue>>(emptySet())
    val on: StateFlow<Set<Cue>> = _on.asStateFlow()
    private val _caption = MutableStateFlow<String?>(null)
    val caption: StateFlow<String?> = _caption.asStateFlow()
    /** The controls of the present theme, in strip order. */
    var controls: List<Cue> = emptyList(); private set

    private var theme = SceneTheme.KART
    private var speed = 0f
    private var target = CRUISE_KMH
    /** km/h per second while slowing down (a harsh stop is quicker). */
    private var slowing = BRAKE_RATE
    private var blinkLeft = 0f
    private var clock = 0f
    private var captionAt = 0f
    private var rest = 0f

    fun start(theme: SceneTheme) {
        this.theme = theme
        controls = CONTROLS.filter { ThemeReactions.of(theme, it) != null }
        speed = 0f; target = CRUISE_KMH; slowing = BRAKE_RATE; blinkLeft = 0f; clock = 0f; rest = 0f
        _on.value = emptySet()
        apply { PARKED_CAR.copy(gear = GearState.DRIVE, speedKmh = 0f, evBatteryPct = it.evBatteryPct ?: PARKED_CAR.evBatteryPct) }
        say(Cue.START)
        _running.value = true
    }

    /** Back to a parked car with everything off. */
    fun stop() {
        if (!_running.value) return
        _running.value = false
        _on.value = emptySet()
        _caption.value = null
        apply { PARKED_CAR.copy(evBatteryPct = it.evBatteryPct ?: PARKED_CAR.evBatteryPct) }
    }

    /** The user pressed [cue]'s button. */
    fun press(cue: Cue) {
        if (!_running.value || cue !in controls) return
        when (cue) {
            Cue.ACCEL -> { target = min(max(target, speed) + STEP_KMH, MAX_KMH); momentary(cue) }
            Cue.BRAKE -> { target = max(min(target, speed) - STEP_KMH, MIN_KMH); slowing = BRAKE_RATE; momentary(cue) }
            Cue.HARSH -> { target = max(min(target, speed) - HARSH_DROP_KMH, 0f); slowing = HARSH_RATE; momentary(cue) }
            Cue.STOP -> { target = 0f; slowing = BRAKE_RATE; momentary(cue) }
            Cue.TURN_LEFT, Cue.TURN_RIGHT -> {
                val was = cue in _on.value
                _on.value = _on.value - Cue.TURN_LEFT - Cue.TURN_RIGHT + (if (was) emptySet() else setOf(cue))
                blinkLeft = if (was) 0f else BLINK_S
                apply { it.copy(turnSignal = if (was) TurnSignal.NONE else if (cue == Cue.TURN_LEFT) TurnSignal.LEFT else TurnSignal.RIGHT) }
                if (!was) say(cue)
            }
            Cue.COOL, Cue.WARM -> {
                val was = cue in _on.value
                _on.value = _on.value - Cue.COOL - Cue.WARM + (if (was) emptySet() else setOf(cue))
                apply { s ->
                    when {
                        was -> s.copy(acOn = false, setTempC = 22f)
                        cue == Cue.COOL -> s.copy(acOn = true, setTempC = 18f)
                        else -> s.copy(acOn = false, setTempC = 27f)
                    }
                }
                if (!was) say(cue)
            }
            Cue.ECO, Cue.SPORT -> {
                val was = cue in _on.value
                _on.value = _on.value - Cue.ECO - Cue.SPORT + (if (was) emptySet() else setOf(cue))
                apply { it.copy(drivingMode = if (was) DriveModeState.NORMAL else if (cue == Cue.ECO) DriveModeState.ECO else DriveModeState.SPORT) }
                if (!was) say(cue)
            }
            Cue.FAN -> toggle(cue) { s, on -> s.copy(fanLevel = if (on) 8 else 2) }
            Cue.LIGHTS -> toggle(cue) { s, on -> s.copy(headlightsOn = on) }
            Cue.WIPER -> toggle(cue) { s, on -> s.copy(wiperActive = on) }
            Cue.DEFROST -> toggle(cue) { s, on -> s.copy(defrostOn = on) }
            Cue.CRUISE -> toggle(cue) { s, on -> s.copy(cruiseOn = on) }
            else -> Unit
        }
    }

    /** Advances the car by [dt] seconds: the speed follows its target, momentary controls release, an old caption leaves. */
    fun tick(dt: Float) {
        if (!_running.value) return
        clock += dt
        speed = if (speed < target) min(target, speed + ACCEL_RATE * dt) else max(target, speed - slowing * dt)
        if (speed == target) {
            if (target <= 0f) {
                // Standing still: wait a little, then release the pedal and set off again.
                rest += dt
                if (rest >= STOP_HOLD_S) { rest = 0f; target = CRUISE_KMH; slowing = BRAKE_RATE; _on.value = _on.value - PEDALS }
            } else if (_on.value.any { it == Cue.ACCEL || it in PEDALS }) {
                _on.value = _on.value - Cue.ACCEL - PEDALS
                slowing = BRAKE_RATE
            }
        } else rest = 0f
        if (blinkLeft > 0f) {
            blinkLeft -= dt
            if (blinkLeft <= 0f) { _on.value = _on.value - Cue.TURN_LEFT - Cue.TURN_RIGHT; apply { it.copy(turnSignal = TurnSignal.NONE) } }
        }
        val pedal = _on.value.any { it in PEDALS }
        apply { it.copy(speedKmh = speed, brakePressed = pedal, gear = GearState.DRIVE) }
        if (_caption.value != null && clock - captionAt > CAPTION_S) _caption.value = null
    }

    private fun momentary(cue: Cue) {
        _on.value = _on.value - Cue.ACCEL - PEDALS + cue
        say(cue)
    }

    private fun toggle(cue: Cue, change: (VehicleSnapshot, Boolean) -> VehicleSnapshot) {
        val now = cue !in _on.value
        _on.value = if (now) _on.value + cue else _on.value - cue
        apply { change(it, now) }
        if (now) say(cue)
    }

    private fun say(cue: Cue) {
        ThemeReactions.caption(theme, cue)?.let { _caption.value = it; captionAt = clock }
    }

    companion object {
        /** Every control the strip can show, in its order. */
        val CONTROLS = listOf(
            Cue.ACCEL, Cue.BRAKE, Cue.HARSH, Cue.STOP, Cue.TURN_LEFT, Cue.TURN_RIGHT, Cue.LIGHTS, Cue.WIPER, Cue.DEFROST,
            Cue.FAN, Cue.COOL, Cue.WARM, Cue.ECO, Cue.SPORT, Cue.CRUISE,
        )

        /** The button's short label. */
        fun label(cue: Cue): String = when (cue) {
            Cue.ACCEL -> "가속"; Cue.BRAKE -> "브레이크"; Cue.HARSH -> "급제동"; Cue.STOP -> "멈추기"
            Cue.TURN_LEFT -> "◀ 깜빡이"; Cue.TURN_RIGHT -> "깜빡이 ▶"; Cue.LIGHTS -> "전조등"; Cue.WIPER -> "와이퍼"; Cue.DEFROST -> "서리 제거"
            Cue.FAN -> "바람"; Cue.COOL -> "에어컨"; Cue.WARM -> "따뜻하게"; Cue.ECO -> "에코"; Cue.SPORT -> "스포츠"; Cue.CRUISE -> "크루즈"
            else -> cue.action
        }

        private val PEDALS = setOf(Cue.BRAKE, Cue.HARSH, Cue.STOP)

        const val CRUISE_KMH = 40f
        const val STEP_KMH = 30f
        const val MAX_KMH = 100f
        const val MIN_KMH = 10f
        const val HARSH_DROP_KMH = 45f
        /** km/h per second: 2.2 m/s² up and 1.9 m/s² down are smooth; 6.1 m/s² down is a harsh stop. */
        const val ACCEL_RATE = 8f
        const val BRAKE_RATE = 7f
        const val HARSH_RATE = 22f
        const val BLINK_S = 6f
        const val CAPTION_S = 7f
        const val STOP_HOLD_S = 6f
    }
}
