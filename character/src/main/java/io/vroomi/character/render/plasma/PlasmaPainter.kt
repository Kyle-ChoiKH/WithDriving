package io.vroomi.character.render.plasma

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.vroomi.character.calibre.SceneTheme
import io.vroomi.character.plasma.PlasmaSim
import io.vroomi.character.render.hash01
import io.vroomi.character.render.theme.LayerSlot
import io.vroomi.character.render.theme.ThemeFrame
import io.vroomi.character.render.theme.ThemePainter
import io.vroomi.character.render.theme.ThemeTexts
import io.vroomi.core.vehicle.model.TurnSignal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Where things stand on the bench for one design width: the globe in the middle, the instruments either side. */
private class Bench(val dw: Float) {
    val cx = dw / 2f
    /** The instruments move in on a narrower canvas. */
    private val sp = (dw / 1600f).coerceIn(0.8f, 1f)
    val globe = Offset(cx, 258f)
    val counterX = cx - 520f * sp
    val scopeX = cx - 296f * sp
    val valvesX = cx + 304f * sp
    val ladderX = cx + 520f * sp

    companion object {
        const val R = 158f
        const val DESK = 474f
    }
}

/**
 * PLASMA: a plasma globe on a dark lab bench (spec 5.11). Violet filaments wander from the electrode to the glass;
 * their core is white, the inside of the glass glows softly. The instruments of the upgrade steps stand on the
 * bench: the neon counter and the scope at the left (they carry readings, so they stay clear of the app's
 * corners), the valves and the ladder at the right.
 *
 * The room, the bench, the globe's base and the instruments' bodies are one cached bitmap; the light moves.
 */
internal class PlasmaPainter : ThemePainter<PlasmaSim>() {
    override val minDesignWidth = 1280f
    override val focusWidth = 1000f
    override fun headroom(sim: PlasmaSim): Color = Lab.roomTop
    override val backdrop = Lab.roomTop

    private val base = LayerSlot(SceneTheme.PLASMA)
    private var bench: Bench? = null
    private val strand = Path()
    private val arc = Path()
    private val clip = Path()
    private val glow = Stroke(11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val mid = Stroke(4.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val core = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val arcGlow = Stroke(8f, cap = StrokeCap.Round)
    private val arcMid = Stroke(3.2f, cap = StrokeCap.Round)
    private val arcCore = Stroke(1.2f, cap = StrokeCap.Round)
    private val rim = Stroke(2f)
    private val band = Stroke(7f, cap = StrokeCap.Round)
    private val bandThin = Stroke(3.6f, cap = StrokeCap.Round)
    /** The gas of the chosen step: its glow and its filament body (and the second pair, for a mixed gas). */
    private var gasV = Lab.violet
    private var gasM = Lab.magenta
    private var gasV2 = Lab.violet
    private var gasM2 = Lab.magenta

    private fun bench(dw: Float): Bench {
        val b = bench
        if (b != null && abs(b.dw - dw) < 0.5f) return b
        return Bench(dw).also { bench = it }
    }

    override fun drawPx(s: DrawScope, sim: PlasmaSim, f: ThemeFrame, tx: ThemeTexts, left: Float, top: Float) {
        val g = bench(f.dw)
        val w = f.w.roundToInt(); val h = f.h.roundToInt()
        val key = ((w.toLong() shl 20) xor h.toLong()) * 8 + f.model
        val k = f.k
        val model = f.model
        val bmp = base.get(key, w, h, f.density) {
            withTransform({ scale(k, k, Offset.Zero) }) { staticLayer(g, f, model) }
        }
        s.drawImage(bmp, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(bmp.width, bmp.height))
    }

    // ------------------------------------------------------------------ static (cached)

    private fun DrawScope.staticLayer(g: Bench, f: ThemeFrame, model: Int) {
        val w = f.dw; val h = f.dh
        val desk = Bench.DESK
        // The room: blue-black, a faint cool light on the wall behind the globe.
        drawRect(Brush.verticalGradient(0f to Lab.roomTop, 1f to Lab.roomBottom, startY = 0f, endY = desk), size = Size(w, desk))
        val wall = if (model == 0) Color(0xFF3A3560) else androidx.compose.ui.graphics.lerp(GAS[model.coerceIn(0, GAS.size - 1)][0], Color(0xFF14121E), 0.55f)
        drawRect(Brush.radialGradient(listOf(wall.copy(alpha = if (model == 0) 0.30f else 0.42f), Color.Transparent), Offset(g.cx, 230f), 620f), size = Size(w, desk))
        // The bench top: a satin dark surface, its front edge catching a little light.
        drawRect(Brush.verticalGradient(0f to Lab.desk, 1f to Lab.deskDeep, startY = desk, endY = h), Offset(0f, desk), Size(w, h - desk))
        drawRect(Color.White.copy(alpha = 0.08f), Offset(0f, desk), Size(w, 1.4f))
        drawRect(Brush.radialGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f), center = Offset(g.cx, h * 0.45f), radius = w * 0.62f), size = Size(w, h))

        // The instruments' bodies, left to right in the order of the steps around the globe.
        if (PlasmaStep.counter(model)) counterBody(g.counterX, desk)
        if (PlasmaStep.scope(model)) scopeBody(g.scopeX, desk)
        if (PlasmaStep.valves(model)) valvesBody(g.valvesX, desk)
        if (PlasmaStep.ladder(model)) ladderBody(g.ladderX, desk)

        // The globe's black base: a turned foot, a collar under the glass, the electrode's stem.
        val c = g.globe; val r = Bench.R
        val foot = Path().apply {
            moveTo(c.x - 58f, c.y + r - 6f); lineTo(c.x + 58f, c.y + r - 6f); lineTo(c.x + 104f, desk); lineTo(c.x - 104f, desk); close()
        }
        drawOval(Color.Black.copy(alpha = 0.5f), Offset(c.x - 130f, desk - 8f), Size(260f, 22f))
        drawPath(foot, Brush.horizontalGradient(0f to Lab.bakelite, 0.3f to Lab.bakeliteLight, 0.5f to Color(0xFF14161D), 1f to Lab.bakelite, startX = c.x - 104f, endX = c.x + 104f))
        drawLine(Color.White.copy(alpha = 0.14f), Offset(c.x - 56f, c.y + r - 5f), Offset(c.x + 56f, c.y + r - 5f), 1.2f)
        drawRect(Brush.horizontalGradient(listOf(Lab.steelDark, Lab.steel, Lab.steelDark), startX = c.x - 62f, endX = c.x + 62f), Offset(c.x - 62f, c.y + r - 12f), Size(124f, 7f))
        drawLine(Lab.steelDark, Offset(c.x, c.y + 14f), Offset(c.x, c.y + r - 12f), 5f)
        drawLine(Lab.steel.copy(alpha = 0.7f), Offset(c.x - 1f, c.y + 14f), Offset(c.x - 1f, c.y + r - 12f), 1.2f)
    }

    // ------------------------------------------------------------------ the light (per frame)

    override fun draw(s: DrawScope, sim: PlasmaSim, f: ThemeFrame, tx: ThemeTexts) = with(s) {
        val g = bench(f.dw)
        val c = g.globe; val r = Bench.R
        val desk = Bench.DESK
        val model = f.model
        val gas = GAS[model.coerceIn(0, GAS.size - 1)]
        gasV = gas[0]; gasM = gas[1]; gasV2 = gas[2]; gasM2 = gas[3]
        // Brightness of everything the globe emits: the slow breath, dimmer while braking and when held.
        val b = sim.flicker * (1f - 0.42f * sim.braking) * (1f - 0.5f * sim.freeze)

        // The globe's light on the bench and inside the glass.
        drawOval(Brush.radialGradient(listOf(gasV.copy(alpha = 0.16f * b), gasV.copy(alpha = 0f)), Offset(c.x, desk + 30f), 300f), Offset(c.x - 300f, desk + 4f), Size(600f, 60f))
        drawCircle(Brush.radialGradient(0f to gasM.copy(alpha = 0.34f * b), 0.5f to gasV.copy(alpha = 0.16f * b), 1f to gasV.copy(alpha = 0.04f * b), center = c, radius = r), r, c)

        filaments(sim, c, r, b)

        // The electrode: a small bright ball.
        drawCircle(Brush.radialGradient(listOf(Lab.core.copy(alpha = b), gasM.copy(alpha = 0.7f * b), gasV.copy(alpha = 0f)), c, 26f), 26f, c)
        drawCircle(Lab.core.copy(alpha = 0.95f * b), 9f, c)

        // A blink's touch point on the glass: 80 ms in, a soft half second out (one per blink, flash-guarded).
        val tt = sim.since(sim.touchAt).toFloat()
        if (tt in 0f..1.2f && !sim.calm && sim.touchSide != TurnSignal.NONE) {
            val e = if (tt < 0.08f) tt / 0.08f else exp(-(tt - 0.08f) / 0.3f)
            val p = Offset(c.x + (if (sim.touchSide == TurnSignal.LEFT) -1f else 1f) * r * 0.97f, c.y)
            drawCircle(Brush.radialGradient(listOf(Lab.core.copy(alpha = 0.8f * e), gasM.copy(alpha = 0.35f * e), gasV.copy(alpha = 0f)), p, 30f), 30f, p)
        }
        // A star: one point of light rests on the glass for a while.
        val st = sim.since(sim.starAt).toFloat()
        if (st in 0f..PlasmaSim.STAR_S) {
            val e = (st / 0.5f).coerceAtMost(1f) * ((PlasmaSim.STAR_S - st) / 1.2f).coerceIn(0f, 1f)
            val p = Offset(c.x + sin(sim.starAngle) * r * 0.86f, c.y - cos(sim.starAngle) * r * 0.86f)
            drawCircle(Brush.radialGradient(listOf(Lab.core.copy(alpha = 0.5f * e), gasV.copy(alpha = 0f)), p, 18f), 18f, p)
            drawCircle(Lab.core.copy(alpha = 0.95f * e), 3.6f, p)
        }

        glass(sim, c, r)

        // The instruments' light.
        if (PlasmaStep.counter(model)) counterDigits(g.counterX, desk, sim.odometerM, tx, 1f - 0.4f * sim.freeze)
        if (PlasmaStep.valves(model)) valvesGlow(g.valvesX, desk, sim.tubeGlow * (1f - 0.4f * sim.freeze))
        if (PlasmaStep.scope(model)) scopeTrace(g.scopeX, desk, sim.scopeHz, sim.scopeAmp, sim.scopeT, 1f - 0.4f * sim.freeze, clip)
        if (PlasmaStep.ladder(model)) ladderArc(g.ladderX, desk, sim.ladder, sim.phase * 6.0, sim.flicker * (1f - 0.5f * sim.freeze), arc, arcGlow, arcMid, arcCore)
    }

    /**
     * The filaments: each a smooth curve from the electrode toward the glass, bent by slow noise. Three strokes:
     * a wide faint violet, a magenta body, a thin white core. A blinker pulls their ends to that side of the glass.
     */
    private fun DrawScope.filaments(sim: PlasmaSim, c: Offset, r: Float, b: Float) {
        val n = sim.filaments
        val ph = sim.phase
        val gather = sim.gather
        val pull = abs(gather) * 0.82f
        val side = if (gather < 0f) PI.toFloat() else 0f
        val reach = sim.reach
        val touching = ((reach - 0.86f) / 0.14f).coerceIn(0f, 1f)
        for (i in 0 until n) {
            val even = (i + 0.5f) / n * 2f * PI.toFloat() + sim.spin + (0.32 * sin(ph * 0.61 + i * 1.7)).toFloat()
            // Toward the touched side the ends bunch up, a little apart from each other.
            val bunch = side + (i - (n - 1) / 2f) * (0.9f / n)
            val a = even + shortest(bunch - even) * pull
            val len = r * 0.965f * reach * (1f - 0.06f * hash01(i, 3))
            val dx = cos(a); val dy = sin(a)
            val nx = -dy; val ny = dx
            val a1 = (r * 0.16 * sin(ph * 1.31 + i * 2.1)).toFloat()
            val a2 = (r * 0.13 * sin(ph * 0.87 + i * 3.7 + 1.0)).toFloat()
            val p0 = Offset(c.x + dx * 12f, c.y + dy * 12f)
            val end = Offset(c.x + dx * len, c.y + dy * len)
            strand.reset()
            strand.moveTo(p0.x, p0.y)
            strand.cubicTo(
                c.x + dx * len * 0.34f + nx * a1, c.y + dy * len * 0.34f + ny * a1,
                c.x + dx * len * 0.68f + nx * a2, c.y + dy * len * 0.68f + ny * a2,
                end.x, end.y,
            )
            // The mixed gas of the last step: every other filament takes the second colour.
            val second = i % 2 == 1
            drawPath(strand, (if (second) gasV2 else gasV).copy(alpha = 0.20f * b), style = glow)
            drawPath(strand, (if (second) gasM2 else gasM).copy(alpha = 0.55f * b), style = mid)
            drawPath(strand, Lab.core.copy(alpha = 0.9f * b), style = core)
            // Where a filament meets the glass it spreads into a small soft foot.
            if (touching > 0.02f) drawCircle(Brush.radialGradient(listOf(Lab.core.copy(alpha = 0.55f * b * touching), gasV.copy(alpha = 0f)), end, 13f), 13f, end)
        }
    }

    /** The shorter way round from 0 to [d] (radians). */
    private fun shortest(d: Float): Float {
        var x = d % (2f * PI.toFloat())
        if (x > PI) x -= 2f * PI.toFloat()
        if (x < -PI) x += 2f * PI.toFloat()
        return x
    }

    /** The glass: a thin rim, the brightening toward its edge, two soft reflections of the room, rain beads. */
    private fun DrawScope.glass(sim: PlasmaSim, c: Offset, r: Float) {
        drawCircle(Brush.radialGradient(0.74f to Color.Transparent, 1f to Lab.glass.copy(alpha = 0.20f), center = c, radius = r), r, c)
        drawCircle(Lab.glass.copy(alpha = 0.42f), r, c, style = rim)
        val r1 = r * 0.88f; val r2 = r * 0.76f
        drawArc(Color.White.copy(alpha = 0.16f), 200f, 46f, false, Offset(c.x - r1, c.y - r1), Size(r1 * 2, r1 * 2), style = band)
        drawArc(Color.White.copy(alpha = 0.10f), 214f, 20f, false, Offset(c.x - r2, c.y - r2), Size(r2 * 2, r2 * 2), style = bandThin)
        drawArc(Color.White.copy(alpha = 0.07f), 28f, 30f, false, Offset(c.x - r1, c.y - r1), Size(r1 * 2, r1 * 2), style = bandThin)
        val rain = sim.rain
        if (rain > 0.01f) for (i in 0 until DROPS) {
            if (hash01(i, 24) > rain) continue
            val a = hash01(i, 21) * 2f * PI.toFloat(); val d = r * (0.25f + 0.7f * hash01(i, 22))
            val p = Offset(c.x + cos(a) * d, c.y + sin(a) * d)
            val pr = 2.4f + 3.6f * hash01(i, 23)
            drawCircle(Color.White.copy(alpha = 0.16f), pr, p)
            drawCircle(Color.White.copy(alpha = 0.85f), pr * 0.3f, Offset(p.x - pr * 0.35f, p.y - pr * 0.38f))
        }
    }

    private companion object {
        const val DROPS = 34

        /**
         * The gas in the globe, one per step (glow, filament body, and a second pair for a mixed gas): violet argon,
         * blue xenon, orange-red neon, green krypton, and a violet and teal mix.
         */
        val GAS = arrayOf(
            arrayOf(Lab.violet, Lab.magenta, Lab.violet, Lab.magenta),
            arrayOf(Color(0xFF3F5BFF), Color(0xFF86A8FF), Color(0xFF3F5BFF), Color(0xFF86A8FF)),
            arrayOf(Color(0xFFFF4E2A), Color(0xFFFF9A62), Color(0xFFFF4E2A), Color(0xFFFF9A62)),
            arrayOf(Color(0xFF1FCF7A), Color(0xFF9CFFC8), Color(0xFF1FCF7A), Color(0xFF9CFFC8)),
            arrayOf(Lab.violet, Lab.magenta, Color(0xFF17B6C8), Color(0xFF74F2E8)),
        )
    }
}
