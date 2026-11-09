package io.vroomi.character.engine

import io.vroomi.character.CarSignals
import io.vroomi.core.vehicle.model.GearState
import kotlin.math.exp

/**
 * The estimated state of the VE-M1 motor: speed from the road speed (single reduction 9.0) and power from
 * a simple road-load model. Nothing is measured; every value is an estimate ("추정").
 */
class MotorModel {
    /** Signed motor speed (negative in reverse), smoothed (τ 0.2 s). */
    var rpm = 0.0; private set
    /** Estimated power in kW, smoothed (τ 0.3 s). Negative = regeneration (capped at −60 kW). */
    var powerKw = 0.0; private set
    var compressorOn = false; private set

    val regen: Boolean get() = powerKw < -0.5
    val driving: Boolean get() = powerKw > 0.5

    fun step(dt: Double, c: CarSignals) {
        val v = (c.speedKmh ?: 0f).toDouble().coerceAtLeast(0.0)
        val a = (c.accelMps2 ?: 0f).toDouble()
        val dir = when (c.gear) { GearState.REVERSE -> -1.0; GearState.DRIVE -> 1.0; else -> 0.0 }
        val target = (dir * Gearbox.wheelRpm(v) * EngineGeometry.MOTOR_REDUCTION)
            .coerceIn(-EngineGeometry.MOTOR_MAX_RPM, EngineGeometry.MOTOR_MAX_RPM)
        rpm += (target - rpm) * (1 - exp(-dt / 0.2))
        if (dir == 0.0 && kotlin.math.abs(rpm) < 5) rpm = 0.0
        val p = if (dir == 0.0 || v < 0.5) 0.0 else powerKw(a, v)
        powerKw += (p - powerKw) * (1 - exp(-dt / 0.3))
        compressorOn = c.acOn == true || c.maxAcOn == true
    }

    companion object {
        const val MASS_KG = 1900.0
        const val CDA_M2 = 0.62
        const val RHO = 1.2
        const val CRR = 0.011
        const val G = 9.81
        const val REGEN_CAP_KW = -60.0

        /** P = (m·a·v + ½·ρ·CdA·v³ + Crr·m·g·v) ÷ 1000, v in km/h here (converted to m/s); regen capped. */
        fun powerKw(a: Double, kmh: Double): Double {
            val v = kmh / 3.6
            val p = (MASS_KG * a * v + 0.5 * RHO * CDA_M2 * v * v * v + CRR * MASS_KG * G * v) / 1000.0
            return p.coerceAtLeast(REGEN_CAP_KW)
        }
    }
}
