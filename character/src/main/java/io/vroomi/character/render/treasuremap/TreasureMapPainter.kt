package io.vroomi.character.render.treasuremap

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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.render.Intro
import io.vroomi.character.render.hash01
import io.vroomi.character.render.theme.LayerSlot
import io.vroomi.character.render.theme.ChromeZones
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.character.render.theme.ThemePainter
import io.vroomi.character.render.theme.ThemeTexts
import io.vroomi.character.theme.ThemeSim
import io.vroomi.character.treasuremap.TreasureMapSim
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * TREASURE_MAP, art direction: a copperplate engraving chart, full-bleed. Ivory paper with the copper plate's
 * mark, a graduated neatline border of alternating black and white ticks, an engraved compass rose and a title
 * cartouche reading "여정 일지". Every tone is made of lines: hatched cones for woods, hachures for hills, tiny
 * plans for towns, stippled coasts with water-lining. Iron-gall ink plus one madder-red accent (wax seals and the
 * rhumb lines of a combo). The trail is engraved behind the car only: the map never shows a position, a
 * coordinate or a route ahead, only the distance-based trail from the simulation. Ink draws on; nothing bounces.
 */

/** The theme's private palette: ivory paper, iron-gall ink, madder red. */
private object MP {
    val paper = Color(0xFFF2EAD6)
    val paperEdge = Color(0xFFE1D3B2)
    val plate = Color(0xFFD9CBAA)
    val ink = Color(0xFF2B2119)
    val inkSoft = Color(0xFF5A4A3A)
    val inkFaint = Color(0xFF8C7B66)
    val madder = Color(0xFFA5322B)
    val madderDark = Color(0xFF6E1E1A)
    val madderLight = Color(0xFFD06A5E)
    val candle = Color(0xFFFFC98A)
    val night = Color(0xFF120C08)
}

private class MGeo(val dw: Float, val dh: Float) {
    /** The neatline (outer rule), the graduated band and the inner rule. */
    val outer = 20f
    val bandIn = 25f
    val bandOut = 35f
    val inner = 40f
    val ml = inner; val mr = dw - inner; val mt = inner; val mb = dh - inner
    val compass = Offset(mr - 112f, mt + 108f)
    val roseR = 70f
    /** The cartouche (lower left) and the scale bar beside it. */
    val cartL = ml + 22f; val cartT = mb - 112f; val cartW = 268f; val cartH = 90f
    val scaleX = cartL + cartW + 34f; val scaleY = mb - 30f

    val candleLight = Brush.radialGradient(listOf(MP.candle.copy(alpha = 0.10f), MP.night.copy(alpha = 0.55f), MP.night.copy(alpha = 0.86f)),
        Offset(dw * 0.42f, dh * 0.78f), max(dw, 1180f) * 0.62f)
    val candleWarm = Brush.radialGradient(listOf(MP.candle.copy(alpha = 0.30f), MP.candle.copy(alpha = 0f)), Offset(dw * 0.42f, dh * 0.78f), 520f)
}

/** The engraved chart painter (decoration only; it reads the simulation's distance-based trail). */
internal class TreasureMapPainter : ThemePainter<TreasureMapSim>() {
    override val minDesignWidth = 1180f
    override val backdrop = Color(0xFF231B14)

    private var geo: MGeo? = null
    private val base = LayerSlot(SceneTheme.TREASURE_MAP)
    private val symbols = LayerSlot(SceneTheme.TREASURE_MAP)
    private val trail = Path()
    private val fresh = Path()
    private val link = Path()
    private val trackTicks = Stroke(5.2f, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.9f, 6.1f), 0f))
    private val trackLine = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val freshLine = Stroke(2.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val smudge = Stroke(10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val dotted = Stroke(1.6f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.5f), 0f))
    private val hair = Stroke(0.8f)
    private val pen = Stroke(1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val rule = Stroke(0.6f)
    private val outline = Stroke(0.7f)
    // Strokes in a seal's unit frame.
    private val u12 = Stroke(0.12f); private val u08 = Stroke(0.08f); private val u06 = Stroke(0.06f); private val u05 = Stroke(0.05f); private val u03 = Stroke(0.03f)
    private val needle = Path().apply { moveTo(15f, 0f); lineTo(0f, -4.2f); lineTo(-12f, 0f); lineTo(0f, 4.2f); close() }
    private val needleFore = Path().apply { moveTo(15f, 0f); lineTo(0f, -4.2f); lineTo(0f, 4.2f); close() }
    /** A wax seal's irregular rim (unit radius). */
    private val seal = Path().apply {
        for (i in 0..24) {
            val a = i * 2f * PI.toFloat() / 24f
            val r = 1f + (hash01(i % 24, 401) - 0.5f) * 0.16f
            if (i == 0) moveTo(cos(a) * r, sin(a) * r) else lineTo(cos(a) * r, sin(a) * r)
        }
        close()
    }
    private var italic: Paint? = null

    private fun geo(dw: Float, dh: Float): MGeo {
        val g = geo
        if (g != null && abs(g.dw - dw) < 0.5f && abs(g.dh - dh) < 0.5f) return g
        return MGeo(dw, dh).also { geo = it }
    }

    override fun tap(sim: TreasureMapSim, x: Float, y: Float) { sim.trailView = !sim.trailView }

    override fun drawPx(s: DrawScope, sim: TreasureMapSim, f: ThemeFrame, tx: ThemeTexts, left: Float, top: Float) {
        val g = geo(f.dw, f.dh)
        val w = f.w.roundToInt(); val h = f.h.roundToInt(); val k = f.k
        val bmp = base.get(((w.toLong() shl 20) xor h.toLong()) * 8L + f.model, w, h, f.density) { withTransform({ scale(k, k, Offset.Zero) }) { sheet(g, f) } }
        s.drawImage(bmp, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(bmp.width, bmp.height))
    }

    // Metres → design units.
    private var sx = 1f; private var ox = 0f; private var oy = 0f
    private fun mx(m: Float) = ox + m * sx
    private fun my(m: Float) = oy - m * sx

    /** Entrance accent: how much of the trail's ink is drawn (1 = all of it, the normal frame). */
    private var inkReach = 1f
    /** Step 5: the track is gilded. */
    private var routeGold = false
    private val goldUnder = Stroke(3.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    /**
     * Legibility rule: small labels show only while parked (1 parked .. 0 moving, eased over 0.3 s; the painter's own
     * state). While moving the sheet keeps its title and, near the destination, one note, both at 24 sp or more.
     */
    private var textFade = -1f
    private var fade = 1f
    private var big = 20f
    private var lastTime = 0.0

    private fun textFade(sim: TreasureMapSim): Float {
        val target = if (sim.moving) 0f else 1f
        textFade = if (textFade < 0f) target else ThemeSim.approach(textFade, target, ((sim.time - lastTime).toFloat().coerceIn(0f, 0.1f)) / 0.3f)
        lastTime = sim.time
        return textFade
    }

    /** 24 sp in design units for this frame. */
    private fun minText(f: ThemeFrame): Float = 24f * f.density.density * f.density.fontScale / f.k

    /** The app's chrome zones of this frame in design units ([ChromeZones]; the moving pair while moving). */
    private var zones: List<ChromeZones.Zone> = emptyList()
    private var zoneKey = 0L
    private val nudged = FloatArray(2)

    private fun zones(f: ThemeFrame, moving: Boolean): List<ChromeZones.Zone> {
        val key = (f.w.toLong() shl 32) xor (f.h.toLong() shl 2) xor (if (moving) 1L else 0L) xor (f.k.toBits().toLong() shl 12)
        if (key != zoneKey) {
            val d = f.density.density
            zones = ChromeZones.current(moving, f.w / d, f.h / d).map { it.scaled(d / f.k) }
            zoneKey = key
        }
        return zones
    }

    override fun draw(s: DrawScope, sim: TreasureMapSim, f: ThemeFrame, tx: ThemeTexts) = with(s) {
        inkReach = if (f.options.intro < 1f) Intro.out(f.options.intro, 0.1f, 0.85f) else 1f
        routeGold = MapStep.gold(f.model)
        fade = textFade(sim)
        big = max(15f, minText(f))
        zones(f, sim.moving)
        val g = geo(f.dw, f.dh)
        // Fit the trail into the chart, leaving the compass rose's corner and the cartouche free.
        val availW = g.mr - g.ml - 230f; val availH = g.mb - g.mt - 130f
        sx = min(availW / 2f / sim.camRx, availH / 2f / sim.camRy) * 0.92f
        ox = g.ml + 40f + availW / 2f - sim.camX * sx
        oy = g.mt + 30f + availH / 2f + sim.camY * sx
        clipRect(g.ml, g.mt, g.mr, g.mb) {
            symbolsAlong(g, sim, f)
            rhumbs(g, sim)
            // The point of departure is marked from the first metre (the sheet is never empty around it).
            if (sim.count >= 1) departure(mx(sim.xs[0]), my(sim.ys[0]), inkReach)
            engrave(g, sim)
            restStamps(g, sim)
            seals(g, sim)
            ticks(g, sim)
            head(g, sim)
            labels(g, sim)
            rain(g, sim)
        }
        scaleBar(g, sim, MapStep.rose(f.model))
        arrival(g, sim)
        night(g, sim, f)
    }

    // ------------------------------------------------------------------ static

    private fun italicPaint(): Paint {
        italic?.let { return it }
        return Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC); textSize = 13f; color = MP.ink.toArgb() }.also { italic = it }
    }

    /** The printed sheet: paper and plate tone, plate mark, graduated neatline, compass rose, cartouche. */
    private fun DrawScope.sheet(g: MGeo, f: ThemeFrame) {
        drawRect(MP.paper, size = Size(f.dw, f.dh))
        // Plate tone: the faint film of ink the wiping leaves, in long horizontal strokes.
        for (i in 0 until 160) {
            val y = hash01(i, 11) * f.dh; val x = hash01(i, 12) * f.dw - 100f
            drawLine(MP.inkFaint.copy(alpha = 0.025f + 0.03f * hash01(i, 13)), Offset(x, y), Offset(x + 200f + hash01(i, 14) * 500f, y + (hash01(i, 15) - 0.5f) * 3f), 0.8f + hash01(i, 16) * 2f)
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, MP.paperEdge.copy(alpha = 0.8f)), Offset(f.dw / 2f, f.dh / 2f), max(f.dw, 1180f) * 0.62f), size = Size(f.dw, f.dh))
        val model = f.model
        parchment(f.dw, f.dh)
        if (MapStep.wash(model)) washes(g.ml, g.mt, g.mr, g.mb)
        // The copper plate's mark: a bevel pressed into the paper just outside the neatline.
        drawRect(MP.plate.copy(alpha = 0.55f), Offset(8f, 8f), Size(f.dw - 16f, f.dh - 16f), style = Stroke(1.6f))
        drawRect(Color.White.copy(alpha = 0.5f), Offset(9.5f, 9.5f), Size(f.dw - 19f, f.dh - 19f), style = Stroke(0.8f))
        // Neatline: a heavy outer rule, a graduated band of alternating black and white ticks, a fine inner rule.
        drawRect(MP.ink, Offset(g.outer, g.outer), Size(f.dw - 2 * g.outer, f.dh - 2 * g.outer), style = Stroke(2.4f))
        drawRect(MP.ink, Offset(g.bandIn, g.bandIn), Size(f.dw - 2 * g.bandIn, f.dh - 2 * g.bandIn), style = Stroke(0.7f))
        drawRect(MP.ink, Offset(g.bandOut, g.bandOut), Size(f.dw - 2 * g.bandOut, f.dh - 2 * g.bandOut), style = Stroke(0.7f))
        val seg = 26f
        val bw = g.bandOut - g.bandIn
        var x = g.bandIn; var n = 0
        while (x < f.dw - g.bandIn) {
            val w = min(seg, f.dw - g.bandIn - x)
            if (n % 2 == 0) { drawRect(MP.ink, Offset(x, g.bandIn), Size(w, bw / 2f)); drawRect(MP.ink, Offset(x, f.dh - g.bandOut + bw / 2f), Size(w, bw / 2f)) }
            else { drawRect(MP.ink, Offset(x, g.bandIn + bw / 2f), Size(w, bw / 2f)); drawRect(MP.ink, Offset(x, f.dh - g.bandOut), Size(w, bw / 2f)) }
            x += seg; n++
        }
        var y = g.bandIn; n = 0
        while (y < f.dh - g.bandIn) {
            val h = min(seg, f.dh - g.bandIn - y)
            if (n % 2 == 0) { drawRect(MP.ink, Offset(g.bandIn, y), Size(bw / 2f, h)); drawRect(MP.ink, Offset(f.dw - g.bandOut + bw / 2f, y), Size(bw / 2f, h)) }
            else { drawRect(MP.ink, Offset(g.bandIn + bw / 2f, y), Size(bw / 2f, h)); drawRect(MP.ink, Offset(f.dw - g.bandOut, y), Size(bw / 2f, h)) }
            y += seg; n++
        }
        drawLine(MP.ink, Offset(g.bandIn + bw / 2f, g.bandIn), Offset(g.bandIn + bw / 2f, f.dh - g.bandIn), 0.5f)
        drawLine(MP.ink, Offset(f.dw - g.bandIn - bw / 2f, g.bandIn), Offset(f.dw - g.bandIn - bw / 2f, f.dh - g.bandIn), 0.5f)
        drawLine(MP.ink, Offset(g.bandIn, g.bandIn + bw / 2f), Offset(f.dw - g.bandIn, g.bandIn + bw / 2f), 0.5f)
        drawLine(MP.ink, Offset(g.bandIn, f.dh - g.bandIn - bw / 2f), Offset(f.dw - g.bandIn, f.dh - g.bandIn - bw / 2f), 0.5f)
        drawRect(MP.ink, Offset(g.inner, g.inner), Size(f.dw - 2 * g.inner, f.dh - 2 * g.inner), style = Stroke(0.9f))
        // Corner squares of the border, cross-hatched.
        for (cx in floatArrayOf(g.outer, f.dw - g.bandOut - 0f)) for (cy in floatArrayOf(g.outer, f.dh - g.bandOut)) {
            val l = if (cx < f.dw / 2f) g.outer else f.dw - g.inner; val t = if (cy < f.dh / 2f) g.outer else f.dh - g.inner
            drawRect(MP.paper, Offset(l, t), Size(20f, 20f))
            drawRect(MP.ink, Offset(l, t), Size(20f, 20f), style = Stroke(1f))
            for (i in 1 until 7) { drawLine(MP.ink, Offset(l + i * 20f / 7f, t), Offset(l, t + i * 20f / 7f), 0.5f); drawLine(MP.ink, Offset(l + 20f - i * 20f / 7f, t + 20f), Offset(l + 20f, t + 20f - i * 20f / 7f), 0.5f) }
        }
        // The rose's network of rhumb lines across the whole chart, very faint (sixteen winds).
        val roseStart = if (MapStep.rose(model)) g.roseR + 16f else 32f
        clipRect(g.ml, g.mt, g.mr, g.mb) {
            graticule(g.ml, g.mt, g.mr, g.mb)
            if (MapStep.rose(model)) terrain(g.ml, g.mt, g.mr, g.mb)
            if (MapStep.decor(model)) seaLife(g.ml, g.mt, g.mr, g.mb)
            val decor = MapStep.decor(model)
            // From step 4 the winds are ruled in a portolan's colours (black for the eight winds, green and red between).
            val c2 = Offset(g.ml + (g.mr - g.ml) * 0.34f, g.mt + (g.mb - g.mt) * 0.70f)
            for (i in 0 until 16) {
                val a = i * PI.toFloat() / 8f
                val c = g.compass
                val col = if (!decor) MP.inkFaint.copy(alpha = if (i % 2 == 0) 0.50f else 0.30f)
                else when (i % 4) { 0 -> MP.ink.copy(alpha = 0.55f); 2 -> Color(0xFF2E6B4A).copy(alpha = 0.6f); else -> Color(0xFFA83A2C).copy(alpha = 0.55f) }
                val wd = if (decor) (if (i % 4 == 0) 1.4f else 1.0f) else (if (i % 4 == 0) 0.8f else 0.5f)
                drawLine(col, Offset(c.x + cos(a) * roseStart, c.y + sin(a) * roseStart), Offset(c.x + cos(a) * 3000f, c.y + sin(a) * 3000f), wd)
                if (decor) drawLine(col.copy(alpha = col.alpha * 0.8f), Offset(c2.x + cos(a) * 34f, c2.y + sin(a) * 34f), Offset(c2.x + cos(a) * 3000f, c2.y + sin(a) * 3000f), wd * 0.8f)
            }
            if (decor) windMark(c2)
        }
        if (MapStep.rose(model)) compassRose(g) else windMark(g.compass)
        cartouche(g, f, framed = MapStep.decor(model))
        if (MapStep.gold(model)) {
            goldBorder(f.dw, f.dh, g.outer, g.bandIn, g.bandOut, g.inner)
            waxSeal(Offset(g.mr - 66f, g.mb - 150f))
        }
    }

    /** A 32-point rose: each point split into a hatched (shaded) half and a clean (lit) half, in rings of ticks. */
    private fun DrawScope.compassRose(g: MGeo) {
        val c = g.compass; val r = g.roseR
        drawCircle(MP.ink, r + 14f, c, style = Stroke(1.6f))
        drawCircle(MP.ink, r + 10f, c, style = Stroke(0.6f))
        for (i in 0 until 72) {
            val a = i * 2f * PI.toFloat() / 72f
            val r0 = if (i % 9 == 0) r + 2f else r + 6f
            drawLine(MP.ink, Offset(c.x + cos(a) * r0, c.y + sin(a) * r0), Offset(c.x + cos(a) * (r + 10f), c.y + sin(a) * (r + 10f)), if (i % 9 == 0) 1f else 0.6f)
        }
        val half = Path()
        for (pass in 0 until 3) {
            val count = when (pass) { 0 -> 16; 1 -> 8; else -> 4 }
            val len = when (pass) { 0 -> r * 0.42f; 1 -> r * 0.68f; else -> r }
            val wid = when (pass) { 0 -> 4f; 1 -> 7f; else -> 10f }
            for (i in 0 until count) {
                val a = i * 2f * PI.toFloat() / count - PI.toFloat() / 2f + (if (pass == 0) PI.toFloat() / 16f else if (pass == 1) PI.toFloat() / 8f else 0f)
                val tipX = c.x + cos(a) * len; val tipY = c.y + sin(a) * len
                val nx = -sin(a); val ny = cos(a)
                for (side in 0..1) {
                    val sg = if (side == 0) 1f else -1f
                    half.reset()
                    half.moveTo(c.x, c.y); half.lineTo(tipX, tipY); half.lineTo(c.x + nx * wid * sg + cos(a) * wid, c.y + ny * wid * sg + sin(a) * wid); half.close()
                    drawPath(half, MP.paper)
                    if (side == 0) {
                        // Shaded half: engraved hatching parallel to the point's axis.
                        val steps = (len / 2.4f).toInt()
                        for (k in 1 until steps) {
                            val u = k / steps.toFloat()
                            val px = c.x + cos(a) * len * u; val py = c.y + sin(a) * len * u
                            val wv = wid * (if (u < wid / len) u * len / wid else (1f - u) / (1f - wid / len))
                            drawLine(MP.ink, Offset(px, py), Offset(px + nx * wv, py + ny * wv), 0.55f)
                        }
                    }
                    drawPath(half, MP.ink, style = Stroke(0.8f))
                }
            }
        }
        drawCircle(MP.paper, 5f, c); drawCircle(MP.ink, 5f, c, style = Stroke(1f)); drawCircle(MP.ink, 1.6f, c)
        val p = italicPaint()
        val nc = drawContext.canvas.nativeCanvas
        p.textAlign = Paint.Align.CENTER; p.textSize = 18f; p.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        nc.drawText("N", c.x, c.y - r - 20f, p)
        p.typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC); p.textSize = 13f; p.textAlign = Paint.Align.LEFT
    }

    /** The title cartouche: a double-ruled frame with cut corners and an engraved shadow, "여정 일지". */
    private fun DrawScope.cartouche(g: MGeo, f: ThemeFrame, framed: Boolean) {
        val l = g.cartL; val t = g.cartT; val w = g.cartW; val h = g.cartH
        val cut = 12f
        val frame = Path().apply {
            moveTo(l + cut, t); lineTo(l + w - cut, t); quadraticTo(l + w - cut, t + cut, l + w, t + cut)
            lineTo(l + w, t + h - cut); quadraticTo(l + w - cut, t + h - cut, l + w - cut, t + h)
            lineTo(l + cut, t + h); quadraticTo(l + cut, t + h - cut, l, t + h - cut)
            lineTo(l, t + cut); quadraticTo(l + cut, t + cut, l + cut, t); close()
        }
        // Engraved shadow to the lower right (diagonal hatching).
        if (framed) withTransform({ translate(5f, 5f) }) {
            clipRect(l, t, l + w + 6f, t + h + 6f) {
                var d = -h
                while (d < w + h) { drawLine(MP.ink, Offset(l + d, t + h + 6f), Offset(l + d + h + 6f, t), 0.5f); d += 2.6f }
            }
        }
        if (framed) {
            drawPath(frame, MP.paper)
            drawPath(frame, MP.ink, style = Stroke(1.6f))
            withTransform({ scale(0.94f, 0.88f, Offset(l + w / 2f, t + h / 2f)) }) { drawPath(frame, MP.ink, style = Stroke(0.6f)) }
        }
        // Small scroll flourishes at the ends of a rule under the title.
        drawLine(MP.ink, Offset(l + 50f, t + 64f), Offset(l + w - 50f, t + 64f), 0.7f)
        drawCircle(MP.ink, 2.2f, Offset(l + 46f, t + 64f), style = Stroke(0.7f))
        drawCircle(MP.ink, 2.2f, Offset(l + w - 46f, t + 64f), style = Stroke(0.7f))
        val nc = drawContext.canvas.nativeCanvas
        // The title stays legible while driving: at least 24 sp (it is the one fixed text of the sheet).
        val size = max(27f, minText(f) * 1.05f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textSize = size; color = MP.ink.toArgb(); textAlign = Paint.Align.CENTER; letterSpacing = 0.16f }
        nc.drawText("여정 일지", l + w / 2f, t + 22f + size * 0.8f, p)
    }

    /** One engraved cartographic symbol per landscape, in a cell [CELL] wide, base line at y = 0. */
    private fun DrawScope.symbol(kind: Int) {
        when (kind) {
            0 -> { // CITY: a tiny walled town plan (blocks cross-hatched, streets left white).
                drawCircle(MP.ink, 15f, Offset(0f, -14f), style = Stroke(1f))
                drawCircle(MP.ink, 13f, Offset(0f, -14f), style = Stroke(0.5f))
                for (bx in -2..1) for (by in -2..1) {
                    val x = bx * 6.4f + 1f; val y = -14f + by * 6.4f + 1f
                    if ((bx * bx + by * by) > 5) continue
                    drawRect(MP.ink, Offset(x, y), Size(4.6f, 4.6f), style = Stroke(0.5f))
                    drawLine(MP.ink, Offset(x, y + 4.6f), Offset(x + 4.6f, y), 0.45f)
                    drawLine(MP.ink, Offset(x, y + 2.3f), Offset(x + 2.3f, y), 0.45f)
                    drawLine(MP.ink, Offset(x + 2.3f, y + 4.6f), Offset(x + 4.6f, y + 2.3f), 0.45f)
                }
                for (i in 0 until 4) { val a = i * PI.toFloat() / 2f + PI.toFloat() / 4f; drawCircle(MP.ink, 1.6f, Offset(cos(a) * 15f, -14f + sin(a) * 15f)) }
            }
            1 -> { // PARK: a wood of conifers as hatched cones (shaded on the right), back row higher and smaller.
                for (row in 0..1) for (i in 0 until (if (row == 0) 4 else 5)) {
                    val x = -20f + i * 9.5f + (if (row == 0) 4.5f else 0f) + (hash01(i + row * 7, 311) - 0.5f) * 2f
                    val b = if (row == 0) -7f else 0f
                    val ht = (if (row == 0) 12f else 16f) + hash01(i + row * 7, 312) * 4f
                    val hw = ht * 0.3f
                    drawPath(Path().apply { moveTo(x - hw, b); lineTo(x, b - ht); lineTo(x + hw, b); close() }, MP.paper)
                    var k = 0.8f
                    while (k < hw) { drawLine(MP.ink, Offset(x + k, b), Offset(x + k * 0.15f, b - ht * (1f - k / hw) * 0.92f - 0.5f), 0.5f); k += 1.1f }
                    drawLine(MP.ink, Offset(x - hw, b), Offset(x, b - ht), 0.8f)
                    drawLine(MP.ink, Offset(x, b - ht), Offset(x + hw, b), 0.8f)
                    drawLine(MP.ink, Offset(x - hw, b), Offset(x + hw, b), 0.5f)
                    drawLine(MP.ink, Offset(x, b), Offset(x, b + 2.2f), 0.8f)
                }
            }
            2 -> { // FIELD: enclosed plots stippled in rows.
                val plot = Path().apply { moveTo(-22f, -2f); lineTo(-6f, -18f); lineTo(20f, -14f); lineTo(16f, 2f); close() }
                drawPath(plot, MP.ink, style = Stroke(0.7f))
                drawLine(MP.ink, Offset(-12f, -12f), Offset(18f, -6f), 0.5f)
                for (r in 0 until 5) for (cI in 0 until 9) {
                    val x = -16f + cI * 4.2f + r * 1.6f; val y = -14f + r * 3.2f
                    if (x > 16f + r * 0f || x < -20f + r * 3f) continue
                    drawCircle(MP.ink, 0.55f, Offset(x, y))
                }
            }
            3 -> { // SEASIDE: a stippled coast with water-lining off shore.
                val coast = Path().apply { moveTo(-24f, -20f); cubicTo(-12f, -14f, -8f, -4f, 4f, -6f); cubicTo(12f, -8f, 16f, 0f, 24f, 2f) }
                drawPath(coast, MP.ink, style = Stroke(1f))
                for (i in 1..3) withTransform({ translate(i * 1.4f, i * 3.2f) }) { drawPath(coast, MP.ink.copy(alpha = 1f - i * 0.25f), style = Stroke(0.45f)) }
                for (i in 0 until 40) {
                    val u = hash01(i, 301); val d = hash01(i, 302) * 9f
                    val x = -24f + u * 48f
                    val y = -20f + u * 22f - d - 2f - (if (u in 0.3f..0.6f) 3f else 0f)
                    drawCircle(MP.ink, 0.5f, Offset(x, y))
                }
            }
            else -> { // MOUNTAIN: two peaks drawn with hachures, heavier on the shaded side.
                for ((px, ph, pw) in PEAKS) {
                    for (i in 0..12) {
                        val a = PI.toFloat() * (0.1f + 0.8f * i / 12f)
                        val shade = cos(a) < 0f
                        val len = ph * (0.55f + 0.25f * sin(a * 3f))
                        val sxp = px + cos(a) * pw * 0.12f; val syp = -ph + sin(a) * 2f
                        drawLine(MP.ink, Offset(sxp, syp), Offset(sxp - cos(a) * pw * 0.5f, syp + len), if (shade) 0.5f else 0.9f)
                    }
                    drawLine(MP.ink, Offset(px - pw * 0.15f, -ph + 1f), Offset(px + pw * 0.15f, -ph + 1f), 0.8f)
                }
            }
        }
    }

    // ------------------------------------------------------------------ moving

    private fun DrawScope.symbolsAlong(g: MGeo, sim: TreasureMapSim, f: ThemeFrame) {
        if (sim.doodles == 0) return
        val k = f.k
        val ks = k * SYM
        val cw = (CELL * ks).roundToInt(); val ch = (CELL_H * ks).roundToInt()
        val img = symbols.get((cw.toLong() shl 20) xor ch.toLong(), cw * 5, ch, f.density) {
            for (kind in 0 until 5) withTransform({ scale(ks, ks, Offset.Zero); translate(kind * CELL + CELL / 2f, CELL_H * 0.78f) }) { symbol(kind) }
        }
        val n = sim.count
        for (i in 0 until sim.doodles) {
            val x0 = mx(sim.doodleX[i]) + sim.doodleSide[i] * 40f
            val y0 = my(sim.doodleY[i]) - 30f * sim.doodleSide[i]
            val a = if (inkReach < 1f) ((inkReach * n - nearestIndex(sim, sim.doodleX[i], sim.doodleY[i])) / 20f).coerceIn(0f, 1f) else 1f
            if (a <= 0.01f) continue
            withTransform({ scale(1f / k, 1f / k, Offset.Zero) }) {
                drawImage(img, srcOffset = IntOffset(sim.doodleKind[i].coerceIn(0, 4) * cw, 0), srcSize = IntSize(cw, ch),
                    dstOffset = IntOffset(((x0 - CELL * SYM / 2f) * k).roundToInt(), ((y0 - CELL_H * SYM * 0.78f) * k).roundToInt()), dstSize = IntSize(cw, ch), alpha = a)
            }
        }
    }

    /** The trail point index nearest in order to a doodle (only during the entrance, to fade symbols in with the ink). */
    private fun nearestIndex(sim: TreasureMapSim, x: Float, y: Float): Float {
        var best = 0; var bd = Float.MAX_VALUE
        val step = max(1, sim.count / 200)
        var i = 0
        while (i < sim.count) { val dx = sim.xs[i] - x; val dy = sim.ys[i] - y; val d = dx * dx + dy * dy; if (d < bd) { bd = d; best = i }; i += step }
        return best.toFloat()
    }

    /** The engraved track: a fine line with cross ticks (a track symbol); the newest ink is heavier. */
    private fun DrawScope.engrave(g: MGeo, sim: TreasureMapSim) {
        val n = sim.count
        if (n < 2) return
        val reach = if (inkReach < 1f) (n - 1) * inkReach else (n - 1).toFloat()
        val whole = reach.toInt().coerceIn(0, n - 1)
        var freshStart = n
        if (inkReach >= 1f) for (i in 0 until n) if (sim.inkAge(i) < 3f) { freshStart = i; break }
        trail.reset(); fresh.reset()
        val end = min(whole, freshStart)
        for (i in 0..end) {
            val x = mx(sim.xs[i]); val y = my(sim.ys[i])
            if (i == 0) trail.moveTo(x, y) else trail.lineTo(x, y)
        }
        if (inkReach < 1f && whole + 1 < n) {
            val t = reach - whole
            val ax = mx(sim.xs[whole]); val ay = my(sim.ys[whole])
            trail.lineTo(ax + (mx(sim.xs[whole + 1]) - ax) * t, ay + (my(sim.ys[whole + 1]) - ay) * t)
        }
        drawPath(trail, MP.ink, style = trackTicks)
        if (routeGold) { drawPath(trail, GOLD_DARK, style = goldUnder); drawPath(trail, GOLD_LIGHT, style = trackLine) }
        else drawPath(trail, MP.ink, style = trackLine)
        if (freshStart < n) {
            for (i in max(0, freshStart - 1) until n) {
                val x = mx(sim.xs[i]); val y = my(sim.ys[i])
                if (i == max(0, freshStart - 1)) fresh.moveTo(x, y) else fresh.lineTo(x, y)
            }
            if (sim.rain > 0.3f) drawPath(fresh, MP.inkSoft.copy(alpha = 0.16f * sim.rain), style = smudge)
            drawPath(fresh, MP.ink, style = trackTicks)
            if (routeGold) { drawPath(fresh, GOLD_DARK, style = goldUnder); drawPath(fresh, GOLD_LIGHT, style = freshLine) }
            else drawPath(fresh, MP.ink, style = freshLine)
        }
        // Point of departure: a ring with a dot.
        val sx0 = mx(sim.xs[0]); val sy0 = my(sim.ys[0])
        drawCircle(MP.paper, 6.5f, Offset(sx0, sy0)); drawCircle(MP.ink, 6.5f, Offset(sx0, sy0), style = pen); drawCircle(MP.ink, 1.8f, Offset(sx0, sy0))
    }

    /** The combo: dotted madder rhumb lines run from the compass rose to the last seal of each linked group. */
    private fun DrawScope.rhumbs(g: MGeo, sim: TreasureMapSim) {
        if (sim.links <= 0) return
        link.reset()
        for (i in 0 until sim.links) {
            val a = sim.linkFrom[i]; val b = sim.linkTo[i]
            link.moveTo(mx(sim.markX[a]) + 14f, my(sim.markY[a]) - 14f); link.lineTo(mx(sim.markX[b]) + 14f, my(sim.markY[b]) - 14f)
        }
        drawPath(link, MP.madder.copy(alpha = 0.85f), style = dotted)
        val c = g.compass
        val newest = (sim.since(sim.comboAt) / 1.5).toFloat().coerceIn(0f, 1f)
        for (j in 0 until sim.links) {
            val endOfChain = j == sim.links - 1 || sim.linkFrom[j + 1] != sim.linkTo[j]
            if (!endOfChain) continue
            val m = sim.linkTo[j]
            val tx = mx(sim.markX[m]) + 14f; val ty = my(sim.markY[m]) - 14f
            val reach = if (j == sim.links - 1) newest * inkReach else inkReach
            if (reach <= 0f) continue
            link.reset(); link.moveTo(c.x, c.y); link.lineTo(c.x + (tx - c.x) * reach, c.y + (ty - c.y) * reach)
            drawPath(link, MP.madder.copy(alpha = 0.9f), style = dotted)
        }
    }

    /** The reward: a madder wax seal pressed beside the trail (it settles from slightly large; no bounce). */
    private fun DrawScope.seals(g: MGeo, sim: TreasureMapSim) {
        for (i in 0 until sim.marks) {
            val x = mx(sim.markX[i]) + 14f; val y = my(sim.markY[i]) - 14f
            val age = (sim.decoTime - sim.markAt[i]).toFloat()
            val press = (age / 0.5f).coerceIn(0f, 1f) * (if (inkReach < 1f) Intro.out(inkReach, 0.6f, 0.4f) else 1f)
            if (press <= 0.01f) continue
            val r = 11f * (1.25f - 0.25f * press)
            withTransform({ translate(x, y); scale(r, r, Offset.Zero) }) {
                drawPath(seal, MP.madderDark.copy(alpha = press), style = u12)
                drawPath(seal, MP.madder.copy(alpha = press))
                drawCircle(MP.madderDark.copy(alpha = 0.7f * press), 0.68f, Offset.Zero, style = u08)
                // The impressed device: an eight-ray rosette with a ring.
                for (k in 0 until 8) {
                    val a = k * PI.toFloat() / 4f
                    drawLine(MP.madderLight.copy(alpha = 0.9f * press), Offset(cos(a) * 0.14f, sin(a) * 0.14f), Offset(cos(a) * 0.5f, sin(a) * 0.5f), 0.07f, cap = StrokeCap.Round)
                }
                drawCircle(MP.madderLight.copy(alpha = 0.9f * press), 0.14f, Offset.Zero, style = u06)
                drawArc(Color.White.copy(alpha = 0.35f * press), 200f, 70f, false, Offset(-0.85f, -0.85f), Size(1.7f, 1.7f), style = u08)
            }
        }
    }

    private fun DrawScope.restStamps(g: MGeo, sim: TreasureMapSim) {
        for (i in 0 until sim.stamps) {
            val x = mx(sim.stampX[i]); val y = my(sim.stampY[i]) + 18f
            drawCircle(MP.paper, 7f, Offset(x, y))
            drawCircle(MP.ink, 7f, Offset(x, y), style = pen)
            drawCircle(MP.ink, 4f, Offset(x, y), style = hair)
            drawLine(MP.ink, Offset(x, y - 7f), Offset(x, y - 13f), 0.8f)
        }
        for (i in 0 until sim.spats) {
            val x = mx(sim.spatX[i]); val y = my(sim.spatY[i])
            for (k in 0 until 14) {
                val a = hash01(sim.spatSeed[i], k) * 6.28f; val d = 5f + hash01(sim.spatSeed[i], k + 10) * 12f
                drawCircle(MP.ink.copy(alpha = 0.7f), 0.5f + hash01(sim.spatSeed[i], k + 20) * 1.2f, Offset(x + cos(a) * d, y + sin(a) * d))
            }
        }
    }

    private fun DrawScope.ticks(g: MGeo, sim: TreasureMapSim) {
        val nc = drawContext.canvas.nativeCanvas
        val p = italicPaint()
        p.textAlign = Paint.Align.CENTER; p.textSize = 11f; p.color = MP.ink.copy(alpha = fade).toArgb()
        for (i in 0 until sim.ticks) {
            val x = mx(sim.tickX[i]); val y = my(sim.tickY[i])
            val a = sim.tickAng[i] + PI.toFloat() / 2f
            drawLine(MP.ink, Offset(x - cos(a) * 8f, y + sin(a) * 8f), Offset(x + cos(a) * 8f, y - sin(a) * 8f), 2f, cap = StrokeCap.Butt)
            if (fade > 0.01f) nc.drawText(KM[sim.tickKm[i].coerceIn(0, KM.size - 1)], x + cos(a) * 18f, y - sin(a) * 18f + 4f, p)
        }
        p.textAlign = Paint.Align.LEFT; p.textSize = 13f
    }

    /** The trail head: a compass needle along the direction of travel (dark fore half, hatched aft half). */
    private fun DrawScope.head(g: MGeo, sim: TreasureMapSim) {
        if (inkReach < 1f) return
        val x = mx(sim.headX); val y = my(sim.headY)
        val h = sim.heading.toFloat()
        val deg = -h * 180f / PI.toFloat()
        withTransform({ translate(x, y); rotate(deg, Offset.Zero); scale(2f, 2f, Offset.Zero) }) {
            drawPath(needle, MP.paper)
            drawPath(needleFore, MP.ink)
            for (k in 1 until 6) { val u = -k * 2f; drawLine(MP.ink, Offset(u, -4.2f * (1f + u / 12f)), Offset(u, 4.2f * (1f + u / 12f)), 0.45f) }
            drawPath(needle, MP.ink, style = outline)
            drawCircle(MP.paper, 2f, Offset.Zero); drawCircle(MP.ink, 2f, Offset.Zero, style = rule)
        }
        // Blinker: a small engraved arrow beside the needle on that side (never ahead of it).
        for (side in 0..1) {
            val lamp = if (side == 0) sim.blinkL else sim.blinkR
            if (lamp < 0.05f) continue
            val sd = if (side == 0) 1f else -1f
            val nx = -sin(h) * sd; val ny = cos(h) * sd
            val ax = x + nx * 26f; val ay = y - ny * 26f
            val dx = nx * 9f; val dy = -ny * 9f
            drawLine(MP.ink.copy(alpha = lamp), Offset(ax - dx, ay - dy), Offset(ax + dx, ay + dy), 1.4f, cap = StrokeCap.Round)
            drawLine(MP.ink.copy(alpha = lamp), Offset(ax + dx, ay + dy), Offset(ax + dx * 0.4f - dy * 0.5f, ay + dy * 0.4f + dx * 0.5f), 1.4f, cap = StrokeCap.Round)
            drawLine(MP.ink.copy(alpha = lamp), Offset(ax + dx, ay + dy), Offset(ax + dx * 0.4f + dy * 0.5f, ay + dy * 0.4f - dx * 0.5f), 1.4f, cap = StrokeCap.Round)
        }
        // Near the destination: an engraved pennant beside the head (no direction shown).
        val nd = sim.nearDestination
        if (nd > 0.01f) {
            drawLine(MP.ink.copy(alpha = nd), Offset(x + 18f, y - 6f), Offset(x + 18f, y - 36f), 1.2f)
            link.reset(); link.moveTo(x + 18f, y - 36f); link.lineTo(x + 36f, y - 31f); link.lineTo(x + 18f, y - 26f); link.close()
            drawPath(link, MP.madder.copy(alpha = nd))
        }
    }

    /** Place names in serif italic beside the first symbol of each landscape, and the notes of the sheet. */
    private fun DrawScope.labels(g: MGeo, sim: TreasureMapSim) {
        val nc = drawContext.canvas.nativeCanvas
        val p = italicPaint()
        p.textAlign = Paint.Align.LEFT; p.textSize = 13f
        val nd = sim.nearDestination
        if (nd > 0.01f) {
            // The one note that stays while driving, at 24 sp or more.
            p.textSize = big
            p.color = MP.madderDark.copy(alpha = nd).toArgb()
            val tx0 = (mx(sim.headX) + 40f).coerceIn(g.ml + 10f, g.mr - big * 6f)
            val ty0 = (my(sim.headY) - 28f).coerceIn(g.mt + big, g.mb - 10f)
            // The note stays clear of the app's chrome zones: it moves the shortest way out of any it would meet.
            val tw = p.measureText("목적지 근처"); val th = big * 1.25f
            ChromeZones.nudge(zones, tx0, ty0 - big, tw, th, g.ml + 10f, g.mt, g.mr - tw - 10f, g.mb - 10f - th, nudged)
            val tx = nudged[0]; val ty = nudged[1] + big
            nc.drawText("목적지 근처", tx, ty, p)
            p.textSize = 13f
        }
        if (fade < 0.01f) return
        p.color = MP.ink.copy(alpha = inkReach * fade).toArgb()
        for (i in 0 until sim.doodles) {
            val kind = sim.doodleKind[i].coerceIn(0, 4)
            if (i > 0 && sim.doodleKind[i - 1] == kind) continue
            val x0 = mx(sim.doodleX[i]) + sim.doodleSide[i] * 40f
            val y0 = my(sim.doodleY[i]) - 30f * sim.doodleSide[i]
            nc.drawText(PLACE[kind], x0 + 26f, y0 + 12f, p)
        }
        p.color = MP.ink.copy(alpha = fade).toArgb()
        if (sim.count > 1) nc.drawText("출발", mx(sim.xs[0]) + 10f, my(sim.ys[0]) + 18f, p)
        for (i in 0 until sim.stamps) {
            val x = mx(sim.stampX[i]); val y = my(sim.stampY[i]) + 18f
            nc.drawText("쉼터", x + 10f, y + 4f, p)
        }
        p.color = MP.ink.toArgb()
    }

    /** A scale bar of alternating blocks; its length is a round distance at the chart's current scale. */
    private fun DrawScope.scaleBar(g: MGeo, sim: TreasureMapSim, bar: Boolean) {
        var pick = 0
        for (i in SCALE_M.indices) { if (SCALE_M[i] * sx <= 200f) pick = i }
        val len = SCALE_M[pick] * sx
        val x = g.scaleX; val y = g.scaleY
        val seg = len / 4f
        if (bar) for (i in 0 until 4) {
            if (i % 2 == 0) drawRect(MP.ink, Offset(x + i * seg, y - 3f), Size(seg, 4f))
            drawRect(MP.ink, Offset(x + i * seg, y - 3f), Size(seg, 4f), style = rule)
        }
        if (fade < 0.01f) return
        val nc = drawContext.canvas.nativeCanvas
        val p = italicPaint()
        p.textSize = 12f; p.color = MP.ink.copy(alpha = fade).toArgb()
        p.textAlign = Paint.Align.CENTER
        if (bar) {
            nc.drawText("0", x, y - 9f, p)
            nc.drawText(SCALE_LABEL[pick], x + len, y - 9f, p)
        }
        // The cartouche's subtitle (a note on what the chart shows), parked only.
        p.color = MP.inkSoft.copy(alpha = fade).toArgb()
        nc.drawText("지나온 길만 새긴 항적도", g.cartL + g.cartW / 2f, g.cartT + 82f, p)
        p.color = MP.ink.toArgb()
        p.textAlign = Paint.Align.LEFT; p.textSize = 13f
    }

    private fun DrawScope.rain(g: MGeo, sim: TreasureMapSim) {
        val r = sim.rain
        if (r < 0.02f) return
        // Water marks: pale tide rings where drops dried on the paper.
        for (i in 0 until 36) {
            if (hash01(i, 81) > r) continue
            val x = g.ml + 20f + hash01(i, 82) * (g.mr - g.ml - 40f); val y = g.mt + 20f + hash01(i, 83) * (g.mb - g.mt - 40f)
            val rr = 4f + hash01(i, 84) * 10f
            drawCircle(MP.paperEdge.copy(alpha = 0.45f), rr, Offset(x, y))
            drawCircle(MP.inkFaint.copy(alpha = 0.35f), rr, Offset(x, y), style = hair)
        }
    }

    /** Arrival: a large seal is pressed at the end of the track (the chart is finished). */
    private fun DrawScope.arrival(g: MGeo, sim: TreasureMapSim) {
        val a = ((sim.roll - 0.4f) / 0.6f).coerceIn(0f, 1f)
        if (a <= 0.01f) return
        val x = (mx(sim.headX) + 34f).coerceIn(g.ml + 40f, g.mr - 40f); val y = (my(sim.headY) + 30f).coerceIn(g.mt + 40f, g.mb - 40f)
        val r = 24f * (1.2f - 0.2f * a)
        withTransform({ translate(x, y); scale(r, r, Offset.Zero) }) {
            drawPath(seal, MP.madder.copy(alpha = a))
            drawCircle(MP.madderDark.copy(alpha = 0.8f * a), 0.74f, Offset.Zero, style = u05)
            drawCircle(MP.madderDark.copy(alpha = 0.8f * a), 0.66f, Offset.Zero, style = u03)
            for (k in 0 until 16) {
                val ang = k * PI.toFloat() / 8f
                val l = if (k % 2 == 0) 0.52f else 0.34f
                drawLine(MP.madderLight.copy(alpha = 0.9f * a), Offset(0f, 0f), Offset(cos(ang) * l, sin(ang) * l), 0.045f, cap = StrokeCap.Round)
            }
            drawArc(Color.White.copy(alpha = 0.3f * a), 200f, 70f, false, Offset(-0.88f, -0.88f), Size(1.76f, 1.76f), style = u06)
        }
    }

    /** Night: the same sheet by candlelight (a warm pool low in the frame, the corners in darkness). */
    private fun DrawScope.night(g: MGeo, sim: TreasureMapSim, f: ThemeFrame) {
        val n = sim.night
        if (n < 0.01f) return
        drawRect(g.candleLight, size = Size(f.dw, f.dh), alpha = n)
        drawRect(g.candleWarm, size = Size(f.dw, f.dh), alpha = n)
    }
}

private const val CELL = 64f
/** Symbols are engraved 1.3 times their cell size (the sheet is rendered at that scale, drawn 1:1). */
private const val SYM = 1.3f
private const val CELL_H = 40f
private val PEAKS = listOf(Triple(-9f, 22f, 26f), Triple(10f, 16f, 22f))
private val PLACE = arrayOf("시가지", "숲", "들판", "해안", "산지")
private val SCALE_M = floatArrayOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)
private val SCALE_LABEL = arrayOf("50 m", "100 m", "200 m", "500 m", "1 km", "2 km", "5 km", "10 km")

/** km labels (built once; shown only while parked). */
private val KM = Array(1000) { it.toString() }
