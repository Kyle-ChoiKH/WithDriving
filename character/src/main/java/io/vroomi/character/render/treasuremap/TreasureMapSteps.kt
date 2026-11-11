package io.vroomi.character.render.treasuremap

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import io.vroomi.character.render.hash01
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The engraved chart's upgrade ladder (spec 5.7). Every step keeps what the one before had:
 * 1 an ink chart (aged parchment, the graticule and the net of rhumb lines, the inked track), 2 the large compass
 * rose and the scale bar, 3 pale watercolour washes for land and sea along an invented coast, 4 the title
 * cartouche, a sailing ship and a sea serpent in the margins' waters, 5 a gold-leaf border, a wax seal with
 * ribbons and a gilded track. The coast is ornament only: it is the same on every drive and shows no real place.
 */
internal object MapStep {
    fun rose(model: Int): Boolean = model >= 1
    fun wash(model: Int): Boolean = model >= 2
    fun decor(model: Int): Boolean = model >= 3
    fun gold(model: Int): Boolean = model >= 4
}

private val INK = Color(0xFF2B2119)
private val INK_FAINT = Color(0xFF8C7B66)
private val PAPER = Color(0xFFF2EAD6)
internal val GOLD = Color(0xFFC9A227)
internal val GOLD_LIGHT = Color(0xFFF3DC84)
internal val GOLD_DARK = Color(0xFF7E5F10)
private val PEN = Stroke(1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val FINE = Stroke(0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** The sheet's age: stains with a tide line, foxing, two old folds. Low contrast everywhere. */
internal fun DrawScope.parchment(dw: Float, dh: Float) {
    for (i in 0 until 9) {
        val c = Offset(hash01(i, 501) * dw, hash01(i, 502) * dh); val r = 70f + hash01(i, 503) * 170f
        val tone = lerp(Color(0xFF9A7440), Color(0xFF6E5A3A), hash01(i, 504))
        for (k in 0 until 3) {
            val o = Offset(c.x + (hash01(i * 7 + k, 505) - 0.5f) * r, c.y + (hash01(i * 7 + k, 506) - 0.5f) * r * 0.6f)
            drawCircle(Brush.radialGradient(0f to tone.copy(alpha = 0.020f), 0.8f to tone.copy(alpha = 0.016f), 0.94f to tone.copy(alpha = 0.036f), 1f to tone.copy(alpha = 0f), center = o, radius = r * 0.7f), r * 0.7f, o)
        }
    }
    for (i in 0 until 150) {
        val p = Offset(hash01(i, 511) * dw, hash01(i, 512) * dh)
        drawCircle(Color(0xFF946A38).copy(alpha = 0.08f + 0.14f * hash01(i, 513)), 0.7f + hash01(i, 514) * hash01(i, 515) * 2.6f, p)
    }
    for (fx in floatArrayOf(dw / 3f, dw * 2f / 3f)) {
        drawLine(Color(0xFF5A4A3A).copy(alpha = 0.10f), Offset(fx, 0f), Offset(fx + 2f, dh), 1.2f)
        drawLine(Color.White.copy(alpha = 0.30f), Offset(fx + 1.6f, 0f), Offset(fx + 3.6f, dh), 1f)
    }
}

/** The graticule: fine meridians and parallels with small crosses where they meet. */
internal fun DrawScope.graticule(l: Float, t: Float, r: Float, b: Float) {
    val step = 140f
    var x = l + step * 0.6f
    while (x < r) { drawLine(INK_FAINT.copy(alpha = 0.22f), Offset(x, t), Offset(x, b), 0.5f); x += step }
    var y = t + step * 0.5f
    while (y < b) { drawLine(INK_FAINT.copy(alpha = 0.22f), Offset(l, y), Offset(r, y), 0.5f); y += step }
    x = l + step * 0.6f
    while (x < r) {
        y = t + step * 0.5f
        while (y < b) { drawLine(INK.copy(alpha = 0.45f), Offset(x - 4f, y), Offset(x + 4f, y), 0.7f); drawLine(INK.copy(alpha = 0.45f), Offset(x, y - 4f), Offset(x, y + 4f), 0.7f); y += step }
        x += step
    }
}

/** Step 1's small wind mark where the large rose will stand: a ring, eight points, a north arrowhead. */
internal fun DrawScope.windMark(c: Offset) {
    drawCircle(PAPER, 26f, c); drawCircle(INK, 26f, c, style = PEN); drawCircle(INK, 22f, c, style = FINE)
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f - PI.toFloat() / 2f; val len = if (i % 2 == 0) 22f else 12f
        val nx = -sin(a); val ny = cos(a)
        val p = Path().apply { moveTo(c.x + cos(a) * len, c.y + sin(a) * len); lineTo(c.x + nx * 3f, c.y + ny * 3f); lineTo(c.x - nx * 3f, c.y - ny * 3f); close() }
        drawPath(p, if (i % 2 == 0) INK else PAPER); drawPath(p, INK, style = FINE)
    }
    val n = Path().apply { moveTo(c.x, c.y - 40f); lineTo(c.x - 5f, c.y - 30f); lineTo(c.x + 5f, c.y - 30f); close() }
    drawPath(n, INK)
}

/**
 * Step 2 draws the land: little hills with their shaded side and clumps of trees, in the mapmaker's pen, across
 * the middle of the sheet (the corners stay clear for the seas of step 3). Fixed places: the chart never moves.
 */
internal fun DrawScope.terrain(l: Float, t: Float, r: Float, b: Float) {
    val w = r - l; val h = b - t
    for (i in 0 until 84) {
        val u = hash01(i, 301); val v = hash01(i, 302)
        if (u + v < 0.62f || u + v > 1.38f) continue
        if (u > 0.40f && u < 0.60f && v > 0.34f && v < 0.66f) continue      // the point of departure stays open
        if ((u > 0.80f && v < 0.42f) || (u < 0.27f && v > 0.66f)) continue    // so do the rose and the title
        val x = l + 30f + u * (w - 60f); val y = t + 34f + v * (h - 60f)
        val sc = 1.5f + hash01(i, 303) * 0.9f
        if (i % 3 != 0) {
            val p = Path().apply {
                moveTo(x - 20f * sc, y); quadraticTo(x - 8f * sc, y - 26f * sc, x + 2f * sc, y - 8f * sc)
                quadraticTo(x + 10f * sc, y - 20f * sc, x + 22f * sc, y)
            }
            drawPath(p, PAPER.copy(alpha = 0.7f)); drawPath(p, INK.copy(alpha = 0.95f), style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            for (k in 0 until 4) drawLine(INK.copy(alpha = 0.7f), Offset(x + (4f + k * 4f) * sc, y - (11f - k * 2.6f) * sc), Offset(x + (8f + k * 4f) * sc, y - 1f), 0.8f)
        } else {
            for (k in 0 until 3) {
                val tx = x + (k - 1) * 11f * sc; val ty = y - (if (k == 1) 5f else 0f) * sc
                drawLine(INK.copy(alpha = 0.7f), Offset(tx, ty), Offset(tx, ty - 7f * sc), 1f)
                drawCircle(PAPER, 6.4f * sc, Offset(tx, ty - 12f * sc)); drawCircle(INK.copy(alpha = 0.9f), 6.4f * sc, Offset(tx, ty - 12f * sc), style = PEN)
                drawArc(INK.copy(alpha = 0.5f), 300f, 130f, false, Offset(tx - 4f * sc, ty - 16f * sc), Size(8f * sc, 8f * sc), style = FINE)
            }
        }
    }
}

/** The point of departure's ornament, there from the first metre: rings, eight ticks, a dotted circle. */
internal fun DrawScope.departure(x: Float, y: Float, alpha: Float) {
    val c = Offset(x, y)
    drawCircle(INK.copy(alpha = 0.8f * alpha), 11f, c, style = FINE)
    for (i in 0 until 8) { val a = i * PI.toFloat() / 4f; drawLine(INK.copy(alpha = 0.8f * alpha), Offset(x + cos(a) * 14f, y + sin(a) * 14f), Offset(x + cos(a) * (if (i % 2 == 0) 21f else 18f), y + sin(a) * (if (i % 2 == 0) 21f else 18f)), 0.8f) }
    for (i in 0 until 28) { val a = i * 2f * PI.toFloat() / 28f; drawCircle(INK.copy(alpha = 0.6f * alpha), 0.7f, Offset(x + cos(a) * 27f, y + sin(a) * 27f)) }
}

/** An invented coast: a wavy line from [a] to [b] (the sea lies toward [corner]). */
private fun coast(a: Offset, b: Offset, seed: Int): Path = Path().apply {
    moveTo(a.x, a.y)
    val n = 14
    val dx = b.x - a.x; val dy = b.y - a.y
    for (i in 1..n) {
        val u = i / n.toFloat()
        val off = (hash01(i, seed) - 0.5f) * 46f * sin(u * PI.toFloat())
        val mx = a.x + dx * (u - 0.5f / n) - dy / 300f * off * 1.4f; val my = a.y + dy * (u - 0.5f / n) + dx / 300f * off * 1.4f
        quadraticTo(mx, my, a.x + dx * u + (if (i < n) -dy / 300f * off else 0f), a.y + dy * u + (if (i < n) dx / 300f * off else 0f))
    }
}

private val SEA = Color(0xFF7FAFBD)
private val LAND = Color(0xFFC2B05E)

/** Step 3: watercolour. Two seas in opposite corners behind an inked coast with water lining, land washes between. */
internal fun DrawScope.washes(l: Float, t: Float, r: Float, b: Float) {
    val w = r - l; val h = b - t
    clipRect(l, t, r, b) {
        for (i in 0 until 6) {
            val c = Offset(l + w * (0.2f + 0.6f * hash01(i, 521)), t + h * (0.15f + 0.7f * hash01(i, 522))); val rr = 140f + hash01(i, 523) * 150f
            drawCircle(Brush.radialGradient(0f to LAND.copy(alpha = 0.11f), 0.85f to LAND.copy(alpha = 0.09f), 0.95f to LAND.copy(alpha = 0.15f), 1f to LAND.copy(alpha = 0f), center = c, radius = rr), rr, c)
        }
        for (k in 0..1) {
            val a = if (k == 0) Offset(r + 10f, t + h * 0.34f) else Offset(l - 10f, t + h * 0.56f)
            val bb = if (k == 0) Offset(l + w * 0.60f, b + 10f) else Offset(l + w * 0.24f, t - 10f)
            val corner = if (k == 0) Offset(r + 10f, b + 10f) else Offset(l - 10f, t - 10f)
            val line = coast(a, bb, 531 + k)
            val sea = Path().apply { addPath(line); lineTo(corner.x, corner.y); close() }
            drawPath(sea, SEA.copy(alpha = 0.22f))
            clipPath(sea) {
                // The pigment pools along the shore, and the engraver's water lines follow it.
                drawPath(line, SEA.copy(alpha = 0.20f), style = Stroke(26f, join = StrokeJoin.Round))
                drawPath(line, SEA.copy(alpha = 0.22f), style = Stroke(10f, join = StrokeJoin.Round))
                for (j in 1..3) withTransform({ translate((corner.x - (a.x + bb.x) / 2f) / 60f * j, (corner.y - (a.y + bb.y) / 2f) / 60f * j) }) { drawPath(line, INK.copy(alpha = 0.30f - 0.07f * j), style = FINE) }
            }
            drawPath(line, INK.copy(alpha = 0.85f), style = PEN)
        }
    }
}

/** Step 4: a small ship under sail in the lower-right sea and a sea serpent in the upper-left one, engraved. */
internal fun DrawScope.seaLife(l: Float, t: Float, r: Float, b: Float) {
    val h = b - t
    // The ship.
    val sx = r - 96f; val sy = t + h * 0.60f
    val hull = Path().apply { moveTo(sx - 32f, sy); quadraticTo(sx - 26f, sy + 13f, sx - 12f, sy + 14f); lineTo(sx + 18f, sy + 14f); quadraticTo(sx + 30f, sy + 10f, sx + 36f, sy - 4f); lineTo(sx + 22f, sy + 1f); lineTo(sx - 22f, sy + 1f); close() }
    drawPath(hull, PAPER); drawPath(hull, INK, style = PEN)
    for (k in 1..3) drawLine(INK.copy(alpha = 0.7f), Offset(sx - 26f + k * 2f, sy + 3f + k * 2.6f), Offset(sx + 26f - k * 1.5f, sy + 3f + k * 2.6f), 0.5f)
    for ((mx, mh) in listOf(sx - 12f to 46f, sx + 10f to 54f)) {
        drawLine(INK, Offset(mx, sy + 1f), Offset(mx, sy - mh), 1.2f)
        for (k in 0..1) {
            val top = sy - mh + 5f + k * (mh * 0.44f); val sh = mh * 0.36f; val sw = 17f - k * 1f
            val sail = Path().apply { moveTo(mx - sw, top); lineTo(mx + sw, top); quadraticTo(mx + sw + 6f, top + sh * 0.5f, mx + sw, top + sh); lineTo(mx - sw, top + sh); quadraticTo(mx - sw + 6f, top + sh * 0.5f, mx - sw, top); close() }
            drawPath(sail, PAPER); drawPath(sail, INK, style = FINE)
            for (j in 1..4) drawLine(INK.copy(alpha = 0.55f), Offset(mx + sw - j * 2.2f, top + 1.5f), Offset(mx + sw + 3f - j * 2.2f, top + sh - 1.5f), 0.45f)
        }
        val flag = Path().apply { moveTo(mx, sy - mh); lineTo(mx + 11f, sy - mh + 2.5f); lineTo(mx, sy - mh + 5f); close() }
        drawPath(flag, INK)
    }
    drawLine(INK, Offset(sx + 34f, sy - 5f), Offset(sx + 50f, sy - 14f), 0.9f)
    for (k in 0 until 5) drawArc(INK.copy(alpha = 0.7f), 200f, 140f, false, Offset(sx - 50f + k * 22f, sy + 15f + (k % 2) * 3f), Size(16f, 6f), style = FINE)
    // The serpent: three coils rising from the water, a head with a crest, a tail fluke.
    val qx = l + 74f; val qy = t + h * 0.30f
    for (k in 0 until 3) {
        val cx = qx + k * 30f
        val coil = Path().apply { moveTo(cx - 12f, qy); quadraticTo(cx, qy - 30f + k * 5f, cx + 12f, qy) }
        drawPath(coil, INK, style = Stroke(6.4f, cap = StrokeCap.Round)); drawPath(coil, PAPER, style = Stroke(4.2f, cap = StrokeCap.Round))
        for (j in 0 until 5) { val u = 0.15f + j * 0.17f; val px = cx - 12f + 24f * u; val py = qy - (30f - k * 5f) * 2f * u * (1f - u) * 1f; drawLine(INK.copy(alpha = 0.7f), Offset(px, py - 2f), Offset(px + 1.4f, py + 2f), 0.5f) }
        drawArc(INK.copy(alpha = 0.7f), 200f, 140f, false, Offset(cx - 18f, qy - 1f), Size(36f, 7f), style = FINE)
    }
    val neck = Path().apply { moveTo(qx - 26f, qy); quadraticTo(qx - 34f, qy - 26f, qx - 46f, qy - 30f) }
    drawPath(neck, INK, style = Stroke(6.4f, cap = StrokeCap.Round)); drawPath(neck, PAPER, style = Stroke(4.2f, cap = StrokeCap.Round))
    val head = Path().apply { moveTo(qx - 42f, qy - 36f); lineTo(qx - 60f, qy - 31f); lineTo(qx - 44f, qy - 25f); close() }
    drawPath(head, PAPER); drawPath(head, INK, style = PEN)
    drawCircle(INK, 1.1f, Offset(qx - 47f, qy - 32f))
    drawLine(INK, Offset(qx - 42f, qy - 36f), Offset(qx - 37f, qy - 42f), 0.9f); drawLine(INK, Offset(qx - 39f, qy - 33f), Offset(qx - 33f, qy - 37f), 0.9f)
    val fluke = Path().apply { moveTo(qx + 82f, qy); quadraticTo(qx + 90f, qy - 12f, qx + 100f, qy - 16f); moveTo(qx + 90f, qy - 9f); lineTo(qx + 99f, qy - 5f) }
    drawPath(fluke, INK, style = PEN)
    drawArc(INK.copy(alpha = 0.7f), 200f, 140f, false, Offset(qx - 40f, qy - 1f), Size(28f, 6f), style = FINE)
}

/** Step 5: gold leaf laid in the border's two margins and its corner squares; soft sheen, a few worn flecks. */
internal fun DrawScope.goldBorder(dw: Float, dh: Float, outer: Float, bandIn: Float, bandOut: Float, inner: Float) {
    val leaf = Brush.linearGradient(0f to GOLD, 0.3f to GOLD_LIGHT, 0.5f to GOLD, 0.75f to GOLD_LIGHT, 1f to GOLD_DARK, start = Offset(0f, 0f), end = Offset(dw, dh))
    for ((o, i) in listOf(outer to bandIn, bandOut to inner)) {
        val wd = i - o
        drawRect(leaf, Offset(o, o), Size(dw - 2 * o, wd)); drawRect(leaf, Offset(o, dh - i), Size(dw - 2 * o, wd))
        drawRect(leaf, Offset(o, o), Size(wd, dh - 2 * o)); drawRect(leaf, Offset(dw - i, o), Size(wd, dh - 2 * o))
        drawRect(GOLD_DARK, Offset(o, o), Size(dw - 2 * o, dh - 2 * o), style = Stroke(0.8f)); drawRect(GOLD_DARK, Offset(i, i), Size(dw - 2 * i, dh - 2 * i), style = Stroke(0.8f))
    }
    for (cx in floatArrayOf(outer, dw - inner)) for (cy in floatArrayOf(outer, dh - inner)) {
        drawRect(leaf, Offset(cx, cy), Size(20f, 20f)); drawRect(GOLD_DARK, Offset(cx, cy), Size(20f, 20f), style = Stroke(1f))
        drawLine(GOLD_DARK, Offset(cx, cy), Offset(cx + 20f, cy + 20f), 0.6f); drawLine(GOLD_DARK, Offset(cx + 20f, cy), Offset(cx, cy + 20f), 0.6f)
    }
    for (k in 0 until 90) {
        val along = hash01(k, 541); val side = k % 4
        val p = when (side) { 0 -> Offset(outer + along * (dw - 2 * outer), outer + 2f); 1 -> Offset(outer + along * (dw - 2 * outer), dh - outer - 3f); 2 -> Offset(outer + 2f, outer + along * (dh - 2 * outer)); else -> Offset(dw - outer - 3f, outer + along * (dh - 2 * outer)) }
        drawCircle(if (k % 3 == 0) PAPER.copy(alpha = 0.5f) else GOLD_LIGHT.copy(alpha = 0.7f), 0.6f + hash01(k, 542), p)
    }
}

/** Step 5: a madder wax seal on two ribbons, pressed with a small star. */
internal fun DrawScope.waxSeal(c: Offset) {
    val wax = Color(0xFFA5322B); val dark = Color(0xFF6E1E1A); val light = Color(0xFFD06A5E)
    for (s in floatArrayOf(-1f, 1f)) {
        val rib = Path().apply { moveTo(c.x + s * 6f, c.y + 10f); lineTo(c.x + s * 26f, c.y + 62f); lineTo(c.x + s * 17f, c.y + 56f); lineTo(c.x + s * 9f, c.y + 66f); lineTo(c.x - s * 4f, c.y + 14f); close() }
        drawPath(rib, lerp(wax, Color.Black, if (s < 0f) 0.25f else 0.1f)); drawPath(rib, dark, style = FINE)
    }
    drawCircle(Color.Black.copy(alpha = 0.18f), 30f, Offset(c.x + 3f, c.y + 4f))
    val rim = Path().apply {
        for (i in 0..28) { val a = i * 2f * PI.toFloat() / 28f; val rr = 29f * (1f + (hash01(i % 28, 551) - 0.5f) * 0.14f); if (i == 0) moveTo(c.x + cos(a) * rr, c.y + sin(a) * rr) else lineTo(c.x + cos(a) * rr, c.y + sin(a) * rr) }
        close()
    }
    drawPath(rim, Brush.radialGradient(listOf(light, wax, dark), Offset(c.x - 9f, c.y - 11f), 44f))
    drawCircle(dark.copy(alpha = 0.7f), 20f, c, style = Stroke(1.4f)); drawCircle(light.copy(alpha = 0.5f), 21.4f, c, style = FINE)
    val star = Path().apply { for (i in 0 until 10) { val a = i * PI.toFloat() / 5f - PI.toFloat() / 2f; val rr = if (i % 2 == 0) 13f else 5.6f; if (i == 0) moveTo(c.x + cos(a) * rr, c.y + sin(a) * rr) else lineTo(c.x + cos(a) * rr, c.y + sin(a) * rr) }; close() }
    drawPath(star, dark.copy(alpha = 0.75f)); drawPath(star, light.copy(alpha = 0.5f), style = FINE)
}
