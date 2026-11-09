package io.vroomi.character.render.calibre

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/** Key light: a unit vector pointing from the watch toward the light, in screen space. */
internal class KeyLight(val angle: Float) {
    val x = cos(angle); val y = sin(angle)
    val deg: Float get() = angle * 180f / PI.toFloat()

    companion object {
        /** The studio key light: one large soft source, upper left of the watch. */
        val STUDIO = KeyLight(-2.25f)
    }
}

// ------------------------------------------------------------------ the upgrade ladder (spec 5.1)

/** The case outline of a step: our own round, cushion and tonneau shapes (no brand case shapes). */
internal enum class CaseShape { ROUND, CUSHION, TONNEAU }

/** The rotor of a step: a solid half disc, an openworked one, then a skeleton rim on two spokes. */
internal enum class RotorKind { SOLID, OPEN, SKELETON }

/**
 * What each upgrade step adds to the watch ([model] 0..4 = Lv.1..5). Every step is the one before it plus its
 * new parts: 1 a round steel case with matte bridges; 2 Geneva stripes, perlage and blued screws; 3 a cushion
 * case, a guilloché dial and an openworked rotor; 4 a tonneau case and openworked bridges; 5 a flying
 * tourbillon at six, gold chatons and a skeleton dial.
 */
internal class CalStep(model: Int) {
    val model = model.coerceIn(0, 4)
    val shape = when { this.model <= 1 -> CaseShape.ROUND; this.model == 2 -> CaseShape.CUSHION; else -> CaseShape.TONNEAU }
    /** Côtes de Genève on the bridges, perlage on the plate, heat-blued screws. */
    val deco = this.model >= 1
    /** Engine-turned dial (dial view). */
    val guilloche = this.model >= 2
    val rotor = when { this.model <= 1 -> RotorKind.SOLID; this.model <= 3 -> RotorKind.OPEN; else -> RotorKind.SKELETON }
    /** Openworked bridges: the wheels show through them. */
    val skeleton = this.model >= 3
    /** Flying tourbillon at six, jewels in gold chatons, skeleton dial. */
    val tourbillon = this.model >= 4
}

/** Case outlines in units of the movement radius (x right, y down, centred on the movement). */
internal object CaseGeo {
    /** Cushion: a superellipse |x/A|^N + |y/A|^N = 1, a soft rounded square around the round bezel. */
    const val CUSHION_A = 1.27f
    const val CUSHION_N = 3.0f
    /** Tonneau (a barrel): widest at the middle, the flanks curving in to narrow, rounded ends. */
    const val TON_MID = 1.30f
    const val TON_END = 0.82f
    const val TON_HT = 1.38f
    private const val TON_P = 10f

    fun cushionR(a: Float): Float {
        val c = abs(cos(a)); val s = abs(sin(a))
        return CUSHION_A / (c.pow(CUSHION_N) + s.pow(CUSHION_N)).pow(1f / CUSHION_N)
    }

    /** Half width of the tonneau at height [y]. */
    fun tonneauHalfW(y: Float): Float {
        val t = (abs(y) / TON_HT).coerceIn(0f, 1f)
        val w = TON_END + (TON_MID - TON_END) * (1f - t * t)
        return w * (1f - t.pow(TON_P)).coerceAtLeast(0f).pow(1f / TON_P)
    }

    fun inside(s: CaseShape, x: Float, y: Float): Boolean = when (s) {
        CaseShape.ROUND -> x * x + y * y <= CW.CASE_OUT * CW.CASE_OUT
        CaseShape.CUSHION -> (abs(x) / CUSHION_A).pow(CUSHION_N) + (abs(y) / CUSHION_A).pow(CUSHION_N) <= 1f
        CaseShape.TONNEAU -> abs(y) <= TON_HT && abs(x) <= tonneauHalfW(y)
    }

    /** Distance from the centre to the case outline along angle [a] (radians, screen space). */
    fun edge(s: CaseShape, a: Float): Float {
        if (s == CaseShape.ROUND) return CW.CASE_OUT
        if (s == CaseShape.CUSHION) return cushionR(a)
        var lo = 0.5f; var hi = 1.9f
        repeat(24) { val m = (lo + hi) / 2f; if (inside(s, cos(a) * m, sin(a) * m)) lo = m else hi = m }
        return lo
    }

    /** The case middle's outline in px around [c] for movement radius [u]. */
    fun outline(s: CaseShape, c: Offset, u: Float): Path = when (s) {
        CaseShape.ROUND -> circlePath(c, CW.CASE_OUT * u)
        CaseShape.CUSHION -> Path().apply {
            val n = 240
            for (i in 0..n) {
                val a = i * 2f * PI.toFloat() / n
                val r = cushionR(a) * u
                val x = c.x + cos(a) * r; val y = c.y + sin(a) * r
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        CaseShape.TONNEAU -> Path().apply {
            val n = 120
            // Right flank top to bottom, then the left flank back up (sine spacing: dense at the rounded ends).
            for (i in 0..n) {
                val y = TON_HT * sin(-PI.toFloat() / 2f + PI.toFloat() * i / n)
                val x = tonneauHalfW(y)
                if (i == 0) moveTo(c.x + x * u, c.y + y * u) else lineTo(c.x + x * u, c.y + y * u)
            }
            for (i in n downTo 0) {
                val y = TON_HT * sin(-PI.toFloat() / 2f + PI.toFloat() * i / n)
                lineTo(c.x - tonneauHalfW(y) * u, c.y + y * u)
            }
            close()
        }
    }

    /**
     * The short lugs of the cushion and tonneau cases (a pair at each end), as one path per lug. [sx], [sy]: the
     * quadrant. The root is hidden under the case body.
     */
    fun shortLug(s: CaseShape, c: Offset, u: Float, sx: Float, sy: Float): Path {
        val (xIn, xOut, root, tip) = when (s) {
            CaseShape.TONNEAU -> Quad(0.36f, 0.64f, 1.16f, 1.505f)
            else -> Quad(0.40f, 0.72f, 1.02f, 1.47f)
        }
        fun pt(x: Float, y: Float) = Offset(c.x + sx * x * u, c.y + sy * y * u)
        val rr = (xOut - xIn) * 0.5f
        val taper = 0.04f
        return Path().apply {
            val a = pt(xIn, root); moveTo(a.x, a.y)
            val b = pt(xIn + taper * 0.3f, tip - rr * 0.5f); lineTo(b.x, b.y)
            val q = pt((xIn + xOut) / 2f, tip + rr * 0.15f); val e = pt(xOut - taper, tip - rr * 0.5f)
            quadraticTo(q.x, q.y, e.x, e.y)
            val f = pt(xOut, root); lineTo(f.x, f.y)
            close()
        }
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)
}

// ------------------------------------------------------------------ path builders (px)

/** A capsule (stadium) from [a] to [b] with half-width [w]. */
internal fun capsule(a: Offset, b: Offset, w: Float): Path {
    val dx = b.x - a.x; val dy = b.y - a.y
    val len = hypot(dx, dy).coerceAtLeast(0.001f)
    val nx = -dy / len * w; val ny = dx / len * w
    val th = atan2(dy, dx) * 180f / PI.toFloat()
    return Path().apply {
        moveTo(a.x + nx, a.y + ny)
        lineTo(b.x + nx, b.y + ny)
        arcTo(Rect(b, w), th + 90f, -180f, false)
        lineTo(a.x - nx, a.y - ny)
        arcTo(Rect(a, w), th - 90f, -180f, false)
        close()
    }
}

internal fun circlePath(c: Offset, r: Float) = Path().apply { addOval(Rect(c, r)) }

internal fun union(vararg ps: Path): Path {
    var acc = ps[0]
    for (i in 1 until ps.size) acc = Path().apply { op(acc, ps[i], PathOperation.Union) }
    return acc
}

internal fun intersect(a: Path, b: Path) = Path().apply { op(a, b, PathOperation.Intersect) }
internal fun subtract(a: Path, b: Path) = Path().apply { op(a, b, PathOperation.Difference) }

/** Spur gear outline: [teeth] trapezoid teeth between radius [r] - [depth] and [r]. */
internal fun gearOutline(c: Offset, r: Float, teeth: Int, depth: Float, ratchet: Boolean = false): Path = Path().apply {
    val ri = r - depth
    val step = 2f * PI.toFloat() / teeth
    for (i in 0 until teeth) {
        val a0 = i * step
        if (ratchet) {
            // Saw teeth: a straight flank and a slope (the click drops off the straight flank)
            val p0 = pol(c, ri, a0); val p1 = pol(c, r, a0 + step * 0.08f); val p2 = pol(c, r * 0.995f, a0 + step * 0.22f)
            if (i == 0) moveTo(p0.x, p0.y) else lineTo(p0.x, p0.y)
            lineTo(p1.x, p1.y); lineTo(p2.x, p2.y)
        } else {
            val p0 = pol(c, ri, a0); val p1 = pol(c, r, a0 + step * 0.2f); val p2 = pol(c, r, a0 + step * 0.48f); val p3 = pol(c, ri, a0 + step * 0.66f)
            if (i == 0) moveTo(p0.x, p0.y) else lineTo(p0.x, p0.y)
            lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y)
        }
    }
    close()
}

/** Curved-spoke windows of a wheel (crossings), added to [p] as holes (use EvenOdd fill). */
internal fun addWindows(p: Path, c: Offset, rOut: Float, rIn: Float, spokes: Int, spokeHalfDeg: Float, twistDeg: Float, startDeg: Float = 0f) {
    val span = 360f / spokes
    for (i in 0 until spokes) {
        val a0 = startDeg + i * span + spokeHalfDeg
        val a1 = startDeg + (i + 1) * span - spokeHalfDeg
        p.arcTo(Rect(c, rOut), a0, a1 - a0, true)
        // The inner arc is shifted by the twist, so every spoke leans like a turbine blade.
        p.arcTo(Rect(c, rIn), a1 + twistDeg, -(a1 - a0), false)
        p.close()
    }
}

internal fun pol(c: Offset, r: Float, a: Float) = Offset(c.x + cos(a) * r, c.y + sin(a) * r)

// ------------------------------------------------------------------ brushes

/** Flat polished metal lit by [light]: bright on the lit side, dark on the far side. */
internal fun metalBrush(m: Metal, c: Offset, r: Float, light: KeyLight, contrast: Float = 1f): Brush {
    val lit = Offset(c.x + light.x * r, c.y + light.y * r)
    val far = Offset(c.x - light.x * r, c.y - light.y * r)
    return Brush.linearGradient(
        0f to lerp(m.base, m.dark, 0.55f * contrast),
        0.45f to m.base,
        0.78f to lerp(m.base, m.light, 0.55f * contrast),
        1f to lerp(m.base, m.light, 0.85f * contrast),
        start = far, end = lit,
    )
}

/** Turned (lathe) metal: alternating bright and dark sectors, aligned with the light. */
internal fun turnedBrush(m: Metal, c: Offset): Brush = Brush.sweepGradient(
    0f to m.light, 0.12f to m.base, 0.25f to m.dark, 0.38f to m.base, 0.5f to m.light,
    0.62f to m.base, 0.75f to m.dark, 0.88f to m.base, 1f to m.light, center = c,
)

// ------------------------------------------------------------------ soft shadows (cached layers only)

/**
 * [p] filled with [color] through a Gaussian blur of [radius] px, shifted by ([dx], [dy]). Software canvases only:
 * every caller draws into a cached bitmap, never on the live (hardware) canvas.
 */
internal fun DrawScope.blurred(p: Path, color: Color, radius: Float, dx: Float = 0f, dy: Float = 0f) {
    val paint = Paint().apply { this.color = color; isAntiAlias = true }
    if (radius >= 0.5f) paint.asFrameworkPaint().maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    drawIntoCanvas { cv ->
        cv.save(); cv.translate(dx, dy); cv.drawPath(p, paint); cv.restore()
    }
}

/** [p] filled with [brush] through a Gaussian blur of [radius] px (software canvases only, as [blurred]). */
internal fun DrawScope.blurredBrush(p: Path, brush: Brush, radius: Float, alpha: Float = 1f) {
    val paint = Paint().apply { isAntiAlias = true }
    brush.applyTo(size, paint, alpha)
    if (radius >= 0.5f) paint.asFrameworkPaint().maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    drawIntoCanvas { it.drawPath(p, paint) }
}

// ------------------------------------------------------------------ small parts

/** A ruby in a polished sink: specular dot toward the light, a darker oil sink in the centre. */
internal fun DrawScope.jewel(c: Offset, r: Float, light: KeyLight, sink: Metal, glow: Float = 0f) {
    drawCircle(Brush.radialGradient(listOf(sink.light, sink.base, sink.dark), Offset(c.x + light.x * r * 0.6f, c.y + light.y * r * 0.6f), r * 2.3f), r * 1.75f, c)
    drawCircle(sink.dark.a(0.7f), r * 1.75f, c, style = Stroke(width = (r * 0.12f).coerceAtLeast(0.8f)))
    drawCircle(Brush.radialGradient(listOf(CalPal.ruby.light, CalPal.ruby.base, CalPal.ruby.dark), Offset(c.x + light.x * r * 0.35f, c.y + light.y * r * 0.35f), r * 1.15f), r, c)
    drawCircle(CalPal.ruby.dark.a(0.85f), r * 0.32f, c)
    drawCircle(Color.White.a(0.85f), r * 0.2f, Offset(c.x + light.x * r * 0.42f, c.y + light.y * r * 0.42f))
    if (glow > 0.01f) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE6EA).a(0.85f * glow), Color.Transparent), c, r * 4.5f), r * 4.5f, c)
    }
}

/** A jewel set in a polished gold chaton held by two tiny blued screws (the top step). */
internal fun DrawScope.goldChaton(o: Offset, jr: Float, u: Float, light: KeyLight, screwDeg: Float) {
    val cr = jr * 2.5f
    drawCircle(Color.Black.a(0.30f), cr * 1.06f, Offset(o.x - light.x * cr * 0.12f, o.y - light.y * cr * 0.12f))
    drawCircle(metalBrush(CalPal.yellowGold, o, cr * 1.04f, light), cr, o)
    bevel(circlePath(o, cr), o, cr, light, (cr * 0.06f).coerceAtLeast(1f))
    drawCircle(CalPal.yellowGold.dark.a(0.5f), cr * 0.72f, o, style = Stroke(width = (cr * 0.05f).coerceAtLeast(0.8f)))
    jewel(o, jr, light, CalPal.yellowGold)
    for (k in 0..1) screw(pol(o, cr * 1.36f, (k * 180f + screwDeg) * PI.toFloat() / 180f), u * 0.016f, screwDeg * 1.7f + k * 50f, light, CalPal.blued)
}

/** A screw with its slot at [slotDeg]: heat-blued from the second step on, plain polished steel before it. */
internal fun DrawScope.screw(c: Offset, r: Float, slotDeg: Float, light: KeyLight, b: Metal = CalPal.blued) {
    drawCircle(Color.Black.a(0.30f), r * 1.12f, Offset(c.x - light.x * r * 0.25f, c.y - light.y * r * 0.25f))
    drawCircle(Brush.radialGradient(listOf(b.light, b.base, b.dark), Offset(c.x + light.x * r * 0.45f, c.y + light.y * r * 0.45f), r * 1.4f), r, c)
    val a = slotDeg * PI.toFloat() / 180f
    val d = Offset(cos(a) * r * 0.82f, sin(a) * r * 0.82f)
    drawLine(b.dark, c - d, c + d, strokeWidth = r * 0.3f, cap = StrokeCap.Butt)
    drawLine(b.light.a(0.5f), c - d + Offset(light.x, light.y), c + d + Offset(light.x, light.y), strokeWidth = r * 0.08f)
}

/** Polished bevel (anglage): a thin bright line on the lit side, dark on the shadow side. */
internal fun DrawScope.bevel(p: Path, c: Offset, r: Float, light: KeyLight, width: Float, strength: Float = 1f) {
    val lit = Offset(c.x + light.x * r, c.y + light.y * r)
    val far = Offset(c.x - light.x * r, c.y - light.y * r)
    drawPath(p, Brush.linearGradient(0f to Color.Black.a(0.50f * strength), 0.5f to Color.White.a(0.16f * strength), 1f to Color.White.a(0.90f * strength), start = far, end = lit), style = Stroke(width = width))
}

/** Fake soft shadow: a few offset fills of decreasing strength (parts over metal, inside the watch). */
internal fun DrawScope.softShadow(p: Path, dx: Float, dy: Float, strength: Float = 0.32f) {
    for (i in 1..3) {
        val k = i / 3f
        drawContext.transform.translate(dx * k, dy * k)
        drawPath(p, Color.Black.a(strength / i))
        drawContext.transform.translate(-dx * k, -dy * k)
    }
}

/**
 * Côtes de Genève: wide parallel bands, each lit like a shallow cylinder, clipped to [shape]. Kept low in contrast
 * (a soft ripple of light, not a stripe pattern).
 */
internal fun DrawScope.cotesDeGeneve(shape: Path, c: Offset, extent: Float, m: Metal, angleDeg: Float, band: Float) {
    clipPath(shape) {
        drawRect(m.base, Offset(c.x - extent, c.y - extent), Size(extent * 2, extent * 2))
        val a = angleDeg * PI.toFloat() / 180f
        val ux = cos(a); val uy = sin(a)        // along the stripes
        val vx = -uy; val vy = ux                // across
        val n = (extent * 2 / band).toInt() + 2
        for (i in -n / 2..n / 2) {
            val o = i * band
            val s = Offset(c.x + vx * o, c.y + vy * o)
            val e = Offset(c.x + vx * (o + band), c.y + vy * (o + band))
            val brush = Brush.linearGradient(
                0f to lerp(m.base, m.dark, 0.34f), 0.3f to m.base, 0.55f to lerp(m.base, m.light, 0.68f),
                0.72f to m.base, 1f to lerp(m.base, m.dark, 0.34f), start = s, end = e,
            )
            val p = Path().apply {
                moveTo(s.x - ux * extent * 2, s.y - uy * extent * 2); lineTo(s.x + ux * extent * 2, s.y + uy * extent * 2)
                lineTo(e.x + ux * extent * 2, e.y + uy * extent * 2); lineTo(e.x - ux * extent * 2, e.y - uy * extent * 2); close()
            }
            drawPath(p, brush)
        }
    }
}

/** Sunray brushing: fine radial lines (rotates with the part, like the real finish). */
internal fun DrawScope.sunray(c: Offset, r: Float, lines: Int, alpha: Float) {
    for (i in 0 until lines) {
        val a = i * 2f * PI.toFloat() / lines
        val col = if (i % 2 == 0) Color.White.a(alpha) else Color.Black.a(alpha * 0.8f)
        drawLine(col, c, pol(c, r, a), strokeWidth = (r * 0.012f).coerceAtLeast(0.6f))
    }
}

/** Circular graining (concentric fine rings). */
internal fun DrawScope.circularGrain(c: Offset, r0: Float, r1: Float, step: Float, alpha: Float) {
    var r = r0
    var k = 0
    while (r < r1) {
        drawCircle(if (k % 2 == 0) Color.White.a(alpha) else Color.Black.a(alpha * 0.7f), r, c, style = Stroke(width = step * 0.5f))
        r += step; k++
    }
}

internal fun evenOdd(p: Path): Path = p.apply { fillType = PathFillType.EvenOdd }
