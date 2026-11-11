package io.vroomi.character.render.calibre

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import io.vroomi.character.calibre.CalibreTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The flying tourbillon of the top step, at six o'clock (units of the movement radius). The whole escapement — the
 * balance, its hairspring, the lever and the escape wheel — rides in a cage that turns once a minute; the escape
 * pinion rolls round the fixed seconds wheel set in the plate. "Flying": the cage is held from below only, so
 * nothing covers it from this side.
 *
 * Timekeeping is the real clock's ([CalibreTime]): the cage steps with the seconds (8 beats a second, one turn a
 * minute) and the balance swings at exactly 4 Hz.
 */
internal object TB {
    /** Centre of the cage: six o'clock, on both the caseback and the dial side. */
    val C = Offset(0f, 0.58f)
    /** The opening through the plate, and the fixed seconds wheel (a ring with inner teeth) round it. */
    const val OPEN_R = 0.318f
    const val FIXED_OUT = 0.300f
    const val FIXED_IN = 0.266f
    const val FIXED_TEETH = 60
    /** The cage (its outer ring), the balance and the escape wheel riding in it. */
    const val CAGE_R = 0.238f
    const val BAL_R = 0.122f
    const val ESC_R = 0.046f
    const val ESC_AT = 0.176f
    const val ESC_TEETH = 15

    /** The cage angle (degrees) at [hands]: one turn a minute, stepping with the seconds; [mirror] -1 seen from the back. */
    fun cageDeg(hands: CalibreTime.Hands, mirror: Float): Float = hands.secondDeg * mirror
}

/** The tourbillon's moving parts, rendered once per finish layer (cage-local, drawn rotated each frame). */
internal class TourbillonParts(private val L: CalibreLayers) {
    private val u = L.u
    private val light = L.light
    private val pal = L.pal
    private val steel = CalPal.rhodium

    /** Lower frame: a ring and three spokes under the balance (only glimpsed between the parts). */
    val back: Part = L.part(TB.CAGE_R + 0.01f) { o, _ -> drawBack(o) }
    /** Balance wheel: rim, two arms, four gold timing weights. */
    val balance: Part = L.part(TB.BAL_R + 0.012f) { o, _ -> drawBalance(o) }
    /** Escape wheel (club teeth). */
    val escape: Part = L.part(TB.ESC_R + 0.008f) { o, _ -> drawEscape(o) }
    /** Upper frame: the polished ring and three curved arms meeting at a gold chaton. */
    val top: Part = L.part(TB.CAGE_R + 0.01f) { o, _ -> drawTop(o) }

    /** The lever (cage-local, around (0,0) = the cage centre), its pivot and its stones' ends. */
    val palletPivot: Offset
    val pallet: Path
    val stone1: Offset
    val stone2: Offset
    init {
        val e = Offset(TB.ESC_AT * u, 0f)
        palletPivot = Offset(0.105f * u, -0.068f * u)
        val ang = atan2(palletPivot.y - e.y, palletPivot.x - e.x)
        stone1 = pol(e, TB.ESC_R * u * 1.05f, ang + 0.85f)
        stone2 = pol(e, TB.ESC_R * u * 1.05f, ang - 0.85f)
        val toB = Offset(-palletPivot.x, -palletPivot.y)
        val len = hypot(toB.x, toB.y)
        val fork = Offset(palletPivot.x + toB.x / len * (len - 0.03f * u), palletPivot.y + toB.y / len * (len - 0.03f * u))
        pallet = union(
            capsule(palletPivot, stone1, 0.010f * u), capsule(palletPivot, stone2, 0.010f * u),
            capsule(palletPivot, fork, 0.008f * u), circlePath(palletPivot, 0.016f * u),
        )
    }

    private fun DrawScope.drawBack(o: Offset) {
        val dark = Metal(lerp(steel.base, steel.dark, 0.35f), steel.base, steel.dark)
        val ring = evenOdd(Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(o, TB.CAGE_R * u)); addOval(androidx.compose.ui.geometry.Rect(o, (TB.CAGE_R - 0.018f) * u))
        })
        drawPath(ring, Brush.radialGradient(listOf(dark.light, dark.base, dark.dark), o, TB.CAGE_R * u))
        for (k in 0 until 3) {
            val a = (k * 120f + 60f) * PI.toFloat() / 180f
            drawPath(capsule(pol(o, 0.03f * u, a), pol(o, (TB.CAGE_R - 0.01f) * u, a), 0.012f * u), dark.base)
        }
        drawCircle(dark.dark, 0.034f * u, o)
    }

    private fun DrawScope.drawBalance(o: Offset) {
        val m = pal.wheel
        val r = TB.BAL_R * u
        val rim = evenOdd(Path().apply { addOval(androidx.compose.ui.geometry.Rect(o, r)); addOval(androidx.compose.ui.geometry.Rect(o, r * 0.84f)) })
        drawPath(rim, Brush.radialGradient(0f to m.light, 0.82f to m.base, 1f to m.dark, center = o, radius = r))
        drawPath(rim, m.dark.a(0.6f), style = Stroke(width = 0.8f))
        drawPath(union(capsule(pol(o, r * 0.9f, 0f), pol(o, r * 0.9f, PI.toFloat()), 0.010f * u), circlePath(o, 0.026f * u)), m.base)
        for (k in 0 until 4) {
            val p = pol(o, r * 0.99f, (k * 90f + 45f) * PI.toFloat() / 180f)
            drawCircle(Brush.radialGradient(listOf(CalPal.yellowGold.light, CalPal.yellowGold.base, CalPal.yellowGold.dark), p, 0.016f * u), 0.013f * u, p)
        }
        drawCircle(CalPal.blackSteel.base, 0.014f * u, o)
        drawCircle(CalPal.ruby.base, 0.006f * u, Offset(o.x, o.y + 0.020f * u))
    }

    private fun DrawScope.drawEscape(o: Offset) {
        val r = TB.ESC_R * u
        val n = TB.ESC_TEETH
        val shape = Path().apply {
            for (i in 0 until n) {
                val a = i * 2f * PI.toFloat() / n
                val step = 2f * PI.toFloat() / n
                val root = pol(o, r * 0.70f, a); val tip = pol(o, r, a + step * 0.35f)
                val club = pol(o, r * 0.95f, a + step * 0.55f); val back = pol(o, r * 0.72f, a + step * 0.62f)
                if (i == 0) moveTo(root.x, root.y) else lineTo(root.x, root.y)
                lineTo(tip.x, tip.y); lineTo(club.x, club.y); lineTo(back.x, back.y)
            }
            close()
        }
        drawPath(shape, Brush.radialGradient(listOf(steel.light, steel.base, steel.dark), o, r))
        drawCircle(steel.dark.a(0.7f), r * 0.42f, o, style = Stroke(width = 1f))
        drawCircle(CalPal.blackSteel.base, r * 0.2f, o)
    }

    private fun DrawScope.drawTop(o: Offset) {
        val r = TB.CAGE_R * u
        val ring = evenOdd(Path().apply { addOval(androidx.compose.ui.geometry.Rect(o, r)); addOval(androidx.compose.ui.geometry.Rect(o, r - 0.022f * u)) })
        // Three arms leaning like a lyre from the hub to the ring.
        val arms = ArrayList<Path>()
        for (k in 0 until 3) {
            val a0 = (k * 120f - 90f) * PI.toFloat() / 180f
            val a1 = a0 + 0.55f
            val p0 = pol(o, 0.035f * u, a0); val pm = pol(o, r * 0.55f, a0 + 0.22f); val p1 = pol(o, r - 0.012f * u, a1)
            arms += union(capsule(p0, pm, 0.011f * u), capsule(pm, p1, 0.011f * u), circlePath(pm, 0.011f * u))
        }
        val frame = union(ring, *arms.toTypedArray(), circlePath(o, 0.05f * u))
        // Soft shadow on what lies under it (the balance and the depth).
        drawPath(frame, Color.Black.a(0.22f))
        drawPath(frame, Brush.radialGradient(0f to steel.light, 0.5f to steel.base, 1f to lerp(steel.base, steel.dark, 0.4f), center = o, radius = r))
        drawPath(frame, steel.dark.a(0.55f), style = Stroke(width = 0.8f))
        // A gold chaton with the cap jewel at the hub, three blued screws on the ring.
        goldChaton(o, 0.017f * u, u, light, 30f)
        for (k in 0 until 3) screw(pol(o, r - 0.011f * u, (k * 120f - 90f + 32f) * PI.toFloat() / 180f), 0.012f * u, k * 40f + 10f, light, CalPal.blued)
    }
}

/**
 * The static seat of the tourbillon (baked into the plate layer): the opening through the movement (dark, the dial
 * far below), its polished chamfer, the cage's soft shadow and the fixed seconds wheel screwed to the plate.
 */
internal fun DrawScope.drawTourbillonSeat(L: CalibreLayers) {
    val u = L.u; val light = L.light
    val c = L.p(TB.C.x, TB.C.y)
    val open = TB.OPEN_R * u
    // The opening: dark depth, a little lighter on the side the light falls into.
    drawCircle(Brush.radialGradient(
        listOf(CalPal.depth.light, CalPal.depth.base, CalPal.depth.dark),
        Offset(c.x - light.x * open * 0.35f, c.y - light.y * open * 0.35f), open * 1.3f,
    ), open, c)
    // The hovering cage's soft shadow on the depth (down and right).
    drawCircle(Brush.radialGradient(listOf(Color.Black.a(0.45f), Color.Transparent), Offset(c.x - light.x * u * 0.03f, c.y - light.y * u * 0.03f), TB.CAGE_R * u * 1.15f), TB.CAGE_R * u * 1.15f, c)
    // Polished chamfer round the opening (anglage).
    drawCircle(Brush.linearGradient(
        0f to Color.White.a(0.85f), 0.5f to Color.White.a(0.15f), 1f to Color.Black.a(0.45f),
        start = Offset(c.x - light.x * open, c.y - light.y * open), end = Offset(c.x + light.x * open, c.y + light.y * open),
    ), open + 0.006f * u, c, style = Stroke(width = 0.014f * u))
    // Fixed seconds wheel: a ring with fine inner teeth, held by three blued screws.
    val m = L.pal.wheel
    // The hole's edge is a gear outline, so the ring keeps fine teeth pointing inward between its notches.
    val ring = subtract(circlePath(c, TB.FIXED_OUT * u), gearOutline(c, TB.FIXED_IN * u, TB.FIXED_TEETH, 0.014f * u))
    drawCircle(Color.Black.a(0.25f), TB.FIXED_OUT * u * 1.02f, Offset(c.x - light.x * u * 0.008f, c.y - light.y * u * 0.008f), style = Stroke(width = 0.01f * u))
    drawPath(ring, metalBrush(m, c, TB.FIXED_OUT * u, light))
    drawCircle(m.light.a(0.5f), (TB.FIXED_OUT - 0.008f) * u, c, style = Stroke(width = 1f))
    for (k in 0 until 3) screw(pol(c, (TB.FIXED_OUT - 0.017f) * u, (k * 120f + 30f) * PI.toFloat() / 180f), 0.013f * u, k * 55f + 20f, light, CalPal.blued)
}

/**
 * The tourbillon this frame: the cage turning once a minute ([cageDeg]), the escape wheel carried round it and
 * turning on its own pinion, the lever snapping on every beat, the balance swinging at 4 Hz with a breathing
 * hairspring. [mirror] is -1 seen from the back. [spring] is a reused path.
 */
internal fun DrawScope.drawTourbillon(L: CalibreLayers, nowMs: Long, cageDeg: Float, ampDeg: Float, mirror: Float, spring: Path) {
    val tp = L.tourbillon ?: return
    val u = L.u; val light = L.light
    val c = L.p(TB.C.x, TB.C.y)
    spin(tp.back, c, cageDeg)
    val beats = CalibreTime.beats(nowMs)
    // Escape wheel: carried by the cage, and turning one tooth every two beats.
    val ea = cageDeg * PI.toFloat() / 180f
    val ep = pol(c, TB.ESC_AT * u, ea)
    spin(tp.escape, ep, cageDeg + mirror * (beats % (TB.ESC_TEETH * 2)) * (360f / (TB.ESC_TEETH * 2)))
    // The lever snaps from side to side on every beat.
    val side = if (beats % 2 == 0L) 1f else -1f
    val snap = ((Math.floorMod(nowMs, 125L)) / 14f).coerceAtMost(1f)
    rotate(cageDeg, c) {
        val pv = c + tp.palletPivot
        rotate(side * (4f * snap - 4f * (1f - snap)) * 0.5f + side * 2f, pv) {
            translateBy(c) {
                val s = CalPal.blackSteel
                drawPath(tp.pallet, Brush.linearGradient(0f to s.dark, 0.45f to s.base, 0.55f to Color(0xFFC9CED6), 1f to s.dark, start = tp.palletPivot, end = tp.stone1))
                drawLine(CalPal.ruby.base, tp.stone1, tp.stone1 + (tp.palletPivot - tp.stone1) * 0.3f, strokeWidth = 0.012f * u)
                drawLine(CalPal.ruby.base, tp.stone2, tp.stone2 + (tp.palletPivot - tp.stone2) * 0.3f, strokeWidth = 0.012f * u)
            }
        }
    }
    // Balance and hairspring: the stud rides on the cage, so the spring turns with it and breathes with the balance.
    val bal = CalibreTime.balanceDeg(nowMs, ampDeg)
    spiral(spring, c, 0.016f * u, 0.088f * u, 9f, cageDeg, bal, ampDeg)
    drawPath(spring, CalPal.hairspring, style = Stroke(width = (0.004f * u).coerceAtLeast(0.8f)))
    spin(tp.balance, c, cageDeg + bal)
    val br = TB.BAL_R * u
    drawArc(Color.White.a(0.5f), light.deg - 30f, 60f, false, Offset(c.x - br, c.y - br), Size(br * 2, br * 2), style = Stroke(width = 1.2f))
    spin(tp.top, c, cageDeg)
    // One fixed highlight on the polished ring, toward the light (the reflection does not turn with the cage).
    val rr = (TB.CAGE_R - 0.011f) * u
    drawArc(Color.White.a(0.6f), light.deg - 28f, 56f, false, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(width = (0.008f * u).coerceAtLeast(1f), cap = StrokeCap.Round))
}

private inline fun DrawScope.translateBy(o: Offset, block: DrawScope.() -> Unit) {
    drawContext.transform.translate(o.x, o.y)
    block()
    drawContext.transform.translate(-o.x, -o.y)
}

private fun DrawScope.spin(p: Part, at: Offset, deg: Float) =
    rotate(deg, at) { drawImage(p.bmp, Offset(at.x - p.half, at.y - p.half)) }

/** A hairspring of [turns] coils from [r0] to [r1] round [c]: the outer end turns with [baseDeg], the inner with the balance. */
internal fun spiral(path: Path, c: Offset, r0: Float, r1: Float, turns: Float, baseDeg: Float, balDeg: Float, ampDeg: Float) {
    path.reset()
    val n = 200
    val base = baseDeg * PI.toFloat() / 180f
    val th = balDeg * PI.toFloat() / 180f
    val breath = if (ampDeg > 0f) balDeg / ampDeg else 0f
    for (i in 0..n) {
        val s = i / n.toFloat()
        val r = (r0 + (r1 - r0) * s) * (1f + 0.07f * breath * (1f - s))
        val a = base + s * turns * 2f * PI.toFloat() + th * (1f - s)
        val x = c.x + cos(a) * r; val y = c.y + sin(a) * r
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
}
