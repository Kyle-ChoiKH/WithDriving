package io.vroomi.character.render.calibre

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.drawText
import io.vroomi.character.calibre.CalibreTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Dial-side geometry (units of movement radius, dial view: crown on the RIGHT). */
internal object DG {
    val SMALL_SECONDS = Offset(-0.47f, 0f)
    val COUNTER = Offset(0.47f, 0f)
    const val SUB_R = 0.20f
    val PR_PIVOT = Offset(0f, -0.29f)
    const val PR_R = 0.24f
    const val PR_FROM = -150f
    const val PR_SWEEP = 120f
    val MOON = Offset(0f, 0.50f)
    const val MOON_W = 0.36f
    const val MOON_H = 0.15f
    val DATE = Offset(0.50f, 0.50f)
    val AUTO = Offset(-0.58f, 0.58f)
    val HOURS = intArrayOf(1, 2, 4, 5, 7, 8, 10, 11)
}

/** Original hand shapes: a faceted lance with a lume channel (pointing up, pivot at 0,0). */
internal class Hand(val left: Path, val right: Path, val lume: Path?, val outline: Path)

internal fun lanceHand(u: Float, len: Float, w: Float, tail: Float, lumeOn: Boolean): Hand {
    val tip = Offset(0f, -len * u)
    val shoulder = -len * 0.40f * u
    val wMax = w * u
    val left = Path().apply { moveTo(tip.x, tip.y); lineTo(-wMax, shoulder); lineTo(-wMax * 0.55f, 0f); lineTo(0f, tail * u); close() }
    val right = Path().apply { moveTo(tip.x, tip.y); lineTo(wMax, shoulder); lineTo(wMax * 0.55f, 0f); lineTo(0f, tail * u); close() }
    val outline = Path().apply { moveTo(tip.x, tip.y); lineTo(wMax, shoulder); lineTo(wMax * 0.55f, 0f); lineTo(0f, tail * u); lineTo(-wMax * 0.55f, 0f); lineTo(-wMax, shoulder); close() }
    val lume = if (!lumeOn) null else Path().apply {
        val a = -len * 0.80f * u; val b = -len * 0.16f * u
        moveTo(0f, a); lineTo(wMax * 0.42f, -len * 0.42f * u); lineTo(wMax * 0.24f, b); lineTo(-wMax * 0.24f, b); lineTo(-wMax * 0.42f, -len * 0.42f * u); close()
    }
    return Hand(left, right, lume, outline)
}

internal class DialHands(u: Float) {
    val hour = lanceHand(u, 0.52f, 0.052f, 0.08f, true)
    val minute = lanceHand(u, 0.84f, 0.040f, 0.10f, true)
    val sub = lanceHand(u, 0.17f, 0.014f, 0.04f, false)
    val pr = lanceHand(u, 0.21f, 0.016f, 0.03f, false)
}

// ------------------------------------------------------------------ static dial (cached)

internal fun DrawScope.drawDialBase(L: CalibreLayers): Unit = with(L) {
    val pal = L.pal
    val light = L.light
    val c = L.c
    val u = L.u
    drawLugs(pal.case)
    // Case and polished bezel
    softShadow(circlePath(c, CW.CASE_OUT * u), -light.x * u * 0.05f, -light.y * u * 0.05f, 0.45f)
    drawCircle(metalBrush(pal.case, c, CW.CASE_OUT * u, light), CW.CASE_OUT * u, c)
    rotate(light.deg + 45f, c) { drawCircle(turnedBrush(pal.case, c), CW.CASE_OUT * u, c, alpha = 0.55f) }
    drawCircle(Color.Black.a(0.45f), CW.BEZEL_IN * u, c, style = Stroke(width = u * 0.01f))
    // Rehaut (inner flange) with the minute track
    drawCircle(lerp(pal.dial.base, Color.Black, 0.25f), CW.GASKET * u, c)
    val d = pal.dial
    drawCircle(Brush.radialGradient(0f to d.light, 0.55f to d.base, 1f to d.dark, center = Offset(c.x + light.x * u * 0.35f, c.y + light.y * u * 0.35f), radius = u * 1.25f), u, c)
    clipPath(circlePath(c, u)) {
        if (pal.guilloche == 0) sunray(c, u, 540, 0.035f)
        else {
            // Original wave guilloché: concentric rings with a slow 12-fold ripple
            var k = 0
            var r = u * 0.08f
            while (r < u) {
                val p = Path()
                for (i in 0..180) {
                    val a = i / 180f * 2f * PI.toFloat()
                    val rr = r + u * 0.006f * sin(12f * a + k * 0.55f)
                    val x = c.x + cos(a) * rr; val y = c.y + sin(a) * rr
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                drawPath(p, if (k % 2 == 0) Color.White.a(0.10f) else Color.Black.a(0.06f), style = Stroke(width = (u * 0.006f).coerceAtLeast(1f)))
                r += u * 0.018f; k++
            }
        }
    }
    // Minute track
    for (i in 0 until 60) {
        val a = (i * 6f - 90f) * PI.toFloat() / 180f
        val five = i % 5 == 0
        drawLine(pal.dialPrint.a(if (five) 0.95f else 0.6f), pol(c, u * 0.985f, a), pol(c, u * (if (five) 0.925f else 0.955f), a), strokeWidth = if (five) u * 0.008f else u * 0.004f)
    }
    // Applied baton indices with a lume channel
    for (h in DG.HOURS) {
        val a = (h * 30f - 90f) * PI.toFloat() / 180f
        val p0 = pol(c, u * 0.74f, a); val p1 = pol(c, u * 0.895f, a)
        drawLine(Color.Black.a(0.35f), p0 - Offset(light.x, light.y) * (u * 0.012f), p1 - Offset(light.x, light.y) * (u * 0.012f), strokeWidth = u * 0.046f, cap = StrokeCap.Round)
        drawLine(pal.hand.base, p0, p1, strokeWidth = u * 0.044f, cap = StrokeCap.Round)
        drawLine(pal.hand.light, p0 + Offset(light.x, light.y) * (u * 0.008f), p1 + Offset(light.x, light.y) * (u * 0.008f), strokeWidth = u * 0.01f, cap = StrokeCap.Round)
        drawLine(CalPal.lumeDay, pol(c, u * 0.76f, a), pol(c, u * 0.875f, a), strokeWidth = u * 0.016f, cap = StrokeCap.Round)
    }
    // Double index at 12 is replaced by the power-reserve fan; 3, 6 and 9 by sub-dials.
    subDial(L, DG.SMALL_SECONDS, 12, listOf("60", "15", "30", "45"))
    subDial(L, DG.COUNTER, 30, listOf("30", "10", "20"))
    // Power reserve fan (0 .. 72H)
    val pc = Offset(c.x + DG.PR_PIVOT.x * u, c.y + DG.PR_PIVOT.y * u)
    drawArc(pal.dialPrint.a(0.8f), DG.PR_FROM, DG.PR_SWEEP, false, Offset(pc.x - DG.PR_R * u, pc.y - DG.PR_R * u), Size(DG.PR_R * 2 * u, DG.PR_R * 2 * u), style = Stroke(width = u * 0.006f))
    for (k in 0..6) {
        val a = (DG.PR_FROM + DG.PR_SWEEP * k / 6f) * PI.toFloat() / 180f
        drawLine(pal.dialPrint, pol(pc, DG.PR_R * u, a), pol(pc, (DG.PR_R - if (k % 3 == 0) 0.045f else 0.028f) * u, a), strokeWidth = u * 0.007f)
    }
    engrave("0", pol(pc, (DG.PR_R + 0.05f) * u, DG.PR_FROM * PI.toFloat() / 180f), 0f, 0.04f, pal.dialPrint, serif = false)
    engrave("72H", pol(pc, (DG.PR_R + 0.07f) * u, (DG.PR_FROM + DG.PR_SWEEP) * PI.toFloat() / 180f), 0f, 0.04f, pal.dialPrint, serif = false)
    engrave("RESERVE", Offset(pc.x, pc.y - 0.10f * u), 0f, 0.028f, pal.dialPrint.a(0.8f), serif = false)
    // Name, calibre
    engrave("Calibre", Offset(c.x, c.y + 0.205f * u), 0f, 0.07f, pal.dialPrint, smallCaps = true, spacing = 0.22f)
    engrave("VR-01 · AUTOMATIC", Offset(c.x, c.y + 0.295f * u), 0f, 0.03f, pal.dialPrint.a(0.85f), smallCaps = true, spacing = 0.28f)
    // Moon-phase aperture: night sky with painted stars (the moon is drawn per frame)
    val ap = moonAperture(c, u)
    drawPath(ap, Brush.verticalGradient(listOf(Color(0xFF0B1638), Color(0xFF1C2E66)), ap.getBounds().top, ap.getBounds().bottom))
    clipPath(ap) {
        for (i in 0 until 14) {
            val sx = c.x + (DG.MOON.x - DG.MOON_W / 2 + DG.MOON_W * ((i * 0.37f) % 1f)) * u
            val sy = c.y + (DG.MOON.y - DG.MOON_H / 2 + DG.MOON_H * ((i * 0.61f) % 1f)) * u
            drawCircle(Color(0xFFE9D9A8).a(0.85f), u * (0.004f + 0.003f * ((i * 0.53f) % 1f)), Offset(sx, sy))
        }
    }
    drawPath(ap, pal.hand.base, style = Stroke(width = u * 0.01f))
    // Date window frame
    val dw = Rect(Offset(c.x + DG.DATE.x * u - 0.06f * u, c.y + DG.DATE.y * u - 0.045f * u), Size(0.12f * u, 0.09f * u))
    drawRoundRect(Color(0xFFF4EFE4), dw.topLeft, dw.size, CornerRadius(u * 0.01f))
    drawRoundRect(pal.hand.base, dw.topLeft, dw.size, CornerRadius(u * 0.01f), style = Stroke(width = u * 0.008f))
    engrave("AUTO", Offset(c.x + DG.AUTO.x * u, c.y + DG.AUTO.y * u), 45f, 0.034f, pal.dialPrint.a(0.45f), serif = false)
}

private fun DrawScope.subDial(L: CalibreLayers, at: Offset, ticks: Int, labels: List<String>): Unit = with(L) {
    val o = Offset(c.x + at.x * u, c.y + at.y * u)
    val r = DG.SUB_R * u
    drawCircle(Color.Black.a(0.25f), r * 1.03f, o + Offset(-light.x, -light.y) * (u * 0.006f))
    drawCircle(Brush.radialGradient(listOf(lerp(pal.dial.base, pal.dial.dark, 0.25f), pal.dial.base), o, r), r, o)
    circularGrain(o, r * 0.1f, r, (u * 0.008f).coerceAtLeast(1.2f), 0.06f)
    drawCircle(pal.hand.base.a(0.9f), r, o, style = Stroke(width = u * 0.006f))
    for (i in 0 until ticks) {
        val a = (i * 360f / ticks - 90f) * PI.toFloat() / 180f
        val major = i % (ticks / labels.size.coerceAtLeast(1)).coerceAtLeast(1) == 0
        drawLine(pal.dialPrint.a(if (major) 0.95f else 0.55f), pol(o, r * 0.96f, a), pol(o, r * (if (major) 0.80f else 0.88f), a), strokeWidth = u * if (major) 0.006f else 0.003f)
    }
    labels.forEachIndexed { i, s ->
        val a = (i * 360f / labels.size - 90f) * PI.toFloat() / 180f
        engrave(s, pol(o, r * 0.62f, a), 0f, 0.036f, pal.dialPrint, serif = false)
    }
}

internal fun moonAperture(c: Offset, u: Float): Path {
    val w = DG.MOON_W * u; val h = DG.MOON_H * u
    val x0 = c.x + DG.MOON.x * u - w / 2; val y1 = c.y + DG.MOON.y * u + h / 2
    return Path().apply {
        moveTo(x0, y1)
        arcTo(Rect(Offset(c.x + DG.MOON.x * u, y1), w / 2), 180f, 180f, false)
        close()
    }
}

// ------------------------------------------------------------------ per frame

/** Dial-side motion: moon, date, AUTO, hands from the real clock, chronograph, reserve, lume. */
internal fun DrawScope.drawDialFrame(L: CalibreLayers, rt: CalibreRuntime, hands: CalibreTime.Hands, cache: CalibreText) {
    val c = L.c; val u = L.u; val pal = L.pal; val light = L.light
    val m = rt.mech
    // Moon: a gold disc travelling across the aperture, masked by the two humps.
    val ap = L.moonClip
    clipPath(ap) {
        val phase = (hands.moonAgeDays / CalibreTime.SYNODIC_MONTH_DAYS).toFloat()
        val mx = c.x + (DG.MOON.x + (phase - 0.5f) * DG.MOON_W * 1.6f) * u
        val my = c.y + (DG.MOON.y + 0.012f) * u
        val mr = DG.MOON_H * 0.42f * u
        drawCircle(Brush.radialGradient(listOf(CalPal.yellowGold.light, CalPal.yellowGold.base, CalPal.yellowGold.dark), Offset(mx - mr * 0.3f, my - mr * 0.3f), mr * 1.3f), mr, Offset(mx, my))
    }
    // Date
    val dl = cache.get("${hands.dayOfMonth}", 0.055f * u, Color(0xFF1E1E22))
    drawText(dl, topLeft = Offset(c.x + DG.DATE.x * u - dl.size.width / 2f, c.y + DG.DATE.y * u - dl.size.height / 2f))
    // AUTO lights up with the climate's auto mode
    if (m.autoMark > 0.01f) {
        val al = cache.get("AUTO", 0.034f * u, CalPal.lume)
        rotate(45f, Offset(c.x + DG.AUTO.x * u, c.y + DG.AUTO.y * u)) {
            drawText(al, topLeft = Offset(c.x + DG.AUTO.x * u - al.size.width / 2f, c.y + DG.AUTO.y * u - al.size.height / 2f), alpha = m.autoMark)
        }
    }
    val sh = Offset(-light.x, -light.y) * (u * 0.018f)
    // Power reserve hand
    val pc = Offset(c.x + DG.PR_PIVOT.x * u, c.y + DG.PR_PIVOT.y * u)
    val prDeg = DG.PR_FROM + DG.PR_SWEEP * (m.reserveH / 72f) + 90f
    hand(L.hands.pr, pc, prDeg, pal.hand, light, sh * 0.5f, 0f)
    // Small seconds (8 steps per second), chronograph 30-minute counter
    hand(L.hands.sub, Offset(c.x + DG.SMALL_SECONDS.x * u, c.y + DG.SMALL_SECONDS.y * u), hands.secondDeg, CalPal.blued, light, sh * 0.5f, 0f)
    val chronoMin = (m.chronoElapsedMs / 60_000.0) % 30.0
    hand(L.hands.sub, Offset(c.x + DG.COUNTER.x * u, c.y + DG.COUNTER.y * u), (chronoMin * 12.0).toFloat(), CalPal.blued, light, sh * 0.5f, 0f)
    // Hour and minute (from the real clock)
    val lumeA = m.night
    hand(L.hands.hour, c, hands.hourDeg, pal.hand, light, sh, lumeA)
    hand(L.hands.minute, c, hands.minuteDeg, pal.hand, light, sh * 1.3f, lumeA)
    // Chronograph seconds: a fine blued needle with a counterweight
    val chronoSec = ((m.chronoElapsedMs % 60_000L) / 125L) * 0.75f // 1/8 s steps
    rotate(chronoSec, c) {
        drawLine(Color.Black.a(0.3f), c + sh * 1.6f + Offset(0f, 0.16f * u), c + sh * 1.6f + Offset(0f, -0.93f * u), strokeWidth = u * 0.008f)
        drawLine(CalPal.blued.base, c + Offset(0f, 0.16f * u), c + Offset(0f, -0.93f * u), strokeWidth = u * 0.008f, cap = StrokeCap.Round)
        drawCircle(CalPal.blued.base, u * 0.03f, c + Offset(0f, 0.13f * u))
    }
    drawCircle(Brush.radialGradient(listOf(pal.hand.light, pal.hand.base, pal.hand.dark), c + Offset(light.x, light.y) * (u * 0.01f), u * 0.04f), u * 0.035f, c)
    drawCircle(CalPal.blued.base, u * 0.014f, c)
    // Night: indices and hands glow
    if (m.night > 0.05f) {
        val g = m.night
        for (h in DG.HOURS) {
            val a = (h * 30f - 90f) * PI.toFloat() / 180f
            val p = pol(c, u * 0.82f, a)
            drawCircle(Brush.radialGradient(listOf(CalPal.lumeGlow.a(0.35f * g), Color.Transparent), p, u * 0.09f), u * 0.09f, p)
        }
    }
}

private fun DrawScope.hand(h: Hand, pivot: Offset, deg: Float, m: Metal, light: KeyLight, shadow: Offset, lumeA: Float) {
    translate(pivot.x + shadow.x, pivot.y + shadow.y) { rotate(deg, Offset.Zero) { drawPath(h.outline, Color.Black.a(0.35f)) } }
    translate(pivot.x, pivot.y) {
        rotate(deg, Offset.Zero) {
            // Facets: the one turned toward the light is bright
            val a = (deg - 90f) * PI.toFloat() / 180f
            val side = cos(a + PI.toFloat() / 2) * light.x + sin(a + PI.toFloat() / 2) * light.y
            drawPath(h.left, if (side < 0) m.light else lerp(m.base, m.dark, 0.3f))
            drawPath(h.right, if (side >= 0) m.light else lerp(m.base, m.dark, 0.3f))
            drawPath(h.outline, m.dark.a(0.6f), style = Stroke(width = 0.8f))
            h.lume?.let {
                drawPath(it, lerp(CalPal.lumeDay, CalPal.lume, lumeA))
                if (lumeA > 0.4f) drawPath(it, CalPal.lumeGlow.a((lumeA - 0.4f) * 1.4f), style = Stroke(width = 3f))
            }
        }
    }
}
