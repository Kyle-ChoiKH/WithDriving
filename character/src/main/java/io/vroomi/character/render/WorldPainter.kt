package io.vroomi.character.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import io.vroomi.character.Biome
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Screen bands (fractions of height). */
internal object Band {
    const val HORIZON = 0.56f
    const val ROAD_TOP = 0.70f
    const val ROAD_BOT = 0.955f
    const val CENTER_LINE = 0.785f
}

// ======================================================================== sky

internal fun DrawScope.drawSky(rt: SceneRuntime, o: SceneOptions) {
    val w = size.width; val h = size.height
    val n = rt.dark.value.coerceIn(0f, 1f)
    val horizon = h * Band.HORIZON
    val (top, bot) = skyColors(o.hourOfDay, n, rt.rain.value)
    drawRect(Brush.verticalGradient(listOf(top, bot), 0f, horizon), Offset.Zero, Size(w, horizon + 2f))

    // Stars and moon at night (slow, gentle twinkle: never flashing)
    if (n > 0.3f) {
        val a = ((n - 0.3f) / 0.7f).coerceIn(0f, 1f) * (1f - rt.rain.value * 0.8f)
        for (i in 0 until 26) {
            val sx = hash01(i, 3) * w
            val sy = hash01(i, 5) * horizon * 0.75f
            val tw = 0.55f + 0.25f * sin(rt.time * 0.6f + i * 1.7f)
            drawCircle(Color.White.alpha(a * tw), 1.4f + hash01(i, 9) * 1.4f, Offset(sx, sy))
        }
        val mc = Offset(w * 0.84f, h * 0.14f)
        drawCircle(Color(0xFFFFF4D2).alpha(a * 0.25f), h * 0.085f, mc)
        drawCircle(Color(0xFFFFF4D2).alpha(a), h * 0.05f, mc)
        drawCircle(lerp(top, bot, 0.2f).alpha(a), h * 0.044f, Offset(mc.x + h * 0.022f, mc.y - h * 0.012f))
    }
    // Sun follows the clock (east -> west), hidden by rain and night.
    val hr = ((o.hourOfDay % 24f) + 24f) % 24f
    if (hr in 6f..19f && n < 0.8f) {
        val u = (hr - 6f) / 13f
        val sx = w * (0.10f + 0.80f * u)
        val sy = horizon - sin(u * PI.toFloat()) * h * 0.42f
        val sunA = (1f - n / 0.8f) * (1f - rt.rain.value)
        val sunCol = lerp(Color(0xFFFFE7A3), Color(0xFFFFB46B), (abs01(u - 0.5f) * 2f - 0.6f).coerceIn(0f, 1f) * 2.5f)
        drawCircle(sunCol.alpha(0.28f * sunA), h * 0.09f, Offset(sx, sy))
        drawCircle(sunCol.alpha(sunA), h * 0.055f, Offset(sx, sy))
    }
    drawClouds(rt, w, h, n)
    drawSkyGears(rt)
}

private fun abs01(x: Float) = if (x < 0f) -x else x

internal fun skyColors(hour: Float, n: Float, rain: Float): Pair<Color, Color> {
    val h = ((hour % 24f) + 24f) % 24f
    val dayT = Color(0xFF9ED8F0); val dayB = Color(0xFFEAF7F1)
    val dawnT = Color(0xFF9DB6E2); val dawnB = Color(0xFFFFD2B0)
    val dusk1 = Color(0xFF7484C4); val dusk2 = Color(0xFFFFB27A)
    val nightT = Color(0xFF0E1B2B); val nightB = Color(0xFF22364A)
    var top: Color; var bot: Color
    when {
        h < 5f || h >= 19.5f -> { top = nightT; bot = nightB }
        h < 6.5f -> { val u = (h - 5f) / 1.5f; top = lerp(nightT, dawnT, u); bot = lerp(nightB, dawnB, u) }
        h < 8f -> { val u = (h - 6.5f) / 1.5f; top = lerp(dawnT, dayT, u); bot = lerp(dawnB, dayB, u) }
        h < 16.5f -> { top = dayT; bot = dayB }
        h < 18f -> { val u = (h - 16.5f) / 1.5f; top = lerp(dayT, dusk1, u); bot = lerp(dayB, dusk2, u) }
        else -> { val u = (h - 18f) / 1.5f; top = lerp(dusk1, nightT, u); bot = lerp(dusk2, nightB, u) }
    }
    // Headlights at daytime (tunnel/rain) still read as "evening": blend toward night by darkness.
    top = lerp(top, nightT, (n - 0f).coerceIn(0f, 1f) * 0.85f)
    bot = lerp(bot, nightB, n * 0.85f)
    val grayT = lerp(Color(0xFF9AA8B4), Color(0xFF1C2833), n)
    val grayB = lerp(Color(0xFFC9D2D6), Color(0xFF2A3A46), n)
    return lerp(top, grayT, rain * 0.75f) to lerp(bot, grayB, rain * 0.75f)
}

private fun DrawScope.drawClouds(rt: SceneRuntime, w: Float, h: Float, n: Float) {
    val count = if (rt.rain.value > 0.3f) 7 else 5
    val col = lerp(lerp(Color.White, Color(0xFFB7C2CA), rt.rain.value), Color(0xFF3A4A5A), n * 0.8f)
    val a = if (n > 0.6f) 0.35f + 0.4f * rt.rain.value else 0.92f
    for (i in 0 until count) {
        val speed = 0.004f + hash01(i, 21) * 0.006f
        val base = hash01(i, 11) * 1.4f
        val x = (((base - rt.time * speed - rt.scrollFar.toFloat() * 0.6f) % 1.4f) + 1.4f) % 1.4f - 0.2f
        val y = h * (0.07f + hash01(i, 13) * 0.25f)
        val r = h * (0.035f + hash01(i, 17) * 0.03f) * (1f + rt.rain.value * 0.4f)
        val cx = x * w
        drawCircle(col.alpha(a), r, Offset(cx, y))
        drawCircle(col.alpha(a), r * 0.75f, Offset(cx - r * 0.9f, y + r * 0.25f))
        drawCircle(col.alpha(a), r * 0.85f, Offset(cx + r * 0.95f, y + r * 0.2f))
        drawRoundRect(col.alpha(a), Offset(cx - r * 1.6f, y + r * 0.1f), Size(r * 3.3f, r * 0.75f), CornerRadius(r * 0.4f))
    }
}

// ======================================================================== far layer (slots)

internal fun DrawScope.drawFar(rt: SceneRuntime, o: SceneOptions) {
    val w = size.width; val h = size.height
    val n = rt.dark.value.coerceIn(0f, 1f)
    val horizon = h * Band.HORIZON
    val slot = SceneRuntime.FAR_SLOT
    val first = floorDiv(rt.scrollFar, slot) - 1
    for (k in first..first + (1.0 / slot).toInt() + 3) {
        val x0 = ((k * slot - rt.scrollFar) * w).toFloat()
        val sw = (slot * w).toFloat()
        val biome = if (k >= rt.farBoundary) rt.biomeCur else rt.biomePrev
        val face = if (k >= rt.farBoundary) rt.faceCur else rt.facePrev
        if (face != null) drawFarVariant(rt, face, k, x0, sw, horizon, h, n, o) else drawFarSlot(rt, biome, k, x0, sw, horizon, h, n, o)
    }
    // Destination flag grows on the horizon in the last kilometre of guidance.
    val d = rt.lastJourney.destinationM
    if (d != null && d < 1_000) {
        val p = 1f - d / 1_000f
        val fx = w * 0.86f; val base = horizon + h * 0.01f
        val hgt = h * (0.05f + 0.13f * p)
        drawLine(Color(0xFF6B4E3A).night(n), Offset(fx, base), Offset(fx, base - hgt), strokeWidth = max(2f, h * 0.006f))
        tmpFlag(rt, fx, base - hgt, hgt * 0.45f, n)
    }
}

private fun DrawScope.tmpFlag(rt: SceneRuntime, x: Float, y: Float, len: Float, n: Float) {
    val p = rt.tmpPath2
    val flutter = sin(rt.time * 6f) * len * 0.08f
    p.reset()
    p.moveTo(x, y)
    p.cubicTo(x + len * 0.4f, y + flutter, x + len * 0.7f, y - flutter, x + len, y + len * 0.25f)
    p.lineTo(x, y + len * 0.55f)
    p.close()
    drawPath(p, Color(0xFFE4574B).night(n, 0.4f))
    // little checker to read as "goal"
    drawCircle(Color.White.night(n, 0.4f), len * 0.09f, Offset(x + len * 0.35f, y + len * 0.22f))
}

private fun DrawScope.drawFarSlot(rt: SceneRuntime, b: Biome, k: Int, x0: Float, sw: Float, horizon: Float, h: Float, n: Float, o: SceneOptions) {
    val haze = Color(0xFFB9D3DC)
    when (b) {
        Biome.CITY -> {
            var x = x0
            var i = 0
            while (x < x0 + sw) {
                val bw = sw * (0.16f + hash01(k * 7 + i, 1) * 0.16f)
                val bh = h * (0.08f + hash01(k * 7 + i, 2) * 0.17f)
                val c = lerp(Color(0xFF9DB4C4), Color(0xFF7F97AE), hash01(k * 7 + i, 3)).night(n, 0.75f)
                drawRect(c, Offset(x, horizon - bh), Size(bw * 0.94f, bh + 2f))
                // windows: one dashed line per row (a single draw call), some rows lit at night
                val cols = max(2, (bw / (h * 0.022f)).toInt())
                val rows = max(2, (bh / (h * 0.03f)).toInt())
                val pitchX = bw * 0.76f / cols
                val dash = PathEffect.dashPathEffect(floatArrayOf(pitchX * 0.42f, pitchX * 0.58f), 0f)
                for (ry in 0 until rows) {
                    val lit = hash01(k * 131 + i * 17 + ry, 4) < 0.55f
                    val wc = if (n > 0.4f && lit) Color(0xFFFFE08A).alpha(0.9f) else Color.White.alpha(0.18f * (1f - n) + 0.05f * n)
                    val wy = horizon - bh + h * 0.016f + ry * (bh * 0.85f / rows)
                    drawLine(wc, Offset(x + bw * 0.12f, wy), Offset(x + bw * 0.12f + pitchX * cols, wy), strokeWidth = h * 0.009f, pathEffect = dash)
                }
                x += bw
                i++
            }
        }
        Biome.PARK -> {
            val c = Color(0xFFA6DCCB).night(n, 0.75f)
            drawOval(c, Offset(x0 - sw * 0.2f, horizon - h * (0.07f + hash01(k, 1) * 0.04f)), Size(sw * 1.4f, h * 0.2f))
        }
        Biome.FIELD -> {
            val c = Color(0xFFB8DDA0).night(n, 0.75f)
            drawOval(c, Offset(x0 - sw * 0.3f, horizon - h * 0.05f), Size(sw * 1.6f, h * 0.14f))
            if (hash01(k, 6) < 0.7f) {
                // Wind turbine (blades turn slowly)
                val tx = x0 + sw * (0.3f + hash01(k, 7) * 0.4f)
                val th = h * (0.13f + hash01(k, 8) * 0.06f)
                val col = Color(0xFFF4F7F8).night(n, 0.75f)
                drawLine(col, Offset(tx, horizon), Offset(tx, horizon - th), strokeWidth = max(2f, h * 0.006f))
                val hub = Offset(tx, horizon - th)
                for (bl in 0 until 3) {
                    rotate(rt.time * 50f + bl * 120f + k * 37f, hub) {
                        drawLine(col, hub, Offset(hub.x, hub.y - th * 0.45f), strokeWidth = max(2f, h * 0.007f), cap = StrokeCap.Round)
                    }
                }
                drawCircle(col.dim(0.85f), h * 0.007f, hub)
            }
        }
        Biome.SEASIDE -> {
            val sea = lerp(Color(0xFF5DB7D6), Color(0xFF1C3C55), n)
            drawRect(sea, Offset(x0, horizon - h * 0.045f), Size(sw + 2f, h * 0.05f))
            for (i in 0 until 3) {
                val wx = x0 + sw * hash01(k * 3 + i, 9)
                val wy = horizon - h * (0.01f + 0.03f * hash01(k * 3 + i, 10))
                drawLine(Color.White.alpha(0.5f * (1f - n * 0.6f)), Offset(wx, wy), Offset(wx + sw * 0.1f, wy), strokeWidth = 2f, cap = StrokeCap.Round)
            }
            val r = hash01(k, 12)
            if (r < 0.35f) {
                // Lighthouse (its beam turns at night)
                val lx = x0 + sw * 0.6f; val lh = h * 0.12f
                drawRect(Color.White.night(n, 0.6f), Offset(lx - h * 0.012f, horizon - lh - h * 0.04f), Size(h * 0.024f, lh))
                drawRect(Color(0xFFE4574B).night(n, 0.6f), Offset(lx - h * 0.012f, horizon - lh * 0.6f - h * 0.04f), Size(h * 0.024f, lh * 0.15f))
                val lamp = Offset(lx, horizon - lh - h * 0.05f)
                drawCircle(Color(0xFFFFE08A), h * 0.012f, lamp)
                if (n > 0.3f) rotate(sin(rt.time * 0.8f) * 25f, lamp) {
                    drawLine(Color(0xFFFFF0B0).alpha(0.25f * n), lamp, Offset(lamp.x + h * 0.3f, lamp.y), strokeWidth = h * 0.02f)
                }
            } else if (r < 0.75f) {
                // Sailboat drifting a little
                val bx = x0 + sw * (0.2f + 0.6f * hash01(k, 13)) + sin(rt.time * 0.3f + k) * sw * 0.03f
                val by = horizon - h * 0.025f
                drawRect(Color(0xFF7A5A43).night(n), Offset(bx - h * 0.02f, by), Size(h * 0.04f, h * 0.008f))
                val p = rt.tmpPath2
                p.reset(); p.moveTo(bx, by - h * 0.002f); p.lineTo(bx, by - h * 0.05f); p.lineTo(bx + h * 0.028f, by - h * 0.004f); p.close()
                drawPath(p, Color.White.night(n, 0.6f))
            }
        }
        Biome.MOUNTAIN -> {
            val p = rt.tmpPath2
            val peaks = 2
            for (i in 0 until peaks) {
                val px = x0 + sw * (0.15f + 0.55f * i + hash01(k * 2 + i, 1) * 0.15f)
                val ph = h * (0.18f + hash01(k * 2 + i, 2) * 0.14f)
                val half = sw * (0.35f + hash01(k * 2 + i, 3) * 0.15f)
                val c = lerp(Color(0xFF8FA6C8), Color(0xFF7189B0), hash01(k * 2 + i, 4)).night(n, 0.75f)
                p.reset(); p.moveTo(px - half, horizon + 1f); p.lineTo(px, horizon - ph); p.lineTo(px + half, horizon + 1f); p.close()
                drawPath(p, c)
                // snowcap (bigger in winter)
                val cap = if (o.season == Season.WINTER) 0.38f else 0.22f
                p.reset()
                p.moveTo(px, horizon - ph)
                p.lineTo(px - half * cap, horizon - ph * (1f - cap))
                p.lineTo(px - half * cap * 0.3f, horizon - ph * (1f - cap * 0.75f))
                p.lineTo(px + half * cap * 0.2f, horizon - ph * (1f - cap * 0.95f))
                p.lineTo(px + half * cap, horizon - ph * (1f - cap))
                p.close()
                drawPath(p, Color.White.night(n, 0.6f))
            }
        }
    }
    // Atmospheric haze at the horizon line
    if (n < 0.5f) drawRect(haze.alpha(0.12f), Offset(x0, horizon - h * 0.02f), Size(sw + 1f, h * 0.02f))
}

// ======================================================================== ground + mid props

internal fun DrawScope.drawGroundAndMid(rt: SceneRuntime, o: SceneOptions) {
    val w = size.width; val h = size.height
    val n = rt.dark.value.coerceIn(0f, 1f)
    val horizon = h * Band.HORIZON
    val roadTop = h * Band.ROAD_TOP
    drawRect(rt.groundCol.night(n), Offset(0f, horizon), Size(w, roadTop - horizon + 2f))

    val slot = SceneRuntime.MID_SLOT
    val first = floorDiv(rt.scrollMid, slot) - 1
    val baseY = roadTop - h * 0.01f
    for (k in first..first + (1.0 / slot).toInt() + 3) {
        val x = ((k * slot - rt.scrollMid) * w).toFloat() + (hash01(k, 31) * 0.3f * slot * w).toFloat()
        val biome = if (k >= rt.midBoundary) rt.biomeCur else rt.biomePrev
        val face = if (k >= rt.midBoundary) rt.faceCur else rt.facePrev
        if (face != null) drawMidVariant(rt, face, k, x, baseY, h, n, o) else drawMidProp(rt, biome, k, x, baseY, h, n, o)
        if (KartStep.roadster(rt.model)) {
            val xNext = (((k + 1) * slot - rt.scrollMid) * w).toFloat() + (hash01(k + 1, 31) * 0.3f * slot * w).toFloat()
            drawParade(rt, k, x, xNext, baseY, h, n, o)
        }
    }
}

private fun treeColor(season: Season, k: Int, base: Color): Color = when (season) {
    Season.SPRING -> if (hash01(k, 41) < 0.5f) Color(0xFFF7B7C9) else Color(0xFFF2C6D6)
    Season.SUMMER -> base
    Season.AUTUMN -> if (hash01(k, 41) < 0.5f) Color(0xFFF2B33D) else Color(0xFFE9824A)
    Season.WINTER -> Color(0xFFE3EEF2)
}

private fun DrawScope.drawMidProp(rt: SceneRuntime, b: Biome, k: Int, x: Float, baseY: Float, h: Float, n: Float, o: SceneOptions) {
    val r = hash01(k, 33)
    val u = h * 0.01f
    when (b) {
        Biome.CITY -> when {
            r < 0.30f -> { // street lamp
                val top = baseY - h * 0.17f
                drawLine(Color(0xFF55606A).night(n, 0.5f), Offset(x, baseY), Offset(x, top), strokeWidth = u * 0.8f)
                drawLine(Color(0xFF55606A).night(n, 0.5f), Offset(x, top), Offset(x + u * 3f, top), strokeWidth = u * 0.8f, cap = StrokeCap.Round)
                val lamp = Offset(x + u * 3f, top + u)
                if (n > 0.3f) {
                    drawCircle(Color(0xFFFFE6A0).alpha(0.22f * n), u * 7f, lamp)
                    drawCircle(Color(0xFFFFE6A0).alpha(0.9f), u * 1.4f, lamp)
                } else drawCircle(Color(0xFFE9EEF0), u * 1.3f, lamp)
            }
            r < 0.62f -> { // little shop with striped awning
                val bw = h * 0.13f; val bh = h * 0.10f
                val wall = lerp(Color(0xFFF3E3CF), Color(0xFFD8E6EE), hash01(k, 34)).night(n, 0.7f)
                drawRect(wall, Offset(x - bw / 2, baseY - bh), Size(bw, bh))
                val win = if (n > 0.4f) Color(0xFFFFE08A) else Color(0xFFBFE6FF)
                drawRect(win.night(n, 0.2f), Offset(x - bw * 0.35f, baseY - bh * 0.62f), Size(bw * 0.4f, bh * 0.35f))
                drawRect(Color(0xFF6B4E3A).night(n, 0.6f), Offset(x + bw * 0.15f, baseY - bh * 0.55f), Size(bw * 0.2f, bh * 0.55f))
                val aw = if (hash01(k, 35) < 0.5f) Color(0xFFE4574B) else Color(0xFF3CA0C8)
                for (s in 0 until 5) drawRect((if (s % 2 == 0) aw else Color.White).night(n, 0.6f), Offset(x - bw / 2 + s * bw / 5f, baseY - bh - u * 1.2f), Size(bw / 5f + 1f, u * 2.2f))
            }
            r < 0.85f -> drawRoundTree(x, baseY, h * 0.055f, treeColor(o.season, k, Color(0xFF4FA889)), n)
            else -> { // bus stop sign
                drawLine(Color(0xFF55606A).night(n, 0.5f), Offset(x, baseY), Offset(x, baseY - h * 0.1f), strokeWidth = u * 0.6f)
                drawCircle(Pal.signBlue.night(n, 0.5f), u * 2.2f, Offset(x, baseY - h * 0.1f))
                drawRect(Color.White.night(n, 0.5f), Offset(x - u * 1.1f, baseY - h * 0.1f - u * 0.3f), Size(u * 2.2f, u * 0.6f))
            }
        }
        Biome.PARK -> when {
            r < 0.6f -> drawRoundTree(x, baseY, h * (0.05f + hash01(k, 36) * 0.025f), treeColor(o.season, k, Color(0xFF4FA889)), n)
            r < 0.8f -> { // bench
                val c = Color(0xFF9C6B4A).night(n, 0.6f)
                drawRect(c, Offset(x - u * 3f, baseY - u * 2.2f), Size(u * 6f, u * 0.6f))
                drawRect(c, Offset(x - u * 3f, baseY - u * 3.6f), Size(u * 6f, u * 0.5f))
                drawLine(c, Offset(x - u * 2.5f, baseY), Offset(x - u * 2.5f, baseY - u * 2.2f), strokeWidth = u * 0.5f)
                drawLine(c, Offset(x + u * 2.5f, baseY), Offset(x + u * 2.5f, baseY - u * 2.2f), strokeWidth = u * 0.5f)
            }
            else -> drawFlowers(x, baseY, u, k, n)
        }
        Biome.FIELD -> when {
            r < 0.45f -> { // fence
                val c = Color(0xFFB98B5E).night(n, 0.6f)
                for (p in 0 until 3) drawLine(c, Offset(x + p * u * 3f, baseY), Offset(x + p * u * 3f, baseY - u * 3.2f), strokeWidth = u * 0.6f)
                drawLine(c, Offset(x - u, baseY - u * 2.4f), Offset(x + u * 7f, baseY - u * 2.4f), strokeWidth = u * 0.5f)
                drawLine(c, Offset(x - u, baseY - u * 1.2f), Offset(x + u * 7f, baseY - u * 1.2f), strokeWidth = u * 0.5f)
            }
            r < 0.7f -> { // haystack
                val c = Color(0xFFE9C46A).night(n, 0.6f)
                drawArc(c, 180f, 180f, true, Offset(x - u * 3.5f, baseY - u * 4f), Size(u * 7f, u * 8f))
                drawLine(c.dim(0.85f), Offset(x - u * 2f, baseY - u * 2f), Offset(x + u * 2f, baseY - u * 2.4f), strokeWidth = u * 0.4f)
            }
            else -> { // sunflowers
                for (i in 0 until 3) {
                    val fx = x + i * u * 2.2f; val fh = u * (5f + hash01(k * 3 + i, 37) * 2f)
                    drawLine(Color(0xFF4F8A3C).night(n, 0.6f), Offset(fx, baseY), Offset(fx, baseY - fh), strokeWidth = u * 0.4f)
                    drawCircle(Color(0xFFF6C945).night(n, 0.6f), u * 1.3f, Offset(fx, baseY - fh))
                    drawCircle(Color(0xFF7A5A43).night(n, 0.6f), u * 0.55f, Offset(fx, baseY - fh))
                }
            }
        }
        Biome.SEASIDE -> when {
            r < 0.45f -> drawPine(rt, x, baseY, h * 0.09f, Color(0xFF3E8E6A), n)
            r < 0.7f -> { // beach umbrella
                val c = if (hash01(k, 38) < 0.5f) Color(0xFFE4574B) else Color(0xFF3CA0C8)
                drawLine(Color(0xFFEDEDED).night(n, 0.6f), Offset(x, baseY), Offset(x, baseY - u * 6f), strokeWidth = u * 0.4f)
                drawArc(c.night(n, 0.6f), 180f, 180f, true, Offset(x - u * 4f, baseY - u * 8f), Size(u * 8f, u * 4f))
                drawArc(Color.White.night(n, 0.6f), 240f, 60f, true, Offset(x - u * 4f, baseY - u * 8f), Size(u * 8f, u * 4f))
            }
            else -> { // gull on a post
                drawLine(Color(0xFF9C6B4A).night(n, 0.6f), Offset(x, baseY), Offset(x, baseY - u * 3f), strokeWidth = u * 0.8f)
                drawOval(Color.White.night(n, 0.6f), Offset(x - u * 1.2f, baseY - u * 4.6f), Size(u * 2.4f, u * 1.6f))
                drawCircle(Color.White.night(n, 0.6f), u * 0.7f, Offset(x + u * 1.0f, baseY - u * 4.9f))
                drawLine(Color(0xFFF2A93B), Offset(x + u * 1.6f, baseY - u * 4.9f), Offset(x + u * 2.3f, baseY - u * 4.8f), strokeWidth = u * 0.4f)
            }
        }
        Biome.MOUNTAIN -> when {
            r < 0.65f -> {
                drawPine(rt, x, baseY, h * (0.08f + hash01(k, 39) * 0.05f), Color(0xFF2F7A57), n)
                if (hash01(k, 40) < 0.5f) drawPine(rt, x + u * 4f, baseY, h * 0.065f, Color(0xFF3B8A62), n)
            }
            r < 0.85f -> drawOval(Color(0xFF9AA3A8).night(n, 0.6f), Offset(x - u * 3f, baseY - u * 2.4f), Size(u * 6f, u * 3f))
            else -> { // wooden sign
                drawLine(Color(0xFF7A5A43).night(n, 0.6f), Offset(x, baseY), Offset(x, baseY - u * 5f), strokeWidth = u * 0.6f)
                drawRoundRect(Color(0xFFB98B5E).night(n, 0.6f), Offset(x - u * 3f, baseY - u * 6.5f), Size(u * 6f, u * 2.4f), CornerRadius(u * 0.4f))
            }
        }
    }
}

private fun DrawScope.drawRoundTree(x: Float, baseY: Float, r: Float, foliage: Color, n: Float) {
    drawRect(Pal.trunk.night(n, 0.6f), Offset(x - r * 0.1f, baseY - r * 1.2f), Size(r * 0.2f, r * 1.2f))
    drawCircle(foliage.night(n), r, Offset(x, baseY - r * 1.7f))
    drawCircle(foliage.dim(0.92f).night(n), r * 0.7f, Offset(x + r * 0.6f, baseY - r * 1.35f))
    drawCircle(Color.White.alpha(0.12f * (1f - n)), r * 0.35f, Offset(x - r * 0.35f, baseY - r * 2.0f))
}

private fun DrawScope.drawPine(rt: SceneRuntime, x: Float, baseY: Float, hgt: Float, c: Color, n: Float) {
    val p = rt.tmpPath2
    drawRect(Pal.trunk.night(n, 0.6f), Offset(x - hgt * 0.04f, baseY - hgt * 0.15f), Size(hgt * 0.08f, hgt * 0.15f))
    for (i in 0 until 3) {
        val top = baseY - hgt * (0.45f + i * 0.22f)
        val half = hgt * (0.28f - i * 0.06f)
        p.reset(); p.moveTo(x - half, baseY - hgt * (0.12f + i * 0.22f)); p.lineTo(x, top - hgt * 0.1f); p.lineTo(x + half, baseY - hgt * (0.12f + i * 0.22f)); p.close()
        drawPath(p, c.dim(1f - i * 0.04f).night(n))
    }
}

private fun DrawScope.drawFlowers(x: Float, baseY: Float, u: Float, k: Int, n: Float) {
    drawOval(Color(0xFF5FAE6E).night(n), Offset(x - u * 3f, baseY - u * 2.6f), Size(u * 6f, u * 3f))
    val cols = arrayOf(Color(0xFFFF8FA3), Color(0xFFFFD34D), Color.White)
    for (i in 0 until 5) drawCircle(cols[(k + i) % 3].night(n, 0.6f), u * 0.55f, Offset(x - u * 2.2f + i * u * 1.1f, baseY - u * (1.4f + hash01(k * 5 + i, 42) * 1.2f)))
}

// ======================================================================== road

internal fun DrawScope.drawRoad(rt: SceneRuntime, o: SceneOptions) {
    val w = size.width; val h = size.height
    val n = rt.dark.value.coerceIn(0f, 1f)
    val top = h * Band.ROAD_TOP; val bot = h * Band.ROAD_BOT
    val asphalt = lerp(Pal.road, Pal.roadNight, n)
    drawRect(asphalt, Offset(0f, top), Size(w, bot - top))
    drawRect(Pal.curb.night(n), Offset(0f, top - h * 0.006f), Size(w, h * 0.014f))
    // centre dashes
    val dash = 0.12
    val first = floorDiv(rt.scrollNear, dash) - 1
    val dashCol = Pal.dash.night(n, 0.5f)
    for (d in first..first + (1.0 / dash).toInt() + 2) {
        val dx = ((d * dash - rt.scrollNear) * w).toFloat()
        drawLine(dashCol, Offset(dx, h * Band.CENTER_LINE), Offset(dx + (dash * w * 0.5).toFloat(), h * Band.CENTER_LINE), strokeWidth = h * 0.011f, cap = StrokeCap.Round)
    }
    // edge line
    drawLine(dashCol.alpha(0.8f), Offset(0f, bot - h * 0.012f), Offset(w, bot - h * 0.012f), strokeWidth = h * 0.006f)
    // wet road: soft reflections that slide with the road
    if (rt.rain.value > 0.15f) {
        val a = rt.rain.value * 0.35f
        val step = 0.09
        val f2 = floorDiv(rt.scrollNear, step) - 1
        for (i in f2..f2 + (1.0 / step).toInt() + 2) {
            val rx = ((i * step - rt.scrollNear) * w).toFloat()
            val ry = h * (0.82f + hash01(i, 51) * 0.1f)
            drawLine(Color.White.alpha(a * (0.4f + hash01(i, 52) * 0.6f)), Offset(rx, ry), Offset(rx + w * 0.03f, ry), strokeWidth = 2.5f, cap = StrokeCap.Round)
        }
    }
    // near verge with grass tufts (fastest layer)
    val verge = lerp(rt.groundCol.dim(0.88f), rt.groundCol, 0.3f).night(n)
    drawRect(verge, Offset(0f, bot), Size(w, h - bot))
    val tuft = 0.07
    val f3 = floorDiv(rt.scrollNear * 1.2, tuft) - 1
    val tc = Color(0xFF5FAE6E).dim(0.9f).night(n)
    for (i in f3..f3 + (1.0 / tuft).toInt() + 2) {
        if (hash01(i, 61) < 0.45f) continue
        val tx = ((i * tuft - rt.scrollNear * 1.2) * w).toFloat()
        val ty = bot + (h - bot) * (0.35f + hash01(i, 62) * 0.5f)
        val s = h * 0.012f
        drawLine(tc, Offset(tx, ty), Offset(tx - s * 0.4f, ty - s), strokeWidth = 2.2f, cap = StrokeCap.Round)
        drawLine(tc, Offset(tx, ty), Offset(tx + s * 0.1f, ty - s * 1.2f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        drawLine(tc, Offset(tx, ty), Offset(tx + s * 0.5f, ty - s * 0.9f), strokeWidth = 2.2f, cap = StrokeCap.Round)
    }
}

// ======================================================================== entities

/** Km signs stand at the far edge of the road (drawn before the kart). */
internal fun DrawScope.drawRoadsideSigns(rt: SceneRuntime, tm: TextMeasurer?, cache: TextCache) {
    val h = size.height; val w = size.width
    val n = rt.dark.value.coerceIn(0f, 1f)
    for (i in 0 until rt.ents.count) {
        if (rt.ents.type[i] != EType.KM_SIGN) continue
        val x = rt.ents.x[i] * w
        val base = h * Band.ROAD_TOP - h * 0.004f
        val postH = h * 0.11f
        drawLine(Color(0xFF8E979C).night(n, 0.5f), Offset(x, base), Offset(x, base - postH), strokeWidth = h * 0.007f)
        val sw = h * 0.13f; val sh = h * 0.065f
        val tl = Offset(x - sw / 2, base - postH - sh)
        drawRoundRect(Pal.signGreen.night(n, 0.35f), tl, Size(sw, sh), CornerRadius(h * 0.01f))
        drawRoundRect(Color.White.alpha(0.85f), tl + Offset(h * 0.004f, h * 0.004f), Size(sw - h * 0.008f, sh - h * 0.008f), CornerRadius(h * 0.008f), style = Stroke(width = h * 0.003f))
        if (tm != null) {
            val layout = cache.get(tm, "${rt.ents.value[i]} km", sh * 0.5f, this)
            drawText(layout, color = Color.White, topLeft = Offset(x - layout.size.width / 2f, tl.y + (sh - layout.size.height) / 2f))
        }
    }
}

/** Star pickups float at Vroomi's face height; the finish arch spans the road on arrival. */
internal fun DrawScope.drawRoadEntities(rt: SceneRuntime) {
    val w = size.width; val h = size.height
    val s = rt.unitPx(w, h, 1f)
    for (i in 0 until rt.ents.count) {
        when (rt.ents.type[i]) {
            EType.STAR -> {
                val (nx, ny) = rt.starWorldN(rt.ents.x[i], rt.ents.age[i])
                val c = Offset(nx * w, ny * h)
                val r = s * 0.11f
                drawCircle(Pal.star.alpha(0.25f), r * 1.8f, c)
                rotate(sin(rt.ents.age[i] * 3f) * 12f, c) { drawStar(rt, c, r, Pal.star, Pal.starEdge) }
            }
            EType.FINISH -> drawFinish(rt, rt.ents.x[i] * w, rt.ents.age[i], s)
            else -> Unit
        }
    }
}

/** Arrival: a checkered finish line painted across the road and a waving checkered flag on the far side. */
private fun DrawScope.drawFinish(rt: SceneRuntime, x: Float, age: Float, s: Float) {
    val h = size.height
    val a = (age / 0.4f).coerceIn(0f, 1f)
    val top = h * Band.ROAD_TOP; val bot = h * Band.ROAD_BOT
    val rows = 10
    val cell = (bot - top) / rows
    for (r in 0 until rows) for (c in 0 until 2) {
        val col = if ((r + c) % 2 == 0) Color.White else Color(0xFF222222)
        val slant = (r * cell) * 0.25f
        drawRect(col.alpha(a), Offset(x + c * cell + slant, top + r * cell), Size(cell + 1f, cell + 1f))
    }
    val postX = x + cell
    val postTop = top - s * 1.1f
    drawLine(Color(0xFF8E979C).alpha(a), Offset(postX, top), Offset(postX, postTop), strokeWidth = s * 0.03f, cap = StrokeCap.Round)
    val fw = s * 0.42f; val fh = s * 0.26f
    val wave = sin(rt.time * 8f) * fh * 0.08f
    for (cx in 0 until 6) for (cy in 0 until 4) {
        val col = if ((cx + cy) % 2 == 0) Color.White else Color(0xFF222222)
        val px = postX + cx * fw / 6f
        val py = postTop + cy * fh / 4f + wave * (cx / 6f) * (if (cx % 2 == 0) 1f else -1f)
        drawRect(col.alpha(a), Offset(px, py), Size(fw / 6f + 1f, fh / 4f + 1f))
    }
}

internal fun DrawScope.drawStar(rt: SceneRuntime, c: Offset, r: Float, fill: Color, edge: Color?) {
    val p = rt.tmpPath2
    p.reset()
    for (i in 0 until 10) {
        val ang = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
        val rr = if (i % 2 == 0) r else r * 0.48f
        val px = c.x + kotlin.math.cos(ang) * rr; val py = c.y + sin(ang) * rr
        if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
    }
    p.close()
    drawPath(p, fill)
    if (edge != null) drawPath(p, edge, style = Stroke(width = r * 0.16f))
}

// ======================================================================== speed lines

internal fun DrawScope.drawSpeedLines(rt: SceneRuntime) {
    val w = size.width; val h = size.height
    val f = rt.speedFx.value
    if (f < 0.5f) return
    val n = rt.dark.value
    val c = lerp(Color.White, Color(0xFF9CC8C0), n).alpha(0.30f * ((f - 0.5f) / 0.5f))
    for (i in 0 until 6) {
        val y = h * (0.16f + i * 0.08f)
        val off = (((rt.scrollNear * 2.6 + i * 0.37) % 1.0) * w).toFloat()
        drawLine(c, Offset(w - off, y), Offset(w - off + w * 0.07f, y), strokeWidth = 3f, cap = StrokeCap.Round)
    }
}

