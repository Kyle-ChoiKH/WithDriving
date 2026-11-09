package io.vroomi.character.render.calibre

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
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

/** Heat-blued screw with its slot at [slotDeg]. */
internal fun DrawScope.screw(c: Offset, r: Float, slotDeg: Float, light: KeyLight) {
    val b = CalPal.blued
    drawCircle(Color.Black.a(0.35f), r * 1.12f, Offset(c.x - light.x * r * 0.25f, c.y - light.y * r * 0.25f))
    drawCircle(Brush.radialGradient(listOf(b.light, b.base, b.dark), Offset(c.x + light.x * r * 0.45f, c.y + light.y * r * 0.45f), r * 1.4f), r, c)
    val a = slotDeg * PI.toFloat() / 180f
    val d = Offset(cos(a) * r * 0.82f, sin(a) * r * 0.82f)
    drawLine(b.dark, c - d, c + d, strokeWidth = r * 0.3f, cap = StrokeCap.Butt)
    drawLine(b.light.a(0.5f), c - d + Offset(light.x, light.y), c + d + Offset(light.x, light.y), strokeWidth = r * 0.08f)
}

/** Polished bevel (anglage): a thin bright line on the lit side, dark on the shadow side. */
internal fun DrawScope.bevel(p: Path, c: Offset, r: Float, light: KeyLight, width: Float) {
    val lit = Offset(c.x + light.x * r, c.y + light.y * r)
    val far = Offset(c.x - light.x * r, c.y - light.y * r)
    drawPath(p, Brush.linearGradient(0f to Color.Black.a(0.55f), 0.5f to Color.White.a(0.18f), 1f to Color.White.a(0.92f), start = far, end = lit), style = Stroke(width = width))
}

/** Fake soft shadow: a few offset fills of decreasing strength. */
internal fun DrawScope.softShadow(p: Path, dx: Float, dy: Float, strength: Float = 0.32f) {
    for (i in 1..3) {
        val k = i / 3f
        drawContext.transform.translate(dx * k, dy * k)
        drawPath(p, Color.Black.a(strength / i))
        drawContext.transform.translate(-dx * k, -dy * k)
    }
}

/** Côtes de Genève: wide parallel bands, each lit like a shallow cylinder, clipped to [shape]. */
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
                0f to lerp(m.base, m.dark, 0.45f), 0.3f to m.base, 0.55f to lerp(m.base, m.light, 0.8f),
                0.72f to m.base, 1f to lerp(m.base, m.dark, 0.45f), start = s, end = e,
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
