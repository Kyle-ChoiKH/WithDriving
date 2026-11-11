package io.vroomi.character.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import io.vroomi.character.Accessory
import io.vroomi.character.Blinker
import io.vroomi.character.CharacterTarget
import io.vroomi.character.KartStyle
import io.vroomi.character.Mood
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Vroomi's kart, drawn in "s units": every function here runs inside scale(s) with the origin at the
 * road contact point under the kart centre (x forward/right, y up is negative).
 */

internal fun kartColor(style: KartStyle, skin: io.vroomi.character.CharacterSkin = io.vroomi.character.CharacterSkin.VROOMI): Color = when (style) {
    KartStyle.NORMAL -> skinColors(skin).kart; KartStyle.SPORT -> Pal.kartSport; KartStyle.ECO -> Pal.kartEco
}

/** Smooth blinker pulse ~1.5 Hz (soft on/off, no hard flashing). */
internal fun blinkPulse(time: Float): Float {
    val v = sin(time * 2f * PI.toFloat() * 1.5f)
    return smooth01((v + 0.3f) / 0.6f)
}

/** Ground-level effects under the kart (not pitched): shadow, cruise underglow. */
internal fun DrawScope.drawKartGround(rt: SceneRuntime, n: Float) {
    drawOval(Color.Black.alpha(0.16f), Offset(-0.92f, -0.045f), Size(1.86f, 0.09f))
    val g = rt.underglow.value
    if (g > 0.02f) {
        drawOval(Pal.underglow.alpha(0.18f * g * (0.6f + 0.4f * n)), Offset(-1.05f, -0.08f), Size(2.1f, 0.16f))
        drawOval(Pal.underglow.alpha(0.30f * g), Offset(-0.75f, -0.035f), Size(1.5f, 0.07f))
    }
}

/** Headlight cone onto the road ahead (night / rain). */
internal fun DrawScope.drawHeadlightCone(rt: SceneRuntime, t: CharacterTarget, n: Float) {
    val on = t.kart.driving && (t.kart.headlights || n > 0.6f)
    if (!on) return
    val a = (0.25f + 0.25f * n)
    val p = rt.tmpPath
    p.reset()
    p.moveTo(KG.HEAD_X + 0.02f, KG.HEAD_Y - 0.03f)
    p.lineTo(3.2f, -0.95f)
    p.lineTo(3.2f, 0.02f)
    p.lineTo(KG.HEAD_X + 0.02f, KG.HEAD_Y + 0.04f)
    p.close()
    drawPath(p, Brush.horizontalGradient(listOf(Pal.headlight.alpha(a), Pal.headlight.alpha(0f)), KG.HEAD_X, 3.2f))
}

/** Behind Vroomi: flag pole with its fluttering flag, the lantern at night, the charger cable. */
internal fun DrawScope.drawKartBack(rt: SceneRuntime, t: CharacterTarget, n: Float, seated: Boolean) {
    val k = t.kart
    // Flag pole
    val base = Offset(-0.74f, -0.66f); val top = Offset(-0.82f, -1.56f)
    drawLine(Color(0xFF8E979C).night(n, 0.4f), base, top, strokeWidth = 0.024f, cap = StrokeCap.Round)
    val speed = rt.speedFx.value
    val wave = sin(rt.sailT * 1.4f) * (0.03f + 0.05f * (speed + rt.wind.value))
    val droop = (1f - (speed * 1.6f + rt.wind.value).coerceIn(0f, 1f)) * 0.16f
    val p = rt.tmpPath
    val tip = Offset(top.x - 0.34f + droop * 0.6f, top.y + 0.09f + droop + wave)
    p.reset()
    p.moveTo(top.x, top.y)
    p.cubicTo(top.x - 0.10f, top.y + wave, tip.x + 0.10f, tip.y - 0.05f - wave, tip.x, tip.y)
    p.cubicTo(tip.x + 0.12f, tip.y + 0.05f + wave, top.x - 0.10f, top.y + 0.20f - wave, top.x, top.y + 0.19f)
    p.close()
    val parade = KartStep.roadster(rt.model)
    val flagCol = if (parade) Color(0xFFFFF1C9) else when (k.style) { KartStyle.ECO -> Color(0xFF6CC24A); KartStyle.SPORT -> Color(0xFFFFFFFF); else -> Pal.raincoat }
    drawPath(p, flagCol.night(n, 0.5f))
    drawPath(p, Pal.kartLine.alpha(0.6f), style = Stroke(width = 0.012f))
    drawStar(rt, Offset(top.x - 0.12f, top.y + 0.10f + wave * 0.5f), 0.035f, if (parade) Pal.star else if (k.style == KartStyle.SPORT) Pal.kartSport else Color.White, if (parade) Pal.starEdge else null)
    // The parade flag carries a row of stars.
    if (parade) for (i in 1..2) drawStar(rt, Offset(top.x - 0.12f - i * 0.075f + droop * 0.25f * i, top.y + 0.10f + wave * (0.5f + 0.2f * i) + droop * 0.3f * i), 0.024f, Pal.star, Pal.starEdge)
    drawCircle(Pal.sole, 0.028f, top)

    // Lantern on the pole at night (the standing pose holds it in the hand instead)
    if (seated && Accessory.LANTERN in t.accessories) {
        val lx = -0.80f; val ly = -1.24f
        val sway = sin(rt.time * 2.2f) * 6f
        rotate(sway, Offset(lx, ly)) {
            drawLine(Pal.cable, Offset(lx, ly), Offset(lx, ly + 0.06f), strokeWidth = 0.01f)
            drawCircle(Pal.sole.alpha(0.30f), 0.13f, Offset(lx, ly + 0.11f))
            drawRoundRect(Pal.sparkle, Offset(lx - 0.035f, ly + 0.06f), Size(0.07f, 0.09f), CornerRadius(0.015f))
            drawRoundRect(Pal.cable, Offset(lx - 0.035f, ly + 0.06f), Size(0.07f, 0.09f), CornerRadius(0.015f), style = Stroke(width = 0.01f))
        }
    }
}

/** Charger post and cable when the car is charging (parked). Drawn in kart space, left of the kart. */
internal fun DrawScope.drawCharger(rt: SceneRuntime, t: CharacterTarget, n: Float) {
    if (!t.kart.charging || Accessory.CABLE_LASSO !in t.accessories) return
    val px = -1.42f
    drawRoundRect(Color(0xFFE9EEF0).night(n, 0.5f), Offset(px - 0.10f, -1.0f), Size(0.20f, 1.0f), CornerRadius(0.05f))
    drawRoundRect(Color(0xFF39434A), Offset(px - 0.07f, -0.92f), Size(0.14f, 0.12f), CornerRadius(0.02f))
    val pulse = 0.5f + 0.5f * sin(rt.time * 3f)
    drawRect(Pal.energy.alpha(0.6f + 0.4f * pulse), Offset(px - 0.05f, -0.90f), Size(0.10f * (0.4f + 0.6f * pulse), 0.08f))
    val p = rt.tmpPath
    p.reset()
    p.moveTo(px + 0.08f, -0.55f)
    p.cubicTo(px + 0.25f, -0.10f, -1.0f, -0.12f, KG.TAIL_X - 0.01f, -0.44f)
    drawPath(p, Pal.cable, style = Stroke(width = 0.035f, cap = StrokeCap.Round))
    // energy dots travelling along the cable
    for (i in 0 until 3) {
        val u = ((rt.time * 0.6f + i / 3f) % 1f)
        val x = px + 0.08f + (KG.TAIL_X - px - 0.09f) * u
        val y = -0.55f + 0.25f * sin(u * PI.toFloat()) + (-0.44f + 0.55f) * u
        drawCircle(Pal.energy.alpha(0.9f), 0.025f, Offset(x, y))
    }
}

/** Tub, decals, star meter, lights and wheels: drawn after Vroomi's body (covers the lower half). */
internal fun DrawScope.drawKartBody(rt: SceneRuntime, t: CharacterTarget, n: Float) {
    val k = t.kart
    val col = kartColor(k.style, rt.skin).night(n, 0.55f)
    val p = rt.kartPath
    p.reset()
    p.moveTo(-0.84f, -0.24f)
    p.cubicTo(-0.89f, -0.40f, -0.89f, -0.60f, -0.80f, -0.66f)
    p.lineTo(-0.70f, -0.67f)
    p.cubicTo(-0.40f, -0.63f, 0.10f, -0.63f, 0.32f, -0.65f)
    p.cubicTo(0.52f, -0.63f, 0.76f, -0.57f, 0.86f, -0.47f)
    p.cubicTo(0.94f, -0.39f, 0.94f, -0.26f, 0.88f, -0.20f)
    p.lineTo(0.80f, -0.15f)
    p.lineTo(-0.76f, -0.15f)
    p.cubicTo(-0.81f, -0.15f, -0.84f, -0.19f, -0.84f, -0.24f)
    p.close()
    drawPath(p, col)
    clipPath(p) {
        drawRect(col.dim(0.84f), Offset(-1f, -0.27f), Size(2f, 0.2f))                 // lower shade
        drawRect(Color.White.alpha(0.18f), Offset(-1f, -0.66f), Size(2f, 0.05f))        // rim highlight
        if (k.style == KartStyle.SPORT) {
            drawRect(Color.White.night(n, 0.4f), Offset(-1f, -0.36f), Size(2f, 0.045f))
            drawRect(Color.White.night(n, 0.4f), Offset(-1f, -0.30f), Size(2f, 0.02f))
        }
        if (KartStep.roadster(rt.model)) {
            // The classic roadster's two-tone: a cream lower body under a chrome waistline.
            drawRect(Color(0xFFF6EBD2).night(n, 0.5f), Offset(-1f, -0.33f), Size(2f, 0.2f))
            drawRect(Color(0xFFE2D4B4).night(n, 0.5f), Offset(-1f, -0.21f), Size(2f, 0.08f))
            drawRect(CHROME.night(n, 0.35f), Offset(-1f, -0.345f), Size(2f, 0.026f))
            drawRect(Color.White.alpha(0.7f), Offset(-1f, -0.345f), Size(2f, 0.007f))
        }
    }
    if (KartStep.roadster(rt.model)) {
        // Chrome bumpers, a running board between the wheels, a star on the hood.
        drawRoundRect(CHROME.night(n, 0.35f), Offset(0.84f, -0.25f), Size(0.14f, 0.06f), CornerRadius(0.03f))
        drawRoundRect(CHROME.night(n, 0.35f), Offset(-0.97f, -0.26f), Size(0.14f, 0.06f), CornerRadius(0.03f))
        drawRoundRect(Color.White.alpha(0.7f), Offset(0.86f, -0.243f), Size(0.10f, 0.014f), CornerRadius(0.007f))
        drawRoundRect(Color.White.alpha(0.7f), Offset(-0.95f, -0.253f), Size(0.10f, 0.014f), CornerRadius(0.007f))
        drawRoundRect(CHROME.night(n, 0.35f), Offset(KG.REAR_WHEEL_X + 0.25f, -0.155f), Size(KG.FRONT_WHEEL_X - KG.REAR_WHEEL_X - 0.5f, 0.035f), CornerRadius(0.015f))
        drawLine(CHROME.night(n, 0.35f), Offset(0.70f, -0.62f), Offset(0.70f, -0.69f), strokeWidth = 0.014f)
        drawStar(rt, Offset(0.70f, -0.715f), 0.04f, Pal.star, Pal.starEdge)
    }
    drawPath(p, Pal.kartLine, style = Stroke(width = 0.024f))

    // Wheel arches
    val archCol = col.dim(0.62f)
    for (wx in floatArrayOf(KG.REAR_WHEEL_X, KG.FRONT_WHEEL_X)) {
        drawArc(archCol, 180f, 180f, true, Offset(wx - 0.245f, -KG.WHEEL_R - 0.245f), Size(0.49f, 0.49f))
        drawArc(Pal.kartLine, 180f, 180f, false, Offset(wx - 0.245f, -KG.WHEEL_R - 0.245f), Size(0.49f, 0.49f), style = Stroke(width = 0.02f))
    }

    // ECO leaf decal on the hood
    if (k.style == KartStyle.ECO) {
        rotate(-25f, Offset(0.62f, -0.50f)) {
            drawOval(Color(0xFF3F9A3A).night(n, 0.5f), Offset(0.56f, -0.53f), Size(0.13f, 0.06f))
            drawLine(Color.White.alpha(0.6f), Offset(0.565f, -0.50f), Offset(0.68f, -0.50f), strokeWidth = 0.008f)
        }
    }

    // Door emblem: Vroomi's sprout
    val em = Offset(0.04f, -0.34f)
    drawCircle(Pal.body.night(n, 0.5f), 0.085f, em)
    drawCircle(Pal.kartLine, 0.085f, em, style = Stroke(width = 0.016f))
    drawOval(Color.White, Offset(em.x - 0.055f, em.y - 0.03f), Size(0.05f, 0.028f))
    drawOval(Color.White, Offset(em.x + 0.005f, em.y - 0.045f), Size(0.05f, 0.028f))
    drawLine(Color.White, Offset(em.x, em.y - 0.01f), Offset(em.x, em.y + 0.045f), strokeWidth = 0.014f, cap = StrokeCap.Round)

    // 5-star meter (fills with each star; flashes rainbow on a combo)
    val fill = rt.meterFill()
    for (i in 0 until 5) {
        val c = Offset(-0.66f + i * 0.13f, -0.53f)
        val filled = i < fill
        val r = if (filled && i == fill - 1 && rt.collectPulse > 0f) 0.046f * (1f + rt.collectPulse * 1.2f) else 0.046f
        if (filled) {
            val c1 = if (rt.meterFlash > 0f) rainbow(rt.time * 2f + i * 0.2f) else Pal.star
            drawCircle(c1.alpha(0.35f), r * 1.6f, c)
            drawStar(rt, c, r, c1, Pal.starEdge)
        } else {
            drawStar(rt, c, r, col.dim(0.78f), Pal.kartLine.alpha(0.35f))
        }
    }

    // Lights
    val blink = blinkPulse(rt.time)
    val leftOn = k.blinker == Blinker.LEFT || k.blinker == Blinker.HAZARD
    val rightOn = k.blinker == Blinker.RIGHT || k.blinker == Blinker.HAZARD
    val anyBlink = (leftOn || rightOn)
    // headlight
    val hc = Offset(KG.HEAD_X + 0.01f, KG.HEAD_Y + 0.02f)
    val headOn = k.driving && (k.headlights || n > 0.6f)
    if (headOn) drawCircle(Pal.headlight.alpha(0.45f), 0.11f, hc)
    drawCircle(if (headOn) Pal.headlight else Color(0xFFFFF8E0).night(n, 0.4f), 0.045f, hc)
    drawCircle(Pal.kartLine, 0.045f, hc, style = Stroke(width = 0.012f))
    if (KartStep.roadster(rt.model)) drawCircle(CHROME.night(n, 0.35f), 0.064f, hc, style = Stroke(width = 0.018f))
    // tail / brake light
    val bg = rt.brakeGlow.value
    val tailCol = lerp(Pal.brakeOff, Pal.brakeOn, maxOf(bg, if (headOn) 0.35f else 0f))
    if (bg > 0.05f) drawCircle(Pal.brakeOn.alpha(0.40f * bg), 0.17f, Offset(-0.88f, -0.50f))
    drawRoundRect(tailCol, Offset(-0.905f, -0.57f), Size(0.05f, 0.13f), CornerRadius(0.02f))
    // blinkers (front + rear amber): which side is shown by the arrow sign, both lamps pulse
    if (anyBlink) {
        drawCircle(Pal.amber.alpha(0.45f * blink), 0.09f, Offset(0.85f, -0.25f))
        drawCircle(Pal.amber.alpha(0.45f * blink), 0.09f, Offset(-0.865f, -0.34f))
    }
    drawCircle(lerp(Color(0xFFB8862A), Pal.amber, if (anyBlink) blink else 0f), 0.032f, Offset(0.85f, -0.25f))
    drawCircle(lerp(Color(0xFFB8862A), Pal.amber, if (anyBlink) blink else 0f), 0.032f, Offset(-0.865f, -0.34f))

    // Wheels
    for (wx in floatArrayOf(KG.REAR_WHEEL_X, KG.FRONT_WHEEL_X)) drawWheel(rt, Offset(wx, -KG.WHEEL_R), n, k.style)

    // Low battery: a small battery above the hood that "breathes" (slow, never flashing)
    val pct = k.batteryPct
    if (t.mood == Mood.HUNGRY || k.charging) {
        val bc = Offset(0.62f, -1.0f)
        val a = if (k.charging) 1f else 0.6f + 0.4f * sin(rt.time * 2f)
        drawRoundRect(Color.White.alpha(0.9f * a), Offset(bc.x - 0.11f, bc.y - 0.06f), Size(0.22f, 0.12f), CornerRadius(0.025f))
        drawRoundRect(Pal.kartLine.alpha(a), Offset(bc.x - 0.11f, bc.y - 0.06f), Size(0.22f, 0.12f), CornerRadius(0.025f), style = Stroke(width = 0.014f))
        drawRect(Pal.kartLine.alpha(a), Offset(bc.x + 0.11f, bc.y - 0.025f), Size(0.02f, 0.05f))
        val level = if (k.charging) ((pct ?: 30) / 100f + (rt.time * 0.15f % 0.3f)).coerceIn(0.1f, 1f) else ((pct ?: 10) / 100f).coerceIn(0.08f, 1f)
        val lc = if (k.charging) Color(0xFF4CC38A) else Color(0xFFE5483F)
        drawRect(lc.alpha(a), Offset(bc.x - 0.095f, bc.y - 0.045f), Size(0.19f * level, 0.09f))
        if (k.charging) {
            val p2 = rt.tmpPath2
            p2.reset()
            p2.moveTo(bc.x + 0.01f, bc.y - 0.05f); p2.lineTo(bc.x - 0.03f, bc.y + 0.01f); p2.lineTo(bc.x, bc.y + 0.005f)
            p2.lineTo(bc.x - 0.01f, bc.y + 0.05f); p2.lineTo(bc.x + 0.035f, bc.y - 0.01f); p2.lineTo(bc.x + 0.005f, bc.y - 0.005f); p2.close()
            drawPath(p2, Color.White)
        }
    }
}

private fun DrawScope.drawWheel(rt: SceneRuntime, c: Offset, n: Float, style: KartStyle) {
    val r = KG.WHEEL_R
    drawWheelTeeth(rt, c, r)
    drawCircle(Pal.tire, r, c)
    drawCircle(Color.White.alpha(0.10f), r * 0.86f, c, style = Stroke(width = 0.012f))
    val hubCol = (if (style == KartStyle.SPORT) Color(0xFFFFD34D) else Pal.hub).night(n, 0.5f)
    drawCircle(hubCol, r * 0.55f, c)
    val spinRate = rt.speedFx.value
    val spokeA = 1f - (spinRate * 0.75f)
    rotate(rt.wheelAngle, c) {
        for (i in 0 until 5) {
            rotate(i * 72f, c) {
                drawLine(hubCol.dim(0.62f).alpha(spokeA), c, Offset(c.x, c.y - r * 0.48f), strokeWidth = 0.026f, cap = StrokeCap.Round)
            }
        }
    }
    if (spinRate > 0.3f) drawCircle(hubCol.dim(0.7f).alpha(0.35f * spinRate), r * 0.36f, c, style = Stroke(width = 0.03f))
    drawCircle(Pal.kartLine, r * 0.14f, c)
    if (KartStep.roadster(rt.model)) {
        // Chrome wire wheels on whitewall tyres.
        drawCircle(Color.White.night(n, 0.4f), r * 0.76f, c, style = Stroke(width = 0.045f))
        drawCircle(CHROME.night(n, 0.35f), r * 0.56f, c)
        rotate(rt.wheelAngle, c) {
            for (i in 0 until 10) rotate(i * 36f, c) { drawLine(Color(0xFF7E8A93).alpha(spokeA), c, Offset(c.x, c.y - r * 0.52f), strokeWidth = 0.012f) }
        }
        drawCircle(Color.White.alpha(0.55f), r * 0.50f, c, style = Stroke(width = 0.012f))
        drawCircle(Color(0xFF8E9AA3).night(n, 0.35f), r * 0.56f, c, style = Stroke(width = 0.016f))
        drawCircle(Pal.star.night(n, 0.3f), r * 0.16f, c)
        drawCircle(Pal.kartLine, r * 0.16f, c, style = Stroke(width = 0.012f))
    }
}

/** Chrome on the parade roadster (step 5). */
private val CHROME = Color(0xFFD5DEE4)

/** Windshield (wiper, raindrops, fog) and steering wheel: between Vroomi's body and the front arm. */
internal fun DrawScope.drawKartDash(rt: SceneRuntime, t: CharacterTarget, n: Float) {
    val k = t.kart
    // Steering column + wheel (seen from the side as an ellipse; a marker shows it turning)
    val wc = Offset(KG.STEER_X, KG.STEER_Y)
    drawLine(Color(0xFF4A535A), Offset(0.34f, -0.66f), Offset(wc.x + 0.02f, wc.y + 0.02f), strokeWidth = 0.032f, cap = StrokeCap.Round)
    rotate(-18f, wc) {
        drawOval(Color(0xFF39434A), Offset(wc.x - 0.05f, wc.y - 0.15f), Size(0.10f, 0.30f), style = Stroke(width = 0.04f))
        val th = Math.toRadians(rt.steer.value.toDouble() * 3.0)
        val mx = wc.x + 0.05f * sin(th).toFloat(); val my = wc.y - 0.15f * cos(th).toFloat()
        drawCircle(Pal.amber, 0.024f, Offset(mx, my))
    }

    // Windshield
    val p = rt.tmpPath
    p.reset()
    p.moveTo(0.34f, -0.64f); p.lineTo(0.48f, -0.62f); p.lineTo(0.36f, -1.00f); p.lineTo(0.25f, -1.02f); p.close()
    drawPath(p, Pal.glass.alpha(0.42f).night(n, 0.5f))
    // fog that Vroomi wipes away with the defroster (and frosty edges when it is cold)
    val fog = if (t.action == io.vroomi.character.Action.DEFROST_WIPE) (1f - rt.actionAge / 3f).coerceIn(0f, 1f) * 0.85f
    else if (t.mood == Mood.COLD) 0.25f else 0f
    if (fog > 0.01f) drawPath(p, Color.White.alpha(fog))
    if (rt.rain.value > 0.1f) {
        val drops = arrayOf(Offset(0.36f, -0.72f), Offset(0.41f, -0.68f), Offset(0.32f, -0.86f), Offset(0.38f, -0.80f), Offset(0.30f, -0.95f))
        for ((i, d) in drops.withIndex()) {
            val life = ((rt.time * 0.7f + i * 0.23f) % 1f)
            drawCircle(Color.White.alpha(0.55f * rt.rain.value * (1f - life)), 0.012f + 0.006f * (i % 2), d)
        }
    }
    drawLine(Color.White.alpha(0.55f), Offset(0.40f, -0.70f), Offset(0.335f, -0.92f), strokeWidth = 0.014f, cap = StrokeCap.Round)
    drawPath(p, Pal.kartLine, style = Stroke(width = 0.022f))
    // wiper
    if (k.wipers) {
        val u = (sin(rt.time * PI.toFloat() * 1.4f) + 1f) / 2f
        val ang = Math.toRadians((185f + 65f * u).toDouble())
        val pv = Offset(0.46f, -0.635f)
        drawLine(Color(0xFF2B2F33), pv, Offset(pv.x + 0.14f * cos(ang).toFloat(), pv.y + 0.14f * sin(ang).toFloat()), strokeWidth = 0.016f, cap = StrokeCap.Round)
    }
}

/** Turn arrow sign above the hood (↰ / ↱), pulsing with the blinker. */
internal fun DrawScope.drawTurnSign(rt: SceneRuntime, t: CharacterTarget) {
    val b = t.kart.blinker
    if (b != Blinker.LEFT && b != Blinker.RIGHT) return
    val c = Offset(0.66f, -1.30f)
    val pulse = blinkPulse(rt.time)
    val sz = 0.34f
    drawRoundRect(Pal.amber.alpha(0.25f + 0.25f * pulse), Offset(c.x - sz / 2 - 0.03f, c.y - sz / 2 - 0.03f), Size(sz + 0.06f, sz + 0.06f), CornerRadius(0.09f))
    drawRoundRect(lerp(Color(0xFFFFC966), Pal.amber, pulse), Offset(c.x - sz / 2, c.y - sz / 2), Size(sz, sz), CornerRadius(0.07f))
    val dir = if (b == Blinker.LEFT) -1f else 1f
    val sw = 0.045f
    val shaftX = c.x - dir * 0.05f
    drawLine(Color.White, Offset(shaftX, c.y + 0.12f), Offset(shaftX, c.y - 0.03f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(shaftX, c.y - 0.03f), Offset(c.x + dir * 0.06f, c.y - 0.03f), strokeWidth = sw, cap = StrokeCap.Round)
    val p = rt.tmpPath2
    p.reset()
    p.moveTo(c.x + dir * 0.13f, c.y - 0.03f)
    p.lineTo(c.x + dir * 0.05f, c.y - 0.10f)
    p.lineTo(c.x + dir * 0.05f, c.y + 0.04f)
    p.close()
    drawPath(p, Color.White)
}

internal fun rainbow(x: Float): Color {
    val cols = arrayOf(Color(0xFFFF6B6B), Color(0xFFFFB020), Color(0xFFFFE066), Color(0xFF6CC24A), Color(0xFF4DABF7), Color(0xFF9775FA))
    val f = ((x % 1f) + 1f) % 1f * cols.size
    val i = f.toInt() % cols.size
    return lerp(cols[i], cols[(i + 1) % cols.size], f - f.toInt())
}

/** Draws [block] in kart space: translate to the contact point, pitch around the axle line, scale to s units. */
internal inline fun DrawScope.inKartSpace(cx: Float, cy: Float, s: Float, pitchDeg: Float, crossinline block: DrawScope.() -> Unit) {
    withTransform({
        translate(cx, cy)
        rotate(pitchDeg, Offset(0f, -KG.WHEEL_R * s))
        scale(s, s, Offset.Zero)
    }) { block() }
}

