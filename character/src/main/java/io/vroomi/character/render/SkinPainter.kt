package io.vroomi.character.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import io.vroomi.character.Action
import io.vroomi.character.CharacterSkin
import io.vroomi.character.CharacterSkin.BANEUL
import io.vroomi.character.CharacterSkin.BOOST
import io.vroomi.character.CharacterSkin.CHOCHIM
import io.vroomi.character.CharacterSkin.DOTOL
import io.vroomi.character.CharacterSkin.EOLEUM
import io.vroomi.character.CharacterSkin.MUNGE
import io.vroomi.character.CharacterSkin.NACHIM
import io.vroomi.character.CharacterSkin.NUT
import io.vroomi.character.CharacterSkin.ORGEL
import io.vroomi.character.CharacterSkin.PILA
import io.vroomi.character.CharacterSkin.PISTON
import io.vroomi.character.CharacterSkin.SSUK
import io.vroomi.character.CharacterSkin.TAEYEOP
import io.vroomi.character.CharacterSkin.TOPNI
import io.vroomi.character.CharacterSkin.VROOMI
import io.vroomi.character.CharacterTarget
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * The 14 friends beside Vroomi share Vroomi's rig (arms, face moods, accessories, kart) and differ in body
 * shape, colours and signature parts. Mechanical parts are driven by the car: speed, steering, acceleration.
 * All coordinates are inside the body transform: origin = feet/seat point, cy = body centre,
 * bw/bh = body box, s = body height.
 */

private val PI_F = PI.toFloat()

internal class SkinColors(
    val body: Color, val belly: Color, val outline: Color, val sole: Color, val arm: Color, val kart: Color,
    val eye: Color = Pal.eye, val foot: Color = body, val accent: Color = Pal.sole,
)

private val brass = Color(0xFFD7A945)
private val steel = Color(0xFFC6CDD5)

internal fun skinColors(skin: CharacterSkin): SkinColors = when (skin) {
    VROOMI -> SkinColors(Pal.body, Pal.belly, Pal.outline, Pal.sole, Pal.body, Pal.kartNormal)
    SSUK -> SkinColors(Color(0xFFA9D18E), Color(0xFFEFF6E2), Color(0xFF4E6B3A), Color(0xFFE9B98F), Color(0xFFA9D18E), Color(0xFFF4A6B8))
    EOLEUM -> SkinColors(Color(0xFF2F4B6E), Color.White, Color(0xFF14243A), Color(0xFFFF9F43), Color(0xFF2F4B6E), Color(0xFF7CC4E8), foot = Color(0xFFFF9F43))
    MUNGE -> SkinColors(Color(0xFFF7FBFF), Color(0xFFE3EEFA), Color(0xFF6E8BB3), Color(0xFFBFD5EE), Color(0xFFF7FBFF), Color(0xFF8EC9F0))
    DOTOL -> SkinColors(Color(0xFFC07A3C), Color(0xFFF2D3A6), Color(0xFF5A3417), Color(0xFF8A5527), Color(0xFFC07A3C), Color(0xFF5C8A3A), accent = Color(0xFF8A5A2E))
    NUT -> SkinColors(Color(0xFFB7C4D3), Color(0xFF26313D), Color(0xFF3B4A5A), Color(0xFF6B7C8F), Color(0xFF9AAABB), Color(0xFF5E7FA3), eye = Color(0xFF7FF0FF), foot = Color(0xFF7D8D9F))
    CHOCHIM -> SkinColors(Color(0xFFD5DBE1), Color(0xFFFFF8EA), Color(0xFF3A4350), Color(0xFF8B5E3C), Color(0xFFC3CAD2), Color(0xFFD9A441), foot = Color(0xFF8B5E3C))
    PISTON -> SkinColors(Color(0xFF7D8A96), Color(0xFF5B6670), Color(0xFF2C343B), Color(0xFF3A4249), Color(0xFF8E9AA5), Color(0xFFD8443A), foot = Color(0xFF4A535C), accent = Color(0xFFD8443A))
    BOOST -> SkinColors(Color(0xFFCBD1D8), Color(0xFFE9EDF1), Color(0xFF34404D), Color(0xFF3D7BD9), Color(0xFFB4BCC5), Color(0xFF3D7BD9), foot = Color(0xFF3D7BD9), accent = Color(0xFF3D7BD9))
    TOPNI -> SkinColors(brass, Color(0xFFF6E3A8), Color(0xFF6A4A12), Color(0xFF9C7424), Color(0xFFCDA040), Color(0xFFA8672E), foot = Color(0xFF9C7424))
    BANEUL -> SkinColors(Color(0xFF2E353D), Color(0xFFFFFBF0), Color(0xFF15191E), Color(0xFFF28C28), Color(0xFF59636E), Color(0xFFF28C28), foot = Color(0xFF3B434C), accent = Color(0xFFF28C28))
    TAEYEOP -> SkinColors(Color(0xFFD9534F), Color(0xFFF4E3C3), Color(0xFF6B1F1C), Color(0xFF3A6EA5), Color(0xFFD9534F), Color(0xFF4FB3A9), foot = Color(0xFF3A6EA5), accent = steel)
    ORGEL -> SkinColors(Color(0xFFB07A4F), Color(0xFF7A4E2E), Color(0xFF4A2B15), Color(0xFF5E3A20), Color(0xFFB07A4F), Color(0xFF9B3B4A), foot = Color(0xFF5E3A20), accent = brass)
    PILA -> SkinColors(Color(0xFFFFF4D6), Color(0xFFFFF4D6), Color(0xFF7A6234), Color(0xFFB59440), Color(0xFFE8DDBF), Color(0xFF2E6B4F), foot = Color(0xFFB59440), accent = brass)
    NACHIM -> SkinColors(Color(0xFFC9A25A), Color(0xFFFFF8E6), Color(0xFF5A4318), Color(0xFF8A6A2E), Color(0xFFC9A25A), Color(0xFF2F4F7A), foot = Color(0xFF8A6A2E))
}

/** A gear outline (flat-topped teeth) centred at [c]. */
internal fun gearPath(p: Path, c: Offset, rOut: Float, rIn: Float, teeth: Int, angleDeg: Float) {
    p.reset()
    val step = 2f * PI_F / teeth
    val a0 = angleDeg * PI_F / 180f
    for (i in 0 until teeth) {
        val a = a0 + i * step
        val pts = floatArrayOf(a, a + step * 0.15f, a + step * 0.45f, a + step * 0.6f)
        val rs = floatArrayOf(rIn, rOut, rOut, rIn)
        for (k in 0 until 4) {
            val x = c.x + cos(pts[k]) * rs[k]; val y = c.y + sin(pts[k]) * rs[k]
            if (i == 0 && k == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
    }
    p.close()
}

/** Body silhouette for the skin, written into [p]. */
internal fun skinBodyPath(rt: SceneRuntime, skin: CharacterSkin, p: Path, cy: Float, bw: Float, bh: Float) {
    when (skin) {
        VROOMI, TAEYEOP -> egg(p, 0f, cy, bw, bh)
        EOLEUM -> egg(p, 0f, cy, bw * 0.96f, bh)
        DOTOL -> egg(p, 0f, cy + bh * 0.04f, bw * 0.92f, bh * 0.92f)
        SSUK -> {
            val jig = 1f + 0.025f * sin(rt.time * 5.5f)
            val hw = bw * 0.60f * jig
            val top = cy - bh * 0.40f / jig; val bot = cy + bh / 2f
            p.reset()
            p.moveTo(-hw, bot - bh * 0.06f)
            p.cubicTo(-hw * 1.02f, top + bh * 0.20f, -hw * 0.62f, top, 0f, top)
            p.cubicTo(hw * 0.62f, top, hw * 1.02f, top + bh * 0.20f, hw, bot - bh * 0.06f)
            p.cubicTo(hw, bot + bh * 0.01f, hw * 0.7f, bot, 0f, bot)
            p.cubicTo(-hw * 0.7f, bot, -hw, bot + bh * 0.01f, -hw, bot - bh * 0.06f)
            p.close()
        }
        NUT -> { p.reset(); p.addRoundRect(RoundRect(-bw * 0.50f, cy - bh * 0.46f, bw * 0.50f, cy + bh * 0.50f, CornerRadius(bw * 0.24f))) }
        PISTON -> { p.reset(); p.addRoundRect(RoundRect(-bw * 0.54f, cy - bh * 0.36f, bw * 0.54f, cy + bh * 0.50f, CornerRadius(bw * 0.14f))) }
        ORGEL -> { p.reset(); p.addRoundRect(RoundRect(-bw * 0.56f, cy - bh * 0.34f, bw * 0.56f, cy + bh * 0.50f, CornerRadius(bw * 0.08f))) }
        CHOCHIM, BOOST, BANEUL, NACHIM -> { p.reset(); p.addOval(Rect(Offset(0f, cy + bh * 0.04f), bw * 0.54f)) }
        TOPNI -> gearPath(p, Offset(0f, cy + bh * 0.04f), bw * 0.58f, bw * 0.48f, 12, rt.wheelAngle * 0.15f)
        PILA -> {
            val r = bw * 0.50f
            val c = Offset(0f, cy - bh * 0.10f)
            val neckY = cy + bh * 0.26f
            p.reset()
            p.moveTo(-bw * 0.24f, neckY)
            p.cubicTo(-bw * 0.26f, cy + bh * 0.14f, -r, c.y + r * 0.7f, -r, c.y)
            p.cubicTo(-r, c.y - r * 1.1f, r, c.y - r * 1.1f, r, c.y)
            p.cubicTo(r, c.y + r * 0.7f, bw * 0.26f, cy + bh * 0.14f, bw * 0.24f, neckY)
            p.close()
        }
        MUNGE -> { p.reset(); p.addOval(Rect(-bw * 0.6f, cy - bh * 0.38f, bw * 0.6f, cy + bh * 0.48f)) }
    }
}

/** Fills and outlines the body. The cloud is a union of puffs, the others a single path. */
internal fun DrawScope.drawSkinBody(rt: SceneRuntime, skin: CharacterSkin, c: SkinColors, body: Color, cy: Float, bw: Float, bh: Float, stroke: Float) {
    if (skin == MUNGE) {
        val puffs = arrayOf(
            Offset(-bw * 0.30f, cy + bh * 0.14f) to bw * 0.32f, Offset(bw * 0.30f, cy + bh * 0.14f) to bw * 0.32f,
            Offset(-bw * 0.12f, cy - bh * 0.12f) to bw * 0.34f, Offset(bw * 0.18f, cy - bh * 0.06f) to bw * 0.30f,
            Offset(0f, cy + bh * 0.24f) to bw * 0.36f,
        )
        val wob = sin(rt.time * 2f) * bw * 0.01f
        for ((p, r) in puffs) drawCircle(c.outline, r + stroke, p + Offset(0f, wob))
        for ((p, r) in puffs) drawCircle(body, r, p + Offset(0f, wob))
        drawCircle(Color.White.alpha(0.6f), bw * 0.12f, Offset(-bw * 0.20f, cy - bh * 0.20f))
        return
    }
    skinBodyPath(rt, skin, rt.bodyPath, cy, bw, bh)
    drawPath(rt.bodyPath, body)
    drawPath(rt.bodyPath, c.outline, style = Stroke(width = stroke))
}

/** Parts behind the body: tail, antenna, rotor, straps, keys, pulleys, cranks. */
internal fun DrawScope.drawSkinBehind(rt: SceneRuntime, t: CharacterTarget, skin: CharacterSkin, c: SkinColors, cy: Float, bw: Float, bh: Float, s: Float, n: Float) {
    val stroke = max(3f, s * 0.022f)
    when (skin) {
        EOLEUM -> {
            val p = rt.tmpPath
            p.reset(); p.moveTo(-bw * 0.38f, cy + bh * 0.32f); p.lineTo(-bw * 0.62f, cy + bh * 0.44f); p.lineTo(-bw * 0.36f, cy + bh * 0.44f); p.close()
            drawPath(p, c.body); drawPath(p, c.outline, style = Stroke(width = stroke * 0.8f))
        }
        NUT -> {
            val top = Offset(bw * 0.08f, cy - bh * 0.46f - s * 0.24f)
            drawLine(c.outline, Offset(bw * 0.08f, cy - bh * 0.44f), top, strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            val active = t.action != Action.NONE
            val pulse = 0.5f + 0.5f * sin(rt.time * (if (active) 6f else 1.6f))
            val bulb = lerp(Color(0xFFFF8A65), Color(0xFFFFE066), if (active) 1f else 0f)
            drawCircle(bulb.alpha(0.25f + 0.35f * pulse), s * 0.09f, top)
            drawCircle(bulb, s * 0.045f, top)
            drawCircle(c.outline, s * 0.045f, top, style = Stroke(width = stroke * 0.6f))
            drawFan(rt, Offset(-bw * 0.52f, cy + bh * 0.05f), s * 0.11f, c.arm.dim(0.8f), c.outline, rt.propSpin * 2f)
        }
        CHOCHIM -> {
            drawRoundRect(c.sole.night(n, 0.3f), Offset(-bw * 0.22f, cy - bh * 0.62f), Size(bw * 0.44f, bh * 0.22f), CornerRadius(s * 0.04f))
            val rc = Offset(-bw * 0.30f, cy + bh * 0.04f); val rr = bw * 0.48f
            rotate(rt.propSpin * 1.4f, rc) {
                drawArc(Color(0xFFD9A441).night(n, 0.3f), 0f, 180f, true, Offset(rc.x - rr, rc.y - rr), Size(rr * 2, rr * 2))
                drawArc(c.outline, 0f, 180f, true, Offset(rc.x - rr, rc.y - rr), Size(rr * 2, rr * 2), style = Stroke(width = stroke * 0.7f))
                drawLine(Color.White.alpha(0.5f), Offset(rc.x - rr * 0.7f, rc.y + rr * 0.15f), Offset(rc.x + rr * 0.7f, rc.y + rr * 0.15f), strokeWidth = s * 0.012f)
            }
        }
        PISTON -> {
            // Three pistons pumping with "RPM" (speed), staggered like a real crankshaft
            val rpm = Math.toRadians(rt.propSpin * 2.5).toFloat()
            for (i in 0 until 3) {
                val x = -bw * 0.30f + i * bw * 0.30f
                val up = (sin(rpm + i * 2f * PI_F / 3f) * 0.5f + 0.5f) * s * 0.10f
                val top = cy - bh * 0.36f - s * 0.12f - up
                drawLine(c.outline, Offset(x, cy - bh * 0.36f), Offset(x, top + s * 0.04f), strokeWidth = s * 0.03f)
                drawRoundRect(steel.night(n, 0.3f), Offset(x - bw * 0.10f, top - s * 0.04f), Size(bw * 0.20f, s * 0.09f), CornerRadius(s * 0.02f))
                drawRoundRect(c.outline, Offset(x - bw * 0.10f, top - s * 0.04f), Size(bw * 0.20f, s * 0.09f), CornerRadius(s * 0.02f), style = Stroke(width = stroke * 0.6f))
            }
            // belt pulley on the back
            val pc = Offset(-bw * 0.55f, cy + bh * 0.18f)
            drawCircle(Color(0xFF2C343B), s * 0.10f, pc)
            rotate(rt.propSpin * 3f, pc) { drawLine(steel, Offset(pc.x - s * 0.08f, pc.y), Offset(pc.x + s * 0.08f, pc.y), strokeWidth = s * 0.025f) }
        }
        BOOST -> {
            // outlet pipe
            drawRoundRect(c.body.dim(0.85f), Offset(-bw * 0.80f, cy - bh * 0.06f), Size(bw * 0.36f, bh * 0.20f), CornerRadius(s * 0.04f))
            drawRoundRect(c.outline, Offset(-bw * 0.80f, cy - bh * 0.06f), Size(bw * 0.36f, bh * 0.20f), CornerRadius(s * 0.04f), style = Stroke(width = stroke * 0.7f))
            drawRoundRect(c.sole, Offset(-bw * 0.84f, cy - bh * 0.08f), Size(bw * 0.07f, bh * 0.24f), CornerRadius(s * 0.02f))
        }
        TAEYEOP -> {
            // wind-up key turning on the back
            val kc = Offset(-bw * 0.56f, cy + bh * 0.02f)
            drawLine(steel, Offset(-bw * 0.45f, kc.y), kc, strokeWidth = s * 0.04f)
            rotate(rt.propSpin * 0.8f, kc) {
                drawOval(steel.night(n, 0.3f), Offset(kc.x - s * 0.05f, kc.y - s * 0.17f), Size(s * 0.10f, s * 0.16f))
                drawOval(steel.night(n, 0.3f), Offset(kc.x - s * 0.05f, kc.y + s * 0.01f), Size(s * 0.10f, s * 0.16f))
                drawOval(c.outline, Offset(kc.x - s * 0.05f, kc.y - s * 0.17f), Size(s * 0.10f, s * 0.16f), style = Stroke(width = stroke * 0.6f))
                drawOval(c.outline, Offset(kc.x - s * 0.05f, kc.y + s * 0.01f), Size(s * 0.10f, s * 0.16f), style = Stroke(width = stroke * 0.6f))
            }
        }
        ORGEL -> {
            // crank handle on the side
            val hc = Offset(-bw * 0.56f, cy + bh * 0.08f)
            rotate(rt.propSpin, hc) {
                drawLine(c.accent, hc, Offset(hc.x - s * 0.12f, hc.y), strokeWidth = s * 0.025f, cap = StrokeCap.Round)
                drawCircle(Color(0xFF7A4E2E), s * 0.03f, Offset(hc.x - s * 0.12f, hc.y))
            }
            drawCircle(c.accent, s * 0.025f, hc)
        }
        DOTOL -> {
            // small leaf on a twig behind
            val b = Offset(-bw * 0.30f, cy - bh * 0.30f)
            rotate(-30f + 6f * sin(rt.sailT), b) {
                drawOval(Color(0xFF6CA24A), Offset(b.x - s * 0.18f, b.y - s * 0.05f), Size(s * 0.18f, s * 0.08f))
            }
        }
        MUNGE -> {
            // drizzle when the wipers run
            if (rt.rain.value > 0.1f) for (i in 0 until 6) {
                val u = ((rt.time * 1.2f + i * 0.17f) % 1f)
                val x = -bw * 0.40f + i * bw * 0.16f
                drawLine(Color(0xFF7FB2E5).alpha(1f - u), Offset(x, cy + bh * 0.52f + u * s * 0.5f), Offset(x - s * 0.01f, cy + bh * 0.58f + u * s * 0.5f), strokeWidth = s * 0.015f, cap = StrokeCap.Round)
            }
        }
        else -> Unit
    }
}

private fun DrawScope.drawFan(rt: SceneRuntime, fc: Offset, len: Float, col: Color, outline: Color, angle: Float) {
    rotate(-angle, fc) {
        for (i in 0 until 3) rotate(i * 120f, fc) { drawOval(col, Offset(fc.x - len * 0.23f, fc.y - len), Size(len * 0.46f, len)) }
    }
    drawCircle(outline, len * 0.18f, fc)
}

/** Markings on the body, drawn before the face: belly, mask, visor, dials, impeller, filament. */
internal fun DrawScope.drawSkinMarkings(rt: SceneRuntime, t: CharacterTarget, o: SceneOptions?, skin: CharacterSkin, c: SkinColors, cy: Float, bw: Float, bh: Float, s: Float, turn: Float, n: Float) {
    val shift = -turn * bw * 0.12f
    val stroke = max(3f, s * 0.022f)
    val dc = Offset(shift * 0.5f, cy + bh * 0.04f)
    when (skin) {
        VROOMI, DOTOL -> drawOval(c.belly, Offset(bw * 0.15f - bw * 0.27f + shift, cy + bh * 0.18f - bh * 0.20f), Size(bw * 0.54f, bh * 0.40f))
        SSUK -> {
            drawOval(c.belly, Offset(-bw * 0.22f + shift, cy + bh * 0.02f), Size(bw * 0.56f, bh * 0.38f))
            for (i in 0 until 6) {
                val dx = (hash01(i, 71) - 0.5f) * bw * 0.9f
                val dy = cy - bh * 0.25f + hash01(i, 72) * bh * 0.6f
                drawCircle(Color(0xFFC9A66B).alpha(0.7f), s * 0.012f, Offset(dx, dy))
            }
            drawOval(Color.White.alpha(0.35f), Offset(-bw * 0.32f, cy - bh * 0.30f), Size(bw * 0.22f, bh * 0.10f))
        }
        EOLEUM -> {
            drawOval(c.belly, Offset(-bw * 0.16f + shift, cy - bh * 0.30f), Size(bw * 0.38f, bh * 0.36f))
            drawOval(c.belly, Offset(bw * 0.08f + shift, cy - bh * 0.30f), Size(bw * 0.38f, bh * 0.36f))
            drawOval(c.belly, Offset(-bw * 0.20f + shift, cy - bh * 0.02f), Size(bw * 0.62f, bh * 0.48f))
        }
        MUNGE -> Unit
        NUT -> {
            drawRoundRect(c.belly, Offset(-bw * 0.30f + shift * 0.6f, cy - bh * 0.30f), Size(bw * 0.78f, bh * 0.40f), CornerRadius(s * 0.10f))
            drawLine(Color.White.alpha(0.18f), Offset(-bw * 0.22f, cy - bh * 0.24f), Offset(bw * 0.05f, cy - bh * 0.24f), strokeWidth = s * 0.02f, cap = StrokeCap.Round)
            drawRoundRect(c.body.dim(0.85f), Offset(-bw * 0.20f, cy + bh * 0.16f), Size(bw * 0.50f, bh * 0.20f), CornerRadius(s * 0.04f))
            val cols = arrayOf(Color(0xFF6CC24A), Color(0xFFFFD34D), Color(0xFFFF6B6B))
            for (i in 0 until 3) {
                val on = ((rt.time * 0.8f).toInt() % 3) == i
                drawCircle(cols[i].alpha(if (on) 1f else 0.35f), s * 0.03f, Offset(-bw * 0.08f + i * bw * 0.13f, cy + bh * 0.26f))
            }
            for (y in floatArrayOf(-0.30f, 0.30f)) drawCircle(c.outline.alpha(0.6f), s * 0.02f, Offset(bw * 0.42f, cy + bh * y))
        }
        CHOCHIM -> {
            val dr = bw * 0.44f
            drawCircle(c.belly, dr, dc)
            hourMarkers(dc, dr, c.outline, s)
            val hour = o?.hourOfDay ?: 10.1f
            val ha = ((hour % 12f) / 12f) * 2f * PI_F
            val ma = (hour % 1f) * 2f * PI_F
            val handCol = c.outline.alpha(0.35f)
            drawLine(handCol, dc, Offset(dc.x + sin(ha) * dr * 0.45f, dc.y - cos(ha) * dr * 0.45f), strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            drawLine(handCol, dc, Offset(dc.x + sin(ma) * dr * 0.70f, dc.y - cos(ma) * dr * 0.70f), strokeWidth = s * 0.02f, cap = StrokeCap.Round)
            val sa = (rt.time % 60f) / 60f * 2f * PI_F
            drawLine(Color(0xFFE4574B).alpha(0.6f), dc, Offset(dc.x + sin(sa) * dr * 0.82f, dc.y - cos(sa) * dr * 0.82f), strokeWidth = s * 0.008f, cap = StrokeCap.Round)
            drawCircle(c.outline.alpha(0.5f), s * 0.018f, dc)
        }
        PISTON -> {
            // red valve cover with ribs across the top, intake grille below the face
            drawRoundRect(c.accent.night(n, 0.3f), Offset(-bw * 0.50f, cy - bh * 0.36f), Size(bw * 1.0f, bh * 0.16f), CornerRadius(s * 0.04f))
            for (i in 1..5) drawLine(Color.White.alpha(0.35f), Offset(-bw * 0.50f + i * bw * 0.17f, cy - bh * 0.34f), Offset(-bw * 0.50f + i * bw * 0.17f, cy - bh * 0.22f), strokeWidth = s * 0.012f)
            for (i in 0 until 4) drawLine(c.belly, Offset(-bw * 0.30f + shift, cy + bh * (0.20f + i * 0.06f)), Offset(bw * 0.34f + shift, cy + bh * (0.20f + i * 0.06f)), strokeWidth = s * 0.02f, cap = StrokeCap.Round)
        }
        BOOST -> {
            // spiral compressor housing, blue ring, and an impeller that spins with speed + acceleration
            drawCircle(Color(0xFF3D7BD9).night(n, 0.3f), bw * 0.47f, dc, style = Stroke(width = s * 0.04f))
            val p = rt.tmpPath
            p.reset()
            for (i in 0..40) {
                val a = i / 40f * 2f * PI_F * 0.95f
                val r = bw * (0.20f + 0.20f * i / 40f)
                val x = dc.x + cos(a) * r; val y = dc.y + sin(a) * r
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, c.outline.alpha(0.35f), style = Stroke(width = s * 0.012f))
            val ic = Offset(dc.x, cy + bh * 0.22f)
            val spin = rt.propSpin * (6f + (if (t.action == Action.BURST) 10f else 0f))
            rotate(spin, ic) {
                for (i in 0 until 7) rotate(i * 360f / 7f, ic) {
                    val bp = rt.tmpPath2
                    bp.reset(); bp.moveTo(ic.x, ic.y); bp.cubicTo(ic.x + s * 0.04f, ic.y - s * 0.03f, ic.x + s * 0.08f, ic.y - s * 0.08f, ic.x + s * 0.03f, ic.y - s * 0.12f); bp.close()
                    drawPath(bp, steel.dim(0.9f)); drawPath(bp, c.outline.alpha(0.6f), style = Stroke(width = s * 0.006f))
                }
            }
            drawCircle(c.outline, s * 0.025f, ic)
        }
        TOPNI -> {
            val ang = rt.wheelAngle * 0.15f
            drawCircle(c.belly, bw * 0.40f, dc)
            drawCircle(c.outline.alpha(0.4f), bw * 0.40f, dc, style = Stroke(width = s * 0.012f))
            rotate(ang, dc) {
                for (i in 0 until 4) rotate(i * 90f + 45f, dc) { drawCircle(c.body.dim(0.85f), s * 0.035f, Offset(dc.x, dc.y + bw * 0.30f)) }
            }
        }
        BANEUL -> {
            // speedometer: scale from 0 (lower left) to 120 (lower right); the needle shows the real speed
            val dr = bw * 0.45f
            drawCircle(c.belly, dr, dc)
            for (i in 0..12) {
                val a = (-135f + i * 22.5f) * PI_F / 180f
                val major = i % 2 == 0
                val col = if (i >= 10) Color(0xFFE4574B) else c.outline.alpha(0.6f)
                drawLine(col, Offset(dc.x + sin(a) * dr * (if (major) 0.78f else 0.85f), dc.y - cos(a) * dr * (if (major) 0.78f else 0.85f)),
                    Offset(dc.x + sin(a) * dr * 0.94f, dc.y - cos(a) * dr * 0.94f), strokeWidth = s * (if (major) 0.016f else 0.008f))
            }
            val kmh = t.gaitSpeedKmh.coerceIn(0f, 120f)
            val wobble = if (kmh > 1f) sin(rt.time * 23f) * 1.2f else 0f
            val na = (-135f + kmh / 120f * 270f + wobble) * PI_F / 180f
            drawLine(c.sole, dc, Offset(dc.x + sin(na) * dr * 0.82f, dc.y - cos(na) * dr * 0.82f), strokeWidth = s * 0.022f, cap = StrokeCap.Round)
            drawCircle(c.outline, s * 0.03f, dc)
        }
        TAEYEOP -> {
            // tin seam band and rivets
            drawRect(steel.alpha(0.85f), Offset(-bw * 0.5f, cy + bh * 0.06f), Size(bw, bh * 0.07f))
            for (i in 0 until 5) drawCircle(c.outline.alpha(0.5f), s * 0.014f, Offset(-bw * 0.36f + i * bw * 0.18f, cy + bh * 0.095f))
            drawOval(c.belly, Offset(-bw * 0.10f + shift, cy + bh * 0.17f), Size(bw * 0.44f, bh * 0.24f))
        }
        ORGEL -> {
            // lid line, and a brass pin cylinder that turns as you drive, with its steel comb
            drawLine(c.outline.alpha(0.5f), Offset(-bw * 0.56f, cy - bh * 0.20f), Offset(bw * 0.56f, cy - bh * 0.20f), strokeWidth = s * 0.012f)
            val top = cy + bh * 0.16f
            drawRoundRect(c.belly, Offset(-bw * 0.40f, top - s * 0.02f), Size(bw * 0.80f, bh * 0.22f), CornerRadius(s * 0.03f))
            drawRoundRect(c.accent, Offset(-bw * 0.34f, top + s * 0.01f), Size(bw * 0.68f, bh * 0.10f), CornerRadius(s * 0.05f))
            val roll = (rt.wheelAngle / 360f) % 1f
            for (i in 0 until 10) {
                val u = ((i / 10f + roll) % 1f)
                val x = -bw * 0.32f + u * bw * 0.64f
                drawCircle(steel, s * 0.008f, Offset(x, top + s * 0.03f + hash01(i, 81) * bh * 0.05f))
            }
            for (i in 0 until 8) drawLine(steel, Offset(-bw * 0.30f + i * bw * 0.085f, top + bh * 0.12f), Offset(-bw * 0.30f + i * bw * 0.085f, top + bh * 0.18f), strokeWidth = s * 0.012f)
        }
        PILA -> {
            // filament glowing with speed (and a little at night); brass screw base under the neck
            val heat = (0.25f + 0.75f * rt.speedFx.value + 0.2f * n).coerceIn(0f, 1f)
            val fc = Offset(0f, cy - bh * 0.02f)
            drawCircle(Color(0xFFFFD27A).alpha(0.18f + 0.35f * heat), bw * 0.48f, Offset(0f, cy - bh * 0.10f))
            val glowCol = lerp(Color(0xFFB0662A), Color(0xFFFFF0A0), heat)
            drawLine(c.outline.alpha(0.5f), Offset(-bw * 0.08f, cy + bh * 0.24f), Offset(-bw * 0.10f, fc.y + bh * 0.08f), strokeWidth = s * 0.008f)
            drawLine(c.outline.alpha(0.5f), Offset(bw * 0.08f, cy + bh * 0.24f), Offset(bw * 0.10f, fc.y + bh * 0.08f), strokeWidth = s * 0.008f)
            val p = rt.tmpPath
            p.reset(); p.moveTo(-bw * 0.10f, fc.y + bh * 0.08f)
            for (i in 1..6) p.lineTo(-bw * 0.10f + i * bw * 0.033f, fc.y + bh * (if (i % 2 == 0) 0.08f else 0.04f))
            drawPath(p, glowCol, style = Stroke(width = s * 0.014f, cap = StrokeCap.Round))
            for (i in 0 until 3) drawRoundRect(c.accent.dim(1f - i * 0.08f), Offset(-bw * 0.25f, cy + bh * (0.26f + i * 0.075f)), Size(bw * 0.50f, bh * 0.065f), CornerRadius(s * 0.02f))
        }
        NACHIM -> {
            val dr = bw * 0.44f
            drawCircle(c.belly, dr, dc)
            hourMarkers(dc, dr, c.outline, s)
            // needle swings toward the blinker and wobbles while moving
            val a = (rt.steer.value * 2.5f + sin(rt.time * 3f) * 4f * rt.speedFx.value) * PI_F / 180f
            val tipN = Offset(dc.x + sin(a) * dr * 0.80f, dc.y - cos(a) * dr * 0.80f)
            val tipS = Offset(dc.x - sin(a) * dr * 0.80f, dc.y + cos(a) * dr * 0.80f)
            drawLine(Color(0xFFE4574B).alpha(0.55f), dc, tipN, strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            drawLine(c.outline.alpha(0.3f), dc, tipS, strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            drawCircle(c.body, s * 0.025f, dc)
        }
    }
}

private fun DrawScope.hourMarkers(dc: Offset, dr: Float, col: Color, s: Float) {
    for (i in 0 until 12) {
        val a = i * PI_F / 6f
        val r0 = dr * (if (i % 3 == 0) 0.78f else 0.86f)
        drawLine(col.alpha(0.55f), Offset(dc.x + sin(a) * r0, dc.y - cos(a) * r0), Offset(dc.x + sin(a) * dr * 0.94f, dc.y - cos(a) * dr * 0.94f), strokeWidth = s * (if (i % 3 == 0) 0.016f else 0.008f))
    }
}

/** Signature parts on top (after the body outline): leaf, crest, crowns, caps, loops. */
internal fun DrawScope.drawSkinTop(rt: SceneRuntime, skin: CharacterSkin, c: SkinColors, cy: Float, bw: Float, bh: Float, s: Float) {
    val stroke = max(3f, s * 0.022f)
    when (skin) {
        SSUK -> {
            val base = Offset(bw * 0.02f, cy - bh * 0.40f)
            rotate(10f + 8f * sin(rt.sailT), base) {
                val p = rt.tmpPath
                p.reset()
                p.moveTo(base.x, base.y)
                p.cubicTo(base.x + s * 0.02f, base.y - s * 0.12f, base.x + s * 0.12f, base.y - s * 0.16f, base.x + s * 0.18f, base.y - s * 0.14f)
                p.cubicTo(base.x + s * 0.14f, base.y - s * 0.04f, base.x + s * 0.06f, base.y, base.x, base.y)
                p.close()
                drawPath(p, Color(0xFF5E9E45))
                drawPath(p, c.outline, style = Stroke(width = stroke * 0.6f))
            }
        }
        EOLEUM -> {
            val p = rt.tmpPath
            val b = Offset(-bw * 0.02f, cy - bh * 0.48f)
            val sway = sin(rt.sailT) * s * 0.02f
            p.reset()
            p.moveTo(b.x, b.y)
            p.cubicTo(b.x - s * 0.02f, b.y - s * 0.14f, b.x + s * 0.12f + sway, b.y - s * 0.18f, b.x + s * 0.10f + sway, b.y - s * 0.08f)
            drawPath(p, c.body, style = Stroke(width = s * 0.05f, cap = StrokeCap.Round))
        }
        CHOCHIM, BOOST, NACHIM, BANEUL -> {
            val r = bw * 0.54f
            if (skin == NACHIM) {
                // lanyard loop on top
                drawCircle(c.body, s * 0.06f, Offset(0f, cy + bh * 0.04f - r - s * 0.03f), style = Stroke(width = s * 0.03f))
            } else if (skin == CHOCHIM) {
                val k = Offset(r * 0.97f, cy + bh * 0.04f - r * 0.2f)
                drawRoundRect(c.body.dim(0.85f), Offset(k.x - s * 0.01f, k.y - s * 0.05f), Size(s * 0.09f, s * 0.10f), CornerRadius(s * 0.02f))
                for (i in 0 until 3) drawLine(c.outline.alpha(0.6f), Offset(k.x + s * 0.015f + i * s * 0.022f, k.y - s * 0.045f), Offset(k.x + s * 0.015f + i * s * 0.022f, k.y + s * 0.045f), strokeWidth = s * 0.006f)
            } else if (skin == BANEUL) {
                // chrome bezel ring
                drawCircle(Color(0xFFB9C2CB), r * 0.93f, Offset(0f, cy + bh * 0.04f), style = Stroke(width = s * 0.025f))
            }
        }
        DOTOL -> {
            // acorn cap with cross-hatch and a stem, wobbling a little at speed
            val top = cy - bh * 0.40f
            rotate(sin(rt.time * 9f) * 4f * rt.speedFx.value, Offset(0f, top + bh * 0.1f)) {
                val capCol = c.accent
                drawArc(capCol, 180f, 180f, true, Offset(-bw * 0.50f, top - bh * 0.14f), Size(bw * 1.0f, bh * 0.38f))
                drawRect(capCol, Offset(-bw * 0.50f, top + bh * 0.04f), Size(bw * 1.0f, bh * 0.04f))
                for (i in 0 until 6) {
                    val x = -bw * 0.40f + i * bw * 0.16f
                    drawLine(c.outline.alpha(0.35f), Offset(x, top - bh * 0.06f), Offset(x + bw * 0.08f, top + bh * 0.07f), strokeWidth = s * 0.008f)
                    drawLine(c.outline.alpha(0.35f), Offset(x + bw * 0.08f, top - bh * 0.06f), Offset(x, top + bh * 0.07f), strokeWidth = s * 0.008f)
                }
                drawArc(c.outline, 180f, 180f, false, Offset(-bw * 0.50f, top - bh * 0.14f), Size(bw * 1.0f, bh * 0.38f), style = Stroke(width = stroke * 0.8f))
                drawLine(c.outline, Offset(0f, top - bh * 0.14f), Offset(s * 0.03f, top - bh * 0.24f), strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            }
        }
        PILA -> {
            // glass shine
            drawArc(Color.White.alpha(0.7f), 200f, 60f, false, Offset(-bw * 0.38f, cy - bh * 0.48f), Size(bw * 0.6f, bh * 0.6f), style = Stroke(width = s * 0.025f, cap = StrokeCap.Round))
        }
        else -> Unit
    }
}

/** Penguin beak in place of a mouth: closed, or open when blowing / cheering / surprised. */
internal fun DrawScope.drawBeak(rt: SceneRuntime, mx: Float, my: Float, s: Float, open: Boolean) {
    val col = Color(0xFFFF9F43)
    val edge = Color(0xFF9A4F10)
    val tip = Offset(mx + s * 0.12f, my - s * 0.005f)
    val p = rt.tmpPath2
    if (!open) {
        p.reset(); p.moveTo(mx - s * 0.03f, my - s * 0.04f); p.lineTo(tip.x, tip.y); p.lineTo(mx - s * 0.03f, my + s * 0.03f); p.close()
        drawPath(p, col)
        drawLine(edge.alpha(0.6f), Offset(mx - s * 0.02f, my), Offset(tip.x - s * 0.02f, tip.y), strokeWidth = s * 0.008f)
    } else {
        p.reset(); p.moveTo(mx - s * 0.03f, my - s * 0.05f); p.lineTo(tip.x, tip.y - s * 0.04f); p.lineTo(mx - s * 0.03f, my - s * 0.005f); p.close()
        drawPath(p, col)
        p.reset(); p.moveTo(mx - s * 0.03f, my + s * 0.01f); p.lineTo(tip.x - s * 0.02f, tip.y + s * 0.05f); p.lineTo(mx - s * 0.03f, my + s * 0.04f); p.close()
        drawPath(p, col.dim(0.9f))
        drawCircle(Color(0xFF8A2E2E), s * 0.018f, Offset(mx + s * 0.01f, my + s * 0.005f))
    }
}

/** Faint gears turning in the sky for gear-themed friends (faster when you drive faster). */
internal fun DrawScope.drawSkyGears(rt: SceneRuntime) {
    if (rt.skin != CHOCHIM && rt.skin != TOPNI && rt.skin != TAEYEOP && rt.skin != PISTON) return
    val w = size.width; val h = size.height
    val n = rt.dark.value
    val col = lerp(Color.White, Color(0xFFFFE6A0), n).alpha(0.22f)
    val spin = rt.time * 6f + rt.scrollFar.toFloat() * 900f
    val p = rt.tmpPath2
    fun gear(c: Offset, r: Float, teeth: Int, a: Float) {
        gearPath(p, c, r, r * 0.84f, teeth, a)
        drawPath(p, col, style = Stroke(width = r * 0.10f))
        drawCircle(col, r * 0.22f, c)
    }
    gear(Offset(w * 0.30f, h * 0.18f), h * 0.12f, 14, spin)
    gear(Offset(w * 0.30f + h * 0.205f, h * 0.30f), h * 0.085f, 10, -spin * 1.4f + 18f)
    gear(Offset(w * 0.66f, h * 0.12f), h * 0.06f, 8, spin * 1.8f)
}

/** Gear-toothed rims on the kart wheels for mechanical friends (kart space, s units). */
internal fun DrawScope.drawWheelTeeth(rt: SceneRuntime, c: Offset, r: Float) {
    if (!rt.skin.mechanical) return
    val p = rt.tmpPath2
    gearPath(p, c, r * 1.12f, r * 0.98f, 14, rt.wheelAngle)
    drawPath(p, Color(0xFF7A6A4A))
}
