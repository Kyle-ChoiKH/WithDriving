package io.vroomi.character.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import io.vroomi.character.Biome
import io.vroomi.core.vehicle.signal.Thresholds
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * The kart's upgrade ladder (spec 5.6): each step adds a new road to the rotation, and the last one changes the
 * kart itself. 1 the five neighbourhood roads, 2 a cherry-blossom riverside, 3 a neon night street (letterless
 * signs that glow steadily, never blinking), 4 an aurora snowfield, 5 the parade roadster.
 *
 * The new roads are other faces of roads the trip already has (the tree-lined road, the city, the mountain road):
 * they take those roads' turns on every other lap of the rotation, starting with the first. The trip's own scenery
 * model, its spoken lines and the diary stay as they are.
 *
 * A step is seen the moment it is chosen: the road a step added is where its trips begin, so it is also what the
 * parked scene shows ([signature]). The neon street and the aurora bring their own night ([dusk]), and the parade
 * roadster drives under bunting on every road ([drawParade]).
 */
internal object KartStep {
    fun blossom(model: Int): Boolean = model >= 1
    fun neon(model: Int): Boolean = model >= 2
    fun aurora(model: Int): Boolean = model >= 3
    fun roadster(model: Int): Boolean = model >= 4

    /** How many different roads the rotation has at this step. */
    fun roads(model: Int): Int = Biome.entries.size + listOf(blossom(model), neon(model), aurora(model)).count { it }

    /** The road this step added, named by the road whose new face it is (blossom PARK, neon CITY, aurora MOUNTAIN). */
    fun signature(model: Int): Biome? = when (model) { 1 -> Biome.PARK; 2 -> Biome.CITY; 3 -> Biome.MOUNTAIN; else -> null }

    /**
     * The new face shown at this point of the trip, named by the road it belongs to, or null for the plain road.
     * The first stretch of a trip (and so the parked scene) is the chosen step's own road.
     */
    fun face(model: Int, biome: Biome, tripKm: Float): Biome? {
        val stretch = floor(tripKm * 1000f / Thresholds.BIOME_EVERY_M).toInt()
        if (stretch <= 0) return signature(model)
        if ((stretch / Biome.entries.size) % 2 != 0) return null
        return when (biome) {
            Biome.PARK -> biome.takeIf { blossom(model) }
            Biome.CITY -> biome.takeIf { neon(model) }
            Biome.MOUNTAIN -> biome.takeIf { aurora(model) }
            else -> null
        }
    }

    /** True when the road shows a new face at this point of the trip. */
    fun variant(model: Int, biome: Biome, tripKm: Float): Boolean = face(model, biome, tripKm) != null

    /** How dark a face makes the scene whatever the hour: the neon street and the aurora are night roads. */
    fun dusk(face: Biome?): Float = when (face) { Biome.CITY -> 0.86f; Biome.MOUNTAIN -> 0.78f; else -> 0f }

    /** The ground between the horizon and the road for a road's new face. */
    fun ground(biome: Biome): Color = when (biome) {
        Biome.PARK -> Color(0xFFDCEBCB)
        Biome.CITY -> Color(0xFF474B63)
        else -> Color(0xFFEEF4F7)
    }
}

private val PINKS = arrayOf(Color(0xFFF7B7C9), Color(0xFFF9CBD8), Color(0xFFF29BB5))
private val NEONS = arrayOf(Color(0xFFFF5FA2), Color(0xFF4FE3F0), Color(0xFFFFD84D), Color(0xFF9B7BFF))

/** A letterless neon sign: a soft steady glow under a bright tube. [kind] 0 bar, 1 ring, 2 chevron, 3 stacked bars. */
private fun DrawScope.neonSign(c: Offset, s: Float, kind: Int, col: Color, n: Float) {
    val glow = col.copy(alpha = 0.22f + 0.16f * n)
    val tube = lerp(col, Color.White, 0.35f)
    val wG = s * 0.55f; val wT = s * 0.16f
    when (kind) {
        0 -> { drawLine(glow, Offset(c.x - s, c.y), Offset(c.x + s, c.y), wG, cap = StrokeCap.Round); drawLine(tube, Offset(c.x - s, c.y), Offset(c.x + s, c.y), wT, cap = StrokeCap.Round) }
        1 -> { drawCircle(glow, s * 0.8f, c, style = Stroke(wG)); drawCircle(tube, s * 0.8f, c, style = Stroke(wT)) }
        2 -> for (pass in 0..1) {
            val w = if (pass == 0) wG else wT; val cc = if (pass == 0) glow else tube
            drawLine(cc, Offset(c.x - s * 0.8f, c.y + s * 0.5f), Offset(c.x, c.y - s * 0.5f), w, cap = StrokeCap.Round)
            drawLine(cc, Offset(c.x, c.y - s * 0.5f), Offset(c.x + s * 0.8f, c.y + s * 0.5f), w, cap = StrokeCap.Round)
        }
        else -> for (j in -1..1) {
            val y = c.y + j * s * 0.6f; val half = s * (1f - 0.25f * kotlin.math.abs(j))
            drawLine(glow, Offset(c.x - half, y), Offset(c.x + half, y), wG * 0.7f, cap = StrokeCap.Round); drawLine(tube, Offset(c.x - half, y), Offset(c.x + half, y), wT, cap = StrokeCap.Round)
        }
    }
}

/** The far layer of a road's new face (one slot). */
internal fun DrawScope.drawFarVariant(rt: SceneRuntime, b: Biome, k: Int, x0: Float, sw: Float, horizon: Float, h: Float, n: Float, o: SceneOptions) {
    when (b) {
        Biome.PARK -> {
            // The far bank under blossom, and the river in front of it.
            for (i in 0 until 3) {
                val c = PINKS[Math.floorMod(k + i, 3)].night(n, 0.75f)
                drawOval(c, Offset(x0 + sw * (i * 0.34f - 0.1f), horizon - h * (0.10f + hash01(k * 3 + i, 1) * 0.05f)), Size(sw * 0.56f, h * 0.16f))
            }
            for (i in 0 until 4) drawCircle(Color.White.alpha(0.30f * (1f - n)), h * 0.012f, Offset(x0 + sw * hash01(k * 5 + i, 2), horizon - h * (0.06f + hash01(k * 5 + i, 3) * 0.05f)))
            val water = lerp(Color(0xFF9FD3E6), Color(0xFF223E58), n)
            drawRect(water, Offset(x0, horizon - h * 0.045f), Size(sw + 2f, h * 0.05f))
            drawRect(PINKS[1].alpha(0.35f * (1f - n)), Offset(x0, horizon - h * 0.045f), Size(sw + 2f, h * 0.012f))
            for (i in 0 until 3) {
                val wx = x0 + sw * hash01(k * 3 + i, 9); val wy = horizon - h * (0.008f + 0.026f * hash01(k * 3 + i, 10))
                drawLine(Color.White.alpha(0.5f * (1f - n * 0.6f)), Offset(wx, wy), Offset(wx + sw * 0.09f, wy), strokeWidth = 2f, cap = StrokeCap.Round)
            }
        }
        Biome.CITY -> {
            // A neon district: dark blocks, each with one letterless sign that glows steadily.
            var x = x0
            var i = 0
            while (x < x0 + sw) {
                val bw = sw * (0.16f + hash01(k * 7 + i, 1) * 0.16f)
                val bh = h * (0.09f + hash01(k * 7 + i, 2) * 0.17f)
                val c = lerp(Color(0xFF3A3F6B), Color(0xFF2B2F52), hash01(k * 7 + i, 3)).night(n, 0.5f)
                drawRect(c, Offset(x, horizon - bh), Size(bw * 0.94f, bh + 2f))
                drawRect(Color.White.alpha(0.06f), Offset(x, horizon - bh), Size(bw * 0.94f, h * 0.004f))
                val rows = max(2, (bh / (h * 0.034f)).toInt())
                for (ry in 0 until rows) if (hash01(k * 131 + i * 17 + ry, 4) < 0.5f) {
                    drawRect(Color(0xFFFFE08A).alpha(0.55f), Offset(x + bw * (0.12f + 0.5f * hash01(k * 31 + i * 7 + ry, 5)), horizon - bh + h * 0.014f + ry * (bh * 0.85f / rows)), Size(bw * 0.14f, h * 0.008f))
                }
                val kind = (hash01(k * 7 + i, 6) * 4f).toInt().coerceIn(0, 3)
                neonSign(Offset(x + bw * 0.47f, horizon - bh - h * 0.022f), h * 0.016f, kind, NEONS[(k + i) and 3], n)
                x += bw
                i++
            }
        }
        else -> {
            // The aurora over snow mountains: slow curtains, continuous from slot to slot.
            val slow = if (o.calm) 0f else rt.time * 0.10f
            val aur = 0.26f + 0.42f * n
            val steps = 6
            for (band in 0 until 3) {
                val col = when (band) { 0 -> Color(0xFF7CF2B0); 1 -> Color(0xFF6FD6E8); else -> Color(0xFFB79BF2) }
                val p = rt.tmpPath2
                p.reset()
                for (j in 0..steps) {
                    val xa = k + j / steps.toFloat()
                    val y = horizon - h * (0.46f - band * 0.07f + 0.045f * sin(xa * 1.7f + band * 1.3f + slow) + 0.02f * sin(xa * 4.1f - slow * 1.6f))
                    if (j == 0) p.moveTo(x0, y) else p.lineTo(x0 + sw * j / steps, y)
                }
                for (j in steps downTo 0) {
                    val xa = k + j / steps.toFloat()
                    val y = horizon - h * (0.26f - band * 0.05f + 0.03f * sin(xa * 1.3f + band + slow * 0.7f))
                    p.lineTo(x0 + sw * j / steps + 1f, y)
                }
                p.close()
                drawPath(p, Brush.verticalGradient(listOf(col.copy(alpha = 0f), col.copy(alpha = aur), col.copy(alpha = 0f)), startY = horizon - h * 0.52f, endY = horizon - h * 0.18f))
            }
            val p = rt.tmpPath2
            for (i in 0 until 2) {
                val px = x0 + sw * (0.15f + 0.55f * i + hash01(k * 2 + i, 1) * 0.15f)
                val ph = h * (0.17f + hash01(k * 2 + i, 2) * 0.13f)
                val half = sw * (0.36f + hash01(k * 2 + i, 3) * 0.15f)
                p.reset(); p.moveTo(px - half, horizon + 1f); p.lineTo(px, horizon - ph); p.lineTo(px + half, horizon + 1f); p.close()
                drawPath(p, Color(0xFFEAF1F6).night(n, 0.6f))
                p.reset(); p.moveTo(px, horizon - ph); p.lineTo(px + half, horizon + 1f); p.lineTo(px + half * 0.25f, horizon + 1f); p.close()
                drawPath(p, Color(0xFFBCCBDD).night(n, 0.6f))
            }
        }
    }
}

/** A roadside prop of a road's new face. */
internal fun DrawScope.drawMidVariant(rt: SceneRuntime, b: Biome, k: Int, x: Float, baseY: Float, h: Float, n: Float, o: SceneOptions) {
    val r = hash01(k, 33)
    val u = h * 0.01f
    when (b) {
        Biome.PARK -> when {
            r < 0.72f -> {
                // A cherry tree in bloom, and a few petals coming down slowly.
                val tr = h * (0.055f + hash01(k, 36) * 0.02f)
                drawRect(Color(0xFF6B4A3A).night(n, 0.6f), Offset(x - tr * 0.1f, baseY - tr * 1.3f), Size(tr * 0.2f, tr * 1.3f))
                drawCircle(PINKS[0].night(n), tr, Offset(x, baseY - tr * 1.8f))
                drawCircle(PINKS[2].night(n), tr * 0.72f, Offset(x + tr * 0.62f, baseY - tr * 1.4f))
                drawCircle(PINKS[1].night(n), tr * 0.62f, Offset(x - tr * 0.6f, baseY - tr * 1.5f))
                drawCircle(Color.White.alpha(0.22f * (1f - n)), tr * 0.36f, Offset(x - tr * 0.3f, baseY - tr * 2.15f))
                if (!o.calm) for (i in 0 until 3) {
                    val f = ((rt.time * 0.11f + hash01(k * 3 + i, 44)) % 1f)
                    drawCircle(PINKS[i].alpha(0.9f * (1f - f)).night(n, 0.4f), u * 0.45f, Offset(x - tr + tr * 2f * hash01(k * 3 + i, 45) + sin(f * 6f + i) * u * 1.5f, baseY - tr * 1.4f + f * tr * 1.4f))
                }
            }
            r < 0.86f -> { // a stone lantern by the river walk
                val c = Color(0xFF9AA3A8).night(n, 0.6f)
                drawRect(c, Offset(x - u * 0.5f, baseY - u * 3.4f), Size(u, u * 3.4f))
                drawRoundRect(c, Offset(x - u * 1.5f, baseY - u * 5.2f), Size(u * 3f, u * 1.8f), CornerRadius(u * 0.3f))
                drawRect(if (n > 0.4f) Color(0xFFFFE08A) else Color(0xFFF4EAD0), Offset(x - u * 0.8f, baseY - u * 4.8f), Size(u * 1.6f, u))
                drawRect(c.dim(0.85f), Offset(x - u * 1.9f, baseY - u * 5.7f), Size(u * 3.8f, u * 0.6f))
            }
            else -> { // fallen petals on the grass
                drawOval(Color(0xFF8CC59A).night(n), Offset(x - u * 3f, baseY - u * 1.6f), Size(u * 6f, u * 2f))
                for (i in 0 until 6) drawCircle(PINKS[Math.floorMod(k + i, 3)].night(n, 0.5f), u * 0.5f, Offset(x - u * 2.4f + i * u * 0.95f, baseY - u * (0.5f + hash01(k * 5 + i, 42))))
            }
        }
        Biome.CITY -> when {
            r < 0.40f -> { // a sign post with one neon shape
                val top = baseY - h * 0.15f
                drawLine(Color(0xFF2E3348).night(n, 0.4f), Offset(x, baseY), Offset(x, top), strokeWidth = u * 0.8f)
                neonSign(Offset(x, top - u * 2f), u * 2.6f, (hash01(k, 34) * 4f).toInt().coerceIn(0, 3), NEONS[k and 3], n)
            }
            r < 0.75f -> { // a small shop outlined in a neon tube
                val bw = h * 0.13f; val bh = h * 0.10f
                drawRect(Color(0xFF34385A).night(n, 0.4f), Offset(x - bw / 2, baseY - bh), Size(bw, bh))
                drawRect(Color(0xFFFFE08A).alpha(0.75f), Offset(x - bw * 0.35f, baseY - bh * 0.62f), Size(bw * 0.4f, bh * 0.35f))
                drawRect(Color(0xFF20233A).night(n, 0.4f), Offset(x + bw * 0.15f, baseY - bh * 0.55f), Size(bw * 0.2f, bh * 0.55f))
                val col = NEONS[(k + 1) and 3]
                drawRect(col.copy(alpha = 0.22f + 0.14f * n), Offset(x - bw / 2, baseY - bh), Size(bw, bh), style = Stroke(u * 1.6f))
                drawRect(lerp(col, Color.White, 0.35f), Offset(x - bw / 2, baseY - bh), Size(bw, bh), style = Stroke(u * 0.45f))
            }
            else -> { // a street lamp with a warm steady glow
                val top = baseY - h * 0.17f
                drawLine(Color(0xFF2E3348).night(n, 0.4f), Offset(x, baseY), Offset(x, top), strokeWidth = u * 0.8f)
                drawLine(Color(0xFF2E3348).night(n, 0.4f), Offset(x, top), Offset(x + u * 3f, top), strokeWidth = u * 0.8f, cap = StrokeCap.Round)
                drawCircle(Color(0xFFFFC0DA).alpha(0.24f), u * 6f, Offset(x + u * 3f, top + u))
                drawCircle(Color(0xFFFFE6F0), u * 1.4f, Offset(x + u * 3f, top + u))
            }
        }
        else -> when {
            r < 0.68f -> {
                // A pine under snow.
                val hgt = h * (0.08f + hash01(k, 39) * 0.05f)
                val p = rt.tmpPath2
                drawRect(Pal.trunk.night(n, 0.6f), Offset(x - hgt * 0.04f, baseY - hgt * 0.15f), Size(hgt * 0.08f, hgt * 0.15f))
                for (i in 0 until 3) {
                    val top = baseY - hgt * (0.45f + i * 0.22f); val half = hgt * (0.28f - i * 0.06f); val foot = baseY - hgt * (0.12f + i * 0.22f)
                    p.reset(); p.moveTo(x - half, foot); p.lineTo(x, top - hgt * 0.1f); p.lineTo(x + half, foot); p.close()
                    drawPath(p, Color(0xFF2F6F5A).dim(1f - i * 0.04f).night(n))
                    p.reset(); p.moveTo(x - half * 0.62f, foot - (foot - top + hgt * 0.1f) * 0.38f); p.lineTo(x, top - hgt * 0.1f); p.lineTo(x + half * 0.62f, foot - (foot - top + hgt * 0.1f) * 0.38f)
                    p.lineTo(x + half * 0.2f, foot - (foot - top + hgt * 0.1f) * 0.22f); p.lineTo(x - half * 0.25f, foot - (foot - top + hgt * 0.1f) * 0.30f); p.close()
                    drawPath(p, Color.White.night(n, 0.5f))
                }
            }
            r < 0.88f -> { // a snow drift
                drawOval(Color.White.night(n, 0.5f), Offset(x - u * 4f, baseY - u * 1.8f), Size(u * 8f, u * 3f))
                drawOval(Color(0xFFD5E2EC).night(n, 0.5f), Offset(x - u * 1f, baseY - u * 1.1f), Size(u * 4.4f, u * 1.8f))
            }
            else -> { // a marker pole with a snow cap
                drawLine(Color(0xFFE4574B).night(n, 0.6f), Offset(x, baseY), Offset(x, baseY - u * 6f), strokeWidth = u * 0.6f)
                drawOval(Color.White.night(n, 0.5f), Offset(x - u * 0.9f, baseY - u * 6.8f), Size(u * 1.8f, u * 1.2f))
            }
        }
    }
}

private val PENNANTS = arrayOf(Color(0xFF3CC8B4), Color(0xFFFFD84D), Color(0xFFFF7A6B), Color(0xFFFDFBF4), Color(0xFF7FB7F2))

/**
 * The parade (step 5): a line of pennants from this roadside pole to the next one ([xNext]), and now and then a bunch
 * of balloons tied to the pole. Nothing blinks; the balloons sway slowly.
 */
internal fun DrawScope.drawParade(rt: SceneRuntime, k: Int, x: Float, xNext: Float, baseY: Float, h: Float, n: Float, o: SceneOptions) {
    val u = h * 0.01f
    val top = baseY - h * 0.25f
    val pole = Color(0xFFF4F1E8).night(n, 0.55f)
    drawLine(Color(0xFF8E96A0).night(n, 0.5f), Offset(x + u * 0.5f, baseY), Offset(x + u * 0.5f, top), strokeWidth = u * 1.1f)
    drawLine(pole, Offset(x, baseY), Offset(x, top), strokeWidth = u * 0.9f)
    drawCircle(Color(0xFFFFD84D).night(n, 0.4f), u * 1.1f, Offset(x, top - u * 0.6f))
    // The string sags between the poles; a pennant hangs every few steps.
    val span = xNext - x
    val count = 9
    var px = x; var py = top
    for (i in 1..count) {
        val f = i / count.toFloat()
        val nx = x + span * f
        val ny = top + h * 0.055f * (1f - (2f * f - 1f) * (2f * f - 1f))
        drawLine(Color(0xFF5A606B).night(n, 0.4f), Offset(px, py), Offset(nx, ny), strokeWidth = u * 0.3f)
        if (i < count) {
            val c = PENNANTS[Math.floorMod(k * 3 + i, PENNANTS.size)].night(n, 0.45f)
            val hw = span / count * 0.36f
            val path = rt.tmpPath2
            path.reset(); path.moveTo(nx - hw, ny); path.lineTo(nx + hw, ny); path.lineTo(nx, ny + u * 3.4f); path.close()
            drawPath(path, c)
            drawLine(Color.White.alpha(0.35f * (1f - n)), Offset(nx - hw, ny + u * 0.3f), Offset(nx + hw * 0.4f, ny + u * 0.3f), strokeWidth = u * 0.3f)
        }
        px = nx; py = ny
    }
    if (Math.floorMod(k, 3) == 0) {
        val sway = if (o.calm) 0f else sin(rt.time * 0.7f + k) * u * 0.8f
        for (i in 0 until 3) {
            val c = PENNANTS[Math.floorMod(k + i * 2, PENNANTS.size)].night(n, 0.45f)
            val bx = x + u * (2.2f + i * 2.3f) + sway * (1f + i * 0.3f); val by = top - u * (5.5f + (i % 2) * 2.4f)
            drawLine(Color(0xFF5A606B).night(n, 0.4f), Offset(x, top), Offset(bx, by + u * 2f), strokeWidth = u * 0.22f)
            drawOval(c, Offset(bx - u * 1.5f, by - u * 1.9f), Size(u * 3f, u * 3.8f))
            drawOval(Color.White.alpha(0.4f * (1f - n * 0.6f)), Offset(bx - u * 0.9f, by - u * 1.4f), Size(u * 0.8f, u * 1.2f))
        }
    }
}
