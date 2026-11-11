package io.vroomi.character.render.hangar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import io.vroomi.character.render.hash01
import io.vroomi.character.render.theme.drawTextCentered
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The hangar's colours: graphite steel and concrete, signal orange paint, warm sodium and cool LED light. */
internal object Bay {
    val wallTop = Color(0xFF14161A)
    val wall = Color(0xFF22262B)
    val wallPanel = Color(0xFF2B3036)
    val steel = Color(0xFF3C4148)
    val steelLight = Color(0xFF5B626B)
    val steelDark = Color(0xFF23262B)
    val concrete = Color(0xFF4A4D50)
    val concreteDeep = Color(0xFF2B2D30)
    val orange = Color(0xFFF07A1A)
    val amber = Color(0xFFFFB03A)
    val paintWhite = Color(0xFFD9D7D0)
    val sodium = Color(0xFFFFC98A)
    val led = Color(0xFFCFE3FF)
    val sky = Color(0xFF6E8298)
    val skyNight = Color(0xFF131A26)
    val shadow = Color(0xFF07080A)

    /** The floor line the robot stands on, and where the wall meets the floor (design units, canvas 600 tall). */
    const val FEET = 524f
    const val WALL_B = 468f
    /** Half the distance between the two gantry towers. */
    const val GANTRY = 206f
    const val CRANE_Y = 58f
    /** The robot's size on screen: its 440-unit drawing stands just under the gantry's beam. */
    const val ROBOT = 0.92f
}

private val HAIR = Stroke(1f)

/** Diagonal hazard stripes (amber on black) along a band. */
private fun DrawScope.hazard(l: Float, t: Float, w: Float, h: Float) {
    drawRect(Bay.shadow, Offset(l, t), Size(w, h))
    clipRect(l, t, l + w, t + h) {
        var x = l - h
        while (x < l + w) {
            val p = Path().apply { moveTo(x, t + h); lineTo(x + h, t); lineTo(x + h + h * 0.55f, t); lineTo(x + h * 0.55f, t + h); close() }
            drawPath(p, Bay.amber.copy(alpha = 0.85f))
            x += h * 1.25f
        }
    }
}

/** A lattice tower of the gantry: two posts, cross bracing, two catwalks reaching toward the robot. [dir] +1: the catwalks reach right. */
private fun DrawScope.tower(x: Float, dir: Float) {
    val top = 96f; val bottom = Bay.FEET
    val w = 34f
    val l = if (dir > 0f) x - w else x
    // Posts
    for (px in floatArrayOf(l, l + w - 6f)) {
        drawRect(Brush.horizontalGradient(listOf(Bay.steelLight, Bay.steel, Bay.steelDark), startX = px, endX = px + 6f), Offset(px, top), Size(6f, bottom - top))
    }
    // Bracing
    var y = top
    var k = 0
    while (y < bottom - 30f) {
        val a = Offset(l + 3f, y); val b = Offset(l + w - 3f, y + 38f)
        if (k % 2 == 0) drawLine(Bay.steel, a, b, 2.4f) else drawLine(Bay.steel, Offset(l + w - 3f, y), Offset(l + 3f, y + 38f), 2.4f)
        drawLine(Bay.steelLight.copy(alpha = 0.5f), Offset(l, y), Offset(l + w, y), 1.6f)
        y += 38f; k++
    }
    // Catwalks at two levels, with a rail and a hazard edge
    for (cy in floatArrayOf(214f, 352f)) {
        val reach = 58f
        val cl = if (dir > 0f) x else x - reach
        drawRect(Bay.steelDark, Offset(cl, cy), Size(reach, 7f))
        drawRect(Bay.steelLight.copy(alpha = 0.6f), Offset(cl, cy), Size(reach, 1.4f))
        hazard(cl, cy + 7f, reach, 5f)
        drawLine(Bay.steelLight, Offset(cl, cy - 20f), Offset(cl + reach, cy - 20f), 1.6f)
        for (i in 0..3) drawLine(Bay.steel, Offset(cl + reach * i / 3f, cy - 20f), Offset(cl + reach * i / 3f, cy), 1.4f)
    }
    hazard(l - 6f, bottom - 10f, w + 12f, 10f)
}

/** A lamp on the wall and the soft pool of light under it. */
private fun DrawScope.lamp(at: Offset, color: Color, reach: Float, strength: Float) {
    drawOval(Brush.radialGradient(listOf(color.copy(alpha = 0.30f * strength), color.copy(alpha = 0f)), Offset(at.x, at.y + reach * 0.5f), reach), Offset(at.x - reach * 0.7f, at.y), Size(reach * 1.4f, reach * 1.7f))
    drawRect(Bay.steelDark, Offset(at.x - 14f, at.y - 6f), Size(28f, 7f))
    drawRect(color, Offset(at.x - 11f, at.y + 1f), Size(22f, 3f))
}

/**
 * The bay, painted once: the back wall with its panels and the door to the outside at the left, the roof truss
 * and the crane's rail, sodium lamps and LED strips, the concrete floor with its painted bay lines, and the two
 * gantry towers the robot stands between.
 */
internal fun DrawScope.bayStatic(dw: Float, dh: Float, cx: Float) {
    val wb = Bay.WALL_B
    drawRect(Brush.verticalGradient(0f to Bay.wallTop, 0.35f to Bay.wall, 1f to Bay.wallPanel, startY = 0f, endY = wb), size = Size(dw, wb))
    // Wall panels: tall sheets with faint seams
    var x = (cx % 92f) - 92f
    var i = 0
    while (x < dw) {
        drawLine(Color.Black.copy(alpha = 0.28f), Offset(x, 70f), Offset(x, wb), 1.2f)
        drawLine(Color.White.copy(alpha = 0.035f), Offset(x + 1.4f, 70f), Offset(x + 1.4f, wb), 1f)
        if (hash01(i, 3) > 0.6f) drawRect(Color.Black.copy(alpha = 0.07f), Offset(x, 70f), Size(92f, wb - 70f))
        x += 92f; i++
    }
    drawRect(Bay.steelDark, Offset(0f, 252f), Size(dw, 5f))
    // The roof: a truss and the crane's rail
    drawRect(Bay.shadow.copy(alpha = 0.55f), size = Size(dw, 44f))
    drawRect(Bay.steel, Offset(0f, Bay.CRANE_Y), Size(dw, 8f))
    drawRect(Bay.steelLight.copy(alpha = 0.55f), Offset(0f, Bay.CRANE_Y), Size(dw, 1.5f))
    var tx = (cx % 64f) - 64f
    while (tx < dw) {
        drawLine(Bay.steel, Offset(tx, 22f), Offset(tx + 32f, Bay.CRANE_Y), 2f)
        drawLine(Bay.steel, Offset(tx + 32f, Bay.CRANE_Y), Offset(tx + 64f, 22f), 2f)
        tx += 64f
    }
    drawRect(Bay.steel, Offset(0f, 20f), Size(dw, 4f))
    // Lamps: warm sodium high on the wall, a cool LED strip over the robot
    for (k in -3..3) if (k != 0) lamp(Offset(cx + k * 230f, 84f), Bay.sodium, 190f, 1f)
    drawRect(Bay.led.copy(alpha = 0.9f), Offset(cx - 120f, 46f), Size(240f, 3f))
    drawOval(Brush.radialGradient(listOf(Bay.led.copy(alpha = 0.20f), Bay.led.copy(alpha = 0f)), Offset(cx, 240f), 330f), Offset(cx - 300f, 40f), Size(600f, 520f))

    // The floor: concrete, darker toward the camera, with a low-contrast aggregate and the painted bay
    val fl = Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, wb, dw, dh)) }
    drawPath(fl, Brush.verticalGradient(0f to Bay.concrete, 1f to Bay.concreteDeep, startY = wb, endY = dh))
    clipRect(0f, wb, dw, dh) {
        val n = (dw * 0.5f).toInt()
        for (k in 0 until n) {
            val p = Offset(hash01(k, 11) * dw, wb + hash01(k, 12) * (dh - wb))
            drawCircle(if (hash01(k, 13) > 0.5f) Color.White.copy(alpha = 0.035f) else Color.Black.copy(alpha = 0.07f), 0.6f + hash01(k, 14) * 1.2f, p)
        }
        // Expansion joints
        var jx = (cx % 240f) - 240f
        while (jx < dw) { drawLine(Color.Black.copy(alpha = 0.22f), Offset(jx, wb), Offset(jx + (jx - cx) * 0.25f, dh), 1.2f); jx += 240f }
    }
    drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, wb), Size(dw, 3f))
    // The bay's painted outline around the robot's stand, and a walkway line
    val by = Bay.FEET + 22f
    drawLine(Bay.orange.copy(alpha = 0.8f), Offset(cx - 290f, by), Offset(cx + 290f, by), 4f)
    drawLine(Bay.orange.copy(alpha = 0.8f), Offset(cx - 290f, by), Offset(cx - 262f, Bay.FEET - 30f), 4f)
    drawLine(Bay.orange.copy(alpha = 0.8f), Offset(cx + 290f, by), Offset(cx + 262f, Bay.FEET - 30f), 4f)
    drawLine(Bay.paintWhite.copy(alpha = 0.45f), Offset(0f, dh - 26f), Offset(dw, dh - 26f), 3f)
    // The robot's contact shadow
    drawOval(Brush.radialGradient(listOf(Bay.shadow.copy(alpha = 0.6f), Bay.shadow.copy(alpha = 0f)), Offset(cx + 14f, Bay.FEET + 4f), 190f), Offset(cx - 180f, Bay.FEET - 14f), Size(390f, 40f))

    tower(cx - Bay.GANTRY, 1f)
    tower(cx + Bay.GANTRY, -1f)
    // The beam across the towers
    drawRect(Brush.verticalGradient(listOf(Bay.steelLight, Bay.steel, Bay.steelDark), startY = 96f, endY = 110f), Offset(cx - Bay.GANTRY - 34f, 96f), Size(Bay.GANTRY * 2f + 68f, 14f))
    hazard(cx - Bay.GANTRY - 34f, 110f, Bay.GANTRY * 2f + 68f, 5f)
}

/** The door to the outside, at the far left of the back wall: the sky (day or night) and, in rain, falling streaks. */
internal fun DrawScope.bayDoor(cx: Float, night: Float, rain: Float, time: Double) {
    val l = cx - 560f; val t = 150f; val w = 150f; val b = Bay.WALL_B
    val sky = androidx.compose.ui.graphics.lerp(Bay.sky, Bay.skyNight, night)
    drawRect(Brush.verticalGradient(listOf(sky, androidx.compose.ui.graphics.lerp(sky, Color.White, 0.25f * (1f - night))), startY = t, endY = b), Offset(l, t), Size(w, b - t))
    // The apron outside
    drawRect(Color.Black.copy(alpha = 0.35f), Offset(l, b - 34f), Size(w, 34f))
    if (rain > 0.01f) clipRect(l, t, l + w, b) {
        for (k in 0 until 26) {
            val x = l + hash01(k, 31) * w
            val y = t + (((hash01(k, 32) + time * (0.9 + 0.5 * hash01(k, 33))) % 1.0).toFloat()) * (b - t)
            drawLine(Color.White.copy(alpha = 0.30f * rain), Offset(x, y), Offset(x - 3f, y + 20f), 1.1f)
        }
    }
    // Door frame and its rolled shutter
    drawRect(Bay.steelDark, Offset(l - 8f, t - 10f), Size(w + 16f, 12f))
    drawRect(Bay.steel, Offset(l - 8f, t), Size(8f, b - t)); drawRect(Bay.steel, Offset(l + w, t), Size(8f, b - t))
    for (k in 0 until 3) drawLine(Color.Black.copy(alpha = 0.4f), Offset(l - 6f, t - 8f + k * 3.5f), Offset(l + w + 6f, t - 8f + k * 3.5f), 1f)
}

/** The wall's ventilation fan (it turns with the climate fan). */
internal fun DrawScope.bayVent(cx: Float, angle: Double) {
    val c = Offset(cx + 470f, 196f); val r = 34f
    drawCircle(Bay.shadow, r, c)
    rotate((angle * 180.0 / PI).toFloat() % 360f, c) {
        for (k in 0 until 4) rotate(k * 90f, c) {
            val p = Path().apply { moveTo(c.x, c.y); quadraticTo(c.x + r * 0.5f, c.y - r * 0.35f, c.x + r * 0.9f, c.y - r * 0.1f); quadraticTo(c.x + r * 0.55f, c.y + r * 0.2f, c.x, c.y); close() }
            drawPath(p, Bay.steelLight.copy(alpha = 0.8f))
        }
    }
    drawCircle(Bay.steel, r, c, style = Stroke(4f))
    drawCircle(Bay.steelDark, 6f, c)
    for (k in -1..1) drawLine(Bay.steel, Offset(c.x - r, c.y + k * 14f), Offset(c.x + r, c.y + k * 14f), 1.2f)
}

/** Wet floor: the lamps' reflections stand in the water as soft vertical streaks. */
internal fun DrawScope.bayWet(dw: Float, dh: Float, cx: Float, rain: Float) {
    if (rain <= 0.01f) return
    drawRect(Color.White.copy(alpha = 0.04f * rain), Offset(0f, Bay.WALL_B), Size(dw, dh - Bay.WALL_B))
    for (k in -3..3) {
        val x = cx + k * 230f
        val col = if (k == 0) Bay.led else Bay.sodium
        drawRect(Brush.verticalGradient(listOf(col.copy(alpha = 0.22f * rain), col.copy(alpha = 0f)), startY = Bay.WALL_B, endY = dh), Offset(x - 16f, Bay.WALL_B), Size(32f, dh - Bay.WALL_B))
    }
}

/** Night: the floodlights on the gantry come on (two cool cones on the robot), the rest of the hall falls back. */
internal fun DrawScope.bayNight(dw: Float, dh: Float, cx: Float, night: Float) {
    if (night <= 0.01f) return
    drawRect(Bay.shadow.copy(alpha = 0.34f * night), size = Size(dw, dh))
    for (s in floatArrayOf(-1f, 1f)) {
        val src = Offset(cx + s * (Bay.GANTRY - 6f), 122f)
        val cone = Path().apply { moveTo(src.x, src.y); lineTo(cx - s * 30f, Bay.FEET); lineTo(cx + s * 150f, Bay.FEET); close() }
        drawPath(cone, Brush.verticalGradient(listOf(Bay.led.copy(alpha = 0.17f * night), Bay.led.copy(alpha = 0.02f * night)), startY = src.y, endY = Bay.FEET))
        drawCircle(Bay.led.copy(alpha = 0.95f * night), 5f, src)
    }
}

/** A worker's silhouette for scale (about 1.7 m beside an 18 m robot), with a hi-vis band. [x] feet, [y] ground. */
internal fun DrawScope.worker(x: Float, y: Float, step: Float) {
    val h = 40f
    drawLine(Bay.shadow, Offset(x, y - h * 0.48f), Offset(x - 3f - step * 3f, y), 3.2f, cap = StrokeCap.Round)
    drawLine(Bay.shadow, Offset(x, y - h * 0.48f), Offset(x + 3f + step * 3f, y), 3.2f, cap = StrokeCap.Round)
    drawLine(Bay.shadow, Offset(x, y - h * 0.48f), Offset(x, y - h * 0.84f), 6.4f, cap = StrokeCap.Round)
    drawLine(Bay.amber.copy(alpha = 0.9f), Offset(x - 3f, y - h * 0.68f), Offset(x + 3f, y - h * 0.68f), 2.4f)
    drawCircle(Bay.shadow, 3.4f, Offset(x, y - h * 0.93f))
    drawCircle(Bay.paintWhite.copy(alpha = 0.8f), 3.4f, Offset(x, y - h * 0.97f), style = HAIR)
}

/** The crane's trolley on its rail, its cable and what hangs from it: a small part (a star) or an armour plate (a kilometre). */
internal fun DrawScope.crane(x: Float, drop: Float, plate: Boolean) {
    val y = Bay.CRANE_Y + 8f
    drawRect(Bay.orange, Offset(x - 22f, y - 2f), Size(44f, 12f))
    drawRect(Color.Black.copy(alpha = 0.35f), Offset(x - 22f, y + 6f), Size(44f, 4f))
    drawLine(Bay.steelLight, Offset(x, y + 10f), Offset(x, y + 10f + drop), 1.6f)
    val hy = y + 10f + drop
    if (plate) {
        drawLine(Bay.steelLight, Offset(x, hy), Offset(x - 20f, hy + 14f), 1.2f); drawLine(Bay.steelLight, Offset(x, hy), Offset(x + 20f, hy + 14f), 1.2f)
        drawRect(Color(0xFF8B9096), Offset(x - 26f, hy + 14f), Size(52f, 30f))
        drawRect(Color.White.copy(alpha = 0.18f), Offset(x - 26f, hy + 14f), Size(52f, 3f))
        drawRect(Color.Black.copy(alpha = 0.5f), Offset(x - 26f, hy + 14f), Size(52f, 30f), style = HAIR)
    } else {
        drawRect(Bay.steelLight, Offset(x - 8f, hy), Size(16f, 12f))
        drawRect(Color.Black.copy(alpha = 0.5f), Offset(x - 8f, hy), Size(16f, 12f), style = HAIR)
    }
}

/** The gantry's clamps (they close on the robot's waist after harsh braking) and the amber lamp turning on the beam. */
internal fun DrawScope.clamps(cx: Float, close: Float, lampAngle: Double) {
    val y = Bay.FEET - 236f * Bay.ROBOT
    for (s in floatArrayOf(-1f, 1f)) {
        val from = cx + s * (Bay.GANTRY - 34f)
        val to = cx + s * (150f - 90f * close)
        drawLine(Bay.steelDark, Offset(from, y), Offset(to, y), 12f)
        drawLine(Bay.steelLight.copy(alpha = 0.6f), Offset(from, y - 4.5f), Offset(to, y - 4.5f), 1.4f)
        val jaw = Path().apply { moveTo(to, y - 16f); lineTo(to - s * 16f, y - 16f); lineTo(to - s * 16f, y - 9f); lineTo(to - s * 6f, y - 9f); lineTo(to - s * 6f, y + 9f); lineTo(to - s * 16f, y + 9f); lineTo(to - s * 16f, y + 16f); lineTo(to, y + 16f); close() }
        drawPath(jaw, Bay.orange)
        drawPath(jaw, Color.Black.copy(alpha = 0.5f), style = HAIR)
    }
    if (close > 0.01f) {
        // A rotating lamp: its beam turns steadily (no blinking); seen from the front it brightens and dims smoothly.
        val c = Offset(cx - Bay.GANTRY - 17f, 88f)
        val facing = (0.5 + 0.5 * cos(lampAngle)).toFloat()
        drawCircle(Brush.radialGradient(listOf(Bay.amber.copy(alpha = (0.18f + 0.30f * facing) * close), Bay.amber.copy(alpha = 0f)), c, 70f), 70f, c)
        drawRect(Bay.steelDark, Offset(c.x - 8f, c.y + 5f), Size(16f, 5f))
        drawCircle(Bay.amber.copy(alpha = (0.55f + 0.4f * facing) * close + 0.2f * (1f - close)), 7f, c)
        drawLine(Color.White.copy(alpha = 0.5f * facing * close), Offset(c.x + (4.0 * sin(lampAngle)).toFloat(), c.y - 4f), Offset(c.x + (4.0 * sin(lampAngle)).toFloat(), c.y + 4f), 1.4f)
    }
}

/**
 * The order board on the wall beside the gantry: the unit's code over a swatch of its three paints. It names the
 * unit the step picker chose, so the choice shows even in an empty bay.
 */
internal fun DrawScope.unitSign(x: Float, y: Float, style: MechStyle, code: androidx.compose.ui.text.TextLayoutResult) {
    val w = 132f; val h = 92f
    drawRect(Color.Black.copy(alpha = 0.35f), Offset(x - w / 2 + 3f, y - h / 2 + 4f), Size(w, h))
    drawRect(Color(0xFF23272D), Offset(x - w / 2, y - h / 2), Size(w, h))
    drawRect(Color(0xFF4A515A), Offset(x - w / 2, y - h / 2), Size(w, h), style = HAIR)
    drawRect(Color.White.copy(alpha = 0.06f), Offset(x - w / 2, y - h / 2), Size(w, 2f))
    drawTextCentered(code, x, y - 16f)
    val sw = (w - 24f) / 3f
    val paints = arrayOf(style.primary, style.secondary, style.accent)
    for (i in 0 until 3) {
        drawRect(paints[i], Offset(x - w / 2 + 12f + i * sw, y + 14f), Size(sw - 4f, 20f))
        drawRect(Color.Black.copy(alpha = 0.4f), Offset(x - w / 2 + 12f + i * sw, y + 14f), Size(sw - 4f, 20f), style = HAIR)
    }
    for (s in floatArrayOf(-1f, 1f)) drawCircle(Color(0xFF6A727C), 2.2f, Offset(x + s * (w / 2 - 6f), y - h / 2 + 6f))
}
