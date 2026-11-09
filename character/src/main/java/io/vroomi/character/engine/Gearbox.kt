package io.vroomi.character.engine

import io.vroomi.core.vehicle.model.DriveModeState
import kotlin.math.PI

/**
 * A virtual 8-speed automatic. Nothing here is read from the car: it turns the car's speed, drive mode
 * and an estimated load into a believable gear and engine speed ("추정").
 */
class Gearbox {
    /** Current forward gear 1..8 (held while in P, N or R; used again when D is selected). */
    var gear = 1; private set
    var previousGear = 1; private set
    /** Seconds since the last shift (large when none happened yet). */
    var sinceShiftS = 99.0; private set
    var shifts = 0; private set
    private var kickArmed = true
    private var steadyS = 0.0

    /** Choose a sensible gear for [kmh] at once (entering D while rolling, or at start). */
    fun reset(kmh: Double, mode: DriveModeState) {
        var g = 1
        for (cand in 1..8) if (rpmFor(kmh, cand) >= (down(mode) + up(mode)) / 2) g = cand
        gear = g; previousGear = g; sinceShiftS = 99.0
    }

    /**
     * Advances the shift logic in D. Upshift above up(mode) × (1 + 0.6 × load), downshift below down(mode),
     * at least [MIN_SHIFT_GAP_S] apart; kick-down when load rises above 0.85; and a light-load economy upshift
     * after [STEADY_S] of steady cruising when the next gear still keeps 1.2 × down(mode).
     */
    fun step(dt: Double, kmh: Double, load: Double, mode: DriveModeState) {
        sinceShiftS += dt
        steadyS = if (load < STEADY_LOAD) steadyS + dt else 0.0
        if (load < 0.7) kickArmed = true
        if (sinceShiftS < MIN_SHIFT_GAP_S) return
        val now = rpmFor(kmh, gear)
        val upAt = up(mode) * (1 + 0.6 * load)
        when {
            gear < 8 && now > upAt -> shiftTo(gear + 1)
            gear > 1 && now < down(mode) -> shiftTo(gear - 1)
            kickArmed && load > 0.85 && gear > 1 -> {
                kickArmed = false
                val drop = if (mode == DriveModeState.SPORT && gear > 2 && rpmFor(kmh, gear - 2) < upAt) 2 else 1
                if (rpmFor(kmh, gear - drop) < upAt) shiftTo(gear - drop)
            }
            gear < 8 && steadyS >= STEADY_S && rpmFor(kmh, gear + 1) >= down(mode) * 1.2 -> shiftTo(gear + 1)
        }
    }

    private fun shiftTo(g: Int) {
        previousGear = gear; gear = g.coerceIn(1, 8); sinceShiftS = 0.0; shifts++; steadyS = 0.0
    }

    companion object {
        val RATIOS = doubleArrayOf(4.71, 3.14, 2.11, 1.67, 1.29, 1.00, 0.84, 0.67)
        const val REVERSE = 3.30
        const val FINAL_DRIVE = 3.20
        const val TYRE_RADIUS_M = 0.33
        const val MIN_SHIFT_GAP_S = 1.2
        const val STEADY_S = 3.0
        /** Below this load the car is "cruising" (no acceleration demand). */
        const val STEADY_LOAD = 0.55
        const val SHIFT_ANIM_S = 0.25

        /** Wheel speed (rpm) for a road speed in km/h. */
        fun wheelRpm(kmh: Double): Double = kmh / 3.6 / (2 * PI * TYRE_RADIUS_M) * 60.0

        fun rpmFor(kmh: Double, gear: Int): Double = wheelRpm(kmh) * RATIOS[(gear - 1).coerceIn(0, 7)] * FINAL_DRIVE
        fun reverseRpm(kmh: Double): Double = wheelRpm(kmh) * REVERSE * FINAL_DRIVE

        fun up(mode: DriveModeState): Double = when (mode) { DriveModeState.ECO -> 1900.0; DriveModeState.SPORT -> 3600.0; else -> 2400.0 }
        fun down(mode: DriveModeState): Double = when (mode) { DriveModeState.ECO -> 1100.0; DriveModeState.SPORT -> 2000.0; else -> 1300.0 }
    }
}
