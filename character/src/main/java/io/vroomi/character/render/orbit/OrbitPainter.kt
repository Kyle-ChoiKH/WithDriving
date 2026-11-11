package io.vroomi.character.render.orbit

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.orbit.OrbitSim
import io.vroomi.character.render.Intro
import io.vroomi.character.render.hash01
import io.vroomi.character.render.theme.LayerSlot
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.character.render.theme.ThemePainter
import io.vroomi.character.render.theme.ThemeTexts
import io.vroomi.character.render.theme.drawNative
import io.vroomi.core.vehicle.model.TurnSignal
import io.vroomi.character.theme.ThemeSim
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * ORBIT, art direction: a vector phosphor display. Thin glowing lines on pure black with a soft bloom and a
 * faint scanline texture. The planet is a wireframe globe (latitude and longitude lines, hidden lines removed),
 * orbits are dashed ellipses with periapsis and apoapsis ticks, the craft is a small chevron that leaves a
 * fading phosphor trail. Motion is Keplerian: the craft runs faster at periapsis. Small monospace telemetry
 * sits in the corners. Two channels only: cyan-white for the craft system, dim green for everything else.
 */

/** The theme's private palette (two phosphors on black). */
private object OP {
    val black = Color(0xFF000000)
    var core = Color(0xFFE2FFFF)
    var cyan = Color(0xFF3FD9F2)
    var green = Color(0xFF3FBF7F)
    var greenCore = Color(0xFFA6F5C6)
    var ink = INK_DUO

    /** The phosphors of the upgrade step: one green up to step 2, teal and amber from step 3 (set before each draw). */
    fun use(model: Int) {
        ink = if (OrbitStep.duo(model)) INK_DUO else INK_MONO
        cyan = ink.a; core = ink.aCore; green = ink.b; greenCore = ink.bCore
    }
}

/** Orbit geometry: projection of the orbital plane, sampled orbits, Kepler positions. */
private class OGeo(val dw: Float) {
    val cx = dw / 2f
    val cy = 300f
    val planetR = 96f
    /** View of the orbital plane: foreshortening and a slight roll of the whole system. */
    val tilt = 0.34f
    val roll = -6f * PI.toFloat() / 180f
    val cr = cos(roll); val sr = sin(roll)
    /** The craft's orbit: semi-major axis, eccentricity, argument of periapsis. */
    val a = 330f
    val e = 0.22f
    val w = 8f * PI.toFloat() / 180f
    val b = a * sqrt(1f - e * e)
    /** The primary star, where the entrance iris opens (kept at dw / 2 − 640, 160). */
    val star = Offset(cx - 640f, 160f)

    fun moonA(level: Int) = 150f * OrbitSim.radiusRatio(level).toFloat()
    val moonE = 0.05f
    fun moonW(level: Int) = (level * 50f + 20f) * PI.toFloat() / 180f

    /** Plane (x right, y toward the viewer) → screen. */
    fun sx(px: Float, py: Float) = cx + px * cr - py * tilt * sr
    fun sy(px: Float, py: Float) = cy + px * sr + py * tilt * cr

    // Sampled orbits (screen x, y, and plane y for the hidden-line test): index 0 = craft, 1..4 = moon levels.
    val n = 180
    val ox = Array(OrbitSim.LEVELS + 1) { FloatArray(n + 1) }
    val oy = Array(OrbitSim.LEVELS + 1) { FloatArray(n + 1) }
    val oz = Array(OrbitSim.LEVELS + 1) { FloatArray(n + 1) }
    val back = Array(OrbitSim.LEVELS + 1) { Path() }
    val front = Array(OrbitSim.LEVELS + 1) { Path() }
    /**
     * The same halves as dashes (the craft's orbit 6 on 6 off, the moonlets' 3 on 7 off), cut once here: a dash
     * effect is worked out again on every draw, a path of plain dashes is drawn from the renderer's cache.
     */
    val backDash = Array(OrbitSim.LEVELS + 1) { Path() }
    val frontDash = Array(OrbitSim.LEVELS + 1) { Path() }

    init {
        for (o in 0..OrbitSim.LEVELS) {
            val aa = if (o == 0) a else moonA(o - 1); val ee = if (o == 0) e else moonE; val ww = if (o == 0) w else moonW(o - 1)
            val bb = aa * sqrt(1f - ee * ee)
            var lastFront: Boolean? = null
            for (i in 0..n) {
                val ea = i * 2f * PI.toFloat() / n
                val qx = aa * (cos(ea) - ee); val qy = bb * sin(ea)
                val px = qx * cos(ww) - qy * sin(ww); val py = qx * sin(ww) + qy * cos(ww)
                ox[o][i] = sx(px, py); oy[o][i] = sy(px, py); oz[o][i] = py
                val isFront = py >= 0f
                val path = if (isFront) front[o] else back[o]
                if (lastFront != isFront) { if (i > 0) path.moveTo(ox[o][i - 1], oy[o][i - 1]) else path.moveTo(ox[o][i], oy[o][i]) }
                path.lineTo(ox[o][i], oy[o][i])
                lastFront = isFront
            }
            val on = if (o == 0) 6f else 3f; val off = if (o == 0) 6f else 7f
            dashes(o, true, on, off, frontDash[o]); dashes(o, false, on, off, backDash[o])
        }
    }

    /** Walks the sampled orbit [o] and adds its [wantFront] half to [out] as dashes; the pattern restarts with each run. */
    private fun dashes(o: Int, wantFront: Boolean, on: Float, off: Float, out: Path) {
        var pos = 0f          // the distance into the present on + off period
        var running = false
        for (i in 1..n) {
            if ((oz[o][i] >= 0f) != wantFront) { running = false; continue }
            if (!running) { pos = 0f; running = true }
            val x0 = ox[o][i - 1]; val y0 = oy[o][i - 1]; val x1 = ox[o][i]; val y1 = oy[o][i]
            val len = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
            if (len <= 0f) continue
            var d = 0f
            while (d < len) {
                val inOn = pos < on
                val step = min(len - d, (if (inOn) on else on + off) - pos)
                if (inOn) {
                    out.moveTo(x0 + (x1 - x0) * d / len, y0 + (y1 - y0) * d / len)
                    out.lineTo(x0 + (x1 - x0) * (d + step) / len, y0 + (y1 - y0) * (d + step) / len)
                }
                d += step; pos += step
                if (pos >= on + off - 1e-4f) pos = 0f
            }
        }
    }

    // Apsides of the craft's orbit (screen) and a unit outward direction for their ticks.
    val periX = sx(a * (1f - e) * cos(w), a * (1f - e) * sin(w)); val periY = sy(a * (1f - e) * cos(w), a * (1f - e) * sin(w))
    val apoX = sx(-a * (1f + e) * cos(w), -a * (1f + e) * sin(w)); val apoY = sy(-a * (1f + e) * cos(w), -a * (1f + e) * sin(w))

    /** Faint points (a few catalogued stars the display plots) in the dim channel. */
    val dotX = FloatArray(70) { hash01(it, 3) * dw }
    val dotY = FloatArray(70) { 20f + hash01(it, 4) * 560f }
    val windX = FloatArray(30) { hash01(it, 71) }
    val windY = FloatArray(30) { 40f + hash01(it, 72) * 520f }
    /** What the upgrade steps add (OrbitSteps.kt). */
    val extras = OrbitExtras(cx, cy, dw)
    val vignette = Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), Offset(cx, 300f), max(dw, 1080f) * 0.62f)
}

/** The orbit painter: a vector phosphor display of a little star system. */
internal class OrbitPainter : ThemePainter<OrbitSim>() {
    override val minDesignWidth = 1080f
    override val focusWidth = 760f
    override fun headroom(sim: OrbitSim): Color = Color(0xFF000000)
    override val backdrop = Color(0xFF000000)

    private var geo: OGeo? = null
    private val base = LayerSlot(SceneTheme.ORBIT)
    private val glass = LayerSlot(SceneTheme.ORBIT)
    /** The steady light the steps add behind the planet (stars, nebulae, the dust band, the halo): drawn once. */
    private val lights = LayerSlot(SceneTheme.ORBIT)

    private val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
    private val dashFine = PathEffect.dashPathEffect(floatArrayOf(3f, 7f), 0f)
    private val bloom = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val core = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val bloomDash = Stroke(6f, cap = StrokeCap.Butt, pathEffect = dash)
    private val coreDash = Stroke(1.5f, cap = StrokeCap.Butt, pathEffect = dash)
    private val coreFine = Stroke(1.2f, cap = StrokeCap.Butt, pathEffect = dashFine)
    // Plain strokes for the orbits that are already cut into dashes (OGeo.frontDash, backDash).
    private val bloomCut = Stroke(6f, cap = StrokeCap.Butt)
    private val coreCut = Stroke(1.5f, cap = StrokeCap.Butt)
    private val fineCut = Stroke(1.2f, cap = StrokeCap.Butt)
    private val chevron = Path().apply { moveTo(7f, 0f); lineTo(-6f, -5.5f); lineTo(-2.5f, 0f); lineTo(-6f, 5.5f); close() }
    private val sat = Path().apply {
        moveTo(-2.5f, -2.5f); lineTo(2.5f, -2.5f); lineTo(2.5f, 2.5f); lineTo(-2.5f, 2.5f); close()
        moveTo(-2.5f, 0f); lineTo(-9f, 0f); moveTo(2.5f, 0f); lineTo(9f, 0f)
        moveTo(-9f, -2f); lineTo(-9f, 2f); moveTo(9f, -2f); lineTo(9f, 2f)
    }
    private val hex = Path().apply { for (i in 0..6) { val a = i * PI.toFloat() / 3f; if (i == 0) moveTo(cos(a) * 9f, sin(a) * 9f) else lineTo(cos(a) * 9f, sin(a) * 9f) } }
    private val globeFront = Path()
    private val globeBack = Path()
    // The globe's lines are kept between frames: the latitudes never change, and the meridians are worked out
    // again only when the planet has turned far enough to show (a path left alone is drawn from the renderer's cache).
    private val latFront = Path()
    private val latBack = Path()
    private var globeFor: OGeo? = null
    private var globeSpin = Double.NaN
    private val trace = Path()
    private val tmp = FloatArray(4)
    private val chars = CharArray(8)
    private var paint: Paint? = null
    private var glowPaint: Paint? = null

    /** The entrance accent of this frame ([io.vroomi.character.render.SceneOptions.intro]). */
    private var intro = 1f
    /** Phosphor brightness of this frame (dimmer at night). */
    private var lum = 1f

    private fun geo(dw: Float): OGeo {
        val g = geo
        if (g != null && abs(g.dw - dw) < 0.5f) return g
        return OGeo(dw).also { geo = it }
    }

    override fun drawPx(s: DrawScope, sim: OrbitSim, f: ThemeFrame, tx: ThemeTexts, left: Float, top: Float) {
        val g = geo(f.dw)
        OP.use(f.model)
        val w = f.w.roundToInt(); val h = f.h.roundToInt(); val k = f.k
        val bmp = base.get(((w.toLong() shl 20) xor h.toLong()) * 8L + f.model, w, h, f.density) { withTransform({ scale(k, k, Offset.Zero) }) { screen(g, f) } }
        s.drawImage(bmp, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(bmp.width, bmp.height))
        if (OrbitStep.moon(f.model)) {
            val model = f.model
            val l = lights.get(((w.toLong() shl 20) xor h.toLong()) * 8L + model, w, h, f.density) {
                withTransform({ scale(k, k, Offset.Zero) }) { with(g.extras) { light(model, g.planetR, OP.ink, 1f, front = false) } }
            }
            // The phosphor is dimmer at night: the layer is drawn at that brightness.
            s.drawImage(l, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(l.width, l.height), alpha = 1f - 0.32f * sim.night)
        }
    }

    override fun draw(s: DrawScope, sim: OrbitSim, f: ThemeFrame, tx: ThemeTexts) = with(s) {
        intro = f.options.intro
        lum = 1f - 0.32f * sim.night
        val g = geo(f.dw)
        val model = f.model
        OP.use(model)
        val st = if (sim.reducedMotion) 0f else sim.decoTime.toFloat() * (if (sim.calm) 0.5f else 1f)
        wind(g, sim, f)
        star(g, sim)
        meteor(g, sim)
        // Back halves (behind the planet's limb they are hidden by its black disc).
        // (The steady light behind the planet is a cached layer, drawn in drawPx.)
        with(g.extras) { bodies(model, st, OP.ink, lum, front = false) }
        orbitLines(g, sim, front = false)
        satellites(g, sim, front = false)
        craft(g, sim, front = false)
        globe(g, sim)
        with(g.extras) { light(model, g.planetR, OP.ink, lum, front = true); bodies(model, st, OP.ink, lum, front = true) }
        orbitLines(g, sim, front = true)
        markerRing(g, sim)
        station(g, sim, model, st)
        satellites(g, sim, front = true)
        craft(g, sim, front = true)
        telemetry(g, sim, f)
        // The instrument columns: drawn gauges without lettering, so they stay while the car moves.
        val e = if (intro < 1f) Intro.out(intro, 0.4f, 0.6f) else 1f
        val phase = (((sim.angle - OrbitSim.STATION) / (2.0 * PI)) % 1.0).let { if (it < 0) it + 1.0 else it }.toFloat()
        with(g.extras) {
            column(44f, f.dh - 330f, phase, min(sim.kmh, 110f) / 110f, OP.ink, lum, e)
            if (OrbitStep.station(model)) dockColumn(f.dw - 150f, 150f, min(phase, 1f - phase) * 2f, sim.docked, sim.laps, OP.ink, lum, e)
        }
        val k = f.k
        val img = glass.get((f.w.roundToInt().toLong() shl 20) xor f.h.roundToInt().toLong(), f.w.roundToInt(), f.h.roundToInt(), f.density) { scanlines(g, f, k) }
        drawNative(img, f, 0f, 0f)
    }

    // ------------------------------------------------------------------ static

    private fun DrawScope.screen(g: OGeo, f: ThemeFrame) {
        drawRect(OP.black, size = Size(f.dw, f.dh))
        // Corner brackets of the display's plotting area.
        val m = 26f; val l = 34f
        for ((x, y, sx, sy) in listOf(Quad(m, m, 1f, 1f), Quad(f.dw - m, m, -1f, 1f), Quad(m, f.dh - m, 1f, -1f), Quad(f.dw - m, f.dh - m, -1f, -1f))) {
            drawLine(OP.green.copy(alpha = 0.35f), Offset(x, y), Offset(x + sx * l, y), 1f)
            drawLine(OP.green.copy(alpha = 0.35f), Offset(x, y), Offset(x, y + sy * l), 1f)
        }
        // The orbital plane's grid: range circles and bearing lines.
        with(g.extras) { grid(OP.green) }
        // A centre reticle, very faint.
        drawLine(OP.green.copy(alpha = 0.10f), Offset(g.cx - 14f, f.dh - 46f), Offset(g.cx + 14f, f.dh - 46f), 1f)
        for (i in 0 until 9) drawLine(OP.green.copy(alpha = 0.16f), Offset(g.cx - 120f + i * 30f, f.dh - 50f), Offset(g.cx - 120f + i * 30f, f.dh - 42f), 1f)
        for (i in g.dotX.indices) drawCircle(OP.greenCore.copy(alpha = 0.10f + 0.25f * hash01(i, 5)), 0.9f, Offset(g.dotX[i], g.dotY[i]))
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

    /** Scanlines in device pixels and a soft tube falloff at the edges (drawn over everything). */
    private fun DrawScope.scanlines(g: OGeo, f: ThemeFrame, k: Float) {
        var y = 0f
        // Scanlines stay very faint (4 %): they are a texture of the tube, not a pattern over the picture.
        while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.04f), Offset(0f, y), Size(size.width, 1f)); y += 3f }
        withTransform({ scale(k, k, Offset.Zero) }) { drawRect(g.vignette, size = Size(f.dw, f.dh)) }
    }

    // ------------------------------------------------------------------ helpers

    /** A glowing stroke: a wide faint bloom under a thin bright core. */
    private fun DrawScope.glowLine(c: Color, a: Float, p0: Offset, p1: Offset, w: Float = 1.3f) {
        drawLine(c.copy(alpha = (0.22f * a * lum).coerceIn(0f, 1f)), p0, p1, w * 3.6f, cap = StrokeCap.Round)
        drawLine(OP.core.copy(alpha = (0.9f * a * lum).coerceIn(0f, 1f)), p0, p1, w, cap = StrokeCap.Round)
    }

    private fun DrawScope.glowPath(p: Path, c: Color, a: Float, dashed: Boolean = false, coreColor: Color = OP.core) {
        drawPath(p, c.copy(alpha = (0.20f * a * lum).coerceIn(0f, 1f)), style = if (dashed) bloomDash else bloom)
        drawPath(p, coreColor.copy(alpha = (0.85f * a * lum).coerceIn(0f, 1f)), style = if (dashed) coreDash else core)
    }

    /** Kepler: position of mean anomaly [m] on orbit [o] → tmp = (screen x, screen y, plane y, heading deg). */
    private fun kepler(g: OGeo, o: Int, m: Double, retro: Boolean = false) {
        val aa = if (o == 0) g.a else g.moonA(o - 1); val ee = if (o == 0) g.e else g.moonE; val ww = if (o == 0) g.w else g.moonW(o - 1)
        val bb = aa * sqrt(1f - ee * ee)
        var ea = m
        repeat(5) { ea -= (ea - ee * sin(ea) - m) / (1.0 - ee * cos(ea)) }
        val ce = cos(ea).toFloat(); val se = sin(ea).toFloat()
        val qx = aa * (ce - ee); val qy = bb * se
        val cw = cos(ww); val sw = sin(ww)
        val px = qx * cw - qy * sw; val py = qx * sw + qy * cw
        tmp[0] = g.sx(px, py); tmp[1] = g.sy(px, py); tmp[2] = py
        // Velocity direction (dE), projected.
        var vx = -aa * se; var vy = bb * ce
        if (retro) { vx = -vx; vy = -vy }
        val dpx = vx * cw - vy * sw; val dpy = vx * sw + vy * cw
        val dsx = dpx * g.cr - dpy * g.tilt * g.sr; val dsy = dpx * g.sr + dpy * g.tilt * g.cr
        tmp[3] = atan2(dsy, dsx) * 180f / PI.toFloat()
    }

    // ------------------------------------------------------------------ moving

    private fun DrawScope.star(g: OGeo, sim: OrbitSim) {
        val c = g.star
        drawCircle(OP.cyan.copy(alpha = 0.10f * lum), 26f, c)
        drawCircle(OP.cyan.copy(alpha = 0.22f * lum), 12f, c, style = bloom)
        drawCircle(OP.core.copy(alpha = 0.9f * lum), 12f, c, style = core)
        for (i in 0 until 12) {
            val a = i * PI.toFloat() / 6f
            val r0 = 17f; val r1 = if (i % 3 == 0) 30f else 23f
            glowLine(OP.cyan, 0.8f, Offset(c.x + cos(a) * r0, c.y + sin(a) * r0), Offset(c.x + cos(a) * r1, c.y + sin(a) * r1), 1f)
        }
    }

    private fun DrawScope.orbitLines(g: OGeo, sim: OrbitSim, front: Boolean) {
        val e = if (intro < 1f) Intro.out(intro, 0f, 0.75f) else 1f
        // Moonlet orbits in the dim channel (only those with a satellite on them).
        for (lvl in 0 until OrbitSim.LEVELS) {
            if (sim.moonlets <= lvl) break
            val born = ((sim.decoTime - sim.moonBornAt[lvl]) / 1.6).toFloat().coerceIn(0f, 1f)
            val reach = min(born, if (intro < 1f) Intro.out(intro, 0.15f + lvl * 0.08f, 0.6f) else 1f)
            if (reach >= 1f) drawPath(if (front) g.frontDash[lvl + 1] else g.backDash[lvl + 1], OP.green.copy(alpha = (0.55f * lum) * (if (front) 1f else 0.6f)), style = fineCut)
            else partialOrbit(g, lvl + 1, reach, front, OP.green, 0.55f)
        }
        // The craft's orbit, with its apsides ticked.
        val a = if (front) 0.75f else 0.4f
        if (e >= 1f) {
            val p = if (front) g.frontDash[0] else g.backDash[0]
            drawPath(p, OP.cyan.copy(alpha = (0.20f * a * lum).coerceIn(0f, 1f)), style = bloomCut)
            drawPath(p, OP.core.copy(alpha = (0.85f * a * lum).coerceIn(0f, 1f)), style = coreCut)
        }
        else partialOrbit(g, 0, e, front, OP.cyan, a)
        if (front) {
            apsis(g, g.periX, g.periY, true, e)
            apsis(g, g.apoX, g.apoY, false, e)
        }
    }

    /** An apsis tick across the orbit; periapsis also gets a short cross bar outside it. */
    private fun DrawScope.apsis(g: OGeo, x: Float, y: Float, peri: Boolean, e: Float) {
        val dx = (x - g.cx); val dy = (y - g.cy); val d = max(1f, sqrt(dx * dx + dy * dy))
        val ux = dx / d; val uy = dy / d
        glowLine(OP.cyan, 0.8f * e, Offset(x - ux * 7f, y - uy * 7f), Offset(x + ux * 7f, y + uy * 7f), 1.2f)
        if (peri) glowLine(OP.cyan, 0.8f * e, Offset(x + ux * 10f - uy * 4f, y + uy * 10f + ux * 4f), Offset(x + ux * 10f + uy * 4f, y + uy * 10f - ux * 4f), 1f)
        else { glowLine(OP.cyan, 0.8f * e, Offset(x - ux * 10f - uy * 4f, y - uy * 10f + ux * 4f), Offset(x - ux * 10f + uy * 4f, y - uy * 10f - ux * 4f), 1f) }
    }

    /** Entrance and birth: an orbit strokes on from its start (a phosphor beam tracing it once). */
    private fun DrawScope.partialOrbit(g: OGeo, o: Int, reach: Float, front: Boolean, c: Color, a: Float) {
        val last = (g.n * reach).toInt()
        if (last < 1) return
        trace.reset()
        var open = false
        for (i in 0..last) {
            val isFront = g.oz[o][i] >= 0f
            if (isFront != front) { open = false; continue }
            if (!open) { trace.moveTo(g.ox[o][i], g.oy[o][i]); open = true } else trace.lineTo(g.ox[o][i], g.oy[o][i])
        }
        if (o == 0) glowPath(trace, c, a, dashed = true) else drawPath(trace, c.copy(alpha = a * lum), style = coreFine)
        // The beam's head.
        val hx = g.ox[o][last]; val hy = g.oy[o][last]
        if ((g.oz[o][last] >= 0f) == front) drawCircle(OP.core.copy(alpha = 0.9f * lum), 1.8f, Offset(hx, hy))
    }

    /** The wireframe globe: latitudes and meridians on a sphere seen from a little above, hidden lines removed. */
    private fun DrawScope.globe(g: OGeo, sim: OrbitSim) {
        val r = g.planetR
        val spin = if (sim.reducedMotion) 0.0 else sim.decoTime * 0.06 * (if (sim.calm) 0.5 else 1.0)
        val elev = 0.38f
        val ce = cos(elev); val se = sin(elev)
        if (globeFor !== g) {
            globeFor = g; globeSpin = Double.NaN
            latFront.reset(); latBack.reset()
            // Latitudes every 30°.
            for (li in -2..2) {
                val lat = li * PI.toFloat() / 6f
                var lastF: Boolean? = null
                for (j in 0..48) {
                    val lon = j * 2f * PI.toFloat() / 48f
                    project(g, lat, lon, ce, se)
                    val f = tmp[2] >= 0f
                    val p = if (f) latFront else latBack
                    if (lastF != f) p.moveTo(tmp[0], tmp[1]) else p.lineTo(tmp[0], tmp[1])
                    lastF = f
                }
            }
        }
        // Meridians every 30°, turning with the planet (again only after a turn of half a pixel at the limb).
        if (globeSpin.isNaN() || abs(spin - globeSpin) * r >= 0.5) {
            globeSpin = spin
            globeFront.reset(); globeBack.reset()
            for (mi in 0 until 6) {
                val lon = (mi * PI / 6 + spin).toFloat()
                for (half in 0..1) {
                    val lo = lon + half * PI.toFloat()
                    var lastF: Boolean? = null
                    for (j in 0..24) {
                        val lat = -PI.toFloat() / 2f + j * PI.toFloat() / 24f
                        project(g, lat, lo, ce, se)
                        val f = tmp[2] >= 0f
                        val p = if (f) globeFront else globeBack
                        if (lastF != f) p.moveTo(tmp[0], tmp[1]) else p.lineTo(tmp[0], tmp[1])
                        lastF = f
                    }
                }
            }
        }
        val c = Offset(g.cx, g.cy)
        // The black disc hides whatever passes behind the planet.
        drawCircle(OP.black, r + 2f, c)
        drawPath(latBack, OP.green.copy(alpha = 0.16f * lum), style = core)
        drawPath(globeBack, OP.green.copy(alpha = 0.16f * lum), style = core)
        glowPath(latFront, OP.cyan, 0.62f)
        glowPath(globeFront, OP.cyan, 0.62f)
        // Limb and a faint atmosphere ring.
        drawCircle(OP.cyan.copy(alpha = 0.22f * lum), r, c, style = bloom)
        drawCircle(OP.core.copy(alpha = 0.95f * lum), r, c, style = core)
        drawCircle(OP.cyan.copy(alpha = 0.18f * lum), r + 7f, c, style = core)
    }

    /** Sphere point (lat, lon) seen from elevation (cos, sin) → tmp = (x, y, depth). */
    private fun project(g: OGeo, lat: Float, lon: Float, ce: Float, se: Float) {
        val r = g.planetR
        val x = cos(lat) * sin(lon); val y = sin(lat); val z = cos(lat) * cos(lon)
        val y2 = y * ce - z * se; val z2 = y * se + z * ce
        val rx = x * g.cr - y2 * g.sr; val ry = x * g.sr + y2 * g.cr
        tmp[0] = g.cx + rx * r; tmp[1] = g.cy - ry * r; tmp[2] = z2
    }

    /** The combo: a ring of 24 orbit markers lights in order and completes. */
    private fun DrawScope.markerRing(g: OGeo, sim: OrbitSim) {
        if (sim.ringGlow < 0.01f) return
        val lit = ((sim.since(sim.ringAt) / 1.6).toFloat() * 24f).toInt().coerceIn(0, 24)
        val rr = 190f
        for (i in 0 until 24) {
            val a = i * 2f * PI.toFloat() / 24f
            val px = cos(a) * rr; val py = sin(a) * rr
            val x = g.sx(px, py); val y = g.sy(px, py)
            val on = i < lit
            val al = (if (on) 0.9f else 0.25f) * sim.ringGlow
            // A radial marker (in the projected plane) at each of the 24 stations of the ring.
            val ox = g.sx(px * 1.05f, py * 1.05f) - x; val oy = g.sy(px * 1.05f, py * 1.05f) - y
            if (on) glowLine(OP.cyan, al, Offset(x - ox, y - oy), Offset(x + ox, y + oy), 1.2f)
            else drawLine(OP.green.copy(alpha = al * lum), Offset(x - ox * 0.6f, y - oy * 0.6f), Offset(x + ox * 0.6f, y + oy * 0.6f), 1f)
        }
    }

    private fun DrawScope.station(g: OGeo, sim: OrbitSim, model: Int, st: Float) {
        // The ring station waits just outside periapsis (where the craft docks).
        kepler(g, 0, OrbitSim.STATION)
        val x = tmp[0]; val y = tmp[1] - 22f
        if (OrbitStep.station(model)) with(g.extras) { station(x, y - 8f, sim.docked, st, OP.ink, lum) }
        else {
            withTransform({ translate(x, y) }) { glowPath(hex, OP.cyan, 0.7f) }
            glowLine(OP.cyan, 0.6f, Offset(x, y + 9f), Offset(x, y + 16f), 1f)
            val lampA = 0.35f + 0.65f * sim.docked
            drawCircle(OP.core.copy(alpha = lampA * lum), 1.6f, Offset(x - 13f, y)); drawCircle(OP.core.copy(alpha = lampA * lum), 1.6f, Offset(x + 13f, y))
        }
        // Lap ticks: one per km, stacked beside the station.
        for (i in 0 until min(sim.laps, 20)) {
            val tx = x + (if (OrbitStep.station(model)) 58f else 22f) + i * 4.5f
            drawLine(OP.green.copy(alpha = 0.8f * lum), Offset(tx, y - 5f), Offset(tx, y + 5f), 1.2f)
        }
    }

    /** Satellites (the reward): each deploys from the craft into its own orbit and then keeps its Kepler period. */
    private fun DrawScope.satellites(g: OGeo, sim: OrbitSim, front: Boolean) {
        for (k in 0 until sim.moonlets) {
            val lvl = k % OrbitSim.LEVELS
            kepler(g, lvl + 1, sim.moonAngle[k])
            if ((tmp[2] >= 0f) != front) continue
            var x = tmp[0]; var y = tmp[1]
            val born = ((sim.decoTime - sim.moonBornAt[k]) / 1.6).toFloat().coerceIn(0f, 1f)
            if (born < 1f || intro < 1f) {
                // Deploying: it slides out from the planet's side toward its slot.
                val e = min(Intro.out(born, 0f, 1f), if (intro < 1f) Intro.out(intro, 0.2f, 0.8f) else 1f)
                x = g.cx + (x - g.cx) * (0.6f + 0.4f * e); y = g.cy + (y - g.cy) * (0.6f + 0.4f * e)
            }
            val ang = atan2(y - g.cy, x - g.cx) * 180f / PI.toFloat() + 90f
            withTransform({ translate(x, y); rotate(ang, Offset.Zero) }) { glowPath(sat, OP.green, 0.9f, coreColor = OP.greenCore) }
        }
    }

    private fun DrawScope.craft(g: OGeo, sim: OrbitSim, front: Boolean) {
        val m = sim.angle - OrbitSim.STATION
        // Phosphor persistence: the last stretch of the orbit fades behind the craft.
        val dir = if (sim.retrograde) -1.0 else 1.0
        if (!sim.reducedMotion) {
            var px = 0f; var py = 0f; var pz = 0f
            for (i in 0..22) {
                kepler(g, 0, m - dir * i * 0.018)
                if (i > 0 && (pz >= 0f) == front) {
                    val a = (1f - i / 22f)
                    drawLine(OP.cyan.copy(alpha = 0.35f * a * lum), Offset(px, py), Offset(tmp[0], tmp[1]), 3.2f, cap = StrokeCap.Round)
                    drawLine(OP.core.copy(alpha = 0.6f * a * lum), Offset(px, py), Offset(tmp[0], tmp[1]), 1.1f, cap = StrokeCap.Round)
                }
                px = tmp[0]; py = tmp[1]; pz = tmp[2]
            }
        }
        kepler(g, 0, m, sim.retrograde)
        if ((tmp[2] >= 0f) != front) return
        val x = tmp[0]; val y = tmp[1]
        val ang = tmp[3] + sim.wobble() * 57.3f
        val sc = 2.1f
        withTransform({ translate(x, y); rotate(ang, Offset.Zero); scale(sc, sc, Offset.Zero) }) {
            glowPath(chevron, OP.cyan, 1f)
            // Thrust vector (acceleration), retro vector (braking), RCS ticks (blinker), solar arrays (charging).
            if (sim.plume > 0.01f) glowLine(OP.cyan, sim.plume, Offset(-4f, 0f), Offset(-4f - 6f - 22f * sim.plume, 0f), 0.9f)
            if (sim.retro > 0.02f) for (s in SIDES) glowLine(OP.cyan, sim.retro, Offset(8f, s * 2.5f), Offset(8f + 8f * sim.retro, s * 3.5f), 0.8f)
            val rcs = sim.rcs()
            if (rcs > 0.01f) {
                val side = when (sim.rcsSide) { TurnSignal.LEFT -> -1f; TurnSignal.RIGHT -> 1f; else -> 0f }
                for (s in SIDES) if (sim.rcsSide == TurnSignal.HAZARD || s == side) glowLine(OP.cyan, rcs, Offset(0f, s * 4f), Offset(0f, s * (4f + 6f * rcs)), 0.8f)
            }
            val u = sim.panels
            if (u > 0.02f) {
                val span = 3f + 13f * u
                for (s in SIDES) {
                    glowLine(OP.green, 0.9f, Offset(-2f, s * 5f), Offset(-2f, s * (5f + span)), 0.8f)
                    glowLine(OP.green, 0.9f, Offset(-5f, s * (5f + span)), Offset(1f, s * (5f + span)), 0.8f)
                }
            }
            if (sim.lamps > 0.2f) drawCircle(OP.core.copy(alpha = sim.lamps * lum), 0.9f, Offset(8.5f, 0f))
        }
    }

    /** Solar wind with the climate fan: faint dashes streaming across in the dim channel. */
    private fun DrawScope.wind(g: OGeo, sim: OrbitSim, f: ThemeFrame) {
        val n = (sim.fan * g.windX.size).toInt()
        if (n <= 0 || sim.reducedMotion) return
        val ph = sim.windPhase.toFloat()
        for (i in 0 until n) {
            val x = ((g.windX[i] + ph * (0.6f + g.windX[i] * 0.4f)) % 1f) * (f.dw + 200f) - 100f
            val y = g.windY[i]
            val len = 10f + 22f * sim.fan
            drawLine(OP.green.copy(alpha = 0.35f * lum), Offset(x, y), Offset(x + len, y), 1f)
        }
    }

    private fun DrawScope.meteor(g: OGeo, sim: OrbitSim) {
        val t = sim.meteor()
        if (t < 0f) return
        val x0 = g.cx - 500f + (sim.meteorSeed % 5) * 160f; val y0 = 60f + (sim.meteorSeed % 3) * 30f
        val x = x0 + t * 300f; val y = y0 + t * 120f
        val a = (if (t < 0.2f) t / 0.2f else 1f - (t - 0.2f) / 0.8f) * 0.8f
        glowLine(OP.cyan, a, Offset(x - 40f, y - 16f), Offset(x, y), 1f)
    }

    // ------------------------------------------------------------------ telemetry

    /** The two text paints, made once on the first frame (never in a plain unit test). */
    private fun paint(): Paint {
        paint?.let { return it }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL); textSize = 14f; letterSpacing = 0.12f }
        glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = p.typeface; textSize = 14f; letterSpacing = 0.12f; style = Paint.Style.STROKE; strokeWidth = 2.6f }
        paint = p
        return p
    }

    /** 1 parked .. 0 moving, eased over 0.3 s (the painter's own state; the simulation is only read). */
    private var textFade = -1f
    private var lastTime = 0.0

    private fun textFade(sim: OrbitSim): Float {
        val target = if (sim.moving) 0f else 1f
        textFade = if (textFade < 0f) target else ThemeSim.approach(textFade, target, ((sim.time - lastTime).toFloat().coerceIn(0f, 0.1f)) / 0.3f)
        lastTime = sim.time
        return textFade
    }

    /**
     * Monospace uppercase telemetry in two corners, both clear of the app's chrome zones ([ChromeZones]: status
     * top left, actions bottom right): the lap count and the moonlet count at the top right, the orbit, power and
     * mode readings stacked at the bottom left. Legibility rule: while the car moves only one reading stays, the
     * lap count, set at 24 sp or more (derived from the frame's density and scale); every small line fades out
     * within 0.3 s and comes back when parked. Numbers are written into a char buffer (no allocation).
     */
    private fun DrawScope.telemetry(g: OGeo, sim: OrbitSim, f: ThemeFrame) {
        val p = paint()
        val q = glowPaint!!
        val e = if (intro < 1f) Intro.out(intro, 0.4f, 0.6f) else 1f
        val c = drawContext.canvas.nativeCanvas
        val lx = 44f; val rx = f.dw - 44f
        // The one moving reading: laps (one per km), at least 24 sp.
        val big = max(14f, 24f * f.density.density * f.density.fontScale / f.k)
        p.textSize = big; q.textSize = big; q.strokeWidth = big * 0.18f
        p.color = OP.greenCore.copy(alpha = 0.9f * lum * e).toArgb()
        q.color = OP.green.copy(alpha = 0.25f * lum * e).toArgb()
        val adv = p.measureText("0")
        val bx = rx - adv * 9.2f; val by = 40f + big
        text(c, "T+", bx, by, p, q)
        num(c, min(sim.laps, 99), 2, -1, bx + adv * 3f, by, p, q)
        text(c, "REV", bx + adv * 6.2f, by, p, q)
        // Everything else is small and shows only while parked.
        val fade = textFade(sim)
        p.textSize = 14f; q.textSize = 14f; q.strokeWidth = 2.6f
        if (fade < 0.01f) return
        p.color = OP.greenCore.copy(alpha = 0.85f * lum * e * fade).toArgb()
        q.color = OP.green.copy(alpha = 0.25f * lum * e * fade).toArgb()
        // Orbital state from Kepler: altitude above the limb and speed by vis-viva (the system's own units).
        val m = sim.angle - OrbitSim.STATION
        var ea = m
        repeat(5) { ea -= (ea - g.e * sin(ea) - m) / (1.0 - g.e * cos(ea)) }
        val r = g.a * (1.0 - g.e * cos(ea))
        val alt = ((r - g.planetR) * 2.6).roundToInt().coerceIn(0, 9999)
        val v = sqrt(7.4 * 7.4 * g.a * (2.0 / r - 1.0 / g.a))
        val altY = f.dh - 112f; val vY = f.dh - 92f
        text(c, "ALT", lx, altY, p, q)
        num(c, alt, 4, -1, lx + 40f, altY, p, q)
        text(c, "KM", lx + 40f + 4 * 10.1f + 6f, altY, p, q)
        text(c, "V", lx, vY, p, q)
        num(c, (v * 100).roundToInt().coerceIn(0, 9999), 3, 1, lx + 40f, vY, p, q)
        text(c, "KM/S", lx + 40f + 4 * 10.1f + 6f, vY, p, q)
        val x0 = rx - 112f
        text(c, "SAT", x0, by + 22f, p, q)
        num(c, sim.moonlets, 2, -1, x0 + 40f, by + 22f, p, q)
        if (sim.docked > 0.5f) text(c, "DOCKED", x0, by + 44f, p, q)
        else if (sim.retrograde) text(c, "RETRO", x0, by + 44f, p, q)
        // Power: a bar of ten cells (no number), and the drive mode tag.
        if (sim.battery >= 0f) {
            text(c, "PWR", lx, f.dh - 46f, p, q)
            val cells = (sim.battery * 10f).roundToInt().coerceIn(0, 10)
            for (i in 0 until 10) {
                val x = lx + 42f + i * 8f
                if (i < cells) drawRect(OP.greenCore.copy(alpha = 0.8f * lum * e * fade), Offset(x, f.dh - 57f), Size(5f, 11f))
                else drawRect(OP.green.copy(alpha = 0.6f * lum * e * fade), Offset(x, f.dh - 57f), Size(5f, 11f), style = core)
            }
        }
        val tag = when { sim.mode < -0.3f -> "ECO"; sim.mode > 0.3f -> "SPT"; else -> null }
        if (tag != null) text(c, tag, lx, f.dh - 68f, p, q)
    }

    private fun text(c: android.graphics.Canvas, s: String, x: Float, y: Float, p: Paint, q: Paint) {
        c.drawText(s, x, y, q); c.drawText(s, x, y, p)
    }

    /** Writes [value] with [digits] digits ([dot] ≥ 0: a decimal point after that many digits) without allocating. */
    private fun num(c: android.graphics.Canvas, value: Int, digits: Int, dot: Int, x: Float, y: Float, p: Paint, q: Paint) {
        var v = value
        val total = digits + (if (dot >= 0) 1 else 0)
        var i = total - 1
        var placed = 0
        val fracDigits = if (dot >= 0) digits - dot else 0
        while (i >= 0) {
            if (dot >= 0 && placed == fracDigits) { chars[i] = '.'; i--; placed++; continue }
            chars[i] = '0' + (v % 10); v /= 10; i--; placed++
        }
        c.drawText(chars, 0, total, x, y, q); c.drawText(chars, 0, total, x, y, p)
    }
}

private val SIDES = floatArrayOf(-1f, 1f)
