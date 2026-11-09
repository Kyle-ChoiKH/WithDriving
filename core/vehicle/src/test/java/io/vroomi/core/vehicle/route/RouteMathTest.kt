package io.vroomi.core.vehicle.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class RouteMathTest {

    @Test fun localProjectionErrorUnderOnePercentOver20km() {
        val lat0 = 37.5; val lon0 = 127.0
        val out = FloatArray(2)
        for (deg in 0 until 360 step 15) {
            // A point 20 km away in direction deg (computed on the sphere).
            val d = 20_000.0 / 6_371_008.8
            val b = deg * PI / 180
            val p1 = lat0 * PI / 180; val l1 = lon0 * PI / 180
            val p2 = kotlin.math.asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
            val l2 = l1 + kotlin.math.atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
            RouteMath.project(lat0, lon0, p2 * 180 / PI, l2 * 180 / PI, out)
            val flat = hypot(out[0].toDouble(), out[1].toDouble())
            val real = RouteMath.haversine(lat0, lon0, p2 * 180 / PI, l2 * 180 / PI)
            assertTrue("dir $deg: ${abs(flat - real) / real}", abs(flat - real) / real < 0.01)
        }
    }

    @Test fun liveRouteKeepsOnlyMetresFromTheJourneyStart() {
        LiveRoute.clear()
        LiveRoute.markJourneyStart()
        LiveRoute.onFix(37.5, 127.0, 1_000L)
        assertEquals(0f, LiveRoute.east, 0f); assertEquals(0f, LiveRoute.north, 0f)
        LiveRoute.onFix(37.5 + 0.009, 127.0, 2_000L) // about 1 km north
        assertEquals(1000.0, LiveRoute.north.toDouble(), 15.0)
        assertTrue(LiveRoute.fresh(2_500L)); assertTrue(!LiveRoute.fresh(10_000L))
        LiveRoute.markJourneyStart()
        LiveRoute.onFix(40.0, 120.0, 3_000L) // a new journey: its first fix is the new origin
        assertEquals(0f, LiveRoute.east, 0f)
        LiveRoute.clear()
        // No public member exposes a latitude, a longitude or the origin.
        LiveRoute::class.java.methods.forEach { m -> assertTrue(m.name, listOf("getLat", "getLon", "getOrigin").none { p -> m.name.startsWith(p) }) }
    }

    @Test fun douglasPeuckerDropsStraightRunsAndKeepsCorners() {
        // East for 100 m, then north with a 2 m sideways wobble.
        val xs = FloatArray(21) { if (it <= 10) it * 10f else 100f + (if (it % 2 == 0) 2f else -2f) }
        val ys = FloatArray(21) { if (it <= 10) 0f else (it - 10) * 10f }
        val keep = RouteMath.douglasPeucker(xs, ys, 21, 5f)
        assertTrue(keep[0] && keep[20] && keep[10])
        assertEquals(3, keep.count { it })
        val keepFine = RouteMath.douglasPeucker(xs, ys, 21, 1f)
        assertTrue(keepFine.count { it } > 3)
    }

    @Test fun sketchIs64PointsInsideTheUnitSquare() {
        val n = 300
        val xs = FloatArray(n) { 5000f + it * 7f }; val ys = FloatArray(n) { -3000f + sin(it / 20f) * 400f }
        val s = RouteMath.normalise64(xs, ys, n)
        assertEquals(128, s.size)
        s.forEach { assertTrue(it in 0f..1f) }
        // Origin removed and scale gone: the same shape anywhere gives the same sketch.
        val s2 = RouteMath.normalise64(FloatArray(n) { xs[it] - 5000f + 99f }, FloatArray(n) { ys[it] + 3000f - 7f }, n)
        for (i in s.indices) assertEquals(s[i], s2[i], 1e-4f)
    }

    @Test fun alongFollowsThePolyline() {
        val route = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f)
        val p = FloatArray(2)
        RouteMath.along(route, 50f, p); assertEquals(50f, p[0], 1e-3f); assertEquals(0f, p[1], 1e-3f)
        RouteMath.along(route, 150f, p); assertEquals(100f, p[0], 1e-3f); assertEquals(50f, p[1], 1e-3f)
        RouteMath.along(route, 999f, p); assertEquals(100f, p[1], 1e-3f)
    }
}
