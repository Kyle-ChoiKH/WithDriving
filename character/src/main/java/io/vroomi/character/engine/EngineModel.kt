package io.vroomi.character.engine

import io.vroomi.character.CarSignals
import io.vroomi.core.vehicle.model.GearState
import kotlin.math.exp
import kotlin.math.max

/**
 * The estimated state of the VE-4T, rebuilt from the car's speed, acceleration, gear, brake, drive mode
 * and climate. No engine sensor is read (the Pleos SDK exposes none): every value is an estimate.
 */
class EngineModel {
    val gearbox = Gearbox()

    /** Estimated engine speed, smoothed like a needle (τ 0.2 s). */
    var rpm = EngineGeometry.IDLE_RPM; private set
    var rpmTarget = EngineGeometry.IDLE_RPM; private set
    /** 0..1, smoothed (τ 0.3 s, 0.6 s with cruise). */
    var load = 0.2; private set
    var boost = 0.0; private set
    var fuelCut = false; private set
    var acClutch = false; private set
    /** The engine runs (false after an arrival shutdown in P, until the gear leaves P). */
    var running = true; private set
    /** What the gearbox inset shows: "P", "R", "N" or "D1".."D8". */
    var gearLabel = "P"; private set
    private var lastSel = GearState.UNKNOWN

    val wastegateOpen: Boolean get() = boost > WASTEGATE_AT

    fun shutDown() { running = false }

    /**
     * [neutralThrottle] 0..1 free-revs the engine in P or N (no car signal carries it today; kept for the
     * simulator and tests).
     */
    fun step(dt: Double, c: CarSignals, neutralThrottle: Double = 0.0) {
        val v = (c.speedKmh ?: 0f).toDouble().coerceAtLeast(0.0)
        val a = (c.accelMps2 ?: 0f).toDouble()
        val sel = c.gear
        if (sel != GearState.PARK && sel != GearState.UNKNOWN) running = true

        acClutch = running && (c.acOn == true || c.maxAcOn == true)
        val idle = EngineGeometry.IDLE_RPM + if (acClutch) AC_IDLE_ADD else 0.0

        // Load (only defined for a ≥ 0; lifting off drops it to an overrun value).
        val loadT = when {
            !running -> 0.0
            sel == GearState.PARK || sel == GearState.NEUTRAL || sel == GearState.UNKNOWN -> 0.12 + 0.88 * neutralThrottle
            a >= 0 -> (0.18 + 0.30 * a + 0.0025 * v).coerceIn(0.0, 1.0)
            else -> 0.05
        }
        val tau = if (c.cruiseOn == true) 0.6 else 0.3
        load += (loadT - load) * (1 - exp(-dt / tau))

        if (sel != lastSel) {
            if (sel == GearState.DRIVE) gearbox.reset(v, c.drivingMode)
            lastSel = sel
        }

        rpmTarget = when {
            !running -> 0.0
            sel == GearState.DRIVE -> {
                gearbox.step(dt, v, load, c.drivingMode)
                var r = Gearbox.rpmFor(v, gearbox.gear)
                if (gearbox.gear == 1 && v < 10) r = max(r, 750 + 1400 * load) // torque converter slip
                max(idle, r)
            }
            sel == GearState.REVERSE -> {
                var r = Gearbox.reverseRpm(v)
                if (v < 10) r = max(r, 750 + 1400 * load)
                max(idle, r)
            }
            else -> idle + neutralThrottle * (FREE_REV_MAX - EngineGeometry.IDLE_RPM)
        }.coerceAtMost(EngineGeometry.DISPLAY_MAX_RPM)

        fuelCut = running && sel == GearState.DRIVE && v > FUEL_CUT_MIN_KMH && rpmTarget > FUEL_CUT_MIN_RPM &&
            (c.brakePressed == true || a < FUEL_CUT_DECEL)

        // Needle-like smoothing; a shut-down engine coasts to rest over about 2 s.
        val needleTau = if (!running) 0.55 else 0.2
        rpm += (rpmTarget - rpm) * (1 - exp(-dt / needleTau))
        if (!running && rpm < 5) rpm = 0.0

        val bT = if (fuelCut || !running) 0.0 else load * load * ((rpm - 1200) / 1800).coerceIn(0.0, 1.0)
        boost += (bT - boost) * (1 - exp(-dt / if (bT > boost) BOOST_UP_TAU else BOOST_DOWN_TAU))

        gearLabel = when (sel) {
            GearState.DRIVE -> D_LABELS[gearbox.gear - 1]
            GearState.REVERSE -> "R"
            GearState.NEUTRAL -> "N"
            GearState.PARK -> "P"
            GearState.UNKNOWN -> "P"
        }
    }

    companion object {
        val D_LABELS = Array(8) { "D${it + 1}" }
        const val AC_IDLE_ADD = 50.0
        const val FREE_REV_MAX = 2950.0
        const val FUEL_CUT_DECEL = -0.6
        const val FUEL_CUT_MIN_KMH = 15.0
        const val FUEL_CUT_MIN_RPM = 1000.0
        const val BOOST_UP_TAU = 0.6
        const val BOOST_DOWN_TAU = 0.3
        const val WASTEGATE_AT = 0.85

        /** The load formula of the prompt, for a ≥ 0 (v in km/h). */
        fun loadFor(a: Double, kmh: Double): Double = (0.18 + 0.30 * a + 0.0025 * kmh).coerceIn(0.0, 1.0)
        fun boostTarget(load: Double, rpm: Double): Double = load * load * ((rpm - 1200) / 1800).coerceIn(0.0, 1.0)
    }
}
