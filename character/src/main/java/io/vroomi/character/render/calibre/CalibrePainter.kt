package io.vroomi.character.render.calibre

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import io.vroomi.character.render.theme.ChromeZones
import androidx.compose.ui.unit.sp
import io.vroomi.character.calibre.CalibreGeometry
import io.vroomi.character.calibre.CalibreGeometry.Wheel
import io.vroomi.character.calibre.CalibreTime
import io.vroomi.character.calibre.CalibreView
import io.vroomi.character.render.Intro
import io.vroomi.character.render.hash01
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Moving levers and small parts, built once per layer size (cache px coordinates). */
internal class MovingParts(private val L: CalibreLayers) {
    private val g = CalibreGeometry
    private val u = L.u
    private fun p(x: Float, y: Float) = L.p(x, y)

    // Lever escapement: fork from the pallet pivot to the balance roller, two arms to the escape wheel
    val palletPivot = L.p(g.PALLET)
    private val e = L.p(g.ESCAPE)
    private val b = L.p(g.BALANCE)
    val stone1: Offset
    val stone2: Offset
    val pallet: Path
    init {
        val ang = atan2(palletPivot.y - e.y, palletPivot.x - e.x)
        stone1 = pol(e, g.ESCAPE.r * u * 1.04f, ang + 0.9f)
        stone2 = pol(e, g.ESCAPE.r * u * 1.04f, ang - 0.9f)
        val toB = Offset(b.x - palletPivot.x, b.y - palletPivot.y)
        val len = hypot(toB.x, toB.y)
        val fork = Offset(palletPivot.x + toB.x / len * (len - 0.05f * u), palletPivot.y + toB.y / len * (len - 0.05f * u))
        val n = Offset(-toB.y / len, toB.x / len) * (0.022f * u)
        pallet = union(
            capsule(palletPivot, stone1, 0.016f * u), capsule(palletPivot, stone2, 0.016f * u),
            capsule(palletPivot, fork, 0.012f * u), capsule(fork + n, fork - n, 0.009f * u), circlePath(palletPivot, 0.026f * u),
        )
    }

    // Click (pawl) riding the ratchet teeth
    val clickPivot = p(0.02f, -0.56f)
    private val ratchetC = L.p(g.RATCHET)
    val clickTip = pol(ratchetC, g.RATCHET.r * u * 1.0f, -55f * PI.toFloat() / 180f)
    val click: Path = union(capsule(clickPivot, clickTip, 0.02f * u), circlePath(clickPivot, 0.032f * u))
    /** Sign of the rotation that lifts the tip away from the ratchet. */
    val clickLiftSign: Float = run {
        val v = clickTip - clickPivot
        val perp = Offset(-v.y, v.x)
        val out = clickTip - ratchetC
        if (perp.x * out.x + perp.y * out.y > 0) 1f else -1f
    }
    val clickSpring = Path().apply {
        val a = p(0.13f, -0.64f)
        val t = clickPivot + (clickTip - clickPivot) * 0.45f
        moveTo(a.x, a.y); quadraticTo(p(0.16f, -0.50f).x, p(0.16f, -0.50f).y, t.x, t.y)
    }

    // Chronograph clutch lever and reset hammer
    val clutchPivot = p(0.74f, -0.16f)
    val clutchTip: Offset = run {
        val r = L.p(g.RUNNER); val d = clutchPivot - r; val l = hypot(d.x, d.y)
        r + d * ((g.RUNNER.r + 0.035f) * u / l)
    }
    val clutch: Path = union(capsule(clutchPivot, clutchTip, 0.03f * u), circlePath(clutchPivot, 0.042f * u))
    val resetPivot = p(0.72f, -0.60f)
    val resetHeadA = L.p(g.RUNNER) + Offset(0.07f * u, -0.025f * u)
    private val headA = resetHeadA
    private val headB = L.p(g.COUNTER) + Offset(0.06f * u, 0.03f * u)
    val reset: Path = union(
        capsule(resetPivot, headA, 0.024f * u), capsule(resetPivot, headB, 0.021f * u),
        capsule(headA + Offset(0f, -0.02f * u), headA + Offset(0f, 0.02f * u), 0.014f * u),
        capsule(headB + Offset(0f, -0.02f * u), headB + Offset(0f, 0.02f * u), 0.012f * u),
        circlePath(resetPivot, 0.03f * u),
    )

    // Repeater hammers and their gongs (screen left = blinker left)
    val hammerPivotL = p(-0.22f, 0.80f)
    val hammerHeadL = pol(L.c, 0.952f * u, 114f * PI.toFloat() / 180f)
    val hammerPivotR = p(0.22f, 0.80f)
    val hammerHeadR = pol(L.c, 0.952f * u, 66f * PI.toFloat() / 180f)
    val hammerL: Path = union(capsule(hammerPivotL, hammerHeadL, 0.014f * u), circlePath(hammerHeadL, 0.03f * u), circlePath(hammerPivotL, 0.026f * u))
    val hammerR: Path = union(capsule(hammerPivotR, hammerHeadR, 0.014f * u), circlePath(hammerHeadR, 0.03f * u), circlePath(hammerPivotR, 0.026f * u))

    // Balance wheel: rim, three arms, timing screws (drawn around (0,0), rotated each frame)
    val balanceRim: Path = evenOdd(Path().apply {
        addOval(Rect(Offset.Zero, g.BALANCE.r * u)); addOval(Rect(Offset.Zero, g.BALANCE.r * u * 0.86f))
    })
    val balanceArms: Path = union(
        capsule(Offset.Zero, pol(Offset.Zero, g.BALANCE.r * u * 0.9f, 0f), 0.012f * u),
        capsule(Offset.Zero, pol(Offset.Zero, g.BALANCE.r * u * 0.9f, 2.094f), 0.012f * u),
        capsule(Offset.Zero, pol(Offset.Zero, g.BALANCE.r * u * 0.9f, 4.189f), 0.012f * u),
        circlePath(Offset.Zero, 0.04f * u),
    )
    val timingScrews: Array<Offset> = Array(12) { pol(Offset.Zero, g.BALANCE.r * u * 0.995f, it * PI.toFloat() / 6f + 0.26f) }

    // Shock-protection spring over the balance cap jewel (lyre shape)
    val shockSpring = Path().apply {
        val r = 0.05f * u
        moveTo(b.x - r, b.y + r * 0.6f); quadraticTo(b.x - r * 1.3f, b.y - r * 0.9f, b.x, b.y - r * 0.8f)
        quadraticTo(b.x + r * 1.3f, b.y - r * 0.9f, b.x + r, b.y + r * 0.6f)
    }

    // Droplets on the sapphire (fixed seeded positions)
    val drops: Array<Offset> = Array(28) {
        val a = hash01(it, 11) * 2f * PI.toFloat(); val rr = kotlin.math.sqrt(hash01(it, 12)) * 0.95f * u
        pol(L.c, rr, a)
    }
    val dropR: FloatArray = FloatArray(28) { (0.018f + 0.03f * hash01(it, 13)) * u }
}

// ------------------------------------------------------------------ the whole scene

/**
 * A macro product photograph: the watch floats over black velvet under one soft key light from the upper left,
 * falls off into black, and carries a slim engraved caption line underneath like a catalogue plate.
 */
internal fun CalibreRuntime.draw(ds: DrawScope, tm: TextMeasurer, text: CalibreText, nowMs: Long, zone: ZoneId, debugMs: Float? = null): Unit = with(ds) {
    val w = size.width; val h = size.height
    val o = lastOptions
    val m = mech
    val full = fullRadius(w, h)
    val cur = layers(full, m.tone, this, tm)
    val wx = cx.value * w; val wy = cy.value * h
    val r = full * scale.value
    val k = full / cur.u * scale.value
    studio.ensure(w, h, r, this)

    drawVelvet(ds, wx, wy, r)

    // Entrance accent: the hands glide to the real time from 10 s behind (a mechanical catch-up).
    val hands = if (o.intro < 1f) CalibreTime.hands(nowMs - ((1f - Intro.out(o.intro)) * 10_000f).toLong(), zone)
    else CalibreTime.hands(nowMs, zone)
    val flipX = abs(cos(PI.toFloat() * flip))
    val winderDeg = m.winderAngle * 180f / PI.toFloat()
    val px = if (o.reducedMotion) 0f else (m.steerSmooth * 3f) * (1f - m.freeze)
    val py = if (o.reducedMotion) 0f else (-m.accelSmooth * 0.8f).coerceIn(-4f, 4f) * (1f - m.freeze)

    fun watch(L: CalibreLayers) {
        withTransform({
            translate(wx, wy)
            rotate(winderDeg, Offset.Zero)
            scale(k * flipX.coerceAtLeast(0.02f), k, Offset.Zero)
            translate(-L.half, -L.half)
        }) {
            if (view == CalibreView.CASEBACK) drawCaseback(L, this@draw, hands, nowMs, px, py)
            else drawDial(L, this@draw, hands, text, px, py)
            if (flip < 1f) drawCircle(Color.Black.a(0.6f * (1f - flipX)), CW.HALF * L.u, L.c)
        }
    }

    if (m.toneBlend < 1f && m.prevTone != m.tone) {
        watch(layers(full, m.prevTone, this, tm))
        drawIntoCanvas { it.saveLayer(Rect(0f, 0f, w, h), Paint().apply { alpha = m.toneBlend }) }
        watch(cur)
        drawIntoCanvas { it.restore() }
    } else watch(cur)

    // The key light's colour follows the cabin: warmer with a warm set temperature, colder with the A/C.
    val warm = (m.warm * (1f - m.cool * 0.6f)).coerceIn(0f, 1f)
    val kl = Offset(wx - r * 0.9f, wy - r * 1.0f)
    translate(kl.x, kl.y) {
        drawCircle(studio.keyWarm, r * 2.6f, Offset.Zero, alpha = warm)
        drawCircle(studio.keyCold, r * 2.6f, Offset.Zero, alpha = 1f - warm)
    }

    // Combo: one full turn of light around the bevel (from the chronograph seconds), ending in a brief glint.
    val combo = (m.decoTime - m.trainGlintStart).toFloat()
    if (!o.calm && combo in 0f..1.25f) bevelSweep(this@draw, wx, wy, r, combo)

    // Night: the studio light goes out; only the lume glows, green-blue.
    if (m.night > 0.01f) {
        drawRect(Color.Black.a(0.88f * m.night))
        withTransform({ translate(wx, wy); rotate(winderDeg, Offset.Zero); scale(k * flipX.coerceAtLeast(0.02f), k, Offset.Zero); translate(-cur.half, -cur.half) }) {
            if (view == CalibreView.DIAL) drawLumeGlow(cur, hands, m.night, studio) else drawLumeRing(cur, m.night, studio)
        }
    }
    // Lens vignette.
    drawRect(studio.vignette)
    if (o.intro < 1f) {
        // Entrance accent: the modelling light comes up (exposure from black) while one glint crosses the bevel.
        val e = Intro.out(o.intro, 0f, 0.85f)
        drawRect(Color.Black.a((1f - e) * 0.92f))
        if (o.intro in 0.25f..0.9f) bevelSweep(this@draw, wx, wy, r, (o.intro - 0.25f) / 0.65f)
    }
    if (m.freeze > 0.01f) drawRect(Color.Black.a(0.30f * m.freeze))

    drawCaption(ds, text, w, h)
    debugMs?.let { val l = text.get("draw %.1f ms".format(it), h * 0.03f, Color(0xFF9FE8B9)); drawText(l, topLeft = Offset(16f, h - l.size.height - 12f)) }
}

/** Black velvet: the cloth, its pile texture, the key-light lift around the watch and the soft floating shadow. */
private fun CalibreRuntime.drawVelvet(ds: DrawScope, wx: Float, wy: Float, r: Float): Unit = with(ds) {
    drawRect(CalPal.velvet)
    translate(wx - r * 0.55f, wy - r * 0.65f) { drawCircle(studio.lift, r * 3.4f, Offset.Zero) }
    studio.pile?.let { drawRect(it) }
    translate(wx + r * 0.10f, wy + r * 0.14f) { drawCircle(studio.shadow, r * 1.75f, Offset.Zero) }
}

/** A short arc of light running once round the case bevel; [t] 0..1 is the turn, 1..1.25 the closing glint. */
private fun DrawScope.bevelSweep(rt: CalibreRuntime, wx: Float, wy: Float, r: Float, t: Float) {
    val start = ((rt.mech.chronoElapsedMs % 60_000L) / 1000f * 6f) - 90f
    // The light runs in the dark flange just inside the bezel's polished bevel, where it reads on every finish.
    val rr = r * 1.064f
    val tl = Offset(wx - rr, wy - rr); val sz = Size(rr * 2, rr * 2)
    if (t <= 1f) {
        val a = start + 360f * t
        drawArc(Color.White.a(0.30f), a - 70f, 48f, false, tl, sz, style = rt.studio.sweepTrail)
        drawArc(Color.White.a(0.9f), a - 22f, 22f, false, tl, sz, style = rt.studio.sweep)
        drawCircle(Color.White, r * 0.016f, pol(Offset(wx, wy), rr, a * PI.toFloat() / 180f))
    } else {
        val g = sin(PI.toFloat() * ((t - 1f) / 0.25f).coerceIn(0f, 1f))
        val at = pol(Offset(wx, wy), rr, start * PI.toFloat() / 180f)
        translate(at.x, at.y) { scale(r * 0.10f * g + 0.01f, r * 0.10f * g + 0.01f, Offset.Zero) { drawCircle(rt.studio.bloom, 1f, Offset.Zero, alpha = g) } }
        drawLine(Color.White.a(0.9f * g), Offset(at.x - r * 0.16f * g, at.y), Offset(at.x + r * 0.16f * g, at.y), strokeWidth = 1.2f)
    }
}

private fun DrawScope.part(p: Part, at: Offset, deg: Float) =
    rotate(deg, at) { drawImage(p.bmp, Offset(at.x - p.half, at.y - p.half)) }

// ------------------------------------------------------------------ caseback

private fun DrawScope.drawCaseback(L: CalibreLayers, rt: CalibreRuntime, hands: CalibreTime.Hands, nowMs: Long, px: Float, py: Float) {
    val m = rt.mech
    val g = CalibreGeometry
    val u = L.u; val c = L.c; val light = L.light; val pal = L.pal
    val o = rt.lastOptions
    val parts = L.parts

    // Winder cradle (EV charging): a turned gunmetal ring behind the watch
    if (m.winder > 0.01f) {
        drawCircle(metalBrush(CalPal.gunmetal, c, 1.55f * u, light).let { it }, 1.55f * u, c, alpha = m.winder)
        drawCircle(Color.Black.a(0.55f * m.winder), 1.40f * u, c)
        drawCircle(CalPal.gunmetal.light.a(0.5f * m.winder), 1.55f * u, c, style = Stroke(width = u * 0.008f))
    }
    drawImage(L.base, Offset.Zero)

    // ---- going train under the bridges (parallax 1 px)
    translate(px * 0.3f, py * 0.3f) {
        // Barrel drum and the mainspring, seen through the skeleton ratchet
        val bc = L.p(g.BARREL)
        drawCircle(lerp(pal.wheel.dark, Color.Black, 0.2f), g.BARREL.r * u * 0.93f, bc)
        drawCircle(pal.wheel.base.a(0.6f), g.BARREL.r * u * 0.93f, bc, style = Stroke(width = u * 0.01f))
        drawMainspring(rt, bc, u, m.reserveH / g.POWER_RESERVE_H, m.ratchetAngle.toFloat(), m.slipPhase, m.slipping)
        // Winding stem from the crown, under the bridges
        val stemY = c.y + g.STEM_Y * u
        drawLine(Color.Black.a(0.35f), Offset(c.x - 1.0f * u, stemY + 0.012f * u), Offset(c.x - 0.66f * u, stemY + 0.012f * u), strokeWidth = 0.03f * u)
        drawLine(Brush.verticalGradient(listOf(CalPal.rhodium.dark, CalPal.rhodium.light, CalPal.rhodium.dark), stemY - 0.013f * u, stemY + 0.013f * u), Offset(c.x - 1.0f * u, stemY), Offset(c.x - 0.66f * u, stemY), strokeWidth = 0.026f * u)
        val minDeg = hands.minuteDeg
        part(L.wheels.getValue(Wheel.CENTRE_W), L.p(g.CENTRE), -minDeg)
        part(L.wheels.getValue(Wheel.THIRD_W), L.p(g.THIRD), minDeg * 8f)
        part(L.wheels.getValue(Wheel.FOURTH_W), L.p(g.FOURTH), -hands.secondDeg)
        val chronoSec = (m.chronoElapsedMs % 60_000L) / 1000f * 6f
        part(L.wheels.getValue(Wheel.RUNNER_W), L.p(g.RUNNER), -chronoSec)
        part(L.wheels.getValue(Wheel.COUNTER_W), L.p(g.COUNTER), -((m.chronoElapsedMs / 60_000f) % 30f) * 12f)
        val beats = CalibreTime.beats(nowMs)
        part(L.escape, L.p(g.ESCAPE), (beats % 40) * 9f)
        // Pallet fork snaps from side to side on every beat
        val side = if (beats % 2 == 0L) 1f else -1f
        val snap = ((Math.floorMod(nowMs, 125L)) / 14f).coerceAtMost(1f)
        rotate(side * (4.5f * snap - 4.5f * (1f - snap)) * 0.5f + side * 2.2f, parts.palletPivot) {
            blackPolished(parts.pallet, parts.palletPivot, parts.stone1)
            drawLine(CalPal.ruby.base, parts.stone1, parts.stone1 + (parts.palletPivot - parts.stone1) * 0.25f, strokeWidth = 0.02f * u)
            drawLine(CalPal.ruby.base, parts.stone2, parts.stone2 + (parts.palletPivot - parts.stone2) * 0.25f, strokeWidth = 0.02f * u)
        }
    }

    // ---- bridges (parallax 2 px)
    translate(px * 0.6f, py * 0.6f) { drawImage(L.bridges, Offset.Zero) }

    // ---- winding works and chronograph on top of the bridges
    translate(px * 0.7f, py * 0.7f) {
        part(L.ratchet, L.p(g.RATCHET), (m.ratchetAngle * 180.0 / PI).toFloat())
        part(L.crownWheel, L.p(g.CROWN_WHEEL), (m.crownWheelAngle * 180.0 / PI).toFloat())
        rotate(parts.clickLiftSign * m.clickLift * 4.5f, parts.clickPivot) { blackPolished(parts.click, parts.clickPivot, parts.clickTip) }
        drawPath(parts.clickSpring, CalPal.rhodium.light, style = Stroke(width = (0.008f * u).coerceAtLeast(1f), cap = StrokeCap.Round))
        screw(parts.clickPivot, 0.02f * u, 60f, light)
        // Column wheel, clutch and reset hammer
        part(L.column, L.p(g.COLUMN), (m.columnAngle * 180.0 / PI).toFloat())
        rotate(if (m.chronoRunning) 0f else 3.5f, parts.clutchPivot) {
            blackPolished(parts.clutch, parts.clutchPivot, parts.clutchTip)
            drawCircle(metalBrush(pal.wheel, parts.clutchTip, 0.034f * u, light), 0.034f * u, parts.clutchTip)
        }
        screw(parts.clutchPivot, 0.022f * u, 10f, light)
        rotate(-6f * m.resetHammer + 2f, parts.resetPivot) { blackPolished(parts.reset, parts.resetPivot, parts.resetHeadA) }
        screw(parts.resetPivot, 0.02f * u, 130f, light)
        // Reversers: the driving one carries a bright ring
        val ra = (m.reverserA * 180.0 / PI).toFloat(); val rb = (m.reverserB * 180.0 / PI).toFloat()
        part(L.wheels.getValue(Wheel.REVERSER_A_W), L.p(g.REVERSER_A), ra)
        part(L.wheels.getValue(Wheel.REVERSER_B_W), L.p(g.REVERSER_B), rb)
        val drv = if (m.reverserDriver == 0) g.REVERSER_A else g.REVERSER_B
        drawCircle(Color.White.a(0.35f), drv.r * u * 1.08f, L.p(drv), style = Stroke(width = 1.4f))
        val sink = Metal(CalPal.rhodium.base, Color.White, CalPal.rhodium.dark)
        for (i in intArrayOf(7, 10, 11)) { val j = g.JEWELS[i]; jewel(L.p(j.x, j.y), j.r * u, light, sink) }
    }

    // ---- balance (4 Hz from the real clock) and its cock (parallax 3 px)
    translate(px, py) {
        val b = L.p(g.BALANCE)
        val amp = CalibreTime.amplitudeDeg(m.reserveH) * if (o.reducedMotion) 0.25f else 1f
        val bal = CalibreTime.balanceDeg(nowMs, amp)
        drawHairspring(rt, b, u, bal, amp)
        rotate(bal, b) {
            translate(b.x, b.y) {
                drawPath(parts.balanceRim, metalBrush(pal.wheel, Offset.Zero, g.BALANCE.r * u, light))
                drawPath(parts.balanceArms, pal.wheel.base)
                for (s in parts.timingScrews) drawCircle(pal.wheel.light, 0.012f * u, s)
                drawCircle(CalPal.blackSteel.base, 0.022f * u, Offset.Zero)
                drawCircle(CalPal.ruby.base, 0.007f * u, Offset(0f, 0.028f * u))
            }
        }
        val rr = g.BALANCE.r * u
        drawArc(Color.White.a(0.55f), light.deg - 35f, 70f, false, Offset(b.x - rr, b.y - rr), Size(rr * 2, rr * 2), style = Stroke(width = 1.4f))
        drawImage(L.cock, Offset.Zero)
        translate(0f, -m.shock * 0.012f * u) { drawPath(parts.shockSpring, CalPal.rhodium.light, style = Stroke(width = (0.006f * u).coerceAtLeast(1f))) }
    }

    // ---- repeater gongs and hammers (blinker, arrival chime)
    translate(px, py) {
        val wobL = if (o.calm) 0f else sin(m.decoTime * 70.0).toFloat() * m.gongL * 0.007f * u
        val wobR = if (o.calm) 0f else sin(m.decoTime * 82.0).toFloat() * m.gongR * 0.007f * u
        val gl = 0.975f * u + wobL; val gr = 0.962f * u + wobR
        drawArc(lerp(CalPal.rhodium.base, Color.White, 0.4f * m.gongL), 108f, 160f, false, Offset(c.x - gl, c.y - gl), Size(gl * 2, gl * 2), style = Stroke(width = 0.012f * u))
        drawArc(lerp(CalPal.rhodium.base, Color.White, 0.4f * m.gongR), 72f, -160f, false, Offset(c.x - gr, c.y - gr), Size(gr * 2, gr * 2), style = Stroke(width = 0.012f * u))
        rotate(-m.hammerL * 7f, parts.hammerPivotL) { blackPolished(parts.hammerL, parts.hammerPivotL, parts.hammerHeadL) }
        rotate(m.hammerR * 7f, parts.hammerPivotR) { blackPolished(parts.hammerR, parts.hammerPivotR, parts.hammerHeadR) }
        screw(parts.hammerPivotL, 0.018f * u, 20f, light); screw(parts.hammerPivotR, 0.018f * u, 160f, light)
    }

    // ---- rotor (parallax 5 px): contact shadow, the rotor, a sheen fixed to the light, the bearing
    translate(px * 1.6f, py * 1.6f) {
        val rotDeg = (m.rotorAngle * 180.0 / PI).toFloat()
        withTransform({ translate(c.x - light.x * u * 0.035f, c.y - light.y * u * 0.035f); rotate(rotDeg, Offset.Zero) }) {
            drawPath(L.rotorPath, Color.Black.a(0.38f))
        }
        withTransform({ translate(c.x - light.x * u * 0.016f, c.y - light.y * u * 0.016f); rotate(rotDeg, Offset.Zero) }) {
            drawPath(L.rotorPath, Color.Black.a(0.25f))
        }
        part(L.rotor, c, rotDeg)
        withTransform({ translate(c.x, c.y); rotate(rotDeg, Offset.Zero) }) {
            clipPath(L.rotorPath) {
                rotate(-rotDeg, Offset.Zero) { drawCircle(L.rotorSheen, CalibreGeometry.ROTOR_R * u, Offset.Zero) }
            }
            // Km marker steps one division per km driven
            val a = (-78f + (m.kmDivisions % 31f) * 5.2f) * PI.toFloat() / 180f
            drawCircle(CalPal.lumeDay, 0.013f * u, pol(Offset.Zero, 0.94f * CalibreGeometry.ROTOR_R * u, a))
        }
        // Ball bearing: outer race turns with the rotor, the balls at half speed
        val br = CalibreGeometry.BEARING_R * u
        drawCircle(metalBrush(pal.rotor, c, br, light), br, c)
        drawCircle(Color.Black.a(0.55f), br * 0.78f, c)
        for (i in 0 until 9) {
            val a = (rotDeg * 0.5f + i * 40f) * PI.toFloat() / 180f
            val bp = pol(c, br * 0.64f, a)
            drawCircle(Brush.radialGradient(listOf(Color.White, CalPal.rhodium.base, CalPal.rhodium.dark), bp + Offset(light.x, light.y) * (br * 0.04f), br * 0.16f), br * 0.13f, bp)
        }
        drawCircle(metalBrush(CalPal.rhodium, c, br * 0.45f, light), br * 0.45f, c)
        screw(c, br * 0.28f, 25f, light)
    }

    // ---- star glints travelling along the jewels
    if (!o.calm) drawGlints(L, m.decoTime, m.glintStart, m.trainGlintStart, rt.studio)

    // ---- crown (on the LEFT seen from the back) and the chronograph pushers
    drawCrownAndPushers(L, m, mirrored = true)

    // ---- sapphire crystal: condensation, rain, reflection (parallax 6 px)
    translate(px * 2f, py * 2f) { drawCrystal(L, m) }

}

/** Black-polished steel: almost black, with one mirror flash across the part. */
private fun DrawScope.blackPolished(p: Path, from: Offset, to: Offset) {
    val s = CalPal.blackSteel
    drawPath(p, Brush.linearGradient(0f to s.dark, 0.40f to s.base, 0.5f to Color(0xFFC9CED6), 0.6f to s.base, 1f to s.dark, start = from, end = to))
    drawPath(p, Color.White.a(0.35f), style = Stroke(width = 0.8f))
}

private fun DrawScope.drawMainspring(rt: CalibreRuntime, bc: Offset, u: Float, wound: Float, ratchetRad: Float, slip: Float, slipping: Boolean) {
    val path = rt.mainspring
    path.reset()
    val r0 = 0.055f * u; val r1 = 0.255f * u
    val p = 0.55f + 1.9f * wound.coerceIn(0f, 1f)
    val turns = 9f
    val n = 260
    val jitter = if (slipping) sin(slip * 2f * PI.toFloat()) * 0.04f else 0f
    for (i in 0..n) {
        val s = i / n.toFloat()
        val r = r0 + (r1 - r0) * s.pow(p)
        val a = s * turns * 2f * PI.toFloat() + ratchetRad + jitter * s
        val x = bc.x + cos(a) * r; val y = bc.y + sin(a) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, Color(0xFF8E98A8), style = Stroke(width = (0.010f * u).coerceAtLeast(1.2f)))
    drawPath(path, Color.White.a(0.35f), style = Stroke(width = (0.003f * u).coerceAtLeast(0.6f)))
}

private fun DrawScope.drawHairspring(rt: CalibreRuntime, b: Offset, u: Float, balDeg: Float, ampDeg: Float) {
    val path = rt.hairspring
    path.reset()
    val r0 = 0.03f * u; val r1 = 0.16f * u
    val turns = 11f
    val n = 320
    val th = balDeg * PI.toFloat() / 180f
    val breath = if (ampDeg > 0f) balDeg / ampDeg else 0f
    for (i in 0..n) {
        val s = i / n.toFloat()
        // The inner end turns with the balance, the outer end is held by the stud: the coils breathe.
        val r = (r0 + (r1 - r0) * s) * (1f + 0.07f * breath * (1f - s))
        val a = s * turns * 2f * PI.toFloat() + th * (1f - s)
        val x = b.x + cos(a) * r; val y = b.y + sin(a) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, CalPal.hairspring, style = Stroke(width = (0.0045f * u).coerceAtLeast(0.8f)))
}

/**
 * Reward: a ruby bearing catches the key light (a soft ruby bloom and a thin horizontal lens flare). The star
 * event lights the going train in order; the combo lights every jewel.
 */
private fun DrawScope.drawGlints(L: CalibreLayers, t: Double, star: Double, train: Double, studio: CalibreStudio) {
    val g = CalibreGeometry
    fun catchLight(at: Offset, i: Float) {
        if (i <= 0.01f) return
        val r = 0.07f * L.u * i
        translate(at.x, at.y) { scale(r, r, Offset.Zero) { drawCircle(studio.bloom, 1f, Offset.Zero, alpha = i) } }
        drawLine(Color.White.a(0.85f * i), at - Offset(r * 2.4f, 0f), at + Offset(r * 2.4f, 0f), strokeWidth = 1.1f)
        drawCircle(Color.White.a(i), r * 0.22f, at)
    }
    g.TRAIN_PATH.forEachIndexed { k, idx ->
        val x = ((t - star - k * 0.09) / 0.35).toFloat()
        if (x in 0f..1f) { val j = g.JEWELS[idx]; catchLight(L.p(j.x, j.y), sin(PI.toFloat() * x)) }
    }
    g.JEWELS.forEachIndexed { k, j ->
        val x = ((t - train - k * 0.06) / 0.3).toFloat()
        if (x in 0f..1f) catchLight(L.p(j.x, j.y), sin(PI.toFloat() * x))
    }
}

/** Night, caseback: the lume ring in the flange and the rotor's km marker glow green-blue. */
private fun DrawScope.drawLumeRing(L: CalibreLayers, night: Float, studio: CalibreStudio) {
    val c = L.c; val u = L.u
    drawCircle(CalPal.lumeHalo.a(0.35f * night), CW.LUME_RING * u, c, style = L.lumeHaloStroke)
    drawCircle(CalPal.lumeNight.a(night), CW.LUME_RING * u, c, style = L.lumeRingStroke)
}

/** Crown and pushers. [mirrored]: the caseback view puts them on the left. */
private fun DrawScope.drawCrownAndPushers(L: CalibreLayers, m: io.vroomi.character.calibre.CalibreMechanics, mirrored: Boolean) {
    val u = L.u; val c = L.c; val light = L.light; val pal = L.pal
    val sx = if (mirrored) -1f else 1f
    // Pushers at 2 and 4 o'clock (dial side)
    for ((deg, press) in listOf(-30f to m.pusher, 30f to m.resetHammer)) {
        val a = (if (mirrored) 180f - deg else deg) * PI.toFloat() / 180f
        val inset = press * 0.035f * u
        val p0 = pol(c, (CW.CASE_OUT - 0.02f) * u, a); val p1 = pol(c, (CW.CASE_OUT + 0.11f) * u - inset, a)
        drawLine(lerp(pal.case.dark, Color.Black, 0.3f), p0, p1, strokeWidth = 0.10f * u, cap = StrokeCap.Butt)
        drawLine(pal.case.base, p0, p1, strokeWidth = 0.085f * u, cap = StrokeCap.Round)
        drawLine(pal.case.light.a(0.8f), p0 + Offset(light.x, light.y) * (0.02f * u), p1 + Offset(light.x, light.y) * (0.02f * u), strokeWidth = 0.015f * u, cap = StrokeCap.Round)
    }
    // Crown: a knurled cylinder whose ridges roll across as it turns
    val y = c.y + CalibreGeometry.STEM_Y * u
    val xIn = c.x + sx * (CW.CASE_OUT - 0.02f) * u
    val xOut = c.x + sx * (CW.CASE_OUT + 0.17f) * u
    val hh = 0.12f * u
    val left = minOf(xIn, xOut); val wdt = abs(xOut - xIn)
    drawRect(lerp(pal.case.dark, Color.Black, 0.2f), Offset(left, y - hh * 0.55f), Size(wdt, hh * 1.1f))
    val cl = if (mirrored) left else left + wdt * 0.25f
    val cw = wdt * 0.75f
    val body = Rect(Offset(cl, y - hh), Size(cw, hh * 2))
    drawRoundRect(Brush.verticalGradient(listOf(pal.case.dark, pal.case.light, pal.case.base, pal.case.dark), body.top, body.bottom), body.topLeft, body.size, CornerRadius(u * 0.02f))
    val ridges = 18
    val ph = ((m.crownAngle / (2 * PI / ridges)) % 1.0).toFloat()
    for (i in 0 until ridges / 2) {
        val a = ((i + ph) / (ridges / 2f)) * PI.toFloat()
        val yy = y - cos(a) * hh * 0.94f
        drawLine(pal.case.dark.a(0.75f), Offset(body.left + 1f, yy), Offset(body.right - 1f, yy), strokeWidth = (0.008f * u).coerceAtLeast(1f))
    }
    if (m.crownGlint > 0f) {
        val gy = y - cos(m.crownGlint * PI.toFloat()) * hh
        drawLine(Color.White.a(0.8f * sin(m.crownGlint * PI.toFloat())), Offset(body.left, gy), Offset(body.right, gy), strokeWidth = 0.02f * u)
    }
}

private fun DrawScope.drawCrystal(L: CalibreLayers, m: io.vroomi.character.calibre.CalibreMechanics) {
    val u = L.u; val c = L.c; val light = L.light
    val r = CW.GASKET * u
    clipPath(L.crystal) {
        // Condensation clears from the bottom edge upward while the defroster runs
        if (m.fog > 0.01f) {
            val clearY = c.y + r - m.clear * 2f * r
            val fogH = (clearY - (c.y - r)).coerceAtLeast(0f)
            drawRect(Color(0xFFDDE4EA).a(0.55f * m.fog), Offset(c.x - r, c.y - r), Size(2 * r, (fogH - 0.06f * u).coerceAtLeast(0f)))
            // Soft, slightly brighter edge where the warm air pushes the mist up
            drawRect(Brush.verticalGradient(listOf(Color(0xFFDDE4EA).a(0.55f * m.fog), Color.White.a(0.62f * m.fog), Color.White.a(0f)), clearY - 0.06f * u, clearY + 0.0001f),
                Offset(c.x - r, clearY - 0.06f * u), Size(2 * r, 0.06f * u))
        }
        // Rain: droplets bead on the glass; one soft sheen per wiper cycle wipes and they grow back
        if (m.rain > 0.01f) {
            val cycle = ((m.decoTime % 1.6) / 1.6).toFloat()
            val sweepX = c.x - r + cycle * 2.6f * r
            val parts = L.parts
            for (i in parts.drops.indices) {
                val d = parts.drops[i]
                val since = ((sweepX - d.x) / (2.6f * r)).let { if (it < 0) it + 1f else it }
                val grow = (since * 1.8f).coerceIn(0f, 1f) * m.rain
                if (grow < 0.05f) continue
                val dr = parts.dropR[i] * (0.6f + 0.4f * grow)
                // A tiny lens: shadow on the far side, a brightened body, a dark rim, a sharp highlight
                drawCircle(Color.Black.a(0.30f * grow), dr * 1.05f, d + Offset(-light.x, -light.y) * (dr * 0.4f))
                drawCircle(Brush.radialGradient(listOf(Color.White.a(0.05f * grow), Color.White.a(0.32f * grow)), d - Offset(light.x, light.y) * (dr * 0.3f), dr), dr, d)
                drawCircle(Color(0xFF2A3440).a(0.55f * grow), dr, d, style = Stroke(width = (dr * 0.16f).coerceAtLeast(1f)))
                drawCircle(Color.White.a(0.95f * grow), dr * 0.24f, d + Offset(light.x, light.y) * (dr * 0.45f))
            }
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.a(0.16f * m.rain), Color.Transparent), sweepX - 0.25f * u, sweepX + 0.25f * u), Offset(sweepX - 0.25f * u, c.y - r), Size(0.5f * u, 2 * r))
        }
        // One soft reflection on the sapphire, toward the light
        drawArc(Color.White.a(0.09f), light.deg - 40f, 80f, false, Offset(c.x - r * 0.9f, c.y - r * 0.9f), Size(r * 1.8f, r * 1.8f), style = Stroke(width = 0.07f * u, cap = StrokeCap.Round))
    }
}

// ------------------------------------------------------------------ dial view

private fun DrawScope.drawDial(L: CalibreLayers, rt: CalibreRuntime, hands: CalibreTime.Hands, text: CalibreText, px: Float, py: Float) {
    val m = rt.mech
    drawImage(L.dial, Offset.Zero)
    translate(px * 0.5f, py * 0.5f) { drawDialFrame(L, rt, hands, text) }
    drawCrownAndPushers(L, m, mirrored = false)
    translate(px * 2f, py * 2f) { drawCrystal(L, m) }
}

private fun DrawScope.drawLumeGlow(L: CalibreLayers, hands: CalibreTime.Hands, night: Float, studio: CalibreStudio) {
    val c = L.c; val u = L.u
    for (h in DG.HOURS) {
        val a = (h * 30f - 90f) * PI.toFloat() / 180f
        val p = pol(c, u * 0.82f, a)
        translate(p.x, p.y) { scale(u * 0.10f, u * 0.10f, Offset.Zero) { drawCircle(studio.lumeHalo, 1f, Offset.Zero, alpha = night) } }
        drawLine(CalPal.lumeNight.a(night), pol(c, u * 0.76f, a), pol(c, u * 0.875f, a), strokeWidth = u * 0.016f, cap = StrokeCap.Round)
    }
    lumeHand(L.hands.hour, c, hands.hourDeg, night, studio)
    lumeHand(L.hands.minute, c, hands.minuteDeg, night, studio)
}

private fun DrawScope.lumeHand(hand: Hand, c: Offset, deg: Float, night: Float, studio: CalibreStudio) {
    translate(c.x, c.y) { rotate(deg, Offset.Zero) { hand.lume?.let { drawPath(it, CalPal.lumeNight.a(night)); drawPath(it, CalPal.lumeHalo.a(0.5f * night), style = studio.lumeEdge) } } }
}

// ------------------------------------------------------------------ catalogue caption

/**
 * The slim engraved caption under the watch, like a catalogue plate: the maker's mark in small capitals, the
 * power reserve and the chronograph (or the winder's charge). Built from cached pieces; no string per frame.
 */
private fun CalibreRuntime.drawCaption(ds: DrawScope, text: CalibreText, w: Float, h: Float): Unit = with(ds) {
    val corner = ((1f - scale.value) / 0.7f).coerceIn(0f, 1f)
    val a = (1f - corner * 2f).coerceIn(0f, 1f) * (1f - 0.55f * mech.night)
    if (a < 0.02f) return@with
    // Parked: the fine catalogue line. Driving: the values alone at 24 sp or more (cross-faded with the info spring).
    studio.caption(h, with(ds) { MIN_DRIVING_SP.sp.toPx() }, text)
    val d = info.value.coerceIn(0f, 1f)
    if (d < 0.99f) captionLine(ds, studio.parkedCaption, w, h, a * 0.75f * (1f - d), moving = false)
    if (d > 0.01f) captionLine(ds, studio.drivingCaption, w, h, a * d, moving = true)
}

/** Text shown while the car moves is never smaller than this. */
internal const val MIN_DRIVING_SP = 24

/**
 * One caption line, centred under the watch, or moved left just enough to clear the app's action corner
 * ([ChromeZones]) on a canvas where the two would meet. On a narrow screen where even that would run off the left
 * edge or under the chrome, the line sheds pieces until it fits: first the maker's mark, then the second reading
 * (the chronograph or the charge). The power reserve always stays. The choice is made from the widest value each
 * reading can show, so the line does not change shape as the seconds tick.
 */
private fun CalibreRuntime.captionLine(ds: DrawScope, st: CaptionSet, w: Float, h: Float, a: Float, moving: Boolean): Unit = with(ds) {
    val m = mech
    val sep = st.sep ?: return@with
    val gap = h * 0.022f
    val l1 = st.reserveLabel!!
    val v1 = st.hours(kotlin.math.round(m.reserveH).toInt())
    val charging = m.winder > 0.5f
    val l2 = if (charging) st.chargeLabel!! else st.chronoLabel!!
    val v2 = if (charging) st.percent(m.batteryPct ?: 0) else st.chrono(m.chronoElapsedMs / 1000)
    val small = h * 0.008f
    val y = h * 0.935f
    // Hairline rules either side, like the plate's engraved border.
    val rule = h * 0.09f
    val half = l1.size.height * 0.6f
    // The action corner (against the right edge) is what the line answers to. On a narrow landscape screen it can
    // start left of centre (the demo dock on a 1024 dp screen); a square or portrait close-up, whose corner reaches
    // past the centre, keeps the whole centred line as it always has.
    val landscape = w >= h * 1.2f
    val right = ChromeZones.current(moving, w / density, h / density).map { it.scaled(density) }
        .filter { it.right >= w - 0.5f && (landscape || it.left > w / 2f) }
    // Widest values (72시간, 29:59 or 100%) for the fitting decision.
    val v1Max = maxOf(v1.size.width, st.hours(72).size.width).toFloat()
    val v2Max = maxOf(v2.size.width, (if (charging) st.percent(100) else st.chrono(1799)).size.width).toFloat()
    fun width(mark: Boolean, second: Boolean, v1w: Float, v2w: Float): Float =
        (if (mark && st.mark != null) st.mark!!.size.width + sep.size.width + gap * 2 else 0f) + l1.size.width + small + v1w +
            (if (second) sep.size.width + gap * 2 + l2.size.width + small + v2w else 0f)
    fun place(total: Float): Float {
        var x0 = w / 2f - total / 2f
        for (zp in right) {
            if (zp.intersects(x0 - gap - rule, y - half, x0 + total + gap + rule, y + half)) x0 = minOf(x0, zp.left - total - gap - rule)
        }
        return x0
    }
    // Only the text must fit: the hairline rules are decoration and may bleed off the edge or pass under the chrome.
    fun fits(x0: Float, total: Float): Boolean =
        x0 >= 0f && x0 + total <= w && ChromeZones.clear(right, x0, y - half, x0 + total, y + half)
    var withMark = true; var second = true
    for (level in 0..2) {
        withMark = level == 0; second = level < 2
        val worst = width(withMark, second, v1Max, v2Max)
        if (fits(place(worst), worst)) break
    }
    val mark = if (withMark) st.mark else null
    val total = width(withMark, second, v1.size.width.toFloat(), v2.size.width.toFloat())
    val x0 = place(total)
    drawLine(CalibreStudio.CAPTION_DIM.a(0.6f * a), Offset(x0 - gap - rule, y), Offset(x0 - gap, y), strokeWidth = 1f)
    var x = x0
    if (mark != null) {
        drawText(mark, topLeft = Offset(x, y - mark.size.height / 2f), alpha = a); x += mark.size.width + gap
        drawText(sep, topLeft = Offset(x, y - sep.size.height / 2f), alpha = a); x += sep.size.width + gap
    }
    drawText(l1, topLeft = Offset(x, y - l1.size.height / 2f), alpha = a); x += l1.size.width + small
    drawText(v1, topLeft = Offset(x, y - v1.size.height / 2f), alpha = a); x += v1.size.width
    if (second) {
        x += gap
        drawText(sep, topLeft = Offset(x, y - sep.size.height / 2f), alpha = a); x += sep.size.width + gap
        drawText(l2, topLeft = Offset(x, y - l2.size.height / 2f), alpha = a); x += l2.size.width + small
        drawText(v2, topLeft = Offset(x, y - v2.size.height / 2f), alpha = a); x += v2.size.width
    }
    drawLine(CalibreStudio.CAPTION_DIM.a(0.6f * a), Offset(x + gap, y), Offset(x + gap + rule, y), strokeWidth = 1f)
}
