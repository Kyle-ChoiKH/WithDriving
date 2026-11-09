package io.vroomi.character.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import io.vroomi.character.Accessory
import io.vroomi.character.Action
import io.vroomi.character.CharacterSkin
import io.vroomi.character.CharacterTarget
import io.vroomi.character.Gait
import io.vroomi.character.Mood
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Vroomi itself, in px around an origin: the feet contact point when standing, the seat point when
 * driving. Split in three passes so the kart can be layered in between (back arm / body / front arm).
 */
internal class VroomiGeo(val s: Float) {
    val bw = s * 0.87f
    val bh = s
}

private val PI_F = PI.toFloat()

/** Body centre y relative to the origin. */
internal fun SceneRuntime.bodyCy(t: CharacterTarget, s: Float, seated: Boolean): Float {
    val footH = s * 0.12f
    val base = if (seated) -s / 2f else -footH * 0.6f - s / 2f
    if (t.still) return base
    val breathe = sin(time * 2f * PI_F * 0.25f) * s * 0.015f
    val run = if (seated) 0f else -abs(sin(phase)) * s * 0.05f * stride.value
    val celebrate = if (t.action == Action.CELEBRATE || t.action == Action.SPIN) -abs(sin(time * 7f)) * s * (if (seated) 0.10f else 0.18f) else 0f
    val hop = -hopY * s * 0.22f
    val hum = if (seated && (fidget == Fidget.HUM || fidget == Fidget.DRUM)) -abs(sin(time * 2f * PI_F * 1.6f)) * s * 0.02f else 0f
    return base + breathe + run + celebrate + hop + hum
}

/** Rotation for the whole body: lean, plus the stand-up tumble (seated tumble is a forward bonk = lean). */
internal fun SceneRuntime.bodyRotation(t: CharacterTarget, seated: Boolean): Float {
    val tumble = if (!seated && t.action == Action.TUMBLE && actionAge < 1.2f) (actionAge / 1.2f) * 360f else 0f
    return lean.value + tumble
}

/** Arm target: either an absolute point (px, origin-relative, unrotated frame) or an angle from the shoulder. */
private class ArmGoal { var point: Offset? = null; var angleDeg = 0f; var len = 0f; var prop = 0 }

private val goalF = ArmGoal()
private val goalB = ArmGoal()

private fun SceneRuntime.armGoals(t: CharacterTarget, s: Float, seated: Boolean) {
    goalF.point = null; goalB.point = null; goalF.prop = 0; goalB.prop = 0
    goalF.len = s * 0.22f; goalB.len = s * 0.22f
    val swingF = sin(phase + PI_F) * 38f * stride.value
    val swingB = sin(phase) * 38f * stride.value
    val wave = 150f + 25f * sin(time * 9f)
    // Steering grips (origin-relative): wheel centre is at kart (STEER_X, STEER_Y), seat at (SEAT_X, SEAT_Y).
    val gx = (KG.STEER_X - KG.SEAT_X) * s
    val gy = (KG.STEER_Y - KG.SEAT_Y) * s
    val drum = if (fidget == Fidget.DRUM) s * 0.03f else 0f
    val gripF = Offset(gx - s * 0.03f, gy - s * 0.10f - abs(sin(time * 9f)) * drum)
    val gripB = Offset(gx + s * 0.01f, gy - s * 0.04f - abs(cos(time * 9f)) * drum)

    fun ArmGoal.grip(p: Offset) { point = p }
    fun ArmGoal.ang(a: Float, l: Float = s * 0.24f) { point = null; angleDeg = a; len = l }

    if (seated) {
        goalF.grip(gripF); goalB.grip(gripB)
        val back = s * 0.34f // the far arm has to clear the body silhouette to be seen
        val waveB = -(128f + 22f * sin(time * 9f))
        when (t.action) {
            Action.TUMBLE -> if (actionAge < 0.9f) { goalF.ang(120f + 30f * sin(time * 20f)); goalB.ang(-120f - 30f * sin(time * 20f), back) }
            Action.POINT_LEFT -> goalB.ang(-138f + 6f * sin(time * 2f * PI_F * 0.75f), back)
            Action.POINT_RIGHT -> goalF.ang(150f + 6f * sin(time * 2f * PI_F * 0.75f), s * 0.26f)
            Action.HAZARD_WAVE -> { goalF.ang(140f + 18f * sin(time * 4f)); goalB.ang(-135f - 18f * sin(time * 4f), back) }
            Action.WAVE_HELLO, Action.WAVE_BYE -> goalB.ang(waveB, back)
            Action.CELEBRATE, Action.SPIN -> { goalF.ang(155f + 10f * sin(time * 8f)); goalB.ang(-148f - 10f * sin(time * 8f), back) }
            Action.DEFROST_WIPE -> {
                goalF.point = Offset(gx + s * 0.22f + s * 0.04f * sin(time * 7f), gy - s * 0.12f + s * 0.10f * sin(time * 7f + 1f))
                goalF.prop = 1
            }
            Action.STRETCH -> { goalF.ang(172f); goalB.ang(-160f, back) }
            Action.LOOK_AHEAD -> goalF.ang(165f, s * 0.20f)
            Action.THUMBS_UP -> { goalF.ang(125f); goalF.prop = 2 }
            else -> when (fidget) {
                Fidget.LOOK_BACK, Fidget.BIRD -> goalB.ang(waveB, back)
                Fidget.LOOK_UP -> goalB.ang(-150f + 5f * sin(time * 3f), back)
                Fidget.YAWN -> if (fidgetT < fidgetDur * 0.6f) { goalB.ang(-160f, back); goalF.ang(170f) }
                else -> Unit
            }
        }
        return
    }
    // Standing (parked, or hopping in/out)
    val stand = when (t.action) {
        Action.POINT_RIGHT -> swingB * 0.3f to 92f + 6f * sin(time * 2f * PI_F * 0.75f)
        Action.POINT_LEFT -> -92f - 6f * sin(time * 2f * PI_F * 0.75f) to swingF * 0.3f
        Action.HAZARD_WAVE -> -140f - 18f * sin(time * 4f) to 140f + 18f * sin(time * 4f)
        Action.WAVE_HELLO, Action.WAVE_BYE -> -10f to wave
        Action.CELEBRATE, Action.SPIN -> -155f to 155f
        Action.THUMBS_UP -> -15f to 125f
        Action.LOOK_AHEAD -> -12f to 165f
        Action.DEFROST_WIPE -> -20f to 168f + 18f * sin(time * 6f)
        Action.EAT_ENERGY -> -15f to 120f + 10f * sin(time * 8f)
        Action.STRETCH -> -172f to 172f
        Action.NAP -> -8f to 8f
        Action.SKID -> -60f to 60f
        Action.TUMBLE -> -110f to 110f
        Action.BLOW -> -25f to 30f
        else -> when (fidget) {
            Fidget.LOOK_BACK -> -10f to wave
            Fidget.LOOK_UP -> -12f to 160f
            Fidget.HOP -> -60f to 60f
            else -> swingB - 6f to swingF + 6f
        }
    }
    val (b, f) = if (t.still) -6f to 6f else stand
    goalB.ang(b); goalF.ang(f)
    if (t.action == Action.THUMBS_UP) goalF.prop = 2
    if (t.action == Action.DEFROST_WIPE) goalF.prop = 1
    if (t.action == Action.LOOK_AHEAD && t.still.not()) goalF.len = s * 0.17f
}

private fun rotatePt(x: Float, y: Float, deg: Float): Offset {
    val r = Math.toRadians(deg.toDouble())
    val c = cos(r).toFloat(); val sn = sin(r).toFloat()
    return Offset(x * c - y * sn, x * sn + y * c)
}

/** Shoulder position (origin-relative, after lean) for the seated pose. */
private fun SceneRuntime.shoulder(t: CharacterTarget, s: Float, back: Boolean, seated: Boolean): Offset {
    val g = VroomiGeo(s)
    val cy = bodyCy(t, s, seated)
    // Gripping the wheel the back arm starts mid-body (mostly hidden); raised or waving it starts at the
    // back edge so the gesture is visible.
    val local = when {
        !back -> Offset(g.bw * 0.32f, cy + s * 0.08f)
        goalB.point != null -> Offset(g.bw * 0.04f, cy + s * 0.06f)
        else -> Offset(-g.bw * 0.36f, cy + s * 0.06f)
    }
    return rotatePt(local.x * squashX.value, local.y * squashY.value, bodyRotation(t, seated))
}

private fun DrawScope.drawArmLine(start: Offset, end: Offset, s: Float, body: Color, outline: Color, back: Boolean) {
    val wOut = s * 0.10f; val wIn = s * 0.075f
    val col = if (back) body.dim(0.88f) else body
    drawLine(outline, start, end, strokeWidth = wOut, cap = StrokeCap.Round)
    drawLine(col, start, end, strokeWidth = wIn, cap = StrokeCap.Round)
}

/** Pass 1: the back arm (behind the body). */
internal fun DrawScope.drawVroomiBackArm(rt: SceneRuntime, t: CharacterTarget, ox: Float, oy: Float, s: Float, n: Float, seated: Boolean) {
    if (!seated) return // standing: drawn inside the body pass
    rt.armGoals(t, s, true)
    val sc = skinColors(rt.skin)
    val body = sc.arm.night(n, 0.25f)
    val sh = rt.shoulder(t, s, back = true, seated = true)
    val end = goalB.point ?: endFromAngle(sh, goalB.angleDeg - rt.lean.value, goalB.len)
    drawArmLine(Offset(ox + sh.x, oy + sh.y), Offset(ox + end.x, oy + end.y), s, body, sc.outline, back = true)
}

/** Pass 3: the front arm (over the steering wheel). */
internal fun DrawScope.drawVroomiFrontArm(rt: SceneRuntime, t: CharacterTarget, ox: Float, oy: Float, s: Float, n: Float, seated: Boolean) {
    if (!seated) return
    rt.armGoals(t, s, true)
    val sc = skinColors(rt.skin)
    val body = sc.arm.night(n, 0.25f)
    val sh = rt.shoulder(t, s, back = false, seated = true)
    val end = goalF.point ?: endFromAngle(sh, goalF.angleDeg - rt.lean.value, goalF.len)
    val a = Offset(ox + sh.x, oy + sh.y); val e = Offset(ox + end.x, oy + end.y)
    drawArmLine(a, e, s, body, sc.outline, back = false)
    drawHandProp(t, e, s, goalF.prop)
}

private fun endFromAngle(sh: Offset, deg: Float, len: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset(sh.x + (sin(a) * len).toFloat(), sh.y + (cos(a) * len).toFloat())
}

private fun DrawScope.drawHandProp(t: CharacterTarget, e: Offset, s: Float, prop: Int) {
    when (prop) {
        1 -> drawRoundRect(Pal.paper, Offset(e.x - s * 0.06f, e.y - s * 0.05f), Size(s * 0.12f, s * 0.09f), CornerRadius(s * 0.02f))
        2 -> drawCircle(Pal.outline, s * 0.022f, Offset(e.x, e.y - s * 0.045f))
    }
}

/** Pass 2: body, face, accessories (and, standing, legs and both arms). */
internal fun DrawScope.drawVroomiBody(rt: SceneRuntime, t: CharacterTarget, ox: Float, oy: Float, s: Float, n: Float, seated: Boolean) {
    val g = VroomiGeo(s)
    val bw = g.bw; val bh = g.bh
    val dimF = 1f - 0.15f * n
    val skin = rt.skin
    val sc = skinColors(skin)
    val body = sc.body.dim(dimF); val outline = sc.outline
    val arm = sc.arm.dim(dimF)
    val footH = s * 0.12f
    val cy = rt.bodyCy(t, s, seated)
    val stroke = max(3f, s * 0.022f)
    val shiver = if (t.mood == Mood.COLD && !t.still) sin(rt.time * 38f) * s * 0.004f else 0f
    val pivot = if (seated) Offset.Zero else Offset(0f, cy)

    withTransform({
        translate(ox + shiver, oy)
        rotate(rt.bodyRotation(t, seated), pivot)
        scale(rt.squashX.value, rt.squashY.value, Offset.Zero)
    }) {
        if (!seated) {
            // Feet
            val st = rt.stride.value
            val lift = s * 0.06f
            for (side in 0..1) {
                val ph = rt.phase + if (side == 0) 0f else PI_F
                val fx = (if (side == 0) -s * 0.17f else s * 0.15f) + sin(ph) * s * 0.16f * st +
                    (if (t.action == Action.SKID) s * 0.10f else 0f)
                val fy = -footH / 2f - max(0f, cos(ph)) * lift * st - rt.hopY * s * 0.22f
                drawOval(outline, Offset(fx - s * 0.14f, fy - s * 0.08f), Size(s * 0.28f, s * 0.16f))
                drawOval(sc.foot.dim(dimF), Offset(fx - s * 0.12f, fy - s * 0.065f), Size(s * 0.24f, s * 0.13f))
                drawOval(sc.sole, Offset(fx - s * 0.10f, fy + s * 0.01f), Size(s * 0.20f, s * 0.045f))
                if (rt.glow.value > 0.05f) drawCircle(sc.sole.copy(alpha = 0.35f * rt.glow.value), s * 0.11f, Offset(fx, fy + s * 0.03f))
            }
            rt.armGoals(t, s, false)
            drawStandingArm(rt, t, back = true, bw, cy, s, arm, outline)
        }
        if (skin == CharacterSkin.VROOMI) {
            drawPropeller(rt, -bw * 0.5f, cy + bh * 0.08f, s, outline, body)
            drawSails(rt, t, bw, cy, bh, s, body, outline)
        }
        drawSkinBehind(rt, t, skin, sc, cy, bw, bh, s, n)
        drawSkinBody(rt, skin, sc, body, cy, bw, bh, stroke)
        drawSkinMarkings(rt, t, rt.lastOptions, skin, sc, cy, bw, bh, s, rt.faceTurn.value.coerceIn(0f, 1f), n)
        drawSkinTop(rt, skin, sc, cy, bw, bh, s)
        if (Accessory.BLANKET in t.accessories && !seated) drawBlanket(bw, cy, bh)
        if (Accessory.RAINCOAT in t.accessories) drawRaincoat(bw, cy, bh, stroke)
        if (Accessory.SCARF in t.accessories) drawScarf(rt, bw, cy, bh, s)
        drawFace(rt, t, bw, cy, bh, s)
        if (Accessory.BEANIE in t.accessories) drawBeanie(bw, cy, bh, s)
        if (Accessory.GOGGLES in t.accessories) drawGoggles(rt, bw, cy, bh, s)
        if (Accessory.SUNGLASSES in t.accessories && Accessory.GOGGLES !in t.accessories) drawSunglasses(rt, bw, cy, bh, s)
        if (Accessory.MAP_HAT in t.accessories) drawMapHat(cy, bh, s)
        if (Accessory.LEAF in t.accessories) drawLeaf(rt, cy, bh, s)
        if (!seated) drawStandingArm(rt, t, back = false, bw, cy, s, arm, outline)
        if (t.mood == Mood.HUNGRY) drawTummyRumble(rt, bw, cy, bh, s)
        if (t.mood == Mood.DIZZY) drawDizzyStars(rt, cy, bh, s)
    }
}

private fun DrawScope.drawStandingArm(rt: SceneRuntime, t: CharacterTarget, back: Boolean, bw: Float, cy: Float, s: Float, body: Color, outline: Color) {
    val goal = if (back) goalB else goalF
    val sx = if (back) -bw * 0.34f else bw * 0.33f
    val sy = cy + s * 0.08f
    val end = endFromAngle(Offset(sx, sy), goal.angleDeg, goal.len)
    drawArmLine(Offset(sx, sy), end, s, body, outline, back)
    if (!back) {
        drawHandProp(t, end, s, goal.prop)
        if (Accessory.LANTERN in t.accessories && t.action == Action.NONE) drawLantern(end.x, end.y, s)
    }
}

internal fun egg(p: Path, cx: Float, cy: Float, w: Float, h: Float) {
    val top = cy - h / 2f; val bot = cy + h / 2f; val r = w / 2f
    p.reset()
    p.moveTo(cx, top)
    p.cubicTo(cx + r * 0.62f, top, cx + r, cy - h * 0.12f, cx + r, cy + h * 0.10f)
    p.cubicTo(cx + r, bot - h * 0.08f, cx + r * 0.55f, bot, cx, bot)
    p.cubicTo(cx - r * 0.55f, bot, cx - r, bot - h * 0.08f, cx - r, cy + h * 0.10f)
    p.cubicTo(cx - r, cy - h * 0.12f, cx - r * 0.62f, top, cx, top)
    p.close()
}

private fun DrawScope.drawSails(rt: SceneRuntime, t: CharacterTarget, bw: Float, cy: Float, bh: Float, s: Float, body: Color, outline: Color) {
    val top = cy - bh / 2f
    val speedF = (t.gaitSpeedKmh / 110f).coerceIn(0f, 1f)
    val droop = if (t.gait == Gait.IDLE) (if (t.action == Action.NAP) 38f else 14f) else 0f
    val stream = speedF * 32f + rt.wind.value * 28f
    val flutter = sin(rt.sailT) * (3f + 9f * (speedF + rt.wind.value))
    val len = s * 0.32f; val wid = s * 0.13f
    for (i in 0..1) {
        val rx = if (i == 0) -bw * 0.13f else bw * 0.12f
        val ry = top + bh * (if (i == 0) 0.07f else 0.05f)
        val base = if (i == 0) -22f else 6f
        val droopSigned = if (i == 0) -droop else droop * 0.6f
        val ang = base - stream + flutter * (if (i == 0) 1f else 0.8f) + droopSigned
        rotate(ang, Offset(rx, ry)) {
            val lp = rt.leafPath
            lp.reset()
            lp.moveTo(rx, ry)
            lp.cubicTo(rx - wid, ry - len * 0.35f, rx - wid * 0.6f, ry - len * 0.9f, rx, ry - len)
            lp.cubicTo(rx + wid * 0.6f, ry - len * 0.9f, rx + wid, ry - len * 0.35f, rx, ry)
            lp.close()
            drawPath(lp, body)
            drawPath(lp, outline, style = Stroke(width = max(2.5f, s * 0.018f)))
            drawLine(outline.copy(alpha = 0.5f), Offset(rx, ry - len * 0.15f), Offset(rx, ry - len * 0.8f), strokeWidth = max(1.5f, s * 0.008f))
        }
    }
}

private fun DrawScope.drawPropeller(rt: SceneRuntime, x: Float, y: Float, s: Float, outline: Color, body: Color) {
    val r = s * 0.075f
    rotate(-rt.propSpin, Offset(x, y)) {
        drawArc(body, 0f, 270f, false, Offset(x - r, y - r), Size(r * 2, r * 2), style = Stroke(width = s * 0.045f, cap = StrokeCap.Round))
        drawArc(outline, 0f, 270f, false, Offset(x - r, y - r), Size(r * 2, r * 2), style = Stroke(width = max(2f, s * 0.012f), cap = StrokeCap.Round))
        drawCircle(outline, s * 0.018f, Offset(x + r, y))
    }
}

private fun DrawScope.drawFace(rt: SceneRuntime, t: CharacterTarget, bw: Float, cy: Float, bh: Float, s: Float) {
    val turn = rt.faceTurn.value.coerceIn(0f, 1f)
    val up = rt.lookUp.value.coerceIn(0f, 1f)
    val eyeY = cy - bh * 0.08f - up * s * 0.035f
    val eyeR = s * 0.085f
    val e1 = lerp1(bw * 0.02f, -bw * 0.15f, turn); val e2 = lerp1(bw * 0.27f, bw * 0.15f, turn)
    val closed = rt.blink > 0f
    val look = when (t.action) { Action.LOOK_AHEAD -> s * 0.02f; Action.POINT_LEFT -> -s * 0.02f; else -> 0f }
    val yawning = rt.fidget == Fidget.YAWN && rt.fidgetT < rt.fidgetDur * 0.7f
    val sleepy = t.mood == Mood.SLEEPY || t.action == Action.NAP || yawning
    val happy = t.mood == Mood.HAPPY || rt.fidget == Fidget.HUM || t.action == Action.SPIN || t.action == Action.CELEBRATE
    val sc = skinColors(rt.skin)
    val eyeCol = sc.eye
    val led = rt.skin == CharacterSkin.NUT
    for (ex in floatArrayOf(e1, e2)) {
        val c = Offset(ex + look, eyeY)
        if (led) drawCircle(eyeCol.alpha(0.25f), eyeR * 1.5f, c)
        when {
            t.mood == Mood.DIZZY -> {
                drawCircle(eyeCol, eyeR * 0.9f, c, style = Stroke(width = s * 0.016f))
                rotate(rt.time * 360f, c) { drawArc(eyeCol, 0f, 220f, false, Offset(c.x - eyeR * 0.45f, c.y - eyeR * 0.45f), Size(eyeR * 0.9f, eyeR * 0.9f), style = Stroke(width = s * 0.016f)) }
            }
            sleepy || closed && !t.still ->
                drawArc(eyeCol, 20f, 140f, false, Offset(c.x - eyeR, c.y - eyeR * 0.6f), Size(eyeR * 2, eyeR * 1.2f), style = Stroke(width = s * 0.02f, cap = StrokeCap.Round))
            happy ->
                drawArc(eyeCol, 200f, 140f, false, Offset(c.x - eyeR, c.y - eyeR * 0.3f), Size(eyeR * 2, eyeR * 1.4f), style = Stroke(width = s * 0.024f, cap = StrokeCap.Round))
            else -> {
                val ry = if (t.mood == Mood.HUNGRY) eyeR * 0.7f else eyeR * 1.1f
                val wide = if (t.action == Action.SKID || t.action == Action.BURST) 1.12f else 1f
                drawOval(eyeCol, Offset(c.x - eyeR * wide, c.y - ry * wide), Size(eyeR * 2 * wide, ry * 2 * wide))
                if (!led) drawCircle(Color.White, eyeR * 0.32f, Offset(c.x - eyeR * 0.3f, c.y - ry * (0.35f + up * 0.2f)))
                if (up > 0.3f) drawCircle(Color.White.alpha(up), eyeR * 0.14f, Offset(c.x + eyeR * 0.3f, c.y + ry * 0.2f))
            }
        }
    }
    if (!led && (rt.blushA.value > 0.01f || turn > 0.3f)) {
        val a = maxOf(rt.blushA.value, turn * 0.2f)
        drawOval(Pal.blush.copy(alpha = a), Offset(lerp1(bw * 0.30f, bw * 0.22f, turn), cy), Size(s * 0.12f, s * 0.06f))
        drawOval(Pal.blush.copy(alpha = a), Offset(lerp1(-bw * 0.10f, -bw * 0.34f, turn), cy), Size(s * 0.10f, s * 0.06f))
    }
    val mx = lerp1(bw * 0.16f, 0f, turn); val my = cy + bh * 0.07f
    if (rt.skin == CharacterSkin.EOLEUM) {
        val open = t.action == Action.BLOW || t.action == Action.BURST || t.action == Action.SPIN || t.action == Action.CELEBRATE ||
            t.action == Action.EAT_ENERGY || rt.fidget == Fidget.YAWN || rt.wind.value > 0.4f && !t.still
        drawBeak(rt, mx, my, s, open)
        return
    }
    when {
        yawning -> {
            val open = sin((rt.fidgetT / (rt.fidgetDur * 0.7f)).coerceIn(0f, 1f) * PI_F)
            drawOval(eyeCol, Offset(mx - s * 0.04f, my - s * 0.02f), Size(s * 0.08f, s * 0.03f + s * 0.07f * open))
        }
        t.action == Action.BLOW || rt.wind.value > 0.4f && !t.still -> drawCircle(eyeCol, s * 0.032f, Offset(mx + s * 0.04f, my))
        t.action == Action.EAT_ENERGY -> {
            val open = 0.5f + 0.5f * sin(rt.time * 8f)
            drawOval(eyeCol, Offset(mx - s * 0.03f, my - s * 0.02f * open), Size(s * 0.07f, s * 0.04f * open + 2f))
        }
        t.action == Action.BURST || t.action == Action.SPIN || t.action == Action.CELEBRATE -> {
            drawArc(eyeCol, 0f, 180f, true, Offset(mx - s * 0.05f, my - s * 0.03f), Size(s * 0.10f, s * 0.07f))
            drawArc(Color(0xFFFF8F8F), 30f, 120f, true, Offset(mx - s * 0.03f, my - s * 0.005f), Size(s * 0.06f, s * 0.04f))
        }
        rt.fidget == Fidget.LOOK_UP -> drawCircle(eyeCol, s * 0.022f, Offset(mx, my))
        t.mood == Mood.DIZZY || t.action == Action.SKID ->
            drawLine(eyeCol, Offset(mx - s * 0.04f, my), Offset(mx + s * 0.05f, my + s * 0.01f), strokeWidth = s * 0.016f, cap = StrokeCap.Round)
        else -> {
            val smile = if (happy) 1.4f else 1f
            drawArc(eyeCol, 20f, 140f, false, Offset(mx - s * 0.05f, my - s * 0.04f * smile), Size(s * 0.10f, s * 0.06f * smile), style = Stroke(width = s * 0.016f, cap = StrokeCap.Round))
        }
    }
}

private fun lerp1(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun DrawScope.drawDizzyStars(rt: SceneRuntime, cy: Float, bh: Float, s: Float) {
    val c = Offset(0f, cy - bh * 0.62f)
    for (i in 0 until 3) {
        val a = rt.time * 4f + i * 2f * PI_F / 3f
        val p = Offset(c.x + cos(a) * s * 0.28f, c.y + sin(a) * s * 0.07f)
        drawStar(rt, p, s * 0.045f, Pal.star, Pal.starEdge)
    }
}

private fun DrawScope.drawLantern(x: Float, y: Float, s: Float) {
    drawLine(Pal.cable, Offset(x, y), Offset(x, y + s * 0.06f), strokeWidth = 2f)
    drawCircle(Pal.sole.copy(alpha = 0.28f), s * 0.13f, Offset(x, y + s * 0.11f))
    drawRoundRect(Pal.sparkle, Offset(x - s * 0.035f, y + s * 0.06f), Size(s * 0.07f, s * 0.09f), CornerRadius(s * 0.015f))
    drawRoundRect(Pal.cable, Offset(x - s * 0.035f, y + s * 0.06f), Size(s * 0.07f, s * 0.09f), CornerRadius(s * 0.015f), style = Stroke(width = 2f))
}

private fun DrawScope.drawScarf(rt: SceneRuntime, bw: Float, cy: Float, bh: Float, s: Float) {
    val y = cy + bh * 0.17f
    drawRoundRect(Pal.scarf, Offset(-bw * 0.47f, y - s * 0.04f), Size(bw * 0.94f, s * 0.08f), CornerRadius(s * 0.04f))
    val p = rt.tmpPath
    p.reset()
    val tx = -bw * 0.40f
    p.moveTo(tx, y)
    val flap = sin(rt.sailT * 1.3f) * s * 0.03f
    val stream = rt.speedFx.value * s * 0.08f
    p.cubicTo(tx - s * 0.12f, y + s * 0.02f + flap, tx - s * 0.20f - stream, y + s * 0.08f - flap - stream * 0.6f, tx - s * 0.26f - stream * 1.4f, y + s * 0.10f + flap - stream)
    drawPath(p, Pal.scarf, style = Stroke(width = s * 0.06f, cap = StrokeCap.Round))
}

private fun DrawScope.drawRaincoat(bw: Float, cy: Float, bh: Float, stroke: Float) {
    val top = cy - bh / 2f
    drawArc(Pal.raincoat, 180f, 180f, true, Offset(-bw * 0.52f, top - bh * 0.06f), Size(bw * 1.04f, bh * 0.62f))
    drawArc(Pal.outline, 180f, 180f, false, Offset(-bw * 0.52f, top - bh * 0.06f), Size(bw * 1.04f, bh * 0.62f), style = Stroke(width = stroke))
    drawOval(Pal.raincoat.copy(alpha = 0.92f), Offset(-bw * 0.49f, cy + bh * 0.12f), Size(bw * 0.98f, bh * 0.36f))
}

private fun DrawScope.drawBlanket(bw: Float, cy: Float, bh: Float) {
    drawOval(Pal.blanket, Offset(-bw * 0.50f, cy + bh * 0.16f), Size(bw * 1.0f, bh * 0.34f))
    for (i in 1..3) {
        val x = -bw * 0.5f + bw * i * 0.25f
        drawLine(Color.White.copy(alpha = 0.45f), Offset(x, cy + bh * 0.20f), Offset(x, cy + bh * 0.46f), strokeWidth = bw * 0.03f)
    }
}

/** Seat heating while driving: a blanket draped over the kart's side (drawn in kart space). */
internal fun DrawScope.drawKartBlanket(t: CharacterTarget) {
    if (Accessory.BLANKET !in t.accessories) return
    drawRoundRect(Pal.blanket, Offset(-0.74f, -0.70f), Size(0.62f, 0.20f), CornerRadius(0.06f))
    for (i in 1..3) drawLine(Color.White.alpha(0.5f), Offset(-0.74f + i * 0.155f, -0.69f), Offset(-0.74f + i * 0.155f, -0.51f), strokeWidth = 0.025f)
}

private fun DrawScope.drawBeanie(bw: Float, cy: Float, bh: Float, s: Float) {
    val top = cy - bh / 2f
    drawArc(Pal.beanie, 180f, 180f, true, Offset(-bw * 0.40f, top - bh * 0.02f), Size(bw * 0.80f, bh * 0.40f))
    drawRoundRect(Pal.beanie.dim(0.85f), Offset(-bw * 0.42f, top + bh * 0.15f), Size(bw * 0.84f, bh * 0.07f), CornerRadius(s * 0.03f))
    drawCircle(Color.White, s * 0.045f, Offset(0f, top - bh * 0.03f))
}

private fun DrawScope.drawGoggles(rt: SceneRuntime, bw: Float, cy: Float, bh: Float, s: Float) {
    val y = cy - bh * 0.30f
    drawLine(Pal.goggles, Offset(-bw * 0.48f, y), Offset(bw * 0.46f, y), strokeWidth = s * 0.03f)
    val turn = rt.faceTurn.value
    for (gxp in floatArrayOf(lerp1(bw * 0.04f, -bw * 0.13f, turn), lerp1(bw * 0.27f, bw * 0.13f, turn))) {
        drawCircle(Color(0xFF9FD6FF), s * 0.06f, Offset(gxp, y))
        drawCircle(Pal.goggles, s * 0.06f, Offset(gxp, y), style = Stroke(width = s * 0.02f))
    }
}

private fun DrawScope.drawSunglasses(rt: SceneRuntime, bw: Float, cy: Float, bh: Float, s: Float) {
    val turn = rt.faceTurn.value
    val y = cy - bh * 0.08f
    val xs = floatArrayOf(lerp1(bw * 0.02f, -bw * 0.15f, turn), lerp1(bw * 0.27f, bw * 0.15f, turn))
    drawLine(Color(0xFF1B2423), Offset(xs[0], y - s * 0.03f), Offset(xs[1], y - s * 0.03f), strokeWidth = s * 0.018f)
    drawLine(Color(0xFF1B2423), Offset(xs[0] - s * 0.09f, y - s * 0.03f), Offset(-bw * 0.40f, y - s * 0.05f), strokeWidth = s * 0.014f)
    for (x in xs) {
        drawRoundRect(Color(0xFF1B2423), Offset(x - s * 0.095f, y - s * 0.07f), Size(s * 0.19f, s * 0.12f), CornerRadius(s * 0.05f))
        drawLine(Color.White.alpha(0.55f), Offset(x - s * 0.05f, y - s * 0.045f), Offset(x - s * 0.01f, y - s * 0.045f), strokeWidth = s * 0.014f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawMapHat(cy: Float, bh: Float, s: Float) {
    val top = cy - bh / 2f
    rotate(-8f, Offset(0f, top)) {
        drawRect(Pal.paper, Offset(-s * 0.13f, top - s * 0.07f), Size(s * 0.26f, s * 0.10f))
        drawLine(Pal.outline.copy(alpha = 0.6f), Offset(-s * 0.04f, top - s * 0.07f), Offset(-s * 0.04f, top + s * 0.03f), strokeWidth = 2f)
        drawLine(Pal.outline.copy(alpha = 0.6f), Offset(s * 0.05f, top - s * 0.07f), Offset(s * 0.05f, top + s * 0.03f), strokeWidth = 2f)
        drawLine(Pal.scarf, Offset(-s * 0.11f, top - s * 0.01f), Offset(s * 0.10f, top - s * 0.04f), strokeWidth = 3f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawLeaf(rt: SceneRuntime, cy: Float, bh: Float, s: Float) {
    val top = cy - bh / 2f
    val p = rt.tmpPath
    p.reset()
    p.moveTo(0f, top + s * 0.01f)
    p.cubicTo(s * 0.05f, top - s * 0.04f, s * 0.12f, top - s * 0.05f, s * 0.15f, top - s * 0.10f)
    p.cubicTo(s * 0.08f, top - s * 0.12f, s * 0.02f, top - s * 0.07f, 0f, top + s * 0.01f)
    p.close()
    drawPath(p, Pal.leaf)
}

private fun DrawScope.drawTummyRumble(rt: SceneRuntime, bw: Float, cy: Float, bh: Float, s: Float) {
    val c = Offset(bw * 0.15f, cy + bh * 0.20f)
    rotate(rt.time * 90f, c) {
        drawArc(Pal.outline.copy(alpha = 0.5f), 0f, 270f, false, Offset(c.x - s * 0.04f, c.y - s * 0.04f), Size(s * 0.08f, s * 0.08f), style = Stroke(width = 2.5f))
    }
}

/** A small bird that flies along with the kart during the BIRD fidget (Vroomi waves at it). */
internal fun DrawScope.drawBird(rt: SceneRuntime, headX: Float, headY: Float, s: Float, n: Float) {
    if (rt.fidget != Fidget.BIRD) return
    val u = rt.fidgetT
    val dur = rt.fidgetDur
    val (bx, by) = when {
        u < 1.2f -> { val k = smooth01(u / 1.2f); lerp1(headX + s * 2.2f, headX + s * 0.75f, k) to lerp1(headY - s * 1.4f, headY - s * 0.55f, k) }
        u < dur - 1.2f -> (headX + s * 0.75f + sin(u * 2f) * s * 0.06f) to (headY - s * 0.55f + sin(u * 3.1f) * s * 0.05f)
        else -> { val k = smooth01((u - (dur - 1.2f)) / 1.2f); lerp1(headX + s * 0.75f, headX + s * 2.6f, k) to lerp1(headY - s * 0.55f, headY - s * 1.9f, k) }
    }
    val col = Color(0xFF6FB7E8).night(n, 0.4f)
    val flap = sin(rt.time * 14f) * s * 0.09f
    val r = s * 0.085f
    drawOval(col, Offset(bx - r, by - r * 0.7f), Size(r * 2f, r * 1.4f))
    drawCircle(col, r * 0.6f, Offset(bx + r * 0.9f, by - r * 0.5f))
    drawCircle(Pal.eye, r * 0.12f, Offset(bx + r * 1.1f, by - r * 0.6f))
    drawLine(Pal.sole, Offset(bx + r * 1.4f, by - r * 0.45f), Offset(bx + r * 1.8f, by - r * 0.4f), strokeWidth = r * 0.25f, cap = StrokeCap.Round)
    val p = rt.tmpPath2
    p.reset()
    p.moveTo(bx - r * 0.3f, by - r * 0.3f)
    p.lineTo(bx - r * 0.1f, by - r * 0.3f - r * 1.1f - flap)
    p.lineTo(bx + r * 0.5f, by - r * 0.3f)
    p.close()
    drawPath(p, col.dim(0.85f))
}
