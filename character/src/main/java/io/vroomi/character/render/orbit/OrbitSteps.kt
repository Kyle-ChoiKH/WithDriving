package io.vroomi.character.render.orbit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import io.vroomi.character.render.hash01
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The little star system's upgrade ladder (spec 5.8). Every step keeps what the one before had:
 * 1 one green phosphor (the planet, the craft, the plane's grid and an instrument column), 2 a moon and its
 * orbit, 3 two phosphors (teal for the craft's system, amber for the rest) and a small swarm of satellites on an
 * inclined orbit, 4 the planet's rings and an asteroid belt, 5 a space station with its docking orbit and a
 * second instrument column.
 */
internal object OrbitStep {
    fun moon(model: Int): Boolean = model >= 1
    fun duo(model: Int): Boolean = model >= 2
    fun rings(model: Int): Boolean = model >= 3
    fun station(model: Int): Boolean = model >= 4
}

/** The display's phosphors: [a] the craft's channel with its bright core, [b] the dim channel for everything else. */
internal class Ink(val a: Color, val aCore: Color, val b: Color, val bCore: Color)

internal val INK_MONO = Ink(Color(0xFF45D97F), Color(0xFFD6FFE4), Color(0xFF3FBF7F), Color(0xFFA6F5C6))
internal val INK_DUO = Ink(Color(0xFF3FD9F2), Color(0xFFE2FFFF), Color(0xFFE39A3A), Color(0xFFFFDFA8))

private val FINE = Stroke(1.2f, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 7f), 0f))
private val THIN = Stroke(1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val BLOOM = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val CORE = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** A circular orbit in a plane through the planet: its far and near halves (for the hidden-line pass) and points on it. */
internal class Ring(private val cx: Float, private val cy: Float, val r: Float, rollDeg: Float, private val tilt: Float) {
    private val cr = cos(rollDeg * PI.toFloat() / 180f)
    private val sr = sin(rollDeg * PI.toFloat() / 180f)
    val back = Path()
    val front = Path()
    fun x(a: Float): Float = cx + r * cos(a) * cr - r * sin(a) * tilt * sr
    fun y(a: Float): Float = cy + r * cos(a) * sr + r * sin(a) * tilt * cr
    fun isFront(a: Float): Boolean = sin(a) >= 0f

    init {
        val n = 48
        for (i in 0..n) { val a = i * PI.toFloat() / n; if (i == 0) front.moveTo(x(a), y(a)) else front.lineTo(x(a), y(a)) }
        for (i in 0..n) { val a = PI.toFloat() + i * PI.toFloat() / n; if (i == 0) back.moveTo(x(a), y(a)) else back.lineTo(x(a), y(a)) }
    }
}

/** The bodies the steps add around the planet, and the instrument columns. */
internal class OrbitExtras(private val cx: Float, private val cy: Float, private val dw: Float) {
    private val moonRing = Ring(cx, cy, 190f, -6f, 0.34f)
    private val swarmRing = Ring(cx, cy, 172f, 54f, 0.30f)
    private val planetRings = arrayOf(Ring(cx, cy, 112f, -6f, 0.34f), Ring(cx, cy, 119f, -6f, 0.34f), Ring(cx, cy, 131f, -6f, 0.34f))
    private val belt = Ring(cx, cy, 1f, -6f, 0.34f)
    private val beltR = FloatArray(BELT) { 498f + hash01(it, 201) * 52f }
    private val beltA = FloatArray(BELT) { hash01(it, 202) * 2f * PI.toFloat() }
    private val beltS = FloatArray(BELT) { 1.1f + hash01(it, 203) * 1.6f }
    private val rock = Path().apply { moveTo(-1f, -0.4f); lineTo(0.2f, -1f); lineTo(1f, -0.1f); lineTo(0.4f, 0.9f); lineTo(-0.7f, 0.7f); close() }
    private val swarmSat = Path().apply { moveTo(-2f, -2f); lineTo(2f, -2f); lineTo(2f, 2f); lineTo(-2f, 2f); close(); moveTo(-2f, 0f); lineTo(-6.5f, 0f); moveTo(2f, 0f); lineTo(6.5f, 0f) }
    private val trace = Path()

    /** The plane's grid, baked into the display: range circles and bearing lines, very faint. */
    fun DrawScope.grid(c: Color) {
        for (k in 1..5) {
            val rg = Ring(cx, cy, 120f * k, -6f, 0.34f)
            drawPath(rg.back, c.copy(alpha = 0.10f), style = THIN); drawPath(rg.front, c.copy(alpha = 0.10f), style = THIN)
        }
        val inner = Ring(cx, cy, 110f, -6f, 0.34f); val outer = Ring(cx, cy, 600f, -6f, 0.34f)
        for (k in 0 until 12) {
            val a = k * PI.toFloat() / 6f
            drawLine(c.copy(alpha = 0.08f), Offset(inner.x(a), inner.y(a)), Offset(outer.x(a), outer.y(a)), 1f)
        }
    }

    /** One half of what the steps add: [front] false before the planet is drawn, true after it. */
    fun DrawScope.bodies(model: Int, t: Float, ink: Ink, lum: Float, front: Boolean) {
        if (OrbitStep.rings(model)) {
            // The asteroid belt beyond the craft's orbit: small rocks drifting slowly around.
            for (i in 0 until BELT) {
                val a = beltA[i] + t * 0.011f * (0.8f + 0.4f * hash01(i, 204))
                if (belt.isFront(a) != front) continue
                val x = cx + (belt.x(a) - cx) * beltR[i]; val y = cy + (belt.y(a) - cy) * beltR[i]
                withTransform({ translate(x, y); scale(beltS[i], beltS[i], Offset.Zero); rotate(i * 37f, Offset.Zero) }) {
                    drawPath(rock, ink.bCore.copy(alpha = (0.34f + 0.3f * hash01(i, 205)) * lum * (if (front) 1f else 0.7f)), style = Stroke(0.7f))
                }
            }
            // The planet's rings.
            for ((k, rg) in planetRings.withIndex()) {
                val p = if (front) rg.front else rg.back
                drawPath(p, ink.b.copy(alpha = 0.16f * lum), style = BLOOM)
                drawPath(p, ink.bCore.copy(alpha = (if (k == 1) 0.5f else 0.8f) * lum * (if (front) 1f else 0.6f)), style = THIN)
            }
        }
        if (OrbitStep.moon(model)) {
            drawPath(if (front) moonRing.front else moonRing.back, ink.b.copy(alpha = 0.5f * lum * (if (front) 1f else 0.6f)), style = FINE)
            val a = 0.9f + t * 2f * PI.toFloat() / 48f
            if (moonRing.isFront(a) == front) {
                val c = Offset(moonRing.x(a), moonRing.y(a))
                drawCircle(Color.Black, 13f, c)
                drawCircle(ink.a.copy(alpha = 0.22f * lum), 12f, c, style = BLOOM)
                drawCircle(ink.aCore.copy(alpha = 0.9f * lum), 12f, c, style = CORE)
                drawCircle(ink.aCore.copy(alpha = 0.6f * lum), 3.2f, Offset(c.x - 4f, c.y - 3f), style = THIN)
                drawCircle(ink.aCore.copy(alpha = 0.6f * lum), 2f, Offset(c.x + 4.5f, c.y + 3.5f), style = THIN)
                drawArc(ink.aCore.copy(alpha = 0.5f * lum), 300f, 120f, false, Offset(c.x - 16f, c.y - 9f), Size(18f, 18f), style = THIN)
            }
        }
        if (OrbitStep.duo(model)) {
            drawPath(if (front) swarmRing.front else swarmRing.back, ink.b.copy(alpha = 0.42f * lum * (if (front) 1f else 0.6f)), style = FINE)
            for (k in 0 until 5) {
                val a = 2.2f + t * 2f * PI.toFloat() / 26f + k * 0.21f
                if (swarmRing.isFront(a) != front) continue
                val x = swarmRing.x(a); val y = swarmRing.y(a)
                withTransform({ translate(x, y); rotate(54f + 90f, Offset.Zero) }) {
                    drawPath(swarmSat, ink.b.copy(alpha = 0.25f * lum), style = BLOOM)
                    drawPath(swarmSat, ink.bCore.copy(alpha = 0.9f * lum), style = THIN)
                }
            }
        }
    }

    /** The space station at the craft's periapsis (step 5): a wheel with spokes, two solar wings, its docking orbit. */
    fun DrawScope.station(x: Float, y: Float, docked: Float, t: Float, ink: Ink, lum: Float) {
        drawOval(ink.a.copy(alpha = 0.40f * lum), Offset(x - 50f, y - 19f), Size(100f, 38f), style = FINE)
        drawOval(ink.a.copy(alpha = 0.2f * lum), Offset(x - 24f, y - 10f), Size(48f, 20f), style = BLOOM)
        drawOval(ink.aCore.copy(alpha = 0.9f * lum), Offset(x - 24f, y - 10f), Size(48f, 20f), style = CORE)
        val spin = t * 0.25f
        for (k in 0 until 4) {
            val a = spin + k * PI.toFloat() / 2f
            drawLine(ink.aCore.copy(alpha = 0.7f * lum), Offset(x, y), Offset(x + cos(a) * 24f, y + sin(a) * 10f), 1f)
        }
        drawCircle(Color.Black, 4f, Offset(x, y)); drawCircle(ink.aCore.copy(alpha = 0.9f * lum), 4f, Offset(x, y), style = CORE)
        for (s in floatArrayOf(-1f, 1f)) {
            drawLine(ink.aCore.copy(alpha = 0.8f * lum), Offset(x + s * 24f, y), Offset(x + s * 32f, y), 1.2f)
            drawRect(ink.b.copy(alpha = 0.22f * lum), Offset(x + s * 32f - (if (s < 0f) 16f else 0f), y - 6f), Size(16f, 12f))
            drawRect(ink.bCore.copy(alpha = 0.85f * lum), Offset(x + s * 32f - (if (s < 0f) 16f else 0f), y - 6f), Size(16f, 12f), style = THIN)
            drawLine(ink.bCore.copy(alpha = 0.6f * lum), Offset(x + s * 40f, y - 6f), Offset(x + s * 40f, y + 6f), 0.8f)
        }
        // Docking lamps: steady when docked, dim otherwise (never blinking).
        val lamp = 0.35f + 0.65f * docked
        drawCircle(ink.aCore.copy(alpha = lamp * lum), 1.8f, Offset(x, y - 14f)); drawCircle(ink.aCore.copy(alpha = lamp * lum), 1.8f, Offset(x, y + 14f))
    }

    /**
     * The instrument column (bottom left, clear of the app's corners): a phase dial whose hand goes round once a
     * kilometre, a speed bar (full at 110 km/h) and the altitude over one orbit with the craft's point on it.
     * Drawn instruments only, no lettering.
     */
    fun DrawScope.column(x0: Float, y0: Float, phase: Float, speed: Float, ink: Ink, lum: Float, e: Float) {
        val al = lum * e
        val c = Offset(x0 + 34f, y0 + 34f)
        drawCircle(ink.b.copy(alpha = 0.55f * al), 30f, c, style = THIN)
        for (k in 0 until 12) {
            val a = k * PI.toFloat() / 6f; val r0 = if (k % 3 == 0) 22f else 26f
            drawLine(ink.b.copy(alpha = 0.7f * al), Offset(c.x + cos(a) * r0, c.y + sin(a) * r0), Offset(c.x + cos(a) * 30f, c.y + sin(a) * 30f), 1f)
        }
        val ha = phase * 2f * PI.toFloat() - PI.toFloat() / 2f
        drawLine(ink.a.copy(alpha = 0.25f * al), c, Offset(c.x + cos(ha) * 27f, c.y + sin(ha) * 27f), 4.4f, cap = StrokeCap.Round)
        drawLine(ink.aCore.copy(alpha = 0.95f * al), c, Offset(c.x + cos(ha) * 27f, c.y + sin(ha) * 27f), 1.4f, cap = StrokeCap.Round)
        drawCircle(ink.aCore.copy(alpha = 0.9f * al), 2f, c)
        // Speed bar.
        val top = y0 + 82f; val h = 96f
        drawRect(ink.b.copy(alpha = 0.55f * al), Offset(x0 + 2f, top), Size(10f, h), style = THIN)
        for (k in 0..8) drawLine(ink.b.copy(alpha = 0.6f * al), Offset(x0 + 14f, top + h * k / 8f), Offset(x0 + (if (k % 4 == 0) 21f else 18f), top + h * k / 8f), 1f)
        val fill = h * speed.coerceIn(0f, 1f)
        drawRect(ink.a.copy(alpha = 0.30f * al), Offset(x0 + 2f, top + h - fill), Size(10f, fill))
        drawLine(ink.aCore.copy(alpha = 0.95f * al), Offset(x0, top + h - fill), Offset(x0 + 14f, top + h - fill), 1.6f)
        // Altitude over one orbit (lowest at the station, highest half a lap later) and where the craft is on it.
        val l = x0 + 30f; val w = 74f
        for ((bx, by) in listOf(l to top, l + w to top, l to top + h, l + w to top + h)) {
            val sx = if (bx == l) 1f else -1f; val sy = if (by == top) 1f else -1f
            drawLine(ink.b.copy(alpha = 0.6f * al), Offset(bx, by), Offset(bx + sx * 8f, by), 1f); drawLine(ink.b.copy(alpha = 0.6f * al), Offset(bx, by), Offset(bx, by + sy * 8f), 1f)
        }
        trace.reset()
        for (k in 0..24) {
            val u = k / 24f
            val yy = top + h - 10f - (h - 20f) * (0.5f - 0.5f * cos(u * 2f * PI.toFloat()))
            if (k == 0) trace.moveTo(l + 4f, yy) else trace.lineTo(l + 4f + (w - 8f) * u, yy)
        }
        drawPath(trace, ink.b.copy(alpha = 0.75f * al), style = THIN)
        val py = top + h - 10f - (h - 20f) * (0.5f - 0.5f * cos(phase * 2f * PI.toFloat()))
        val px = l + 4f + (w - 8f) * phase
        drawLine(ink.b.copy(alpha = 0.4f * al), Offset(px, top + 4f), Offset(px, top + h - 4f), 1f)
        drawCircle(ink.a.copy(alpha = 0.3f * al), 5f, Offset(px, py)); drawCircle(ink.aCore.copy(alpha = 0.95f * al), 2.2f, Offset(px, py))
    }

    /**
     * The second column (step 5, right, under the lap count): the range to the station as a closing bracket pair,
     * and one cell per lap of the last ten.
     */
    fun DrawScope.dockColumn(x0: Float, y0: Float, range: Float, docked: Float, laps: Int, ink: Ink, lum: Float, e: Float) {
        val al = lum * e
        val c = Offset(x0 + 52f, y0 + 34f)
        val gap = 8f + 34f * range.coerceIn(0f, 1f)
        for (s in floatArrayOf(-1f, 1f)) {
            val bx = c.x + s * gap
            drawLine(ink.aCore.copy(alpha = 0.9f * al), Offset(bx, c.y - 22f), Offset(bx, c.y + 22f), 1.4f)
            drawLine(ink.aCore.copy(alpha = 0.9f * al), Offset(bx, c.y - 22f), Offset(bx - s * 8f, c.y - 22f), 1.4f)
            drawLine(ink.aCore.copy(alpha = 0.9f * al), Offset(bx, c.y + 22f), Offset(bx - s * 8f, c.y + 22f), 1.4f)
        }
        drawCircle(ink.b.copy(alpha = 0.6f * al), 5f, c, style = THIN)
        drawLine(ink.b.copy(alpha = 0.6f * al), Offset(c.x - 46f, c.y), Offset(c.x + 46f, c.y), 1f)
        if (docked > 0.5f) drawCircle(ink.aCore.copy(alpha = 0.95f * al), 2.6f, c)
        for (k in 0 until 10) {
            val x = x0 + 4f + k * 10f
            if (k < laps % 10 || (laps > 0 && laps % 10 == 0)) drawRect(ink.bCore.copy(alpha = 0.85f * al), Offset(x, y0 + 74f), Size(6f, 12f))
            else drawRect(ink.b.copy(alpha = 0.6f * al), Offset(x, y0 + 74f), Size(6f, 12f), style = THIN)
        }
    }

    private companion object { const val BELT = 64 }
}
