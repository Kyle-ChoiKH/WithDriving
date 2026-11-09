package io.vroomi.character.marble

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * One rail of the marble machine: a Catmull-Rom spline through control points, sampled every few units and indexed
 * by arc length. A marble on it is a single number s (and its speed); it can never leave the rail.
 * Coordinates are machine units (x right, y down), the machine spans about 1000 × 480 units.
 */
class Track(val id: Int, control: FloatArray, step: Float = 4f, val trueDrop: Float = 0f) {
    val xs: FloatArray
    val ys: FloatArray
    val cum: FloatArray
    val length: Float
    /** The rail's real height (y, down positive): the screen y, or a steady descent for helices seen from the front. */
    val hs: FloatArray

    init {
        val n = control.size / 2
        val px = ArrayList<Float>(); val py = ArrayList<Float>()
        for (i in 0 until n - 1) {
            val x0 = control[maxOf(0, i - 1) * 2]; val y0 = control[maxOf(0, i - 1) * 2 + 1]
            val x1 = control[i * 2]; val y1 = control[i * 2 + 1]
            val x2 = control[(i + 1) * 2]; val y2 = control[(i + 1) * 2 + 1]
            val x3 = control[minOf(n - 1, i + 2) * 2]; val y3 = control[minOf(n - 1, i + 2) * 2 + 1]
            val segLen = hypot(x2 - x1, y2 - y1)
            val k = maxOf(2, (segLen / step).toInt())
            for (j in 0 until k) {
                val t = j / k.toFloat()
                px += catmull(x0, x1, x2, x3, t); py += catmull(y0, y1, y2, y3, t)
            }
        }
        px += control[(n - 1) * 2]; py += control[(n - 1) * 2 + 1]
        xs = px.toFloatArray(); ys = py.toFloatArray()
        cum = FloatArray(xs.size)
        for (i in 1 until xs.size) cum[i] = cum[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        length = cum[cum.size - 1]
        hs = if (trueDrop <= 0f) ys.copyOf() else FloatArray(xs.size) { ys[0] + trueDrop * cum[it] / length }
    }

    /** The sample nearest to (x, y), as arc length. */
    fun nearest(x: Float, y: Float): Float {
        var best = 0; var bd = Float.MAX_VALUE
        for (i in xs.indices) { val d = hypot(xs[i] - x, ys[i] - y); if (d < bd) { bd = d; best = i } }
        return cum[best]
    }

    /** Index of the sample segment that contains [s]. */
    private fun seg(s: Float): Int {
        var lo = 0; var hi = cum.size - 1
        val t = s.coerceIn(0f, length)
        while (hi - lo > 1) { val mid = (lo + hi) ushr 1; if (cum[mid] <= t) lo = mid else hi = mid }
        return lo
    }

    fun point(s: Float, out: FloatArray) {
        val i = seg(s)
        val l = (cum[i + 1] - cum[i]).coerceAtLeast(1e-6f)
        val t = ((s.coerceIn(0f, length) - cum[i]) / l).coerceIn(0f, 1f)
        out[0] = xs[i] + (xs[i + 1] - xs[i]) * t
        out[1] = ys[i] + (ys[i + 1] - ys[i]) * t
    }

    /** sin of the downhill slope at [s] (positive = going down), from the real height. */
    fun sinSlope(s: Float): Float {
        val i = seg(s)
        val l = (cum[i + 1] - cum[i]).coerceAtLeast(1e-6f)
        return ((hs[i + 1] - hs[i]) / l).coerceIn(-1f, 1f)
    }

    /** Real height at [s] (y, down positive). */
    fun heightAt(s: Float): Float {
        val i = seg(s)
        val l = (cum[i + 1] - cum[i]).coerceAtLeast(1e-6f)
        val t = ((s.coerceIn(0f, length) - cum[i]) / l).coerceIn(0f, 1f)
        return hs[i] + (hs[i + 1] - hs[i]) * t
    }

    companion object {
        fun catmull(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
            val t2 = t * t; val t3 = t2 * t
            return 0.5f * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
        }

        /** A descending helix seen from the front: [turns] ellipses around (cx, ·) from y0 to y1. */
        fun helix(cx: Float, rx: Float, ry: Float, y0: Float, y1: Float, turns: Float, startAngle: Float = 0f): FloatArray {
            val n = (turns * 24).toInt()
            return FloatArray((n + 1) * 2) { k ->
                val i = k / 2
                val u = i / n.toFloat()
                val a = startAngle + u * turns * 2f * PI.toFloat()
                if (k % 2 == 0) cx + cos(a) * rx else y0 + (y1 - y0) * u + sin(a) * ry
            }
        }
    }
}

/** The machine's rails (machine units). */
object MarbleLayout {
    const val W = 1000f
    const val H = 480f
    const val LIFT_X = 70f
    const val LIFT_BOTTOM = 446f
    const val LIFT_TOP = 46f
    const val HOPPER_X = 70f
    const val HOPPER_Y = 456f
    const val GATE_X = 560f
    const val GATE_Y = 132f
    const val PEND_PIVOT_X = 245f
    const val PEND_PIVOT_Y = 196f
    const val PEND_LEN = 92f
    /** Pendulum amplitude (deg): side A (right) +, side B (left) −. */
    const val PEND_AMP = 34f

    const val A = 0       // lift top → gate, with the brake station
    const val L = 1       // left lane → pendulum (or the bypass)
    const val BYPASS = 2  // under the pendulum gap
    const val P = 3       // from the pendulum's far side down to the return
    const val R = 4       // right lane (the cool lane), zigzag down
    const val LOOP = 5    // the backward loop (reverse)
    const val RET = 6     // the bottom return to the hopper
    const val GOLD = 7    // the gold spiral
    const val CHIME = 8   // the chime ladder (combo)

    /** Where the brake lever drops on rail A (arc length fraction). */
    const val BRAKE_AT = 0.62f

    val tracks: List<Track> = listOf(
        Track(A, floatArrayOf(LIFT_X + 14f, LIFT_TOP + 6f, 200f, 64f, 330f, 84f, 450f, 108f, GATE_X, GATE_Y)),
        Track(L, floatArrayOf(GATE_X, GATE_Y + 4f, 480f, 168f, 400f, 190f, 330f, 228f, PEND_PIVOT_X + 58f, 266f)),
        Track(BYPASS, floatArrayOf(PEND_PIVOT_X + 58f, 268f, 250f, 300f, 200f, 318f, 168f, 330f)),
        Track(P, floatArrayOf(PEND_PIVOT_X - 58f, 268f, 176f, 300f, 168f, 330f, 150f, 360f, 120f, 410f, HOPPER_X + 10f, HOPPER_Y - 6f)),
        Track(R, floatArrayOf(GATE_X + 4f, GATE_Y + 4f, 700f, 170f, 860f, 210f, 880f, 230f, 760f, 262f, 640f, 296f, 660f, 316f, 820f, 346f, 900f, 380f)),
        Track(LOOP, Track.helix(860f, 70f, 22f, 382f, 436f, 1.5f, -0.3f), trueDrop = 54f),
        Track(RET, floatArrayOf(900f, 384f, 700f, 404f, 500f, 418f, 300f, 432f, 140f, 444f, HOPPER_X + 10f, HOPPER_Y)),
        Track(GOLD, Track.helix(440f, 78f, 24f, 160f, 360f, 3f, 0f), trueDrop = 200f),
        Track(CHIME, floatArrayOf(GATE_X + 4f, GATE_Y + 6f, 640f, 150f, 600f, 170f, 680f, 192f, 640f, 214f, 720f, 238f, 680f, 262f, 760f, 290f, 900f, 380f)),
    )
}
