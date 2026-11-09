package io.vroomi.character.render.marble

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.marble.MarbleLayout
import io.vroomi.character.marble.MarbleSim
import io.vroomi.character.render.Intro
import io.vroomi.character.render.theme.LayerSlot
import io.vroomi.character.render.theme.ChromeZones
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.character.render.theme.ThemePainter
import io.vroomi.character.render.theme.ThemeTexts
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Bauhaus toy palette: three primaries, black and white, a white pegboard by day and a deep navy one at
 * night. Nothing here is shared with another theme.
 */
private object BauhausInk {
    val red = Color(0xFFD7262E)
    val yellow = Color(0xFFF2B705)
    val blue = Color(0xFF1F4BA5)
    val black = Color(0xFF141414)
    val white = Color(0xFFFAF8F2)
    /** Day board, its drilled holes and the hard shadow of every piece. */
    val board = Color(0xFFF1EEE6)
    val hole = Color(0xFFCBC6BA)
    val holeCore = Color(0xFF9C978B)
    val shadow = Color(0xFFDAD5CA)
    /** Night board. */
    val nightBoard = Color(0xFF0F1A3E)
    val nightHole = Color(0xFF07102B)
    val nightHoleRim = Color(0xFF1C2856)
    val nightShadow = Color(0xFF0A1331)
    /** Glass marble: a pale tint, a flat darker body side, one hard white specular dot. */
    val glass = Color(0xFFD6E5F4)
    val glassBody = Color(0xFFA9C2DE)
    val lamp = Color(0xFFFFE07A)
    val warm = Color(0xFFFF9A3C)
    val cool = Color(0xFF6FA8FF)

    /** The colour of each rail: a primary per piece, so the run reads as a toy built from parts. */
    val rail = arrayOf(red, yellow, blue, red, blue, yellow, black, yellow, blue)
}

/** Geometry of one design width: rail paths, peg positions and the chime bars (rebuilt only when the width changes). */
private class BoardGeo(val dw: Float) {
    val ox = dw / 2f - 500f
    val oy = 74f
    fun x(u: Float) = ox + u
    fun y(v: Float) = oy + v

    val railPaths: List<Path> = MarbleLayout.tracks.map { t ->
        Path().apply { for (i in t.xs.indices) if (i == 0) moveTo(x(t.xs[i]), y(t.ys[i])) else lineTo(x(t.xs[i]), y(t.ys[i])) }
    }
    /** Thick uniform outline, the coloured body, and a white channel line where the marbles roll. */
    val railOutline = Stroke(19f, cap = StrokeCap.Butt, join = StrokeJoin.Round)
    val railBody = Stroke(12f, cap = StrokeCap.Butt, join = StrokeJoin.Round)
    val railChannel = Stroke(2f, cap = StrokeCap.Butt, join = StrokeJoin.Round)

    /** Clips that hold each rail on the board: rail point and the peg hole it reaches down to. */
    val clipX: FloatArray
    val clipY: FloatArray
    val pegX: FloatArray
    val pegY: FloatArray

    val chimeX = FloatArray(MarbleSim.CHIME_BARS)
    val chimeY = FloatArray(MarbleSim.CHIME_BARS)

    init {
        val cx = ArrayList<Float>(); val cy = ArrayList<Float>(); val px = ArrayList<Float>(); val py = ArrayList<Float>()
        val p = FloatArray(2)
        for ((i, t) in MarbleLayout.tracks.withIndex()) {
            // Helices are clipped at their centre only; straight runs every 120 units.
            val spacing = if (i == MarbleLayout.GOLD || i == MarbleLayout.LOOP) t.length else 120f
            var s = if (i == MarbleLayout.GOLD || i == MarbleLayout.LOOP) t.length * 0.5f else 40f
            while (s < t.length - 10f) {
                t.point(s, p)
                val hx = x(p[0]); val hy = y(p[1])
                cx += hx; cy += hy
                // The nearest hole below the rail on the 30-unit grid.
                px += ox + ((hx - ox) / PITCH).roundToInt() * PITCH
                py += oy + (((hy - oy) / PITCH).toInt() + 1) * PITCH
                s += spacing
            }
        }
        clipX = cx.toFloatArray(); clipY = cy.toFloatArray(); pegX = px.toFloatArray(); pegY = py.toFloatArray()
        val t = MarbleLayout.tracks[MarbleLayout.CHIME]
        for (k in 0 until MarbleSim.CHIME_BARS) { t.point(t.length * (k + 0.5f) / MarbleSim.CHIME_BARS, p); chimeX[k] = p[0]; chimeY[k] = p[1] }
    }

    /** The hopper bowl: a half disc. */
    val hopper = Path().apply { arcTo(Rect(Offset(x(85f), y(410f)), 64f), 0f, 180f, false); close() }

    companion object { const val PITCH = 30f }
}

/**
 * The marble run as a Bauhaus toy: flat primary pieces with thick uniform black outlines, clipped to a white
 * pegboard, glass marbles with one hard highlight. Motion is honest and crisp: gravity, roll, click.
 */
internal class MarblePainter : ThemePainter<MarbleSim>() {
    override val minDesignWidth = 1080f
    override val backdrop = Color(0xFFE4E0D6)

    private var geo: BoardGeo? = null
    /** The day board and the night board: two cached layers, cross-faded while the light changes. */
    private val dayBoard = LayerSlot(SceneTheme.MARBLE)
    private val nightBoard = LayerSlot(SceneTheme.MARBLE)
    private val pt = FloatArray(2)
    private val outline = Stroke(3f, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
    private val outlineThin = Stroke(2f)
    private val rod = Stroke(4f, cap = StrokeCap.Butt)
    private val quarter = Path()
    private val bold = Stroke(6f)

    private var textsOf: ThemeTexts? = null
    private var title: TextLayoutResult? = null
    private var titleNight: TextLayoutResult? = null
    private var kmLabel: TextLayoutResult? = null

    /** 0 parked .. 1 moving (linear, 0.3 s): words that carry information hide while the car moves. */
    private var drive = 0f
    private var driveT = -1.0

    private fun drivingFade(moving: Boolean, time: Double): Float {
        val target = if (moving) 1f else 0f
        drive = if (driveT < 0.0) target else {
            val step = ((time - driveT) / 0.3).toFloat().coerceIn(0f, 1f)
            if (drive < target) minOf(target, drive + step) else maxOf(target, drive - step)
        }
        driveT = time
        return drive
    }

    private fun geo(dw: Float): BoardGeo {
        val g = geo
        if (g != null && abs(g.dw - dw) < 0.5f) return g
        return BoardGeo(dw).also { geo = it }
    }

    private fun texts(tx: ThemeTexts) {
        if (textsOf === tx) return
        textsOf = tx
        val t = tx.text
        title = t.styled("mb-title", "RUN 01", 26f, BauhausInk.red, FontFamily.SansSerif, FontWeight.Black, 0.04f)
        titleNight = t.styled("mb-title-n", "RUN 01", 26f, BauhausInk.yellow, FontFamily.SansSerif, FontWeight.Black, 0.04f)
        kmLabel = t.styled("mb-km", "KM", 18f, BauhausInk.blue, FontFamily.SansSerif, FontWeight.Black, 0.04f)
    }

    override fun drawPx(s: DrawScope, sim: MarbleSim, f: ThemeFrame, tx: ThemeTexts, left: Float, top: Float) {
        val g = geo(f.dw)
        val n = sim.night
        val w = f.w.roundToInt(); val h = f.h.roundToInt(); val k = f.k
        val key = (w.toLong() shl 20) xor h.toLong()
        val at = IntOffset(left.roundToInt(), top.roundToInt())
        if (n < 0.999f) {
            val day = dayBoard.get(key, w, h, f.density) { withTransform({ scale(k, k, Offset.Zero) }) { board(g, f, false) } }
            s.drawImage(day, dstOffset = at, dstSize = IntSize(day.width, day.height))
        }
        if (n > 0.001f) {
            val night = nightBoard.get(key, w, h, f.density) { withTransform({ scale(k, k, Offset.Zero) }) { board(g, f, true) } }
            s.drawImage(night, dstOffset = at, dstSize = IntSize(night.width, night.height), alpha = n)
        }
    }

    override fun draw(s: DrawScope, sim: MarbleSim, f: ThemeFrame, tx: ThemeTexts) = with(s) {
        val g = geo(f.dw)
        texts(tx)
        drivingFade(sim.moving, sim.time)
        val combo = comboFlip(sim)
        labels(g, sim, f)
        coolLane(g, sim)
        chimeBars(g, sim, combo)
        lift(g, sim)
        gears(g, sim)
        windmill(g, sim)
        abacus(g, sim)
        brake(g, sim, combo)
        gate(g, sim, combo)
        pendulum(g, sim, combo)
        marbles(g, sim)
        stoppers(g, sim, combo)
        trayAndSolar(g, sim)
        light(g, sim, f)
        if (f.options.intro < 1f) entrance(f, f.options.intro)
    }

    /** Combo: for one second every gate shows the same yellow (a hard switch, no fade). */
    private fun comboFlip(sim: MarbleSim): Boolean = sim.since(sim.comboAt) in 0.0..1.0

    // ------------------------------------------------------------------ static: pegboard and the clipped rails

    private fun DrawScope.board(g: BoardGeo, f: ThemeFrame, night: Boolean) {
        val P = BauhausInk
        drawRect(if (night) P.nightBoard else P.board, size = Size(f.dw, f.dh))
        // The drilled hole grid, aligned with the machine origin.
        val pitch = BoardGeo.PITCH
        var x = g.ox - pitch * ((g.ox / pitch).toInt() + 1)
        while (x < f.dw + pitch) {
            var y = g.oy - pitch * ((g.oy / pitch).toInt() + 1)
            while (y < f.dh + pitch) {
                if (night) {
                    drawCircle(P.nightHoleRim, 4.2f, Offset(x, y))
                    drawCircle(P.nightHole, 3.2f, Offset(x, y))
                } else {
                    drawCircle(P.hole, 4.2f, Offset(x, y))
                    drawCircle(P.holeCore, 3.0f, Offset(x + 0.7f, y + 0.7f))
                }
                y += pitch
            }
            x += pitch
        }
        val shadow = if (night) P.nightShadow else P.shadow
        // Hard offset shadow of every rail (one light, far away, upper left: crisp edges).
        for (p in g.railPaths) withTransform({ translate(5f, 6f) }) { drawPath(p, shadow, style = g.railOutline) }
        // Clips: a black stem from the rail down to its peg, and a white peg cap.
        for (i in g.clipX.indices) {
            val cx = g.clipX[i]; val cy = g.clipY[i]; val px = g.pegX[i]; val py = g.pegY[i]
            drawLine(shadow, Offset(cx + 5f, cy + 6f), Offset(px + 5f, py + 6f), 7f)
            drawLine(P.black, Offset(cx, cy), Offset(px, py), 7f)
            drawCircle(shadow, 7f, Offset(px + 4f, py + 5f))
            drawCircle(P.white, 7f, Offset(px, py))
            drawCircle(P.black, 7f, Offset(px, py), style = outline)
        }
        // Rails: black outline, a primary body, a white rolling channel.
        for ((i, p) in g.railPaths.withIndex()) {
            drawPath(p, P.black, style = g.railOutline)
            drawPath(p, P.rail[i], style = g.railBody)
            drawPath(p, P.white.copy(alpha = if (P.rail[i] == P.black) 0.9f else 0.55f), style = g.railChannel)
        }
        // Lift column: a white rectangle with a black frame.
        val lx = g.x(MarbleLayout.LIFT_X)
        val top = g.y(MarbleLayout.LIFT_TOP) - 12f; val bottom = g.y(MarbleLayout.LIFT_BOTTOM) + 18f
        drawRect(shadow, Offset(lx - 18f + 5f, top + 6f), Size(36f, bottom - top))
        drawRect(P.white, Offset(lx - 18f, top), Size(36f, bottom - top))
        // Hopper bowl.
        withTransform({ translate(5f, 6f) }) { drawPath(g.hopper, shadow) }
        drawPath(g.hopper, P.yellow)
        drawPath(g.hopper, P.black, style = outline)
        // Abacus frame (km counter) and the tray for the striped marbles.
        frame(g.x(700f), g.y(-30f), 270f, 76f, P.white, shadow)
        for (row in 0 until 2) drawLine(P.black, Offset(g.x(704f), g.y(-6f + row * 30f)), Offset(g.x(966f), g.y(-6f + row * 30f)), 2.5f)
        frame(g.x(880f), g.y(440f), 130f, 50f, P.blue, shadow)
    }

    private fun DrawScope.frame(x: Float, y: Float, w: Float, h: Float, fill: Color, shadow: Color) {
        drawRect(shadow, Offset(x + 5f, y + 6f), Size(w, h))
        drawRect(fill, Offset(x, y), Size(w, h))
        drawRect(BauhausInk.black, Offset(x, y), Size(w, h), style = outline)
    }

    // ------------------------------------------------------------------ moving pieces

    /**
     * "RUN 01" is the toy's printed title (a marking): red on the white board, yellow on the navy one, cross-faded
     * with the light. It sits on the top margin, right of the app's status corner ([ChromeZones]) whatever the
     * canvas. "KM" labels the counter (information below the 24 sp driving minimum): it hides while moving.
     */
    private fun DrawScope.labels(g: BoardGeo, sim: MarbleSim, f: ThemeFrame) {
        val n = sim.night
        val d = f.density.density
        val clearX = ChromeZones.all(f.w / d, f.h / d).filter { it.top <= 1f }.maxOf { it.right } * d / f.k
        val at = Offset(maxOf(g.x(-30f), clearX + 12f), g.y(-62f))
        if (n < 0.99f) title?.let { drawText(it, topLeft = at, alpha = 1f - n) }
        if (n > 0.01f) titleNight?.let { drawText(it, topLeft = at, alpha = n) }
        val a = 1f - drive
        if (a > 0.01f) kmLabel?.let { drawText(it, topLeft = Offset(g.x(700f) - it.size.width - 10f, g.y(-30f)), alpha = a) }
    }

    /** A/C on: the right lane turns cobalt blue (a flat recolour, no glow). */
    private fun DrawScope.coolLane(g: BoardGeo, sim: MarbleSim) {
        val c = sim.coolGlow
        if (c > 0.5f) drawPath(g.railPaths[MarbleLayout.R], BauhausInk.cool, style = g.railBody)
    }

    private fun DrawScope.chimeBars(g: BoardGeo, sim: MarbleSim, combo: Boolean) {
        val P = BauhausInk
        for (k in 0 until MarbleSim.CHIME_BARS) {
            val t = (sim.decoTime - sim.chimeAt[k]).toFloat()
            val struck = t in 0f..0.25f
            val x = g.x(g.chimeX[k]) - 22f; val y = g.y(g.chimeY[k]) + 12f + if (struck) 3f else 0f
            val fill = when {
                combo -> P.yellow
                struck -> P.white
                else -> when (k % 3) { 0 -> P.red; 1 -> P.yellow; else -> P.blue }
            }
            drawRect(fill, Offset(x, y), Size(44f, 8f))
            drawRect(P.black, Offset(x, y), Size(44f, 8f), style = outlineThin)
        }
    }

    private fun DrawScope.lift(g: BoardGeo, sim: MarbleSim) {
        // Red diagonal bands inside the white column climb with the lift (the screw turning).
        val P = BauhausInk
        val lx = g.x(MarbleLayout.LIFT_X)
        val top = g.y(MarbleLayout.LIFT_TOP) - 12f; val bottom = g.y(MarbleLayout.LIFT_BOTTOM) + 18f
        val ph = (sim.liftPhase % 1.0).toFloat()
        var y = bottom - ph * 40f
        while (y > top - 20f) {
            val y0 = max(top, y - 14f); val y1 = (y).coerceAtMost(bottom)
            if (y1 > y0 + 0.5f) {
                // A band is a slanted rectangle clipped to the column: drawn as a thick line between the walls.
                val a = Offset(lx - 18f, y1.coerceIn(top, bottom))
                val b = Offset(lx + 18f, (y1 - 14f).coerceIn(top, bottom))
                drawLine(P.red, a, b, 8f, cap = StrokeCap.Butt)
            }
            y -= 40f
        }
        drawRect(P.black, Offset(lx - 18f, top), Size(36f, bottom - top), style = outline)
    }

    private fun DrawScope.gears(g: BoardGeo, sim: MarbleSim) {
        // Discs with a turning quarter: yellow normally, red in SPORT, blue in ECO.
        val P = BauhausInk
        val accent = when { sim.mode > 0.3f -> P.red; sim.mode < -0.3f -> P.blue; else -> P.yellow }
        for ((k, spec) in GEARS.withIndex()) {
            val cx = g.x(spec[0]); val cy = g.y(spec[1]); val r = spec[2] + 4f
            val a = (sim.gears * (if (k % 2 == 0) 1.0 else -1.6) * 57.3).toFloat()
            drawCircle(P.white, r, Offset(cx, cy))
            drawArc(accent, a, 90f, true, Offset(cx - r, cy - r), Size(r * 2, r * 2))
            drawArc(P.black, a + 180f, 90f, true, Offset(cx - r, cy - r), Size(r * 2, r * 2))
            drawCircle(P.black, r, Offset(cx, cy), style = outline)
        }
        // ECO: small blue damper blocks on the stations.
        if (sim.mode < -0.3f) for (p in DAMPERS) {
            drawRect(P.blue, Offset(g.x(p[0]) - 10f, g.y(p[1]) + 8f), Size(20f, 7f))
            drawRect(P.black, Offset(g.x(p[0]) - 10f, g.y(p[1]) + 8f), Size(20f, 7f), style = outlineThin)
        }
    }

    private fun DrawScope.windmill(g: BoardGeo, sim: MarbleSim) {
        // Four quarter-disc vanes in the three primaries and black, on a black post.
        val P = BauhausInk
        val cx = g.x(960f); val cy = g.y(150f)
        drawRect(P.black, Offset(cx - 4f, cy), Size(8f, 76f))
        val a = (sim.windmill * 57.3).toFloat()
        val r = 34f
        rotate(a, Offset(cx, cy)) {
            for (i in 0 until 4) {
                val col = when (i) { 0 -> P.red; 1 -> P.yellow; 2 -> P.blue; else -> P.black }
                // Each vane is a quarter disc whose corner sits on the hub, set 90° apart with a gap.
                val start = i * 90f + 8f
                drawArc(col, start, 74f, true, Offset(cx - r, cy - r), Size(r * 2, r * 2))
                drawArc(P.black, start, 74f, true, Offset(cx - r, cy - r), Size(r * 2, r * 2), style = outlineThin)
            }
        }
        drawCircle(P.white, 6f, Offset(cx, cy)); drawCircle(P.black, 6f, Offset(cx, cy), style = outlineThin)
    }

    private fun DrawScope.abacus(g: BoardGeo, sim: MarbleSim) {
        val P = BauhausInk
        val slide = ((sim.decoTime - sim.beadAt) / 0.35).toFloat().coerceIn(0f, 1f)
        val eased = slide * slide // a bead accelerates along its rod and stops with a click
        for (i in 0 until 20) {
            val row = i / 10; val col = i % 10
            val counted = i < sim.beads
            val moving = i == sim.beads - 1 && slide < 1f
            val left = g.x(714f + col * 11f); val right = g.x(856f + col * 11f)
            val x = if (!counted) left else if (moving) left + (right - left) * eased else right
            val y = g.y(-6f + row * 30f)
            drawCircle(if (counted) (if (row == 0) P.red else P.blue) else P.white, 5.5f, Offset(x, y))
            drawCircle(P.black, 5.5f, Offset(x, y), style = outlineThin)
        }
    }

    private fun DrawScope.brake(g: BoardGeo, sim: MarbleSim, combo: Boolean) {
        val P = BauhausInk
        val t = MarbleLayout.tracks[MarbleLayout.A]
        t.point(t.length * MarbleLayout.BRAKE_AT, pt)
        val px = g.x(pt[0]); val py = g.y(pt[1])
        val pivot = Offset(px + 30f, py - 48f)
        val a = -40f + 40f * sim.brakeLever
        rotate(a, pivot) {
            drawLine(P.black, pivot, Offset(pivot.x - 30f, pivot.y + 40f), 6f, cap = StrokeCap.Butt)
            drawRect(if (combo) P.yellow else P.red, Offset(pivot.x - 42f, pivot.y + 36f), Size(24f, 10f))
            drawRect(P.black, Offset(pivot.x - 42f, pivot.y + 36f), Size(24f, 10f), style = outlineThin)
        }
        drawCircle(P.white, 6f, pivot); drawCircle(P.black, 6f, pivot, style = outlineThin)
    }

    private fun DrawScope.gate(g: BoardGeo, sim: MarbleSim, combo: Boolean) {
        val P = BauhausInk
        val cx = g.x(MarbleLayout.GATE_X); val cy = g.y(MarbleLayout.GATE_Y) - 4f
        val left = when (sim.turn) { TurnSignal.LEFT -> true; TurnSignal.RIGHT -> false; else -> sim.flipLeft }
        val a = if (left) 30f else -30f
        // The flag is blue when it sends marbles left, red when right (yellow for the combo).
        val fill = if (combo) P.yellow else if (left) P.blue else P.red
        rotate(a, Offset(cx, cy)) {
            drawRect(fill, Offset(cx - 5f, cy - 32f), Size(10f, 34f))
            drawRect(P.black, Offset(cx - 5f, cy - 32f), Size(10f, 34f), style = outlineThin)
        }
        drawCircle(P.white, 5f, Offset(cx, cy)); drawCircle(P.black, 5f, Offset(cx, cy), style = outlineThin)
        // Blinker lamp beside the gate: a round lamp that follows the car's rhythm.
        val lamp = max(sim.blinkL, sim.blinkR)
        val lx = cx + (if (sim.blinkL >= sim.blinkR) -26f else 26f); val ly = cy - 34f
        drawCircle(lerp(P.white, P.yellow, lamp), 7f, Offset(lx, ly))
        drawCircle(P.black, 7f, Offset(lx, ly), style = outlineThin)
    }

    private fun DrawScope.pendulum(g: BoardGeo, sim: MarbleSim, combo: Boolean) {
        val P = BauhausInk
        val pivot = Offset(g.x(MarbleLayout.PEND_PIVOT_X), g.y(MarbleLayout.PEND_PIVOT_Y))
        sim.cupPoint(pt)
        val cup = Offset(g.x(pt[0]), g.y(pt[1]))
        drawLine(P.black, pivot, cup, 4f)
        val fill = if (combo) P.yellow else P.red
        drawArc(fill, 0f, 180f, true, Offset(cup.x - 15f, cup.y - 15f), Size(30f, 30f))
        drawArc(P.black, 0f, 180f, true, Offset(cup.x - 15f, cup.y - 15f), Size(30f, 30f), style = outlineThin)
        drawCircle(P.white, 6f, pivot); drawCircle(P.black, 6f, pivot, style = outlineThin)
    }

    private fun DrawScope.marble(x: Float, y: Float, striped: Boolean) {
        val P = BauhausInk
        if (striped) {
            // The reward marble: white with one red band.
            drawCircle(P.white, 7f, Offset(x, y))
            drawLine(P.red, Offset(x - 6.6f, y), Offset(x + 6.6f, y), 4f, cap = StrokeCap.Butt)
        } else {
            drawCircle(P.glass, 7f, Offset(x, y))
            drawCircle(P.glassBody, 4.6f, Offset(x + 1.6f, y + 1.6f))
        }
        drawCircle(P.black, 7f, Offset(x, y), style = outlineThin)
        drawCircle(Color.White, 1.9f, Offset(x - 2.6f, y - 2.7f))
    }

    private fun DrawScope.marbles(g: BoardGeo, sim: MarbleSim) {
        for (i in 0 until MarbleSim.MAX) {
            val st = sim.state[i]
            if (st != MarbleSim.TRACK && st != MarbleSim.LIFT && st != MarbleSim.CUP) continue
            sim.where(i, pt)
            marble(g.x(pt[0]), g.y(pt[1]) - 6f, sim.gold[i])
        }
        // The hopper's contents: a neat stack in the bowl.
        val n = sim.inHopper
        for (k in 0 until n) {
            val col = k % 7; val row = k / 7
            marble(g.x(46f + col * 13f + (row % 2) * 6f), g.y(458f - row * 11f), false)
        }
    }

    private fun DrawScope.stoppers(g: BoardGeo, sim: MarbleSim, combo: Boolean) {
        val t = sim.since(sim.stoppersAt).toFloat()
        if (t < 0f || t > 1.4f) return
        // Pins snap up (no ease), hold, and drop in two clicks.
        val up = when { t < 0.08f -> 1f; t < 1f -> 1f; t < 1.2f -> 0.5f; else -> 0f }
        if (up <= 0f) return
        val P = BauhausInk
        for (p in STOPPERS) {
            val h = 18f * up
            drawRect(if (combo) P.yellow else P.red, Offset(g.x(p[0]) - 3.5f, g.y(p[1]) - h), Size(7f, h))
            drawRect(P.black, Offset(g.x(p[0]) - 3.5f, g.y(p[1]) - h), Size(7f, h), style = outlineThin)
        }
    }

    private fun DrawScope.trayAndSolar(g: BoardGeo, sim: MarbleSim) {
        for (k in 0 until sim.tray) marble(g.x(896f + (k % 6) * 19f), g.y(456f + (k / 6) * 17f), true)
        if (sim.charging > 0.05f) {
            // Charging: a yellow sun disc with eight rectangular rays turns the lift slowly.
            val P = BauhausInk
            val cx = g.x(160f); val cy = g.y(380f)
            rotate((sim.solar * 57.3).toFloat(), Offset(cx, cy)) {
                for (i in 0 until 8) rotate(i * 45f, Offset(cx, cy)) {
                    drawRect(P.yellow, Offset(cx - 3f, cy - 26f), Size(6f, 10f))
                    drawRect(P.black, Offset(cx - 3f, cy - 26f), Size(6f, 10f), style = outlineThin)
                }
            }
            drawCircle(P.yellow, 12f, Offset(cx, cy)); drawCircle(P.black, 12f, Offset(cx, cy), style = outlineThin)
        }
    }

    private fun DrawScope.light(g: BoardGeo, sim: MarbleSim, f: ThemeFrame) {
        val P = BauhausInk
        val w = sim.warmth
        if (w > 0.05f) drawRect(P.warm.copy(alpha = 0.05f * w), size = Size(f.dw, f.dh))
        if (w < -0.05f) drawRect(P.cool.copy(alpha = 0.05f * -w), size = Size(f.dw, f.dh))
        // Small round lamps on the board at night: a yellow disc with a white core.
        val l = sim.lamps
        if (l > 0.01f) for (p in LAMPS) {
            val c = Offset(g.x(p[0]), g.y(p[1]))
            drawCircle(P.black, 9f, c)
            drawCircle(P.lamp.copy(alpha = l), 7f, c)
            drawCircle(Color.White.copy(alpha = l), 2.5f, c)
        }
    }

    /**
     * Entrance accent: a red disc, a yellow square and a blue quarter disc cover the board and slide out to
     * three edges along straight lines (a title card made of the toy's own primitives).
     */
    private fun DrawScope.entrance(f: ThemeFrame, intro: Float) {
        val P = BauhausInk
        val e = Intro.out(intro, 0.05f, 0.8f)
        val cx = f.dw / 2f; val cy = f.dh / 2f
        val r = f.dh * 0.42f
        // Disc to the left, square up, quarter disc to the right.
        val d = Offset(cx - r * 0.7f - e * (cx + r * 1.2f), cy)
        drawCircle(P.red, r, d); drawCircle(P.black, r, d, style = bold)
        val sq = Offset(cx - r * 0.6f, cy - r * 0.6f - e * (f.dh + r * 1.4f))
        drawRect(P.yellow, sq, Size(r * 1.2f, r * 1.2f)); drawRect(P.black, sq, Size(r * 1.2f, r * 1.2f), style = bold)
        val q = Offset(cx + r * 0.5f + e * (cx + r * 1.4f), cy + r)
        quarter.reset()
        quarter.moveTo(q.x, q.y)
        quarter.arcTo(Rect(q, r * 1.6f), 180f, 90f, false)
        quarter.close()
        drawPath(quarter, P.blue); drawPath(quarter, P.black, style = bold)
    }

    companion object {
        /** Gears: x, y, radius (machine units). */
        private val GEARS = arrayOf(floatArrayOf(120f, 40f, 22f), floatArrayOf(160f, 72f, 14f))
        private val DAMPERS = arrayOf(floatArrayOf(MarbleLayout.GATE_X, MarbleLayout.GATE_Y), floatArrayOf(MarbleLayout.PEND_PIVOT_X + 58f, 266f), floatArrayOf(900f, 380f))
        private val STOPPERS = arrayOf(floatArrayOf(330f, 84f), floatArrayOf(480f, 168f), floatArrayOf(700f, 170f), floatArrayOf(640f, 296f), floatArrayOf(500f, 418f), floatArrayOf(300f, 432f))
        private val LAMPS = arrayOf(floatArrayOf(MarbleLayout.GATE_X, MarbleLayout.GATE_Y - 70f), floatArrayOf(MarbleLayout.LIFT_X + 40f, 10f), floatArrayOf(900f, 330f), floatArrayOf(245f, 150f), floatArrayOf(640f, 470f))
    }
}
