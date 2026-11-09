package io.vroomi.app.ui.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The geometry of a rounded rectangle whose corners have continuous curvature (pure Kotlin, unit-tested).
 *
 * A plain rounded corner is a quarter circle: the curvature jumps from 0 on the edge to 1/r on the arc, which
 * the eye reads as a small kink. Here each corner starts earlier on the edge, at p = (1 + smoothing) * r, and
 * eases into a shorter circular arc through a cubic whose first three control points lie on the edge line, so
 * the curvature starts at exactly 0 where the straight edge ends. The arc in the middle keeps radius r and the
 * corner is symmetric about its diagonal. smoothing = 0 gives the plain quarter circle.
 */
object SmoothCorners {
    /** One path segment, absolute coordinates. A line has only (x, y). */
    class Seg(val cubic: Boolean, val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float)

    class Geometry(val startX: Float, val startY: Float, val segs: List<Seg>)

    /** The corner in local units: u along the incoming edge (towards the corner = smaller u), v along the outgoing edge. */
    private class Corner(r: Float, s: Float) {
        val p: Float
        val pts: FloatArray // u,v of: S, C1, C2, E1, A1, A2, E2, C3, C4, End
        init {
            val arcDeg = 90f * (1f - s)
            val arcSection = sin(rad(arcDeg / 2f)) * r * sqrt(2f)
            val alpha = (90f - arcDeg) / 2f
            val p3p4 = r * tan(rad(alpha / 2f))
            val beta = 45f * s
            val c = p3p4 * cos(rad(beta))
            val d = c * tan(rad(beta))
            p = (1f + s) * r
            val b = (p - arcSection - c - d) / 3f
            val a = 2f * b
            // S -> E1: three control points on the edge line (zero curvature where the edge ends)
            val su = p; val sv = 0f
            val c1u = p - a; val c2u = p - a - b
            val e1u = p - a - b - c; val e1v = d
            // E1 -> E2: the circular arc (centre at (r, r)) as one cubic
            val e2u = e1v; val e2v = e1u
            val theta = rad(arcDeg)
            val k = (4f / 3f) * tan(theta / 4f) * r
            // Tangent at E1 is perpendicular to the radius, heading towards E2.
            var tu = -(e1v - r); var tv = (e1u - r)
            val tl = sqrt(tu * tu + tv * tv).coerceAtLeast(1e-6f)
            tu /= tl; tv /= tl
            if (tu > 0f) { tu = -tu; tv = -tv }
            val a1u = e1u + k * tu; val a1v = e1v + k * tv
            // Mirror about the diagonal for the second half.
            val a2u = a1v; val a2v = a1u
            pts = floatArrayOf(su, sv, c1u, 0f, c2u, 0f, e1u, e1v, a1u, a1v, a2u, a2v, e2u, e2v, 0f, c2u, 0f, c1u, 0f, p)
        }
    }

    private fun rad(deg: Float) = deg * PI.toFloat() / 180f

    fun geometry(w: Float, h: Float, radius: Float, smoothing: Float = 0.6f): Geometry {
        val maxP = min(w, h) / 2f
        var r = radius.coerceAtLeast(0f)
        var s = smoothing.coerceIn(0f, 1f)
        if (r <= 0f) {
            return Geometry(0f, 0f, listOf(line(w, 0f), line(w, h), line(0f, h), line(0f, 0f)))
        }
        if ((1f + s) * r > maxP) {
            s = (maxP / r - 1f).coerceIn(0f, s)
            if ((1f + s) * r > maxP) { r = maxP; s = 0f }
        }
        val c = Corner(r, s)
        val p = c.p
        val segs = ArrayList<Seg>(12)
        // top edge -> top-right corner: (u, v) -> (w - u, v)
        segs += line(w - p, 0f); corner(segs, c) { u, v -> floatArrayOf(w - u, v) }
        // right edge -> bottom-right: (u, v) -> (w - v, h - u)
        segs += line(w, h - p); corner(segs, c) { u, v -> floatArrayOf(w - v, h - u) }
        // bottom edge -> bottom-left: (u, v) -> (u, h - v)
        segs += line(p, h); corner(segs, c) { u, v -> floatArrayOf(u, h - v) }
        // left edge -> top-left: (u, v) -> (v, u)
        segs += line(0f, p); corner(segs, c) { u, v -> floatArrayOf(v, u) }
        return Geometry(p, 0f, segs)
    }

    private fun line(x: Float, y: Float) = Seg(false, 0f, 0f, 0f, 0f, x, y)

    private fun corner(out: MutableList<Seg>, c: Corner, map: (Float, Float) -> FloatArray) {
        val q = c.pts
        fun pt(i: Int) = map(q[i * 2], q[i * 2 + 1])
        for (start in intArrayOf(1, 4, 7)) {
            val a = pt(start); val b = pt(start + 1); val e = pt(start + 2)
            out += Seg(true, a[0], a[1], b[0], b[1], e[0], e[1])
        }
    }
}

/** A rounded rectangle with continuous-curvature corners ([SmoothCorners]). */
class SmoothCornerShape(private val radius: Dp, private val smoothing: Float = 0.6f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val g = SmoothCorners.geometry(size.width, size.height, r, smoothing)
        val path = Path()
        path.moveTo(g.startX, g.startY)
        for (s in g.segs) if (s.cubic) path.cubicTo(s.x1, s.y1, s.x2, s.y2, s.x, s.y) else path.lineTo(s.x, s.y)
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is SmoothCornerShape && other.radius == radius && other.smoothing == smoothing
    override fun hashCode(): Int = radius.hashCode() * 31 + smoothing.hashCode()
}

/** Shapes from the radius scale. */
object Shapes {
    val r10 = SmoothCornerShape(Radius.r10)
    val r16 = SmoothCornerShape(Radius.r16)
    val r24 = SmoothCornerShape(Radius.r24)
    val r32 = SmoothCornerShape(Radius.r32)
    val r44 = SmoothCornerShape(Radius.r44)
    val capsule = SmoothCornerShape(Radius.capsule, smoothing = 0f)

    /** Semantic names: a list section or card, a big toggle, a sheet or floating panel. */
    val card = r24
    val toggle = r24
    val sheet = r32
}
