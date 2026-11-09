package io.vroomi.core.vehicle.route

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The on-device route sketcher (privacy by design). Each FusedLocation fix is converted at once into metres east
 * and north of the journey's first fix (equirectangular around that fix). Only the origin needed for that
 * conversion is held, in memory, for the current journey. Nothing here is ever stored, logged, displayed or sent;
 * a finished trip keeps only [RouteMath.normalise64] of its trail (origin removed, scaled into the unit square).
 */
object LiveRoute {
    private const val EARTH_R = 6_371_008.8
    private var originLatRad = Double.NaN
    private var originLonRad = Double.NaN
    private var cosOrigin = 1.0
    @Volatile private var reorigin = true

    /** Metres east and north of the journey's first fix. */
    @Volatile var east = 0f; private set
    @Volatile var north = 0f; private set
    @Volatile var fixes = 0; private set
    @Volatile private var lastFixWallMs = 0L

    /** Demo and simulator only: a scripted route as metres (east, north) pairs. */
    @Volatile var demoRoute: FloatArray? = null

    /** Called from the location callback (any thread). */
    @Synchronized
    fun onFix(latDeg: Double, lonDeg: Double, wallMs: Long = System.currentTimeMillis()) {
        val lat = latDeg * PI / 180.0; val lon = lonDeg * PI / 180.0
        if (reorigin || originLatRad.isNaN()) {
            originLatRad = lat; originLonRad = lon; cosOrigin = cos(lat); reorigin = false
            east = 0f; north = 0f
        } else {
            east = ((lon - originLonRad) * cosOrigin * EARTH_R).toFloat()
            north = ((lat - originLatRad) * EARTH_R).toFloat()
        }
        fixes++
        lastFixWallMs = wallMs
    }

    /** A new journey: the next fix becomes the origin. */
    @Synchronized
    fun markJourneyStart() { reorigin = true; fixes = 0; east = 0f; north = 0f }

    /** Forget everything (location stopped). */
    @Synchronized
    fun clear() { originLatRad = Double.NaN; originLonRad = Double.NaN; reorigin = true; fixes = 0; east = 0f; north = 0f }

    fun fresh(nowWallMs: Long = System.currentTimeMillis()): Boolean = fixes > 0 && nowWallMs - lastFixWallMs < FRESH_MS

    const val FRESH_MS = 5_000L
}

/** A finished trip's sketch, handed from the map theme to the trip diary (normalised; no coordinates). */
object TripSketch {
    @Volatile private var pending: FloatArray? = null
    fun offer(sketch: FloatArray) { pending = sketch }
    fun take(): FloatArray? { val s = pending; pending = null; return s }
}

object RouteMath {
    private const val EARTH_R = 6_371_008.8

    /** Equirectangular metres (east, north) of ([lat], [lon]) around ([lat0], [lon0]), degrees in. */
    fun project(lat0: Double, lon0: Double, lat: Double, lon: Double, out: FloatArray) {
        val c = cos(lat0 * PI / 180.0)
        out[0] = ((lon - lon0) * PI / 180.0 * c * EARTH_R).toFloat()
        out[1] = ((lat - lat0) * PI / 180.0 * EARTH_R).toFloat()
    }

    /** Great-circle distance in metres (haversine), for tests. */
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = lat1 * PI / 180; val p2 = lat2 * PI / 180
        val dp = p2 - p1; val dl = (lon2 - lon1) * PI / 180
        val a = kotlin.math.sin(dp / 2).let { it * it } + cos(p1) * cos(p2) * kotlin.math.sin(dl / 2).let { it * it }
        return 2 * EARTH_R * kotlin.math.asin(sqrt(a))
    }

    /** Douglas–Peucker: which of the [n] points (xs, ys) to keep at [tol] metres. Endpoints are always kept. */
    fun douglasPeucker(xs: FloatArray, ys: FloatArray, n: Int, tol: Float): BooleanArray {
        val keep = BooleanArray(n)
        if (n == 0) return keep
        keep[0] = true; keep[n - 1] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, n - 1))
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast().let { it[0] to it[1] }
            var best = -1; var bestD = 0f
            for (i in a + 1 until b) {
                val d = segDist(xs[i], ys[i], xs[a], ys[a], xs[b], ys[b])
                if (d > bestD) { bestD = d; best = i }
            }
            if (best >= 0 && bestD > tol) { keep[best] = true; stack.addLast(intArrayOf(a, best)); stack.addLast(intArrayOf(best, b)) }
        }
        return keep
    }

    fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        if (l2 <= 1e-9f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /**
     * 64 points evenly spaced along the trail, origin removed and scaled into the unit square (aspect kept):
     * the only shape a finished trip keeps. Returns 128 floats (x0, y0, x1, y1, ...), each in 0..1.
     */
    fun normalise64(xs: FloatArray, ys: FloatArray, n: Int): FloatArray {
        val out = FloatArray(128)
        if (n <= 0) return out.also { it.fill(0.5f) }
        // Arc length.
        val cum = FloatArray(n)
        for (i in 1 until n) cum[i] = cum[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        val total = cum[n - 1]
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until n) { minX = min(minX, xs[i]); maxX = max(maxX, xs[i]); minY = min(minY, ys[i]); maxY = max(maxY, ys[i]) }
        val span = max(max(maxX - minX, maxY - minY), 1e-3f)
        val padX = (span - (maxX - minX)) / 2f; val padY = (span - (maxY - minY)) / 2f
        var j = 0
        for (k in 0 until 64) {
            val target = if (total <= 0f) 0f else total * k / 63f
            while (j < n - 2 && cum[j + 1] < target) j++
            val seg = (cum.getOrElse(j + 1) { cum[j] } - cum[j]).coerceAtLeast(1e-6f)
            val t = if (n == 1) 0f else ((target - cum[j]) / seg).coerceIn(0f, 1f)
            val x = if (n == 1) xs[0] else xs[j] + (xs[j + 1] - xs[j]) * t
            val y = if (n == 1) ys[0] else ys[j] + (ys[j + 1] - ys[j]) * t
            out[k * 2] = ((x - minX + padX) / span).coerceIn(0f, 1f)
            out[k * 2 + 1] = ((y - minY + padY) / span).coerceIn(0f, 1f)
        }
        return out
    }

    /** The point [dist] metres along a polyline of (east, north) pairs (clamped at its ends). */
    fun along(route: FloatArray, dist: Float, out: FloatArray) {
        val n = route.size / 2
        if (n == 0) { out[0] = 0f; out[1] = 0f; return }
        var left = dist.coerceAtLeast(0f)
        for (i in 0 until n - 1) {
            val ax = route[i * 2]; val ay = route[i * 2 + 1]; val bx = route[i * 2 + 2]; val by = route[i * 2 + 3]
            val l = hypot(bx - ax, by - ay)
            if (left <= l || i == n - 2) {
                val t = if (l <= 0f) 0f else (left / l).coerceIn(0f, 1f)
                out[0] = ax + (bx - ax) * t; out[1] = ay + (by - ay) * t
                return
            }
            left -= l
        }
        out[0] = route[(n - 1) * 2]; out[1] = route[(n - 1) * 2 + 1]
    }
}
