package io.vroomi.app.ui.motion

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A spring defined the way a designer thinks about it: how long it takes to settle within 1 % of the distance,
 * and how much it is damped. The stiffness (unit mass) is computed from those two targets.
 */
class SpringToken(val name: String, val settleMs: Float, val dampingRatio: Float) {
    /** Natural angular frequency (rad/s) that settles in [settleMs]. */
    val omega: Double = SpringMath.omegaFor(settleMs / 1000.0, dampingRatio.toDouble())
    /** Compose stiffness (unit mass) = omega². */
    val stiffness: Float = (omega * omega).toFloat()

    /** Remaining fraction of the distance at [tMs] (1 at the start, 0 at rest), starting at rest. */
    fun remaining(tMs: Double, slow: Float = 1f): Double = SpringMath.remaining(tMs / 1000.0, omega / slow, dampingRatio.toDouble())

    /** Progress 0..1 at [tMs]. */
    fun progress(tMs: Double, slow: Float = 1f): Float = (1.0 - remaining(tMs, slow)).toFloat()
}

/** Closed-form damped spring from x = 1, v = 0 (pure, used by the motion tokens, the passage and the tests). */
object SpringMath {
    fun remaining(t: Double, omega: Double, zeta: Double): Double {
        if (t <= 0.0) return 1.0
        return when {
            zeta < 1.0 -> {
                val wd = omega * sqrt(1 - zeta * zeta)
                exp(-zeta * omega * t) * (cos(wd * t) + zeta * omega / wd * sin(wd * t))
            }
            zeta == 1.0 -> (1 + omega * t) * exp(-omega * t)
            else -> {
                val s = sqrt(zeta * zeta - 1)
                val r1 = -omega * (zeta - s); val r2 = -omega * (zeta + s)
                (r2 * exp(r1 * t) - r1 * exp(r2 * t)) / (r2 - r1)
            }
        }
    }

    /**
     * The last moment the spring is more than [eps] away from rest. Scans back from a bound past which the
     * decay envelope is already below [eps] (4000 samples), so it is cheap enough to run at start-up.
     */
    fun settleTime(omega: Double, zeta: Double, eps: Double = 0.01): Double {
        val tEnd = when {
            zeta < 1.0 -> ln(1.0 / (eps * sqrt(1 - zeta * zeta))) / (zeta * omega)
            zeta == 1.0 -> 12.0 / omega
            else -> 12.0 / (omega * (zeta - sqrt(zeta * zeta - 1)))
        }
        val n = 4000
        for (i in n downTo 0) {
            val t = tEnd * i / n
            if (abs(remaining(t, omega, zeta)) > eps) return t
        }
        return 0.0
    }

    /** Bisection on omega so that the 1 % settle time equals [seconds]. */
    fun omegaFor(seconds: Double, zeta: Double): Double {
        var lo = 1.0; var hi = 400.0
        repeat(40) {
            val mid = (lo + hi) / 2
            if (settleTime(mid, zeta) > seconds) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    /** Largest overshoot past the target, as a fraction of the distance (0 for critically damped). */
    fun overshoot(zeta: Double): Double = if (zeta >= 1.0) 0.0 else exp(-Math.PI * zeta / sqrt(1 - zeta * zeta))
}

/** A cubic Bézier easing curve (0,0)-(x1,y1)-(x2,y2)-(1,1), solved for x with Newton steps and bisection. */
class CubicEase(private val x1: Float, private val y1: Float, private val x2: Float, private val y2: Float) {
    private fun bez(a: Float, b: Float, s: Float): Float { val u = 1 - s; return 3 * u * u * s * a + 3 * u * s * s * b + s * s * s }
    private fun dbez(a: Float, b: Float, s: Float): Float { val u = 1 - s; return 3 * u * u * a + 6 * u * s * (b - a) + 3 * s * s * (1 - b) }

    fun ease(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var s = x
        repeat(6) {
            val d = dbez(x1, x2, s)
            if (abs(d) < 1e-5f) return@repeat
            s -= (bez(x1, x2, s) - x) / d
        }
        if (s !in 0f..1f || abs(bez(x1, x2, s) - x) > 1e-4f) {
            var lo = 0f; var hi = 1f
            repeat(30) { val m = (lo + hi) / 2; if (bez(x1, x2, m) < x) lo = m else hi = m }
            s = (lo + hi) / 2
        }
        return bez(y1, y2, s)
    }
}

/**
 * The motion tokens. Position, size and scale use the springs; only opacity may use a tween.
 * Overshoot: none for anything larger than a quarter of the screen (Smooth, Passage), at most 3 % for small things.
 */
object Motion {
    val Press = SpringToken("Press", 140f, 0.75f)
    val Snappy = SpringToken("Snappy", 280f, 0.88f)
    val Smooth = SpringToken("Smooth", 430f, 0.93f)
    val Passage = SpringToken("Passage", 720f, 0.98f)
    val springs = listOf(Press, Snappy, Smooth, Passage)

    /** The only motion allowed while moving: opacity, 400 ms on this curve. */
    const val MOVING_MS = 400
    val MovingCurve = CubicEase(0.2f, 0f, 0f, 1f)

    /** Reduced motion: every passage becomes a crossfade. */
    const val REDUCED_MS = 200
    /** A crossfade between two themes far apart in brightness never runs faster than this (the flash rule). */
    const val CALM_CROSSFADE_MS = 700
    /** ...and it eases in and out evenly, so the change is spread over the whole time. */
    val CalmCurve = CubicEase(0.4f, 0f, 0.6f, 1f)
    /** Luminance gap (of full scale) from which a crossfade is slowed to [CALM_CROSSFADE_MS]. */
    const val BIG_LUMA_STEP = 0.18f

    // Choreography
    const val CONTENT_DELAY_MS = 70
    const val STAGGER_MS = 35
    const val SHEET_STAGGER_MS = 30
    const val WELCOME_STAGGER_MS = 60
    const val EXIT_FACTOR = 0.7f

    // Fixed opacity timings (ms)
    const val FADE_FAST_MS = 120
    const val SWAP_MS = 120
    const val FADE_OUT_MS = 160
    const val FADE_IN_MS = 200
    const val CHIP_MS = 250
    const val CAPTION_MS = 300
    const val LAUNCH_BACKDROP_MS = 250
    const val LAUNCH_SCENE_MS = 450
    const val LAUNCH_CHROME_DELAY_MS = 120
    const val SAFETY_FINISH_MS = 150
    const val SWIPE_LIVE_MS = 150
    const val CARD_LIFT_MS = 80
    const val COUNT_UP_MS = 600
    const val PEEK_DRIFT_MS = 3000
    const val CONFIRM_CHIP_MS = 2000
    const val TRIP_RETURN_MS = 4000
    /** If a scene never reports its first frame, the launch fade-up starts anyway after this. */
    const val FIRST_FRAME_FALLBACK_MS = 1500

    // P8 choreography (ms after the moving signal)
    const val P8_SCENE_DELAY_MS = 80
    const val P8_CONTROLS_DELAY_MS = 300

    /** Debug "느린 애니메이션 ×5". */
    const val SLOW_FACTOR = 5f
}
