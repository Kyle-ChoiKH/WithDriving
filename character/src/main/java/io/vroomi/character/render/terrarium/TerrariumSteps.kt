package io.vroomi.character.render.terrarium

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import io.vroomi.character.render.hash01
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The glass garden's upgrade ladder (spec 5.5). The vessel changes with the step, and every step keeps the
 * planting of the one before and adds its own:
 * 1 a small jar (moss and sprouts), 2 a corked jar (wider; a fern, a rosette, river stones), 3 a large flask
 * (a spring and a small stream into the pond, a small tree), 4 a glass box of flat panes (mushrooms, a flowering
 * plant, a small stone bridge), 5 an iron-framed Wardian case with a dome (vines and flowers all over).
 */
internal object TerraStep {
    fun planted(model: Int): Boolean = model >= 1
    fun stream(model: Int): Boolean = model >= 2
    fun bridge(model: Int): Boolean = model >= 3
    fun vines(model: Int): Boolean = model >= 4
}

/** The vessel of one step: its measures in design units and its outline. */
internal class Vessel(val kind: Int, val x: Float) {
    val base = 472f
    val half = floatArrayOf(150f, 226f, 262f, 250f, 270f)[kind]
    /** Where the wall turns into the shoulder (jars), the top edge (box) or the eave (case). */
    val shoulder = floatArrayOf(300f, 236f, 300f, 144f, 250f)[kind]
    val neckHalf = floatArrayOf(100f, 92f, 56f, 250f, 60f)[kind]
    val neckY = floatArrayOf(236f, 150f, 136f, 124f, 112f)[kind]
    val lipY = floatArrayOf(218f, 116f, 80f, 124f, 86f)[kind]
    val ground = if (kind == 0) 398f else 374f
    /** How much narrower than the corked jar (the small jar's planting moves in by this). */
    val q = min(1f, half / 226f)
    val box: Boolean get() = kind == 3
    val case: Boolean get() = kind == 4

    /** The outline, inset by [i] (0 the glass's outside, 7 its inside). */
    fun path(i: Float): Path = Path().apply {
        when (kind) {
            3 -> { moveTo(x - half + i, base - i); lineTo(x + half - i, base - i); lineTo(x + half - i, lipY + i); lineTo(x - half + i, lipY + i); close() }
            4 -> {
                val h = half - i; val top = lipY + i
                moveTo(x - h, base - i); lineTo(x + h, base - i); lineTo(x + h, shoulder)
                cubicTo(x + h, shoulder - 90f, x + h * 0.55f, top, x, top)
                cubicTo(x - h * 0.55f, top, x - h, shoulder - 90f, x - h, shoulder)
                close()
            }
            else -> {
                val c1 = base - (base - shoulder) * 0.30f; val c2 = base - (base - shoulder) * 0.73f
                val h = half - i; val nh = neckHalf - i
                moveTo(x - half + 18f + i * 0.3f, base - i)
                lineTo(x + half - 18f - i * 0.3f, base - i)
                quadraticTo(x + h, base - i, x + h + 2f, base - 20f - i * 0.3f)
                cubicTo(x + h + 9f, c1, x + h + 8f, c2, x + h - 2f, shoulder)
                cubicTo(x + h - 14f, shoulder - 54f + i * 0.6f, x + nh + 46f, neckY + 6f + i, x + nh, neckY + i)
                lineTo(x + nh, lipY + i); lineTo(x - nh, lipY + i); lineTo(x - nh, neckY + i)
                cubicTo(x - nh - 46f, neckY + 6f + i, x - h + 14f, shoulder - 54f + i * 0.6f, x - h + 2f, shoulder)
                cubicTo(x - h - 8f, c2, x - h - 9f, c1, x - h - 2f, base - 20f - i * 0.3f)
                quadraticTo(x - h, base - i, x - half + 18f + i * 0.3f, base - i)
                close()
            }
        }
    }

    /** Where the moth rests after arriving: on the lid, the cork, the lip, the box's top or the dome. */
    val mothAt: Offset get() = when (kind) {
        0 -> Offset(x + 46f, lipY - 14f)
        1 -> Offset(x + 30f, lipY - 30f)
        2 -> Offset(x + 30f, lipY - 8f)
        3 -> Offset(x + 80f, lipY - 30f)
        else -> Offset(x + 124f, 94f)
    }
}

private val HAIR = Stroke(0.9f)
private val NIGHT = Color(0xFF0B1A33)
private fun at(c: Color, night: Boolean) = if (night) lerp(c, NIGHT, 0.55f) else c

// ------------------------------------------------------------------ in the soil (baked, inside the glass)

/** A river stone: rounded, lit from the upper right. */
private fun DrawScope.riverStone(cx: Float, by: Float, w: Float, h: Float, tone: Float, night: Boolean) {
    val lit = at(lerp(Color(0xFFD9D5CA), Color(0xFFBFC6C2), tone), night); val dark = at(lerp(Color(0xFF6E6A62), Color(0xFF58605E), tone), night)
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(cx - w * 0.55f, by - 3f), Size(w * 1.1f, 7f))
    drawOval(Brush.radialGradient(listOf(lit, lerp(lit, dark, 0.6f), dark), Offset(cx + w * 0.2f, by - h * 0.8f), w * 0.9f), Offset(cx - w / 2f, by - h), Size(w, h))
}

/** What the step plants in the soil that does not move: stones, the spring's rocks, the bridge, mushrooms, the vines. */
internal fun DrawScope.stepSoil(v: Vessel, night: Boolean, leaf: Color, lit: Color, petal: Color) {
    val x = v.x; val gy = v.ground
    if (TerraStep.planted(v.kind)) {
        if (!TerraStep.stream(v.kind)) { riverStone(x + 30f, gy + 5f, 34f, 20f, 0.2f, night); riverStone(x + 58f, gy + 6f, 24f, 14f, 0.8f, night) }
        if (!TerraStep.bridge(v.kind)) riverStone(x + 176f, gy + 5f, 30f, 17f, 0.5f, night)
    }
    if (TerraStep.stream(v.kind)) {
        // The spring: three stacked stones the water comes out between.
        riverStone(x - 82f, gy + 5f, 40f, 30f, 0.7f, night)
        riverStone(x - 54f, gy + 6f, 30f, 20f, 0.3f, night)
        riverStone(x - 70f, gy - 16f, 28f, 18f, 0.5f, night)
    }
    if (TerraStep.bridge(v.kind)) {
        // A small stone bridge over the pond: one arch, voussoirs, moss on the deck.
        val x0 = x - 8f; val x1 = x + 124f; val xc = (x0 + x1) / 2f
        val stone = at(Color(0xFFA9A69B), night); val dark = at(Color(0xFF5E5B53), night); val pale = at(Color(0xFFD6D2C6), night)
        val arch = Path().apply {
            moveTo(x0 - 8f, gy + 3f); lineTo(x0 - 8f, gy - 14f); quadraticTo(xc, gy - 50f, x1 + 8f, gy - 14f); lineTo(x1 + 8f, gy + 3f)
            lineTo(x1 - 12f, gy + 3f); quadraticTo(xc, gy - 40f, x0 + 12f, gy + 3f); close()
        }
        drawPath(arch, Brush.verticalGradient(listOf(pale, stone, dark), startY = gy - 46f, endY = gy + 4f))
        for (k in 0..10) {
            val u = k / 10f
            val ix = (1 - u) * (1 - u) * (x0 + 12f) + 2 * (1 - u) * u * xc + u * u * (x1 - 12f)
            val iy = (1 - u) * (1 - u) * (gy + 3f) + 2 * (1 - u) * u * (gy - 40f) + u * u * (gy + 3f)
            val ox = (1 - u) * (1 - u) * (x0 - 8f) + 2 * (1 - u) * u * xc + u * u * (x1 + 8f)
            val oy = (1 - u) * (1 - u) * (gy - 14f) + 2 * (1 - u) * u * (gy - 50f) + u * u * (gy - 14f)
            drawLine(dark.copy(alpha = 0.55f), Offset(ix, iy), Offset(ix + (ox - ix) * 0.55f, iy + (oy - iy) * 0.55f), 1f)
        }
        val deck = Path().apply { moveTo(x0 - 8f, gy - 14f); quadraticTo(xc, gy - 50f, x1 + 8f, gy - 14f) }
        drawPath(deck, pale, style = Stroke(2.4f, cap = StrokeCap.Round))
        drawPath(arch, dark.copy(alpha = 0.7f), style = Stroke(1f, join = StrokeJoin.Round))
        for (k in 0 until 26) {
            val u = hash01(k, 211)
            val mx = (1 - u) * (1 - u) * (x0 - 8f) + 2 * (1 - u) * u * xc + u * u * (x1 + 8f)
            val my = (1 - u) * (1 - u) * (gy - 14f) + 2 * (1 - u) * u * (gy - 50f) + u * u * (gy - 14f)
            drawCircle(at(lerp(Color(0xFF4F7A3A), Color(0xFF9CC466), hash01(k, 212)), night).copy(alpha = 0.85f), 1.4f + hash01(k, 213) * 1.8f, Offset(mx, my - 1f))
        }
        // Mushrooms under the fern and by the far wall: a pale stem, a rounded ochre cap lit from above.
        for ((mx, mh) in listOf(x - 112f to 24f, x - 100f to 15f, x - 122f to 13f, x + 222f to 18f, x + 232f to 11f)) {
            val cap = at(Color(0xFFC98F5A), night); val capLit = at(Color(0xFFEBC79A), night)
            drawLine(at(Color(0xFFE9E1CF), night), Offset(mx, gy + 2f), Offset(mx, gy - mh), 3.4f + mh * 0.08f, cap = StrokeCap.Round)
            val r = 6f + mh * 0.36f
            drawArc(Brush.verticalGradient(listOf(capLit, cap, lerp(cap, Color.Black, 0.3f)), startY = gy - mh - r * 0.8f, endY = gy - mh + 2f), 180f, 180f, true, Offset(mx - r, gy - mh - r * 0.8f), Size(r * 2f, r * 1.6f))
            drawLine(at(Color(0xFF7A5230), night).copy(alpha = 0.6f), Offset(mx - r, gy - mh), Offset(mx + r, gy - mh), 1f)
        }
    }
    if (TerraStep.vines(v.kind)) {
        // A climber up the right wall and along the dome; strands hang from the dome on the left; flowers at the back.
        fun leafAt(px: Float, py: Float, ang: Float, len: Float, k: Int) {
            rotate(ang, Offset(px, py)) {
                drawOval(at(lerp(leaf, Color.Black, 0.15f), night), Offset(px, py - len * 0.22f), Size(len, len * 0.44f))
                drawOval(at(lerp(leaf, lit, 0.5f + 0.3f * hash01(k, 221)), night), Offset(px + len * 0.08f, py - len * 0.22f), Size(len * 0.85f, len * 0.22f))
            }
        }
        fun blossom(px: Float, py: Float, r: Float) {
            for (p in 0 until 5) { val a = p * 72f * PI.toFloat() / 180f; drawCircle(at(petal, night), r * 0.62f, Offset(px + cos(a) * r * 0.7f, py + sin(a) * r * 0.7f)) }
            drawCircle(at(Color(0xFFE9B53C), night), r * 0.4f, Offset(px, py))
        }
        val stem = at(Color(0xFF5B7A3A), night)
        var px = x + v.half - 20f; var py = gy
        for (k in 0 until 34) {
            val u = k / 33f
            // Up the wall, then over along the dome toward the middle.
            val nx = x + (v.half - 20f) * (if (u < 0.45f) 1f else 1f - 0.62f * ((u - 0.45f) / 0.55f).let { it * it }) + 5f * sin(k * 0.9f)
            val ny = if (u < 0.45f) gy - (gy - v.shoulder) * (u / 0.45f) else v.shoulder - (v.shoulder - v.lipY - 26f) * sin(((u - 0.45f) / 0.55f) * PI.toFloat() / 2f)
            drawLine(stem, Offset(px, py), Offset(nx, ny), 2.2f - 1.2f * u, cap = StrokeCap.Round)
            if (k % 2 == 0) leafAt(nx, ny, if (k % 4 == 0) 200f + 30f * hash01(k, 222) else -30f + 30f * hash01(k, 223), 15f + 6f * hash01(k, 224), k)
            if (k % 5 == 3) blossom(nx - 6f, ny + 4f, 5f)
            px = nx; py = ny
        }
        for (sIdx in 0 until 4) {
            var hx = x - v.half * (0.62f - sIdx * 0.13f); var hy = v.lipY + 44f - sIdx * 10f + (sIdx % 2) * 14f
            val n = 6 + (sIdx * 2) % 5
            for (k in 0 until n) {
                val nx = hx + 3f * sin(k * 1.1f + sIdx); val ny = hy + 13f
                drawLine(stem, Offset(hx, hy), Offset(nx, ny), 1.3f, cap = StrokeCap.Round)
                leafAt(nx, ny, if (k % 2 == 0) 150f else 30f, 12f, k + sIdx * 9)
                if (k % 3 == 2 || k == n - 1) blossom(nx + (if (k % 2 == 0) 5f else -5f), ny + 5f, 4.4f)
                hx = nx; hy = ny
            }
        }
        for (k in 0 until 9) {
            val fx = x - v.half + 34f + hash01(k, 231) * (v.half * 2f - 68f)
            if (fx > x - 6f && fx < x + 126f) continue
            val fh = 18f + hash01(k, 232) * 22f
            drawLine(stem, Offset(fx, gy + 2f), Offset(fx + 2f, gy - fh), 1.2f)
            blossom(fx + 2f, gy - fh, 4f + hash01(k, 233) * 1.6f)
        }
    }
}

// ------------------------------------------------------------------ alive (drawn every frame, inside the glass)

/** The small jar's sprouts: a short stem and two seed leaves each; they nod with the fan. */
internal fun DrawScope.sprouts(v: Vessel, sway: Float, leaf: Color, lit: Color) {
    for (k in 0 until 4) {
        val bx = v.x + floatArrayOf(-64f, -8f, 44f, 92f)[k]; val h = floatArrayOf(22f, 34f, 18f, 26f)[k]
        val base = Offset(bx, v.ground - 2f)
        rotate(sway * (if (k % 2 == 0) 1f else -0.7f), base) {
            val top = Offset(bx + 2f, v.ground - 2f - h)
            drawLine(lerp(leaf, lit, 0.5f), base, top, 1.8f, cap = StrokeCap.Round)
            for (s in floatArrayOf(-1f, 1f)) rotate(if (s < 0f) -148f else -32f, top) {
                drawOval(leaf, Offset(top.x, top.y - 3.6f), Size(13f + h * 0.1f, 7.2f))
                drawOval(lit.copy(alpha = 0.8f), Offset(top.x + 1.5f, top.y - 3.2f), Size(10f, 3f))
            }
        }
    }
}

private val WATER = Color(0xFF8CC3D4)
private val WATER_DEEP = Color(0xFF3F7286)
private val STREAM = Path()

/** The stream from the spring's stones down into the pond: a narrow ribbon whose glints drift with the flow. */
internal fun DrawScope.stream(v: Vessel, t: Float, night: Float) {
    val x = v.x; val gy = v.ground
    val a = Offset(x - 64f, gy - 8f); val c = Offset(x - 40f, gy + 12f); val b = Offset(x + 14f, gy + 5f)
    STREAM.reset(); STREAM.moveTo(a.x, a.y); STREAM.quadraticTo(c.x, c.y, b.x, b.y)
    val dim = 1f - 0.5f * night
    drawPath(STREAM, Color(0xFF8A8375).copy(alpha = 0.9f * dim), style = Stroke(15f, cap = StrokeCap.Round))
    drawPath(STREAM, WATER_DEEP.copy(alpha = 0.95f * dim), style = Stroke(10f, cap = StrokeCap.Round))
    drawPath(STREAM, WATER.copy(alpha = 0.95f * dim), style = Stroke(6f, cap = StrokeCap.Round))
    // A thin fall between the stones.
    drawLine(WATER.copy(alpha = 0.85f * dim), Offset(x - 67f, gy - 22f), a, 3.4f, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.5f * dim), Offset(x - 66.4f, gy - 21f), Offset(a.x + 0.6f, a.y - 1f), 1f)
    for (k in 0 until 5) {
        val u = ((t * 0.22f + k / 5f) % 1f)
        val om = 1f - u
        val px = om * om * a.x + 2f * om * u * c.x + u * u * b.x
        val py = om * om * a.y + 2f * om * u * c.y + u * u * b.y
        drawLine(Color.White.copy(alpha = 0.6f * dim * sin(u * PI.toFloat())), Offset(px - 3f, py - 0.6f), Offset(px + 3f, py + 0.2f), 1.1f, cap = StrokeCap.Round)
    }
}

// ------------------------------------------------------------------ on the glass (baked, in sharp focus)

private val CORK = Color(0xFFC49A62)
private val IRON = Color(0xFF27322E)
private val IRON_LIT = Color(0xFF8FA69A)
private val BRASS = Color(0xFF8A6A32)
private val BRASS_LIT = Color(0xFFE2C27A)

private fun DrawScope.bar(a: Offset, b: Offset, w: Float, body: Color, lit: Color, dim: Float) {
    drawLine(body.copy(alpha = dim), a, b, w, cap = StrokeCap.Round)
    drawLine(lit.copy(alpha = 0.55f * dim), Offset(a.x + w * 0.22f, a.y - w * 0.1f), Offset(b.x + w * 0.22f, b.y - w * 0.1f), w * 0.22f, cap = StrokeCap.Round)
}

/** What closes or frames the vessel: a cork, the flask's rolled lip, the box's leaded edges and lid, the case's iron frame. */
internal fun DrawScope.vesselTop(v: Vessel, spec: Color, specA: Float, glassEdge: Color, night: Boolean) {
    val x = v.x
    val dim = if (night) 0.8f else 1f
    when (v.kind) {
        1 -> {
            // A cork stopper, a little wider at the top, with its pores; the neck's rim around it.
            val nh = v.neckHalf
            val cork = Path().apply { moveTo(x - nh + 8f, v.lipY + 20f); lineTo(x + nh - 8f, v.lipY + 20f); lineTo(x + nh + 2f, v.lipY - 26f); lineTo(x - nh - 2f, v.lipY - 26f); close() }
            val c = at(CORK, night)
            drawPath(cork, Brush.horizontalGradient(listOf(lerp(c, Color.Black, 0.35f), c, lerp(c, Color.White, 0.25f), lerp(c, Color.Black, 0.25f)), startX = x - nh, endX = x + nh))
            for (k in 0 until 70) {
                val px = x - nh + 6f + hash01(k, 241) * (nh * 2f - 12f); val py = v.lipY - 24f + hash01(k, 242) * 40f
                drawOval(Color(0xFF5E4022).copy(alpha = 0.22f + 0.2f * hash01(k, 243)), Offset(px, py), Size(2f + hash01(k, 244) * 4f, 1.2f + hash01(k, 245)))
            }
            drawOval(lerp(c, Color.White, 0.3f), Offset(x - nh - 2f, v.lipY - 31f), Size(nh * 2f + 4f, 10f))
            drawOval(Color(0xFF5E4022).copy(alpha = 0.5f), Offset(x - nh - 2f, v.lipY - 31f), Size(nh * 2f + 4f, 10f), style = HAIR)
            drawRect(glassEdge.copy(alpha = if (night) 0.25f else 0.42f), Offset(x - nh - 5f, v.lipY - 3f), Size(nh * 2f + 10f, 8f))
            drawLine(spec.copy(alpha = 0.8f * specA), Offset(x + 10f, v.lipY - 1f), Offset(x + nh + 2f, v.lipY - 1f), 1.4f, cap = StrokeCap.Round)
        }
        2 -> {
            // The flask's open neck: a rolled lip.
            val nh = v.neckHalf
            drawOval(glassEdge.copy(alpha = if (night) 0.22f else 0.36f), Offset(x - nh - 7f, v.lipY - 7f), Size(nh * 2f + 14f, 14f))
            drawOval(spec.copy(alpha = 0.6f * specA), Offset(x - nh - 7f, v.lipY - 7f), Size(nh * 2f + 14f, 14f), style = Stroke(1.5f))
            drawOval(Color(0xFF0E1A10).copy(alpha = 0.25f), Offset(x - nh + 4f, v.lipY - 3f), Size(nh * 2f - 8f, 7f))
            drawLine(spec.copy(alpha = 0.75f * specA), Offset(x + nh - 12f, v.lipY + 20f), Offset(x + nh - 12f, v.neckY - 6f), 2f, cap = StrokeCap.Round)
        }
        3 -> {
            // Flat panes joined by leaded edges; the lid is the box's top face, seen a little from above.
            val h = v.half; val top = v.lipY
            val lid = Path().apply { moveTo(x - h - 4f, top); lineTo(x + h + 4f, top); lineTo(x + h - 34f, top - 30f); lineTo(x - h + 34f, top - 30f); close() }
            drawPath(lid, glassEdge.copy(alpha = if (night) 0.14f else 0.22f))
            drawPath(lid, Brush.linearGradient(listOf(spec.copy(alpha = 0.34f * specA), spec.copy(alpha = 0f)), Offset(x + h, top - 30f), Offset(x, top)))
            for (sx in floatArrayOf(-0.5f, 0.5f)) {
                bar(Offset(x + sx * h, top), Offset(x + sx * h, v.base - 4f), 4.2f, BRASS, BRASS_LIT, dim)
                bar(Offset(x + sx * h, top), Offset(x + sx * (h - 38f), top - 30f), 3.4f, BRASS, BRASS_LIT, dim)
            }
            bar(Offset(x - h, top), Offset(x - h, v.base - 4f), 5.4f, BRASS, BRASS_LIT, dim); bar(Offset(x + h, top), Offset(x + h, v.base - 4f), 5.4f, BRASS, BRASS_LIT, dim)
            bar(Offset(x - h - 4f, top), Offset(x + h + 4f, top), 5.4f, BRASS, BRASS_LIT, dim)
            bar(Offset(x - h + 34f, top - 30f), Offset(x + h - 34f, top - 30f), 3.6f, BRASS, BRASS_LIT, dim)
            bar(Offset(x - h - 4f, top), Offset(x - h + 34f, top - 30f), 3.6f, BRASS, BRASS_LIT, dim); bar(Offset(x + h + 4f, top), Offset(x + h - 34f, top - 30f), 3.6f, BRASS, BRASS_LIT, dim)
            bar(Offset(x - h - 3f, v.base - 3f), Offset(x + h + 3f, v.base - 3f), 7f, BRASS, BRASS_LIT, dim)
            drawCircle(BRASS.copy(alpha = dim), 7f, Offset(x, top - 22f)); drawCircle(BRASS_LIT.copy(alpha = 0.8f * dim), 2.2f, Offset(x + 2.4f, top - 24.4f))
        }
        4 -> {
            // The Wardian case: an iron plinth, mullions, the eave band, ribs gathering at the dome's finial.
            val h = v.half; val sh = v.shoulder; val top = v.lipY
            for (m in floatArrayOf(-0.5f, 0f, 0.5f)) {
                bar(Offset(x + m * h, sh), Offset(x + m * h, v.base - 10f), 4.4f, IRON, IRON_LIT, dim)
                if (m != 0f) {
                    val rib = Path().apply { moveTo(x + m * h, sh); cubicTo(x + m * h, sh - 90f, x + m * h * 0.55f, top, x, top) }
                    drawPath(rib, IRON.copy(alpha = dim), style = Stroke(3.6f, cap = StrokeCap.Round))
                    drawPath(rib, IRON_LIT.copy(alpha = 0.4f * dim), style = Stroke(0.9f))
                }
            }
            bar(Offset(x, sh), Offset(x, top), 3.6f, IRON, IRON_LIT, dim)
            drawPath(v.path(0f), IRON.copy(alpha = dim), style = Stroke(6f, join = StrokeJoin.Round))
            drawPath(v.path(2.2f), IRON_LIT.copy(alpha = 0.35f * dim), style = Stroke(0.9f))
            bar(Offset(x - h - 3f, sh), Offset(x + h + 3f, sh), 7f, IRON, IRON_LIT, dim)
            drawRect(IRON.copy(alpha = dim), Offset(x - h - 10f, v.base - 12f), Size(h * 2f + 20f, 16f))
            drawRect(IRON_LIT.copy(alpha = 0.45f * dim), Offset(x - h - 10f, v.base - 12f), Size(h * 2f + 20f, 1.6f))
            for (k in 0 until 9) drawCircle(IRON_LIT.copy(alpha = 0.5f * dim), 1.6f, Offset(x - h + 6f + k * (h * 2f - 12f) / 8f, v.base - 4f))
            // Finial.
            drawLine(IRON.copy(alpha = dim), Offset(x, top + 2f), Offset(x, top - 30f), 3f, cap = StrokeCap.Round)
            drawCircle(IRON.copy(alpha = dim), 6.4f, Offset(x, top - 12f)); drawCircle(IRON_LIT.copy(alpha = 0.7f * dim), 1.8f, Offset(x + 2.2f, top - 14.2f))
        }
    }
}
