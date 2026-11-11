package io.vroomi.character.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The invented inline four "VE-4T" (about 1.4 L, turbo, DOHC 16 valves, firing order 1-3-4-2) and the
 * invented motor "VE-M1". All dimensions in millimetres, angles in degrees unless said otherwise.
 * The painter draws everything from these numbers, at one scale, so rods never stretch.
 */
object EngineGeometry {
    const val BORE_MM = 75.0
    const val STROKE_MM = 79.0
    const val ROD_MM = 135.0
    const val CRANK_R_MM = STROKE_MM / 2
    const val CYLINDERS = 4

    /** Displacement in litres (4 × π/4 × bore² × stroke). */
    val displacementL: Double get() = CYLINDERS * PI / 4 * BORE_MM * BORE_MM * STROKE_MM / 1e6

    /** Phase offsets of cylinders 1..4 inside the 720° cycle: firing order 1-3-4-2. */
    val PHASE_OFFSET_DEG = doubleArrayOf(0.0, 540.0, 180.0, 360.0)
    val FIRING_ORDER = intArrayOf(1, 3, 4, 2)

    const val EXHAUST_OPEN = 140.0
    const val EXHAUST_CLOSE = 370.0
    const val INTAKE_OPEN = 350.0
    const val INTAKE_CLOSE = 580.0
    const val LIFT_MM = 9.0

    /** Piston-pin height above the crank axis for crank angle [thetaRad] (0 = TDC): r·cos θ + √(l² − r²·sin² θ). */
    fun pinHeightMm(thetaRad: Double): Double {
        val r = CRANK_R_MM; val l = ROD_MM; val s = sin(thetaRad)
        return r * cos(thetaRad) + sqrt(l * l - r * r * s * s)
    }

    /** Phase of [cylinder] (0-based) inside its 720° cycle, for the engine cycle angle [cycleDeg]. */
    fun phase(cycleDeg: Double, cylinder: Int): Double = wrap720(cycleDeg - PHASE_OFFSET_DEG[cylinder])

    /** Crank angle of [cylinder] in degrees, 0 = TDC (the throw is shared by one crankshaft). */
    fun crankDeg(cycleDeg: Double, cylinder: Int): Double = phase(cycleDeg, cylinder) % 360.0

    fun stroke(phaseDeg: Double): Stroke {
        val p = wrap720(phaseDeg)
        return when {
            p < 180 -> Stroke.POWER
            p < 360 -> Stroke.EXHAUST
            p < 540 -> Stroke.INTAKE
            else -> Stroke.COMPRESSION
        }
    }

    /** Raised-cosine valve lift 0..1 between [open] and [close] (degrees of the 720° cycle). */
    fun lift(phaseDeg: Double, open: Double, close: Double): Double {
        val p = wrap720(phaseDeg)
        if (p < open || p > close) return 0.0
        return 0.5 - 0.5 * cos((p - open) / (close - open) * 2 * PI)
    }

    fun exhaustLift(phaseDeg: Double) = lift(phaseDeg, EXHAUST_OPEN, EXHAUST_CLOSE)
    fun intakeLift(phaseDeg: Double) = lift(phaseDeg, INTAKE_OPEN, INTAKE_CLOSE)

    /**
     * Cam lobe direction (radians) relative to "pointing at the valve": cams turn at half crank speed and
     * the nose points at the valve exactly when the lift peaks.
     */
    fun camRad(phaseDeg: Double, open: Double, close: Double): Double = (phaseDeg - (open + close) / 2) / 2 * PI / 180

    // ---------------------------------------------------------------- slow motion (no aliasing, no strobing)

    const val IDLE_RPM = 750.0
    const val DISPLAY_MAX_RPM = 6000.0
    const val MAX_VISUAL_RPS = 1.4
    const val CALM_VISUAL_RPS = 0.5

    /** Visual crank speed (rev/s): 0.30 + 1.10 × clamp((rpm − 750) ÷ 5,250); below idle it coasts linearly to 0. */
    fun visualRps(rpm: Double): Double = when {
        rpm <= 1.0 -> 0.0
        rpm < IDLE_RPM -> 0.30 * rpm / IDLE_RPM
        else -> (0.30 + 1.10 * ((rpm - IDLE_RPM) / (DISPLAY_MAX_RPM - IDLE_RPM)).coerceIn(0.0, 1.0)).coerceAtMost(MAX_VISUAL_RPS)
    }

    /** "슬로모션 1/N": how much slower than the real crank the drawing turns. */
    fun slowMotionFactor(rpm: Double, visualRps: Double): Int =
        if (visualRps <= 1e-6 || rpm <= 1.0) 0 else (rpm / 60.0 / visualRps).roundToInt().coerceAtLeast(1)

    // ---------------------------------------------------------------- VE-M1 electric motor

    const val MOTOR_POLES = 8
    const val MOTOR_POLE_PAIRS = MOTOR_POLES / 2
    const val STATOR_SLOTS = 48
    const val MOTOR_REDUCTION = 9.0
    const val MOTOR_MAX_RPM = 16_000.0

    /** Visual rotor speed (rev/s, signed): 0.15 + 1.05 × |rpm| ÷ 16,000; 0 when stopped. */
    fun motorVisualRps(rpm: Double): Double =
        if (abs(rpm) < 1.0) 0.0 else sign(rpm) * (0.15 + 1.05 * (abs(rpm) / MOTOR_MAX_RPM).coerceAtMost(1.0))

    // ---------------------------------------------------------------- telemetry curves (estimates, pure)

    /** Valve lift 0..1 of cylinder 1 at [crankDeg] of the 720° cycle (0 = firing TDC): the intake or the exhaust valve. */
    fun valveLift(crankDeg: Float, intake: Boolean): Float =
        (if (intake) intakeLift(crankDeg.toDouble()) else exhaustLift(crankDeg.toDouble())).toFloat()

    /** Peak torque (N·m) of each upgrade step, Lv.1..5. */
    val PEAK_TORQUE_NM = floatArrayOf(125f, 140f, 220f, 250f, 300f)

    /**
     * Estimated full-load torque (N·m) at [rpm] for upgrade step [model] (0..4). The naturally aspirated steps
     * (0, 1) peak in the mid range; the turbo steps rise to a plateau as the turbo spools and fall off at the top.
     */
    fun torqueAt(rpm: Float, model: Int): Float {
        val m = model.coerceIn(0, 4)
        val peak = PEAK_TORQUE_NM[m]
        val x = rpm.coerceIn(0f, 7200f)
        if (m < 2) {
            val at = if (m == 0) 3800f else 4400f
            val d = (x - at) / (if (x < at) at * 1.25f else 7200f - at)
            return peak * (1f - 0.45f * d * d).coerceAtLeast(0.3f)
        }
        val spool = if (m == 4) 2600f else 1900f
        val fall = if (m == 4) 6200f else 4800f
        return peak * (0.35f + 0.65f * smooth(900f, spool, x)) * (1f - 0.35f * smooth(fall, 7200f, x))
    }

    /** Estimated power (kW) at [rpm]: torque × speed. */
    fun powerKwAt(rpm: Float, model: Int): Float = torqueAt(rpm, model) * rpm * (2f * PI.toFloat() / 60f) / 1000f

    /** Estimated spark advance (degrees before TDC): more with speed, less with load. */
    fun ignitionAdvanceDeg(rpm: Double, load: Double): Float =
        (10.0 + 24.0 * ((rpm - IDLE_RPM) / (DISPLAY_MAX_RPM - IDLE_RPM)).coerceIn(0.0, 1.0) - 8.0 * load.coerceIn(0.0, 1.0)).toFloat()

    private fun smooth(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

    fun wrap720(d: Double): Double { val t = d % 720.0; return if (t < 0) t + 720.0 else t }
}
